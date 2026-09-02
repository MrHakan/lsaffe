package com.deckwatch.feature.survivalcraft

import com.deckwatch.core.model.Equipment
import com.deckwatch.feature.survivalcraft.schematic.SchematicHotspot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The attribute key that binds a sub-component row to the hotspot it was created from.
 *
 * §6.5 is explicit that sub-components are ordinary `Equipment` rows with `parentId` set — there
 * is no join table to add. The dynamic attribute bag (§9.3) is therefore where the binding lives:
 * when a hotspot's "add" state creates a row, the row's `attributesJson` gets
 * `"hotspotKey": "<hotspot key>"`. It survives edits, export/import and re-typing, and it is the
 * only way to tell the forward hook from the aft one — both are `LSA_ONLOAD_RELEASE_GEAR`.
 */
const val HOTSPOT_ATTRIBUTE_KEY: String = "hotspotKey"

private val codecJson = Json { ignoreUnknownKeys = true }

/** The hotspot this row was created from, or null when it was never bound to one. */
fun Equipment.boundHotspotKey(): String? = runCatching {
    codecJson.parseToJsonElement(attributesJson).jsonObject[HOTSPOT_ATTRIBUTE_KEY]
        ?.jsonPrimitive?.contentOrNull
}.getOrNull()

/** [attributesJson] with the hotspot binding written in, leaving every other attribute alone. */
fun Equipment.withHotspotBinding(hotspotKey: String): Equipment {
    val existing = runCatching { codecJson.parseToJsonElement(attributesJson).jsonObject }
        .getOrDefault(JsonObject(emptyMap()))
    val merged = JsonObject(existing + (HOTSPOT_ATTRIBUTE_KEY to JsonPrimitive(hotspotKey)))
    return copy(attributesJson = merged.toString())
}

/**
 * Resolves each hotspot of a schematic to the child record it stands for.
 *
 * The rules, in order, and deliberately deterministic:
 *
 *  1. **Explicit binding.** A child whose [HOTSPOT_ATTRIBUTE_KEY] attribute equals the hotspot
 *     key. This is what the add-from-hotspot flow writes, so anything created in the app binds
 *     exactly.
 *  2. **Type match.** Among the still-unclaimed children with `typeKey == hotspot.childTypeKey`,
 *     sorted by tag, take the one at `hotspot.ordinal`. This picks up equipment that already
 *     existed before the schematic screen was ever opened (imported registers, seeded demo data).
 *     Which physical hook a bare ordinal lands on is arbitrary — it is a best-effort fallback,
 *     and the officer fixes it once by opening the row and re-tagging it.
 *  3. **Nothing.** The hotspot renders its "add" state.
 *
 * A child is never handed to two hotspots.
 */
object HotspotMatching {

    /** One hotspot and the child it resolved to (null = the hotspot is in its "add" state). */
    data class Match(val hotspot: SchematicHotspot, val child: Equipment?)

    fun match(hotspots: List<SchematicHotspot>, children: List<Equipment>): List<Match> {
        val byHotspotKey = children
            .mapNotNull { child -> child.boundHotspotKey()?.let { it to child } }
            .toMap()

        // Pass 1 — explicit bindings win, wherever the hotspot sits in the list.
        val explicit: Map<String, Equipment> = hotspots
            .mapNotNull { hotspot -> byHotspotKey[hotspot.key]?.let { hotspot.key to it } }
            .toMap()
        val claimed = explicit.values.map { it.id }.toSet()

        // Pass 2 — the unclaimed children of a type, in tag order, are handed to the unbound
        // hotspots of that type in declared-ordinal order.
        val poolByType = children
            .filter { it.id !in claimed }
            .sortedBy { it.tag }
            .groupBy { it.typeKey }
        val byTypeAssignment = mutableMapOf<String, Equipment>()
        hotspots
            .filter { it.childTypeKey != null && explicit[it.key] == null }
            .groupBy { requireNotNull(it.childTypeKey) }
            .forEach { (typeKey, unbound) ->
                val pool = poolByType[typeKey].orEmpty()
                unbound.sortedBy { it.ordinal }.forEachIndexed { index, hotspot ->
                    pool.getOrNull(index)?.let { byTypeAssignment[hotspot.key] = it }
                }
            }

        return hotspots.map { hotspot ->
            Match(hotspot, explicit[hotspot.key] ?: byTypeAssignment[hotspot.key])
        }
    }
}
