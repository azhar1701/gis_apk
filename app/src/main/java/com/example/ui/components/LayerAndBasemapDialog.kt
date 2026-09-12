package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.gis.BasemapType

@Composable
fun LayerAndBasemapDialog(
    currentBasemap: BasemapType,
    showBatasDesa: Boolean,
    showIrigasi: Boolean,
    showSungai: Boolean,
    isDarkMode: Boolean,
    onSelectBasemap: (BasemapType) -> Unit,
    onToggleLayer: (String) -> Unit,
    onToggleDarkMode: () -> Unit,
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
                    imageVector = Icons.Filled.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Lapisan & Peta Dasar",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Basemap Section
                Text(
                    text = "Peta Dasar (Basemap)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasemapOptionCard(
                        title = "OpenStreetMap",
                        icon = Icons.Filled.Map,
                        isSelected = currentBasemap == BasemapType.OPEN_STREET_MAP,
                        onClick = { onSelectBasemap(BasemapType.OPEN_STREET_MAP) },
                        modifier = Modifier.weight(1f).testTag("opt_basemap_osm")
                    )
                    BasemapOptionCard(
                        title = "Satelit Hybrid",
                        icon = Icons.Filled.Satellite,
                        isSelected = currentBasemap == BasemapType.SATELLITE,
                        onClick = { onSelectBasemap(BasemapType.SATELLITE) },
                        modifier = Modifier.weight(1f).testTag("opt_basemap_satellite")
                    )
                }

                HorizontalDivider()

                // Vector Layers Section
                Text(
                    text = "Lapisan Vektor Sumber Daya Air",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LayerToggleRow(
                        title = "Batas Administrasi",
                        subtitle = "Batas Desa & Kecamatan",
                        color = Color(0xFF64748B),
                        isChecked = showBatasDesa,
                        onCheckedChange = { onToggleLayer("batasDesa") },
                        tag = "toggle_layer_batas_desa"
                    )

                    LayerToggleRow(
                        title = "Daerah Irigasi",
                        subtitle = "Area poligon, saluran & bangunan",
                        color = Color(0xFF10B981),
                        isChecked = showIrigasi,
                        onCheckedChange = { onToggleLayer("irigasi") },
                        tag = "toggle_layer_irigasi"
                    )

                    LayerToggleRow(
                        title = "Jaringan Sungai",
                        subtitle = "Aliran hidrografi & orde sungai",
                        color = Color(0xFF2563EB),
                        isChecked = showSungai,
                        onCheckedChange = { onToggleLayer("sungai") },
                        tag = "toggle_layer_sungai"
                    )
                }

                HorizontalDivider()

                // Theme Mode Switch Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDarkMode() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = null,
                            tint = if (isDarkMode) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Mode Gelap (Dark Mode)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isDarkMode) "Aktif (Tampilan kontras malam)" else "Nonaktif (Tampilan terang siang)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onToggleDarkMode() },
                        modifier = Modifier.testTag("dialog_switch_dark_mode")
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Terapkan")
            }
        }
    )
}

@Composable
private fun BasemapOptionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LayerToggleRow(
    title: String,
    subtitle: String,
    color: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, MaterialTheme.shapes.extraSmall)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
