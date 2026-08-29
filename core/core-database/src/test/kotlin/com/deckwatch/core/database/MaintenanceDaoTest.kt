package com.deckwatch.core.database

import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MaintenanceDaoTest : DeckWatchDatabaseTest() {

    private val definitionDao get() = database.taskDefinitionDao()
    private val instanceDao get() = database.taskInstanceDao()

    @Before
    fun seedVesselDeckAndEquipment() = runTest {
        database.vesselDao().upsert(Fixtures.vessel())
        database.deckDao().upsert(Fixtures.deck())
        database.equipmentDao().upsert(Fixtures.equipment(id = "eq-1", tag = "FE-UD-001"))
    }

    @Test
    fun `task definition round-trips including its flag overrides and verification status`() =
        runTest {
            val original = Fixtures.taskDefinition()
            definitionDao.upsert(original)

            val stored = definitionDao.observeAll().first().single()
            assertThat(stored).isEqualTo(original)
            assertThat(stored.toModel().toEntity()).isEqualTo(original)
            assertThat(stored.flagOverrides).containsEntry(
                "LIB",
                "Trained crew may perform the annual service.",
            )
            assertThat(stored.appliesToTypeKeys).hasSize(2)

            definitionDao.upsert(original.copy(toleranceDaysAfter = 21))
            assertThat(definitionDao.getByKey(original.key)?.toleranceDaysAfter).isEqualTo(21)

            definitionDao.deleteByKey(original.key)
            assertThat(definitionDao.observeAll().first()).isEmpty()
        }

    @Test
    fun `a null flagOverrides map survives the round-trip as null`() = runTest {
        definitionDao.upsert(Fixtures.taskDefinition(key = "PLAIN").copy(flagOverrides = null))

        assertThat(definitionDao.getByKey("PLAIN")?.flagOverrides).isNull()
    }

    @Test
    fun `re-seeding replaces bundled definitions and keeps user-defined ones`() = runTest {
        definitionDao.upsertAll(
            listOf(
                Fixtures.taskDefinition(key = "BUNDLED"),
                Fixtures.taskDefinition(key = "MINE").copy(isUserDefined = true),
            ),
        )

        definitionDao.deleteBundled()

        assertThat(definitionDao.getAll().map { it.key }).containsExactly("MINE")
    }

    @Test
    fun `task instance round-trips and open instances resolve through the equipment table`() =
        runTest {
            val original = Fixtures.taskInstance(id = "ti-1", dueDate = 20_300L)
            instanceDao.upsert(original)
            instanceDao.upsert(
                Fixtures.taskInstance(id = "ti-2", dueDate = 20_250L, status = TaskStatus.OVERDUE),
            )
            instanceDao.upsert(
                Fixtures.taskInstance(id = "ti-3", dueDate = 20_100L, status = TaskStatus.DONE),
            )

            assertThat(instanceDao.observeByEquipment("eq-1").first()).hasSize(3)
            assertThat(instanceDao.getById("ti-1")).isEqualTo(original)
            assertThat(original.toModel().toEntity()).isEqualTo(original)

            val open = instanceDao.observeOpenForVessel(Fixtures.VESSEL_ID).first()
            assertThat(open.map { it.id }).containsExactly("ti-2", "ti-1").inOrder()
            assertThat(instanceDao.getSoonestOpen("eq-1")?.id).isEqualTo("ti-2")
            assertThat(
                instanceDao.observeOpenForVesselDueOnOrBefore(Fixtures.VESSEL_ID, 20_260L)
                    .first().map { it.id },
            ).containsExactly("ti-2")

            instanceDao.deleteById("ti-1")
            assertThat(instanceDao.observeByEquipment("eq-1").first().map { it.id })
                .containsExactly("ti-2", "ti-3")
        }

    @Test
    fun `open instances of soft-deleted equipment drop out of the vessel work list`() = runTest {
        instanceDao.upsert(Fixtures.taskInstance(id = "ti-1"))
        assertThat(instanceDao.observeOpenForVessel(Fixtures.VESSEL_ID).first()).hasSize(1)

        database.equipmentDao().softDelete("eq-1", atMillis = 5L)

        assertThat(instanceDao.observeOpenForVessel(Fixtures.VESSEL_ID).first()).isEmpty()
    }

    @Test
    fun `completing a task records the evidence and closes it`() = runTest {
        instanceDao.upsert(Fixtures.taskInstance(id = "ti-1"))

        instanceDao.complete(
            id = "ti-1",
            completedDate = 20_290L,
            completedBy = "3/O Yilmaz",
            serviceProvider = "Rotterdam Fire Services",
            certificateNumber = "RFS-2026-118",
            findings = "Recharged, seal replaced",
            conditionAfter = ConditionGrade.GOOD,
            atMillis = 1_700_003_000_000L,
        )

        val done = instanceDao.getById("ti-1")
        assertThat(done?.status).isEqualTo(TaskStatus.DONE)
        assertThat(done?.completedDate).isEqualTo(20_290L)
        assertThat(done?.certificateNumber).isEqualTo("RFS-2026-118")
        assertThat(done?.conditionAfter).isEqualTo(ConditionGrade.GOOD)
        assertThat(done?.updatedAt).isEqualTo(1_700_003_000_000L)
        assertThat(instanceDao.observeOpenForVessel(Fixtures.VESSEL_ID).first()).isEmpty()
        assertThat(instanceDao.getLastCompleted("eq-1", "FE_MONTHLY_INSPECTION")?.id).isEqualTo("ti-1")
    }

    @Test
    fun `deleting open instances leaves the completed history intact`() = runTest {
        instanceDao.upsertAll(
            listOf(
                Fixtures.taskInstance(id = "ti-open", status = TaskStatus.PENDING),
                Fixtures.taskInstance(id = "ti-done", status = TaskStatus.DONE),
            ),
        )

        instanceDao.deleteOpenForEquipment("eq-1")

        assertThat(instanceDao.getByEquipment("eq-1").map { it.id }).containsExactly("ti-done")
    }
}
