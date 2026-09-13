package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GeoFeatureEntity
import com.example.ui.FilterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndFilterPanel(
    filters: FilterState,
    searchResults: List<GeoFeatureEntity>,
    totalFeatureCount: Int,
    districts: List<String>,
    villages: List<String>,
    dis: List<String>,
    conditions: List<String>,
    onQueryChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onGeometryChange: (String) -> Unit,
    onDistrictChange: (String) -> Unit,
    onVillageChange: (String) -> Unit,
    onDIChange: (String) -> Unit,
    onConditionChange: (String) -> Unit,
    onResetFilters: () -> Unit,
    onFeatureClick: (GeoFeatureEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFilterExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = filters.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Cari nama, ID aset, kode, wilayah atau DI...") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Cari")
            },
            trailingIcon = {
                if (filters.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_search_query")
        )

        // Quick source filter chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val sources = listOf(
                "" to "Semua Sumber",
                "batasDesa" to "Administrasi",
                "sungai" to "Sungai",
                "irigasi" to "Irigasi"
            )
            items(sources) { (key, label) ->
                FilterChip(
                    selected = filters.source == key,
                    onClick = { onSourceChange(if (filters.source == key) "" else key) },
                    label = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.testTag("filter_source_$key")
                )
            }
        }

        // Toggle filter details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { isFilterExpanded = !isFilterExpanded },
                modifier = Modifier.testTag("btn_toggle_advanced_filters")
            ) {
                Icon(
                    imageVector = if (isFilterExpanded) Icons.Filled.FilterListOff else Icons.Filled.FilterList,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isFilterExpanded) "Tutup Filter Lanjutan" else "Filter Lanjutan")
            }

            TextButton(
                onClick = onResetFilters,
                modifier = Modifier.testTag("btn_reset_filters")
            ) {
                Text("Reset")
            }
        }

        // Expanded Filter Dropdowns
        if (isFilterExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Geometry Filter
                FilterDropdown(
                    label = "Jenis Geometri",
                    currentValue = filters.geometryType,
                    options = listOf("" to "Semua Geometri", "Point" to "Titik", "LineString" to "Garis", "Polygon" to "Poligon"),
                    onSelect = onGeometryChange
                )

                // District Filter
                FilterDropdown(
                    label = "Kecamatan",
                    currentValue = filters.district,
                    options = listOf("" to "Semua Kecamatan") + districts.map { it to it },
                    onSelect = onDistrictChange
                )

                // Village Filter
                if (villages.isNotEmpty()) {
                    FilterDropdown(
                        label = "Desa / Kelurahan",
                        currentValue = filters.village,
                        options = listOf("" to "Semua Desa") + villages.map { it to it },
                        onSelect = onVillageChange
                    )
                }

                // DI Filter
                if (dis.isNotEmpty()) {
                    FilterDropdown(
                        label = "Daerah Irigasi (DI)",
                        currentValue = filters.di,
                        options = listOf("" to "Semua DI") + dis.map { it to it },
                        onSelect = onDIChange
                    )
                }

                // Condition Filter
                if (conditions.isNotEmpty()) {
                    FilterDropdown(
                        label = "Kondisi",
                        currentValue = filters.condition,
                        options = listOf("" to "Semua Kondisi") + conditions.map { it to it },
                        onSelect = onConditionChange
                    )
                }
            }
        }

        // Summary count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${searchResults.size} dari $totalFeatureCount fitur ditemukan",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Search Results List
        if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tidak ada data sesuai kata kunci/filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(searchResults, key = { it.id }) { feature ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFeatureClick(feature) }
                            .testTag("search_result_item_${feature.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val (sourceLabel, sourceColor) = when (feature.source) {
                                "batasDesa" -> Pair("Batas", Color(0xFF64748B))
                                "sungai" -> Pair("Sungai", Color(0xFF2563EB))
                                "irigasi" -> Pair("Irigasi", Color(0xFF0D9488))
                                else -> Pair(feature.source, MaterialTheme.colorScheme.primary)
                            }

                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = sourceColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = sourceLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = sourceColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feature.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1
                                )
                                val subText = listOfNotNull(
                                    feature.code.takeIf { it.isNotEmpty() },
                                    feature.village.takeIf { it.isNotEmpty() },
                                    feature.district.takeIf { it.isNotEmpty() },
                                    feature.di.takeIf { it.isNotEmpty() }?.let { "DI $it" }
                                ).joinToString(" • ")

                                if (subText.isNotEmpty()) {
                                    Text(
                                        text = subText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Filled.NearMe,
                                contentDescription = "Lihat di Peta",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = options.find { it.first == currentValue }?.second ?: currentValue.ifEmpty { "Semua" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}
