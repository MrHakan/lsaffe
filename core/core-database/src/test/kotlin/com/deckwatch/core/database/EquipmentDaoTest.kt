package com.deckwatch.core.database

import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.ConditionGrade
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class EquipmentDaoTest : DeckWatchDatabaseTest() {

    private val equipmentDao get() = database.equipmentDao()

    @Before
    fun seedVesselAndDeck() = runTest {
        database.vesselDao().upsert(Fixtures.vessel())
        database.deckDao().upsert(Fixtures.deck())
    }

    @Test
    fun `equipment round-trips through the dao and the domain mappers`() = runTest {
        val original = Fixtures.equipment()
        equipmentDao.upsert(original)

        val stored = equipmentDao.observeByVessel(Fixtures.VESSEL_ID).first().single()
        assertThat(stored).isEqualTo(original)
        assertThat(stored.toModel().toEntity()).isEqualTo(original)
        assertThat(stored.photoUris).containsExactly("content://photo/1", "content://photo/2").inOrder()

        equipmentDao.upsert(original.copy(name = "Renamed", quantity = 5))
        val updated = equipmentDao.getById("eq-1")
        assertThat(updated?.name).isEqualTo("Renamed")
        assertThat(updated?.quantity).isEqualTo(5)

        equipmentDao.deletePermanently("eq-1")
        assertThat(equipmentDao.observeByVessel(Fixtures.VESSEL_ID).first()).isEmpty()
    }

    @Test
    fun `soft-deleted equipment disappears from every default query and comes back on undelete`() =
        runTest {
            equipmentDao.upsert(Fixtures.equipment(id = "eq-1", tag = "FE-UD-001"))
            equipmentDao.upsert(Fixtures.equipment(id = "eq-2", tag = "FE-UD-002"))

            equipmentDao.softDelete("eq-2", atMillis = 1_700_001_000_000L)

            assertThat(equipmentDao.observeByVessel(Fixtures.VESSEL_ID).first().map { it.id })
                .containsExactly("eq-1")
            assertThat(equipmentDao.observeByDeck(Fixtures.DECK_ID).first().map { it.id })
                .containsExactly("eq-1")
            assertThat(equipmentDao.observeByZone("zone-1").first().map { it.id })
                .containsExactly("eq-1")
            assertThat(equipmentDao.getById("eq-2")).isNull()
            assertThat(equipmentDao.idsForVessel(Fixtures.VESSEL_ID)).containsExactly("eq-1")

            // The tombstone is still there — undo (C10) and import merge (§13.5) both need it.
            val tombstone = equipmentDao.getByIdIncludingDeleted("eq-2")
            assertThat(tombstone?.deletedAt).isEqualTo(1_700_001_000_000L)

            equipmentDao.undelete("eq-2", atMillis = 1_700_001_500_000L)
            assertThat(equipmentDao.observeByVessel(Fixtures.VESSEL_ID).first().map { it.id })
                .containsExactly("eq-1", "eq-2")
            assertThat(equipmentDao.getById("eq-2")?.deletedAt).isNull()
        }

    @Test
    fun `children and unplaced items are separated from the placed register`() = runTest {
        equipmentDao.upsert(Fixtures.equipment(id = "boat", tag = "LB-001"))
        equipmentDao.upsert(Fixtures.equipment(id = "boat-fe", tag = "LB-001-FE", parentId = "boat"))
        equipmentDao.upsert(
            Fixtures.equipment(id = "inbox", tag = "FE-INBOX-001", deckId = null, zoneId = null),
        )
        equipmentDao.upsert(
            Fixtures.equipment(id = "gone", tag = "LB-001-HRU", parentId = "boat", deletedAt = 1L),
        )

        assertThat(equipmentDao.observeChildren("boat").first().map { it.id }).containsExactly("boat-fe")
        assertThat(equipmentDao.observeUnplaced(Fixtures.VESSEL_ID).first().map { it.id })
            .containsExactly("inbox")
        assertThat(equipmentDao.observeByDeck(Fixtures.DECK_ID).first().map { it.id })
            .containsExactly("boat", "boat-fe")
    }

    @Test
    fun `setCondition writes the grade and the timestamp in one statement`() = runTest {
        equipmentDao.upsert(Fixtures.equipment(id = "eq-1"))

        equipmentDao.setCondition("eq-1", ConditionGrade.DEFECTIVE, atMillis = 1_700_002_000_000L)

        val stored = equipmentDao.getById("eq-1")
        assertThat(stored?.condition).isEqualTo(ConditionGrade.DEFECTIVE)
        assertThat(stored?.conditionSetAt).isEqualTo(1_700_002_000_000L)
        assertThat(stored?.updatedAt).isEqualTo(1_700_002_000_000L)
    }

    @Test
    fun `move repositions an item and can send it back to the inbox`() = runTest {
        equipmentDao.upsert(Fixtures.equipment(id = "eq-1"))

        equipmentDao.move("eq-1", Fixtures.DECK_ID, null, 0.9f, 0.1f, atMillis = 2L)
        val moved = equipmentDao.getById("eq-1")
        assertThat(moved?.posX).isEqualTo(0.9f)
        assertThat(moved?.posY).isEqualTo(0.1f)
        assertThat(moved?.zoneId).isNull()

        equipmentDao.move("eq-1", null, null, 0.5f, 0.5f, atMillis = 3L)
        assertThat(equipmentDao.observeUnplaced(Fixtures.VESSEL_ID).first().map { it.id })
            .containsExactly("eq-1")
    }

    @Test
    fun `setNextDue denormalises the due state the plan view colours markers from`() = runTest {
        equipmentDao.upsert(Fixtures.equipment(id = "eq-1", nextDueDate = null))

        equipmentDao.setNextDue("eq-1", nextDueDate = 20_450L, nextDueTaskKey = "FE_ANNUAL_SERVICE", atMillis = 4L)

        val stored = equipmentDao.getById("eq-1")
        assertThat(stored?.nextDueDate).isEqualTo(20_450L)
        assertThat(stored?.nextDueTaskKey).isEqualTo("FE_ANNUAL_SERVICE")
        assertThat(equipmentDao.observeDueOnOrBefore(Fixtures.VESSEL_ID, 20_500L).first().map { it.id })
            .containsExactly("eq-1")
        assertThat(equipmentDao.observeDueOnOrBefore(Fixtures.VESSEL_ID, 20_000L).first()).isEmpty()
    }

    @Test
    fun `nextTagNumber returns one past the highest numeric suffix for the prefix`() = runTest {
        assertThat(equipmentDao.nextTagNumber(Fixtures.VESSEL_ID, "FE-UD-")).isEqualTo(1)

        equipmentDao.upsert(Fixtures.equipment(id = "e1", tag = "FE-UD-001"))
        equipmentDao.upsert(Fixtures.equipment(id = "e2", tag = "FE-UD-007"))
        equipmentDao.upsert(Fixtures.equipment(id = "e3", tag = "FE-UD-12"))
        // Different prefix — must not influence the FE-UD- series.
        equipmentDao.upsert(Fixtures.equipment(id = "e4", tag = "LB-UD-099"))
        // Non-numeric tail — must be ignored rather than parsed as 0.
        equipmentDao.upsert(Fixtures.equipment(id = "e5", tag = "FE-UD-SPARE"))
        equipmentDao.upsert(Fixtures.equipment(id = "e6", tag = "FE-UD-3A"))
        // Exactly the prefix, no suffix at all.
        equipmentDao.upsert(Fixtures.equipment(id = "e7", tag = "FE-UD-"))

        assertThat(equipmentDao.maxTagSuffix(Fixtures.VESSEL_ID, "FE-UD-")).isEqualTo(12)
        assertThat(equipmentDao.nextTagNumber(Fixtures.VESSEL_ID, "FE-UD-")).isEqualTo(13)
        assertThat(equipmentDao.nextTagNumber(Fixtures.VESSEL_ID, "LB-UD-")).isEqualTo(100)
    }

    @Test
    fun `a soft-deleted tag frees its number and another vessel keeps its own series`() = runTest {
        database.vesselDao().upsert(Fixtures.vessel(id = "v2", name = "MV Two", isActive = false))

        equipmentDao.upsert(Fixtures.equipment(id = "e1", tag = "FE-UD-005"))
        equipmentDao.upsert(Fixtures.equipment(id = "e2", tag = "FE-UD-009", deletedAt = 1L))
        equipmentDao.upsert(
            Fixtures.equipment(id = "e3", vesselId = "v2", deckId = null, zoneId = null, tag = "FE-UD-050"),
        )

        assertThat(equipmentDao.nextTagNumber(Fixtures.VESSEL_ID, "FE-UD-")).isEqualTo(6)
        assertThat(equipmentDao.nextTagNumber("v2", "FE-UD-")).isEqualTo(51)
    }

    @Test
    fun `a prefix containing LIKE wildcards matches literally`() = runTest {
        equipmentDao.upsert(Fixtures.equipment(id = "e1", tag = "FE%UD-004"))
        equipmentDao.upsert(Fixtures.equipment(id = "e2", tag = "FEXUD-900"))

        assertThat(equipmentDao.nextTagNumber(Fixtures.VESSEL_ID, "FE%UD-")).isEqualTo(5)
    }

    @Test
    fun `categories are replaced wholesale and observed back`() = runTest {
        equipmentDao.upsert(Fixtures.equipment(id = "eq-1"))
        database.categoryDao().upsertAll(
            listOf(
                Fixtures.category(id = "cat-a"),
                Fixtures.category(id = "cat-b"),
                Fixtures.category(id = "cat-c"),
            ),
        )

        equipmentDao.setCategories("eq-1", listOf("cat-a", "cat-b"))
        assertThat(equipmentDao.observeCategoryIds("eq-1").first()).containsExactly("cat-a", "cat-b")
        assertThat(equipmentDao.observeByCategory("cat-a").first().map { it.id }).containsExactly("eq-1")

        equipmentDao.setCategories("eq-1", listOf("cat-c"))
        assertThat(equipmentDao.observeCategoryIds("eq-1").first()).containsExactly("cat-c")
        assertThat(equipmentDao.observeByCategory("cat-a").first()).isEmpty()

        equipmentDao.setCategories("eq-1", emptyList())
        assertThat(equipmentDao.observeCategoryIds("eq-1").first()).isEmpty()
    }
}
