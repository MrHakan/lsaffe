package com.deckwatch.data.repository

import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomVesselRepositoryTest : RepositoryTest() {

    private val repository get() = vesselRepository()

    @Test
    fun `exactly one vessel is active after a switch`() = runTest {
        val repository = repository
        val first = TestData.vessel(id = "vessel-a", isActive = true)
        val second = TestData.vessel(id = "vessel-b", isActive = false)
        repository.upsertVessel(first)
        repository.upsertVessel(second)

        repository.setActiveVessel("vessel-b")

        assertThat(repository.observeActiveVessel().first()?.id).isEqualTo("vessel-b")
        assertThat(repository.observeVessels().first().filter { it.isActive }).hasSize(1)
    }

    @Test
    fun `the first deck is the ground and later decks step by ten`() = runTest {
        val repository = repository
        repository.upsertVessel(TestData.vessel(id = VESSEL))

        val main = repository.addDeckAbove(VESSEL, "Main Deck", "MD", TestData.deckPlan())
        val boat = repository.addDeckAbove(VESSEL, "Boat Deck", "BD", TestData.deckPlan())
        val tank = repository.addDeckBelow(VESSEL, "Tank Top", "TT", TestData.deckPlan())

        assertThat(main.levelIndex).isEqualTo(RoomVesselRepository.FIRST_LEVEL_INDEX)
        assertThat(boat.levelIndex).isEqualTo(RoomVesselRepository.LEVEL_STEP)
        assertThat(tank.levelIndex).isEqualTo(-RoomVesselRepository.LEVEL_STEP)

        // Highest deck first — the 2.5D stack renders top-down (§6.2).
        assertThat(repository.observeDecks(VESSEL).first().map { it.name })
            .containsExactly("Boat Deck", "Main Deck", "Tank Top")
            .inOrder()
    }

    @Test
    fun `insert between takes the midpoint and refuses adjacent neighbours`() = runTest {
        val repository = repository
        repository.upsertVessel(TestData.vessel(id = VESSEL))
        repository.addDeckAbove(VESSEL, "Main Deck", "MD", TestData.deckPlan())
        repository.addDeckAbove(VESSEL, "Boat Deck", "BD", TestData.deckPlan())

        val inserted = repository.insertDeckBetween(
            vesselId = VESSEL,
            lowerLevelIndex = 0,
            upperLevelIndex = RoomVesselRepository.LEVEL_STEP,
            name = "A Deck",
            shortCode = "AD",
            plan = TestData.deckPlan(),
        )
        assertThat(inserted.levelIndex).isEqualTo(5)

        val clash = runCatching {
            repository.insertDeckBetween(VESSEL, 0, 1, "No room", null, TestData.deckPlan())
        }
        assertThat(clash.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `global categories come back alongside the vessel's own`() = runTest {
        val repository = repository
        repository.upsertVessel(TestData.vessel(id = VESSEL))
        repository.upsertCategory(TestData.category(id = "cat-global", vesselId = null, name = "PSC Focus"))
        repository.upsertCategory(TestData.category(id = "cat-own", vesselId = VESSEL, name = "Bridge"))
        repository.upsertCategory(TestData.category(id = "cat-other", vesselId = "vessel-other", name = "Elsewhere"))

        assertThat(repository.observeCategories(VESSEL).first().map { it.id })
            .containsExactly("cat-global", "cat-own")
    }

    @Test
    fun `deleting a deck takes its zones with it`() = runTest {
        val repository = repository
        repository.upsertVessel(TestData.vessel(id = VESSEL))
        val deck = repository.addDeckAbove(VESSEL, "Main Deck", "MD", TestData.deckPlan())
        repository.upsertZone(TestData.zone(id = "zone-1", deckId = deck.id))
        assertThat(repository.observeZones(deck.id).first()).hasSize(1)

        repository.deleteDeck(deck.id)

        assertThat(repository.getDeck(deck.id)).isNull()
        assertThat(repository.observeZones(deck.id).first()).isEmpty()
    }

    @Test
    fun `deleting a vessel clears everything recorded against it`() = runTest {
        val repository = repository
        val equipment = equipmentRepository()
        val inspection = inspectionRepository()
        repository.upsertVessel(TestData.vessel(id = VESSEL))
        val deck = repository.addDeckAbove(VESSEL, "Main Deck", "MD", TestData.deckPlan())
        repository.upsertZone(TestData.zone(id = "zone-1", deckId = deck.id))
        equipment.upsertEquipment(
            TestData.equipment(id = "equipment-1", vesselId = VESSEL, deckId = deck.id),
        )
        database.taskInstanceDao().upsert(
            TestData.taskInstance(id = "instance-1", equipmentId = "equipment-1").toEntity(),
        )
        inspection.upsertRound(TestData.round(id = "round-1", vesselId = VESSEL))
        inspection.upsertRoundItem(
            TestData.roundItem(id = "item-1", roundId = "round-1", equipmentId = "equipment-1"),
        )
        inspection.upsertDeficiency(TestData.deficiency(id = "def-1", vesselId = VESSEL))

        repository.deleteVessel(VESSEL)

        assertThat(repository.getVessel(VESSEL)).isNull()
        assertThat(repository.getDeck(deck.id)).isNull()
        assertThat(repository.observeZones(deck.id).first()).isEmpty()
        assertThat(equipment.getEquipment("equipment-1")).isNull()
        assertThat(database.taskInstanceDao().getById("instance-1")).isNull()
        assertThat(inspection.getRound("round-1")).isNull()
        assertThat(database.roundItemDao().getById("item-1")).isNull()
        assertThat(inspection.getDeficiency("def-1")).isNull()
    }

    @Test
    fun `deleting a deck returns its equipment to the unplaced inbox`() = runTest {
        val repository = repository
        val equipment = equipmentRepository()
        repository.upsertVessel(TestData.vessel(id = VESSEL))
        val deck = repository.addDeckAbove(VESSEL, "Main Deck", "MD", TestData.deckPlan())
        equipment.upsertEquipment(
            TestData.equipment(id = "equipment-1", vesselId = VESSEL, deckId = deck.id),
        )

        repository.deleteDeck(deck.id)

        assertThat(equipment.observeUnplaced(VESSEL).first().map { it.id })
            .containsExactly("equipment-1")
    }

    private companion object {
        const val VESSEL = "vessel-a"
    }
}
