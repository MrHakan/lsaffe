package com.deckwatch.feature.notes

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.deckwatch.core.designsystem.components.RegulationCardLabels
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.RegulationSection

/**
 * `core-designsystem` carries no string resources, so the shared §8.2 card is localised from the
 * feature module — C8.
 */
@Composable
@ReadOnlyComposable
internal fun regulationCardLabels(): RegulationCardLabels = RegulationCardLabels(
    what = stringResource(R.string.notes_card_what),
    howOften = stringResource(R.string.notes_card_how_often),
    byWhom = stringResource(R.string.notes_card_by_whom),
    evidence = stringResource(R.string.notes_card_evidence),
    flagNotes = stringResource(R.string.notes_card_flag_notes),
    appliesTo = stringResource(R.string.notes_card_applies_to),
    verifyStrip = stringResource(R.string.notes_card_verify),
    revisionPrefix = stringResource(R.string.notes_card_captured),
)

@StringRes
internal fun sectionTitleRes(section: RegulationSection): Int = when (section) {
    RegulationSection.SOLAS -> R.string.notes_section_solas
    RegulationSection.LSA -> R.string.notes_section_lsa
    RegulationSection.FFE -> R.string.notes_section_ffe
    RegulationSection.HELIDECK -> R.string.notes_section_helideck
    RegulationSection.ISGOTT -> R.string.notes_section_isgott
    RegulationSection.IAMSAR -> R.string.notes_section_iamsar
    RegulationSection.FLAG -> R.string.notes_section_flag
    RegulationSection.CLASS -> R.string.notes_section_class
    RegulationSection.MY_NOTES -> R.string.notes_section_my_notes
}

@StringRes
internal fun sectionDescriptionRes(section: RegulationSection): Int = when (section) {
    RegulationSection.SOLAS -> R.string.notes_section_solas_desc
    RegulationSection.LSA -> R.string.notes_section_lsa_desc
    RegulationSection.FFE -> R.string.notes_section_ffe_desc
    RegulationSection.HELIDECK -> R.string.notes_section_helideck_desc
    RegulationSection.ISGOTT -> R.string.notes_section_isgott_desc
    RegulationSection.IAMSAR -> R.string.notes_section_iamsar_desc
    RegulationSection.FLAG -> R.string.notes_section_flag_desc
    RegulationSection.CLASS -> R.string.notes_section_class_desc
    RegulationSection.MY_NOTES -> R.string.notes_section_my_notes_desc
}

@StringRes
internal fun flagSubSectionRes(subSection: FlagSubSection?): Int = when (subSection) {
    FlagSubSection.RMI -> R.string.notes_flag_rmi
    FlagSubSection.LIBERIA -> R.string.notes_flag_liberia
    FlagSubSection.PANAMA -> R.string.notes_flag_panama
    null -> R.string.notes_flag_other
}

@StringRes
internal fun performedByRes(performedBy: PerformedBy): Int = when (performedBy) {
    PerformedBy.SHIP_STAFF -> R.string.notes_performed_ship_staff
    PerformedBy.SHIP_STAFF_TRAINED -> R.string.notes_performed_ship_staff_trained
    PerformedBy.AUTHORISED_SERVICE_PROVIDER -> R.string.notes_performed_service_provider
    PerformedBy.MANUFACTURER -> R.string.notes_performed_manufacturer
    PerformedBy.RO_SURVEYOR_ATTENDING -> R.string.notes_performed_ro_surveyor
    PerformedBy.SHORE_FACILITY -> R.string.notes_performed_shore_facility
}

/** Catalogue group names, for the equipment guide — §9.1. */
@StringRes
internal fun equipmentGroupLabel(group: EquipmentGroup): Int = when (group) {
    EquipmentGroup.LSA -> R.string.guide_group_lsa
    EquipmentGroup.FFE -> R.string.guide_group_ffe
    EquipmentGroup.EMERGENCY_ESCAPE -> R.string.guide_group_emergency
    EquipmentGroup.MACHINERY_CONTROLS -> R.string.guide_group_machinery
    EquipmentGroup.SIGNAGE -> R.string.guide_group_signage
    EquipmentGroup.OTHER -> R.string.guide_group_other
}

/** Human-readable cadence for the interval matrix — §8.3. */
@Composable
@ReadOnlyComposable
internal fun intervalLabel(kind: IntervalKind, months: Int?): String = when (kind) {
    IntervalKind.WEEKLY -> stringResource(R.string.notes_interval_weekly)
    IntervalKind.MONTHLY -> stringResource(R.string.notes_interval_monthly)
    IntervalKind.QUARTERLY -> stringResource(R.string.notes_interval_quarterly)
    IntervalKind.ANNUAL -> stringResource(R.string.notes_interval_annual)
    IntervalKind.BIENNIAL -> stringResource(R.string.notes_interval_biennial)
    IntervalKind.FIVE_YEARLY -> stringResource(R.string.notes_interval_five_yearly)
    IntervalKind.TEN_YEARLY -> stringResource(R.string.notes_interval_ten_yearly)
    IntervalKind.TWENTY_YEARLY -> stringResource(R.string.notes_interval_twenty_yearly)
    IntervalKind.AT_SURVEY -> stringResource(R.string.notes_interval_at_survey)
    IntervalKind.EVENT_DRIVEN -> stringResource(R.string.notes_interval_event_driven)
    IntervalKind.CUSTOM_MONTHS ->
        months?.let { stringResource(R.string.notes_interval_custom_months, it) }
            ?: stringResource(R.string.notes_interval_event_driven)
}
