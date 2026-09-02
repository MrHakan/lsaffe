package com.deckwatch.feature.report

import com.deckwatch.core.common.DefaultDispatcherProvider
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The two-phase apply of §13.5 — see [ImportApplier]'s documented guarantee. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportApplierTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fakes = FakeRepositories()

    private fun applier() = ImportApplier(
        vesselRepository = fakes.vessels,
        equipmentRepository = fakes.equipment,
        maintenanceRepository = fakes.maintenance,
        inspectionRepository = fakes.inspections,
        referenceRepository = fakes.reference,
        dispatchers = DefaultDispatcherProvider(main = dispatcher, io = dispatcher, default = dispatcher),
        clock = { TestData.referenceMillis },
    )

    @Test
    fun `an empty device takes the whole file`() = runTest(dispatcher) {
        val incoming = ReportFixtures.payload()
        val applier = applier()

        val snapshot = applier.snapshot(incoming)
        val plan = ImportMerger.plan(snapshot, incoming)
        val outcome = applier.apply(plan, snapshot)

        assertThat(outcome).isInstanceOf(ImportOutcome.Applied::class.java)
        assertThat(fakes.vessels.vessels.value).containsKey(ReportFixtures.VESSEL_ID)
        assertThat(fakes.vessels.decks.value).containsKey(ReportFixtures.DECK_ID)
        assertThat(fakes.vessels.zones.value).containsKey(ReportFixtures.ZONE_ID)
        assertThat(fakes.equipment.equipment.value).hasSize(3)
        assertThat(fakes.inspections.rounds.value).containsKey(ReportFixtures.ROUND_ID)
        assertThat(fakes.inspections.roundItems.value).hasSize(1)
        assertThat(fakes.inspections.deficiencies.value).hasSize(1)
        assertThat(fakes.reference.userNotes.value).hasSize(1)
        assertThat(fakes.equipment.categoryXref.value["equipment-1"]).containsExactly("category-1")
    }

    @Test
    fun `a foreign key that resolves to nothing writes nothing at all`() = runTest(dispatcher) {
        val orphan = TestData.equipment(
            id = "equipment-orphan",
            vesselId = "vessel-that-does-not-exist",
            deckId = null,
            tag = "FE-ZZ-01",
        )
        val incoming = DeckWatchExportPayload(equipment = listOf(orphan))
        val applier = applier()

        val snapshot = applier.snapshot(incoming)
        val plan = ImportMerger.plan(snapshot, incoming)
        val outcome = applier.apply(plan, snapshot)

        assertThat(outcome).isInstanceOf(ImportOutcome.Rejected::class.java)
        assertThat((outcome as ImportOutcome.Rejected).violations.single().field).isEqualTo("vesselId")

        // Phase 1 refuses before any write: every fake is still exactly as it was.
        assertThat(fakes.vessels.vessels.value).isEmpty()
        assertThat(fakes.vessels.decks.value).isEmpty()
        assertThat(fakes.equipment.equipment.value).isEmpty()
        assertThat(fakes.inspections.rounds.value).isEmpty()
        assertThat(fakes.inspections.deficiencies.value).isEmpty()
        assertThat(fakes.reference.userNotes.value).isEmpty()
        assertThat(fakes.maintenance.instances.value).isEmpty()
    }

    @Test
    fun `a propagated deletion soft-deletes the local row rather than resurrecting it`() =
        runTest(dispatcher) {
            val applier = applier()
            // Device already holds the item.
            fakes.vessels.upsertVessel(ReportFixtures.vessel)
            fakes.vessels.upsertDeck(ReportFixtures.deck)
            fakes.equipment.upsertEquipment(ReportFixtures.extinguisher)

            val deletedAt = ReportFixtures.extinguisher.updatedAt + 9_000
            val tombstone = ReportFixtures.extinguisher.copy(deletedAt = deletedAt, updatedAt = deletedAt)
            val incoming = DeckWatchExportPayload(
                vessels = listOf(ReportFixtures.vessel),
                decks = listOf(ReportFixtures.deck),
                equipment = listOf(tombstone),
            )

            val snapshot = applier.snapshot(incoming)
            val plan = ImportMerger.plan(snapshot, incoming)
            val outcome = applier.apply(plan, snapshot)

            assertThat(outcome).isInstanceOf(ImportOutcome.Applied::class.java)
            assertThat((outcome as ImportOutcome.Applied).deletions).isEqualTo(1)
            assertThat(fakes.equipment.getEquipment("equipment-1")?.deletedAt).isEqualTo(deletedAt)
        }

    @Test
    fun `TAKE_THEIRS overwrites the stored record`() = runTest(dispatcher) {
        val applier = applier()
        fakes.vessels.upsertVessel(ReportFixtures.vessel)
        fakes.vessels.upsertDeck(ReportFixtures.deck)
        fakes.equipment.upsertEquipment(ReportFixtures.extinguisher)

        val theirs = ReportFixtures.extinguisher.copy(
            condition = ConditionGrade.OUT_OF_SERVICE,
            updatedAt = ReportFixtures.extinguisher.updatedAt + 1,
        )
        val incoming = DeckWatchExportPayload(
            vessels = listOf(ReportFixtures.vessel),
            decks = listOf(ReportFixtures.deck),
            equipment = listOf(theirs),
        )

        val snapshot = applier.snapshot(incoming)
        val plan = ImportMerger.plan(
            snapshot,
            incoming,
            mapOf(conflictKey(RecordKind.EQUIPMENT, "equipment-1") to ConflictResolution.TAKE_THEIRS),
        )
        applier.apply(plan, snapshot)

        assertThat(fakes.equipment.getEquipment("equipment-1")?.condition)
            .isEqualTo(ConditionGrade.OUT_OF_SERVICE)
    }

    @Test
    fun `KEEP_BOTH leaves the local record untouched and adds the duplicate beside it`() =
        runTest(dispatcher) {
            val applier = applier()
            fakes.vessels.upsertVessel(ReportFixtures.vessel)
            fakes.vessels.upsertDeck(ReportFixtures.deck)
            fakes.equipment.upsertEquipment(ReportFixtures.extinguisher)

            val theirs = ReportFixtures.extinguisher.copy(
                condition = ConditionGrade.DEFECTIVE,
                updatedAt = ReportFixtures.extinguisher.updatedAt + 1,
            )
            val incoming = DeckWatchExportPayload(
                vessels = listOf(ReportFixtures.vessel),
                decks = listOf(ReportFixtures.deck),
                equipment = listOf(theirs),
            )

            val snapshot = applier.snapshot(incoming)
            val plan = ImportMerger.plan(
                snapshot,
                incoming,
                mapOf(conflictKey(RecordKind.EQUIPMENT, "equipment-1") to ConflictResolution.KEEP_BOTH),
            )
            applier.apply(plan, snapshot)

            val stored = fakes.equipment.equipment.value.values
            assertThat(stored).hasSize(2)
            assertThat(fakes.equipment.getEquipment("equipment-1")?.condition).isEqualTo(ConditionGrade.GOOD)
            assertThat(stored.map { it.tag }).contains("FE-UD-01 (imported)")
        }

    @Test
    fun `the snapshot sees soft-deleted local rows so a deletion is not re-applied`() =
        runTest(dispatcher) {
            val applier = applier()
            fakes.vessels.upsertVessel(ReportFixtures.vessel)
            fakes.equipment.upsertEquipment(ReportFixtures.extinguisher)
            fakes.equipment.softDelete("equipment-1", 1_700_000_000_000L)

            val snapshot = applier.snapshot(DeckWatchExportPayload(equipment = listOf(ReportFixtures.extinguisher)))
            assertThat(snapshot.equipment).containsKey("equipment-1")
            assertThat(snapshot.equipment.getValue("equipment-1").deletedAt).isNotNull()
        }

    @Test
    fun `a mid-write failure rolls back what it can and names what it cannot`() = runTest(dispatcher) {
        val exploding = ExplodingInspectionRepository(fakes.inspections)
        val applier = ImportApplier(
            vesselRepository = fakes.vessels,
            equipmentRepository = fakes.equipment,
            maintenanceRepository = fakes.maintenance,
            inspectionRepository = exploding,
            referenceRepository = fakes.reference,
            dispatchers = DefaultDispatcherProvider(main = dispatcher, io = dispatcher, default = dispatcher),
            clock = { TestData.referenceMillis },
        )
        val incoming = ReportFixtures.payload()

        val snapshot = applier.snapshot(incoming)
        val plan = ImportMerger.plan(snapshot, incoming)
        val outcome = applier.apply(plan, snapshot)

        assertThat(outcome).isInstanceOf(ImportOutcome.RolledBack::class.java)
        val rolledBack = outcome as ImportOutcome.RolledBack
        assertThat(rolledBack.message).contains("no room in the log book")

        // Rollback removes what it can: the vessel, decks, zones and categories are gone again,
        // and the created equipment is left as an invisible tombstone.
        assertThat(fakes.vessels.vessels.value).isEmpty()
        assertThat(fakes.vessels.decks.value).isEmpty()
        assertThat(fakes.equipment.equipment.value.values.all { it.deletedAt != null }).isTrue()

        // Task instances cannot be withdrawn through the repository interface, and the outcome
        // says so rather than pretending otherwise.
        assertThat(rolledBack.unrecoverable).isNotEmpty()
        assertThat(rolledBack.unrecoverable.any { it.startsWith("Task instance") }).isTrue()
    }
}
