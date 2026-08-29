package com.deckwatch.data.repository

import com.deckwatch.core.database.TransactionRunner
import com.deckwatch.core.database.dao.EquipmentTypeDao
import com.deckwatch.core.database.dao.RegulationCardDao
import com.deckwatch.core.database.dao.RoundTemplateDao
import com.deckwatch.core.database.dao.TaskDefinitionDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.seed.SeedDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports the bundled reference content into the database — MASTER_PROMPT §19.
 *
 * The app ships its regulation cards, equipment catalogue, task definitions and round templates as
 * assets, but the Notes tab searches them and the officer adds rows of their own beside them, so
 * they have to live in tables. This class is what puts them there.
 *
 * It runs on every start and does nothing unless the bundled content is newer than what the
 * database already holds, which is the version recorded in
 * [com.deckwatch.core.datastore.UserPreferences.seededContentVersion]. When it does run, the
 * bundled rows are replaced and the user's own are kept: `deleteBundled()` spares user-defined
 * equipment types and task definitions (§9.2), and user notes are never touched at all (§8.1).
 *
 * The whole import is one transaction, so a kill mid-write leaves the previous content intact
 * rather than a half-replaced catalogue.
 */
@Singleton
class ContentSeeder @Inject constructor(
    private val seed: SeedDataSource,
    private val equipmentTypeDao: EquipmentTypeDao,
    private val taskDefinitionDao: TaskDefinitionDao,
    private val regulationCardDao: RegulationCardDao,
    private val roundTemplateDao: RoundTemplateDao,
    private val preferences: UserPreferencesRepository,
    private val transaction: TransactionRunner,
) {

    /**
     * Import the bundled content if the database does not already hold this version of it.
     *
     * @return true when content was imported, false when it was already up to date.
     */
    suspend fun seedIfNeeded(): Boolean {
        val bundle = seed.loadAll()
        // The cards carry the content version (§8.2); the newest of them versions the bundle.
        val bundledVersion = bundle.regulationCards.maxOfOrNull { it.contentVersion } ?: 0
        if (preferences.get().seededContentVersion >= bundledVersion) return false

        transaction {
            equipmentTypeDao.deleteBundled()
            taskDefinitionDao.deleteBundled()
            regulationCardDao.deleteAll()
            roundTemplateDao.deleteAll()

            equipmentTypeDao.upsertAll(bundle.equipmentTypes.map { it.toEntity() })
            taskDefinitionDao.upsertAll(bundle.taskDefinitions.map { it.toEntity() })
            regulationCardDao.upsertAll(bundle.regulationCards.map { it.toEntity() })
            roundTemplateDao.upsertAll(bundle.roundTemplates.map { it.toEntity() })
        }
        preferences.setSeededContentVersion(bundledVersion)
        return true
    }
}
