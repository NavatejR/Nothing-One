package com.nothing.one.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Month windowing decides what the Instances query reads: the window must
 * cover whole days at both edges and respect real month lengths (leap years
 * included) — a missed boundary day is a silently invisible event.
 */
class CalendarMonthWindowTest {

    private fun calendarOf(year: Int, month: Int, day: Int = 15): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }

    @Test
    fun `window starts at midnight on the first and ends on the last millisecond of the month`() {
        val (start, end) = monthWindow(calendarOf(2026, Calendar.SEPTEMBER))

        val startCal = Calendar.getInstance().apply { clear(); timeInMillis = start }
        assertEquals(2026, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, startCal.get(Calendar.MONTH))
        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        assertEquals(0, startCal.get(Calendar.MILLISECOND))

        val endCal = Calendar.getInstance().apply { clear(); timeInMillis = end }
        assertEquals(Calendar.SEPTEMBER, endCal.get(Calendar.MONTH))
        assertEquals(30, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(999, endCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `window handles 28-day february`() {
        val (start, end) = monthWindow(calendarOf(2026, Calendar.FEBRUARY))
        val spanDays = TimeUnit.MILLISECONDS.toDays(end - start)
        // 28 days from first midnight to last 23:59:59.999 rounds to ~28 days.
        assertEquals(27, spanDays)
        val endCal = Calendar.getInstance().apply { clear(); timeInMillis = end }
        assertEquals(28, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `window handles leap-year february`() {
        val (start, end) = monthWindow(calendarOf(2028, Calendar.FEBRUARY))
        val endCal = Calendar.getInstance().apply { clear(); timeInMillis = end }
        assertEquals(29, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `window handles 31-day months`() {
        val (_, end) = monthWindow(calendarOf(2026, Calendar.DECEMBER))
        val endCal = Calendar.getInstance().apply { clear(); timeInMillis = end }
        assertEquals(31, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `window ignores the input day-of-month via normalization`() {
        // A mid-month anchor (the 15th) must still produce a first→last window.
        val (start, _) = monthWindow(calendarOf(2026, Calendar.MARCH, 15))
        val startCal = Calendar.getInstance().apply { clear(); timeInMillis = start }
        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `window covers an event on the last day at 22h`() {
        val (_, end) = monthWindow(calendarOf(2026, Calendar.JANUARY))
        val lateEvent = calendarOf(2026, Calendar.JANUARY, 31).apply {
            set(Calendar.HOUR_OF_DAY, 22)
        }
        assertTrue(lateEvent.timeInMillis <= end)
    }
}
