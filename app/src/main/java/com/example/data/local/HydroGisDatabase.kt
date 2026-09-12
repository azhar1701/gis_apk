package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.GeoFeatureDao
import com.example.data.local.dao.InspectionDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.entity.GeoFeatureEntity
import com.example.data.local.entity.InspectionEntity
import com.example.data.local.entity.SyncMetadataEntity

@Database(
    entities = [
        GeoFeatureEntity::class,
        SyncMetadataEntity::class,
        InspectionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HydroGisDatabase : RoomDatabase() {

    abstract fun geoFeatureDao(): GeoFeatureDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun inspectionDao(): InspectionDao

    companion object {
        @Volatile
        private var INSTANCE: HydroGisDatabase? = null

        fun getInstance(context: Context): HydroGisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HydroGisDatabase::class.java,
                    "hydrogis_ciamis.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
