package com.deckwatch.core.common.due

import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.FlagState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** §11.1 (1), §9.3 — deriving the applicable task set from type + attributes + flag. */
class DueEngineDerivationTest {

    private val engine = DueEngine { day(2026, 1, 20) }

    private fun derive(attributesJson: String, flag: FlagState = FlagState.OTHER): Set<String> =
        engine.deriveTaskKeys(Extinguisher.type, attributesJson, flag)

    // ---------------------------------------------------------------- §9.3 worked example

    @Test
    fun `a CO2 extinguisher picks up the cylinder weight check`() {
        assertThat(derive("""{"extinguishingMedium":"CO2"}"""))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL, Extinguisher.CO2_WEIGHT)
    }

    @Test
    fun `a dry-powder extinguisher picks up the powder caking check instead`() {
        assertThat(derive("""{"extinguishingMedium":"DRY_POWDER_ABC"}"""))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL, Extinguisher.POWDER_CAKING)
    }

    @Test
    fun `changing the medium re-derives the set - CO2 tasks do not linger`() {
        val asCo2 = derive("""{"extinguishingMedium":"CO2"}""")
        val asPowder = derive("""{"extinguishingMedium":"DRY_POWDER_ABC"}""")
        assertThat(asCo2).contains(Extinguisher.CO2_WEIGHT)
        assertThat(asPowder).doesNotContain(Extinguisher.CO2_WEIGHT)
    }

    @Test
    fun `the base catalogue set is always present`() {
        assertThat(derive("{}"))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL)
    }

    @Test
    fun `an unmapped attribute value adds nothing`() {
        assertThat(derive("""{"extinguishingMedium":"WET_CHEMICAL"}"""))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL)
    }

    @Test
    fun `an attribute that does not affect tasks is ignored`() {
        val type = equipmentType(
            taskKeys = listOf("BASE"),
            attributeSchema = listOf(
                attribute(
                    key = "medium",
                    affectsTasks = false,
                    taskKeysByValue = mapOf("CO2" to listOf("EXTRA")),
                ),
            ),
        )
        assertThat(engine.deriveTaskKeys(type, """{"medium":"CO2"}""", FlagState.OTHER))
            .containsExactly("BASE")
    }

    // ---------------------------------------------------------------- totality

    @Test
    fun `malformed JSON degrades to the base set`() {
        assertThat(derive("not json at all"))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL)
    }

    @Test
    fun `an empty attribute blob degrades to the base set`() {
        assertThat(derive("")).containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL)
    }

    @Test
    fun `a JSON array instead of an object degrades to the base set`() {
        assertThat(derive("""["CO2"]"""))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL)
    }

    @Test
    fun `a null attribute value adds nothing`() {
        assertThat(derive("""{"extinguishingMedium":null}"""))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL)
    }

    @Test
    fun `an affectsTasks attribute with no value mapping adds nothing`() {
        val type = equipmentType(
            taskKeys = listOf("BASE"),
            attributeSchema = listOf(attribute("medium", taskKeysByValue = emptyMap())),
        )
        assertThat(engine.deriveTaskKeys(type, """{"medium":"CO2"}""", FlagState.OTHER))
            .containsExactly("BASE")
    }

    @Test
    fun `a nested object attribute value is ignored`() {
        val type = equipmentType(
            taskKeys = listOf("BASE"),
            attributeSchema = listOf(attribute("gauge", taskKeysByValue = mapOf("X" to listOf("EXTRA")))),
        )
        assertThat(engine.deriveTaskKeys(type, """{"gauge":{"value":"X"}}""", FlagState.OTHER))
            .containsExactly("BASE")
    }

    @Test
    fun `nulls and nested objects inside a MULTI_ENUM array are skipped`() {
        val type = equipmentType(
            taskKeys = listOf("BASE"),
            attributeSchema = listOf(
                attribute(
                    key = "fittings",
                    kind = AttributeKind.MULTI_ENUM,
                    taskKeysByValue = mapOf("HRU" to listOf("HRU_TASK")),
                ),
            ),
        )
        assertThat(engine.deriveTaskKeys(type, """{"fittings":["HRU",null,{"a":1}]}""", FlagState.OTHER))
            .containsExactly("BASE", "HRU_TASK")
    }

    @Test
    fun `a type with no schema and no tasks derives the empty set`() {
        assertThat(engine.deriveTaskKeys(equipmentType(), "{}", FlagState.OTHER)).isEmpty()
    }

    @Test
    fun `derivation is deterministic and order-stable`() {
        val first = derive("""{"extinguishingMedium":"CO2"}""").toList()
        val second = derive("""{"extinguishingMedium":"CO2"}""").toList()
        assertThat(first).isEqualTo(second)
        assertThat(first.take(2))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL).inOrder()
    }

    @Test
    fun `duplicate keys collapse - the result is a set`() {
        val type = equipmentType(
            taskKeys = listOf("BASE", "SHARED"),
            attributeSchema = listOf(attribute("medium", taskKeysByValue = mapOf("X" to listOf("SHARED")))),
        )
        assertThat(engine.deriveTaskKeys(type, """{"medium":"X"}""", FlagState.OTHER))
            .containsExactly("BASE", "SHARED")
    }

    // ---------------------------------------------------------------- value kinds

    @Test
    fun `a MULTI_ENUM array contributes every matching element`() {
        val type = equipmentType(
            taskKeys = listOf("BASE"),
            attributeSchema = listOf(
                attribute(
                    key = "fittings",
                    kind = AttributeKind.MULTI_ENUM,
                    taskKeysByValue = mapOf("HRU" to listOf("HRU_TASK"), "PAINTER" to listOf("PAINTER_TASK")),
                ),
            ),
        )
        assertThat(engine.deriveTaskKeys(type, """{"fittings":["HRU","PAINTER","OTHER"]}""", FlagState.OTHER))
            .containsExactly("BASE", "HRU_TASK", "PAINTER_TASK")
    }

    @Test
    fun `a boolean attribute matches on its literal text`() {
        val type = equipmentType(
            taskKeys = listOf("BASE"),
            attributeSchema = listOf(
                attribute(
                    key = "hasSprinkler",
                    kind = AttributeKind.BOOLEAN,
                    taskKeysByValue = mapOf("true" to listOf("SPRINKLER_TASK")),
                ),
            ),
        )
        assertThat(engine.deriveTaskKeys(type, """{"hasSprinkler":true}""", FlagState.OTHER))
            .containsExactly("BASE", "SPRINKLER_TASK")
        assertThat(engine.deriveTaskKeys(type, """{"hasSprinkler":false}""", FlagState.OTHER))
            .containsExactly("BASE")
    }

    @Test
    fun `a numeric attribute matches on its literal text`() {
        val type = equipmentType(
            taskKeys = listOf("BASE"),
            attributeSchema = listOf(
                attribute(
                    key = "cylinderCount",
                    kind = AttributeKind.NUMBER,
                    taskKeysByValue = mapOf("2" to listOf("TWIN_BANK_TASK")),
                ),
            ),
        )
        assertThat(engine.deriveTaskKeys(type, """{"cylinderCount":2}""", FlagState.OTHER))
            .containsExactly("BASE", "TWIN_BANK_TASK")
    }

    // ---------------------------------------------------------------- flag overlay (§11.5)

    @Test
    fun `the vessel flag drives tasks through the reserved pseudo-attribute`() {
        val type = equipmentType(
            typeKey = "FFE_PORTABLE_EXTINGUISHER",
            taskKeys = listOf(Extinguisher.BASE_MONTHLY),
            attributeSchema = listOf(
                attribute(
                    key = DueEngine.VESSEL_FLAG_ATTRIBUTE_KEY,
                    taskKeysByValue = mapOf("MARSHALL_ISLANDS" to listOf(Extinguisher.RMI_HYDRO)),
                ),
            ),
        )
        assertThat(engine.deriveTaskKeys(type, "{}", FlagState.MARSHALL_ISLANDS))
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.RMI_HYDRO)
        assertThat(engine.deriveTaskKeys(type, "{}", FlagState.LIBERIA))
            .containsExactly(Extinguisher.BASE_MONTHLY)
    }

    @Test
    fun `the vessel record beats a stale flag stored in the attribute blob`() {
        val type = equipmentType(
            taskKeys = listOf("BASE"),
            attributeSchema = listOf(
                attribute(
                    key = DueEngine.VESSEL_FLAG_ATTRIBUTE_KEY,
                    taskKeysByValue = mapOf(
                        "PANAMA" to listOf("PANAMA_TASK"),
                        "LIBERIA" to listOf("LIBERIA_TASK"),
                    ),
                ),
            ),
        )
        val stale = """{"${DueEngine.VESSEL_FLAG_ATTRIBUTE_KEY}":"PANAMA"}"""
        assertThat(engine.deriveTaskKeys(type, stale, FlagState.LIBERIA))
            .containsExactly("BASE", "LIBERIA_TASK")
    }

    @Test
    fun `every flag state is handled`() {
        FlagState.entries.forEach { flag ->
            assertThat(derive("""{"extinguishingMedium":"CO2"}""", flag)).contains(Extinguisher.CO2_WEIGHT)
        }
    }
}
