package com.deckwatch.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.ConfirmDialog
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.settings.backup.BackupArchive
import com.deckwatch.feature.settings.backup.BackupManager
import com.deckwatch.feature.settings.backup.PassphraseDialog
import com.deckwatch.feature.settings.backup.RestoreFailure
import com.deckwatch.feature.settings.settings.findActivity
import com.deckwatch.feature.vessel.selector.VesselSelector

/**
 * Tab 4 — MASTER_PROMPT §5 and DESIGN_OVERHAUL's IA table: *"sectioned list: Vessel manager ·
 * Reports & export · Backup · Settings · About"*, under the persistent vessel selector of §5.
 *
 * Callable with **no arguments** — the app shell calls `MoreScreen()`. Every navigation callback is
 * defaulted to a no-op so the module builds, previews and tests on its own; the app supplies the
 * real destinations.
 *
 * ### Why a plain sectioned list and no primary action
 *
 * DESIGN_OVERHAUL rule 1 allows a screen to have *no* primary action, and this one has none by
 * design: it is a launcher, not a task. Everything on it is a 56dp `DeckWatchListRow` with an icon
 * and one line of description, because an officer who is looking for "the thing that makes the
 * file I send the Chief Officer" should not have to know that it is called an export.
 *
 * ### The three long actions
 *
 * Loading the demo vessel, backing up and restoring all confirm first (rule 8), show progress while
 * they run (rule 10) and report through a snackbar. Only one runs at a time — see `MoreViewModel`.
 *
 * @param onShareLastReport optional override for the §13.6 shortcut; when left null the screen
 *   fires the chooser itself from the view model's intent.
 */
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onOpenVesselManager: () -> Unit = {},
    onOpenDeckManager: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onShareLastReport: (() -> Unit)? = null,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val resolver = rememberMoreMessages()

    var confirm by rememberSaveable { mutableStateOf<MoreConfirm?>(null) }
    var askBackupPassphrase by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(resolver(message))
        }
    }

    val createBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupArchive.MIME_TYPE),
    ) { uri -> uri?.let { viewModel.createBackup(it, PendingBackupPassphrase.consume()) } }

    val pickBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.restoreBackup(it) } }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::setAutoBackupFolder) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DeckWatchTopBar(
                title = stringResource(R.string.more_title),
                actions = { VesselSelector() },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.busy != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.showBackupNudge) {
                    item {
                        BackupNudgeBanner(
                            daysOfUse = state.daysOfUse,
                            onBackUp = { askBackupPassphrase = true },
                            onDismiss = viewModel::dismissBackupNudge,
                        )
                    }
                }

                item { SectionHeader(stringResource(R.string.more_section_vessel)) }
                item {
                    MoreRow(
                        Icons.Filled.DirectionsBoat,
                        R.string.more_vessel_manager,
                        R.string.more_vessel_manager_desc,
                        onOpenVesselManager,
                    )
                }
                item {
                    MoreRow(
                        Icons.Filled.Layers,
                        R.string.more_decks_zones,
                        R.string.more_decks_zones_desc,
                        onOpenDeckManager,
                    )
                }
                item {
                    MoreRow(
                        Icons.Filled.Category,
                        R.string.more_categories,
                        R.string.more_categories_desc,
                        onOpenCategories,
                    )
                }
                item { HorizontalDivider() }

                item { SectionHeader(stringResource(R.string.more_section_reports)) }
                item {
                    MoreRow(
                        Icons.Filled.Description,
                        R.string.more_reports,
                        R.string.more_reports_desc,
                        onOpenReports,
                    )
                }
                item {
                    MoreRow(
                        Icons.Filled.FileDownload,
                        R.string.more_import,
                        R.string.more_import_desc,
                        onOpenImport,
                    )
                }
                item {
                    MoreRow(
                        icon = Icons.Filled.Share,
                        titleRes = R.string.more_share_last,
                        descriptionRes = if (state.hasLastReport) {
                            R.string.more_share_last_desc
                        } else {
                            R.string.more_share_last_none
                        },
                        onClick = {
                            if (onShareLastReport != null) {
                                onShareLastReport()
                            } else {
                                viewModel.shareLastReport()?.let { intent ->
                                    (context.findActivity() ?: context).startActivity(intent)
                                }
                            }
                        },
                    )
                }
                item { HorizontalDivider() }

                item { SectionHeader(stringResource(R.string.more_section_data)) }
                item {
                    MoreRow(
                        icon = Icons.Filled.Backup,
                        titleRes = R.string.more_backup_now,
                        descriptionRes = R.string.more_backup_now_desc,
                        onClick = { askBackupPassphrase = true },
                        enabled = state.busy == null,
                        busy = state.busy == MoreBusy.BACKUP,
                    )
                }
                item {
                    MoreRow(
                        icon = Icons.Filled.Restore,
                        titleRes = R.string.more_restore,
                        descriptionRes = R.string.more_restore_desc,
                        onClick = { confirm = MoreConfirm.RESTORE },
                        enabled = state.busy == null,
                        busy = state.busy == MoreBusy.RESTORE,
                    )
                }
                item {
                    MoreRow(
                        icon = Icons.Filled.Science,
                        titleRes = if (state.demoInstalled) {
                            R.string.more_demo_remove
                        } else {
                            R.string.more_demo_load
                        },
                        descriptionRes = if (state.demoInstalled) {
                            R.string.more_demo_remove_desc
                        } else {
                            R.string.more_demo_load_desc
                        },
                        onClick = {
                            confirm = if (state.demoInstalled) MoreConfirm.DEMO_REMOVE else MoreConfirm.DEMO_INSTALL
                        },
                        enabled = state.busy == null,
                        busy = state.busy == MoreBusy.DEMO_INSTALL || state.busy == MoreBusy.DEMO_REMOVE,
                    )
                }
                item {
                    val folder = state.autoBackupFolder
                    DeckWatchListRow(
                        title = stringResource(R.string.more_auto_backup_folder),
                        subtitle = if (folder == null) {
                            stringResource(R.string.more_auto_backup_folder_none)
                        } else {
                            stringResource(
                                R.string.more_auto_backup_folder_set,
                                folder,
                                BackupManager.AUTO_BACKUP_KEEP,
                            )
                        },
                        leading = { RowIcon(Icons.Filled.Folder) },
                        onClick = { pickFolder.launch(null) },
                        trailing = if (folder == null) {
                            null
                        } else {
                            {
                                TextButton(onClick = viewModel::clearAutoBackupFolder) {
                                    Text(stringResource(R.string.backup_folder_clear))
                                }
                            }
                        },
                    )
                }
                item { HorizontalDivider() }

                item { SectionHeader(stringResource(R.string.more_section_app)) }
                item {
                    MoreRow(
                        Icons.Filled.Settings,
                        R.string.more_settings,
                        R.string.more_settings_desc,
                        onOpenSettings,
                    )
                }
                item {
                    MoreRow(Icons.Filled.Info, R.string.more_about, R.string.more_about_desc, onOpenAbout)
                }
                item {
                    // Keeps the last row clear of the bottom navigation bar.
                    Column(modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary)) {}
                }
            }
        }
    }

    when (confirm) {
        MoreConfirm.DEMO_INSTALL -> ConfirmDialog(
            title = stringResource(R.string.demo_install_title),
            body = stringResource(R.string.demo_install_body),
            confirmLabel = stringResource(R.string.demo_install_confirm),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = false,
            onConfirm = {
                confirm = null
                viewModel.installDemoVessel()
            },
            onDismiss = { confirm = null },
        )

        MoreConfirm.DEMO_REMOVE -> ConfirmDialog(
            title = stringResource(R.string.demo_remove_title),
            body = stringResource(R.string.demo_remove_body),
            confirmLabel = stringResource(R.string.demo_remove_confirm),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirm = null
                viewModel.removeDemoVessel()
            },
            onDismiss = { confirm = null },
        )

        MoreConfirm.RESTORE -> ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            body = stringResource(R.string.restore_confirm_body),
            confirmLabel = stringResource(R.string.restore_confirm_action),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = false,
            onConfirm = {
                confirm = null
                pickBackupFile.launch(arrayOf(BackupArchive.MIME_TYPE, MIME_ANY))
            },
            onDismiss = { confirm = null },
        )

        null -> Unit
    }

    if (askBackupPassphrase) {
        PassphraseDialog(
            title = stringResource(R.string.backup_passphrase_title),
            body = stringResource(R.string.backup_passphrase_body),
            confirmLabel = stringResource(R.string.backup_continue),
            requireConfirmation = true,
            allowEmpty = true,
            onDismiss = { askBackupPassphrase = false },
            onConfirm = { passphrase ->
                askBackupPassphrase = false
                PendingBackupPassphrase.hold(passphrase)
                createBackupFile.launch(state.suggestedBackupFileName)
            },
        )
    }

    if (state.restoreNeedsPassphrase) {
        PassphraseDialog(
            title = stringResource(R.string.restore_title),
            body = stringResource(R.string.backup_passphrase_required),
            confirmLabel = stringResource(R.string.restore_confirm_action),
            requireConfirmation = false,
            allowEmpty = false,
            onDismiss = viewModel::cancelRestore,
            onConfirm = { passphrase ->
                viewModel.retryRestoreWithPassphrase(passphrase ?: CharArray(0))
            },
        )
    }
}

/** Which confirmation is open, if any — kept saveable so rotation does not lose the question. */
private enum class MoreConfirm { DEMO_INSTALL, DEMO_REMOVE, RESTORE }

/**
 * The passphrase chosen in the dialog, held between the dialog closing and the SAF file picker
 * returning.
 *
 * A `CharArray` cannot go through `rememberSaveable` without becoming a `String` in the saved-state
 * bundle — which is exactly what a passphrase must not be — so it is held in one process-lifetime
 * slot and consumed once. If the process dies while the SAF picker is up, the slot comes back empty
 * and the backup is written unencrypted rather than with a passphrase the officer did not confirm;
 * that is the safe direction, because the alternative is a file they cannot open.
 */
private object PendingBackupPassphrase {
    private var value: CharArray? = null

    fun hold(passphrase: CharArray?) {
        value?.fill('\u0000')
        value = passphrase
    }

    fun consume(): CharArray? {
        val held = value
        value = null
        return held
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    titleRes: Int,
    descriptionRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    DeckWatchListRow(
        title = stringResource(titleRes),
        subtitle = stringResource(descriptionRes),
        leading = { RowIcon(icon) },
        onClick = if (enabled) onClick else null,
        trailing = if (busy) {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(PROGRESS_SIZE),
                    strokeWidth = PROGRESS_STROKE,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(ROW_ICON_SIZE),
    )
}

/** §18's day-30 prompt, as a dismissible banner at the top of the tab. */
@Composable
private fun BackupNudgeBanner(daysOfUse: Int, onBackUp: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.SpacingL),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimens.SpacingM),
                )
                Text(
                    text = stringResource(R.string.more_nudge_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.more_nudge_body, daysOfUse),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimens.SpacingS),
            )
            Row(modifier = Modifier.padding(top = Dimens.SpacingS)) {
                TextButton(onClick = onBackUp, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                    Text(stringResource(R.string.more_nudge_action))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                    Text(stringResource(R.string.more_nudge_dismiss))
                }
            }
        }
    }
}

/** Turns a [MoreMessage] into the localised snackbar text — C8 keeps the words in `res/`. */
@Composable
private fun rememberMoreMessages(): (MoreMessage) -> String {
    val demoInstalled = stringResource(R.string.demo_installed)
    val demoRemoved = stringResource(R.string.demo_removed)
    val demoFailed = stringResource(R.string.demo_failed)
    val backupEmpty = stringResource(R.string.backup_empty)
    val folderDenied = stringResource(R.string.backup_folder_denied)
    val folderCleared = stringResource(R.string.backup_folder_cleared)
    val noLastReport = stringResource(R.string.more_share_last_none)
    val passphraseWrong = stringResource(R.string.backup_passphrase_wrong)
    val unreadable = stringResource(R.string.restore_unreadable)
    val schema = stringResource(R.string.restore_schema)
    val context = LocalContext.current
    return remember(context) {
        { message ->
            when (message) {
                MoreMessage.DemoInstalled -> demoInstalled
                MoreMessage.DemoRemoved -> demoRemoved
                MoreMessage.DemoFailed -> demoFailed
                MoreMessage.BackupEmpty -> backupEmpty
                MoreMessage.FolderDenied -> folderDenied
                MoreMessage.FolderCleared -> folderCleared
                MoreMessage.NoLastReport -> noLastReport
                is MoreMessage.FolderSet -> context.getString(R.string.backup_folder_set, message.name)
                is MoreMessage.BackupDone -> context.getString(
                    R.string.backup_done,
                    message.vessels,
                    message.photos,
                    formatBytes(message.bytes),
                )

                is MoreMessage.BackupFailed -> context.getString(R.string.backup_failed, message.detail)
                is MoreMessage.RestoreDone ->
                    context.getString(R.string.restore_done, message.written, message.photos)

                is MoreMessage.RestoreRejected -> context.getString(R.string.restore_rejected, message.detail)
                is MoreMessage.RestorePartial ->
                    context.getString(R.string.restore_partial, message.detail, message.unrecoverable)

                is MoreMessage.RestoreUnreadable -> when (message.reason) {
                    RestoreFailure.PASSPHRASE_WRONG, RestoreFailure.PASSPHRASE_REQUIRED -> passphraseWrong
                    RestoreFailure.SCHEMA_MISMATCH -> schema
                    else -> unreadable
                }
            }
        }
    }
}

/** Human file size for the backup snackbar. Binary units, because that is what a file manager shows. */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= MEGABYTE -> "%.1f MB".format(bytes.toDouble() / MEGABYTE)
    bytes >= KILOBYTE -> "%.0f kB".format(bytes.toDouble() / KILOBYTE)
    else -> "$bytes B"
}

/**
 * SAF's `OpenDocument` filters by MIME, and a `.dwbackup` has no registered type — many providers
 * report it as `application/octet-stream`, some as nothing at all. Offering the wildcard alongside
 * the real type is what stops the picker showing an empty folder.
 */
private const val MIME_ANY = "*/*"

private const val KILOBYTE = 1024L
private const val MEGABYTE = KILOBYTE * 1024

private val ROW_ICON_SIZE = 24.dp
private val PROGRESS_SIZE = 20.dp
private val PROGRESS_STROKE = 2.dp
