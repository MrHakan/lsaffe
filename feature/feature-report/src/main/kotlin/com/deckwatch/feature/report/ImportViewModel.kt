package com.deckwatch.feature.report

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the import screen is — nothing is written before [Preview] is confirmed (§13.5). */
sealed interface ImportPhase {
    data object Idle : ImportPhase
    data object Reading : ImportPhase
    data class Preview(val preview: ImportPreview) : ImportPhase
    data object Applying : ImportPhase
    data class Done(val outcome: ImportOutcome) : ImportPhase
    data class Failed(val failure: ImportFailure, val detail: String) : ImportPhase
}

/** Everything `ImportScreen` draws. */
data class ImportUiState(
    val fileName: String? = null,
    val phase: ImportPhase = ImportPhase.Idle,
    val resolutions: Map<String, ConflictResolution> = emptyMap(),
) {
    val preview: ImportPreview? get() = (phase as? ImportPhase.Preview)?.preview
    val busy: Boolean get() = phase is ImportPhase.Reading || phase is ImportPhase.Applying
}

/**
 * The import screen's state — §13.5.
 *
 * The flow is strictly: read the file, show the preview, **wait for the user**, then write. The
 * local snapshot is taken once when the preview is built and reused for the apply, so what the
 * user approved is what gets written; a concurrent edit in another tab cannot change the meaning
 * of the choice they made.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val fileStore: ReportFileStore,
    private val applier: ImportApplier,
) : ViewModel() {

    private val state = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = state.asStateFlow()

    private var snapshot: LocalSnapshot = LocalSnapshot()

    /** Read a user-picked file and build the preview. Never throws — §17.4. */
    fun load(source: Uri) {
        state.value = ImportUiState(phase = ImportPhase.Reading)
        viewModelScope.launch {
            val name = fileStore.displayName(source)
            val text = fileStore.readText(source)
            if (text == null) {
                state.value = ImportUiState(
                    fileName = name,
                    phase = ImportPhase.Failed(ImportFailure.UNREADABLE, "The file could not be opened."),
                )
                return@launch
            }
            when (val parsed = PayloadParser.parse(text)) {
                is PayloadParseResult.Failed ->
                    state.value = ImportUiState(
                        fileName = name,
                        phase = ImportPhase.Failed(parsed.reason, parsed.detail),
                    )

                is PayloadParseResult.Parsed -> {
                    snapshot = applier.snapshot(parsed.payload)
                    val preview = ImportMerger.preview(snapshot, parsed.payload)
                    state.value = ImportUiState(
                        fileName = name,
                        phase = ImportPhase.Preview(preview),
                        resolutions = preview.defaultResolutions(),
                    )
                }
            }
        }
    }

    /** One conflict's resolution — §13.5's "explicit user choice". */
    fun resolve(conflict: ImportConflict, resolution: ConflictResolution) {
        state.update { current ->
            current.copy(
                resolutions = current.resolutions + (conflictKey(conflict.kind, conflict.id) to resolution),
            )
        }
    }

    /** The "apply all" shortcut on the conflicts header. */
    fun resolveAll(resolution: ConflictResolution) {
        state.update { current ->
            val preview = current.preview ?: return@update current
            current.copy(
                resolutions = preview.conflicts.associate {
                    conflictKey(it.kind, it.id) to resolution
                },
            )
        }
    }

    /** Build the plan from the user's choices, validate it, and write — see [ImportApplier]. */
    fun apply() {
        val preview = state.value.preview ?: return
        val resolutions = state.value.resolutions
        val name = state.value.fileName
        state.value = ImportUiState(fileName = name, phase = ImportPhase.Applying, resolutions = resolutions)
        viewModelScope.launch {
            val plan = ImportMerger.plan(snapshot, preview.payload, resolutions)
            val outcome = applier.apply(plan, snapshot)
            state.value = ImportUiState(fileName = name, phase = ImportPhase.Done(outcome), resolutions = resolutions)
        }
    }

    /** Back to the file picker. */
    fun reset() {
        snapshot = LocalSnapshot()
        state.value = ImportUiState()
    }
}
