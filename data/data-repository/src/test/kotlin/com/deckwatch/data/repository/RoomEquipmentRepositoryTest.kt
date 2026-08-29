package com.deckwatch.data.repository

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RoomEquipmentRepositoryTest : RepositoryTest() {

    private val repository get() = equipmentRepository()

    @Before
    fun seedVesselAndDeck() = runTest {
        val vessels = vesselRepository()
        vessels.upsertVessel(TestData.vessel(id = VESSEL))
        vessels.upsertDeck(TestData.deck(id = DECK, vesselId = VESSEL))
    }

    @Test
    fun `a soft-deleted item leaves every list but comes back on undelete`() = runTest {
        val repository = repository
        repository.upsertEquipment(item(id = "equipment-1", tag = "FE-UD-01"))
        repository.upsertEquipment(item(id = "equipment-2", tag = "FE-UD-02"))

        repository.softDelete("equipment-1", atMillis = DELETED_AT)

        assertThat(repository.observeEquipment(VESSEL).first().map { it.id })
            .containsExactly("equipment-2")
        assertThat(repository.observeEquipmentOnDeck(DECK).first().map { it.id })
            .containsExactly("equipment-2")
        // Undo needs to read the row it has just tombstoned — §7.3.
        assertThat(repository.getEquipment("equipment-1")?.deletedAt).isEqualTo(DELETED_AT)

        repository.undelete("equipment-1")

        assertThat(repository.observeEquipment(VESSEL).first().map { it.id })
            .containsExactly("equipment-1", "equipment-2")
    }

    @Test
    fun `duplicate numbers the copies on from the highest tag in use`() = runTest {
        val repository = repository
        repository.upsertEquipment(item(id = "equipment-1", tag = "FE-UD-07"))

        val copies = repository.duplicate("equipment-1", count = 3)

        assertThat(copies).containsExactly("generated-1", "generated-2", "generated-3").inOrder()
        assertThat(repository.observeEquipment(VESSEL).first().map { it.tag })
            .containsExactly("FE-UD-07", "FE-UD-8", "FE-UD-9", "FE-UD-10")
    }

    @Test
    fun `a duplicate carries no due state of its own`() = runTest {
        val repository = repository
        repository.upsertEquipment(
            item(id = "equipment-1", tag = "FE-UD-01")
                .copy(nextDueDate = TestData.referenceDay, nextDueTaskKey = "FE_MONTHLY_INSPECTION"),
        )

        val copy = repository.getEquipment(repository.duplicate("equipment-1", count = 1).single())

        assertThat(copy?.nextDueDate).isNull()
        assertThat(copy?.nextDueTaskKey).isNull()
    }

    @Test
    fun `a deleted tag frees its number for reuse`() = runTest {
        val repository = repository
        repository.upsertEquipment(item(id = "equipment-1", tag = "FE-UD-07"))
        assertThat(repository.nextTagNumber(VESSEL, "FE-UD-")).isEqualTo(8)

        repository.softDelete("equipment-1", atMillis = DELETED_AT)

        assertThat(repository.nextTagNumber(VESSEL, "FE-UD-")).isEqualTo(1)
    }

    @Test
    fun `condition, position and categories round-trip`() = runTest {
        val repository = repository
        vesselRepository().upsertCategory(TestData.category(id = "cat-1", vesselId = VESSEL))
        vesselRepository().upsertCategory(TestData.category(id = "cat-2", vesselId = VESSEL))
        repository.upsertEquipment(item(id = "equipment-1", tag = "FE-UD-01"))

        repository.setCondition("equipment-1", ConditionGrade.DEFECTIVE, atMillis = CONDITION_AT)
        repository.move("equipment-1", deckId = null, zoneId = null, posX = 0.1f, posY = 0.2f)
        repository.setCategories("equipment-1", listOf("cat-1", "cat-2"))

        val stored = requireNotNull(repository.getEquipment("equipment-1"))
        assertThat(stored.condition).isEqualTo(ConditionGrade.DEFECTIVE)
        assertThat(stored.conditionSetAt).isEqualTo(CONDITION_AT)
        assertThat(stored.deckId).isNull()
        assertThat(stored.posX).isEqualTo(0.1f)
        assertThat(repository.observeCategoryIds("equipment-1").first())
            .containsExactly("cat-1", "cat-2")
        // Off a deck, the item belongs to the unplaced inbox — §6.5.
        assertThat(repository.observeUnplaced(VESSEL).first().map { it.id })
            .containsExactly("equipment-1")
    }

    private fun item(id: String, tag: String) =
        TestData.equipment(id = id, vesselId = VESSEL, deckId = DECK, tag = tag)

    private companion object {
        const val VESSEL = "vessel-a"
        const val DECK = "deck-a"
        const val DELETED_AT = 1_800_000_000_000L
        const val CONDITION_AT = 1_700_000_000_000L
    }
}
