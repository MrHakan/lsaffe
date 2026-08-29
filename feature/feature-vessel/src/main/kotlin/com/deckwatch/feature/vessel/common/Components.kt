package com.deckwatch.feature.vessel.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.feature.vessel.R

/** Milliseconds in a day — epoch-days are the storage unit for every DeckWatch date (§6). */
const val MILLIS_PER_DAY: Long = 86_400_000L

/**
 * The §14 empty state: one sentence saying what goes here and the single button that starts it.
 * No decoration that does not carry information.
 */
@Composable
fun TeachingEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.SpacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
        content()
    }
}

/** A 56dp primary action — C6 says primary actions are 56dp, gloves and all. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.TouchTargetPrimary),
    ) {
        Text(text = text)
    }
}

/** Every destructive action is confirmed, and the dialog says exactly what will happen — C10. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(text = confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(text = stringResource(R.string.vessel_action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VesselTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.vessel_cd_back),
                    )
                }
            }
        },
        actions = { actions() },
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Dimens.SpacingM, bottom = Dimens.SpacingXs),
        )
    }
}

/** The semantic condition dot used on deck rows and equipment rows (§6.9 colours). */
@Composable
fun ConditionDot(
    grade: ConditionGrade?,
    modifier: Modifier = Modifier,
) {
    val colour = if (grade == null) ConditionColors.NotChecked else ConditionColors.of(grade)
    val description = if (grade == null) {
        stringResource(R.string.deck_manager_no_equipment)
    } else {
        stringResource(R.string.deck_manager_condition, stringResource(grade.labelRes))
    }
    Box(
        modifier = modifier
            .size(CONDITION_DOT)
            .clip(CircleShape)
            .background(colour)
            .clearAndSetSemantics { contentDescription = description },
    )
}

/**
 * A read-only date field that writes epoch-days (§6). The picker works in UTC millis, so the
 * conversion lives in exactly one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val text = epochDay?.let(Dates::formatIso) ?: stringResource(R.string.vessel_edit_date_not_set)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(text = label) },
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { showPicker = true },
            modifier = Modifier.size(Dimens.TouchTargetMin),
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = stringResource(R.string.vessel_edit_date_pick),
            )
        }
        if (epochDay != null) {
            IconButton(
                onClick = { onChange(null) },
                modifier = Modifier.size(Dimens.TouchTargetMin),
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.vessel_edit_date_clear),
                )
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = epochDay?.times(MILLIS_PER_DAY),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(state.selectedDateMillis?.floorDiv(MILLIS_PER_DAY))
                        showPicker = false
                    },
                ) {
                    Text(text = stringResource(R.string.vessel_action_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(text = stringResource(R.string.vessel_action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * A dropdown over a fixed set of values, used for flag, class society and vessel type. A
 * segmented control runs out of room at ten vessel types, and a dropdown reads the same under
 * TalkBack (§14 accessibility).
 */
@Composable
fun <T> EnumDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(text = label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // A transparent hit target over the disabled field keeps the whole row tappable at 48dp.
        Box(
            modifier = Modifier
                .matchParentSize()
                .heightIn(min = Dimens.TouchTargetMin)
                .clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = DROPDOWN_MIN_WIDTH),
        ) {
            for (option in options) {
                val text = optionLabel(option)
                DropdownMenuItem(
                    text = { Text(text = text) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                )
            }
        }
    }
}

private val CONDITION_DOT = 12.dp
private val DROPDOWN_MIN_WIDTH = 200.dp
