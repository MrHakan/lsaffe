package com.deckwatch.data.repository.work

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * §11.3: the digest posts on its own channel, and the app must stay fully usable when the
 * notification permission is denied — so a suppressed post is reported, never thrown.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationPosterTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val poster = NotificationPoster(context)
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `posts the digest on its own channel`() {
        val posted = poster.postDueDigest("DeckWatch", "3 overdue, 7 due this week")

        assertThat(posted).isTrue()
        assertThat(shadowOf(manager).allNotifications).hasSize(1)
        assertThat(manager.notificationChannels.map { it.id })
            .contains(NotificationPoster.CHANNEL_ID)
    }

    @Test
    fun `posts nothing and reports it when notifications are switched off`() {
        shadowOf(manager).setNotificationsEnabled(false)

        val posted = poster.postDueDigest("DeckWatch", "3 overdue, 7 due this week")

        assertThat(posted).isFalse()
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }
}
