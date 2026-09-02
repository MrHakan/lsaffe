package com.deckwatch.feature.report

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The live size estimate of the export dialog — §13.2. */
class PhotoSizeEstimatorTest {

    @Test
    fun `no photos is no photo payload`() {
        assertThat(PhotoSizeEstimator.estimateBytes(emptyList())).isEqualTo(0L)
    }

    @Test
    fun `the estimate is the source bytes times the downscale and base64 factor`() {
        val sizes = listOf(4_000_000L, 6_000_000L)
        assertThat(PhotoSizeEstimator.estimateBytes(sizes)).isEqualTo(3_500_000L)
    }

    @Test
    fun `an unreadable photo reported as a negative size does not shrink the estimate`() {
        assertThat(PhotoSizeEstimator.estimateBytes(listOf(1_000_000L, -1L)))
            .isEqualTo(PhotoSizeEstimator.estimateBytes(listOf(1_000_000L)))
    }

    @Test
    fun `the file estimate adds the payload and the document shell`() {
        val estimate = PhotoSizeEstimator.estimateFileBytes(listOf(1_000_000L), payloadBytes = 50_000)
        assertThat(estimate).isEqualTo(350_000L + 50_000L + PhotoSizeEstimator.DOCUMENT_OVERHEAD_BYTES)
    }

    @Test
    fun `sizes are formatted compactly and without a locale decimal comma`() {
        assertThat(PhotoSizeEstimator.format(0)).isEqualTo("0 B")
        assertThat(PhotoSizeEstimator.format(900)).isEqualTo("900 B")
        assertThat(PhotoSizeEstimator.format(2048)).isEqualTo("2 kB")
        assertThat(PhotoSizeEstimator.format(2_621_440)).isEqualTo("2.5 MB")
    }

    /**
     * §17.3 gives a 300-item vessel with deficiency photos a 10 MB budget. With a realistic
     * 3 MB per photo the estimator must warn well before a couple of dozen photos blow it.
     */
    @Test
    fun `the estimate flags an export that would exceed the section 17_3 budget`() {
        val twentyPhotos = List(20) { 3_000_000L }
        val estimate = PhotoSizeEstimator.estimateFileBytes(twentyPhotos, payloadBytes = 200_000)
        assertThat(estimate).isGreaterThan(10L * 1024 * 1024)
    }
}
