package com.deckwatch.feature.deckview.gesture

/** Everything the canvas gesture layer reports upwards — the §7.2 gesture table. */
data class DeckGestureCallbacks(
    val onTapMarker: (equipmentId: String, deckId: String) -> Unit = { _, _ -> },
    val onTapDeck: (deckId: String) -> Unit = {},
    val onTapEmpty: () -> Unit = {},
    /** Double tap: zoom to fit the deck under the finger, or the focused one. */
    val onZoomToFit: (deckId: String?) -> Unit = {},
    /** Long-press on a marker, before the drag starts. */
    val onMarkerPickedUp: (equipmentId: String, deckId: String) -> Unit = { _, _ -> },
    /** Drag finished. [inside] is false when the drop landed outside the deck outline. */
    val onMarkerDropped: (
        equipmentId: String,
        deckId: String,
        planX: Float,
        planY: Float,
        inside: Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
    /** Long-press on empty plan: "Add equipment here" at that coordinate (§7.2, §7.5). */
    val onAddEquipmentAt: (deckId: String, planX: Float, planY: Float) -> Unit = { _, _, _ -> },
)
