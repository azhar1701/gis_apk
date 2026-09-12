package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "geo_features",
    indices = [
        Index(value = ["source"]),
        Index(value = ["name"]),
        Index(value = ["district"]),
        Index(value = ["village"]),
        Index(value = ["di"]),
        Index(value = ["minLng", "maxLng", "minLat", "maxLat"])
    ]
)
data class GeoFeatureEntity(
    @PrimaryKey
    val id: String, // e.g. "batasDesa:0", "sungai:12"
    val source: String, // "batasDesa", "sungai", "irigasi"
    val name: String,
    val code: String,
    val nomenclature: String,
    val district: String,
    val village: String,
    val di: String, // Daerah Irigasi
    val condition: String,
    val geometryType: String, // Point, LineString, Polygon, MultiPoint, MultiLineString, MultiPolygon
    val coordinatesJson: String, // Flattened or JSON geometry coordinates
    val propertiesJson: String, // Raw properties JSON
    val minLng: Double,
    val minLat: Double,
    val maxLng: Double,
    val maxLat: Double,
    val shapeArea: Double? = null,
    val shapeLength: Double? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
