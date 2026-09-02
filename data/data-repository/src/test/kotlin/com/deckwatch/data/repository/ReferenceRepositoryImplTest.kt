package com.deckwatch.data.repository

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

/**
 * The two halves of the reference library: what lives in Room, and what is served straight from the
 * seed assets with no table behind it (plan presets §6.3, symbol metadata §10.3).
 */
@RunWith(RobolectricTestRunner::class)
class ReferenceRepositoryImplTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var harness: RepositoryHarness

    @Before
    fun setUp() {
        harness = RepositoryHarness(temporaryFolder.newFolder(), TestData.referenceDay)
    }

    @After
    fun tearDown() = harness.close()

    @Test
    fun `plan presets and symbols come from the seed without a table`() = runTest {
        // No seeding has run, and there is no table for either of these.
        val presets = harness.referenceRepository.observePlanPresets().first()
        val symbols = harness.referenceRepository.observeSymbols().first()

        assertThat(presets).hasSize(6)
        assertThat(presets.map { it.key }).containsNoDuplicates()
        assertThat(symbols).isNotEmpty()
        assertThat(symbols.map { it.key }).contains("FES001")

        // Memoised: a second collection returns the same parsed content.
        assertThat(harness.referenceRepository.observePlanPresets().first()).isEqualTo(presets)
    }

    @Test
    fun `regulation cards and the catalogue are searchable once seeded`() = runTest {
        harness.seedInitializer.ensureSeeded()

        assertThat(harness.referenceRepository.observeRegulationCards().first().size)
            .isAtLeast(120)
        assertThat(harness.referenceRepository.searchRegulationCards("lifeboat").first())
            .isNotEmpty()
        assertThat(harness.referenceRepository.observeEquipmentTypes().first()).isNotEmpty()
        assertThat(harness.referenceRepository.observeRoundTemplates().first()).isNotEmpty()
    }

    @Test
    fun `user notes are written, listed and deleted`() = runTest {
        val note = TestData.userNote(title = "Liferaft servicing station, Rotterdam")
        harness.referenceRepository.upsertUserNote(note)

        assertThat(harness.referenceRepository.observeUserNotes().first().map { it.title })
            .containsExactly("Liferaft servicing station, Rotterdam")

        harness.referenceRepository.upsertUserNote(note.copy(body = "Booked for 12 March."))
        assertThat(harness.referenceRepository.observeUserNotes().first().single().body)
            .isEqualTo("Booked for 12 March.")

        harness.referenceRepository.deleteUserNote(note.id)
        assertThat(harness.referenceRepository.observeUserNotes().first()).isEmpty()
    }

    @Test
    fun `a user-defined type is always flagged as the user's own`() = runTest {
        val type = TestData.equipmentType(typeKey = "USER_SAND_BOX", isUserDefined = false)

        harness.referenceRepository.upsertUserDefinedType(type)

        assertThat(harness.referenceRepository.getEquipmentType("USER_SAND_BOX")?.isUserDefined)
            .isTrue()
    }
}
