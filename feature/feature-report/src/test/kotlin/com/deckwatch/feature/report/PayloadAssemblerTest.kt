package com.deckwatch.feature.report

import com.deckwatch.core.common.DefaultDispatcherProvider
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/** Loading a payload out of the repositories — §13.3. */
@OptIn(ExperimentalCoroutinesApi::class)
class PayloadAssemblerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fakes = FakeRepositories()

    private val assembler = PayloadAssembler(
        vesselRepository = fakes.vessels,
        equipmentRepository = fakes.equipment,
        maintenanceRepository = fakes.maintenance,
        inspectionRepository = fakes.inspections,
        referenceRepository = fakes.reference,
        dispatchers = DefaultDispatcherProvider(main = dispatcher, io = dispatcher, default = dispatcher),
        clock = { TestData.referenceMillis },
        appVersion = { "1.2.3 (45)" },
    )

    @Before
    fun seed() = runTest(dispatcher) {
        fakes.vessels.upsertVessel(ReportFixtures.vessel)
        fakes.vessels.upsertDeck(ReportFixtures.deck)
        fakes.vessels.upsertZone(ReportFixtures.zone)
        fakes.vessels.upsertCategory(TestData.category(id = "category-1", vesselId = ReportFixtures.VESSEL_ID))
        fakes.equipment.upsertEquipment(ReportFixtures.extinguisher)
        fakes.equipment.upsertEquipment(ReportFixtures.lifebuoy)
        fakes.equipment.setCategories("equipment-1", listOf("category-1"))
        fakes.maintenance.upsertInstances(
            listOf(TestData.taskInstance(id = "instance-1", equipmentId = "equipment-1")),
        )
        fakes.inspections.upsertRound(ReportFixtures.round)
        fakes.inspections.upsertRoundItem(ReportFixtures.roundItem)
        fakes.inspections.upsertDeficiency(ReportFixtures.deficiency)
        fakes.reference.upsertUserNote(ReportFixtures.note)
        fakes.reference.upsertUserDefinedType(
            TestData.equipmentType(typeKey = "USER_THING", nameEn = "User thing"),
        )
    }

    @Test
    fun `a full backup carries every table`() = runTest(dispatcher) {
        val assembled = assembler.build(ReportFixtures.VESSEL_ID, ExportScope.FULL_BACKUP, PhotoTier.NONE)
        val payload = assembled.payload

        assertThat(payload.appVersion).isEqualTo("1.2.3 (45)")
        assertThat(payload.schemaVersion).isEqualTo(DeckWatchExportPayload.CURRENT_SCHEMA_VERSION)
        assertThat(payload.vessels.single().id).isEqualTo(ReportFixtures.VESSEL_ID)
        assertThat(payload.decks).hasSize(1)
        assertThat(payload.zones).hasSize(1)
        assertThat(payload.categories.map { it.id }).contains("category-1")
        assertThat(payload.equipment.map { it.id }).containsExactly("equipment-1", "equipment-2")
        assertThat(payload.equipmentCategoryLinks)
            .containsExactly(EquipmentCategoryLink("equipment-1", "category-1"))
        assertThat(payload.taskInstances).hasSize(1)
        assertThat(payload.rounds).hasSize(1)
        assertThat(payload.roundItems).hasSize(1)
        assertThat(payload.deficiencies).hasSize(1)
        assertThat(payload.userNotes).hasSize(1)
        assertThat(payload.userDefinedTypes.map { it.typeKey }).containsExactly("USER_THING")
    }

    @Test
    fun `a soft-deleted item still named by a deficiency travels as a tombstone`() =
        runTest(dispatcher) {
            fakes.equipment.softDelete("equipment-1", 1_700_000_000_000L)

            val payload = assembler
                .build(ReportFixtures.VESSEL_ID, ExportScope.FULL_BACKUP, PhotoTier.NONE)
                .payload

            val tombstone = payload.equipment.single { it.id == "equipment-1" }
            assertThat(tombstone.deletedAt).isEqualTo(1_700_000_000_000L)
        }

    @Test
    fun `a deck sheet narrows to one deck and the items on it`() = runTest(dispatcher) {
        val other = TestData.deck(id = "deck-2", vesselId = ReportFixtures.VESSEL_ID, name = "A Deck", levelIndex = 10)
        fakes.vessels.upsertDeck(other)
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "equipment-9", vesselId = ReportFixtures.VESSEL_ID, deckId = "deck-2", tag = "FE-A-01"),
        )

        val payload = assembler.build(
            vesselId = ReportFixtures.VESSEL_ID,
            scope = ExportScope.DECK_SHEET,
            photoTier = PhotoTier.NONE,
            deckId = ReportFixtures.DECK_ID,
        ).payload

        assertThat(payload.decks.map { it.id }).containsExactly(ReportFixtures.DECK_ID)
        assertThat(payload.equipment.map { it.id }).containsExactly("equipment-1", "equipment-2")
        assertThat(payload.rounds).isEmpty()
    }

    @Test
    fun `a deficiency report narrows to open deficiencies and their equipment`() =
        runTest(dispatcher) {
            fakes.inspections.upsertDeficiency(
                TestData.deficiency(
                    id = "deficiency-closed",
                    vesselId = ReportFixtures.VESSEL_ID,
                    equipmentId = "equipment-2",
                    status = DeficiencyStatus.CLOSED,
                ),
            )

            val payload = assembler
                .build(ReportFixtures.VESSEL_ID, ExportScope.DEFICIENCY_REPORT, PhotoTier.NONE)
                .payload

            assertThat(payload.deficiencies.map { it.id }).containsExactly("deficiency-1")
            assertThat(payload.equipment.map { it.id }).containsExactly("equipment-1")
        }

    @Test
    fun `the photo tier decides which photos are asked for`() = runTest(dispatcher) {
        fakes.equipment.upsertEquipment(
            ReportFixtures.extinguisher.copy(photoUris = listOf("file:///equipment.jpg")),
        )
        fakes.inspections.upsertDeficiency(
            ReportFixtures.deficiency.copy(photoUris = listOf("file:///deficiency.jpg")),
        )

        val none = assembler.build(ReportFixtures.VESSEL_ID, ExportScope.FULL_BACKUP, PhotoTier.NONE)
        val deficiencyOnly =
            assembler.build(ReportFixtures.VESSEL_ID, ExportScope.FULL_BACKUP, PhotoTier.DEFICIENCY_ONLY)
        val all = assembler.build(ReportFixtures.VESSEL_ID, ExportScope.FULL_BACKUP, PhotoTier.ALL)

        assertThat(none.photoUris).isEmpty()
        assertThat(deficiencyOnly.photoUris).containsExactly("file:///deficiency.jpg")
        assertThat(all.photoUris).containsExactly("file:///deficiency.jpg", "file:///equipment.jpg")
    }

    @Test
    fun `bundled task definitions travel as keys, user-defined ones in full`() = runTest(dispatcher) {
        fakes.maintenance.upsertTaskDefinition(TestData.taskDefinition(key = "FE_MONTHLY_INSPECTION"))
        fakes.maintenance.upsertTaskDefinition(
            TestData.taskDefinition(key = "MY_OWN_TASK").copy(isUserDefined = true),
        )

        val payload = assembler
            .build(ReportFixtures.VESSEL_ID, ExportScope.FULL_BACKUP, PhotoTier.NONE)
            .payload

        assertThat(payload.taskDefinitions.map { it.key }).containsExactly("MY_OWN_TASK")
        assertThat(payload.bundledTaskDefinitionKeys).contains("FE_MONTHLY_INSPECTION")
    }

    @Test
    fun `an assembled payload renders and re-imports`() = runTest(dispatcher) {
        val assembled = assembler.build(ReportFixtures.VESSEL_ID, ExportScope.FULL_BACKUP, PhotoTier.NONE)
        val html = HtmlReportRenderer().render(
            ReportDocument(payload = assembled.payload, typeNames = assembled.typeNames),
        )
        val parsed = PayloadParser.parse(html)

        assertThat(parsed).isInstanceOf(PayloadParseResult.Parsed::class.java)
        assertThat((parsed as PayloadParseResult.Parsed).payload).isEqualTo(assembled.payload)
    }
}
