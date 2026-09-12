package com.example.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.GisRepository
import java.util.concurrent.TimeUnit

class GisSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repository = GisRepository(applicationContext)
            // Sync all layers in background
            val results = repository.syncAll()
            val allSuccess = results.values.all { it.isSuccess }
            if (allSuccess) {
                Result.success()
            } else {
                // Retry later with backoff if network failed
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object SyncScheduler {
    private const val PERIODIC_SYNC_TAG = "hydrogis_periodic_sync"

    /**
     * Schedules periodic background sync with battery-safe and network constraints.
     * Ensures zero battery waste when battery is low.
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true) // Hemat baterai: tidak berjalan jika baterai kritis
            .setRequiresStorageNotLow(true)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<GisSyncWorker>(
            12, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
    }
}
