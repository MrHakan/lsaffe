package com.deckwatch.feature.notes

import androidx.compose.runtime.saveable.Saver
import com.deckwatch.core.model.RegulationSection

/**
 * Where the Notes tab currently is. The tab navigates inside itself — the app's `NavHost` owns
 * only the four top-level tabs (§5) — so this is a one-level state machine rather than a nested
 * navigation graph: [Home] is always the parent, everything else is one step down.
 *
 * The regulation-card detail is *not* a destination: it is a dialog layered over whichever
 * destination opened it (§8.4 wants the card readable without leaving where you were).
 */
internal sealed interface NotesDestination {

    /** Six section tiles plus global search — §8.1. */
    data object Home : NotesDestination

    /** One section's cards; [RegulationSection.MY_NOTES] renders the user's own notes. */
    data class Section(val section: RegulationSection) : NotesDestination

    /** The equipment-type × interval × performed-by matrix — §8.3. */
    data object Intervals : NotesDestination
}

private const val HomeToken = "home"
private const val IntervalsToken = "intervals"
private const val SectionPrefix = "section:"

/**
 * Survives process death: the destination is stored as a short string rather than a Parcelable so
 * that no `@Parcelize`/`kotlin-parcelize` dependency is needed in a pure-Compose feature module.
 */
internal val NotesDestinationSaver: Saver<NotesDestination, String> = Saver(
    save = { destination ->
        when (destination) {
            NotesDestination.Home -> HomeToken
            NotesDestination.Intervals -> IntervalsToken
            is NotesDestination.Section -> SectionPrefix + destination.section.name
        }
    },
    restore = { token ->
        when {
            token == IntervalsToken -> NotesDestination.Intervals
            token.startsWith(SectionPrefix) -> {
                val name = token.removePrefix(SectionPrefix)
                RegulationSection.entries.firstOrNull { it.name == name }
                    ?.let(NotesDestination::Section)
                    ?: NotesDestination.Home
            }
            else -> NotesDestination.Home
        }
    },
)
