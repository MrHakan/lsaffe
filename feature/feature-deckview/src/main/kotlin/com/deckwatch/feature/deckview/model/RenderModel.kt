package com.deckwatch.feature.deckview.model

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.StatusFlag
import com.deckwatch.core.model.Zone
import com.deckwatch.feature.deckview.geometry.DeckOutlineGeometry
import com.deckwatch.feature.deckview.geometry.DeckStackOrder
import com.deckwatch.feature.deckview.geometry.Polygons
import com.deckwatch.feature.deckview.geometry.Vec2

/** One equipment marker, flattened for the renderer — §10.4. */
data class MarkerNode(
    val equipmentId: String,
    val tag: String,
    val typeName: String,
    val symbolKey: String,
    val condition: ConditionGrade,
    val statusFlag: StatusFlag,
    /** Plan-space position, 0..1 — §6.5 `posX` / `posY`. */
    val position: Vec2,
    val zoneId: String?,
    val nextDueDate: Long?,
    val overdue: Boolean,
) {
    /** Out-of-service equipment renders with a diagonal hatch overlay — §10.4. */
    val outOfService: Boolean
        get() = statusFlag == StatusFlag.OUT_OF_SERVICE ||
            statusFlag == StatusFlag.CONDEMNED ||
            condition == ConditionGrade.OUT_OF_SERVICE
}

/** A spatial zone on a deck (§6.4) with the aggregate the low-zoom LOD draws (§7.2). */
data class ZoneNode(
    val zoneId: String,
    val name: String,
    val polygon: List<Vec2>,
    val colorArgb: Int,
    val centroid: Vec2,
    val worstCondition: ConditionGrade,
    val overdueCount: Int,
    val markerCount: Int,
)

/** One deck, ready to project — everything the canvas needs without touching a repository. */
data class DeckNode(
    val deckId: String,
    val name: String,
    val shortCode: String,
    val levelIndex: Int,
    /** Rank in the stack, bottom = 0 — never the raw `levelIndex` (§6.2, §7.2). */
    val levelZ: Int,
    val plan: DeckPlan,
    val outline: List<Vec2>,
    val planHash: Int,
    val colorTint: Int?,
    val zones: List<ZoneNode>,
    val markers: List<MarkerNode>,
    val worstCondition: ConditionGrade,
    val overdueCount: Int,
) {
    /** Markers with no zone — they aggregate into one deck-level dot at low zoom. */
    val unzonedMarkers: List<MarkerNode> get() = markers.filter { it.zoneId == null }
}

/** The whole vessel as the canvas sees it. Decks are in painter's order: bottom first (§7.2). */
data class StackRenderModel(
    val vesselId: String? = null,
    val vesselName: String = "",
    val decks: List<DeckNode> = emptyList(),
    val unplacedCount: Int = 0,
) {
    val isEmpty: Boolean get() = decks.isEmpty()

    fun deck(deckId: String?): DeckNode? = decks.firstOrNull { it.deckId == deckId }

    /** Decks highest first — the order the deck spine lists them in (§7.2). */
    val decksTopFirst: List<DeckNode> get() = decks.asReversed()
}

/** Worst-condition aggregation for a deck, a zone or the spine's coloured dot — §7.2. */
object ConditionAggregate {

    /**
     * The worst grade in [grades].
     *
     * `NOT_CHECKED` scores -1 in §6.9, below `OUT_OF_SERVICE`, but it is an *absence* of a grade,
     * not the worst one: a deck holding one defective item and forty ungraded ones must show orange,
     * not grey. So graded items decide, and `NOT_CHECKED` is the answer only when nothing on the
     * deck has been graded at all.
     */
    fun worst(grades: Iterable<ConditionGrade>): ConditionGrade {
        var worst: ConditionGrade? = null
        for (grade in grades) {
            if (grade == ConditionGrade.NOT_CHECKED) continue
            if (worst == null || grade.score < worst.score) worst = grade
        }
        return worst ?: ConditionGrade.NOT_CHECKED
    }

    /** The worst grade across a set of markers. */
    fun worstOf(markers: Iterable<MarkerNode>): ConditionGrade = worst(markers.map { it.condition })
}

/**
 * Assembles the render model from the repositories' output — pure, so the whole shape of a frame is
 * unit-testable without a canvas (§7.2 performance budget: nothing here runs per frame).
 */
object RenderModelAssembler {

    /**
     * @param today epoch-day used for the overdue test; `nextDueDate < today` is overdue (§11.1).
     * @param typeNames `typeKey` → display name, for the marker's TalkBack announcement (§14).
     */
    @Suppress("LongParameterList") // One assembly point; every argument is a distinct data source.
    fun assemble(
        vesselId: String?,
        vesselName: String,
        decks: List<Deck>,
        zonesByDeck: Map<String, List<Zone>>,
        equipment: List<Equipment>,
        typeNames: Map<String, String>,
        today: Long,
    ): StackRenderModel {
        val placed = equipment.filter { it.deletedAt == null && it.parentId == null }
        val byDeck = placed.groupBy { it.deckId }
        val nodes = DeckStackOrder.bottomFirst(decks).map { (deck, levelZ) ->
            deckNode(
                deck = deck,
                levelZ = levelZ,
                zones = zonesByDeck[deck.id].orEmpty(),
                equipment = byDeck[deck.id].orEmpty(),
                typeNames = typeNames,
                today = today,
            )
        }
        return StackRenderModel(
            vesselId = vesselId,
            vesselName = vesselName,
            decks = nodes,
            unplacedCount = byDeck[null].orEmpty().size,
        )
    }

    @Suppress("LongParameterList") // Internal helper mirroring assemble's inputs for one deck.
    private fun deckNode(
        deck: Deck,
        levelZ: Int,
        zones: List<Zone>,
        equipment: List<Equipment>,
        typeNames: Map<String, String>,
        today: Long,
    ): DeckNode {
        val markers = equipment
            .sortedWith(compareBy({ it.posY }, { it.posX }, { it.id }))
            .map { item -> markerNode(item, typeNames, today) }
        val byZone = markers.groupBy { it.zoneId }
        val zoneNodes = zones.sortedBy { it.sortOrder }.map { zone ->
            val inZone = byZone[zone.id].orEmpty()
            val polygon = Polygons.of(zone.polygon)
            ZoneNode(
                zoneId = zone.id,
                name = zone.name,
                polygon = polygon,
                colorArgb = zone.colorArgb,
                centroid = Polygons.centroid(polygon),
                worstCondition = ConditionAggregate.worstOf(inZone),
                overdueCount = inZone.count { it.overdue },
                markerCount = inZone.size,
            )
        }
        return DeckNode(
            deckId = deck.id,
            name = deck.name,
            shortCode = deck.shortCode?.takeIf { it.isNotBlank() } ?: shortCodeFrom(deck.name),
            levelIndex = deck.levelIndex,
            levelZ = levelZ,
            plan = deck.plan,
            outline = DeckOutlineGeometry.polygon(deck.plan),
            planHash = DeckOutlineGeometry.planHash(deck.plan),
            colorTint = deck.colorTint,
            zones = zoneNodes,
            markers = markers,
            worstCondition = ConditionAggregate.worstOf(markers),
            overdueCount = markers.count { it.overdue },
        )
    }

    private fun markerNode(item: Equipment, typeNames: Map<String, String>, today: Long): MarkerNode {
        val due = item.nextDueDate
        return MarkerNode(
            equipmentId = item.id,
            tag = item.tag,
            typeName = typeNames[item.typeKey] ?: item.typeKey,
            symbolKey = item.symbolKey,
            condition = item.condition,
            statusFlag = item.statusFlag,
            position = Vec2(item.posX.coerceIn(0f, 1f), item.posY.coerceIn(0f, 1f)),
            zoneId = item.zoneId,
            nextDueDate = due,
            overdue = due != null && due < today,
        )
    }

    /** A deck with no `shortCode` still needs a spine pill: initials, up to two characters. */
    internal fun shortCodeFrom(name: String): String {
        val words = name.split(' ', '-', '_').filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> words.take(2).map { it.first().uppercaseChar() }.joinToString("")
        }
    }
}
