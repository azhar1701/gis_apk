package com.example.gis

import androidx.compose.ui.graphics.Color
import com.example.data.local.entity.GeoFeatureEntity
import org.json.JSONArray

sealed class FeatureGeometry {
    data class Point(val point: GisPoint) : FeatureGeometry()
    data class MultiPoint(val points: List<GisPoint>) : FeatureGeometry()
    data class LineString(val points: List<GisPoint>) : FeatureGeometry()
    data class MultiLineString(val lines: List<List<GisPoint>>) : FeatureGeometry()
    data class Polygon(val rings: List<List<GisPoint>>) : FeatureGeometry()
    data class MultiPolygon(val polygons: List<List<List<GisPoint>>>) : FeatureGeometry()
}

data class RenderableFeature(
    val entity: GeoFeatureEntity,
    val geometry: FeatureGeometry,
    val strokeColor: Color,
    val fillColor: Color,
    val strokeWidth: Float,
    val pointRadius: Float = 6f
)

object FeatureGeometryParser {

    fun parseEntity(entity: GeoFeatureEntity): RenderableFeature? {
        val geometry = parseGeometry(entity.geometryType, entity.coordinatesJson) ?: return null
        
        // Colors matching HydroGIS Ciamis cartographic style:
        val strokeColor: Color
        val fillColor: Color
        val strokeWidth: Float
        var pointRadius = 6f

        when (entity.source) {
            "sungai" -> {
                strokeColor = Color(0xFF2563EB) // Blue river
                fillColor = Color.Transparent
                strokeWidth = 3.0f
            }
            "batasDesa" -> {
                strokeColor = Color(0xFF64748B) // Slate border
                fillColor = Color(0x2894A3B8) // 16% opacity
                strokeWidth = 2.0f
            }
            "irigasi" -> {
                when (entity.geometryType) {
                    "Point", "MultiPoint" -> {
                        strokeColor = Color(0xFFFFFFFF)
                        fillColor = Color(0xFFEF4444) // Red marker
                        strokeWidth = 2.0f
                        pointRadius = 8f
                    }
                    "LineString", "MultiLineString" -> {
                        strokeColor = Color(0xFF06B6D4) // Cyan irrigation canal
                        fillColor = Color.Transparent
                        strokeWidth = 3.5f
                    }
                    else -> {
                        strokeColor = Color(0xFF065F46) // Dark green
                        fillColor = Color(0x4010B981) // Emerald green translucent
                        strokeWidth = 2.0f
                    }
                }
            }
            else -> {
                strokeColor = Color(0xFF475569)
                fillColor = Color(0x3064748B)
                strokeWidth = 2.0f
            }
        }

        return RenderableFeature(
            entity = entity,
            geometry = geometry,
            strokeColor = strokeColor,
            fillColor = fillColor,
            strokeWidth = strokeWidth,
            pointRadius = pointRadius
        )
    }

    private fun parsePoint(arr: JSONArray): GisPoint? {
        if (arr.length() < 2) return null
        return GisPoint(arr.getDouble(0), arr.getDouble(1))
    }

    private fun parseLine(arr: JSONArray): List<GisPoint> {
        val list = mutableListOf<GisPoint>()
        for (i in 0 until arr.length()) {
            val pArr = arr.optJSONArray(i) ?: continue
            parsePoint(pArr)?.let { list.add(it) }
        }
        return list
    }

    private fun parseRings(arr: JSONArray): List<List<GisPoint>> {
        val list = mutableListOf<List<GisPoint>>()
        for (i in 0 until arr.length()) {
            val ringArr = arr.optJSONArray(i) ?: continue
            val ring = parseLine(ringArr)
            if (ring.isNotEmpty()) list.add(ring)
        }
        return list
    }

    private fun parseGeometry(type: String, json: String): FeatureGeometry? {
        return try {
            val arr = JSONArray(json)
            when (type) {
                "Point" -> parsePoint(arr)?.let { FeatureGeometry.Point(it) }
                "MultiPoint" -> FeatureGeometry.MultiPoint(parseLine(arr))
                "LineString" -> FeatureGeometry.LineString(parseLine(arr))
                "MultiLineString" -> {
                    val lines = mutableListOf<List<GisPoint>>()
                    for (i in 0 until arr.length()) {
                        arr.optJSONArray(i)?.let { lines.add(parseLine(it)) }
                    }
                    FeatureGeometry.MultiLineString(lines)
                }
                "Polygon" -> FeatureGeometry.Polygon(parseRings(arr))
                "MultiPolygon" -> {
                    val polygons = mutableListOf<List<List<GisPoint>>>()
                    for (i in 0 until arr.length()) {
                        arr.optJSONArray(i)?.let { polygons.add(parseRings(it)) }
                    }
                    FeatureGeometry.MultiPolygon(polygons)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
