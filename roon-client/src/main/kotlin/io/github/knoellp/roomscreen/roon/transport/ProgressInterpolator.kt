package io.github.knoellp.roomscreen.roon.transport

/**
 * Smooth playback position between the Core's seek events (about 1 Hz, whole seconds).
 *
 * Feed every zone update to [onZone]. The last seek position is the anchor; while the zone is `playing`
 * the position advances with the injected monotonic [clock] (milliseconds). The anchor is replaced when the
 * seek value changes (drift correction and seeks), the state changes (pause, resume, loading) or the track
 * changes. The position never exceeds the track length. Not thread safe: use from one thread.
 */
class ProgressInterpolator(private val clock: () -> Long) {
    private var trackKey: String? = null
    private var lastSeek: Long? = null
    private var lastState: String? = null
    private var anchorSeconds = 0L
    private var anchorAtMs = 0L
    private var playing = false
    private var lengthSeconds: Long? = null

    fun onZone(zone: Zone?) {
        val np = zone?.nowPlaying
        if (zone == null || np == null) {
            trackKey = null; lastSeek = null; lastState = null; playing = false; lengthSeconds = null; anchorSeconds = 0
            return
        }
        val key = "${np.imageKey}|${np.threeLine?.line1}|${np.length}"
        val changed = key != trackKey || zone.state != lastState || np.seekPosition != lastSeek
        trackKey = key
        lastState = zone.state
        lastSeek = np.seekPosition
        lengthSeconds = np.length
        playing = zone.state == "playing"
        if (changed) {
            // A track change without a seek value (loading) starts at 0.
            anchorSeconds = np.seekPosition ?: 0L
            anchorAtMs = clock()
        }
    }

    /** Current position in milliseconds, capped at the track length. */
    fun positionMs(): Long {
        val base = anchorSeconds * 1000
        val raw = if (playing) base + (clock() - anchorAtMs).coerceAtLeast(0) else base
        val cap = lengthSeconds?.times(1000)
        return if (cap != null && cap > 0) raw.coerceAtMost(cap) else raw
    }

    /** Track length in milliseconds, or null if unknown (streams). */
    fun lengthMs(): Long? = lengthSeconds?.takeIf { it > 0 }?.times(1000)
}
