package com.nothing.one.feature.music.domain.model

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String,
    val albumArtUri: Uri? = null,
    val folderPath: String,
    val dateAdded: Long,
    val trackNumber: Int,
    val year: Int,
    val mimeType: String? = null,
    val isFavorite: Boolean = false,
    val sizeBytes: Long = 0L,
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
) {
    val displayDuration: String
        get() {
            val totalSeconds = ((durationMs / 1000).toInt()).takeIf { it > 0 } ?: 0
            return formatDuration(totalSeconds)
        }

    val extension: String
        get() = path.substringAfterLast(".", "").lowercase()
}

fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatDurationMs(durationMs: Long): String =
    formatDuration((durationMs / 1000).toInt())