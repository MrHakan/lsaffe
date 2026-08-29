package com.deckwatch.feature.inspection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import kotlinx.coroutines.delay

/**
 * Walking a round, one item at a time — the list equivalent of the canvas sweep (§7.1 C, §7.3).
 *
 * Grading advances to the next unchecked item without leaving the screen, so a weekly round is a
 * rhythm of one tap per item. Skip moves on without writing anything, which keeps the "18 of 24
 * checked" count honest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundRunScreen(
    roundId: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RoundRunViewModel = hiltViewModel(),
) {
    LaunchedEffect(roundId) { viewModel.bind(roundId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var index by rememberSaveable(roundId) { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, (state.items.size - 1).coerceAtLeast(0))
    val current = state.items.getOrNull(safeIndex)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.rounds_progress, state.doneCount, state.itemCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LinearProgressIndicator(
                progress = {
                    if (state.itemCount > 0) state.doneCount.toFloat() / state.itemCount else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (current == null) {
                EmptyHint(text = stringResource(R.string.round_run_empty))
                return@Column
            }
            RoundItemPane(
                item = current,
                position = safeIndex + 1,
                total = state.itemCount,
                onGrade = { grade ->
                    viewModel.grade(current.itemId, grade)
                    index = nextUnchecked(state, safeIndex)
                },
                onRemark = { viewModel.setRemark(current.itemId, it) },
                modifier = Modifier.weight(1f),
            )
            HorizontalDivider()
            RoundRunControls(
                canGoBack = safeIndex > 0,
                canGoForward = safeIndex < state.itemCount - 1,
                allChecked = state.allChecked,
                finished = state.finished,
                onPrevious = { index = safeIndex - 1 },
                onSkip = { index = (safeIndex + 1).coerceAtMost(state.itemCount - 1) },
                onFinish = {
                    viewModel.finish()
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun RoundItemPane(
    item: RoundRunItem,
    position: Int,
    total: Int,
    onGrade: (ConditionGrade) -> Unit,
    onRemark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var remark by remember(item.itemId) { mutableStateOf(item.remark.orEmpty()) }
    // Debounced so a remark typed on deck is one write, not one per keystroke.
    LaunchedEffect(item.itemId, remark) {
        if (remark != item.remark.orEmpty()) {
            delay(REMARK_DEBOUNCE_MS)
            onRemark(remark)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.SpacingL),
    ) {
        Text(
            text = stringResource(R.string.round_run_position, position, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpacingM),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolTile(symbolKey = item.symbolKey, size = RunSymbolSize)
            Column(modifier = Modifier.weight(1f)) {
                TagText(tag = item.tag)
                item.name?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
                    if (item.deckShortName.isNotBlank()) {
                        InfoChip(text = item.deckShortName)
                    }
                    if (item.checked) {
                        InfoChip(
                            text = stringResource(R.string.round_run_checked),
                            color = ConditionColors.Good.copy(alpha = 0.18f),
                            contentColor = ConditionColors.Good,
                        )
                    }
                }
                item.location?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.round_run_condition),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpacingL),
        )
        ConditionChipRow(
            selected = item.condition,
            onSelect = onGrade,
            modifier = Modifier.padding(top = Dimens.SpacingS),
        )
        DialogField(
            value = remark,
            onValueChange = { remark = it },
            label = stringResource(R.string.round_run_remark),
            singleLine = false,
            modifier = Modifier.padding(top = Dimens.SpacingM),
        )
    }
}

@Composable
private fun RoundRunControls(
    canGoBack: Boolean,
    canGoForward: Boolean,
    allChecked: Boolean,
    finished: Boolean,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier = Modifier.padding(Dimens.SpacingM)) {
        if (allChecked && !finished) {
            Text(
                text = stringResource(R.string.round_run_all_checked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.SpacingS),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrevious, enabled = canGoBack) {
                Text(stringResource(R.string.round_run_previous))
            }
            OutlinedButton(onClick = onSkip, enabled = canGoForward) {
                Text(stringResource(R.string.round_run_skip))
            }
            Button(
                onClick = onFinish,
                enabled = !finished,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.round_run_finish))
            }
        }
    }
}

/** Sweep behaviour — after a grade, jump to the next item that still has none (§7.3). */
private fun nextUnchecked(state: RoundRunUiState, from: Int): Int {
    val forward = (from + 1 until state.items.size).firstOrNull { !state.items[it].checked }
    if (forward != null) return forward
    val wrapped = (0 until from).firstOrNull { !state.items[it].checked }
    return wrapped ?: from
}

private val RunSymbolSize = 48.dp
private const val REMARK_DEBOUNCE_MS = 400L
