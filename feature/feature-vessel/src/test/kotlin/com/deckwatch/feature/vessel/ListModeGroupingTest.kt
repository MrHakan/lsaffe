package com.deckwatch.feature.vessel

import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.vessel.list.ListModeGrouping
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** §7.1C — Deck → Zone → Equipment, with nothing allowed to vanish on the way through. */
class ListModeGroupingTest {

    private val upper = TestData.deck(id = "deck-upper", vesselId = "v", name = "Upper Deck", levelIndex = 0)
    private val bridge = TestData.deck(id = "deck-bridge", vesselId = "v", name = "Bridge Deck", levelIndex = 20)

    @Test
    fun `decks group in stack order and zones follow their sort order`() {
        val zoneAft = TestData.zone(id = "z-aft", deckId = "deck-upper", name = "Aft", sortOrder = 1)
        val zoneFwd = TestData.zone(id = "z-fwd", deckId = "deck-upper", name = "Fwd", sortOrder = 0)

        val groups = ListModeGrouping.group(
            decks = listOf(upper, bridge),
            zonesByDeck = mapOf("deck-upper" to listOf(zoneAft, zoneFwd)),
            equipment = listOf(
                TestData.equipment(id = "e1", deckId = "deck-upper", zoneId = "z-aft", tag = "FE-02"),
                TestData.equipment(id = "e2", deckId = "deck-upper", zoneId = "z-fwd", tag = "FE-01"),
                TestData.equipment(id = "e3", deckId = "deck-bridge", zoneId = null, tag = "LB-01"),
            ),
        )

        assertThat(groups.map { it.key }).containsExactly("deck-bridge", "deck-upper").inOrder()
        val upperGroup = groups.single { it.key == "deck-upper" }
        assertThat(upperGroup.zoneGroups.map { it.zone?.name }).containsExactly("Fwd", "Aft").inOrder()
    }

    @Test
    fun `unzoned equipment closes the deck under a no-zone group`() {
        val zone = TestData.zone(id = "z-fwd", deckId = "deck-upper", name = "Fwd", sortOrder = 0)

        val groups = ListModeGrouping.group(
            decks = listOf(upper),
            zonesByDeck = mapOf("deck-upper" to listOf(zone)),
            equipment = listOf(
                TestData.equipment(id = "e1", deckId = "deck-upper", zoneId = "z-fwd", tag = "FE-01"),
                TestData.equipment(id = "e2", deckId = "deck-upper", zoneId = null, tag = "FE-02"),
            ),
        )

        val zoneGroups = groups.single().zoneGroups
        assertThat(zoneGroups).hasSize(2)
        assertThat(zoneGroups.last().zone).isNull()
        assertThat(zoneGroups.last().key).isEqualTo(ListModeGrouping.NO_ZONE_KEY)
        assertThat(zoneGroups.last().equipment.map { it.id }).containsExactly("e2")
    }

    @Test
    fun `an empty zone is not shown`() {
        val zone = TestData.zone(id = "z-fwd", deckId = "deck-upper", name = "Fwd", sortOrder = 0)

        val groups = ListModeGrouping.group(
            decks = listOf(upper),
            zonesByDeck = mapOf("deck-upper" to listOf(zone)),
            equipment = emptyList(),
        )

        assertThat(groups.single().zoneGroups).isEmpty()
        assertThat(groups.single().equipmentCount).isEqualTo(0)
    }

    @Test
    fun `equipment pointing at a zone on another deck counts as unzoned`() {
        val foreignZone = TestData.zone(id = "z-other", deckId = "deck-bridge", name = "Bridge Zone", sortOrder = 0)

        val groups = ListModeGrouping.group(
            decks = listOf(upper, bridge),
            zonesByDeck = mapOf("deck-bridge" to listOf(foreignZone)),
            equipment = listOf(
                TestData.equipment(id = "e1", deckId = "deck-upper", zoneId = "z-other", tag = "FE-01"),
            ),
        )

        val upperGroup = groups.single { it.key == "deck-upper" }
        assertThat(upperGroup.zoneGroups.single().zone).isNull()
        assertThat(upperGroup.zoneGroups.single().equipment.map { it.id }).containsExactly("e1")
    }

    @Test
    fun `unplaced equipment lands in a final inbox group`() {
        val groups = ListModeGrouping.group(
            decks = listOf(upper),
            zonesByDeck = emptyMap(),
            equipment = listOf(
                TestData.equipment(id = "e1", deckId = "deck-upper", tag = "FE-01"),
                TestData.equipment(id = "e2", deckId = null, tag = "FE-99"),
            ),
        )

        assertThat(groups).hasSize(2)
        val inbox = groups.last()
        assertThat(inbox.isUnplaced).isTrue()
        assertThat(inbox.key).isEqualTo(ListModeGrouping.UNPLACED_KEY)
        assertThat(inbox.zoneGroups.single().equipment.map { it.id }).containsExactly("e2")
    }

    @Test
    fun `equipment on a deck that no longer exists still shows up as unplaced`() {
        val groups = ListModeGrouping.group(
            decks = listOf(upper),
            zonesByDeck = emptyMap(),
            equipment = listOf(TestData.equipment(id = "e1", deckId = "deck-deleted", tag = "FE-01")),
        )

        assertThat(groups.last().isUnplaced).isTrue()
        assertThat(groups.last().zoneGroups.single().equipment.map { it.id }).containsExactly("e1")
    }

    @Test
    fun `no inbox group appears when everything is placed`() {
        val groups = ListModeGrouping.group(
            decks = listOf(upper),
            zonesByDeck = emptyMap(),
            equipment = listOf(TestData.equipment(id = "e1", deckId = "deck-upper", tag = "FE-01")),
        )

        assertThat(groups.none { it.isUnplaced }).isTrue()
    }

    @Test
    fun `equipment inside a group is ordered by tag`() {
        val groups = ListModeGrouping.group(
            decks = listOf(upper),
            zonesByDeck = emptyMap(),
            equipment = listOf(
                TestData.equipment(id = "e1", deckId = "deck-upper", tag = "FE-UD-03"),
                TestData.equipment(id = "e2", deckId = "deck-upper", tag = "FE-UD-01"),
                TestData.equipment(id = "e3", deckId = "deck-upper", tag = "FE-UD-02"),
            ),
        )

        assertThat(groups.single().zoneGroups.single().equipment.map { it.tag })
            .containsExactly("FE-UD-01", "FE-UD-02", "FE-UD-03")
            .inOrder()
    }
}
