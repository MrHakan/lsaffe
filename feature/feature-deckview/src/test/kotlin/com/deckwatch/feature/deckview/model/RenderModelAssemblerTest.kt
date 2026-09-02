package com.deckwatch.feature.deckview.model

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.StatusFlag
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Assembling one frame's worth of render model from the repositories' output — §7.2. */
class RenderModelAssemblerTest {

    private val today = TestData.referenceDay

    private val zone = TestData.zone(
        id = "z-fwd",
        deckId = "upper",
        name = "Fwd Mooring Station",
        polygon = listOf(
            PlanPoint(0f, 0f),
            PlanPoint(1f, 0f),
            PlanPoint(1f, 0.5f),
            PlanPoint(0f, 0.5f),
        ),
    )

    @Test
    fun `decks are ranked bottom first with gaps of ten collapsed`() {
        val model = assemble(
            decks = listOf(
                TestData.deck(id = "bridge", levelIndex = 20, shortCode = "BR"),
                TestData.deck(id = "upper", levelIndex = 0, shortCode = "UD"),
                TestData.deck(id = "engine", levelIndex = -30, shortCode = "ER"),
            ),
        )

        assertThat(model.decks.map { it.deckId }).containsExactly("engine", "upper", "bridge").inOrder()
        assertThat(model.decks.map { it.levelZ }).containsExactly(0, 1, 2).inOrder()
        assertThat(model.decksTopFirst.map { it.shortCode })
            .containsExactly("BR", "UD", "ER").inOrder()
    }

    @Test
    fun `worst condition on a deck ignores ungraded items`() {
        val model = assemble(
            decks = listOf(TestData.deck(id = "upper", levelIndex = 0)),
            equipment = listOf(
                equipment("a", ConditionGrade.GOOD),
                equipment("b", ConditionGrade.NOT_CHECKED),
                equipment("c", ConditionGrade.MONITOR),
                equipment("d", ConditionGrade.NOT_CHECKED),
            ),
        )

        assertThat(model.decks.single().worstCondition).isEqualTo(ConditionGrade.MONITOR)
    }

    @Test
    fun `a deck with nothing graded reads as not checked`() {
        val model = assemble(
            decks = listOf(TestData.deck(id = "upper", levelIndex = 0)),
            equipment = listOf(equipment("a", ConditionGrade.NOT_CHECKED)),
        )

        assertThat(model.decks.single().worstCondition).isEqualTo(ConditionGrade.NOT_CHECKED)
    }

    @Test
    fun `an empty deck reads as not checked`() {
        val model = assemble(decks = listOf(TestData.deck(id = "upper", levelIndex = 0)))

        assertThat(model.decks.single().worstCondition).isEqualTo(ConditionGrade.NOT_CHECKED)
        assertThat(model.decks.single().overdueCount).isEqualTo(0)
    }

    @Test
    fun `overdue is nextDueDate strictly before today, per deck and per zone`() {
        val model = assemble(
            decks = listOf(TestData.deck(id = "upper", levelIndex = 0)),
            zones = mapOf("upper" to listOf(zone)),
            equipment = listOf(
                equipment("late", ConditionGrade.GOOD, zoneId = "z-fwd", nextDueDate = today - 1),
                equipment("dueToday", ConditionGrade.GOOD, zoneId = "z-fwd", nextDueDate = today),
                equipment("later", ConditionGrade.GOOD, nextDueDate = today + 30),
                equipment("veryLate", ConditionGrade.GOOD, nextDueDate = today - 90),
            ),
        )

        val deck = model.decks.single()
        assertThat(deck.overdueCount).isEqualTo(2)
        assertThat(deck.zones.single().overdueCount).isEqualTo(1)
        assertThat(deck.markers.first { it.equipmentId == "dueToday" }.overdue).isFalse()
    }

    @Test
    fun `zones aggregate the worst condition of the equipment inside them`() {
        val model = assemble(
            decks = listOf(TestData.deck(id = "upper", levelIndex = 0)),
            zones = mapOf("upper" to listOf(zone)),
            equipment = listOf(
                equipment("in1", ConditionGrade.DEFECTIVE, zoneId = "z-fwd"),
                equipment("in2", ConditionGrade.GOOD, zoneId = "z-fwd"),
                equipment("out", ConditionGrade.OUT_OF_SERVICE),
            ),
        )

        val zoneNode = model.decks.single().zones.single()
        assertThat(zoneNode.worstCondition).isEqualTo(ConditionGrade.DEFECTIVE)
        assertThat(zoneNode.markerCount).isEqualTo(2)
        assertThat(zoneNode.centroid.y).isWithin(1e-4f).of(0.25f)
        assertThat(model.decks.single().unzonedMarkers.map { it.equipmentId }).containsExactly("out")
        assertThat(model.decks.single().worstCondition).isEqualTo(ConditionGrade.OUT_OF_SERVICE)
    }

    @Test
    fun `deleted, unplaced and child equipment stay off the plan`() {
        val model = assemble(
            decks = listOf(TestData.deck(id = "upper", levelIndex = 0)),
            equipment = listOf(
                equipment("live", ConditionGrade.GOOD),
                equipment("gone", ConditionGrade.GOOD).copy(deletedAt = 1L),
                equipment("child", ConditionGrade.GOOD).copy(parentId = "live"),
                equipment("inbox", ConditionGrade.GOOD).copy(deckId = null),
            ),
        )

        assertThat(model.decks.single().markers.map { it.equipmentId }).containsExactly("live")
        assertThat(model.unplacedCount).isEqualTo(1)
    }

    @Test
    fun `markers carry the type name, the symbol and the out-of-service flag`() {
        val model = assemble(
            decks = listOf(TestData.deck(id = "upper", levelIndex = 0)),
            equipment = listOf(
                equipment("a", ConditionGrade.GOOD).copy(statusFlag = StatusFlag.LANDED_ASHORE),
                equipment("b", ConditionGrade.OUT_OF_SERVICE),
                equipment("c", ConditionGrade.GOOD).copy(statusFlag = StatusFlag.CONDEMNED),
            ),
        )

        val markers = model.decks.single().markers.associateBy { it.equipmentId }
        assertThat(markers.getValue("a").typeName).isEqualTo("Portable fire extinguisher")
        assertThat(markers.getValue("a").symbolKey).isEqualTo("FES001")
        assertThat(markers.getValue("a").outOfService).isFalse()
        assertThat(markers.getValue("b").outOfService).isTrue()
        assertThat(markers.getValue("c").outOfService).isTrue()
    }

    @Test
    fun `markers come out in walking order, forward to aft`() {
        val model = assemble(
            decks = listOf(TestData.deck(id = "upper", levelIndex = 0)),
            equipment = listOf(
                equipment("aft", ConditionGrade.GOOD, posY = 0.9f),
                equipment("fwdPort", ConditionGrade.GOOD, posX = 0.1f, posY = 0.1f),
                equipment("fwdStbd", ConditionGrade.GOOD, posX = 0.9f, posY = 0.1f),
            ),
        )

        assertThat(model.decks.single().markers.map { it.equipmentId })
            .containsExactly("fwdPort", "fwdStbd", "aft").inOrder()
    }

    @Test
    fun `a deck with no short code gets initials for its spine pill`() {
        val model = assemble(
            decks = listOf(
                TestData.deck(id = "a", levelIndex = 0, name = "Engine Room 2nd Flat", shortCode = null),
                TestData.deck(id = "b", levelIndex = 10, name = "Poop", shortCode = "  "),
            ),
        )

        assertThat(model.deck("a")?.shortCode).isEqualTo("ER")
        assertThat(model.deck("b")?.shortCode).isEqualTo("PO")
    }

    @Test
    fun `an empty vessel assembles to an empty model`() {
        val model = assemble(decks = emptyList())

        assertThat(model.isEmpty).isTrue()
        assertThat(model.deck("nothing")).isNull()
    }

    private fun assemble(
        decks: List<com.deckwatch.core.model.Deck>,
        zones: Map<String, List<com.deckwatch.core.model.Zone>> = emptyMap(),
        equipment: List<com.deckwatch.core.model.Equipment> = emptyList(),
    ) = RenderModelAssembler.assemble(
        vesselId = "vessel-1",
        vesselName = "MV Example",
        decks = decks,
        zonesByDeck = zones,
        equipment = equipment,
        typeNames = mapOf("FFE_PORTABLE_EXTINGUISHER" to "Portable fire extinguisher"),
        today = today,
    )

    @Suppress("LongParameterList") // A fixture shorthand; every argument has a default.
    private fun equipment(
        id: String,
        condition: ConditionGrade,
        zoneId: String? = null,
        nextDueDate: Long? = null,
        posX: Float = 0.5f,
        posY: Float = 0.5f,
    ) = TestData.equipment(
        id = id,
        deckId = "upper",
        zoneId = zoneId,
        tag = "FE-UD-$id",
        condition = condition,
        nextDueDate = nextDueDate,
        posX = posX,
        posY = posY,
    )
}
