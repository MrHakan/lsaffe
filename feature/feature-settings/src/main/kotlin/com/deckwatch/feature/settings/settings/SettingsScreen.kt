package com.deckwatch.feature.settings.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.datastore.PhotoQuality
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.AppLanguage
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode
import com.deckwatch.feature.settings.AppLocale
import com.deckwatch.feature.settings.R
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Every setting of MASTER_PROMPT §18, bound to `UserPreferencesRepository` — no local state, no
 * optimistic updates, one primary action (none: this screen is all controls).
 *
 * ### Notification permission (§11.3)
 *
 * `POST_NOTIFICATIONS` is requested **here, lazily, when the officer turns the reminder on** — never
 * at launch. A launch-time permission dialog on a tool like this is the fastest way to have it
 * denied forever, and §11.3 requires the app to be fully usable without it. If the request is
 * refused the switch still turns on (the preference is the officer's intent, and it survives them
 * granting the permission later in Android settings), and a snackbar says plainly that Android is
 * blocking notifications and everything else still works.
 *
 * ### Language (C8)
 *
 * The choice is written to DataStore and handed to `AppLocale`, which uses the framework's per-app
 * language on API 33+ and a wrapped `Configuration` below it. On the lower path the hosting
 * activity is recreated so the change is visible immediately, and the restart hint is shown either
 * way — a `Configuration` swap cannot reach a notification that is already posted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    // The tag format is the one free-text control on the screen. It keeps a local draft so the
    // field does not jump under the officer's cursor when the repository normalises a blank value
    // back to the default; null means "nothing typed yet, show what is stored".
    var tagFormatDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionRefusals by rememberSaveable { mutableIntStateOf(0) }

    val permissionDeniedMessage = stringResource(R.string.settings_notification_permission_denied)
    val restartHint = stringResource(R.string.settings_language_restart)

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // The preference stays on either way: it records what the officer asked for, and the
        // digest starts arriving the moment they grant the permission in Android's own settings.
        if (!granted) permissionRefusals++
    }

    LaunchedEffect(permissionRefusals) {
        if (permissionRefusals > 0) snackbarHostState.showSnackbar(permissionDeniedMessage)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DeckWatchTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.settings_back),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                SegmentedSettingRow(
                    title = stringResource(R.string.settings_theme),
                    options = ThemeMode.entries,
                    selected = state.themeMode,
                    label = { themeLabel(it) },
                    onSelect = viewModel::setThemeMode,
                )
            }
            item {
                SwitchSettingRow(
                    title = stringResource(R.string.settings_theme_schedule),
                    subtitle = stringResource(R.string.settings_theme_schedule_desc),
                    checked = state.themeFollowSchedule,
                    onCheckedChange = viewModel::setThemeFollowSchedule,
                )
            }
            item {
                SegmentedSettingRow(
                    title = stringResource(R.string.settings_language),
                    options = AppLanguage.entries,
                    selected = state.language,
                    label = { languageLabel(it) },
                    onSelect = { language ->
                        viewModel.setLanguage(language)
                        val appliedByPlatform = AppLocale.apply(context, language)
                        if (!appliedByPlatform) context.findActivity()?.recreate()
                    },
                )
            }
            item {
                Text(
                    text = restartHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
                )
            }
            item {
                SegmentedSettingRow(
                    title = stringResource(R.string.settings_density),
                    options = ListDensity.entries,
                    selected = state.density,
                    label = { densityLabel(it) },
                    onSelect = viewModel::setDensity,
                )
            }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.settings_section_work)) }
            item {
                SliderSettingRow(
                    title = stringResource(R.string.settings_lead_time),
                    subtitle = stringResource(R.string.settings_lead_time_desc),
                    valueLabel = stringResource(R.string.settings_lead_time_value, state.dueLeadTimeDays),
                    value = state.dueLeadTimeDays.coerceIn(DUE_LEAD_TIME_RANGE).toFloat(),
                    range = DUE_LEAD_TIME_RANGE.first.toFloat()..DUE_LEAD_TIME_RANGE.last.toFloat(),
                    steps = DUE_LEAD_TIME_RANGE.last - DUE_LEAD_TIME_RANGE.first - 1,
                    onValueChange = { viewModel.setDueLeadTimeDays(it.roundToInt()) },
                )
            }
            item {
                SwitchSettingRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = state.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setNotificationsEnabled(enabled)
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !hasNotificationPermission(context)
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
            item {
                DeckWatchListRow(
                    title = stringResource(R.string.settings_notification_time),
                    subtitle = formatTime(state.notificationHour, state.notificationMinute),
                    onClick = { showTimePicker = true },
                )
            }
            item {
                ChipSettingRow(
                    title = stringResource(R.string.settings_default_flag),
                    subtitle = stringResource(R.string.settings_default_flag_desc),
                    options = FlagState.entries,
                    selected = state.defaultFlag,
                    label = { flagLabel(it) },
                    onSelect = viewModel::setDefaultFlag,
                )
            }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.settings_section_deckview)) }
            item {
                SliderSettingRow(
                    title = stringResource(R.string.settings_iso_angle),
                    subtitle = stringResource(R.string.settings_iso_angle_desc),
                    valueLabel = stringResource(R.string.settings_iso_angle_value, state.isoAngleDeg.roundToInt()),
                    value = state.isoAngleDeg,
                    range = ISO_ANGLE_MIN..ISO_ANGLE_MAX,
                    steps = ISO_ANGLE_STEPS,
                    onValueChange = viewModel::setIsoAngleDeg,
                )
            }
            item {
                SwitchSettingRow(
                    title = stringResource(R.string.settings_grid_snap),
                    subtitle = stringResource(R.string.settings_grid_snap_desc),
                    checked = state.gridSnapEnabled,
                    onCheckedChange = viewModel::setGridSnapEnabled,
                )
            }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.settings_section_records)) }
            item {
                TagFormatRow(
                    value = tagFormatDraft ?: state.tagNumberFormat,
                    onValueChange = { typed ->
                        tagFormatDraft = typed
                        // Committed on every keystroke: DataStore writes are cheap, and an officer
                        // who types a format and then backs out has still saved it. The repository
                        // falls back to the default for a blank value, so an empty field cannot
                        // produce untraceable tags.
                        viewModel.setTagNumberFormat(typed)
                    },
                )
            }
            item {
                ChipSettingRow(
                    title = stringResource(R.string.settings_photo_quality),
                    options = PhotoQuality.ALL,
                    selected = state.photoQuality,
                    label = { photoQualityLabel(it) },
                    onSelect = viewModel::setPhotoQuality,
                )
            }
            item {
                SegmentedSettingRow(
                    title = stringResource(R.string.settings_units),
                    options = listOf(true, false),
                    selected = state.metricUnits,
                    label = { metric ->
                        stringResource(
                            if (metric) R.string.settings_units_metric else R.string.settings_units_imperial,
                        )
                    },
                    onSelect = viewModel::setMetricUnits,
                )
            }
            item {
                SegmentedSettingRow(
                    title = stringResource(R.string.settings_first_day),
                    options = FIRST_DAY_CHOICES,
                    selected = state.firstDayOfWeek.takeIf { it in FIRST_DAY_CHOICES } ?: FIRST_DAY_CHOICES.first(),
                    label = { dayLabel(it) },
                    onSelect = viewModel::setFirstDayOfWeek,
                )
            }
        }
    }

    if (showTimePicker) {
        NotificationTimeDialog(
            initialHour = state.notificationHour,
            initialMinute = state.notificationMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setNotificationTime(hour, minute)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun TagFormatRow(value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(stringResource(R.string.settings_tag_format)) },
            supportingText = { Text(stringResource(R.string.settings_tag_format_desc)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_tag_format_example, tagFormatExample(value)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpacingXs),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notification_time_picker_title)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.DAY -> R.string.settings_theme_day
        ThemeMode.NIGHT -> R.string.settings_theme_night
        ThemeMode.BRIDGE -> R.string.settings_theme_bridge
    },
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.ENGLISH -> R.string.settings_language_en
        AppLanguage.TURKISH -> R.string.settings_language_tr
    },
)

@Composable
private fun densityLabel(density: ListDensity): String = stringResource(
    when (density) {
        ListDensity.COMPACT -> R.string.settings_density_compact
        ListDensity.COMFORTABLE -> R.string.settings_density_comfortable
    },
)

@Composable
private fun flagLabel(flag: FlagState): String = stringResource(
    when (flag) {
        FlagState.MARSHALL_ISLANDS -> R.string.settings_flag_rmi
        FlagState.LIBERIA -> R.string.settings_flag_liberia
        FlagState.PANAMA -> R.string.settings_flag_panama
        FlagState.OTHER -> R.string.settings_flag_other
    },
)

@Composable
private fun photoQualityLabel(quality: String): String = stringResource(
    when (quality) {
        PhotoQuality.LOW -> R.string.settings_photo_low
        PhotoQuality.HIGH -> R.string.settings_photo_high
        else -> R.string.settings_photo_medium
    },
)

@Composable
private fun dayLabel(isoDay: Int): String = stringResource(
    when (isoDay) {
        SATURDAY -> R.string.settings_day_saturday
        SUNDAY -> R.string.settings_day_sunday
        else -> R.string.settings_day_monday
    },
)

/** 24-hour, zero-padded — the only unambiguous way to write a time on a ship. */
internal fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)

internal fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** The activity behind a composable's context, for the API < 33 locale recreate. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Matches `ISO_ANGLE_RANGE` in core-datastore — 0° is a flat plan, 35° is the readable limit. */
private const val ISO_ANGLE_MIN = 0f
private const val ISO_ANGLE_MAX = 35f

/** One stop per degree. */
private const val ISO_ANGLE_STEPS = 34

private const val SATURDAY = 6
private const val SUNDAY = 7
