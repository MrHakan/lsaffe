package com.deckwatch.data.repository

import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * The due engine itself is unit-tested in `core-common`; what these tests cover is the wiring —
 * that the engine's inputs are loaded from the right tables, its instances are written, and the
 * soonest one lands on the equipment row (§11.1 step 5).
 */
class RoomMaintenanceRepositoryTest : RepositoryTest() {

    private val repository get() = maintenanceRepository()

    @Before
    fun seedCatalogueAndVessel() = runTest {
        vesselRepository().upsertVessel(TestData.vessel(id = VESSEL))
        vesselRepository().upsertDeck(TestData.deck(id = DECK, vesselId = VESSEL))
        database.equipmentTypeDao().upsert(
            TestData.equipmentType(taskKeys = listOf(MONTHLY_TASK)).toEntity(),
        )
        database.taskDefinitionDao().upsert(
            TestData.taskDefinition(key = MONTHLY_TASK, intervalKind = IntervalKind.MONTHLY).toEntity(),
        )
    }

    @Test
    fun `recompute derives the item's tasks and denormalises the soonest onto the equipment`() =
        runTest {
            val equipment = equipmentRepository()
            equipment.upsertEquipment(item())

            repository.recomputeDue(EQUIPMENT)

            val instances = repository.observeTaskInstances(EQUIPMENT).first()
            assertThat(instances.map { it.taskKey }).containsExactly(MONTHLY_TASK)
            val stored = requireNotNull(equipment.getEquipment(EQUIPMENT))
            assertThat(stored.nextDueTaskKey).isEqualTo(MONTHLY_TASK)
            assertThat(stored.nextDueDate).isEqualTo(instances.single().dueDate)
        }

    @Test
    fun `completing a task closes it and schedules the next occurrence`() = runTest {
        val equipment = equipmentRepository()
        equipment.upsertEquipment(item())
        repository.recomputeDue(EQUIPMENT)
        val open = repository.observeTaskInstances(EQUIPMENT).first().single()

        repository.completeTask(
            instanceId = open.id,
            completedDate = TestData.referenceDay,
            completedBy = "3/O",
            serviceProvider = null,
            certificateNumber = null,
            findings = "Pressure in band, seal intact",
            conditionAfter = ConditionGrade.GOOD,
        )

        val instances = repository.observeTaskInstances(EQUIPMENT).first()
        val done = instances.single { it.id == open.id }
        assertThat(done.status).isEqualTo(TaskStatus.DONE)
        assertThat(done.findings).isEqualTo("Pressure in band, seal intact")

        // The next occurrence is a month on from the completion, and it is the one now open.
        val next = instances.single { it.status != TaskStatus.DONE }
        assertThat(next.dueDate).isGreaterThan(TestData.referenceDay)
        assertThat(repository.observeOpenInstancesForVessel(VESSEL).first().map { it.id })
            .containsExactly(next.id)
    }

    @Test
    fun `a vessel-wide recomputation covers every live item and skips deleted ones`() = runTest {
        val equipment = equipmentRepository()
        equipment.upsertEquipment(item(id = "equipment-1", tag = "FE-UD-01"))
        equipment.upsertEquipment(item(id = "equipment-2", tag = "FE-UD-02"))
        equipment.upsertEquipment(item(id = "equipment-3", tag = "FE-UD-03"))
        equipment.softDelete("equipment-3", atMillis = TestData.referenceMillis)

        repository.recomputeDueForVessel(VESSEL)

        assertThat(equipment.getEquipment("equipment-1")?.nextDueTaskKey).isEqualTo(MONTHLY_TASK)
        assertThat(equipment.getEquipment("equipment-2")?.nextDueTaskKey).isEqualTo(MONTHLY_TASK)
        assertThat(equipment.getEquipment("equipment-3")?.nextDueTaskKey).isNull()
    }

    @Test
    fun `the lead time comes from the user's setting on every recomputation`() = runTest {
        val equipment = equipmentRepository()
        equipment.upsertEquipment(item())
        repository.recomputeDue(EQUIPMENT)
        val defaultStatus = repository.observeTaskInstances(EQUIPMENT).first().single().status

        // A lead time wide enough to swallow the whole first interval turns PENDING into DUE_SOON.
        preferences.setDueLeadTimeDays(WIDE_LEAD_TIME_DAYS)
        repository.recomputeDue(EQUIPMENT)

        val widened = repository.observeTaskInstances(EQUIPMENT).first().single()
        assertThat(defaultStatus).isEqualTo(TaskStatus.PENDING)
        assertThat(widened.status).isEqualTo(TaskStatus.DUE_SOON)
    }

    private fun item(id: String = EQUIPMENT, tag: String = "FE-UD-01") = TestData.equipment(
        id = id,
        vesselId = VESSEL,
        deckId = DECK,
        tag = tag,
        installedDate = TestData.referenceDay,
    )

    private companion object {
        const val VESSEL = "vessel-a"
        const val DECK = "deck-a"
        const val EQUIPMENT = "equipment-1"
        const val MONTHLY_TASK = "FE_MONTHLY_INSPECTION"
        const val WIDE_LEAD_TIME_DAYS = 400
    }
}
