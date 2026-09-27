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

    /** Display name of the codec/container, e.g. FLAC, M4A, MP3. */
    val formatLabel: String
        get() = when (extension) {
            "mp4" -> "M4A"
            "aif" -> "AIFF"
            "dff", "dsf" -> "DSD"
            else -> extension.uppercase()
        }

    /** Formats ExoPlayer decodes bit-perfectly — no lossy transform. */
    val isLossless: Boolean
        get() = extension in setOf("flac", "wav", "alac", "aiff", "aif", "dsf", "dff")

    /** One-line quality summary for the Now Playing screen. */
    val qualityLabel: String
        get() = buildString {
            append(formatLabel)
            if (sampleRate >= 1000) {
                append(" · ")
                append("%.1fk".format(sampleRate / 1000f))
            }
            if (isLossless) {
                append(" · LOSSLESS")
            } else if (bitrate > 0) {
                append(" · ")
                append("${bitrate / 1000}kbps")
            }
        }
}

fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatDurationMs(durationMs: Long): String =
    formatDuration((durationMs / 1000).toInt())