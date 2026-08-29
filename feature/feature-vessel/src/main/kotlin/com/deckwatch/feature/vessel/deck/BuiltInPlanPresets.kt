package com.deckwatch.feature.vessel.deck

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.PlanShape

/**
 * The six §6.3 presets, held in code as the fallback for
 * [com.deckwatch.core.common.repository.ReferenceRepository.observePlanPresets].
 *
 * The presets are seed content (`plan_presets.json`, §19.7) and the repository is their home. But
 * an officer opening the app on a fresh install, or before the seed loader has run, must still be
 * able to lay out a deck in under twenty seconds — so the picker never renders empty. When the
 * repository supplies presets, they win; these fill in only when it supplies none.
 *
 * Keys match the seed file so a repository-supplied preset with the same key replaces its
 * built-in twin rather than doubling it, and so [presetNameRes] can localise both.
 */
object BuiltInPlanPresets {

    const val BULKER_MAIN_DECK = "BULKER_MAIN_DECK"
    const val TANKER_MAIN_DECK = "TANKER_MAIN_DECK"
    const val CONTAINER_MAIN_DECK = "CONTAINER_MAIN_DECK"
    const val ACCOMMODATION_BLOCK = "ACCOMMODATION_BLOCK"
    const val ENGINE_ROOM_FLAT = "ENGINE_ROOM_FLAT"
    const val BRIDGE_DECK = "BRIDGE_DECK"

    val all: List<PlanPreset> = listOf(
        PlanPreset(
            key = BULKER_MAIN_DECK,
            nameEn = "Bulker main deck",
            nameTr = "Dökme yük ana güvertesi",
            plan = DeckPlan(
                shape = PlanShape.SHIP_HULL,
                lengthRatio = 1.0f,
                breadthRatio = 1.0f,
                bowSharpness = 0.55f,
                sternRounding = 0.35f,
            ),
            suggestedShortCode = "MD",
        ),
        PlanPreset(
            key = TANKER_MAIN_DECK,
            nameEn = "Tanker main deck",
            nameTr = "Tanker ana güvertesi",
            plan = DeckPlan(
                shape = PlanShape.SHIP_HULL,
                lengthRatio = 1.0f,
                breadthRatio = 0.94f,
                bowSharpness = 0.45f,
                sternRounding = 0.45f,
            ),
            suggestedShortCode = "MD",
        ),
        PlanPreset(
            key = CONTAINER_MAIN_DECK,
            nameEn = "Container main deck",
            nameTr = "Konteyner ana güvertesi",
            plan = DeckPlan(
                shape = PlanShape.SHIP_HULL,
                lengthRatio = 1.0f,
                breadthRatio = 0.88f,
                bowSharpness = 0.78f,
                sternRounding = 0.25f,
            ),
            suggestedShortCode = "MD",
        ),
        PlanPreset(
            key = ACCOMMODATION_BLOCK,
            nameEn = "Accommodation block",
            nameTr = "Yaşam mahalli bloğu",
            plan = DeckPlan(
                shape = PlanShape.RECTANGLE,
                lengthRatio = 0.46f,
                breadthRatio = 0.82f,
            ),
            suggestedShortCode = "A",
        ),
        PlanPreset(
            key = ENGINE_ROOM_FLAT,
            nameEn = "Engine room flat",
            nameTr = "Makine dairesi platformu",
            plan = DeckPlan(
                shape = PlanShape.L_SHAPE,
                lengthRatio = 0.62f,
                breadthRatio = 0.9f,
            ),
            suggestedShortCode = "ER",
        ),
        PlanPreset(
            key = BRIDGE_DECK,
            nameEn = "Bridge deck",
            nameTr = "Köprüüstü güvertesi",
            plan = DeckPlan(
                shape = PlanShape.RECTANGLE,
                lengthRatio = 0.3f,
                breadthRatio = 0.96f,
            ),
            suggestedShortCode = "BR",
        ),
    )

    val keys: Set<String> = all.map { it.key }.toSet()

    /**
     * Repository presets take precedence; the built-ins fill the gaps so the picker always shows
     * the full six of §6.3, in a stable order.
     */
    fun merge(fromRepository: List<PlanPreset>): List<PlanPreset> {
        if (fromRepository.isEmpty()) return all
        val supplied = fromRepository.associateBy { it.key }
        val builtInsFirst = all.map { supplied[it.key] ?: it }
        val extras = fromRepository.filterNot { it.key in keys }
        return builtInsFirst + extras
    }
}
