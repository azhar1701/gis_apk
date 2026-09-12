package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.FileProvider
import androidx.work.WorkInfo
import com.example.data.local.entity.GeoFeatureEntity
import com.example.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncAndExportPanel(
    syncMetadata: List<SyncMetadataEntity>,
    isSyncing: Boolean,
    totalFeatureCount: Int,
    chargingWifiWorkInfo: WorkInfo? = null,
    oneTimeWorkInfo: WorkInfo? = null,
    onQueueChargingWifiSync: () -> Unit = {},
    onSyncAll: () -> Unit,
    onSyncSingle: (String) -> Unit,
    onExportCsv: suspend () -> String,
    onExportGeoJson: suspend () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }

    fun shareExportedFile(fileName: String, content: String, mimeType: String) {
        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            FileOutputStream(file).use { it.write(content.toByteArray()) }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan / Simpan Berkas GIS"))
            exportSuccessMessage = "Berkas $fileName siap dibagikan."
        } catch (e: Exception) {
            exportSuccessMessage = "Gagal mengekspor: ${e.message}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sinkronisasi & Basis Data",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Tersimpan offline di Room Database ($totalFeatureCount fitur)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onSyncAll,
                enabled = !isSyncing,
                modifier = Modifier.testTag("btn_sync_all")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Sinkron...")
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Sinkron Semua")
                }
            }
        }

        // Layer Metadata Cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val defaultSources = listOf(
                Triple("batasDesa", "Batas Administrasi", Color(0xFF64748B)),
                Triple("sungai", "Jaringan Sungai", Color(0xFF2563EB)),
                Triple("irigasi", "Daerah Irigasi", Color(0xFF10B981))
            )

            defaultSources.forEach { (key, title, color) ->
                val meta = syncMetadata.find { it.sourceKey == key }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(top = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = color,
                                    modifier = Modifier.size(12.dp)
                                ) {}
                            }

                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                val syncInfo = if (meta != null && meta.lastSyncTime > 0) {
                                    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(meta.lastSyncTime))
                                    "${meta.featureCount} fitur • $dateStr (${meta.fileSizeFormatted})"
                                } else {
                                    "Belum disinkronkan"
                                }
                                Text(
                                    text = syncInfo,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { onSyncSingle(key) },
                            enabled = !isSyncing,
                            modifier = Modifier.size(36.dp).testTag("btn_sync_$key")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Sinkronkan $title")
                        }
                    }
                }
            }
        }

        // WorkManager Background Sync Task (Charging + Wi-Fi)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Sinkronisasi Latar Belakang (WorkManager)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Periodic Work status badge
                    val stateName = chargingWifiWorkInfo?.state?.name ?: "TERJADWAL"
                    val isRunning = chargingWifiWorkInfo?.state == WorkInfo.State.RUNNING || oneTimeWorkInfo?.state == WorkInfo.State.RUNNING
                    Surface(
                        shape = CircleShape,
                        color = if (isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isRunning) "SEDANG BERJALAN" else stateName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = if (isRunning) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Tugas latar belakang WorkManager otomatis menyinkronkan data spasial Room dengan server pusat saat kondisi terpenuhi:",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Required Constraints Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("Mengisi Daya", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text("Charging", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("Jaringan Wi-Fi", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text("Unmetered", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Action to queue one-time test task
                OutlinedButton(
                    onClick = onQueueChargingWifiSync,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_queue_charging_wifi_sync")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Antrekan Tugas Uji Coba (Jalankan Saat Dicas & Wi-Fi)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        HorizontalDivider()

        // Export Data Section
        Text(
            text = "Ekspor Data Spasial",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isExporting = true
                        val csv = onExportCsv()
                        val fileName = "HydroGIS_Ciamis_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.csv"
                        shareExportedFile(fileName, csv, "text/csv")
                        isExporting = false
                    }
                },
                enabled = !isExporting && totalFeatureCount > 0,
                modifier = Modifier.weight(1f).testTag("btn_export_csv")
            ) {
                Icon(Icons.Filled.TableChart, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Ekspor CSV")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isExporting = true
                        val geoJson = onExportGeoJson()
                        val fileName = "HydroGIS_Ciamis_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.geojson"
                        shareExportedFile(fileName, geoJson, "application/geo+json")
                        isExporting = false
                    }
                },
                enabled = !isExporting && totalFeatureCount > 0,
                modifier = Modifier.weight(1f).testTag("btn_export_geojson")
            ) {
                Icon(Icons.Filled.Polyline, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Ekspor GeoJSON")
            }
        }

        exportSuccessMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
