package com.deckwatch.feature.inspection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Swipe-right "mark done" — §12, capturing the §6.6 evidence fields: date, who did it, the service
 * provider and certificate number when it was a shore job, findings, and the grade the item is in
 * afterwards (§7.3).
 *
 * Only the date is mandatory, and it is pre-filled with today, so the common case — a ship's-staff
 * check done on the spot — is two taps.
 */
@Composable
fun CompletionDialog(
    row: DueRow,
    defaultDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (TaskCompletionInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val turkish = isTurkishLocale()
    var dateText by rememberSaveable(row.instanceId) { mutableStateOf(Dates.formatIso(defaultDate)) }
    var completedBy by rememberSaveable(row.instanceId) { mutableStateOf("") }
    var serviceProvider by rememberSaveable(row.instanceId) { mutableStateOf("") }
    var certificateNumber by rememberSaveable(row.instanceId) { mutableStateOf("") }
    var findings by rememberSaveable(row.instanceId) { mutableStateOf("") }
    var conditionAfter by remember(row.instanceId) { mutableStateOf<ConditionGrade?>(null) }

    val parsedDate = parseIsoDate(dateText)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.due_complete_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = DialogMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "${row.tag} · ${row.taskTitle.resolve(turkish)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.due_complete_date)) },
                    isError = parsedDate == null,
                    supportingText = if (parsedDate == null) {
                        { Text(stringResource(R.string.due_complete_bad_date)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpacingM),
                )
                DialogField(
                    value = completedBy,
                    onValueChange = { completedBy = it },
                    label = stringResource(R.string.due_complete_by),
                )
                DialogField(
                    value = serviceProvider,
                    onValueChange = { serviceProvider = it },
                    label = stringResource(R.string.due_complete_provider),
                )
                DialogField(
                    value = certificateNumber,
                    onValueChange = { certificateNumber = it },
                    label = stringResource(R.string.due_complete_certificate),
                )
                DialogField(
                    value = findings,
                    onValueChange = { findings = it },
                    label = stringResource(R.string.due_complete_findings),
                    singleLine = false,
                )
                Text(
                    text = stringResource(R.string.due_complete_condition),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.SpacingM),
                )
                ConditionChipRow(
                    selected = conditionAfter,
                    onSelect = { conditionAfter = it },
                    modifier = Modifier.padding(top = Dimens.SpacingXs),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedDate != null,
                onClick = {
                    val date = parsedDate ?: return@TextButton
                    onConfirm(
                        TaskCompletionInput(
                            instanceId = row.instanceId,
                            equipmentId = row.equipmentId,
                            completedDate = date,
                            completedBy = completedBy,
                            serviceProvider = serviceProvider,
                            certificateNumber = certificateNumber,
                            findings = findings,
                            conditionAfter = conditionAfter,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.due_complete_title))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.insp_action_cancel)) }
        },
    )
}

/**
 * Swipe-left "defer with reason" — §12. The reason is mandatory: a deferral without one is exactly
 * the record a surveyor will ask about, so the app does not let the officer leave it blank.
 */
@Composable
fun DeferDialog(
    row: DueRow,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val turkish = isTurkishLocale()
    var reason by rememberSaveable(row.instanceId) { mutableStateOf("") }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.due_defer_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${row.tag} · ${row.taskTitle.resolve(turkish)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.due_defer_reason)) },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpacingM),
                )
                Text(
                    text = stringResource(R.string.due_defer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.SpacingS),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = reason.isNotBlank(),
                onClick = { onConfirm(reason) },
            ) {
                Text(stringResource(R.string.due_swipe_defer))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.insp_action_cancel)) }
        },
    )
}

@Composable
internal fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpacingS),
    )
}

/** ISO-8601 (`2026-03-12`) to epoch-days; `null` when the officer is mid-typing — §6 dates. */
internal fun parseIsoDate(text: String): Long? = try {
    LocalDate.parse(text.trim()).toEpochDay()
} catch (_: DateTimeParseException) {
    null
}

private val DialogMaxHeight = 420.dp
