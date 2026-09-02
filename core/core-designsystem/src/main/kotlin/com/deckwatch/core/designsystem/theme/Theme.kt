package com.deckwatch.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode

/**
 * Day theme — high contrast, near-white ground, built for sunlight on deck — §14.
 */
private val DayColorScheme: ColorScheme = lightColorScheme(
    primary = Palette.Navy800,
    onPrimary = Palette.White,
    primaryContainer = Palette.Slate200,
    onPrimaryContainer = Palette.Navy900,
    secondary = Palette.Navy700,
    onSecondary = Palette.White,
    secondaryContainer = Palette.Slate100,
    onSecondaryContainer = Palette.Navy900,
    tertiary = Palette.AmberDeep,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.Amber,
    onTertiaryContainer = Palette.Navy950,
    background = Palette.Slate50,
    onBackground = Palette.Navy950,
    surface = Palette.White,
    onSurface = Palette.Navy950,
    surfaceVariant = Palette.Slate100,
    onSurfaceVariant = Palette.Navy700,
    outline = Palette.Slate400,
    outlineVariant = Palette.Slate200,
    error = ConditionColors.OutOfService,
    onError = Palette.White,
)

/** Night theme — true dark, OLED-friendly. */
private val NightColorScheme: ColorScheme = darkColorScheme(
    primary = Palette.Slate200,
    onPrimary = Palette.Navy950,
    primaryContainer = Palette.Navy800,
    onPrimaryContainer = Palette.Slate100,
    secondary = Palette.Slate300,
    onSecondary = Palette.Navy950,
    secondaryContainer = Palette.Navy850,
    onSecondaryContainer = Palette.Slate200,
    tertiary = Palette.Amber,
    onTertiary = Palette.Navy950,
    tertiaryContainer = Palette.AmberDeep,
    onTertiaryContainer = Palette.Slate50,
    background = Palette.Navy950,
    onBackground = Palette.Slate100,
    surface = Palette.Navy900,
    onSurface = Palette.Slate100,
    surfaceVariant = Palette.Navy850,
    onSurfaceVariant = Palette.Slate300,
    outline = Palette.Slate500,
    outlineVariant = Palette.Navy700,
    error = ConditionColors.OutOfService,
    onError = Palette.White,
)

/**
 * Bridge theme — red-only palette for a darkened bridge at night — §14.
 *
 * It is a theme, not a filter: every role is a red, the ground is near-black, and nothing carries
 * green or blue. See [Palette] for why readable brightness and night vision are not in conflict.
 */
private val BridgeColorScheme: ColorScheme = darkColorScheme(
    primary = Palette.BridgeRed,
    onPrimary = Palette.BridgeSurface,
    primaryContainer = Palette.BridgeRedDim,
    onPrimaryContainer = Palette.BridgeRed,
    secondary = Palette.BridgeOnDark,
    onSecondary = Palette.BridgeSurface,
    secondaryContainer = Palette.BridgeSurfaceHigh,
    onSecondaryContainer = Palette.BridgeRed,
    tertiary = Palette.BridgeRed,
    onTertiary = Palette.BridgeSurface,
    tertiaryContainer = Palette.BridgeRedDim,
    onTertiaryContainer = Palette.BridgeRed,
    background = Palette.BridgeSurface,
    onBackground = Palette.BridgeRed,
    surface = Palette.BridgeSurface,
    onSurface = Palette.BridgeRed,
    surfaceVariant = Palette.BridgeSurfaceHigh,
    onSurfaceVariant = Palette.BridgeOnDark,
    outline = Palette.BridgeRedDim,
    outlineVariant = Palette.BridgeSurfaceHigh,
    error = Palette.BridgeRed,
    onError = Palette.BridgeSurface,
)

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.DAY }

/**
 * @param themeMode the officer's choice from §18. Null follows the system's dark setting, which is
 *   the right default for a preview and for the first frame before preferences have loaded.
 * @param density the officer's §18 list density, published as [LocalListDensity] so that every
 *   list can honour it without taking it as a parameter.
 */
@Composable
fun DeckWatchTheme(
    themeMode: ThemeMode? = null,
    density: ListDensity = ListDensity.COMPACT,
    content: @Composable () -> Unit,
) {
    val resolved = themeMode ?: if (isSystemInDarkTheme()) ThemeMode.NIGHT else ThemeMode.DAY
    val colorScheme = when (resolved) {
        ThemeMode.DAY -> DayColorScheme
        ThemeMode.NIGHT -> NightColorScheme
        ThemeMode.BRIDGE -> BridgeColorScheme
    }
    CompositionLocalProvider(
        LocalThemeMode provides resolved,
        LocalListDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DeckWatchTypography,
            content = content,
        )
    }
}

/** The three schemes, for a picker that shows what it is offering — §18. */
fun colorSchemeFor(mode: ThemeMode): ColorScheme = when (mode) {
    ThemeMode.DAY -> DayColorScheme
    ThemeMode.NIGHT -> NightColorScheme
    ThemeMode.BRIDGE -> BridgeColorScheme
}
