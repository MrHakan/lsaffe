package com.deckwatch.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One sans for UI (system default), one monospace for tags, serials and
 * certificate numbers — those get read aloud and cross-checked against
 * paperwork — §14.
 */
val TagFontFamily: FontFamily = FontFamily.Monospace

val DeckWatchTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
)

/**
 * Section headings in the app's signage voice — §14.
 *
 * Capitals on wide tracking is how a deck marking, a locker label and a ship's plate are set, and
 * it is the app's display personality: there is no bundled typeface (an offline app should not
 * carry a font file it does not need), so the character comes from treatment. Small and tracked
 * rather than large and bold, because a heading on a phone held at arm's length needs to be
 * *distinguishable* from body text, not louder than it.
 *
 * Use it through [com.deckwatch.core.designsystem.components.PlateHeading], which adds the rule.
 */
@Composable
@ReadOnlyComposable
fun plateTextStyle(): TextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.2.sp,
)

/** Monospace style for equipment tags / serial numbers. */
@Composable
@ReadOnlyComposable
fun tagTextStyle(): TextStyle = TextStyle(
    fontFamily = TagFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    letterSpacing = 0.5.sp,
)
