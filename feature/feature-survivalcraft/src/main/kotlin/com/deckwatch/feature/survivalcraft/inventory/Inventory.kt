package com.deckwatch.feature.survivalcraft.inventory

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Equipment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The boat / raft inventory of §7.6.
 *
 * **Persistence choice.** There is no new table: the whole list is stored as the parent
 * equipment's own dynamic attribute (§9.3) under the key `inventory`, as a JSON array of
 * [InventoryItem]. Writing goes through `EquipmentRepository.upsertEquipment`, so the inventory
 * rides along with export, import and soft-delete exactly like every other attribute, and the
 * schema of §6.5 is untouched.
 */
@Serializable
data class InventoryItem(
    /** Template key (`flares`, `rations`, …) or a generated key for a user-added row. */
    val key: String,
    /** How many the approved inventory calls for; 0 when the list does not fix a number. */
    val required: Int = 0,
    /** How many are actually on board. */
    val quantity: Int = 0,
    /** Epoch-day. Pyrotechnics, rations, water and batteries carry one; a bailer does not. */
    val expiryEpochDay: Long? = null,
    val condition: ConditionGrade = ConditionGrade.NOT_CHECKED,
    /** Label for rows the officer added by hand; template rows are labelled from strings.xml. */
    val label: String? = null,
    val remark: String? = null,
)

/** One authored checklist. Item *labels* live in strings.xml; only structure lives here. */
data class InventoryTemplate(
    val key: String,
    /** Item keys in the order they are shown. */
    val itemKeys: List<String>,
    /** Item keys that carry an expiry date. */
    val expiringKeys: Set<String> = emptySet(),
    /** Default required quantities where the LSA Code fixes one; absent = "as approved". */
    val requiredByKey: Map<String, Int> = emptyMap(),
    /** True when the officer may add numbered rows (the CO₂ cylinder register). */
    val addable: Boolean = false,
    /** Prefix for generated keys on an [addable] template. */
    val addKeyPrefix: String = "row",
)

/**
 * The bundled checklists.
 *
 * The LSA lists are the generic Chapter IV item names, not a transcription of the Code, and the
 * UI carries the standing caveat that they are verified against LSA Code Ch. IV and the vessel's
 * own approved inventory before use.
 */
object InventoryTemplates {

    const val BOAT = "LSA_CH_IV_BOAT"
    const val RAFT = "LSA_CH_IV_RAFT"
    const val CO2 = "CO2_CYLINDER_RECORD"

    private val boat = InventoryTemplate(
        key = BOAT,
        itemKeys = listOf(
            "buoyant_oars", "boat_hook", "bailer", "buckets", "sea_anchor", "painter",
            "hatchet", "torch", "signalling_mirror", "whistle", "first_aid_kit",
            "seasickness_bag", "jack_knife", "fishing_tackle", "rations", "water",
            "dipper", "graduated_cup", "rocket_parachute_flares", "hand_flares",
            "buoyant_smoke_signals", "manual_pump", "repair_kit", "fire_extinguisher",
            "searchlight", "radar_reflector", "thermal_protective_aids", "immersion_suits",
            "survival_instructions", "compass",
        ),
        expiringKeys = setOf(
            "rations", "water", "rocket_parachute_flares", "hand_flares",
            "buoyant_smoke_signals", "first_aid_kit", "fire_extinguisher", "torch",
        ),
        requiredByKey = mapOf(
            "rocket_parachute_flares" to 4,
            "hand_flares" to 6,
            "buoyant_smoke_signals" to 2,
            "sea_anchor" to 1,
            "bailer" to 1,
            "hatchet" to 2,
            "torch" to 1,
            "whistle" to 1,
            "signalling_mirror" to 1,
            "first_aid_kit" to 1,
            "fishing_tackle" to 1,
            "repair_kit" to 1,
            "thermal_protective_aids" to 3,
            "compass" to 1,
        ),
    )

    private val raft = InventoryTemplate(
        key = RAFT,
        itemKeys = listOf(
            "rescue_quoit", "knife", "bailer", "sponges", "sea_anchor", "paddles",
            "tin_opener", "first_aid_kit", "whistle", "rocket_parachute_flares",
            "hand_flares", "buoyant_smoke_signals", "torch", "signalling_mirror",
            "fishing_tackle", "rations", "water", "dipper", "graduated_cup",
            "seasickness_bag", "survival_instructions", "repair_kit", "manual_pump",
            "thermal_protective_aids",
        ),
        expiringKeys = setOf(
            "rations", "water", "rocket_parachute_flares", "hand_flares",
            "buoyant_smoke_signals", "first_aid_kit", "torch",
        ),
        requiredByKey = mapOf(
            "rocket_parachute_flares" to 4,
            "hand_flares" to 6,
            "buoyant_smoke_signals" to 2,
            "sea_anchor" to 2,
            "bailer" to 1,
            "torch" to 1,
            "signalling_mirror" to 1,
            "whistle" to 1,
            "first_aid_kit" to 1,
            "fishing_tackle" to 1,
            "repair_kit" to 1,
            "thermal_protective_aids" to 2,
        ),
    )

    /**
     * The fixed-gas system's per-cylinder record — the rows the drawing deliberately leaves out.
     * Every row is user-added: `quantity` is the cylinder number, `expiryEpochDay` the next
     * hydrostatic-test date, and the weight goes in the remark.
     */
    private val co2 = InventoryTemplate(
        key = CO2,
        itemKeys = emptyList(),
        addable = true,
        addKeyPrefix = "cylinder",
    )

    private val byKey = listOf(boat, raft, co2).associateBy { it.key }

    fun forKey(key: String?): InventoryTemplate? = key?.let { byKey[it] }
}

/**
 * Reads and writes the `inventory` attribute without disturbing the rest of the attribute bag.
 */
object InventoryCodec {

    const val ATTRIBUTE_KEY: String = "inventory"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The stored list, or an empty list when the attribute is absent or unreadable. */
    fun decode(attributesJson: String): List<InventoryItem> = runCatching {
        val array = json.parseToJsonElement(attributesJson).jsonObject[ATTRIBUTE_KEY] as? JsonArray
            ?: return emptyList()
        json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(InventoryItem.serializer()), array)
    }.getOrDefault(emptyList())

    /** [attributesJson] with the list written in; every other attribute is preserved verbatim. */
    fun encodeInto(attributesJson: String, items: List<InventoryItem>): String {
        val existing = runCatching { json.parseToJsonElement(attributesJson).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
        val array = json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(InventoryItem.serializer()),
            items,
        )
        return JsonObject(existing + (ATTRIBUTE_KEY to array)).toString()
    }

    /** Convenience for the write path: the parent with its inventory replaced. */
    fun apply(equipment: Equipment, items: List<InventoryItem>, updatedAt: Long): Equipment =
        equipment.copy(
            attributesJson = encodeInto(equipment.attributesJson, items),
            updatedAt = updatedAt,
        )

    /**
     * The stored rows merged over the template: template rows always appear (so an unchecked
     * inventory reads as "not checked" rather than as an empty list), user-added rows follow.
     */
    fun merge(template: InventoryTemplate?, stored: List<InventoryItem>): List<InventoryItem> {
        if (template == null) return stored
        val storedByKey = stored.associateBy { it.key }
        val fromTemplate = template.itemKeys.map { key ->
            storedByKey[key] ?: InventoryItem(key = key, required = template.requiredByKey[key] ?: 0)
        }
        val extra = stored.filter { it.key !in template.itemKeys }
        return fromTemplate + extra
    }

    /** The next generated key for an [InventoryTemplate.addable] list. */
    fun nextAddedKey(template: InventoryTemplate, current: List<InventoryItem>): String {
        val used = current.mapNotNull { item ->
            item.key.removePrefix("${template.addKeyPrefix}_").toIntOrNull()
        }
        return "${template.addKeyPrefix}_${(used.maxOrNull() ?: 0) + 1}"
    }
}

/** Summary of the items that expire — the "inventory expiries" line of §7.6. */
data class InventoryExpirySummary(
    val expired: Int = 0,
    val dueWithinLeadTime: Int = 0,
    val tracked: Int = 0,
    /** Soonest expiry still ahead of, or on, today. Null when nothing expires. */
    val soonestEpochDay: Long? = null,
) {
    val hasAttention: Boolean get() = expired > 0 || dueWithinLeadTime > 0
}

/** Counts the expiring rows against [todayEpochDay]. Rows with no expiry are ignored. */
fun List<InventoryItem>.expirySummary(
    todayEpochDay: Long,
    leadTimeDays: Int = INVENTORY_LEAD_TIME_DAYS,
): InventoryExpirySummary {
    val dated = mapNotNull { it.expiryEpochDay }
    if (dated.isEmpty()) return InventoryExpirySummary()
    return InventoryExpirySummary(
        expired = dated.count { it < todayEpochDay },
        dueWithinLeadTime = dated.count { it >= todayEpochDay && it - todayEpochDay <= leadTimeDays },
        tracked = dated.size,
        soonestEpochDay = dated.minOrNull(),
    )
}

/** Same 30-day lead time the due engine and the Due tab use — §11.1 (4). */
const val INVENTORY_LEAD_TIME_DAYS: Int = 30
