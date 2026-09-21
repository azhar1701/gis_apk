package com.example

import com.example.gis.GisBoundingBox
import com.example.gis.GisPoint
import com.example.gis.SpatialAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testBoundingBoxIntersects() {
    val box1 = GisBoundingBox(108.0, -7.5, 108.5, -7.0)
    val box2 = GisBoundingBox(108.4, -7.3, 108.8, -6.8)
    val box3 = GisBoundingBox(109.0, -7.5, 109.5, -7.0)

    assertTrue("Box1 dan Box2 harus saling berpotongan (intersect)", box1.intersects(box2))
    assertFalse("Box1 dan Box3 tidak boleh saling berpotongan", box1.intersects(box3))
  }

  @Test
  fun testBoundingBoxContains() {
    val box = GisBoundingBox(108.0, -7.5, 108.5, -7.0)
    val inside = GisPoint(108.2, -7.2)
    val outside = GisPoint(108.6, -7.2)

    assertTrue(box.contains(inside))
    assertFalse(box.contains(outside))
  }

  @Test
  fun testPointToSegmentDistance() {
    val p = GisPoint(0.0, 1.0)
    val a = GisPoint(-1.0, 0.0)
    val b = GisPoint(1.0, 0.0)

    val dist = SpatialAlgorithms.pointToSegmentDistance(p, a, b)
    assertEquals(1.0, dist, 0.0001)
  }

  @Test
  fun testPointInPolygonConvexAndConcave() {
    // L-shaped polygon
    val lPolygon = listOf(
      GisPoint(0.0, 0.0),
      GisPoint(2.0, 0.0),
      GisPoint(2.0, 1.0),
      GisPoint(1.0, 1.0),
      GisPoint(1.0, 2.0),
      GisPoint(0.0, 2.0),
      GisPoint(0.0, 0.0)
    )

    val inside1 = GisPoint(0.5, 0.5)
    val inside2 = GisPoint(0.5, 1.5)
    val outsideHole = GisPoint(1.5, 1.5)

    assertTrue(SpatialAlgorithms.isPointInPolygon(inside1, lPolygon))
    assertTrue(SpatialAlgorithms.isPointInPolygon(inside2, lPolygon))
    assertFalse(SpatialAlgorithms.isPointInPolygon(outsideHole, lPolygon))
  }
}
