package com.deckwatch.feature.report

import com.deckwatch.core.common.Dates
import com.deckwatch.feature.inspection.DueExportRequest
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A report that has been written to the export cache and is ready to share — §13.6. */
data class GeneratedReport(
    val file: File,
    val scope: ExportScope,
    val sizeBytes: Long,
    val photosEmbedded: Int,
    val photosFailed: Int,
)

/**
 * The one place a report is made: assemble the payload, embed the photos, render the HTML, write
 * the file — §13.
 *
 * Each step is an object that can be tested on its own; this class is only the sequence. It is the
 * single entry point the rest of the app calls, which is why the Due tab's hand-off
 * ([exportDueList]) lives here too.
 */
@Singleton
class ReportGenerator @Inject constructor(
    private val assembler: PayloadAssembler,
    private val embedder: PhotoEmbedder,
    private val fileStore: ReportFileStore,
) {

    private val renderer = HtmlReportRenderer()

    /** Generate and write one report. */
    suspend fun generate(
        vesselId: String,
        options: ExportOptions,
        labels: ReportLabels = ReportLabels(),
        dueList: DueExportRequest? = null,
    ): GeneratedReport {
        val assembled = assembler.build(
            vesselId = vesselId,
            scope = options.scope,
            photoTier = options.photoTier,
            deckId = options.deckId,
            roundId = options.roundId,
            dueList = dueList,
        )
        val photos = embedder.embed(assembled.photoUris)
        val html = renderer.render(
            ReportDocument(
                payload = assembled.payload,
                options = options,
                labels = labels,
                photos = photos,
                typeNames = assembled.typeNames,
                taskTitles = assembled.taskTitles,
            ),
        )
        val name = ReportFileNames.forReport(
            vesselName = assembled.payload.vessels.firstOrNull()?.name ?: dueList?.vesselName,
            scope = options.scope,
            atMillis = assembled.payload.generatedAtMillis,
        )
        val file = fileStore.write(name, html)
        return GeneratedReport(
            file = file,
            scope = options.scope,
            sizeBytes = file.length(),
            photosEmbedded = photos.size,
            photosFailed = assembled.photoUris.size - photos.size,
        )
    }

    /**
     * The live size estimate the export dialog shows next to the photo-tier chooser — §13.2.
     *
     * Assembles the payload (cheap: it is already in memory as flows) and measures the *source*
     * photo files rather than encoding them, so moving between tiers updates instantly instead of
     * decoding forty JPEGs each time the user changes their mind.
     */
    suspend fun estimateBytes(vesselId: String, options: ExportOptions): Long {
        val assembled = assembler.build(
            vesselId = vesselId,
            scope = options.scope,
            photoTier = options.photoTier,
            deckId = options.deckId,
            roundId = options.roundId,
        )
        return PhotoSizeEstimator.estimateFileBytes(
            sourceBytes = embedder.sourceSizes(assembled.photoUris),
            payloadBytes = assembled.payload.toJson().length.toLong(),
        )
    }

    /**
     * The Due tab's `onExportHtml` hand-off — §12, §13.3.
     *
     * The Due list is a snapshot the officer is already looking at, so it is rendered straight from
     * the [DueExportRequest] without touching the database again: what prints is exactly what was
     * on screen, filters and all.
     */
    suspend fun exportDueList(
        request: DueExportRequest,
        labels: ReportLabels = ReportLabels(),
        appVersion: String = "",
    ): File {
        val payload = DeckWatchExportPayload(
            appVersion = appVersion,
            generatedAtMillis = Dates.nowMillis(),
            scope = ExportScope.DUE_LIST.name,
            dueList = request,
        )
        val html = renderer.render(
            ReportDocument(
                payload = payload,
                options = ExportOptions(scope = ExportScope.DUE_LIST, photoTier = PhotoTier.NONE),
                labels = labels,
            ),
        )
        val name = ReportFileNames.forReport(
            vesselName = request.vesselName,
            scope = ExportScope.DUE_LIST,
            atMillis = payload.generatedAtMillis,
        )
        return fileStore.write(name, html)
    }

    /** The same payload without the HTML wrapper — §13.5's "also support JSON export". */
    suspend fun exportJson(vesselId: String, options: ExportOptions): File {
        val assembled = assembler.build(
            vesselId = vesselId,
            scope = options.scope,
            photoTier = PhotoTier.NONE,
            deckId = options.deckId,
            roundId = options.roundId,
        )
        val name = ReportFileNames.forReport(
            vesselName = assembled.payload.vessels.firstOrNull()?.name,
            scope = options.scope,
            atMillis = assembled.payload.generatedAtMillis,
            extension = ReportFileNames.JSON_EXTENSION,
        )
        return fileStore.write(name, assembled.payload.toJson())
    }

    /** CSV of the equipment register — §13.5. */
    suspend fun exportRegisterCsv(vesselId: String, labels: ReportLabels = ReportLabels()): File {
        val assembled = assembler.build(vesselId, ExportScope.FULL_BACKUP, PhotoTier.NONE)
        val name = ReportFileNames.forReport(
            vesselName = assembled.payload.vessels.firstOrNull()?.name,
            scope = ExportScope.FULL_BACKUP,
            atMillis = assembled.payload.generatedAtMillis,
            extension = ReportFileNames.CSV_EXTENSION,
        )
        return fileStore.write(
            name,
            CsvExport.equipmentRegister(assembled.payload, labels, assembled.typeNames),
        )
    }

    /** CSV of the Due list, straight from the Due tab's snapshot — §12. */
    suspend fun exportDueListCsv(request: DueExportRequest, labels: ReportLabels = ReportLabels()): File {
        val name = ReportFileNames.forReport(
            vesselName = request.vesselName,
            scope = ExportScope.DUE_LIST,
            atMillis = Dates.nowMillis(),
            extension = ReportFileNames.CSV_EXTENSION,
        )
        return fileStore.write(name, CsvExport.dueList(request, labels))
    }
}
