package com.deckwatch.feature.notes

import app.cash.turbine.test
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.testing.FakeReferenceRepository
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** MY NOTES: create, edit, delete, folders and the attached-card citation chip — §8.1. */
class MyNotesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reference = FakeReferenceRepository()

    @Test
    fun `a note survives the round trip through the repository`() = runTest {
        val viewModel = MyNotesViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.isEmpty }

            viewModel.createNote(
                title = "Extinguisher service contact",
                body = "Rotterdam provider — booked through the agent.",
                folder = "Shore services",
            )

            val created = awaitState { !it.isEmpty }
            val note = created.folders.single().notes.single()
            assertThat(note.title).isEqualTo("Extinguisher service contact")
            assertThat(note.body).isEqualTo("Rotterdam provider — booked through the agent.")
            assertThat(note.folder).isEqualTo("Shore services")
            assertThat(note.id).isNotEmpty()
            assertThat(note.createdAt).isEqualTo(note.updatedAt)

            viewModel.updateNote(
                note = note,
                title = "Extinguisher service — Rotterdam",
                body = "Confirmed for the 14th.",
                folder = "Shore services",
            )

            val edited = awaitState { it.folders.single().notes.single().body == "Confirmed for the 14th." }
            val same = edited.folders.single().notes.single()
            assertThat(same.id).isEqualTo(note.id)
            assertThat(same.createdAt).isEqualTo(note.createdAt)
            assertThat(same.title).isEqualTo("Extinguisher service — Rotterdam")

            viewModel.deleteNote(same.id)
            assertThat(awaitState { it.isEmpty }.noteCount).isEqualTo(0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notes group into folders with the unfiled group first`() = runTest {
        val viewModel = MyNotesViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.isEmpty }

            viewModel.createNote(title = "Zulu", body = "b", folder = "Surveys")
            viewModel.createNote(title = "Alpha", body = "b", folder = "Drills")
            viewModel.createNote(title = "Loose", body = "b")

            val state = awaitState { it.noteCount == 3 }
            assertThat(state.folders.map { it.name })
                .containsExactly("", "Drills", "Surveys")
                .inOrder()
            assertThat(state.folders.first().isUnfiled).isTrue()
            assertThat(state.folderNames).containsExactly("Drills", "Surveys").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `folder names are trimmed so stray spaces do not split a folder`() = runTest {
        val viewModel = MyNotesViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.isEmpty }

            viewModel.createNote(title = "One", body = "b", folder = "PSC")
            viewModel.createNote(title = "Two", body = "b", folder = "  PSC  ")

            val state = awaitState { it.noteCount == 2 }
            assertThat(state.folders).hasSize(1)
            assertThat(state.folders.single().name).isEqualTo("PSC")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a note attached to a card reports the card's citation`() = runTest {
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "SOLAS_III_20_6",
                section = RegulationSection.SOLAS,
                citation = "SOLAS III/20.6",
            ),
        )
        val viewModel = MyNotesViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.isEmpty }
            viewModel.createNote(
                title = "Weekly round",
                body = "Bosun signs the deck log after the boat engines run.",
                regulationRefKey = "SOLAS_III_20_6",
            )

            val state = awaitState { !it.isEmpty }
            val note = state.folders.single().notes.single()
            assertThat(state.citationFor(note)).isEqualTo("SOLAS III/20.6")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an attachment to an unknown card falls back to the raw key rather than showing nothing`() = runTest {
        val viewModel = MyNotesViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.isEmpty }
            viewModel.createNote(title = "Orphan", body = "b", regulationRefKey = "NOT_SEEDED")

            val state = awaitState { !it.isEmpty }
            assertThat(state.citationFor(state.folders.single().notes.single())).isEqualTo("NOT_SEEDED")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an entirely blank note is not written`() = runTest {
        val viewModel = MyNotesViewModel(reference)
        viewModel.createNote(title = "   ", body = "  ")

        assertThat(reference.userNotes.value).isEmpty()
    }

    @Test
    fun `toggling favourite flips the stored note`() = runTest {
        val viewModel = MyNotesViewModel(reference)
        reference.upsertUserNote(TestData.userNote(id = "note-1", isFavourite = false))

        viewModel.uiState.test {
            val initial = awaitState { !it.isEmpty }
            viewModel.toggleFavourite(initial.folders.single().notes.single())

            val state = awaitState { it.folders.single().notes.single().isFavourite }
            assertThat(state.folders.single().notes.single().isFavourite).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
