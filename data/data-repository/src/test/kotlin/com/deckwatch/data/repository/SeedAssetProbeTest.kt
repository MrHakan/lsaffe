package com.deckwatch.data.repository

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import com.deckwatch.data.seed.SeedDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The seed assets live in `data-seed`'s `assets/seed`. AGP merges a library dependency's assets
 * into this module's unit-test assets, so `SeedDataSource` works here against the real bundled
 * content — this test is the guard that says so out loud, because if it ever stops being true the
 * demo-vessel and seeding tests below would silently need hand-built fixtures instead.
 */
@RunWith(RobolectricTestRunner::class)
class SeedAssetProbeTest {

    @Test
    fun `bundled seed assets are readable from this module's unit tests`() = runTest {
        val seed = SeedDataSource(ApplicationProvider.getApplicationContext<Application>())
        assertThat(seed.loadPlanPresets()).isNotEmpty()
        assertThat(seed.loadDemoVesselSeed().equipment).isNotEmpty()
    }
}
