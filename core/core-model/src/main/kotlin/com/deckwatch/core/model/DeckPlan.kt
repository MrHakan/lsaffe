package com.deckwatch.core.model

import kotlinx.serialization.Serializable

/** A point in the normalised 0..1 plan coordinate space. */
@Serializable
data class PlanPoint(val x: Float, val y: Float)

/**
 * A deck plan is a normalised 2D outline in a unit coordinate space so that
 * plans scale to any screen and any vessel size — §6.3.
 */
@Serializable
data class DeckPlan(
    val shape: PlanShape,
    val lengthRatio: Float = 1.0f,
    val breadthRatio: Float = 1.0f,
    val polygon: List<PlanPoint> = emptyList(),
    val bowSharpness: Float = 0.5f,
    val sternRounding: Float = 0.3f,
    val bowAtTop: Boolean = true,
    val backgroundImageUri: String? = null,
    val backgroundOpacity: Float = 0.35f,
)

/** One of the six built-in deck plan presets — §6.3. */
@Serializable
data class PlanPreset(
    val key: String,
    val nameEn: String,
    val nameTr: String,
    val plan: DeckPlan,
    val suggestedShortCode: String? = null,
)
