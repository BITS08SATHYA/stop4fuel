package com.stopforfuel.app

import android.app.Application
import com.stopforfuel.app.push.AppForegroundState
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StopForFuelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppForegroundState.attach(this)
    }
}
