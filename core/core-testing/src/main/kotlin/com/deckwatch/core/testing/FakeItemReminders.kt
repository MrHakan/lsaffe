package com.deckwatch.core.testing

import com.deckwatch.core.common.reminders.ItemReminders

/** One armed reminder, as the fake saw it. */
data class ArmedReminder(val equipmentId: String, val tag: String, val days: Int)

/**
 * Records what a screen asked for instead of touching `WorkManager` — §11.3.
 *
 * Re-arming an item replaces its entry, which is the contract [ItemReminders] states: two
 * reminders for one extinguisher is a mistake, not a feature.
 */
class FakeItemReminders : ItemReminders {

    private val armed = LinkedHashMap<String, ArmedReminder>()

    /** Everything currently armed, in the order it was first armed. */
    val reminders: List<ArmedReminder> get() = armed.values.toList()

    fun reminderFor(equipmentId: String): ArmedReminder? = armed[equipmentId]

    override fun scheduleIn(equipmentId: String, tag: String, days: Int) {
        armed[equipmentId] = ArmedReminder(equipmentId, tag, days)
    }

    override fun cancel(equipmentId: String) {
        armed.remove(equipmentId)
    }
}
