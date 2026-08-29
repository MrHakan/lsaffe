package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes and ratios shared by the symbol tile and the equipment marker. */
object SymbolTileDefaults {
    /** Default tile edge in a list or a form. */
    val Size: Dp = 40.dp

    /** Tile edge inside a picker cell — a full glove-friendly target. */
    val PickerSize: Dp = 48.dp

    /** Fraction of the tile edge occupied by the pictogram. */
    const val PictogramRatio: Float = 0.80f

    /** Corner radius as a fraction of the tile edge. */
    const val CornerRatio: Float = 0.22f

    fun shapeFor(size: Dp): Shape = RoundedCornerShape(size * CornerRatio)
}
