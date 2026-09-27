package com.nothing.one.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The streak is the number users gamify against themselves, so its edge
 * cases are pinned: a blank today must not break the chain, a dark
 * yesterday must.
 */
class StreakTest {

    @Test
    fun `empty history has no streak`() {
        assertEquals(0, HomeViewModel.computeStreak(emptySet(), today = 20_000))
    }

    @Test
    fun `today written counts as day one`() {
        assertEquals(1, HomeViewModel.computeStreak(setOf(20_000), today = 20_000))
    }

    @Test
    fun `blank today anchors on yesterday instead of breaking`() {
        // Yesterday and the day before are written; today is not yet.
        assertEquals(2, HomeViewModel.computeStreak(setOf(19_998, 19_999), today = 20_000))
    }

    @Test
    fun `dark yesterday breaks the streak even with older entries`() {
        assertEquals(0, HomeViewModel.computeStreak(setOf(19_997, 19_995), today = 20_000))
    }

    @Test
    fun `long consecutive run is counted fully`() {
        val days = (19_991L..20_000L).toSet() // ten days ending today
        assertEquals(10, HomeViewModel.computeStreak(days, today = 20_000))
    }

    @Test
    fun `gap in the middle stops the count`() {
        val days = setOf(19_996L, 19_997L, 19_999L, 20_000L) // hole at 19_998
        assertEquals(2, HomeViewModel.computeStreak(days, today = 20_000))
    }

    @Test
    fun `future-only entries do not count`() {
        assertEquals(0, HomeViewModel.computeStreak(setOf(20_001, 20_002), today = 20_000))
    }
}
