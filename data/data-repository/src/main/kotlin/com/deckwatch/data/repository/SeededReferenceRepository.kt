package com.deckwatch.data.repository

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ReferenceRepository] over the two places reference content lives — MASTER_PROMPT §8, §9, §19.
 *
 * Regulation cards, the equipment-type catalogue, round templates and the user's own notes are
 * database tables: they are searched, they carry user-defined rows alongside the bundled ones, and
 * re-seeding replaces only the bundled half. Deck-plan presets and the symbol library are neither
 * searched nor editable, so they stay read-only assets and are never copied into the database —
 * they are read once per process and cached.
 */
@Singleton
class SeededReferenceRepository @Inject constructor(
    private val regulationCardDao: RegulationCardDao,
    private val equipmentTypeDao: EquipmentTypeDao,
    private val roundTemplateDao: RoundTemplateDao,
    private val userNoteDao: UserNoteDao,
    private val seed: SeedDataSource,
) : ReferenceRepository {

    private val planPresets = SuspendLazy { seed.loadPlanPresets() }
    private val symbols = SuspendLazy { seed.loadSymbols() }

    override fun observeRegulationCards(): Flow<List<RegulationCard>> =
        regulationCardDao.observeAll().mapRows { it.toModel() }

    override suspend fun getRegulationCard(refKey: String): RegulationCard? =
        regulationCardDao.getByKey(refKey)?.toModel()

    /** A blank query is "show everything", matching the Notes-tab search box when it is empty. */
    override fun searchRegulationCards(query: String): Flow<List<RegulationCard>> {
        val needle = query.trim()
        val rows = if (needle.isEmpty()) {
            regulationCardDao.observeAll()
        } else {
            regulationCardDao.search(needle)
        }
        return rows.mapRows { it.toModel() }
    }

    override fun observeEquipmentTypes(): Flow<List<EquipmentType>> =
        equipmentTypeDao.observeAll().mapRows { it.toModel() }

    override suspend fun getEquipmentType(typeKey: String): EquipmentType? =
        equipmentTypeDao.getByKey(typeKey)?.toModel()

    /** The user-defined-type escape hatch of §9.2; the flag is set here so re-seeding spares it. */
    override suspend fun upsertUserDefinedType(type: EquipmentType) =
        equipmentTypeDao.upsert(type.copy(isUserDefined = true).toEntity())

    override fun observeRoundTemplates(): Flow<List<RoundTemplate>> =
        roundTemplateDao.observeAll().mapRows { it.toModel() }

    override fun observePlanPresets(): Flow<List<PlanPreset>> = flow { emit(planPresets.get()) }

    override fun observeSymbols(): Flow<List<SymbolInfo>> = flow { emit(symbols.get()) }

    override fun observeUserNotes(): Flow<List<UserNote>> =
        userNoteDao.observeAll().mapRows { it.toModel() }

    override suspend fun upsertUserNote(note: UserNote) = userNoteDao.upsert(note.toEntity())

    override suspend fun deleteUserNote(id: String) = userNoteDao.deleteById(id)
}

/** Map every row of an observed table to its domain model, keeping the DAO's ordering. */
internal fun <E, M> Flow<List<E>>.mapRows(transform: (E) -> M): Flow<List<M>> =
    map { rows -> rows.map(transform) }

/** Reads its value once, however many collectors ask for it at the same time. */
internal class SuspendLazy<T>(private val supplier: suspend () -> T) {

    private val mutex = Mutex()

    @Volatile
    private var value: T? = null

    suspend fun get(): T = value ?: mutex.withLock {
        value ?: supplier().also { value = it }
    }
}
