package com.example.data.remote

import com.example.data.local.entity.GeoFeatureEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

object GeoJsonParser {

    fun parseFeatureCollection(sourceKey: String, jsonString: String): List<GeoFeatureEntity> {
        val root = JSONObject(jsonString)
        val featuresArray = root.optJSONArray("features") ?: JSONArray()
        val entities = ArrayList<GeoFeatureEntity>(featuresArray.length())

        for (i in 0 until featuresArray.length()) {
            val featureObj = featuresArray.optJSONObject(i) ?: continue
            val geometryObj = featureObj.optJSONObject("geometry") ?: continue
            val geomType = geometryObj.optString("type", "")
            val coordsArray = geometryObj.optJSONArray("coordinates") ?: continue
            val propsObj = featureObj.optJSONObject("properties") ?: JSONObject()

            // Calculate Bounding Box and check valid coords
            var minLng = Double.MAX_VALUE
            var minLat = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE

            fun scanCoords(arr: JSONArray) {
                if (arr.length() == 0) return
                if (arr.get(0) is Number) {
                    if (arr.length() >= 2) {
                        val lng = arr.getDouble(0)
                        val lat = arr.getDouble(1)
                        if (lng in -180.0..180.0 && lat in -90.0..90.0) {
                            minLng = min(minLng, lng)
                            minLat = min(minLat, lat)
                            maxLng = max(maxLng, lng)
                            maxLat = max(maxLat, lat)
                        }
                    }
                } else {
                    for (k in 0 until arr.length()) {
                        val sub = arr.optJSONArray(k)
                        if (sub != null) scanCoords(sub)
                    }
                }
            }

            scanCoords(coordsArray)

            // If bounding box was not valid, skip or default
            if (minLng > maxLng || minLat > maxLat) {
                minLng = 108.35
                minLat = -7.32
                maxLng = 108.35
                maxLat = -7.32
            }

            // Extract core fields based on source attributes
            val name = firstNonEmpty(
                propsObj,
                "NAMOBJ", "namobj", "NAMA", "nama", "WADMKD", "wadmkd"
            ) ?: "Tanpa Nama"

            val code = firstNonEmpty(propsObj, "n_aset", "N_ASET", "kode", "KODE") ?: ""
            val nomenclature = firstNonEmpty(propsObj, "nomenklatur", "NOMENKLATUR") ?: ""
            val district = firstNonEmpty(propsObj, "WADMKC", "wadmkc", "kecamatan", "KECAMATAN") ?: ""
            val village = firstNonEmpty(propsObj, "WADMKD", "wadmkd", "desa", "DESA", "kelurahan", "KELURAHAN") ?: ""
            val di = firstNonEmpty(propsObj, "n_di", "N_DI") ?: ""
            val condition = firstNonEmpty(propsObj, "kondisi", "KONDISI") ?: ""

            val shapeArea = propsObj.optDouble("SHAPE_Area", Double.NaN).takeIf { !it.isNaN() }
            val shapeLength = propsObj.optDouble("PANJANG", Double.NaN).takeIf { !it.isNaN() }

            val featureId = featureObj.optString("id", "").ifEmpty {
                "$sourceKey:$i"
            }

            entities.add(
                GeoFeatureEntity(
                    id = featureId,
                    source = sourceKey,
                    name = name,
                    code = code,
                    nomenclature = nomenclature,
                    district = district,
                    village = village,
                    di = di,
                    condition = condition,
                    geometryType = geomType,
                    coordinatesJson = coordsArray.toString(),
                    propertiesJson = propsObj.toString(),
                    minLng = minLng,
                    minLat = minLat,
                    maxLng = maxLng,
                    maxLat = maxLat,
                    shapeArea = shapeArea,
                    shapeLength = shapeLength
                )
            )
        }

        return entities
    }

    private fun firstNonEmpty(json: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (json.has(key)) {
                val value = json.optString(key, "").trim()
                if (value.isNotEmpty() && value != "null") return value
            }
        }
        return null
    }
}
