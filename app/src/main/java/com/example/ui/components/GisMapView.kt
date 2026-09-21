package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GeoFeatureEntity
import com.example.gis.*
import com.example.ui.MapCameraState
import kotlinx.coroutines.*
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
    val tileProvider = remember { TileProvider.getInstance(context) }
    val fallbackTileBg = MaterialTheme.colorScheme.background

    // Track latest states in long-lived coroutines to prevent stale closures
    val currentCameraState by rememberUpdatedState(cameraState)
    val currentOnCameraChange by rememberUpdatedState(onCameraChange)
    val currentOnMapTapped by rememberUpdatedState(onMapTapped)

    // Reusable Path instance to achieve ZERO GC allocation during drawing frames
    val reusablePath = remember { Path() }

    // Persistent in-memory tile cache across camera changes to avoid screen flicker
    val tileBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    // Debounced, concurrent background tile fetching
    LaunchedEffect(cameraState.centerLng, cameraState.centerLat, cameraState.zoom, basemapType) {
        // Debounce micro-gestures to prevent hammering I/O on rapid pan/zoom
        delay(60)

        val zoomInt = cameraState.zoom.toInt().coerceIn(0, 19)
        val numTiles = 1 shl zoomInt
        val (worldX, worldY) = MercatorProjection.toWorld(cameraState.centerLng, cameraState.centerLat)
        val centerTileX = (worldX * numTiles).toInt().coerceIn(0, numTiles - 1)
        val centerTileY = (worldY * numTiles).toInt().coerceIn(0, numTiles - 1)

        val radius = 2 // 5x5 viewport tile grid
        withContext(Dispatchers.IO) {
            coroutineScope {
                val jobs = mutableListOf<Deferred<Pair<String, Bitmap>?>>()

                for (dx in -radius..radius) {
                    for (dy in -radius..radius) {
                        val tx = (centerTileX + dx).coerceIn(0, numTiles - 1)
                        val ty = (centerTileY + dy).coerceIn(0, numTiles - 1)
                        val key = "${basemapType.name}_${zoomInt}_${tx}_$ty"

                        if (tileBitmaps.containsKey(key)) continue

                        // Immediate fast memory cache hit
                        val memTile = tileProvider.getTileFromMemory(basemapType, tx, ty, zoomInt)
                        if (memTile != null) {
                            withContext(Dispatchers.Main) {
                                tileBitmaps[key] = memTile
                            }
                            continue
                        }

                        // Asynchronous concurrent fetch
                        jobs.add(async {
                            val bmp = tileProvider.loadTile(basemapType, tx, ty, zoomInt)
                            if (bmp != null) key to bmp else null
                        })
                    }
                }

                val loaded = jobs.awaitAll().filterNotNull()
                if (loaded.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        for ((k, v) in loaded) {
                            tileBitmaps[k] = v
                        }
                        // Prune excess cached tiles to keep memory footprint bounded
                        if (tileBitmaps.size > 80) {
                            val removeCount = tileBitmaps.size - 50
                            tileBitmaps.keys.take(removeCount).forEach { tileBitmaps.remove(it) }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Primary Vector & Tile Map Canvas (Rendered only on state/camera updates, ZERO infinite loop redraws)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var lastTapTime = 0L
                    var lastTapPos = Offset.Zero

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val startPos = down.position
                        val touchSlop = viewConfiguration.touchSlop

                        var pastTouchSlop = false
                        var isDragOrPinch = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (canceled) break

                            val activePointers = event.changes.filter { it.pressed }
                            if (activePointers.isEmpty()) {
                                // All touches released
                                if (!isDragOrPinch) {
                                    val elapsed = System.currentTimeMillis() - downTime
                                    if (elapsed < 350) {
                                        val cam = currentCameraState
                                        val now = System.currentTimeMillis()
                                        val distFromLast = (startPos - lastTapPos).getDistance()

                                        if (now - lastTapTime < 350L && distFromLast < touchSlop * 2.5f) {
                                            // Double-tap detected: Zoom in at tap position
                                            lastTapTime = 0L
                                            val newZoom = (cam.zoom + 1.0).coerceAtMost(19.0)
                                            val (tapLng, tapLat) = MercatorProjection.screenToLatLng(
                                                startPos.x, startPos.y,
                                                cam.centerLng, cam.centerLat, cam.zoom,
                                                size.width.toFloat(), size.height.toFloat()
                                            )
                                            currentOnCameraChange(tapLng, tapLat, newZoom)
                                        } else {
                                            // Single-tap detected: Select feature at tap position
                                            lastTapTime = now
                                            lastTapPos = startPos
                                            val (tapLng, tapLat) = MercatorProjection.screenToLatLng(
                                                startPos.x, startPos.y,
                                                cam.centerLng, cam.centerLat, cam.zoom,
                                                size.width.toFloat(), size.height.toFloat()
                                            )
                                            currentOnMapTapped(tapLng, tapLat, cam.zoom)
                                        }
                                    }
                                }
                                break
                            }

                            if (activePointers.size > 1) {
                                isDragOrPinch = true
                                pastTouchSlop = true
                            }

                            val panChange = event.calculatePan()
                            val zoomChange = event.calculateZoom()

                            if (!pastTouchSlop) {
                                val dist = (activePointers[0].position - startPos).getDistance()
                                if (dist > touchSlop) {
                                    pastTouchSlop = true
                                    isDragOrPinch = true
                                }
                            }

                            if (pastTouchSlop) {
                                val cam = currentCameraState
                                val newZoom = if (zoomChange != 1.0f) {
                                    (cam.zoom + log2(zoomChange.toDouble())).coerceIn(8.0, 19.0)
                                } else {
                                    cam.zoom
                                }
                                val scale = MercatorProjection.TILE_SIZE * 2.0.pow(cam.zoom)
                                val worldDx = -panChange.x / scale
                                val worldDy = -panChange.y / scale

                                val (curWorldX, curWorldY) = MercatorProjection.toWorld(cam.centerLng, cam.centerLat)
                                val (newLng, newLat) = MercatorProjection.fromWorld(
                                    curWorldX + worldDx,
                                    curWorldY + worldDy
                                )
                                event.changes.forEach { it.consume() }
                                currentOnCameraChange(newLng, newLat, newZoom)
                            }
                        }
                    }
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

            val fracX = (centerTileExactX - centerTileX).toFloat()
            val fracY = (centerTileExactY - centerTileY).toFloat()

            val originScreenX = (width / 2f) - (fracX * tileSizeAtZoom)
            val originScreenY = (height / 2f) - (fracY * tileSizeAtZoom)

            val radius = 2
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val tx = (centerTileX + dx).coerceIn(0, numTiles - 1)
                    val ty = (centerTileY + dy).coerceIn(0, numTiles - 1)
                    val key = "${basemapType.name}_${zoomInt}_${tx}_$ty"
                    val bmp = tileBitmaps[key] ?: tileProvider.getTileFromMemory(basemapType, tx, ty, zoomInt)

                    val tileLeft = originScreenX + (dx * tileSizeAtZoom)
                    val tileTop = originScreenY + (dy * tileSizeAtZoom)

                    if (tileLeft + tileSizeAtZoom < 0 || tileLeft > width ||
                        tileTop + tileSizeAtZoom < 0 || tileTop > height) {
                        continue
                    }

                    if (bmp != null && !bmp.isRecycled) {
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(tileLeft.toInt(), tileTop.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                ceil(tileSizeAtZoom).toInt() + 1,
                                ceil(tileSizeAtZoom).toInt() + 1
                            )
                        )
                    } else {
                        drawRect(
                            color = fallbackTileBg,
                            topLeft = Offset(tileLeft, tileTop),
                            size = androidx.compose.ui.geometry.Size(tileSizeAtZoom, tileSizeAtZoom)
                        )
                    }
                }
            }

            // 2. Viewport Bounding Box for Culling
            val screenBBox = MercatorProjection.getScreenBoundingBox(
                cameraState.centerLng, cameraState.centerLat, zoom, width, height
            )
            val scale = MercatorProjection.TILE_SIZE * 2.0.pow(zoom)

            // 3. Draw Vector Features with LOD Culling and Zero-Allocation Path Reuse
            for (rf in features) {
                val entity = rf.entity
                val featBBox = GisBoundingBox(entity.minLng, entity.minLat, entity.maxLng, entity.maxLat)
                if (!screenBBox.intersects(featBBox)) continue

                // Level-of-Detail (LOD): Skip sub-pixel geometry calculations at far zoom
                val approxW = (entity.maxLng - entity.minLng) * (scale / 360.0)
                val approxH = (entity.maxLat - entity.minLat) * (scale / 360.0)
                if (approxW < 1.2 && approxH < 1.2 && entity.geometryType != "Point") {
                    continue
                }

                val isSelected = selectedFeature?.id == entity.id
                val effectiveStrokeColor = if (isSelected) Color(0xFFF59E0B) else rf.strokeColor
                val effectiveStrokeWidth = if (isSelected) rf.strokeWidth * 2.2f else rf.strokeWidth
                val effectiveFillColor = if (isSelected) Color(0x60F59E0B) else rf.fillColor

                when (val geom = rf.geometry) {
                    is FeatureGeometry.Polygon -> {
                        drawPolygonRings(geom.rings, effectiveFillColor, effectiveStrokeColor, effectiveStrokeWidth, cameraState, width, height, reusablePath)
                    }
                    is FeatureGeometry.MultiPolygon -> {
                        for (rings in geom.polygons) {
                            drawPolygonRings(rings, effectiveFillColor, effectiveStrokeColor, effectiveStrokeWidth, cameraState, width, height, reusablePath)
                        }
                    }
                    is FeatureGeometry.LineString -> {
                        drawLineString(geom.points, effectiveStrokeColor, effectiveStrokeWidth, cameraState, width, height, reusablePath)
                    }
                    is FeatureGeometry.MultiLineString -> {
                        for (line in geom.lines) {
                            drawLineString(line, effectiveStrokeColor, effectiveStrokeWidth, cameraState, width, height, reusablePath)
                        }
                    }
                    is FeatureGeometry.Point -> {
                        val (px, py) = MercatorProjection.latLngToScreen(
                            geom.point.lng, geom.point.lat,
                            cameraState.centerLng, cameraState.centerLat, zoom, width, height
                        )
                        drawPointMarker(px, py, rf.pointRadius, effectiveFillColor, effectiveStrokeColor, isSelected)
                    }
                    is FeatureGeometry.MultiPoint -> {
                        for (pt in geom.points) {
                            val (px, py) = MercatorProjection.latLngToScreen(
                                pt.lng, pt.lat,
                                cameraState.centerLng, cameraState.centerLat, zoom, width, height
                            )
                            drawPointMarker(px, py, rf.pointRadius, effectiveFillColor, effectiveStrokeColor, isSelected)
                        }
                    }
                }
            }

            // 4. Draw User GPS Location
            userLocation?.let { loc ->
                val (ux, uy) = MercatorProjection.latLngToScreen(
                    loc.lng, loc.lat,
                    cameraState.centerLng, cameraState.centerLat, zoom, width, height
                )
                val accuracy = userAccuracy ?: 15f
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

            // 5. Draw Target Marker (Custom coordinates or photo EXIF)
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
                // Pin bottom pointer using recycled path
                reusablePath.reset()
                reusablePath.moveTo(px - 7f, py - 14f)
                reusablePath.lineTo(px + 7f, py - 14f)
                reusablePath.lineTo(px, py)
                reusablePath.close()
                drawPath(reusablePath, Color(0xFF9333EA))
            }
        }

        // 2. Isolated Lightweight Overlay for Selected Feature Pulse Animation
        // Only active when a feature is selected; does NOT cause the heavy main Canvas to re-evaluate!
        if (selectedFeature != null) {
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

            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerLng = (selectedFeature.minLng + selectedFeature.maxLng) / 2.0
                val centerLat = (selectedFeature.minLat + selectedFeature.maxLat) / 2.0
                val (sx, sy) = MercatorProjection.latLngToScreen(
                    centerLng, centerLat,
                    cameraState.centerLng, cameraState.centerLat, cameraState.zoom, size.width, size.height
                )
                if (sx >= -40f && sx <= size.width + 40f && sy >= -40f && sy <= size.height + 40f) {
                    drawCircle(
                        color = Color(0xFFF59E0B).copy(alpha = pulseAlpha * 0.45f),
                        radius = 28f * pulseAlpha,
                        center = Offset(sx, sy)
                    )
                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = 6f,
                        center = Offset(sx, sy)
                    )
                }
            }
        }

        // 3. Scale bar overlay in bottom-left
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
    isSelected: Boolean
) {
    if (isSelected) {
        drawCircle(
            color = Color(0xFFFBBF24),
            radius = radius * 1.8f,
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
    screenHeight: Float,
    path: Path
) {
    if (points.size < 2) return
    path.reset()
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
    screenHeight: Float,
    path: Path
) {
    if (rings.isEmpty() || rings[0].size < 3) return
    path.reset()

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
