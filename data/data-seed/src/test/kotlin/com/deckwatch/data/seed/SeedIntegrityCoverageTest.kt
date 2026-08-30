package com.deckwatch.data.seed

import androidx.test.core.app.ApplicationProvider
import com.deckwatch.core.model.RegulationSection
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every section the Notes tab lists must have something in it — §8.1.
 *
 * The tab is driven by `RegulationSection.entries`, so adding a section without content would ship
 * a row that opens an empty screen. MY_NOTES is the one section that is empty on purpose: it holds
 * the officer's own notes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedIntegrityCoverageTest {

    @Test
    fun `every section except the officer's own notes carries cards`() = runTest {
        val bundle = SeedDataSource(ApplicationProvider.getApplicationContext()).loadAll()
        val bySection = bundle.regulationCards.groupingBy { it.section }.eachCount()

        for (section in RegulationSection.entries - RegulationSection.MY_NOTES) {
            assertThat(bySection[section] ?: 0).isGreaterThan(0)
        }
    }

    @Test
    fun `the bundle version matches the one this build declares`() = runTest {
        val bundle = SeedDataSource(ApplicationProvider.getApplicationContext()).loadAll()

        // ContentSeeder takes the newest card's contentVersion as the bundle's version, and only
        // re-imports when that number is higher than the one already seeded. Content added at the
        // old version therefore ships and stays invisible on every phone that has run the app
        // before. This constant is the tripwire: adding content without bumping it fails here.
        assertThat(bundle.regulationCards.maxOf { it.contentVersion }).isEqualTo(BUNDLE_VERSION)
    }

    @Test
    fun `the newest content carries the bundle version, so it actually reaches an installed app`() = runTest {
        val bundle = SeedDataSource(ApplicationProvider.getApplicationContext()).loadAll()

        assertThat(bundle.regulationCards.count { it.contentVersion == BUNDLE_VERSION })
            .isGreaterThan(0)
    }

    @Test
    fun `the equipment guide covers the types an officer reaches for first`() = runTest {
        val bundle = SeedDataSource(ApplicationProvider.getApplicationContext()).loadAll()
        val withNotes = bundle.equipmentTypes.filter { it.technicalNotes.isNotEmpty() }

        // Not every type needs a guide page, but the ones a deck or engineer officer opens on a
        // round do — and each page has to actually say something.
        assertThat(withNotes.map { it.typeKey }).containsAtLeast(
            "LSA_LIFEBUOY_PLAIN",
            "LSA_LIFEJACKET_ADULT",
            "LSA_IMMERSION_SUIT",
            "LSA_LIFERAFT_THROWOVER",
            "LSA_LIFEBOAT_TOTALLY_ENCLOSED",
            "LSA_PILOT_LADDER",
            "FFE_FIRE_HYDRANT",
            "FFE_FIRE_HOSE",
            "FFE_INTERNATIONAL_SHORE_CONNECTION",
            "FFE_PORTABLE_EXTINGUISHER",
            "FFE_SCBA_SET",
            "FFE_EEBD",
            "FFE_FIREMANS_OUTFIT",
        )
        assertThat(withNotes.all { type -> type.technicalNotes.all { it.bullets.isNotEmpty() } }).isTrue()
        assertThat(withNotes.all { type -> type.technicalNotes.all { it.heading.isNotBlank() } }).isTrue()
    }

    private companion object {
        /** Bump with every content change; see the test above for why. */
        const val BUNDLE_VERSION = 3
    }
}
