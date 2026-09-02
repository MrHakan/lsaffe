package com.deckwatch.feature.deckview.render

/** One node in the canvas's accessibility tree — a deck or one of its markers (§14). */
internal data class DeckSemanticNode(
    val id: String,
    val levelZ: Int,
    val planX: Float,
    val planY: Float,
    val description: String,
    val clickLabel: String,
    val onClick: () -> Unit,
)
