package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.json.JSONObject

@Composable
fun FeatureDetailSheet(
    feature: GeoFeatureEntity,
    onFocusFeature: () -> Unit,
    onClose: () -> Unit,
    onAddInspection: (condition: String, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isAddingNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var selectedCondition by remember { mutableStateOf("baik") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("feature_detail_sheet"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (sourceBadgeText, sourceBadgeColor) = when (feature.source) {
                    "batasDesa" -> Pair("Batas Administrasi", Color(0xFF64748B))
                    "sungai" -> Pair("Jaringan Sungai", Color(0xFF2563EB))
                    "irigasi" -> Pair("Daerah Irigasi", Color(0xFF0D9488))
                    else -> Pair(feature.source, MaterialTheme.colorScheme.primary)
                }

                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = sourceBadgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = sourceBadgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = sourceBadgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onFocusFeature,
                        modifier = Modifier.size(36.dp).testTag("btn_focus_feature")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusStrong,
                            contentDescription = "Fokus Fitur",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp).testTag("btn_close_feature_detail")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup Detail")
                    }
                }
            }

            // Main Name
            Text(
                text = feature.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Quick Facts Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (feature.code.isNotEmpty()) {
                    AttributeRow(label = "Kode Aset", value = feature.code)
                }
                if (feature.nomenclature.isNotEmpty()) {
                    AttributeRow(label = "Nomenklatur", value = feature.nomenclature)
                }
                if (feature.di.isNotEmpty()) {
                    AttributeRow(label = "Daerah Irigasi (DI)", value = feature.di)
                }
                if (feature.village.isNotEmpty()) {
                    AttributeRow(label = "Desa / Kelurahan", value = feature.village)
                }
                if (feature.district.isNotEmpty()) {
                    AttributeRow(label = "Kecamatan", value = feature.district)
                }
                AttributeRow(label = "Kabupaten", value = "Ciamis")
                AttributeRow(label = "Tipe Geometri", value = feature.geometryType)

                feature.shapeArea?.let { area ->
                    val formatted = String.format(java.util.Locale.US, "%.2f m²", area)
                    AttributeRow(label = "Luas (atribut sumber)", value = formatted)
                }
                feature.shapeLength?.let { len ->
                    val formatted = String.format(java.util.Locale.US, "%.2f m", len)
                    AttributeRow(label = "Panjang (atribut sumber)", value = formatted)
                }
                if (feature.condition.isNotEmpty()) {
                    AttributeRow(label = "Kondisi (atribut)", value = feature.condition)
                }
            }

            // Extended Raw Properties Accordion
            var showRawProps by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showRawProps = !showRawProps },
                modifier = Modifier.fillMaxWidth().testTag("btn_toggle_raw_props")
            ) {
                Icon(
                    imageVector = if (showRawProps) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(if (showRawProps) "Sembunyikan Atribut Lengkap" else "Lihat Semua Atribut Lengkap")
            }

            if (showRawProps) {
                val props = remember(feature.propertiesJson) {
                    try {
                        val json = JSONObject(feature.propertiesJson)
                        val map = mutableListOf<Pair<String, String>>()
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map.add(k to json.optString(k, ""))
                        }
                        map
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    props.forEach { (k, v) ->
                        if (v.isNotEmpty() && v != "null") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = k,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // Field Inspection Section
            HorizontalDivider()
            if (!isAddingNote) {
                Button(
                    onClick = { isAddingNote = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_add_inspection")
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah Catatan Inspeksi Lapangan")
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Input Kondisi & Catatan Lapangan",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    // Condition radio selector
                    val conditions = listOf("baik", "rusak_ringan", "rusak_sedang", "rusak_berat")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        conditions.forEach { cond ->
                            FilterChip(
                                selected = selectedCondition == cond,
                                onClick = { selectedCondition = cond },
                                label = { Text(cond.replace("_", " "), fontSize = 10.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Catatan Pengamatan Lapangan") },
                        placeholder = { Text("Contoh: Pintu air berfungsi baik, debit lancar...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { isAddingNote = false }) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                onAddInspection(selectedCondition, noteText)
                                isAddingNote = false
                                noteText = ""
                            }
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
