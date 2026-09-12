package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GeoFeatureEntity

@Composable
fun FeatureChoiceDialog(
    candidates: List<GeoFeatureEntity>,
    onSelectFeature: (GeoFeatureEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pilih Objek (${candidates.size} ditemukan)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Beberapa objek berada di lokasi sentuhan ini. Pilih salah satu untuk melihat detail lengkap:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates) { feature ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFeature(feature) }
                                .testTag("candidate_item_${feature.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val (icon, iconColor) = when (feature.source) {
                                    "sungai" -> Pair(Icons.Filled.Water, Color(0xFF2563EB))
                                    "batasDesa" -> Pair(Icons.Filled.Map, Color(0xFF64748B))
                                    "irigasi" -> when (feature.geometryType) {
                                        "Point", "MultiPoint" -> Pair(Icons.Filled.LocationOn, Color(0xFFEF4444))
                                        "LineString", "MultiLineString" -> Pair(Icons.Filled.LinearScale, Color(0xFF06B6D4))
                                        else -> Pair(Icons.Filled.CropSquare, Color(0xFF10B981))
                                    }
                                    else -> Pair(Icons.Filled.Place, MaterialTheme.colorScheme.primary)
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = feature.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1
                                    )
                                    val subtitle = listOfNotNull(
                                        when (feature.source) {
                                            "batasDesa" -> "Batas Administrasi"
                                            "sungai" -> "Jaringan Sungai"
                                            "irigasi" -> "Daerah Irigasi"
                                            else -> feature.source
                                        },
                                        feature.code.takeIf { it.isNotEmpty() }?.let { "Kode: $it" },
                                        feature.village.takeIf { it.isNotEmpty() },
                                        feature.district.takeIf { it.isNotEmpty() }
                                    ).joinToString(" • ")

                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_disambiguation")
            ) {
                Text("Tutup")
            }
        }
    )
}
