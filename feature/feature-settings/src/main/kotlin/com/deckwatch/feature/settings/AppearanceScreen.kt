@file:OptIn(ExperimentalMaterial3Api::class)

package com.deckwatch.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.PlateHeading
import com.deckwatch.core.designsystem.components.StatusSpine
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.colorSchemeFor
import com.deckwatch.core.designsystem.theme.plateTextStyle
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode

/**
 * Appearance — the three themes of §14 and the list density of §18.
 *
 * The themes are shown, not described. "Bridge" means nothing as a word; a swatch of the actual
 * scheme, with a row of the app's own status colours on it, answers the only question an officer
 * has, which is what the screen will look like when they are standing in the dark.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.appearance_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.SpacingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            PlateHeading(text = stringResource(R.string.appearance_theme))
            ThemeMode.entries.forEach { mode ->
                ThemeChoice(
                    mode = mode,
                    selected = mode == state.themeMode,
                    onSelect = { viewModel.setThemeMode(mode) },
                )
            }
            Text(
                text = stringResource(R.string.appearance_theme_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PlateHeading(
                text = stringResource(R.string.appearance_density),
                modifier = Modifier.padding(top = Dimens.SpacingM),
            )
            ListDensity.entries.forEach { density ->
                DensityChoice(
                    density = density,
                    selected = density == state.density,
                    onSelect = { viewModel.setDensity(density) },
                )
            }
        }
    }
}

/**
 * One theme, rendered in its own colours.
 *
 * The swatch is built from [colorSchemeFor] rather than from a picture, so it cannot drift away
 * from the scheme it is advertising.
 */
@Composable
private fun ThemeChoice(mode: ThemeMode, selected: Boolean, onSelect: () -> Unit) {
    val scheme = colorSchemeFor(mode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary)
            .clip(RoundedCornerShape(Dimens.CardCorner))
            .border(
                width = if (selected) 2.dp else Dimens.Hairline,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(Dimens.CardCorner),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(Dimens.SpacingM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(themeNameRes(mode)), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(themeDescriptionRes(mode)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A miniature of the scheme: its ground, a card on it, and the status spine colours the
        // app uses to carry condition.
        Box(
            modifier = Modifier
                .size(width = SwatchWidth, height = SwatchHeight)
                .clip(RoundedCornerShape(Dimens.PlateCorner))
                .background(scheme.background)
                .border(Dimens.Hairline, scheme.outlineVariant, RoundedCornerShape(Dimens.PlateCorner)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(SwatchPadding),
                verticalArrangement = Arrangement.spacedBy(SwatchPadding),
            ) {
                repeat(SwatchRows) { index ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SwatchPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(Dimens.SpineWidth)
                                .height(SwatchRowHeight)
                                .background(SwatchSpineColors[index]),
                        )
                        Box(
                            modifier = Modifier
                                .width(SwatchLineWidth)
                                .height(SwatchRowHeight)
                                .background(scheme.surfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DensityChoice(density: ListDensity, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(densityNameRes(density)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(densityDescriptionRes(density)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A row at the density being offered, so the choice is visible rather than described.
        Row(
            modifier = Modifier
                .height(Dimens.rowHeight(density))
                .width(DensitySampleWidth)
                .clip(RoundedCornerShape(Dimens.PlateCorner))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(Dimens.SpacingXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusSpine(
                color = ConditionColors.Good,
                contentDescription = "",
                modifier = Modifier.height(Dimens.rowHeight(density) - Dimens.SpacingS),
            )
            Text(
                text = stringResource(densityNameRes(density)).take(1),
                style = plateTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.SpacingS),
            )
        }
    }
}

private fun themeNameRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.DAY -> R.string.appearance_theme_day
    ThemeMode.NIGHT -> R.string.appearance_theme_night
    ThemeMode.BRIDGE -> R.string.appearance_theme_bridge
}

private fun themeDescriptionRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.DAY -> R.string.appearance_theme_day_desc
    ThemeMode.NIGHT -> R.string.appearance_theme_night_desc
    ThemeMode.BRIDGE -> R.string.appearance_theme_bridge_desc
}

private fun densityNameRes(density: ListDensity): Int = when (density) {
    ListDensity.COMPACT -> R.string.appearance_density_compact
    ListDensity.COMFORTABLE -> R.string.appearance_density_comfortable
}

private fun densityDescriptionRes(density: ListDensity): Int = when (density) {
    ListDensity.COMPACT -> R.string.appearance_density_compact_desc
    ListDensity.COMFORTABLE -> R.string.appearance_density_comfortable_desc
}

private val SwatchWidth = 64.dp
private val SwatchHeight = 48.dp
private val SwatchRowHeight = 6.dp
private val SwatchLineWidth = 34.dp
private val SwatchPadding = 4.dp
private val DensitySampleWidth = 56.dp
private const val SwatchRows = 3

/** The condition colours the swatch shows: good, monitor, out of service — §14. */
private val SwatchSpineColors = listOf(
    ConditionColors.Good,
    ConditionColors.Monitor,
    ConditionColors.OutOfService,
)
