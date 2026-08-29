package com.deckwatch.core.common.due

import com.deckwatch.core.common.Dates
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * The due-date engine — §11.
 *
 * Pure Kotlin with **no Android dependency** so it runs and is unit-tested on the JVM (§11 preamble).
 * The class is stateless: every method is a total function of its arguments, and the only piece of
 * ambient state — "what is today" — is injected as [today] so tests are deterministic.
 *
 * Responsibilities, in the order §11.1 states them:
 * 1. [deriveTaskKeys] — the applicable task set from type + attributes + vessel flag.
 * 2. [computeSchedule] — the next occurrence from the last completion, or from
 *    `installedDate` / `manufactureDate` when there is none.
 * 3. [computeSchedule] also applies the tolerance window and the HSSC anniversary rule.
 * 4. [classify] — `OVERDUE` / `DUE_SOON` / `PENDING`.
 * 5. [summarise] — the soonest result, denormalised onto the equipment row.
 *
 * [computeForEquipment] orchestrates all five for one equipment item.
 *
 * @param today supplies today's epoch-day. Defaults to the system clock; inject a fixed lambda in
 *   tests. Every public method also accepts an explicit `todayEpochDay`, so the engine can be
 *   driven entirely without a clock.
 */
class DueEngine(
    private val today: () -> Long = { Dates.todayEpochDay() },
) {

    /**
     * Derive the applicable task keys for an equipment item — §11.1 (1), §9.3.
     *
     * The result is the union of:
     * * [EquipmentType.taskKeys] — the base set from the bundled catalogue (§9.1); plus
     * * for every [com.deckwatch.core.model.AttributeDefinition] in the type's schema with
     *   `affectsTasks = true`, the entries of `taskKeysByValue[currentValue]` for the value the
     *   equipment currently holds for that attribute.
     *
     * The worked example from §9.3: a `FFE_PORTABLE_EXTINGUISHER` whose `extinguishingMedium` is
     * `CO2` picks up the cylinder-weight-check task, while `DRY_POWDER_ABC` picks up the
     * powder-condition / caking check instead.
     *
     * The vessel [flag] participates through the reserved pseudo-attribute
     * [VESSEL_FLAG_ATTRIBUTE_KEY]: a catalogue attribute definition with that key and
     * `affectsTasks = true` can map `MARSHALL_ISLANDS` / `LIBERIA` / `PANAMA` / `OTHER` to extra
     * task keys, so a flag overlay (§11.5) is data rather than code (§6.6). The vessel record wins
     * over any value stored in [attributesJson] for that key.
     *
     * **Total and deterministic.** Malformed or non-object [attributesJson] yields the base set;
     * iteration order is the catalogue's own order, so the returned set is stable across runs.
     */
    fun deriveTaskKeys(
        type: EquipmentType,
        attributesJson: String,
        flag: FlagState,
    ): Set<String> {
        val keys = LinkedHashSet(type.taskKeys)
        val values = AttributeLookup.parse(attributesJson, flag.name)
        for (attribute in type.attributeSchema) {
            if (!attribute.affectsTasks || attribute.taskKeysByValue.isEmpty()) continue
            val current = values[attribute.key].orEmpty()
            for (value in current) {
                attribute.taskKeysByValue[value]?.let { keys.addAll(it) }
            }
        }
        return keys
    }

    /**
     * Compute the next occurrence of [definition] for one equipment item — §11.1 (2)–(3), §11.4.
     *
     * **Anchor.** `lastCompletedEpochDay ?: installedDate ?: manufactureDate`. The last completion
     * is authoritative; failing that the item is scheduled from the date it went into service, and
     * failing that from the date it was made.
     *
     * **Interval, by [IntervalKind]:**
     * | Kind | Next due |
     * |---|---|
     * | `WEEKLY` | anchor + [DAYS_PER_WEEK] days |
     * | `MONTHLY` | anchor + 1 calendar month |
     * | `QUARTERLY` | anchor + 3 calendar months |
     * | `ANNUAL` | anchor + 12 calendar months |
     * | `BIENNIAL` | anchor + 24 calendar months |
     * | `FIVE_YEARLY` | anchor + 60 calendar months |
     * | `TEN_YEARLY` | anchor + 120 calendar months |
     * | `TWENTY_YEARLY` | anchor + 240 calendar months |
     * | `CUSTOM_MONTHS` | anchor + `definition.intervalMonths` calendar months |
     * | `AT_SURVEY` | the next anniversary of the Safety Equipment Certificate expiry |
     * | `EVENT_DRIVEN` | — no scheduled occurrence |
     *
     * Calendar months go through [Dates.plusMonths], which clamps to the end of the month: an
     * anchor of 31 January plus one month is 28 February (29 February in a leap year). A weekly
     * interval is exactly seven days, never "a week of the month".
     *
     * **`AT_SURVEY`** implements the HSSC rule of §11.1 (3): the due date is the next anniversary
     * of [certExpiryEpochDay] falling on or after [todayEpochDay], and the window is **±3 months
     * ([SURVEY_WINDOW_DAYS] days) regardless of the definition's tolerances** — unless the
     * definition's own tolerances are wider, in which case the wider value is kept. With no
     * certificate expiry on the vessel there is nothing to anchor to and the method returns `null`.
     *
     * **`EVENT_DRIVEN`** never produces a scheduled instance: those tasks (a drill, a discharge, a
     * repair) are created by the event, not by the calendar. Returns `null`.
     *
     * **`CUSTOM_MONTHS` with a null `intervalMonths`** is an unusable definition and returns
     * `null`, whether or not an anchor exists.
     *
     * **No anchor at all.** When the item has no completion, no installed date and no manufacture
     * date, a non-`AT_SURVEY` definition is due **today**. This is deliberate: an item with no
     * history whatsoever is shown as immediately due-soon so the officer establishes the first
     * record rather than the task silently never appearing. It is a baseline, not a claim that the
     * work is late — the tolerance window still applies around it.
     *
     * **Window.** `windowOpens = dueDate - toleranceDaysBefore`,
     * `windowCloses = dueDate + toleranceDaysAfter` (§6.6). Negative tolerances in seed data are
     * clamped to zero so the window can never be inverted.
     *
     * @return the computed window, or `null` when this definition schedules nothing.
     */
    @Suppress("LongParameterList") // The engine is deliberately a pure function of its inputs.
    fun computeSchedule(
        definition: TaskDefinition,
        lastCompletedEpochDay: Long?,
        installedDate: Long?,
        manufactureDate: Long?,
        certExpiryEpochDay: Long?,
        todayEpochDay: Long = today(),
    ): DueComputation? {
        val toleranceBefore = definition.toleranceDaysBefore.coerceAtLeast(0)
        val toleranceAfter = definition.toleranceDaysAfter.coerceAtLeast(0)

        when (definition.intervalKind) {
            IntervalKind.EVENT_DRIVEN -> return null
            IntervalKind.AT_SURVEY -> {
                if (certExpiryEpochDay == null) return null
                val due = Dates.nextAnniversary(certExpiryEpochDay, todayEpochDay)
                return DueComputation(
                    dueDate = due,
                    windowOpens = Dates.plusDays(due, -maxOf(SURVEY_WINDOW_DAYS, toleranceBefore)),
                    windowCloses = Dates.plusDays(due, maxOf(SURVEY_WINDOW_DAYS, toleranceAfter)),
                )
            }
            IntervalKind.CUSTOM_MONTHS -> if (definition.intervalMonths == null) return null
            else -> Unit
        }

        val anchor = lastCompletedEpochDay ?: installedDate ?: manufactureDate
        val due = when {
            anchor == null -> todayEpochDay
            definition.intervalKind == IntervalKind.WEEKLY -> Dates.plusDays(anchor, DAYS_PER_WEEK)
            else -> Dates.plusMonths(anchor, calendarMonths(definition) ?: return null)
        }
        return DueComputation(
            dueDate = due,
            windowOpens = Dates.plusDays(due, -toleranceBefore),
            windowCloses = Dates.plusDays(due, toleranceAfter),
        )
    }

    /**
     * Classify an occurrence — §11.1 (4), §11.4.
     *
     * * [TaskStatus.OVERDUE] — today is **past** `windowCloses`. On `windowCloses` itself the work
     *   is still in time.
     * * [TaskStatus.DUE_SOON] — today is inside the tolerance window (`today >= windowOpens`), or
     *   within the user's lead-time setting of the nominal due date
     *   (`today >= dueDate - leadTimeDays`). The lead time defaults to
     *   [DEFAULT_LEAD_TIME_DAYS] days (§11.1 (4)).
     * * [TaskStatus.PENDING] — anything earlier.
     *
     * The two `DUE_SOON` conditions are an **or**: a definition with a wide `toleranceDaysBefore`
     * (an HSSC ±3-month window) surfaces as soon as the window opens even though that is well
     * outside the lead time, and a definition with no tolerance at all still surfaces
     * `leadTimeDays` ahead of its due date.
     *
     * This method never returns `DONE`, `SKIPPED` or `NOT_APPLICABLE` — those are recorded by the
     * officer, not derived from the calendar.
     */
    fun classify(
        dueComputation: DueComputation,
        todayEpochDay: Long = today(),
        leadTimeDays: Int = DEFAULT_LEAD_TIME_DAYS,
    ): TaskStatus {
        val lead = leadTimeDays.coerceAtLeast(0)
        return when {
            todayEpochDay > dueComputation.windowCloses -> TaskStatus.OVERDUE
            todayEpochDay >= dueComputation.windowOpens -> TaskStatus.DUE_SOON
            todayEpochDay >= Dates.plusDays(dueComputation.dueDate, -lead) -> TaskStatus.DUE_SOON
            else -> TaskStatus.PENDING
        }
    }

    /**
     * Reduce an equipment item's task instances to the soonest open one, for denormalisation onto
     * [Equipment.nextDueDate] / [Equipment.nextDueTaskKey] — §11.1 (5).
     *
     * [TaskStatus.DONE] and [TaskStatus.NOT_APPLICABLE] instances are ignored: the first is
     * history, the second the officer's explicit statement that the task does not apply to this
     * item. Everything else — `PENDING`, `DUE_SOON`, `OVERDUE`, `SKIPPED` — is still work owed and
     * counts.
     *
     * Ties on the due date are broken by task key so the denormalised value is stable across
     * recomputations rather than flipping between two equally-due tasks.
     */
    fun summarise(instances: List<TaskInstance>): DueSummary {
        val soonest = instances
            .filter { it.status !in CLOSED_STATUSES }
            .minWithOrNull(compareBy({ it.dueDate }, { it.taskKey }))
        return DueSummary(soonest?.dueDate, soonest?.taskKey)
    }

    /**
     * The whole of §11.1 for one equipment item: derive, schedule, classify, denormalise.
     *
     * For every key returned by [deriveTaskKeys] that has a definition in [definitions]:
     * * the **anchor** is the greatest `completedDate` among that key's [TaskStatus.DONE]
     *   instances, falling back to the equipment's `installedDate` then `manufactureDate`
     *   (§11.1 (2));
     * * the schedule comes from [computeSchedule] and the status from [classify];
     * * the resulting instance **reuses the id of the existing open occurrence** for that key when
     *   there is one, so recomputation updates the row in place instead of accumulating
     *   duplicates. "Open" means any status other than `DONE` and `NOT_APPLICABLE`. Where several
     *   open occurrences exist for one key — which should not happen, but must not corrupt the
     *   result — the most recently scheduled one is reused.
     * * an occurrence the officer has deferred keeps its [TaskStatus.SKIPPED] status; its dates are
     *   still refreshed so the Due tab shows the current window.
     *
     * A key whose latest state is [TaskStatus.NOT_APPLICABLE] is **suppressed**: nothing is emitted
     * for it and the existing row is left alone. A key with no matching definition is skipped
     * rather than throwing, so a catalogue entry that references a task the seed does not carry
     * degrades to "no task" instead of crashing the recomputation (§11.2 runs this in-transaction
     * on every write).
     *
     * New instances get a **deterministic** id derived from
     * `equipmentId | taskKey | dueDate`, salted only if that id is already taken by a historical
     * row. The same input therefore always produces the same id — recomputing after a lost write,
     * or on a second device during an import merge (§13.5), converges instead of duplicating.
     *
     * Instances for keys that are no longer derived (an attribute changed, so a task no longer
     * applies) are **not** returned and are not modified; pruning them belongs to the repository
     * layer that owns the transaction.
     *
     * @param existingInstances every instance known for this equipment item; rows belonging to
     *   other equipment are ignored, so the caller may pass a whole-vessel list.
     * @param vessel the flag and Safety Equipment Certificate expiry (§11.1 (1), (3)).
     * @param leadTimeDays the user's due lead-time setting (§18), default [DEFAULT_LEAD_TIME_DAYS].
     * @param nowMillis epoch-millis stamp written to `createdAt` / `updatedAt`.
     */
    @Suppress("LongParameterList") // The engine is deliberately a pure function of its inputs.
    fun computeForEquipment(
        equipment: Equipment,
        type: EquipmentType,
        definitions: Map<String, TaskDefinition>,
        existingInstances: List<TaskInstance>,
        vessel: VesselDueContext,
        todayEpochDay: Long = today(),
        leadTimeDays: Int = DEFAULT_LEAD_TIME_DAYS,
        nowMillis: Long = Dates.nowMillis(),
    ): EngineResult {
        val mine = existingInstances.filter { it.equipmentId == equipment.id }
        val byKey = mine.groupBy { it.taskKey }
        val reservedIds = mine.mapTo(LinkedHashSet()) { it.id }

        val upserts = ArrayList<TaskInstance>()
        for (taskKey in deriveTaskKeys(type, equipment.attributesJson, vessel.flag)) {
            val definition = definitions[taskKey] ?: continue
            val existing = byKey[taskKey].orEmpty()
            if (existing.any { it.status == TaskStatus.NOT_APPLICABLE }) continue

            val lastCompleted = existing
                .filter { it.status == TaskStatus.DONE }
                .mapNotNull { it.completedDate }
                .maxOrNull()
            val computation = computeSchedule(
                definition = definition,
                lastCompletedEpochDay = lastCompleted,
                installedDate = equipment.installedDate,
                manufactureDate = equipment.manufactureDate,
                certExpiryEpochDay = vessel.safetyEquipmentCertExpiry,
                todayEpochDay = todayEpochDay,
            ) ?: continue

            val open = existing
                .filter { it.status !in CLOSED_STATUSES }
                .maxWithOrNull(compareBy({ it.dueDate }, { it.id }))
            val status = if (open?.status == TaskStatus.SKIPPED) {
                TaskStatus.SKIPPED
            } else {
                classify(computation, todayEpochDay, leadTimeDays)
            }

            upserts += if (open != null) {
                open.copy(
                    dueDate = computation.dueDate,
                    windowOpens = computation.windowOpens,
                    windowCloses = computation.windowCloses,
                    status = status,
                    updatedAt = nowMillis,
                )
            } else {
                val id = deterministicInstanceId(equipment.id, taskKey, computation.dueDate, reservedIds)
                reservedIds += id
                TaskInstance(
                    id = id,
                    equipmentId = equipment.id,
                    taskKey = taskKey,
                    dueDate = computation.dueDate,
                    windowOpens = computation.windowOpens,
                    windowCloses = computation.windowCloses,
                    status = status,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                )
            }
        }

        val ordered = upserts.sortedWith(compareBy({ it.dueDate }, { it.taskKey }))
        val summary = summarise(ordered)
        return EngineResult(ordered, summary.nextDueDate, summary.nextDueTaskKey)
    }

    /**
     * A UUID derived from the occurrence's identity rather than from randomness, so the same
     * equipment / task / due date always yields the same instance id. Salted only when a
     * historical row already holds that id.
     */
    private fun deterministicInstanceId(
        equipmentId: String,
        taskKey: String,
        dueDate: Long,
        taken: Set<String>,
    ): String {
        var salt = 0
        while (true) {
            val seed = buildString {
                append(equipmentId).append('|').append(taskKey).append('|').append(dueDate)
                if (salt > 0) append('|').append(salt)
            }
            val candidate = UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
            if (candidate !in taken) return candidate
            salt++
        }
    }

    /** Calendar-month length of a definition's interval, or `null` where months do not apply. */
    private fun calendarMonths(definition: TaskDefinition): Int? = when (definition.intervalKind) {
        IntervalKind.MONTHLY -> MONTHS_MONTHLY
        IntervalKind.QUARTERLY -> MONTHS_QUARTERLY
        IntervalKind.ANNUAL -> MONTHS_ANNUAL
        IntervalKind.BIENNIAL -> MONTHS_BIENNIAL
        IntervalKind.FIVE_YEARLY -> MONTHS_FIVE_YEARLY
        IntervalKind.TEN_YEARLY -> MONTHS_TEN_YEARLY
        IntervalKind.TWENTY_YEARLY -> MONTHS_TWENTY_YEARLY
        IntervalKind.CUSTOM_MONTHS -> definition.intervalMonths
        IntervalKind.WEEKLY, IntervalKind.AT_SURVEY, IntervalKind.EVENT_DRIVEN -> null
    }

    companion object {
        /** The user's due lead-time setting, default 30 days — §11.1 (4), §18. */
        const val DEFAULT_LEAD_TIME_DAYS: Int = 30

        /** The HSSC ±3-month survey window, in days — §11.1 (3). */
        const val SURVEY_WINDOW_DAYS: Int = 90

        /** `WEEKLY` is exactly seven days — §11.4. */
        const val DAYS_PER_WEEK: Int = 7

        /**
         * Reserved pseudo-attribute key carrying [FlagState.name], so a flag overlay (§11.5) can be
         * expressed as data in the catalogue's `taskKeysByValue` map rather than as code.
         */
        const val VESSEL_FLAG_ATTRIBUTE_KEY: String = "vesselFlag"

        internal const val MONTHS_MONTHLY: Int = 1
        internal const val MONTHS_QUARTERLY: Int = 3
        internal const val MONTHS_ANNUAL: Int = 12
        internal const val MONTHS_BIENNIAL: Int = 24
        internal const val MONTHS_FIVE_YEARLY: Int = 60
        internal const val MONTHS_TEN_YEARLY: Int = 120
        internal const val MONTHS_TWENTY_YEARLY: Int = 240

        /**
         * Statuses that close an occurrence: history and explicit non-applicability. Everything
         * else is still work owed — §11.1 (5).
         */
        internal val CLOSED_STATUSES: Set<TaskStatus> =
            setOf(TaskStatus.DONE, TaskStatus.NOT_APPLICABLE)
    }
}
