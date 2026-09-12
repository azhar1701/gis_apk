package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SourceDefinition(
    val key: String,
    val displayName: String,
    val url: String
)

object GisSources {
    val BATAS_DESA = SourceDefinition(
        key = "batasDesa",
        displayName = "Batas Administrasi",
        url = "https://raw.githubusercontent.com/azhar1701/gis_ciamis/main/admin_ciamis.geojson"
    )

    val SUNGAI = SourceDefinition(
        key = "sungai",
        displayName = "Jaringan Sungai",
        url = "https://raw.githubusercontent.com/azhar1701/gis_ciamis/main/sungai_cms.json"
    )

    val IRIGASI = SourceDefinition(
        key = "irigasi",
        displayName = "Daerah Irigasi",
        url = "https://raw.githubusercontent.com/azhar1701/gis_ciamis/main/irigasi_ciamis.geojson"
    )

    val ALL = listOf(BATAS_DESA, SUNGAI, IRIGASI)
}

class GeoJsonService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchGeoJson(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "HydroGIS-Ciamis-Android/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }
        val body = response.body?.string() ?: throw Exception("Respons server kosong")
        body
    }
}
