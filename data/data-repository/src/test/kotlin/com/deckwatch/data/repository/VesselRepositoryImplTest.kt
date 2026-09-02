package com.deckwatch.data.repository

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanShape
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The `levelIndex` mechanic and the active-vessel contract — MASTER_PROMPT §6.2, §5. */
@RunWith(RobolectricTestRunner::class)
class VesselRepositoryImplTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var harness: RepositoryHarness

    private val plan = DeckPlan(shape = PlanShape.RECTANGLE)

    @Before
    fun setUp() {
        harness = RepositoryHarness(temporaryFolder.newFolder(), TestData.referenceDay)
    }

    @After
    fun tearDown() = harness.close()

    @Test
    fun `first deck is level zero whichever way it is added`() = runTest {
        val vessel = seedVessel()
        val first = harness.vesselRepository.addDeckAbove(vessel, "Upper Deck", "UD", plan)
        assertThat(first.levelIndex).isEqualTo(0)

        val other = seedVessel(name = "Second ship")
        val firstBelow = harness.vesselRepository.addDeckBelow(other, "Tank Top", "TT", plan)
        assertThat(firstBelow.levelIndex).isEqualTo(0)
    }

    @Test
    fun `add above steps up by ten and add below steps down by ten`() = runTest {
        val vessel = seedVessel()
        harness.vesselRepository.addDeckAbove(vessel, "Upper Deck", "UD", plan)

        val above = harness.vesselRepository.addDeckAbove(vessel, "A Deck", "A", plan)
        val higher = harness.vesselRepository.addDeckAbove(vessel, "Bridge Deck", "BR", plan)
        val below = harness.vesselRepository.addDeckBelow(vessel, "ER 2nd Flat", "E2", plan)
        val lower = harness.vesselRepository.addDeckBelow(vessel, "ER Floor", "EF", plan)

        assertThat(above.levelIndex).isEqualTo(10)
        assertThat(higher.levelIndex).isEqualTo(20)
        assertThat(below.levelIndex).isEqualTo(-10)
        assertThat(lower.levelIndex).isEqualTo(-20)

        // The stack renders highest first — §7.1 A.
        val levels = harness.vesselRepository.observeDecks(vessel).first().map { it.levelIndex }
        assertThat(levels).containsExactly(20, 10, 0, -10, -20).inOrder()
    }

    @Test
    fun `insert between takes the midpoint, including below the first deck`() = runTest {
        val vessel = seedVessel()
        harness.vesselRepository.addDeckAbove(vessel, "Upper Deck", "UD", plan)
        harness.vesselRepository.addDeckAbove(vessel, "A Deck", "A", plan)
        harness.vesselRepository.addDeckBelow(vessel, "ER 2nd Flat", "E2", plan)

        val between = harness.vesselRepository
            .insertDeckBetween(vessel, 0, 10, "Poop Deck", "PP", plan)
        assertThat(between.levelIndex).isEqualTo(5)

        val negative = harness.vesselRepository
            .insertDeckBetween(vessel, -10, 0, "ER Flat", "EF", plan)
        assertThat(negative.levelIndex).isEqualTo(-5)

        assertThat(harness.vesselRepository.observeDecks(vessel).first()).hasSize(5)
    }

    @Test
    fun `insert between adjacent levels fails loudly and writes nothing`() = runTest {
        val vessel = seedVessel()
        harness.vesselRepository.addDeckAbove(vessel, "Upper Deck", "UD", plan)
        harness.vesselRepository.insertDeckBetween(vessel, 0, 10, "Poop Deck", "PP", plan)
        harness.vesselRepository.addDeckAbove(vessel, "A Deck", "A", plan)
        val before = harness.vesselRepository.observeDecks(vessel).first().size

        // 5 and 6 have no integer between them.
        val failure = runCatching {
            harness.vesselRepository.insertDeckBetween(vessel, 5, 6, "Squeeze", "SQ", plan)
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure).hasMessageThat().contains("levelIndex 5 and 6")
        assertThat(failure).hasMessageThat().contains("adjacent")
        assertThat(harness.vesselRepository.observeDecks(vessel).first()).hasSize(before)
    }

    @Test
    fun `setActiveVessel moves the column and mirrors it into settings`() = runTest {
        val first = seedVessel(name = "MV First")
        val second = seedVessel(name = "MV Second")

        harness.vesselRepository.setActiveVessel(first)
        assertThat(harness.vesselRepository.observeActiveVessel().first()?.id).isEqualTo(first)
        assertThat(harness.preferences.get().activeVesselId).isEqualTo(first)

        harness.vesselRepository.setActiveVessel(second)
        assertThat(harness.vesselRepository.observeActiveVessel().first()?.id).isEqualTo(second)
        assertThat(harness.preferences.get().activeVesselId).isEqualTo(second)
        // Exactly one vessel may carry the flag — §5.
        assertThat(harness.vesselRepository.observeVessels().first().count { it.isActive })
            .isEqualTo(1)
    }

    @Test
    fun `deleting the active vessel clears the settings mirror and its decks`() = runTest {
        val vessel = seedVessel()
        harness.vesselRepository.addDeckAbove(vessel, "Upper Deck", "UD", plan)
        harness.vesselRepository.setActiveVessel(vessel)

        harness.vesselRepository.deleteVessel(vessel)

        assertThat(harness.vesselRepository.observeVessels().first()).isEmpty()
        assertThat(harness.vesselRepository.observeDecks(vessel).first()).isEmpty()
        assertThat(harness.preferences.get().activeVesselId).isNull()
        assertThat(harness.vesselRepository.observeActiveVessel().first()).isNull()
    }

    private suspend fun seedVessel(name: String = "MV Example"): String {
        val vessel = TestData.vessel(name = name, isActive = false)
        harness.vesselRepository.upsertVessel(vessel)
        return vessel.id
    }
}
