package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The layout contract of the plate components — §14. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a height passed by the caller wins over the spine's own fill`() {
        // StatusSpine appends fillMaxHeight() to the caller's modifier. Constraints flow outwards
        // in, so the caller's height fixes the bounds and the fill then fills exactly those — the
        // caller's size is honoured. Asserted rather than reasoned about, because modifier order
        // is the kind of thing that is easy to be confidently wrong about.
        compose.setContent {
            DeckWatchTheme {
                Box(modifier = Modifier.size(200.dp)) {
                    StatusSpine(
                        color = ConditionColors.Good,
                        contentDescription = null,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag(SPINE),
                    )
                }
            }
        }

        compose.onNodeWithTag(SPINE).assertHeightIsEqualTo(40.dp)
    }

    @Test
    fun `with no height of its own the spine fills the row it is in`() {
        compose.setContent {
            DeckWatchTheme {
                Box(modifier = Modifier.size(200.dp)) {
                    StatusSpine(
                        color = ConditionColors.Good,
                        contentDescription = null,
                        modifier = Modifier.testTag(SPINE),
                    )
                }
            }
        }

        compose.onNodeWithTag(SPINE).assertHeightIsEqualTo(200.dp)
    }

    @Test
    fun `a long tag stays on one line, so it cannot grow the row`() {
        // Both plates in one composition: a second setContent is not allowed, and comparing them
        // side by side is the actual question anyway — does a long tag make a taller plate?
        compose.setContent {
            DeckWatchTheme {
                Box(modifier = Modifier.size(width = 90.dp, height = 200.dp)) {
                    TagPlate(tag = "FE-01", modifier = Modifier.testTag(REFERENCE))
                    TagPlate(
                        tag = "FE-UD-01-EXTRA-LONG-TAG-THAT-WOULD-WRAP",
                        modifier = Modifier.testTag(TAG),
                    )
                }
            }
        }

        val short = compose.onNodeWithTag(REFERENCE).fetchSemanticsNode().size.height
        val long = compose.onNodeWithTag(TAG).fetchSemanticsNode().size.height
        assertThat(long).isEqualTo(short)
    }

    @Test
    fun `the spine describes its colour only when the row does not`() {
        compose.setContent {
            DeckWatchTheme {
                Box(modifier = Modifier.size(200.dp)) {
                    StatusSpine(
                        color = ConditionColors.OutOfService,
                        contentDescription = "Out of service",
                        modifier = Modifier.testTag(SPINE),
                    )
                }
            }
        }

        // Colour is never the only carrier of meaning: a described spine is readable aloud.
        compose.onNodeWithContentDescription("Out of service", useUnmergedTree = true).assertExists()
    }

    private companion object {
        const val SPINE = "spine"
        const val TAG = "tag"
        const val REFERENCE = "reference"
    }
}
