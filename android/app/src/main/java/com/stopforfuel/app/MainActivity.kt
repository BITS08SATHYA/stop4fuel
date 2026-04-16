package com.stopforfuel.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.stopforfuel.app.ui.navigation.AppNavGraph
import com.stopforfuel.app.ui.theme.StopForFuelTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingPushDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPushDestination.value = intent?.getStringExtra(EXTRA_OPEN_DESTINATION)
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

    companion object {
        const val EXTRA_OPEN_DESTINATION = "open_destination"
    }
}
