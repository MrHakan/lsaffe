package com.deckwatch.feature.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.ConfirmDialog
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens

/**
 * The import screen — §13.5's preview-and-merge dialog, given a whole screen because a real merge
 * has more conflicts than a dialog can show without scrolling behind the fold.
 *
 * The order is fixed and visible: pick a file, read what it contains, settle each conflict, and
 * only then press the one primary action. Nothing is written before that press.
 */
@Composable
fun ImportScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::load) }
    val mimeTypes = remember { arrayOf(ReportFileStore.MIME_HTML, ReportFileStore.MIME_JSON) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DeckWatchTopBar(
                title = stringResource(R.string.import_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.reports_back),
                subtitle = state.fileName,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            when (val phase = state.phase) {
                ImportPhase.Idle -> EmptyState(
                    icon = Icons.Filled.FileOpen,
                    title = stringResource(R.string.import_empty_title),
                    body = stringResource(R.string.import_empty_body),
                    actionLabel = stringResource(R.string.import_pick),
                    onAction = { picker.launch(mimeTypes) },
                )

                ImportPhase.Reading -> ProgressText(stringResource(R.string.import_reading))

                ImportPhase.Applying -> ProgressText(stringResource(R.string.import_applying))

                is ImportPhase.Failed -> FailureBlock(
                    message = importFailureMessage(phase.failure),
                    detail = phase.detail,
                    onPickAgain = { picker.launch(mimeTypes) },
                )

                is ImportPhase.Preview -> PreviewBlock(
                    preview = phase.preview,
                    resolutions = state.resolutions,
                    onResolve = viewModel::resolve,
                    onResolveAll = viewModel::resolveAll,
                    onApply = viewModel::apply,
                )

                is ImportPhase.Done -> OutcomeBlock(
                    outcome = phase.outcome,
                    onPickAgain = {
                        viewModel.reset()
                        picker.launch(mimeTypes)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProgressText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(Dimens.SpacingL),
    )
}

@Composable
private fun FailureBlock(message: String, detail: String, onPickAgain: () -> Unit) {
    Column(modifier = Modifier.padding(Dimens.SpacingL)) {
        Text(text = message, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpacingS),
            )
        }
        OutlinedButton(
            onClick = onPickAgain,
            modifier = Modifier
                .padding(top = Dimens.SpacingL)
                .heightIn(min = Dimens.TouchTargetMin),
        ) {
            Text(stringResource(R.string.import_pick_again))
        }
    }
}

@Composable
private fun PreviewBlock(
    preview: ImportPreview,
    resolutions: Map<String, ConflictResolution>,
    onResolve: (ImportConflict, ConflictResolution) -> Unit,
    onResolveAll: (ConflictResolution) -> Unit,
    onApply: () -> Unit,
) {
    // Destructive = confirm — DESIGN_OVERHAUL rule 8. An import overwrites records and can
    // propagate deletions, and neither is undoable from this screen.
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        val writes = preview.newRecords.total +
            preview.conflicts.count { resolutions[conflictKey(it.kind, it.id)] != ConflictResolution.KEEP_MINE }
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            body = stringResource(R.string.import_confirm_body, writes, preview.propagatedDeletions),
            confirmLabel = stringResource(R.string.import_confirm_ok),
            cancelLabel = stringResource(R.string.import_confirm_cancel),
            onConfirm = {
                confirming = false
                onApply()
            },
            onDismiss = { confirming = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { SectionHeader(stringResource(R.string.import_preview_heading)) }
            items(RecordKind.entries.filter { preview.incoming[it] > 0 }, key = { it.name }) { kind ->
                Column(modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs)) {
                    Text(text = recordKindName(kind), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(
                            R.string.import_row_counts,
                            preview.incoming[kind],
                            preview.newRecords[kind],
                            preview.unchanged[kind],
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (preview.propagatedDeletions > 0) {
                item {
                    Text(
                        text = stringResource(R.string.import_deletions, preview.propagatedDeletions),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(Dimens.SpacingL),
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.import_conflicts_heading, preview.conflicts.size)) }
            if (preview.conflicts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.import_no_conflicts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.SpacingL),
                    )
                }
            } else {
                item { ApplyAllRow(onResolveAll) }
                items(preview.conflicts, key = { conflictKey(it.kind, it.id) }) { conflict ->
                    ConflictRow(
                        conflict = conflict,
                        resolution = resolutions[conflictKey(conflict.kind, conflict.id)]
                            ?: conflict.suggested,
                        onResolve = { onResolve(conflict, it) },
                    )
                }
            }
        }

        // One primary action — DESIGN_OVERHAUL rule 1. Until it is pressed, nothing is written.
        Button(
            onClick = { confirming = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpacingL)
                .heightIn(min = Dimens.TouchTargetPrimary),
        ) {
            Text(stringResource(R.string.import_apply))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApplyAllRow(onResolveAll: (ConflictResolution) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS)) {
        Text(
            text = stringResource(R.string.import_apply_all),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
            for (resolution in ConflictResolution.entries) {
                OutlinedButton(
                    onClick = { onResolveAll(resolution) },
                    modifier = Modifier.heightIn(min = CHIP_HEIGHT),
                ) {
                    Text(resolutionLabel(resolution))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictRow(
    conflict: ImportConflict,
    resolution: ConflictResolution,
    onResolve: (ConflictResolution) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conflict.label.ifBlank { conflict.id },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (conflict.incomingIsDeletion) {
                    StatusChip(
                        text = stringResource(R.string.import_conflict_deletion),
                        color = ConditionColors.OutOfService,
                    )
                }
            }
            Text(
                text = recordKindName(conflict.kind),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.padding(top = Dimens.SpacingS),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                for (option in ConflictResolution.entries) {
                    FilterChip(
                        selected = resolution == option,
                        onClick = { onResolve(option) },
                        label = { Text(resolutionLabel(option)) },
                        modifier = Modifier.heightIn(min = CHIP_HEIGHT),
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeBlock(outcome: ImportOutcome, onPickAgain: () -> Unit) {
    Column(modifier = Modifier.padding(Dimens.SpacingL)) {
        val text = when (outcome) {
            is ImportOutcome.Applied ->
                stringResource(R.string.import_result_applied, outcome.written, outcome.deletions)

            is ImportOutcome.Rejected ->
                stringResource(
                    R.string.import_result_rejected,
                    outcome.violations.take(MAX_VIOLATIONS_SHOWN).joinToString("; "),
                )

            is ImportOutcome.RolledBack ->
                stringResource(R.string.import_result_rolled_back, outcome.message)
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
        if (outcome is ImportOutcome.RolledBack && outcome.unrecoverable.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.import_result_unrecoverable,
                    outcome.unrecoverable.take(MAX_VIOLATIONS_SHOWN).joinToString("; "),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Dimens.SpacingS),
            )
        }
        OutlinedButton(
            onClick = onPickAgain,
            modifier = Modifier
                .padding(top = Dimens.SpacingL)
                .heightIn(min = Dimens.TouchTargetMin),
        ) {
            Text(stringResource(R.string.import_pick_again))
        }
    }
}

@Composable
private fun resolutionLabel(resolution: ConflictResolution): String = when (resolution) {
    ConflictResolution.KEEP_MINE -> stringResource(R.string.import_keep_mine)
    ConflictResolution.TAKE_THEIRS -> stringResource(R.string.import_take_theirs)
    ConflictResolution.KEEP_BOTH -> stringResource(R.string.import_keep_both)
}

/** DESIGN_OVERHAUL rule 3 — chips are at least 40dp tall. */
private val CHIP_HEIGHT = 40.dp

private const val MAX_VIOLATIONS_SHOWN = 5
