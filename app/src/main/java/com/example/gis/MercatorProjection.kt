package com.example.gis

import kotlin.math.*

object MercatorProjection {
    const val TILE_SIZE = 256.0

    /**
     * Projects longitude/latitude to normalized world coordinate [0.0 .. 1.0]
     */
    fun toWorld(lng: Double, lat: Double): Pair<Double, Double> {
        val x = (lng + 180.0) / 360.0
        val sinLat = sin(lat * PI / 180.0).coerceIn(-0.9999, 0.9999)
        val y = 0.5 - log((1.0 + sinLat) / (1.0 - sinLat), E) / (4.0 * PI)
        return Pair(x, y)
    }

    /**
     * Converts normalized world coordinate [0.0 .. 1.0] back to longitude/latitude
     */
    fun fromWorld(x: Double, y: Double): Pair<Double, Double> {
        val lng = x * 360.0 - 180.0
        val n = PI - 2.0 * PI * y
        val lat = 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
        return Pair(lng, lat)
    }

    /**
     * Converts LatLng to Screen (pixel) position relative to a map center, zoom level, and screen size.
     */
    fun latLngToScreen(
        lng: Double,
        lat: Double,
        centerLng: Double,
        centerLat: Double,
        zoom: Double,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> {
        val scale = TILE_SIZE * 2.0.pow(zoom)
        val (targetX, targetY) = toWorld(lng, lat)
        val (centerX, centerY) = toWorld(centerLng, centerLat)

        val px = ((targetX - centerX) * scale + screenWidth / 2.0).toFloat()
        val py = ((targetY - centerY) * scale + screenHeight / 2.0).toFloat()
        return Pair(px, py)
    }

    /**
     * Converts Screen (pixel) position back to LatLng.
     */
    fun screenToLatLng(
        screenX: Float,
        screenY: Float,
        centerLng: Double,
        centerLat: Double,
        zoom: Double,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Double, Double> {
        val scale = TILE_SIZE * 2.0.pow(zoom)
        val (centerX, centerY) = toWorld(centerLng, centerLat)

        val worldX = (screenX - screenWidth / 2.0) / scale + centerX
        val worldY = (screenY - screenHeight / 2.0) / scale + centerY
        return fromWorld(worldX, worldY)
    }

    /**
     * Calculates the bounding box in LatLng visible on screen.
     */
    fun getScreenBoundingBox(
        centerLng: Double,
        centerLat: Double,
        zoom: Double,
        screenWidth: Float,
        screenHeight: Float
    ): GisBoundingBox {
        val (minLng, maxLat) = screenToLatLng(0f, 0f, centerLng, centerLat, zoom, screenWidth, screenHeight)
        val (maxLng, minLat) = screenToLatLng(screenWidth, screenHeight, centerLng, centerLat, zoom, screenWidth, screenHeight)
        return GisBoundingBox(
            minLng = min(minLng, maxLng),
            minLat = min(minLat, maxLat),
            maxLng = max(minLng, maxLng),
            maxLat = max(minLat, maxLat)
        )
    }
}
