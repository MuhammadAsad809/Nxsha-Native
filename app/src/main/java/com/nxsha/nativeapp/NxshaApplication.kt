package com.nxsha.nativeapp

import android.app.Application
import com.nxsha.nativeapp.data.local.WatchHistoryDatabase

class NxshaApplication : Application() {

    val database: WatchHistoryDatabase by lazy {
        WatchHistoryDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: NxshaApplication
            private set
    }
}
