package com.deckwatch.core.common.due

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads [com.deckwatch.core.model.Equipment.attributesJson] into a flat
 * `attribute key -> list of string values` map so the due engine can match values against
 * [com.deckwatch.core.model.AttributeDefinition.taskKeysByValue] — §9.3.
 *
 * Every value is normalised to its string form: a `MULTI_ENUM` array yields one entry per element,
 * a boolean yields `"true"` / `"false"`, and a number yields its literal text. This is total —
 * malformed JSON yields an empty map rather than throwing, because the due engine must never fail
 * on a bad attribute blob (§11.1).
 */
internal object AttributeLookup {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse [attributesJson], adding the reserved pseudo-attribute
     * [DueEngine.VESSEL_FLAG_ATTRIBUTE_KEY] whose value is always `flagName`.
     *
     * The pseudo-attribute is written last and deliberately overwrites any stored value of the
     * same key: the vessel record is the authority on the flag, not the equipment's attribute blob.
     */
    fun parse(attributesJson: String, flagName: String): Map<String, List<String>> {
        val values = LinkedHashMap<String, List<String>>()
        val root = runCatching { json.parseToJsonElement(attributesJson) }.getOrNull()
        if (root is JsonObject) {
            for ((key, element) in root) {
                values[key] = stringValues(element)
            }
        }
        values[DueEngine.VESSEL_FLAG_ATTRIBUTE_KEY] = listOf(flagName)
        return values
    }

    private fun stringValues(element: JsonElement): List<String> = when (element) {
        is JsonNull -> emptyList()
        is JsonPrimitive -> listOf(element.content)
        is JsonArray -> element.mapNotNull { child ->
            if (child is JsonPrimitive && child !is JsonNull) child.content else null
        }
        else -> emptyList()
    }
}
