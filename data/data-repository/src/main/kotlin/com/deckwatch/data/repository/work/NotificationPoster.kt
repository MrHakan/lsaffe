package com.deckwatch.data.repository.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the daily digest notification — MASTER_PROMPT §11.3.
 *
 * **The app stays fully usable with notifications denied.** Every path here is guarded: the channel
 * is created defensively, `areNotificationsEnabled()` is checked before posting, and the `notify`
 * call itself is wrapped because on API 33+ a missing `POST_NOTIFICATIONS` grant throws
 * `SecurityException` from the platform rather than returning a value. Nothing propagates — the
 * worker that calls this must still report success, because the recomputation it just did was the
 * important half of its job.
 *
 * The tap target is resolved through the package manager's launch intent rather than a hard
 * reference to `MainActivity`, so this module does not have to depend on `app`. The extra tells the
 * app which tab to open — §11.3's "tapping opens the Due tab pre-filtered".
 */
@Singleton
class NotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Post the digest. Returns true when the notification was handed to the system, false when it
     * was suppressed — notifications off, permission missing, or no launcher activity to open.
     */
    fun postDueDigest(title: String, body: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { contentIntent()?.let(::setContentIntent) }
            .build()
        return try {
            manager.notify(DIGEST_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check and the post. Nothing to do.
            false
        }
    }

    /** Creating a channel twice is a no-op, so this is safe to call on every post. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = CHANNEL_DESCRIPTION }
        manager.createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launch.putExtra(EXTRA_OPEN_TAB, TAB_DUE)
        return PendingIntent.getActivity(
            context,
            DIGEST_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Channel id for the daily due digest. Stable: renaming it would orphan user settings. */
        const val CHANNEL_ID: String = "deckwatch_due_digest"

        /**
         * Channel name and description are shown in Android's own settings UI. They are deliberately
         * left in English here; the app may re-create the channel with localised text at startup —
         * the id is what matters and it never changes.
         */
        const val CHANNEL_NAME: String = "Due reminders"
        const val CHANNEL_DESCRIPTION: String = "Daily summary of overdue and upcoming LSA/FFE work"

        const val DIGEST_NOTIFICATION_ID: Int = 1001

        /** Intent extra naming the tab to open — the Due tab (§5, tab 3). */
        const val EXTRA_OPEN_TAB: String = "com.deckwatch.extra.OPEN_TAB"
        const val TAB_DUE: String = "due"

        private const val DIGEST_REQUEST_CODE = 1001
    }
}
