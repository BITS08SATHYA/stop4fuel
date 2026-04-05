package com.stopforfuel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stopforfuel.app.ui.navigation.AppNavGraph
import com.stopforfuel.app.ui.theme.StopForFuelTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StopForFuelTheme {
                AppNavGraph()
            }
        }
    }
}
