package com.deckwatch.core.database.di

import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.RoomTransactionRunner
import com.deckwatch.core.database.TransactionRunner
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Exposes every DAO so `data-repository` can inject the ones it needs without depending on the
 * database type itself. DAOs are not `@Singleton`: Room already caches one instance per database.
 */
@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    /** The cross-DAO transaction seam of §11.1 — see [TransactionRunner]. */
    @Provides
    @Singleton
    fun provideTransactionRunner(database: DeckWatchDatabase): TransactionRunner =
        RoomTransactionRunner(database)

    @Provides
    fun provideVesselDao(database: DeckWatchDatabase): VesselDao = database.vesselDao()

    @Provides
    fun provideDeckDao(database: DeckWatchDatabase): DeckDao = database.deckDao()

    @Provides
    fun provideZoneDao(database: DeckWatchDatabase): ZoneDao = database.zoneDao()

    @Provides
    fun provideCategoryDao(database: DeckWatchDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideEquipmentDao(database: DeckWatchDatabase): EquipmentDao = database.equipmentDao()

    @Provides
    fun provideTaskDefinitionDao(database: DeckWatchDatabase): TaskDefinitionDao =
        database.taskDefinitionDao()

    @Provides
    fun provideTaskInstanceDao(database: DeckWatchDatabase): TaskInstanceDao =
        database.taskInstanceDao()

    @Provides
    fun provideRoundDao(database: DeckWatchDatabase): RoundDao = database.roundDao()

    @Provides
    fun provideRoundItemDao(database: DeckWatchDatabase): RoundItemDao = database.roundItemDao()

    @Provides
    fun provideDeficiencyDao(database: DeckWatchDatabase): DeficiencyDao = database.deficiencyDao()

    @Provides
    fun provideEquipmentTypeDao(database: DeckWatchDatabase): EquipmentTypeDao =
        database.equipmentTypeDao()

    @Provides
    fun provideRegulationCardDao(database: DeckWatchDatabase): RegulationCardDao =
        database.regulationCardDao()

    @Provides
    fun provideRoundTemplateDao(database: DeckWatchDatabase): RoundTemplateDao =
        database.roundTemplateDao()

    @Provides
    fun provideUserNoteDao(database: DeckWatchDatabase): UserNoteDao = database.userNoteDao()
}
