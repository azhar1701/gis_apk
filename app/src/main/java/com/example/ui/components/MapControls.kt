package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun MapFloatingControls(
    onLocateMe: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenTools: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(end = 16.dp, top = 80.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // GPS My Location
        SmallFloatingActionButton(
            onClick = onLocateMe,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .testTag("btn_my_location")
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Lokasi Saya")
        }

        // Layers & Basemap Switcher
        SmallFloatingActionButton(
            onClick = onOpenLayers,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .testTag("btn_layer_switcher")
        ) {
            Icon(Icons.Filled.Layers, contentDescription = "Pilih Layer dan Basemap")
        }

        // Field Tools (Koordinat & Foto EXIF)
        SmallFloatingActionButton(
            onClick = onOpenTools,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(48.dp)
                .testTag("btn_field_tools")
        ) {
            Icon(Icons.Filled.AddLocationAlt, contentDescription = "Alat Lapangan")
        }

        // Zoom Controls Card
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column {
                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("btn_zoom_in")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Perbesar Peta")
                }
                HorizontalDivider(modifier = Modifier.width(36.dp))
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("btn_zoom_out")
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Perkecil Peta")
                }
            }
        }

        // Reset View to Ciamis center
        SmallFloatingActionButton(
            onClick = onResetView,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(44.dp)
                .testTag("btn_reset_view")
        ) {
            Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Pusat Ciamis")
        }
    }
}
