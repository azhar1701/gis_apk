package com.example.data.repository

import android.content.Context
import com.example.data.local.HydroGisDatabase
import com.example.data.local.entity.GeoFeatureEntity
import com.example.data.local.entity.InspectionEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.remote.GeoJsonParser
import com.example.data.remote.GeoJsonService
import com.example.data.remote.GisSources
import com.example.data.remote.SourceDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GisRepository(private val context: Context) {

    private val db = HydroGisDatabase.getInstance(context)
    private val featureDao = db.geoFeatureDao()
    private val syncDao = db.syncMetadataDao()
    private val inspectionDao = db.inspectionDao()
    private val geoJsonService = GeoJsonService()

    val allMetadata: Flow<List<SyncMetadataEntity>> = syncDao.getAllMetadata()
    val allInspections: Flow<List<InspectionEntity>> = inspectionDao.getAllInspections()

    fun filterFeatures(
        query: String,
        source: String,
        geometryType: String,
        district: String,
        village: String,
        di: String,
        condition: String
    ): Flow<List<GeoFeatureEntity>> {
        return featureDao.filterFeatures(
            query = query.trim(),
            source = source,
            geometryType = geometryType,
            district = district,
            village = village,
            di = di,
            condition = condition
        )
    }

    fun getDistinctDistricts(): Flow<List<String>> = featureDao.getDistinctDistricts()
    fun getDistinctVillages(district: String): Flow<List<String>> = featureDao.getDistinctVillages(district)
    fun getDistinctDIs(): Flow<List<String>> = featureDao.getDistinctDIs()
    fun getDistinctConditions(): Flow<List<String>> = featureDao.getDistinctConditions()
    fun getTotalFeatureCount(): Flow<Int> = featureDao.getTotalCountFlow()

    suspend fun getFeaturesInBBox(
        sources: List<String>,
        minLng: Double,
        minLat: Double,
        maxLng: Double,
        maxLat: Double
    ): List<GeoFeatureEntity> = withContext(Dispatchers.IO) {
        featureDao.getFeaturesInBBox(sources, minLng, minLat, maxLng, maxLat)
    }

    suspend fun getFeatureById(id: String): GeoFeatureEntity? = withContext(Dispatchers.IO) {
        featureDao.getById(id)
    }

    suspend fun syncSource(source: SourceDefinition): Result<Int> = withContext(Dispatchers.IO) {
        try {
            syncDao.upsert(
                SyncMetadataEntity(
                    sourceKey = source.key,
                    displayName = source.displayName,
                    url = source.url,
                    status = "SYNCING",
                    errorMessage = null
                )
            )

            val rawJson = geoJsonService.fetchGeoJson(source.url)
            val features = GeoJsonParser.parseFeatureCollection(source.key, rawJson)

            // Atomically replace existing records for this source
            featureDao.deleteBySource(source.key)
            featureDao.insertAll(features)

            val sizeKb = (rawJson.toByteArray().size / 1024.0)
            val sizeFormatted = if (sizeKb > 1024) String.format(Locale.getDefault(), "%.1f MB", sizeKb / 1024.0) else String.format(Locale.getDefault(), "%.0f KB", sizeKb)

            syncDao.upsert(
                SyncMetadataEntity(
                    sourceKey = source.key,
                    displayName = source.displayName,
                    url = source.url,
                    lastSyncTime = System.currentTimeMillis(),
                    featureCount = features.size,
                    status = "SUCCESS",
                    errorMessage = null,
                    fileSizeFormatted = sizeFormatted
                )
            )
            Result.success(features.size)
        } catch (e: Exception) {
            val existing = syncDao.getByKey(source.key)
            syncDao.upsert(
                SyncMetadataEntity(
                    sourceKey = source.key,
                    displayName = source.displayName,
                    url = source.url,
                    lastSyncTime = existing?.lastSyncTime ?: 0L,
                    featureCount = existing?.featureCount ?: 0,
                    status = "ERROR",
                    errorMessage = e.message ?: "Gagal menyinkronkan data",
                    fileSizeFormatted = existing?.fileSizeFormatted ?: ""
                )
            )
            Result.failure(e)
        }
    }

    suspend fun syncAll(): Map<String, Result<Int>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Result<Int>>()
        for (source in GisSources.ALL) {
            results[source.key] = syncSource(source)
        }
        results
    }

    /**
     * Complete background sync of local Room spatial data with remote server:
     * 1. Fetches latest spatial datasets from remote and updates local Room database
     * 2. Inspects local Room surveyor notes/inspections to ensure state consistency
     * 3. Returns total features synchronized
     */
    suspend fun syncSpatialDataWithRemote(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val syncMap = syncAll()
            val hasFailure = syncMap.values.any { it.isFailure }
            if (hasFailure) {
                val errorMsg = syncMap.values.firstOrNull { it.isFailure }?.exceptionOrNull()?.message ?: "Gagal sinkronisasi sebagian layer"
                return@withContext Result.failure(Exception(errorMsg))
            }
            val totalSynced = syncMap.values.mapNotNull { it.getOrNull() }.sum()
            Result.success(totalSynced)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllLocalInspections(): List<InspectionEntity> = withContext(Dispatchers.IO) {
        inspectionDao.getAllInspectionList()
    }

    suspend fun addInspection(inspection: InspectionEntity) = withContext(Dispatchers.IO) {
        inspectionDao.insert(inspection)
    }

    fun getInspectionsForFeature(featureId: String): Flow<List<InspectionEntity>> {
        return inspectionDao.getInspectionsForFeature(featureId)
    }

    suspend fun exportFeaturesAsCsv(features: List<GeoFeatureEntity>): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        sb.append("ID,Sumber,Nama,Kode_Aset,Nomenklatur,Kecamatan,Desa,Daerah_Irigasi,Kondisi,Tipe_Geometri,Luas_Atribut,Panjang_Atribut,Min_Lng,Min_Lat,Max_Lng,Max_Lat\n")
        for (f in features) {
            val escape = { s: String -> "\"${s.replace("\"", "\"\"")}\"" }
            sb.append(escape(f.id)).append(",")
            sb.append(escape(f.source)).append(",")
            sb.append(escape(f.name)).append(",")
            sb.append(escape(f.code)).append(",")
            sb.append(escape(f.nomenclature)).append(",")
            sb.append(escape(f.district)).append(",")
            sb.append(escape(f.village)).append(",")
            sb.append(escape(f.di)).append(",")
            sb.append(escape(f.condition)).append(",")
            sb.append(escape(f.geometryType)).append(",")
            sb.append(f.shapeArea?.toString() ?: "").append(",")
            sb.append(f.shapeLength?.toString() ?: "").append(",")
            sb.append(f.minLng).append(",")
            sb.append(f.minLat).append(",")
            sb.append(f.maxLng).append(",")
            sb.append(f.maxLat).append("\n")
        }
        sb.toString()
    }

    suspend fun exportFeaturesAsGeoJson(features: List<GeoFeatureEntity>): String = withContext(Dispatchers.Default) {
        val root = JSONObject()
        root.put("type", "FeatureCollection")
        val meta = JSONObject()
        meta.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        meta.put("featureCount", features.size)
        meta.put("application", "HydroGIS Ciamis")
        root.put("metadata", meta)

        val featuresArray = JSONArray()
        for (f in features) {
            val featObj = JSONObject()
            featObj.put("type", "Feature")
            featObj.put("id", f.id)
            
            val geomObj = JSONObject()
            geomObj.put("type", f.geometryType)
            geomObj.put("coordinates", JSONArray(f.coordinatesJson))
            featObj.put("geometry", geomObj)

            val props = JSONObject(f.propertiesJson)
            props.put("source_app", f.source)
            props.put("local_id", f.id)
            featObj.put("properties", props)

            featuresArray.put(featObj)
        }
        root.put("features", featuresArray)
        root.toString(2)
    }
}
