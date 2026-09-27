package com.nothing.one.feature.music.service

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nothing.one.feature.music.di.PlayerInfo
import com.nothing.one.feature.music.domain.model.Track
import com.nothing.one.feature.music.util.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var settings: SettingsManager
    @Inject lateinit var playerInfo: PlayerInfo

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settingsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshAudioEffects()
        }

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        mediaSession = MediaSession.Builder(this, player).build()
        settings.addOnChangeListener(settingsListener)
        initializeSleepTimer()
    }

    private fun initializePlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableAudioFloatOutput(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.repeatMode = settings.repeatMode

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                ) {
                    // Skip unplayable track automatically
                    if (player.mediaItemCount > 1) player.seekToNextMediaItem()
                }
            }
        })

        applyAudioEffects()
    }

    private fun buildMediaItems(tracks: List<Track>): List<MediaItem> {
        playerInfo.lastMediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.path)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setExtras(android.os.Bundle().apply {
                            putLong("durationMs", track.durationMs)
                            putString("albumArtUri", track.albumArtUri?.toString())
                            putLong("originalId", track.id)
                        })
                        .build()
                )
                .build()
        }
        return playerInfo.lastMediaItems
    }

    fun playTracksWithStart(tracks: List<Track>, startIndex: Int) {
        val items = buildMediaItems(tracks)
        if (items.isEmpty()) return
        val index = startIndex.coerceIn(0, items.lastIndex)
        player.setMediaItems(items, index, C.TIME_UNSET)
        player.prepare()
        player.play()
        playerInfo.lastTrackIndex = index
    }

    fun playTracks(tracks: List<Track>) = playTracksWithStart(tracks, 0)

    fun enqueue(tracks: List<Track>) {
        val items = buildMediaItems(tracks)
        player.addMediaItems(items)
        if (!player.playWhenReady) {
            player.prepare()
            player.play()
        }
    }

    fun setSleepTimer(durationMs: Long, fadeOut: Boolean) {
        settings.sleepTimerRemaining = durationMs
        settings.sleepTimerEndsAt = System.currentTimeMillis() + durationMs
        settings.isSleepTimerActive = true
        settings.sleepTimerFadeOut = fadeOut
    }

    fun cancelSleepTimer() {
        settings.clearSleepTimer()
    }

    private fun requireAudioSessionId(): Int {
        // Force a real audio session so effects attach to the sink
        if (player.audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            player.setAudioSessionId(C.AUDIO_SESSION_ID_UNSET)
        }
        return player.audioSessionId
    }

    private var equalizer: android.media.audiofx.Equalizer? = null
    private var bassBoost: android.media.audiofx.BassBoost? = null
    private var virtualizer: android.media.audiofx.Virtualizer? = null

    @Synchronized
    private fun applyAudioEffects() {
        runCatching {
            val audioSessionId = requireAudioSessionId()
            releaseAudioEffects()

            if (settings.equalizerEnabled) {
                val eq = android.media.audiofx.Equalizer(0, audioSessionId)
                val gains = settings.equalizerGains
                val bandCount = eq.numberOfBands
                gains.forEachIndexed { index, gainMb ->
                    if (index < bandCount) {
                        eq.setBandLevel(index.toShort(), gainMb.toInt().toShort())
                    }
                }
                eq.enabled = true
                equalizer = eq
            }
            if (settings.bassBoostEnabled) {
                val bb = android.media.audiofx.BassBoost(0, audioSessionId)
                bb.setStrength(((settings.bassBoostStrength * 10).coerceIn(0, 1000)).toInt().toShort())
                bb.enabled = true
                bassBoost = bb
            }
            if (settings.virtualizerEnabled) {
                val virt = android.media.audiofx.Virtualizer(0, audioSessionId)
                virt.setStrength(((settings.virtualizerStrength * 10).coerceIn(0, 1000)).toInt().toShort())
                virt.enabled = true
                virtualizer = virt
            }
        }
    }

    @Synchronized
    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    fun refreshAudioEffects() = applyAudioEffects()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    private fun initializeSleepTimer() {
        sleepTimerJob = serviceScope.launch {
            while (true) {
                if (settings.isSleepTimerActive) {
                    val remaining = settings.sleepTimerEndsAt - System.currentTimeMillis()
                    if (remaining <= 0) {
                        if (settings.sleepTimerFadeOut) {
                            fadeOutAndPause(2500)
                        } else {
                            player.pause()
                        }
                        settings.clearSleepTimer()
                    }
                }
                delay(1000)
            }
        }
    }

    private suspend fun fadeOutAndPause(durationMs: Long) {
        val baseVolume = player.volume
        val steps = 40
        for (i in steps downTo 0) {
            player.volume = baseVolume * (i.toFloat() / steps)
            delay(durationMs / steps)
        }
        player.pause()
        player.volume = baseVolume
    }

    fun updateEqualizerGains(gains: FloatArray) {
        settings.equalizerGains = gains
        settings.equalizerEnabled = true
        applyAudioEffects()
    }

    override fun onGetSession(
        controllerInfo: androidx.media3.session.MediaSession.ControllerInfo,
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val shouldStop = !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_IDLE
        if (shouldStop) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        settings.removeOnChangeListener(settingsListener)
        sleepTimerJob?.cancel()
        releaseAudioEffects()
        playerInfo.saveTracks()
        mediaSession?.release()
        mediaSession = null
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}