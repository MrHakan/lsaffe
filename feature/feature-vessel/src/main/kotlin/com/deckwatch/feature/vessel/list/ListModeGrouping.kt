package com.deckwatch.feature.vessel.list

import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.Zone
import com.deckwatch.feature.vessel.deck.DeckOrdering

/** Equipment under one zone, or under the deck itself when [zone] is null. */
data class ZoneGroup(
    val zone: Zone?,
    val equipment: List<Equipment>,
) {
    val key: String get() = zone?.id ?: ListModeGrouping.NO_ZONE_KEY
}

/** One deck and its zone groups. A null [deck] is the unplaced-equipment inbox (§6.5). */
data class DeckGroup(
    val deck: Deck?,
    val zoneGroups: List<ZoneGroup>,
) {
    val key: String get() = deck?.id ?: ListModeGrouping.UNPLACED_KEY
    val equipmentCount: Int get() = zoneGroups.sumOf { it.equipment.size }
    val isUnplaced: Boolean get() = deck == null
}

/**
 * The §7.1C grouping: **Deck → Zone → Equipment**, with no graphics anywhere in it.
 *
 * Rules, all of them deliberate:
 * * decks come out in stack order, highest `levelIndex` first (§6.2);
 * * zones follow their `sortOrder`, and a zone with nothing in it is not shown — the deck row
 *   already carries the count, and empty zones are noise on a twenty-deck ship;
 * * a "no zone" group closes each deck when anything on it is unzoned;
 * * equipment whose `zoneId` points at a zone that is not on its deck counts as unzoned rather
 *   than disappearing;
 * * equipment with no deck, or with a `deckId` that matches no deck, lands in a final unplaced
 *   group. Nothing the repository hands over may fail to appear somewhere — C10.
 */
object ListModeGrouping {

    const val UNPLACED_KEY = "unplaced"
    const val NO_ZONE_KEY = "no-zone"

    fun group(
        decks: List<Deck>,
        zonesByDeck: Map<String, List<Zone>>,
        equipment: List<Equipment>,
    ): List<DeckGroup> {
        val deckIds = decks.mapTo(mutableSetOf()) { it.id }
        val byDeck = equipment.groupBy { item ->
            item.deckId?.takeIf { it in deckIds }
        }

        val deckGroups = DeckOrdering.sortedForStack(decks).map { deck ->
            val items = byDeck[deck.id].orEmpty()
            val zones = zonesByDeck[deck.id].orEmpty().sortedBy { it.sortOrder }
            val zoneIds = zones.mapTo(mutableSetOf()) { it.id }
            val byZone = items.groupBy { item -> item.zoneId?.takeIf { it in zoneIds } }

            val groups = buildList {
                for (zone in zones) {
                    val inZone = byZone[zone.id].orEmpty()
                    if (inZone.isNotEmpty()) add(ZoneGroup(zone, inZone.sortedForList()))
                }
                val unzoned = byZone[null].orEmpty()
                if (unzoned.isNotEmpty()) add(ZoneGroup(null, unzoned.sortedForList()))
            }
            DeckGroup(deck = deck, zoneGroups = groups)
        }

        val unplaced = byDeck[null].orEmpty()
        return if (unplaced.isEmpty()) {
            deckGroups
        } else {
            deckGroups + DeckGroup(deck = null, zoneGroups = listOf(ZoneGroup(null, unplaced.sortedForList())))
        }
    }

    /** Tag order: the ship's own identifiers are what the officer reads off the equipment (§6.5). */
    private fun List<Equipment>.sortedForList(): List<Equipment> = sortedBy { it.tag }
}
