package com.deckwatch.feature.notes.equipment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TechnicalNote
import com.deckwatch.feature.notes.ListSectionHeading
import com.deckwatch.feature.notes.MetaChip
import com.deckwatch.feature.notes.NotesEmptyState
import com.deckwatch.feature.notes.R
import com.deckwatch.feature.notes.equipmentGroupLabel
import com.deckwatch.feature.notes.intervalLabel
import com.deckwatch.feature.notes.performedByRes

/**
 * One equipment type's page — §9.1.
 *
 * The order is the order an officer needs it in: what the thing is, then the numbers that get
 * asked, then what has to be done to it and how often, then the rules behind that, then what
 * inspectors actually write up. Rules come after the tests deliberately — the officer is standing
 * in front of the equipment, not reading the convention.
 */
@Composable
internal fun EquipmentTypeDetailScreen(
    typeKey: String,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EquipmentTypeDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(typeKey) { viewModel.bind(typeKey) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val type = state.type
    if (type == null) {
        if (!state.loading) NotesEmptyState(text = stringResource(R.string.guide_type_missing), modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SpacingM),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        item(key = "identity") { TypeIdentity(type) }

        if (type.helpTextEn.isNotBlank()) {
            item(key = "help") {
                Text(text = type.helpTextEn, style = MaterialTheme.typography.bodyMedium)
            }
        }

        for (note in type.technicalNotes) {
            item(key = "note-${note.heading}") { TechnicalNoteCard(note) }
        }

        if (state.tasks.isNotEmpty()) {
            item(key = "tasks-heading") {
                ListSectionHeading(text = stringResource(R.string.guide_tasks))
            }
            for (task in state.tasks) {
                item(key = "task-${task.key}") { TaskRow(task) }
            }
        }

        if (state.cards.isNotEmpty()) {
            item(key = "rules-heading") {
                ListSectionHeading(text = stringResource(R.string.guide_rules))
            }
            for (card in state.cards) {
                item(key = "rule-${card.refKey}") {
                    RuleRow(card = card, onClick = { onCardClick(card.refKey) })
                }
            }
        }

        if (type.commonPscFindings.isNotEmpty()) {
            item(key = "psc-heading") {
                ListSectionHeading(text = stringResource(R.string.guide_psc))
            }
            item(key = "psc") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Dimens.SpacingM)) {
                        type.commonPscFindings.forEach { Bullet(it) }
                    }
                }
            }
        }

        item(key = "disclaimer") {
            Text(
                text = stringResource(R.string.guide_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpacingM),
            )
        }
    }
}

@Composable
private fun TypeIdentity(type: EquipmentType) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SymbolTile(symbolKey = type.symbolKey)
        Column(modifier = Modifier.padding(start = Dimens.SpacingM)) {
            Text(text = type.nameEn, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${stringResource(equipmentGroupLabel(type.group))} · ${type.subGroup}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TechnicalNoteCard(note: TechnicalNote) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.SpacingM)) {
            Text(
                text = note.heading,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingS))
            note.bullets.forEach { Bullet(it) }
        }
    }
}

@Composable
private fun TaskRow(task: TaskDefinition) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.SpacingM)) {
            Text(text = task.titleEn, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.padding(top = Dimens.SpacingXs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            ) {
                MetaChip(text = intervalLabel(task.intervalKind, task.intervalMonths))
                MetaChip(text = stringResource(performedByRes(task.performedBy)))
            }
            if (task.descriptionEn.isNotBlank()) {
                Text(
                    text = task.descriptionEn,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimens.SpacingXs),
                )
            }
        }
    }
}

@Composable
private fun RuleRow(card: RegulationCard, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingM)) {
            Text(
                text = card.citation,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = card.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = card.what,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = Dimens.SpacingXs)) {
        Text(text = "•", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Dimens.SpacingS),
        )
    }
}
