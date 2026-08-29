package com.deckwatch.core.database

import com.deckwatch.core.database.converter.DeckWatchTypeConverters
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape
import com.deckwatch.core.model.VerificationStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The converters are pure Kotlin, so these run without Robolectric.
 *
 * The point of the enum cases is the storage *format*: a name, never an ordinal. If someone
 * switches to ordinals, inserting a constant into an enum silently rewrites the meaning of every
 * stored row — a `GOOD` extinguisher becoming `OUT_OF_SERVICE` — and no schema check would catch
 * it. These assertions on the literal text are the guard.
 */
class TypeConverterTest {

    private val converters = DeckWatchTypeConverters()

    @Test
    fun `enums are stored as their name, not their ordinal`() {
        assertThat(converters.fromConditionGrade(ConditionGrade.OUT_OF_SERVICE))
            .isEqualTo("OUT_OF_SERVICE")
        assertThat(converters.fromIntervalKind(IntervalKind.FIVE_YEARLY)).isEqualTo("FIVE_YEARLY")
        assertThat(converters.fromVerificationStatus(VerificationStatus.NEEDS_PERIODIC_REVIEW))
            .isEqualTo("NEEDS_PERIODIC_REVIEW")

        assertThat(converters.toConditionGrade("MONITOR")).isEqualTo(ConditionGrade.MONITOR)
        assertThat(converters.toIntervalKind("AT_SURVEY")).isEqualTo(IntervalKind.AT_SURVEY)
    }

    @Test
    fun `every ConditionGrade survives a round-trip`() {
        ConditionGrade.entries.forEach { grade ->
            assertThat(converters.toConditionGrade(converters.fromConditionGrade(grade)))
                .isEqualTo(grade)
        }
    }

    @Test
    fun `DeckPlan round-trips through JSON with every field preserved`() {
        val plan = DeckPlan(
            shape = PlanShape.CUSTOM_POLYGON,
            lengthRatio = 0.85f,
            breadthRatio = 0.6f,
            polygon = listOf(PlanPoint(0f, 0f), PlanPoint(1f, 0.25f), PlanPoint(0.5f, 1f)),
            bowSharpness = 0.7f,
            sternRounding = 0.2f,
            bowAtTop = false,
            backgroundImageUri = "content://ga-plan/upper-deck",
            backgroundOpacity = 0.45f,
        )

        val encoded = converters.fromDeckPlan(plan)
        assertThat(converters.toDeckPlan(encoded)).isEqualTo(plan)
        // encodeDefaults keeps the JSON self-describing rather than relying on the reader's defaults.
        assertThat(encoded).contains("\"bowAtTop\":false")
        assertThat(encoded).contains("\"shape\":\"CUSTOM_POLYGON\"")
    }

    @Test
    fun `a DeckPlan with a null background image round-trips as null`() {
        val plan = DeckPlan(shape = PlanShape.SHIP_HULL, backgroundImageUri = null)

        assertThat(converters.toDeckPlan(converters.fromDeckPlan(plan)).backgroundImageUri).isNull()
    }

    @Test
    fun `list, map and polygon converters round-trip including their empty forms`() {
        val uris = listOf("content://photo/1", "content://photo/2")
        assertThat(converters.toStringList(converters.fromStringList(uris))).isEqualTo(uris)
        assertThat(converters.toStringList(converters.fromStringList(emptyList()))).isEmpty()

        val overrides = mapOf("RMI" to "RO surveyor in attendance", "LIB" to "Trained crew allowed")
        assertThat(converters.toStringMap(converters.fromStringMap(overrides))).isEqualTo(overrides)
        assertThat(converters.toStringMap(converters.fromStringMap(emptyMap()))).isEmpty()

        val polygon = listOf(PlanPoint(0.1f, 0.2f), PlanPoint(0.9f, 0.8f))
        assertThat(converters.toPlanPointList(converters.fromPlanPointList(polygon)))
            .isEqualTo(polygon)

        val groups = listOf(EquipmentGroup.LSA, EquipmentGroup.EMERGENCY_ESCAPE)
        assertThat(converters.toEquipmentGroupList(converters.fromEquipmentGroupList(groups)))
            .isEqualTo(groups)
    }

    @Test
    fun `an attribute schema round-trips with its task-driving metadata`() {
        val schema = listOf(
            AttributeDefinition(
                key = "extinguishingMedium",
                kind = AttributeKind.ENUM,
                labelEn = "Extinguishing medium",
                labelTr = "Söndürücü madde",
                required = true,
                options = listOf("WATER", "CO2"),
                affectsTasks = true,
                taskKeysByValue = mapOf("CO2" to listOf("FE_CYLINDER_WEIGHT_CHECK")),
            ),
            AttributeDefinition(
                key = "sealIntact",
                kind = AttributeKind.BOOLEAN,
                labelEn = "Seal intact",
                monthlyChecklist = true,
            ),
        )

        val decoded = converters.toAttributeDefinitionList(
            converters.fromAttributeDefinitionList(schema),
        )

        assertThat(decoded).isEqualTo(schema)
        assertThat(decoded.first().taskKeysByValue).containsEntry("CO2", listOf("FE_CYLINDER_WEIGHT_CHECK"))
        assertThat(decoded[1].monthlyChecklist).isTrue()
    }

    @Test
    fun `an unknown JSON field is ignored so a newer build's data still reads`() {
        val forwardCompatible =
            """{"shape":"RECTANGLE","lengthRatio":1.0,"somethingNew":"ignored"}"""

        val plan = converters.toDeckPlan(forwardCompatible)

        assertThat(plan.shape).isEqualTo(PlanShape.RECTANGLE)
        assertThat(plan.lengthRatio).isEqualTo(1.0f)
    }
}
