package com.deckwatch.feature.equipment

import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.common.due.VesselDueContext
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.TaskDefinition

/**
 * One line of the "due dates this will generate" preview — §7.5.4.
 *
 * @property dueDate epoch-days, or `null` for a definition that schedules nothing
 *   (`EVENT_DRIVEN`, or `AT_SURVEY` with no certificate expiry on the vessel).
 */
internal data class DuePreviewRow(
    val taskKey: String,
    val titleEn: String,
    val titleTr: String,
    val intervalKind: IntervalKind,
    val intervalMonths: Int?,
    val dueDate: Long?,
)

/**
 * Live due-date preview for the add form — §7.5.4: *"Each attribute that drives a due date shows
 * the interval it will generate and the resulting due date, live, as the user types."*
 *
 * This is a **preview only**. Persisting the schedule is the repository's job
 * ([com.deckwatch.core.common.repository.MaintenanceRepository.recomputeDue]); nothing here writes.
 * It runs the same [DueEngine] the repository will run, on an item that does not exist yet, so what
 * the officer sees while typing is what the register will hold a second later.
 */
internal object DuePreview {

    /**
     * The task set this type + these attribute values derives, with the due date each will get.
     *
     * The item has no completion history yet, so the engine's anchor is
     * `installedDate ?: manufactureDate`, exactly as it will be after the first save. Rows are
     * ordered by due date, undated definitions last.
     */
    @Suppress("LongParameterList") // A pure preview function of the form's current state.
    fun compute(
        type: EquipmentType,
        definitions: Map<String, TaskDefinition>,
        attributesJson: String,
        installedDate: Long?,
        manufactureDate: Long?,
        vessel: VesselDueContext,
        todayEpochDay: Long,
        engine: DueEngine = DueEngine(),
    ): List<DuePreviewRow> =
        engine.deriveTaskKeys(type, attributesJson, vessel.flag)
            .mapNotNull { key -> definitions[key] }
            .map { definition ->
                val schedule = engine.computeSchedule(
                    definition = definition,
                    lastCompletedEpochDay = null,
                    installedDate = installedDate,
                    manufactureDate = manufactureDate,
                    certExpiryEpochDay = vessel.safetyEquipmentCertExpiry,
                    todayEpochDay = todayEpochDay,
                )
                DuePreviewRow(
                    taskKey = definition.key,
                    titleEn = definition.titleEn,
                    titleTr = definition.titleTr,
                    intervalKind = definition.intervalKind,
                    intervalMonths = definition.intervalMonths,
                    dueDate = schedule?.dueDate,
                )
            }
            .sortedWith(compareBy({ it.dueDate == null }, { it.dueDate }, { it.taskKey }))

    /**
     * The line shown under a single `DATE` attribute: *if this date is the last performance of the
     * task it names, the next one falls due here.*
     *
     * The attribute is matched to a task by [AttributeTaskLink]; when nothing matches uniquely the
     * caller shows no anchor line, because inventing a schedule from a date the data model does not
     * link to a task would be a confidently wrong interval (§8.5).
     */
    @Suppress("LongParameterList") // A pure preview function of one field's current value.
    fun anchorPreview(
        attribute: AttributeDefinition,
        enteredEpochDay: Long?,
        derivedTaskKeys: Collection<String>,
        definitions: Map<String, TaskDefinition>,
        vessel: VesselDueContext,
        todayEpochDay: Long,
        engine: DueEngine = DueEngine(),
    ): DuePreviewRow? {
        if (attribute.kind != AttributeKind.DATE || enteredEpochDay == null) return null
        val taskKey = AttributeTaskLink.resolve(attribute.key, derivedTaskKeys) ?: return null
        val definition = definitions[taskKey] ?: return null
        val schedule = engine.computeSchedule(
            definition = definition,
            lastCompletedEpochDay = enteredEpochDay,
            installedDate = null,
            manufactureDate = null,
            certExpiryEpochDay = vessel.safetyEquipmentCertExpiry,
            todayEpochDay = todayEpochDay,
        ) ?: return null
        return DuePreviewRow(
            taskKey = definition.key,
            titleEn = definition.titleEn,
            titleTr = definition.titleTr,
            intervalKind = definition.intervalKind,
            intervalMonths = definition.intervalMonths,
            dueDate = schedule.dueDate,
        )
    }

    /**
     * The task titles an `affectsTasks` attribute adds at its current value — the second half of
     * §7.5.4: the officer picking `CO2` sees the cylinder-weight check appear as they pick it.
     */
    fun tasksAddedBy(
        type: EquipmentType,
        attribute: AttributeDefinition,
        value: String,
    ): List<String> {
        if (!attribute.affectsTasks || value.isBlank()) return emptyList()
        return attribute.taskKeysByValue[value].orEmpty().filterNot { it in type.taskKeys }
    }
}

/**
 * Links a `DATE` attribute to the task it records the last performance of.
 *
 * The catalogue does not state this relation as data — [AttributeDefinition] carries
 * `taskKeysByValue` for task *derivation*, not task *anchoring* — so it is inferred from the two
 * names, which the seed spells consistently: `lastAnnualServiceDate` against `FE_ANNUAL_SERVICE`,
 * `lastHydrostaticTestDate` against `FE_TEN_YEARLY_HYDROSTATIC`,
 * `lastFiveYearlyOverloadTestDate` against `RG_FIVE_YEARLY_OVERLOAD_TEST`.
 *
 * Both names are reduced to upper-case word tokens — camel case split for the attribute, `_` split
 * for the task key — and scored by how many tokens they share. The **strictly** highest scorer
 * above zero wins; a tie resolves to `null`, so an ambiguous name shows no anchored preview rather
 * than a guessed interval (§8.5: a missing figure is acceptable, a wrong one is not).
 */
internal object AttributeTaskLink {

    /**
     * Tokens that say nothing about which task a date belongs to. `LAST` and `DATE` appear in every
     * attribute name in the seed; the rest are grammatical filler.
     */
    private val NOISE = setOf("LAST", "NEXT", "DATE", "DUE", "ON", "OF", "THE", "AT", "BY", "NO")

    fun resolve(attributeKey: String, taskKeys: Collection<String>): String? {
        val attributeTokens = attributeTokens(attributeKey)
        if (attributeTokens.isEmpty()) return null
        val scored = taskKeys
            .map { key -> key to attributeTokens.intersect(taskTokens(key)).size }
            .filter { (_, score) -> score > 0 }
        val best = scored.maxOfOrNull { it.second } ?: return null
        val winners = scored.filter { it.second == best }
        return winners.singleOrNull()?.first
    }

    /** `lastAnnualServiceDate` -> `[ANNUAL, SERVICE]`. */
    fun attributeTokens(attributeKey: String): Set<String> {
        val words = StringBuilder()
        for (char in attributeKey) {
            if (char.isUpperCase() || !char.isLetterOrDigit()) words.append(' ')
            words.append(char)
        }
        return words.toString()
            .split(' ', '_', '-')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it !in NOISE }
            .toSet()
    }

    /** `FE_ANNUAL_SERVICE` -> `[FE, ANNUAL, SERVICE]`. */
    fun taskTokens(taskKey: String): Set<String> =
        taskKey.split('_', '-')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it !in NOISE }
            .toSet()
}
