package echo.player.design

import androidx.compose.ui.graphics.Color

/**
 * ECHO palette. JARVEX lime is an accent only (active states, the one primary action, meters);
 * surfaces are near-black and neutral so the music stays the focus. Never use raw colors in screens.
 *
 * Contrast (against the 0D0D0D background): TextPrimary ~18:1, TextSecondary ~10:1, TextMuted ~5.7:1,
 * Lime ~17:1. Against the Graphite surface TextMuted is ~4.9:1.
 */
object EchoColors {
    // JARVEX brand
    val Lime = Color(0xFFE8FF47)
    val Black = Color(0xFF0D0D0D)
    val White = Color(0xFFFFFFFF)

    // Derived neutral surfaces, darkest to lightest
    val DisplayDeep = Color(0xFF060606)
    val Charcoal = Color(0xFF151515)
    val Graphite = Color(0xFF1E1E1E)
    val Steel = Color(0xFF2A2A2A)
    val Metal = Color(0xFF3A3A3A)
    val Highlight = Color(0xFF4C4C4C)
    val Separator = Color(0xFF262626)

    // Text
    val TextPrimary = White
    val TextSecondary = Color(0xFFBBBBBB)
    val TextMuted = Color(0xFF8C8C8C)
    val OnLime = Black

    // Lime tints, kept restrained
    val LimeDim = Color(0xFFA7B82F)
    val LimeFaint = Color(0x24E8FF47)
    val LimeOff = Color(0x14E8FF47)

    // Status
    val Danger = Color(0xFFFF7A66)
    val Warning = Color(0xFFFFC857)
}
