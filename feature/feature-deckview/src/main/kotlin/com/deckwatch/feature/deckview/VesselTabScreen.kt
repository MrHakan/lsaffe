package com.deckwatch.feature.deckview

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.deckview.canvas.DeckCanvasScreen
import com.deckwatch.feature.equipment.AddEquipmentSheet
import com.deckwatch.feature.equipment.EquipmentBottomSheet
import com.deckwatch.feature.equipment.EquipmentDetailScreen
import com.deckwatch.feature.vessel.category.CategoryManagerScreen
import com.deckwatch.feature.vessel.deck.DeckManagerScreen
import com.deckwatch.feature.vessel.edit.VesselEditScreen
import com.deckwatch.feature.vessel.list.VesselListModeScreen
import com.deckwatch.feature.vessel.manager.VesselManagerScreen
import com.deckwatch.feature.vessel.selector.VesselSelector

/**
 * Tab 2 — the vessel.
 *
 * The body is one of two views of the same vessel, switched from the top bar:
 * - **LIST** (§7.1C): Deck → Zone → Equipment, the fastest way to find a known item;
 * - **PLAN** (§7.1A): the 2.5D deck canvas, where an item is placed and moved by touching the
 *   spot it occupies.
 *
 * This screen is the frame around whichever is showing — the vessel selector, the overflow into
 * the vessel/deck/category managers, and the equipment FAB — so every journey the spec asks for is
 * reachable from the tab an officer opens first: add a vessel, add a deck, add equipment, place it,
 * open it.
 *
 * Sub-screens open as full-screen dialogs rather than nav destinations. They are already complete
 * screens with their own `onBack`, and a dialog keeps the tab's own back stack (and the bottom
 * bar) intact underneath.
 */
@Composable
fun VesselTabScreen(
    modifier: Modifier = Modifier,
    viewModel: VesselTabViewModel = hiltViewModel(),
) {
    val activeVessel by viewModel.activeVessel.collectAsStateWithLifecycle()
    var overlay by rememberSaveable { mutableStateOf(VesselTabOverlay.NONE) }
    var openEquipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailEquipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    // Where the next item goes. Null/null is the unplaced inbox, which is what the FAB means.
    var addToDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var addToZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    // Only PLAN mode names a point; the list has no geometry to offer, so it leaves this null.
    var addToPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var mode by rememberSaveable { mutableStateOf(VesselTabMode.LIST) }

    val topBarActions: @Composable () -> Unit = {
        VesselSelector()
        ViewModeToggle(mode = mode, onToggle = { mode = it })
        VesselTabMenu(
            onVessels = { overlay = VesselTabOverlay.VESSELS },
            onDecks = { overlay = VesselTabOverlay.DECKS },
            onCategories = { overlay = VesselTabOverlay.CATEGORIES },
        )
    }
    val addEquipmentFab: @Composable () -> Unit = {
        // Equipment belongs to a vessel, so the FAB only appears once one is active.
        if (activeVessel != null) {
            ExtendedFloatingActionButton(
                onClick = {
                    // The FAB adds to the vessel, not to a place: the deck comes from the row's
                    // own button, from a long press on the canvas, or from the move picker.
                    addToDeckId = null
                    addToZoneId = null
                    addToPosition = null
                    overlay = VesselTabOverlay.ADD_EQUIPMENT
                },
                text = { Text(stringResource(R.string.vessel_tab_add_equipment)) },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
            )
        }
    }

    when (mode) {
        VesselTabMode.LIST -> VesselListModeScreen(
            modifier = modifier,
            onOpenEquipment = { openEquipmentId = it },
            onAddDeck = { overlay = VesselTabOverlay.DECKS },
            onAddVessel = { overlay = VesselTabOverlay.NEW_VESSEL },
            onAddEquipment = { deckId, zoneId ->
                addToDeckId = deckId
                addToZoneId = zoneId
                addToPosition = null
                overlay = VesselTabOverlay.ADD_EQUIPMENT
            },
            topBarActions = topBarActions,
            floatingActionButton = addEquipmentFab,
        )

        VesselTabMode.PLAN -> PlanModeScaffold(
            modifier = modifier,
            topBarActions = topBarActions,
            floatingActionButton = addEquipmentFab,
        ) {
            DeckCanvasScreen(
                onOpenEquipment = { openEquipmentId = it },
                onPlaceEquipment = { deckId, zoneId, posX, posY ->
                    addToDeckId = deckId
                    addToZoneId = zoneId
                    addToPosition = posX to posY
                    overlay = VesselTabOverlay.ADD_EQUIPMENT
                },
            )
        }
    }

    val vesselId = activeVessel?.id
    when (overlay) {
        VesselTabOverlay.NONE -> Unit

        VesselTabOverlay.VESSELS -> FullScreenOverlay(onDismiss = { overlay = VesselTabOverlay.NONE }) {
            VesselManagerScreen(onBack = { overlay = VesselTabOverlay.NONE })
        }

        VesselTabOverlay.NEW_VESSEL -> FullScreenOverlay(onDismiss = { overlay = VesselTabOverlay.NONE }) {
            VesselEditScreen(vesselId = null, onDone = { overlay = VesselTabOverlay.NONE })
        }

        VesselTabOverlay.DECKS -> FullScreenOverlay(onDismiss = { overlay = VesselTabOverlay.NONE }) {
            DeckManagerScreen(onBack = { overlay = VesselTabOverlay.NONE })
        }

        VesselTabOverlay.CATEGORIES -> FullScreenOverlay(onDismiss = { overlay = VesselTabOverlay.NONE }) {
            CategoryManagerScreen(onBack = { overlay = VesselTabOverlay.NONE })
        }

        // The FAB that opens this only exists while a vessel is active, so a null id here means
        // the vessel was deleted underneath the sheet: show nothing rather than an empty form.
        VesselTabOverlay.ADD_EQUIPMENT -> if (vesselId != null) {
            AddEquipmentSheet(
                vesselId = vesselId,
                deckId = addToDeckId,
                zoneId = addToZoneId,
                posX = addToPosition?.first ?: DEFAULT_POSITION,
                posY = addToPosition?.second ?: DEFAULT_POSITION,
                onDismiss = { overlay = VesselTabOverlay.NONE },
            )
        }
    }

    val peeking = openEquipmentId
    if (peeking != null) {
        EquipmentBottomSheet(
            equipmentId = peeking,
            onDismiss = { openEquipmentId = null },
            onOpenFullDetail = { id ->
                openEquipmentId = null
                detailEquipmentId = id
            },
        )
    }

    val detail = detailEquipmentId
    if (detail != null) {
        FullScreenOverlay(onDismiss = { detailEquipmentId = null }) {
            EquipmentDetailScreen(equipmentId = detail, onBack = { detailEquipmentId = null })
        }
    }
}

/** Which view of the vessel the tab is showing — §7.1. */
internal enum class VesselTabMode { LIST, PLAN }

/**
 * Centre of the deck: where an item goes when nobody said where. The list has no geometry, so
 * everything it adds starts here and is moved from the canvas or the move picker.
 */
private const val DEFAULT_POSITION = 0.5f

/** Which full-screen sub-screen the tab is showing, if any. */
internal enum class VesselTabOverlay { NONE, VESSELS, NEW_VESSEL, DECKS, CATEGORIES, ADD_EQUIPMENT }

/**
 * LIST ⇄ PLAN. One button, not a tab row: the two are the same data seen two ways, and the icon
 * shows what tapping it gives you rather than where you already are.
 */
@Composable
private fun ViewModeToggle(mode: VesselTabMode, onToggle: (VesselTabMode) -> Unit) {
    val goingToPlan = mode == VesselTabMode.LIST
    IconButton(
        onClick = { onToggle(if (goingToPlan) VesselTabMode.PLAN else VesselTabMode.LIST) },
        modifier = Modifier.size(Dimens.TouchTargetMin),
    ) {
        Icon(
            imageVector = if (goingToPlan) Icons.Filled.Layers else Icons.AutoMirrored.Filled.List,
            contentDescription = stringResource(
                if (goingToPlan) R.string.vessel_tab_show_plan else R.string.vessel_tab_show_list,
            ),
        )
    }
}

/**
 * The frame PLAN mode borrows from LIST mode: the same top bar and FAB, around the canvas.
 *
 * LIST mode brings its own scaffold (it owns the empty states and the preset picker), so rather
 * than pushing a scaffold down into both, the canvas gets a matching one here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanModeScaffold(
    topBarActions: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vessel_tab_plan_title)) },
                actions = { topBarActions() },
            )
        },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) { content() }
    }
}

@Composable
private fun VesselTabMenu(
    onVessels: () -> Unit,
    onDecks: () -> Unit,
    onCategories: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.size(Dimens.TouchTargetMin),
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.vessel_tab_menu),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MenuItem(R.string.vessel_tab_manage_vessels) {
            expanded = false
            onVessels()
        }
        MenuItem(R.string.vessel_tab_manage_decks) {
            expanded = false
            onDecks()
        }
        MenuItem(R.string.vessel_tab_manage_categories) {
            expanded = false
            onCategories()
        }
    }
}

@Composable
private fun MenuItem(@StringRes labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
        text = { Text(stringResource(labelRes)) },
        onClick = onClick,
    )
}

/** A complete screen shown over the tab, keeping the bottom bar and back stack underneath. */
@Composable
private fun FullScreenOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        content = content,
    )
}
