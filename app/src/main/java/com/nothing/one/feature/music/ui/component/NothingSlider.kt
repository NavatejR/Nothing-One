package com.nothing.one.feature.music.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun NothingLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.15f),
    progressColor: Color = MaterialTheme.colorScheme.primary,
    height: Int = 3,
    thumbColor: Color = Color.White,
    showThumb: Boolean = true,
    draggable: Boolean = true,
    onSeek: (Float) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
) {
    val clamped = progress.coerceIn(0f, 1f)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .weight(1f)
                .height(height.dp)
        ) {
            val barHeight = size.height
            // Track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, (size.height - barHeight) / 2),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(barHeight / 2, barHeight / 2),
            )
            // Progress
            val barWidth = size.width * clamped
            if (barWidth > 0.01f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(progressColor.copy(alpha = 0.7f), progressColor)
                    ),
                    topLeft = Offset(0f, (size.height - barHeight) / 2),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2, barHeight / 2),
                )
            }
            // Thumb
            if (showThumb && barWidth > 0) {
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset((size.width * clamped).coerceAtLeast(5.dp.toPx()), size.height / 2),
                )
                drawCircle(
                    color = progressColor,
                    radius = 3.dp.toPx(),
                    center = Offset((size.width * clamped).coerceAtLeast(5.dp.toPx()), size.height / 2),
                )
            }
        }
    }
}