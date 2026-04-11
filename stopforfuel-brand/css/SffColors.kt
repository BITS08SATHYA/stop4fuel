package com.stopforfuel.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ═══════════════════════════════════════════════════════════
 *  StopForFuel — Android Color Tokens
 *  Brand Identity v1.0
 * ═══════════════════════════════════════════════════════════
 *
 *  Usage in Compose:
 *    Text(color = SffColors.Amber600)
 *    Box(modifier = Modifier.background(SffColors.Deep900))
 */

object SffColors {

    // ── Primary: Fuel Amber ─────────────────────────────
    val Amber50  = Color(0xFFFFF8E1)
    val Amber100 = Color(0xFFFFECB3)
    val Amber200 = Color(0xFFFFD54F)
    val Amber400 = Color(0xFFFFCA28)
    val Amber500 = Color(0xFFFFC107)
    val Amber600 = Color(0xFFFFB300)  // Primary brand color
    val Amber700 = Color(0xFFFF8F00)
    val Amber800 = Color(0xFFE65100)

    // ── Secondary: Petroleum Deep ───────────────────────
    val Deep950 = Color(0xFF080B12)
    val Deep900 = Color(0xFF0D1117)  // Primary dark background
    val Deep800 = Color(0xFF161B22)
    val Deep700 = Color(0xFF1C2333)
    val Deep600 = Color(0xFF21283B)
    val Deep500 = Color(0xFF30394A)

    // ── Accent: Ignition Teal ───────────────────────────
    val Teal400 = Color(0xFF2DD4BF)
    val Teal500 = Color(0xFF14B8A6)
    val Teal600 = Color(0xFF0D9488)

    // ── Semantic ────────────────────────────────────────
    val Success = Color(0xFF22C55E)
    val Danger  = Color(0xFFEF4444)
    val Warning = Color(0xFFF59E0B)
    val Info    = Color(0xFF3B82F6)

    // ── Neutrals ────────────────────────────────────────
    val Gray50  = Color(0xFFF8FAFC)
    val Gray100 = Color(0xFFF1F5F9)
    val Gray200 = Color(0xFFE2E8F0)
    val Gray300 = Color(0xFFCBD5E1)
    val Gray400 = Color(0xFF94A3B8)
    val Gray500 = Color(0xFF64748B)
    val Gray600 = Color(0xFF475569)
    val Gray700 = Color(0xFF334155)
    val Gray800 = Color(0xFF1E293B)
    val Gray900 = Color(0xFF0F172A)
}


/**
 * ═══════════════════════════════════════════════════════════
 *  Typography — Compose Font Families
 * ═══════════════════════════════════════════════════════════
 *
 *  Add these fonts to res/font/:
 *    - outfit_regular.ttf, outfit_semibold.ttf, outfit_bold.ttf, outfit_extrabold.ttf
 *    - plusjakartasans_regular.ttf, plusjakartasans_medium.ttf, plusjakartasans_semibold.ttf, plusjakartasans_bold.ttf
 *    - jetbrainsmono_regular.ttf, jetbrainsmono_medium.ttf
 *
 *  Download from Google Fonts:
 *    https://fonts.google.com/specimen/Outfit
 *    https://fonts.google.com/specimen/Plus+Jakarta+Sans
 *    https://fonts.google.com/specimen/JetBrains+Mono
 */

// import androidx.compose.ui.text.font.Font
// import androidx.compose.ui.text.font.FontFamily
// import androidx.compose.ui.text.font.FontWeight
//
// val OutfitFamily = FontFamily(
//     Font(R.font.outfit_regular, FontWeight.Normal),
//     Font(R.font.outfit_semibold, FontWeight.SemiBold),
//     Font(R.font.outfit_bold, FontWeight.Bold),
//     Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
// )
//
// val JakartaFamily = FontFamily(
//     Font(R.font.plusjakartasans_regular, FontWeight.Normal),
//     Font(R.font.plusjakartasans_medium, FontWeight.Medium),
//     Font(R.font.plusjakartasans_semibold, FontWeight.SemiBold),
//     Font(R.font.plusjakartasans_bold, FontWeight.Bold),
// )
//
// val MonoFamily = FontFamily(
//     Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
//     Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
// )
