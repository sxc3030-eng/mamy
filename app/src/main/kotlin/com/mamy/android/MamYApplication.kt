package com.mamy.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MamYApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Hilt-managed singletons (DB, vault, settings) wire up from DI modules in Task 20.
    }
}
