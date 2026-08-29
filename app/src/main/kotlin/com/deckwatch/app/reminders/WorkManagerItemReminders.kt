package com.deckwatch.app.reminders

import android.content.Context
import com.deckwatch.core.common.reminders.ItemReminders
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** The `WorkManager` side of [ItemReminders] — §11.3. */
@Singleton
class WorkManagerItemReminders @Inject constructor(
    @ApplicationContext private val context: Context,
) : ItemReminders {

    override fun scheduleIn(equipmentId: String, tag: String, days: Int) {
        ReminderScheduler.scheduleItemReminder(
            context = context,
            equipmentId = equipmentId,
            tag = tag,
            delay = Duration.ofDays(days.coerceAtLeast(1).toLong()),
        )
    }

    override fun cancel(equipmentId: String) {
        ReminderScheduler.cancelItemReminder(context, equipmentId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {

    @Binds
    @Singleton
    abstract fun bindItemReminders(impl: WorkManagerItemReminders): ItemReminders
}
