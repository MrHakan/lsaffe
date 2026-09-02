package com.deckwatch.feature.settings.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.repository.DemoVessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where onboarding is — MASTER_PROMPT §14 and §17.6. */
enum class OnboardingStage {
    /** The §17.6 disclaimer. Must be accepted; there is no way past it. */
    DISCLAIMER,

    /** The four teaching pages. Skippable. */
    PAGES,

    /** The final choice: demo vessel or create my own. */
    CHOICE,
}

/** Everything `OnboardingScreen` draws. */
data class OnboardingUiState(
    val stage: OnboardingStage = OnboardingStage.DISCLAIMER,
    val pageIndex: Int = 0,
    val installingDemo: Boolean = false,
    val demoFailed: Boolean = false,
    /** Set once the flow is finished and `onboardingDone` has been written. */
    val finished: Boolean = false,
    /** True when the officer chose "create my vessel" — the host navigates to the vessel editor. */
    val createVessel: Boolean = false,
) {
    val isLastPage: Boolean get() = pageIndex >= ONBOARDING_PAGE_COUNT - 1
}

/** §14: "four screens maximum". */
const val ONBOARDING_PAGE_COUNT: Int = 4

/**
 * The onboarding state machine — §14 (≤4 pages, skippable, demo vessel in one tap) and §17.6 (the
 * disclaimer is shown on first run).
 *
 * ### The order, and why the disclaimer is first
 *
 * `DISCLAIMER → PAGES → CHOICE`. The disclaimer comes **before** anything else and cannot be
 * skipped or dismissed: §17.6 says it is shown on first run, and a user who has been shown four
 * marketing pages first has already formed a view of what the app is. Accepting it writes
 * `disclaimerAccepted`, which is also what the Notes tab's first-entry banner reads, so the officer
 * is never asked twice.
 *
 * Skipping jumps `PAGES → CHOICE`, never past `CHOICE`: §14's demo vessel exists precisely so the
 * app is not empty on first launch, and a user who lands on a blank Vessel tab with no idea what to
 * do is the failure mode the whole flow is here to prevent. The choice screen is therefore two
 * buttons and no skip.
 *
 * ### Where `onboardingDone` is written
 *
 * Only in [finish], and only after the chosen action has succeeded — so an install that fails
 * leaves the flow on screen with an error rather than dropping the officer into an empty app that
 * will never offer to help them again. The flag lives in DataStore, not in memory, which is what
 * makes the gate survive process death (§17.4).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val demoVessel: DemoVessel,
) : ViewModel() {

    private val state = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    /** The §17.6 acceptance. Writes `disclaimerAccepted` and moves to the pages. */
    fun acceptDisclaimer() {
        viewModelScope.launch { preferences.setDisclaimerAccepted(true) }
        state.update { it.copy(stage = OnboardingStage.PAGES, pageIndex = 0) }
    }

    /** Advance one page, or move on to the choice from the last one. */
    fun next() = state.update { current ->
        when {
            current.stage != OnboardingStage.PAGES -> current
            current.isLastPage -> current.copy(stage = OnboardingStage.CHOICE)
            else -> current.copy(pageIndex = current.pageIndex + 1)
        }
    }

    /** Jump to a page — the pager's own swipe. */
    fun goToPage(index: Int) = state.update { current ->
        if (current.stage != OnboardingStage.PAGES) {
            current
        } else {
            current.copy(pageIndex = index.coerceIn(0, ONBOARDING_PAGE_COUNT - 1))
        }
    }

    /** §14's "skippable" — straight to the choice, never past it. */
    fun skip() = state.update { current ->
        if (current.stage == OnboardingStage.PAGES) current.copy(stage = OnboardingStage.CHOICE) else current
    }

    /** Install the demo vessel, then finish. A failure keeps the flow open and says so. */
    fun loadDemoVessel() {
        if (state.value.installingDemo) return
        state.update { it.copy(installingDemo = true, demoFailed = false) }
        viewModelScope.launch {
            val installed = runCatching { demoVessel.install() }.isSuccess
            if (installed) {
                finish()
                state.update { it.copy(installingDemo = false, finished = true) }
            } else {
                state.update { it.copy(installingDemo = false, demoFailed = true) }
            }
        }
    }

    /** Finish with an empty register; the host then opens the vessel editor. */
    fun chooseCreateVessel() {
        viewModelScope.launch {
            finish()
            state.update { it.copy(finished = true, createVessel = true) }
        }
    }

    /** Consumed by the host once it has acted on [OnboardingUiState.finished]. */
    fun onFinishHandled() = state.update { it.copy(finished = false, createVessel = false) }

    fun onDemoFailureHandled() = state.update { it.copy(demoFailed = false) }

    private suspend fun finish() {
        preferences.setOnboardingDone(true)
        // Belt and braces: a user who somehow reached the choice without the disclaimer stage
        // (a saved-state restore across an app update, say) still has it recorded as accepted,
        // because the only route here goes through it.
        preferences.setDisclaimerAccepted(true)
    }
}
