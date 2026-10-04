package com.deckwatch.feature.deckview.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class DeckBottomControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `primary action is below the compass at narrow width and double font size`() {
        var added = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                DeckWatchTheme {
                    Box(Modifier.width(320.dp)) {
                        DeckBottomControls({ 0f }, {}, {}, { added = true })
                    }
                }
            }
        }
        val compass = compose.onNodeWithContentDescription("Deck heading")
            .fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Add equipment").fetchSemanticsNode().boundsInRoot
        assertThat(button.top).isAtLeast(compass.bottom)
        compose.onNodeWithText("Add equipment").performClick()
        assertThat(added).isTrue()
    }
}
