package io.github.knoellp.roomscreen

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogBufferTest {
    @Test fun keepsTheLast200LinesOldestFirst() {
        val buffer = LogBuffer()
        for (i in 1..250) buffer.add('I', "line $i")
        val lines = buffer.snapshot()
        assertEquals(200, lines.size)
        assertTrue(lines.first().endsWith("I line 51"), lines.first())
        assertTrue(lines.last().endsWith("I line 250"), lines.last())
    }

    @Test fun linesCarryATimestampAndTheLevel() {
        val buffer = LogBuffer(clock = { 0L })
        buffer.add('W', "conn watchdog=silence")
        assertTrue(Regex("\\d\\d:\\d\\d:\\d\\d\\.\\d{3} W conn watchdog=silence").matches(buffer.snapshot().single()), buffer.snapshot().toString())
    }

    @Test fun clearEmptiesTheBuffer() {
        val buffer = LogBuffer()
        buffer.add('I', "x")
        buffer.clear()
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test fun snapshotIsACopy() {
        val buffer = LogBuffer(capacity = 3)
        buffer.add('I', "a")
        val before = buffer.snapshot()
        buffer.add('I', "b")
        assertEquals(1, before.size)
    }

    @Test fun concurrentWritersLoseNothingAndNeverOverflow() {
        val buffer = LogBuffer(capacity = 200)
        val pool = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(8)
        repeat(8) { t -> pool.execute { repeat(500) { buffer.add('D', "t$t-$it"); buffer.snapshot() }; done.countDown() } }
        done.await()
        pool.shutdown()
        assertEquals(200, buffer.snapshot().size)
    }
}
