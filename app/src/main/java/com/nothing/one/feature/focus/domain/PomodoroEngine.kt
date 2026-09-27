package com.nothing.one.feature.focus.domain

/**
 * A deterministic, side-effect-free pomodoro state machine. The ViewModel
 * owns the clock tick and calls [reduce]; the engine never touches Android,
 * so every transition is unit-testable.
 *
 * Transitions:
 *   IDLE → FOCUS → SHORT_BREAK → FOCUS → … → LONG_BREAK (after
 *   [PomodoroConfig.longBreakEvery] completed focus blocks) → FOCUS → …
 *
 * PAUSED remembers the phase it interrupted via [PomodoroState.pausedFrom].
 */
object PomodoroEngine {

    /** Immutable pomodoro tuning. */
    data class PomodoroConfig(
        val focusMinutes: Int = 25,
        val shortBreakMinutes: Int = 5,
        val longBreakMinutes: Int = 15,
        val longBreakEvery: Int = 4,
    )

    enum class Phase { IDLE, FOCUS, SHORT_BREAK, LONG_BREAK, PAUSED }

    /** Full UI state, owned by the engine; the ViewModel just holds it. */
    data class PomodoroState(
        val phase: Phase = Phase.IDLE,
        val remainingMs: Long = 0,
        /** Focus blocks completed since the last long break. */
        val completedFocusBlocks: Int = 0,
        /** Focus blocks completed since the counter was reset. */
        val totalFocusBlocks: Int = 0,
        /** Where a PAUSED countdown came from; null otherwise. */
        val pausedFrom: Phase? = null,
        /** Set for exactly one reduce() when a phase runs out, so the UI can chime. */
        val phaseCompleted: Boolean = false,
    )

    fun initial(config: PomodoroConfig): PomodoroState =
        PomodoroState(remainingMs = config.focusMinutes * 60_000L)

    private fun nextBreak(blocksDone: Int, config: PomodoroConfig): Phase =
        if (blocksDone > 0 && blocksDone % config.longBreakEvery == 0) Phase.LONG_BREAK else Phase.SHORT_BREAK

    private fun breakMinutes(phase: Phase, config: PomodoroConfig): Int = when (phase) {
        Phase.LONG_BREAK -> config.longBreakMinutes
        else -> config.shortBreakMinutes
    }

    /** Advance the countdown by [deltaMs]; completes and flips phases. */
    fun reduce(state: PomodoroState, config: PomodoroConfig, deltaMs: Long): PomodoroState {
        if (state.phase != Phase.FOCUS &&
            state.phase != Phase.SHORT_BREAK &&
            state.phase != Phase.LONG_BREAK
        ) {
            return state
        }
        val remaining = state.remainingMs - deltaMs
        if (remaining > 0) return state.copy(remainingMs = remaining, phaseCompleted = false)

        // Phase ran out: flip to the next phase with a fresh budget.
        return when (state.phase) {
            Phase.FOCUS -> {
                val blocks = state.completedFocusBlocks + 1
                val next = nextBreak(blocks, config)
                state.copy(
                    phase = next,
                    remainingMs = breakMinutes(next, config) * 60_000L,
                    completedFocusBlocks = blocks,
                    totalFocusBlocks = state.totalFocusBlocks + 1,
                    phaseCompleted = true,
                )
            }
            Phase.SHORT_BREAK, Phase.LONG_BREAK -> state.copy(
                phase = Phase.FOCUS,
                remainingMs = config.focusMinutes * 60_000L,
                phaseCompleted = true,
            )
            else -> state
        }
    }

    fun start(state: PomodoroState, config: PomodoroConfig): PomodoroState = when (state.phase) {
        Phase.IDLE -> state.copy(
            phase = Phase.FOCUS,
            remainingMs = config.focusMinutes * 60_000L,
            phaseCompleted = false,
        )
        Phase.PAUSED -> state.copy(
            phase = state.pausedFrom ?: Phase.FOCUS,
            pausedFrom = null,
            phaseCompleted = false,
        )
        else -> state
    }

    fun pause(state: PomodoroState): PomodoroState = when (state.phase) {
        Phase.FOCUS, Phase.SHORT_BREAK, Phase.LONG_BREAK ->
            state.copy(phase = Phase.PAUSED, pausedFrom = state.phase)
        else -> state
    }

    fun skip(state: PomodoroState, config: PomodoroConfig): PomodoroState = when (state.phase) {
        Phase.FOCUS -> {
            val next = nextBreak(state.completedFocusBlocks, config)
            state.copy(
                phase = next,
                remainingMs = breakMinutes(next, config) * 60_000L,
                pausedFrom = null,
                phaseCompleted = false,
            )
        }
        Phase.SHORT_BREAK, Phase.LONG_BREAK -> state.copy(
            phase = Phase.FOCUS,
            remainingMs = config.focusMinutes * 60_000L,
            pausedFrom = null,
            phaseCompleted = false,
        )
        else -> state
    }

    /** Full reset back to a fresh IDLE with a full focus budget. */
    fun reset(state: PomodoroState, config: PomodoroConfig): PomodoroState =
        PomodoroState(remainingMs = config.focusMinutes * 60_000L)

    /** Clear the blocks-since-long-break counter (used after manually resetting a cycle). */
    fun resetCycle(state: PomodoroState): PomodoroState =
        state.copy(completedFocusBlocks = 0, phaseCompleted = false)
}
