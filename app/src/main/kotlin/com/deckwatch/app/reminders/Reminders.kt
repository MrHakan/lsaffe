package com.deckwatch.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.deckwatch.app.MainActivity
import com.deckwatch.app.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The two local notification channels of §11.3, and posting to them.
 *
 * Everything is local: the app has no network and no push service, so a "notification" here is a
 * `WorkManager` job that reads the same due data the Due tab reads and posts a summary. Posting is
 * always best-effort — the permission can be refused, the channel can be muted, and the app has to
 * stay fully usable either way (C4), so nothing in the product path ever waits on a notification.
 */
object Reminders {

    /** The daily digest of what is overdue or due this week. */
    const val CHANNEL_DUE: String = "due_digest"

    /** One-off reminders the officer sets against a single item. */
    const val CHANNEL_ITEM: String = "item_reminders"

    private const val NOTIFICATION_ID_DIGEST = 1
    private const val REQUEST_CODE_OPEN = 0

    /**
     * Intent extra naming the tab to open — §11.3's "tapping opens the Due tab pre-filtered".
     *
     * Only the digest carries it. A per-item reminder deliberately does not: it names one piece of
     * equipment, and dropping the officer on a cross-vessel work list would be a worse answer than
     * the app's own last screen.
     */
    const val EXTRA_OPEN_TAB: String = "com.deckwatch.extra.OPEN_TAB"
    const val TAB_DUE: String = "due"

    /**
     * Creates the channels. Safe to call repeatedly — Android treats a second create of the same id
     * as a no-op and, importantly, will not override a volume or importance the officer has changed.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DUE,
                context.getString(R.string.notif_channel_due),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_due_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ITEM,
                context.getString(R.string.notif_channel_item),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_item_description) },
        )
    }

    /**
     * True when the app may post. Below Android 13 there is no runtime permission, so the only
     * question is whether the officer has muted the app in system settings.
     */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts the daily digest, replacing yesterday's rather than stacking a new one each morning. */
    fun postDigest(context: Context, digest: DueDigest) {
        if (!digest.worthNotifying || !canPost(context)) return
        val text = when {
            digest.overdue > 0 && digest.thisWeek > 0 ->
                context.getString(R.string.notif_due_body_both, digest.overdue, digest.thisWeek)

            digest.overdue > 0 -> context.resources.getQuantityString(
                R.plurals.notif_due_body_overdue,
                digest.overdue,
                digest.overdue,
            )

            else -> context.resources.getQuantityString(
                R.plurals.notif_due_body_week,
                digest.thisWeek,
                digest.thisWeek,
            )
        }
        post(
            context = context,
            channelId = CHANNEL_DUE,
            notificationId = NOTIFICATION_ID_DIGEST,
            title = context.getString(R.string.notif_due_title),
            text = text,
            openTab = TAB_DUE,
        )
    }

    /** Posts a one-off reminder the officer set against a single item. */
    fun postItemReminder(context: Context, notificationId: Int, tag: String, note: String?) {
        if (!canPost(context)) return
        post(
            context = context,
            channelId = CHANNEL_ITEM,
            notificationId = notificationId,
            title = context.getString(R.string.notif_item_title, tag),
            text = note?.takeIf { it.isNotBlank() } ?: context.getString(R.string.notif_item_body),
        )
    }

    private fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String,
        openTab: String? = null,
    ) {
        createChannels(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { openTab?.let { putExtra(EXTRA_OPEN_TAB, it) } }
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // The permission can be revoked between the check above and this call.
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    /**
     * Delay from [now] until the next occurrence of [hour]:[minute] in the device's own time zone.
     *
     * A ship crosses time zones; anchoring to the device clock means the digest keeps arriving at
     * the hour the officer sees on the bridge, not at a fixed UTC offset. Exactly-now counts as
     * today's slot having passed, so the job never fires twice within a minute of itself.
     */
    fun delayUntilNext(
        hour: Int,
        minute: Int,
        now: LocalDateTime,
    ): Duration {
        val today = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        return Duration.between(now, next)
    }

    /** [delayUntilNext] against the device clock. */
    fun delayUntilNext(hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Duration =
        delayUntilNext(hour, minute, LocalDateTime.now(zone))

    /** Today, in the device's own zone — the epoch day the due engine compares against. */
    fun todayEpochDay(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()
}
