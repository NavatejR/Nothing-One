package com.nothing.one.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "journal_settings")

/** Display font selection for the whole app. */
enum class FontMode { NOTHING, INTER }

data class Settings(
    val reducedMotion: Boolean = false,
    val onboarded: Boolean = false,
    /** Global text scale multiplier applied to every typography style. */
    val textScale: Float = 1.0f,
    /** NOTHING = dot-matrix display type on titles; INTER = Inter everywhere. */
    val fontMode: FontMode = FontMode.NOTHING,
    /** Accent color as #RRGGBB; drives the live accent used across the app. */
    val accentColorHex: String = "#FF0044",
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val FONT_MODE = stringPreferencesKey("font_mode")
        val ACCENT_HEX = stringPreferencesKey("accent_hex")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            onboarded = prefs[Keys.ONBOARDED] ?: false,
            textScale = prefs[Keys.TEXT_SCALE] ?: 1.0f,
            fontMode = runCatching {
                FontMode.valueOf(prefs[Keys.FONT_MODE] ?: FontMode.NOTHING.name)
            }.getOrDefault(FontMode.NOTHING),
            accentColorHex = prefs[Keys.ACCENT_HEX] ?: "#FF0044",
        )
    }

    suspend fun setReducedMotion(reduced: Boolean) {
        context.dataStore.edit { it[Keys.REDUCED_MOTION] = reduced }
    }

    suspend fun setTextScale(scale: Float) {
        context.dataStore.edit { it[Keys.TEXT_SCALE] = scale.coerceIn(0.8f, 1.4f) }
    }

    suspend fun setFontMode(mode: FontMode) {
        context.dataStore.edit { it[Keys.FONT_MODE] = mode.name }
    }

    suspend fun setAccentColorHex(hex: String) {
        context.dataStore.edit { it[Keys.ACCENT_HEX] = hex }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[Keys.ONBOARDED] = true }
    }
}
