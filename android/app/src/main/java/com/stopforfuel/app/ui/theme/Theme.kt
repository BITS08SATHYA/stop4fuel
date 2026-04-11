package com.stopforfuel.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand: StopForFuel v1.0 ──────────────────────────────

// Primary: Fuel Amber
private val Amber400 = Color(0xFFFFCA28)
private val Amber500 = Color(0xFFFFC107)
private val Amber600 = Color(0xFFFFB300)  // Primary brand color
private val Amber700 = Color(0xFFFF8F00)
private val Amber800 = Color(0xFFE65100)

// Secondary: Petroleum Deep
private val Deep900 = Color(0xFF0D1117)
private val Deep800 = Color(0xFF161B22)
private val Deep700 = Color(0xFF1C2333)

// Accent: Ignition Teal
private val Teal400 = Color(0xFF2DD4BF)
private val Teal500 = Color(0xFF14B8A6)
private val Teal600 = Color(0xFF0D9488)

// Semantic
private val Success = Color(0xFF22C55E)
private val Danger = Color(0xFFEF4444)

// Neutrals
private val Gray50 = Color(0xFFF8FAFC)
private val Gray100 = Color(0xFFF1F5F9)
private val Gray500 = Color(0xFF64748B)
private val Gray900 = Color(0xFF0F172A)

private val S4fColorScheme = lightColorScheme(
    primary = Amber600,
    onPrimary = Deep900,
    primaryContainer = Color(0xFFFFF8E1),  // Amber 50
    onPrimaryContainer = Amber800,
    secondary = Teal500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Teal600,
    tertiary = Teal400,
    onTertiary = Color.White,
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFB91C1C),
    background = Gray50,
    onBackground = Gray900,
    surface = Color.White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray500,
    outline = Color(0xFFCBD5E1),  // Gray 300
    inverseSurface = Deep900,
    inverseOnSurface = Gray50,
    surfaceTint = Amber600
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
