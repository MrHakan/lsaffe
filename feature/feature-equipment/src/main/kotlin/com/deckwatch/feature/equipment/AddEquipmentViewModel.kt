package com.deckwatch.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.common.due.VesselDueContext
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.feature.equipment.attributes.AttributeCodec
import com.deckwatch.feature.equipment.attributes.AttributeDraft
import com.deckwatch.feature.equipment.attributes.AttributeError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three steps of §7.5. */
internal enum class AddStep { CATALOGUE, DETAILS, ATTRIBUTES }

/** The catalogue's two tabs — §7.5 step 2. */
internal enum class CatalogueTab { BY_CATEGORY, RECENT }

/** One catalogue row: symbol, both names, one line of help. */
internal data class CatalogueEntryUi(
    val typeKey: String,
    val nameEn: String,
    val nameTr: String,
    val symbolKey: String,
    val helpEn: String,
    val helpTr: String,
    val group: EquipmentGroup,
)

/** A collapsible catalogue section — §7.5 step 2. */
internal data class CatalogueGroupUi(
    val group: EquipmentGroup,
    val entries: List<CatalogueEntryUi>,
)

/** The fixed part of the add form. Only [tag] is required (§7.5 step 3). */
internal data class EquipmentFormState(
    val tag: String = "",
    val name: String = "",
    val location: String = "",
    val maker: String = "",
    val model: String = "",
    val serial: String = "",
    val typeApproval: String = "",
    val quantity: String = "1",
    /** Epoch-days. */
    val manufactureDate: Long? = null,
    val installedDate: Long? = null,
    val notes: String = "",
)

internal data class AddEquipmentUiState(
    val step: AddStep = AddStep.CATALOGUE,
    val query: String = "",
    val tab: CatalogueTab = CatalogueTab.BY_CATEGORY,
    val groups: List<CatalogueGroupUi> = emptyList(),
    val recent: List<CatalogueEntryUi> = emptyList(),
    val expandedGroups: Set<EquipmentGroup> = emptySet(),
    val selectedType: EquipmentType? = null,
    val form: EquipmentFormState = EquipmentFormState(),
    val attributes: AttributeDraft = emptyMap(),
    val attributeErrors: Map<String, AttributeError> = emptyMap(),
    /** Live due-date preview — §7.5.4. */
    val duePreview: List<DuePreviewRow> = emptyList(),
    /** Task definitions the preview resolves against — §7.5.4 per-field anchor lines. */
    val definitions: Map<String, TaskDefinition> = emptyMap(),
    /** Vessel flag and certificate expiry the preview runs against — §11.1 (1), (3). */
    val vesselContext: VesselDueContext = VesselDueContext(),
    val copies: Int = 1,
    val tagError: Boolean = false,
    val saving: Boolean = false,
    /** Non-empty once the items are written; the host closes the sheet on this. */
    val createdIds: List<String> = emptyList(),
    val todayEpochDay: Long = Dates.todayEpochDay(),
)

/**
 * The add-equipment flow — §7.5.
 *
 * Holds the catalogue, the form and the live due-date preview. The preview runs a [DueEngine]
 * directly because nothing is persisted yet; once the item exists,
 * [MaintenanceRepository.recomputeDue] owns its schedule (§11.2), and this view model calls it for
 * every item it creates.
 */
@HiltViewModel
internal class AddEquipmentViewModel @Inject constructor(
    private val equipmentRepository: EquipmentRepository,
    private val referenceRepository: ReferenceRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val vesselRepository: VesselRepository,
) : ViewModel() {

    private val engine = DueEngine()
    private val state = MutableStateFlow(AddEquipmentUiState())
    val uiState: StateFlow<AddEquipmentUiState> = state.asStateFlow()

    private var allTypes: List<EquipmentType> = emptyList()
    private var definitions: Map<String, TaskDefinition> = emptyMap()

    /** "Recent" is deliberately in-memory view-model state — §7.5 step 2. */
    private val recentKeys = MutableStateFlow<List<String>>(emptyList())

    private var vesselId: String = ""
    private var deckId: String? = null
    private var zoneId: String? = null
    private var posX: Float = DEFAULT_POSITION
    private var posY: Float = DEFAULT_POSITION
    private var deckShortCode: String? = null
    private var vesselContext = VesselDueContext()
    private var bound = false

    init {
        viewModelScope.launch {
            referenceRepository.observeEquipmentTypes().collect { types ->
                allTypes = types
                refreshCatalogue()
            }
        }
        viewModelScope.launch {
            maintenanceRepository.observeTaskDefinitions().collect { list ->
                definitions = list.associateBy { it.key }
                state.update { it.copy(definitions = definitions) }
                refreshDuePreview()
            }
        }
        viewModelScope.launch {
            recentKeys.collect { refreshCatalogue() }
        }
    }

    /** Bind the drop point the officer long-pressed — §7.5 step 1. */
    fun bind(vesselId: String, deckId: String?, zoneId: String?, posX: Float, posY: Float) {
        // The zone counts too: two rows on the same deck are two different drop points.
        val samePlace = this.vesselId == vesselId && this.deckId == deckId && this.zoneId == zoneId
        if (bound && samePlace) return
        bound = true
        this.vesselId = vesselId
        this.deckId = deckId
        this.zoneId = zoneId
        this.posX = posX
        this.posY = posY
        viewModelScope.launch {
            deckShortCode = deckId?.let { vesselRepository.getDeck(it)?.shortCode }
            vesselContext = vesselRepository.getVessel(vesselId)?.let(VesselDueContext::from) ?: VesselDueContext()
            state.update { it.copy(vesselContext = vesselContext) }
            refreshDuePreview()
        }
    }

    // ------------------------------------------------------------------ step 1: catalogue

    fun setQuery(value: String) {
        state.update { it.copy(query = value) }
        refreshCatalogue()
    }

    fun setTab(tab: CatalogueTab) = state.update { it.copy(tab = tab) }

    fun toggleGroup(group: EquipmentGroup) = state.update { current ->
        val expanded = if (group in current.expandedGroups) {
            current.expandedGroups - group
        } else {
            current.expandedGroups + group
        }
        current.copy(expandedGroups = expanded)
    }

    /**
     * Pick a type and move to the form — §7.5 step 3.
     *
     * The form arrives pre-populated: the symbol comes from the catalogue entry, and the tag is
     * suggested as `PREFIX-DECK-NN` (see [TagSuggestion]) from the type's prefix, the deck's short
     * code and the next free number on the vessel.
     */
    fun selectType(typeKey: String) {
        val type = allTypes.firstOrNull { it.typeKey == typeKey } ?: return
        recentKeys.update { current -> (listOf(typeKey) + current.filterNot { it == typeKey }).take(RECENT_LIMIT) }
        state.update {
            it.copy(
                selectedType = type,
                step = AddStep.DETAILS,
                attributes = AttributeCodec.decode(type.attributeSchema, "{}"),
                attributeErrors = emptyMap(),
                tagError = false,
                form = EquipmentFormState(),
            )
        }
        viewModelScope.launch {
            val prefix = TagSuggestion.prefix(type.defaultTagPrefix, deckShortCode)
            val next = equipmentRepository.nextTagNumber(vesselId, prefix)
            state.update { current ->
                if (current.selectedType?.typeKey != typeKey) {
                    current
                } else {
                    current.copy(form = current.form.copy(tag = TagSuggestion.format(prefix, next)))
                }
            }
            refreshDuePreview()
        }
    }

    // ------------------------------------------------------------------ step 2: the form

    fun updateForm(transform: (EquipmentFormState) -> EquipmentFormState) {
        state.update { it.copy(form = transform(it.form), tagError = false) }
        refreshDuePreview()
    }

    fun setCopies(count: Int) = state.update { it.copy(copies = count.coerceIn(1, MAX_COPIES)) }

    // ------------------------------------------------------------------ step 3: attributes

    fun setAttribute(key: String, raw: String) {
        state.update {
            it.copy(attributes = it.attributes + (key to raw), attributeErrors = it.attributeErrors - key)
        }
        refreshDuePreview()
    }

    fun goTo(step: AddStep) {
        if (step == AddStep.DETAILS && state.value.selectedType == null) return
        state.update { it.copy(step = step) }
    }

    /** Back out of the form to the catalogue, clearing the selection — §7.5. */
    fun backToCatalogue() = state.update {
        it.copy(step = AddStep.CATALOGUE, selectedType = null, attributeErrors = emptyMap(), tagError = false)
    }

    // ------------------------------------------------------------------ create

    /**
     * Write the item, plus `copies - 1` further items with incremented tags — §7.5 duplicate ×N.
     *
     * Copies are laid out in a small grid around the drop point so the officer can drag them apart;
     * every item is scheduled through [MaintenanceRepository.recomputeDue] straight after the write
     * (§11.2).
     */
    fun create() {
        val current = state.value
        val type = current.selectedType ?: return
        if (current.saving) return
        val tag = current.form.tag.trim()
        val errors = AttributeCodec.validate(type.attributeSchema, current.attributes)
        if (tag.isEmpty() || errors.isNotEmpty()) {
            state.update { it.copy(tagError = tag.isEmpty(), attributeErrors = errors) }
            return
        }
        state.update { it.copy(saving = true) }

        val now = Dates.nowMillis()
        val attributesJson = AttributeCodec.encodeToString(type.attributeSchema, current.attributes)
        val layout = gridPositions(current.copies, posX, posY)

        viewModelScope.launch {
            val created = layout.mapIndexed { index, position ->
                val item = Equipment(
                    id = UUID.randomUUID().toString(),
                    vesselId = vesselId,
                    deckId = deckId,
                    zoneId = zoneId,
                    typeKey = type.typeKey,
                    symbolKey = type.symbolKey,
                    tag = TagSuggestion.increment(tag, index),
                    name = current.form.name.trimToNull(),
                    location = current.form.location.trimToNull(),
                    posX = position.first,
                    posY = position.second,
                    makerName = current.form.maker.trimToNull(),
                    modelName = current.form.model.trimToNull(),
                    serialNumber = current.form.serial.trimToNull(),
                    typeApprovalNumber = current.form.typeApproval.trimToNull(),
                    manufactureDate = current.form.manufactureDate,
                    installedDate = current.form.installedDate,
                    quantity = current.form.quantity.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    attributesJson = attributesJson,
                    notes = current.form.notes.trimToNull(),
                    createdAt = now,
                    updatedAt = now,
                )
                equipmentRepository.upsertEquipment(item)
                maintenanceRepository.recomputeDue(item.id)
                item.id
            }
            state.update { it.copy(saving = false, createdIds = created) }
        }
    }

    /** Reset after the host has consumed [AddEquipmentUiState.createdIds]. */
    fun consumeCreated() = state.update { it.copy(createdIds = emptyList()) }

    // ------------------------------------------------------------------ derivation

    private fun refreshCatalogue() {
        val needle = state.value.query.trim()
        val matches = allTypes.filter { it.matches(needle) }
        val groups = EquipmentGroup.entries
            .mapNotNull { group ->
                val entries = matches.filter { it.group == group }.map { it.toEntry() }
                if (entries.isEmpty()) null else CatalogueGroupUi(group, entries)
            }
        val recent = recentKeys.value.mapNotNull { key -> allTypes.firstOrNull { it.typeKey == key }?.toEntry() }
        state.update { current ->
            current.copy(
                groups = groups,
                recent = recent,
                // A search opens every matching section; browsing keeps the officer's own choice.
                expandedGroups = if (needle.isEmpty()) current.expandedGroups else groups.map { it.group }.toSet(),
            )
        }
    }

    /** §7.5.4 — the preview recomputes on every keystroke that can move a date. */
    private fun refreshDuePreview() {
        val current = state.value
        val type = current.selectedType ?: return
        val preview = DuePreview.compute(
            type = type,
            definitions = definitions,
            attributesJson = AttributeCodec.encodeToString(type.attributeSchema, current.attributes),
            installedDate = current.form.installedDate,
            manufactureDate = current.form.manufactureDate,
            vessel = vesselContext,
            todayEpochDay = Dates.todayEpochDay(),
            engine = engine,
        )
        state.update { it.copy(duePreview = preview) }
    }

    private fun EquipmentType.matches(needle: String): Boolean = needle.isEmpty() ||
        nameEn.contains(needle, ignoreCase = true) ||
        nameTr.contains(needle, ignoreCase = true) ||
        typeKey.contains(needle, ignoreCase = true) ||
        subGroup.contains(needle, ignoreCase = true)

    private fun EquipmentType.toEntry() = CatalogueEntryUi(
        typeKey = typeKey,
        nameEn = nameEn,
        nameTr = nameTr,
        symbolKey = symbolKey,
        helpEn = helpTextEn,
        helpTr = helpTextTr,
        group = group,
    )

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

    companion object {
        /** Normalised plan spacing between duplicated markers — §7.5. */
        const val GRID_SPACING: Float = 0.06f

        /** The stepper's ceiling: "a deck has 14 identical extinguishers" (§7.5), not 400. */
        const val MAX_COPIES: Int = 20

        private const val RECENT_LIMIT = 12
        private const val DEFAULT_POSITION = 0.5f
        private const val MIN_POSITION = 0.02f
        private const val MAX_POSITION = 0.98f

        /**
         * Positions for [count] copies, in a near-square grid centred on the drop point and clamped
         * inside the plan.
         */
        fun gridPositions(count: Int, posX: Float, posY: Float): List<Pair<Float, Float>> {
            if (count <= 1) return listOf(posX to posY)
            val columns = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
            val rows = ceil(count.toDouble() / columns).toInt().coerceAtLeast(1)
            return (0 until count).map { index ->
                val column = index % columns
                val row = index / columns
                val x = posX + (column - (columns - 1) / 2f) * GRID_SPACING
                val y = posY + (row - (rows - 1) / 2f) * GRID_SPACING
                x.coerceIn(MIN_POSITION, MAX_POSITION) to y.coerceIn(MIN_POSITION, MAX_POSITION)
            }
        }
    }
}
