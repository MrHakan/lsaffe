package com.deckwatch.core.designsystem.components

import com.deckwatch.core.model.ConditionGrade

/** Localised labels for the five grades (core-designsystem carries no strings). */
data class ConditionLabels(
    val good: String = "Good",
    val acceptable: String = "Acceptable",
    val monitor: String = "Monitor",
    val defective: String = "Defective",
    val outOfService: String = "Out of service",
    val notChecked: String = "Not checked",
) {
    fun of(grade: ConditionGrade): String = when (grade) {
        ConditionGrade.GOOD -> good
        ConditionGrade.ACCEPTABLE -> acceptable
        ConditionGrade.MONITOR -> monitor
        ConditionGrade.DEFECTIVE -> defective
        ConditionGrade.OUT_OF_SERVICE -> outOfService
        ConditionGrade.NOT_CHECKED -> notChecked
    }
}
