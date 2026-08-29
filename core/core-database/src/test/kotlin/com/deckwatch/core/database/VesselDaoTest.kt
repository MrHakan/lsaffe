package com.deckwatch.core.database

import android.database.sqlite.SQLiteConstraintException
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.PlanShape
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VesselDaoTest : DeckWatchDatabaseTest() {

    private val vesselDao get() = database.vesselDao()
    private val deckDao get() = database.deckDao()
    private val zoneDao get() = database.zoneDao()
    private val categoryDao get() = database.categoryDao()

    @Test
    fun `vessel round-trips through the dao and the domain mappers`() = runTest {
        val original = Fixtures.vessel()
        vesselDao.upsert(original)

        val stored = vesselDao.observeAll().first().single()
        assertThat(stored).isEqualTo(original)
        // Entity -> domain -> entity must be lossless: the exporter relies on it (§13).
        assertThat(stored.toModel().toEntity()).isEqualTo(original)

        vesselDao.upsert(original.copy(name = "MV Renamed", grossTonnage = 41_000))
        val updated = vesselDao.getById(Fixtures.VESSEL_ID)
        assertThat(updated?.name).isEqualTo("MV Renamed")
        assertThat(updated?.grossTonnage).isEqualTo(41_000)

        vesselDao.deleteById(Fixtures.VESSEL_ID)
        assertThat(vesselDao.observeAll().first()).isEmpty()
    }

    @Test
    fun `exactly one vessel is active after a switch`() = runTest {
        vesselDao.upsert(Fixtures.vessel(id = "v1", name = "MV One", isActive = true))
        vesselDao.upsert(Fixtures.vessel(id = "v2", name = "MV Two", isActive = false))

        vesselDao.setActive("v2")

        assertThat(vesselDao.observeActive().first()?.id).isEqualTo("v2")
        assertThat(vesselDao.observeAll().first().filter { it.isActive }).hasSize(1)
    }

    @Test
    fun `observeActive emits null when no vessel is selected`() = runTest {
        vesselDao.upsert(Fixtures.vessel(isActive = false))

        assertThat(vesselDao.observeActive().first()).isNull()
    }

    @Test
    fun `decks are observed highest level first and carry their plan geometry`() = runTest {
        vesselDao.upsert(Fixtures.vessel())
        deckDao.upsert(Fixtures.deck(id = "d0", levelIndex = 0, name = "Upper Deck"))
        deckDao.upsert(Fixtures.deck(id = "dUp", levelIndex = 10, name = "A Deck"))
        deckDao.upsert(Fixtures.deck(id = "dDown", levelIndex = -10, name = "Engine Room 2nd Flat"))

        val stack = deckDao.observeByVessel(Fixtures.VESSEL_ID).first()
        assertThat(stack.map { it.id }).containsExactly("dUp", "d0", "dDown").inOrder()
        assertThat(deckDao.observeByVesselBottomUp(Fixtures.VESSEL_ID).first().map { it.id })
            .containsExactly("dDown", "d0", "dUp").inOrder()

        val plan = stack.first().plan
        assertThat(plan.shape).isEqualTo(PlanShape.CUSTOM_POLYGON)
        assertThat(plan.polygon).hasSize(3)
        assertThat(plan.backgroundImageUri).isEqualTo("content://ga-plan/upper-deck")
    }

    @Test
    fun `deck level bounds drive the add-above and add-below mechanic`() = runTest {
        vesselDao.upsert(Fixtures.vessel())
        assertThat(deckDao.maxLevelIndex(Fixtures.VESSEL_ID)).isNull()

        deckDao.upsert(Fixtures.deck(id = "d0", levelIndex = 0))
        deckDao.upsert(Fixtures.deck(id = "dUp", levelIndex = 10))
        deckDao.upsert(Fixtures.deck(id = "dDown", levelIndex = -10))

        assertThat(deckDao.maxLevelIndex(Fixtures.VESSEL_ID)).isEqualTo(10)
        assertThat(deckDao.minLevelIndex(Fixtures.VESSEL_ID)).isEqualTo(-10)
        assertThat(deckDao.countForVessel(Fixtures.VESSEL_ID)).isEqualTo(3)
        assertThat(deckDao.getByLevelIndex(Fixtures.VESSEL_ID, 10)?.id).isEqualTo("dUp")
    }

    @Test
    fun `two decks cannot share a level index on one vessel`() = runTest {
        vesselDao.upsert(Fixtures.vessel())
        deckDao.upsert(Fixtures.deck(id = "d0", levelIndex = 0))

        val error = runCatching {
            deckDao.upsert(Fixtures.deck(id = "d-clash", levelIndex = 0, name = "Clashing deck"))
        }.exceptionOrNull()
        assertThat(error).isInstanceOf(SQLiteConstraintException::class.java)

        assertThat(deckDao.observeByVessel(Fixtures.VESSEL_ID).first()).hasSize(1)
    }

    @Test
    fun `the same level index is free on a different vessel`() = runTest {
        vesselDao.upsert(Fixtures.vessel(id = "v1", isActive = true))
        vesselDao.upsert(Fixtures.vessel(id = "v2", name = "MV Two", isActive = false))
        deckDao.upsert(Fixtures.deck(id = "d1", vesselId = "v1", levelIndex = 0))
        deckDao.upsert(Fixtures.deck(id = "d2", vesselId = "v2", levelIndex = 0))

        assertThat(deckDao.observeByVessel("v1").first()).hasSize(1)
        assertThat(deckDao.observeByVessel("v2").first()).hasSize(1)
    }

    @Test
    fun `deleting a vessel cascades to its decks`() = runTest {
        vesselDao.upsert(Fixtures.vessel())
        deckDao.upsert(Fixtures.deck(id = "d0", levelIndex = 0))
        deckDao.upsert(Fixtures.deck(id = "dUp", levelIndex = 10))
        assertThat(deckDao.countForVessel(Fixtures.VESSEL_ID)).isEqualTo(2)

        vesselDao.deleteById(Fixtures.VESSEL_ID)

        assertThat(deckDao.countForVessel(Fixtures.VESSEL_ID)).isEqualTo(0)
    }

    @Test
    fun `a deck cannot reference a vessel that does not exist`() = runTest {
        val error = runCatching {
            deckDao.upsert(Fixtures.deck(vesselId = "no-such-vessel"))
        }.exceptionOrNull()
        assertThat(error).isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun `zone round-trips and is scoped to its deck`() = runTest {
        vesselDao.upsert(Fixtures.vessel())
        deckDao.upsert(Fixtures.deck())
        val zone = Fixtures.zone()
        zoneDao.upsert(zone)

        val stored = zoneDao.observeByDeck(Fixtures.DECK_ID).first().single()
        assertThat(stored).isEqualTo(zone)
        assertThat(stored.polygon).hasSize(3)
        assertThat(stored.toModel().toEntity()).isEqualTo(zone)

        zoneDao.upsert(zone.copy(name = "Pump Room"))
        assertThat(zoneDao.getById("zone-1")?.name).isEqualTo("Pump Room")

        zoneDao.deleteById("zone-1")
        assertThat(zoneDao.observeByDeck(Fixtures.DECK_ID).first()).isEmpty()
    }

    @Test
    fun `global categories are returned alongside the vessel's own`() = runTest {
        vesselDao.upsert(Fixtures.vessel())
        val global = Fixtures.category(id = "global", vesselId = null)
        val local = Fixtures.category(id = "local", vesselId = Fixtures.VESSEL_ID)
        val otherShip = Fixtures.category(id = "other", vesselId = "some-other-vessel")
        categoryDao.upsertAll(listOf(global, local, otherShip))

        val forVessel = categoryDao.observeForVessel(Fixtures.VESSEL_ID).first()
        assertThat(forVessel.map { it.id }).containsExactly("global", "local")

        // A null vesselId asks only for the globals.
        assertThat(categoryDao.observeForVessel(null).first().map { it.id }).containsExactly("global")
        assertThat(categoryDao.observeGlobal().first().single().toModel().toEntity()).isEqualTo(global)

        categoryDao.deleteById("local")
        assertThat(categoryDao.observeForVessel(Fixtures.VESSEL_ID).first().map { it.id })
            .containsExactly("global")
    }
}
