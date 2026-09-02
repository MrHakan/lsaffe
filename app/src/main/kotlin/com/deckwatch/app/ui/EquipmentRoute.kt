package com.deckwatch.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.feature.equipment.EquipmentDetailScreen
import com.deckwatch.feature.survivalcraft.SchematicScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which screen an equipment id opens. */
enum class EquipmentTarget { DETAIL, SCHEMATIC }

/**
 * One equipment destination, two possible screens — the app-level routing rule for §7.6.
 *
 * ### Why the rule lives here
 *
 * §7.6 gives lifeboats, rescue boats, liferafts, fixed gas systems, SCBA sets and fireman's outfits
 * a **dedicated schematic screen** with hotspots, an inventory and a task panel, and says the same
 * pattern is reused for the rest as *data*, not code. `feature-survivalcraft` implements exactly
 * that: `SchematicCatalogue` maps a `typeKey` to a bundled schematic definition.
 *
 * What it does not do — and must not, because it would invert the module graph — is decide *which*
 * of the two screens the app should push. `feature-equipment` exposes no "open schematic" hook
 * either. So the choice is made here, in the only module that depends on both, from the single fact
 * both sides agree on: the item's `typeKey`.
 *
 * ### The rule
 *
 * A destination resolves the item's `typeKey` once and then renders **the schematic** when that key
 * is in [SCHEMATIC_TYPE_KEYS], otherwise the ordinary detail screen. The key set mirrors the
 * `appliesToTypeKeys` of the six bundled schematics under
 * `feature-survivalcraft/src/main/assets/schematics/`, checked against the catalogue keys in
 * `data-seed/src/main/assets/seed/equipment_catalogue.json`.
 *
 * Duplicating that list here is a real cost — add a seventh schematic and this set needs the same
 * edit — but the alternatives are worse: a public accessor on the catalogue would make the app
 * depend on another module's asset-loading order at navigation time, and routing every item through
 * the schematic screen's fallback would replace the equipment detail screen for the other 116 types
 * in the catalogue. The set is small, it is a compile-time constant, and it is named in the
 * hand-off notes so the two stay in step.
 *
 * The switch happens **inside** one navigation destination rather than by choosing a route at the
 * call site: the caller (a marker tap, a Due row, a deep link) has only an id, and looking the type
 * up before navigating would put a database read on the tap. This way the destination is entered
 * immediately and resolves itself.
 */
@Composable
fun EquipmentDestination(
    equipmentId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EquipmentRouteViewModel = hiltViewModel(),
) {
    LaunchedEffect(equipmentId) { viewModel.resolve(equipmentId) }
    val target by viewModel.target.collectAsStateWithLifecycle()

    when (target) {
        // A single frame at most on a warm database; a spinner rather than a flash of the wrong
        // screen, which would then be replaced under the officer's thumb.
        null -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        EquipmentTarget.SCHEMATIC -> SchematicScreen(
            equipmentId = equipmentId,
            onBack = onBack,
            modifier = modifier,
        )

        EquipmentTarget.DETAIL -> EquipmentDetailScreen(
            equipmentId = equipmentId,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@HiltViewModel
class EquipmentRouteViewModel @Inject constructor(
    private val equipmentRepository: EquipmentRepository,
) : ViewModel() {

    private val targetState = MutableStateFlow<EquipmentTarget?>(null)
    val target: StateFlow<EquipmentTarget?> = targetState.asStateFlow()

    fun resolve(equipmentId: String) {
        viewModelScope.launch {
            val typeKey = runCatching { equipmentRepository.getEquipment(equipmentId)?.typeKey }.getOrNull()
            // An item that cannot be read at all still opens the detail screen, which shows its own
            // "not found" state — a spinner that never resolves would be the worse failure.
            targetState.value = targetFor(typeKey)
        }
    }

    companion object {
        /**
         * The survival-craft and system parents of §7.6, matching the `appliesToTypeKeys` of the
         * bundled schematics: lifeboat (three variants), rescue boat (two), liferaft (two), fixed
         * gas systems, SCBA set and fireman's outfit.
         */
        val SCHEMATIC_TYPE_KEYS: Set<String> = setOf(
            "LSA_LIFEBOAT_TOTALLY_ENCLOSED",
            "LSA_LIFEBOAT_PARTIALLY_ENCLOSED",
            "LSA_LIFEBOAT_FREEFALL",
            "LSA_RESCUE_BOAT",
            "LSA_FAST_RESCUE_BOAT",
            "LSA_LIFERAFT_THROWOVER",
            "LSA_LIFERAFT_DAVIT_LAUNCHED",
            "FFE_FIXED_CO2_SYSTEM",
            "FFE_DRY_POWDER_SYSTEM",
            "FFE_SCBA_SET",
            "FFE_FIREMANS_OUTFIT",
        )

        /** Pure, so the rule is testable without a repository. */
        fun targetFor(typeKey: String?): EquipmentTarget =
            if (typeKey != null && typeKey in SCHEMATIC_TYPE_KEYS) {
                EquipmentTarget.SCHEMATIC
            } else {
                EquipmentTarget.DETAIL
            }
    }
}
