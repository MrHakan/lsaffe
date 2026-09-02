package com.deckwatch.feature.report

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.UserNote
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.inspection.DueExportLine
import com.deckwatch.feature.inspection.DueExportRequest
import com.deckwatch.feature.inspection.DueSegment

/**
 * A small, complete vessel used by the renderer, merge and CSV tests.
 *
 * Ids are literal so a failure message names the record that failed rather than a UUID, and every
 * timestamp comes off [TestData.referenceMillis] so nothing drifts with the wall clock.
 */
object ReportFixtures {

    const val VESSEL_ID = "vessel-fixture"
    const val DECK_ID = "deck-fixture"
    const val ZONE_ID = "zone-fixture"
    const val ROUND_ID = "round-fixture"

    val vessel: Vessel = TestData.vessel(id = VESSEL_ID, name = "MV Example")

    val deck: Deck = TestData.deck(id = DECK_ID, vesselId = VESSEL_ID, name = "Upper Deck", shortCode = "UD")

    val zone: Zone = TestData.zone(id = ZONE_ID, deckId = DECK_ID)

    val extinguisher: Equipment = TestData.equipment(
        id = "equipment-1",
        vesselId = VESSEL_ID,
        deckId = DECK_ID,
        tag = "FE-UD-01",
        posX = 0.25f,
        posY = 0.30f,
        condition = ConditionGrade.GOOD,
    )

    val lifebuoy: Equipment = TestData.equipment(
        id = "equipment-2",
        vesselId = VESSEL_ID,
        deckId = DECK_ID,
        typeKey = "LSA_LIFEBUOY",
        symbolKey = "LSS001",
        tag = "LB-UD-02",
        posX = 0.70f,
        posY = 0.60f,
        condition = ConditionGrade.MONITOR,
    )

    val unplaced: Equipment = TestData.equipment(
        id = "equipment-3",
        vesselId = VESSEL_ID,
        deckId = null,
        tag = "FE-STORE-09",
        condition = ConditionGrade.NOT_CHECKED,
    )

    val deficiency: Deficiency = TestData.deficiency(
        id = "deficiency-1",
        vesselId = VESSEL_ID,
        equipmentId = "equipment-1",
    )

    val round: Round = TestData.round(
        id = ROUND_ID,
        vesselId = VESSEL_ID,
        itemCount = 2,
        doneCount = 2,
        deficiencyCount = 1,
    )

    val roundItem: RoundItem = TestData.roundItem(
        id = "roundItem-1",
        roundId = ROUND_ID,
        equipmentId = "equipment-1",
        checkedAt = TestData.referenceMillis,
        condition = ConditionGrade.GOOD,
        remark = "Seal intact, gauge in the green.",
    )

    val note: UserNote = TestData.userNote(id = "note-1")

    /** A payload with one of everything, so a renderer test exercises every section. */
    fun payload(
        scope: ExportScope = ExportScope.FULL_BACKUP,
        equipment: List<Equipment> = listOf(extinguisher, lifebuoy, unplaced),
        notes: List<UserNote> = listOf(note),
        dueList: DueExportRequest? = null,
    ): DeckWatchExportPayload = DeckWatchExportPayload(
        appVersion = "1.0.0 (1)",
        generatedAtMillis = TestData.referenceMillis,
        scope = scope.name,
        vessels = listOf(vessel),
        decks = listOf(deck),
        zones = listOf(zone),
        categories = listOf(TestData.category(id = "category-1", vesselId = VESSEL_ID)),
        equipmentCategoryLinks = listOf(EquipmentCategoryLink("equipment-1", "category-1")),
        equipment = equipment,
        taskInstances = listOf(
            TestData.taskInstance(
                id = "instance-1",
                equipmentId = "equipment-1",
                status = TaskStatus.OVERDUE,
            ),
        ),
        rounds = listOf(round),
        roundItems = listOf(roundItem),
        deficiencies = listOf(deficiency),
        userNotes = notes,
        dueList = dueList,
    )

    /** The Due tab's snapshot, for the DUE_LIST scope and the CSV exporter. */
    fun dueRequest(): DueExportRequest = DueExportRequest(
        vesselName = "MV Example",
        vesselImoNumber = "9074729",
        segment = DueSegment.OVERDUE,
        generatedOnEpochDay = TestData.referenceDay,
        lines = listOf(
            DueExportLine(
                tag = "FE-UD-01",
                task = "Monthly inspection",
                dueDate = TestData.referenceDay - 12,
                dayDelta = -12,
                performedBy = PerformedBy.SHIP_STAFF,
                deck = "Upper Deck",
                status = TaskStatus.OVERDUE,
                equipmentId = "equipment-1",
            ),
            DueExportLine(
                tag = "LB-UD-02",
                task = "Annual service, \"thorough\"",
                dueDate = TestData.referenceDay + 30,
                dayDelta = 30,
                performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER,
                deck = "Upper Deck, aft",
                status = TaskStatus.PENDING,
            ),
        ),
    )

    /** The local side of a merge, built from the same records the payload carries. */
    fun localSnapshot(
        equipment: List<Equipment> = listOf(extinguisher, lifebuoy),
    ): LocalSnapshot = LocalSnapshot(
        vessels = mapOf(vessel.id to vessel),
        decks = mapOf(deck.id to deck),
        zones = mapOf(zone.id to zone),
        equipment = equipment.associateBy { it.id },
    )
}
