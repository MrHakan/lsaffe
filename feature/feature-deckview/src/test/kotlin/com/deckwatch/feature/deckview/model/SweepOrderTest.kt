package com.deckwatch.feature.deckview.model

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.StatusFlag
import com.deckwatch.feature.deckview.geometry.Vec2
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Sweep mode's walking order and its advancement rule — §7.3. */
class SweepOrderTest {

    private val markers = listOf(
        marker("aft", y = 0.9f),
        marker("mid", y = 0.5f),
        marker("fwdStbd", x = 0.9f, y = 0.1f),
        marker("fwdPort", x = 0.1f, y = 0.1f),
    )

    @Test
    fun `walking order is forward to aft, then port to starboard`() {
        assertThat(SweepOrder.order(markers).map { it.equipmentId })
            .containsExactly("fwdPort", "fwdStbd", "mid", "aft").inOrder()
    }

    @Test
    fun `the sweep opens on the first ungraded item`() {
        assertThat(SweepOrder.first(markers)).isEqualTo("fwdPort")
    }

    @Test
    fun `already graded equipment is skipped when the sweep opens`() {
        val partlyGraded = markers.map {
            if (it.equipmentId == "fwdPort") it.copy(condition = ConditionGrade.GOOD) else it
        }

        assertThat(SweepOrder.first(partlyGraded)).isEqualTo("fwdStbd")
    }

    @Test
    fun `grading advances to the next ungraded item on the deck`() {
        val next = SweepOrder.next(markers, gradedInSweep = setOf("fwdPort"), currentId = "fwdPort")

        assertThat(next).isEqualTo("fwdStbd")
    }

    @Test
    fun `the sweep wraps once past the last item to pick up anything skipped`() {
        // The officer jumped straight to the aft item; the three forward ones are still open.
        val next = SweepOrder.next(markers, gradedInSweep = setOf("aft"), currentId = "aft")

        assertThat(next).isEqualTo("fwdPort")
    }

    @Test
    fun `the sweep ends when nothing on the deck is left`() {
        val graded = markers.map { it.equipmentId }.toSet()

        assertThat(SweepOrder.next(markers, graded, currentId = "aft")).isNull()
        assertThat(SweepOrder.first(markers, graded)).isNull()
    }

    @Test
    fun `items already graded before the sweep are never offered`() {
        val allGood = markers.map { it.copy(condition = ConditionGrade.GOOD) }

        assertThat(SweepOrder.first(allGood)).isNull()
        assertThat(SweepOrder.next(allGood, emptySet(), currentId = null)).isNull()
    }

    @Test
    fun `an unknown current item restarts from the first candidate`() {
        assertThat(SweepOrder.next(markers, emptySet(), currentId = "not-on-this-deck"))
            .isEqualTo("fwdPort")
    }

    @Test
    fun `an empty deck has nothing to sweep`() {
        assertThat(SweepOrder.next(emptyList(), emptySet(), currentId = null)).isNull()
        assertThat(SweepOrder.first(emptyList())).isNull()
    }

    private fun marker(id: String, x: Float = 0.5f, y: Float = 0.5f) = MarkerNode(
        equipmentId = id,
        tag = "FE-UD-$id",
        typeName = "Portable fire extinguisher",
        symbolKey = "FES001",
        condition = ConditionGrade.NOT_CHECKED,
        statusFlag = StatusFlag.IN_SERVICE,
        position = Vec2(x, y),
        zoneId = null,
        nextDueDate = null,
        overdue = false,
    )
}
