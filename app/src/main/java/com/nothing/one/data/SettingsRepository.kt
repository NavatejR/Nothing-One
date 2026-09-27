package com.nothing.one.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "journal_settings")

data class Settings(
    val reducedMotion: Boolean = false,
    val onboarded: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            onboarded = prefs[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun setReducedMotion(reduced: Boolean) {
        context.dataStore.edit { it[Keys.REDUCED_MOTION] = reduced }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[Keys.ONBOARDED] = true }
    }
}
