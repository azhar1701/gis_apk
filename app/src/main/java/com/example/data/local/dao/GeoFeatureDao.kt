package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.GeoFeatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoFeatureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(features: List<GeoFeatureEntity>)

    @Query("DELETE FROM geo_features WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT * FROM geo_features WHERE source = :source")
    fun getFeaturesBySource(source: String): Flow<List<GeoFeatureEntity>>

    @Query("SELECT * FROM geo_features")
    fun getAllFeatures(): Flow<List<GeoFeatureEntity>>

    @Query("SELECT * FROM geo_features WHERE source IN (:sources)")
    suspend fun getFeaturesBySourcesSync(sources: List<String>): List<GeoFeatureEntity>

    @Query("""
        SELECT * FROM geo_features 
        WHERE source IN (:sources)
          AND maxLng >= :minLng AND minLng <= :maxLng
          AND maxLat >= :minLat AND minLat <= :maxLat
    """)
    suspend fun getFeaturesInBBox(
        sources: List<String>,
        minLng: Double,
        minLat: Double,
        maxLng: Double,
        maxLat: Double
    ): List<GeoFeatureEntity>

    @Query("""
        SELECT * FROM geo_features
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%' OR di LIKE '%' || :query || '%' OR nomenclature LIKE '%' || :query || '%')
          AND (:source = '' OR source = :source)
          AND (:geometryType = '' OR geometryType = :geometryType)
          AND (:district = '' OR district = :district)
          AND (:village = '' OR village = :village)
          AND (:di = '' OR di = :di)
          AND (:condition = '' OR condition = :condition)
        LIMIT 300
    """)
    fun filterFeatures(
        query: String,
        source: String,
        geometryType: String,
        district: String,
        village: String,
        di: String,
        condition: String
    ): Flow<List<GeoFeatureEntity>>

    @Query("SELECT DISTINCT district FROM geo_features WHERE district != '' ORDER BY district ASC")
    fun getDistinctDistricts(): Flow<List<String>>

    @Query("SELECT DISTINCT village FROM geo_features WHERE (:district = '' OR district = :district) AND village != '' ORDER BY village ASC")
    fun getDistinctVillages(district: String): Flow<List<String>>

    @Query("SELECT DISTINCT di FROM geo_features WHERE di != '' ORDER BY di ASC")
    fun getDistinctDIs(): Flow<List<String>>

    @Query("SELECT DISTINCT condition FROM geo_features WHERE condition != '' ORDER BY condition ASC")
    fun getDistinctConditions(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM geo_features WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("SELECT COUNT(*) FROM geo_features")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT * FROM geo_features WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GeoFeatureEntity?
}
