package com.deckwatch.feature.report

import com.deckwatch.core.common.Dates
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Everything one rendered report needs. Assembled by [PayloadAssembler] on a background
 * dispatcher; consumed by [HtmlReportRenderer] on any thread.
 *
 * @param photos original photo URI -> `data:` URI. A URI absent from the map renders as a
 *   placeholder rather than a broken image — a 200 MB or corrupt photo must not cost the officer
 *   the report (§17.4).
 * @param typeNames `typeKey` -> catalogue display name (§9.1).
 * @param taskTitles `taskKey` -> task title (§6.6).
 */
data class ReportDocument(
    val payload: DeckWatchExportPayload,
    val options: ExportOptions = ExportOptions(),
    val labels: ReportLabels = ReportLabels(),
    val photos: Map<String, String> = emptyMap(),
    val typeNames: Map<String, String> = emptyMap(),
    val taskTitles: Map<String, String> = emptyMap(),
    val todayEpochDay: Long = Dates.todayEpochDay(),
)

/**
 * Renders one self-contained `.html` file — §13.2.
 *
 * Pure Kotlin: no Android, no Compose, no I/O. Given the same [ReportDocument] it returns the same
 * string, which is what makes the whole of §13 testable on the JVM.
 *
 * The document is laid out in the exact order §13.2 specifies:
 * 1. `<style>` — the complete stylesheet, inline, including the A4 print rules of §13.4.
 * 2. `<div id="report">` — the *static* report. Every table, every deck plan and every photo is
 *    already here; the file is fully readable with JavaScript disabled.
 * 3. `<script id="deckwatch-data" type="application/json">` — the payload, which is what makes the
 *    file re-importable (§13.5). Escaped so no user text can close the block early.
 * 4. `<script>` — the interactive layer: deck tabs, a filter box and an SVG re-render of each plan
 *    from the JSON. Enhancement only.
 *
 * The §17.6 disclaimer closes every document, verbatim.
 *
 * @param groundHex symbol key -> signage ground colour. Injected so a test can pin marker colours
 *   without loading the design system's symbol table.
 */
class HtmlReportRenderer(
    private val groundHex: (String) -> String = MarkerPalette::groundHex,
) {

    fun render(doc: ReportDocument): String {
        val out = StringBuilder(INITIAL_CAPACITY)
        val vessel = doc.payload.vessels.firstOrNull()
        val title = documentTitle(doc, vessel)

        out.append("<!doctype html>\n")
        out.append("<html lang=\"en\">\n<head>\n")
        out.append("<meta charset=\"utf-8\">\n")
        out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        out.append("<title>").append(title.esc()).append("</title>\n")
        out.append("<style>\n").append(ReportAssets.CSS).append("\n</style>\n")
        out.append("</head>\n<body>\n")

        out.append("<div id=\"report\">\n")
        appendHeader(out, doc, vessel, title)
        appendControls(out, doc)
        appendSummary(out, doc)
        appendScopeContent(out, doc)
        appendFooter(out, doc)
        out.append("</div>\n")

        appendDataBlock(out, doc)
        appendInteractiveLayer(out, doc)

        out.append("</body>\n</html>\n")
        return out.toString()
    }

    // ------------------------------------------------------------------ chrome

    private fun documentTitle(doc: ReportDocument, vessel: Vessel?): String {
        val vesselName = vessel?.name ?: doc.payload.dueList?.vesselName ?: doc.labels.documentTitle
        return "$vesselName — ${doc.labels.scopeName(doc.options.scope)}"
    }

    private fun appendHeader(out: StringBuilder, doc: ReportDocument, vessel: Vessel?, title: String) {
        val l = doc.labels
        val name = vessel?.name ?: doc.payload.dueList?.vesselName ?: l.documentTitle
        val imo = vessel?.imoNumber ?: doc.payload.dueList?.vesselImoNumber

        out.append("<header class=\"doc\">\n")
        out.append("<div class=\"type\">").append(l.scopeName(doc.options.scope).esc()).append("</div>\n")
        out.append("<div class=\"vessel\">").append(name.esc()).append("</div>\n")
        out.append("<div class=\"particulars\">\n")
        particular(out, l.imo, imo)
        particular(out, l.callSign, vessel?.callSign)
        particular(out, l.flag, vessel?.let { it.flagOtherName ?: it.flag.name })
        particular(out, l.classSociety, vessel?.classSociety?.name)
        particular(out, l.vesselTypeLabel, vessel?.vesselType?.name)
        particular(out, l.grossTonnage, vessel?.grossTonnage?.toString())
        particular(out, l.certExpiry, vessel?.safetyEquipmentCertExpiry?.let(Dates::formatIso))
        particular(out, l.generated, formatMillis(doc.payload.generatedAtMillis))
        particular(out, l.appVersion, doc.payload.appVersion)
        out.append("</div>\n")
        out.append("</header>\n")
        // A title element is not read by every mail preview; repeat it for screen readers.
        out.append("<h1 class=\"visually-hidden\" hidden>").append(title.esc()).append("</h1>\n")
    }

    private fun particular(out: StringBuilder, label: String, value: String?) {
        if (value.isNullOrBlank()) return
        out.append("<span>").append(label.esc()).append(" <b>").append(value.esc()).append("</b></span>\n")
    }

    /**
     * The JS-only control strip. Hidden by CSS until the interactive layer adds `.interactive` to
     * the body, so a JavaScript-disabled reader never sees a dead filter box.
     */
    private fun appendControls(out: StringBuilder, doc: ReportDocument) {
        val decks = decksInScope(doc)
        out.append("<div class=\"controls js-only\"")
        if (decks.size > 1) out.append(" data-deck-tabs")
        out.append(">\n")
        out.append("<input type=\"search\" id=\"dw-filter\" placeholder=\"")
            .append(doc.labels.filterPlaceholder.esc()).append("\" aria-label=\"")
            .append(doc.labels.filterPlaceholder.esc()).append("\">\n")
        out.append("<span class=\"count\" id=\"dw-filter-count\"></span>\n")
        if (decks.size > 1) {
            out.append("<button type=\"button\" data-deck-target=\"ALL\" aria-selected=\"true\">")
                .append(doc.labels.tabAllDecks.esc()).append("</button>\n")
            for (deck in decks) {
                out.append("<button type=\"button\" data-deck-target=\"").append(deck.id.esc())
                    .append("\" aria-selected=\"false\">")
                    .append((deck.shortCode ?: deck.name).esc()).append("</button>\n")
            }
        }
        out.append("</div>\n")
    }

    /** Counts by condition and by due status — the §13.4 summary strip. */
    private fun appendSummary(out: StringBuilder, doc: ReportDocument) {
        val l = doc.labels
        val live = doc.payload.equipment.filter { it.deletedAt == null }
        val instances = doc.payload.taskInstances
        val openDeficiencies = doc.payload.deficiencies.count { it.status != DeficiencyStatus.CLOSED }

        out.append("<div class=\"summary\">\n")
        stat(out, live.size.toString(), l.summaryEquipment, MarkerPalette.SLATE)
        for (grade in ConditionGrade.entries) {
            val count = live.count { it.condition == grade }
            if (count > 0) stat(out, count.toString(), l.condition(grade), MarkerPalette.conditionHex(grade))
        }
        val overdue = instances.count { it.status == TaskStatus.OVERDUE }
        val dueSoon = instances.count { it.status == TaskStatus.DUE_SOON }
        val planned = instances.count { it.status == TaskStatus.PENDING }
        if (overdue > 0) stat(out, overdue.toString(), l.summaryOverdue, "#C2261B")
        if (dueSoon > 0) stat(out, dueSoon.toString(), l.summaryDueSoon, "#E8A317")
        if (planned > 0) stat(out, planned.toString(), l.summaryPlanned, MarkerPalette.SLATE)
        if (openDeficiencies > 0) {
            stat(out, openDeficiencies.toString(), l.summaryOpenDeficiencies, "#E5661B")
        }
        out.append("</div>\n")
    }

    private fun stat(out: StringBuilder, number: String, label: String, colour: String) {
        out.append("<div class=\"stat\" style=\"border-left-color:").append(colour).append("\">")
            .append("<div class=\"n\">").append(number.esc()).append("</div>")
            .append("<div class=\"l\">").append(label.esc()).append("</div></div>\n")
    }

    private fun appendFooter(out: StringBuilder, doc: ReportDocument) {
        out.append("<footer class=\"doc\">\n")
        out.append("<p class=\"disclaimer\">").append(REPORT_DISCLAIMER.esc()).append("</p>\n")
        out.append("<p>DeckWatch ").append(doc.payload.appVersion.esc()).append(" &middot; ")
            .append(formatMillis(doc.payload.generatedAtMillis).esc())
            .append(" &middot; schema v").append(doc.payload.schemaVersion.toString().esc())
            .append("</p>\n")
        out.append("</footer>\n")
    }

    private fun appendDataBlock(out: StringBuilder, doc: ReportDocument) {
        out.append("<script id=\"deckwatch-data\" type=\"application/json\">")
        out.append(HtmlEscape.escapeJsonForScriptBlock(doc.payload.toJson()))
        out.append("</script>\n")
    }

    private fun appendInteractiveLayer(out: StringBuilder, doc: ReportDocument) {
        val grounds = doc.payload.equipment
            .map { it.symbolKey }
            .distinct()
            .joinToString(",") { key -> "\"${key.replace("\"", "")}\":\"${groundHex(key)}\"" }
        out.append("<script>\n")
        out.append("window.DW_GROUNDS={").append(grounds).append("};\n")
        out.append(ReportAssets.JS)
        out.append("\n</script>\n")
    }

    // ------------------------------------------------------------------ scope content

    private fun appendScopeContent(out: StringBuilder, doc: ReportDocument) {
        when (doc.options.scope) {
            ExportScope.FULL_BACKUP -> {
                appendDeckSections(out, doc)
                appendRegister(out, doc, pageBreak = true)
                appendDueTable(out, doc, pageBreak = true)
                appendDeficiencies(out, doc, openOnly = false, pageBreak = true)
                appendRoundsTable(out, doc, doc.payload.rounds, pageBreak = false)
                appendNotes(out, doc)
            }

            ExportScope.DUE_LIST -> appendDueExport(out, doc)

            ExportScope.ROUND_REPORT -> appendRoundReport(out, doc)

            ExportScope.DEFICIENCY_REPORT -> appendDeficiencies(out, doc, openOnly = true, pageBreak = false)

            ExportScope.DECK_SHEET -> appendDeckSections(out, doc)

            ExportScope.PSC_SURVEY_PACK -> {
                appendCertificateStatus(out, doc)
                appendRegister(out, doc, pageBreak = true)
                appendRoundsTable(out, doc, recentRounds(doc), pageBreak = true)
                appendDeficiencies(out, doc, openOnly = true, pageBreak = true)
            }
        }
    }

    // ---- equipment register

    private fun appendRegister(out: StringBuilder, doc: ReportDocument, pageBreak: Boolean) {
        val l = doc.labels
        val decks = doc.payload.decks.associateBy { it.id }
        val zones = doc.payload.zones.associateBy { it.id }
        val items = doc.payload.equipment.filter { it.deletedAt == null }.sortedBy { it.tag }

        section(out, l.sectionRegister, pageBreak) {
            if (items.isEmpty()) {
                out.append("<p>").append(l.empty.esc()).append("</p>\n")
                return@section
            }
            out.append("<table>\n<thead><tr>")
            headings(
                out,
                l.colTag, l.colName, l.colType, l.colDeck, l.colZone, l.colLocation,
                l.colMaker, l.colSerial, l.colQuantity, l.colCondition, l.colStatus, l.colNextDue,
            )
            out.append("</tr></thead>\n<tbody>\n")
            for (item in items) {
                val deckName = item.deckId?.let { decks[it]?.name }
                val zoneName = item.zoneId?.let { zones[it]?.name }
                out.append("<tr data-filter=\"").append(filterKey(item, deckName, doc)).append("\">")
                cell(out, item.tag, mono = true)
                cell(out, item.name)
                cell(out, doc.typeNames[item.typeKey] ?: item.typeKey)
                cell(out, deckName)
                cell(out, zoneName)
                cell(out, item.location)
                cell(out, item.makerName)
                cell(out, item.serialNumber, mono = true)
                out.append("<td class=\"num\">").append(item.quantity.toString()).append("</td>")
                conditionCell(out, doc, item.condition)
                cell(out, item.statusFlag.name.replace('_', ' '))
                cell(out, item.nextDueDate?.let(Dates::formatIso))
                out.append("</tr>\n")
            }
            out.append("</tbody>\n</table>\n")
        }
    }

    private fun filterKey(item: Equipment, deckName: String?, doc: ReportDocument): String =
        listOfNotNull(
            item.tag,
            item.name,
            doc.typeNames[item.typeKey] ?: item.typeKey,
            deckName,
            item.location,
            item.makerName,
            item.serialNumber,
            doc.labels.condition(item.condition),
        ).joinToString(" ").lowercase().esc()

    // ---- deck plans

    private fun decksInScope(doc: ReportDocument): List<Deck> {
        val all = doc.payload.decks.sortedByDescending { it.levelIndex }
        val wanted = doc.options.deckId
        return if (doc.options.scope == ExportScope.DECK_SHEET && wanted != null) {
            all.filter { it.id == wanted }
        } else if (doc.options.scope == ExportScope.DECK_SHEET || doc.options.scope == ExportScope.FULL_BACKUP) {
            all
        } else {
            emptyList()
        }
    }

    private fun appendDeckSections(out: StringBuilder, doc: ReportDocument) {
        val decks = decksInScope(doc)
        if (decks.isEmpty()) {
            section(out, doc.labels.sectionDecks, pageBreak = false) {
                out.append("<p>").append(doc.labels.empty.esc()).append("</p>\n")
            }
            return
        }
        val zonesByDeck = doc.payload.zones.groupBy { it.deckId }
        decks.forEachIndexed { index, deck ->
            appendDeckSheet(out, doc, deck, zonesByDeck[deck.id].orEmpty(), pageBreak = index > 0)
        }
    }

    private fun appendDeckSheet(
        out: StringBuilder,
        doc: ReportDocument,
        deck: Deck,
        zones: List<Zone>,
        pageBreak: Boolean,
    ) {
        val l = doc.labels
        val items = doc.payload.equipment
            .filter { it.deletedAt == null && it.deckId == deck.id }
            .sortedBy { it.tag }

        out.append("<section class=\"block")
        if (pageBreak) out.append(" page-break")
        out.append("\" data-deck-section=\"").append(deck.id.esc()).append("\">\n")
        out.append("<h2>").append(deck.name.esc())
        deck.shortCode?.let { out.append(" (").append(it.esc()).append(')') }
        out.append(" &middot; ").append(l.deckLevel.esc()).append(' ').append(deck.levelIndex.toString())
        out.append("</h2>\n")

        out.append("<div class=\"plan\">\n<figure>\n")
        appendDeckSvg(out, deck, zones, items)
        out.append("<figcaption>").append(l.deckPlanCaption.esc()).append("</figcaption>\n")
        out.append("</figure>\n")

        out.append("<div class=\"legend\">\n")
        out.append("<h3>").append(l.sectionLegend.esc()).append("</h3>\n")
        if (items.isEmpty()) {
            out.append("<p>").append(l.empty.esc()).append("</p>\n")
        } else {
            out.append("<table>\n<thead><tr>")
            headings(out, l.colNo, l.colTag, l.colType, l.colLocation, l.colCondition, l.colNextDue)
            out.append("</tr></thead>\n<tbody>\n")
            items.forEachIndexed { index, item ->
                out.append("<tr data-filter=\"").append(filterKey(item, deck.name, doc)).append("\">")
                out.append("<td class=\"num\">").append((index + 1).toString()).append("</td>")
                cell(out, item.tag, mono = true)
                cell(out, doc.typeNames[item.typeKey] ?: item.typeKey)
                cell(out, item.location)
                conditionCell(out, doc, item.condition)
                cell(out, item.nextDueDate?.let(Dates::formatIso))
                out.append("</tr>\n")
            }
            out.append("</tbody>\n</table>\n")
        }
        out.append("</div>\n</div>\n</section>\n")
    }

    /**
     * The plan as inline SVG: the hull outline of §6.3, any spatial zones, then one numbered
     * marker per item, numbered in the same order as the legend table beside it (§13.3).
     */
    private fun appendDeckSvg(
        out: StringBuilder,
        deck: Deck,
        zones: List<Zone>,
        items: List<Equipment>,
    ) {
        // The viewBox is the plan box plus a margin. The *geometry* is identical to the app's
        // canvas — same user-space units, same maths — but paper has no reason to clip: a hull's
        // bow crown and a marker sitting hard against the ship's side both overhang the plan box
        // by a few units, and on screen they are simply cropped. `data-w` / `data-h` stay the
        // plan box, so the script's marker arithmetic is unchanged.
        val frameW = PLAN_W + 2 * PLAN_PAD
        val frameH = PLAN_H + 2 * PLAN_PAD
        out.append("<svg class=\"deckplan\" xmlns=\"http://www.w3.org/2000/svg\" ")
            .append("viewBox=\"").append(DeckSvg.num(-PLAN_PAD)).append(' ')
            .append(DeckSvg.num(-PLAN_PAD)).append(' ')
            .append(DeckSvg.num(frameW)).append(' ').append(DeckSvg.num(frameH))
            .append("\" width=\"").append(DeckSvg.num(frameW))
            .append("\" height=\"").append(DeckSvg.num(frameH))
            .append("\" data-deck-id=\"").append(deck.id.esc())
            .append("\" data-w=\"").append(DeckSvg.num(PLAN_W))
            .append("\" data-h=\"").append(DeckSvg.num(PLAN_H))
            .append("\" role=\"img\" aria-label=\"").append(deck.name.esc()).append("\">\n")

        out.append("<path class=\"hull\" d=\"")
            .append(DeckSvg.outlinePath(deck.plan, PLAN_W, PLAN_H)).append("\"/>\n")

        for (zone in zones.sortedBy { it.sortOrder }) {
            if (zone.polygon.size < DeckSvg.MIN_POLYGON_POINTS) continue
            val points = zone.polygon.joinToString(" ") { point ->
                val (x, y) = DeckSvg.markerPoint(point.x, point.y, PLAN_W, PLAN_H)
                "${DeckSvg.num(x)},${DeckSvg.num(y)}"
            }
            val colour = argbToHex(zone.colorArgb)
            out.append("<polygon class=\"zone\" points=\"").append(points)
                .append("\" fill=\"").append(colour).append("\" stroke=\"").append(colour)
                .append("\"><title>").append(zone.name.esc()).append("</title></polygon>\n")
        }

        out.append("<g class=\"markers\">\n")
        items.forEachIndexed { index, item ->
            val (x, y) = DeckSvg.markerPoint(item.posX, item.posY, PLAN_W, PLAN_H)
            out.append("<g class=\"marker\" data-id=\"").append(item.id.esc())
                .append("\" data-tag=\"").append(item.tag.esc()).append("\">")
            out.append("<rect x=\"").append(DeckSvg.num(x - MARKER_HALF))
                .append("\" y=\"").append(DeckSvg.num(y - MARKER_HALF))
                .append("\" width=\"").append(DeckSvg.num(MARKER_SIZE))
                .append("\" height=\"").append(DeckSvg.num(MARKER_SIZE))
                .append("\" rx=\"5\" ry=\"5\" fill=\"").append(groundHex(item.symbolKey))
                .append("\" stroke=\"#ffffff\" stroke-width=\"1.5\"/>")
            out.append("<text class=\"mk\" x=\"").append(DeckSvg.num(x))
                .append("\" y=\"").append(DeckSvg.num(y + MARKER_TEXT_BASELINE))
                .append("\" text-anchor=\"middle\">").append((index + 1).toString()).append("</text>")
            out.append("<title>").append(item.tag.esc())
            item.name?.takeIf { it.isNotBlank() }?.let { out.append(" — ").append(it.esc()) }
            out.append("</title>")
            out.append("</g>\n")
        }
        out.append("</g>\n</svg>\n")
    }

    // ---- due list

    /** The Due tab's own snapshot — §12, §13.3. */
    private fun appendDueExport(out: StringBuilder, doc: ReportDocument) {
        val l = doc.labels
        val request = doc.payload.dueList
        section(out, l.sectionDue, pageBreak = false) {
            if (request == null || request.lines.isEmpty()) {
                out.append("<p>").append(l.empty.esc()).append("</p>\n")
                return@section
            }
            out.append("<p class=\"meta\">")
                .append(l.segmentLabel.esc()).append(": <b>").append(request.segment.name.esc())
                .append("</b> &middot; ").append(l.generated.esc()).append(": ")
                .append(Dates.formatIso(request.generatedOnEpochDay).esc())
            request.surveyCertExpiry?.let {
                out.append(" &middot; ").append(l.certExpiry.esc()).append(": ")
                    .append(Dates.formatIso(it).esc())
            }
            out.append("</p>\n")

            out.append("<table>\n<thead><tr>")
            headings(out, l.colTag, l.colDeck, l.colTask, l.colDue, l.colDays, l.colBy, l.colStatus)
            out.append("</tr></thead>\n<tbody>\n")
            for (line in request.lines) {
                val key = listOfNotNull(line.tag, line.deck, line.task)
                    .joinToString(" ").lowercase().esc()
                out.append("<tr data-filter=\"").append(key).append("\">")
                cell(out, line.tag, mono = true)
                cell(out, line.deck)
                cell(out, line.task)
                cell(out, Dates.formatIso(line.dueDate), mono = true)
                out.append("<td class=\"num\">").append(formatSignedDays(line.dayDelta)).append("</td>")
                cell(out, l.performer(line.performedBy))
                statusCell(out, doc, line.status)
                out.append("</tr>\n")
            }
            out.append("</tbody>\n")
            out.append("<tfoot><tr><td colspan=\"7\">").append(l.total.esc()).append(": ")
                .append(request.lines.size.toString()).append("</td></tr></tfoot>\n")
            out.append("</table>\n")
        }
    }

    /** The full-backup variant: every open task instance joined to its equipment. */
    private fun appendDueTable(out: StringBuilder, doc: ReportDocument, pageBreak: Boolean) {
        val l = doc.labels
        val byId = doc.payload.equipment.associateBy { it.id }
        val open = doc.payload.taskInstances
            .filter { it.status != TaskStatus.DONE && it.status != TaskStatus.NOT_APPLICABLE }
            .sortedBy { it.dueDate }
        section(out, l.sectionDue, pageBreak) {
            if (open.isEmpty()) {
                out.append("<p>").append(l.empty.esc()).append("</p>\n")
                return@section
            }
            out.append("<table>\n<thead><tr>")
            headings(out, l.colTag, l.colTask, l.colDue, l.colDays, l.colStatus)
            out.append("</tr></thead>\n<tbody>\n")
            for (instance in open) {
                val item = byId[instance.equipmentId]
                val task = doc.taskTitles[instance.taskKey] ?: instance.taskKey
                val key = listOfNotNull(item?.tag, task).joinToString(" ").lowercase().esc()
                out.append("<tr data-filter=\"").append(key).append("\">")
                cell(out, item?.tag, mono = true)
                cell(out, task)
                cell(out, Dates.formatIso(instance.dueDate), mono = true)
                out.append("<td class=\"num\">")
                    .append(formatSignedDays(instance.dueDate - doc.todayEpochDay))
                    .append("</td>")
                statusCell(out, doc, instance.status)
                out.append("</tr>\n")
            }
            out.append("</tbody>\n</table>\n")
        }
    }

    // ---- round report

    private fun appendRoundReport(out: StringBuilder, doc: ReportDocument) {
        val l = doc.labels
        val round = doc.payload.rounds.firstOrNull { it.id == doc.options.roundId }
            ?: doc.payload.rounds.firstOrNull()
        if (round == null) {
            section(out, l.sectionRound, pageBreak = false) {
                out.append("<p>").append(l.empty.esc()).append("</p>\n")
            }
            return
        }
        val byId = doc.payload.equipment.associateBy { it.id }
        val decks = doc.payload.decks.associateBy { it.id }
        val items = doc.payload.roundItems.filter { it.roundId == round.id }

        section(out, l.sectionRound, pageBreak = false) {
            out.append("<h3>").append(round.title.esc()).append("</h3>\n")
            out.append("<p class=\"meta\">")
                .append(l.roundPerformedBy.esc()).append(": <b>").append(round.performedBy.escOrDash())
                .append("</b> &middot; ").append(l.roundStarted.esc()).append(": ")
                .append(formatMillis(round.startedAt).esc())
            round.completedAt?.let {
                out.append(" &middot; ").append(l.roundCompleted.esc()).append(": ")
                    .append(formatMillis(it).esc())
            }
            out.append("</p>\n")
            round.notes?.takeIf { it.isNotBlank() }?.let {
                out.append("<p><b>").append(l.roundNotes.esc()).append(":</b> ").append(it.esc()).append("</p>\n")
            }
        }

        section(out, l.sectionRoundItems, pageBreak = false) {
            if (items.isEmpty()) {
                out.append("<p>").append(l.empty.esc()).append("</p>\n")
            } else {
                out.append("<table>\n<thead><tr>")
                headings(out, l.colTag, l.colDeck, l.colCondition, l.colChecked, l.colRemark)
                out.append("</tr></thead>\n<tbody>\n")
                for (item in items) {
                    val equipment = byId[item.equipmentId]
                    val deckName = equipment?.deckId?.let { decks[it]?.name }
                    val key = listOfNotNull(equipment?.tag, deckName, item.remark)
                        .joinToString(" ").lowercase().esc()
                    val grade = item.condition
                    out.append("<tr data-filter=\"").append(key).append("\">")
                    cell(out, equipment?.tag, mono = true)
                    cell(out, deckName)
                    if (grade == null) out.append("<td>&mdash;</td>") else conditionCell(out, doc, grade)
                    cell(out, item.checkedAt?.let { formatMillis(it) })
                    out.append("<td>").append(item.remark.escOrDash())
                    appendPhotos(out, doc, item.photoUris)
                    out.append("</td>")
                    out.append("</tr>\n")
                }
                out.append("</tbody>\n</table>\n")
            }
            appendSignatureBlock(out, doc)
        }
    }

    /** §13.3 — a round report is a signed record, so it carries a signature block. */
    private fun appendSignatureBlock(out: StringBuilder, doc: ReportDocument) {
        val l = doc.labels
        out.append("<div class=\"signature\">\n")
        for (caption in listOf(l.signatureInspected, l.signatureVerified)) {
            out.append("<div><div class=\"rule\"></div><div class=\"cap\">")
                .append(caption.esc()).append(" &middot; ").append(l.signatureRank.esc())
                .append("</div></div>\n")
        }
        out.append("<div><div class=\"rule\"></div><div class=\"cap\">")
            .append(l.signatureDate.esc()).append("</div></div>\n")
        out.append("</div>\n")
    }

    // ---- deficiencies

    private fun appendDeficiencies(
        out: StringBuilder,
        doc: ReportDocument,
        openOnly: Boolean,
        pageBreak: Boolean,
    ) {
        val l = doc.labels
        val byId = doc.payload.equipment.associateBy { it.id }
        val list = doc.payload.deficiencies
            .filter { !openOnly || it.status != DeficiencyStatus.CLOSED }
            .sortedWith(compareByDescending<Deficiency> { it.severity.ordinal }.thenBy { it.raisedDate })
        val heading = if (openOnly) l.sectionOpenDeficiencies else l.sectionDeficiencies

        section(out, heading, pageBreak) {
            if (list.isEmpty()) {
                out.append("<p>").append(l.empty.esc()).append("</p>\n")
                return@section
            }
            for (deficiency in list) {
                val equipment = deficiency.equipmentId?.let { byId[it] }
                val key = listOfNotNull(deficiency.title, deficiency.description, equipment?.tag)
                    .joinToString(" ").lowercase().esc()
                out.append("<div class=\"card\" data-filter=\"").append(key)
                    .append("\" style=\"border-left-color:")
                    .append(severityHex(deficiency.severity)).append("\">\n")
                out.append("<h3>").append(deficiency.title.esc()).append("</h3>\n")
                out.append("<div class=\"meta\">")
                    .append("<span class=\"chip\" style=\"background:").append(severityHex(deficiency.severity))
                    .append("\">").append(l.severity(deficiency.severity).esc()).append("</span> ")
                    .append(l.deficiencyStatus(deficiency.status).esc())
                    .append(" &middot; ").append(l.colRaised.esc()).append(' ')
                    .append(Dates.formatIso(deficiency.raisedDate).esc())
                deficiency.targetDate?.let {
                    out.append(" &middot; ").append(l.colTarget.esc()).append(' ')
                        .append(Dates.formatIso(it).esc())
                }
                equipment?.let { out.append(" &middot; ").append(it.tag.esc()) }
                deficiency.closedDate?.let {
                    out.append(" &middot; ").append(l.deficiencyClosed.esc()).append(' ')
                        .append(Dates.formatIso(it).esc())
                }
                out.append("</div>\n")
                if (deficiency.description.isNotBlank()) {
                    out.append("<p>").append(deficiency.description.esc()).append("</p>\n")
                }
                deficiency.correctiveAction?.takeIf { it.isNotBlank() }?.let {
                    out.append("<p><b>").append(l.deficiencyCorrectiveAction.esc()).append(":</b> ")
                        .append(it.esc()).append("</p>\n")
                }
                deficiency.sparePartRequired?.takeIf { it.isNotBlank() }?.let {
                    out.append("<p><b>").append(l.deficiencySparePart.esc()).append(":</b> ")
                        .append(it.esc()).append("</p>\n")
                }
                appendPhotos(out, doc, deficiency.photoUris)
                out.append("</div>\n")
            }
        }
    }

    // ---- rounds, certificates, notes

    private fun recentRounds(doc: ReportDocument): List<Round> {
        val cutoff = doc.payload.generatedAtMillis - TWELVE_MONTHS_MILLIS
        return doc.payload.rounds.filter { it.startedAt >= cutoff }
    }

    private fun appendRoundsTable(
        out: StringBuilder,
        doc: ReportDocument,
        rounds: List<Round>,
        pageBreak: Boolean,
    ) {
        val l = doc.labels
        section(out, l.sectionRounds, pageBreak) {
            if (rounds.isEmpty()) {
                out.append("<p>").append(l.empty.esc()).append("</p>\n")
                return@section
            }
            out.append("<table>\n<thead><tr>")
            headings(out, l.colTitle, l.colStarted, l.colCompleted, l.colPerformedBy, l.colItems, l.colFindings)
            out.append("</tr></thead>\n<tbody>\n")
            for (round in rounds.sortedByDescending { it.startedAt }) {
                val key = listOf(round.title, round.performedBy).joinToString(" ").lowercase().esc()
                out.append("<tr data-filter=\"").append(key).append("\">")
                cell(out, round.title)
                cell(out, formatMillis(round.startedAt))
                cell(out, round.completedAt?.let { formatMillis(it) })
                cell(out, round.performedBy)
                out.append("<td class=\"num\">").append(round.doneCount.toString()).append(" / ")
                    .append(round.itemCount.toString()).append("</td>")
                out.append("<td class=\"num\">").append(round.deficiencyCount.toString()).append("</td>")
                out.append("</tr>\n")
            }
            out.append("</tbody>\n</table>\n")
        }
    }

    private fun appendCertificateStatus(out: StringBuilder, doc: ReportDocument) {
        val l = doc.labels
        val vessel = doc.payload.vessels.firstOrNull()
        section(out, l.sectionCertificates, pageBreak = false) {
            if (vessel == null) {
                out.append("<p>").append(l.empty.esc()).append("</p>\n")
                return@section
            }
            out.append("<table>\n<tbody>\n")
            row(out, l.certExpiry, vessel.safetyEquipmentCertExpiry?.let(Dates::formatIso))
            row(out, l.lastAnnualSurvey, vessel.lastAnnualSurveyDate?.let(Dates::formatIso))
            row(out, l.nextDrydock, vessel.nextDrydockDate?.let(Dates::formatIso))
            row(out, l.flag, vessel.flagOtherName ?: vessel.flag.name)
            row(out, l.classSociety, vessel.classSociety?.name)
            out.append("</tbody>\n</table>\n")
        }
    }

    private fun appendNotes(out: StringBuilder, doc: ReportDocument) {
        val notes = doc.payload.userNotes
        if (notes.isEmpty()) return
        section(out, doc.labels.sectionNotes, pageBreak = false) {
            for (note in notes.sortedByDescending { it.updatedAt }) {
                out.append("<div class=\"card\" data-filter=\"")
                    .append((note.title + " " + note.body).lowercase().esc()).append("\">\n")
                out.append("<h3>").append(note.title.esc()).append("</h3>\n")
                out.append("<p>").append(note.body.esc()).append("</p>\n")
                out.append("</div>\n")
            }
        }
    }

    // ------------------------------------------------------------------ small builders

    private inline fun section(out: StringBuilder, heading: String, pageBreak: Boolean, body: () -> Unit) {
        out.append("<section class=\"block")
        if (pageBreak) out.append(" page-break")
        out.append("\">\n<h2>").append(heading.esc()).append("</h2>\n")
        body()
        out.append("</section>\n")
    }

    private fun headings(out: StringBuilder, vararg names: String) {
        for (name in names) out.append("<th>").append(name.esc()).append("</th>")
    }

    private fun cell(out: StringBuilder, value: String?, mono: Boolean = false) {
        out.append(if (mono) "<td class=\"mono\">" else "<td>").append(value.escOrDash()).append("</td>")
    }

    private fun row(out: StringBuilder, label: String, value: String?) {
        out.append("<tr><th scope=\"row\">").append(label.esc()).append("</th><td>")
            .append(value.escOrDash()).append("</td></tr>\n")
    }

    private fun conditionCell(out: StringBuilder, doc: ReportDocument, grade: ConditionGrade) {
        out.append("<td><span class=\"chip\" style=\"background:")
            .append(MarkerPalette.conditionHex(grade)).append("\">")
            .append(doc.labels.condition(grade).esc()).append("</span></td>")
    }

    private fun statusCell(out: StringBuilder, doc: ReportDocument, status: TaskStatus) {
        val colour = when (status) {
            TaskStatus.OVERDUE -> "#C2261B"
            TaskStatus.DUE_SOON -> "#E8A317"
            TaskStatus.DONE -> "#1B873F"
            TaskStatus.SKIPPED -> "#E5661B"
            else -> MarkerPalette.SLATE
        }
        out.append("<td><span class=\"chip\" style=\"background:").append(colour).append("\">")
            .append(doc.labels.taskStatus(status).esc()).append("</span></td>")
    }

    /**
     * Photos as `data:` URIs — §13.2. A URI the embedder could not decode (missing file, 200 MB
     * original, unreadable bytes) is replaced by a visible placeholder: the report says a photo
     * was expected here and could not be embedded, rather than silently dropping evidence or
     * crashing the export (§17.4).
     */
    private fun appendPhotos(out: StringBuilder, doc: ReportDocument, uris: List<String>) {
        if (uris.isEmpty()) return
        if (doc.options.photoTier == PhotoTier.NONE) return
        out.append("<div class=\"photos\">")
        for (uri in uris) {
            val dataUri = doc.photos[uri]
            if (dataUri == null) {
                out.append("<span class=\"photo-missing\">")
                    .append(doc.labels.photoUnavailable.esc()).append("</span>")
            } else {
                out.append("<img alt=\"\" src=\"").append(dataUri.esc()).append("\">")
            }
        }
        out.append("</div>")
    }

    private fun severityHex(severity: Severity): String = when (severity) {
        Severity.OBSERVATION -> "#8A8F98"
        Severity.MINOR -> "#E8A317"
        Severity.MAJOR -> "#E5661B"
        Severity.CRITICAL_DETAINABLE -> "#C2261B"
    }

    private fun argbToHex(argb: Int): String =
        "#" + (argb and RGB_MASK).toString(HEX_RADIX).padStart(HEX_DIGITS, '0')

    private fun formatSignedDays(days: Long): String = if (days > 0) "+$days" else days.toString()

    private fun formatMillis(millis: Long): String =
        if (millis <= 0L) {
            "—"
        } else {
            TIMESTAMP.format(Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC))
        }

    private companion object {
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
        const val INITIAL_CAPACITY = 64 * 1024
        const val PLAN_W = 340f
        const val PLAN_H = 440f

        /** Margin around the plan box, so a bow crown or an edge marker is not cropped in print. */
        const val PLAN_PAD = 24f
        const val MARKER_SIZE = 22f
        const val MARKER_HALF = 11f
        const val MARKER_TEXT_BASELINE = 4f
        const val RGB_MASK = 0xFFFFFF
        const val HEX_RADIX = 16
        const val HEX_DIGITS = 6
        const val TWELVE_MONTHS_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}
