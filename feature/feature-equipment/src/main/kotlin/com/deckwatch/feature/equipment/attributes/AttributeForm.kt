@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deckwatch.feature.equipment.attributes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.feature.equipment.R
import com.deckwatch.feature.equipment.localised

/** Epoch-millis in one day — the date picker speaks UTC millis, the register speaks epoch-days (§6). */
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The dynamic attribute form — §9.3.
 *
 * Every field kind the schema can declare is rendered here and nowhere else, so the add flow, the
 * equipment sheet and the full-screen detail all edit attributes identically.
 *
 * @param footerFor extra content under one field — the live due-date preview of §7.5.4.
 */
@Composable
internal fun AttributeForm(
    schema: List<AttributeDefinition>,
    values: AttributeDraft,
    errors: Map<String, AttributeError>,
    onValueChange: (key: String, raw: String) -> Unit,
    modifier: Modifier = Modifier,
    footerFor: @Composable (AttributeDefinition) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        schema.forEach { definition ->
            AttributeField(
                definition = definition,
                raw = values[definition.key].orEmpty(),
                error = errors[definition.key],
                onValueChange = { onValueChange(definition.key, it) },
            )
            footerFor(definition)
        }
    }
}

@Composable
private fun AttributeField(
    definition: AttributeDefinition,
    raw: String,
    error: AttributeError?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = localised(definition.labelEn, definition.labelTr)
    val required = if (definition.required) " (${stringResource(R.string.attr_required)})" else ""
    Column(modifier = modifier.fillMaxWidth()) {
        when (definition.kind) {
            AttributeKind.BOOLEAN -> BooleanField(label, raw, onValueChange)
            AttributeKind.DATE -> DateField(label + required, raw, onValueChange)
            AttributeKind.ENUM -> EnumField(definition, label + required, raw, onValueChange)
            AttributeKind.MULTI_ENUM -> MultiEnumField(definition, label + required, raw, onValueChange)
            AttributeKind.PHOTO, AttributeKind.SIGNATURE -> CaptureField(definition, label, raw)
            AttributeKind.NUMBER -> NumericField(definition, label + required, raw, error, onValueChange, decimal = false)
            AttributeKind.DECIMAL, AttributeKind.PRESSURE, AttributeKind.WEIGHT ->
                NumericField(definition, label + required, raw, error, onValueChange, decimal = true)
            AttributeKind.TEXT -> TextField(label + required, raw, onValueChange)
        }
        FieldMessages(definition, raw, error)
    }
}

@Composable
private fun TextField(label: String, raw: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = raw,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin),
    )
}

@Composable
private fun NumericField(
    definition: AttributeDefinition,
    label: String,
    raw: String,
    error: AttributeError?,
    onValueChange: (String) -> Unit,
    decimal: Boolean,
) {
    OutlinedTextField(
        value = raw,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        suffix = definition.unit?.takeIf { it.isNotBlank() }?.let { unit -> { Text(unit) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin),
    )
}

@Composable
private fun BooleanField(label: String, raw: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = AttributeCodec.isTicked(raw),
            onCheckedChange = { onValueChange(it.toString()) },
        )
    }
}

@Composable
private fun DateField(label: String, raw: String, onValueChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val epochDay = raw.trim().toLongOrNull()

    OutlinedTextField(
        value = epochDay?.let { java.time.LocalDate.ofEpochDay(it).toString() }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            TextButton(onClick = { picking = true }) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = stringResource(R.string.attr_pick_date),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin),
    )

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = epochDay?.let { it * MILLIS_PER_DAY },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = state.selectedDateMillis
                        onValueChange(selected?.let { (it / MILLIS_PER_DAY).toString() }.orEmpty())
                        picking = false
                    },
                ) { Text(stringResource(R.string.attr_ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onValueChange("")
                        picking = false
                    },
                ) { Text(stringResource(R.string.attr_clear)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun EnumField(
    definition: AttributeDefinition,
    label: String,
    raw: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = raw.optionLabel(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetMin),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            definition.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.optionLabel()) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                )
            }
        }
    }
}

@Composable
private fun MultiEnumField(
    definition: AttributeDefinition,
    label: String,
    raw: String,
    onValueChange: (String) -> Unit,
) {
    val selected = AttributeCodec.multiSelection(raw).toSet()
    Text(text = label, style = MaterialTheme.typography.labelLarge)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        definition.options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = {
                    // Keep the schema's own option order so the stored value is stable.
                    val next = definition.options.filter { candidate ->
                        if (candidate == option) candidate !in selected else candidate in selected
                    }
                    onValueChange(AttributeCodec.multiRaw(next))
                },
                label = { Text(option.optionLabel()) },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            )
        }
    }
}

/**
 * `PHOTO` / `SIGNATURE` placeholder — the value is a URI string and there is no capture flow yet
 * (deferred to the photo phase; the field still displays and round-trips any URI already stored).
 */
@Composable
private fun CaptureField(definition: AttributeDefinition, label: String, raw: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = raw.ifBlank {
                stringResource(
                    if (definition.kind == AttributeKind.PHOTO) {
                        R.string.attr_photo_placeholder
                    } else {
                        R.string.attr_signature_placeholder
                    },
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Errors, the green-band verdict and the schema's own help text, in that order of urgency. */
@Composable
private fun FieldMessages(definition: AttributeDefinition, raw: String, error: AttributeError?) {
    when (error) {
        AttributeError.REQUIRED -> FieldNote(stringResource(R.string.attr_error_required), MaterialTheme.colorScheme.error)
        AttributeError.NOT_A_NUMBER -> FieldNote(stringResource(R.string.attr_error_number), MaterialTheme.colorScheme.error)
        null -> Unit
    }
    when (AttributeCodec.band(definition, raw)) {
        BandStatus.OUT_OF_BAND -> FieldNote(
            text = stringResource(R.string.attr_warning_band, definition.minValue.orDash(), definition.maxValue.orDash()),
            color = ConditionColors.Defective,
        )
        BandStatus.IN_BAND -> FieldNote(
            text = stringResource(R.string.attr_in_band, definition.minValue.orDash(), definition.maxValue.orDash()),
            color = ConditionColors.Good,
        )
        BandStatus.UNKNOWN -> Unit
    }
    val help = localised(definition.helpEn, definition.helpTr)
    if (help.isNotBlank()) FieldNote(help, MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FieldNote(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = Dimens.SpacingXs),
    )
}

/** A confirm button sized for gloves — C6. */
@Composable
internal fun AttributePrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.TouchTargetPrimary),
    ) { Text(text) }
}

/** `DRY_POWDER_ABC` -> `Dry powder ABC` — catalogue option tokens are upper snake case. */
internal fun String.optionLabel(): String =
    split('_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> if (word.length <= SHORT_TOKEN) word else word.lowercase() }
        .replaceFirstChar { it.uppercase() }

/** Tokens this short (`CO2`, `ABC`, `BC`) are abbreviations and stay upper case. */
private const val SHORT_TOKEN = 3

private fun Double?.orDash(): String = this?.let { value ->
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
} ?: "—"
