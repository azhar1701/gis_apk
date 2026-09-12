package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.GisViewModel
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: GisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkMode) {
                HydroGisMainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydroGisMainScreen(viewModel: GisViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe ViewModel states
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val renderables by viewModel.renderableFeatures.collectAsStateWithLifecycle()
    val selectedFeature by viewModel.selectedFeature.collectAsStateWithLifecycle()
    val candidates by viewModel.candidateFeatures.collectAsStateWithLifecycle()
    val targetMarker by viewModel.targetMarker.collectAsStateWithLifecycle()
    val targetLabel by viewModel.targetMarkerLabel.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val userAccuracy by viewModel.userAccuracy.collectAsStateWithLifecycle()

    val layerBatasDesa by viewModel.layerBatasDesa.collectAsStateWithLifecycle()
    val layerIrigasi by viewModel.layerIrigasi.collectAsStateWithLifecycle()
    val layerSungai by viewModel.layerSungai.collectAsStateWithLifecycle()

    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val totalFeatureCount by viewModel.totalFeatureCount.collectAsStateWithLifecycle()
    val districts by viewModel.distinctDistricts.collectAsStateWithLifecycle()
    val villages by viewModel.distinctVillages.collectAsStateWithLifecycle()
    val dis by viewModel.distinctDIs.collectAsStateWithLifecycle()
    val conditions by viewModel.distinctConditions.collectAsStateWithLifecycle()

    val syncMetadata by viewModel.syncMetadata.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val chargingWifiWorkInfo by viewModel.chargingWifiWorkInfo.collectAsStateWithLifecycle()
    val oneTimeWorkInfo by viewModel.oneTimeChargingWifiWorkInfo.collectAsStateWithLifecycle()

    // Dialog & Sheet states
    var showLayerDialog by remember { mutableStateOf(false) }
    var showToolsDialog by remember { mutableStateOf(false) }
    var currentBottomSheetTab by remember { mutableStateOf(0) } // 0: Search/Filter, 1: Detail, 2: Sync/Export

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = false
        )
    )

    // Switch to Detail tab automatically when a feature is selected
    LaunchedEffect(selectedFeature) {
        if (selectedFeature != null) {
            currentBottomSheetTab = 1
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    // Show status messages via Snackbar
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // GPS Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.requestUserLocation(context)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Izin lokasi ditolak. Aktifkan di pengaturan jika ingin memakai GPS.")
            }
        }
    }

    fun checkAndRequestLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.requestUserLocation(context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 84.dp,
        sheetShape = MaterialTheme.shapes.large,
        sheetShadowElevation = 12.dp,
        sheetDragHandle = {
            BottomSheetDefaults.DragHandle()
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Bottom Sheet Navigation Tabs
                PrimaryTabRow(
                    selectedTabIndex = currentBottomSheetTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = currentBottomSheetTab == 0,
                        onClick = { currentBottomSheetTab = 0 },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        text = { Text("Data & Cari", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = currentBottomSheetTab == 1,
                        onClick = { currentBottomSheetTab = 1 },
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        text = { Text("Detail Objek", fontSize = 11.sp) },
                        enabled = selectedFeature != null
                    )
                    Tab(
                        selected = currentBottomSheetTab == 2,
                        onClick = { currentBottomSheetTab = 2 },
                        icon = { Icon(Icons.Filled.CloudSync, contentDescription = null) },
                        text = { Text("Sinkronisasi", fontSize = 11.sp) }
                    )
                }

                when (currentBottomSheetTab) {
                    0 -> {
                        SearchAndFilterPanel(
                            filters = filters,
                            searchResults = searchResults,
                            totalFeatureCount = totalFeatureCount,
                            districts = districts,
                            villages = villages,
                            dis = dis,
                            conditions = conditions,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            onSourceChange = { viewModel.setFilterSource(it) },
                            onGeometryChange = { viewModel.setFilterGeometry(it) },
                            onDistrictChange = { viewModel.setFilterDistrict(it) },
                            onVillageChange = { viewModel.setFilterVillage(it) },
                            onDIChange = { viewModel.setFilterDI(it) },
                            onConditionChange = { viewModel.setFilterCondition(it) },
                            onResetFilters = { viewModel.resetFilters() },
                            onFeatureClick = { feature ->
                                viewModel.flyToFeature(feature)
                                currentBottomSheetTab = 1
                            }
                        )
                    }
                    1 -> {
                        selectedFeature?.let { feat ->
                            FeatureDetailSheet(
                                feature = feat,
                                onFocusFeature = { viewModel.flyToFeature(feat) },
                                onClose = { viewModel.clearSelection() },
                                onAddInspection = { cond, note ->
                                    viewModel.addInspectionNote(feat.code.ifEmpty { feat.id }, feat.id, cond, note)
                                }
                            )
                        } ?: Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pilih fitur pada peta atau cari dari tab Data untuk melihat detail atribut.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    2 -> {
                        SyncAndExportPanel(
                            syncMetadata = syncMetadata,
                            isSyncing = isSyncing,
                            totalFeatureCount = totalFeatureCount,
                            chargingWifiWorkInfo = chargingWifiWorkInfo,
                            oneTimeWorkInfo = oneTimeWorkInfo,
                            onQueueChargingWifiSync = { viewModel.queueChargingWifiSync() },
                            onSyncAll = { viewModel.syncAllData() },
                            onSyncSingle = { key -> viewModel.syncSingleSource(key) },
                            onExportCsv = { viewModel.exportCsv(searchResults) },
                            onExportGeoJson = { viewModel.exportGeoJson(searchResults) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Water logo icon
                        Icon(
                            imageVector = Icons.Filled.Water,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "HydroGIS Ciamis",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (isSyncing) Color(0xFFFBBF24) else if (totalFeatureCount > 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (isSyncing) "Menyinkronkan..." else "$totalFeatureCount Fitur SDA • Offline Room",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Switch between light or dark mode
                    IconButton(
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier.testTag("topbar_btn_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDarkMode) "Beralih ke Mode Terang" else "Beralih ke Mode Gelap",
                            tint = if (isDarkMode) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.syncAllData() },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("topbar_btn_sync")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = "Sinkronkan Sekarang"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Interactive Vector Map Canvas
            GisMapView(
                cameraState = cameraState,
                basemapType = basemap,
                features = renderables,
                selectedFeature = selectedFeature,
                targetMarker = targetMarker,
                targetMarkerLabel = targetLabel,
                userLocation = userLocation,
                userAccuracy = userAccuracy,
                onMapTapped = { lng, lat, zoom ->
                    viewModel.onMapTapped(lng, lat, zoom)
                },
                onCameraChange = { lng, lat, zoom ->
                    viewModel.setPanAndZoom(lng, lat, zoom)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Controls on right edge
            MapFloatingControls(
                onLocateMe = { checkAndRequestLocation() },
                onOpenLayers = { showLayerDialog = true },
                onOpenTools = { showToolsDialog = true },
                onZoomIn = { viewModel.setPanAndZoom(cameraState.centerLng, cameraState.centerLat, cameraState.zoom + 1.0) },
                onZoomOut = { viewModel.setPanAndZoom(cameraState.centerLng, cameraState.centerLat, cameraState.zoom - 1.0) },
                onResetView = { viewModel.flyTo(108.35, -7.32, 11.5) },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }

    // Spatial Disambiguation Dialog (when multiple features are tapped)
    if (candidates.size > 1) {
        FeatureChoiceDialog(
            candidates = candidates,
            onSelectFeature = { feature ->
                viewModel.selectFeature(feature)
                currentBottomSheetTab = 1
            },
            onDismiss = {
                viewModel.clearSelection()
            }
        )
    }

    // Layers & Basemap Dialog
    if (showLayerDialog) {
        LayerAndBasemapDialog(
            currentBasemap = basemap,
            showBatasDesa = layerBatasDesa,
            showIrigasi = layerIrigasi,
            showSungai = layerSungai,
            isDarkMode = isDarkMode,
            onSelectBasemap = { viewModel.setBasemap(it) },
            onToggleLayer = { viewModel.toggleLayer(it) },
            onToggleDarkMode = { viewModel.toggleDarkMode() },
            onDismiss = { showLayerDialog = false }
        )
    }

    // Field Tools Dialog (Manual Coordinates & Foto EXIF Geotag)
    if (showToolsDialog) {
        FieldToolsDialog(
            onSetCoordinate = { lng, lat, label ->
                viewModel.setTargetMarker(lng, lat, label)
            },
            onPhotoSelected = { uri ->
                viewModel.parsePhotoExif(context, uri)
            },
            onClearTarget = {
                viewModel.clearTargetMarker()
            },
            onDismiss = { showToolsDialog = false }
        )
    }
}
