package com.deckwatch.feature.settings.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.settings.R

/**
 * First-run onboarding — MASTER_PROMPT §14 ("four screens maximum, skippable, then a guided build
 * your first deck flow") and §17.6 (the disclaimer on first run).
 *
 * Full-screen: the host replaces the whole tab UI with this while `onboardingDone` is false, so
 * there is no bottom bar to tap past it and no half-configured app behind it.
 *
 * @param onDone the flow is finished and `onboardingDone` is written; show the app.
 * @param onCreateVessel the officer chose "Create my vessel"; the host opens the vessel editor.
 *   Called *in addition to* [onDone] — the flag is written either way, so a user who backs out of
 *   the editor lands in the app rather than in onboarding again.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit = {},
    onCreateVessel: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) {
            val wantsEditor = state.createVessel
            viewModel.onFinishHandled()
            onDone()
            if (wantsEditor) onCreateVessel()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            when (state.stage) {
                OnboardingStage.DISCLAIMER -> Unit
                OnboardingStage.PAGES -> PagesStage(
                    pageIndex = state.pageIndex,
                    onPageChange = viewModel::goToPage,
                    onNext = viewModel::next,
                    onSkip = viewModel::skip,
                )

                OnboardingStage.CHOICE -> ChoiceStage(
                    installingDemo = state.installingDemo,
                    demoFailed = state.demoFailed,
                    onLoadDemo = viewModel::loadDemoVessel,
                    onCreateVessel = viewModel::chooseCreateVessel,
                )
            }
        }
    }

    if (state.stage == OnboardingStage.DISCLAIMER) {
        DisclaimerDialog(onAccept = viewModel::acceptDisclaimer)
    }
}

/**
 * The §17.6 acceptance.
 *
 * `onDismissRequest` re-shows nothing and does not advance: this dialog has exactly one way out,
 * and a back press or an outside tap must not become a way to skip a legal notice. The wording is
 * `translatable="false"` and identical in both locales — see `AboutScreen`.
 */
@Composable
private fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.onboarding_disclaimer_title)) },
        text = {
            Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onAccept, modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary)) {
                Text(stringResource(R.string.onboarding_disclaimer_accept))
            }
        },
    )
}

@Composable
private fun ColumnScope.PagesStage(
    pageIndex: Int,
    onPageChange: (Int) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = pageIndex) { ONBOARDING_PAGE_COUNT }

    LaunchedEffect(pageIndex) {
        if (pagerState.currentPage != pageIndex) pagerState.animateScrollToPage(pageIndex)
    }
    LaunchedEffect(pagerState.currentPage) { onPageChange(pagerState.currentPage) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(Dimens.SpacingS),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
            Text(stringResource(R.string.onboarding_skip))
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
        val content = onboardingPages[page]
        Column(
            modifier = Modifier.fillMaxSize().padding(Dimens.SpacingXl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = content.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ILLUSTRATION_SIZE),
            )
            Text(
                text = stringResource(content.titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.SpacingXl),
            )
            Text(
                text = stringResource(content.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.SpacingL),
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(Dimens.SpacingL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            repeat(ONBOARDING_PAGE_COUNT) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(end = Dimens.SpacingS)
                        .size(if (selected) DOT_SELECTED else DOT_PLAIN)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
        Button(onClick = onNext, modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary)) {
            Text(stringResource(R.string.onboarding_next))
        }
    }
}

@Composable
private fun ChoiceStage(
    installingDemo: Boolean,
    demoFailed: Boolean,
    onLoadDemo: () -> Unit,
    onCreateVessel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.SpacingXl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_start_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(Dimens.SpacingXl))

        Button(
            onClick = onLoadDemo,
            enabled = !installingDemo,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
        ) {
            if (installingDemo) {
                CircularProgressIndicator(
                    modifier = Modifier.size(PROGRESS_SIZE),
                    strokeWidth = PROGRESS_STROKE,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = stringResource(R.string.onboarding_demo_loading),
                    modifier = Modifier.padding(start = Dimens.SpacingM),
                )
            } else {
                Text(stringResource(R.string.onboarding_start_demo))
            }
        }
        Text(
            text = stringResource(R.string.onboarding_start_demo_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpacingXs, bottom = Dimens.SpacingXl),
        )

        OutlinedButton(
            onClick = onCreateVessel,
            enabled = !installingDemo,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
        ) {
            Text(stringResource(R.string.onboarding_start_create))
        }
        Text(
            text = stringResource(R.string.onboarding_start_create_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpacingXs),
        )

        if (demoFailed) {
            Text(
                text = stringResource(R.string.demo_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Dimens.SpacingL),
            )
        }
    }
}

/** One teaching page — §14's four screens: what it is, build, grade, export. */
private data class OnboardingPage(val icon: ImageVector, val titleRes: Int, val bodyRes: Int)

private val onboardingPages = listOf(
    OnboardingPage(Icons.Filled.WifiOff, R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingPage(Icons.Filled.Layers, R.string.onboarding_2_title, R.string.onboarding_2_body),
    OnboardingPage(Icons.Filled.CheckCircle, R.string.onboarding_3_title, R.string.onboarding_3_body),
    OnboardingPage(
        Icons.AutoMirrored.Filled.InsertDriveFile,
        R.string.onboarding_4_title,
        R.string.onboarding_4_body,
    ),
)

private val ILLUSTRATION_SIZE = 96.dp
private val DOT_SELECTED = 10.dp
private val DOT_PLAIN = 8.dp
private val PROGRESS_SIZE = 20.dp
private val PROGRESS_STROKE = 2.dp
