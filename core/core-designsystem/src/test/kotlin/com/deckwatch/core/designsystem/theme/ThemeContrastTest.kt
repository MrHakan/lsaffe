package com.deckwatch.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Test

/**
 * The three schemes of §14 have jobs, and each job is a number.
 *
 * A theme is normally judged by eye, but two of these have a hard requirement — sunlight on deck
 * and night vision on the bridge — so they are checked rather than admired.
 */
class ThemeContrastTest {

    @Test
    fun `body text clears the WCAG AA contrast bar on every theme`() {
        for (mode in ThemeMode.entries) {
            val scheme = colorSchemeFor(mode)
            assertThat(contrast(scheme.onSurface, scheme.surface)).isAtLeast(AA_BODY)
            assertThat(contrast(scheme.onBackground, scheme.background)).isAtLeast(AA_BODY)
            assertThat(contrast(scheme.onSurfaceVariant, scheme.surfaceVariant)).isAtLeast(AA_BODY)
        }
    }

    @Test
    fun `the day theme is a light ground, for sunlight on deck`() {
        val day = colorSchemeFor(ThemeMode.DAY)

        assertThat(day.background.luminance()).isGreaterThan(LIGHT_GROUND)
        // Sunlight needs more than the AA minimum: the day theme is the one read through a visor.
        assertThat(contrast(day.onSurface, day.surface)).isAtLeast(SUNLIGHT_CONTRAST)
    }

    @Test
    fun `the bridge theme is red only, on a near-black ground`() {
        val bridge = colorSchemeFor(ThemeMode.BRIDGE)
        val surfaces = listOf(bridge.background, bridge.surface, bridge.surfaceVariant)
        val inks = listOf(bridge.onBackground, bridge.onSurface, bridge.onSurfaceVariant, bridge.primary)

        // The ground stays black: a lit panel on a darkened bridge is the thing that ruins a watch.
        surfaces.forEach { assertThat(it.luminance()).isLessThan(BRIDGE_MAX_GROUND) }

        // What protects dark adaptation is the spectrum, not the dimness. Green and blue are held
        // well below red on every ink, which is why these can be bright enough to read.
        inks.forEach { ink ->
            assertThat(ink.green).isLessThan(ink.red * BRIDGE_MAX_SHORT_WAVELENGTH)
            assertThat(ink.blue).isLessThan(ink.red * BRIDGE_MAX_SHORT_WAVELENGTH)
        }
    }

    @Test
    fun `condition colours are the same in every theme, so a grade never changes meaning`() {
        // §14: the semantic colours are fixed. A grade that looked amber on deck and red on the
        // bridge would be a different grade to the person reading it.
        val grades = listOf(
            ConditionColors.Good,
            ConditionColors.Acceptable,
            ConditionColors.Monitor,
            ConditionColors.Defective,
            ConditionColors.OutOfService,
            ConditionColors.NotChecked,
        )

        assertThat(grades.distinct()).hasSize(grades.size)
        // They are distinguishable from each other by more than hue alone: adjacent grades differ
        // in luminance too, which is what makes them readable to a colour-blind reader.
        val ordered = listOf(ConditionColors.Good, ConditionColors.Monitor, ConditionColors.OutOfService)
        ordered.zipWithNext { a, b ->
            assertThat(abs(a.luminance() - b.luminance())).isGreaterThan(MIN_LUMINANCE_STEP)
        }
    }

    @Test
    fun `row height follows the density preference`() {
        assertThat(Dimens.rowHeight(ListDensity.COMPACT)).isEqualTo(Dimens.ListRowCompact)
        assertThat(Dimens.rowHeight(ListDensity.COMFORTABLE)).isEqualTo(Dimens.ListRowComfortable)
        assertThat(Dimens.ListRowComfortable).isGreaterThan(Dimens.ListRowCompact)
    }

    @Test
    fun `every scheme defines the roles the app actually paints with`() {
        // A scheme with a role left at the Material default is the way an unstyled surface sneaks
        // into a theme that has to be exactly one of three things.
        for (mode in ThemeMode.entries) {
            val scheme: ColorScheme = colorSchemeFor(mode)
            listOf(
                scheme.surface,
                scheme.surfaceVariant,
                scheme.background,
                scheme.outline,
                scheme.outlineVariant,
                scheme.primary,
            ).forEach { assertThat(it.alpha).isEqualTo(1f) }
        }
    }

    /** WCAG relative-luminance contrast ratio between two opaque colours. */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + CONTRAST_OFFSET
        val lb = b.luminance() + CONTRAST_OFFSET
        return max(la, lb) / min(la, lb)
    }

    private companion object {
        const val AA_BODY = 4.5f
        const val SUNLIGHT_CONTRAST = 12f
        const val LIGHT_GROUND = 0.7f
        const val BRIDGE_MAX_GROUND = 0.02f

        /** Green and blue may not exceed this fraction of red on any bridge ink. */
        const val BRIDGE_MAX_SHORT_WAVELENGTH = 0.35f
        const val MIN_LUMINANCE_STEP = 0.02f
        const val CONTRAST_OFFSET = 0.05f
    }
}
