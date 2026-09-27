package com.nothing.one.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.data.EntryRepository
import com.nothing.one.data.SettingsRepository
import com.nothing.one.data.local.EntryEntity
import com.nothing.one.data.model.Mood
import com.nothing.one.feature.calendar.CalendarRepository
import com.nothing.one.feature.focus.data.FocusSessionDao
import com.nothing.one.feature.focus.data.FocusSessionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    entryRepository: EntryRepository,
    settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
    focusSessionDao: FocusSessionDao,
) : ViewModel() {

    /** All notes, pinned first (the DAO orders them). */
    val notes: StateFlow<List<EntryEntity>> = entryRepository.observeNotes("")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Days (epochDay) with a journal entry — powers the streak stat. */
    val journalDays: StateFlow<List<Long>> = entryRepository.observeJournalDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Today's journal mood, if set. */
    val todayMood: StateFlow<Mood?> = entryRepository.observeJournalByDay(EntryRepository.todayIndex())
        .map { it?.mood?.let(Mood::fromValue) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val noteCount: StateFlow<Int> = entryRepository.observeNoteCount()
        .map { it.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val reducedMotion: StateFlow<Boolean> = settingsRepository.settings
        .map { it.reducedMotion }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Completed focus blocks today — the dashboard's fourth stat. */
    val focusToday: StateFlow<Int> = focusSessionDao.observeSince(dayStartEpochMs())
        .map { sessions -> sessions.count { it.kind == FocusSessionKind.FOCUS.name && it.completed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Next event line for the dashboard, refreshed on each screen entry. */
    private val _nextEventLine = MutableStateFlow<String?>(null)
    val nextEventLine: StateFlow<String?> = _nextEventLine.asStateFlow()

    fun refreshNextEvent() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _nextEventLine.value = calendarRepository
                .eventsBetween(now, now + TimeUnit.DAYS.toMillis(14))
                .firstOrNull()
                ?.let { event ->
                    val day = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(event.beginMs))
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.beginMs))
                    "$day $time — ${event.title}"
                }
        }
    }

    /** Consecutive days (ending today, tolerating a blank today) with entries. */
    fun streak(days: List<Long>): Int =
        computeStreak(days.toSet(), EntryRepository.todayIndex())

    private fun dayStartEpochMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        /**
         * Consecutive days ending at [today] with a journal entry. Today
         * itself may still be blank — the streak survives until yesterday
         * goes dark too.
         */
        fun computeStreak(days: Set<Long>, today: Long): Int {
            if (days.isEmpty()) return 0
            var cursor = today
            if (cursor !in days) cursor -= 1 // today not written yet; anchor on yesterday
            var count = 0
            while (cursor in days) {
                count++
                cursor -= 1
            }
            return count
        }
    }
}
