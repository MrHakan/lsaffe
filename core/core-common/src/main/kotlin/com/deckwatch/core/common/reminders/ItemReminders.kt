package com.deckwatch.core.common.reminders

/**
 * Arming a local reminder against one piece of equipment — MASTER_PROMPT §11.3.
 *
 * A port, not an implementation: scheduling needs `WorkManager`, which belongs to the app module,
 * while the screen that offers "remind me" belongs to a feature module. Declaring the capability
 * here keeps the feature testable — the fake in `core-testing` records the calls — and keeps the
 * dependency pointing the right way.
 *
 * Nothing in the product path may depend on a reminder actually arriving: notifications can be
 * refused, muted or dropped by the system, and the app stays fully usable either way (C4).
 */
interface ItemReminders {

    /** Arm a reminder [days] from now. Re-arming the same item replaces its pending reminder. */
    fun scheduleIn(equipmentId: String, tag: String, days: Int)

    /** Drop a pending reminder. Cancelling one that was never armed is a no-op. */
    fun cancel(equipmentId: String)
}
