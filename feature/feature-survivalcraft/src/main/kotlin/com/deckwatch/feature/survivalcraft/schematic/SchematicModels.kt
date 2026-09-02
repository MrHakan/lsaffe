package com.deckwatch.feature.survivalcraft.schematic

import kotlinx.serialization.Serializable

/**
 * The schematic definitions of §7.6 are **data**, not code.
 *
 * Every survival-craft / fixed-system view is one [SchematicDef] authored as JSON under
 * `src/main/assets/schematics/`: a vector elevation drawn from a handful of primitives in a
 * 0..1 coordinate space, plus the hotspots that stand for the sub-components which get inspected.
 * Adding a new craft type is a JSON file and an index entry — never a new screen.
 *
 * Coordinates are fractions of the drawing box: `x` grows to starboard-of-the-page (right),
 * `y` grows downwards, exactly like Compose's canvas. Nothing here knows about dp or pixels.
 */

/** The primitive kinds the renderer understands. Deliberately few — this is line art, not SVG. */
@Serializable
enum class ShapeKind {
    /** Closed path through the vertex list. */
    POLYGON,

    /** Open path through the vertex list. */
    POLYLINE,

    /** `points = [cx, cy, rx, ry]`. */
    ELLIPSE,

    /** `points = [left, top, right, bottom]`. */
    RECT,
}

/** Stroke roles map to theme colours in the renderer, so the art follows Day / Night / Bridge. */
@Serializable
enum class StrokeRole {
    /** The main outline of the craft or appliance. */
    OUTLINE,

    /** Structure that carries load: davit arms, cradles, racks. */
    STRUCTURE,

    /** Secondary detail: strakes, hatches, panel lines. */
    DETAIL,

    /** Wires, falls, painters, pilot lines — drawn thin, usually dashed. */
    LINE,

    /** Nothing is stroked (fill only). */
    NONE,
}

/** Fill roles, likewise resolved against the theme rather than hard-coded. */
@Serializable
enum class FillRole {
    NONE,

    /** The body of the craft — opaque, so structure behind it reads as "behind". */
    BODY,

    /** Sub-assemblies drawn as blocks: engine, tanks, lockers, cylinders. */
    PANEL,

    /** Small emphasised parts: hooks, sheaves, plugs. */
    ACCENT,
}

/** One primitive of the elevation. */
@Serializable
data class SchematicShape(
    val kind: ShapeKind,
    /** Flat `x, y, x, y …` list in 0..1 space; see [ShapeKind] for the per-kind meaning. */
    val points: List<Float>,
    val stroke: StrokeRole = StrokeRole.OUTLINE,
    val fill: FillRole = FillRole.NONE,
    /** Multiplier on the renderer's base stroke width. */
    val weight: Float = 1f,
    val dashed: Boolean = false,
)

/**
 * A tappable sub-component marker.
 *
 * [x] / [y] anchor the hotspot to the feature it names. [markerX] / [markerY] are where the
 * touch target is actually drawn: crowded areas (a fall and the hook it ends in) get the marker
 * moved into clear space with a leader line back to the anchor, the way a technical illustration
 * does it. Both default to the anchor.
 *
 * [childTypeKey] is the catalogue type of the sub-component — a real `Equipment` row with
 * `parentId` set to the parent craft (§6.5), never a special-case table. It may be null when the
 * bundled catalogue has no type for that part (a boat's drain plug, a davit limit switch); such a
 * hotspot still works — the add flow lets the officer pick any type, and the binding is kept by
 * [com.deckwatch.feature.survivalcraft.HOTSPOT_ATTRIBUTE_KEY].
 *
 * [ordinal] disambiguates two hotspots that share a [childTypeKey] — the fore and aft on-load
 * release hooks are both `LSA_ONLOAD_RELEASE_GEAR`. It is a *fallback* only; see
 * [com.deckwatch.feature.survivalcraft.HotspotMatching].
 */
@Serializable
data class SchematicHotspot(
    val key: String,
    val labelEn: String,
    val labelTr: String,
    val x: Float,
    val y: Float,
    val markerX: Float? = null,
    val markerY: Float? = null,
    val childTypeKey: String? = null,
    val ordinal: Int = 0,
    /** Task keys this part is normally inspected under — a hint shown on the hotspot sheet. */
    val taskKeys: List<String> = emptyList(),
) {
    /** Where the touch target sits. */
    val touchX: Float get() = markerX ?: x

    /** Where the touch target sits. */
    val touchY: Float get() = markerY ?: y

    /** True when the marker was moved off its anchor and needs a leader line. */
    val hasLeader: Boolean get() = markerX != null || markerY != null
}

/** Which extra panels a schematic wants below the drawing — §7.6. */
@Serializable
enum class SchematicPanel { COMPONENTS, INVENTORY, TASKS, DRILL_LOG }

/** One authored schematic. */
@Serializable
data class SchematicDef(
    val key: String,
    /** Equipment type keys this schematic is drawn for. */
    val appliesToTypeKeys: List<String> = emptyList(),
    val titleEn: String,
    val titleTr: String,
    /** Width / height of the drawing box. The renderer letterboxes to preserve it. */
    val aspect: Float = 1.4f,
    val shapes: List<SchematicShape> = emptyList(),
    val hotspots: List<SchematicHotspot> = emptyList(),
    /** Key into [com.deckwatch.feature.survivalcraft.inventory.InventoryTemplates]; null = no inventory panel. */
    val inventoryTemplateKey: String? = null,
    val panels: List<SchematicPanel> = listOf(SchematicPanel.COMPONENTS, SchematicPanel.TASKS),
)

/** The `index.json` payload: the list of schematic files that ship with the module. */
@Serializable
internal data class SchematicIndex(val files: List<String>)
