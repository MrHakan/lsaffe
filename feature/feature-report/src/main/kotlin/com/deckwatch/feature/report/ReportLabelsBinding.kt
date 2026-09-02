package com.deckwatch.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.TaskStatus

/**
 * Fills [ReportLabels] from `strings.xml`, so an exported report comes out in the officer's
 * language — C8, and the pattern `DueExportLabels` set for the clipboard export.
 *
 * The renderer itself stays pure: it never sees a `Context`. This is the only place the two
 * meet, and it is a composable so the values follow a language change without a restart.
 *
 * The §17.6 disclaimer is deliberately **not** here. It is a verbatim English constant
 * ([REPORT_DISCLAIMER]) in every report, whatever the app language: a translated disclaimer is
 * a different disclaimer.
 */
@Composable
fun rememberReportLabels(): ReportLabels = ReportLabels(
    documentTitle = stringResource(R.string.report_document_title),
    imo = stringResource(R.string.report_imo),
    callSign = stringResource(R.string.report_call_sign),
    flag = stringResource(R.string.report_flag),
    classSociety = stringResource(R.string.report_class),
    vesselTypeLabel = stringResource(R.string.report_vessel_type),
    grossTonnage = stringResource(R.string.report_gross_tonnage),
    generated = stringResource(R.string.report_generated),
    appVersion = stringResource(R.string.report_app_version),
    certExpiry = stringResource(R.string.report_cert_expiry),
    lastAnnualSurvey = stringResource(R.string.report_last_annual_survey),
    nextDrydock = stringResource(R.string.report_next_drydock),
    summaryEquipment = stringResource(R.string.report_summary_equipment),
    summaryOverdue = stringResource(R.string.report_summary_overdue),
    summaryDueSoon = stringResource(R.string.report_summary_due_soon),
    summaryPlanned = stringResource(R.string.report_summary_planned),
    summaryOpenDeficiencies = stringResource(R.string.report_summary_open_deficiencies),
    sectionRegister = stringResource(R.string.report_section_register),
    sectionDecks = stringResource(R.string.report_section_decks),
    sectionDue = stringResource(R.string.report_section_due),
    sectionRound = stringResource(R.string.report_section_round),
    sectionRoundItems = stringResource(R.string.report_section_round_items),
    sectionDeficiencies = stringResource(R.string.report_section_deficiencies),
    sectionOpenDeficiencies = stringResource(R.string.report_section_open_deficiencies),
    sectionRounds = stringResource(R.string.report_section_rounds),
    sectionCertificates = stringResource(R.string.report_section_certificates),
    sectionLegend = stringResource(R.string.report_section_legend),
    sectionNotes = stringResource(R.string.report_section_notes),
    colNo = stringResource(R.string.report_col_no),
    colTag = stringResource(R.string.report_col_tag),
    colName = stringResource(R.string.report_col_name),
    colType = stringResource(R.string.report_col_type),
    colDeck = stringResource(R.string.report_col_deck),
    colZone = stringResource(R.string.report_col_zone),
    colLocation = stringResource(R.string.report_col_location),
    colMaker = stringResource(R.string.report_col_maker),
    colModel = stringResource(R.string.report_col_model),
    colSerial = stringResource(R.string.report_col_serial),
    colApproval = stringResource(R.string.report_col_approval),
    colQuantity = stringResource(R.string.report_col_quantity),
    colCondition = stringResource(R.string.report_col_condition),
    colStatus = stringResource(R.string.report_col_status),
    colNextDue = stringResource(R.string.report_col_next_due),
    colTask = stringResource(R.string.report_col_task),
    colDue = stringResource(R.string.report_col_due),
    colDays = stringResource(R.string.report_col_days),
    colBy = stringResource(R.string.report_col_by),
    colRaised = stringResource(R.string.report_col_raised),
    colTarget = stringResource(R.string.report_col_target),
    colTitle = stringResource(R.string.report_col_title),
    colRemark = stringResource(R.string.report_col_remark),
    colChecked = stringResource(R.string.report_col_checked),
    colStarted = stringResource(R.string.report_col_started),
    colCompleted = stringResource(R.string.report_col_completed),
    colItems = stringResource(R.string.report_col_items),
    colPerformedBy = stringResource(R.string.report_col_performed_by),
    colFindings = stringResource(R.string.report_col_findings),
    roundPerformedBy = stringResource(R.string.report_round_performed_by),
    roundStarted = stringResource(R.string.report_round_started),
    roundCompleted = stringResource(R.string.report_round_completed),
    roundNotes = stringResource(R.string.report_round_notes),
    signatureInspected = stringResource(R.string.report_signature_inspected),
    signatureVerified = stringResource(R.string.report_signature_verified),
    signatureDate = stringResource(R.string.report_signature_date),
    signatureRank = stringResource(R.string.report_signature_rank),
    deficiencyCorrectiveAction = stringResource(R.string.report_deficiency_corrective_action),
    deficiencySparePart = stringResource(R.string.report_deficiency_spare_part),
    deficiencyClosed = stringResource(R.string.report_deficiency_closed),
    deckLevel = stringResource(R.string.report_deck_level),
    deckPlanCaption = stringResource(R.string.report_deck_plan_caption),
    filterPlaceholder = stringResource(R.string.report_filter_placeholder),
    tabAllDecks = stringResource(R.string.report_tab_all_decks),
    empty = stringResource(R.string.report_empty),
    photoUnavailable = stringResource(R.string.report_photo_unavailable),
    total = stringResource(R.string.report_total),
    segmentLabel = stringResource(R.string.report_segment),
    scopeNames = mapOf(
        ExportScope.FULL_BACKUP to stringResource(R.string.report_scope_full),
        ExportScope.DUE_LIST to stringResource(R.string.report_scope_due),
        ExportScope.ROUND_REPORT to stringResource(R.string.report_scope_round),
        ExportScope.DEFICIENCY_REPORT to stringResource(R.string.report_scope_deficiency),
        ExportScope.DECK_SHEET to stringResource(R.string.report_scope_deck),
        ExportScope.PSC_SURVEY_PACK to stringResource(R.string.report_scope_psc),
    ),
    conditionNames = mapOf(
        ConditionGrade.GOOD to stringResource(R.string.report_condition_good),
        ConditionGrade.ACCEPTABLE to stringResource(R.string.report_condition_acceptable),
        ConditionGrade.MONITOR to stringResource(R.string.report_condition_monitor),
        ConditionGrade.DEFECTIVE to stringResource(R.string.report_condition_defective),
        ConditionGrade.OUT_OF_SERVICE to stringResource(R.string.report_condition_out_of_service),
        ConditionGrade.NOT_CHECKED to stringResource(R.string.report_condition_not_checked),
    ),
    taskStatusNames = mapOf(
        TaskStatus.PENDING to stringResource(R.string.report_task_pending),
        TaskStatus.DUE_SOON to stringResource(R.string.report_task_due_soon),
        TaskStatus.OVERDUE to stringResource(R.string.report_task_overdue),
        TaskStatus.DONE to stringResource(R.string.report_task_done),
        TaskStatus.SKIPPED to stringResource(R.string.report_task_skipped),
        TaskStatus.NOT_APPLICABLE to stringResource(R.string.report_task_not_applicable),
    ),
    severityNames = mapOf(
        Severity.OBSERVATION to stringResource(R.string.report_severity_observation),
        Severity.MINOR to stringResource(R.string.report_severity_minor),
        Severity.MAJOR to stringResource(R.string.report_severity_major),
        Severity.CRITICAL_DETAINABLE to stringResource(R.string.report_severity_critical),
    ),
    deficiencyStatusNames = mapOf(
        DeficiencyStatus.OPEN to stringResource(R.string.report_deficiency_open),
        DeficiencyStatus.IN_PROGRESS to stringResource(R.string.report_deficiency_in_progress),
        DeficiencyStatus.CLOSED to stringResource(R.string.report_deficiency_status_closed),
        DeficiencyStatus.DEFERRED_TO_OFFICE to stringResource(R.string.report_deficiency_deferred),
    ),
    performedByNames = mapOf(
        PerformedBy.SHIP_STAFF to stringResource(R.string.report_by_ship_staff),
        PerformedBy.SHIP_STAFF_TRAINED to stringResource(R.string.report_by_ship_staff_trained),
        PerformedBy.AUTHORISED_SERVICE_PROVIDER to stringResource(R.string.report_by_service_provider),
        PerformedBy.MANUFACTURER to stringResource(R.string.report_by_manufacturer),
        PerformedBy.RO_SURVEYOR_ATTENDING to stringResource(R.string.report_by_ro_surveyor),
        PerformedBy.SHORE_FACILITY to stringResource(R.string.report_by_shore_facility),
    ),
)

/** One-line description of each scope, for the chooser cards — §13.3. */
@Composable
fun scopeDescription(scope: ExportScope): String = when (scope) {
    ExportScope.FULL_BACKUP -> stringResource(R.string.report_scope_full_desc)
    ExportScope.DUE_LIST -> stringResource(R.string.report_scope_due_desc)
    ExportScope.ROUND_REPORT -> stringResource(R.string.report_scope_round_desc)
    ExportScope.DEFICIENCY_REPORT -> stringResource(R.string.report_scope_deficiency_desc)
    ExportScope.DECK_SHEET -> stringResource(R.string.report_scope_deck_desc)
    ExportScope.PSC_SURVEY_PACK -> stringResource(R.string.report_scope_psc_desc)
}

/** The localised name of a record kind, for the import preview — §13.5. */
@Composable
fun recordKindName(kind: RecordKind): String = when (kind) {
    RecordKind.VESSEL -> stringResource(R.string.import_kind_vessel)
    RecordKind.DECK -> stringResource(R.string.import_kind_deck)
    RecordKind.ZONE -> stringResource(R.string.import_kind_zone)
    RecordKind.CATEGORY -> stringResource(R.string.import_kind_category)
    RecordKind.USER_TYPE -> stringResource(R.string.import_kind_user_type)
    RecordKind.TASK_DEFINITION -> stringResource(R.string.import_kind_task_definition)
    RecordKind.EQUIPMENT -> stringResource(R.string.import_kind_equipment)
    RecordKind.CATEGORY_LINK -> stringResource(R.string.import_kind_category_link)
    RecordKind.TASK_INSTANCE -> stringResource(R.string.import_kind_task_instance)
    RecordKind.ROUND -> stringResource(R.string.import_kind_round)
    RecordKind.ROUND_ITEM -> stringResource(R.string.import_kind_round_item)
    RecordKind.DEFICIENCY -> stringResource(R.string.import_kind_deficiency)
    RecordKind.USER_NOTE -> stringResource(R.string.import_kind_user_note)
}

/** The localised sentence for a parse failure — §13.5, §17.4. */
@Composable
fun importFailureMessage(failure: ImportFailure): String = when (failure) {
    ImportFailure.EMPTY_FILE -> stringResource(R.string.import_fail_empty)
    ImportFailure.NO_DATA_BLOCK -> stringResource(R.string.import_fail_no_block)
    ImportFailure.TRUNCATED_FILE -> stringResource(R.string.import_fail_truncated)
    ImportFailure.MALFORMED_JSON -> stringResource(R.string.import_fail_malformed)
    ImportFailure.UNSUPPORTED_SCHEMA_VERSION -> stringResource(R.string.import_fail_schema)
    ImportFailure.UNREADABLE -> stringResource(R.string.import_fail_unreadable)
}
