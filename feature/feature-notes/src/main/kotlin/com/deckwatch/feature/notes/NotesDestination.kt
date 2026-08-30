package com.deckwatch.feature.notes

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.RegulationSection

/**
 * Where the Notes tab currently is. The tab navigates inside itself — the app's `NavHost` owns
 * only the four top-level tabs (§5) — so this is a small stack rather than a nested navigation
 * graph. [Home] is the bottom of it; the equipment guide is two deep (group, then one type), which
 * is why back pops rather than always returning to Home.
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

    /**
     * The equipment guide of §9.1: what a type *is*, not only what the rules say about it.
     * A null [group] lists every group; a set one lists that group's types.
     */
    data class Equipment(val group: EquipmentGroup? = null) : NotesDestination

    /** One equipment type's page: guide, figures, tests, rules and PSC findings. */
    data class TypeDetail(val typeKey: String) : NotesDestination
}

private const val HomeToken = "home"
private const val IntervalsToken = "intervals"
private const val SectionPrefix = "section:"
private const val EquipmentPrefix = "equipment:"
private const val TypePrefix = "type:"
private const val AllGroupsToken = "*"

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
            is NotesDestination.Equipment ->
                EquipmentPrefix + (destination.group?.name ?: AllGroupsToken)
            is NotesDestination.TypeDetail -> TypePrefix + destination.typeKey
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
            token.startsWith(EquipmentPrefix) -> {
                val name = token.removePrefix(EquipmentPrefix)
                NotesDestination.Equipment(
                    group = EquipmentGroup.entries.firstOrNull { it.name == name },
                )
            }
            token.startsWith(TypePrefix) ->
                NotesDestination.TypeDetail(token.removePrefix(TypePrefix))
            else -> NotesDestination.Home
        }
    },
)

/**
 * Saves the whole back stack, so returning to the app after process death lands the officer where
 * they were rather than at the top of the tab. Entries are joined with a character no token can
 * contain — type keys and enum names are all `[A-Z0-9_]`, and the two prefixes end in a colon.
 */
internal val NotesBackStackSaver: Saver<SnapshotStateList<NotesDestination>, String> = Saver(
    save = { stack -> stack.joinToString(StackSeparator) { NotesDestinationSaver.run { save(it) }.orEmpty() } },
    restore = { text ->
        val restored = text.split(StackSeparator)
            .filter { it.isNotEmpty() }
            .map { token -> NotesDestinationSaver.restore(token) ?: NotesDestination.Home }
        mutableStateListOf<NotesDestination>().apply { addAll(restored) }
    },
)

private const val StackSeparator = "|"
