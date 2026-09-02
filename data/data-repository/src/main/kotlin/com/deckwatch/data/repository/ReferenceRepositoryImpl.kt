package com.deckwatch.data.repository

import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.database.dao.EquipmentTypeDao
import com.deckwatch.core.database.dao.RegulationCardDao
import com.deckwatch.core.database.dao.RoundTemplateDao
import com.deckwatch.core.database.dao.UserNoteDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.SymbolInfo
import com.deckwatch.core.model.UserNote
import com.deckwatch.data.seed.SeedDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The reference library — MASTER_PROMPT §8, §9, §19.
 *
 * Two different kinds of content sit behind one interface, and the split is deliberate:
 *
 * * **Regulation cards, the equipment type catalogue, round templates and the user's own notes**
 *   live in Room. They are searched (§8.1), they are extended by the user (a custom equipment type
 *   is the mandatory escape hatch of §9.2, "MY NOTES" is a whole section of the Notes tab), and
 *   they must survive a content-version bump with the user's additions intact — all of which needs
 *   a table. `SeedInitializer` fills them.
 * * **Plan presets and symbol metadata** are served **straight from `SeedDataSource`**, with no
 *   table at all. Nothing writes them, nothing searches them in SQL, and both are small, fixed
 *   lists keyed by a constant (§6.3's six deck outlines, §10.3's symbol keys) whose artwork lives
 *   in `core-designsystem` as Kotlin anyway. A table would be a second copy of a constant that can
 *   drift from the asset on a content bump. They are parsed once and memoised for the process.
 */
@Singleton
class ReferenceRepositoryImpl @Inject constructor(
    private val regulationCardDao: RegulationCardDao,
    private val equipmentTypeDao: EquipmentTypeDao,
    private val roundTemplateDao: RoundTemplateDao,
    private val userNoteDao: UserNoteDao,
    private val seedDataSource: SeedDataSource,
    private val dispatchers: DispatcherProvider,
) : ReferenceRepository {

    private val planPresets = SuspendMemo { seedDataSource.loadPlanPresets() }
    private val symbols = SuspendMemo { seedDataSource.loadSymbols() }

    override fun observeRegulationCards(): Flow<List<RegulationCard>> =
        regulationCardDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getRegulationCard(refKey: String): RegulationCard? =
        withContext(dispatchers.io) { regulationCardDao.getByKey(refKey)?.toModel() }

    override fun searchRegulationCards(query: String): Flow<List<RegulationCard>> =
        regulationCardDao.search(query).map { rows -> rows.map { it.toModel() } }

    override fun observeEquipmentTypes(): Flow<List<EquipmentType>> =
        equipmentTypeDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getEquipmentType(typeKey: String): EquipmentType? =
        withContext(dispatchers.io) { equipmentTypeDao.getByKey(typeKey)?.toModel() }

    /**
     * The user's own type — §9.2's escape hatch. `isUserDefined` is forced on so that re-seeding
     * can never overwrite or delete it (§8.1, `SeedInitializer`).
     */
    override suspend fun upsertUserDefinedType(type: EquipmentType) = withContext(dispatchers.io) {
        equipmentTypeDao.upsert(type.copy(isUserDefined = true).toEntity())
    }

    override fun observeRoundTemplates(): Flow<List<RoundTemplate>> =
        roundTemplateDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observePlanPresets(): Flow<List<PlanPreset>> =
        flow { emit(planPresets.get()) }.flowOn(dispatchers.io)

    override fun observeSymbols(): Flow<List<SymbolInfo>> =
        flow { emit(symbols.get()) }.flowOn(dispatchers.io)

    override fun observeUserNotes(): Flow<List<UserNote>> =
        userNoteDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertUserNote(note: UserNote) = withContext(dispatchers.io) {
        userNoteDao.upsert(note.toEntity())
    }

    override suspend fun deleteUserNote(id: String) = withContext(dispatchers.io) {
        userNoteDao.deleteById(id)
    }
}

/**
 * A suspending `lazy`: [compute] runs at most once, and concurrent callers wait for that one run
 * rather than starting their own. Parsing an asset twice would be wasteful but harmless; parsing it
 * on every collection of a `Flow` would not be.
 */
internal class SuspendMemo<T : Any>(private val compute: suspend () -> T) {

    private val mutex = Mutex()

    @Volatile
    private var value: T? = null

    suspend fun get(): T = value ?: mutex.withLock {
        value ?: compute().also { value = it }
    }
}
