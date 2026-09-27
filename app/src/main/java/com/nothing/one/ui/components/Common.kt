package com.nothing.one.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nothing.one.ui.theme.DotMatrixFont
import com.nothing.one.ui.theme.NothingRed

@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = Color.White,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontFamily = DotMatrixFont),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** NothingOS glyph signature: a small red dot. */
@Composable
fun RedDot(modifier: Modifier = Modifier, size: Dp = 6.dp, color: Color = NothingRed) {
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .size(size)
    )
}

/**
 * Row of small dots — the NothingOS divider. Drawn on a Canvas so the dot
 * count adapts to the width at a fixed 12dp period.
 */
@Composable
fun DotGridDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
    dotRadius: Dp = 1.dp,
    period: Dp = 12.dp,
) {
    Canvas(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val r = dotRadius.toPx()
        val step = period.toPx()
        var x = 0f
        val y = size.height / 2f
        while (x <= size.width) {
            drawCircle(color = color, radius = r, center = Offset(x, y))
            x += step
        }
    }
}

/** Uppercase dot-matrix section label with a red dot prefix. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .background(NothingRed, RoundedCornerShape(50))
                .padding(2.dp)
        )
        Spacer(Modifier.width(6.dp))
        DotMatrixText(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** Squared dot-matrix badge chip — NothingOS tag style. */
@Composable
fun DotMatrixBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceElevated(),
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = DotMatrixFont),
            color = textColor,
        )
    }
}

private fun androidx.compose.material3.ColorScheme.surfaceElevated(): Color = surfaceVariant

