package com.deckwatch.feature.inspection

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The segment boundaries of §12, pinned against a fixed today. */
class DueBucketingTest {

    private val today = TestData.referenceDay
    private val certExpiry = today + 100

    private fun instance(
        dueDate: Long,
        status: TaskStatus = TaskStatus.PENDING,
        windowCloses: Long = dueDate,
    ) = TestData.taskInstance(dueDate = dueDate, windowCloses = windowCloses, status = status)

    @Test
    fun `an engine-classified overdue occurrence is overdue`() {
        val segment = DueBucketing.segmentOf(
            instance(dueDate = today - 40, status = TaskStatus.OVERDUE),
            today,
            certExpiry,
        )
        assertThat(segment).isEqualTo(DueSegment.OVERDUE)
    }

    @Test
    fun `a closed tolerance window is overdue even when the status is stale`() {
        val segment = DueBucketing.segmentOf(
            instance(dueDate = today - 5, status = TaskStatus.DUE_SOON, windowCloses = today - 1),
            today,
            certExpiry,
        )
        assertThat(segment).isEqualTo(DueSegment.OVERDUE)
    }

    @Test
    fun `inside tolerance but past the nominal date reads as this week`() {
        val segment = DueBucketing.segmentOf(
            instance(dueDate = today - 5, status = TaskStatus.DUE_SOON, windowCloses = today + 10),
            today,
            certExpiry,
        )
        assertThat(segment).isEqualTo(DueSegment.THIS_WEEK)
    }

    @Test
    fun `today and the seventh day are this week, the eighth is this month`() {
        assertThat(DueBucketing.segmentOf(instance(today), today, certExpiry))
            .isEqualTo(DueSegment.THIS_WEEK)
        assertThat(DueBucketing.segmentOf(instance(today + 7), today, certExpiry))
            .isEqualTo(DueSegment.THIS_WEEK)
        assertThat(DueBucketing.segmentOf(instance(today + 8), today, certExpiry))
            .isEqualTo(DueSegment.THIS_MONTH)
    }

    @Test
    fun `the thirtieth day is this month, the thirty-first falls before the survey`() {
        assertThat(DueBucketing.segmentOf(instance(today + 30), today, certExpiry))
            .isEqualTo(DueSegment.THIS_MONTH)
        assertThat(DueBucketing.segmentOf(instance(today + 31), today, certExpiry))
            .isEqualTo(DueSegment.BEFORE_SURVEY)
    }

    @Test
    fun `the certificate expiry day is inside the survey window, the day after is planned`() {
        assertThat(DueBucketing.segmentOf(instance(certExpiry), today, certExpiry))
            .isEqualTo(DueSegment.BEFORE_SURVEY)
        assertThat(DueBucketing.segmentOf(instance(certExpiry + 1), today, certExpiry))
            .isEqualTo(DueSegment.PLANNED)
    }

    @Test
    fun `with no certificate expiry everything past a month is planned`() {
        assertThat(DueBucketing.segmentOf(instance(today + 31), today, certExpiry = null))
            .isEqualTo(DueSegment.PLANNED)
    }

    @Test
    fun `a deferred occurrence leaves the urgent segments for planned`() {
        val segment = DueBucketing.segmentOf(
            instance(dueDate = today - 40, status = TaskStatus.SKIPPED, windowCloses = today - 40),
            today,
            certExpiry,
        )
        assertThat(segment).isEqualTo(DueSegment.PLANNED)
    }

    @Test
    fun `the five segments partition the open list`() {
        val instances = listOf(
            instance(today - 40, TaskStatus.OVERDUE),
            instance(today + 2),
            instance(today + 20),
            instance(today + 60),
            instance(today + 400),
            instance(today - 3, TaskStatus.SKIPPED),
        )
        val segments = instances.map { DueBucketing.segmentOf(it, today, certExpiry) }
        assertThat(segments).containsExactly(
            DueSegment.OVERDUE,
            DueSegment.THIS_WEEK,
            DueSegment.THIS_MONTH,
            DueSegment.BEFORE_SURVEY,
            DueSegment.PLANNED,
            DueSegment.PLANNED,
        )
    }

    // ------------------------------------------------------------------ filters

    private fun row(
        id: String,
        deckId: String? = "deck-1",
        zoneId: String? = null,
        group: EquipmentGroup = EquipmentGroup.FFE,
        performedBy: PerformedBy = PerformedBy.SHIP_STAFF,
        condition: ConditionGrade = ConditionGrade.GOOD,
        equipmentId: String = "equipment-$id",
    ) = DueRow(
        instanceId = id,
        equipmentId = equipmentId,
        tag = "FE-UD-$id",
        symbolKey = "FES001",
        deckId = deckId,
        deckShortName = "UD",
        zoneId = zoneId,
        taskKey = "FE_MONTHLY_INSPECTION",
        taskTitle = LocalisedText("Monthly check"),
        equipmentTypeName = LocalisedText("Portable fire extinguisher"),
        dueDate = today,
        dayDelta = 0,
        status = TaskStatus.DUE_SOON,
        performedBy = performedBy,
        condition = condition,
        group = group,
        segment = DueSegment.THIS_WEEK,
    )

    @Test
    fun `an empty filter set passes everything through untouched`() {
        val rows = listOf(row("1"), row("2"))
        assertThat(DueBucketing.applyFilters(rows, DueFilters())).isEqualTo(rows)
    }

    @Test
    fun `filter dimensions combine with AND`() {
        val rows = listOf(
            row("1", group = EquipmentGroup.LSA, performedBy = PerformedBy.SHIP_STAFF),
            row("2", group = EquipmentGroup.LSA, performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER),
            row("3", group = EquipmentGroup.FFE, performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER),
        )
        val filtered = DueBucketing.applyFilters(
            rows,
            DueFilters(
                group = EquipmentGroup.LSA,
                performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER,
            ),
        )
        assertThat(filtered.map { it.instanceId }).containsExactly("2")
    }

    @Test
    fun `deck, zone and condition each narrow the list`() {
        val rows = listOf(
            row("1", deckId = "deck-1", zoneId = "zone-1", condition = ConditionGrade.GOOD),
            row("2", deckId = "deck-2", zoneId = "zone-1", condition = ConditionGrade.GOOD),
            row("3", deckId = "deck-1", zoneId = "zone-2", condition = ConditionGrade.MONITOR),
        )
        assertThat(
            DueBucketing.applyFilters(rows, DueFilters(deckId = "deck-1")).map { it.instanceId },
        ).containsExactly("1", "3")
        assertThat(
            DueBucketing.applyFilters(rows, DueFilters(zoneId = "zone-1")).map { it.instanceId },
        ).containsExactly("1", "2")
        assertThat(
            DueBucketing.applyFilters(rows, DueFilters(condition = ConditionGrade.MONITOR))
                .map { it.instanceId },
        ).containsExactly("3")
    }

    @Test
    fun `the category filter reads the cross-reference and drops unlisted equipment`() {
        val rows = listOf(
            row("1", equipmentId = "equipment-1"),
            row("2", equipmentId = "equipment-2"),
        )
        val filtered = DueBucketing.applyFilters(
            rows,
            DueFilters(categoryId = "category-1"),
            categoryMembership = mapOf("equipment-1" to setOf("category-1")),
        )
        assertThat(filtered.map { it.instanceId }).containsExactly("1")
    }

    // ------------------------------------------------------------------ survey prep

    @Test
    fun `survey prep splits by performer and includes overdue work`() {
        val rows = listOf(
            row("1", performedBy = PerformedBy.SHIP_STAFF).copy(dueDate = today - 10, segment = DueSegment.OVERDUE),
            row("2", performedBy = PerformedBy.SHIP_STAFF_TRAINED).copy(dueDate = today + 20),
            row("3", performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER).copy(dueDate = today + 50),
            row("4", performedBy = PerformedBy.MANUFACTURER).copy(dueDate = today + 60),
            row("5", performedBy = PerformedBy.SHIP_STAFF).copy(dueDate = certExpiry + 1),
        )
        val prep = DueBucketing.surveyPrep(rows, certExpiry, today)

        assertThat(prep.shipStaff.map { it.instanceId }).containsExactly("1", "2").inOrder()
        assertThat(prep.shoreProvider.map { it.instanceId }).containsExactly("3", "4").inOrder()
        assertThat(prep.daysToExpiry).isEqualTo(100)
    }

    @Test
    fun `the shopping list counts distinct shore task titles heaviest first`() {
        val service = row("1", performedBy = PerformedBy.SHORE_FACILITY)
        val rows = listOf(
            service.copy(instanceId = "1", taskKey = "FE_ANNUAL", taskTitle = LocalisedText("Annual service")),
            service.copy(instanceId = "2", taskKey = "FE_ANNUAL", taskTitle = LocalisedText("Annual service")),
            service.copy(instanceId = "3", taskKey = "LB_ANNUAL", taskTitle = LocalisedText("Boat exam")),
        )
        val list = DueBucketing.shoppingList(rows)
        assertThat(list.map { it.taskKey }).containsExactly("FE_ANNUAL", "LB_ANNUAL").inOrder()
        assertThat(list.first().count).isEqualTo(2)
        assertThat(list.last().count).isEqualTo(1)
    }
}
