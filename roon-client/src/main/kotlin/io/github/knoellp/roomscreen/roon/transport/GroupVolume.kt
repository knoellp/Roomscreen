package io.github.knoellp.roomscreen.roon.transport

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One output of a zone as seen by the volume logic. [fixed] outputs (no `volume` object) are ignored everywhere. */
data class VolumeMember(
    val id: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val value: Double,
    val fixed: Boolean = false,
)

/**
 * Pure group volume logic with Sonos semantics (docs.sonos.com/docs/volume; docs/SPEC.md "Volume"). No Android, no I/O.
 * The group value is the average of the controllable members; a click moves the target average, and every member follows
 * proportionally to a snapshot taken when the panel opened (or after the last single-member change).
 */
object GroupVolume {
    fun controllable(members: List<VolumeMember>): List<VolumeMember> = members.filter { !it.fixed }

    /** Average of the controllable members; 0 if there are none. */
    fun average(members: List<VolumeMember>): Double {
        val c = controllable(members)
        return if (c.isEmpty()) 0.0 else c.sumOf { it.value } / c.size
    }

    /** Step size of the [clickIndex]-th click (1-based) in one direction: clicks 1-3 give 1, clicks 4-9 give 3, from click 10 give 5. */
    fun stepFor(clickIndex: Int): Int = when {
        clickIndex <= 3 -> 1
        clickIndex <= 9 -> 3
        else -> 5
    }

    /** Unit of a group click: the smallest step among the controllable members (1 if none). */
    fun unit(members: List<VolumeMember>): Double = controllable(members).minOfOrNull { it.step }?.takeIf { it > 0 } ?: 1.0

    /** Rounds [v] to the member's step grid (anchored at its minimum) and clamps it to min..max. */
    fun fit(m: VolumeMember, v: Double): Double {
        val grid = if (m.step > 0) m.min + floor((v - m.min) / m.step + 0.5) * m.step else v
        return clean(min(m.max, max(m.min, grid)))
    }

    /** Next target average after a click: [direction] (+1/-1) times [stepFor] times [unit], kept within the members' overall range. */
    fun nextAverage(members: List<VolumeMember>, current: Double, direction: Int, clickIndex: Int): Double {
        val c = controllable(members)
        if (c.isEmpty()) return current
        val raw = current + direction * stepFor(clickIndex) * unit(members)
        return clean(min(c.maxOf { it.max }, max(c.minOf { it.min }, raw)))
    }

    /**
     * Value of every controllable member for the target average [newAverage]: snapshot value times (target / snapshot average),
     * rounded to the member's step, clamped to min..max. A member at 0 stays 0, a target of 0 sets all to 0, and a snapshot average
     * of 0 makes all members equal to the target. Members with a negative minimum (dB scales) have no meaningful ratio and
     * are shifted by the difference of the averages instead.
     */
    fun targetsForGroup(snapshot: List<VolumeMember>, newAverage: Double): Map<String, Double> {
        val c = controllable(snapshot)
        val avg = average(snapshot)
        val out = LinkedHashMap<String, Double>()
        for (m in c) {
            val raw = when {
                m.min < 0 -> m.value + (newAverage - avg)
                newAverage <= 0.0 -> 0.0
                m.value <= 0.0 -> if (avg <= 0.0) newAverage else 0.0
                else -> m.value * (newAverage / avg)
            }
            out[m.id] = fit(m, raw)
        }
        return out
    }

    /** New value of a single member after [delta] (already multiplied by the step rule), rounded and clamped. */
    fun targetForSingle(member: VolumeMember, delta: Double): Double = fit(member, member.value + delta)

    private fun clean(v: Double): Double = Math.round(v * 1_000_000.0) / 1_000_000.0
}

/** Counts clicks in one direction for the 1/3/5 rule; a change of direction starts again at click 1. */
class ClickCounter {
    private var direction = 0

    /** Number of clicks in the current direction so far (1-based index of the last click). */
    var count = 0
        private set

    /** Registers a click ([direction] +1 or -1) and returns the step factor ([GroupVolume.stepFor]) of this click; [count] is its index. */
    fun click(direction: Int): Int {
        if (direction != this.direction) { this.direction = direction; count = 0 }
        count++
        return GroupVolume.stepFor(count)
    }

    fun reset() { direction = 0; count = 0 }
}
