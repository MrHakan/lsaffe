package com.deckwatch.app.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.datastore.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fires one reminder the officer set against a single item — §11.3.
 *
 * The tag is re-read from the record rather than trusted from the input data, so a reminder set
 * before a re-tag arrives naming the item as it is now. A record deleted in the meantime cancels
 * the reminder by simply not posting it: a notification for equipment that no longer exists would
 * send someone to a locker to look for nothing.
 */
@HiltWorker
class ItemReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: UserPreferencesRepository,
    private val equipment: EquipmentRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!preferences.get().notificationsEnabled) return Result.success()
        val equipmentId = inputData.getString(KEY_EQUIPMENT_ID) ?: return Result.success()
        val record = equipment.getEquipment(equipmentId)?.takeIf { it.deletedAt == null }
            ?: return Result.success()

        Reminders.postItemReminder(
            context = applicationContext,
            notificationId = notificationIdFor(equipmentId),
            tag = record.tag,
            note = null,
        )
        return Result.success()
    }

    companion object {
        const val KEY_EQUIPMENT_ID: String = "equipment_id"
        const val KEY_TAG: String = "tag"

        /**
         * A stable per-item notification id, so a repeated reminder for the same item replaces the
         * previous one. Offset past the digest's id, and masked to stay positive.
         */
        fun notificationIdFor(equipmentId: String): Int =
            (equipmentId.hashCode() and Int.MAX_VALUE) % ID_SPACE + ID_OFFSET

        private const val ID_OFFSET = 1_000
        private const val ID_SPACE = 100_000
    }
}
