package com.nothing.one.ui.screen.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nothing.one.ChatUiMessage
import com.nothing.one.ChatViewModel
import com.nothing.one.ai.AssistantMemory
import com.nothing.one.ai.AssistantTool
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.orb.OrbState
import com.nothing.one.ui.orb.ShaderOrb
import com.nothing.one.ui.theme.NothingRed

/**
 * The assistant as a conversation: local model replies, a visible memory
 * card showing exactly what context it read, and tool calls the user
 * confirms with one tap before anything is written.
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    val error by viewModel.error.collectAsState()
    val memory by viewModel.lastMemory.collectAsState()
    val aiReady by viewModel.aiReady.collectAsState()
    val orbState by viewModel.orbState.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val partial by viewModel.partialText.collectAsState()
    val finalResult by viewModel.finalResult.collectAsState()

    var input by remember { mutableStateOf("") }

    // Dictation lands in the composer for review, never sends itself.
    LaunchedEffect(finalResult) {
        if (finalResult.isNotBlank()) {
            input = finalResult.trim()
            viewModel.clearFinalResult()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, thinking, memory) {
        val last = listState.layoutInfo.totalItemsCount
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RedDot(size = 8.dp)
            Spacer(Modifier.width(10.dp))
            DotMatrixText(text = "ASSISTANT", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            // The orb mirrors dictation / inference / speech at a glance.
            ShaderOrb(
                state = orbState,
                sizeDp = 30.dp,
                pinnedInput = 0f,
                reduceMotion = false,
                contentDescription = "Assistant state orb",
            )
            Spacer(Modifier.width(4.dp))
            if (messages.isNotEmpty()) {
                IconButton(onClick = viewModel::clearChat) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Clear conversation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DotGridDivider()

        if (!aiReady) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "LOCAL ENGINE OFF — copy gemma3-1b-it-int4.task into the app assets to enable replies.",
                style = MaterialTheme.typography.bodySmall,
                color = NothingRed,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = "Everything you say stays on this device. Ask about your notes, " +
                        "mood, calendar or focus — or let me do things for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            items(messages, key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    onRunTool = { viewModel.runTool(message.id, message.tool!!) },
                )
            }
            if (memory != null) {
                item(key = "memory") { MemoryCard(memory!!) }
            }
            if (thinking) {
                item(key = "thinking") {
                    Text(
                        text = "THINKING…",
                        style = MaterialTheme.typography.labelMedium,
                        color = NothingRed,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }

        AnimatedVisibility(visible = listening, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = partial.ifBlank { "LISTENING…" },
                style = MaterialTheme.typography.bodySmall,
                color = if (partial.isBlank()) NothingRed else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = NothingRed,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NothingRed,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = NothingRed,
                ),
            )
            Spacer(Modifier.width(8.dp))
            // Mic: tap to dictate, tap again to stop.
            IconButton(onClick = { if (listening) viewModel.stopDictation() else viewModel.startDictation() }) {
                Icon(
                    imageVector = if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = if (listening) "Stop dictation" else "Dictate",
                    tint = if (listening) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !thinking,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank() && !thinking) NothingRed
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MessageRow(message: ChatUiMessage, onRunTool: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (message.isUser) NothingRed.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surface,
                )
                .then(
                    if (message.isUser) Modifier
                    else Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        RoundedCornerShape(10.dp),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        message.tool?.let { ToolCard(it, message.toolDone, onRunTool) }
    }
}

@Composable
private fun ToolCard(tool: AssistantTool, done: Boolean, onRun: () -> Unit) {
    val label = when (tool) {
        is AssistantTool.CreateNote -> "CREATE NOTE — ${tool.title}"
        is AssistantTool.AddEvent -> "ADD EVENT — ${tool.title} · ${tool.date} ${tool.time}"
        is AssistantTool.StartFocus -> "START FOCUS — ${tool.minutes} MIN"
    }
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, NothingRed), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DotMatrixText(text = label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (done) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedDot(size = 5.dp)
                Spacer(Modifier.width(6.dp))
                DotMatrixText(text = "DONE", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            OutlinedButton(onClick = onRun) {
                DotMatrixText(
                    text = "DO IT",
                    style = MaterialTheme.typography.labelMedium,
                    color = NothingRed,
                )
            }
        }
    }
}

/** The transparency card: exactly what the model was told about you. */
@Composable
private fun MemoryCard(memory: AssistantMemory.MemorySnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DotMatrixText(
            text = "READ FROM YOUR DEVICE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        val facts = buildList {
            memory.lastMoodLabel?.let { add("Latest mood: $it") }
            if (memory.recentNoteTitles.isNotEmpty()) {
                add("Notes: " + memory.recentNoteTitles.take(3).joinToString(", "))
            }
            memory.nextEventLine?.let { add("Next event: $it") }
            add("Focus blocks this week: ${memory.focusBlocksThisWeek}")
        }
        facts.forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedDot(size = 3.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
