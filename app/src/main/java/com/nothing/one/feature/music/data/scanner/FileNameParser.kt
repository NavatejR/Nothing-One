package com.nothing.one.feature.music.data.scanner

/**
 * Parses what a filename usually tells you. Files found on the filesystem
 * (not in MediaStore) have no tags, so the scanner extracts "Artist - Title"
 * patterns, leading track numbers and "(Official Video)" noise — the same
 * conventions every ripper and downloader uses.
 */
object FileNameParser {

    // Noisy segments that never belong in a title.
    private val NOISE = listOf(
        Regex("\\((official\\s+)?(music\\s+)?video\\)", RegexOption.IGNORE_CASE),
        Regex("\\((official\\s+)?audio\\)", RegexOption.IGNORE_CASE),
        Regex("\\(lyrics?(\\s+video)?\\)", RegexOption.IGNORE_CASE),
        Regex("\\[(official\\s+)?(music\\s+)?video\\]", RegexOption.IGNORE_CASE),
        Regex("\\[(official\\s+)?audio\\]", RegexOption.IGNORE_CASE),
        Regex("\\[lyrics?(\\s+video)?\\]", RegexOption.IGNORE_CASE),
        Regex("\\(hq\\)|\\[hq\\]|\\(hd\\)|\\[hd\\]", RegexOption.IGNORE_CASE),
        Regex("\\(4k\\)|\\[4k\\]", RegexOption.IGNORE_CASE),
    )

    data class Parsed(
        val artist: String?,
        val title: String,
        val trackNumber: Int?,
    )

    fun parse(fileName: String): Parsed {
        var work = fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .trim()

        NOISE.forEach { work = work.replace(it, "") }
        work = work.trim().trimStart('-', '–', ' ').trim()

        // Leading track number: "07 - ...", "07. ...", "07 ...", "07_..."
        var trackNumber: Int? = null
        val leading = Regex("^(\\d{1,3})[\\s._\\-]+(.*)$")
        leading.find(work)?.let { m ->
            trackNumber = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..999 }
            if (trackNumber != null) work = m.groupValues[2].trim()
        }

        // "Artist - Title" split on the first separator not inside brackets.
        var artist: String? = null
        val split = Regex("^(.{1,80}?)\\s+[-–—]\\s+(.+)$")
        split.find(work)?.let { m ->
            val left = m.groupValues[1].trim()
            val right = m.groupValues[2].trim()
            if (left.isNotEmpty() && right.isNotEmpty()) {
                artist = left
                work = right
            }
        }

        // Anything left in ALL CAPS with no spaces is likely a code, keep it.
        return Parsed(
            artist = artist?.takeIf { it.isNotBlank() },
            title = work.ifBlank { fileName.substringBeforeLast('.') },
            trackNumber = trackNumber,
        )
    }
}
