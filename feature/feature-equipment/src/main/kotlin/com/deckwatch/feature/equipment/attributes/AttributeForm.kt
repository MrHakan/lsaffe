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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.deckwatch.core.designsystem.components.DateField
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.feature.equipment.R
import com.deckwatch.feature.equipment.dateFieldLabels
import com.deckwatch.feature.equipment.localised

/** The gutter the shared section header applies; the fields between headers match it. */
private val Gutter = Dimens.SpacingL

/**
 * The three groups a dynamic attribute form is ordered into — DESIGN_OVERHAUL, *Forms*:
 * identification → dates → checks.
 */
internal enum class AttributeGroup { IDENTIFICATION, DATES, CHECKS }

/** The heading each group renders under, so every form spells the three the same way. */
internal fun attributeGroupTitle(group: AttributeGroup): Int = when (group) {
    AttributeGroup.IDENTIFICATION -> R.string.attr_group_identification
    AttributeGroup.DATES -> R.string.attr_group_dates
    AttributeGroup.CHECKS -> R.string.attr_group_checks
}

/** The attributes of [schema] belonging to [group], in schema order. */
internal fun attributesIn(schema: List<AttributeDefinition>, group: AttributeGroup): List<AttributeDefinition> =
    schema.filter { groupOf(it) == group }

private fun groupOf(definition: AttributeDefinition): AttributeGroup = when {
    definition.kind == AttributeKind.DATE -> AttributeGroup.DATES
    definition.kind == AttributeKind.BOOLEAN -> AttributeGroup.CHECKS
    else -> AttributeGroup.IDENTIFICATION
}

/**
 * The dynamic attribute form — §9.3.
 *
 * Every field kind the schema can declare is rendered here and nowhere else, so the add flow, the
 * equipment sheet and the full-screen detail all edit attributes identically. Fields are grouped
 * identification → dates → checks, each under the design system's `SectionHeader`; required fields
 * carry a `*`, and every `DATE` is the shared `DateField` — nothing in this module is ever typed as
 * a date (DESIGN_OVERHAUL rule 4).
 *
 * The caller's [modifier] must carry **no** horizontal padding: the group headers supply their own
 * gutter and the fields match it, so a padded caller would indent the headings twice.
 *
 * @param footerFor extra content under one field — the live due-date consequence line of §7.5.4.
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
    if (schema.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        AttributeGroup.entries.forEach { group ->
            val fields = attributesIn(schema, group)
            if (fields.isEmpty()) return@forEach
            SectionHeader(stringResource(attributeGroupTitle(group)))
            AttributeFieldGroup(fields, values, errors, onValueChange, footerFor = footerFor)
        }
    }
}

/**
 * One group's fields, with the gutter the shared `SectionHeader` uses.
 *
 * Exposed so the add flow can interleave the record's fixed fields with the type's dynamic ones
 * under a single set of headings — a *Dates* section that lists the installed date and then the
 * type's last-service date reads as one form, two sections named "Dates" would not.
 */
@Composable
internal fun AttributeFieldGroup(
    fields: List<AttributeDefinition>,
    values: AttributeDraft,
    errors: Map<String, AttributeError>,
    onValueChange: (key: String, raw: String) -> Unit,
    modifier: Modifier = Modifier,
    footerFor: @Composable (AttributeDefinition) -> Unit = {},
) {
    if (fields.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        fields.forEach { definition ->
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
    val label = requiredLabel(localised(definition.labelEn, definition.labelTr), definition.required)
    Column(modifier = modifier.fillMaxWidth()) {
        when (definition.kind) {
            AttributeKind.BOOLEAN -> BooleanField(label, raw, onValueChange)
            AttributeKind.DATE -> AttributeDateField(definition, raw, error, onValueChange)
            AttributeKind.ENUM -> EnumField(definition, label, raw, onValueChange)
            AttributeKind.MULTI_ENUM -> MultiEnumField(definition, label, raw, onValueChange)
            AttributeKind.PHOTO, AttributeKind.SIGNATURE -> CaptureField(definition, label, raw)
            AttributeKind.NUMBER -> NumericField(definition, label, raw, error, onValueChange, decimal = false)
            AttributeKind.DECIMAL, AttributeKind.PRESSURE, AttributeKind.WEIGHT ->
                NumericField(definition, label, raw, error, onValueChange, decimal = true)
            AttributeKind.TEXT -> TextField(label, raw, onValueChange)
        }
        FieldMessages(definition, raw, error)
    }
}

/** Required fields are marked with `*` — DESIGN_OVERHAUL, *Forms*. */
internal fun requiredLabel(label: String, required: Boolean): String = if (required) "$label *" else label

@Composable
private fun TextField(label: String, raw: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = raw,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary),
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
            .heightIn(min = Dimens.TouchTargetPrimary),
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

/**
 * A `DATE` attribute — the shared [DateField], never a text field (DESIGN_OVERHAUL rule 4).
 *
 * The raw editor value of a date is its epoch-day as a decimal literal (see [AttributeDraft]), so
 * this is the one place that converts between the picker's `Long?` and that text.
 */
@Composable
private fun AttributeDateField(
    definition: AttributeDefinition,
    raw: String,
    error: AttributeError?,
    onValueChange: (String) -> Unit,
) {
    DateField(
        label = localised(definition.labelEn, definition.labelTr),
        epochDay = raw.trim().toLongOrNull(),
        onChange = { onValueChange(it?.toString().orEmpty()) },
        labels = dateFieldLabels(),
        required = definition.required,
        isError = error != null,
    )
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
                .heightIn(min = Dimens.TouchTargetPrimary),
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
