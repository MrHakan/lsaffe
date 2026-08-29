package com.deckwatch.data.seed

import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.model.VerificationStatus

/**
 * Cross-document validation of the bundled seed — §8.5, §19.
 *
 * Returns human-readable problems rather than throwing, so a content update can
 * be checked in a test and reported in full instead of failing on the first
 * broken reference.
 */
object SeedIntegrity {

    const val MIN_REGULATION_CARDS = 120
    const val MIN_EQUIPMENT_TYPES = 70

    private val FLAG_CODES = setOf("RMI", "LIB", "PAN")

    @Suppress("CyclomaticComplexMethod")
    fun validate(bundle: SeedBundle): List<String> {
        val problems = mutableListOf<String>()

        val typeKeys = bundle.equipmentTypes.map { it.typeKey }
        val typeKeySet = typeKeys.toSet()
        val taskKeys = bundle.taskDefinitions.map { it.key }.toSet()
        val symbolKeys = bundle.symbols.map { it.key }.toSet()
        val refKeys = bundle.regulationCards.map { it.refKey }.toSet()

        if (bundle.equipmentTypes.size < MIN_EQUIPMENT_TYPES) {
            problems += "equipment catalogue has ${bundle.equipmentTypes.size} types, " +
                "the minimum bar is $MIN_EQUIPMENT_TYPES"
        }
        if (bundle.regulationCards.size < MIN_REGULATION_CARDS) {
            problems += "regulations have ${bundle.regulationCards.size} cards, " +
                "the minimum bar is $MIN_REGULATION_CARDS"
        }
        duplicatesOf(typeKeys).forEach { problems += "duplicate equipment typeKey '$it'" }
        duplicatesOf(bundle.taskDefinitions.map { it.key })
            .forEach { problems += "duplicate task key '$it'" }
        duplicatesOf(bundle.regulationCards.map { it.refKey })
            .forEach { problems += "duplicate regulation refKey '$it'" }
        duplicatesOf(bundle.symbols.map { it.key })
            .forEach { problems += "duplicate symbol key '$it'" }
        duplicatesOf(bundle.planPresets.map { it.key })
            .forEach { problems += "duplicate plan preset key '$it'" }
        duplicatesOf(bundle.roundTemplates.map { it.key })
            .forEach { problems += "duplicate round template key '$it'" }

        // --- catalogue -> tasks / symbols / regulations
        bundle.equipmentTypes.forEach { type ->
            if (type.symbolKey !in symbolKeys) {
                problems += "equipment type '${type.typeKey}' uses symbolKey " +
                    "'${type.symbolKey}' which is not in symbols.json"
            }
            type.taskKeys.filterNot { it in taskKeys }.forEach {
                problems += "equipment type '${type.typeKey}' references task '$it' " +
                    "which is not defined in task_definitions.json"
            }
            type.regulationRefs.filterNot { it in refKeys }.forEach {
                problems += "equipment type '${type.typeKey}' references regulation '$it' " +
                    "which is not defined in regulations.json"
            }
            type.attributeSchema.forEach { attribute ->
                attribute.taskKeysByValue.forEach { (value, keys) ->
                    keys.filterNot { it in taskKeys }.forEach {
                        problems += "equipment type '${type.typeKey}' attribute " +
                            "'${attribute.key}' maps value '$value' to task '$it' " +
                            "which is not defined in task_definitions.json"
                    }
                }
            }
        }

        // --- tasks -> catalogue / regulations / flag overlays
        bundle.taskDefinitions.forEach { task ->
            task.appliesToTypeKeys.filterNot { it in typeKeySet }.forEach {
                problems += "task '${task.key}' applies to type '$it' which is not in the " +
                    "equipment catalogue"
            }
            task.regulationRefs.filterNot { it in refKeys }.forEach {
                problems += "task '${task.key}' references regulation '$it' which is not " +
                    "defined in regulations.json"
            }
            task.flagOverrides?.keys?.filterNot { it in FLAG_CODES }?.forEach {
                problems += "task '${task.key}' has flag override '$it' which is not one of " +
                    FLAG_CODES.joinToString("/")
            }
            if (task.sourceRef.isBlank()) {
                problems += "task '${task.key}' has no sourceRef"
            }
            if (task.lastReviewed.isBlank()) {
                problems += "task '${task.key}' has no lastReviewed date"
            }
        }

        // --- regulation cards
        bundle.regulationCards.forEach { card ->
            if (card.section == RegulationSection.MY_NOTES) {
                problems += "regulation card '${card.refKey}' is seeded into MY_NOTES, which " +
                    "is reserved for the user's own content"
            }
            card.appliesToTypeKeys.filterNot { it in typeKeySet }.forEach {
                problems += "regulation card '${card.refKey}' applies to type '$it' which is " +
                    "not in the equipment catalogue"
            }
            card.flagNotes.keys.filterNot { it in FLAG_CODES }.forEach {
                problems += "regulation card '${card.refKey}' has flag note '$it' which is " +
                    "not one of " + FLAG_CODES.joinToString("/")
            }
            listOf(
                "citation" to card.citation,
                "what" to card.what,
                "howOften" to card.howOften,
                "byWhom" to card.byWhom,
                "evidence" to card.evidence,
                "sourceRef" to card.sourceRef,
                "lastReviewed" to card.lastReviewed,
            ).filter { it.second.isBlank() }.forEach { (field, _) ->
                problems += "regulation card '${card.refKey}' has an empty $field"
            }
            if (card.section == RegulationSection.FLAG) {
                if (card.verificationStatus != VerificationStatus.NEEDS_PERIODIC_REVIEW) {
                    problems += "flag card '${card.refKey}' must be NEEDS_PERIODIC_REVIEW " +
                        "because flag notices change"
                }
                if (card.revisionNote.isBlank()) {
                    problems += "flag card '${card.refKey}' has no revisionNote naming the " +
                        "notice and its capture date"
                }
            }
        }

        // --- round templates and plan presets
        bundle.roundTemplates.forEach { template ->
            template.includesTypeKeys.filterNot { it in typeKeySet }.forEach {
                problems += "round template '${template.key}' includes type '$it' which is " +
                    "not in the equipment catalogue"
            }
        }

        problems += validateDemo(bundle, typeKeySet, symbolKeys, taskKeys)
        return problems
    }

    private fun validateDemo(
        bundle: SeedBundle,
        typeKeys: Set<String>,
        symbolKeys: Set<String>,
        taskKeys: Set<String>,
    ): List<String> {
        val problems = mutableListOf<String>()
        val demo = bundle.demoVessel
        val presetKeys = bundle.planPresets.map { it.key }.toSet()
        val deckKeys = demo.decks.map { it.key }.toSet()
        val zoneKeys = demo.zones.map { it.key }.toSet()
        val equipmentKeys = demo.equipment.map { it.key }.toSet()

        duplicatesOf(demo.equipment.map { it.key })
            .forEach { problems += "duplicate demo equipment key '$it'" }
        duplicatesOf(demo.decks.map { it.key })
            .forEach { problems += "duplicate demo deck key '$it'" }

        demo.decks.filterNot { it.planPresetKey in presetKeys }.forEach {
            problems += "demo deck '${it.key}' uses plan preset '${it.planPresetKey}' which " +
                "is not in plan_presets.json"
        }
        demo.zones.filterNot { it.deckKey in deckKeys }.forEach {
            problems += "demo zone '${it.key}' is on deck '${it.deckKey}' which does not exist"
        }
        demo.equipment.forEach { item ->
            if (item.typeKey !in typeKeys) {
                problems += "demo equipment '${item.key}' has typeKey '${item.typeKey}' which " +
                    "is not in the equipment catalogue"
            }
            if (item.symbolKey !in symbolKeys) {
                problems += "demo equipment '${item.key}' has symbolKey '${item.symbolKey}' " +
                    "which is not in symbols.json"
            }
            if (item.deckKey != null && item.deckKey !in deckKeys) {
                problems += "demo equipment '${item.key}' is on deck '${item.deckKey}' which " +
                    "does not exist"
            }
            if (item.zoneKey != null && item.zoneKey !in zoneKeys) {
                problems += "demo equipment '${item.key}' is in zone '${item.zoneKey}' which " +
                    "does not exist"
            }
            if (item.parentKey != null && item.parentKey !in equipmentKeys) {
                problems += "demo equipment '${item.key}' has parent '${item.parentKey}' which " +
                    "does not resolve"
            }
            if (item.parentKey == item.key) {
                problems += "demo equipment '${item.key}' is its own parent"
            }
            if (item.nextDueTaskKey != null && item.nextDueTaskKey !in taskKeys) {
                problems += "demo equipment '${item.key}' is due task " +
                    "'${item.nextDueTaskKey}' which is not defined in task_definitions.json"
            }
            if (item.posX !in 0f..1f || item.posY !in 0f..1f) {
                problems += "demo equipment '${item.key}' sits outside the 0..1 plan space"
            }
        }
        demo.deficiencies.forEach { deficiency ->
            if (deficiency.equipmentKey != null && deficiency.equipmentKey !in equipmentKeys) {
                problems += "demo deficiency '${deficiency.key}' points at equipment " +
                    "'${deficiency.equipmentKey}' which does not resolve"
            }
        }
        return problems
    }

    private fun duplicatesOf(values: List<String>): List<String> =
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList()
}
