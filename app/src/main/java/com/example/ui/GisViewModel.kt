package com.example.ui

import android.app.Application
import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GeoFeatureEntity
import com.example.data.local.entity.InspectionEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.remote.GisSources
import com.example.data.repository.GisRepository
import com.example.gis.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

data class MapCameraState(
    val centerLng: Double = 108.35,
    val centerLat: Double = -7.32,
    val zoom: Double = 11.5
)

data class FilterState(
    val query: String = "",
    val source: String = "",
    val geometryType: String = "",
    val district: String = "",
    val village: String = "",
    val di: String = "",
    val condition: String = ""
)

class GisViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GisRepository(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // Camera state
    private val _cameraState = MutableStateFlow(MapCameraState())
    val cameraState: StateFlow<MapCameraState> = _cameraState.asStateFlow()

    // Layer visibility
    private val _layerBatasDesa = MutableStateFlow(true)
    val layerBatasDesa: StateFlow<Boolean> = _layerBatasDesa.asStateFlow()

    private val _layerIrigasi = MutableStateFlow(true)
    val layerIrigasi: StateFlow<Boolean> = _layerIrigasi.asStateFlow()

    private val _layerSungai = MutableStateFlow(true)
    val layerSungai: StateFlow<Boolean> = _layerSungai.asStateFlow()

    // Basemap
    private val _basemap = MutableStateFlow(BasemapType.OPEN_STREET_MAP)
    val basemap: StateFlow<BasemapType> = _basemap.asStateFlow()

    // Filter & Search
    private val _filters = MutableStateFlow(FilterState())
    val filters: StateFlow<FilterState> = _filters.asStateFlow()

    // Selection
    private val _selectedFeature = MutableStateFlow<GeoFeatureEntity?>(null)
    val selectedFeature: StateFlow<GeoFeatureEntity?> = _selectedFeature.asStateFlow()

    // Disambiguation picker
    private val _candidateFeatures = MutableStateFlow<List<GeoFeatureEntity>>(emptyList())
    val candidateFeatures: StateFlow<List<GeoFeatureEntity>> = _candidateFeatures.asStateFlow()

    // Custom Target Marker (e.g. from manual coordinate or photo)
    private val _targetMarker = MutableStateFlow<GisPoint?>(null)
    val targetMarker: StateFlow<GisPoint?> = _targetMarker.asStateFlow()

    private val _targetMarkerLabel = MutableStateFlow<String>("")
    val targetMarkerLabel: StateFlow<String> = _targetMarkerLabel.asStateFlow()

    // User GPS location
    private val _userLocation = MutableStateFlow<GisPoint?>(null)
    val userLocation: StateFlow<GisPoint?> = _userLocation.asStateFlow()

    private val _userAccuracy = MutableStateFlow<Float?>(null)
    val userAccuracy: StateFlow<Float?> = _userAccuracy.asStateFlow()

    // Sync metadata from Room
    val syncMetadata: StateFlow<List<SyncMetadataEntity>> = repository.allMetadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalFeatureCount: StateFlow<Int> = repository.getTotalFeatureCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val distinctDistricts: StateFlow<List<String>> = repository.getDistinctDistricts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val distinctVillages: StateFlow<List<String>> = _filters
        .flatMapLatest { f -> repository.getDistinctVillages(f.district) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctDIs: StateFlow<List<String>> = repository.getDistinctDIs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctConditions: StateFlow<List<String>> = repository.getDistinctConditions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<GeoFeatureEntity>> = _filters
        .flatMapLatest { f ->
            repository.filterFeatures(
                query = f.query,
                source = f.source,
                geometryType = f.geometryType,
                district = f.district,
                village = f.village,
                di = f.di,
                condition = f.condition
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // In-memory cache of renderable features
    private val _renderableFeatures = MutableStateFlow<List<RenderableFeature>>(emptyList())
    val renderableFeatures: StateFlow<List<RenderableFeature>> = _renderableFeatures.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Observe active layers and reload features into memory for fast Canvas rendering
        viewModelScope.launch {
            combine(
                _layerBatasDesa,
                _layerIrigasi,
                _layerSungai,
                totalFeatureCount
            ) { desa, irigasi, sungai, count ->
                Triple(desa, irigasi, sungai)
            }.collect { (desa, irigasi, sungai) ->
                loadFeaturesIntoMemory(desa, irigasi, sungai)
            }
        }

        // Check if database is empty on first run, auto-sync if empty
        viewModelScope.launch {
            repository.getTotalFeatureCount().firstOrNull()?.let { count ->
                if (count == 0) {
                    syncAllData()
                }
            }
        }
    }

    private suspend fun loadFeaturesIntoMemory(desa: Boolean, irigasi: Boolean, sungai: Boolean) = withContext(Dispatchers.Default) {
        val sources = mutableListOf<String>()
        if (desa) sources.add("batasDesa")
        if (irigasi) sources.add("irigasi")
        if (sungai) sources.add("sungai")

        if (sources.isEmpty()) {
            _renderableFeatures.value = emptyList()
            return@withContext
        }

        val entities = repository.getFeaturesInBBox(sources, -180.0, -90.0, 180.0, 90.0)
        val renderables = entities.mapNotNull { FeatureGeometryParser.parseEntity(it) }
        _renderableFeatures.value = renderables
    }

    fun syncAllData() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _statusMessage.value = "Menyinkronkan data spasial..."
            val results = repository.syncAll()
            val total = results.values.sumOf { it.getOrDefault(0) }
            _statusMessage.value = "Sinkronisasi selesai: $total fitur tersimpan di Room"
            _isSyncing.value = false
            // Refresh renderables
            loadFeaturesIntoMemory(_layerBatasDesa.value, _layerIrigasi.value, _layerSungai.value)
        }
    }

    fun syncSingleSource(sourceKey: String) {
        val def = GisSources.ALL.find { it.key == sourceKey } ?: return
        viewModelScope.launch {
            _statusMessage.value = "Menyinkronkan ${def.displayName}..."
            val res = repository.syncSource(def)
            if (res.isSuccess) {
                _statusMessage.value = "${def.displayName} berhasil disinkronkan (${res.getOrDefault(0)} fitur)"
                loadFeaturesIntoMemory(_layerBatasDesa.value, _layerIrigasi.value, _layerSungai.value)
            } else {
                _statusMessage.value = "Gagal menyinkronkan: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun setPanAndZoom(centerLng: Double, centerLat: Double, zoom: Double) {
        _cameraState.value = MapCameraState(
            centerLng = centerLng.coerceIn(-180.0, 180.0),
            centerLat = centerLat.coerceIn(-85.0, 85.0),
            zoom = zoom.coerceIn(5.0, 20.0)
        )
    }

    fun flyTo(lng: Double, lat: Double, zoom: Double = 15.0) {
        _cameraState.value = MapCameraState(centerLng = lng, centerLat = lat, zoom = zoom)
    }

    fun flyToFeature(feature: GeoFeatureEntity) {
        selectFeature(feature)
        val centerLng = (feature.minLng + feature.maxLng) / 2.0
        val centerLat = (feature.minLat + feature.maxLat) / 2.0
        val zoom = if (feature.geometryType == "Point" || feature.geometryType == "MultiPoint") 16.5 else 14.0
        flyTo(centerLng, centerLat, zoom)
    }

    fun toggleLayer(sourceKey: String) {
        when (sourceKey) {
            "batasDesa" -> _layerBatasDesa.value = !_layerBatasDesa.value
            "irigasi" -> _layerIrigasi.value = !_layerIrigasi.value
            "sungai" -> _layerSungai.value = !_layerSungai.value
        }
    }

    fun setBasemap(type: BasemapType) {
        _basemap.value = type
    }

    fun updateSearchQuery(query: String) {
        _filters.value = _filters.value.copy(query = query)
    }

    fun setFilterSource(source: String) {
        _filters.value = _filters.value.copy(source = source)
    }

    fun setFilterGeometry(geometry: String) {
        _filters.value = _filters.value.copy(geometryType = geometry)
    }

    fun setFilterDistrict(district: String) {
        _filters.value = _filters.value.copy(district = district, village = "")
    }

    fun setFilterVillage(village: String) {
        _filters.value = _filters.value.copy(village = village)
    }

    fun setFilterDI(di: String) {
        _filters.value = _filters.value.copy(di = di)
    }

    fun setFilterCondition(condition: String) {
        _filters.value = _filters.value.copy(condition = condition)
    }

    fun resetFilters() {
        _filters.value = FilterState()
    }

    fun selectFeature(feature: GeoFeatureEntity?) {
        _selectedFeature.value = feature
        _candidateFeatures.value = emptyList()
    }

    fun clearSelection() {
        _selectedFeature.value = null
        _candidateFeatures.value = emptyList()
    }

    fun setTargetMarker(lng: Double, lat: Double, label: String) {
        _targetMarker.value = GisPoint(lng, lat)
        _targetMarkerLabel.value = label
        flyTo(lng, lat, 16.0)
    }

    fun clearTargetMarker() {
        _targetMarker.value = null
        _targetMarkerLabel.value = ""
    }

    /**
     * Handles spatial tap on the map.
     * Evaluates all visible layers, calculates distance/containment, and disambiguates overlapping features.
     */
    fun onMapTapped(tapLng: Double, tapLat: Double, zoom: Double) {
        val tapPoint = GisPoint(tapLng, tapLat)
        
        // Pixel tolerance converted to degrees based on zoom
        val degTolerance = (360.0 / (MercatorProjection.TILE_SIZE * Math.pow(2.0, zoom))) * 24.0

        val candidates = mutableListOf<GeoFeatureEntity>()

        for (rf in _renderableFeatures.value) {
            val entity = rf.entity
            // Fast bounding box check with tolerance
            if (tapLng < entity.minLng - degTolerance || tapLng > entity.maxLng + degTolerance ||
                tapLat < entity.minLat - degTolerance || tapLat > entity.maxLat + degTolerance) {
                continue
            }

            var hit = false
            when (val geom = rf.geometry) {
                is FeatureGeometry.Point -> {
                    hit = Math.hypot(tapLng - geom.point.lng, tapLat - geom.point.lat) <= degTolerance
                }
                is FeatureGeometry.MultiPoint -> {
                    hit = geom.points.any { Math.hypot(tapLng - it.lng, tapLat - it.lat) <= degTolerance }
                }
                is FeatureGeometry.LineString -> {
                    hit = SpatialAlgorithms.pointToPolylineDistance(tapPoint, geom.points) <= degTolerance
                }
                is FeatureGeometry.MultiLineString -> {
                    hit = geom.lines.any { SpatialAlgorithms.pointToPolylineDistance(tapPoint, it) <= degTolerance }
                }
                is FeatureGeometry.Polygon -> {
                    hit = geom.rings.isNotEmpty() && SpatialAlgorithms.isPointInPolygon(tapPoint, geom.rings[0])
                }
                is FeatureGeometry.MultiPolygon -> {
                    hit = geom.polygons.any { rings -> rings.isNotEmpty() && SpatialAlgorithms.isPointInPolygon(tapPoint, rings[0]) }
                }
            }

            if (hit) {
                candidates.add(entity)
            }
        }

        // Sort candidates by visual priority (Points > Lines > Polygons)
        candidates.sortByDescending {
            when (it.geometryType) {
                "Point", "MultiPoint" -> 3
                "LineString", "MultiLineString" -> 2
                else -> 1
            }
        }

        if (candidates.size == 1) {
            selectFeature(candidates[0])
        } else if (candidates.size > 1) {
            _candidateFeatures.value = candidates
            _selectedFeature.value = candidates[0]
        } else {
            clearSelection()
        }
    }

    /**
     * Requests GPS device location with high accuracy.
     */
    fun requestUserLocation(context: Context) {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        _userLocation.value = GisPoint(loc.longitude, loc.latitude)
                        _userAccuracy.value = loc.accuracy
                        flyTo(loc.longitude, loc.latitude, 16.0)
                        _statusMessage.value = "Lokasi terdeteksi (Akurasi: ±${loc.accuracy.toInt()}m)"
                    } else {
                        _statusMessage.value = "Tidak dapat menemukan sinyal GPS"
                    }
                }
                .addOnFailureListener {
                    _statusMessage.value = "Gagal mengambil lokasi: ${it.message}"
                }
        } catch (e: SecurityException) {
            _statusMessage.value = "Izin lokasi diperlukan"
        }
    }

    /**
     * Reads GPS EXIF coordinates from photo URI.
     */
    fun parsePhotoExif(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = androidx.exifinterface.media.ExifInterface(stream)
                    val latLong = FloatArray(2)
                    if (exif.getLatLong(latLong)) {
                        val lat = latLong[0].toDouble()
                        val lng = latLong[1].toDouble()
                        withContext(Dispatchers.Main) {
                            setTargetMarker(lng, lat, "Foto EXIF Geotag")
                            _statusMessage.value = "Koordinat Foto: Lat ${String.format("%.5f", lat)}, Lng ${String.format("%.5f", lng)}"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _statusMessage.value = "Foto tidak memiliki metadata koordinat GPS (EXIF)"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Gagal membaca EXIF foto: ${e.message}"
                }
            }
        }
    }

    fun addInspectionNote(assetId: String, featureId: String, condition: String, note: String) {
        viewModelScope.launch {
            val inspection = InspectionEntity(
                id = "insp_${System.currentTimeMillis()}",
                assetId = assetId,
                featureId = featureId,
                observedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                condition = condition,
                note = note,
                latitude = _userLocation.value?.lat,
                longitude = _userLocation.value?.lng
            )
            repository.addInspection(inspection)
            _statusMessage.value = "Catatan inspeksi berhasil disimpan"
        }
    }

    suspend fun exportCsv(features: List<GeoFeatureEntity>): String {
        return repository.exportFeaturesAsCsv(features)
    }

    suspend fun exportGeoJson(features: List<GeoFeatureEntity>): String {
        return repository.exportFeaturesAsGeoJson(features)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
