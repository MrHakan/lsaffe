package com.deckwatch.feature.vessel.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.deckwatch.core.model.ClassSociety
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.VesselType
import com.deckwatch.feature.vessel.R

/**
 * Every enum the vessel feature shows resolves to a string resource — C8 keeps all user-facing
 * text in `values/` and `values-tr/`, never in a `when` branch that hard-codes English.
 */

@get:StringRes
val FlagState.labelRes: Int
    get() = when (this) {
        FlagState.MARSHALL_ISLANDS -> R.string.flag_marshall_islands
        FlagState.LIBERIA -> R.string.flag_liberia
        FlagState.PANAMA -> R.string.flag_panama
        FlagState.OTHER -> R.string.flag_other
    }

@get:StringRes
val ClassSociety.labelRes: Int
    get() = when (this) {
        ClassSociety.DNV -> R.string.class_dnv
        ClassSociety.LR -> R.string.class_lr
        ClassSociety.ABS -> R.string.class_abs
        ClassSociety.BV -> R.string.class_bv
        ClassSociety.CLASSNK -> R.string.class_classnk
        ClassSociety.RINA -> R.string.class_rina
        ClassSociety.KR -> R.string.class_kr
        ClassSociety.CCS -> R.string.class_ccs
        ClassSociety.IRS -> R.string.class_irs
        ClassSociety.OTHER -> R.string.class_other
    }

@get:StringRes
val VesselType.labelRes: Int
    get() = when (this) {
        VesselType.BULK_CARRIER -> R.string.vessel_type_bulk_carrier
        VesselType.TANKER_OIL -> R.string.vessel_type_tanker_oil
        VesselType.TANKER_CHEM -> R.string.vessel_type_tanker_chem
        VesselType.TANKER_LPG -> R.string.vessel_type_tanker_lpg
        VesselType.CONTAINER -> R.string.vessel_type_container
        VesselType.GENERAL_CARGO -> R.string.vessel_type_general_cargo
        VesselType.RORO -> R.string.vessel_type_roro
        VesselType.PASSENGER -> R.string.vessel_type_passenger
        VesselType.OFFSHORE -> R.string.vessel_type_offshore
        VesselType.OTHER -> R.string.vessel_type_other
    }

@get:StringRes
val ConditionGrade.labelRes: Int
    get() = when (this) {
        ConditionGrade.GOOD -> R.string.condition_good
        ConditionGrade.ACCEPTABLE -> R.string.condition_acceptable
        ConditionGrade.MONITOR -> R.string.condition_monitor
        ConditionGrade.DEFECTIVE -> R.string.condition_defective
        ConditionGrade.OUT_OF_SERVICE -> R.string.condition_out_of_service
        ConditionGrade.NOT_CHECKED -> R.string.condition_not_checked
    }

@Composable
@ReadOnlyComposable
fun FlagState.label(): String = stringResource(labelRes)

@Composable
@ReadOnlyComposable
fun ClassSociety.label(): String = stringResource(labelRes)

@Composable
@ReadOnlyComposable
fun VesselType.label(): String = stringResource(labelRes)

@Composable
@ReadOnlyComposable
fun ConditionGrade.label(): String = stringResource(labelRes)
