package com.nothing.one.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.feature.focus.data.FocusSessionDao
import com.nothing.one.feature.focus.data.FocusSessionEntity
import com.nothing.one.feature.focus.data.FocusSessionKind
import com.nothing.one.feature.focus.domain.PomodoroEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which tool the Focus tab shows. */
enum class FocusMode { POMODORO, TIMER, STOPWATCH }

/**
 * Drives the three focus tools. The pomodoro runs on the pure
 * [PomodoroEngine]; this class only owns the wall clock, the tick loop and
 * persistence of completed sessions.
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val sessionDao: FocusSessionDao,
    private val focusTrigger: FocusTrigger,
) : ViewModel() {

    private val config = PomodoroEngine.PomodoroConfig()

    private val _mode = MutableStateFlow(FocusMode.POMODORO)
    val mode: StateFlow<FocusMode> = _mode.asStateFlow()

    // ---- Pomodoro ----------------------------------------------------------

    private val _pomodoro = MutableStateFlow(PomodoroEngine.initial(config))
    val pomodoro: StateFlow<PomodoroEngine.PomodoroState> = _pomodoro.asStateFlow()

    // ---- Countdown timer ----------------------------------------------------

    data class TimerState(
        val plannedMs: Long = 5 * 60_000L,
        val remainingMs: Long = 5 * 60_000L,
        val running: Boolean = false,
        /** Set for one beat when the timer hits zero. */
        val finished: Boolean = false,
    )

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    // ---- Stopwatch -----------------------------------------------------------

    data class Lap(val index: Int, val atMs: Long, val splitMs: Long)

    data class StopwatchState(
        val elapsedMs: Long = 0,
        val running: Boolean = false,
        val laps: List<Lap> = emptyList(),
    )

    private val _stopwatch = MutableStateFlow(StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()

    /** Today's completed focus-block count, for the Home dashboard. */
    private val _focusBlocksToday = MutableStateFlow(0)
    val focusBlocksToday: StateFlow<Int> = _focusBlocksToday.asStateFlow()

    private var tickJob: kotlinx.coroutines.Job? = null
    private var lastTickAt = 0L

    /** When the currently-running pomodoro phase began (epoch ms). */
    private var phaseStartedAt = 0L

    init {
        viewModelScope.launch {
            val dayStart = dayStartEpochMs()
            sessionDao.observeSince(dayStart).collect { sessions ->
                _focusBlocksToday.value = sessions.count {
                    it.kind == FocusSessionKind.FOCUS.name && it.completed
                }
            }
        }
        // A pending assistant request ("start a 25-minute focus") starts the
        // session the moment this tab opens.
        viewModelScope.launch {
            focusTrigger.pending.collect { request ->
                if (request != null) {
                    setMode(FocusMode.POMODORO)
                    pomodoroStart()
                    focusTrigger.consume()
                }
            }
        }
    }

    fun setMode(mode: FocusMode) {
        _mode.value = mode
    }

    // ---- Pomodoro controls ----------------------------------------------------

    fun pomodoroStart() {
        phaseStartedAt = System.currentTimeMillis()
        _pomodoro.value = PomodoroEngine.start(_pomodoro.value, config)
        ensureTicking()
    }

    fun pomodoroPause() {
        _pomodoro.value = PomodoroEngine.pause(_pomodoro.value)
    }

    fun pomodoroResume() = pomodoroStart()

    fun pomodoroSkip() {
        // A skipped focus block still counts as time on task, but incomplete.
        logSkippedIfFocus()
        _pomodoro.value = PomodoroEngine.skip(_pomodoro.value, config)
        phaseStartedAt = System.currentTimeMillis()
    }

    fun pomodoroReset() {
        _pomodoro.value = PomodoroEngine.reset(_pomodoro.value, config)
    }

    fun consumePhaseCompleted() {
        _pomodoro.value = _pomodoro.value.copy(phaseCompleted = false)
    }

    // ---- Timer controls ---------------------------------------------------------

    fun setTimerMinutes(minutes: Int) {
        if (_timer.value.running) return
        _timer.value = TimerState(
            plannedMs = minutes * 60_000L,
            remainingMs = minutes * 60_000L,
        )
    }

    fun timerStartPause() {
        val t = _timer.value
        _timer.value = if (t.running) {
            t.copy(running = false)
        } else {
            t.copy(running = true, finished = false)
        }
        ensureTicking()
    }

    fun timerReset() {
        val t = _timer.value
        _timer.value = TimerState(plannedMs = t.plannedMs, remainingMs = t.plannedMs)
    }

    // ---- Stopwatch controls --------------------------------------------------------

    fun stopwatchStartPause() {
        val s = _stopwatch.value
        _stopwatch.value = s.copy(running = !s.running)
        ensureTicking()
    }

    fun stopwatchLap() {
        val s = _stopwatch.value
        val prev = s.laps.lastOrNull()?.atMs ?: 0L
        _stopwatch.value = s.copy(
            laps = s.laps + Lap(index = s.laps.size + 1, atMs = s.elapsedMs, splitMs = s.elapsedMs - prev),
        )
    }

    fun stopwatchReset() {
        _stopwatch.value = StopwatchState()
    }

    // ---- Tick loop -------------------------------------------------------------------

    /**
     * One loop drives whichever tool is currently running. Delta time comes
     * from the wall clock so background throttling cannot stall the countdown.
     */
    private fun ensureTicking() {
        val anyRunning = _pomodoro.value.phase in ACTIVE_PHASES ||
            _timer.value.running || _stopwatch.value.running
        if (!anyRunning || tickJob?.isActive == true) return
        lastTickAt = System.currentTimeMillis()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val now = System.currentTimeMillis()
                val delta = (now - lastTickAt).coerceAtLeast(0)
                lastTickAt = now
                tick(delta)
                val stillRunning = _pomodoro.value.phase in ACTIVE_PHASES ||
                    _timer.value.running || _stopwatch.value.running
                if (!stillRunning) break
            }
        }
    }

    private fun tick(deltaMs: Long) {
        // Pomodoro
        val before = _pomodoro.value
        if (before.phase in ACTIVE_PHASES) {
            val after = PomodoroEngine.reduce(before, config, deltaMs)
            _pomodoro.value = after
            if (after.phase != before.phase) {
                // A phase just ended: log it with its true start time, then
                // stamp the new phase's start.
                val kind = when (before.phase) {
                    PomodoroEngine.Phase.FOCUS -> FocusSessionKind.FOCUS
                    PomodoroEngine.Phase.LONG_BREAK -> FocusSessionKind.LONG_BREAK
                    else -> FocusSessionKind.SHORT_BREAK
                }
                logSession(kind, completed = true, startedAtMs = phaseStartedAt)
                phaseStartedAt = System.currentTimeMillis()
            }
        }

        // Timer
        val t = _timer.value
        if (t.running) {
            val remaining = t.remainingMs - deltaMs
            if (remaining <= 0) {
                _timer.value = t.copy(remainingMs = 0, running = false, finished = true)
                logTimerSession(t.plannedMs)
            } else {
                _timer.value = t.copy(remainingMs = remaining)
            }
        }

        // Stopwatch
        val s = _stopwatch.value
        if (s.running) {
            _stopwatch.value = s.copy(elapsedMs = s.elapsedMs + deltaMs)
        }
    }

    // ---- Persistence -------------------------------------------------------------------

    private fun logSession(kind: FocusSessionKind, completed: Boolean, startedAtMs: Long, durationOverride: Long? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            sessionDao.insert(
                FocusSessionEntity(
                    startedAt = startedAtMs,
                    endedAt = now,
                    durationMs = durationOverride ?: (now - startedAtMs),
                    kind = kind.name,
                    completed = completed,
                ),
            )
        }
    }

    private fun logSkippedIfFocus() {
        if (_pomodoro.value.phase == PomodoroEngine.Phase.FOCUS && phaseStartedAt > 0) {
            logSession(FocusSessionKind.FOCUS, completed = false, startedAtMs = phaseStartedAt)
        }
    }

    private fun logTimerSession(plannedMs: Long) {
        logSession(
            FocusSessionKind.TIMER,
            completed = true,
            startedAtMs = System.currentTimeMillis() - plannedMs,
            durationOverride = plannedMs,
        )
    }

    private fun dayStartEpochMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private companion object {
        val ACTIVE_PHASES = setOf(
            PomodoroEngine.Phase.FOCUS,
            PomodoroEngine.Phase.SHORT_BREAK,
            PomodoroEngine.Phase.LONG_BREAK,
        )
        const val TICK_MS = 250L
    }
}
