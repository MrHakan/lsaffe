package com.deckwatch.feature.report

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The payload itself — §13.2's re-importable dataset. */
class ExportPayloadTest {

    @Test
    fun `the payload round-trips through JSON unchanged`() {
        val original = ReportFixtures.payload(dueList = ReportFixtures.dueRequest())
        val decoded = PayloadJson.decodeFromString<DeckWatchExportPayload>(original.toJson())
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `every field is written out, so an exported file reads on its own`() {
        val json = DeckWatchExportPayload().toJson()
        for (field in listOf(
            "schemaVersion", "appVersion", "generatedAtMillis", "scope", "vessels", "decks",
            "zones", "categories", "equipmentCategoryLinks", "equipment", "taskDefinitions",
            "bundledTaskDefinitionKeys", "taskInstances", "rounds", "roundItems", "deficiencies",
            "userNotes", "userDefinedTypes",
        )) {
            assertThat(json).contains("\"$field\"")
        }
    }

    @Test
    fun `an empty payload is small enough to be worth nothing`() {
        assertThat(DeckWatchExportPayload().toJson().length).isLessThan(400)
    }

    @Test
    fun `every scope has its own filename slug`() {
        val slugs = ExportScope.entries.map { it.fileSlug }
        assertThat(slugs).containsNoDuplicates()
        assertThat(slugs.all { it.isNotBlank() }).isTrue()
    }

    @Test
    fun `only the deck sheet needs a deck and only the round report needs a round`() {
        assertThat(ExportScope.entries.filter { it.needsDeck }).containsExactly(ExportScope.DECK_SHEET)
        assertThat(ExportScope.entries.filter { it.needsRound }).containsExactly(ExportScope.ROUND_REPORT)
    }

    @Test
    fun `import counts sum across kinds`() {
        val counts = ImportCounts(mapOf(RecordKind.VESSEL to 1, RecordKind.EQUIPMENT to 12))
        assertThat(counts[RecordKind.VESSEL]).isEqualTo(1)
        assertThat(counts[RecordKind.ROUND]).isEqualTo(0)
        assertThat(counts.total).isEqualTo(13)
    }

    @Test
    fun `the preview offers the suggested resolution for every conflict as its default`() {
        val theirs = ReportFixtures.extinguisher.copy(
            tag = "FE-UD-01A",
            updatedAt = ReportFixtures.extinguisher.updatedAt + 1,
        )
        val preview = ImportMerger.preview(
            ReportFixtures.localSnapshot(),
            ReportFixtures.payload(equipment = listOf(theirs)),
        )
        val defaults = preview.defaultResolutions()
        assertThat(defaults).hasSize(preview.conflicts.size)
        assertThat(defaults[conflictKey(RecordKind.EQUIPMENT, "equipment-1")])
            .isEqualTo(ConflictResolution.TAKE_THEIRS)
    }
}
