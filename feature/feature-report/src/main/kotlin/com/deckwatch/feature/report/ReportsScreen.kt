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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

/** The export screen — Tab 4's "Reports & export" (§13). */
@Serializable
internal object ExportRoute

/** The merge dialog of §13.5, as a screen: it needs the room. */
@Serializable
internal object ImportRoute

/**
 * `Reports & export` — the single entry point of `feature-report` (§13).
 *
 * Zero-argument callable so the app's `NavHost` keeps calling `ReportsScreen()`. Import hangs off a
 * nested `NavHost` here rather than leaking a route into the app module, the same arrangement
 * `DueScreen` uses for rounds and deficiencies.
 */
@Composable
fun ReportsScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ExportRoute,
        modifier = modifier,
    ) {
        composable<ExportRoute> {
            ExportScreen(
                onBack = onBack,
                onOpenImport = { navController.navigate(ImportRoute) },
            )
        }
        composable<ImportRoute> {
            ImportScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
@Suppress("LongMethod") // One screen, laid out top to bottom; splitting it would hide the order.
internal fun ExportScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels = rememberReportLabels()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val savedMessage = stringResource(R.string.reports_saved)
    val saveFailedMessage = stringResource(R.string.reports_save_failed)
    var pendingSave by remember { mutableStateOf<File?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportFileStore.MIME_HTML),
    ) { destination ->
        val file = pendingSave
        if (destination != null && file != null) {
            viewModel.saveTo(destination, file) { ok ->
                scope.launch { snackbarHost.showSnackbar(if (ok) savedMessage else saveFailedMessage) }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            DeckWatchTopBar(
                title = stringResource(R.string.reports_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.reports_back),
                subtitle = state.vessel?.name,
                actions = {
                    IconButton(onClick = onOpenImport) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = stringResource(R.string.reports_open_import),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.vessel == null) {
            EmptyState(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.reports_no_vessel_title),
                body = stringResource(R.string.reports_no_vessel_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item { SectionHeader(stringResource(R.string.reports_scope_heading)) }
                items(ExportScope.entries.toList(), key = { it.name }) { scopeOption ->
                    ScopeCard(
                        label = labels.scopeName(scopeOption),
                        description = scopeDescription(scopeOption),
                        selected = state.options.scope == scopeOption,
                        onSelect = { viewModel.selectScope(scopeOption) },
                    )
                }

                item { SectionHeader(stringResource(R.string.reports_options_heading)) }
                item { PhotoTierRow(state, viewModel::selectPhotoTier) }
                item { EstimateLine(state) }

                if (state.options.scope.needsDeck) {
                    item { SectionHeader(stringResource(R.string.reports_deck_heading)) }
                    items(state.decks, key = { it.id }) { deck ->
                        DeckWatchListRow(
                            title = deck.name,
                            subtitle = deck.shortCode,
                            onClick = { viewModel.selectDeck(deck.id) },
                            trailing = {
                                RadioButton(
                                    selected = state.options.deckId == deck.id,
                                    onClick = { viewModel.selectDeck(deck.id) },
                                )
                            },
                        )
                    }
                }

                if (state.options.scope.needsRound) {
                    item { SectionHeader(stringResource(R.string.reports_round_heading)) }
                    items(state.rounds, key = { it.id }) { round ->
                        DeckWatchListRow(
                            title = round.title,
                            subtitle = round.performedBy,
                            onClick = { viewModel.selectRound(round.id) },
                            trailing = {
                                RadioButton(
                                    selected = state.options.roundId == round.id,
                                    onClick = { viewModel.selectRound(round.id) },
                                )
                            },
                        )
                    }
                }

                item {
                    ResultBlock(
                        state = state,
                        onShare = { file -> context.startActivity(viewModel.shareIntentFor(file)) },
                        onSave = { file ->
                            pendingSave = file
                            saveLauncher.launch(file.name)
                        },
                    )
                }
            }

            if (state.phase is ExportPhase.Working) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.reports_generating),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
                )
            }

            // One primary action per screen — DESIGN_OVERHAUL rule 1.
            Button(
                onClick = { viewModel.generate(labels) },
                enabled = state.canGenerate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpacingL)
                    .heightIn(min = Dimens.TouchTargetPrimary),
            ) {
                Text(stringResource(R.string.reports_generate))
            }
        }
    }
}

@Composable
private fun ScopeCard(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs)
            .selectable(selected = selected, onClick = onSelect),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpacingM).heightIn(min = Dimens.TouchTargetPrimary),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.padding(start = Dimens.SpacingS)) {
                Text(text = label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoTierRow(state: ReportsUiState, onSelect: (PhotoTier) -> Unit) {
    val names = mapOf(
        PhotoTier.NONE to stringResource(R.string.reports_photo_none),
        PhotoTier.DEFICIENCY_ONLY to stringResource(R.string.reports_photo_deficiency),
        PhotoTier.ALL to stringResource(R.string.reports_photo_all),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpacingL),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        for (tier in PhotoTier.entries) {
            FilterChip(
                selected = state.options.photoTier == tier,
                onClick = { onSelect(tier) },
                label = { Text(names[tier].orEmpty()) },
                modifier = Modifier.heightIn(min = CHIP_MIN_HEIGHT),
            )
        }
    }
}

@Composable
private fun EstimateLine(state: ReportsUiState) {
    val text = when {
        state.estimating -> stringResource(R.string.reports_estimating)
        state.estimateBytes != null ->
            stringResource(R.string.reports_estimate, PhotoSizeEstimator.format(state.estimateBytes))

        else -> ""
    }
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
    )
}

@Composable
private fun ResultBlock(state: ReportsUiState, onShare: (File) -> Unit, onSave: (File) -> Unit) {
    when (val phase = state.phase) {
        is ExportPhase.Ready -> {
            val report = phase.report
            Column(modifier = Modifier.padding(Dimens.SpacingL)) {
                Text(
                    text = stringResource(
                        R.string.reports_ready,
                        report.file.name,
                        PhotoSizeEstimator.format(report.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (report.photosFailed > 0) {
                    Text(
                        text = stringResource(R.string.reports_photos_failed, report.photosFailed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = Dimens.SpacingS),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                ) {
                    OutlinedButton(
                        onClick = { onShare(report.file) },
                        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text(
                            text = stringResource(R.string.reports_share),
                            modifier = Modifier.padding(start = Dimens.SpacingS),
                        )
                    }
                    OutlinedButton(
                        onClick = { onSave(report.file) },
                        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Text(
                            text = stringResource(R.string.reports_save),
                            modifier = Modifier.padding(start = Dimens.SpacingS),
                        )
                    }
                }
            }
        }

        is ExportPhase.Failed -> Text(
            text = stringResource(R.string.reports_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(Dimens.SpacingL),
        )

        else -> {
            val last = state.lastReport ?: return
            OutlinedButton(
                onClick = { onShare(last) },
                modifier = Modifier
                    .padding(Dimens.SpacingL)
                    .heightIn(min = Dimens.TouchTargetMin),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(
                    text = stringResource(R.string.reports_share_last),
                    modifier = Modifier.padding(start = Dimens.SpacingS),
                )
            }
        }
    }
}

/** DESIGN_OVERHAUL rule 3 — chips are at least 40dp tall, gloves on. */
private val CHIP_MIN_HEIGHT = 40.dp
