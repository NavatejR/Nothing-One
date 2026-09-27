package com.nothing.one.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nothing.one.ui.orb.OrbState
import com.nothing.one.ui.orb.ShaderOrb
import com.nothing.one.ui.theme.DotMatrixFont
import com.nothing.one.ui.theme.NothingRed

/**
 * The dictation menu: a scrim fades in over the screen, the orb springs up
 * from [restSize] to [menuSize] — the growth animation — and a clean
 * transcript card under the orb streams the text as it is dictated. Tapping
 * the scrim or the orb stops (and saves, per the sink); CANCEL discards.
 */
@Composable
fun DictationMenu(
    orbState: OrbState,
    micLevel: Float?,
    partial: String,
    idleHint: String,
    restSize: Dp,
    menuSize: Dp,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    // The orb grows from its resting size into the menu on entry.
    val orbSize = remember { Animatable(restSize, Dp.VectorConverter) }
    LaunchedEffect(Unit) {
        orbSize.animateTo(menuSize, spring(dampingRatio = 0.7f, stiffness = 300f))
    }
    val thinking = orbState == OrbState.THINKING

    // Keep the newest words visible as the transcript grows.
    val scroll = rememberScrollState()
    LaunchedEffect(partial) { scroll.animateScrollTo(scroll.maxValue) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(onClick = onStop),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ShaderOrb(
                state = orbState,
                sizeDp = orbSize.value,
                pinnedInput = micLevel,
                reduceMotion = false,
                contentDescription = if (thinking) "Transcribing" else "Dictating — tap to stop",
                modifier = Modifier.clickable(onClick = onStop),
            )

            Spacer(Modifier.height(28.dp))

            // Transcript card — NothingOS surface, dot-matrix header, live text.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) {}
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RedDot(size = 6.dp)
                    Spacer(Modifier.width(8.dp))
                    DotMatrixText(
                        text = when {
                            thinking -> "TRANSCRIBING"
                            partial.isNotBlank() -> "TRANSCRIPT"
                            else -> "LISTENING"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    MicLevelBars(level = micLevel, active = !thinking)
                }
                DotGridDivider()
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .heightIn(min = 72.dp, max = 190.dp)
                        .verticalScroll(scroll),
                ) {
                    Text(
                        text = when {
                            partial.isNotBlank() -> partial
                            thinking -> "…"
                            else -> idleHint
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (partial.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuAction("CANCEL", muted = true, onClick = onCancel)
                MenuAction("STOP & SAVE", onClick = onStop)
            }

            Spacer(Modifier.height(10.dp))
            DotMatrixText(
                text = "OR TAP ANYWHERE TO STOP",
                style = MaterialTheme.typography.labelSmall,
                color = NothingRed.copy(alpha = 0.8f),
            )
        }
    }
}

/** Five red bars that breathe with the mic level — the NothingOS equalizer. */
@Composable
private fun MicLevelBars(level: Float?, active: Boolean) {
    val l = if (active) (level ?: 0f).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val thresholds = listOf(0.05f, 0.2f, 0.4f, 0.6f, 0.8f)
        thresholds.forEachIndexed { i, t ->
            val lit = l >= t
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (lit) 12.dp else 5.dp)
                    .background(
                        if (lit) NothingRed else NothingRed.copy(alpha = 0.25f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun MenuAction(label: String, muted: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (muted) MaterialTheme.colorScheme.surfaceVariant else NothingRed)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = DotMatrixFont),
            color = if (muted) MaterialTheme.colorScheme.onSurface else Color.Black,
        )
    }
}
