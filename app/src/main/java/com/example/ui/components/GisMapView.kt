package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GeoFeatureEntity
import com.example.gis.*
import com.example.ui.MapCameraState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

@Composable
fun GisMapView(
    cameraState: MapCameraState,
    basemapType: BasemapType,
    features: List<RenderableFeature>,
    selectedFeature: GeoFeatureEntity?,
    targetMarker: GisPoint?,
    targetMarkerLabel: String,
    userLocation: GisPoint?,
    userAccuracy: Float?,
    onMapTapped: (Double, Double, Double) -> Unit,
    onCameraChange: (Double, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileProvider = remember { TileProvider(context) }
    val coroutineScope = rememberCoroutineScope()
    val fallbackTileBg = MaterialTheme.colorScheme.background

    // Smooth pulsing animation for selected feature
    val infiniteTransition = rememberInfiniteTransition(label = "highlight_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Cached map tiles map
    var tileBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // Load tiles whenever camera changes
    LaunchedEffect(cameraState.centerLng, cameraState.centerLat, cameraState.zoom, basemapType) {
        val zoomInt = cameraState.zoom.toInt().coerceIn(0, 19)
        val numTiles = 1 shl zoomInt
        val (worldX, worldY) = MercatorProjection.toWorld(cameraState.centerLng, cameraState.centerLat)
        val centerTileX = (worldX * numTiles).toInt().coerceIn(0, numTiles - 1)
        val centerTileY = (worldY * numTiles).toInt().coerceIn(0, numTiles - 1)

        val newTiles = mutableMapOf<String, Bitmap>()
        val radius = 2 // Load 5x5 tile window around screen center

        coroutineScope.launch {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val tx = (centerTileX + dx).coerceIn(0, numTiles - 1)
                    val ty = (centerTileY + dy).coerceIn(0, numTiles - 1)
                    val key = "${basemapType.name}_${zoomInt}_${tx}_$ty"
                    val bmp = tileProvider.loadTile(basemapType, tx, ty, zoomInt)
                    if (bmp != null) {
                        newTiles[key] = bmp
                    }
                }
            }
            tileBitmaps = newTiles
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val newZoom = (cameraState.zoom + (log2(zoomChange.toDouble()))).coerceIn(8.0, 19.0)
                        val scale = MercatorProjection.TILE_SIZE * 2.0.pow(cameraState.zoom)
                        val worldDx = -pan.x / scale
                        val worldDy = -pan.y / scale

                        val (curWorldX, curWorldY) = MercatorProjection.toWorld(cameraState.centerLng, cameraState.centerLat)
                        val (newLng, newLat) = MercatorProjection.fromWorld(
                            curWorldX + worldDx,
                            curWorldY + worldDy
                        )
                        onCameraChange(newLng, newLat, newZoom)
                    }
                }
                .pointerInput(cameraState) {
                    detectTapGestures(
                        onTap = { offset ->
                            val (tapLng, tapLat) = MercatorProjection.screenToLatLng(
                                offset.x, offset.y,
                                cameraState.centerLng, cameraState.centerLat, cameraState.zoom,
                                size.width.toFloat(), size.height.toFloat()
                            )
                            onMapTapped(tapLng, tapLat, cameraState.zoom)
                        },
                        onDoubleTap = { offset ->
                            val newZoom = (cameraState.zoom + 1.0).coerceAtMost(19.0)
                            val (tapLng, tapLat) = MercatorProjection.screenToLatLng(
                                offset.x, offset.y,
                                cameraState.centerLng, cameraState.centerLat, cameraState.zoom,
                                size.width.toFloat(), size.height.toFloat()
                            )
                            onCameraChange(tapLng, tapLat, newZoom)
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val zoom = cameraState.zoom
            val zoomInt = zoom.toInt().coerceIn(0, 19)
            val numTiles = 1 shl zoomInt

            // 1. Draw Basemap Tiles
            val tileSizeAtZoom = (MercatorProjection.TILE_SIZE * 2.0.pow(zoom - zoomInt)).toFloat()
            val (worldCenterX, worldCenterY) = MercatorProjection.toWorld(cameraState.centerLng, cameraState.centerLat)
            val centerTileExactX = worldCenterX * numTiles
            val centerTileExactY = worldCenterY * numTiles

            val centerTileX = centerTileExactX.toInt()
            val centerTileY = centerTileExactY.toInt()

            val radius = 3
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val tx = centerTileX + dx
                    val ty = centerTileY + dy
                    if (tx in 0 until numTiles && ty in 0 until numTiles) {
                        val key = "${basemapType.name}_${zoomInt}_${tx}_$ty"
                        val bitmap = tileBitmaps[key]

                        val screenTileX = (width / 2f + (tx - centerTileExactX) * tileSizeAtZoom).toFloat()
                        val screenTileY = (height / 2f + (ty - centerTileExactY) * tileSizeAtZoom).toFloat()

                        if (bitmap != null && !bitmap.isRecycled) {
                            drawImage(
                                image = bitmap.asImageBitmap(),
                                dstOffset = androidx.compose.ui.unit.IntOffset(screenTileX.toInt(), screenTileY.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(tileSizeAtZoom.toInt() + 1, tileSizeAtZoom.toInt() + 1)
                            )
                        } else {
                            // Fallback subtle tile grid background
                            drawRect(
                                color = if (basemapType == BasemapType.SATELLITE) Color(0xFF1E293B) else fallbackTileBg,
                                topLeft = Offset(screenTileX, screenTileY),
                                size = androidx.compose.ui.geometry.Size(tileSizeAtZoom, tileSizeAtZoom)
                            )
                        }
                    }
                }
            }

            // 2. Viewport Bounding Box for culling
            val screenBBox = MercatorProjection.getScreenBoundingBox(
                cameraState.centerLng, cameraState.centerLat, zoom, width, height
            )

            // 3. Draw Vector Features (Polygons first, then lines, then points)
            for (rf in features) {
                val entity = rf.entity
                val featBBox = GisBoundingBox(entity.minLng, entity.minLat, entity.maxLng, entity.maxLat)
                if (!screenBBox.intersects(featBBox)) continue

                val isSelected = selectedFeature?.id == entity.id
                val effectiveStrokeColor = if (isSelected) Color(0xFFF59E0B) else rf.strokeColor
                val effectiveStrokeWidth = if (isSelected) rf.strokeWidth * 2f else rf.strokeWidth
                val effectiveFillColor = if (isSelected) Color(0x60F59E0B) else rf.fillColor

                when (val geom = rf.geometry) {
                    is FeatureGeometry.Polygon -> {
                        drawPolygonRings(geom.rings, effectiveFillColor, effectiveStrokeColor, effectiveStrokeWidth, cameraState, width, height)
                    }
                    is FeatureGeometry.MultiPolygon -> {
                        for (rings in geom.polygons) {
                            drawPolygonRings(rings, effectiveFillColor, effectiveStrokeColor, effectiveStrokeWidth, cameraState, width, height)
                        }
                    }
                    is FeatureGeometry.LineString -> {
                        drawLineString(geom.points, effectiveStrokeColor, effectiveStrokeWidth, cameraState, width, height)
                    }
                    is FeatureGeometry.MultiLineString -> {
                        for (line in geom.lines) {
                            drawLineString(line, effectiveStrokeColor, effectiveStrokeWidth, cameraState, width, height)
                        }
                    }
                    is FeatureGeometry.Point -> {
                        val (px, py) = MercatorProjection.latLngToScreen(
                            geom.point.lng, geom.point.lat,
                            cameraState.centerLng, cameraState.centerLat, zoom, width, height
                        )
                        drawPointMarker(px, py, rf.pointRadius, effectiveFillColor, effectiveStrokeColor, isSelected, pulseAlpha)
                    }
                    is FeatureGeometry.MultiPoint -> {
                        for (pt in geom.points) {
                            val (px, py) = MercatorProjection.latLngToScreen(
                                pt.lng, pt.lat,
                                cameraState.centerLng, cameraState.centerLat, zoom, width, height
                            )
                            drawPointMarker(px, py, rf.pointRadius, effectiveFillColor, effectiveStrokeColor, isSelected, pulseAlpha)
                        }
                    }
                }
            }

            // 4. Draw Highlight Glow on Selected Feature if any
            selectedFeature?.let { sel ->
                // Draw pulsating selection beacon
                val centerLng = (sel.minLng + sel.maxLng) / 2.0
                val centerLat = (sel.minLat + sel.maxLat) / 2.0
                val (sx, sy) = MercatorProjection.latLngToScreen(
                    centerLng, centerLat,
                    cameraState.centerLng, cameraState.centerLat, zoom, width, height
                )
                drawCircle(
                    color = Color(0xFFF59E0B).copy(alpha = pulseAlpha * 0.4f),
                    radius = 28f * pulseAlpha,
                    center = Offset(sx, sy)
                )
            }

            // 5. Draw User GPS Location
            userLocation?.let { loc ->
                val (ux, uy) = MercatorProjection.latLngToScreen(
                    loc.lng, loc.lat,
                    cameraState.centerLng, cameraState.centerLat, zoom, width, height
                )
                val accuracy = userAccuracy ?: 15f
                val scale = MercatorProjection.TILE_SIZE * 2.0.pow(zoom)
                val accuracyPx = ((accuracy / 111319.5) * (scale / 360.0)).toFloat().coerceIn(12f, 200f)

                // Accuracy translucent circle
                drawCircle(
                    color = Color(0x303B82F6),
                    radius = accuracyPx,
                    center = Offset(ux, uy)
                )
                // GPS White border
                drawCircle(
                    color = Color.White,
                    radius = 9f,
                    center = Offset(ux, uy)
                )
                // GPS Inner blue dot
                drawCircle(
                    color = Color(0xFF2563EB),
                    radius = 6.5f,
                    center = Offset(ux, uy)
                )
            }

            // 6. Draw Target Marker (Custom coordinates or photo EXIF)
            targetMarker?.let { pin ->
                val (px, py) = MercatorProjection.latLngToScreen(
                    pin.lng, pin.lat,
                    cameraState.centerLng, cameraState.centerLat, zoom, width, height
                )
                // Pin head
                drawCircle(
                    color = Color(0xFF9333EA),
                    radius = 12f,
                    center = Offset(px, py - 18f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = Offset(px, py - 18f)
                )
                // Pin bottom pointer
                val path = Path().apply {
                    moveTo(px - 7f, py - 14f)
                    lineTo(px + 7f, py - 14f)
                    lineTo(px, py)
                    close()
                }
                drawPath(path, Color(0xFF9333EA))
            }
        }

        // Scale bar overlay in bottom-left
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 28.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shadowElevation = 2.dp
        ) {
            val scaleMeters = calculateScaleBarMeters(cameraState.zoom, cameraState.centerLat)
            Text(
                text = formatDistance(scaleMeters),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun DrawScope.drawPointMarker(
    x: Float,
    y: Float,
    radius: Float,
    fillColor: Color,
    strokeColor: Color,
    isSelected: Boolean,
    pulseAlpha: Float
) {
    if (isSelected) {
        drawCircle(
            color = Color(0xFFFBBF24).copy(alpha = pulseAlpha),
            radius = radius * 2.2f,
            center = Offset(x, y)
        )
    }
    drawCircle(
        color = strokeColor,
        radius = radius + 1.5f,
        center = Offset(x, y)
    )
    drawCircle(
        color = fillColor,
        radius = radius,
        center = Offset(x, y)
    )
}

private fun DrawScope.drawLineString(
    points: List<GisPoint>,
    color: Color,
    strokeWidth: Float,
    cameraState: MapCameraState,
    screenWidth: Float,
    screenHeight: Float
) {
    if (points.size < 2) return
    val path = Path()
    var isFirst = true

    for (pt in points) {
        val (x, y) = MercatorProjection.latLngToScreen(
            pt.lng, pt.lat,
            cameraState.centerLng, cameraState.centerLat, cameraState.zoom,
            screenWidth, screenHeight
        )
        if (isFirst) {
            path.moveTo(x, y)
            isFirst = false
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawPolygonRings(
    rings: List<List<GisPoint>>,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float,
    cameraState: MapCameraState,
    screenWidth: Float,
    screenHeight: Float
) {
    if (rings.isEmpty() || rings[0].size < 3) return
    val path = Path()

    for (ring in rings) {
        if (ring.size < 3) continue
        var isFirst = true
        for (pt in ring) {
            val (x, y) = MercatorProjection.latLngToScreen(
                pt.lng, pt.lat,
                cameraState.centerLng, cameraState.centerLat, cameraState.zoom,
                screenWidth, screenHeight
            )
            if (isFirst) {
                path.moveTo(x, y)
                isFirst = false
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
    }

    if (fillColor != Color.Transparent) {
        drawPath(path = path, color = fillColor)
    }
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
    )
}

private fun calculateScaleBarMeters(zoom: Double, lat: Double): Double {
    val groundResolution = 156543.03392 * cos(lat * PI / 180.0) / 2.0.pow(zoom)
    return groundResolution * 80.0 // 80 pixels bar
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
    } else {
        String.format(java.util.Locale.US, "%.0f m", meters)
    }
}
