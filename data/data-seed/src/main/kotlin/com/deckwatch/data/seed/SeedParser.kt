package com.deckwatch.data.seed

import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.SymbolInfo
import com.deckwatch.core.model.TaskDefinition
import kotlinx.serialization.json.Json

/**
 * Pure, testable parsing of the bundled seed documents — no Android dependency,
 * so the whole seed can be checked on the JVM.
 *
 * Unknown keys are ignored so that a content update which adds a field cannot
 * break an older build that has not yet learned about it.
 */
object SeedParser {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parseEquipmentTypes(text: String): List<EquipmentType> = json.decodeFromString(text)

    fun parseTaskDefinitions(text: String): List<TaskDefinition> = json.decodeFromString(text)

    fun parseRegulationCards(text: String): List<RegulationCard> = json.decodeFromString(text)

    fun parseRoundTemplates(text: String): List<RoundTemplate> = json.decodeFromString(text)

    fun parsePlanPresets(text: String): List<PlanPreset> = json.decodeFromString(text)

    fun parseSymbols(text: String): List<SymbolInfo> = json.decodeFromString(text)

    fun parseDemoVessel(text: String): DemoVesselSeed = json.decodeFromString(text)
}
