package com.deckwatch.feature.report

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.testing.SequentialIds
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The merge rules of §13.5. */
class ImportMergerTest {

    private val local = ReportFixtures.localSnapshot()

    // ------------------------------------------------------------------ preview

    @Test
    fun `a record present on only one side is simply new`() {
        val incoming = ReportFixtures.payload()
        val preview = ImportMerger.preview(local, incoming)

        assertThat(preview.newRecords[RecordKind.EQUIPMENT]).isEqualTo(1) // the unplaced one
        assertThat(preview.unchanged[RecordKind.EQUIPMENT]).isEqualTo(2)
        assertThat(preview.conflicts).isEmpty()
    }

    @Test
    fun `re-importing the same file writes nothing`() {
        val incoming = ReportFixtures.payload(equipment = listOf(ReportFixtures.extinguisher, ReportFixtures.lifebuoy))
        val fullLocal = local.copy(
            categories = mapOf("category-1" to TestData.category(id = "category-1", vesselId = ReportFixtures.VESSEL_ID)),
            categoryLinks = setOf(EquipmentCategoryLink("equipment-1", "category-1")),
            taskInstances = incoming.taskInstances.associateBy { it.id },
            rounds = mapOf(ReportFixtures.ROUND_ID to ReportFixtures.round),
            roundItems = mapOf("roundItem-1" to ReportFixtures.roundItem),
            deficiencies = mapOf("deficiency-1" to ReportFixtures.deficiency),
            userNotes = mapOf("note-1" to ReportFixtures.note),
        )
        val preview = ImportMerger.preview(fullLocal, incoming)
        assertThat(preview.conflicts).isEmpty()

        val plan = ImportMerger.plan(fullLocal, incoming)
        assertThat(plan.writeCount).isEqualTo(0)
    }

    @Test
    fun `a differing record is a conflict and suggests the newer side`() {
        val theirs = ReportFixtures.extinguisher.copy(
            condition = ConditionGrade.DEFECTIVE,
            updatedAt = ReportFixtures.extinguisher.updatedAt + 1_000,
        )
        val preview = ImportMerger.preview(local, ReportFixtures.payload(equipment = listOf(theirs)))

        val conflict = preview.conflicts.single { it.kind == RecordKind.EQUIPMENT }
        assertThat(conflict.id).isEqualTo("equipment-1")
        assertThat(conflict.label).contains("FE-UD-01")
        assertThat(conflict.suggested).isEqualTo(ConflictResolution.TAKE_THEIRS)
        assertThat(conflict.incomingIsDeletion).isFalse()
    }

    @Test
    fun `an older incoming record suggests keeping mine`() {
        val theirs = ReportFixtures.extinguisher.copy(
            condition = ConditionGrade.DEFECTIVE,
            updatedAt = ReportFixtures.extinguisher.updatedAt - 1_000,
        )
        val preview = ImportMerger.preview(local, ReportFixtures.payload(equipment = listOf(theirs)))
        assertThat(preview.conflicts.single().suggested).isEqualTo(ConflictResolution.KEEP_MINE)
    }

    @Test
    fun `a record type with no updatedAt cannot claim to know which side is newer`() {
        val theirs = ReportFixtures.zone.copy(name = "Renamed on the other device")
        val preview = ImportMerger.preview(local, ReportFixtures.payload().copy(zones = listOf(theirs)))

        val conflict = preview.conflicts.single { it.kind == RecordKind.ZONE }
        assertThat(conflict.mineUpdatedAt).isNull()
        assertThat(conflict.suggested).isEqualTo(ConflictResolution.KEEP_MINE)
    }

    // ------------------------------------------------------------------ resolutions

    @Test
    fun `KEEP_MINE discards the incoming record entirely`() {
        val theirs = ReportFixtures.extinguisher.copy(
            condition = ConditionGrade.DEFECTIVE,
            updatedAt = ReportFixtures.extinguisher.updatedAt + 1_000,
        )
        val incoming = ReportFixtures.payload(equipment = listOf(theirs))
        val plan = ImportMerger.plan(
            local,
            incoming,
            mapOf(conflictKey(RecordKind.EQUIPMENT, "equipment-1") to ConflictResolution.KEEP_MINE),
        )
        assertThat(plan.equipment.map { it.id }).doesNotContain("equipment-1")
    }

    @Test
    fun `TAKE_THEIRS overwrites the local record as-is`() {
        val theirs = ReportFixtures.extinguisher.copy(
            condition = ConditionGrade.DEFECTIVE,
            updatedAt = ReportFixtures.extinguisher.updatedAt + 1_000,
        )
        val plan = ImportMerger.plan(
            local,
            ReportFixtures.payload(equipment = listOf(theirs)),
            mapOf(conflictKey(RecordKind.EQUIPMENT, "equipment-1") to ConflictResolution.TAKE_THEIRS),
        )
        val written = plan.equipment.single { it.id == "equipment-1" }
        assertThat(written.condition).isEqualTo(ConditionGrade.DEFECTIVE)
        assertThat(written.tag).isEqualTo("FE-UD-01")
    }

    @Test
    fun `KEEP_BOTH duplicates under a fresh id with the imported suffix`() {
        val theirs = ReportFixtures.extinguisher.copy(
            name = "Bridge extinguisher",
            condition = ConditionGrade.DEFECTIVE,
            updatedAt = ReportFixtures.extinguisher.updatedAt + 1_000,
        )
        val plan = ImportMerger.plan(
            local,
            ReportFixtures.payload(equipment = listOf(theirs)),
            mapOf(conflictKey(RecordKind.EQUIPMENT, "equipment-1") to ConflictResolution.KEEP_BOTH),
            newId = SequentialIds("imported"),
        )

        val copy = plan.equipment.single()
        assertThat(copy.id).isEqualTo("imported-1")
        assertThat(copy.tag).isEqualTo("FE-UD-01 (imported)")
        assertThat(copy.name).isEqualTo("Bridge extinguisher (imported)")
        assertThat(plan.idRemap).containsEntry("equipment-1", "imported-1")
    }

    @Test
    fun `KEEP_BOTH re-points every reference in the file at the duplicate`() {
        val theirs = ReportFixtures.extinguisher.copy(
            condition = ConditionGrade.DEFECTIVE,
            updatedAt = ReportFixtures.extinguisher.updatedAt + 1_000,
        )
        val plan = ImportMerger.plan(
            local,
            ReportFixtures.payload(equipment = listOf(theirs)),
            mapOf(conflictKey(RecordKind.EQUIPMENT, "equipment-1") to ConflictResolution.KEEP_BOTH),
            newId = SequentialIds("imported"),
        )

        assertThat(plan.roundItems.single().equipmentId).isEqualTo("imported-1")
        assertThat(plan.deficiencies.single().equipmentId).isEqualTo("imported-1")
        assertThat(plan.taskInstances.single().equipmentId).isEqualTo("imported-1")
        assertThat(plan.categoryLinks.single().equipmentId).isEqualTo("imported-1")
    }

    @Test
    fun `a KEEP_BOTH vessel copy is never made the active vessel`() {
        val theirs = ReportFixtures.vessel.copy(
            name = "MV Other",
            updatedAt = ReportFixtures.vessel.updatedAt + 1,
        )
        val plan = ImportMerger.plan(
            local,
            ReportFixtures.payload().copy(vessels = listOf(theirs)),
            mapOf(conflictKey(RecordKind.VESSEL, ReportFixtures.VESSEL_ID) to ConflictResolution.KEEP_BOTH),
            newId = SequentialIds("v"),
        )
        assertThat(plan.vessels.single().isActive).isFalse()
        assertThat(plan.vessels.single().name).isEqualTo("MV Other (imported)")
    }

    // ------------------------------------------------------------------ deletions

    @Test
    fun `a newer incoming deletion propagates instead of resurrecting the row`() {
        val tombstone = ReportFixtures.extinguisher.copy(
            deletedAt = ReportFixtures.extinguisher.updatedAt + 5_000,
            updatedAt = ReportFixtures.extinguisher.updatedAt + 5_000,
        )
        val incoming = ReportFixtures.payload(equipment = listOf(tombstone))

        val preview = ImportMerger.preview(local, incoming)
        assertThat(preview.propagatedDeletions).isEqualTo(1)
        assertThat(preview.conflicts).isEmpty()

        val plan = ImportMerger.plan(local, incoming)
        assertThat(plan.equipmentDeletions).hasSize(1)
        assertThat(plan.equipmentDeletions.single().equipmentId).isEqualTo("equipment-1")
        assertThat(plan.equipment.map { it.id }).doesNotContain("equipment-1")
    }

    @Test
    fun `a deletion already applied locally is a no-op`() {
        val deletedAt = ReportFixtures.extinguisher.updatedAt + 5_000
        val tombstone = ReportFixtures.extinguisher.copy(deletedAt = deletedAt, updatedAt = deletedAt)
        val localDeleted = ReportFixtures.localSnapshot(listOf(tombstone, ReportFixtures.lifebuoy))

        val preview = ImportMerger.preview(localDeleted, ReportFixtures.payload(equipment = listOf(tombstone)))
        assertThat(preview.propagatedDeletions).isEqualTo(0)
        assertThat(preview.conflicts).isEmpty()
    }

    @Test
    fun `a stale incoming deletion against a locally edited row is a conflict, not a silent delete`() {
        val tombstone = ReportFixtures.extinguisher.copy(
            deletedAt = ReportFixtures.extinguisher.updatedAt - 5_000,
            updatedAt = ReportFixtures.extinguisher.updatedAt - 5_000,
        )
        val preview = ImportMerger.preview(local, ReportFixtures.payload(equipment = listOf(tombstone)))

        val conflict = preview.conflicts.single { it.kind == RecordKind.EQUIPMENT }
        assertThat(conflict.incomingIsDeletion).isTrue()
        assertThat(conflict.suggested).isEqualTo(ConflictResolution.KEEP_MINE)
        assertThat(preview.propagatedDeletions).isEqualTo(0)
    }

    @Test
    fun `an incoming tombstone for a row this device never had is added as a tombstone`() {
        val stranger = TestData.equipment(
            id = "equipment-stranger",
            vesselId = ReportFixtures.VESSEL_ID,
            deckId = ReportFixtures.DECK_ID,
            tag = "FE-XX-99",
            deletedAt = 1_700_000_000_000L,
        )
        val plan = ImportMerger.plan(local, ReportFixtures.payload(equipment = listOf(stranger)))
        val written = plan.equipment.single { it.id == "equipment-stranger" }
        assertThat(written.deletedAt).isNotNull()
    }

    // ------------------------------------------------------------------ validation

    @Test
    fun `a plan whose references all resolve validates clean`() {
        val plan = ImportMerger.plan(local, ReportFixtures.payload())
        assertThat(ImportMerger.validate(plan, local)).isEmpty()
    }

    @Test
    fun `an equipment row pointing at a deck nobody has is rejected`() {
        val orphan = TestData.equipment(
            id = "equipment-orphan",
            vesselId = ReportFixtures.VESSEL_ID,
            deckId = "deck-that-does-not-exist",
            tag = "FE-ZZ-01",
        )
        val incoming = ReportFixtures.payload(equipment = listOf(orphan)).copy(decks = emptyList())
        val plan = ImportMerger.plan(local, incoming)

        val violations = ImportMerger.validate(plan, local)
        assertThat(violations).hasSize(1)
        assertThat(violations.single().field).isEqualTo("deckId")
        assertThat(violations.single().missingId).isEqualTo("deck-that-does-not-exist")
        assertThat(violations.single().toString()).contains("EQUIPMENT")
    }

    @Test
    fun `a round item pointing at a missing round is rejected`() {
        val incoming = ReportFixtures.payload().copy(rounds = emptyList())
        val plan = ImportMerger.plan(local, incoming)
        val violations = ImportMerger.validate(plan, local)
        assertThat(violations.map { it.field }).contains("roundId")
    }

    @Test
    fun `a reference satisfied by a record already in the database validates`() {
        // The deck is not in the file, but it is on this device — that is a valid reference.
        val incoming = ReportFixtures.payload(equipment = listOf(ReportFixtures.unplaced.copy(deckId = ReportFixtures.DECK_ID)))
            .copy(decks = emptyList(), zones = emptyList(), rounds = emptyList(), roundItems = emptyList())
        val plan = ImportMerger.plan(local, incoming)
        assertThat(ImportMerger.validate(plan, local).map { it.field }).doesNotContain("deckId")
    }
}
