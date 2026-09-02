package com.deckwatch.feature.survivalcraft

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.feature.survivalcraft.drill.DrillRecord
import com.deckwatch.feature.survivalcraft.inventory.InventoryExpirySummary
import com.deckwatch.feature.survivalcraft.inventory.InventoryItem
import com.deckwatch.feature.survivalcraft.inventory.InventoryTemplate
import com.deckwatch.feature.survivalcraft.schematic.SchematicDef
import com.deckwatch.feature.survivalcraft.schematic.SchematicHotspot
import com.deckwatch.feature.survivalcraft.schematic.SchematicPanel

/** One hotspot resolved against the parent's children — §7.6. */
internal data class HotspotUi(
    val hotspot: SchematicHotspot,
    /** Null when no child answers to this hotspot: the marker shows its "add" state. */
    val childId: String? = null,
    val childTag: String? = null,
    val condition: ConditionGrade = ConditionGrade.NOT_CHECKED,
    val nextDueDate: Long? = null,
) {
    val isMissing: Boolean get() = childId == null
}

/** A row of the COMPONENTS panel. */
internal data class ComponentRowUi(
    val id: String,
    val tag: String,
    val typeNameEn: String,
    val typeNameTr: String,
    val condition: ConditionGrade,
    val nextDueDate: Long?,
    /** Label of the hotspot this row is bound to, if any. */
    val hotspotLabelEn: String? = null,
    val hotspotLabelTr: String? = null,
)

/** A row of the INVENTORY panel: the stored item plus whether the template says it expires. */
internal data class InventoryRowUi(
    val item: InventoryItem,
    val expires: Boolean,
    /** True for rows the officer added by hand (CO₂ cylinders), which can be removed again. */
    val userAdded: Boolean,
)

/** The interval buckets the TASKS panel groups by — §11.4. */
internal enum class TaskGroup {
    WEEKLY,
    MONTHLY,
    ANNUAL,
    FIVE_YEARLY,
    OTHER,
    ;

    companion object {
        fun of(kind: IntervalKind?): TaskGroup = when (kind) {
            IntervalKind.WEEKLY -> WEEKLY
            IntervalKind.MONTHLY, IntervalKind.QUARTERLY -> MONTHLY
            IntervalKind.ANNUAL, IntervalKind.BIENNIAL -> ANNUAL
            IntervalKind.FIVE_YEARLY, IntervalKind.TEN_YEARLY, IntervalKind.TWENTY_YEARLY -> FIVE_YEARLY
            else -> OTHER
        }
    }
}

/** One task instance as the TASKS panel shows it. */
internal data class TaskRowUi(
    val instanceId: String,
    val taskKey: String,
    val titleEn: String,
    val titleTr: String,
    val dueDate: Long,
    val status: TaskStatus,
    val performedBy: PerformedBy,
    val completedDate: Long?,
    /** True when the task must be signed off by someone other than the ship's staff. */
    val needsProvider: Boolean,
)

internal data class TaskGroupUi(val group: TaskGroup, val rows: List<TaskRowUi>)

/** The "log completion" form — one open at a time. */
internal data class TaskCompletionDraft(
    val instanceId: String,
    val titleEn: String,
    val titleTr: String,
    val completedDate: Long?,
    val completedBy: String = "",
    val serviceProvider: String = "",
    val certificateNumber: String = "",
    val findings: String = "",
    val needsProvider: Boolean = false,
) {
    val isValid: Boolean get() = completedDate != null && completedBy.isNotBlank()
}

/** The "record drill" form. */
internal data class DrillDraft(
    val dateEpochDay: Long?,
    val performedBy: String = "",
    val launched: Boolean = false,
    val remarks: String = "",
) {
    val isValid: Boolean get() = dateEpochDay != null && performedBy.isNotBlank()
}

/** Transient one-line confirmations — DESIGN_OVERHAUL rule 10. */
internal enum class CraftMessage { INVENTORY_SAVED, TASK_LOGGED, DRILL_LOGGED, CHILD_LINKED }

/** Everything the schematic screen renders. */
internal data class SurvivalCraftUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val equipment: Equipment? = null,
    val type: EquipmentType? = null,
    val schematic: SchematicDef? = null,
    val hotspots: List<HotspotUi> = emptyList(),
    val components: List<ComponentRowUi> = emptyList(),
    val inventoryTemplate: InventoryTemplate? = null,
    val inventory: List<InventoryRowUi> = emptyList(),
    val inventorySummary: InventoryExpirySummary = InventoryExpirySummary(),
    val taskGroups: List<TaskGroupUi> = emptyList(),
    val completionDraft: TaskCompletionDraft? = null,
    val drills: List<DrillRecord> = emptyList(),
    val drillDraft: DrillDraft? = null,
    val daysSinceLastLaunch: Long? = null,
    val lastDrillDay: Long? = null,
    val message: CraftMessage? = null,
    val todayEpochDay: Long = 0L,
) {
    val panels: List<SchematicPanel>
        get() = schematic?.panels.orEmpty().ifEmpty { listOf(SchematicPanel.COMPONENTS, SchematicPanel.TASKS) }
}
