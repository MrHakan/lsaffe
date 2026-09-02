package com.deckwatch.feature.survivalcraft

import com.deckwatch.feature.survivalcraft.schematic.SchematicCatalogue
import com.deckwatch.feature.survivalcraft.schematic.SchematicResourceReader
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The bundled schematics are read from the very files that ship in the AAR (the module registers
 * `src/main/assets` as a Java-resources root), so these assertions are about real content.
 */
class SchematicCatalogueTest {

    private val catalogue = SchematicCatalogue()

    @Test
    fun `bundled index loads every authored schematic`() {
        assertThat(catalogue.all.map { it.key }).containsExactly(
            "LIFEBOAT",
            "LIFERAFT_HRU",
            "RESCUE_BOAT",
            "FIXED_CO2",
            "SCBA_SET",
            "FIREMANS_OUTFIT_LOCKER",
            "GENERIC_COMPONENTS",
        ).inOrder()
    }

    @Test
    fun `selection is by the parent type key`() {
        assertThat(catalogue.forTypeKey("LSA_LIFEBOAT_TOTALLY_ENCLOSED").key).isEqualTo("LIFEBOAT")
        assertThat(catalogue.forTypeKey("LSA_LIFEBOAT_PARTIALLY_ENCLOSED").key).isEqualTo("LIFEBOAT")
        assertThat(catalogue.forTypeKey("LSA_RESCUE_BOAT").key).isEqualTo("RESCUE_BOAT")
        assertThat(catalogue.forTypeKey("LSA_FAST_RESCUE_BOAT").key).isEqualTo("RESCUE_BOAT")
        assertThat(catalogue.forTypeKey("LSA_LIFERAFT_THROWOVER").key).isEqualTo("LIFERAFT_HRU")
        assertThat(catalogue.forTypeKey("FFE_FIXED_CO2_SYSTEM").key).isEqualTo("FIXED_CO2")
        assertThat(catalogue.forTypeKey("FFE_SCBA_SET").key).isEqualTo("SCBA_SET")
        assertThat(catalogue.forTypeKey("FFE_FIREMANS_OUTFIT").key).isEqualTo("FIREMANS_OUTFIT_LOCKER")
    }

    @Test
    fun `an unmapped or missing type falls back to the components-only schematic`() {
        assertThat(catalogue.forTypeKey("FFE_PORTABLE_EXTINGUISHER").key).isEqualTo("GENERIC_COMPONENTS")
        assertThat(catalogue.forTypeKey(null).key).isEqualTo("GENERIC_COMPONENTS")
        assertThat(catalogue.forTypeKey("GENERIC_COMPONENTS").hotspots).isEmpty()
    }

    @Test
    fun `the lifeboat carries every hotspot the specification names`() {
        val lifeboat = catalogue.forTypeKey("LSA_LIFEBOAT_TOTALLY_ENCLOSED")
        assertThat(lifeboat.hotspots.map { it.key }).containsAtLeast(
            "davit", "winch", "falls", "sheaves", "limit_switches",
            "hook_fwd", "hook_aft", "hydrostatic_interlock", "painter",
            "engine", "fuel", "batteries", "sprinkler", "air_support",
            "compass", "radio", "drain_plugs",
        )
        assertThat(lifeboat.inventoryTemplateKey).isEqualTo("LSA_CH_IV_BOAT")
    }

    @Test
    fun `the two release hooks share a type and are separated by ordinal`() {
        val hooks = catalogue.forTypeKey("LSA_LIFEBOAT_TOTALLY_ENCLOSED").hotspots
            .filter { it.childTypeKey == "LSA_ONLOAD_RELEASE_GEAR" }
        assertThat(hooks).hasSize(2)
        assertThat(hooks.map { it.ordinal }).containsExactly(0, 1)
    }

    @Test
    fun `every authored coordinate stays inside the unit box`() {
        catalogue.all.forEach { definition ->
            definition.shapes.forEach { shape ->
                assertThat(shape.points).isNotEmpty()
                shape.points.forEach { value ->
                    assertThat(value).isAtLeast(-0.05f)
                    assertThat(value).isAtMost(1.05f)
                }
            }
            definition.hotspots.forEach { hotspot ->
                assertThat(hotspot.touchX).isAtLeast(0f)
                assertThat(hotspot.touchX).isAtMost(1f)
                assertThat(hotspot.touchY).isAtLeast(0f)
                assertThat(hotspot.touchY).isAtMost(1f)
                assertThat(hotspot.labelEn).isNotEmpty()
                assertThat(hotspot.labelTr).isNotEmpty()
            }
            assertThat(definition.hotspots.map { it.key }).containsNoDuplicates()
            assertThat(definition.aspect).isGreaterThan(0f)
        }
    }

    @Test
    fun `an unreadable index still yields a usable fallback`() {
        val empty = SchematicCatalogue(SchematicResourceReader { null })
        assertThat(empty.all.map { it.key }).containsExactly("GENERIC_COMPONENTS")
        assertThat(empty.forTypeKey("LSA_LIFEBOAT_TOTALLY_ENCLOSED").hotspots).isEmpty()
    }

    @Test
    fun `a synthetic definition is honoured just like a bundled one`() {
        val reader = SchematicResourceReader { path ->
            when (path) {
                "schematics/index.json" -> """{"files":["schematics/x.json"]}"""
                "schematics/x.json" -> """
                    {
                      "key": "X",
                      "appliesToTypeKeys": ["TYPE_X"],
                      "titleEn": "X", "titleTr": "X",
                      "shapes": [{"kind":"RECT","points":[0.1,0.1,0.9,0.9]}],
                      "hotspots": [{"key":"a","labelEn":"A","labelTr":"A","x":0.5,"y":0.5}]
                    }
                """.trimIndent()
                else -> null
            }
        }
        val custom = SchematicCatalogue(reader)
        assertThat(custom.forTypeKey("TYPE_X").hotspots.single().key).isEqualTo("a")
        // No GENERIC_COMPONENTS in this index, so the in-code fallback answers instead.
        assertThat(custom.forTypeKey("TYPE_Y").key).isEqualTo("GENERIC_COMPONENTS")
    }
}
