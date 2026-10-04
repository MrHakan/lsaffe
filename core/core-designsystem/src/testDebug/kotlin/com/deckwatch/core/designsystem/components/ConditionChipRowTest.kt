package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.model.ConditionGrade
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConditionChipRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `large labels at phone width occupy separate touchable rows`() {
        var selected: ConditionGrade? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                DeckWatchTheme {
                    Box(Modifier.width(320.dp)) {
                        ConditionChipRow(null, { selected = it })
                    }
                }
            }
        }
        val good = compose.onNodeWithText("Good").fetchSemanticsNode().boundsInRoot
        val acceptable = compose.onNodeWithText("Acceptable").fetchSemanticsNode().boundsInRoot
        assertThat(acceptable.top).isAtLeast(good.bottom)
        assertThat(acceptable.width).isAtLeast(104f)
        compose.onNodeWithText("Acceptable").performClick()
        assertThat(selected).isEqualTo(ConditionGrade.ACCEPTABLE)
    }
}
