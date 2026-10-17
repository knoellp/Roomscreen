package io.github.knoellp.roomscreen.roon.transport

enum class ResolvedVia { ID, OUTPUT }

class ResolvedZone(val zone: Zone, val via: ResolvedVia)

/**
 * Finds the zone to show for a saved selection. Roon gives a group a new zone_id, but the member
 * outputs keep their output ids, so:
 * (a) a zone with the saved zone id wins; otherwise
 * (b) a zone containing one of the saved output ids (the group, or a re-created zone); otherwise
 * (c) null.
 * The saved selection is never changed here: when the original zone returns, (a) applies again.
 */
fun resolveZone(zones: Map<String, Zone>, savedZoneId: String?, savedOutputIds: Set<String>): ResolvedZone? {
    if (savedZoneId == null) return null
    zones[savedZoneId]?.let { return ResolvedZone(it, ResolvedVia.ID) }
    if (savedOutputIds.isEmpty()) return null
    val byOutput = zones.values.firstOrNull { z -> z.outputs.any { it.outputId in savedOutputIds } }
    return byOutput?.let { ResolvedZone(it, ResolvedVia.OUTPUT) }
}
