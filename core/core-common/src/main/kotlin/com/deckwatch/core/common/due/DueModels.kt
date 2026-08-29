package com.deckwatch.core.common.due

import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.Vessel

/**
 * The scheduling result for one task definition applied to one equipment item — §11.1 (2)–(3).
 *
 * All three values are **epoch-days** (§6: dates are epoch-days so timezone drift never shifts a
 * due date).
 *
 * @property dueDate the nominal next due date.
 * @property windowOpens `dueDate - toleranceDaysBefore`; the earliest date the work may be
 *   credited against this occurrence.
 * @property windowCloses `dueDate + toleranceDaysAfter`; past this date the occurrence is
 *   [com.deckwatch.core.model.TaskStatus.OVERDUE] (§11.4).
 */
data class DueComputation(
    val dueDate: Long,
    val windowOpens: Long,
    val windowCloses: Long,
) {
    /** Convenience for the ±window width in days, used by the Due tab's "before next survey" segment (§12). */
    val windowLengthDays: Long get() = windowCloses - windowOpens
}

/**
 * The soonest open occurrence for an equipment item, denormalised onto
 * [Equipment.nextDueDate] / [Equipment.nextDueTaskKey] so the plan view can colour markers
 * without a join — §11.1 (5).
 *
 * Both properties are `null` when the item has no open occurrence at all.
 */
data class DueSummary(
    val nextDueDate: Long? = null,
    val nextDueTaskKey: String? = null,
)

/**
 * Everything [DueEngine.computeForEquipment] produces for one equipment item — §11.1.
 *
 * @property instancesToUpsert the task instances the caller must write, ordered by due date then
 *   task key. Instances that already existed keep their id, so the write is an update in place
 *   rather than a duplicate row.
 * @property nextDueDate the denormalised soonest due date — §11.1 (5).
 * @property nextDueTaskKey the task key that [nextDueDate] belongs to.
 */
data class EngineResult(
    val instancesToUpsert: List<TaskInstance>,
    val nextDueDate: Long? = null,
    val nextDueTaskKey: String? = null,
) {
    /** The [DueSummary] view of this result. */
    val summary: DueSummary get() = DueSummary(nextDueDate, nextDueTaskKey)

    /**
     * Copy [equipment] with the denormalised due fields applied — §11.1 (5).
     *
     * @param updatedAtMillis epoch-millis stamp for the write; defaults to leaving the existing
     *   stamp alone so a no-op recomputation does not churn `updatedAt`.
     */
    fun applyTo(equipment: Equipment, updatedAtMillis: Long = equipment.updatedAt): Equipment =
        equipment.copy(
            nextDueDate = nextDueDate,
            nextDueTaskKey = nextDueTaskKey,
            updatedAt = updatedAtMillis,
        )
}

/**
 * The vessel-level context the due engine needs — §11.1 (1) and (3).
 *
 * Kept as its own value so the engine never has to load a [Vessel] and stays trivially testable.
 *
 * @property flag selects the flag overlay layer (§11.5) and is exposed to attribute-driven task
 *   derivation as the reserved pseudo-attribute [DueEngine.VESSEL_FLAG_ATTRIBUTE_KEY].
 * @property safetyEquipmentCertExpiry epoch-days; drives the HSSC anniversary rule for
 *   [com.deckwatch.core.model.IntervalKind.AT_SURVEY] definitions (§11.1 (3)).
 */
data class VesselDueContext(
    val flag: FlagState = FlagState.OTHER,
    val safetyEquipmentCertExpiry: Long? = null,
) {
    companion object {
        /** Read the engine's vessel context straight off a [Vessel] record (§6.1). */
        fun from(vessel: Vessel): VesselDueContext =
            VesselDueContext(vessel.flag, vessel.safetyEquipmentCertExpiry)
    }
}
