package com.deckwatch.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.datastore.UserPreferences
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.repository.DemoVessel
import com.deckwatch.feature.report.ReportFileStore
import com.deckwatch.feature.settings.backup.AutoBackupScheduler
import com.deckwatch.feature.settings.backup.BackupFolder
import com.deckwatch.feature.settings.backup.BackupManager
import com.deckwatch.feature.settings.backup.BackupOutcome
import com.deckwatch.feature.settings.backup.RestoreFailure
import com.deckwatch.feature.settings.backup.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the More tab is busy doing, so exactly one long action can be in flight at a time. */
enum class MoreBusy { DEMO_INSTALL, DEMO_REMOVE, BACKUP, RESTORE }

/** Everything `MoreScreen` draws that is not a static row. */
data class MoreUiState(
    val demoInstalled: Boolean = false,
    val busy: MoreBusy? = null,
    /** §18's day-30 prompt: no backup ever taken and 30 days of use elapsed. */
    val showBackupNudge: Boolean = false,
    val daysOfUse: Int = 0,
    /** Display name of the SAF folder for weekly backups, or null when none is set. */
    val autoBackupFolder: String? = null,
    val hasLastReport: Boolean = false,
    /** Suggested file name for `ACTION_CREATE_DOCUMENT` — §13.6's naming convention. */
    val suggestedBackupFileName: String = "",
    /** Set when a restore hit an encrypted file and needs the passphrase. */
    val restoreNeedsPassphrase: Boolean = false,
    /**
     * The officer tapped "Not now" on the day-30 banner. Held in the view model, not in DataStore:
     * §18 asks for a prompt on the thirtieth day, and a dismissal that outlived the risk would
     * defeat it. The banner returns on the next cold start and stops for good once a backup lands.
     */
    val nudgeDismissed: Boolean = false,
)

/** One-shot results the screen turns into snackbars. Typed, so the strings stay in `res/`. */
sealed interface MoreMessage {
    data object DemoInstalled : MoreMessage
    data object DemoRemoved : MoreMessage
    data object DemoFailed : MoreMessage
    data class BackupDone(val vessels: Int, val photos: Int, val bytes: Long) : MoreMessage
    data object BackupEmpty : MoreMessage
    data class BackupFailed(val detail: String) : MoreMessage
    data class RestoreDone(val written: Int, val photos: Int) : MoreMessage
    data class RestoreRejected(val detail: String) : MoreMessage
    data class RestorePartial(val detail: String, val unrecoverable: Int) : MoreMessage
    data class RestoreUnreadable(val reason: RestoreFailure) : MoreMessage
    data class FolderSet(val name: String) : MoreMessage
    data object FolderDenied : MoreMessage
    data object FolderCleared : MoreMessage
    data object NoLastReport : MoreMessage
}

/**
 * The More tab's state — demo vessel, backup, restore, the automatic-backup folder and the §18
 * day-30 nudge.
 *
 * ### One action at a time
 *
 * [MoreUiState.busy] is a single nullable value, not a set of flags. Installing the demo vessel
 * while a restore is writing would have two merge paths racing over the same rows, and there is no
 * transaction spanning them (see `ImportApplier`). The screen disables the other rows while one is
 * running, which is cheaper to reason about than making them safe to interleave.
 *
 * ### Messages, not strings
 *
 * Results come back as [MoreMessage] values and the composable resolves them through
 * `stringResource`. A view model that formats user-visible text hard-codes a language (C8) and
 * makes every message untestable without Robolectric.
 */
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val demoVessel: DemoVessel,
    private val backupManager: BackupManager,
    private val backupFolder: BackupFolder,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val reportFileStore: ReportFileStore,
    private val vesselRepository: VesselRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val state = MutableStateFlow(MoreUiState())
    val uiState: StateFlow<MoreUiState> = state.asStateFlow()

    private val messageFlow = MutableSharedFlow<MoreMessage>(
        replay = 0,
        extraBufferCapacity = MESSAGE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: Flow<MoreMessage> = messageFlow.asSharedFlow()

    /** The file the passphrase dialog is about to unlock, held between the two steps. */
    private var pendingRestoreUri: Uri? = null

    init {
        refresh()
        viewModelScope.launch {
            preferences.userPreferences.collect { prefs -> applyPreferences(prefs) }
        }
    }

    /**
     * Re-read the things that are not flows: the demo's presence, the folder, the last report.
     *
     * On the IO dispatcher throughout — resolving a SAF tree's display name and checking a
     * persisted URI permission are both binder calls into a content provider, and §17.2 keeps
     * blocking calls off the main dispatcher.
     */
    fun refresh() {
        viewModelScope.launch(dispatchers.io) {
            val installed = runCatching { demoVessel.isInstalled() }.getOrDefault(false)
            val activeName = runCatching { vesselRepository.observeActiveVessel().first()?.name }.getOrNull()
            state.update {
                it.copy(
                    demoInstalled = installed,
                    autoBackupFolder = backupFolder.displayName(),
                    hasLastReport = reportFileStore.lastReport() != null,
                    suggestedBackupFileName = BackupManager.manualBackupFileName(activeName),
                )
            }
        }
    }

    private fun applyPreferences(prefs: UserPreferences) {
        val now = System.currentTimeMillis()
        val days = if (prefs.firstRunAt > 0L) ((now - prefs.firstRunAt) / MILLIS_PER_DAY).toInt() else 0
        state.update {
            it.copy(
                showBackupNudge = BackupNudge.shouldPrompt(prefs, now) && !it.nudgeDismissed,
                daysOfUse = days,
            )
        }
    }

    // ---------------------------------------------------------------- demo vessel

    fun installDemoVessel() = runExclusive(MoreBusy.DEMO_INSTALL) {
        val ok = runCatching { demoVessel.install() }.isSuccess
        emit(if (ok) MoreMessage.DemoInstalled else MoreMessage.DemoFailed)
        if (ok) state.update { it.copy(demoInstalled = true) }
    }

    fun removeDemoVessel() = runExclusive(MoreBusy.DEMO_REMOVE) {
        val ok = runCatching { demoVessel.uninstall() }.isSuccess
        emit(if (ok) MoreMessage.DemoRemoved else MoreMessage.DemoFailed)
        if (ok) state.update { it.copy(demoInstalled = false) }
    }

    // ---------------------------------------------------------------- backup

    /** [passphrase] is zeroed here once the archive has been written. */
    fun createBackup(destination: Uri, passphrase: CharArray?) = runExclusive(MoreBusy.BACKUP) {
        try {
            when (val outcome = backupManager.createBackup(destination, passphrase)) {
                is BackupOutcome.Written -> emit(
                    MoreMessage.BackupDone(outcome.vessels, outcome.photos, outcome.bytes),
                )

                BackupOutcome.NothingToBackUp -> emit(MoreMessage.BackupEmpty)
                is BackupOutcome.Failed -> emit(MoreMessage.BackupFailed(outcome.message))
            }
        } finally {
            passphrase?.fill(NUL)
        }
        // The nudge is driven by `lastBackupAt`, which BackupManager has just written; the
        // preferences flow will clear the banner on its own.
    }

    /**
     * Restore, in up to two steps: try without a passphrase, and if the file turns out to be
     * encrypted, hold the URI and ask.
     */
    fun restoreBackup(source: Uri, passphrase: CharArray? = null) = runExclusive(MoreBusy.RESTORE) {
        try {
            when (val outcome = backupManager.restoreBackup(source, passphrase)) {
                is RestoreOutcome.Applied -> {
                    pendingRestoreUri = null
                    state.update { it.copy(restoreNeedsPassphrase = false) }
                    emit(MoreMessage.RestoreDone(outcome.written, outcome.photos))
                }

                is RestoreOutcome.Rejected -> emit(MoreMessage.RestoreRejected(outcome.reason))

                is RestoreOutcome.PartiallyApplied ->
                    emit(MoreMessage.RestorePartial(outcome.message, outcome.unrecoverable))

                is RestoreOutcome.Unreadable -> when (outcome.reason) {
                    RestoreFailure.PASSPHRASE_REQUIRED -> {
                        pendingRestoreUri = source
                        state.update { it.copy(restoreNeedsPassphrase = true) }
                    }

                    else -> {
                        pendingRestoreUri = null
                        state.update { it.copy(restoreNeedsPassphrase = false) }
                        emit(MoreMessage.RestoreUnreadable(outcome.reason))
                    }
                }
            }
        } finally {
            passphrase?.fill(NUL)
        }
    }

    /** The passphrase dialog's confirm, for the file held by the previous attempt. */
    fun retryRestoreWithPassphrase(passphrase: CharArray) {
        val source = pendingRestoreUri
        if (source == null) {
            passphrase.fill(NUL)
            return
        }
        state.update { it.copy(restoreNeedsPassphrase = false) }
        restoreBackup(source, passphrase)
    }

    fun cancelRestore() {
        pendingRestoreUri = null
        state.update { it.copy(restoreNeedsPassphrase = false) }
    }

    // ---------------------------------------------------------------- folder & sharing

    fun setAutoBackupFolder(treeUri: Uri) {
        viewModelScope.launch(dispatchers.io) {
            val granted = backupFolder.set(treeUri)
            val name = backupFolder.displayName()
            // The weekly job exists only while a folder does — see AutoBackupScheduler.
            autoBackupScheduler.sync()
            state.update { it.copy(autoBackupFolder = name) }
            emit(if (granted && name != null) MoreMessage.FolderSet(name) else MoreMessage.FolderDenied)
        }
    }

    fun clearAutoBackupFolder() {
        viewModelScope.launch(dispatchers.io) {
            backupFolder.clear()
            autoBackupScheduler.sync()
            state.update { it.copy(autoBackupFolder = null) }
            emit(MoreMessage.FolderCleared)
        }
    }

    /**
     * The §13.6 "share last report" shortcut.
     *
     * Returns the chooser intent rather than starting it: only an `Activity` context may start one,
     * and handing the view model an activity would outlive it. Null when there is no report to
     * share — the cache may have been reclaimed since it was written.
     */
    fun shareLastReport(): Intent? {
        // `lastReport()` reads one SharedPreferences string and stats one file; cheap enough to do
        // on the tap, and it has to be synchronous because only the caller can start the chooser.
        val file = reportFileStore.lastReport()
        if (file == null) {
            emitNow(MoreMessage.NoLastReport)
            return null
        }
        return reportFileStore.shareIntent(file)
    }

    /** Dismiss the day-30 banner for this session — §18; it returns on the next cold start. */
    fun dismissBackupNudge() = state.update { it.copy(showBackupNudge = false, nudgeDismissed = true) }

    // ---------------------------------------------------------------- plumbing

    private fun runExclusive(kind: MoreBusy, block: suspend () -> Unit) {
        if (state.value.busy != null) return
        state.update { it.copy(busy = kind) }
        viewModelScope.launch(dispatchers.io) {
            try {
                block()
            } finally {
                state.update { it.copy(busy = null) }
            }
        }
    }

    private suspend fun emit(message: MoreMessage) {
        messageFlow.emit(message)
    }

    private fun emitNow(message: MoreMessage) {
        messageFlow.tryEmit(message)
    }

    private companion object {
        const val MESSAGE_BUFFER = 4
        const val MILLIS_PER_DAY = 86_400_000L

        /**
         * What a spent passphrase array is overwritten with. Explicit rather than a literal NUL in
         * the source, which is invisible in a diff and easy to mistake for a space.
         */
        const val NUL = '\u0000'
    }
}
