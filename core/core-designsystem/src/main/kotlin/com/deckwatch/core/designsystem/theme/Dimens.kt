package com.deckwatch.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deckwatch.core.model.ListDensity

/** Glove-friendly touch targets — C6 — and the metrics of the plate language of §14. */
object Dimens {
    val TouchTargetMin = 48.dp
    val TouchTargetPrimary = 56.dp
    val ListRowCompact = 56.dp
    val ListRowComfortable = 72.dp
    val SpacingXs = 4.dp
    val SpacingS = 8.dp
    val SpacingM = 12.dp
    val SpacingL = 16.dp
    val SpacingXl = 24.dp
    val CardCorner = 12.dp
    val ChipCorner = 8.dp

    /**
     * Width of the status spine. Four is the narrowest bar that still reads as a colour rather
     * than as a line at arm's length, and it costs the row almost nothing.
     */
    val SpineWidth = 4.dp

    /** A tag plate's corner: tighter than a card, because a plate is a stamped thing. */
    val PlateCorner = 3.dp

    /** One device pixel at any density is too thin to see; one dp is the app's rule weight. */
    val Hairline = 1.dp

    /** Row height for the officer's density preference — §18. */
    fun rowHeight(density: ListDensity): Dp = when (density) {
        ListDensity.COMPACT -> ListRowCompact
        ListDensity.COMFORTABLE -> ListRowComfortable
    }
}

/**
 * The list density in force, from the officer's §18 preference.
 *
 * A composition local rather than a parameter threaded through every list: density is a property
 * of the whole app, and passing it down would put it in the signature of composables that have no
 * other reason to know about settings.
 */
val LocalListDensity = staticCompositionLocalOf { ListDensity.COMPACT }
