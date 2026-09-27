package com.nothing.one.ai

import com.nothing.one.data.EntryRepository
import com.nothing.one.feature.calendar.CalendarRepository
import com.nothing.one.feature.focus.data.FocusSessionDao
import com.nothing.one.feature.focus.data.FocusSessionKind
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "knows you" layer. Before each assistant turn, this assembles a compact
 * local context block from the user's own data — journal mood, recent notes,
 * upcoming calendar events, focus stats — everything read from local stores,
 * everything the user can see on the Home screen. No network, no hidden data:
 * the exact block is rendered in the chat UI as a collapsible memory card.
 */
@Singleton
class AssistantMemory @Inject constructor(
    private val entries: EntryRepository,
    private val calendar: CalendarRepository,
    private val focusSessions: FocusSessionDao,
) {

    /** The assembled context for one turn: facts + the prompt block. */
    data class MemorySnapshot(
        val focusBlocksThisWeek: Int,
        val lastMoodLabel: String?,
        val recentNoteTitles: List<String>,
        val nextEventLine: String?,
        val promptBlock: String,
    )

    suspend fun snapshot(): MemorySnapshot {
        val now = System.currentTimeMillis()

        // 1. Latest journal mood
        val recentJournal = entries.observeRecentJournal(7).first()
        val lastMood = recentJournal.firstOrNull { it.mood != null }?.mood
        val moodLabel = lastMood?.let { moodName(it) }

        // 2. Recent note titles
        val notes = entries.observeNotes("").first().take(5)
        val noteTitles = notes.map { it.title.ifBlank { it.body.take(40) } }

        // 3. Next calendar event (14-day horizon)
        val nextEvent = calendar.eventsBetween(now, now + TimeUnit.DAYS.toMillis(14))
            .getOrNull(0)
        val nextEventLine = nextEvent?.let {
            val day = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(it.beginMs))
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.beginMs))
            "$day at $time — ${it.title}"
        }

        // 4. Focus blocks this week (kind FOCUS, completed)
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val focusWeek = focusSessions.observeSince(weekAgo).first()
            .count { it.kind == FocusSessionKind.FOCUS.name && it.completed }

        val today = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))

        val block = buildString {
            appendLine("[CONTEXT — read from the user's local data, private to this device]")
            appendLine("Today is $today.")
            moodLabel?.let { appendLine("Their latest journal mood: $it.") }
            if (noteTitles.isNotEmpty()) {
                appendLine("Recent notes: ${noteTitles.joinToString("; ")}.")
            }
            nextEventLine?.let { appendLine("Next event: $it.") }
            if (focusWeek > 0) appendLine("Focus blocks completed this week: $focusWeek.")
            appendLine("Use this context only when relevant. Never invent events or entries.")
        }

        return MemorySnapshot(
            focusBlocksThisWeek = focusWeek,
            lastMoodLabel = moodLabel,
            recentNoteTitles = noteTitles,
            nextEventLine = nextEventLine,
            promptBlock = block,
        )
    }

    private fun moodName(mood: Int): String = when (mood) {
        1 -> "awful"; 2 -> "low"; 3 -> "okay"; 4 -> "good"; 5 -> "great"; else -> "unknown"
    }
}
