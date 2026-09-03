package com.deckwatch.feature.settings.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.settings.R
import kotlinx.coroutines.launch

/**
 * About — MASTER_PROMPT §17.6 (the disclaimer, verbatim), §8.5 (the flag-notice links) and §20
 * (the licence position).
 *
 * ### The disclaimer
 *
 * Rendered from `R.string.about_disclaimer`, which is marked `translatable="false"` and therefore
 * reads identically in English and Turkish. That is deliberate and matches `feature-report`'s
 * `REPORT_DISCLAIMER`: the same paragraph appears in the footer of every exported HTML report, read
 * by a surveyor or a superintendent who may have no Turkish, and a softened translation of a legal
 * disclaimer is worse than an English one.
 *
 * ### The flag links — the app's only network touch
 *
 * §8.5 allows exactly one network interaction and requires it to be user-initiated: a link to each
 * registry's own public notice index. These are `ACTION_VIEW` intents handed to the system browser,
 * so DeckWatch itself never opens a socket, never has `INTERNET` in its manifest (C1), and cannot
 * be made to fetch anything in the background. The buttons are labelled "(external)" so it is
 * obvious before tapping that the phone is about to leave the app — which, at sea, may mean
 * roaming charges or simply nothing happening.
 *
 * The URLs are the registries' well-known public roots rather than deep links into a notice index,
 * because deep links rot and a dead link in a safety tool is worse than one extra tap.
 *
 * @param versionName supplied by the app module from its `BuildConfig`; blank falls back to nothing
 *   being shown rather than to a made-up number.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    versionName: String = "",
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.about_flag_no_browser)

    fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // A phone with no browser is unusual but not impossible on a ship's spare handset.
            scope.launch { snackbarHostState.showSnackbar(noBrowser) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DeckWatchTopBar(
                title = stringResource(R.string.about_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.settings_back),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Dimens.SpacingXl),
        ) {
            Column(modifier = Modifier.padding(Dimens.SpacingL)) {
                Text(
                    text = stringResource(R.string.app_name_about),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (versionName.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Dimens.SpacingS),
                )
            }

            SectionHeader(stringResource(R.string.about_section_disclaimer))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpacingL),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.about_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Dimens.SpacingL),
                )
            }

            SectionHeader(stringResource(R.string.about_section_content))
            Paragraph(stringResource(R.string.about_content_body))
            Bullet(stringResource(R.string.about_content_sources))
            Bullet(stringResource(R.string.about_content_symbols))
            Bullet(stringResource(R.string.about_content_model))

            SectionHeader(stringResource(R.string.about_section_flags))
            Paragraph(stringResource(R.string.about_flags_body))
            ExternalLinkButton(stringResource(R.string.about_flag_rmi)) { open(REGISTRY_RMI) }
            ExternalLinkButton(stringResource(R.string.about_flag_liberia)) { open(REGISTRY_LIBERIA) }
            ExternalLinkButton(stringResource(R.string.about_flag_panama)) { open(REGISTRY_PANAMA) }

            SectionHeader(stringResource(R.string.about_section_update))
            Paragraph(stringResource(R.string.about_update_body))
            ExternalLinkButton(stringResource(R.string.about_update_check)) { open(RELEASES_URL) }

            SectionHeader(stringResource(R.string.about_section_licence))
            Paragraph(stringResource(R.string.about_licence_body))
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
    )
}

@Composable
private fun Bullet(text: String) {
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
    )
}

@Composable
private fun ExternalLinkButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs)
            .heightIn(min = Dimens.TouchTargetPrimary),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.padding(end = Dimens.SpacingS),
        )
        Text(text = label)
    }
}

/**
 * The three registries' public roots — §8.5's "check for a newer revision" links.
 *
 * Roots, not deep links: a notice-index path is exactly the sort of URL that moves between site
 * redesigns, and a 404 in a safety tool reads as "the registry has withdrawn the notice".
 */
internal const val REGISTRY_RMI: String = "https://www.register-iri.com/"
internal const val REGISTRY_LIBERIA: String = "https://www.liscr.com/"
internal const val REGISTRY_PANAMA: String = "https://amp.gob.pa/"

/**
 * Where the builds are published — the update check of §17.6.
 *
 * The app does not fetch this itself. Checking is a tap that hands the URL to the browser, exactly
 * as the flag links do, so DeckWatch keeps its "no `INTERNET` in the manifest" guarantee (C1): an
 * offline tool that quietly dialled home to look for a newer version of itself would no longer be
 * one, and a background download is the last thing wanted on a ship's metered connection.
 *
 * `/releases/latest` rather than the releases index, so the page that opens is the build to install
 * rather than a list the officer has to compare version numbers in.
 */
internal const val RELEASES_URL: String = "https://github.com/MrHakan/lsaffe/releases/latest"
