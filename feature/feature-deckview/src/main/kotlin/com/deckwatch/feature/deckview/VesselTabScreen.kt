package com.deckwatch.feature.deckview

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.components.ConditionLabels
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.feature.deckview.components.DeckCompass
import com.deckwatch.feature.deckview.components.DeckModeControl
import com.deckwatch.feature.deckview.components.PresetPickerRow
import com.deckwatch.feature.deckview.components.ViewSettingsSheet
import com.deckwatch.feature.deckview.gesture.DeckGestureCallbacks
import com.deckwatch.feature.deckview.gesture.deckGestures
import com.deckwatch.feature.deckview.model.DeckNode
import com.deckwatch.feature.deckview.render.DeckLayoutHolder
import com.deckwatch.feature.deckview.render.DeckRenderDefaults
import com.deckwatch.feature.deckview.render.DeckSemanticNode
import com.deckwatch.feature.deckview.render.DeckSemanticsOverlay
import com.deckwatch.feature.deckview.render.DeckSpine
import com.deckwatch.feature.deckview.render.DeckStackCanvas
import com.deckwatch.feature.deckview.render.DeckVisibility
import com.deckwatch.feature.deckview.render.StackLayout
import com.deckwatch.feature.deckview.render.rememberDeckTransformState
import com.deckwatch.feature.deckview.render.spellTag
import com.deckwatch.feature.equipment.AddEquipmentSheet
import com.deckwatch.feature.equipment.EquipmentBottomSheet
import com.deckwatch.feature.vessel.list.VesselListModeScreen
import com.deckwatch.feature.vessel.selector.VesselSelector
import kotlinx.coroutines.launch

/** Where the "add equipment here" flow of §7.5 was invoked. */
private data class AddTarget(val deckId: String, val zoneId: String?, val posX: Float, val posY: Float)

/**
 * Tab 2 — the 2.5D deck stack, the signature feature (§7).
 *
 * One screen, three modes (§7.1): the isometric stack, one deck filling the screen, and the
 * graphics-free list that `feature-vessel` owns. The canvas, its gesture layer and its accessibility
 * tree live in this module; the equipment sheet (§7.3/§7.4), the add flow (§7.5), the vessel selector
 * and list mode are reused from the feature modules that own them.
 *
 * Every parameter is defaulted so the app can keep calling `VesselTabScreen()`.
 *
 * @param onManageDecks opens `DeckManagerScreen` — wired by the app.
 * @param onCreateVessel opens the vessel editor from the "no vessel yet" empty state.
 * @param onOpenEquipmentDetail pushes the full equipment record (§7.4 "full" stage).
 */
@Composable
@Suppress("LongMethod") // The tab is one screen: chrome, canvas, spine and three sheets.
fun VesselTabScreen(
    modifier: Modifier = Modifier,
    onManageDecks: () -> Unit = {},
    onCreateVessel: () -> Unit = {},
    onOpenEquipmentDetail: (String) -> Unit = {},
    viewModel: DeckViewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transform = rememberDeckTransformState()
    val layoutHolder = remember { DeckLayoutHolder() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduceMotion = rememberReducedMotion()

    var selectedEquipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var addTarget by remember { mutableStateOf<AddTarget?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    val deckMode = state.mode == DeckViewMode.DECK
    val sheetEquipmentId = state.sweep?.currentEquipmentId ?: selectedEquipmentId
    val sweepTitle = stringResource(
        R.string.deckview_sweep_round_title,
        state.activeDeck?.name.orEmpty(),
    )

    // §7.1B: flat and isometric are one animated float, sprung unless the system says no motion.
    LaunchedEffect(state.effectiveAngleDeg, reduceMotion) {
        val target = state.effectiveAngleDeg
        if (reduceMotion) {
            transform.angleDeg = target
        } else {
            animate(
                initialValue = transform.angleDeg,
                targetValue = target,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
            ) { value, _ -> transform.angleDeg = value }
        }
    }

    fun flyToDeck(deck: DeckNode, targetZoom: Float) {
        scope.launch {
            // In deck mode the stack has collapsed to one deck, so the camera only has to unwind
            // whatever pan the officer left behind.
            val targetPan = if (deckMode) {
                Offset.Zero
            } else {
                val levelStep = with(density) { DeckRenderDefaults.DeckHeight.toPx() } *
                    targetZoom * transform.spread
                StackLayout.panToCentre(deck.levelZ, state.model.decks.size, levelStep)
            }
            transform.flyTo(
                targetPan = targetPan,
                targetZoom = targetZoom,
                deckMode = deckMode,
                instant = reduceMotion,
            )
        }
    }

    // Switching into deck mode re-centres: the officer asked for this deck, not for wherever the
    // stack happened to be panned to.
    LaunchedEffect(deckMode, state.focusedDeckId) {
        if (deckMode) transform.pan = Offset.Zero
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            DeckWatchTopBar(
                title = stringResource(R.string.deckview_title),
                subtitle = state.vessel?.name,
                actions = {
                    VesselSelector()
                    IconButton(
                        onClick = {
                            val deck = state.activeDeck
                            when {
                                state.sweep != null -> viewModel.finishSweep()
                                deck != null -> viewModel.startSweep(deck.deckId, sweepTitle)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = stringResource(
                                if (state.sweep != null) {
                                    R.string.deckview_sweep_stop
                                } else {
                                    R.string.deckview_sweep_start
                                },
                            ),
                            tint = if (state.sweep != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.deckview_more_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.deckview_view_settings)) },
                                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    settingsOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.deckview_manage_decks)) },
                                leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    onManageDecks()
                                },
                            )
                            if (deckMode) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (state.flatInDeckMode) {
                                                    R.string.deckview_view_isometric
                                                } else {
                                                    R.string.deckview_view_flat
                                                },
                                            ),
                                        )
                                    },
                                    onClick = {
                                        overflowOpen = false
                                        viewModel.toggleFlat()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val deck = state.activeDeck
            if (deck != null && state.mode != DeckViewMode.LIST) {
                ExtendedFloatingActionButton(
                    onClick = {
                        addTarget = AddTarget(deck.deckId, null, PLAN_CENTRE, PLAN_CENTRE)
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.deckview_add_equipment)) },
                    modifier = Modifier.padding(bottom = Dimens.SpacingS),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.hasVessel) {
                DeckModeControl(
                    mode = state.mode,
                    onModeChange = viewModel::setMode,
                    modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
                )
            }
            when {
                !state.hasVessel && !state.isLoading -> EmptyState(
                    icon = Icons.Filled.Layers,
                    title = stringResource(R.string.deckview_no_vessel_title),
                    body = stringResource(R.string.deckview_no_vessel_body),
                    actionLabel = stringResource(R.string.deckview_create_vessel),
                    onAction = onCreateVessel,
                )

                state.mode == DeckViewMode.LIST -> VesselListModeScreen(
                    onOpenEquipment = { selectedEquipmentId = it },
                    onAddDeck = onManageDecks,
                )

                state.hasNoDecks -> DeckPresetEmptyState(
                    presets = state.presets,
                    onPick = { preset, name -> viewModel.createDeckFromPreset(preset, name) },
                )

                else -> DeckCanvasArea(
                    state = state,
                    transform = transform,
                    layoutHolder = layoutHolder,
                    deckMode = deckMode,
                    reduceMotion = reduceMotion,
                    selectedEquipmentId = sheetEquipmentId,
                    callbacks = DeckGestureCallbacks(
                        onTapMarker = { equipmentId, _ -> selectedEquipmentId = equipmentId },
                        onTapDeck = { deckId ->
                            when {
                                deckMode -> selectedEquipmentId = null
                                state.focusedDeckId == deckId -> viewModel.enterDeckMode(deckId)
                                else -> viewModel.focusDeck(deckId)
                            }
                        },
                        onTapEmpty = {
                            selectedEquipmentId = null
                            viewModel.focusDeck(null)
                        },
                        onZoomToFit = { deckId ->
                            val deck = state.model.deck(deckId) ?: state.activeDeck
                            if (deck != null) flyToDeck(deck, FIT_ZOOM) else transform.reset()
                        },
                        // The pick-up haptic is fired by the gesture layer itself, the moment the
                        // long press lands, so there is nothing to do here.
                        onMarkerDropped = { equipmentId, deckId, x, y, inside ->
                            if (inside) {
                                viewModel.moveEquipment(equipmentId, deckId, x, y)
                            } else {
                                scope.launch {
                                    transform.shake(
                                        amplitudePx = with(density) {
                                            DeckRenderDefaults.ShakeAmplitude.toPx()
                                        },
                                        instant = reduceMotion,
                                    )
                                }
                            }
                        },
                        onAddEquipmentAt = { deckId, x, y ->
                            addTarget = AddTarget(deckId, viewModel.zoneAt(deckId, x, y), x, y)
                        },
                    ),
                    onSpineSelect = { deck ->
                        viewModel.focusDeck(deck.deckId)
                        flyToDeck(deck, transform.zoom)
                    },
                    onStepDeck = { step ->
                        val current = state.activeDeck
                        val next = state.model.decks.firstOrNull {
                            it.levelZ == (current?.levelZ ?: 0) + step
                        }
                        if (next != null) {
                            if (deckMode) viewModel.enterDeckMode(next.deckId) else viewModel.focusDeck(next.deckId)
                            flyToDeck(next, transform.zoom)
                        }
                    },
                    onOpenEquipment = { selectedEquipmentId = it },
                )
            }
        }
    }

    if (sheetEquipmentId != null) {
        EquipmentBottomSheet(
            equipmentId = sheetEquipmentId,
            onDismiss = {
                selectedEquipmentId = null
                if (state.sweep != null) viewModel.finishSweep()
            },
            onGraded = viewModel::onSweepGraded,
            onOpenFullDetail = onOpenEquipmentDetail,
        )
    }

    addTarget?.let { target ->
        val vesselId = state.vessel?.id
        if (vesselId != null) {
            AddEquipmentSheet(
                vesselId = vesselId,
                onDismiss = { addTarget = null },
                deckId = target.deckId,
                zoneId = target.zoneId,
                posX = target.posX,
                posY = target.posY,
                onCreated = { ids -> selectedEquipmentId = ids.lastOrNull() },
            )
        }
    }

    if (settingsOpen) {
        ViewSettingsSheet(
            transform = transform,
            isoAngleDeg = state.isoAngleDeg,
            gridSnapEnabled = state.gridSnapEnabled,
            showGrid = state.showGrid,
            onIsoAngleChange = viewModel::setIsoAngle,
            onGridSnapChange = viewModel::setGridSnap,
            onShowGridChange = { viewModel.toggleGrid() },
            onDismiss = { settingsOpen = false },
        )
    }
}

/** The canvas, its gesture layer, its accessibility tree and the deck spine. */
@Composable
@Suppress("LongParameterList") // The rendering surface's inputs; all come from one caller.
private fun DeckCanvasArea(
    state: DeckViewUiState,
    transform: com.deckwatch.feature.deckview.render.DeckTransformState,
    layoutHolder: DeckLayoutHolder,
    deckMode: Boolean,
    reduceMotion: Boolean,
    selectedEquipmentId: String?,
    callbacks: DeckGestureCallbacks,
    onSpineSelect: (DeckNode) -> Unit,
    onStepDeck: (Int) -> Unit,
    onOpenEquipment: (String) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val hitRadiusPx = with(density) { DeckRenderDefaults.MarkerHitRadius.toPx() }
    val activeDeckId = state.activeDeck?.deckId
    val interactive = remember(state.model, state.focusedDeckId, deckMode, activeDeckId) {
        DeckVisibility.visibleDecks(state.model, deckMode, activeDeckId, null)
            .filter { DeckVisibility.isInteractive(it, state.focusedDeckId, state.model) }
    }
    // The pointer-input block is only restarted when its keys change, so the set of interactive
    // decks is read through an updated state rather than captured — focusing a deck must not need
    // the gesture detector to be torn down and rebuilt mid-touch.
    val interactiveDecks = rememberUpdatedState(interactive)
    val conditionLabels = rememberConditionLabels()
    val deckNodes = state.model.decks.map { deck ->
        DeckSemanticNode(
            id = deck.deckId,
            levelZ = deck.levelZ,
            planX = PLAN_CENTRE,
            planY = PLAN_CENTRE,
            description = stringResource(
                R.string.deckview_a11y_deck,
                deck.name,
                deck.markers.size,
                deck.overdueCount,
            ),
            clickLabel = stringResource(R.string.deckview_a11y_focus_deck),
            onClick = { onSpineSelect(deck) },
        )
    }
    val markerDeck = state.model.deck(activeDeckId)
    val markerNodes = markerDeck?.markers.orEmpty().map { marker ->
        DeckSemanticNode(
            id = marker.equipmentId,
            levelZ = markerDeck?.levelZ ?: 0,
            planX = marker.position.x,
            planY = marker.position.y,
            description = stringResource(
                R.string.deckview_a11y_marker,
                marker.typeName,
                spellTag(marker.tag),
                conditionLabels.of(marker.condition),
                marker.nextDueDate?.let(Dates::formatIso)
                    ?: stringResource(R.string.deckview_a11y_no_due_date),
            ),
            clickLabel = stringResource(R.string.deckview_a11y_open_equipment),
            onClick = { onOpenEquipment(marker.equipmentId) },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DeckStackCanvas(
            model = state.model,
            transform = transform,
            layoutHolder = layoutHolder,
            modifier = Modifier
                .fillMaxSize()
                .deckGestures(
                    key = state.model,
                    transform = transform,
                    layoutHolder = layoutHolder,
                    interactiveDecks = { interactiveDecks.value },
                    deckMode = deckMode,
                    gridSnapEnabled = state.gridSnapEnabled,
                    hitRadiusPx = hitRadiusPx,
                    performHaptic = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    callbacks = callbacks,
                ),
            deckMode = deckMode,
            activeDeckId = activeDeckId,
            focusedDeckId = state.focusedDeckId,
            selectedEquipmentId = selectedEquipmentId,
            showGrid = state.showGrid,
            reduceMotion = reduceMotion,
        )
        DeckSemanticsOverlay(
            model = state.model,
            transform = transform,
            deckMode = deckMode,
            deckNodes = deckNodes,
            markerNodes = markerNodes,
            modifier = Modifier.fillMaxSize(),
        )
        DeckSpine(
            decks = state.model.decksTopFirst,
            focusedDeckId = state.focusedDeckId,
            onSelect = { deckId -> state.model.deck(deckId)?.let(onSpineSelect) },
            onSwipeToDeckAbove = { onStepDeck(1) },
            onSwipeToDeckBelow = { onStepDeck(-1) },
            pillDescription = { deck ->
                stringResource(R.string.deckview_a11y_deck, deck.name, deck.markers.size, deck.overdueCount)
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = Dimens.SpacingXs),
        )
        // Below the stack and above the tab bar: the strip is a control for the thing directly
        // above it, and at the bottom of the screen it is the part of the canvas a thumb reaches
        // without covering the deck it is turning.
        DeckCompass(
            yawDeg = { transform.yawDeg },
            onTurn = transform::yawBy,
            onLevel = transform::levelYaw,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Dimens.SpacingS),
        )
    }
}

/** The first-run state of §14: one sentence and the six deck presets, visible immediately. */
@Composable
private fun DeckPresetEmptyState(
    presets: List<PlanPreset>,
    onPick: (PlanPreset, String) -> Unit,
) {
    val context = LocalContext.current
    EmptyState(
        icon = Icons.Filled.Layers,
        title = stringResource(R.string.deckview_no_decks_title),
        body = stringResource(R.string.deckview_empty_hint),
        extraContent = {
            PresetPickerRow(
                presets = presets,
                presetLabel = { presetDisplayName(context, it) },
                onPick = { preset -> onPick(preset, presetDisplayName(context, preset)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun rememberConditionLabels(): ConditionLabels = ConditionLabels(
    good = stringResource(R.string.deckview_condition_good),
    acceptable = stringResource(R.string.deckview_condition_acceptable),
    monitor = stringResource(R.string.deckview_condition_monitor),
    defective = stringResource(R.string.deckview_condition_defective),
    outOfService = stringResource(R.string.deckview_condition_out_of_service),
    notChecked = stringResource(R.string.deckview_condition_not_checked),
)

/**
 * §14: respect `Settings.Global.ANIMATOR_DURATION_SCALE`. A scale of 0 means the user has turned
 * animations off system-wide, so the deck fan, the fly-to and the marker pulse all snap instead.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** Preset names are bilingual seed content (§6.3), not UI chrome. */
private fun presetDisplayName(context: android.content.Context, preset: PlanPreset): String {
    val language = context.resources.configuration.locales[0].language
    return if (language == TURKISH_LANGUAGE) preset.nameTr else preset.nameEn
}

private const val TURKISH_LANGUAGE = "tr"
private const val PLAN_CENTRE = 0.5f

/** Double-tap zoom-to-fit target: enough that a deck fills the viewport comfortably. */
private const val FIT_ZOOM = 1.6f
