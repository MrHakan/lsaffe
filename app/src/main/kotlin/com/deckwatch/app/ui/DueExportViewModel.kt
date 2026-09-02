package com.deckwatch.app.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.feature.inspection.DueExportRequest
import com.deckwatch.feature.report.ReportFileStore
import com.deckwatch.feature.report.ReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Due tab's `onExportHtml` hand-off — §12 ("export the Due list as HTML"), §13.6 (sharing).
 *
 * The Due screen hands out a [DueExportRequest]: a snapshot of exactly what is on screen, filters
 * and all. Rendering it is `feature-report`'s job and starting a chooser is an activity's, so this
 * sits between them — render on a background dispatcher inside `ReportGenerator`, then emit the
 * chooser intent for the composable to start.
 *
 * The intent comes from `ReportFileStore.shareIntent`, which is the module's own `FileProvider`
 * helper: it grants read permission on the `content://` URI and sets `text/html`, which is what
 * makes a mail client attach it and a phone browser open it. Building an `ACTION_SEND` here would
 * mean re-declaring a provider authority the app module does not own.
 */
@HiltViewModel
class DueExportViewModel @Inject constructor(
    private val reportGenerator: ReportGenerator,
    private val reportFileStore: ReportFileStore,
) : ViewModel() {

    private val shareRequests = MutableSharedFlow<Intent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Chooser intents to start. Collected by the shell, which has an activity to start them with. */
    val shareIntents: Flow<Intent> = shareRequests.asSharedFlow()

    private val failureFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val failures: Flow<Unit> = failureFlow.asSharedFlow()

    fun exportAndShare(request: DueExportRequest) {
        viewModelScope.launch {
            val file = runCatching { reportGenerator.exportDueList(request) }.getOrNull()
            if (file == null) {
                failureFlow.tryEmit(Unit)
                return@launch
            }
            shareRequests.tryEmit(reportFileStore.shareIntent(file, subject = file.name))
        }
    }
}
