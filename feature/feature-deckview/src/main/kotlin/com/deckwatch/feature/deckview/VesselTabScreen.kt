package com.deckwatch.feature.deckview

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
 * The body is LIST MODE (§7.1C): Deck → Zone → Equipment for the active vessel, which is the
 * shipped view until the 2.5D deck canvas of §7.1A exists. This screen is the frame around it —
 * the vessel selector, the overflow into the vessel/deck/category managers, and the equipment
 * FAB — so every journey the spec asks for is reachable from the tab an officer opens first:
 * add a vessel, add a deck, add equipment, open an item.
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

    VesselListModeScreen(
        modifier = modifier,
        onOpenEquipment = { openEquipmentId = it },
        onAddDeck = { overlay = VesselTabOverlay.DECKS },
        onAddVessel = { overlay = VesselTabOverlay.NEW_VESSEL },
        topBarActions = {
            VesselSelector()
            VesselTabMenu(
                onVessels = { overlay = VesselTabOverlay.VESSELS },
                onDecks = { overlay = VesselTabOverlay.DECKS },
                onCategories = { overlay = VesselTabOverlay.CATEGORIES },
            )
        },
        floatingActionButton = {
            // Equipment belongs to a vessel, so the FAB only appears once one is active.
            if (activeVessel != null) {
                ExtendedFloatingActionButton(
                    onClick = { overlay = VesselTabOverlay.ADD_EQUIPMENT },
                    text = { Text(stringResource(R.string.vessel_tab_add_equipment)) },
                    icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                )
            }
        },
    )

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

/** Which full-screen sub-screen the tab is showing, if any. */
internal enum class VesselTabOverlay { NONE, VESSELS, NEW_VESSEL, DECKS, CATEGORIES, ADD_EQUIPMENT }

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
