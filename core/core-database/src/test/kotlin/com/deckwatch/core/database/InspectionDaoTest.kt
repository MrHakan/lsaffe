package com.deckwatch.core.database

import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Severity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class InspectionDaoTest : DeckWatchDatabaseTest() {

    private val roundDao get() = database.roundDao()
    private val roundItemDao get() = database.roundItemDao()
    private val deficiencyDao get() = database.deficiencyDao()

    @Before
    fun seedVesselDeckAndEquipment() = runTest {
        database.vesselDao().upsert(Fixtures.vessel())
        database.deckDao().upsert(Fixtures.deck())
        database.equipmentDao().upsert(Fixtures.equipment(id = "eq-1", tag = "FE-UD-001"))
    }

    @Test
    fun `round round-trips and open rounds are the ones sweep mode resumes`() = runTest {
        val original = Fixtures.round()
        roundDao.upsert(original)

        val stored = roundDao.observeByVessel(Fixtures.VESSEL_ID).first().single()
        assertThat(stored).isEqualTo(original)
        assertThat(stored.toModel().toEntity()).isEqualTo(original)
        assertThat(roundDao.observeOpenByVessel(Fixtures.VESSEL_ID).first()).hasSize(1)

        roundDao.upsert(original.copy(completedAt = 1_700_009_000_000L, doneCount = 24))
        assertThat(roundDao.getById("round-1")?.doneCount).isEqualTo(24)
        assertThat(roundDao.observeOpenByVessel(Fixtures.VESSEL_ID).first()).isEmpty()

        roundDao.deleteById("round-1")
        assertThat(roundDao.observeByVessel(Fixtures.VESSEL_ID).first()).isEmpty()
    }

    @Test
    fun `round items round-trip and unchecked ones come first`() = runTest {
        roundDao.upsert(Fixtures.round())
        val checked = Fixtures.roundItem(id = "ri-checked")
        val unchecked = Fixtures.roundItem(id = "ri-unchecked")
            .copy(checkedAt = null, condition = null, remark = null)
        roundItemDao.upsertAll(listOf(checked, unchecked))

        val items = roundItemDao.observeByRound("round-1").first()
        assertThat(items.map { it.id }).containsExactly("ri-unchecked", "ri-checked").inOrder()
        assertThat(items.last()).isEqualTo(checked)
        assertThat(checked.toModel().toEntity()).isEqualTo(checked)
        assertThat(roundItemDao.nextUnchecked("round-1")?.id).isEqualTo("ri-unchecked")

        roundItemDao.upsert(
            unchecked.copy(checkedAt = 1_700_009_500_000L, condition = ConditionGrade.GOOD),
        )
        assertThat(roundItemDao.nextUnchecked("round-1")).isNull()
        assertThat(roundItemDao.getById("ri-unchecked")?.condition).isEqualTo(ConditionGrade.GOOD)

        roundItemDao.deleteByRound("round-1")
        assertThat(roundItemDao.observeByRound("round-1").first()).isEmpty()
    }

    @Test
    fun `deficiency round-trips and open ones are ranked by real severity`() = runTest {
        val major = Fixtures.deficiency(id = "def-major")
        val critical = Fixtures.deficiency(id = "def-critical")
            .copy(severity = Severity.CRITICAL_DETAINABLE)
        val observation = Fixtures.deficiency(id = "def-observation")
            .copy(severity = Severity.OBSERVATION, status = DeficiencyStatus.IN_PROGRESS)
        val closed = Fixtures.deficiency(id = "def-closed")
            .copy(status = DeficiencyStatus.CLOSED, closedDate = 20_260L, closedBy = "C/O")
        val deferred = Fixtures.deficiency(id = "def-deferred")
            .copy(status = DeficiencyStatus.DEFERRED_TO_OFFICE)
        deficiencyDao.upsertAll(listOf(major, critical, observation, closed, deferred))

        assertThat(deficiencyDao.observeByVessel(Fixtures.VESSEL_ID).first()).hasSize(5)
        assertThat(deficiencyDao.getById("def-major")).isEqualTo(major)
        assertThat(major.toModel().toEntity()).isEqualTo(major)

        val open = deficiencyDao.observeOpenByVessel(Fixtures.VESSEL_ID).first()
        assertThat(open.map { it.id })
            .containsExactly("def-critical", "def-major", "def-observation").inOrder()
        assertThat(deficiencyDao.observeOpenCount(Fixtures.VESSEL_ID).first()).isEqualTo(3)
        assertThat(deficiencyDao.observeOpenByEquipment("eq-1").first()).hasSize(3)

        deficiencyDao.upsert(major.copy(status = DeficiencyStatus.CLOSED, closedDate = 20_300L))
        assertThat(deficiencyDao.observeOpenCount(Fixtures.VESSEL_ID).first()).isEqualTo(2)

        deficiencyDao.deleteById("def-major")
        assertThat(deficiencyDao.observeByVessel(Fixtures.VESSEL_ID).first()).hasSize(4)
    }
}
