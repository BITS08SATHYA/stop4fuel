package com.stopforfuel.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors from StopForFuel web app
private val Teal = Color(0xFF14B8A6)
private val TealLight = Color(0xFF2DD4BF)
private val TealDark = Color(0xFF0D9488)
private val Orange = Color(0xFFF59E0B)
private val OrangeDark = Color(0xFFD97706)
private val Red = Color(0xFFEF4444)

// Light theme palette
private val LightBackground = Color(0xFFF8FAFC)
private val LightSurface = Color.White
private val LightSurfaceVariant = Color(0xFFF1F5F9)
private val DarkText = Color(0xFF0F172A)
private val MutedText = Color(0xFF64748B)

private val S4fColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = TealDark,
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = OrangeDark,
    tertiary = TealLight,
    onTertiary = Color.White,
    error = Red,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFB91C1C),
    background = LightBackground,
    onBackground = DarkText,
    surface = LightSurface,
    onSurface = DarkText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = MutedText,
    outline = Color(0xFFCBD5E1),
    inverseSurface = DarkText,
    inverseOnSurface = Color(0xFFF8FAFC),
    surfaceTint = Teal
)

@Composable
fun StopForFuelTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = S4fColorScheme,
        content = content
    )
}
