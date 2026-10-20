package io.github.knoellp.roomscreen

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TapCounterTest {
    private val counter = TapCounter(windowMs = 3000, count = 5)

    @Test fun fiveTapsWithinThreeSecondsOpen() {
        val results = listOf(0L, 500L, 1000L, 1500L, 2000L).map { counter.tap(it) }
        assertEquals(listOf(false, false, false, false, true), results)
    }

    @Test fun fourTapsDoNot() {
        assertFalse(listOf(0L, 100L, 200L, 300L).any { counter.tap(it) })
    }

    @Test fun slowTapsNeverAdd() {
        assertFalse((0..20).any { counter.tap(it * 1000L) })
    }

    @Test fun theWindowSlides() {
        // 0, 1000, 2000 expire as time passes; five taps within 3 s are 3000..5900 here.
        assertFalse(listOf(0L, 1000L, 2000L).any { counter.tap(it) })
        assertTrue(listOf(3500L, 3600L, 3700L, 3800L).any { counter.tap(it) })
    }

    @Test fun theCounterStartsOverAfterOpening() {
        assertTrue((0L..4L).any { counter.tap(it) })
        assertFalse(listOf(10L, 11L, 12L, 13L).any { counter.tap(it) })
        assertTrue(counter.tap(14L))
    }

    @Test fun defaultsAreFiveTapsInThreeSeconds() {
        val c = TapCounter()
        assertFalse(listOf(0L, 1L, 2L, 3L).any { c.tap(it) })
        assertTrue(c.tap(4L))
    }
}
