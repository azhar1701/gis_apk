package com.example

import android.app.Application
import com.example.data.sync.SyncScheduler

class HydroGisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            // Automatically schedule WorkManager background synchronization
            // constrained to device charging and Wi-Fi connection
            SyncScheduler.scheduleChargingWifiSync(this)
        } catch (e: Exception) {
            // Graceful fallback for test runner environments where WorkManager is not pre-configured
        }
    }
}
