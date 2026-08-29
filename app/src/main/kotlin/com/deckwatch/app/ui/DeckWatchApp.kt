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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deckwatch.app.R
import com.deckwatch.feature.deckview.VesselTabScreen
import com.deckwatch.feature.equipment.EquipmentDetailScreen
import com.deckwatch.feature.inspection.DueScreen
import com.deckwatch.feature.notes.NotesScreen
import com.deckwatch.app.reminders.ReminderScheduler
import com.deckwatch.feature.settings.MoreScreen
import kotlinx.serialization.Serializable

@Serializable object NotesRoute

@Serializable object VesselRoute

@Serializable object DueRoute

@Serializable object MoreRoute

private data class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

@Composable
fun DeckWatchApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var openEquipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    val destinations = listOf(
        TopLevelDestination(NotesRoute, R.string.tab_notes, Icons.AutoMirrored.Filled.MenuBook),
        TopLevelDestination(VesselRoute, R.string.tab_vessel, Icons.Filled.Layers),
        TopLevelDestination(DueRoute, R.string.tab_due, Icons.Filled.EventAvailable),
        TopLevelDestination(MoreRoute, R.string.tab_more, Icons.Filled.MoreHoriz),
    )

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route::class.qualifiedName
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = VesselRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<NotesRoute> { NotesScreen() }
            composable<VesselRoute> { VesselTabScreen() }
            // The work list names an equipment id; the record itself belongs to feature-equipment,
            // so the hand-off between the two features is wired here rather than in either of them.
            composable<DueRoute> { DueScreen(onOpenEquipment = { openEquipmentId = it }) }
            composable<MoreRoute> {
                // Settings live in feature-settings; arming the queue is the app module's job,
                // because WorkManager and the reminder workers are here.
                MoreScreen(
                    onRemindersChanged = { enabled, hour, minute ->
                        ReminderScheduler.apply(context, enabled, hour, minute)
                    },
                )
            }
        }
    }

    val equipmentId = openEquipmentId
    if (equipmentId != null) {
        Dialog(
            onDismissRequest = { openEquipmentId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            EquipmentDetailScreen(equipmentId = equipmentId, onBack = { openEquipmentId = null })
        }
    }
}
