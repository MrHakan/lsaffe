package com.deckwatch.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The app-level survival-craft routing rule of §7.6 — see [EquipmentRouteViewModel] for why the
 * decision lives in the app module and what the cost of that is.
 */
class EquipmentRouteTest {

    @Test
    fun `survival craft and system parents open the schematic`() {
        val schematicKeys = listOf(
            "LSA_LIFEBOAT_TOTALLY_ENCLOSED",
            "LSA_LIFEBOAT_PARTIALLY_ENCLOSED",
            "LSA_LIFEBOAT_FREEFALL",
            "LSA_RESCUE_BOAT",
            "LSA_FAST_RESCUE_BOAT",
            "LSA_LIFERAFT_THROWOVER",
            "LSA_LIFERAFT_DAVIT_LAUNCHED",
            "FFE_FIXED_CO2_SYSTEM",
            "FFE_DRY_POWDER_SYSTEM",
            "FFE_SCBA_SET",
            "FFE_FIREMANS_OUTFIT",
        )
        for (key in schematicKeys) {
            assertThat(EquipmentRouteViewModel.targetFor(key)).isEqualTo(EquipmentTarget.SCHEMATIC)
        }
    }

    @Test
    fun `ordinary equipment opens the detail screen`() {
        val ordinary = listOf(
            "FFE_PORTABLE_EXTINGUISHER",
            "LSA_LIFEBUOY_PLAIN",
            "LSA_LIFEJACKET_ADULT",
            "FFE_FIRE_HYDRANT",
            "MCH_FIRE_DOOR",
            "DOC_MUSTER_LIST",
            // Sub-components of a lifeboat are equipment in their own right (§7.6 "sub-components
            // are real EquipmentEntity rows"), and they open the ordinary record, not a schematic.
            "LSA_ONLOAD_RELEASE_GEAR",
            "LSA_LIFEBOAT_DAVIT",
        )
        for (key in ordinary) {
            assertThat(EquipmentRouteViewModel.targetFor(key)).isEqualTo(EquipmentTarget.DETAIL)
        }
    }

    @Test
    fun `an unreadable item falls back to the detail screen`() {
        // A spinner that never resolves would be the worse failure; the detail screen has its own
        // "not found" state.
        assertThat(EquipmentRouteViewModel.targetFor(null)).isEqualTo(EquipmentTarget.DETAIL)
        assertThat(EquipmentRouteViewModel.targetFor("USER_DEFINED_THING"))
            .isEqualTo(EquipmentTarget.DETAIL)
    }

    @Test
    fun `every routed key exists in the bundled catalogue naming scheme`() {
        // Cheap guard against a typo: every key is LSA_* or FFE_*, matching data-seed's catalogue.
        for (key in EquipmentRouteViewModel.SCHEMATIC_TYPE_KEYS) {
            assertThat(key.startsWith("LSA_") || key.startsWith("FFE_")).isTrue()
        }
    }
}
