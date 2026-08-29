package com.deckwatch.feature.notes

import app.cash.turbine.test
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.testing.FakeReferenceRepository
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The card detail dialog: the card, its equipment names and the officer's own notes — §8.2, §8.4. */
class CardDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reference = FakeReferenceRepository()

    @Before
    fun seed() {
        reference.seedEquipmentType(
            TestData.equipmentType(
                typeKey = "FFE_PORTABLE_EXTINGUISHER",
                nameEn = "Portable fire extinguisher",
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "SOLAS_II2_10_3",
                section = RegulationSection.FFE,
                citation = "SOLAS II-2/10.3",
                title = "Fire-fighting equipment readiness",
                appliesToTypeKeys = listOf("FFE_PORTABLE_EXTINGUISHER", "FFE_FIRE_HOSE"),
            ),
        )
    }

    @Test
    fun `opening a card loads it with resolved equipment names`() = runTest {
        val viewModel = CardDetailViewModel(reference)
        viewModel.open("SOLAS_II2_10_3")

        viewModel.uiState.test {
            val state = awaitState { it.isLoaded }

            assertThat(state.card?.citation).isEqualTo("SOLAS II-2/10.3")
            assertThat(state.appliesToNames)
                .containsExactly("Portable fire extinguisher", "FFE_FIRE_HOSE")
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unknown refKey loads nothing rather than a half-built card`() = runTest {
        val viewModel = CardDetailViewModel(reference)
        viewModel.open("NOT_SEEDED")

        viewModel.uiState.test {
            val state = awaitState { it.appliesToNames.isEmpty() }
            assertThat(state.isLoaded).isFalse()
            assertThat(state.myNotes).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Add my note attaches the note to this card and it comes straight back`() = runTest {
        val viewModel = CardDetailViewModel(reference)
        viewModel.open("SOLAS_II2_10_3")

        viewModel.uiState.test {
            awaitState { it.isLoaded }

            viewModel.addNote(
                title = "SOLAS II-2/10.3",
                body = "Bracket for FE-UD-07 is loose; landed for repair.",
            )

            val state = awaitState { it.myNotes.isNotEmpty() }
            val note = state.myNotes.single()
            assertThat(note.regulationRefKey).isEqualTo("SOLAS_II2_10_3")
            assertThat(note.body).isEqualTo("Bracket for FE-UD-07 is loose; landed for repair.")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notes attached to other cards are not shown here`() = runTest {
        reference.upsertUserNote(
            TestData.userNote(id = "other", regulationRefKey = "SOMETHING_ELSE"),
        )
        reference.upsertUserNote(TestData.userNote(id = "loose", regulationRefKey = null))
        val viewModel = CardDetailViewModel(reference)
        viewModel.open("SOLAS_II2_10_3")

        viewModel.uiState.test {
            val state = awaitState { it.isLoaded }
            assertThat(state.myNotes).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank note is not written`() = runTest {
        val viewModel = CardDetailViewModel(reference)
        viewModel.open("SOLAS_II2_10_3")
        viewModel.addNote(title = "  ", body = "   ")

        assertThat(reference.userNotes.value).isEmpty()
    }

    @Test
    fun `no note is written before a card is opened`() = runTest {
        val viewModel = CardDetailViewModel(reference)
        viewModel.addNote(title = "Orphan", body = "Nothing to attach to")

        assertThat(reference.userNotes.value).isEmpty()
    }
}
