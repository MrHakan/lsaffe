package com.deckwatch.core.designsystem.components

/**
 * UI labels for [RegulationCardView] so feature modules can localise them
 * (core-designsystem carries no string resources).
 */
data class RegulationCardLabels(
    val what: String = "WHAT",
    val howOften: String = "HOW OFTEN",
    val byWhom: String = "BY WHOM",
    val evidence: String = "EVIDENCE",
    val flagNotes: String = "FLAG NOTES",
    val appliesTo: String = "Applies to",
    val verifyStrip: String = "Verify against the current instrument",
    val revisionPrefix: String = "Captured",
)
