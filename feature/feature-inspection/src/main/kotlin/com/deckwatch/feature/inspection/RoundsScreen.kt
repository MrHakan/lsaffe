package com.deckwatch.feature.inspection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.Round

/**
 * Round history plus the start-a-round flow — §6.7, and the list-mode sweep §7.1 C requires.
 *
 * Starting a round materialises it from a bundled template (§19 (5)) and drops straight into the
 * run screen, because the officer taps "start" while already standing at the first item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundsScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val turkish = isTurkishLocale()
    LaunchedEffect(turkish) { viewModel.setTurkish(turkish) }

    var runningRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val noMatchMessage = stringResource(R.string.rounds_no_matching_equipment)

    LaunchedEffect(event) {
        when (val current = event) {
            is RoundsEvent.Started -> {
                runningRoundId = current.roundId
                viewModel.consumeEvent()
            }

            RoundsEvent.NoMatchingEquipment -> {
                snackbarHostState.showSnackbar(noMatchMessage)
                viewModel.consumeEvent()
            }

            null -> Unit
        }
    }

    val activeRound = runningRoundId
    if (activeRound != null) {
        RoundRunScreen(
            roundId = activeRound,
            onBack = { runningRoundId = null },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rounds_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.insp_action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickerOpen = true },
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                text = { Text(stringResource(R.string.rounds_start)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.hasVessel -> EmptyHint(text = stringResource(R.string.due_no_vessel))
                state.rounds.isEmpty() -> EmptyHint(text = stringResource(R.string.rounds_empty))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rounds, key = { it.id }) { round ->
                        RoundHistoryRow(
                            round = round,
                            onClick = { runningRoundId = round.id },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        StartRoundDialog(
            templates = state.templates,
            onDismiss = { pickerOpen = false },
            onStart = { templateKey, performedBy ->
                pickerOpen = false
                viewModel.startRound(templateKey, performedBy)
            },
        )
    }
}

@Composable
private fun RoundHistoryRow(round: Round, onClick: () -> Unit) {
    val progress = if (round.itemCount > 0) round.doneCount.toFloat() / round.itemCount else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Dimens.ListRowComfortable)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingM),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = round.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (round.completedAt == null) {
                InfoChip(
                    text = stringResource(R.string.rounds_in_progress),
                    color = ConditionColors.Monitor.copy(alpha = 0.18f),
                    contentColor = ConditionColors.Monitor,
                )
            }
            if (round.deficiencyCount > 0) {
                InfoChip(
                    text = stringResource(R.string.rounds_deficiency_count, round.deficiencyCount),
                    color = ConditionColors.Defective.copy(alpha = 0.18f),
                    contentColor = ConditionColors.Defective,
                )
            }
        }
        Text(
            text = stringResource(R.string.rounds_progress, round.doneCount, round.itemCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpacingXs),
        )
        Row(
            modifier = Modifier.padding(top = Dimens.SpacingXs),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
        ) {
            Text(
                text = stringResource(R.string.rounds_started, Dates.formatIso(round.startedAt.epochMillisToDay())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            round.completedAt?.let { completed ->
                Text(
                    text = stringResource(R.string.rounds_completed, Dates.formatIso(completed.epochMillisToDay())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (round.performedBy.isNotBlank()) {
                Text(
                    text = round.performedBy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StartRoundDialog(
    templates: List<RoundTemplateOption>,
    onDismiss: () -> Unit,
    onStart: (templateKey: String, performedBy: String) -> Unit,
) {
    val turkish = isTurkishLocale()
    var selected by rememberSaveable { mutableStateOf(templates.firstOrNull()?.key) }
    var performedBy by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rounds_pick_template)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PickerMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (templates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.rounds_no_templates),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                templates.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.TouchTargetMin)
                            .clickable { selected = option.key },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                    ) {
                        RadioButton(
                            selected = option.key == selected,
                            onClick = { selected = option.key },
                        )
                        Text(
                            text = option.title.resolve(turkish),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        InfoChip(text = stringResource(R.string.rounds_template_matches, option.matchCount))
                    }
                }
                DialogField(
                    value = performedBy,
                    onValueChange = { performedBy = it },
                    label = stringResource(R.string.rounds_performed_by),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { onStart(it, performedBy) } },
            ) {
                Text(stringResource(R.string.rounds_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.insp_action_cancel)) }
        },
    )
}

/** Epoch-millis to epoch-days for display — round stamps are millis (§6.7), dates are days (§6). */
internal fun Long.epochMillisToDay(): Long = Math.floorDiv(this, MILLIS_PER_DAY)

private const val MILLIS_PER_DAY = 86_400_000L
private val PickerMaxHeight = 380.dp
