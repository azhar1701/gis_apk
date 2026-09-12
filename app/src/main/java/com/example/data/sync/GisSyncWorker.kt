package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.repository.GisRepository
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker that synchronizes local Room spatial data with remote server.
 * Operates when device is charging and connected to Wi-Fi.
 */
class GisSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "GisSyncWorker"
        const val KEY_FEATURES_SYNCED = "features_synced"
        const val KEY_SYNC_TIMESTAMP = "sync_timestamp"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "GisSyncWorker started: device is charging and connected to Wi-Fi")
        return try {
            val repository = GisRepository(applicationContext)

            // Perform complete synchronization of Room spatial data with remote server
            val syncResult = repository.syncSpatialDataWithRemote()

            if (syncResult.isSuccess) {
                val totalCount = syncResult.getOrDefault(0)
                Log.i(TAG, "GisSyncWorker successfully synchronized $totalCount features into Room")
                val outputData = workDataOf(
                    KEY_FEATURES_SYNCED to totalCount,
                    KEY_SYNC_TIMESTAMP to System.currentTimeMillis()
                )
                Result.success(outputData)
            } else {
                val error = syncResult.exceptionOrNull()?.message ?: "Unknown error"
                Log.w(TAG, "GisSyncWorker failed with error: $error (attempt ${runAttemptCount})")
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("error" to error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GisSyncWorker exception during execution", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (e.message ?: "Sync failed")))
            }
        }
    }
}

/**
 * Helper scheduler to configure and monitor WorkManager background tasks
 * specifically constrained to device charging and Wi-Fi connection.
 */
object SyncScheduler {
    const val CHARGING_WIFI_PERIODIC_WORK = "hydrogis_charging_wifi_spatial_sync"
    const val CHARGING_WIFI_ONETIME_WORK = "hydrogis_charging_wifi_onetime_sync"

    /**
     * Builds standard WorkManager constraints requiring:
     * 1. Device is charging (setRequiresCharging(true))
     * 2. Device is connected to unmetered network / Wi-Fi (setRequiredNetworkType(NetworkType.UNMETERED))
     * 3. Device storage is not low (setRequiresStorageNotLow(true))
     */
    fun buildChargingWifiConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .build()
    }

    /**
     * Automatically registers a periodic background sync with WorkManager that runs
     * whenever the device is charging and connected to Wi-Fi.
     */
    fun scheduleChargingWifiSync(context: Context) {
        val constraints = buildChargingWifiConstraints()

        // Runs periodically (minimum period is 15 minutes, standard 6 hours with 30-min flex interval)
        val periodicSyncRequest = PeriodicWorkRequestBuilder<GisSyncWorker>(
            6, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("hydrogis_charging_wifi")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CHARGING_WIFI_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicSyncRequest
        )
        Log.d("SyncScheduler", "Enqueued unique periodic work: $CHARGING_WIFI_PERIODIC_WORK with Charging + Wi-Fi constraints")
    }

    /**
     * Queues an immediate one-time sync task that triggers as soon as the device
     * is plugged in and connected to Wi-Fi.
     */
    fun enqueueOneTimeChargingWifiSync(context: Context) {
        val constraints = buildChargingWifiConstraints()

        val oneTimeRequest = OneTimeWorkRequestBuilder<GisSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("hydrogis_charging_wifi_onetime")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CHARGING_WIFI_ONETIME_WORK,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
        Log.d("SyncScheduler", "Enqueued unique one-time work: $CHARGING_WIFI_ONETIME_WORK with Charging + Wi-Fi constraints")
    }

    /**
     * Returns a Flow observing the status of the charging + Wi-Fi periodic work.
     */
    fun getPeriodicWorkInfoFlow(context: Context): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(CHARGING_WIFI_PERIODIC_WORK)
    }

    /**
     * Returns a Flow observing the status of the charging + Wi-Fi one-time work.
     */
    fun getOneTimeWorkInfoFlow(context: Context): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(CHARGING_WIFI_ONETIME_WORK)
    }
}
