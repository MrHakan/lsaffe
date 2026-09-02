package com.deckwatch.feature.settings.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.settings.R

/**
 * Asks for the optional backup passphrase of §18, or for the one needed to open an encrypted file.
 *
 * ### Two modes, one dialog
 *
 * * **Taking a backup** — `allowEmpty = true`, `requireConfirmation = true`. Leaving both fields
 *   blank means "no passphrase", which is a legitimate choice and the one most officers will make;
 *   typing one requires typing it twice, because there is no recovery and a typo in an encrypted
 *   backup is a lost register.
 * * **Restoring** — `allowEmpty = false`, `requireConfirmation = false`. The passphrase either
 *   works or it does not, and the container's authentication tag says which.
 *
 * ### Why the value comes back as a `CharArray`
 *
 * `BackupCrypto` zeroes the array after deriving the key. Compose's `TextField` holds a `String`,
 * so the characters are in the string pool for as long as the dialog is open — that cannot be
 * helped without a custom text field — but they do not then travel any further as an immutable
 * value that survives until the next GC. The conversion happens once, on confirm.
 */
@Composable
fun PassphraseDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (CharArray?) -> Unit,
    requireConfirmation: Boolean = false,
    allowEmpty: Boolean = false,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val mismatch = requireConfirmation && passphrase.isNotEmpty() && passphrase != repeated
    val canConfirm = when {
        mismatch -> false
        passphrase.isEmpty() -> allowEmpty
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_passphrase_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpacingL),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = repeated,
                        onValueChange = { repeated = it },
                        singleLine = true,
                        isError = mismatch,
                        label = { Text(stringResource(R.string.backup_passphrase_confirm_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = {
                            if (mismatch) Text(stringResource(R.string.backup_passphrase_mismatch))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.SpacingS),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase.takeIf { it.isNotEmpty() }?.toCharArray()) },
                enabled = canConfirm,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                Text(stringResource(R.string.backup_cancel))
            }
        },
    )
}
