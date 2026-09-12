package com.example.gis

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.*

enum class BasemapType(val title: String) {
    OPEN_STREET_MAP("OpenStreetMap"),
    SATELLITE("Google Hybrid (Satelit)")
}

class TileProvider(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // 64MB memory cache for quick tile rendering
    private val memoryCache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    init {
        // Register modern memory trim callback instead of deprecated ashmem pinning
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                when {
                    level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                        memoryCache.trimToSize(memoryCache.maxSize() / 2)
                    }
                    level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        memoryCache.evictAll()
                    }
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}

            override fun onLowMemory() {
                memoryCache.evictAll()
            }
        })
    }

    private val tileCacheDir = File(context.cacheDir, "gis_tiles").apply { mkdirs() }

    fun getTileUrl(type: BasemapType, x: Int, y: Int, z: Int): String {
        return when (type) {
            BasemapType.OPEN_STREET_MAP -> "https://tile.openstreetmap.org/$z/$x/$y.png"
            BasemapType.SATELLITE -> {
                val subdomains = listOf("mt0", "mt1", "mt2", "mt3")
                val s = subdomains[(x + y) % subdomains.size]
                "https://$s.google.com/vt/lyrs=y&x=$x&y=$y&z=$z"
            }
        }
    }

    suspend fun loadTile(type: BasemapType, x: Int, y: Int, z: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${type.name}_${z}_${x}_$y"
        
        // 1. Check memory cache
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // 2. Check disk cache
        val diskFile = File(tileCacheDir, "$cacheKey.img")
        if (diskFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                diskFile.delete()
            }
        }

        // 3. Fetch over network
        val url = getTileUrl(type, x, y, z)
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HydroGIS-Ciamis-Android/1.0 (Mobile App; GIS SDA Ciamis)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()?.let { bytes ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        memoryCache.put(cacheKey, bitmap)
                        try {
                            FileOutputStream(diskFile).use { it.write(bytes) }
                        } catch (_: Exception) {}
                        return@withContext bitmap
                    }
                }
            }
        } catch (e: Exception) {
            // Offline or timeout, return null gracefully
        }
        null
    }

    fun clearCache() {
        memoryCache.evictAll()
        tileCacheDir.deleteRecursively()
        tileCacheDir.mkdirs()
    }
}
