package com.nothing.one.feature.music.service

import androidx.media3.exoplayer.ExoPlayer
import com.nothing.one.feature.music.util.Constants
import com.nothing.one.feature.music.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor(
    private val settings: SettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var playerRef: ExoPlayer? = null

    private val _remainingMs = MutableStateFlow(-1L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun bind(player: ExoPlayer) {
        playerRef = player
    }

    fun start(durationMs: Long, fadeOut: Boolean) {
        settings.sleepTimerRemaining = durationMs
        settings.sleepTimerEndsAt = System.currentTimeMillis() + durationMs
        settings.isSleepTimerActive = true
        settings.sleepTimerFadeOut = fadeOut
        _remainingMs.value = durationMs
        _isActive.value = true

        job?.cancel()
        job = scope.launch {
            val endAt = System.currentTimeMillis() + durationMs
            while (true) {
                delay(1000)
                val remaining = endAt - System.currentTimeMillis()
                _remainingMs.value = remaining.coerceAtLeast(0)
                settings.sleepTimerRemaining = _remainingMs.value

                if (remaining <= 0) {
                    stopSleeping()
                    break
                }
                if (fadeOut && remaining <= Constants.DEFAULT_FADE_OUT_MS) {
                    val progress = 1f - (remaining.toFloat() / Constants.DEFAULT_FADE_OUT_MS)
                    playerRef?.volume = (1f - progress).coerceIn(0f, 1f)
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        settings.clearSleepTimer()
        playerRef?.volume = 1f
        _remainingMs.value = -1L
        _isActive.value = false
    }

    fun restore(): Boolean {
        if (!settings.isSleepTimerActive) return false
        val endAt = settings.sleepTimerEndsAt
        if (endAt <= 0) {
            cancel()
            return false
        }
        val remaining = endAt - System.currentTimeMillis()
        if (remaining <= 0) {
            stopSleeping()
            return false
        }
        _remainingMs.value = remaining
        _isActive.value = true
        start(remaining, settings.sleepTimerFadeOut)
        return true
    }

    private fun stopSleeping() {
        job?.cancel()
        playerRef?.pause()
        playerRef?.volume = 1f
        settings.clearSleepTimer()
        _remainingMs.value = -1L
        _isActive.value = false
    }
}