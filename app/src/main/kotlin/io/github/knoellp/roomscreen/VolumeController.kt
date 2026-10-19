package io.github.knoellp.roomscreen

import android.view.KeyEvent
import io.github.knoellp.roomscreen.roon.Logger
import io.github.knoellp.roomscreen.roon.transport.ClickCounter
import io.github.knoellp.roomscreen.roon.transport.GroupVolume
import io.github.knoellp.roomscreen.roon.transport.Output
import io.github.knoellp.roomscreen.roon.transport.VolumeKind
import io.github.knoellp.roomscreen.roon.transport.VolumeMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One row of the volume panel: the group row ([outputId] null) or one output. [value] is null when no number is shown. */
data class VolumeRow(
    val outputId: String?,
    val name: String,
    val kind: VolumeKind,
    val value: Double?,
    val min: Double,
    val max: Double,
    val softLimit: Double?,
    val muted: Boolean,
    val fixed: Boolean,
) {
    val isGroup: Boolean get() = outputId == null
}

data class VolumePanelState(val rows: List<VolumeRow>, val selected: Int)

/**
 * State and logic of the volume panel (docs/SPEC.md section 6.1); no Android views. Rows come from the outputs of the shown zone,
 * clicks become targets, targets are sent through [send] with at most one request in flight per output (later clicks only update
 * the target), and the displayed value is the target until the Core reports it or [pendingMs] pass.
 */
class VolumeController(
    private val scope: CoroutineScope,
    private val log: Logger,
    private val send: suspend (outputId: String, how: String, value: Double) -> Unit,
    private val visibleMs: Long = Tokens.volumePanelVisibleMs,
    private val pendingMs: Long = Tokens.pendingMs,
) {
    private val _state = MutableStateFlow<VolumePanelState?>(null)
    val state: StateFlow<VolumePanelState?> = _state

    var isOpen = false
        private set

    private var outputs: List<Output> = emptyList()
    private var selected = 0
    private var snapshot: List<VolumeMember> = emptyList()
    private var groupAverage: Double? = null
    private val counter = ClickCounter()
    private var closeJob: Job? = null

    /** Number targets not yet confirmed by the Core (also the displayed values), and the last value sent per output. */
    private val desired = HashMap<String, Double>()
    private val sent = HashMap<String, Double>()
    private val relative = HashMap<String, Int>()
    private val inFlight = HashSet<String>()
    private val expiry = HashMap<String, Job>()

    /** True if at least one output has a volume object (the speaker button is shown). */
    fun available(outs: List<Output>): Boolean = outs.any { it.volume != null }

    /** Opens the panel for the outputs of the shown zone; false if no output is controllable. */
    fun open(outs: List<Output>): Boolean {
        if (!available(outs)) return false
        outputs = outs
        isOpen = true
        counter.reset()
        groupAverage = null
        snapshot = buildSnapshot()
        selected = rows().indexOfFirst { !it.fixed }.coerceAtLeast(0)
        log.info("volume panel=open rows=${rows().size}")
        touch()
        refresh()
        return true
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        closeJob?.cancel(); closeJob = null
        log.info("volume panel=close")
        refresh()
    }

    /** Zone update: confirms targets the Core has reached; closes the panel if no output is controllable any more. */
    fun onOutputs(outs: List<Output>) {
        val idsChanged = outs.map { it.outputId } != outputs.map { it.outputId }
        outputs = outs
        for ((id, target) in desired.toMap()) {
            val actual = outs.firstOrNull { it.outputId == id }?.volume?.value
            if (actual != null && actual == target) clearTarget(id)
        }
        if (isOpen) {
            if (!available(outs)) { close(); return }
            if (idsChanged) {
                snapshot = buildSnapshot(); groupAverage = null; counter.reset()
                selected = selected.coerceIn(0, rows().lastIndex).let { if (rows()[it].fixed) rows().indexOfFirst { r -> !r.fixed }.coerceAtLeast(0) else it }
            }
            refresh()
        }
    }

    /** A key while the panel is open: Left/Right change the selected row, Up/Down move, Back closes. Restarts the auto-close timer. */
    fun key(keyCode: Int, repeat: Boolean = false) {
        if (!isOpen) return
        touch()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> change(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> change(+1)
            KeyEvent.KEYCODE_DPAD_UP -> if (!repeat) move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!repeat) move(+1)
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> close()
            else -> {}
        }
    }

    /** Restarts the auto-close timer (any key, including media keys). */
    fun touch() {
        if (!isOpen) return
        closeJob?.cancel()
        closeJob = scope.launch { delay(visibleMs); close() }
    }

    // ---- rows ----

    private fun displayValue(o: Output): Double? = desired[o.outputId] ?: o.volume?.value

    private fun member(o: Output): VolumeMember? {
        val v = o.volume ?: return null
        if (v.kind == VolumeKind.INCREMENTAL) return null
        val min = v.min ?: 0.0
        return VolumeMember(o.outputId, min, v.max ?: 100.0, v.step?.takeIf { it > 0 } ?: 1.0, displayValue(o) ?: min)
    }

    private fun numericMembers(): List<VolumeMember> = outputs.mapNotNull { member(it) }

    private fun buildSnapshot(): List<VolumeMember> = numericMembers()

    private fun rows(): List<VolumeRow> {
        val single = outputs.map { o ->
            val v = o.volume
            val name = o.displayName ?: o.outputId
            if (v == null) VolumeRow(o.outputId, name, VolumeKind.NUMBER, null, 0.0, 0.0, null, muted = false, fixed = true)
            else if (v.kind == VolumeKind.INCREMENTAL) VolumeRow(o.outputId, name, VolumeKind.INCREMENTAL, null, 0.0, 0.0, null, v.isMuted, false)
            else VolumeRow(o.outputId, name, v.kind, displayValue(o), v.min ?: 0.0, v.max ?: 100.0, v.softLimit, v.isMuted, false)
        }
        val controllable = outputs.count { it.volume != null }
        if (controllable < 2) return single
        val numeric = single.filter { !it.fixed && it.kind != VolumeKind.INCREMENTAL }
        val group = VolumeRow(
            outputId = null, name = "",
            kind = when {
                numeric.isEmpty() -> VolumeKind.INCREMENTAL
                numeric.all { it.kind == VolumeKind.DB } -> VolumeKind.DB
                else -> VolumeKind.NUMBER
            },
            value = numeric.takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { it.value ?: 0.0 } / r.size },
            min = numeric.minOfOrNull { it.min } ?: 0.0, max = numeric.maxOfOrNull { it.max } ?: 0.0,
            softLimit = null, muted = false, fixed = false,
        )
        return listOf(group) + single
    }

    private fun refresh() {
        _state.value = if (isOpen) rows().let { VolumePanelState(it, selected.coerceIn(0, it.lastIndex)) } else null
    }

    private fun move(delta: Int) {
        val r = rows()
        var i = selected
        repeat(r.size) {
            i += delta
            if (i !in r.indices) return
            if (!r[i].fixed) { selected = i; counter.reset(); refresh(); return }
        }
    }

    // ---- changing ----

    private fun change(direction: Int) {
        val row = rows().getOrNull(selected) ?: return
        if (row.fixed) return
        counter.click(direction)
        val index = counter.count
        if (row.isGroup) changeGroup(direction, index) else changeSingle(row.outputId!!, direction, index)
        refresh()
    }

    private fun changeSingle(id: String, direction: Int, index: Int) {
        val o = outputs.first { it.outputId == id }
        val m = member(o)
        if (m == null) { addRelative(id, direction); return }
        val target = GroupVolume.targetForSingle(m, direction * GroupVolume.stepFor(index) * m.step)
        setTarget(id, target)
        // A single change retakes the snapshot, so the group continues from the new values.
        snapshot = buildSnapshot(); groupAverage = null
    }

    private fun changeGroup(direction: Int, index: Int) {
        val current = groupAverage ?: GroupVolume.average(snapshot)
        val next = GroupVolume.nextAverage(snapshot, current, direction, index)
        for ((id, v) in GroupVolume.targetsForGroup(snapshot, next)) setTarget(id, v)
        for (o in outputs) if (o.volume?.kind == VolumeKind.INCREMENTAL) addRelative(o.outputId, direction)
        groupAverage = next
        // At 0 the differences are gone: the next click starts from equal values.
        if (next <= 0.0) snapshot = snapshot.map { it.copy(value = 0.0) }
    }

    private fun setTarget(id: String, value: Double) {
        desired[id] = value
        expiry.remove(id)?.cancel()
        expiry[id] = scope.launch { delay(pendingMs); clearTarget(id); refresh() }
        pump(id)
    }

    private fun clearTarget(id: String) {
        desired.remove(id); sent.remove(id); expiry.remove(id)?.cancel()
    }

    private fun addRelative(id: String, direction: Int) {
        relative[id] = (relative[id] ?: 0) + direction
        pump(id)
    }

    // ---- sending: one request in flight per output; later clicks only update the target ----

    private fun name(id: String) = outputs.firstOrNull { it.outputId == id }?.displayName ?: id

    private fun pump(id: String) {
        if (id in inFlight) return
        val how: String
        val value: Double
        val rel = relative[id]
        if (rel != null && rel != 0) {
            how = "relative"; value = rel.toDouble(); relative.remove(id)
        } else {
            relative.remove(id)
            val v = desired[id] ?: return
            val known = sent[id] ?: outputs.firstOrNull { it.outputId == id }?.volume?.value
            if (v == known) return
            how = "absolute"; value = v; sent[id] = v
        }
        inFlight += id
        val label = name(id)
        log.info("volume send=$how output=\"$label\" value=${fmt(value)}")
        scope.launch {
            try {
                send(id, how, value)
                log.info("volume ok output=\"$label\"")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("volume failed output=\"$label\" error=${e.javaClass.simpleName}: ${e.message}")
                clearTarget(id)
                relative.remove(id)
                refresh()
            } finally {
                inFlight -= id
                pump(id)
            }
        }
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
}
