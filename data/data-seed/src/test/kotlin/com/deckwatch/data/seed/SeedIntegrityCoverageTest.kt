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
    fun `the bundle version rises when content is added, so an installed app re-imports`() = runTest {
        val bundle = SeedDataSource(ApplicationProvider.getApplicationContext()).loadAll()

        // ContentSeeder takes the newest card's contentVersion as the bundle's; a new section
        // added at the old version would never reach a phone that already seeded.
        val newest = bundle.regulationCards.maxOf { it.contentVersion }
        val newSections = listOf(
            RegulationSection.HELIDECK,
            RegulationSection.ISGOTT,
            RegulationSection.IAMSAR,
        )
        for (section in newSections) {
            val cards = bundle.regulationCards.filter { it.section == section }
            assertThat(cards.map { it.contentVersion }.distinct()).containsExactly(newest)
        }
    }
}
