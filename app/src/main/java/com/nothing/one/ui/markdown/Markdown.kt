package com.nothing.one.ui.markdown

/**
 * Journal's formatting storage: plain Markdown, so notes stay portable and
 * the export stays human-readable. Supports exactly what the editor toolbar
 * exposes — headings, bold, italic, inline code, quotes, bullet lists and
 * checkbox lists — nothing more, on purpose.
 */
object Markdown {

    /** One rendered span of a line. */
    sealed interface Span {
        data class Text(val text: String) : Span
        data class Bold(val text: String) : Span
        data class Italic(val text: String) : Span
        data class Code(val text: String) : Span
    }

    /** One rendered block of a document. */
    sealed interface Block {
        val text: String

        data class Heading(val level: Int, override val text: String) : Block
        data class Paragraph(override val text: String) : Block
        data class Quote(override val text: String) : Block
        data class ListItem(override val text: String) : Block
        data class CheckItem(val checked: Boolean, override val text: String) : Block
    }

    private val CHECK = Regex("^\\s*- \\[( |x|X)\\] ?")
    private val BULLET = Regex("^\\s*- ?")
    private val HEADING = Regex("^(#{1,3}) ")
    private val QUOTE = Regex("^> ?")
    private val INLINE = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`([^`]+)`")

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Parses one line into a block. Blank lines become empty paragraphs. */
    fun parseLine(line: String): Block {
        val check = CHECK.find(line)
        if (check != null) {
            return Block.CheckItem(
                checked = check.groupValues[1] != " ",
                text = line.substring(check.value.length),
            )
        }
        val heading = HEADING.find(line)
        if (heading != null) {
            return Block.Heading(heading.groupValues[1].length, line.substring(heading.value.length))
        }
        val quote = QUOTE.find(line)
        if (quote != null) return Block.Quote(line.substring(quote.value.length))
        val bullet = BULLET.find(line)
        if (bullet != null && line.substring(bullet.value.length).isNotBlank()) {
            return Block.ListItem(line.substring(bullet.value.length))
        }
        return Block.Paragraph(line)
    }

    /**
     * One inline marker occurrence: the span it produces plus the source
     * ranges of its open marker, content and close marker. Powers the live
     * preview's marker-hiding transform.
     */
    data class InlineMatch(
        val span: Span,
        val open: IntRange,
        val content: IntRange,
        val close: IntRange,
    )

    /** Finds the next inline marker match at or after [startIndex], or null. */
    fun findInline(text: String, startIndex: Int = 0): InlineMatch? {
        val match = INLINE.find(text, startIndex) ?: return null
        val (bold, italic, code) = match.destructured
        val span = when {
            bold.isNotEmpty() -> Span.Bold(bold)
            italic.isNotEmpty() -> Span.Italic(italic)
            else -> Span.Code(code)
        }
        val contentLen = when (span) {
            is Span.Bold -> bold.length
            is Span.Italic -> italic.length
            is Span.Code -> code.length
            is Span.Text -> 0
        }
        val markerLen = (match.range.last - match.range.first + 1 - contentLen) / 2
        val open = match.range.first until match.range.first + markerLen
        val content = open.last + 1..open.last + contentLen
        val close = content.last + 1..match.range.last
        return InlineMatch(span, open, content, close)
    }

    /** Splits inline text into styled spans (bold / italic / code). */
    fun spans(text: String): List<Span> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<Span>()
        var plain = StringBuilder()
        var index = 0
        while (index < text.length) {
            val match = INLINE.find(text, index)
            if (match == null) {
                plain.append(text.substring(index))
                break
            }
            if (match.range.first > index) plain.append(text.substring(index, match.range.first))
            if (plain.isNotEmpty()) {
                result += Span.Text(plain.toString())
                plain = StringBuilder()
            }
            val (bold, italic, code) = match.destructured
            result += when {
                bold.isNotEmpty() -> Span.Bold(bold)
                italic.isNotEmpty() -> Span.Italic(italic)
                else -> Span.Code(code)
            }
            index = match.range.last + 1
        }
        if (plain.isNotEmpty()) result += Span.Text(plain.toString())
        return result
    }

    /** Full document parse, preserving blank lines as empty paragraphs. */
    fun parse(body: String): List<Block> =
        body.split('\n').map { parseLine(it) }

    // ------------------------------------------------------------------
    // Transformations (toolbar actions, cursor-aware)
    // ------------------------------------------------------------------

    /**
     * Wraps the current selection (or the word at the cursor) in [marker].
     * Returns the new text and where the cursor should land. A second press
     * on already-wrapped text unwraps it.
     */
    fun toggleWrap(text: String, cursor: Int, marker: String): Edit {
        val (start, end) = selectionBounds(text, cursor, marker)
        val already = start < end &&
            text.regionMatches(start, marker, 0, marker.length) &&
            text.regionMatches(end - marker.length, marker, 0, marker.length) &&
            end - start >= 2 * marker.length
        if (already) {
            // Unwrap: strip the markers that are part of the selection.
            val newText = text.removeRange(end - marker.length, end)
                .removeRange(start, start + marker.length)
            return Edit(newText, start.coerceAtMost(newText.length))
        }
        val newText = text.substring(0, end) + marker + text.substring(end)
        val withOpen = text.substring(0, start) + marker + newText.substring(start)
        val newCursor = if (start == end) start + marker.length else end + 2 * marker.length
        return Edit(withOpen, newCursor.coerceAtMost(withOpen.length))
    }

    /**
     * Toggles a line prefix: headings cycle levels, quotes/bullets/checks
     * add-or-remove themselves. Operates on every selected line.
     */
    fun toggleLinePrefix(text: String, cursor: Int, kind: PrefixKind): Edit {
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1
        val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val (newLine, newCursor) = when (kind) {
            PrefixKind.HEADING -> {
                val existing = HEADING.find(line)
                when {
                    existing == null -> "# $line" to cursor + 2
                    existing.groupValues[1].length < 3 ->
                        "#" + line to cursor + 1
                    else -> line.substring(existing.value.length) to
                        (cursor - existing.value.length).coerceAtLeast(lineStart)
                }
            }
            PrefixKind.QUOTE -> togglePlainPrefix(line, cursor, "> ")
            PrefixKind.BULLET -> togglePlainPrefix(line, cursor, "- ")
            PrefixKind.CHECK -> togglePlainPrefix(line, cursor, "- [ ] ")
        }
        return Edit(
            text.substring(0, lineStart) + newLine + text.substring(lineEnd),
            newCursor.coerceAtMost(text.length - (line.length - newLine.length)).coerceAtLeast(0),
        )
    }

    /** Toggles a checkbox on the given line (by char index); returns new text. */
    fun toggleCheckbox(text: String, tapLineStart: Int): String {
        val lineEnd = text.indexOf('\n', tapLineStart).let { if (it == -1) text.length else it }
        val line = text.substring(tapLineStart, lineEnd)
        val match = CHECK.find(line) ?: return text
        val toggled = if (match.groupValues[1] == " ") "[x]" else "[ ]"
        val newLine = line.replaceRange(match.range, "- $toggled ")
        return text.substring(0, tapLineStart) + newLine + text.substring(lineEnd)
    }

    /** True when the line at [index] starts a checklist item. */
    fun isCheckLine(text: String, index: Int): Boolean {
        if (index < 0 || index > text.length) return false
        val lineStart = text.lastIndexOf('\n', (index - 1).coerceAtLeast(0)) + 1
        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        return CHECK.containsMatchIn(text.substring(lineStart, lineEnd))
    }

    private fun togglePlainPrefix(line: String, cursor: Int, prefix: String): Pair<String, Int> {
        return if (line.startsWith(prefix)) {
            line.substring(prefix.length) to (cursor - prefix.length).coerceAtLeast(0)
        } else {
            "$prefix$line" to cursor + prefix.length
        }
    }

    /**
     * Expands a bare cursor to the word around it so wrap works mid-word,
     * then absorbs adjacent [marker] runs into the bounds so a second press
     * detects "already wrapped" instead of double-wrapping.
     */
    private fun selectionBounds(text: String, cursor: Int, marker: String): Pair<Int, Int> {
        val clamped = cursor.coerceIn(0, text.length)
        var start = clamped
        var end = clamped
        if (start < text.length && !text[start].isWhitespace()) {
            while (start > 0 && !text[start - 1].isWhitespace()) start--
            while (end < text.length && !text[end].isWhitespace()) end++
        }
        while (
            start >= marker.length &&
            text.regionMatches(start - marker.length, marker, 0, marker.length)
        ) {
            start -= marker.length
        }
        while (
            end + marker.length <= text.length &&
            text.regionMatches(end, marker, 0, marker.length)
        ) {
            end += marker.length
        }
        return start to end
    }

    /** Result of a transform: the new text and where the cursor belongs. */
    data class Edit(val text: String, val cursor: Int)

    enum class PrefixKind { HEADING, QUOTE, BULLET, CHECK }
}
