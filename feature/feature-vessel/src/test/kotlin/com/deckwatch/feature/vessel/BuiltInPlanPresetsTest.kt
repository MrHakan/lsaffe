package com.deckwatch.feature.vessel

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.PlanShape
import com.deckwatch.feature.vessel.deck.BuiltInPlanPresets
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** §6.3 ships six presets, and the picker must never be empty on a fresh install. */
class BuiltInPlanPresetsTest {

    @Test
    fun `six presets ship in code as the fallback`() {
        assertThat(BuiltInPlanPresets.all).hasSize(6)
        assertThat(BuiltInPlanPresets.all.map { it.key }).containsNoDuplicates()
        assertThat(BuiltInPlanPresets.all.all { it.nameEn.isNotBlank() && it.nameTr.isNotBlank() }).isTrue()
    }

    @Test
    fun `the three main-deck presets are parametric hulls`() {
        val hulls = BuiltInPlanPresets.all.filter { it.plan.shape == PlanShape.SHIP_HULL }

        assertThat(hulls).hasSize(3)
        assertThat(hulls.all { it.plan.bowSharpness in 0f..1f }).isTrue()
        assertThat(hulls.all { it.plan.sternRounding in 0f..1f }).isTrue()
    }

    @Test
    fun `an empty repository list falls back to the built-ins`() {
        assertThat(BuiltInPlanPresets.merge(emptyList())).isEqualTo(BuiltInPlanPresets.all)
    }

    @Test
    fun `a repository preset replaces its built-in twin rather than doubling it`() {
        val supplied = PlanPreset(
            key = BuiltInPlanPresets.BRIDGE_DECK,
            nameEn = "Bridge Deck (seeded)",
            nameTr = "Köprüüstü (tohumlanmış)",
            plan = DeckPlan(shape = PlanShape.RECTANGLE),
        )

        val merged = BuiltInPlanPresets.merge(listOf(supplied))

        assertThat(merged).hasSize(BuiltInPlanPresets.all.size)
        assertThat(merged.single { it.key == BuiltInPlanPresets.BRIDGE_DECK }.nameEn)
            .isEqualTo("Bridge Deck (seeded)")
    }

    @Test
    fun `an extra repository preset is appended after the six`() {
        val extra = PlanPreset(
            key = "TANK_TOP",
            nameEn = "Tank top",
            nameTr = "Tank tavanı",
            plan = DeckPlan(shape = PlanShape.RECTANGLE),
        )

        val merged = BuiltInPlanPresets.merge(listOf(extra))

        assertThat(merged).hasSize(BuiltInPlanPresets.all.size + 1)
        assertThat(merged.last().key).isEqualTo("TANK_TOP")
    }
}
