package com.nothing.one.feature.music.util

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nothing_music_settings", Context.MODE_PRIVATE)

    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    /** Notifies registered listeners of any settings change. */
    @Synchronized
    fun addOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    @Synchronized
    fun removeOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    var crossfadeMs: Int
        get() = prefs.getInt(KEY_CROSSFADE_MS, 0)
        set(value) = prefs.edit().putInt(KEY_CROSSFADE_MS, value).apply()

    var gaplessPlayback: Boolean
        get() = prefs.getBoolean(KEY_GAPLESS, true)
        set(value) = prefs.edit().putBoolean(KEY_GAPLESS, value).apply()

    var sleepTimerRemaining: Long
        get() = prefs.getLong(KEY_SLEEP_TIMER_REMAINING_MS, -1L)
        set(value) = prefs.edit().putLong(KEY_SLEEP_TIMER_REMAINING_MS, value).apply()

    var sleepTimerEndsAt: Long
        get() = prefs.getLong(KEY_SLEEP_TIMER_ENDS_AT, -1L)
        set(value) = prefs.edit().putLong(KEY_SLEEP_TIMER_ENDS_AT, value).apply()

    var isSleepTimerActive: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_TIMER_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP_TIMER_ACTIVE, value).apply()

    var sleepTimerFadeOut: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_TIMER_FADE_OUT, true)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP_TIMER_FADE_OUT, value).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var repeatMode: Int
        get() = prefs.getInt(KEY_REPEAT_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_REPEAT_MODE, value).apply()

    var shuffleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    var equalizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()

    var equalizerGains: FloatArray
        get() {
            val value = prefs.getString(KEY_EQ_GAINS, null) ?: return FloatArray(Constants.EQUALIZER_BAND_COUNT) { 0f }
            return value.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        }
        set(v) {
            prefs.edit().putString(KEY_EQ_GAINS, v.joinToString(",")).apply()
        }

    var bassBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_BASS_BOOST_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BASS_BOOST_ENABLED, value).apply()

    var bassBoostStrength: Int
        get() = prefs.getInt(KEY_BASS_BOOST_STRENGTH, 0)
        set(value) = prefs.edit().putInt(KEY_BASS_BOOST_STRENGTH, value).apply()

    var virtualizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIRTUALIZER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VIRTUALIZER_ENABLED, value).apply()

    var virtualizerStrength: Int
        get() = prefs.getInt(KEY_VIRTUALIZER_STRENGTH, 0)
        set(value) = prefs.edit().putInt(KEY_VIRTUALIZER_STRENGTH, value).apply()

    var eqPreset: String
        get() = prefs.getString(KEY_EQ_PRESET, "flat") ?: "flat"
        set(value) = prefs.edit().putString(KEY_EQ_PRESET, value).apply()

    fun clearSleepTimer() {
        prefs.edit()
            .remove(KEY_SLEEP_TIMER_REMAINING_MS)
            .remove(KEY_SLEEP_TIMER_ENDS_AT)
            .putBoolean(KEY_SLEEP_TIMER_ACTIVE, false)
            .apply()
    }

    private companion object {
        const val KEY_CROSSFADE_MS = "crossfade_ms"
        const val KEY_GAPLESS = "gapless"
        const val KEY_SLEEP_TIMER_REMAINING_MS = "sleep_timer_remaining"
        const val KEY_SLEEP_TIMER_ENDS_AT = "sleep_timer_ends_at"
        const val KEY_SLEEP_TIMER_ACTIVE = "sleep_timer_active"
        const val KEY_SLEEP_TIMER_FADE_OUT = "sleep_timer_fade_out"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_REPEAT_MODE = "repeat_mode"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_GAINS = "eq_gains"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_BASS_BOOST_ENABLED = "bass_boost_enabled"
        const val KEY_BASS_BOOST_STRENGTH = "bass_boost_strength"
        const val KEY_VIRTUALIZER_ENABLED = "virtualizer_enabled"
        const val KEY_VIRTUALIZER_STRENGTH = "virtualizer_strength"
    }
}