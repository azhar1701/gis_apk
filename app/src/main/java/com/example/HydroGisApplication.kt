package com.example

import android.app.Application
import com.example.data.sync.SyncScheduler

class HydroGisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Schedule battery-efficient periodic background sync
        SyncScheduler.schedulePeriodicSync(this)
    }
}
