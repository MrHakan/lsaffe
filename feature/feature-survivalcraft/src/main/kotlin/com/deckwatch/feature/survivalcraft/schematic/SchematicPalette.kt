package com.deckwatch.feature.survivalcraft.schematic

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Resolved theme colours the renderer paints with. */
internal data class SchematicPalette(
    val outline: Color,
    val structure: Color,
    val detail: Color,
    val line: Color,
    val body: Color,
    val panel: Color,
    val accent: Color,
)

@Composable
internal fun rememberSchematicPalette(): SchematicPalette {
    val scheme = MaterialTheme.colorScheme
    return SchematicPalette(
        outline = scheme.onSurface,
        structure = scheme.onSurfaceVariant,
        detail = scheme.onSurfaceVariant.copy(alpha = 0.75f),
        line = scheme.onSurfaceVariant.copy(alpha = 0.65f),
        body = scheme.surfaceVariant,
        panel = scheme.surfaceContainerHighest,
        accent = scheme.secondaryContainer,
    )
}
