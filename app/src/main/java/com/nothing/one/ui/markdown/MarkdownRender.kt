package com.nothing.one.ui.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nothing.one.ui.theme.NothingRed

/** Colors used by both the editor colorizer and the read-only renderer. */
data class MarkdownColors(
    val heading: Color,
    val accent: Color,
    val muted: Color,
    val code: Color,
    val checked: Color,
) {
    companion object {
        @Composable
        fun default(): MarkdownColors = MarkdownColors(
            heading = MaterialTheme.colorScheme.onSurface,
            accent = NothingRed,
            muted = MaterialTheme.colorScheme.onSurfaceVariant,
            code = MaterialTheme.colorScheme.onSurfaceVariant,
            checked = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Read-only Markdown renderer for previews. Headings shrink, quotes indent,
 * checklist rows are tappable and toggle the stored source via [onToggleLine].
 */
@Composable
fun MarkdownBody(
    body: String,
    colors: MarkdownColors = MarkdownColors.default(),
    onToggleLine: ((lineStart: Int) -> Unit)? = null,
) {
    Column {
        Markdown.parse(body).forEach { block ->
            when (block) {
                is Markdown.Block.Heading -> Text(
                    text = annotatedSpans(block.text, colors, boldAll = true),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = colors.heading,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                is Markdown.Block.Paragraph -> if (block.text.isNotBlank()) Text(
                    text = annotatedSpans(block.text, colors),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is Markdown.Block.Quote -> Row {
                    Text("▍ ", color = colors.accent)
                    Text(
                        text = annotatedSpans(block.text, colors, italicAll = true),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.muted,
                    )
                }
                is Markdown.Block.ListItem -> Row {
                    Text("•  ", color = colors.accent)
                    Text(
                        text = annotatedSpans(block.text, colors),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is Markdown.Block.CheckItem -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = onToggleLine?.let { cb ->
                        Modifier.clickable {
                            cb(Markdown.lineStartOf(body, block))
                        }
                    } ?: Modifier,
                ) {
                    Text(
                        text = if (block.checked) "☒ " else "☐ ",
                        color = if (block.checked) colors.checked else colors.accent,
                    )
                    Text(
                        text = annotatedSpans(block.text, colors),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (block.checked) colors.checked else Color.Unspecified,
                    )
                }
            }
        }
    }
}

/** Char offset where [block] starts in the source body (for checkbox taps). */
fun Markdown.lineStartOf(body: String, block: Markdown.Block): Int {
    var index = 0
    for (line in body.split('\n')) {
        if (Markdown.parseLine(line) == block) return index
        index += line.length + 1
    }
    return 0
}

/** Inline spans with weight/slant/code styling applied. */
private fun annotatedSpans(
    text: String,
    colors: MarkdownColors,
    boldAll: Boolean = false,
    italicAll: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    Markdown.spans(text).forEach { span ->
        when (span) {
            is Markdown.Span.Text -> append(span.text)
            is Markdown.Span.Bold -> {
                val start = length
                append(span.text)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
            }
            is Markdown.Span.Italic -> {
                val start = length
                append(span.text)
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
            }
            is Markdown.Span.Code -> {
                val start = length
                append(span.text)
                addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, color = colors.code),
                    start, length,
                )
            }
        }
    }
    if (boldAll) addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, length)
    if (italicAll) addStyle(SpanStyle(fontStyle = FontStyle.Italic), 0, length)
}

/**
 * Colors the Markdown source inside the TextField: markers go muted, styled
 * text keeps its weight/slant, check marks turn red. The user edits the
 * source; this is a live lens over it. Still used as the fallback lens and
 * for the cursor line inside [livePreviewTransform].
 */
fun colorizeSource(text: String, colors: MarkdownColors): AnnotatedString = buildAnnotatedString {
    append(text)
    // Walk lines with offsets.
    var offset = 0
    for (line in text.split('\n')) {
        val block = Markdown.parseLine(line)
        when (block) {
            is Markdown.Block.Heading -> {
                addStyle(
                    SpanStyle(color = colors.heading, fontWeight = FontWeight.Bold),
                    offset, offset + line.length,
                )
            }
            is Markdown.Block.Quote -> addStyle(
                SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic),
                offset, offset + line.length,
            )
            is Markdown.Block.CheckItem -> {
                val markerLen = line.length - block.text.length
                addStyle(
                    SpanStyle(color = if (block.checked) colors.checked else colors.accent),
                    offset, offset + markerLen,
                )
                addStyle(
                    SpanStyle(color = if (block.checked) colors.checked else Color.Unspecified),
                    offset + markerLen, offset + line.length,
                )
            }
            else -> {}
        }
        // Inline styles over the whole line's text region.
        Markdown.spans(line).forEach { span ->
            val local = when (span) {
                is Markdown.Span.Text -> return@forEach
                is Markdown.Span.Bold -> span.text
                is Markdown.Span.Italic -> span.text
                is Markdown.Span.Code -> span.text
            }
            val idx = line.indexOf(local)
            if (idx >= 0) {
                val style = when (span) {
                    is Markdown.Span.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                    is Markdown.Span.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                    is Markdown.Span.Code -> SpanStyle(fontFamily = FontFamily.Monospace, color = colors.code)
                    is Markdown.Span.Text -> null
                }
                if (style != null) addStyle(style, offset + idx, offset + idx + local.length)
            }
        }
        offset += line.length + 1
    }
}

// ---------------------------------------------------------------------------
// Obsidian-style live preview
// ---------------------------------------------------------------------------

/** A styled range in display coordinates, applied after the text is built. */
private class StyleRange(val start: Int, val end: Int, val style: SpanStyle)

/**
 * Appends [body] to [sb], hiding inline markers (the `**` around bold text
 * and friends) and recording styled runs. Every source index covered by the
 * body — including hidden markers — is entered into [map] at [bodyStart].
 * Markers collapse onto the first display column of their content, so a
 * caret inside them parks at the content edge.
 */
private fun appendBodyHidingMarkers(
    sb: StringBuilder,
    styles: MutableList<StyleRange>,
    body: String,
    bodyStart: Int,
    map: IntArray,
    colors: MarkdownColors,
) {
    var index = 0
    while (index < body.length) {
        val match = Markdown.findInline(body, index)
        if (match == null) {
            for (i in index..body.length) map[bodyStart + i] = sb.length + (i - index)
            sb.append(body.substring(index))
            break
        }
        if (match.open.first > index) {
            // Plain segment before the marker: mapped 1:1.
            for (i in index..match.open.first) map[bodyStart + i] = sb.length + (i - index)
            sb.append(body.substring(index, match.open.first))
        }
        // Open marker: hidden — collapse onto the content's display column.
        for (i in match.open) map[bodyStart + i] = sb.length
        // Content: mapped 1:1 and styled.
        val content = body.substring(match.content)
        for (i in match.content) map[bodyStart + i] = sb.length + (i - match.content.first)
        val ds = sb.length
        sb.append(content)
        val style = when (match.span) {
            is Markdown.Span.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            is Markdown.Span.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            is Markdown.Span.Code -> SpanStyle(fontFamily = FontFamily.Monospace, color = colors.code)
            is Markdown.Span.Text -> null
        }
        if (style != null && sb.length > ds) styles += StyleRange(ds, sb.length, style)
        // Close marker: hidden — collapse onto the next content column.
        for (i in match.close) map[bodyStart + i] = sb.length
        index = match.close.last + 1
    }
    if (body.isEmpty()) map[bodyStart] = sb.length
}

/**
 * Obsidian-style live preview for the note editor: every line away from the
 * cursor renders actively — `# Title` becomes a large bold title, `**x**`
 * becomes bold x with the asterisks gone, `- [ ] task` becomes an unchecked
 * box — while the line the caret sits on keeps its raw, colorized Markdown
 * so editing stays predictable. A real [OffsetMapping] keeps the caret and
 * selections anchored to the untouched source; markers collapse onto the
 * display column of the text they wrapped, exactly like Obsidian.
 */
fun livePreviewTransform(
    text: String,
    cursor: Int,
    colors: MarkdownColors,
): TransformedText {
    val sb = StringBuilder(text.length)
    val styles = mutableListOf<StyleRange>()
    // source offset -> display offset (identity where nothing is hidden).
    val map = IntArray(text.length + 1)

    val clampedCursor = cursor.coerceIn(0, text.length)
    val cursorLineStart = text.lastIndexOf('\n', (clampedCursor - 1).coerceAtLeast(0)) + 1
    val cursorLineEnd = text.indexOf('\n', clampedCursor).let { if (it == -1) text.length else it }

    var srcIndex = 0
    for (line in text.split('\n')) {
        val lineStart = srcIndex
        val lineEnd = srcIndex + line.length
        val isCursorLine = clampedCursor >= lineStart && clampedCursor <= lineEnd
        val lineDisplayStart = sb.length

        if (isCursorLine || line.isBlank()) {
            // Verbatim: source and display agree character for character.
            for (i in lineStart..lineEnd) map[i] = sb.length + (i - lineStart)
            sb.append(line)
            if (isCursorLine && line.isNotEmpty()) {
                // The colorize lens on the raw line: roles stay readable.
                val base = sb.length - line.length
                when (val block = Markdown.parseLine(line)) {
                    is Markdown.Block.Heading -> styles += StyleRange(
                        base, base + line.length,
                        SpanStyle(color = colors.heading, fontWeight = FontWeight.Bold),
                    )
                    is Markdown.Block.Quote -> styles += StyleRange(
                        base, base + line.length,
                        SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic),
                    )
                    is Markdown.Block.CheckItem -> {
                        val markerLen = line.length - block.text.length
                        styles += StyleRange(
                            base, base + markerLen,
                            SpanStyle(color = if (block.checked) colors.checked else colors.accent),
                        )
                        if (block.checked) {
                            styles += StyleRange(
                                base + markerLen, base + line.length,
                                SpanStyle(color = colors.checked),
                            )
                        }
                    }
                    else -> Unit
                }
                Markdown.spans(line).forEach { span ->
                    val local = when (span) {
                        is Markdown.Span.Text -> return@forEach
                        is Markdown.Span.Bold -> span.text
                        is Markdown.Span.Italic -> span.text
                        is Markdown.Span.Code -> span.text
                    }
                    val idx = line.indexOf(local)
                    if (idx >= 0) {
                        val style = when (span) {
                            is Markdown.Span.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                            is Markdown.Span.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                            is Markdown.Span.Code -> SpanStyle(
                                fontFamily = FontFamily.Monospace, color = colors.code,
                            )
                            is Markdown.Span.Text -> null
                        }
                        if (style != null) {
                            styles += StyleRange(base + idx, base + idx + local.length, style)
                        }
                    }
                }
            }
        } else {
            when (val block = Markdown.parseLine(line)) {
                is Markdown.Block.Heading -> {
                    val prefixLen = line.length - block.text.length
                    for (i in lineStart..lineStart + prefixLen) map[i] = sb.length
                    appendBodyHidingMarkers(sb, styles, block.text, lineStart + prefixLen, map, colors)
                    styles += StyleRange(
                        lineDisplayStart, sb.length,
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = colors.heading,
                            fontSize = when (block.level) {
                                1 -> 22.sp
                                2 -> 19.sp
                                else -> 17.sp
                            },
                        ),
                    )
                }
                is Markdown.Block.Quote -> {
                    val prefixLen = line.length - block.text.length
                    sb.append("▍ ")
                    for (i in lineStart..lineStart + prefixLen) map[i] = sb.length
                    appendBodyHidingMarkers(sb, styles, block.text, lineStart + prefixLen, map, colors)
                    styles += StyleRange(
                        lineDisplayStart, sb.length,
                        SpanStyle(fontStyle = FontStyle.Italic, color = colors.muted),
                    )
                }
                is Markdown.Block.CheckItem -> {
                    val prefixLen = line.length - block.text.length
                    sb.append(if (block.checked) "☒ " else "☐ ")
                    for (i in lineStart..lineStart + prefixLen) map[i] = sb.length
                    appendBodyHidingMarkers(sb, styles, block.text, lineStart + prefixLen, map, colors)
                    if (block.checked) {
                        styles += StyleRange(
                            lineDisplayStart, sb.length,
                            SpanStyle(color = colors.checked),
                        )
                    }
                }
                is Markdown.Block.ListItem -> {
                    val prefixLen = line.length - block.text.length
                    sb.append("•  ")
                    for (i in lineStart..lineStart + prefixLen) map[i] = sb.length
                    appendBodyHidingMarkers(sb, styles, block.text, lineStart + prefixLen, map, colors)
                }
                is Markdown.Block.Paragraph -> {
                    for (i in lineStart..lineEnd) map[i] = sb.length + (i - lineStart)
                    appendBodyHidingMarkers(sb, styles, line, lineStart, map, colors)
                }
            }
        }

        // The separating newline (or end of text) keeps its own column.
        if (lineEnd < text.length) {
            map[lineEnd] = sb.length
            sb.append('\n')
        } else {
            map[lineEnd] = sb.length
        }
        srcIndex = lineEnd + 1
    }

    val annotated = buildAnnotatedString {
        append(sb.toString())
        styles.forEach { if (it.end > it.start) addStyle(it.style, it.start, it.end) }
    }
    return TransformedText(annotated, LiveOffsetMapping(map))
}

/**
 * Maps source offsets to display offsets (plateaus for hidden markers) and
 * back. The inverse picks the last source index at or before the requested
 * display column — inside a hidden run the caret therefore lands on the
 * boundary next to visible content, so typing always edits real text and
 * never corrupts a marker.
 */
private class LiveOffsetMapping(private val map: IntArray) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int =
        map[offset.coerceIn(0, map.size - 1)]

    override fun transformedToOriginal(offset: Int): Int {
        for (i in map.indices.reversed()) {
            if (map[i] <= offset) return i
        }
        return 0
    }
}

/**
 * Convenience wrapper for a TextField: the transformation re-applies whenever
 * the cursor moves, so the cursor's line flips between raw and rendered the
 * way Obsidian's live preview does.
 */
fun livePreview(colors: MarkdownColors, cursorProvider: () -> Int): VisualTransformation =
    VisualTransformation { text ->
        livePreviewTransform(text.text, cursorProvider(), colors)
    }
