package com.deckwatch.feature.report

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Where the export screen is in its one long-running job — DESIGN_OVERHAUL rule 10. */
sealed interface ExportPhase {
    data object Idle : ExportPhase
    data object Working : ExportPhase
    data class Ready(val report: GeneratedReport) : ExportPhase
    data class Failed(val message: String) : ExportPhase
}

/** Everything `ReportsScreen` draws. */
data class ReportsUiState(
    val vessel: Vessel? = null,
    val decks: List<Deck> = emptyList(),
    val rounds: List<Round> = emptyList(),
    val options: ExportOptions = ExportOptions(),
    val estimateBytes: Long? = null,
    val estimating: Boolean = false,
    val phase: ExportPhase = ExportPhase.Idle,
    val lastReport: File? = null,
) {
    /** The generate button is enabled only when the chosen scope has what it needs — forms rule. */
    val canGenerate: Boolean
        get() = vessel != null &&
            phase != ExportPhase.Working &&
            (!options.scope.needsDeck || options.deckId != null) &&
            (!options.scope.needsRound || options.roundId != null)
}

/**
 * The export screen's state — §13.2, §13.3, §13.6.
 *
 * The live size estimate is debounced rather than recomputed on every tap: changing the photo tier
 * measures every photo file on the vessel, and doing that four times while the user makes up their
 * mind is work nobody sees.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
    private val inspectionRepository: InspectionRepository,
    private val generator: ReportGenerator,
    private val fileStore: ReportFileStore,
) : ViewModel() {

    private val options = MutableStateFlow(ExportOptions())
    private val estimate = MutableStateFlow<Long?>(null)
    private val estimating = MutableStateFlow(false)
    private val phase = MutableStateFlow<ExportPhase>(ExportPhase.Idle)
    private val lastReport = MutableStateFlow(fileStore.lastReport())

    private var estimateJob: Job? = null

    private val vessel: StateFlow<Vessel?> = vesselRepository.observeActiveVessel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val decks = vessel
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vesselRepository.observeDecks(id) }

    private val rounds = vessel
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else inspectionRepository.observeRounds(id) }

    val uiState: StateFlow<ReportsUiState> = combine(
        combine(vessel, decks, rounds) { vessel, decks, rounds -> Triple(vessel, decks, rounds) },
        options,
        combine(estimate, estimating) { bytes, busy -> bytes to busy },
        phase,
        lastReport,
    ) { (vessel, decks, rounds), currentOptions, (bytes, busy), currentPhase, last ->
        ReportsUiState(
            vessel = vessel,
            decks = decks,
            rounds = rounds,
            options = currentOptions,
            estimateBytes = bytes,
            estimating = busy,
            phase = currentPhase,
            lastReport = last,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReportsUiState())

    init {
        // Show a size estimate as soon as there is a vessel to estimate, rather than making the
        // officer touch a control first — DESIGN_OVERHAUL rule 10, feedback every time.
        viewModelScope.launch {
            vessel.map { it?.id }.distinctUntilChanged().filterNotNull().collect { scheduleEstimate() }
        }
    }

    // ------------------------------------------------------------------ user actions

    fun selectScope(scope: ExportScope) {
        options.update { current ->
            current.copy(
                scope = scope,
                deckId = if (scope.needsDeck) current.deckId else null,
                roundId = if (scope.needsRound) current.roundId else null,
            )
        }
        phase.value = ExportPhase.Idle
        scheduleEstimate()
    }

    fun selectPhotoTier(tier: PhotoTier) {
        options.update { it.copy(photoTier = tier) }
        scheduleEstimate()
    }

    fun selectDeck(deckId: String?) {
        options.update { it.copy(deckId = deckId) }
        scheduleEstimate()
    }

    fun selectRound(roundId: String?) {
        options.update { it.copy(roundId = roundId) }
        scheduleEstimate()
    }

    /** Generate and write the report. [labels] comes from the composable's string resources. */
    fun generate(labels: ReportLabels) {
        val vesselId = vessel.value?.id ?: return
        if (phase.value == ExportPhase.Working) return
        phase.value = ExportPhase.Working
        viewModelScope.launch {
            val result = runCatching { generator.generate(vesselId, options.value, labels) }
            phase.value = result.fold(
                onSuccess = { ExportPhase.Ready(it) },
                onFailure = { ExportPhase.Failed(it.message.orEmpty()) },
            )
            result.getOrNull()?.let { lastReport.value = it.file }
        }
    }

    /** Copy the generated report to a SAF destination — "Save to Downloads", §13.6. */
    fun saveTo(destination: Uri, file: File, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(fileStore.copyToDocument(file, destination)) }
    }

    fun shareIntentFor(file: File) = fileStore.shareIntent(file)

    fun dismissResult() {
        phase.value = ExportPhase.Idle
    }

    private fun scheduleEstimate() {
        val vesselId = vessel.value?.id ?: return
        estimateJob?.cancel()
        estimating.value = true
        estimateJob = viewModelScope.launch {
            delay(ESTIMATE_DEBOUNCE_MILLIS)
            estimate.value = runCatching { generator.estimateBytes(vesselId, options.value) }.getOrNull()
            estimating.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val ESTIMATE_DEBOUNCE_MILLIS = 250L
    }
}
