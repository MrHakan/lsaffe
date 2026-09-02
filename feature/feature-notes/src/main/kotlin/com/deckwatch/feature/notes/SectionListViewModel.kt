package com.deckwatch.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RegulationSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One block of cards under a heading. [flag] is null for every section except FLAG. */
data class SectionCardGroup(
    val flag: FlagSubSection?,
    val cards: List<RegulationCard>,
)

data class SectionListUiState(
    val section: RegulationSection? = null,
    val groups: List<SectionCardGroup> = emptyList(),
    /** Which FLAG sub-list is selected; null means "all three, grouped". */
    val flagFilter: FlagSubSection? = null,
    /** Sub-sections that actually have cards, so empty chips are never offered. */
    val availableFlags: List<FlagSubSection> = emptyList(),
    val favourites: Set<String> = emptySet(),
    /** typeKey -> catalogue name, for the card's "Applies to" line. */
    val typeNames: Map<String, String> = emptyMap(),
    /** The in-section filter typed into the sticky search field — DESIGN_OVERHAUL rule 9. */
    val query: String = "",
) {
    val cardCount: Int get() = groups.sumOf { it.cards.size }
    val isEmpty: Boolean get() = cardCount == 0
    val isFiltering: Boolean get() = query.isNotBlank()

    /** Empty because the filter excluded everything, rather than because the section is bare. */
    val isFilteredToNothing: Boolean get() = isEmpty && isFiltering
    val showsFlagSubSections: Boolean get() = section == RegulationSection.FLAG

    fun isFavourite(refKey: String): Boolean = refKey in favourites

    /** Resolve a card's `appliesToTypeKeys` to catalogue names, falling back to the raw key. */
    fun appliesToNames(card: RegulationCard): List<String> =
        card.appliesToTypeKeys.map { key -> typeNames[key] ?: key }
}

@HiltViewModel
class SectionListViewModel @Inject constructor(
    private val reference: ReferenceRepository,
) : ViewModel() {

    /**
     * Section and FLAG sub-list move together: changing section must clear the sub-list, and two
     * separate state flows would publish an intermediate state with the old filter still applied.
     */
    private data class Selection(
        val section: RegulationSection? = null,
        val flag: FlagSubSection? = null,
        val query: String = "",
    )

    private val selection = MutableStateFlow(Selection())

    /**
     * Favourites live in memory only for now. §6 defines no favourites table and inventing one
     * here would put a schema decision in a feature module; [toggleFavourite] returns the new
     * state so the host can forward it to whatever persists it later.
     */
    private val favourites = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<SectionListUiState> = combine(
        reference.observeRegulationCards(),
        reference.observeEquipmentTypes(),
        selection,
        favourites,
    ) { cards, types, current, currentFavourites ->
        val currentSection = current.section
        val inSection = cards.filter { it.section == currentSection }
        SectionListUiState(
            section = currentSection,
            groups = groupCards(matching(inSection, current.query), currentSection, current.flag),
            flagFilter = current.flag,
            // Derived from the whole section, not the filtered subset: the chips must not
            // disappear under the officer while they type.
            availableFlags = if (currentSection == RegulationSection.FLAG) {
                FlagSubSection.entries.filter { candidate ->
                    inSection.any { it.flagSubSection() == candidate }
                }
            } else {
                emptyList()
            },
            favourites = currentFavourites,
            typeNames = types.associate { it.typeKey to it.nameEn },
            query = current.query,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionTimeoutMillis),
        initialValue = SectionListUiState(),
    )

    /** Point the screen at a section. Changing section resets the FLAG sub-list selection. */
    fun setSection(value: RegulationSection) {
        selection.update { current ->
            if (current.section == value) current else Selection(section = value)
        }
    }

    fun setFlagFilter(value: FlagSubSection?) {
        selection.update { it.copy(flag = value) }
    }

    /** The sticky in-section filter. Cleared automatically when the section changes. */
    fun onQueryChange(value: String) {
        selection.update { it.copy(query = value) }
    }

    /** Returns the new state, so the caller can forward it to an optional host callback. */
    fun toggleFavourite(refKey: String): Boolean {
        val nowFavourite = refKey !in favourites.value
        favourites.update { current ->
            if (nowFavourite) current + refKey else current - refKey
        }
        return nowFavourite
    }

    private companion object {
        const val SubscriptionTimeoutMillis = 5_000L

        /**
         * The in-section filter matches what the officer can actually see on a card: the
         * citation, the title and the WHAT line. Blank means everything.
         */
        fun matching(cards: List<RegulationCard>, query: String): List<RegulationCard> {
            val needle = query.trim()
            if (needle.isEmpty()) return cards
            return cards.filter { card ->
                card.citation.contains(needle, ignoreCase = true) ||
                    card.title.contains(needle, ignoreCase = true) ||
                    card.what.contains(needle, ignoreCase = true)
            }
        }

        /**
         * FLAG splits into RMI / Liberia / Panama sub-lists (§8.1); every other section is one
         * flat list. Cards whose Administration could not be derived land in a trailing
         * "Other flag notices" group — nothing is ever dropped.
         */
        fun groupCards(
            cards: List<RegulationCard>,
            section: RegulationSection?,
            filter: FlagSubSection?,
        ): List<SectionCardGroup> {
            val sorted = cards.sortedBy { it.citation }
            if (section != RegulationSection.FLAG) {
                return if (sorted.isEmpty()) emptyList() else listOf(SectionCardGroup(null, sorted))
            }
            val byFlag = sorted.groupBy { it.flagSubSection() }
            val order: List<FlagSubSection?> = when (filter) {
                null -> FlagSubSection.entries + listOf(null)
                else -> listOf(filter)
            }
            return order.mapNotNull { flag ->
                byFlag[flag]?.takeIf { it.isNotEmpty() }?.let { SectionCardGroup(flag, it) }
            }
        }
    }
}
