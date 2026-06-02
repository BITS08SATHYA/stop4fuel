package com.stopforfuel.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.stopforfuel.app.ui.navigation.AppNavGraph
import com.stopforfuel.app.ui.theme.StopForFuelTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingPushDestination = mutableStateOf<String?>(null)

    // Android 13+ requires runtime consent before the app can post notifications.
    // Without this, background/system-tray notifications are silently blocked.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* user's choice respected */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPushDestination.value = intent?.getStringExtra(EXTRA_OPEN_DESTINATION)
        maybeRequestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            StopForFuelTheme {
                AppNavGraph(
                    pendingPushDestination = pendingPushDestination.value,
                    onPushDestinationConsumed = { pendingPushDestination.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPushDestination.value = intent.getStringExtra(EXTRA_OPEN_DESTINATION)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_DESTINATION = "open_destination"
    }
}
