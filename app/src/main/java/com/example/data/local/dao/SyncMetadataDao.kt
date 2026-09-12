package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.InspectionEntity
import com.example.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata")
    fun getAllMetadata(): Flow<List<SyncMetadataEntity>>

    @Query("SELECT * FROM sync_metadata WHERE sourceKey = :key LIMIT 1")
    suspend fun getByKey(key: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<SyncMetadataEntity>)

    @Query("UPDATE sync_metadata SET status = :status, errorMessage = :error WHERE sourceKey = :key")
    suspend fun updateStatus(key: String, status: String, error: String?)
}

@Dao
interface InspectionDao {

    @Query("SELECT * FROM inspections ORDER BY createdAt DESC")
    fun getAllInspections(): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections ORDER BY createdAt DESC")
    suspend fun getAllInspectionList(): List<InspectionEntity>

    @Query("SELECT * FROM inspections WHERE featureId = :featureId ORDER BY createdAt DESC")
    fun getInspectionsForFeature(featureId: String): Flow<List<InspectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inspection: InspectionEntity)

    @Query("DELETE FROM inspections WHERE id = :id")
    suspend fun delete(id: String)
}
