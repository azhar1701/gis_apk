package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.gis.GisPoint
import com.example.gis.MercatorProjection
import com.example.gis.SpatialAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("HydroGIS Ciamis", appName)
  }

  @Test
  fun `point in polygon calculation is accurate`() {
    val polygon = listOf(
      GisPoint(108.0, -7.0),
      GisPoint(109.0, -7.0),
      GisPoint(109.0, -8.0),
      GisPoint(108.0, -8.0),
      GisPoint(108.0, -7.0)
    )
    val insidePoint = GisPoint(108.5, -7.5)
    val outsidePoint = GisPoint(110.0, -7.5)

    assertTrue(SpatialAlgorithms.isPointInPolygon(insidePoint, polygon))
    assertTrue(!SpatialAlgorithms.isPointInPolygon(outsidePoint, polygon))
  }

  @Test
  fun `mercator projection converts accurately`() {
    val (wx, wy) = MercatorProjection.toWorld(0.0, 0.0)
    assertEquals(0.5, wx, 0.0001)
    assertEquals(0.5, wy, 0.0001)

    val (lng, lat) = MercatorProjection.fromWorld(0.5, 0.5)
    assertEquals(0.0, lng, 0.0001)
    assertEquals(0.0, lat, 0.0001)
  }
}

