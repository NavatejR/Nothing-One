package com.nothing.one.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nothing.one.data.Settings

private fun buildColorScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = NothingBlack,
    primaryContainer = accent,
    onPrimaryContainer = NothingBlack,
    secondary = NothingWhite,
    onSecondary = NothingBlack,
    secondaryContainer = NothingSurfaceElevated,
    onSecondaryContainer = NothingWhite,
    tertiary = NothingTextTertiary,
    onTertiary = NothingBlack,
    background = NothingBlack,
    onBackground = NothingWhite,
    surface = NothingSurface,
    onSurface = NothingOnSurface,
    surfaceVariant = NothingSurfaceElevated,
    onSurfaceVariant = NothingTextSecondary,
    outline = NothingOutline,
    outlineVariant = NothingOutlineVariant,
    error = accent,
    onError = NothingBlack,
)

/**
 * The app theme, driven entirely by [Settings] — text scale, font mode and
 * the accent color all follow the user's Appearance choices live. A null
 * [Settings] (still loading) renders with stock defaults so startup never
 * flashes an unstyled frame.
 */
@Composable
fun NothingJournalTheme(
    settings: Settings?,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    // Push the persisted accent into the live accent state; every reference
    // to NothingRed across the app recomposes from here.
    SideEffect { setAppAccent(settings?.accentColorHex ?: DEFAULT_ACCENT_HEX) }

    val accent = NothingRed
    val colorScheme = remember(accent) { buildColorScheme(accent) }
    val typography = remember(
        settings?.textScale,
        settings?.fontMode,
    ) {
        buildNothingTypography(
            settings?.textScale ?: 1.0f,
            settings?.fontMode ?: com.nothing.one.data.FontMode.NOTHING,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = NothingShapes,
        content = content,
    )
}
