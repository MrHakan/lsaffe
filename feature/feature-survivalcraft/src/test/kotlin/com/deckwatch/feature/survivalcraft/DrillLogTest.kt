package com.deckwatch.feature.survivalcraft

import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.survivalcraft.drill.DrillLog
import com.deckwatch.feature.survivalcraft.drill.DrillNotes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DrillLogTest {

    private val today = TestData.referenceDay
    private val craftType = "LSA_LIFEBOAT_TOTALLY_ENCLOSED"

    private fun drillRound(
        id: String,
        dayOffset: Long,
        launched: Boolean,
        equipmentId: String = "boat-1",
        templateKey: String = DrillLog.templateKey(craftType),
    ) = TestData.round(
        id = id,
        templateKey = templateKey,
        title = "Lifeboat drill",
        startedAt = DrillLog.toMillis(today + dayOffset),
        performedBy = "C/O",
    ).copy(
        notes = DrillLog.encodeNotes(
            DrillNotes(equipmentId = equipmentId, launched = launched, remarks = "swung out"),
        ),
    )

    @Test
    fun `the template key namespaces drills by craft type`() {
        assertThat(DrillLog.templateKey(craftType)).isEqualTo("DRILL_LSA_LIFEBOAT_TOTALLY_ENCLOSED")
    }

    @Test
    fun `the drill date survives the epoch-day to epoch-millis round trip`() {
        assertThat(DrillLog.toEpochDay(DrillLog.toMillis(today))).isEqualTo(today)
        assertThat(DrillLog.toEpochDay(DrillLog.toMillis(0L))).isEqualTo(0L)
    }

    @Test
    fun `only this craft's drills are listed, newest first`() {
        val rounds = listOf(
            drillRound("r1", -40, launched = true),
            drillRound("r2", -10, launched = false),
            drillRound("r3", -5, launched = true, equipmentId = "boat-2"),
            TestData.round(id = "r4", templateKey = "WEEKLY_LSA", title = "Weekly round"),
        )
        val records = DrillLog.recordsFor("boat-1", craftType, rounds)

        assertThat(records.map { it.roundId }).containsExactly("r2", "r1").inOrder()
    }

    @Test
    fun `days since last launch counts only launched drills`() {
        val records = DrillLog.recordsFor(
            "boat-1",
            craftType,
            listOf(
                drillRound("r1", -40, launched = true),
                drillRound("r2", -3, launched = false),
            ),
        )
        assertThat(DrillLog.daysSinceLastLaunch(records, today)).isEqualTo(40)
        assertThat(DrillLog.lastDrillDay(records)).isEqualTo(today - 3)
    }

    @Test
    fun `a craft that has never been launched has no counter`() {
        val records = DrillLog.recordsFor("boat-1", craftType, listOf(drillRound("r1", -3, launched = false)))
        assertThat(DrillLog.daysSinceLastLaunch(records, today)).isNull()
    }

    @Test
    fun `a future-dated launch never reads as a negative count`() {
        val records = DrillLog.recordsFor("boat-1", craftType, listOf(drillRound("r1", 5, launched = true)))
        assertThat(DrillLog.daysSinceLastLaunch(records, today)).isEqualTo(0)
    }

    @Test
    fun `hand-written notes degrade to a remark rather than being lost`() {
        val notes = DrillLog.decodeNotes("Boat lowered to embarkation deck, brake tested")
        assertThat(notes.launched).isFalse()
        assertThat(notes.remarks).isEqualTo("Boat lowered to embarkation deck, brake tested")
        assertThat(DrillLog.decodeNotes(null)).isEqualTo(DrillNotes())
    }

    @Test
    fun `a record without an equipment id is treated as belonging to this craft`() {
        val round = drillRound("r1", -2, launched = true, equipmentId = "")
        val records = DrillLog.recordsFor("boat-1", craftType, listOf(round))
        assertThat(records.single().equipmentId).isEqualTo("boat-1")
    }
}
