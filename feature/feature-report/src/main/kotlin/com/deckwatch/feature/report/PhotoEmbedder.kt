package com.deckwatch.feature.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.deckwatch.core.common.DefaultDispatcherProvider
import com.deckwatch.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Turns photo URIs into `data:` URIs for embedding in a report — §13.2.
 *
 * Every image is downscaled to a **1280 px long edge** and re-encoded as **JPEG q75**. The
 * downscale happens during decode, via `BitmapFactory.Options.inSampleSize`, so a 200 MB photo is
 * never fully materialised in memory: the bounds pass reads only the header, and the real decode
 * asks the decoder for a sub-sampled bitmap (§17.4).
 *
 * Nothing here throws. A photo that cannot be found, cannot be decoded, or exhausts memory is
 * simply absent from the returned map, and the renderer prints a visible placeholder in its place.
 * Losing one photo must never cost the officer the report.
 */
class PhotoEmbedder(
    private val context: Context,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(Dispatchers.Main),
) {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context, DefaultDispatcherProvider(Dispatchers.Main))

    /**
     * @return original URI -> `data:image/jpeg;base64,…`. URIs that failed are omitted, not mapped
     *   to an empty string, so the renderer can tell "not requested" from "could not embed".
     */
    suspend fun embed(uris: List<String>): Map<String, String> = withContext(dispatchers.io) {
        uris.distinct().mapNotNull { uri -> encode(uri)?.let { uri to it } }.toMap()
    }

    /** Source byte sizes for the export dialog's live estimate. Unreadable URIs count as 0. */
    suspend fun sourceSizes(uris: List<String>): List<Long> = withContext(dispatchers.io) {
        uris.distinct().map { sizeOf(it) }
    }

    /** Convenience: the estimate for [uris], in bytes. */
    suspend fun estimateBytes(uris: List<String>): Long =
        PhotoSizeEstimator.estimateBytes(sourceSizes(uris))

    private fun encode(uri: String): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = openStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        val scaled = scaleToLongEdge(decoded)
        val out = ByteArrayOutputStream(INITIAL_JPEG_BUFFER)
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        DATA_URI_PREFIX + Base64.getEncoder().encodeToString(out.toByteArray())
    }.getOrNull()

    /**
     * `inSampleSize` must be a power of two; anything else is rounded down by the decoder anyway.
     * This picks the largest power of two that still leaves the long edge at or above
     * [MAX_LONG_EDGE], so the follow-up [scaleToLongEdge] never has to upscale.
     */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longEdge = maxOf(width, height)
        while (longEdge / 2 >= MAX_LONG_EDGE) {
            longEdge /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToLongEdge(source: Bitmap): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= MAX_LONG_EDGE || longEdge == 0) return source
        val factor = MAX_LONG_EDGE.toFloat() / longEdge
        val width = (source.width * factor).toInt().coerceAtLeast(1)
        val height = (source.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun openStream(uri: String) = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == null || parsed.scheme == "file") {
            File(parsed.path ?: uri).inputStream()
        } else {
            context.contentResolver.openInputStream(parsed)
        }
    }.getOrNull()

    private fun sizeOf(uri: String): Long = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == null || parsed.scheme == "file") {
            File(parsed.path ?: uri).length()
        } else {
            context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { it.length }
                ?.takeIf { it >= 0 }
                ?: 0L
        }
    }.getOrDefault(0L)

    private companion object {
        const val MAX_LONG_EDGE = 1280
        const val JPEG_QUALITY = 75
        const val INITIAL_JPEG_BUFFER = 128 * 1024
        const val DATA_URI_PREFIX = "data:image/jpeg;base64,"
    }
}

/**
 * The live size estimate shown next to the photo-tier chooser — §13.2.
 *
 * Pure arithmetic so it is testable without a device. The factor is deliberately a single number
 * rather than a model of the JPEG encoder: the officer needs to know "roughly 2 MB or roughly
 * 40 MB" before choosing a tier, not a byte-accurate figure.
 *
 * `0.35` is the product of two effects that pull in opposite directions:
 * * **downscale + q75 re-encode** takes a typical 8–12 MP phone photo down to roughly a quarter of
 *   its original bytes once the long edge is capped at 1280 px;
 * * **base64** then inflates whatever survives by 4/3.
 *
 * 0.25 x 4/3 ≈ 0.33, rounded up to 0.35 so the estimate errs on the pessimistic side — an export
 * that comes out smaller than promised is a good surprise.
 */
object PhotoSizeEstimator {

    /** Downscale-and-base64 factor applied to the source bytes. */
    const val FACTOR: Double = 0.35

    /** Fixed overhead of the HTML shell itself: stylesheet, script, chrome. */
    const val DOCUMENT_OVERHEAD_BYTES: Long = 24 * 1024

    /** Estimated size of the embedded photo payload, in bytes. */
    fun estimateBytes(sourceBytes: List<Long>): Long =
        (sourceBytes.sumOf { it.coerceAtLeast(0L) } * FACTOR).roundToLong()

    /** Estimated size of the whole file: photos plus the payload JSON and the shell. */
    fun estimateFileBytes(sourceBytes: List<Long>, payloadBytes: Long): Long =
        estimateBytes(sourceBytes) + payloadBytes + DOCUMENT_OVERHEAD_BYTES

    /** "2.4 MB" / "812 kB" / "0 B" — a compact, locale-neutral rendering for the dialog. */
    fun format(bytes: Long): String = when {
        bytes >= MB -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes.toDouble() / MB)
        bytes >= KB -> "${bytes / KB} kB"
        else -> "$bytes B"
    }

    private const val KB = 1024L
    private const val MB = 1024L * 1024L
}
