package com.deckwatch.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.withTransaction
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.dao.EquipmentTypeDao
import com.deckwatch.core.database.dao.RegulationCardDao
import com.deckwatch.core.database.dao.RoundTemplateDao
import com.deckwatch.core.database.dao.TaskDefinitionDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.data.seed.SeedDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads the bundled reference content into Room on first run, and again whenever the bundled
 * content version moves — MASTER_PROMPT §8.1, §19.
 *
 * ### What "preserving user notes" means here (§8.1)
 *
 * The user's own material is never in the path of a re-seed:
 *
 * * `user_notes` is not read or written by this class at all. Not once.
 * * A row the user created — `isUserDefined = true` on an equipment type or a task definition — is
 *   skipped even if the bundled content happens to use the same key, so a custom type can never be
 *   silently replaced by a catalogue entry.
 * * Nothing is deleted. The DAOs offer `deleteBundled()` / `deleteAll()`; this class does not call
 *   them. Re-seeding is an upsert of the bundled keys, which means a card the officer has bookmarked
 *   or attached a note to keeps its `refKey` and therefore keeps the attachment. A card withdrawn
 *   from a later content release stays in the database rather than taking the officer's note with
 *   it; that is the C10 trade — a stale card is recoverable, a deleted note is not.
 *
 * ### Idempotency
 *
 * [ensureSeeded] is safe to call on every cold start and from several callers at once: a mutex
 * serialises it in-process, the stored version short-circuits the common case, and the writes are
 * upserts by primary key inside one transaction, so a repeat run leaves row counts unchanged.
 *
 * The version lives in a small private `SharedPreferences` file rather than in
 * `UserPreferencesRepository`: it is not a user setting, nothing in Settings shows it, and keeping
 * it out of the settings store means "reset settings" cannot make the app believe it has never
 * seeded. It is also read/written outside any DataStore coroutine, so it can be consulted while a
 * database transaction is open.
 */
@Singleton
class SeedInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DeckWatchDatabase,
    private val seedDataSource: SeedDataSource,
    private val equipmentTypeDao: EquipmentTypeDao,
    private val taskDefinitionDao: TaskDefinitionDao,
    private val regulationCardDao: RegulationCardDao,
    private val roundTemplateDao: RoundTemplateDao,
    private val dispatchers: DispatcherProvider,
) {

    private val mutex = Mutex()

    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** The content version currently stored in the database, 0 when nothing has been seeded. */
    val installedContentVersion: Int get() = prefs.getInt(KEY_CONTENT_VERSION, NEVER_SEEDED)

    /**
     * Seed if needed. Returns true when content was written, false when the database was already
     * at [CONTENT_VERSION] and had content.
     */
    suspend fun ensureSeeded(): Boolean = withContext(dispatchers.io) {
        mutex.withLock {
            if (installedContentVersion == CONTENT_VERSION && hasContent()) return@withLock false
            seed(
                types = seedDataSource.loadEquipmentTypes(),
                definitions = seedDataSource.loadTaskDefinitions(),
                cards = seedDataSource.loadRegulationCards(),
                templates = seedDataSource.loadRoundTemplates(),
            )
            true
        }
    }

    /**
     * The same write path with the content supplied by the caller — used by tests, and by an
     * eventual "install a content pack" path, so the merge rules above have exactly one
     * implementation.
     */
    suspend fun seed(
        types: List<EquipmentType>,
        definitions: List<TaskDefinition>,
        cards: List<RegulationCard>,
        templates: List<RoundTemplate>,
    ) {
        database.withTransaction {
            val userTypeKeys = equipmentTypeDao.getAll()
                .filter { it.isUserDefined }
                .mapTo(HashSet()) { it.typeKey }
            val userTaskKeys = taskDefinitionDao.getAll()
                .filter { it.isUserDefined }
                .mapTo(HashSet()) { it.key }

            equipmentTypeDao.upsertAll(
                types.filterNot { it.typeKey in userTypeKeys }
                    .map { it.copy(isUserDefined = false).toEntity() },
            )
            taskDefinitionDao.upsertAll(
                definitions.filterNot { it.key in userTaskKeys }
                    .map { it.copy(isUserDefined = false).toEntity() },
            )
            regulationCardDao.upsertAll(cards.map { it.toEntity() })
            roundTemplateDao.upsertAll(templates.map { it.toEntity() })
        }
        prefs.edit { putInt(KEY_CONTENT_VERSION, CONTENT_VERSION) }
    }

    /**
     * Guards the case where the version says "seeded" but the tables are empty — a restore from a
     * settings-only backup, or a database file replaced underneath the app. Task definitions are
     * the cheapest of the four tables to read whole.
     */
    private suspend fun hasContent(): Boolean = taskDefinitionDao.getAll().isNotEmpty()

    companion object {
        /**
         * The bundled content version. **Bump this whenever the JSON under `data-seed/assets/seed`
         * changes** — that is what triggers the migrating re-seed of §8.1 on the next cold start.
         */
        const val CONTENT_VERSION: Int = 1

        internal const val PREFS_NAME: String = "deckwatch_seed"
        internal const val KEY_CONTENT_VERSION: String = "seed_content_version"
        private const val NEVER_SEEDED: Int = 0
    }
}
