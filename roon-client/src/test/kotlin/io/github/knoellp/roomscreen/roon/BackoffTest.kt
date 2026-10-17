package io.github.knoellp.roomscreen.roon

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackoffTest {
    /** Random whose nextDouble() is always 0, i.e. no jitter. */
    private val noJitter = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextDouble() = 0.0
    }

    @Test
    fun `doubles from 1 s up to the 30 s cap`() {
        val b = Backoff(random = noJitter)
        assertEquals(listOf(1000L, 2000, 4000, 8000, 16000, 30000, 30000, 30000), List(8) { b.next() })
    }

    @Test
    fun `jitter only shortens the delay and stays within 20 percent`() {
        val b = Backoff(random = Random(42))
        repeat(200) { i ->
            val raw = (1000L shl minOf(i, 20)).coerceAtMost(30000)
            val d = b.next()
            assertTrue(d <= raw && d >= raw * 0.8 - 1, "attempt $i delay=$d raw=$raw")
        }
    }

    @Test
    fun `same seed gives the same sequence`() {
        val a = Backoff(random = Random(7))
        val b = Backoff(random = Random(7))
        assertEquals(List(6) { a.next() }, List(6) { b.next() })
    }

    @Test
    fun `reset starts over`() {
        val b = Backoff(random = noJitter)
        repeat(4) { b.next() }
        b.reset()
        assertEquals(1000L, b.next())
    }
}
