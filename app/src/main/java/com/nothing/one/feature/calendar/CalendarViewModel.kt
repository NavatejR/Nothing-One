package com.nothing.one.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class CalendarUiState(
    /** The month being viewed; day-of-month 1 normalized. */
    val month: Calendar = Calendar.getInstance(),
    /** Events overlapping the visible month window. */
    val events: List<CalendarEvent> = emptyList(),
    val loading: Boolean = false,
    val hasPermission: Boolean = false,
    /** One-shot feedback after adding an event. */
    val message: String? = null,
)

/**
 * Owns the visible month and re-queries the provider whenever the month or
 * the grant-state changes. Also feeds the Home dashboard: nextEvent() is a
 * cheap provider read shared by both screens.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        refreshPermission()
        loadMonth()
    }

    fun refreshPermission() {
        _state.value = _state.value.copy(hasPermission = repository.hasReadPermission())
    }

    /** Loads the events overlapping the currently shown month. */
    fun loadMonth() {
        if (!_state.value.hasPermission) return
        val (windowStart, windowEnd) = monthWindow(_state.value.month)

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val events = repository.eventsBetween(windowStart, windowEnd)
            _state.value = _state.value.copy(events = events, loading = false)
        }
    }

    fun nextMonth() {
        val m = (_state.value.month.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        _state.value = _state.value.copy(month = m)
        loadMonth()
    }

    fun previousMonth() {
        val m = (_state.value.month.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        _state.value = _state.value.copy(month = m)
        loadMonth()
 }

    fun addEvent(title: String, startMs: Long, durationMinutes: Long) {
        viewModelScope.launch {
            val ok = repository.addLocalEvent(title, startMs, TimeUnit.MINUTES.toMillis(durationMinutes)) != null
            _state.value = _state.value.copy(
                message = if (ok) "EVENT ADDED" else "COULD NOT ADD EVENT",
            )
            loadMonth()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

/** First and last millisecond of the month containing [month]. */
internal fun monthWindow(month: Calendar): Pair<Long, Long> {
    val start = (month.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val end = (month.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }
    return start.timeInMillis to end.timeInMillis
}
