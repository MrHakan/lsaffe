package com.deckwatch.feature.equipment.attributes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.feature.equipment.R
import com.deckwatch.feature.equipment.localised

/**
 * Read-only rendering of a type's dynamic attributes — the summary the equipment sheet shows before
 * the officer taps *Edit details* (§7.4 full stage).
 *
 * Monthly-checklist booleans are excluded: they have their own compact checklist control in the
 * sheet (§9.3) and repeating them here would double the length of the summary for no information.
 */
@Composable
internal fun AttributeSummaryList(
    schema: List<AttributeDefinition>,
    values: AttributeDraft,
    modifier: Modifier = Modifier,
    includeChecklistItems: Boolean = false,
) {
    val rows = schema.filter { includeChecklistItems || !it.monthlyChecklist }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.equip_attributes_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        rows.forEach { definition ->
            val raw = values[definition.key].orEmpty()
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = localised(definition.labelEn, definition.labelTr),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(SummaryLabelWidth),
                )
                Text(
                    text = attributeDisplayValue(definition, raw),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (AttributeCodec.band(definition, raw)) {
                        BandStatus.OUT_OF_BAND -> ConditionColors.Defective
                        BandStatus.IN_BAND -> ConditionColors.Good
                        BandStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val SummaryLabelWidth = 148.dp

/** One attribute's value as the officer reads it: dates as ISO, booleans as yes/no, units appended. */
@Composable
internal fun attributeDisplayValue(definition: AttributeDefinition, raw: String): String {
    val trimmed = raw.trim()
    val notSet = stringResource(R.string.attr_not_set)
    return when (definition.kind) {
        AttributeKind.BOOLEAN -> stringResource(
            if (AttributeCodec.isTicked(trimmed)) R.string.attr_yes else R.string.attr_no,
        )
        AttributeKind.DATE -> trimmed.toLongOrNull()
            ?.let { java.time.LocalDate.ofEpochDay(it).toString() }
            ?: notSet
        AttributeKind.MULTI_ENUM -> AttributeCodec.multiSelection(trimmed)
            .joinToString(", ") { it.optionLabel() }
            .ifEmpty { notSet }
        AttributeKind.ENUM -> trimmed.optionLabel().ifEmpty { notSet }
        AttributeKind.PHOTO, AttributeKind.SIGNATURE -> trimmed.ifEmpty { notSet }
        AttributeKind.NUMBER, AttributeKind.DECIMAL, AttributeKind.PRESSURE, AttributeKind.WEIGHT ->
            if (trimmed.isEmpty()) notSet else listOfNotNull(trimmed, definition.unit?.takeIf { it.isNotBlank() }).joinToString(" ")
        AttributeKind.TEXT -> trimmed.ifEmpty { notSet }
    }
}
