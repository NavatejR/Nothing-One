package com.nothing.one.feature.music.di

import androidx.media3.common.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerInfo @Inject constructor() {
    var lastMediaItems: List<MediaItem> = emptyList()
    var lastTrackIds: List<Long> = emptyList()
    var lastTrackIndex: Int = 0

    fun saveTracks() {
        lastTrackIds = lastMediaItems.mapNotNull { it.mediaId.toLongOrNull() }
    }

    fun fastClear() {
        lastMediaItems = emptyList()
        lastTrackIds = emptyList()
        lastTrackIndex = 0
    }
}