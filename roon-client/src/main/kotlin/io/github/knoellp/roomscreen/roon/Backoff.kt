package io.github.knoellp.roomscreen.roon

import kotlin.random.Random

/**
 * Exponential reconnect delay: base, 2*base, 4*base ... capped at [maxMs]. Jitter shortens each
 * delay by up to [jitter] (fraction), so the cap is never exceeded. The random source is injectable.
 */
class Backoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 30_000,
    private val jitter: Double = 0.2,
    private val random: Random = Random.Default,
) {
    private var attempt = 0

    fun next(): Long {
        val shift = attempt.coerceAtMost(20)
        val raw = (baseMs shl shift).coerceAtMost(maxMs)
        attempt++
        return (raw * (1.0 - jitter * random.nextDouble())).toLong()
    }

    fun reset() { attempt = 0 }
}
