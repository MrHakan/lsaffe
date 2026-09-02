package com.deckwatch.feature.survivalcraft

import androidx.annotation.StringRes

/**
 * Item key → string resource for the bundled inventory checklists.
 *
 * The templates in `inventory/Inventory.kt` hold structure only; every word the officer reads
 * lives in `res/values/strings.xml` and `res/values-tr/strings.xml`, so the list is bilingual
 * without duplicating the template.
 */
internal object InventoryLabels {

    private val byKey: Map<String, Int> = mapOf(
        "buoyant_oars" to R.string.sc_inv_buoyant_oars,
        "boat_hook" to R.string.sc_inv_boat_hook,
        "bailer" to R.string.sc_inv_bailer,
        "buckets" to R.string.sc_inv_buckets,
        "sea_anchor" to R.string.sc_inv_sea_anchor,
        "painter" to R.string.sc_inv_painter,
        "hatchet" to R.string.sc_inv_hatchet,
        "torch" to R.string.sc_inv_torch,
        "signalling_mirror" to R.string.sc_inv_signalling_mirror,
        "whistle" to R.string.sc_inv_whistle,
        "first_aid_kit" to R.string.sc_inv_first_aid_kit,
        "seasickness_bag" to R.string.sc_inv_seasickness_bag,
        "jack_knife" to R.string.sc_inv_jack_knife,
        "fishing_tackle" to R.string.sc_inv_fishing_tackle,
        "rations" to R.string.sc_inv_rations,
        "water" to R.string.sc_inv_water,
        "dipper" to R.string.sc_inv_dipper,
        "graduated_cup" to R.string.sc_inv_graduated_cup,
        "rocket_parachute_flares" to R.string.sc_inv_rocket_parachute_flares,
        "hand_flares" to R.string.sc_inv_hand_flares,
        "buoyant_smoke_signals" to R.string.sc_inv_buoyant_smoke_signals,
        "manual_pump" to R.string.sc_inv_manual_pump,
        "repair_kit" to R.string.sc_inv_repair_kit,
        "fire_extinguisher" to R.string.sc_inv_fire_extinguisher,
        "searchlight" to R.string.sc_inv_searchlight,
        "radar_reflector" to R.string.sc_inv_radar_reflector,
        "thermal_protective_aids" to R.string.sc_inv_thermal_protective_aids,
        "immersion_suits" to R.string.sc_inv_immersion_suits,
        "survival_instructions" to R.string.sc_inv_survival_instructions,
        "compass" to R.string.sc_inv_compass,
        "rescue_quoit" to R.string.sc_inv_rescue_quoit,
        "knife" to R.string.sc_inv_knife,
        "sponges" to R.string.sc_inv_sponges,
        "paddles" to R.string.sc_inv_paddles,
        "tin_opener" to R.string.sc_inv_tin_opener,
    )

    @StringRes
    fun resFor(key: String): Int? = byKey[key]
}
