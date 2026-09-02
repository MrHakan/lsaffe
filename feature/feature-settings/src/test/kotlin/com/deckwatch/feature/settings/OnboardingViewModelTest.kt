package com.deckwatch.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.repository.DemoVessel
import com.deckwatch.feature.settings.onboarding.ONBOARDING_PAGE_COUNT
import com.deckwatch.feature.settings.onboarding.OnboardingStage
import com.deckwatch.feature.settings.onboarding.OnboardingViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The §14 / §17.6 onboarding state machine. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: UserPreferencesRepository
    private lateinit var demo: FakeDemoVessel

    @Before
    fun setUp() {
        storeScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        repository = UserPreferencesRepository(dataStore)
        demo = FakeDemoVessel()
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private fun viewModel() = OnboardingViewModel(repository, demo)

    @Test
    fun `starts on the disclaimer`() {
        assertThat(viewModel().uiState.value.stage).isEqualTo(OnboardingStage.DISCLAIMER)
    }

    @Test
    fun `next does nothing until the disclaimer is accepted`() {
        val vm = viewModel()
        vm.next()
        vm.skip()
        assertThat(vm.uiState.value.stage).isEqualTo(OnboardingStage.DISCLAIMER)
    }

    @Test
    fun `accepting the disclaimer records it and opens the pages`() = runTest {
        val vm = viewModel()
        vm.acceptDisclaimer()
        advanceUntilIdle()
        assertThat(vm.uiState.value.stage).isEqualTo(OnboardingStage.PAGES)
        assertThat(vm.uiState.value.pageIndex).isEqualTo(0)
        assertThat(repository.get().disclaimerAccepted).isTrue()
    }

    @Test
    fun `next walks the pages and then reaches the choice`() {
        val vm = viewModel()
        vm.acceptDisclaimer()
        repeat(ONBOARDING_PAGE_COUNT - 1) { vm.next() }
        assertThat(vm.uiState.value.pageIndex).isEqualTo(ONBOARDING_PAGE_COUNT - 1)
        assertThat(vm.uiState.value.stage).isEqualTo(OnboardingStage.PAGES)

        vm.next()
        assertThat(vm.uiState.value.stage).isEqualTo(OnboardingStage.CHOICE)
    }

    @Test
    fun `skip jumps to the choice but never past it`() {
        val vm = viewModel()
        vm.acceptDisclaimer()
        vm.skip()
        assertThat(vm.uiState.value.stage).isEqualTo(OnboardingStage.CHOICE)
        vm.skip()
        vm.next()
        assertThat(vm.uiState.value.stage).isEqualTo(OnboardingStage.CHOICE)
    }

    @Test
    fun `page index is clamped`() {
        val vm = viewModel()
        vm.acceptDisclaimer()
        vm.goToPage(99)
        assertThat(vm.uiState.value.pageIndex).isEqualTo(ONBOARDING_PAGE_COUNT - 1)
        vm.goToPage(-4)
        assertThat(vm.uiState.value.pageIndex).isEqualTo(0)
    }

    @Test
    fun `loading the demo installs it and finishes onboarding`() = runTest {
        val vm = viewModel()
        vm.acceptDisclaimer()
        vm.skip()
        vm.loadDemoVessel()
        advanceUntilIdle()

        assertThat(demo.installs).isEqualTo(1)
        assertThat(vm.uiState.value.finished).isTrue()
        assertThat(vm.uiState.value.createVessel).isFalse()
        assertThat(repository.get().onboardingDone).isTrue()
    }

    @Test
    fun `a failed demo install keeps onboarding open`() = runTest {
        demo.failInstall = true
        val vm = viewModel()
        vm.acceptDisclaimer()
        vm.skip()
        vm.loadDemoVessel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.demoFailed).isTrue()
        assertThat(vm.uiState.value.finished).isFalse()
        // The gate must stay shut: dropping the officer into an empty app that will never offer
        // help again is the one outcome this flow exists to prevent.
        assertThat(repository.get().onboardingDone).isFalse()
    }

    @Test
    fun `choosing create my vessel finishes and asks for the editor`() = runTest {
        val vm = viewModel()
        vm.acceptDisclaimer()
        vm.skip()
        vm.chooseCreateVessel()
        advanceUntilIdle()

        assertThat(demo.installs).isEqualTo(0)
        assertThat(vm.uiState.value.finished).isTrue()
        assertThat(vm.uiState.value.createVessel).isTrue()
        assertThat(repository.get().onboardingDone).isTrue()
    }

    @Test
    fun `a second load while one is running is ignored`() = runTest {
        val vm = viewModel()
        vm.acceptDisclaimer()
        vm.skip()
        vm.loadDemoVessel()
        vm.loadDemoVessel()
        advanceUntilIdle()
        assertThat(demo.installs).isEqualTo(1)
    }

    @Test
    fun `the host consuming the finish clears the flags`() = runTest {
        val vm = viewModel()
        vm.acceptDisclaimer()
        vm.skip()
        vm.chooseCreateVessel()
        advanceUntilIdle()
        vm.onFinishHandled()
        assertThat(vm.uiState.value.finished).isFalse()
        assertThat(vm.uiState.value.createVessel).isFalse()
    }

    private class FakeDemoVessel : DemoVessel {
        var installs = 0
        var failInstall = false
        var installed = false

        override suspend fun install(): String {
            if (failInstall) error("no assets")
            installs++
            installed = true
            return "demo-vessel"
        }

        override suspend fun uninstall() {
            installed = false
        }

        override suspend fun isInstalled(): Boolean = installed

        override suspend fun demoVesselId(): String = "demo-vessel"
    }
}
