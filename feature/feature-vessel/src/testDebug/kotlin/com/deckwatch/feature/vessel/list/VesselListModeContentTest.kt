package com.deckwatch.feature.vessel.list

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tab's own reachability rules, which is where a first-run officer gets stuck if they are
 * wrong: with no vessel there must be a way to create one, and with a vessel the frame the tab
 * host supplies — selector, overflow, equipment FAB — must actually render.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class VesselListModeContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `with no vessel the empty state offers to create one`() {
        var addVesselClicks = 0
        compose.setContent {
            VesselListModeContent(
                state = ListModeUiState(isLoading = false),
                onAddVessel = { addVesselClicks++ },
            )
        }

        compose.onNodeWithText("Add vessel").assertIsDisplayed().performClick()

        assertThat(addVesselClicks).isEqualTo(1)
    }

    @Test
    fun `with no vessel and no host action the screen stays read-only`() {
        compose.setContent {
            VesselListModeContent(state = ListModeUiState(isLoading = false))
        }

        compose.onNodeWithText("Add vessel").assertDoesNotExist()
    }

    @Test
    fun `the host's top-bar actions and floating action button are rendered`() {
        compose.setContent {
            VesselListModeContent(
                state = ListModeUiState(vessel = TestData.vessel(name = "MV Example"), isLoading = false),
                topBarActions = { Text("selector") },
                floatingActionButton = { Text("add equipment") },
            )
        }

        compose.onNodeWithText("MV Example").assertIsDisplayed()
        compose.onNodeWithText("selector").assertIsDisplayed()
        compose.onNodeWithText("add equipment").assertIsDisplayed()
    }

    @Test
    fun `an empty vessel teaches the first deck and offers the presets`() {
        var addDeckClicks = 0
        compose.setContent {
            VesselListModeContent(
                state = ListModeUiState(vessel = TestData.vessel(), isLoading = false),
                onAddDeck = { addDeckClicks++ },
            )
        }

        // The heading and the button carry the same words, so the button is the clickable one.
        compose.onNode(hasText("Add your first deck") and hasClickAction()).performClick()

        assertThat(addDeckClicks).isEqualTo(1)
    }
}

private const val ROBOLECTRIC_SDK = 34
