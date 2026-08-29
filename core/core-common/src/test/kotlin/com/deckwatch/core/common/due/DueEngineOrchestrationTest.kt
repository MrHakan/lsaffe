package com.deckwatch.core.common.due

import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.Vessel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** §11.1 end to end — computeForEquipment. */
class DueEngineOrchestrationTest {

    private val today = day(2026, 6, 1)
    private val engine = DueEngine { today }

    private val definitions = mapOf(
        Extinguisher.BASE_MONTHLY to taskDefinition(
            key = Extinguisher.BASE_MONTHLY,
            intervalKind = IntervalKind.MONTHLY,
        ),
        Extinguisher.BASE_ANNUAL to taskDefinition(
            key = Extinguisher.BASE_ANNUAL,
            intervalKind = IntervalKind.ANNUAL,
            toleranceDaysBefore = 30,
            toleranceDaysAfter = 30,
        ),
        Extinguisher.CO2_WEIGHT to taskDefinition(
            key = Extinguisher.CO2_WEIGHT,
            intervalKind = IntervalKind.BIENNIAL,
        ),
        Extinguisher.POWDER_CAKING to taskDefinition(
            key = Extinguisher.POWDER_CAKING,
            intervalKind = IntervalKind.ANNUAL,
        ),
    )

    private val co2 = equipment(
        attributesJson = """{"extinguishingMedium":"CO2"}""",
        installedDate = day(2025, 3, 10),
    )

    private fun run(
        item: Equipment = co2,
        existing: List<TaskInstance> = emptyList(),
        vessel: VesselDueContext = VesselDueContext(FlagState.PANAMA, day(2021, 9, 30)),
        todayEpochDay: Long = today,
        nowMillis: Long = 1_000L,
    ): EngineResult = engine.computeForEquipment(
        equipment = item,
        type = Extinguisher.type,
        definitions = definitions,
        existingInstances = existing,
        vessel = vessel,
        todayEpochDay = todayEpochDay,
        nowMillis = nowMillis,
    )

    // ---------------------------------------------------------------- generation

    @Test
    fun `a CO2 extinguisher gets its base tasks plus the cylinder weight check`() {
        assertThat(run().instancesToUpsert.map { it.taskKey })
            .containsExactly(Extinguisher.BASE_MONTHLY, Extinguisher.BASE_ANNUAL, Extinguisher.CO2_WEIGHT)
    }

    @Test
    fun `instances are anchored on the installed date when there is no completion`() {
        val monthly = run().instancesToUpsert.first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(monthly.dueDate).isEqualTo(day(2025, 4, 10))
        assertThat(monthly.status).isEqualTo(TaskStatus.OVERDUE)
    }

    @Test
    fun `the last DONE completion becomes the anchor`() {
        val existing = listOf(
            taskInstance("old", Extinguisher.BASE_MONTHLY, day(2026, 1, 1), TaskStatus.DONE, completedDate = day(2026, 5, 20)),
            taskInstance("older", Extinguisher.BASE_MONTHLY, day(2025, 12, 1), TaskStatus.DONE, completedDate = day(2026, 2, 2)),
        )
        val monthly = run(existing = existing).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(monthly.dueDate).isEqualTo(day(2026, 6, 20))
        assertThat(monthly.status).isEqualTo(TaskStatus.DUE_SOON)
    }

    @Test
    fun `a DONE instance without a completed date does not anchor`() {
        val existing = listOf(
            taskInstance("d", Extinguisher.BASE_MONTHLY, day(2026, 1, 1), TaskStatus.DONE, completedDate = null),
        )
        val monthly = run(existing = existing).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(monthly.dueDate).isEqualTo(day(2025, 4, 10))
    }

    @Test
    fun `results are ordered by due date then task key`() {
        val upserts = run().instancesToUpsert
        assertThat(upserts.map { it.dueDate }).isInOrder()
    }

    @Test
    fun `instances carry the equipment id and the write stamps`() {
        val upserts = run(nowMillis = 4242L).instancesToUpsert
        assertThat(upserts.map { it.equipmentId }.toSet()).containsExactly("eq-1")
        assertThat(upserts.map { it.createdAt }.toSet()).containsExactly(4242L)
        assertThat(upserts.map { it.updatedAt }.toSet()).containsExactly(4242L)
    }

    @Test
    fun `a task key with no definition is skipped rather than throwing`() {
        val result = engine.computeForEquipment(
            equipment = co2,
            type = Extinguisher.type,
            definitions = emptyMap(),
            existingInstances = emptyList(),
            vessel = VesselDueContext(),
            todayEpochDay = today,
        )
        assertThat(result.instancesToUpsert).isEmpty()
        assertThat(result.nextDueDate).isNull()
    }

    @Test
    fun `EVENT_DRIVEN definitions produce no instance`() {
        val result = engine.computeForEquipment(
            equipment = co2,
            type = Extinguisher.type,
            definitions = definitions + (
                Extinguisher.BASE_MONTHLY to taskDefinition(
                    key = Extinguisher.BASE_MONTHLY,
                    intervalKind = IntervalKind.EVENT_DRIVEN,
                )
                ),
            existingInstances = emptyList(),
            vessel = VesselDueContext(),
            todayEpochDay = today,
        )
        assertThat(result.instancesToUpsert.map { it.taskKey })
            .doesNotContain(Extinguisher.BASE_MONTHLY)
    }

    @Test
    fun `instances belonging to other equipment are ignored`() {
        val foreign = listOf(
            taskInstance("foreign", Extinguisher.BASE_MONTHLY, day(2030, 1, 1), equipmentId = "eq-999"),
        )
        val monthly = run(existing = foreign).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(monthly.id).isNotEqualTo("foreign")
    }

    // ---------------------------------------------------------------- id stability

    @Test
    fun `recomputation reuses the open instance ids`() {
        val first = run()
        val second = run(existing = first.instancesToUpsert)
        assertThat(second.instancesToUpsert.map { it.id })
            .containsExactlyElementsIn(first.instancesToUpsert.map { it.id })
    }

    @Test
    fun `recomputation on a later day still reuses the ids and refreshes the dates`() {
        val first = run()
        val later = run(existing = first.instancesToUpsert, todayEpochDay = day(2027, 1, 1), nowMillis = 9_000L)
        assertThat(later.instancesToUpsert.map { it.id })
            .containsExactlyElementsIn(first.instancesToUpsert.map { it.id })
        assertThat(later.instancesToUpsert.map { it.updatedAt }.toSet()).containsExactly(9_000L)
    }

    @Test
    fun `ids are deterministic across engine instances with no existing rows`() {
        val a = run()
        val b = DueEngine { today }.computeForEquipment(
            equipment = co2,
            type = Extinguisher.type,
            definitions = definitions,
            existingInstances = emptyList(),
            vessel = VesselDueContext(FlagState.PANAMA, day(2021, 9, 30)),
            todayEpochDay = today,
            nowMillis = 1_000L,
        )
        assertThat(b.instancesToUpsert).isEqualTo(a.instancesToUpsert)
    }

    @Test
    fun `a new occurrence never collides with a historical DONE row`() {
        val first = run()
        val monthlyId = first.instancesToUpsert.first { it.taskKey == Extinguisher.BASE_MONTHLY }.id
        val history = listOf(
            taskInstance(monthlyId, Extinguisher.BASE_MONTHLY, day(2025, 4, 10), TaskStatus.DONE, completedDate = null),
        )
        val next = run(existing = history).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(next.dueDate).isEqualTo(day(2025, 4, 10))
        assertThat(next.id).isNotEqualTo(monthlyId)
    }

    @Test
    fun `ids look like UUIDs`() {
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        run().instancesToUpsert.forEach { assertThat(it.id).matches(uuid.pattern) }
    }

    @Test
    fun `the most recently scheduled open occurrence is the one reused`() {
        val existing = listOf(
            taskInstance("early", Extinguisher.BASE_MONTHLY, day(2025, 1, 1)),
            taskInstance("late", Extinguisher.BASE_MONTHLY, day(2025, 12, 1)),
        )
        val monthly = run(existing = existing).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(monthly.id).isEqualTo("late")
    }

    @Test
    fun `two open occurrences due on the same day are broken by id so the choice is stable`() {
        val sameDay = day(2025, 4, 10)
        val existing = listOf(
            taskInstance("aaa", Extinguisher.BASE_MONTHLY, sameDay),
            taskInstance("zzz", Extinguisher.BASE_MONTHLY, sameDay),
        )
        val forwards = run(existing = existing).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        val backwards = run(existing = existing.reversed()).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(forwards.id).isEqualTo("zzz")
        assertThat(backwards.id).isEqualTo("zzz")
    }

    // ---------------------------------------------------------------- recorded statuses

    @Test
    fun `a NOT_APPLICABLE key is suppressed and left untouched`() {
        val existing = listOf(
            taskInstance("na", Extinguisher.CO2_WEIGHT, day(2026, 1, 1), TaskStatus.NOT_APPLICABLE),
        )
        val result = run(existing = existing)
        assertThat(result.instancesToUpsert.map { it.taskKey }).doesNotContain(Extinguisher.CO2_WEIGHT)
        assertThat(result.instancesToUpsert).hasSize(2)
    }

    @Test
    fun `a SKIPPED occurrence keeps its status but has its dates refreshed`() {
        val existing = listOf(
            taskInstance("sk", Extinguisher.BASE_MONTHLY, day(2020, 1, 1), TaskStatus.SKIPPED),
        )
        val monthly = run(existing = existing).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(monthly.id).isEqualTo("sk")
        assertThat(monthly.status).isEqualTo(TaskStatus.SKIPPED)
        assertThat(monthly.dueDate).isEqualTo(day(2025, 4, 10))
    }

    @Test
    fun `an existing open occurrence is reclassified from its refreshed window`() {
        val existing = listOf(
            taskInstance("open", Extinguisher.BASE_MONTHLY, day(2020, 1, 1), TaskStatus.PENDING),
        )
        val monthly = run(existing = existing).instancesToUpsert
            .first { it.taskKey == Extinguisher.BASE_MONTHLY }
        assertThat(monthly.status).isEqualTo(TaskStatus.OVERDUE)
        assertThat(monthly.windowCloses).isEqualTo(day(2025, 4, 10))
    }

    @Test
    fun `completion history is not returned for rewriting`() {
        val existing = listOf(
            taskInstance("done", Extinguisher.BASE_MONTHLY, day(2026, 1, 1), TaskStatus.DONE, completedDate = day(2026, 5, 20)),
        )
        assertThat(run(existing = existing).instancesToUpsert.map { it.id }).doesNotContain("done")
    }

    // ---------------------------------------------------------------- denormalisation

    @Test
    fun `the result denormalises the soonest open occurrence`() {
        val result = run()
        val soonest = result.instancesToUpsert.minByOrNull { it.dueDate }
        assertThat(result.nextDueDate).isEqualTo(soonest?.dueDate)
        assertThat(result.nextDueTaskKey).isEqualTo(soonest?.taskKey)
    }

    @Test
    fun `an item with no derivable tasks denormalises to null`() {
        val result = engine.computeForEquipment(
            equipment = equipment(),
            type = equipmentType(),
            definitions = definitions,
            existingInstances = emptyList(),
            vessel = VesselDueContext(),
            todayEpochDay = today,
        )
        assertThat(result.nextDueDate).isNull()
        assertThat(result.nextDueTaskKey).isNull()
        assertThat(result.instancesToUpsert).isEmpty()
    }

    // ---------------------------------------------------------------- vessel context

    @Test
    fun `AT_SURVEY tasks use the vessel certificate expiry`() {
        val surveyDefinitions = mapOf(
            Extinguisher.BASE_ANNUAL to taskDefinition(
                key = Extinguisher.BASE_ANNUAL,
                intervalKind = IntervalKind.AT_SURVEY,
            ),
        )
        val result = engine.computeForEquipment(
            equipment = co2,
            type = Extinguisher.type,
            definitions = surveyDefinitions,
            existingInstances = emptyList(),
            vessel = VesselDueContext(FlagState.LIBERIA, day(2021, 9, 30)),
            todayEpochDay = today,
        )
        val survey = result.instancesToUpsert.single()
        assertThat(survey.dueDate).isEqualTo(day(2026, 9, 30))
        assertThat(survey.windowOpens).isEqualTo(day(2026, 9, 30) - DueEngine.SURVEY_WINDOW_DAYS)
    }

    @Test
    fun `VesselDueContext reads straight off a vessel record`() {
        val vessel = Vessel(
            id = "v",
            name = "MV Example",
            flag = FlagState.MARSHALL_ISLANDS,
            safetyEquipmentCertExpiry = day(2027, 4, 1),
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertThat(VesselDueContext.from(vessel))
            .isEqualTo(VesselDueContext(FlagState.MARSHALL_ISLANDS, day(2027, 4, 1)))
    }

    @Test
    fun `the default vessel context has no flag preference and no certificate`() {
        assertThat(VesselDueContext()).isEqualTo(VesselDueContext(FlagState.OTHER, null))
    }

    @Test
    fun `computeForEquipment falls back to the injected clock`() {
        val result = engine.computeForEquipment(
            equipment = equipment(attributesJson = "{}"),
            type = Extinguisher.type,
            definitions = definitions,
            existingInstances = emptyList(),
            vessel = VesselDueContext(),
        )
        assertThat(result.nextDueDate).isEqualTo(today)
    }
}
