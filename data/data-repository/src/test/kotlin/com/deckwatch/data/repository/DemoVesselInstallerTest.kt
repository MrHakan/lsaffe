package com.deckwatch.data.repository

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
 * The one-tap demo vessel of §14 / §19 item 6, installed from the real `demo_vessel.json` on top of
 * the real catalogue and task definitions, so the Due tab it produces is the one the officer sees.
 */
@RunWith(RobolectricTestRunner::class)
class DemoVesselInstallerTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var harness: RepositoryHarness

    @Before
    fun setUp() = runTest {
        harness = RepositoryHarness(temporaryFolder.newFolder(), TestData.referenceDay)
        harness.seedInitializer.ensureSeeded()
    }

    @After
    fun tearDown() = harness.close()

    @Test
    fun `install materialises the whole demo vessel and computes its due list`() = runTest {
        val vesselId = harness.demoVesselInstaller.install()

        val vessel = harness.vesselRepository.getVessel(vesselId)
        assertThat(vessel?.name).isEqualTo("MV Example")
        assertThat(vessel?.isActive).isTrue()
        assertThat(harness.preferences.get().activeVesselId).isEqualTo(vesselId)

        // §19 item 6: five decks, ~60+ items with sub-components, and three open deficiencies.
        assertThat(harness.vesselRepository.observeDecks(vesselId).first()).hasSize(5)
        val equipment = harness.equipmentRepository.observeEquipment(vesselId).first()
        assertThat(equipment.size).isAtLeast(60)
        assertThat(equipment.count { it.parentId != null }).isGreaterThan(0)
        assertThat(harness.inspectionRepository.observeOpenDeficiencies(vesselId).first())
            .hasSize(3)
        assertThat(harness.countOf("zones")).isGreaterThan(0)

        // The Due tab is populated from real derived instances, not from the seed's hints.
        val open = harness.maintenanceRepository.observeOpenInstancesForVessel(vesselId).first()
        assertThat(open).isNotEmpty()
        assertThat(open.count { it.status == TaskStatus.OVERDUE }).isAtLeast(5)
        assertThat(equipment.count { it.nextDueDate != null }).isGreaterThan(0)
    }

    @Test
    fun `re-installing replaces the demo rather than duplicating it`() = runTest {
        val first = harness.demoVesselInstaller.install()
        val counts = demoCounts()

        val second = harness.demoVesselInstaller.install()

        assertThat(second).isEqualTo(first)
        assertThat(demoCounts()).isEqualTo(counts)
        assertThat(harness.vesselRepository.observeVessels().first()).hasSize(1)
    }

    @Test
    fun `uninstall removes the vessel and everything under it`() = runTest {
        val vesselId = harness.demoVesselInstaller.install()
        assertThat(harness.demoVesselInstaller.isInstalled()).isTrue()

        harness.demoVesselInstaller.uninstall()

        assertThat(harness.demoVesselInstaller.isInstalled()).isFalse()
        assertThat(harness.vesselRepository.getVessel(vesselId)).isNull()
        assertThat(harness.countOf("decks")).isEqualTo(0)
        assertThat(harness.countOf("zones")).isEqualTo(0)
        assertThat(harness.countOf("equipment")).isEqualTo(0)
        assertThat(harness.countOf("deficiencies")).isEqualTo(0)
        assertThat(harness.countOf("task_instances")).isEqualTo(0)
        // The bundled reference content is not the demo's to delete.
        assertThat(harness.countOf("task_definitions")).isGreaterThan(0)
        assertThat(harness.preferences.get().activeVesselId).isNull()
    }

    private fun demoCounts(): Map<String, Int> =
        listOf("vessels", "decks", "zones", "equipment", "deficiencies", "task_instances")
            .associateWith { harness.countOf(it) }
}
