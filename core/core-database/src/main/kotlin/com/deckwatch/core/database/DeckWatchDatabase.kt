package com.deckwatch.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.deckwatch.core.database.converter.DeckWatchTypeConverters
import com.deckwatch.core.database.dao.CategoryDao
import com.deckwatch.core.database.dao.DeckDao
import com.deckwatch.core.database.dao.DeficiencyDao
import com.deckwatch.core.database.dao.EquipmentDao
import com.deckwatch.core.database.dao.EquipmentTypeDao
import com.deckwatch.core.database.dao.RegulationCardDao
import com.deckwatch.core.database.dao.RoundDao
import com.deckwatch.core.database.dao.RoundItemDao
import com.deckwatch.core.database.dao.RoundTemplateDao
import com.deckwatch.core.database.dao.TaskDefinitionDao
import com.deckwatch.core.database.dao.TaskInstanceDao
import com.deckwatch.core.database.dao.UserNoteDao
import com.deckwatch.core.database.dao.VesselDao
import com.deckwatch.core.database.dao.ZoneDao
import com.deckwatch.core.database.entity.CategoryEntity
import com.deckwatch.core.database.entity.DeckEntity
import com.deckwatch.core.database.entity.DeficiencyEntity
import com.deckwatch.core.database.entity.EquipmentCategoryXref
import com.deckwatch.core.database.entity.EquipmentEntity
import com.deckwatch.core.database.entity.EquipmentTypeEntity
import com.deckwatch.core.database.entity.RegulationCardEntity
import com.deckwatch.core.database.entity.RoundEntity
import com.deckwatch.core.database.entity.RoundItemEntity
import com.deckwatch.core.database.entity.RoundTemplateEntity
import com.deckwatch.core.database.entity.TaskDefinitionEntity
import com.deckwatch.core.database.entity.TaskInstanceEntity
import com.deckwatch.core.database.entity.UserNoteEntity
import com.deckwatch.core.database.entity.VesselEntity
import com.deckwatch.core.database.entity.ZoneEntity

/**
 * The single local database — MASTER_PROMPT §6.
 *
 * `exportSchema = true` is not optional here: C10 requires every migration to be tested, and a
 * migration test needs the exported schema JSON of the previous version. The Room Gradle plugin
 * writes those files under `core/core-database/schemas`; **commit them**.
 *
 * The database is encrypted at rest with SQLCipher in production (§18) — see
 * [com.deckwatch.core.database.security.DatabaseKeyProvider] and [createDeckWatchDatabase]. The
 * encryption lives entirely in the open-helper factory, so tests and tooling can open the same
 * schema unencrypted without a second code path.
 */
@Database(
    entities = [
        VesselEntity::class,
        DeckEntity::class,
        ZoneEntity::class,
        CategoryEntity::class,
        EquipmentCategoryXref::class,
        EquipmentEntity::class,
        TaskDefinitionEntity::class,
        TaskInstanceEntity::class,
        RoundEntity::class,
        RoundItemEntity::class,
        DeficiencyEntity::class,
        EquipmentTypeEntity::class,
        RegulationCardEntity::class,
        RoundTemplateEntity::class,
        UserNoteEntity::class,
    ],
    version = DeckWatchDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(DeckWatchTypeConverters::class)
abstract class DeckWatchDatabase : RoomDatabase() {

    abstract fun vesselDao(): VesselDao
    abstract fun deckDao(): DeckDao
    abstract fun zoneDao(): ZoneDao
    abstract fun categoryDao(): CategoryDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun taskDefinitionDao(): TaskDefinitionDao
    abstract fun taskInstanceDao(): TaskInstanceDao
    abstract fun roundDao(): RoundDao
    abstract fun roundItemDao(): RoundItemDao
    abstract fun deficiencyDao(): DeficiencyDao
    abstract fun equipmentTypeDao(): EquipmentTypeDao
    abstract fun regulationCardDao(): RegulationCardDao
    abstract fun roundTemplateDao(): RoundTemplateDao
    abstract fun userNoteDao(): UserNoteDao

    companion object {
        const val VERSION: Int = 1

        /** On-disk file name. Kept stable: it is what a restore looks for. */
        const val NAME: String = "deckwatch.db"
    }
}
