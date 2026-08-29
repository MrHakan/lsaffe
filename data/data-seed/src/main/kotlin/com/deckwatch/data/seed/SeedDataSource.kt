package com.deckwatch.data.seed

import android.content.Context
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.PlanShape
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.SymbolInfo
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads the bundled seed JSON out of the module's assets and hands back
 * core-model objects — §19.
 */
@Singleton
class SeedDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private suspend fun read(path: String): String = withContext(Dispatchers.IO) {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }

    suspend fun loadEquipmentTypes(): List<EquipmentType> =
        SeedParser.parseEquipmentTypes(read(SeedAssets.EQUIPMENT_CATALOGUE))

    suspend fun loadTaskDefinitions(): List<TaskDefinition> =
        SeedParser.parseTaskDefinitions(read(SeedAssets.TASK_DEFINITIONS))

    suspend fun loadRegulationCards(): List<RegulationCard> =
        SeedParser.parseRegulationCards(read(SeedAssets.REGULATIONS))

    suspend fun loadRoundTemplates(): List<RoundTemplate> =
        SeedParser.parseRoundTemplates(read(SeedAssets.ROUND_TEMPLATES))

    suspend fun loadPlanPresets(): List<PlanPreset> =
        SeedParser.parsePlanPresets(read(SeedAssets.PLAN_PRESETS))

    suspend fun loadSymbols(): List<SymbolInfo> =
        SeedParser.parseSymbols(read(SeedAssets.SYMBOLS))

    suspend fun loadDemoVesselSeed(): DemoVesselSeed =
        SeedParser.parseDemoVessel(read(SeedAssets.DEMO_VESSEL))

    /** Every seed document in one call — used by the first-run importer and by the tests. */
    suspend fun loadAll(): SeedBundle = SeedBundle(
        equipmentTypes = loadEquipmentTypes(),
        taskDefinitions = loadTaskDefinitions(),
        regulationCards = loadRegulationCards(),
        roundTemplates = loadRoundTemplates(),
        planPresets = loadPlanPresets(),
        symbols = loadSymbols(),
        demoVessel = loadDemoVesselSeed(),
    )

    /**
     * Materialise the demo vessel with dates relative to [todayEpochDay], so the
     * overdue items in the seed are still overdue whenever the demo is loaded.
     */
    suspend fun buildDemoVessel(todayEpochDay: Long, nowMillis: Long): DemoVesselData =
        buildDemoVessel(loadDemoVesselSeed(), loadPlanPresets(), todayEpochDay, nowMillis)

    companion object {

        private const val DEMO_NAMESPACE = "deckwatch-demo-vessel"

        /** Deterministic ids so a reload of the demo merges instead of duplicating. */
        internal fun demoId(vararg parts: String): String =
            UUID.nameUUIDFromBytes(
                (DEMO_NAMESPACE + "/" + parts.joinToString("/")).toByteArray(),
            ).toString()

        /** Pure materialisation — no Android dependency, so it is unit testable. */
        fun buildDemoVessel(
            seed: DemoVesselSeed,
            presets: List<PlanPreset>,
            todayEpochDay: Long,
            nowMillis: Long,
        ): DemoVesselData {
            val planByKey = presets.associateBy { it.key }
            val vesselId = demoId("vessel", seed.vessel.name)

            fun daysAgo(offset: Int?): Long? = offset?.let { todayEpochDay - it }
            fun daysAhead(offset: Int?): Long? = offset?.let { todayEpochDay + it }

            val vessel = Vessel(
                id = vesselId,
                name = seed.vessel.name,
                imoNumber = seed.vessel.imoNumber,
                callSign = seed.vessel.callSign,
                mmsi = seed.vessel.mmsi,
                flag = seed.vessel.flag,
                classSociety = seed.vessel.classSociety,
                vesselType = seed.vessel.vesselType,
                grossTonnage = seed.vessel.grossTonnage,
                buildDate = daysAgo(seed.vessel.buildDaysAgo),
                safetyEquipmentCertExpiry =
                    daysAhead(seed.vessel.safetyEquipmentCertExpiryDaysAhead),
                lastAnnualSurveyDate = daysAgo(seed.vessel.lastAnnualSurveyDaysAgo),
                nextDrydockDate = daysAhead(seed.vessel.nextDrydockDaysAhead),
                isActive = true,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )

            val deckIds = seed.decks.associate { it.key to demoId("deck", it.key) }
            val decks = seed.decks.map { d ->
                Deck(
                    id = deckIds.getValue(d.key),
                    vesselId = vesselId,
                    name = d.name,
                    shortCode = d.shortCode,
                    levelIndex = d.levelIndex,
                    plan = planByKey[d.planPresetKey]?.plan ?: FALLBACK_PLAN,
                    colorTint = d.colorTint,
                    notes = d.notes,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                )
            }

            val zoneIds = seed.zones.associate { it.key to demoId("zone", it.key) }
            val zones = seed.zones.map { z ->
                Zone(
                    id = zoneIds.getValue(z.key),
                    deckId = deckIds.getValue(z.deckKey),
                    name = z.name,
                    polygon = z.polygon,
                    colorArgb = z.colorArgb,
                    sortOrder = z.sortOrder,
                )
            }

            val equipmentIds = seed.equipment.associate { it.key to demoId("equipment", it.key) }
            val equipment = seed.equipment.map { e ->
                Equipment(
                    id = equipmentIds.getValue(e.key),
                    vesselId = vesselId,
                    deckId = e.deckKey?.let { deckIds[it] },
                    zoneId = e.zoneKey?.let { zoneIds[it] },
                    parentId = e.parentKey?.let { equipmentIds[it] },
                    typeKey = e.typeKey,
                    symbolKey = e.symbolKey,
                    tag = e.tag,
                    name = e.name,
                    location = e.location,
                    posX = e.posX,
                    posY = e.posY,
                    rotationDeg = e.rotationDeg,
                    makerName = e.makerName,
                    modelName = e.modelName,
                    serialNumber = e.serialNumber,
                    typeApprovalNumber = e.typeApprovalNumber,
                    manufactureDate = daysAgo(e.manufactureDaysAgo),
                    installedDate = daysAgo(e.installedDaysAgo),
                    quantity = e.quantity,
                    condition = e.condition,
                    conditionSetAt = e.conditionSetDaysAgo
                        ?.let { nowMillis - it * MILLIS_PER_DAY },
                    statusFlag = e.statusFlag,
                    attributesJson = attributesJson(e, todayEpochDay),
                    nextDueDate = daysAhead(e.nextDueDaysAhead),
                    nextDueTaskKey = e.nextDueTaskKey,
                    notes = e.notes,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                )
            }

            val deficiencies = seed.deficiencies.map { d ->
                Deficiency(
                    id = demoId("deficiency", d.key),
                    vesselId = vesselId,
                    equipmentId = d.equipmentKey?.let { equipmentIds[it] },
                    raisedDate = todayEpochDay - d.raisedDaysAgo,
                    raisedBy = d.raisedBy,
                    severity = d.severity,
                    title = d.title,
                    description = d.description,
                    correctiveAction = d.correctiveAction,
                    targetDate = daysAhead(d.targetDaysAhead),
                    status = d.status,
                    sparePartRequired = d.sparePartRequired,
                )
            }

            return DemoVesselData(vessel, decks, zones, equipment, deficiencies)
        }

        private const val MILLIS_PER_DAY = 86_400_000L

        private val FALLBACK_PLAN = DeckPlan(shape = PlanShape.RECTANGLE)

        /** Merge the literal attribute values with the date offsets resolved to epoch-days. */
        private fun attributesJson(e: DemoEquipment, todayEpochDay: Long): String {
            val merged = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
            merged.putAll(e.attributes)
            e.dateAttributesDaysAgo.forEach { (key, daysAgo) ->
                merged[key] = JsonPrimitive(todayEpochDay - daysAgo)
            }
            return SeedParser.json.encodeToString(
                JsonObject.serializer(),
                JsonObject(merged),
            )
        }
    }
}
