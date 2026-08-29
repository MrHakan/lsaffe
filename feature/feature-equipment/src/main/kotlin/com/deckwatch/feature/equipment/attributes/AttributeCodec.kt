package com.deckwatch.feature.equipment.attributes

import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Editor state for a type's dynamic attribute schema — §9.3.
 *
 * Every value is held as **raw display text**, whatever the field kind, so the form is a plain
 * `Map<String, String>` that survives rotation, is trivially diffable and needs no per-kind state
 * class. [AttributeCodec] is the only place that knows how a kind maps onto JSON.
 *
 * Canonical raw forms:
 * | Kind | Raw text |
 * |---|---|
 * | `TEXT`, `ENUM`, `PHOTO`, `SIGNATURE` | the string itself; blank == unset |
 * | `NUMBER` | a decimal integer literal, e.g. `12` |
 * | `DECIMAL`, `PRESSURE`, `WEIGHT` | a double literal, e.g. `9.5` |
 * | `DATE` | the epoch-day as a decimal literal (§6: all dates are epoch-days) |
 * | `BOOLEAN` | `true` or `false`; never blank |
 * | `MULTI_ENUM` | selected options joined with [AttributeCodec.MULTI_SEPARATOR]; blank == none |
 */
internal typealias AttributeDraft = Map<String, String>

/**
 * Why a field cannot be saved. An out-of-band numeric is a *warning*, never an error — see
 * [AttributeCodec.band].
 */
internal enum class AttributeError { REQUIRED, NOT_A_NUMBER }

/** Where a numeric value sits relative to the schema's `minValue`/`maxValue` green band — §9.3. */
internal enum class BandStatus { UNKNOWN, IN_BAND, OUT_OF_BAND }

/**
 * Serialises the dynamic attribute form to and from
 * [com.deckwatch.core.model.Equipment.attributesJson].
 *
 * The schema is data (§9.3), so this object is a total function of `(schema, values)`: an attribute
 * the schema does not declare is ignored on decode and carried through untouched on encode, and a
 * malformed `attributesJson` decodes to an empty draft rather than throwing — a corrupt value must
 * never cost the officer the rest of the record (C10).
 */
internal object AttributeCodec {

    /**
     * ASCII unit separator. It cannot occur in a catalogue option token, so a `MULTI_ENUM` raw
     * value is unambiguous however the options are spelled.
     */
    const val MULTI_SEPARATOR: String = "\u001F"

    private val json = Json { ignoreUnknownKeys = true }

    /** Parse [attributesJson] into raw editor text for every attribute the [schema] declares. */
    fun decode(schema: List<AttributeDefinition>, attributesJson: String): AttributeDraft =
        parseObject(attributesJson).let { stored ->
            schema.associate { definition -> definition.key to decodeOne(definition, stored[definition.key]) }
        }

    /**
     * Stored values the [schema] does not declare. Kept so that editing a record whose catalogue
     * entry has since changed never silently drops history.
     */
    fun unknownValues(schema: List<AttributeDefinition>, attributesJson: String): Map<String, JsonElement> {
        val declared = schema.mapTo(HashSet()) { it.key }
        return parseObject(attributesJson).filterKeys { it !in declared }
    }

    /**
     * Encode a draft back to a JSON object.
     *
     * Unset values are omitted rather than written as null, so `attributesJson` stays small and a
     * missing key always means "never recorded". Booleans are always written: an unticked monthly
     * checklist item is a positive statement that the item was not satisfied.
     *
     * @param carryOver values from the stored JSON that the schema no longer declares — see
     *   [unknownValues].
     */
    fun encode(
        schema: List<AttributeDefinition>,
        values: AttributeDraft,
        carryOver: Map<String, JsonElement> = emptyMap(),
    ): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(carryOver)
        for (definition in schema) {
            val encoded = encodeOne(definition, values[definition.key].orEmpty())
            if (encoded == null) out -= definition.key else out[definition.key] = encoded
        }
        return JsonObject(out)
    }

    /** [encode] rendered as the string stored on `Equipment.attributesJson`. */
    fun encodeToString(
        schema: List<AttributeDefinition>,
        values: AttributeDraft,
        carryOver: Map<String, JsonElement> = emptyMap(),
    ): String = json.encodeToString(JsonObject.serializer(), encode(schema, values, carryOver))

    /**
     * Blocking validation — required fields and malformed numerics only.
     *
     * A numeric outside the schema's min/max is deliberately **not** an error: a gauge reading below
     * the green band is exactly the observation the officer must be able to record. It surfaces as
     * [BandStatus.OUT_OF_BAND] so the form can colour it and offer a deficiency instead (§7.3).
     */
    fun validate(schema: List<AttributeDefinition>, values: AttributeDraft): Map<String, AttributeError> {
        val errors = LinkedHashMap<String, AttributeError>()
        for (definition in schema) {
            val raw = values[definition.key].orEmpty().trim()
            when {
                raw.isEmpty() && definition.required && definition.kind != AttributeKind.BOOLEAN ->
                    errors[definition.key] = AttributeError.REQUIRED
                raw.isEmpty() -> Unit
                definition.kind == AttributeKind.NUMBER || definition.kind == AttributeKind.DATE ->
                    if (raw.toLongOrNull() == null) errors[definition.key] = AttributeError.NOT_A_NUMBER
                definition.kind in NUMERIC_KINDS ->
                    if (raw.toDoubleOrNull() == null) errors[definition.key] = AttributeError.NOT_A_NUMBER
                else -> Unit
            }
        }
        return errors
    }

    /** Where a numeric raw value sits in the schema's green band — §9.3 (extinguisher gauge reading). */
    fun band(definition: AttributeDefinition, raw: String): BandStatus {
        if (definition.kind !in NUMERIC_KINDS) return BandStatus.UNKNOWN
        if (definition.minValue == null && definition.maxValue == null) return BandStatus.UNKNOWN
        val value = raw.trim().toDoubleOrNull() ?: return BandStatus.UNKNOWN
        val aboveFloor = definition.minValue?.let { value >= it } ?: true
        val belowCeiling = definition.maxValue?.let { value <= it } ?: true
        return if (aboveFloor && belowCeiling) BandStatus.IN_BAND else BandStatus.OUT_OF_BAND
    }

    /** The selected options of a `MULTI_ENUM` raw value. */
    fun multiSelection(raw: String): List<String> =
        if (raw.isEmpty()) emptyList() else raw.split(MULTI_SEPARATOR).filter { it.isNotEmpty() }

    /** The raw value for a `MULTI_ENUM` selection. */
    fun multiRaw(selection: List<String>): String = selection.joinToString(MULTI_SEPARATOR)

    /** True when the raw value of a `BOOLEAN` attribute reads as ticked. */
    fun isTicked(raw: String?): Boolean = raw?.trim().equals("true", ignoreCase = true)

    /** Kinds whose raw text is a number and which therefore take a min/max band. */
    val NUMERIC_KINDS: Set<AttributeKind> = setOf(
        AttributeKind.NUMBER,
        AttributeKind.DECIMAL,
        AttributeKind.PRESSURE,
        AttributeKind.WEIGHT,
    )

    private fun parseObject(attributesJson: String): Map<String, JsonElement> = try {
        json.parseToJsonElement(attributesJson) as? JsonObject ?: emptyMap()
    } catch (error: IllegalArgumentException) {
        // A corrupt attributesJson degrades to "nothing recorded" rather than losing the record.
        emptyMap()
    }

    private fun decodeOne(definition: AttributeDefinition, element: JsonElement?): String {
        if (element == null || element is JsonNull) {
            return if (definition.kind == AttributeKind.BOOLEAN) FALSE else ""
        }
        return when (definition.kind) {
            AttributeKind.BOOLEAN -> (element as? JsonPrimitive)?.booleanOrNull?.toString() ?: FALSE
            AttributeKind.MULTI_ENUM -> runCatching { element.jsonArray.map { it.jsonPrimitive.content } }
                .getOrDefault(emptyList())
                .let(::multiRaw)
            else -> runCatching { element.jsonPrimitive.content }.getOrDefault("")
        }
    }

    private fun encodeOne(definition: AttributeDefinition, raw: String): JsonElement? {
        val trimmed = raw.trim()
        return when (definition.kind) {
            AttributeKind.BOOLEAN -> JsonPrimitive(isTicked(trimmed))
            AttributeKind.MULTI_ENUM -> multiSelection(trimmed)
                .takeIf { it.isNotEmpty() }
                ?.let { options -> JsonArray(options.map { JsonPrimitive(it) }) }
            AttributeKind.NUMBER, AttributeKind.DATE -> trimmed.toLongOrNull()?.let { JsonPrimitive(it) }
            AttributeKind.DECIMAL, AttributeKind.PRESSURE, AttributeKind.WEIGHT ->
                trimmed.toDoubleOrNull()?.let { JsonPrimitive(it) }
            AttributeKind.TEXT, AttributeKind.ENUM, AttributeKind.PHOTO, AttributeKind.SIGNATURE ->
                trimmed.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) }
        }
    }

    private const val FALSE = "false"
}
