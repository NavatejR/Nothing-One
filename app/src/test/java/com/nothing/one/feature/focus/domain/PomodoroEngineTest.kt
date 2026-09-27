package com.nothing.one.feature.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pomodoro engine is pure, so every transition is pinned here: budgets,
 * phase flips, long-break cadence, pause/resume and skip behavior.
 */
class PomodoroEngineTest {

    private val config = PomodoroEngine.PomodoroConfig(
        focusMinutes = 25,
        shortBreakMinutes = 5,
        longBreakMinutes = 15,
        longBreakEvery = 4,
    )

    @Test
    fun `initial state is idle with a full focus budget`() {
        val state = PomodoroEngine.initial(config)
        assertEquals(PomodoroEngine.Phase.IDLE, state.phase)
        assertEquals(25 * 60_000L, state.remainingMs)
        assertEquals(0, state.completedFocusBlocks)
        assertNull(state.pausedFrom)
    }

    @Test
    fun `start moves idle into focus`() {
        val state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        assertEquals(PomodoroEngine.Phase.FOCUS, state.phase)
        assertEquals(25 * 60_000L, state.remainingMs)
    }

    @Test
    fun `reduce ticks the countdown without flipping phase`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.reduce(state, config, 2_500)
        assertEquals(PomodoroEngine.Phase.FOCUS, state.phase)
        assertEquals(25 * 60_000L - 2_500, state.remainingMs)
        assertFalse(state.phaseCompleted)
    }

    @Test
    fun `focus completion flips to short break`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.reduce(state, config, 25 * 60_000L)
        assertEquals(PomodoroEngine.Phase.SHORT_BREAK, state.phase)
        assertEquals(5 * 60_000L, state.remainingMs)
        assertEquals(1, state.completedFocusBlocks)
        assertEquals(1, state.totalFocusBlocks)
        assertTrue(state.phaseCompleted)
    }

    @Test
    fun `break completion returns to focus with a fresh budget`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.reduce(state, config, 25 * 60_000L) // focus -> short
        state = PomodoroEngine.reduce(state, config, 5 * 60_000L) // short -> focus
        assertEquals(PomodoroEngine.Phase.FOCUS, state.phase)
        assertEquals(25 * 60_000L, state.remainingMs)
        assertEquals(1, state.completedFocusBlocks)
        assertEquals(1, state.totalFocusBlocks)
    }

    @Test
    fun `fourth focus block triggers the long break`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        repeat(3) {
            state = PomodoroEngine.reduce(state, config, 25 * 60_000L) // -> break
            state = PomodoroEngine.reduce(state, config, 5 * 60_000L) // -> focus
        }
        assertEquals(PomodoroEngine.Phase.FOCUS, state.phase)
        assertEquals(3, state.completedFocusBlocks)
        state = PomodoroEngine.reduce(state, config, 25 * 60_000L)
        assertEquals(PomodoroEngine.Phase.LONG_BREAK, state.phase)
        assertEquals(15 * 60_000L, state.remainingMs)
        assertEquals(4, state.completedFocusBlocks)
    }

    @Test
    fun `pause remembers the interrupted phase`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.reduce(state, config, 60_000)
        state = PomodoroEngine.pause(state)
        assertEquals(PomodoroEngine.Phase.PAUSED, state.phase)
        assertEquals(PomodoroEngine.Phase.FOCUS, state.pausedFrom)
        assertEquals(25 * 60_000L - 60_000, state.remainingMs)
    }

    @Test
    fun `pause during a break resumes that break`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.reduce(state, config, 25 * 60_000L) // -> short break
        state = PomodoroEngine.pause(state)
        state = PomodoroEngine.start(state, config)
        assertEquals(PomodoroEngine.Phase.SHORT_BREAK, state.phase)
        assertNull(state.pausedFrom)
    }

    @Test
    fun `reduce is a no-op while paused or idle`() {
        val paused = PomodoroEngine.pause(PomodoroEngine.start(PomodoroEngine.initial(config), config))
        val after = PomodoroEngine.reduce(paused, config, 60_000)
        assertEquals(paused.remainingMs, after.remainingMs)

        val idle = PomodoroEngine.initial(config)
        assertEquals(idle, PomodoroEngine.reduce(idle, config, 60_000))
    }

    @Test
    fun `skip from focus moves to the next break without counting a block`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.skip(state, config)
        assertEquals(PomodoroEngine.Phase.SHORT_BREAK, state.phase)
        assertEquals(0, state.completedFocusBlocks)
        assertEquals(0, state.totalFocusBlocks)
    }

    @Test
    fun `skip from a break moves back to focus`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.reduce(state, config, 25 * 60_000L) // -> short break
        state = PomodoroEngine.skip(state, config)
        assertEquals(PomodoroEngine.Phase.FOCUS, state.phase)
        assertEquals(25 * 60_000L, state.remainingMs)
    }

    @Test
    fun `reset returns to a fresh idle`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.reduce(state, config, 10 * 60_000L)
        state = PomodoroEngine.reset(state, config)
        assertEquals(PomodoroEngine.Phase.IDLE, state.phase)
        assertEquals(25 * 60_000L, state.remainingMs)
        assertEquals(0, state.completedFocusBlocks)
        assertEquals(0, state.totalFocusBlocks)
    }

    @Test
    fun `overshoot delta completes the phase exactly once`() {
        var state = PomodoroEngine.start(PomodoroEngine.initial(config), config)
        state = PomodoroEngine.reduce(state, config, 90 * 60_000L)
        assertEquals(PomodoroEngine.Phase.SHORT_BREAK, state.phase)
        assertEquals(5 * 60_000L, state.remainingMs)
        state = PomodoroEngine.reduce(state, config, 1_000)
        assertFalse(state.phaseCompleted)
    }
}
