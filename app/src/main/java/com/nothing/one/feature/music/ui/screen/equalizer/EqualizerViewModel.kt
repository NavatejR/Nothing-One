package com.nothing.one.feature.music.ui.screen.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.feature.music.util.Constants
import com.nothing.one.feature.music.util.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EqualizerPreset(
    val name: String,
    val gains: FloatArray,
)

object EqPresets {
    // Gains stored in millibels (mB) to match Media3 EqualizerEffect
    val FLAT = EqualizerPreset("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f))
    val BASS_BOOST = EqualizerPreset("Bass Boost", floatArrayOf(700f, 400f, 100f, -100f, -200f))
    val TREBLE_BOOST = EqualizerPreset("Treble", floatArrayOf(-200f, -100f, 100f, 400f, 700f))
    val VOCAL = EqualizerPreset("Vocal", floatArrayOf(-300f, 200f, 600f, 200f, -300f))
    val ROCK = EqualizerPreset("Rock", floatArrayOf(600f, 400f, -200f, 400f, 600f))
    val JAZZ = EqualizerPreset("Jazz", floatArrayOf(200f, 400f, 300f, 200f, 400f))
    val CLASSICAL = EqualizerPreset("Classical", floatArrayOf(400f, 300f, -200f, 300f, 400f))
    val ELECTRONIC = EqualizerPreset("Electronic", floatArrayOf(-200f, 300f, 500f, 300f, -200f))

    val ALL = listOf(FLAT, BASS_BOOST, TREBLE_BOOST, VOCAL, ROCK, JAZZ, CLASSICAL, ELECTRONIC)
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val settings: SettingsManager,
) : ViewModel() {

    private val _enabled = MutableStateFlow(settings.equalizerEnabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _gains = MutableStateFlow(settings.equalizerGains)
    val gains: StateFlow<FloatArray> = _gains.asStateFlow()

    private val _presetName = MutableStateFlow(settings.eqPreset)
    val presetName: StateFlow<String> = _presetName.asStateFlow()

    private val _bassBoostEnabled = MutableStateFlow(settings.bassBoostEnabled)
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(settings.bassBoostStrength)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _virtualizerEnabled = MutableStateFlow(settings.virtualizerEnabled)
    val virtualizerEnabled: StateFlow<Boolean> = _virtualizerEnabled.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(settings.virtualizerStrength)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    fun setEnabled(value: Boolean) {
        settings.equalizerEnabled = value
        _enabled.value = value
    }

    fun setBandGain(index: Int, gainMb: Float) {
        val current = _gains.value.toMutableList()
        if (index in current.indices) {
            current[index] = gainMb.coerceIn(
                Constants.EQUALIZER_MIN_GAIN_MB.toFloat(),
                Constants.EQUALIZER_MAX_GAIN_MB.toFloat(),
            )
        }
        val array = current.toFloatArray()
        settings.equalizerGains = array
        _gains.value = array
        _presetName.value = "Custom"
        settings.eqPreset = "Custom"
    }

    fun applyPreset(preset: EqualizerPreset) {
        settings.equalizerGains = preset.gains
        settings.eqPreset = preset.name
        _gains.value = preset.gains
        _presetName.value = preset.name
        settings.equalizerEnabled = true
        _enabled.value = true
    }

    fun setBassBoost(enabled: Boolean, strength: Int) {
        settings.bassBoostEnabled = enabled
        settings.bassBoostStrength = strength.coerceIn(0, 100)
        _bassBoostEnabled.value = enabled
        _bassBoostStrength.value = strength.coerceIn(0, 100)
    }

    fun setVirtualizer(enabled: Boolean, strength: Int) {
        settings.virtualizerEnabled = enabled
        settings.virtualizerStrength = strength.coerceIn(0, 100)
        _virtualizerEnabled.value = enabled
        _virtualizerStrength.value = strength.coerceIn(0, 100)
    }

    fun resetAll() {
        applyPreset(EqPresets.FLAT)
        setBassBoost(false, 0)
        setVirtualizer(false, 0)
        setEnabled(false)
    }
}