package com.deckwatch.feature.equipment

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.TaskStatus

/** One row of the equipment's task list — §7.4 full stage. */
internal data class TaskRowUi(
    val instanceId: String,
    val taskKey: String,
    val titleEn: String,
    val titleTr: String,
    val dueDate: Long,
    val status: TaskStatus,
    val performedBy: PerformedBy,
    val completedDate: Long? = null,
)

/** One box of the compact monthly checklist — §9.3. */
internal data class ChecklistItemUi(
    val key: String,
    val labelEn: String,
    val labelTr: String,
    val checked: Boolean,
)

/**
 * The inline "raise deficiency" form of §7.3 step 4.
 *
 * It is offered, never forced: grading an item `DEFECTIVE` or `OUT_OF_SERVICE` expands this
 * pre-filled form, and dismissing it leaves the grade written and nothing else.
 */
internal data class DeficiencyDraft(
    val equipmentId: String,
    val vesselId: String,
    val title: String,
    val description: String,
    val severity: Severity,
    val raisedBy: String,
    /** Epoch-days — today, per §7.3. */
    val raisedDate: Long,
) {
    companion object {

        /**
         * The severity §7.3 suggests for a grade, or `null` for a grade that raises nothing.
         *
         * `DEFECTIVE` — "not fully serviceable" (§6.9) — is a [Severity.MINOR] finding;
         * `OUT_OF_SERVICE` — "must not be used, landed/condemned" — is [Severity.MAJOR]. Neither is
         * ever escalated to `CRITICAL_DETAINABLE` automatically: whether a finding is detainable is
         * a judgement for the Master and the surveyor, not for a tap on a chip.
         */
        fun suggestedSeverity(grade: ConditionGrade): Severity? = when (grade) {
            ConditionGrade.DEFECTIVE -> Severity.MINOR
            ConditionGrade.OUT_OF_SERVICE -> Severity.MAJOR
            else -> null
        }

        /**
         * Pre-fill from the equipment, the type and today's date — §7.3.
         *
         * The seeded title uses the catalogue's **English** name even in a Turkish UI: deficiency
         * records go to the office, the surveyor and the exported report (§13), where the English
         * term is the one everyone shares. The officer can overwrite it before saving.
         *
         * @return `null` when the grade raises no deficiency.
         */
        fun prefill(
            equipment: Equipment,
            type: EquipmentType?,
            grade: ConditionGrade,
            todayEpochDay: Long,
            raisedBy: String = "",
        ): DeficiencyDraft? {
            val severity = suggestedSeverity(grade) ?: return null
            val typeName = type?.nameEn.orEmpty().ifEmpty { equipment.typeKey }
            return DeficiencyDraft(
                equipmentId = equipment.id,
                vesselId = equipment.vesselId,
                title = "$typeName ${equipment.tag}",
                description = "",
                severity = severity,
                raisedBy = raisedBy,
                raisedDate = todayEpochDay,
            )
        }
    }
}

/**
 * The 10-second undo of a condition write — §7.3 step 3, C10.
 *
 * Held by the view model rather than pushed to a host snackbar so that the affordance is on the
 * sheet the officer is already looking at, and survives the sheet staying open through a sweep.
 */
internal data class ConditionUndo(
    val equipmentId: String,
    val previousGrade: ConditionGrade,
    val previousSetAt: Long?,
    val newGrade: ConditionGrade,
)

/** Transient one-line feedback; the UI turns it into a string resource. */
internal enum class SheetMessage {
    ATTRIBUTES_SAVED,
    DEFICIENCY_SAVED,
    MONTHLY_LOGGED,
    MONTHLY_NO_TASK,
    DUPLICATED,
}
