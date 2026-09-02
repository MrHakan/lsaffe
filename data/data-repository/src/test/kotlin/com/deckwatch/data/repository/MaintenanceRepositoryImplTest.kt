package com.deckwatch.data.repository

import com.deckwatch.core.common.Dates
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
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

/**
 * The orchestration of §11: derive, persist, denormalise, prune, and advance on completion.
 *
 * The catalogue used here is the worked example of §9.3 — a portable extinguisher whose
 * `extinguishingMedium` drives its task set: `CO2` adds a cylinder weight check, `DRY_POWDER_ABC`
 * adds a powder condition check instead.
 */
@RunWith(RobolectricTestRunner::class)
class MaintenanceRepositoryImplTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var harness: RepositoryHarness

    private val today = TestData.referenceDay
    private val vessel = TestData.vessel(id = "vessel-under-test", safetyEquipmentCertExpiry = null)

    private val extinguisherType: EquipmentType = TestData.equipmentType(
        taskKeys = listOf(MONTHLY, ANNUAL),
        attributeSchema = listOf(
            TestData.attributeDefinition(
                taskKeysByValue = mapOf(
                    "CO2" to listOf(CO2_WEIGHT),
                    "DRY_POWDER_ABC" to listOf(POWDER_CHECK),
                ),
            ),
        ),
    )

    @Before
    fun setUp() = runTest {
        harness = RepositoryHarness(temporaryFolder.newFolder(), today)
        harness.vesselRepository.upsertVessel(vessel)
        harness.referenceRepository.upsertUserDefinedType(extinguisherType)
        listOf(
            TestData.taskDefinition(key = MONTHLY, intervalKind = IntervalKind.MONTHLY),
            TestData.taskDefinition(key = ANNUAL, intervalKind = IntervalKind.ANNUAL),
            TestData.taskDefinition(key = CO2_WEIGHT, intervalKind = IntervalKind.BIENNIAL),
            TestData.taskDefinition(key = POWDER_CHECK, intervalKind = IntervalKind.ANNUAL),
        ).forEach { harness.maintenanceRepository.upsertTaskDefinition(it) }
    }

    @After
    fun tearDown() = harness.close()

    @Test
    fun `recompute derives the attribute-driven task set and denormalises the soonest due`() =
        runTest {
            val item = addExtinguisher(medium = "CO2", installedDate = today - 40)

            harness.maintenanceRepository.recomputeDue(item)

            val instances = harness.maintenanceRepository.observeTaskInstances(item).first()
            assertThat(instances.map { it.taskKey })
                .containsExactly(MONTHLY, ANNUAL, CO2_WEIGHT)

            val monthly = instances.first { it.taskKey == MONTHLY }
            assertThat(monthly.dueDate).isEqualTo(Dates.plusMonths(today - 40, 1))
            assertThat(monthly.status).isEqualTo(TaskStatus.OVERDUE)

            val equipment = harness.equipmentRepository.getEquipment(item)
            assertThat(equipment?.nextDueTaskKey).isEqualTo(MONTHLY)
            assertThat(equipment?.nextDueDate).isEqualTo(monthly.dueDate)
        }

    @Test
    fun `changing the medium prunes the stale open task but keeps its completed history`() =
        runTest {
            val item = addExtinguisher(medium = "CO2", installedDate = today - 400)
            harness.maintenanceRepository.recomputeDue(item)
            assertThat(openKeys(item)).contains(CO2_WEIGHT)

            // A weight check the ship actually performed two years ago — history, not schedule.
            val history = TestData.taskInstance(
                id = "history-co2",
                equipmentId = item,
                taskKey = CO2_WEIGHT,
                dueDate = today - 700,
                windowOpens = today - 700,
                windowCloses = today - 700,
                status = TaskStatus.DONE,
                completedDate = today - 690,
                completedBy = "2/O",
            )
            harness.maintenanceRepository.upsertInstances(listOf(history))

            switchMedium(item, "DRY_POWDER_ABC")
            harness.maintenanceRepository.recomputeDue(item)

            val all = harness.maintenanceRepository.observeTaskInstances(item).first()
            // The open CO2 occurrence is gone…
            assertThat(all.none { it.taskKey == CO2_WEIGHT && it.status !in CLOSED })
                .isTrue()
            // …the completed one is untouched…
            val kept = all.single { it.id == history.id }
            assertThat(kept.status).isEqualTo(TaskStatus.DONE)
            assertThat(kept.completedDate).isEqualTo(today - 690)
            // …and the powder check the new medium requires has appeared.
            assertThat(openKeys(item)).containsExactly(MONTHLY, ANNUAL, POWDER_CHECK)
        }

    @Test
    fun `a deferred occurrence is not pruned while its task still applies`() = runTest {
        val item = addExtinguisher(medium = "CO2", installedDate = today - 40)
        harness.maintenanceRepository.recomputeDue(item)
        val annual = harness.maintenanceRepository.observeTaskInstances(item).first()
            .first { it.taskKey == ANNUAL }
        harness.maintenanceRepository.upsertInstances(
            listOf(annual.copy(status = TaskStatus.SKIPPED)),
        )

        harness.maintenanceRepository.recomputeDue(item)

        val after = harness.maintenanceRepository.observeTaskInstances(item).first()
            .first { it.taskKey == ANNUAL }
        assertThat(after.id).isEqualTo(annual.id)
        assertThat(after.status).isEqualTo(TaskStatus.SKIPPED)
    }

    @Test
    fun `completing a task records it and schedules the next occurrence from that date`() =
        runTest {
            val item = addExtinguisher(medium = "CO2", installedDate = today - 40)
            harness.maintenanceRepository.recomputeDue(item)
            val monthly = harness.maintenanceRepository.observeTaskInstances(item).first()
                .first { it.taskKey == MONTHLY }

            harness.maintenanceRepository.completeTask(
                instanceId = monthly.id,
                completedDate = today,
                completedBy = "3/O",
                serviceProvider = null,
                certificateNumber = null,
                findings = "Gauge in the green band.",
                conditionAfter = null,
            )

            val instances = harness.maintenanceRepository.observeTaskInstances(item).first()
                .filter { it.taskKey == MONTHLY }
            val done = instances.single { it.status == TaskStatus.DONE }
            assertThat(done.id).isEqualTo(monthly.id)
            assertThat(done.completedDate).isEqualTo(today)
            assertThat(done.completedBy).isEqualTo("3/O")

            val next = instances.single { it.status !in CLOSED }
            assertThat(next.id).isNotEqualTo(monthly.id)
            assertThat(next.dueDate).isEqualTo(Dates.plusMonths(today, 1))
            // 1 January + 1 month is 31 days out and the default lead time is 30, so the fresh
            // occurrence is still one day short of DUE_SOON — §11.1 (4).
            assertThat(next.status).isEqualTo(TaskStatus.PENDING)

            // The denormalised marker colour follows the same write — §11.1 (5).
            assertThat(harness.equipmentRepository.getEquipment(item)?.nextDueDate)
                .isEqualTo(harness.maintenanceRepository.observeTaskInstances(item).first()
                    .filter { it.status !in CLOSED }.minOf { it.dueDate })
        }

    @Test
    fun `recomputing a whole vessel covers every item in one pass`() = runTest {
        val first = addExtinguisher(medium = "CO2", installedDate = today - 40, tag = "FE-UD-01")
        val second = addExtinguisher(
            medium = "DRY_POWDER_ABC",
            installedDate = today - 400,
            tag = "FE-UD-02",
        )

        harness.maintenanceRepository.recomputeDueForVessel(vessel.id)

        assertThat(openKeys(first)).containsExactly(MONTHLY, ANNUAL, CO2_WEIGHT)
        assertThat(openKeys(second)).containsExactly(MONTHLY, ANNUAL, POWDER_CHECK)
        assertThat(harness.equipmentRepository.getEquipment(first)?.nextDueDate).isNotNull()
        assertThat(harness.equipmentRepository.getEquipment(second)?.nextDueDate).isNotNull()
        assertThat(harness.maintenanceRepository.observeOpenInstancesForVessel(vessel.id).first())
            .hasSize(6)
    }

    @Test
    fun `an item whose type is not in the catalogue is skipped, not fatal`() = runTest {
        val orphan = TestData.equipment(
            vesselId = vessel.id,
            deckId = null,
            typeKey = "TYPE_NOT_SEEDED",
            tag = "XX-01",
        )
        harness.equipmentRepository.upsertEquipment(orphan)

        harness.maintenanceRepository.recomputeDue(orphan.id)
        harness.maintenanceRepository.recomputeDueForVessel(vessel.id)

        assertThat(harness.maintenanceRepository.observeTaskInstances(orphan.id).first()).isEmpty()
        assertThat(harness.equipmentRepository.getEquipment(orphan.id)?.nextDueDate).isNull()
    }

    private suspend fun addExtinguisher(
        medium: String,
        installedDate: Long,
        tag: String = "FE-UD-01",
    ): String {
        val item = TestData.equipment(
            vesselId = vessel.id,
            deckId = null,
            typeKey = extinguisherType.typeKey,
            tag = tag,
            installedDate = installedDate,
            manufactureDate = installedDate,
            attributesJson = """{"extinguishingMedium":"$medium"}""",
        )
        harness.equipmentRepository.upsertEquipment(item)
        return item.id
    }

    private suspend fun switchMedium(equipmentId: String, medium: String) {
        val current = requireNotNull(harness.equipmentRepository.getEquipment(equipmentId))
        harness.equipmentRepository.upsertEquipment(
            current.copy(attributesJson = """{"extinguishingMedium":"$medium"}"""),
        )
    }

    private suspend fun openKeys(equipmentId: String): List<String> =
        harness.maintenanceRepository.observeTaskInstances(equipmentId).first()
            .filter { it.status !in CLOSED }
            .map(TaskInstance::taskKey)

    private companion object {
        const val MONTHLY = "FE_MONTHLY_INSPECTION"
        const val ANNUAL = "FE_ANNUAL_SERVICE"
        const val CO2_WEIGHT = "FE_CO2_CYLINDER_WEIGHT_CHECK"
        const val POWDER_CHECK = "FE_POWDER_CONDITION_CHECK"

        val CLOSED = setOf(TaskStatus.DONE, TaskStatus.NOT_APPLICABLE)
    }
}
