package com.example.gis

import kotlin.math.*

data class GisPoint(val lng: Double, val lat: Double)

data class GisBoundingBox(
    val minLng: Double,
    val minLat: Double,
    val maxLng: Double,
    val maxLat: Double
) {
    fun intersects(other: GisBoundingBox): Boolean {
        return !(other.minLng > maxLng || other.maxLng < minLng || other.minLat > maxLat || other.maxLat < minLat)
    }

    fun contains(p: GisPoint): Boolean {
        return p.lng in minLng..maxLng && p.lat in minLat..maxLat
    }
}

object SpatialAlgorithms {

    /**
     * Point in Polygon test using Ray Casting algorithm.
     */
    fun isPointInPolygon(point: GisPoint, ring: List<GisPoint>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val pi = ring[i]
            val pj = ring[j]
            if ((pi.lat > point.lat) != (pj.lat > point.lat) &&
                point.lng < (pj.lng - pi.lng) * (point.lat - pi.lat) / (pj.lat - pi.lat) + pi.lng
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Minimum distance from point to a line segment in degrees.
     */
    fun pointToSegmentDistance(p: GisPoint, a: GisPoint, b: GisPoint): Double {
        val dx = b.lng - a.lng
        val dy = b.lat - a.lat
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) {
            return hypot(p.lng - a.lng, p.lat - a.lat)
        }
        val t = max(0.0, min(1.0, ((p.lng - a.lng) * dx + (p.lat - a.lat) * dy) / lenSq))
        val projX = a.lng + t * dx
        val projY = a.lat + t * dy
        return hypot(p.lng - projX, p.lat - projY)
    }

    /**
     * Minimum distance from point to a polyline (list of points) in degrees.
     */
    fun pointToPolylineDistance(p: GisPoint, line: List<GisPoint>): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        if (line.size == 1) return hypot(p.lng - line[0].lng, p.lat - line[0].lat)
        var minDistance = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val dist = pointToSegmentDistance(p, line[i], line[i + 1])
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }
}
