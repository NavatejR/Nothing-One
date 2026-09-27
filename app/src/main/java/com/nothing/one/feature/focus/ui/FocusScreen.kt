package com.nothing.one.feature.focus.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.feature.focus.FocusMode
import com.nothing.one.feature.focus.FocusViewModel
import com.nothing.one.feature.focus.domain.PomodoroEngine
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.components.SectionLabel
import com.nothing.one.ui.orb.OrbState
import com.nothing.one.ui.orb.ShaderOrb
import com.nothing.one.ui.theme.NothingRed

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun FocusScreen(viewModel: FocusViewModel = hiltViewModel()) {
    val mode by viewModel.mode.collectAsState()
    val pomodoro by viewModel.pomodoro.collectAsState()
    val timer by viewModel.timer.collectAsState()
    val stopwatch by viewModel.stopwatch.collectAsState()
    val focusBlocks by viewModel.focusBlocksToday.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RedDot(size = 8.dp)
            Spacer(Modifier.width(10.dp))
            DotMatrixText(text = "FOCUS", style = MaterialTheme.typography.headlineSmall)
        }
        DotGridDivider()

        Spacer(Modifier.height(16.dp))

        // Mode switcher — the NothingOS segmented control.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            FocusMode.entries.forEach { m ->
                val selected = m == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setMode(m) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DotMatrixText(
                        text = m.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        when (mode) {
            FocusMode.POMODORO -> PomodoroPane(
                state = pomodoro,
                focusBlocksToday = focusBlocks,
                onStart = viewModel::pomodoroStart,
                onPause = viewModel::pomodoroPause,
                onSkip = viewModel::pomodoroSkip,
                onReset = viewModel::pomodoroReset,
            )
            FocusMode.TIMER -> TimerPane(
                state = timer,
                onPreset = viewModel::setTimerMinutes,
                onStartPause = viewModel::timerStartPause,
                onReset = viewModel::timerReset,
            )
            FocusMode.STOPWATCH -> StopwatchPane(
                state = stopwatch,
                onStartPause = viewModel::stopwatchStartPause,
                onLap = viewModel::stopwatchLap,
                onReset = viewModel::stopwatchReset,
            )
        }
    }
}

@Composable
private fun PomodoroPane(
    state: PomodoroEngine.PomodoroState,
    focusBlocksToday: Int,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
) {
    // The orb mirrors the session: churning plasma while focusing, near-still
    // during breaks, dim when paused or idle.
    val orbState = when (state.phase) {
        PomodoroEngine.Phase.FOCUS -> OrbState.THINKING
        PomodoroEngine.Phase.SHORT_BREAK, PomodoroEngine.Phase.LONG_BREAK -> OrbState.SLEEPING
        PomodoroEngine.Phase.PAUSED -> OrbState.IDLE
        PomodoroEngine.Phase.IDLE -> OrbState.SLEEPING
    }

    ShaderOrb(state = orbState, sizeDp = 168.dp, contentDescription = "Focus orb")

    Spacer(Modifier.height(20.dp))

    DotMatrixText(
        text = when (state.phase) {
            PomodoroEngine.Phase.FOCUS -> "FOCUS"
            PomodoroEngine.Phase.SHORT_BREAK -> "SHORT BREAK"
            PomodoroEngine.Phase.LONG_BREAK -> "LONG BREAK"
            PomodoroEngine.Phase.PAUSED -> "PAUSED"
            PomodoroEngine.Phase.IDLE -> "READY"
        },
        style = MaterialTheme.typography.titleLarge,
        color = if (state.phase == PomodoroEngine.Phase.FOCUS) NothingRed
        else MaterialTheme.colorScheme.onSurface,
    )

    Spacer(Modifier.height(8.dp))

    DotMatrixText(
        text = formatMs(state.remainingMs),
        style = MaterialTheme.typography.displayLarge,
    )

    Spacer(Modifier.height(16.dp))

    // Blocks-since-long-break dots: filled = done, hollow = remaining.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (i < state.completedFocusBlocks % 4) NothingRed
                        else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(50),
                    ),
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    DotMatrixText(
        text = "$focusBlocksToday FOCUS BLOCKS TODAY",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(28.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PaneButton(
            label = when (state.phase) {
                PomodoroEngine.Phase.FOCUS,
                PomodoroEngine.Phase.SHORT_BREAK,
                PomodoroEngine.Phase.LONG_BREAK -> "PAUSE"
                PomodoroEngine.Phase.PAUSED -> "RESUME"
                PomodoroEngine.Phase.IDLE -> "START"
            },
            onClick = when (state.phase) {
                PomodoroEngine.Phase.IDLE -> onStart
                PomodoroEngine.Phase.PAUSED -> onStart
                else -> onPause
            },
        )
        PaneButton(label = "SKIP", muted = true, onClick = onSkip)
        PaneButton(label = "RESET", muted = true, onClick = onReset)
    }
}

@Composable
private fun TimerPane(
    state: FocusViewModel.TimerState,
    onPreset: (Int) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
) {
    SectionLabel("MINUTES")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(5, 10, 15, 25, 45).forEach { minutes ->
            val selected = state.plannedMs == minutes * 60_000L
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) NothingRed else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onPreset(minutes) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                DotMatrixText(
                    text = minutes.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    DotMatrixText(
        text = formatMs(state.remainingMs),
        style = MaterialTheme.typography.displayLarge,
        color = if (state.finished) NothingRed else MaterialTheme.colorScheme.onSurface,
    )

    if (state.finished) {
        Spacer(Modifier.height(8.dp))
        DotMatrixText(
            text = "TIME'S UP",
            style = MaterialTheme.typography.titleMedium,
            color = NothingRed,
        )
    }

    Spacer(Modifier.height(28.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PaneButton(
            label = if (state.running) "PAUSE" else "START",
            onClick = onStartPause,
        )
        PaneButton(label = "RESET", muted = true, onClick = onReset)
    }
}

@Composable
private fun StopwatchPane(
    state: FocusViewModel.StopwatchState,
    onStartPause: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
) {
    DotMatrixText(
        text = formatMs(state.elapsedMs),
        style = MaterialTheme.typography.displayLarge,
    )

    Spacer(Modifier.height(28.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PaneButton(
            label = if (state.running) "PAUSE" else "START",
            onClick = onStartPause,
        )
        PaneButton(label = "LAP", muted = true, onClick = onLap, enabled = state.running)
        PaneButton(label = "RESET", muted = true, onClick = onReset)
    }

    if (state.laps.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionLabel("LAPS")
        Spacer(Modifier.height(8.dp))
        state.laps.reversed().forEach { lap ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "LAP ${lap.index}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMs(lap.splitMs),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PaneButton(
    label: String,
    muted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        DotMatrixText(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                muted -> MaterialTheme.colorScheme.onSurface
                else -> NothingRed
            },
        )
    }
}
