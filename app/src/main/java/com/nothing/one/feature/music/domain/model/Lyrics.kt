package com.nothing.one.feature.music.domain.model

data class LyricsLine(
    val timestampMs: Long,
    val text: String,
)

data class Lyrics(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val offsetMs: Long = 0L,
    val lines: List<LyricsLine>,
    val sourceFile: String? = null,
) {
    companion object {
        val Empty = Lyrics(lines = emptyList())
    }
}