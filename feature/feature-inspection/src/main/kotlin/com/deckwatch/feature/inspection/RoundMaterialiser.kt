package com.deckwatch.feature.inspection

import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.model.RoundTemplate

/** A freshly-materialised round and the items the officer will walk — §6.7. */
data class MaterialisedRound(
    val round: Round,
    val items: List<RoundItem>,
)

/**
 * Turning a bundled [RoundTemplate] (§19 (5)) into a real [Round] plus its [RoundItem]s — §6.7.
 *
 * Pure functions with an injected id supplier and clock, so the materialisation is unit-tested
 * without a database or a wall clock.
 */
object RoundMaterialiser {

    /**
     * Equipment a template covers: a type-key match **or** a group match, unioned.
     *
     * `includesTypeKeys` names individual catalogue entries (§9.1); `includesGroups` names whole
     * branches of the catalogue tree (LSA / FFE / …), resolved through the type catalogue because
     * the group lives on the *type*, not on the equipment row. An item whose `typeKey` is not in the
     * catalogue can still match by type key, so a user-defined type (§9.2's escape hatch) is never
     * silently dropped from a round it was named in.
     *
     * A template that names neither types nor groups matches nothing — an empty round is a bug in
     * the seed data, not a round of the whole ship.
     *
     * The result walks the ship in stack order: decks highest-first as [deckOrder] gives them
     * (§6.2), unplaced items last, and by tag within a deck so the officer's route is stable
     * between rounds.
     */
    fun matchEquipment(
        template: RoundTemplate,
        equipment: List<Equipment>,
        typesByKey: Map<String, EquipmentType>,
        deckOrder: Map<String, Int> = emptyMap(),
    ): List<Equipment> {
        if (template.includesTypeKeys.isEmpty() && template.includesGroups.isEmpty()) return emptyList()
        val wantedTypes = template.includesTypeKeys.toSet()
        val wantedGroups = template.includesGroups.toSet()
        return equipment
            .filter { item ->
                item.deletedAt == null &&
                    (
                        item.typeKey in wantedTypes ||
                            typesByKey[item.typeKey]?.group?.let { it in wantedGroups } == true
                        )
            }
            .sortedWith(
                compareBy<Equipment> { deckOrder[it.deckId] ?: UNPLACED_ORDER }
                    .thenBy { it.tag }
                    .thenBy { it.id },
            )
    }

    /**
     * Build the round record and one item per matching piece of equipment — §6.7.
     *
     * @param idFactory supplies UUIDv4 ids (§6); the first call is the round's own id.
     * @param startedAtMillis epoch-millis stamp for [Round.startedAt].
     */
    fun materialise(
        template: RoundTemplate,
        vesselId: String,
        equipment: List<Equipment>,
        typesByKey: Map<String, EquipmentType>,
        performedBy: String,
        startedAtMillis: Long,
        idFactory: () -> String,
        deckOrder: Map<String, Int> = emptyMap(),
        turkish: Boolean = false,
    ): MaterialisedRound {
        val matched = matchEquipment(template, equipment, typesByKey, deckOrder)
        val roundId = idFactory()
        val round = Round(
            id = roundId,
            vesselId = vesselId,
            templateKey = template.key,
            title = LocalisedText(template.titleEn, template.titleTr).resolve(turkish),
            startedAt = startedAtMillis,
            completedAt = null,
            performedBy = performedBy,
            itemCount = matched.size,
            doneCount = 0,
            deficiencyCount = 0,
        )
        val items = matched.map { item ->
            RoundItem(
                id = idFactory(),
                roundId = roundId,
                equipmentId = item.id,
            )
        }
        return MaterialisedRound(round, items)
    }

    /**
     * Recount a round from its items — §6.7's denormalised counters.
     *
     * `doneCount` counts items that carry a grade; a skipped item stays ungraded and therefore
     * uncounted, which is what makes the "12 of 30 checked" progress honest. `deficiencyCount`
     * counts the grades that mean the item is not fully serviceable (§6.9), which is the set that
     * ought to raise a deficiency (§7.3).
     */
    fun recount(round: Round, items: List<RoundItem>): Round = round.copy(
        itemCount = items.size,
        doneCount = items.count { it.condition != null },
        deficiencyCount = items.count { item -> item.condition?.let { it.score <= DEFICIENT_SCORE } == true },
    )

    /** `DEFECTIVE` and `OUT_OF_SERVICE` — the grades that mean "not fully serviceable" (§6.9). */
    private const val DEFICIENT_SCORE = 1

    /** Unplaced equipment sorts after every deck (§6.5's inbox). */
    private const val UNPLACED_ORDER = Int.MAX_VALUE
}
