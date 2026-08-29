package com.deckwatch.data.seed

import com.deckwatch.core.model.ClassSociety
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.StatusFlag
import com.deckwatch.core.model.SymbolInfo
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.VesselType
import com.deckwatch.core.model.Zone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Asset paths for the bundled seed documents — §19. */
object SeedAssets {
    const val DIR = "seed"
    const val EQUIPMENT_CATALOGUE = "$DIR/equipment_catalogue.json"
    const val TASK_DEFINITIONS = "$DIR/task_definitions.json"
    const val REGULATIONS = "$DIR/regulations.json"
    const val ROUND_TEMPLATES = "$DIR/round_templates.json"
    const val PLAN_PRESETS = "$DIR/plan_presets.json"
    const val SYMBOLS = "$DIR/symbols.json"
    const val DEMO_VESSEL = "$DIR/demo_vessel.json"
}

/** Every bundled seed document, parsed. */
data class SeedBundle(
    val equipmentTypes: List<EquipmentType>,
    val taskDefinitions: List<TaskDefinition>,
    val regulationCards: List<RegulationCard>,
    val roundTemplates: List<RoundTemplate>,
    val planPresets: List<PlanPreset>,
    val symbols: List<SymbolInfo>,
    val demoVessel: DemoVesselSeed,
)

// --- demo vessel DTO ---------------------------------------------------------
//
// JSON is static but "overdue" is relative to the moment the demo is loaded, so
// every date in demo_vessel.json is an INTEGER DAY OFFSET from load time.
// `...DaysAgo` counts backwards, `...DaysAhead` counts forwards, and a negative
// `nextDueDaysAhead` is an item that is already overdue when the demo loads.
// [SeedDataSource.buildDemoVessel] converts them to epoch-days.

@Serializable
data class DemoVesselSeed(
    val vessel: DemoVessel,
    val decks: List<DemoDeck> = emptyList(),
    val zones: List<DemoZone> = emptyList(),
    val equipment: List<DemoEquipment> = emptyList(),
    val deficiencies: List<DemoDeficiency> = emptyList(),
)

@Serializable
data class DemoVessel(
    val name: String,
    val imoNumber: String? = null,
    val callSign: String? = null,
    val mmsi: String? = null,
    val flag: FlagState = FlagState.OTHER,
    val classSociety: ClassSociety? = null,
    val vesselType: VesselType = VesselType.OTHER,
    val grossTonnage: Int? = null,
    val buildDaysAgo: Int? = null,
    val safetyEquipmentCertExpiryDaysAhead: Int? = null,
    val lastAnnualSurveyDaysAgo: Int? = null,
    val nextDrydockDaysAhead: Int? = null,
)

@Serializable
data class DemoDeck(
    /** Stable key used by [DemoEquipment.deckKey] and [DemoZone.deckKey]. */
    val key: String,
    val name: String,
    val shortCode: String? = null,
    val levelIndex: Int,
    /** Key into plan_presets.json. */
    val planPresetKey: String,
    val colorTint: Int? = null,
    val notes: String? = null,
)

@Serializable
data class DemoZone(
    val key: String,
    val deckKey: String,
    val name: String,
    val polygon: List<PlanPoint> = emptyList(),
    val colorArgb: Int,
    val sortOrder: Int = 0,
)

@Serializable
data class DemoEquipment(
    val key: String,
    val deckKey: String? = null,
    val zoneKey: String? = null,
    /** Key of another [DemoEquipment] — a lifeboat's engine, a liferaft's HRU. */
    val parentKey: String? = null,
    val typeKey: String,
    val symbolKey: String,
    val tag: String,
    val name: String? = null,
    val location: String? = null,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val rotationDeg: Float = 0f,
    val makerName: String? = null,
    val modelName: String? = null,
    val serialNumber: String? = null,
    val typeApprovalNumber: String? = null,
    val manufactureDaysAgo: Int? = null,
    val installedDaysAgo: Int? = null,
    val quantity: Int = 1,
    val condition: ConditionGrade = ConditionGrade.NOT_CHECKED,
    val conditionSetDaysAgo: Int? = null,
    val statusFlag: StatusFlag = StatusFlag.IN_SERVICE,
    /** Non-date attribute values, written straight into `attributesJson`. */
    val attributes: JsonObject = JsonObject(emptyMap()),
    /** Date-valued attributes as day offsets; converted to epoch-days on load. */
    val dateAttributesDaysAgo: Map<String, Int> = emptyMap(),
    /** Negative == already overdue when the demo is loaded. */
    val nextDueDaysAhead: Int? = null,
    val nextDueTaskKey: String? = null,
    val notes: String? = null,
)

@Serializable
data class DemoDeficiency(
    val key: String,
    val equipmentKey: String? = null,
    val raisedDaysAgo: Int,
    val raisedBy: String = "",
    val severity: Severity,
    val title: String,
    val description: String = "",
    val correctiveAction: String? = null,
    val targetDaysAhead: Int? = null,
    val status: DeficiencyStatus = DeficiencyStatus.OPEN,
    val sparePartRequired: String? = null,
)

/** The demo vessel materialised into core-model objects with fresh dates. */
data class DemoVesselData(
    val vessel: Vessel,
    val decks: List<Deck>,
    val zones: List<Zone>,
    val equipment: List<Equipment>,
    val deficiencies: List<Deficiency>,
)
