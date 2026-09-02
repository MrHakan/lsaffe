package com.deckwatch.feature.survivalcraft

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.equipment.AddEquipmentSheet
import com.deckwatch.feature.equipment.EquipmentBottomSheet
import com.deckwatch.feature.survivalcraft.schematic.SchematicDrawing
import com.deckwatch.feature.survivalcraft.schematic.SchematicOverlay
import com.deckwatch.feature.survivalcraft.schematic.SchematicPanel
import com.deckwatch.feature.survivalcraft.schematic.rememberSchematicPalette

/**
 * The dedicated survival-craft view of §7.6, generalised: one screen driven entirely by the JSON
 * schematic definitions.
 *
 * A schematic elevation is drawn for the parent's type (falling back to a components-only view),
 * hotspots over it stand for the sub-components that get inspected, and the panels below carry the
 * boat inventory, the MSC.402(96) task set and the SOLAS III/19 drill log.
 *
 * @param equipmentId the parent craft / system.
 */
@Composable
fun SchematicScreen(
    equipmentId: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: SurvivalCraftViewModel = hiltViewModel()
    LaunchedEffect(equipmentId) { viewModel.bind(equipmentId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var openChildId by remember { mutableStateOf<String?>(null) }
    var addingForHotspot by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selectedPanel by rememberSaveable { mutableStateOf(0) }

    val equipment = state.equipment
    val panels = state.panels
    val panel = panels.getOrElse(selectedPanel) { SchematicPanel.COMPONENTS }
    val drillTitle = stringResource(R.string.sc_drill_title_default)

    Scaffold(
        modifier = modifier,
        topBar = {
            DeckWatchTopBar(
                title = equipment?.tag ?: stringResource(R.string.sc_loading),
                subtitle = state.schematic?.let { localised(it.titleEn, it.titleTr) },
                onBack = onBack,
                backContentDescription = stringResource(R.string.sc_back),
            )
        },
        bottomBar = {
            PrimaryAction(
                panel = panel,
                state = state,
                onAdd = {
                    addingForHotspot = null
                    adding = true
                },
                onRecordDrill = viewModel::openDrill,
                onAddInventoryRow = { viewModel.addInventoryRow(it) },
            )
        },
    ) { padding ->
        when {
            state.missing -> EmptyState(
                icon = Icons.Filled.Sailing,
                title = stringResource(R.string.sc_not_found_title),
                body = stringResource(R.string.sc_not_found_body),
                modifier = Modifier.padding(padding),
            )

            equipment == null -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = stringResource(R.string.sc_loading),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                SchematicCard(
                    state = state,
                    onHotspot = { hotspot ->
                        val childId = hotspot.childId
                        if (childId != null) {
                            openChildId = childId
                        } else {
                            addingForHotspot = hotspot.hotspot.key
                            adding = true
                        }
                    },
                )

                if (panels.size > 1) {
                    ScrollableTabRow(selectedTabIndex = selectedPanel.coerceIn(0, panels.lastIndex)) {
                        panels.forEachIndexed { index, entry ->
                            Tab(
                                selected = index == selectedPanel,
                                onClick = { selectedPanel = index },
                                modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary),
                                text = { Text(panelLabel(entry)) },
                            )
                        }
                    }
                }

                val completion = state.completionDraft
                if (completion != null) {
                    TaskCompletionForm(
                        draft = completion,
                        onChange = viewModel::updateCompletion,
                        onSave = viewModel::saveCompletion,
                        onCancel = viewModel::dismissCompletion,
                    )
                }
                val drill = state.drillDraft
                if (drill != null) {
                    DrillForm(
                        draft = drill,
                        onChange = viewModel::updateDrill,
                        onSave = { viewModel.saveDrill(drillTitle) },
                        onCancel = viewModel::dismissDrill,
                    )
                }

                when (panel) {
                    SchematicPanel.COMPONENTS -> ComponentsPanel(
                        state = state,
                        onOpenChild = { openChildId = it },
                        onAdd = {
                            addingForHotspot = null
                            adding = true
                        },
                    )

                    SchematicPanel.INVENTORY -> InventoryPanel(
                        state = state,
                        onQuantity = { key, quantity ->
                            viewModel.updateInventoryItem(key) { it.copy(quantity = quantity) }
                        },
                        onExpiry = { key, day ->
                            viewModel.updateInventoryItem(key) { it.copy(expiryEpochDay = day) }
                        },
                        onRemove = viewModel::removeInventoryRow,
                    )

                    SchematicPanel.TASKS -> TasksPanel(
                        state = state,
                        onLog = viewModel::openCompletion,
                    )

                    SchematicPanel.DRILL_LOG -> DrillPanel(
                        state = state,
                        onRecord = viewModel::openDrill,
                    )
                }

                MessageLine(message = state.message, onConsume = viewModel::consumeMessage)
                Box(modifier = Modifier.navigationBarsPadding())
            }
        }
    }

    val childId = openChildId
    if (childId != null) {
        EquipmentBottomSheet(equipmentId = childId, onDismiss = { openChildId = null })
    }

    if (adding && equipment != null) {
        val hotspotKey = addingForHotspot
        AddEquipmentSheet(
            vesselId = equipment.vesselId,
            onDismiss = {
                adding = false
                addingForHotspot = null
            },
            deckId = equipment.deckId,
            zoneId = equipment.zoneId,
            posX = equipment.posX,
            posY = equipment.posY,
            onCreated = { ids -> viewModel.adoptChildren(ids, hotspotKey) },
        )
    }
}

/**
 * The one primary action of the screen — DESIGN_OVERHAUL rule 1. Which action it is follows the
 * selected panel, so there are never two competing buttons.
 */
@Composable
private fun PrimaryAction(
    panel: SchematicPanel,
    state: SurvivalCraftUiState,
    onAdd: () -> Unit,
    onRecordDrill: () -> Unit,
    onAddInventoryRow: (String) -> Unit,
) {
    if (state.equipment == null) return
    val newRowLabel = stringResource(R.string.sc_inventory_new_row)
    val label: String?
    val action: () -> Unit
    when (panel) {
        SchematicPanel.COMPONENTS -> {
            label = stringResource(R.string.sc_add_component)
            action = onAdd
        }

        SchematicPanel.INVENTORY -> {
            if (state.inventoryTemplate?.addable == true) {
                label = stringResource(R.string.sc_inventory_add_row)
                action = { onAddInventoryRow(newRowLabel) }
            } else {
                label = null
                action = {}
            }
        }

        SchematicPanel.DRILL_LOG -> {
            label = stringResource(R.string.sc_drill_record)
            action = onRecordDrill
        }

        SchematicPanel.TASKS -> {
            label = null
            action = {}
        }
    }
    if (label == null) return
    Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Dimens.SpacingL)) {
        Button(
            onClick = action,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Text(text = label, modifier = Modifier.padding(start = Dimens.SpacingS))
        }
    }
}

@Composable
private fun SchematicCard(state: SurvivalCraftUiState, onHotspot: (HotspotUi) -> Unit) {
    val definition = state.schematic ?: return
    val palette = rememberSchematicPalette()
    if (definition.shapes.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth().padding(Dimens.SpacingL)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(definition.aspect)
                .padding(Dimens.SpacingS),
        ) {
            SchematicDrawing(
                definition = definition,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
            SchematicOverlay(definition = definition, modifier = Modifier.fillMaxSize()) { place ->
                state.hotspots.forEach { hotspot ->
                    HotspotMarker(
                        hotspot = hotspot,
                        modifier = place(hotspot.hotspot.touchX, hotspot.hotspot.touchY),
                        onClick = { onHotspot(hotspot) },
                    )
                }
            }
        }
    }
}

/**
 * One hotspot: a 48dp touch target with a compact dot inside it, coloured by the matched child's
 * condition. A hotspot with no child shows the "add" state — a dashed ring and a plus.
 */
@Composable
private fun HotspotMarker(
    hotspot: HotspotUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = localised(hotspot.hotspot.labelEn, hotspot.hotspot.labelTr)
    val condition = conditionLabel(hotspot.condition)
    val description = if (hotspot.isMissing) {
        stringResource(R.string.sc_hotspot_add_cd, label)
    } else {
        stringResource(R.string.sc_hotspot_cd, label, hotspot.childTag.orEmpty(), condition)
    }
    val fill = if (hotspot.isMissing) Color.Transparent else ConditionColors.of(hotspot.condition)
    val outline = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(HOTSPOT_DOT)
                .background(color = fill, shape = CircleShape)
                .border(width = 2.dp, color = outline, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (hotspot.isMissing) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = outline,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MessageLine(message: CraftMessage?, onConsume: () -> Unit) {
    if (message == null) return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(MESSAGE_MILLIS)
        onConsume()
    }
    Text(
        text = stringResource(
            when (message) {
                CraftMessage.INVENTORY_SAVED -> R.string.sc_msg_inventory_saved
                CraftMessage.TASK_LOGGED -> R.string.sc_msg_task_logged
                CraftMessage.DRILL_LOGGED -> R.string.sc_msg_drill_logged
                CraftMessage.CHILD_LINKED -> R.string.sc_msg_child_linked
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
    )
}

@Composable
private fun panelLabel(panel: SchematicPanel): String = stringResource(
    when (panel) {
        SchematicPanel.COMPONENTS -> R.string.sc_tab_components
        SchematicPanel.INVENTORY -> R.string.sc_tab_inventory
        SchematicPanel.TASKS -> R.string.sc_tab_tasks
        SchematicPanel.DRILL_LOG -> R.string.sc_tab_drill
    },
)

private val HOTSPOT_DOT = 22.dp
private const val MESSAGE_MILLIS = 3_000L
