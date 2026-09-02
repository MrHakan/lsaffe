package com.deckwatch.feature.vessel.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.components.ConditionDot
import com.deckwatch.core.designsystem.components.DateFieldLabels
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.feature.vessel.R

/**
 * What is left of this file after the overhaul: the design system in
 * `core-designsystem/components` owns the top bar, the empty state, the confirm dialog, the date
 * field, the list row, the section header and the condition dot. Only the two pieces the shared
 * set does not carry live here — the enum dropdown of the forms rule and the TalkBack wrapper the
 * bare condition dot needs.
 */

/** Localised labels for the shared `DateField` (the design system carries no strings). */
@Composable
@ReadOnlyComposable
fun vesselDateFieldLabels(): DateFieldLabels = DateFieldLabels(
    pick = stringResource(R.string.vessel_edit_date_pick),
    clear = stringResource(R.string.vessel_edit_date_clear),
    confirm = stringResource(R.string.vessel_action_done),
    cancel = stringResource(R.string.vessel_action_cancel),
)

/** "Vessel name *" — required fields are marked, and validated inline (DESIGN_OVERHAUL forms). */
@Composable
@ReadOnlyComposable
fun requiredLabel(@StringRes labelRes: Int): String =
    stringResource(R.string.vessel_field_required, stringResource(labelRes))

/**
 * The shared [ConditionDot] plus the description a bare coloured dot cannot carry. Colour is never
 * the only signal (DESIGN_OVERHAUL rule 6), so the grade is always spoken.
 */
@Composable
fun ConditionIndicator(
    grade: ConditionGrade?,
    modifier: Modifier = Modifier,
) {
    val description = if (grade == null) {
        stringResource(R.string.deck_manager_no_equipment)
    } else {
        stringResource(R.string.deck_manager_condition, stringResource(grade.labelRes))
    }
    Box(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        ConditionDot(grade = grade ?: ConditionGrade.NOT_CHECKED, size = CONDITION_DOT)
    }
}

/**
 * A dropdown over a fixed set of values, used for flag, class society and vessel type — enum
 * fields are dropdowns (DESIGN_OVERHAUL forms). A segmented control runs out of room at ten vessel
 * types, and a dropdown reads the same under TalkBack.
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
            label = { Text(text = label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetPrimary),
        )
        // A transparent hit target over the read-only field keeps the whole row tappable, and the
        // field itself keeps full-contrast colours rather than the disabled ramp.
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics { role = Role.DropdownList }
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
