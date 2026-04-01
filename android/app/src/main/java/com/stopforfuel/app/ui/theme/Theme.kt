package com.stopforfuel.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand colors inspired by the web app
private val S4fOrange = Color(0xFFE67E22)
private val S4fBlue = Color(0xFF2980B9)
private val S4fGreen = Color(0xFF27AE60)
private val S4fRed = Color(0xFFE74C3C)

private val LightColorScheme = lightColorScheme(
    primary = S4fOrange,
    onPrimary = Color.White,
    secondary = S4fBlue,
    onSecondary = Color.White,
    tertiary = S4fGreen,
    error = S4fRed,
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1A1A)
)

private val DarkColorScheme = darkColorScheme(
    primary = S4fOrange,
    onPrimary = Color.White,
    secondary = S4fBlue,
    onSecondary = Color.White,
    tertiary = S4fGreen,
    error = S4fRed
)

@Composable
fun StopForFuelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
