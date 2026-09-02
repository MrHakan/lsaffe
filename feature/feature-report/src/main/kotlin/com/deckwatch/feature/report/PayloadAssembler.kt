package com.deckwatch.feature.report

import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.DefaultDispatcherProvider
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.model.TaskInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A payload plus the lookup tables the renderer needs and the list of photo URIs the chosen tier
 * asks for. Everything here is plain data, so it crosses back to the main thread freely.
 */
data class AssembledReport(
    val payload: DeckWatchExportPayload,
    val typeNames: Map<String, String> = emptyMap(),
    val taskTitles: Map<String, String> = emptyMap(),
    /** Distinct photo URIs to embed, in the order they were met. */
    val photoUris: List<String> = emptyList(),
)

/**
 * Loads everything one export needs out of the five repositories — §13.3.
 *
 * All work happens on [DispatcherProvider.io]: a full backup of a 300-item vessel touches every
 * table, and §17.3 gives it five seconds and the main thread none of them.
 *
 * The dispatchers and the clock carry defaults and are absent from the `@Inject` constructor, in
 * the same way `DueViewModel` treats its clocks: they are ambient state, not graph dependencies,
 * and binding them would couple this module to a Hilt module it does not own.
 */
class PayloadAssembler(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val inspectionRepository: InspectionRepository,
    private val referenceRepository: ReferenceRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(Dispatchers.Main),
    private val clock: () -> Long = Dates::nowMillis,
    private val appVersion: () -> String = { "" },
) {

    @Inject
    constructor(
        vesselRepository: VesselRepository,
        equipmentRepository: EquipmentRepository,
        maintenanceRepository: MaintenanceRepository,
        inspectionRepository: InspectionRepository,
        referenceRepository: ReferenceRepository,
        appVersionProvider: AppVersionProvider,
    ) : this(
        vesselRepository = vesselRepository,
        equipmentRepository = equipmentRepository,
        maintenanceRepository = maintenanceRepository,
        inspectionRepository = inspectionRepository,
        referenceRepository = referenceRepository,
        dispatchers = DefaultDispatcherProvider(Dispatchers.Main),
        clock = Dates::nowMillis,
        appVersion = appVersionProvider::versionName,
    )

    /**
     * Build the payload for one vessel at one [scope].
     *
     * @param photoTier decides which photo URIs come back in [AssembledReport.photoUris]; the
     *   bytes are fetched later by [PhotoEmbedder], which is the only part that touches Android.
     */
    suspend fun build(
        vesselId: String,
        scope: ExportScope,
        photoTier: PhotoTier,
        deckId: String? = null,
        roundId: String? = null,
        dueList: com.deckwatch.feature.inspection.DueExportRequest? = null,
    ): AssembledReport = withContext(dispatchers.io) {
        val vessel = vesselRepository.getVessel(vesselId)
        val decks = vesselRepository.observeDecks(vesselId).first()
        val zones = decks.flatMap { deck -> vesselRepository.observeZones(deck.id).first() }
        val categories = vesselRepository.observeCategories(vesselId).first()
        val liveEquipment = equipmentRepository.observeEquipment(vesselId).first()
        val rounds = inspectionRepository.observeRounds(vesselId).first()
        val roundItems = rounds.flatMap { inspectionRepository.observeRoundItems(it.id).first() }
        val deficiencies = inspectionRepository.observeDeficiencies(vesselId).first()

        val equipment = liveEquipment + tombstones(liveEquipment, roundItems, deficiencies)
        val links = equipment.flatMap { item ->
            equipmentRepository.observeCategoryIds(item.id).first()
                .map { EquipmentCategoryLink(item.id, it) }
        }
        val instances = equipment.flatMap { maintenanceRepository.observeTaskInstances(it.id).first() }
        val definitions = maintenanceRepository.observeTaskDefinitions().first()
        val types = referenceRepository.observeEquipmentTypes().first()
        val userNotes = referenceRepository.observeUserNotes().first()

        val full = DeckWatchExportPayload(
            appVersion = appVersion(),
            generatedAtMillis = clock(),
            scope = scope.name,
            vessels = listOfNotNull(vessel),
            decks = decks,
            zones = zones,
            categories = categories,
            equipmentCategoryLinks = links,
            equipment = equipment,
            taskDefinitions = definitions.filter { it.isUserDefined },
            bundledTaskDefinitionKeys = definitions.filterNot { it.isUserDefined }.map { it.key },
            taskInstances = instances,
            rounds = rounds,
            roundItems = roundItems,
            deficiencies = deficiencies,
            userNotes = userNotes,
            userDefinedTypes = types.filter { it.isUserDefined },
            dueList = dueList,
        )

        val narrowed = narrow(full, scope, deckId, roundId)
        AssembledReport(
            payload = narrowed,
            typeNames = types.associate { it.typeKey to it.nameEn },
            taskTitles = definitions.associate { it.key to it.titleEn },
            photoUris = photoUris(narrowed, photoTier),
        )
    }

    /**
     * Soft-deleted rows, so a deletion made on one device propagates rather than being resurrected
     * by the other side (§13.5).
     *
     * ### An honest limitation
     * `EquipmentRepository` exposes no "including deleted" listing — every `observe…` filters
     * tombstones out, and only [EquipmentRepository.getEquipment] returns one. So the assembler
     * recovers the tombstones it can *name*: the equipment ids still referenced by a round item or
     * a deficiency. An item created and deleted without ever appearing in a round or a deficiency
     * is invisible to the export, and its deletion will not propagate. The fix is an
     * `observeEquipmentIncludingDeleted(vesselId)` on the repository interface, which lives outside
     * this module; until then the gap is here, in writing, rather than in a silent surprise.
     */
    private suspend fun tombstones(
        live: List<Equipment>,
        roundItems: List<RoundItem>,
        deficiencies: List<Deficiency>,
    ): List<Equipment> {
        val known = live.map { it.id }.toSet()
        val referenced = buildSet {
            roundItems.forEach { add(it.equipmentId) }
            deficiencies.forEach { deficiency -> deficiency.equipmentId?.let { add(it) } }
        }
        return (referenced - known).mapNotNull { id ->
            equipmentRepository.getEquipment(id)?.takeIf { it.deletedAt != null }
        }
    }

    /** Trims the full payload down to the records the chosen scope actually prints — §13.3. */
    private fun narrow(
        payload: DeckWatchExportPayload,
        scope: ExportScope,
        deckId: String?,
        roundId: String?,
    ): DeckWatchExportPayload = when (scope) {
        ExportScope.FULL_BACKUP -> payload

        ExportScope.DUE_LIST -> payload.copy(
            decks = emptyList(),
            zones = emptyList(),
            categories = emptyList(),
            equipmentCategoryLinks = emptyList(),
            equipment = emptyList(),
            taskDefinitions = emptyList(),
            bundledTaskDefinitionKeys = emptyList(),
            taskInstances = emptyList(),
            rounds = emptyList(),
            roundItems = emptyList(),
            deficiencies = emptyList(),
            userNotes = emptyList(),
            userDefinedTypes = emptyList(),
        )

        ExportScope.DECK_SHEET -> {
            val decks = payload.decks.filter { deckId == null || it.id == deckId }
            val deckIds = decks.map { it.id }.toSet()
            val equipment = payload.equipment.filter { it.deckId in deckIds }
            val equipmentIds = equipment.map { it.id }.toSet()
            payload.copy(
                decks = decks,
                zones = payload.zones.filter { it.deckId in deckIds },
                equipment = equipment,
                equipmentCategoryLinks = payload.equipmentCategoryLinks.filter { it.equipmentId in equipmentIds },
                taskInstances = payload.taskInstances.filter { it.equipmentId in equipmentIds },
                rounds = emptyList(),
                roundItems = emptyList(),
                deficiencies = emptyList(),
                userNotes = emptyList(),
            )
        }

        ExportScope.ROUND_REPORT -> {
            val round = payload.rounds.firstOrNull { it.id == roundId } ?: payload.rounds.firstOrNull()
            val items = payload.roundItems.filter { it.roundId == round?.id }
            val equipmentIds = items.map { it.equipmentId }.toSet()
            payload.copy(
                rounds = listOfNotNull(round),
                roundItems = items,
                equipment = payload.equipment.filter { it.id in equipmentIds },
                equipmentCategoryLinks = payload.equipmentCategoryLinks.filter { it.equipmentId in equipmentIds },
                taskInstances = payload.taskInstances.filter { it.equipmentId in equipmentIds },
                deficiencies = emptyList(),
                userNotes = emptyList(),
            )
        }

        ExportScope.DEFICIENCY_REPORT -> {
            val open = payload.deficiencies.filter { it.status != DeficiencyStatus.CLOSED }
            val equipmentIds = open.mapNotNull { it.equipmentId }.toSet()
            payload.copy(
                deficiencies = open,
                equipment = payload.equipment.filter { it.id in equipmentIds },
                equipmentCategoryLinks = payload.equipmentCategoryLinks.filter { it.equipmentId in equipmentIds },
                taskInstances = emptyList(),
                rounds = emptyList(),
                roundItems = emptyList(),
                userNotes = emptyList(),
            )
        }

        ExportScope.PSC_SURVEY_PACK -> {
            val cutoff = payload.generatedAtMillis - TWELVE_MONTHS_MILLIS
            val rounds = payload.rounds.filter { it.startedAt >= cutoff }
            val roundIds = rounds.map(Round::id).toSet()
            payload.copy(
                rounds = rounds,
                roundItems = payload.roundItems.filter { it.roundId in roundIds },
                deficiencies = payload.deficiencies.filter { it.status != DeficiencyStatus.CLOSED },
                userNotes = emptyList(),
            )
        }
    }

    /** Which photos the chosen tier wants, de-duplicated and in document order — §13.2. */
    private fun photoUris(payload: DeckWatchExportPayload, tier: PhotoTier): List<String> = when (tier) {
        PhotoTier.NONE -> emptyList()

        PhotoTier.DEFICIENCY_ONLY -> buildList {
            payload.deficiencies.forEach { addAll(it.photoUris) }
            payload.roundItems.forEach { addAll(it.photoUris) }
        }.distinct()

        PhotoTier.ALL -> buildList {
            payload.deficiencies.forEach { addAll(it.photoUris) }
            payload.roundItems.forEach { addAll(it.photoUris) }
            payload.equipment.forEach { addAll(it.photoUris) }
            payload.taskInstances.forEach { instance: TaskInstance -> addAll(instance.photoUris) }
        }.distinct()
    }

    private companion object {
        const val TWELVE_MONTHS_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}
