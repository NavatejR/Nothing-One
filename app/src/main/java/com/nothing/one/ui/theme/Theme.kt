package com.nothing.one.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NothingColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = NothingBlack,
    primaryContainer = NothingRed,
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
    error = NothingRed,
    onError = NothingBlack,
)

@Composable
fun NothingJournalTheme(content: @Composable () -> Unit) {
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

    MaterialTheme(
        colorScheme = NothingColorScheme,
        typography = NothingTypography,
        shapes = NothingShapes,
        content = content,
    )
}
