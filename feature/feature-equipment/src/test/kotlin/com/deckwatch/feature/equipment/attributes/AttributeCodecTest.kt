package com.deckwatch.feature.equipment.attributes

import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The attribute serialiser of §9.3: round-trip fidelity, validation and the green band. */
class AttributeCodecTest {

    private val schema = listOf(
        AttributeDefinition(key = "medium", kind = AttributeKind.ENUM, labelEn = "Medium", required = true, options = listOf("CO2", "WATER")),
        AttributeDefinition(key = "capacityKg", kind = AttributeKind.DECIMAL, labelEn = "Capacity", unit = "kg"),
        AttributeDefinition(key = "capacityPersons", kind = AttributeKind.NUMBER, labelEn = "Persons"),
        AttributeDefinition(key = "lastServiceDate", kind = AttributeKind.DATE, labelEn = "Last service"),
        AttributeDefinition(key = "accessUnobstructed", kind = AttributeKind.BOOLEAN, labelEn = "Access", monthlyChecklist = true),
        AttributeDefinition(key = "hazards", kind = AttributeKind.MULTI_ENUM, labelEn = "Hazards", options = listOf("HEAT", "SPRAY", "SHOCK")),
        AttributeDefinition(key = "gaugeBar", kind = AttributeKind.PRESSURE, labelEn = "Gauge", unit = "bar", minValue = 12.0, maxValue = 15.0),
        AttributeDefinition(key = "plate", kind = AttributeKind.PHOTO, labelEn = "Maker's plate"),
        AttributeDefinition(key = "remark", kind = AttributeKind.TEXT, labelEn = "Remark"),
    )

    private val values = mapOf(
        "medium" to "CO2",
        "capacityKg" to "6.5",
        "capacityPersons" to "26",
        "lastServiceDate" to "20454",
        "accessUnobstructed" to "true",
        "hazards" to AttributeCodec.multiRaw(listOf("HEAT", "SHOCK")),
        "gaugeBar" to "13.5",
        "plate" to "content://photo/1",
        "remark" to "Landed for service in Rotterdam",
    )

    @Test
    fun `every kind survives a round trip`() {
        val json = AttributeCodec.encodeToString(schema, values)
        assertThat(AttributeCodec.decode(schema, json)).isEqualTo(values)
    }

    @Test
    fun `encoded json uses native json types`() {
        val json = AttributeCodec.encodeToString(schema, values)
        assertThat(json).contains("\"capacityPersons\":26")
        assertThat(json).contains("\"accessUnobstructed\":true")
        assertThat(json).contains("\"lastServiceDate\":20454")
        assertThat(json).contains("\"hazards\":[\"HEAT\",\"SHOCK\"]")
        assertThat(json).contains("\"medium\":\"CO2\"")
    }

    @Test
    fun `unset values are omitted but booleans are always written`() {
        val json = AttributeCodec.encodeToString(schema, mapOf("accessUnobstructed" to "false"))
        assertThat(json).contains("\"accessUnobstructed\":false")
        assertThat(json).doesNotContain("remark")
        assertThat(json).doesNotContain("medium")
    }

    @Test
    fun `a corrupt attributesJson decodes to an empty draft rather than throwing`() {
        val decoded = AttributeCodec.decode(schema, "{not json at all")
        assertThat(decoded["remark"]).isEmpty()
        assertThat(decoded["accessUnobstructed"]).isEqualTo("false")
    }

    @Test
    fun `values the schema no longer declares are carried through an edit`() {
        val stored = """{"remark":"kept","legacyField":"do not lose me"}"""
        val carryOver = AttributeCodec.unknownValues(schema, stored)
        val json = AttributeCodec.encodeToString(schema, AttributeCodec.decode(schema, stored), carryOver)
        assertThat(json).contains("legacyField")
    }

    @Test
    fun `validation flags missing required fields and malformed numbers`() {
        val errors = AttributeCodec.validate(
            schema,
            mapOf("capacityKg" to "six", "capacityPersons" to "twenty"),
        )
        assertThat(errors["medium"]).isEqualTo(AttributeError.REQUIRED)
        assertThat(errors["capacityKg"]).isEqualTo(AttributeError.NOT_A_NUMBER)
        assertThat(errors["capacityPersons"]).isEqualTo(AttributeError.NOT_A_NUMBER)
    }

    @Test
    fun `a required boolean never blocks the save`() {
        val required = listOf(
            AttributeDefinition(key = "sealIntact", kind = AttributeKind.BOOLEAN, labelEn = "Seal", required = true),
        )
        assertThat(AttributeCodec.validate(required, mapOf("sealIntact" to "false"))).isEmpty()
    }

    @Test
    fun `an out-of-band gauge reading is a warning, not an error`() {
        val gauge = schema.first { it.key == "gaugeBar" }
        assertThat(AttributeCodec.band(gauge, "13.5")).isEqualTo(BandStatus.IN_BAND)
        assertThat(AttributeCodec.band(gauge, "9.0")).isEqualTo(BandStatus.OUT_OF_BAND)
        assertThat(AttributeCodec.validate(schema, values + ("gaugeBar" to "9.0"))).isEmpty()
    }

    @Test
    fun `a band needs both a numeric kind and a schema range`() {
        val gauge = schema.first { it.key == "gaugeBar" }
        assertThat(AttributeCodec.band(gauge, "not a number")).isEqualTo(BandStatus.UNKNOWN)
        assertThat(AttributeCodec.band(schema.first { it.key == "capacityKg" }, "6.5"))
            .isEqualTo(BandStatus.UNKNOWN)
    }
}
