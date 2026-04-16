package com.stopforfuel.app.push

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks whether any Activity is in the "started" state, used by FcmService
 * to decide between an in-app banner (foreground) and a system tray
 * notification (background).
 */
object AppForegroundState {

    private val startedCount = AtomicInteger(0)

    val isInForeground: Boolean
        get() = startedCount.get() > 0

    fun attach(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) { startedCount.incrementAndGet() }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) { startedCount.decrementAndGet() }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
