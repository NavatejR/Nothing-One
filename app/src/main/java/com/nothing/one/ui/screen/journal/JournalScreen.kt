package com.nothing.one.ui.screen.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.AssistantViewModel
import com.nothing.one.data.EntryRepository
import com.nothing.one.data.model.Mood
import com.nothing.one.ui.components.DictationMenu
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.components.SectionLabel
import com.nothing.one.ui.orb.OrbState
import com.nothing.one.ui.orb.ShaderOrb
import com.nothing.one.ui.theme.DotMatrixFont
import com.nothing.one.ui.theme.NothingRed
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_HEADER = DateTimeFormatter.ofPattern("EEE d MMM")
private val DAY_LABEL = DateTimeFormatter.ofPattern("d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    viewModel: AssistantViewModel,
    onNavigate: (String) -> Unit,
    journalViewModel: JournalViewModel = hiltViewModel(),
) {
    val selectedDay by journalViewModel.selectedDay.collectAsState()
    val entry by journalViewModel.entry.collectAsState()
    val bodyText by journalViewModel.bodyText.collectAsState()
    val moodByDay by journalViewModel.moodByDay.collectAsState()
    val aiBusy by journalViewModel.aiBusy.collectAsState()
    val aiMessage by journalViewModel.aiMessage.collectAsState()
    val orbState by viewModel.orbState.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val reducedMotion by viewModel.reducedMotion.collectAsState()
    val partial by viewModel.partialText.collectAsState()

    val today = remember { EntryRepository.todayIndex() }
    val zone = remember { ZoneId.systemDefault() }
    val dictating = orbState == OrbState.LISTENING || orbState == OrbState.THINKING
    val streak by journalViewModel.streak.collectAsState()
    val totalDays by journalViewModel.totalDays.collectAsState()
    val recentDays by journalViewModel.recentDays.collectAsState()

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
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RedDot(size = 8.dp)
                Spacer(Modifier.width(10.dp))
                DotMatrixText(text = "JOURNAL", style = MaterialTheme.typography.headlineMedium)
            }
            DotGridDivider()

            // Calendar strip: 30 days ending today.
            val days = remember { (29 downTo 0).map { today - it } }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(days, key = { it }) { day ->
                    DayCell(
                        dayIndex = day,
                        label = DAY_LABEL.format(LocalDate.ofEpochDay(day)),
                        moodValue = moodByDay[day] ?: 0,
                        selected = day == selectedDay,
                        onClick = { journalViewModel.selectDay(day) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(14.dp))

                // Stats row — the page opens with numbers, not silence.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JournalStatCard("STREAK", "$streak", Modifier.weight(1f))
                    JournalStatCard("DAYS", "$totalDays", Modifier.weight(1f))
                    JournalStatCard(
                        "TODAY",
                        entry?.mood?.let { Mood.fromValue(it)?.label?.uppercase() } ?: "—",
                        Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))

                // RECENT DAYS — last two weeks at a glance, tap to open.
                if (recentDays.isNotEmpty()) {
                    SectionLabel("RECENT DAYS")
                    Spacer(Modifier.height(6.dp))
                    recentDays.take(5).forEach { day ->
                        RecentDayRow(
                            preview = day,
                            isToday = day.dayIndex == today,
                            selected = day.dayIndex == selectedDay,
                            onClick = { journalViewModel.selectDay(day.dayIndex) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                }

                DotMatrixText(
                    text = DAY_HEADER.format(LocalDate.ofEpochDay(selectedDay)).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                )
                DotMatrixText(
                    text = if (selectedDay == today) "TODAY" else "PAST DAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // Mood picker
                SectionLabel("MOOD")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mood.entries.forEach { mood ->
                        val active = entry?.mood == mood.value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) mood.color else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    journalViewModel.setMood(if (active) null else mood)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = mood.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Color.Black else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionLabel("ENTRY")
                Spacer(Modifier.height(8.dp))
                // Shared composer style: identical to the assistant input so
                // every text box in the app reads as one voice.
                OutlinedTextField(
                    value = bodyText,
                    onValueChange = { journalViewModel.onBodyChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    placeholder = {
                        Text(
                            "How was the day?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                Spacer(Modifier.height(16.dp))

                // Dictate into the journal with the orb, same menu as home.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShaderOrb(
                        state = orbState,
                        sizeDp = 44.dp,
                        pinnedInput = micLevel,
                        reduceMotion = reducedMotion,
                        contentDescription = "Dictate into journal",
                        modifier = Modifier.clickable {
                            if (dictating) journalViewModel.stopDictation()
                            else journalViewModel.startDictation()
                        },
                    )
                    DotMatrixText(
                        text = when {
                            dictating && orbState == OrbState.THINKING -> "TRANSCRIBING…"
                            dictating -> "LISTENING — TAP TO STOP"
                            else -> "DICTATE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dictating) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { journalViewModel.summarizeDay() }, enabled = !aiBusy) {
                        DotMatrixText(
                            text = if (aiBusy) "…" else "SUMMARIZE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TextButton(onClick = { journalViewModel.inferMood() }, enabled = !aiBusy) {
                        DotMatrixText(
                            text = "READ MOOD",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                aiMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry?.summary?.let {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("DAY SUMMARY")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // Full-page dictation menu: orb grows, transcript under it.
        if (dictating) {
            DictationMenu(
                orbState = orbState,
                micLevel = micLevel,
                partial = partial,
                idleHint = "SPEAK NOW",
                restSize = 180.dp,
                menuSize = 280.dp,
                onStop = { journalViewModel.stopDictation() },
                onCancel = { journalViewModel.cancelDictation() },
            )
        }

    }
}



@Composable
private fun JournalStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DotMatrixText(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (value != "—" && label == "TODAY") MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentDayRow(
    preview: com.nothing.one.ui.screen.journal.JournalViewModel.DayPreview,
    isToday: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val mood = Mood.fromValue(preview.moodValue.takeIf { it > 0 })
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(mood?.color ?: MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isToday) "TODAY" else DAY_HEADER.format(LocalDate.ofEpochDay(preview.dayIndex)).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.preview.isNotBlank()) {
                Text(
                    text = preview.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isToday) {
            RedDot(size = 5.dp)
        }
    }
}

@Composable
private fun DayCell(
    dayIndex: Long,
    label: String,
    moodValue: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val mood = Mood.fromValue(moodValue.takeIf { it > 0 })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        selected -> NothingRed
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (selected) NothingRed else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(6.dp)
                        .background(mood?.color ?: MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}
