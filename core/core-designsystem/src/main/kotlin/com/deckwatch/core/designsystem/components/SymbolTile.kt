package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.symbols.MediaColor
import com.deckwatch.core.designsystem.symbols.SymbolLibrary
import com.deckwatch.core.designsystem.theme.DeckWatchTheme

/**
 * One symbol on its signage ground — the rounded square used everywhere a
 * symbol appears (§10.4).
 *
 * @param symbolKey a canonical key from `docs/SYMBOL_KEYS.md`; unknown keys
 *   fall back to `APP_GENERIC`.
 * @param mediaColor overrides the standard ground with a fire-control-plan
 *   media colour (§10.3). Only meaningful for [SymbolLibrary.tintableKeys].
 * @param contentDescription TalkBack description; `null` marks the tile
 *   decorative, which is right when an adjacent label already names it.
 */
@Composable
fun SymbolTile(
    symbolKey: String,
    modifier: Modifier = Modifier,
    size: Dp = SymbolTileDefaults.Size,
    mediaColor: Color? = null,
    contentDescription: String? = null,
    shape: Shape = SymbolTileDefaults.shapeFor(size),
) {
    val ground = mediaColor ?: SymbolLibrary.groundColor(symbolKey)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(ground),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SymbolLibrary.get(symbolKey),
            contentDescription = contentDescription,
            tint = SymbolLibrary.pictogramColorOn(ground),
            modifier = Modifier.size(size * SymbolTileDefaults.PictogramRatio),
        )
    }
}

/** Convenience overload taking the media colour as an enum value. */
@Composable
fun SymbolTile(
    symbolKey: String,
    media: MediaColor,
    modifier: Modifier = Modifier,
    size: Dp = SymbolTileDefaults.Size,
    contentDescription: String? = null,
    shape: Shape = SymbolTileDefaults.shapeFor(size),
) {
    SymbolTile(
        symbolKey = symbolKey,
        modifier = modifier,
        size = size,
        mediaColor = media.color,
        contentDescription = contentDescription,
        shape = shape,
    )
}

@Preview
@Composable
private fun SymbolTilePreview() {
    DeckWatchTheme {
        Box {
            SymbolTile(symbolKey = "FES001", size = 56.dp)
        }
    }
}
