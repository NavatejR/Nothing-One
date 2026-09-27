package com.nothing.one.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

val NothingBlack = Color(0xFF000000)
val NothingWhite = Color(0xFFFFFFFF)
val NothingRedDark = Color(0xFFCC0033)
val NothingSurface = Color(0xFF0D0D0D)
val NothingSurfaceElevated = Color(0xFF1A1A1A)
val NothingSurfaceHighlight = Color(0xFF262626)
val NothingOutline = Color(0xFF333333)
val NothingOutlineVariant = Color(0xFF1F1F1F)
val NothingTextPrimary = Color(0xFFFFFFFF)
val NothingTextSecondary = Color(0x99FFFFFF)
val NothingTextTertiary = Color(0x66FFFFFF)
val NothingTextDisabled = Color(0x2EFFFFFF)
val NothingOnSurface = Color(0xFFE8E8E8)
val NothingGlyphPulse = Color(0xFF3D3D3D)

// Mood scale — one hue per step, deliberately restrained so the accent stays
// the only saturated color in the app. Dimmed so they read on black.
val Mood1Color = Color(0xFF5A5A5A) // AWFUL — grey
val Mood2Color = Color(0xFF7A6A55) // LOW — dust
val Mood3Color = Color(0xFF8A8A8A) // OKAY — neutral
val Mood4Color = Color(0xFFB8A88A) // GOOD — warm sand
val Mood5Color = Color(0xFFE8E8E8) // GREAT — near white

// ---- Live accent -----------------------------------------------------------
//
// The accent was historically the constant NothingRed. Customization needs it
// to change at runtime, so it is now a snapshot-state read: every composable
// that references [NothingRed] (75 call sites, unchanged) recomposes when the
// user picks a new accent in Settings. Non-UI readers simply get the value.

/** Preset accents offered in Settings → Appearance. */
val AccentPresets: List<Pair<String, Color>> = listOf(
    "NOTHING RED" to Color(0xFFFF0044),
    "SIGNAL WHITE" to Color(0xFFE8E8E8),
    "CYAN PULSE" to Color(0xFF00E5CC),
    "AMBER WIRE" to Color(0xFFFFB300),
    "VIOLET DOT" to Color(0xFFB388FF),
    "ACID LIME" to Color(0xFFC6FF00),
)

const val DEFAULT_ACCENT_HEX = "#FF0044"

private val accentState = mutableStateOf(Color(0xFFFF0044))

/** The app accent — reactive in composition, plain value elsewhere. */
val NothingRed: Color
    get() = accentState.value

/** Current accent as #RRGGBB (for persisting). */
fun currentAccentHex(): String {
    val argb = accentState.value.toArgb()
    return "#%06X".format(argb and 0xFFFFFF)
}

/**
 * Applies an accent from a #RRGGBB string. Invalid input falls back to the
 * Nothing Red default — a broken stored value must never produce a black-on-
 * black UI.
 */
fun setAppAccent(hex: String?) {
    val parsed = runCatching { Color(android.graphics.Color.parseColor(hex?.trim())) }
        .getOrDefault(Color(0xFFFF0044))
    accentState.value = parsed
}
