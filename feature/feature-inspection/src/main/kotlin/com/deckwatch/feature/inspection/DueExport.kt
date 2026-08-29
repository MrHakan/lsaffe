package com.deckwatch.feature.inspection

import com.deckwatch.core.common.Dates
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import kotlinx.serialization.Serializable

/**
 * The Due list handed to an exporter — §12 ("export the Due list as HTML or as clipboard text"),
 * §13.3 scope *Due list*.
 *
 * Serializable and free of Compose and Android types, so `feature-report` can consume it directly
 * (and, later, round-trip it through the JSON payload of §13.2) without depending on this module's
 * UI. It is a **snapshot of what the officer is looking at**: the selected segment, the filters that
 * produced it, and the rows in the order they appear on screen.
 */
@Serializable
data class DueExportRequest(
    val vesselName: String,
    val vesselImoNumber: String? = null,
    val segment: DueSegment,
    /** Epoch-days — the day the list was taken, so a printed sheet dates itself. */
    val generatedOnEpochDay: Long,
    val filters: DueExportFilters = DueExportFilters(),
    val lines: List<DueExportLine> = emptyList(),
    /** Set when the export was taken in survey-prep mode; epoch-days. */
    val surveyCertExpiry: Long? = null,
)

/** The filter set as human-readable names, so the export states what it is a list *of*. */
@Serializable
data class DueExportFilters(
    val deckName: String? = null,
    val zoneName: String? = null,
    val categoryName: String? = null,
    val group: EquipmentGroup? = null,
    val performedBy: PerformedBy? = null,
    val condition: ConditionGrade? = null,
)

/** One exported row: tag, task, due date and who must do it — §12. */
@Serializable
data class DueExportLine(
    val tag: String,
    val task: String,
    /** Epoch-days. */
    val dueDate: Long,
    /** `dueDate - generatedOn`, signed. */
    val dayDelta: Long,
    val performedBy: PerformedBy,
    val deck: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val equipmentId: String? = null,
)

/**
 * The localised chrome of the plaintext export. The renderer stays pure by taking its words as
 * data; the composable fills this from `strings.xml` so the clipboard text comes out in the
 * officer's language — C8.
 */
data class DueExportLabels(
    val header: String,
    val vesselLabel: String,
    val segmentLabel: String,
    val segmentName: String,
    val filtersLabel: String,
    val filtersNone: String,
    val generatedLabel: String,
    val surveyLabel: String,
    val columnTag: String,
    val columnDeck: String,
    val columnTask: String,
    val columnDue: String,
    val columnDays: String,
    val columnBy: String,
    val totalLabel: String,
    val emptyLabel: String,
    val performedByNames: Map<PerformedBy, String> = emptyMap(),
) {
    fun performerName(performedBy: PerformedBy): String =
        performedByNames[performedBy] ?: performedBy.name
}

/**
 * Render [request] as a fixed-width plaintext table for the clipboard — §12.
 *
 * Deliberately plain: no tabs, no markdown, columns padded with spaces so the table survives being
 * pasted into WhatsApp, a noon report or a log-book entry.
 */
fun renderDueListText(request: DueExportRequest, labels: DueExportLabels): String {
    val builder = StringBuilder()
    builder.appendLine(labels.header)
    builder.append(labels.vesselLabel).append(": ").append(request.vesselName)
    request.vesselImoNumber?.takeIf { it.isNotBlank() }?.let { builder.append(" (IMO ").append(it).append(')') }
    builder.appendLine()
    builder.append(labels.segmentLabel).append(": ").appendLine(labels.segmentName)
    request.surveyCertExpiry?.let {
        builder.append(labels.surveyLabel).append(": ").appendLine(Dates.formatIso(it))
    }
    builder.append(labels.filtersLabel).append(": ").appendLine(describeFilters(request.filters, labels))
    builder.append(labels.generatedLabel).append(": ").appendLine(Dates.formatIso(request.generatedOnEpochDay))
    builder.appendLine()

    if (request.lines.isEmpty()) {
        builder.appendLine(labels.emptyLabel)
        return builder.toString()
    }

    val tagWidth = widthOf(labels.columnTag, request.lines.map { it.tag })
    val deckWidth = widthOf(labels.columnDeck, request.lines.map { it.deck.orEmpty() })
    val taskWidth = widthOf(labels.columnTask, request.lines.map { it.task })
    val dueWidth = widthOf(labels.columnDue, request.lines.map { Dates.formatIso(it.dueDate) })
    val daysWidth = widthOf(labels.columnDays, request.lines.map { formatDelta(it.dayDelta) })
    val byWidth = widthOf(labels.columnBy, request.lines.map { labels.performerName(it.performedBy) })

    fun row(tag: String, deck: String, task: String, due: String, days: String, by: String): String =
        listOf(
            tag.padEnd(tagWidth),
            deck.padEnd(deckWidth),
            task.padEnd(taskWidth),
            due.padEnd(dueWidth),
            days.padStart(daysWidth),
            by.padEnd(byWidth),
        ).joinToString(COLUMN_GAP).trimEnd()

    val heading = row(
        labels.columnTag,
        labels.columnDeck,
        labels.columnTask,
        labels.columnDue,
        labels.columnDays,
        labels.columnBy,
    )
    builder.appendLine(heading)
    builder.appendLine("-".repeat(heading.length))
    for (line in request.lines) {
        builder.appendLine(
            row(
                line.tag,
                line.deck.orEmpty(),
                line.task,
                Dates.formatIso(line.dueDate),
                formatDelta(line.dayDelta),
                labels.performerName(line.performedBy),
            ),
        )
    }
    builder.appendLine()
    builder.append(labels.totalLabel).append(": ").appendLine(request.lines.size)
    return builder.toString()
}

/** Signed day delta as the list shows it: `-12` late, `+30` in hand, `0` today. */
fun formatDelta(dayDelta: Long): String = when {
    dayDelta > 0 -> "+$dayDelta"
    else -> dayDelta.toString()
}

private fun describeFilters(filters: DueExportFilters, labels: DueExportLabels): String {
    val parts = buildList {
        filters.deckName?.let { add(it) }
        filters.zoneName?.let { add(it) }
        filters.categoryName?.let { add(it) }
        filters.group?.let { add(it.name) }
        filters.performedBy?.let { add(labels.performerName(it)) }
        filters.condition?.let { add(it.name) }
    }
    return if (parts.isEmpty()) labels.filtersNone else parts.joinToString(" · ")
}

private fun widthOf(heading: String, values: List<String>): Int =
    maxOf(heading.length, values.maxOfOrNull { it.length } ?: 0)

private const val COLUMN_GAP = "  "
