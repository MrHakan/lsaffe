package com.deckwatch.feature.vessel.deck

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.DeckPlanThumbnail
import com.deckwatch.feature.vessel.common.PrimaryButton
import com.deckwatch.feature.vessel.common.SwatchRow
import com.deckwatch.feature.vessel.common.Swatches

/**
 * Build or edit one deck (§6.3).
 *
 * The twenty-second path is deliberate: open the sheet, tap a preset — which fills the name and
 * the short code — and tap save. Everything else on the sheet is optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckEditSheet(
    presets: List<PlanPreset> = BuiltInPlanPresets.all,
    initial: Deck? = null,
    onSave: (DeckDraft) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DeckEditForm(presets = presets, initial = initial, onSave = onSave)
    }
}

/** The sheet body, hoisted out so it can be previewed and reused without a sheet around it. */
@Composable
internal fun DeckEditForm(
    presets: List<PlanPreset>,
    initial: Deck?,
    onSave: (DeckDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var shortCode by remember(initial) { mutableStateOf(initial?.shortCode.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }
    var plan by remember(initial) { mutableStateOf(initial?.plan ?: presets.firstOrNull()?.plan ?: DEFAULT_PLAN) }
    var tint by remember(initial) { mutableStateOf(initial?.colorTint ?: Swatches.Default.argb) }
    var selectedPresetKey by remember(initial) { mutableStateOf<String?>(null) }
    // Only auto-fill fields the user has not typed into.
    var nameIsAuto by remember(initial) { mutableStateOf(initial == null) }
    var codeIsAuto by remember(initial) { mutableStateOf(initial == null) }
    var showNameError by remember(initial) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.SpacingL)
            .padding(bottom = Dimens.SpacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        Text(
            text = stringResource(
                if (initial == null) R.string.deck_edit_title_new else R.string.deck_edit_title_edit,
            ),
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            text = stringResource(R.string.deck_edit_preset),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = stringResource(R.string.deck_edit_preset_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Resolved in composition so the tap handler below is a plain lambda.
        val presetNames: Map<String, String> = presets.associate { it.key to presetName(it) }
        PresetPicker(
            presets = presets,
            names = presetNames,
            selectedKey = selectedPresetKey,
            onSelect = { preset ->
                selectedPresetKey = preset.key
                plan = preset.plan.copy(bowAtTop = plan.bowAtTop)
                if (nameIsAuto) name = presetNames[preset.key] ?: preset.nameEn
                if (codeIsAuto) shortCode = preset.suggestedShortCode.orEmpty()
            },
        )

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameIsAuto = false
                showNameError = false
            },
            label = { Text(stringResource(R.string.deck_edit_name)) },
            singleLine = true,
            isError = showNameError,
            supportingText = {
                if (showNameError) {
                    Text(
                        text = stringResource(R.string.vessel_edit_name_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = shortCode,
            onValueChange = {
                shortCode = it.take(SHORT_CODE_MAX)
                codeIsAuto = false
            },
            label = { Text(stringResource(R.string.deck_edit_short_code)) },
            supportingText = { Text(stringResource(R.string.deck_edit_short_code_help)) },
            singleLine = true,
            textStyle = tagTextStyle(),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.deck_edit_colour),
            style = MaterialTheme.typography.labelLarge,
        )
        SwatchRow(selectedArgb = tint, onSelect = { tint = it })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetMin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.deck_edit_bow_at_top),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.deck_edit_bow_at_top_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = plan.bowAtTop,
                onCheckedChange = { plan = plan.copy(bowAtTop = it) },
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.deck_edit_notes)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = stringResource(R.string.vessel_action_save),
            onClick = {
                if (name.isBlank()) {
                    showNameError = true
                } else {
                    onSave(
                        DeckDraft(
                            name = name.trim(),
                            shortCode = shortCode.trim().ifBlank { null },
                            plan = plan,
                            colorTint = tint,
                            notes = notes.trim().ifBlank { null },
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PresetPicker(
    presets: List<PlanPreset>,
    names: Map<String, String>,
    selectedKey: String?,
    onSelect: (PlanPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        items(items = presets, key = { it.key }) { preset ->
            val selected = preset.key == selectedKey
            val label = names[preset.key] ?: preset.nameEn
            Column(
                modifier = Modifier
                    .widthIn(min = PRESET_TILE_WIDTH, max = PRESET_TILE_WIDTH)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(Dimens.CardCorner),
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(preset) },
                    )
                    .padding(Dimens.SpacingS),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            ) {
                DeckPlanThumbnail(
                    plan = preset.plan,
                    fill = MaterialTheme.colorScheme.surfaceVariant,
                    stroke = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Preset names are bilingual content (§6.3 / §19.7), not UI chrome, so a repository-supplied
 * preset carries its own `nameEn` / `nameTr`. The six built-ins additionally have string
 * resources so they translate with the rest of the module.
 */
@Composable
internal fun presetName(preset: PlanPreset): String {
    val builtIn = builtInPresetNameRes(preset.key)
    if (builtIn != null) return stringResource(builtIn)
    val language = LocalContext.current.resources.configuration.locales[0].language
    return if (language == TURKISH_LANGUAGE) preset.nameTr else preset.nameEn
}

@StringRes
private fun builtInPresetNameRes(key: String): Int? = when (key) {
    BuiltInPlanPresets.BULKER_MAIN_DECK -> R.string.preset_bulker_main_deck
    BuiltInPlanPresets.TANKER_MAIN_DECK -> R.string.preset_tanker_main_deck
    BuiltInPlanPresets.CONTAINER_MAIN_DECK -> R.string.preset_container_main_deck
    BuiltInPlanPresets.ACCOMMODATION_BLOCK -> R.string.preset_accommodation_block
    BuiltInPlanPresets.ENGINE_ROOM_FLAT -> R.string.preset_engine_room_flat
    BuiltInPlanPresets.BRIDGE_DECK -> R.string.preset_bridge_deck
    else -> null
}

/** Deck tint as a drawing colour; [Swatches.Default] stands in when the deck has none. */
internal fun deckTintColor(argb: Int?): Color = Color(argb ?: Swatches.Default.argb)

private val DEFAULT_PLAN = BuiltInPlanPresets.all.first().plan
private val PRESET_TILE_WIDTH = 96.dp
private const val SHORT_CODE_MAX = 4
private const val TURKISH_LANGUAGE = "tr"
