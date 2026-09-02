package com.deckwatch.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Row
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.vessel.category.CategoryManagerScreen
import com.deckwatch.feature.vessel.manager.VesselManagerScreen

/**
 * Tab 4 — everything that is not a daily journey: the fleet, the logical categories, and who is
 * responsible for what the app says (§17.6).
 *
 * Reports (§13) and the survival-craft schematics (§7.6) are not built yet and are listed in
 * `docs/BACKLOG.md`; this tab shows what exists rather than dead rows that open nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onRemindersChanged: (enabled: Boolean, hour: Int, minute: Int) -> Unit = { _, _, _ -> },
) {
    var destination by remember { mutableStateOf(MoreDestination.NONE) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            MoreRow(R.string.more_vessels) { destination = MoreDestination.VESSELS }
            HorizontalDivider()
            MoreRow(R.string.more_categories) { destination = MoreDestination.CATEGORIES }
            HorizontalDivider()
            MoreRow(R.string.more_notifications) { destination = MoreDestination.NOTIFICATIONS }
            HorizontalDivider()
            MoreRow(R.string.more_appearance) { destination = MoreDestination.APPEARANCE }
            HorizontalDivider()

            Text(
                text = stringResource(R.string.more_about_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = Dimens.SpacingL,
                    end = Dimens.SpacingL,
                    top = Dimens.SpacingL,
                ),
            )
            Text(
                text = stringResource(R.string.more_version, versionName()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
            )
            Text(
                text = stringResource(R.string.more_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Dimens.SpacingL,
                    end = Dimens.SpacingL,
                    top = Dimens.SpacingXs,
                    bottom = Dimens.SpacingXl,
                ),
            )
        }
    }

    when (destination) {
        MoreDestination.NONE -> Unit
        MoreDestination.VESSELS -> FullScreen(onDismiss = { destination = MoreDestination.NONE }) {
            VesselManagerScreen(onBack = { destination = MoreDestination.NONE })
        }

        MoreDestination.CATEGORIES -> FullScreen(onDismiss = { destination = MoreDestination.NONE }) {
            CategoryManagerScreen(onBack = { destination = MoreDestination.NONE })
        }

        MoreDestination.NOTIFICATIONS -> FullScreen(onDismiss = { destination = MoreDestination.NONE }) {
            NotificationSettingsScreen(
                onBack = { destination = MoreDestination.NONE },
                onSettingsChanged = onRemindersChanged,
            )
        }

        MoreDestination.APPEARANCE -> FullScreen(onDismiss = { destination = MoreDestination.NONE }) {
            AppearanceScreen(onBack = { destination = MoreDestination.NONE })
        }
    }
}

private enum class MoreDestination { NONE, VESSELS, CATEGORIES, NOTIFICATIONS, APPEARANCE }

@Composable
private fun MoreRow(@StringRes labelRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

@Composable
private fun FullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        content = content,
    )
}

/** The installed build's version name, so a bug report can say which build it came from. */
@Composable
private fun versionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}
