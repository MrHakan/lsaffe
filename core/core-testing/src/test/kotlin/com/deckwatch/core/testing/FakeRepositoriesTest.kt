package com.deckwatch.core.testing

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class FakeRepositoriesTest {

    @Test
    fun levelIndexMechanic() = runBlocking {
        val repo = FakeVesselRepository(idFactory = SequentialIds("deck"))
        val plan = TestData.deckPlan()
        val first = repo.addDeckAbove("v1", "Upper Deck", "UD", plan)
        assertThat(first.levelIndex).isEqualTo(0)
        val above = repo.addDeckAbove("v1", "A Deck", "A", plan)
        assertThat(above.levelIndex).isEqualTo(10)
        val below = repo.addDeckBelow("v1", "ER 2nd Flat", "ER2", plan)
        assertThat(below.levelIndex).isEqualTo(-10)
        val between = repo.insertDeckBetween("v1", 0, 10, "Mezz", "MZ", plan)
        assertThat(between.levelIndex).isEqualTo(5)
        val negBetween = repo.insertDeckBetween("v1", -10, 0, "Tween", "TW", plan)
        assertThat(negBetween.levelIndex).isEqualTo(-5)
        // Another vessel starts its own stack at 0.
        assertThat(repo.addDeckAbove("v2", "Main", "M", plan).levelIndex).isEqualTo(0)
        // Descending order.
        assertThat(repo.observeDecks("v1").first().map { it.levelIndex })
            .containsExactly(10, 5, 0, -5, -10).inOrder()
        // No room -> loud failure.
        runCatching { repo.insertDeckBetween("v1", 0, 1, "X", null, plan) }
            .also { assertThat(it.isFailure).isTrue() }
        Unit
    }

    @Test
    fun activeVesselAndCascade() = runBlocking {
        val repo = FakeVesselRepository()
        val a = TestData.vessel(id = "a", name = "Alpha", isActive = false)
        val b = TestData.vessel(id = "b", name = "Bravo", isActive = false)
        repo.upsertVessel(a); repo.upsertVessel(b)
        repo.setActiveVessel("b")
        assertThat(repo.observeActiveVessel().first()?.id).isEqualTo("b")
        repo.setActiveVessel("a")
        assertThat(repo.observeActiveVessel().first()?.id).isEqualTo("a")
        assertThat(repo.observeVessels().first().map { it.name }).containsExactly("Alpha", "Bravo").inOrder()
        val plan = TestData.deckPlan()
        val deck = repo.addDeckAbove("a", "UD", "UD", plan)
        repo.upsertZone(TestData.zone(deckId = deck.id))
        repo.deleteVessel("a")
        assertThat(repo.observeDecks("a").first()).isEmpty()
        assertThat(repo.zones.value).isEmpty()
    }

    @Test
    fun equipmentSoftDeleteAndDuplicate() = runBlocking {
        val repo = FakeEquipmentRepository(idFactory = SequentialIds("eq"))
        val item = TestData.equipment(id = "eq-0", tag = "FE-UD-07")
        repo.upsertEquipment(item)
        assertThat(repo.nextTagNumber("vessel-1", "FE-UD-")).isEqualTo(8)
        val ids = repo.duplicate("eq-0", 3)
        assertThat(ids).hasSize(3)
        assertThat(repo.observeEquipment("vessel-1").first().map { it.tag })
            .containsExactly("FE-UD-07", "FE-UD-8", "FE-UD-9", "FE-UD-10")
        repo.softDelete("eq-0", 5L)
        assertThat(repo.observeEquipment("vessel-1").first().map { it.id }).doesNotContain("eq-0")
        repo.undelete("eq-0")
        assertThat(repo.observeEquipment("vessel-1").first().map { it.id }).contains("eq-0")
        repo.setCondition("eq-0", ConditionGrade.DEFECTIVE, 7L)
        assertThat(repo.getEquipment("eq-0")?.condition).isEqualTo(ConditionGrade.DEFECTIVE)
        repo.move("eq-0", "deck-9", "zone-1", 0.2f, 0.3f)
        assertThat(repo.getEquipment("eq-0")?.deckId).isEqualTo("deck-9")
        assertThat(repo.observeEquipmentOnDeck("deck-9").first()).hasSize(1)
        repo.move("eq-0", null, null, 0f, 0f)
        assertThat(repo.observeUnplaced("vessel-1").first().map { it.id }).containsExactly("eq-0")
        repo.setCategories("eq-0", listOf("c1", "c2"))
        assertThat(repo.observeCategoryIds("eq-0").first()).containsExactly("c1", "c2")
        // no-op on unknown id
        repo.setCondition("missing", ConditionGrade.GOOD, 1L)
        assertThat(repo.duplicate("missing", 2)).isEmpty()
    }

    @Test
    fun maintenanceWiringRunsTheDueEngine() = runBlocking {
        val world = FakeRepositories(today = { TestData.day(2026, 6, 1) })
        val vessel = TestData.vessel(id = "v1", safetyEquipmentCertExpiry = TestData.day(2021, 9, 30))
        val item = TestData.equipment(
            id = "e1",
            vesselId = "v1",
            attributesJson = """{"extinguishingMedium":"CO2"}""",
            installedDate = TestData.day(2025, 3, 10),
        )
        world.seed(
            vessel = vessel,
            equipmentItems = listOf(item),
            types = listOf(TestData.equipmentType()),
            definitions = listOf(
                TestData.taskDefinition(key = "FE_MONTHLY_INSPECTION", intervalKind = IntervalKind.MONTHLY),
                TestData.taskDefinition(key = "FE_ANNUAL_SERVICE", intervalKind = IntervalKind.ANNUAL),
                TestData.taskDefinition(key = "FE_CO2_CYLINDER_WEIGHT_CHECK", intervalKind = IntervalKind.BIENNIAL),
            ),
        )
        val instances = world.maintenance.observeTaskInstances("e1").first()
        assertThat(instances.map { it.taskKey })
            .containsExactly("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE", "FE_CO2_CYLINDER_WEIGHT_CHECK")
        // Denormalised onto the equipment row.
        val stored = world.equipment.getEquipment("e1")
        assertThat(stored?.nextDueDate).isEqualTo(TestData.day(2025, 4, 10))
        assertThat(stored?.nextDueTaskKey).isEqualTo("FE_MONTHLY_INSPECTION")
        // Open instances for the vessel.
        assertThat(world.maintenance.observeOpenInstancesForVessel("v1")).isNotNull()
        assertThat(world.maintenance.observeOpenInstancesForVessel("v1").first()).hasSize(3)
        // Complete the monthly and see the next occurrence move.
        val monthly = instances.first { it.taskKey == "FE_MONTHLY_INSPECTION" }
        world.maintenance.completeTask(monthly.id, TestData.day(2026, 5, 20), "3/O", null, null, null, ConditionGrade.GOOD)
        val after = world.maintenance.observeTaskInstances("e1").first()
        val done = after.first { it.id == monthly.id }
        assertThat(done.status).isEqualTo(TaskStatus.DONE)
        val nextMonthly = after.filter { it.taskKey == "FE_MONTHLY_INSPECTION" && it.status != TaskStatus.DONE }
        assertThat(nextMonthly).hasSize(1)
        assertThat(nextMonthly.single().dueDate).isEqualTo(TestData.day(2026, 6, 20))
        assertThat(world.maintenance.observeOpenInstancesForVessel("v1").first()).hasSize(3)
    }

    @Test
    fun referenceAndInspectionFakes() = runBlocking {
        val ref = FakeReferenceRepository()
        ref.seedRegulationCard(TestData.regulationCard())
        ref.seedRegulationCard(TestData.regulationCard(refKey = "FSS_CH4", citation = "FSS Ch.4", title = "Fire extinguishers", what = "Portable extinguishers"))
        assertThat(ref.searchRegulationCards("lifeboat").first()).isEmpty()
        assertThat(ref.searchRegulationCards("survival").first()).hasSize(1)
        assertThat(ref.searchRegulationCards("  ").first()).hasSize(2)
        assertThat(ref.getRegulationCard("FSS_CH4")?.title).isEqualTo("Fire extinguishers")
        ref.upsertUserDefinedType(TestData.equipmentType(typeKey = "MY_TYPE"))
        assertThat(ref.getEquipmentType("MY_TYPE")?.isUserDefined).isTrue()
        ref.seedRoundTemplate(TestData.roundTemplate())
        ref.seedPlanPreset(com.deckwatch.core.model.PlanPreset("p1", "Bulker", "Dokme", TestData.deckPlan()))
        ref.seedSymbol(TestData.symbolInfo())
        assertThat(ref.observeRoundTemplates().first()).hasSize(1)
        assertThat(ref.observePlanPresets().first()).hasSize(1)
        assertThat(ref.observeSymbols().first()).hasSize(1)
        ref.upsertUserNote(TestData.userNote(id = "n1"))
        assertThat(ref.observeUserNotes().first()).hasSize(1)
        ref.deleteUserNote("n1")
        assertThat(ref.observeUserNotes().first()).isEmpty()

        val insp = FakeInspectionRepository()
        insp.upsertRound(TestData.round(id = "r1"))
        insp.upsertRoundItem(TestData.roundItem(roundId = "r1"))
        assertThat(insp.observeRounds("vessel-1").first()).hasSize(1)
        assertThat(insp.getRound("r1")).isNotNull()
        assertThat(insp.observeRoundItems("r1").first()).hasSize(1)
        insp.upsertDeficiency(TestData.deficiency(id = "d1", severity = com.deckwatch.core.model.Severity.MINOR))
        insp.upsertDeficiency(TestData.deficiency(id = "d2", severity = com.deckwatch.core.model.Severity.CRITICAL_DETAINABLE))
        insp.upsertDeficiency(TestData.deficiency(id = "d3", status = com.deckwatch.core.model.DeficiencyStatus.CLOSED))
        assertThat(insp.observeDeficiencies("vessel-1").first().first().id).isEqualTo("d2")
        assertThat(insp.observeOpenDeficiencies("vessel-1").first().map { it.id }).containsExactly("d2", "d1")
        assertThat(insp.getDeficiency("d1")).isNotNull()
    }

    @Test
    fun testDataIdsAreDeterministic() {
        TestData.resetIds()
        assertThat(TestData.vessel().id).isEqualTo("vessel-1")
        assertThat(TestData.vessel().id).isEqualTo("vessel-2")
        TestData.resetIds()
        assertThat(TestData.vessel().id).isEqualTo("vessel-1")
        assertThat(SequentialIds("x").let { listOf(it(), it()) }).containsExactly("x-1", "x-2")
        assertThat(randomId()).hasLength(36)
    }
}
