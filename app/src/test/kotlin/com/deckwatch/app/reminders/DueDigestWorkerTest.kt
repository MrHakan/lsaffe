package com.deckwatch.app.reminders

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real WorkManager chain, so replacing the running digest cannot pass silently. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DueDigestWorkerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val fakes = FakeRepositories()
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var workManager: WorkManager
    private lateinit var factory: WorkerFactory

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(folder.root, "settings.preferences_pb") },
            ),
        )
        factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == DueDigestWorker::class.java.name) {
                DueDigestWorker(appContext, workerParameters, preferences, fakes.vessels, fakes.maintenance)
            } else {
                null
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(factory)
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        Reminders.createChannels(context)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
        scope.cancel()
    }

    @Test
    fun `today's digest succeeds and posts while tomorrow waits in the same chain`() = runBlocking {
        preferences.setNotificationsEnabled(true)
        val vessel = TestData.vessel()
        val item = TestData.equipment(vesselId = vessel.id)
        fakes.seed(vessel = vessel, equipmentItems = listOf(item), recomputeDue = false)
        fakes.maintenance.upsertInstances(
            listOf(TestData.taskInstance(equipmentId = item.id, dueDate = Reminders.todayEpochDay() - 1)),
        )
        preferences.setActiveVesselId(vessel.id)

        val work = runScheduledDigest()

        assertThat(work.map { it.state }).containsExactly(WorkInfo.State.SUCCEEDED, WorkInfo.State.ENQUEUED)
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        assertThat(manager.activeNotifications).hasLength(1)
    }

    @Test
    fun `no active vessel still schedules tomorrow without cancelling today`() = runBlocking {
        preferences.setNotificationsEnabled(true)

        val work = runScheduledDigest()

        assertThat(work.map { it.state }).containsExactly(WorkInfo.State.SUCCEEDED, WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `a disabled digest exits without arming another job`() = runBlocking {
        preferences.setNotificationsEnabled(false)
        val worker = TestListenableWorkerBuilder<DueDigestWorker>(context)
            .setWorkerFactory(factory)
            .build()

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(workManager.getWorkInfosForUniqueWork(DueDigestWorker.WORK_NAME).get()).isEmpty()
    }

    @Test
    fun `changing the reminder time replaces the pending chain`() = runBlocking {
        preferences.setNotificationsEnabled(true)
        runScheduledDigest()

        ReminderScheduler.scheduleDaily(context, 10, 30)

        val work = workManager.getWorkInfosForUniqueWork(DueDigestWorker.WORK_NAME).get()
        assertThat(work.filterNot { it.state.isFinished }).hasSize(1)
        assertThat(work.single { !it.state.isFinished }.state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    private suspend fun runScheduledDigest(): List<WorkInfo> {
        ReminderScheduler.scheduleDaily(context, 8, 0)
        val today = workManager.getWorkInfosForUniqueWork(DueDigestWorker.WORK_NAME).get().single()
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setInitialDelayMet(today.id)
        return withTimeout(10_000L) {
            workManager.getWorkInfosForUniqueWorkFlow(DueDigestWorker.WORK_NAME).first { work ->
                work.any { it.id == today.id && it.state.isFinished }
            }
        }
    }
}
