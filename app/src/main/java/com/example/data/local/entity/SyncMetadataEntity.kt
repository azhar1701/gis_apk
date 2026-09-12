package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val sourceKey: String, // "batasDesa", "sungai", "irigasi"
    val displayName: String,
    val url: String,
    val lastSyncTime: Long = 0L,
    val featureCount: Int = 0,
    val status: String = "IDLE", // IDLE, SYNCING, SUCCESS, ERROR
    val errorMessage: String? = null,
    val fileSizeFormatted: String = ""
)

@Entity(tableName = "inspections")
data class InspectionEntity(
    @PrimaryKey
    val id: String,
    val assetId: String,
    val featureId: String,
    val observedAt: String,
    val condition: String, // "baik", "rusak_ringan", "rusak_sedang", "rusak_berat", "tidak_dinilai"
    val verificationStatus: String = "terverifikasi",
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
