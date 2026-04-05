package com.stopforfuel.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors from StopForFuel web app
private val Teal = Color(0xFF14B8A6)
private val TealLight = Color(0xFF2DD4BF)
private val Orange = Color(0xFFF59E0B)
private val OrangeDark = Color(0xFFD97706)
private val Red = Color(0xFFEF4444)
private val DarkNavy = Color(0xFF0F172A)
private val DarkSlate = Color(0xFF1E293B)
private val SlateGray = Color(0xFF334155)
private val TextLight = Color(0xFFE2E8F0)
private val TextMuted = Color(0xFF94A3B8)

private val S4fColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D3D38),
    onPrimaryContainer = TealLight,
    secondary = Orange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3D2800),
    onSecondaryContainer = Orange,
    tertiary = TealLight,
    onTertiary = Color.Black,
    error = Red,
    onError = Color.White,
    errorContainer = Color(0xFF3D0000),
    onErrorContainer = Color(0xFFFFB4AB),
    background = DarkNavy,
    onBackground = TextLight,
    surface = DarkSlate,
    onSurface = TextLight,
    surfaceVariant = SlateGray,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF475569),
    inverseSurface = TextLight,
    inverseOnSurface = DarkNavy,
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
