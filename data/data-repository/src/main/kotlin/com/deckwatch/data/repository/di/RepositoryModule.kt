package com.deckwatch.data.repository.di

import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.data.repository.AppClock
import com.deckwatch.data.repository.IdFactory
import com.deckwatch.data.repository.RoomEquipmentRepository
import com.deckwatch.data.repository.RoomInspectionRepository
import com.deckwatch.data.repository.RoomMaintenanceRepository
import com.deckwatch.data.repository.RoomVesselRepository
import com.deckwatch.data.repository.SeededReferenceRepository
import com.deckwatch.data.repository.SystemAppClock
import com.deckwatch.data.repository.UuidIdFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the repository interfaces of `core-common` to their Room-backed implementations — the
 * whole app above this layer knows only the interfaces, so a screen can be tested against the
 * fakes in `core-testing` without any Android or database machinery.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVesselRepository(impl: RoomVesselRepository): VesselRepository

    @Binds
    @Singleton
    abstract fun bindEquipmentRepository(impl: RoomEquipmentRepository): EquipmentRepository

    @Binds
    @Singleton
    abstract fun bindMaintenanceRepository(impl: RoomMaintenanceRepository): MaintenanceRepository

    @Binds
    @Singleton
    abstract fun bindInspectionRepository(impl: RoomInspectionRepository): InspectionRepository

    @Binds
    @Singleton
    abstract fun bindReferenceRepository(impl: SeededReferenceRepository): ReferenceRepository

    @Binds
    @Singleton
    abstract fun bindIdFactory(impl: UuidIdFactory): IdFactory

    @Binds
    @Singleton
    abstract fun bindAppClock(impl: SystemAppClock): AppClock

    companion object {

        /**
         * The engine reads "today" through the same clock as the repositories, so a test that
         * pins the clock pins the due dates too — §11.
         */
        @Provides
        @Singleton
        fun provideDueEngine(clock: AppClock): DueEngine = DueEngine { clock.todayEpochDay() }
    }
}
