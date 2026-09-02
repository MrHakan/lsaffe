package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.deckwatch.core.designsystem.theme.Dimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Never type a date — DESIGN_OVERHAUL rule 4. A read-only field that opens
 * the Material date picker; the value is an epoch-day (§6), shown as ISO.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    labels: DateFieldLabels = DateFieldLabels(),
    required: Boolean = false,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val display = epochDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ISO_LOCAL_DATE) } ?: ""

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(if (required) "$label *" else label) },
            supportingText = supportingText?.let { { Text(it) } },
            isError = isError,
            leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
            trailingIcon = {
                if (epochDay != null && enabled) {
                    IconButton(onClick = { onChange(null) }, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                        Icon(Icons.Filled.Clear, contentDescription = labels.clear)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetPrimary),
        )
        // Transparent overlay so the whole field (not just the icon) opens the picker.
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics { role = Role.Button }
                .clickable(enabled = enabled, onClickLabel = labels.pick) { open = true },
        )
    }

    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = epochDay?.let { it * MILLIS_PER_DAY },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onChange(Math.floorDiv(it, MILLIS_PER_DAY)) }
                        open = false
                    },
                ) { Text(labels.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(labels.cancel) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
