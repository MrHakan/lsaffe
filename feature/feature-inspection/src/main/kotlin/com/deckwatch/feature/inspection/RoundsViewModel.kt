package com.deckwatch.feature.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One template the officer can start a round from, with how many items it would cover today. */
data class RoundTemplateOption(
    val key: String,
    val title: LocalisedText,
    val matchCount: Int,
)

data class RoundsUiState(
    val loading: Boolean = true,
    val hasVessel: Boolean = false,
    val rounds: List<Round> = emptyList(),
    val templates: List<RoundTemplateOption> = emptyList(),
)

/** One-shot outcomes of "start round", consumed by the screen. */
sealed interface RoundsEvent {
    data class Started(val roundId: String) : RoundsEvent

    data object NoMatchingEquipment : RoundsEvent
}

/**
 * Round history and the start-a-round flow — §6.7.
 *
 * This is the **list-mode sweep** §7.1 C requires: every graphical path has a list equivalent, and
 * the canvas sweep of §7.3 lands in `feature-deckview` later writing the same [Round] records.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoundsViewModel(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    private val inspectionRepository: InspectionRepository,
    private val referenceRepository: ReferenceRepository,
    private val clock: () -> Long = Dates::nowMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    @Inject
    constructor(
        vesselRepository: VesselRepository,
        equipmentRepository: EquipmentRepository,
        inspectionRepository: InspectionRepository,
        referenceRepository: ReferenceRepository,
    ) : this(
        vesselRepository = vesselRepository,
        equipmentRepository = equipmentRepository,
        inspectionRepository = inspectionRepository,
        referenceRepository = referenceRepository,
        clock = Dates::nowMillis,
        idFactory = { UUID.randomUUID().toString() },
    )

    private val turkishState = MutableStateFlow(false)
    private val eventState = MutableStateFlow<RoundsEvent?>(null)

    /** What [startRound] needs at the moment of the tap, kept off the hot path of the UI state. */
    private val cache = MutableStateFlow(Catalogue())

    val event: StateFlow<RoundsEvent?> = eventState.asStateFlow()

    private val vesselFlow = vesselRepository.observeActiveVessel()

    private val catalogueFlow: Flow<Catalogue> = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) {
            flowOf(Catalogue())
        } else {
            combine(
                equipmentRepository.observeEquipment(vessel.id),
                referenceRepository.observeEquipmentTypes(),
                referenceRepository.observeRoundTemplates(),
                vesselRepository.observeDecks(vessel.id),
            ) { equipment, types, templates, decks ->
                Catalogue(
                    vesselId = vessel.id,
                    equipment = equipment,
                    typesByKey = types.associateBy { it.typeKey },
                    templates = templates,
                    // observeDecks is already sorted highest deck first (§6.2), so the index is
                    // the walking order down the stack.
                    deckOrder = decks.withIndex().associate { (index, deck) -> deck.id to index },
                )
            }
        }
    }

    private val roundsFlow: Flow<List<Round>> = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) flowOf(emptyList()) else inspectionRepository.observeRounds(vessel.id)
    }

    val uiState: StateFlow<RoundsUiState> =
        combine(catalogueFlow, roundsFlow) { catalogue, rounds ->
            cache.value = catalogue
            RoundsUiState(
                loading = false,
                hasVessel = catalogue.vesselId != null,
                rounds = rounds,
                templates = catalogue.templates.map { template ->
                    RoundTemplateOption(
                        key = template.key,
                        title = LocalisedText(template.titleEn, template.titleTr),
                        matchCount = RoundMaterialiser.matchEquipment(
                            template = template,
                            equipment = catalogue.equipment,
                            typesByKey = catalogue.typesByKey,
                        ).size,
                    )
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = RoundsUiState(),
        )

    fun setTurkish(turkish: Boolean) {
        turkishState.value = turkish
    }

    /**
     * Materialise a round from [templateKey] and write it with its items — §6.7.
     *
     * A template that matches nothing on this vessel is reported rather than written: an empty
     * round in the history would look like a round that was walked and found nothing.
     */
    fun startRound(templateKey: String, performedBy: String) {
        val catalogue = cache.value
        val vesselId = catalogue.vesselId ?: return
        val template = catalogue.templates.firstOrNull { it.key == templateKey } ?: return
        viewModelScope.launch {
            val materialised = RoundMaterialiser.materialise(
                template = template,
                vesselId = vesselId,
                equipment = catalogue.equipment,
                typesByKey = catalogue.typesByKey,
                performedBy = performedBy,
                startedAtMillis = clock(),
                idFactory = idFactory,
                deckOrder = catalogue.deckOrder,
                turkish = turkishState.value,
            )
            if (materialised.items.isEmpty()) {
                eventState.value = RoundsEvent.NoMatchingEquipment
                return@launch
            }
            inspectionRepository.upsertRound(materialised.round)
            materialised.items.forEach { inspectionRepository.upsertRoundItem(it) }
            eventState.value = RoundsEvent.Started(materialised.round.id)
        }
    }

    fun consumeEvent() {
        eventState.value = null
    }

    private data class Catalogue(
        val vesselId: String? = null,
        val equipment: List<Equipment> = emptyList(),
        val typesByKey: Map<String, EquipmentType> = emptyMap(),
        val templates: List<RoundTemplate> = emptyList(),
        val deckOrder: Map<String, Int> = emptyMap(),
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
