package com.nothing.one.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nothing.one.R

val DotMatrixFont = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.dot_gothic16_regular)
)
val InterFont = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.inter_variable)
)

/**
 * Builds the app typography from the user's appearance settings.
 *
 * [textScale] multiplies every size and line height — the accessibility
 * dial. [fontMode] chooses the display face for titles: NOTHING keeps the
 * signature dot-matrix on headers/labels, INTER swaps it for Inter so the
 * whole app reads as one typeface.
 */
fun buildNothingTypography(textScale: Float, fontMode: com.nothing.one.data.FontMode): Typography {
    val scale = textScale.coerceIn(0.8f, 1.4f)
    val display = if (fontMode == com.nothing.one.data.FontMode.INTER) InterFont else DotMatrixFont

    fun size(sp: Int) = (sp * scale).sp
    fun line(sp: Int) = (sp * scale).sp

    return Typography(
        displayLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Normal,
            fontSize = size(48),
            lineHeight = line(56),
            letterSpacing = 1.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Normal,
            fontSize = size(40),
            lineHeight = line(48),
        ),
        displaySmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Normal,
            fontSize = size(32),
            lineHeight = line(40),
        ),
        headlineLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Normal,
            fontSize = size(28),
            lineHeight = line(36),
        ),
        headlineMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Normal,
            fontSize = size(24),
            lineHeight = line(32),
        ),
        headlineSmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Normal,
            fontSize = size(20),
            lineHeight = line(28),
        ),
        titleLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Normal,
            fontSize = size(20),
            lineHeight = line(28),
            letterSpacing = 0.5.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = InterFont,
            fontWeight = FontWeight.Medium,
            fontSize = size(16),
            lineHeight = line(24),
        ),
        titleSmall = TextStyle(
            fontFamily = InterFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(14),
            lineHeight = line(20),
        ),
        bodyLarge = TextStyle(
            fontFamily = InterFont,
            fontWeight = FontWeight.Normal,
            fontSize = size(16),
            lineHeight = line(24),
            letterSpacing = 0.15.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = InterFont,
            fontWeight = FontWeight.Normal,
            fontSize = size(14),
            lineHeight = line(20),
        ),
        bodySmall = TextStyle(
            fontFamily = InterFont,
            fontWeight = FontWeight.Normal,
            fontSize = size(12),
            lineHeight = line(16),
        ),
        labelLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Medium,
            fontSize = size(14),
            lineHeight = line(20),
        ),
        labelMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Medium,
            fontSize = size(12),
            lineHeight = line(16),
        ),
        labelSmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Medium,
            fontSize = size(11),
            lineHeight = line(16),
            letterSpacing = 0.5.sp,
        ),
    )
}

/** Default typography at stock settings (previews, non-compose callers). */
val NothingTypography = buildNothingTypography(1.0f, com.nothing.one.data.FontMode.NOTHING)
