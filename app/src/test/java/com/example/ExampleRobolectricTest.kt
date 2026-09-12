package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.example.data.sync.SyncScheduler
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

  @Test
  fun `dark mode preference persists correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("hydro_gis_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("is_dark_mode", true).apply()
    assertTrue(prefs.getBoolean("is_dark_mode", false))

    prefs.edit().putBoolean("is_dark_mode", false).apply()
    assertTrue(!prefs.getBoolean("is_dark_mode", true))
  }

  @Test
  fun `workmanager constraints require charging and unmetered wifi`() {
    val constraints = SyncScheduler.buildChargingWifiConstraints()
    assertTrue("Harus memerlukan pengisian daya (charging)", constraints.requiresCharging())
    assertEquals("Harus memerlukan jaringan Wi-Fi (UNMETERED)", NetworkType.UNMETERED, constraints.requiredNetworkType)
    assertTrue("Harus memerlukan kapasitas penyimpanan aman", constraints.requiresStorageNotLow())
  }

  @Test
  fun `workmanager task scheduling can be enqueued`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    try {
      val config = androidx.work.Configuration.Builder().build()
      WorkManager.initialize(context, config)
    } catch (_: Exception) {}
    SyncScheduler.scheduleChargingWifiSync(context)
    val workInfos = WorkManager.getInstance(context)
      .getWorkInfosForUniqueWork(SyncScheduler.CHARGING_WIFI_PERIODIC_WORK)
      .get()
    assertTrue("Periodic WorkManager task harus terdaftar", workInfos.isNotEmpty())
  }
}

