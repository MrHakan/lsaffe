package com.deckwatch.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.deckwatch.app.BuildConfig
import com.deckwatch.app.R
import com.deckwatch.core.datastore.UserPreferences
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.model.ThemeMode
import com.deckwatch.feature.deckview.VesselTabScreen
import com.deckwatch.feature.inspection.DueScreen
import com.deckwatch.feature.notes.NotesScreen
import com.deckwatch.feature.report.ImportScreen
import com.deckwatch.feature.report.ReportsScreen
import com.deckwatch.feature.settings.MoreScreen
import com.deckwatch.feature.settings.ThemeSchedule
import com.deckwatch.feature.settings.about.AboutScreen
import com.deckwatch.feature.settings.onboarding.OnboardingScreen
import com.deckwatch.feature.settings.settings.SettingsScreen
import com.deckwatch.feature.vessel.category.CategoryManagerScreen
import com.deckwatch.feature.vessel.deck.DeckManagerScreen
import com.deckwatch.feature.vessel.edit.VesselEditScreen
import com.deckwatch.feature.vessel.manager.VesselManagerScreen
import com.deckwatch.feature.vessel.zone.ZoneManagerScreen
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.time.LocalTime

// ---------------------------------------------------------------- routes

/** The four tabs of §5. */
@Serializable object NotesRoute

@Serializable object VesselRoute

@Serializable object DueRoute

@Serializable object MoreRoute

/** Detail destinations, pushed over a tab. Type-safe: the arguments are the route's own fields. */
@Serializable data class EquipmentDetailRoute(val equipmentId: String)

@Serializable object VesselManagerRoute

@Serializable data class VesselEditRoute(val vesselId: String? = null)

@Serializable data class DeckManagerRoute(val vesselId: String? = null)

@Serializable data class ZoneManagerRoute(val deckId: String)

@Serializable data class CategoryManagerRoute(val vesselId: String? = null)

@Serializable object ReportsRoute

@Serializable object ImportRoute

@Serializable object SettingsRoute

@Serializable object AboutRoute

/**
 * Which tab the shell should open on, and a nonce that makes a *repeat* request distinguishable
 * from the last one.
 *
 * Without the nonce, tapping the digest notification twice — the second time after the officer has
 * navigated away from the Due tab — would leave the value unchanged and the `LaunchedEffect` would
 * not re-fire, so the tap would appear to do nothing (§11.3 says it opens the Due tab, every time).
 * [nonce] 0 means "the app was launched normally"; the shell only navigates for a real request.
 */
@Immutable
data class StartDestination(val route: Any = VesselRoute, val nonce: Int = 0)

private data class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(NotesRoute, R.string.tab_notes, Icons.AutoMirrored.Filled.MenuBook),
    TopLevelDestination(VesselRoute, R.string.tab_vessel, Icons.Filled.Layers),
    TopLevelDestination(DueRoute, R.string.tab_due, Icons.Filled.EventAvailable),
    TopLevelDestination(MoreRoute, R.string.tab_more, Icons.Filled.MoreHoriz),
)

// ---------------------------------------------------------------- shell

/**
 * The app shell: theme, density, the onboarding gate, the four-tab bottom navigation of §5 and the
 * single `NavHost` every screen lives in.
 *
 * ### Theme and density
 *
 * Both come from the settings DataStore. The theme goes through
 * [ThemeSchedule][com.deckwatch.feature.settings.ThemeSchedule] so the §14 automatic schedule is
 * applied in one place — the same function the settings screen documents — and the resolved mode is
 * handed to `DeckWatchTheme`, which also publishes the density as `LocalListDensity` — the one
 * local every `DeckWatchListRow` in every module reads to pick 56dp or 72dp.
 *
 * ### The onboarding gate
 *
 * While `onboardingDone` is false the whole tab UI is replaced — no bottom bar, no NavHost — so
 * there is nothing to tap past. The flag is read from DataStore on every emission, so process death
 * mid-flow resumes the flow (§17.4).
 *
 * ### The bottom bar
 *
 * Shown only on the four tab destinations. A pushed detail screen (equipment, reports, settings…)
 * gets the full height and its own back arrow, which is what makes "back" mean one thing.
 *
 * @param start the tab to open on, used by the notification tap of §11.3.
 */
@Composable
fun DeckWatchApp(
    start: StartDestination = StartDestination(),
    viewModel: AppViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val settings = preferences ?: UserPreferences()
    val themeMode = rememberScheduledThemeMode(settings)

    // "Create my vessel" is chosen while there is no NavHost to navigate with — onboarding replaces
    // the whole shell. The choice is therefore remembered across the swap and acted on by the
    // scaffold as soon as it exists. rememberSaveable, so a process death between the tap and the
    // first frame does not swallow it (§17.4).
    var pendingCreateVessel by rememberSaveable { mutableStateOf(false) }

    DeckWatchTheme(themeMode = themeMode, density = settings.density) {
        if (preferences != null && !settings.onboardingDone) {
            // onDone needs no body: writing the flag flips the gate and replaces this screen.
            OnboardingScreen(
                onDone = {},
                onCreateVessel = { pendingCreateVessel = true },
            )
        } else {
            MainScaffold(
                start = start,
                openVesselEditor = pendingCreateVessel,
                onVesselEditorOpened = { pendingCreateVessel = false },
                onDisclaimerAccepted = viewModel::onDisclaimerAccepted,
            )
        }
    }
}

@Composable
private fun MainScaffold(
    start: StartDestination,
    openVesselEditor: Boolean = false,
    onVesselEditorOpened: () -> Unit = {},
    onDisclaimerAccepted: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val exportViewModel: DueExportViewModel = hiltViewModel()

    LaunchedEffect(start) {
        // The notification tap of §11.3 arrives as a start destination; switching here rather than
        // by changing the graph's start destination keeps the back stack sane.
        if (start.nonce > 0) navController.switchTab(start.route)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val exportFailed = stringResource(R.string.export_failed)

    LaunchedEffect(exportViewModel) {
        exportViewModel.shareIntents.collect { intent -> context.startActivity(intent) }
    }
    LaunchedEffect(exportViewModel) {
        exportViewModel.failures.collect { snackbarHostState.showSnackbar(exportFailed) }
    }

    LaunchedEffect(openVesselEditor) {
        if (openVesselEditor) {
            navController.navigate(VesselEditRoute())
            onVesselEditorOpened()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = topLevelDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(destination.route::class)
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTab(destination.route) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = VesselRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<NotesRoute> {
                NotesScreen(
                    // §8.2's "show my equipment": the Vessel tab is where the register lives, and
                    // its list mode is inside feature-deckview, so switching tab is the whole job.
                    onShowEquipmentForCard = { navController.switchTab(VesselRoute) },
                    // §17.6: the acknowledgement moves from the tab's own remembered state into
                    // DataStore, so it is asked once per install rather than once per process.
                    onDisclaimerAccepted = onDisclaimerAccepted,
                )
            }

            composable<VesselRoute> {
                VesselTabScreen(
                    onManageDecks = { navController.navigate(DeckManagerRoute()) },
                    onCreateVessel = { navController.navigate(VesselEditRoute()) },
                    onOpenEquipmentDetail = { id -> navController.navigate(EquipmentDetailRoute(id)) },
                )
            }

            composable<DueRoute> {
                DueScreen(
                    onOpenEquipment = { id -> navController.navigate(EquipmentDetailRoute(id)) },
                    onExportHtml = exportViewModel::exportAndShare,
                )
            }

            composable<MoreRoute> {
                MoreScreen(
                    onOpenVesselManager = { navController.navigate(VesselManagerRoute) },
                    onOpenDeckManager = { navController.navigate(DeckManagerRoute()) },
                    onOpenCategories = { navController.navigate(CategoryManagerRoute()) },
                    onOpenReports = { navController.navigate(ReportsRoute) },
                    onOpenImport = { navController.navigate(ImportRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenAbout = { navController.navigate(AboutRoute) },
                )
            }

            composable<EquipmentDetailRoute> { entry ->
                // The survival-craft rule of §7.6 is applied inside the destination — see
                // EquipmentDestination for why the choice is made here and not at the call site.
                EquipmentDestination(
                    equipmentId = entry.toRoute<EquipmentDetailRoute>().equipmentId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<VesselManagerRoute> {
                VesselManagerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenVessel = { navController.switchTab(VesselRoute) },
                    onAddVessel = { navController.navigate(VesselEditRoute()) },
                    onEditVessel = { id -> navController.navigate(VesselEditRoute(id)) },
                )
            }

            composable<VesselEditRoute> { entry ->
                VesselEditScreen(
                    vesselId = entry.toRoute<VesselEditRoute>().vesselId,
                    onDone = { navController.popBackStack() },
                )
            }

            composable<DeckManagerRoute> { entry ->
                DeckManagerScreen(
                    vesselId = entry.toRoute<DeckManagerRoute>().vesselId,
                    onBack = { navController.popBackStack() },
                    onOpenZones = { deckId -> navController.navigate(ZoneManagerRoute(deckId)) },
                )
            }

            composable<ZoneManagerRoute> { entry ->
                ZoneManagerScreen(
                    deckId = entry.toRoute<ZoneManagerRoute>().deckId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<CategoryManagerRoute> { entry ->
                CategoryManagerScreen(
                    vesselId = entry.toRoute<CategoryManagerRoute>().vesselId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<ReportsRoute> { ReportsScreen(onBack = { navController.popBackStack() }) }

            composable<ImportRoute> { ImportScreen(onBack = { navController.popBackStack() }) }

            composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }

            composable<AboutRoute> {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    versionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}

/** The standard tab switch: single top, state saved and restored, popped back to the start. */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The theme to render, re-evaluated as the clock crosses the §14 schedule boundary.
 *
 * Polling once a minute rather than setting an alarm: the app is only interested while it is on
 * screen, a minute of lag either side of 20:00 is invisible, and one coroutine that wakes 60 times
 * an hour costs less than an exact alarm the platform would rather not grant. With the schedule off
 * the loop never runs at all and `ThemeSchedule.resolve` ignores the hour entirely.
 */
@Composable
private fun rememberScheduledThemeMode(preferences: UserPreferences): ThemeMode {
    val hour by produceState(
        initialValue = LocalTime.now().hour,
        key1 = preferences.themeFollowSchedule,
    ) {
        while (preferences.themeFollowSchedule) {
            value = LocalTime.now().hour
            delay(THEME_TICK_MILLIS)
        }
    }
    return ThemeSchedule.resolve(preferences, hour)
}

private const val THEME_TICK_MILLIS = 60_000L
