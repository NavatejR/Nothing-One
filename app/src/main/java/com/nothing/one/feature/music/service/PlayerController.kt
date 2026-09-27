package com.nothing.one.feature.music.service

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nothing.one.feature.music.domain.model.Track
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

class PlayerController(context: Context) {

    private val appContext = context.applicationContext
    private var mediaController: MediaController? = null
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _isPlaying.value = mediaController?.isPlaying ?: false
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncNow()
        }

        override fun onPlaylistMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
            syncNow()
        }

        override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
            syncNow()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            _currentPositionMs.value = newPosition.positionMs.coerceAtLeast(0)
            _durationMs.value = (mediaController?.duration ?: 0L).coerceAtLeast(0L)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncNow()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffled.value = shuffleModeEnabled
        }
    }

    private fun syncNow() {
        val controller = mediaController ?: return
        _currentTrackIndex.value = controller.currentMediaItemIndex
        _currentPositionMs.value = controller.currentPosition
        _durationMs.value = controller.duration.coerceAtLeast(0L)

        val items = (0 until controller.mediaItemCount).mapNotNull { index ->
            controller.getMediaItemAt(index).toTrack()
        }
        _queue.value = items
        _currentTrack.value = controller.currentMediaItem?.toTrack()
        _repeatMode.value = controller.repeatMode
        _isShuffled.value = controller.shuffleModeEnabled
        _isPlaying.value = controller.isPlaying
    }

    fun forceSyncPosition() {
        val controller = mediaController ?: return
        _currentPositionMs.value = controller.currentPosition
        _durationMs.value = controller.duration.coerceAtLeast(0L)
    }

    suspend fun connect() {
        if (mediaController != null) return
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java),
        )
        val controller = buildController(sessionToken)
        mediaController = controller
        controller.addListener(listener)
        _isConnected.value = true
        syncNow()
    }

    private suspend fun buildController(sessionToken: SessionToken): MediaController =
        suspendCoroutine { cont ->
            val future = MediaController.Builder(appContext, sessionToken).buildAsync()
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { cont.resume(it) }
                        .onFailure { cont.resumeWithException(it) }
                },
                mainExecutor,
            )
        }

    fun play() = mediaController?.play()
    fun pause() = mediaController?.pause()

    fun playPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun next() {
        mediaController?.seekToNext()
    }

    fun previous() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000) {
            controller.seekTo(0)
        } else {
            controller.seekToPrevious()
        }
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val modes = intArrayOf(
            Player.REPEAT_MODE_OFF,
            Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ALL,
        )
        val next = modes[(controller.repeatMode + 1) % modes.size]
        controller.repeatMode = next
        _repeatMode.value = next
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        _isShuffled.value = controller.shuffleModeEnabled
    }

    fun playTrack(track: Track, tracks: List<Track>) {
        val controller = mediaController ?: return
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        setPlaylist(tracks, index)
    }

    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        setPlaylist(tracks, startIndex)
    }

    private fun setPlaylist(tracks: List<Track>, startIndex: Int) {
        val controller = mediaController ?: return
        if (tracks.isEmpty()) return
        controller.setMediaItems(
            tracks.map { it.toMediaItem() },
            startIndex.coerceIn(0, tracks.lastIndex),
            0L,
        )
        controller.prepare()
        controller.play()
        syncNow()
    }

    fun addToQueue(tracks: List<Track>) {
        val controller = mediaController ?: return
        controller.addMediaItems(tracks.map { it.toMediaItem() })
        syncNow()
    }

    fun playNext(track: Track) {
        val controller = mediaController ?: return
        val index = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(index, track.toMediaItem())
        syncNow()
    }

    fun moveQueueItem(from: Int, to: Int) {
        val controller = mediaController ?: return
        if (from == to) return
        controller.moveMediaItem(from, to.coerceIn(0, controller.mediaItemCount - 1))
        syncNow()
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        controller.removeMediaItem(index)
        syncNow()
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        controller.clearMediaItems()
        syncNow()
    }

    fun jumpToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        controller.seekTo(index.coerceIn(0, controller.mediaItemCount - 1), 0L)
    }

    fun release() {
        mediaController?.removeListener(listener)
        mediaController?.release()
        mediaController = null
        _isConnected.value = false
    }
}

internal fun MediaItem.toTrack(): Track? {
    val meta = mediaMetadata
    val extras = meta.extras ?: return null
    val id = extras.getLong("originalId", -1L)
    if (id < 0) return null
    return Track(
        id = id,
        title = meta.title?.toString() ?: "Unknown",
        artist = meta.artist?.toString() ?: "Unknown Artist",
        album = meta.albumTitle?.toString() ?: "Unknown Album",
        durationMs = extras.getLong("durationMs", 0L),
        path = localConfiguration?.uri?.toString() ?: "",
        albumArtUri = extras.getString("albumArtUri")?.let { android.net.Uri.parse(it) },
        folderPath = "",
        dateAdded = 0,
        trackNumber = 0,
        year = 0,
    )
}

internal fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(path)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(android.os.Bundle().apply {
                    putLong("durationMs", durationMs)
                    putString("albumArtUri", albumArtUri?.toString())
                    putLong("originalId", id)
                })
                .build()
        )
        .build()