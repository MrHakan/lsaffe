package com.deckwatch.feature.survivalcraft

import app.cash.turbine.test
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.survivalcraft.drill.DrillLog
import com.deckwatch.feature.survivalcraft.inventory.InventoryCodec
import com.deckwatch.feature.survivalcraft.schematic.SchematicCatalogue
import com.deckwatch.feature.survivalcraft.schematic.SchematicPanel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SurvivalCraftViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = TestData.referenceDay
    private val repositories = FakeRepositories(today = { today })
    private val catalogue = SchematicCatalogue()

    private val boatType = TestData.equipmentType(
        typeKey = "LSA_LIFEBOAT_TOTALLY_ENCLOSED",
        nameEn = "Lifeboat (totally enclosed)",
        nameTr = "Can filikası (tam kapalı)",
        defaultTagPrefix = "LB",
        taskKeys = listOf("SC_WEEKLY_VISUAL_INSPECTION", "SC_ANNUAL_THOROUGH_EXAM", "RG_FIVE_YEARLY_OVERLOAD_TEST"),
        attributeSchema = emptyList(),
    )
    private val winchType = TestData.equipmentType(
        typeKey = "LSA_LIFEBOAT_WINCH",
        nameEn = "Lifeboat winch",
        nameTr = "Filika vinci",
        defaultTagPrefix = "WNC",
        taskKeys = emptyList(),
        attributeSchema = emptyList(),
    )

    private val weekly = TestData.taskDefinition(
        key = "SC_WEEKLY_VISUAL_INSPECTION",
        appliesToTypeKeys = listOf(boatType.typeKey),
        titleEn = "Survival craft — weekly visual inspection",
        titleTr = "Can kurtarma aracı — haftalık gözle muayene",
        intervalKind = IntervalKind.WEEKLY,
        performedBy = PerformedBy.SHIP_STAFF,
    )
    private val annual = TestData.taskDefinition(
        key = "SC_ANNUAL_THOROUGH_EXAM",
        appliesToTypeKeys = listOf(boatType.typeKey),
        titleEn = "Annual thorough examination",
        titleTr = "Yıllık ayrıntılı muayene",
        intervalKind = IntervalKind.ANNUAL,
        intervalMonths = 12,
        performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER,
    )
    private val fiveYearly = TestData.taskDefinition(
        key = "RG_FIVE_YEARLY_OVERLOAD_TEST",
        appliesToTypeKeys = listOf(boatType.typeKey),
        titleEn = "Release gear overload operational test",
        titleTr = "Bırakma donanımı aşırı yük testi",
        intervalKind = IntervalKind.FIVE_YEARLY,
        intervalMonths = 60,
        performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER,
    )

    private val boat = TestData.equipment(
        id = "boat-1",
        vesselId = "vessel-1",
        deckId = "deck-1",
        typeKey = boatType.typeKey,
        symbolKey = "LSS001",
        tag = "LB No.1",
        attributesJson = "{}",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SurvivalCraftViewModel(
        equipmentRepository = repositories.equipment,
        referenceRepository = repositories.reference,
        maintenanceRepository = repositories.maintenance,
        inspectionRepository = repositories.inspections,
        catalogue = catalogue,
        today = { today },
        clock = { TestData.referenceMillis },
        newId = { "round-1" },
    )

    private suspend fun seedBoat() {
        repositories.seed(
            vessel = TestData.vessel(id = "vessel-1"),
            equipmentItems = listOf(boat),
            types = listOf(boatType, winchType),
            definitions = listOf(weekly, annual, fiveYearly),
        )
    }

    @Test
    fun `the lifeboat schematic and its panels are selected from the type key`() = runTest(dispatcher) {
        seedBoat()
        val model = viewModel()
        model.bind(boat.id)

        model.uiState.test {
            val state = awaitStateWithEquipment()
            assertThat(state.schematic?.key).isEqualTo("LIFEBOAT")
            assertThat(state.panels).containsExactly(
                SchematicPanel.COMPONENTS,
                SchematicPanel.INVENTORY,
                SchematicPanel.TASKS,
                SchematicPanel.DRILL_LOG,
            ).inOrder()
            assertThat(state.hotspots).isNotEmpty()
            assertThat(state.hotspots.all { it.isMissing }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a child of the hotspot type resolves the hotspot and fills the components panel`() =
        runTest(dispatcher) {
            seedBoat()
            repositories.equipment.upsertEquipment(
                TestData.equipment(
                    id = "winch-1",
                    vesselId = "vessel-1",
                    parentId = boat.id,
                    typeKey = winchType.typeKey,
                    tag = "WNC-01",
                    condition = ConditionGrade.MONITOR,
                    attributesJson = "{}",
                ),
            )
            val model = viewModel()
            model.bind(boat.id)

            model.uiState.test {
                val state = awaitStateMatching { it.components.isNotEmpty() }
                val winchHotspot = state.hotspots.first { it.hotspot.key == "winch" }
                assertThat(winchHotspot.childId).isEqualTo("winch-1")
                assertThat(winchHotspot.condition).isEqualTo(ConditionGrade.MONITOR)
                assertThat(state.components.single().tag).isEqualTo("WNC-01")
                assertThat(state.components.single().hotspotLabelEn).isEqualTo("Winch and brake")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `adopting a created row sets parentId and the hotspot binding`() = runTest(dispatcher) {
        seedBoat()
        val model = viewModel()
        model.bind(boat.id)
        model.uiState.test {
            awaitStateWithEquipment()

            // The add sheet has no parentId parameter, so the screen calls this right after
            // onCreated with the ids it produced.
            repositories.equipment.upsertEquipment(
                TestData.equipment(
                    id = "plug-1",
                    vesselId = "vessel-1",
                    typeKey = winchType.typeKey,
                    tag = "DP-01",
                    attributesJson = """{"maker":"Example"}""",
                ),
            )
            model.adoptChildren(listOf("plug-1"), "drain_plugs")

            val state = awaitStateMatching { it.components.isNotEmpty() }
            val adopted = repositories.equipment.getEquipment("plug-1")
            assertThat(adopted?.parentId).isEqualTo(boat.id)
            assertThat(adopted?.boundHotspotKey()).isEqualTo("drain_plugs")
            assertThat(adopted?.attributesJson).contains("Example")
            assertThat(state.hotspots.first { it.hotspot.key == "drain_plugs" }.childId).isEqualTo("plug-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tasks are grouped by interval, weekly first`() = runTest(dispatcher) {
        seedBoat()
        val model = viewModel()
        model.bind(boat.id)

        model.uiState.test {
            val state = awaitStateMatching { it.taskGroups.isNotEmpty() }
            assertThat(state.taskGroups.map { it.group })
                .containsExactly(TaskGroup.WEEKLY, TaskGroup.ANNUAL, TaskGroup.FIVE_YEARLY)
                .inOrder()
            val annualRow = state.taskGroups.first { it.group == TaskGroup.ANNUAL }.rows.single()
            assertThat(annualRow.taskKey).isEqualTo("SC_ANNUAL_THOROUGH_EXAM")
            assertThat(annualRow.needsProvider).isTrue()
            val weeklyRow = state.taskGroups.first { it.group == TaskGroup.WEEKLY }.rows.single()
            assertThat(weeklyRow.needsProvider).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logging a completion writes through the maintenance repository`() = runTest(dispatcher) {
        seedBoat()
        val model = viewModel()
        model.bind(boat.id)

        model.uiState.test {
            val loaded = awaitStateMatching { it.taskGroups.isNotEmpty() }
            val row = loaded.taskGroups.first { it.group == TaskGroup.WEEKLY }.rows.single()
            model.openCompletion(row.instanceId)
            model.updateCompletion { it.copy(completedBy = "3/O", certificateNumber = "C-1") }
            model.saveCompletion()

            awaitStateMatching { it.message == CraftMessage.TASK_LOGGED }
            val instance = repositories.maintenance.instances.value.getValue(row.instanceId)
            assertThat(instance.status).isEqualTo(TaskStatus.DONE)
            assertThat(instance.completedBy).isEqualTo("3/O")
            assertThat(instance.certificateNumber).isEqualTo("C-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the inventory is stored on the parent and its expiries are summarised`() =
        runTest(dispatcher) {
            seedBoat()
            val model = viewModel()
            model.bind(boat.id)

            model.uiState.test {
                awaitStateMatching { it.inventory.isNotEmpty() }
                model.updateInventoryItem("hand_flares") {
                    it.copy(quantity = 6, expiryEpochDay = today - 1)
                }
                val state = awaitStateMatching { it.inventorySummary.expired == 1 }

                val stored = InventoryCodec.decode(
                    requireNotNull(repositories.equipment.getEquipment(boat.id)).attributesJson,
                )
                assertThat(stored.first { it.key == "hand_flares" }.quantity).isEqualTo(6)
                assertThat(state.inventorySummary.soonestEpochDay).isEqualTo(today - 1)
                assertThat(state.inventory.first { it.item.key == "hand_flares" }.expires).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recording a drill writes a round and drives the days-since-launch counter`() =
        runTest(dispatcher) {
            seedBoat()
            val model = viewModel()
            model.bind(boat.id)

            model.uiState.test {
                awaitStateWithEquipment()
                model.openDrill()
                model.updateDrill {
                    it.copy(dateEpochDay = today - 12, performedBy = "C/O", launched = true)
                }
                model.saveDrill("Lifeboat drill")

                val state = awaitStateMatching { it.drills.isNotEmpty() }
                assertThat(state.daysSinceLastLaunch).isEqualTo(12)
                assertThat(state.lastDrillDay).isEqualTo(today - 12)

                val round = repositories.inspections.rounds.value.getValue("round-1")
                assertThat(round.templateKey).isEqualTo(DrillLog.templateKey(boatType.typeKey))
                assertThat(round.vesselId).isEqualTo("vessel-1")
                assertThat(DrillLog.toEpochDay(round.startedAt)).isEqualTo(today - 12)
                val notes = DrillLog.decodeNotes(round.notes)
                assertThat(notes.launched).isTrue()
                assertThat(notes.equipmentId).isEqualTo(boat.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an item with no schematic still gets the components fallback`() = runTest(dispatcher) {
        val extinguisherType = TestData.equipmentType(attributeSchema = emptyList(), taskKeys = emptyList())
        val extinguisher = TestData.equipment(id = "fe-1", vesselId = "vessel-1", attributesJson = "{}")
        repositories.seed(
            vessel = TestData.vessel(id = "vessel-1"),
            equipmentItems = listOf(extinguisher),
            types = listOf(extinguisherType),
        )
        val model = viewModel()
        model.bind(extinguisher.id)

        model.uiState.test {
            val state = awaitStateWithEquipment()
            assertThat(state.schematic?.key).isEqualTo("GENERIC_COMPONENTS")
            assertThat(state.hotspots).isEmpty()
            assertThat(state.inventoryTemplate).isNull()
            assertThat(state.panels).containsExactly(SchematicPanel.COMPONENTS, SchematicPanel.TASKS)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a deleted craft reports itself missing`() = runTest(dispatcher) {
        seedBoat()
        repositories.equipment.softDelete(boat.id, TestData.referenceMillis)
        val model = viewModel()
        model.bind(boat.id)

        model.uiState.test {
            val state = awaitStateMatching { it.missing }
            assertThat(state.equipment).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private suspend fun app.cash.turbine.ReceiveTurbine<SurvivalCraftUiState>.awaitStateWithEquipment() =
    awaitStateMatching { it.equipment != null }

private suspend fun app.cash.turbine.ReceiveTurbine<SurvivalCraftUiState>.awaitStateMatching(
    predicate: (SurvivalCraftUiState) -> Boolean,
): SurvivalCraftUiState {
    repeat(MAX_EMISSIONS) {
        val state = awaitItem()
        if (predicate(state)) return state
    }
    error("No emission matched the predicate within $MAX_EMISSIONS items")
}

private const val MAX_EMISSIONS = 40
