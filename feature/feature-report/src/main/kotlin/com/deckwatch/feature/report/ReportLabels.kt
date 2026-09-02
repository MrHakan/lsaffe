package com.deckwatch.feature.report

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.TaskStatus

/**
 * The localised chrome of a rendered report.
 *
 * [HtmlReportRenderer] stays pure by taking its words as data — exactly the pattern
 * `DueExportLabels` established for the clipboard export. The composable fills this from
 * `strings.xml`, so a report comes out in the officer's language (C8) while the renderer itself
 * has no Android dependency and is unit-testable on the JVM.
 *
 * Every field defaults to English, so a caller that only cares about the structure — a test, a
 * headless export — can construct `ReportLabels()` and get a complete document.
 */
@Suppress("LongParameterList") // A label bundle: every field is a string with a default.
data class ReportLabels(
    val documentTitle: String = "DeckWatch report",
    val scopeNames: Map<ExportScope, String> = DEFAULT_SCOPE_NAMES,
    // Header block — §13.4
    val imo: String = "IMO",
    val callSign: String = "Call sign",
    val flag: String = "Flag",
    val classSociety: String = "Class",
    val vesselTypeLabel: String = "Type",
    val grossTonnage: String = "GT",
    val generated: String = "Generated",
    val appVersion: String = "App version",
    val certExpiry: String = "SEC expiry",
    val lastAnnualSurvey: String = "Last annual survey",
    val nextDrydock: String = "Next dry dock",
    // Summary strip
    val summaryEquipment: String = "Items",
    val summaryOverdue: String = "Overdue",
    val summaryDueSoon: String = "Due soon",
    val summaryPlanned: String = "Planned",
    val summaryOpenDeficiencies: String = "Open deficiencies",
    // Section headings
    val sectionParticulars: String = "Vessel particulars",
    val sectionRegister: String = "Equipment register",
    val sectionDecks: String = "Deck plans",
    val sectionDue: String = "Due list",
    val sectionRound: String = "Inspection round",
    val sectionRoundItems: String = "Round items",
    val sectionDeficiencies: String = "Deficiencies",
    val sectionOpenDeficiencies: String = "Open deficiencies",
    val sectionRounds: String = "Inspection rounds",
    val sectionCertificates: String = "Certificate status",
    val sectionLegend: String = "Legend",
    val sectionNotes: String = "Notes",
    // Table headings
    val colNo: String = "No.",
    val colTag: String = "Tag",
    val colName: String = "Name",
    val colType: String = "Type",
    val colDeck: String = "Deck",
    val colZone: String = "Zone",
    val colLocation: String = "Location",
    val colMaker: String = "Maker",
    val colModel: String = "Model",
    val colSerial: String = "Serial",
    val colApproval: String = "Approval no.",
    val colQuantity: String = "Qty",
    val colCondition: String = "Condition",
    val colStatus: String = "Status",
    val colNextDue: String = "Next due",
    val colTask: String = "Task",
    val colDue: String = "Due",
    val colDays: String = "Days",
    val colBy: String = "By",
    val colSeverity: String = "Severity",
    val colRaised: String = "Raised",
    val colTarget: String = "Target",
    val colTitle: String = "Title",
    val colRemark: String = "Remark",
    val colChecked: String = "Checked",
    val colStarted: String = "Started",
    val colCompleted: String = "Completed",
    val colItems: String = "Items",
    val colPerformedBy: String = "Performed by",
    val colFindings: String = "Findings",
    // Round report
    val roundPerformedBy: String = "Performed by",
    val roundStarted: String = "Started",
    val roundCompleted: String = "Completed",
    val roundNotes: String = "Round notes",
    val signatureInspected: String = "Inspected by",
    val signatureVerified: String = "Verified by",
    val signatureDate: String = "Date",
    val signatureRank: String = "Rank",
    // Deficiency report
    val deficiencyCorrectiveAction: String = "Corrective action",
    val deficiencySparePart: String = "Spare part required",
    val deficiencyClosed: String = "Closed",
    // Deck sheet
    val deckLevel: String = "Level",
    val deckPlanCaption: String = "Flat plan, bow at top unless marked otherwise.",
    // Controls (JS-only)
    val filterPlaceholder: String = "Filter…",
    val tabAllDecks: String = "All decks",
    // Misc
    val none: String = "None",
    val empty: String = "Nothing to report in this scope.",
    val photoUnavailable: String = "Photo unavailable",
    val total: String = "Total",
    val filtersLabel: String = "Filters",
    val segmentLabel: String = "Segment",
    val bundledTasks: String = "Bundled task definitions relied on",
    val disclaimerHeading: String = "Disclaimer",
    // Enum display names
    val conditionNames: Map<ConditionGrade, String> = DEFAULT_CONDITION_NAMES,
    val taskStatusNames: Map<TaskStatus, String> = DEFAULT_TASK_STATUS_NAMES,
    val severityNames: Map<Severity, String> = DEFAULT_SEVERITY_NAMES,
    val deficiencyStatusNames: Map<DeficiencyStatus, String> = DEFAULT_DEFICIENCY_STATUS_NAMES,
    val performedByNames: Map<PerformedBy, String> = DEFAULT_PERFORMED_BY_NAMES,
) {
    fun scopeName(scope: ExportScope): String = scopeNames[scope] ?: scope.name
    fun condition(grade: ConditionGrade): String = conditionNames[grade] ?: grade.name
    fun taskStatus(status: TaskStatus): String = taskStatusNames[status] ?: status.name
    fun severity(severity: Severity): String = severityNames[severity] ?: severity.name
    fun deficiencyStatus(status: DeficiencyStatus): String = deficiencyStatusNames[status] ?: status.name
    fun performer(performedBy: PerformedBy): String = performedByNames[performedBy] ?: performedBy.name

    companion object {
        val DEFAULT_SCOPE_NAMES: Map<ExportScope, String> = mapOf(
            ExportScope.FULL_BACKUP to "Full vessel backup",
            ExportScope.DUE_LIST to "Due list",
            ExportScope.ROUND_REPORT to "Inspection round report",
            ExportScope.DEFICIENCY_REPORT to "Deficiency report",
            ExportScope.DECK_SHEET to "Deck sheet",
            ExportScope.PSC_SURVEY_PACK to "PSC / survey pack",
        )
        val DEFAULT_CONDITION_NAMES: Map<ConditionGrade, String> = mapOf(
            ConditionGrade.GOOD to "Good",
            ConditionGrade.ACCEPTABLE to "Acceptable",
            ConditionGrade.MONITOR to "Monitor",
            ConditionGrade.DEFECTIVE to "Defective",
            ConditionGrade.OUT_OF_SERVICE to "Out of service",
            ConditionGrade.NOT_CHECKED to "Not checked",
        )
        val DEFAULT_TASK_STATUS_NAMES: Map<TaskStatus, String> = mapOf(
            TaskStatus.PENDING to "Planned",
            TaskStatus.DUE_SOON to "Due soon",
            TaskStatus.OVERDUE to "Overdue",
            TaskStatus.DONE to "Done",
            TaskStatus.SKIPPED to "Skipped",
            TaskStatus.NOT_APPLICABLE to "Not applicable",
        )
        val DEFAULT_SEVERITY_NAMES: Map<Severity, String> = mapOf(
            Severity.OBSERVATION to "Observation",
            Severity.MINOR to "Minor",
            Severity.MAJOR to "Major",
            Severity.CRITICAL_DETAINABLE to "Critical / detainable",
        )
        val DEFAULT_DEFICIENCY_STATUS_NAMES: Map<DeficiencyStatus, String> = mapOf(
            DeficiencyStatus.OPEN to "Open",
            DeficiencyStatus.IN_PROGRESS to "In progress",
            DeficiencyStatus.CLOSED to "Closed",
            DeficiencyStatus.DEFERRED_TO_OFFICE to "Deferred to office",
        )
        val DEFAULT_PERFORMED_BY_NAMES: Map<PerformedBy, String> = mapOf(
            PerformedBy.SHIP_STAFF to "Ship's staff",
            PerformedBy.SHIP_STAFF_TRAINED to "Trained ship's staff",
            PerformedBy.AUTHORISED_SERVICE_PROVIDER to "Authorised service provider",
            PerformedBy.MANUFACTURER to "Manufacturer",
            PerformedBy.RO_SURVEYOR_ATTENDING to "RO surveyor attending",
            PerformedBy.SHORE_FACILITY to "Shore facility",
        )
    }
}
