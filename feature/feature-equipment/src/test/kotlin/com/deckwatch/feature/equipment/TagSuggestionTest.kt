package com.deckwatch.feature.equipment

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tag auto-numbering — §7.5 step 3, `PREFIX-DECK-NN`. */
class TagSuggestionTest {

    @Test
    fun `the prefix carries the deck short code, upper cased`() {
        assertThat(TagSuggestion.prefix("FE", "ud")).isEqualTo("FE-UD-")
        assertThat(TagSuggestion.prefix("LB", "BR")).isEqualTo("LB-BR-")
    }

    @Test
    fun `an unplaced item is numbered per prefix only`() {
        assertThat(TagSuggestion.prefix("FE", null)).isEqualTo("FE-")
        assertThat(TagSuggestion.prefix("FE", "  ")).isEqualTo("FE-")
    }

    @Test
    fun `a type with no prefix still produces a usable tag`() {
        assertThat(TagSuggestion.suggest("", "UD", 3)).isEqualTo("EQ-UD-03")
    }

    @Test
    fun `numbers are zero padded to two digits and widen beyond`() {
        assertThat(TagSuggestion.suggest("FE", "UD", 8)).isEqualTo("FE-UD-08")
        assertThat(TagSuggestion.suggest("FE", "UD", 100)).isEqualTo("FE-UD-100")
    }

    @Test
    fun `increment preserves the width of the trailing digit group`() {
        assertThat(TagSuggestion.increment("FE-UD-08", 1)).isEqualTo("FE-UD-09")
        assertThat(TagSuggestion.increment("FE-UD-08", 2)).isEqualTo("FE-UD-10")
        assertThat(TagSuggestion.increment("FE-UD-99", 1)).isEqualTo("FE-UD-100")
        assertThat(TagSuggestion.increment("LB No.1", 1)).isEqualTo("LB No.2")
    }

    @Test
    fun `increment by zero is the tag itself`() {
        assertThat(TagSuggestion.increment("FE-UD-08", 0)).isEqualTo("FE-UD-08")
    }

    @Test
    fun `a tag with no digits gets a suffix rather than being mangled`() {
        assertThat(TagSuggestion.increment("LB PORT", 1)).isEqualTo("LB PORT-2")
        assertThat(TagSuggestion.increment("LB PORT", 2)).isEqualTo("LB PORT-3")
    }
}
