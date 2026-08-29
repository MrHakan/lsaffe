package com.deckwatch.feature.inspection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

/** The work list itself — Tab 3's start destination (§12). */
@Serializable
internal object DueListRoute

/** Round history and the list-mode sweep — §6.7, §7.1 C. */
@Serializable
internal object RoundsRoute

/** Open and closed deficiencies — §6.8. */
@Serializable
internal object DeficienciesRoute

/**
 * Tab 3 — the cross-vessel work list (§12).
 *
 * The single entry point of `feature-inspection`, and deliberately **zero-argument callable** so the
 * app's `NavHost` keeps calling `DueScreen()`. Everything the feature owns beyond the work list —
 * rounds (§6.7) and deficiencies (§6.8) — hangs off a nested `NavHost` here rather than leaking
 * routes into the app module, so the feature's internal structure stays its own business.
 *
 * @param onOpenEquipment hand-off to `feature-equipment` for one item's full record (§7.4). Defaults
 *   to a no-op until the app wires it.
 * @param onExportHtml hand-off to `feature-report` for the HTML scope of §13.3. The payload is
 *   [DueExportRequest]; the clipboard export needs no host and works today.
 */
@Composable
fun DueScreen(
    modifier: Modifier = Modifier,
    onOpenEquipment: (String) -> Unit = {},
    onExportHtml: (DueExportRequest) -> Unit = {},
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = DueListRoute,
        modifier = modifier,
    ) {
        composable<DueListRoute> {
            DueWorkListScreen(
                onOpenEquipment = onOpenEquipment,
                onExportHtml = onExportHtml,
                onOpenRounds = { navController.navigate(RoundsRoute) },
                onOpenDeficiencies = { navController.navigate(DeficienciesRoute) },
            )
        }
        composable<RoundsRoute> {
            RoundsScreen(onBack = { navController.popBackStack() })
        }
        composable<DeficienciesRoute> {
            DeficienciesScreen(onBack = { navController.popBackStack() })
        }
    }
}
