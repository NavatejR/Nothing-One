package com.nothing.one.feature.music.data.lyrics

import android.net.Uri
import com.nothing.one.feature.music.domain.model.Lyrics
import com.nothing.one.feature.music.domain.model.LyricsLine
import com.nothing.one.feature.music.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LyricsParser {

    private val lineRegex = Regex(
        """(\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\])\s*((?:\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\]\s*)*)(.*)"""
    )
    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    suspend fun parse(track: Track): Lyrics = withContext(Dispatchers.IO) {
        val lrcFile = findLrcFile(track)
        val content = lrcFile?.takeIf { it.exists() }?.readText()
        if (content.isNullOrBlank()) return@withContext Lyrics.Empty

        val lines = mutableListOf<LyricsLine>()
        var offsetMs = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith("[ti:")) {
                title = line.removePrefix("[ti:").removeSuffix("]").trim().takeIf { it.isNotBlank() }
            } else if (line.startsWith("[ar:")) {
                artist = line.removePrefix("[ar:").removeSuffix("]").trim().takeIf { it.isNotBlank() }
            } else if (line.startsWith("[al:")) {
                album = line.removePrefix("[al:").removeSuffix("]").trim().takeIf { it.isNotBlank() }
            } else if (line.startsWith("[offset:")) {
                offsetMs = line.removePrefix("[offset:").removeSuffix("]").trim().toLongOrNull() ?: 0L
            } else {
                val match = lineRegex.matchEntire(line) ?: return@forEach
                val lyricText = match.groupValues[3].trim()
                val allTimestamps = match.groupValues[1] + match.groupValues[2]
                timestampRegex.findAll(allTimestamps).forEach { ts ->
                    val minutes = ts.groupValues[1].toLongOrNull() ?: return@forEach
                    val seconds = ts.groupValues[2].toLongOrNull() ?: return@forEach
                    val fracMs = ts.groupValues[3].let {
                        if (it.isEmpty()) 0L
                        else it.padEnd(3, '0').take(3).toLong()
                    }
                    val millis = minutes * 60_000 + seconds * 1000 + fracMs + offsetMs
                    lines += LyricsLine(timestampMs = millis, text = lyricText)
                }
            }
        }

        lines.sortBy { it.timestampMs }

        Lyrics(
            title = title,
            artist = artist,
            album = album,
            offsetMs = offsetMs,
            lines = lines,
            sourceFile = lrcFile?.absolutePath,
        )
    }

    private fun findLrcFile(track: Track): File? {
        val baseFile = File(track.path)
        val candidates = listOf(
            baseFile.resolveSibling(track.title + ".lrc"),
            baseFile.resolveSibling(baseFile.nameWithoutExtension + ".lrc"),
            baseFile.resolveSibling(baseFile.nameWithoutExtension + ".LRC"),
        )
        return candidates.firstOrNull { it.exists() }
    }
}