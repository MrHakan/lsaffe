@file:OptIn(ExperimentalMaterial3Api::class)

package com.deckwatch.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.Dimens
import java.util.Locale

/**
 * Reminder settings — §11.3, §18.
 *
 * Three things, in the order they matter: whether the daily digest runs, when it arrives, and
 * whether Android will actually let it through. The permission row is the one that catches people
 * out — the app's own switch can be on while the system blocks every post — so it states the real
 * state and offers the fix rather than silently doing nothing.
 *
 * @param onSettingsChanged called after a setting is stored, so the host can re-arm the queue.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSettingsChanged: (enabled: Boolean, hour: Int, minute: Int) -> Unit = { _, _, _ -> },
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingTime by remember { mutableStateOf(false) }

    // The officer can grant or revoke the permission in system settings and come back, so this is
    // re-read on every resume rather than only when the screen is first composed.
    var canPost by remember { mutableStateOf(NotificationPermission.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canPost = NotificationPermission.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Android answers a second request for a permanently denied permission instantly and silently,
    // so after one refusal the only working escape hatch is system settings.
    var permissionRefused by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        canPost = granted
        permissionRefused = !granted
    }
    val openSystemSettings = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Unit
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.notifications_back),
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
                .verticalScroll(rememberScrollState()),
        ) {
            SettingRow(
                title = stringResource(R.string.notifications_enable),
                subtitle = stringResource(R.string.notifications_enable_subtitle),
                trailing = {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.setEnabled(enabled) { applied ->
                                onSettingsChanged(applied.enabled, applied.hour, applied.minute)
                            }
                            if (enabled && !canPost && NotificationPermission.NEEDS_RUNTIME_REQUEST) {
                                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.notifications_time),
                subtitle = formatTime(state.hour, state.minute),
                onClick = { pickingTime = true },
            )
            HorizontalDivider()

            if (!canPost) {
                Column(modifier = Modifier.padding(Dimens.SpacingL)) {
                    Text(
                        text = stringResource(R.string.notifications_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    val canAsk = NotificationPermission.NEEDS_RUNTIME_REQUEST && !permissionRefused
                    OutlinedButton(
                        onClick = {
                            if (canAsk) {
                                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openSystemSettings()
                            }
                        },
                        modifier = Modifier.padding(top = Dimens.SpacingS),
                    ) {
                        Text(
                            stringResource(
                                if (canAsk) {
                                    R.string.notifications_allow
                                } else {
                                    R.string.notifications_open_system_settings
                                },
                            ),
                        )
                    }
                }
                HorizontalDivider()
            }

            Text(
                text = stringResource(R.string.notifications_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimens.SpacingL),
            )
        }
    }

    if (pickingTime) {
        TimePickerDialog(
            initialHour = state.hour,
            initialMinute = state.minute,
            onDismiss = { pickingTime = false },
            onConfirm = { hour, minute ->
                pickingTime = false
                viewModel.setTime(hour, minute) { applied ->
                    onSettingsChanged(applied.enabled, applied.hour, applied.minute)
                }
            },
        )
    }
}

@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    // A ship runs on a 24-hour clock, and "0800" is how the digest time is spoken on the bridge.
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_time)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.notifications_time_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notifications_time_cancel)) }
        },
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** 24-hour, zero-padded, locale-independent — the form a watch schedule is written in. */
internal fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", hour, minute)

/** Whether Android will let the app post, and whether asking is even a thing on this version. */
internal object NotificationPermission {

    /** Below Android 13 notifications need no runtime grant; the app can only be muted. */
    val NEEDS_RUNTIME_REQUEST: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: android.content.Context): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}
