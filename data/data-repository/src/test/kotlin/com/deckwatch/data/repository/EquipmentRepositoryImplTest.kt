package com.deckwatch.data.repository

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Soft deletion (C10) and "duplicate ×N" (§7.5), against the real database. */
@RunWith(RobolectricTestRunner::class)
class EquipmentRepositoryImplTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var harness: RepositoryHarness

    private val vessel = TestData.vessel(id = "vessel-under-test")

    @Before
    fun setUp() {
        harness = RepositoryHarness(temporaryFolder.newFolder(), TestData.referenceDay)
    }

    @After
    fun tearDown() = harness.close()

    @Test
    fun `soft delete hides the item from every list and undo brings it back`() = runTest {
        harness.vesselRepository.upsertVessel(vessel)
        val item = TestData.equipment(vesselId = vessel.id, deckId = null, tag = "FE-UD-01")
        harness.equipmentRepository.upsertEquipment(item)

        harness.equipmentRepository.softDelete(item.id, atMillis = 1_000L)

        assertThat(harness.equipmentRepository.observeEquipment(vessel.id).first()).isEmpty()
        assertThat(harness.equipmentRepository.observeUnplaced(vessel.id).first()).isEmpty()
        assertThat(harness.equipmentRepository.getEquipment(item.id)).isNull()
        // The row is still there — that is what makes the 10-second undo possible.
        assertThat(harness.countOf("equipment")).isEqualTo(1)

        harness.equipmentRepository.undelete(item.id)

        assertThat(harness.equipmentRepository.observeEquipment(vessel.id).first()).hasSize(1)
        assertThat(harness.equipmentRepository.getEquipment(item.id)?.deletedAt).isNull()
    }

    @Test
    fun `duplicate increments the tag number and keeps the ship's padding`() = runTest {
        harness.vesselRepository.upsertVessel(vessel)
        val source = TestData.equipment(
            vesselId = vessel.id,
            deckId = null,
            tag = "FE-UD-07",
            serialNumber = "SN-12345",
            condition = ConditionGrade.GOOD,
            nextDueDate = TestData.referenceDay,
            nextDueTaskKey = "FE_MONTHLY_INSPECTION",
        )
        harness.equipmentRepository.upsertEquipment(source)

        val ids = harness.equipmentRepository.duplicate(source.id, count = 3)

        assertThat(ids).hasSize(3)
        val tags = harness.equipmentRepository.observeEquipment(vessel.id).first().map { it.tag }
        assertThat(tags).containsExactly("FE-UD-07", "FE-UD-08", "FE-UD-09", "FE-UD-10")

        val copy = harness.equipmentRepository.getEquipment(ids.first())
        assertThat(copy?.typeKey).isEqualTo(source.typeKey)
        assertThat(copy?.attributesJson).isEqualTo(source.attributesJson)
        // A copy is a new physical item: no serial, no inherited grade, no inherited due state.
        assertThat(copy?.serialNumber).isNull()
        assertThat(copy?.nextDueDate).isNull()
        assertThat(copy?.condition).isEqualTo(ConditionGrade.NOT_CHECKED)
    }

    @Test
    fun `duplicate skips numbers already taken and numbers a tag that has none`() = runTest {
        harness.vesselRepository.upsertVessel(vessel)
        harness.equipmentRepository.upsertEquipment(
            TestData.equipment(vesselId = vessel.id, deckId = null, tag = "FE-UD-01"),
        )
        harness.equipmentRepository.upsertEquipment(
            TestData.equipment(vesselId = vessel.id, deckId = null, tag = "FE-UD-14"),
        )
        val blanket = TestData.equipment(vesselId = vessel.id, deckId = null, tag = "Fire blanket")
        harness.equipmentRepository.upsertEquipment(blanket)

        val source = harness.equipmentRepository.observeEquipment(vessel.id).first()
            .first { it.tag == "FE-UD-01" }
        harness.equipmentRepository.duplicate(source.id, count = 2)
        harness.equipmentRepository.duplicate(blanket.id, count = 1)

        val tags = harness.equipmentRepository.observeEquipment(vessel.id).first().map { it.tag }
        assertThat(tags).containsAtLeast("FE-UD-15", "FE-UD-16", "Fire blanket-1")
        assertThat(harness.equipmentRepository.nextTagNumber(vessel.id, "FE-UD-")).isEqualTo(17)
    }

    @Test
    fun `duplicate of a missing item and a non-positive count write nothing`() = runTest {
        harness.vesselRepository.upsertVessel(vessel)
        assertThat(harness.equipmentRepository.duplicate("no-such-id", count = 2)).isEmpty()
        assertThat(harness.equipmentRepository.duplicate("no-such-id", count = 0)).isEmpty()
        assertThat(harness.countOf("equipment")).isEqualTo(0)
    }

    @Test
    fun `move places an item on a deck and unplaces it again`() = runTest {
        harness.vesselRepository.upsertVessel(vessel)
        val deck = harness.vesselRepository.addDeckAbove(
            vessel.id,
            "Upper Deck",
            "UD",
            TestData.deckPlan(),
        )
        val item = TestData.equipment(vesselId = vessel.id, deckId = null)
        harness.equipmentRepository.upsertEquipment(item)

        harness.equipmentRepository.move(item.id, deck.id, zoneId = null, posX = 0.2f, posY = 0.8f)

        val placed = harness.equipmentRepository.observeEquipmentOnDeck(deck.id).first()
        assertThat(placed.map { it.id }).containsExactly(item.id)
        assertThat(placed.first().posX).isEqualTo(0.2f)

        harness.equipmentRepository.move(item.id, null, null, posX = 0.5f, posY = 0.5f)

        assertThat(harness.equipmentRepository.observeEquipmentOnDeck(deck.id).first()).isEmpty()
        assertThat(harness.equipmentRepository.observeUnplaced(vessel.id).first()).hasSize(1)
    }
}
