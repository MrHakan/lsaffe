package com.deckwatch.feature.vessel

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.vessel.deck.DeckOrdering
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeckOrderingTest {

    private fun deck(id: String, level: Int) = TestData.deck(id = id, vesselId = "vessel-1", levelIndex = level)

    @Test
    fun `the stack sorts by level index descending`() {
        val decks = listOf(deck("a", 0), deck("b", 20), deck("c", -10), deck("d", 10))

        assertThat(DeckOrdering.sortedForStack(decks).map { it.id })
            .containsExactly("b", "d", "a", "c")
            .inOrder()
    }

    @Test
    fun `one insert slot sits between each adjacent pair`() {
        val decks = listOf(deck("a", 0), deck("b", 20), deck("c", -10))

        val slots = DeckOrdering.insertSlots(decks)

        assertThat(slots).hasSize(2)
        assertThat(slots[0].upperLevelIndex).isEqualTo(20)
        assertThat(slots[0].lowerLevelIndex).isEqualTo(0)
        assertThat(slots[1].upperLevelIndex).isEqualTo(0)
        assertThat(slots[1].lowerLevelIndex).isEqualTo(-10)
        assertThat(slots.all { it.enabled }).isTrue()
    }

    @Test
    fun `a single deck offers no insert slot`() {
        assertThat(DeckOrdering.insertSlots(listOf(deck("a", 0)))).isEmpty()
        assertThat(DeckOrdering.insertSlots(emptyList())).isEmpty()
    }

    @Test
    fun `adjacent level indices leave no room and the slot is disabled`() {
        val decks = listOf(deck("a", 0), deck("b", 1))

        val slot = DeckOrdering.insertSlots(decks).single()

        assertThat(slot.enabled).isFalse()
        assertThat(DeckOrdering.hasRoomBetween(0, 1)).isFalse()
        assertThat(DeckOrdering.hasRoomBetween(0, 2)).isTrue()
    }

    @Test
    fun `room between works for negative levels too`() {
        assertThat(DeckOrdering.hasRoomBetween(-20, -10)).isTrue()
        assertThat(DeckOrdering.hasRoomBetween(-2, -1)).isFalse()
        assertThat(DeckOrdering.hasRoomBetween(-1, 1)).isTrue()
    }

    @Test
    fun `the order the pair is given in does not matter`() {
        assertThat(DeckOrdering.hasRoomBetween(20, 0)).isEqualTo(DeckOrdering.hasRoomBetween(0, 20))
    }

    // ------------------------------------------------------------------ worst condition

    @Test
    fun `worst condition is the lowest graded score on the deck`() {
        val equipment = listOf(
            TestData.equipment(id = "e1", condition = ConditionGrade.GOOD),
            TestData.equipment(id = "e2", condition = ConditionGrade.DEFECTIVE),
            TestData.equipment(id = "e3", condition = ConditionGrade.MONITOR),
        )

        assertThat(DeckOrdering.worstCondition(equipment)).isEqualTo(ConditionGrade.DEFECTIVE)
    }

    @Test
    fun `an ungraded item does not outrank a real bad grade`() {
        val equipment = listOf(
            TestData.equipment(id = "e1", condition = ConditionGrade.NOT_CHECKED),
            TestData.equipment(id = "e2", condition = ConditionGrade.MONITOR),
        )

        assertThat(DeckOrdering.worstCondition(equipment)).isEqualTo(ConditionGrade.MONITOR)
    }

    @Test
    fun `a wholly ungraded deck reports not checked and an empty deck reports nothing`() {
        val ungraded = listOf(TestData.equipment(id = "e1", condition = ConditionGrade.NOT_CHECKED))

        assertThat(DeckOrdering.worstCondition(ungraded)).isEqualTo(ConditionGrade.NOT_CHECKED)
        assertThat(DeckOrdering.worstCondition(emptyList())).isNull()
    }
}
