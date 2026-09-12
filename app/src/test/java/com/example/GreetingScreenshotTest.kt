package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.local.entity.GeoFeatureEntity
import com.example.ui.components.FeatureDetailSheet
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun feature_detail_screenshot() {
    val sampleFeature = GeoFeatureEntity(
      id = "irigasi_test_1",
      source = "irigasi",
      geometryType = "Polygon",
      name = "D.I. Cikunten",
      code = "DI-001",
      nomenclature = "Saluran Induk",
      village = "Baregbeg",
      district = "Baregbeg",
      di = "Cikunten",
      condition = "baik",
      shapeLength = 2450.5,
      shapeArea = 120000.0,
      minLng = 108.3,
      minLat = -7.35,
      maxLng = 108.4,
      maxLat = -7.3,
      propertiesJson = "{\"NAMA\":\"D.I. Cikunten\",\"STATUS\":\"Aktif\"}",
      geometryJson = "{}"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        FeatureDetailSheet(
          feature = sampleFeature,
          onFocusFeature = {},
          onClose = {},
          onAddInspection = { _, _ -> }
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

