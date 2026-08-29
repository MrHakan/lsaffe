package com.deckwatch.feature.vessel.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.ClassSociety
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.VesselType
import com.deckwatch.feature.vessel.common.ImoStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * The create/edit form of §6.1. Numeric and date fields are held as they are typed and converted
 * on save, so a half-entered tonnage never wipes the field.
 */
data class VesselFormState(
    val vesselId: String? = null,
    val name: String = "",
    val imoNumber: String = "",
    val callSign: String = "",
    val mmsi: String = "",
    val flag: FlagState = FlagState.OTHER,
    val flagOtherName: String = "",
    val classSociety: ClassSociety? = null,
    val vesselType: VesselType = VesselType.OTHER,
    val grossTonnage: String = "",
    val buildDate: Long? = null,
    val safetyEquipmentCertExpiry: Long? = null,
    val lastAnnualSurveyDate: Long? = null,
    val nextDrydockDate: Long? = null,
    val showErrors: Boolean = false,
    val saved: Boolean = false,
) {
    val isNew: Boolean get() = vesselId == null

    /** Live check-digit state — recomputed on every keystroke (§6.1). */
    val imoStatus: ImoStatus get() = ImoStatus.of(imoNumber)

    val nameMissing: Boolean get() = name.isBlank()

    /**
     * Name is the only hard requirement. A failing IMO check digit does **not** block the save —
     * see [ImoStatus] for why.
     */
    val canSave: Boolean get() = !nameMissing

    val showNameError: Boolean get() = showErrors && nameMissing

    val showFlagOtherName: Boolean get() = flag == FlagState.OTHER
}

@HiltViewModel
class VesselEditViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VesselFormState())
    val state: StateFlow<VesselFormState> = _state.asStateFlow()

    private var loadedFor: String? = null
    private var loadStarted = false

    /**
     * Loads [vesselId] once. Called from composition, so it must be idempotent — a recomposition
     * must not throw away what the officer has typed.
     */
    fun load(vesselId: String?) {
        if (loadStarted && loadedFor == vesselId) return
        loadStarted = true
        loadedFor = vesselId
        if (vesselId == null) {
            _state.value = VesselFormState()
            return
        }
        viewModelScope.launch {
            val vessel = vesselRepository.getVessel(vesselId) ?: return@launch
            _state.value = vessel.toFormState()
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    /** Accepts what is typed, digits only, capped at the seven digits of an IMO number. */
    fun onImoChange(value: String) = _state.update {
        it.copy(imoNumber = value.filter(Char::isDigit).take(IMO_DIGITS))
    }

    fun onCallSignChange(value: String) = _state.update { it.copy(callSign = value) }

    fun onMmsiChange(value: String) = _state.update {
        it.copy(mmsi = value.filter(Char::isDigit).take(MMSI_DIGITS))
    }

    fun onFlagChange(value: FlagState) = _state.update {
        it.copy(flag = value, flagOtherName = if (value == FlagState.OTHER) it.flagOtherName else "")
    }

    fun onFlagOtherNameChange(value: String) = _state.update { it.copy(flagOtherName = value) }

    fun onClassSocietyChange(value: ClassSociety?) = _state.update { it.copy(classSociety = value) }

    fun onVesselTypeChange(value: VesselType) = _state.update { it.copy(vesselType = value) }

    fun onGrossTonnageChange(value: String) = _state.update {
        it.copy(grossTonnage = value.filter(Char::isDigit).take(GT_DIGITS))
    }

    fun onBuildDateChange(value: Long?) = _state.update { it.copy(buildDate = value) }

    fun onSecExpiryChange(value: Long?) = _state.update { it.copy(safetyEquipmentCertExpiry = value) }

    fun onLastAnnualSurveyChange(value: Long?) = _state.update { it.copy(lastAnnualSurveyDate = value) }

    fun onNextDrydockChange(value: Long?) = _state.update { it.copy(nextDrydockDate = value) }

    /**
     * Writes the vessel. An IMO number that fails its check digit is stored verbatim; the UI
     * carries the "unverified IMO" badge instead of refusing the record (see [ImoStatus]).
     *
     * The first vessel a user ever creates becomes the active one, so the deck manager and list
     * mode have something to resolve to straight away (§5).
     */
    fun save() {
        val current = _state.value
        if (!current.canSave) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            val now = Dates.nowMillis()
            val existing = current.vesselId?.let { vesselRepository.getVessel(it) }
            val id = current.vesselId ?: UUID.randomUUID().toString()
            val vessel = Vessel(
                id = id,
                name = current.name.trim(),
                imoNumber = current.imoNumber.ifBlank { null },
                callSign = current.callSign.trim().ifBlank { null },
                mmsi = current.mmsi.ifBlank { null },
                flag = current.flag,
                flagOtherName = current.flagOtherName.trim().ifBlank { null },
                classSociety = current.classSociety,
                vesselType = current.vesselType,
                grossTonnage = current.grossTonnage.toIntOrNull(),
                buildDate = current.buildDate,
                safetyEquipmentCertExpiry = current.safetyEquipmentCertExpiry,
                lastAnnualSurveyDate = current.lastAnnualSurveyDate,
                nextDrydockDate = current.nextDrydockDate,
                isActive = existing?.isActive ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            vesselRepository.upsertVessel(vessel)
            if (existing == null && vesselRepository.observeActiveVessel().first() == null) {
                vesselRepository.setActiveVessel(id)
            }
            _state.update { it.copy(vesselId = id, saved = true) }
        }
    }

    /** Consumed by the screen once it has acted on [VesselFormState.saved]. */
    fun onSavedHandled() = _state.update { it.copy(saved = false) }

    private fun Vessel.toFormState(): VesselFormState = VesselFormState(
        vesselId = id,
        name = name,
        imoNumber = imoNumber.orEmpty(),
        callSign = callSign.orEmpty(),
        mmsi = mmsi.orEmpty(),
        flag = flag,
        flagOtherName = flagOtherName.orEmpty(),
        classSociety = classSociety,
        vesselType = vesselType,
        grossTonnage = grossTonnage?.toString().orEmpty(),
        buildDate = buildDate,
        safetyEquipmentCertExpiry = safetyEquipmentCertExpiry,
        lastAnnualSurveyDate = lastAnnualSurveyDate,
        nextDrydockDate = nextDrydockDate,
    )

    private companion object {
        const val IMO_DIGITS = 7
        const val MMSI_DIGITS = 9
        const val GT_DIGITS = 7
    }
}
