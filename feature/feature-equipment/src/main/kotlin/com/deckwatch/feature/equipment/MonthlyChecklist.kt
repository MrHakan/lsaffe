package com.deckwatch.feature.equipment

import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.feature.equipment.attributes.AttributeCodec
import com.deckwatch.feature.equipment.attributes.AttributeDraft

/**
 * The monthly inspection checklist — §9.3.
 *
 * Any type may declare a group of `BOOLEAN` attributes with `monthlyChecklist = true`; they render
 * as a compact checklist in the quick-action sheet, and ticking all of them offers a one-tap
 * "log monthly inspection done" that completes the type's monthly task instance.
 *
 * ### Which task a full sweep completes
 *
 * The rule, applied to [EquipmentType.taskKeys] **in catalogue order**:
 * 1. keep the keys containing `MONTHLY` (case-insensitive) — `FE_MONTHLY_INSPECTION`,
 *    `LSA_MONTHLY_CHECKLIST_INSPECTION`, `LB_MONTHLY_MOVE_FROM_STOWED`, …;
 * 2. if any of those also contains `CHECKLIST`, take the first such key. §9.3 defines this control
 *    as *the maintenance checklist inspection*, and the seeded survival-craft types carry both
 *    `LSA_MONTHLY_CHECKLIST_INSPECTION` (a checklist) and `LB_MONTHLY_MOVE_FROM_STOWED` (a physical
 *    operation that a tick-box sweep plainly does not evidence). The checklist task wins;
 * 3. otherwise take the first `MONTHLY` key in catalogue order;
 * 4. if there is none, the action is not offered at all — a type with checklist booleans but no
 *    monthly task still shows the checklist, it simply cannot close a task with it.
 *
 * The key alone is not enough to complete anything: the officer's tick closes a concrete
 * [TaskInstance], which the due engine has already scheduled. [openInstanceFor] picks the one to
 * close — the earliest still-open occurrence of that key, so a sweep credits the oldest outstanding
 * month rather than the next one.
 */
internal object MonthlyChecklist {

    private const val MONTHLY_TOKEN = "MONTHLY"
    private const val CHECKLIST_TOKEN = "CHECKLIST"

    /** Statuses that mean the occurrence is closed and must not be completed again — §11.1 (5). */
    private val CLOSED_STATUSES = setOf(TaskStatus.DONE, TaskStatus.NOT_APPLICABLE)

    /** The `BOOLEAN` attributes that make up the type's monthly checklist, in schema order. */
    fun items(type: EquipmentType): List<AttributeDefinition> =
        type.attributeSchema.filter { it.monthlyChecklist }

    /** The task key a completed sweep should close, per the rule documented on this object. */
    fun taskKeyFor(type: EquipmentType): String? {
        val monthly = type.taskKeys.filter { it.contains(MONTHLY_TOKEN, ignoreCase = true) }
        return monthly.firstOrNull { it.contains(CHECKLIST_TOKEN, ignoreCase = true) }
            ?: monthly.firstOrNull()
    }

    /** The earliest still-open occurrence of [taskKey] among [instances]. */
    fun openInstanceFor(instances: List<TaskInstance>, taskKey: String): TaskInstance? =
        instances
            .filter { it.taskKey == taskKey && it.status !in CLOSED_STATUSES }
            .minWithOrNull(compareBy({ it.dueDate }, { it.id }))

    /** How many of the checklist's boxes are ticked in [draft]. */
    fun tickedCount(items: List<AttributeDefinition>, draft: AttributeDraft): Int =
        items.count { AttributeCodec.isTicked(draft[it.key]) }

    /** True when every box is ticked — the state that offers the one-tap completion. */
    fun allTicked(items: List<AttributeDefinition>, draft: AttributeDraft): Boolean =
        items.isNotEmpty() && tickedCount(items, draft) == items.size
}
