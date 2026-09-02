package com.deckwatch.feature.deckview.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.EquipmentMarker
import com.deckwatch.core.designsystem.components.MarkerLod
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.Zone
import com.deckwatch.feature.deckview.R
import kotlin.math.abs

/**
 * PLAN MODE — the 2.5D deck canvas of §7.1 A and §7.2.
 *
 * One deck at a time: its outline, its zones, and every item placed on it, drawn through the
 * invertible projection in [PlanGeometry] so a long-press lands exactly where the finger was.
 *
 * The three gestures are the ones the spec asks for and no more:
 * - **tap a marker** opens the item;
 * - **long-press empty deck** starts an item at that point, already in the right zone;
 * - **long-press a marker and drag** moves it, writing once on release.
 *
 * Pan and zoom are deliberately absent. Plan space is normalised, so a deck always fits the screen
 * by construction; a viewport transform would buy nothing and add a whole class of "my markers
 * vanished" bugs on top of the projection.
 */
@Composable
fun DeckCanvasScreen(
    modifier: Modifier = Modifier,
    onOpenEquipment: (String) -> Unit = {},
    onPlaceEquipment: (deckId: String, zoneId: String?, posX: Float, posY: Float) -> Unit = { _, _, _, _ -> },
    viewModel: DeckCanvasViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        if (state.decks.size > 1) {
            DeckSelector(
                decks = state.decks,
                selectedId = state.selectedDeck?.id,
                onSelect = viewModel::selectDeck,
            )
        }

        val deck = state.selectedDeck
        if (deck == null) {
            EmptyCanvas(loading = state.loading)
        } else {
            DeckCanvas(
                deck = deck,
                zones = state.zones,
                equipment = state.equipment,
                types = state.types,
                isoAngleDeg = state.isoAngleDeg,
                onOpenEquipment = onOpenEquipment,
                onPlaceAt = { point ->
                    val snapped = viewModel.snap(point)
                    onPlaceEquipment(deck.id, viewModel.zoneAt(snapped), snapped.x, snapped.y)
                },
                onMove = viewModel::moveTo,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DeckSelector(decks: List<Deck>, selectedId: String?, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
    ) {
        items(items = decks, key = { it.id }) { deck ->
            FilterChip(
                selected = deck.id == selectedId,
                onClick = { onSelect(deck.id) },
                label = { Text(deck.shortCode?.takeIf { it.isNotBlank() } ?: deck.name) },
                modifier = Modifier.padding(end = Dimens.SpacingS),
            )
        }
    }
}

@Composable
private fun EmptyCanvas(loading: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(if (loading) R.string.canvas_loading else R.string.canvas_no_decks),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Dimens.SpacingXl),
        )
    }
}

@Composable
private fun DeckCanvas(
    deck: Deck,
    zones: List<Zone>,
    equipment: List<Equipment>,
    types: Map<String, EquipmentType>,
    isoAngleDeg: Float,
    onOpenEquipment: (String) -> Unit,
    onPlaceAt: (PlanPoint) -> Unit,
    onMove: (String, PlanPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val outlineColor = MaterialTheme.colorScheme.outline
    val deckColor = MaterialTheme.colorScheme.surfaceVariant
    val zoneStroke = MaterialTheme.colorScheme.primary
    val markerSize = MarkerLod.MEDIUM.size

    // A drag follows the finger from local state and writes once, on release: a drag across the
    // deck is one move, not a hundred.
    var draggingId by remember { mutableStateOf<String?>(null) }
    var draggingAt by remember { mutableStateOf<PlanPoint?>(null) }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val width = maxWidth
        val height = maxHeight

        // Both gesture handlers work in fractions of the box, so the plan mapping needs no pixels.
        fun toPlan(offset: Offset, sizeX: Float, sizeY: Float): PlanPoint = PlanGeometry.unproject(
            x = (offset.x / sizeX).coerceIn(0f, 1f),
            y = (offset.y / sizeY).coerceIn(0f, 1f),
            angleDeg = isoAngleDeg,
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(deck.id, isoAngleDeg, equipment) {
                    detectTapGestures(
                        onTap = { offset ->
                            hitTest(offset, equipment, isoAngleDeg, size.width.toFloat(), size.height.toFloat())
                                ?.let { onOpenEquipment(it.id) }
                        },
                        onLongPress = { offset ->
                            // A long press on a marker starts a drag; only empty deck places.
                            val hit = hitTest(
                                offset,
                                equipment,
                                isoAngleDeg,
                                size.width.toFloat(),
                                size.height.toFloat(),
                            )
                            if (hit == null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlaceAt(toPlan(offset, size.width.toFloat(), size.height.toFloat()))
                            }
                        },
                    )
                }
                .pointerInput(deck.id, isoAngleDeg, equipment) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val hit = hitTest(
                                offset,
                                equipment,
                                isoAngleDeg,
                                size.width.toFloat(),
                                size.height.toFloat(),
                            )
                            if (hit != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingId = hit.id
                                draggingAt = toPlan(offset, size.width.toFloat(), size.height.toFloat())
                            }
                        },
                        onDrag = { change, _ ->
                            if (draggingId != null) {
                                draggingAt = toPlan(
                                    change.position,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                )
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            val id = draggingId
                            val at = draggingAt
                            draggingId = null
                            draggingAt = null
                            if (id != null && at != null) onMove(id, at)
                        },
                        onDragCancel = {
                            draggingId = null
                            draggingAt = null
                        },
                    )
                },
        ) {
            drawDeck(
                outline = PlanGeometry.outline(deck.plan),
                zones = zones,
                isoAngleDeg = isoAngleDeg,
                deckColor = deckColor,
                outlineColor = outlineColor,
                zoneColor = zoneStroke,
            )
        }

        for (item in equipment) {
            val point = item.id.takeIf { it == draggingId }?.let { draggingAt }
                ?: PlanPoint(item.posX, item.posY)
            val projected = PlanGeometry.project(point.x, point.y, isoAngleDeg)
            EquipmentMarker(
                symbolKey = types[item.typeKey]?.symbolKey ?: item.symbolKey,
                condition = item.condition,
                size = markerSize,
                selected = item.id == draggingId,
                contentDescription = item.tag,
                modifier = Modifier.offset(
                    x = width * projected.x - markerSize / 2,
                    y = height * projected.y - markerSize / 2,
                ),
            )
        }

        if (equipment.isEmpty()) {
            Text(
                text = stringResource(R.string.canvas_hint_long_press),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Dimens.SpacingL),
            )
        }
    }
}

/**
 * The item under [offset], or null.
 *
 * Hit testing happens in canvas space rather than plan space: the tolerance is a fingertip, which
 * is a fixed size on screen, and the projection squashes the fore-and-aft axis — so a plan-space
 * radius would be a wide oval near the bow and a narrow one amidships.
 */
private fun hitTest(
    offset: Offset,
    equipment: List<Equipment>,
    isoAngleDeg: Float,
    widthPx: Float,
    heightPx: Float,
): Equipment? = equipment
    .map { item ->
        val projected = PlanGeometry.project(item.posX, item.posY, isoAngleDeg)
        item to Offset(projected.x * widthPx, projected.y * heightPx)
    }
    .filter { (_, at) -> abs(at.x - offset.x) <= HIT_RADIUS_PX && abs(at.y - offset.y) <= HIT_RADIUS_PX }
    .minByOrNull { (_, at) -> (at - offset).getDistanceSquared() }
    ?.first

private fun DrawScope.drawDeck(
    outline: List<PlanPoint>,
    zones: List<Zone>,
    isoAngleDeg: Float,
    deckColor: Color,
    outlineColor: Color,
    zoneColor: Color,
) {
    val deckPath = projectedPath(outline, isoAngleDeg)
    drawPath(deckPath, color = deckColor)
    drawPath(deckPath, color = outlineColor, style = Stroke(width = OUTLINE_STROKE_PX))

    for (zone in zones) {
        if (zone.polygon.size < MIN_ZONE_POINTS) continue
        val path = projectedPath(zone.polygon, isoAngleDeg)
        drawPath(path, color = Color(zone.colorArgb))
        drawPath(path, color = zoneColor, style = Stroke(width = ZONE_STROKE_PX))
    }
}

/** A closed path in canvas pixels, from plan-space points through the projection. */
private fun DrawScope.projectedPath(points: List<PlanPoint>, isoAngleDeg: Float): Path = Path().apply {
    points.forEachIndexed { index, point ->
        val projected = PlanGeometry.project(point.x, point.y, isoAngleDeg)
        val x = projected.x * size.width
        val y = projected.y * size.height
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Fingertip tolerance around a marker, in pixels. */
private const val HIT_RADIUS_PX = 48f
private const val OUTLINE_STROKE_PX = 3f
private const val ZONE_STROKE_PX = 2f
private const val MIN_ZONE_POINTS = 3
