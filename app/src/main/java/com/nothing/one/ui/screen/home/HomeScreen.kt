package com.nothing.one.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.AssistantViewModel
import com.nothing.one.Routes
import com.nothing.one.data.local.EntryEntity
import com.nothing.one.speech.DictationSink
import com.nothing.one.ui.components.DictationMenu
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixBadge
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.orb.OrbState
import com.nothing.one.ui.orb.ShaderOrb
import com.nothing.one.ui.theme.DotMatrixFont
import com.nothing.one.ui.theme.NothingRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TODAY_HEADER = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")

/** Hero orb resting size; grows to [DICTATION_ORB_DP] when dictating. */
private val HERO_ORB_DP = 240.dp
private val DICTATION_ORB_DP = 280.dp

@Composable
fun HomeScreen(
    viewModel: AssistantViewModel,
    onOpenEditor: (Long?) -> Unit,
    onOpenEditorWithTemplate: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNavigate: (String) -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val notes by homeViewModel.notes.collectAsState()
    val journalDays by homeViewModel.journalDays.collectAsState()
    val todayMood by homeViewModel.todayMood.collectAsState()
    val noteCount by homeViewModel.noteCount.collectAsState()
    val reducedMotion by homeViewModel.reducedMotion.collectAsState()
    val focusToday by homeViewModel.focusToday.collectAsState()
    val nextEvent by homeViewModel.nextEventLine.collectAsState()

    // Live clock — the dashboard is the one screen you read like a watch face.
    var clock by remember { mutableStateOf(java.time.LocalTime.now().format(CLOCK_FORMAT)) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = java.time.LocalTime.now().format(CLOCK_FORMAT)
            kotlinx.coroutines.delay(1_000)
        }
    }
    LaunchedEffect(Unit) { homeViewModel.refreshNextEvent() }

    val orbState by viewModel.orbState.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val partial by viewModel.partialText.collectAsState()
    val savedFeedback by viewModel.savedFeedback.collectAsState()

    val dictating = orbState == OrbState.LISTENING || orbState == OrbState.THINKING

    // "NOTE SAVED" confirmation after stop-and-save; clears itself.
    var showSaved by remember { mutableStateOf(false) }
    LaunchedEffect(savedFeedback) {
        if (savedFeedback != null) {
            showSaved = true
            viewModel.consumeSavedFeedback()
        }
    }
    LaunchedEffect(showSaved) {
        if (showSaved) {
            kotlinx.coroutines.delay(1600)
            showSaved = false
        }
    }

    // The menu is open for the whole dictation; the saved toast confirms
    // the auto-created note after it closes.
    val menuOpen = dictating

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RedDot(size = 8.dp)
                    Spacer(Modifier.width(10.dp))
                    DotMatrixText(
                        text = "NOTES",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                DotMatrixText(
                    text = clock,
                    style = MaterialTheme.typography.titleMedium,
                    color = NothingRed,
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DotGridDivider()

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---- Hero: date + orb + actions (full width) ----
                item(span = { GridItemSpan(2) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(6.dp))
                        DotMatrixText(
                            text = TODAY_HEADER.format(LocalDate.now()).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        // Resting hero orb; while dictating it yields to the
                        // menu's growing orb (no double orb on screen).
                        if (!dictating) {
                            ShaderOrb(
                                state = orbState,
                                sizeDp = HERO_ORB_DP,
                                pinnedInput = micLevel,
                                reduceMotion = reducedMotion,
                                contentDescription = "Voice orb — tap to dictate",
                                modifier = Modifier.clickable {
                                    viewModel.startListening(DictationSink.HOME_NOTE)
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "TAP THE ORB TO SPEAK A NOTE",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = DotMatrixFont),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        // Write-instead: primary action + template shortcuts.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingRed)
                                    .clickable { onOpenEditor(null) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    DotMatrixText(
                                        text = "WRITE INSTEAD",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Black,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            items(com.nothing.one.data.NoteTemplates.All) { template ->
                                DotMatrixBadge(
                                    text = template.title.ifBlank { "BLANK" },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { onOpenEditorWithTemplate(template.id) },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        DotGridDivider()
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ---- Stat strip (full width) ----
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        HomeStat("STREAK", "${homeViewModel.streak(journalDays)}D")
                        HomeStat("NOTES", "$noteCount")
                        HomeStat(
                            "TODAY",
                            todayMood?.label?.uppercase() ?: "—",
                        )
                        HomeStat("FOCUS", "$focusToday")
                    }
                }

                // ---- Next up (from the device calendar provider) ----
                nextEvent?.let { event ->
                    item(span = { GridItemSpan(2) }) {
                        Column(Modifier.padding(horizontal = 8.dp)) {
                            Spacer(Modifier.height(6.dp))
                            com.nothing.one.ui.components.SectionLabel("NEXT UP")
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { onNavigate(Routes.CALENDAR) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                RedDot(size = 6.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = event,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // ---- Pinned row (full width) ----
                val pinnedNotes = notes.filter { it.pinned }
                if (pinnedNotes.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            com.nothing.one.ui.components.SectionLabel("PINNED")
                            Spacer(Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                items(pinnedNotes, key = { it.id }) { note ->
                                    CompactNoteCard(note) { onOpenEditor(note.id) }
                                }
                            }
                        }
                    }
                }

                // ---- Recent notes header + cards ----
                item(span = { GridItemSpan(2) }) {
                    com.nothing.one.ui.components.SectionLabel("RECENT NOTES")
                }

                items(notes, key = { it.id }) { note ->
                    NoteCard(note) { onOpenEditor(note.id) }
                }
                if (notes.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = "No notes yet — speak or write the first one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
        // The dictation menu: orb grows, live transcript under it, stop saves.
        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DictationMenu(
                orbState = orbState,
                micLevel = micLevel,
                partial = partial,
                idleHint = "SPEAK NOW",
                restSize = HERO_ORB_DP,
                menuSize = DICTATION_ORB_DP,
                onStop = { viewModel.stopListening() },
                onCancel = { viewModel.cancelListening() },
            )
        }

        // Saved confirmation toast.
        AnimatedVisibility(
            visible = showSaved,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NothingRed)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                DotMatrixText(
                    text = (savedFeedback ?: "NOTE SAVED"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun HomeStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DotMatrixText(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (label == "TODAY" && value != "—") NothingRed else MaterialTheme.colorScheme.onSurface,
        )
        DotMatrixText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactNoteCard(note: EntryEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        DotMatrixText(
            text = note.title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = note.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoteCard(note: EntryEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        DotMatrixText(
            text = note.title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = note.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        note.summary?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val tags = com.nothing.one.data.Tags.decode(note.tagsJson)
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.take(2).forEach { DotMatrixBadge(text = it) }
            }
        }
    }
}
