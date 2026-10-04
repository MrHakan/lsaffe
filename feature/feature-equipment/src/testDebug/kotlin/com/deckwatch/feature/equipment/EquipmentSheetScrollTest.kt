package com.deckwatch.feature.equipment

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w360dp-h780dp-mdpi")
class EquipmentSheetScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `slow upward drag from peek reveals details and keeps them after release`() {
        openSheet()

        // A slow drag must work without a fling, even when the quick summary barely exceeds
        // half the phone height. This is the gesture that used to snap back in the recording.
        dragUp()

        compose.onNodeWithText("Example Maker").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Example Maker").assertIsDisplayed()
    }

    @Test
    fun `scrolling to lower sections keeps its position and full record remains usable`() {
        var openedId: String? = null
        openSheet { openedId = it }
        dragUp()
        compose.onNodeWithText("Full record").performScrollTo().assertIsDisplayed()

        val scrolled = scrollOffset()
        assertThat(scrolled).isGreaterThan(0f)
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(scrollOffset()).isWithin(1f).of(scrolled)

        compose.onNodeWithText("Full record").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(openedId).isEqualTo("equipment-scroll-test")
    }

    @Test
    fun `lowering and reopening the sheet preserves a scrolled record with double font size`() {
        openSheet(fontScale = 2f)
        dragUp()
        compose.onNodeWithText("Full record").performScrollTo().assertIsDisplayed()
        val scrolled = scrollOffset()
        assertThat(scrolled).isGreaterThan(0f)

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .performSemanticsAction(SemanticsActions.Collapse)
        assertThat(scrollOffset()).isWithin(1f).of(scrolled)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithText("Full record").assertIsDisplayed()
        assertThat(scrollOffset()).isWithin(1f).of(scrolled)
    }

    private fun openSheet(fontScale: Float = 1f, onOpenFullDetail: (String) -> Unit = {}) {
        val fakes = FakeRepositories()
        runBlocking {
            fakes.seed(
                vessel = TestData.vessel(),
                decks = listOf(TestData.deck()),
                types = listOf(TestData.equipmentType()),
                equipmentItems = listOf(
                    TestData.equipment(id = "equipment-scroll-test").copy(
                        notes = (1..40).joinToString("\n") { "Inspection note $it" },
                    ),
                ),
                recomputeDue = false,
            )
        }
        val viewModel = EquipmentSheetViewModel(
            fakes.equipment, fakes.vessels, fakes.reference, fakes.maintenance,
            fakes.inspections, fakes.reminders,
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                DeckWatchTheme {
                    EquipmentSheet(
                        equipmentId = "equipment-scroll-test",
                        onDismiss = {},
                        viewModel = viewModel,
                        onOpenFullDetail = onOpenFullDetail,
                    )
                }
            }
        }
        compose.onNodeWithText("FE-UD-01").assertIsDisplayed()
    }

    private fun scrollOffset(): Float = compose.onNode(hasScrollAction()).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun dragUp() {
        compose.onNode(hasScrollAction()).performTouchInput {
            swipe(
                start = Offset(center.x, 300f.coerceAtMost(height - 24f)),
                end = Offset(center.x, 24f),
                durationMillis = 4_000,
            )
        }
    }
}
