package io.github.knoellp.roomscreen

import org.junit.Test
import kotlin.test.assertEquals

class InputTraceTest {
    @Test fun keepsTheLast20EventsOldestFirst() {
        var now = 0L
        val trace = InputTrace(clock = { now++ })
        for (i in 1..25) trace.add("key $i")
        val events = trace.snapshot()
        assertEquals(20, events.size)
        assertEquals(InputEvent(5, "key 6"), events.first())
        assertEquals(InputEvent(24, "key 25"), events.last())
    }

    @Test fun recordsKeysAndTouches() {
        val trace = InputTrace(clock = { 7L })
        trace.add("key DPAD_UP (19)")
        trace.add("touch 960,540")
        assertEquals(listOf("key DPAD_UP (19)", "touch 960,540"), trace.snapshot().map { it.text })
    }
}
