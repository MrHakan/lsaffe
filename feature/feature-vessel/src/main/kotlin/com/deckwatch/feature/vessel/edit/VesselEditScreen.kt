package com.deckwatch.feature.vessel.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.ClassSociety
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.VesselType
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.DateField
import com.deckwatch.feature.vessel.common.EnumDropdown
import com.deckwatch.feature.vessel.common.ImoStatus
import com.deckwatch.feature.vessel.common.PrimaryButton
import com.deckwatch.feature.vessel.common.SectionHeader
import com.deckwatch.feature.vessel.common.VesselTopBar
import com.deckwatch.feature.vessel.common.label
import com.deckwatch.feature.vessel.manager.UnverifiedImoBadge

/**
 * Create or edit one vessel (§6.1).
 *
 * Passing a null [vesselId] opens the form empty for a new vessel. Only the name is required;
 * everything else, the IMO number included, can be filled in later or left wrong-but-flagged —
 * see [ImoStatus] for the reasoning behind the invalid-but-savable IMO rule.
 */
@Composable
fun VesselEditScreen(
    vesselId: String? = null,
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: VesselEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(vesselId) { viewModel.load(vesselId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.onSavedHandled()
            onDone()
        }
    }

    VesselEditContent(
        state = state,
        modifier = modifier,
        onBack = onDone,
        onSave = viewModel::save,
        onNameChange = viewModel::onNameChange,
        onImoChange = viewModel::onImoChange,
        onCallSignChange = viewModel::onCallSignChange,
        onMmsiChange = viewModel::onMmsiChange,
        onFlagChange = viewModel::onFlagChange,
        onFlagOtherNameChange = viewModel::onFlagOtherNameChange,
        onClassSocietyChange = viewModel::onClassSocietyChange,
        onVesselTypeChange = viewModel::onVesselTypeChange,
        onGrossTonnageChange = viewModel::onGrossTonnageChange,
        onBuildDateChange = viewModel::onBuildDateChange,
        onSecExpiryChange = viewModel::onSecExpiryChange,
        onLastAnnualSurveyChange = viewModel::onLastAnnualSurveyChange,
        onNextDrydockChange = viewModel::onNextDrydockChange,
    )
}

@Composable
internal fun VesselEditContent(
    state: VesselFormState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSave: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onImoChange: (String) -> Unit = {},
    onCallSignChange: (String) -> Unit = {},
    onMmsiChange: (String) -> Unit = {},
    onFlagChange: (FlagState) -> Unit = {},
    onFlagOtherNameChange: (String) -> Unit = {},
    onClassSocietyChange: (ClassSociety?) -> Unit = {},
    onVesselTypeChange: (VesselType) -> Unit = {},
    onGrossTonnageChange: (String) -> Unit = {},
    onBuildDateChange: (Long?) -> Unit = {},
    onSecExpiryChange: (Long?) -> Unit = {},
    onLastAnnualSurveyChange: (Long?) -> Unit = {},
    onNextDrydockChange: (Long?) -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                VesselTopBar(
                    title = stringResource(
                        if (state.isNew) R.string.vessel_edit_title_new else R.string.vessel_edit_title_edit,
                    ),
                    onBack = onBack,
                    actions = {
                        IconButton(
                            onClick = onSave,
                            enabled = state.canSave,
                            modifier = Modifier.size(Dimens.TouchTargetMin),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.vessel_action_save),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.SpacingL),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
            ) {
                SectionHeader(text = stringResource(R.string.vessel_edit_section_identity))

                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.vessel_edit_name)) },
                    isError = state.showNameError,
                    singleLine = true,
                    supportingText = {
                        if (state.showNameError) {
                            Text(
                                text = stringResource(R.string.vessel_edit_name_required),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                ImoField(
                    value = state.imoNumber,
                    status = state.imoStatus,
                    onChange = onImoChange,
                )

                OutlinedTextField(
                    value = state.callSign,
                    onValueChange = onCallSignChange,
                    label = { Text(stringResource(R.string.vessel_edit_call_sign)) },
                    singleLine = true,
                    textStyle = tagTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.mmsi,
                    onValueChange = onMmsiChange,
                    label = { Text(stringResource(R.string.vessel_edit_mmsi)) },
                    singleLine = true,
                    textStyle = tagTextStyle(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionHeader(text = stringResource(R.string.vessel_edit_section_particulars))

                EnumDropdown(
                    label = stringResource(R.string.vessel_edit_flag),
                    selected = state.flag,
                    options = FlagState.entries,
                    optionLabel = { it.label() },
                    onSelect = onFlagChange,
                )

                if (state.showFlagOtherName) {
                    OutlinedTextField(
                        value = state.flagOtherName,
                        onValueChange = onFlagOtherNameChange,
                        label = { Text(stringResource(R.string.vessel_edit_flag_other_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                EnumDropdown(
                    label = stringResource(R.string.vessel_edit_class_society),
                    selected = state.classSociety,
                    options = listOf<ClassSociety?>(null) + ClassSociety.entries,
                    optionLabel = { it?.label() ?: stringResource(R.string.vessel_edit_class_none) },
                    onSelect = onClassSocietyChange,
                )

                EnumDropdown(
                    label = stringResource(R.string.vessel_edit_vessel_type),
                    selected = state.vesselType,
                    options = VesselType.entries,
                    optionLabel = { it.label() },
                    onSelect = onVesselTypeChange,
                )

                OutlinedTextField(
                    value = state.grossTonnage,
                    onValueChange = onGrossTonnageChange,
                    label = { Text(stringResource(R.string.vessel_edit_gross_tonnage)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                DateField(
                    label = stringResource(R.string.vessel_edit_build_date),
                    epochDay = state.buildDate,
                    onChange = onBuildDateChange,
                )

                SectionHeader(text = stringResource(R.string.vessel_edit_section_certificates))

                DateField(
                    label = stringResource(R.string.vessel_edit_sec_expiry),
                    epochDay = state.safetyEquipmentCertExpiry,
                    onChange = onSecExpiryChange,
                )
                DateField(
                    label = stringResource(R.string.vessel_edit_last_annual_survey),
                    epochDay = state.lastAnnualSurveyDate,
                    onChange = onLastAnnualSurveyChange,
                )
                DateField(
                    label = stringResource(R.string.vessel_edit_next_drydock),
                    epochDay = state.nextDrydockDate,
                    onChange = onNextDrydockChange,
                )

                PrimaryButton(
                    text = stringResource(R.string.vessel_action_save),
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.SpacingL),
                )
            }
        }
    }
}

/**
 * IMO number with live check-digit feedback. An invalid number is an inline error *and* a warning
 * badge, but it never disables the save button — [ImoStatus] documents why.
 */
@Composable
private fun ImoField(
    value: String,
    status: ImoStatus,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.vessel_edit_imo)) },
            singleLine = true,
            isError = status == ImoStatus.INVALID,
            textStyle = tagTextStyle(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                when (status) {
                    ImoStatus.INVALID -> Text(
                        text = stringResource(R.string.vessel_edit_imo_invalid),
                        color = MaterialTheme.colorScheme.error,
                    )

                    ImoStatus.VALID -> Text(
                        text = stringResource(R.string.vessel_edit_imo_valid),
                        color = ConditionColors.Good,
                    )

                    ImoStatus.NOT_ENTERED -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (status.needsWarning) {
            UnverifiedImoBadge()
        }
    }
}

@Preview
@Composable
private fun VesselEditPreview() {
    DeckWatchTheme {
        VesselEditContent(
            state = VesselFormState(
                name = "MV Example",
                imoNumber = "1234567",
                flag = FlagState.MARSHALL_ISLANDS,
                vesselType = VesselType.BULK_CARRIER,
            ),
        )
    }
}
