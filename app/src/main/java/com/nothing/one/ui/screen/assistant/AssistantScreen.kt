package com.nothing.one.ui.screen.assistant

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.nothing.one.AssistantViewModel
import com.nothing.one.speech.AssistantState
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixBadge
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.orb.OrbState
import com.nothing.one.ui.orb.ShaderOrb
import com.nothing.one.ui.theme.NothingRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onNavigate: (String) -> Unit,
) {
    val orbState by viewModel.orbState.collectAsState()
    val partial by viewModel.partialText.collectAsState()
    val finalText by viewModel.finalResultText.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val aiBusy by viewModel.aiBusy.collectAsState()
    val error by viewModel.lastError.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val reducedMotion by viewModel.reducedMotion.collectAsState()
    val aiAvailable by viewModel.aiAvailability.available.collectAsState()

    val speechState by viewModel.speech.state.collectAsState()
    val displayState = if (speechState == AssistantState.SLEEPING) {
        OrbState.SLEEPING
    } else {
        orbState
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedDot(size = 8.dp)
                Spacer(Modifier.width(10.dp))
                DotMatrixText(text = "ASSISTANT", style = MaterialTheme.typography.headlineSmall)
            }
            DotGridDivider()

            Spacer(Modifier.height(28.dp))
            ShaderOrb(
                state = displayState,
                sizeDp = 220.dp,
                pinnedInput = micLevel,
                reduceMotion = reducedMotion,
                contentDescription = "Assistant orb",
                modifier = Modifier.clickable {
                    when (displayState) {
                        OrbState.LISTENING -> viewModel.stopListening()
                        OrbState.SPEAKING -> viewModel.speak("")
                        else -> viewModel.startListening()
                    }
                },
            )

            Spacer(Modifier.height(24.dp))
            DotMatrixText(
                text = when (displayState) {
                    OrbState.LISTENING -> "LISTENING"
                    OrbState.THINKING -> "THINKING"
                    OrbState.SPEAKING -> "SPEAKING"
                    OrbState.SLEEPING -> "SLEEPING"
                    OrbState.IDLE -> "IDLE"
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (displayState == OrbState.LISTENING) NothingRed
                else MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    displayState == OrbState.LISTENING && partial.isNotBlank() -> partial
                    displayState == OrbState.SPEAKING -> summary
                    displayState == OrbState.SLEEPING -> "SAY SOMETHING OR TAP THE ORB"
                    else -> "TAP THE ORB TO DICTATE"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            // Engine status — same dot-matrix badge language as Settings.
            DotMatrixBadge(
                text = if (aiAvailable) "LOCAL ENGINE: ON" else "LOCAL ENGINE: OFF",
                color = if (aiAvailable) MaterialTheme.colorScheme.surfaceVariant else NothingRed,
                textColor = if (aiAvailable) MaterialTheme.colorScheme.onSurface else Color.Black,
            )

            Spacer(Modifier.height(12.dp))

            // Actions on the last dictation
            if (finalText.isNotBlank() && displayState == OrbState.IDLE) {
                Text(
                    text = finalText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.saveDictationAsNote() },
                        modifier = Modifier.weight(1f),
                    ) {
                        DotMatrixText("SAVE AS NOTE", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { viewModel.appendDictationToJournal() },
                        modifier = Modifier.weight(1f),
                    ) {
                        DotMatrixText("ADD TO TODAY", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.summarizeAloud() },
                    enabled = !aiBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DotMatrixText(
                        text = if (aiBusy) "THINKING…" else "SUMMARIZE ALOUD",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = NothingRed)
            }

            Spacer(Modifier.height(24.dp))
        }

    }
}
