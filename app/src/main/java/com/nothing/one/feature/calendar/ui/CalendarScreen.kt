package com.nothing.one.feature.calendar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.feature.calendar.CalendarEvent
import com.nothing.one.feature.calendar.CalendarViewModel
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixBadge
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.components.SectionLabel
import com.nothing.one.ui.theme.NothingRed
import android.Manifest
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private val dayFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.hasPermission) {
        if (state.hasPermission) viewModel.loadMonth()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RedDot(size = 8.dp)
            Spacer(Modifier.width(10.dp))
            DotMatrixText(text = "CALENDAR", style = MaterialTheme.typography.headlineSmall)
        }
        DotGridDivider()

        if (!state.hasPermission) {
            Spacer(Modifier.height(48.dp))
            DotMatrixText(
                text = "CALENDAR PERMISSION NEEDED",
                style = MaterialTheme.typography.titleMedium,
                color = NothingRed,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Nothing One reads events through the phone's own calendar " +
                    "provider — the same place Google Calendar syncs them. No " +
                    "network, no account access. Grant calendar access to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                viewModel.refreshPermission()
                if (grants.values.any { it }) viewModel.loadMonth()
            }
            OutlinedButton(onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR,
                    ),
                )
            }) {
                DotMatrixText("GRANT ACCESS", style = MaterialTheme.typography.labelMedium)
            }
            return@Column
        }

        Spacer(Modifier.height(16.dp))

        // Month switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthArrow("<", onClick = viewModel::previousMonth)
            DotMatrixText(
                text = monthFormat.format(state.month.time).uppercase(),
                style = MaterialTheme.typography.titleMedium,
            )
            MonthArrow(">", onClick = viewModel::nextMonth)
        }

        Spacer(Modifier.height(12.dp))

        MonthGrid(
            month = state.month,
            events = state.events,
            onDayClick = { day ->
                val cal = (state.month.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 18)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.addEvent(title = "New event", startMs = cal.timeInMillis, durationMinutes = 60)
            },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("AGENDA")
        DotGridDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.events.sortedBy { it.beginMs }.forEach { event ->
                item(key = event.id) {
                    EventRow(event)
                }
            }
            if (state.events.isEmpty()) {
                item {
                    Text(
                        text = "No events this month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }

        state.message?.let { message ->
            Spacer(Modifier.height(8.dp))
            DotMatrixBadge(
                text = message,
                color = if (message == "EVENT ADDED") MaterialTheme.colorScheme.surfaceVariant else NothingRed,
                textColor = if (message == "EVENT ADDED") MaterialTheme.colorScheme.onSurface else Color.Black,
            )
            LaunchedEffect(message) { viewModel.consumeMessage() }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MonthArrow(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        DotMatrixText(text = symbol, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MonthGrid(
    month: Calendar,
    events: List<CalendarEvent>,
    onDayClick: (Int) -> Unit,
) {
    val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val isCurrentMonth = month.get(Calendar.MONTH) == Calendar.getInstance().get(Calendar.MONTH)
    val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Calendar.SUNDAY = 1 … SATURDAY = 7 → shift to Monday-first columns.
    val firstDayOffset = (month.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 6 else it - 2 }

    val eventDays = events.map { event ->
        val cal = Calendar.getInstance().apply { timeInMillis = event.beginMs }
        cal.get(Calendar.DAY_OF_MONTH)
    }.toSet()

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val cells = firstDayOffset + daysInMonth
        val rows = (cells + 6) / 7
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val day = row * 7 + col - firstDayOffset + 1
                    val valid = day in 1..daysInMonth
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    valid && isCurrentMonth && day == today -> NothingRed
                                    valid && day in eventDays ->
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else -> Color.Transparent
                                },
                            )
                            .clickable(enabled = valid) { if (valid) onDayClick(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (valid) {
                            Text(
                                text = day.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    valid && isCurrentMonth && day == today -> Color.Black
                                    day in eventDays -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = if (event.allDay) {
                    "All day · ${event.calendarDisplayName}"
                } else {
                    "${timeFormat.format(Date(event.beginMs))} – " +
                        timeFormat.format(Date(event.endMs)) + " · ${event.calendarDisplayName}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (event.fromNothingOne) {
            DotMatrixBadge(
                text = "ONE",
                color = NothingRed,
                textColor = Color.Black,
            )
        }
    }
}
