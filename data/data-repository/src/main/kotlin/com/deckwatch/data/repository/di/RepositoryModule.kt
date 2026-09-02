package com.deckwatch.data.repository.di

import com.deckwatch.core.common.DefaultDispatcherProvider
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.data.repository.DemoVessel
import com.deckwatch.data.repository.DemoVesselInstaller
import com.deckwatch.data.repository.EquipmentRepositoryImpl
import com.deckwatch.data.repository.InspectionRepositoryImpl
import com.deckwatch.data.repository.MaintenanceRepositoryImpl
import com.deckwatch.data.repository.ReferenceRepositoryImpl
import com.deckwatch.data.repository.SystemTimeSource
import com.deckwatch.data.repository.TimeSource
import com.deckwatch.data.repository.VesselRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/**
 * Binds the five repository interfaces of `core-common` to their implementations here — the seam
 * every feature module injects across (§3's `UI State -> ViewModel -> UseCase -> Repository -> DAO`).
 *
 * The DAOs and the database itself come from `core-database`'s own modules, and the settings store
 * from `core-datastore`'s; this module adds only the bindings that did not exist anywhere yet.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVesselRepository(impl: VesselRepositoryImpl): VesselRepository

    @Binds
    @Singleton
    abstract fun bindEquipmentRepository(impl: EquipmentRepositoryImpl): EquipmentRepository

    @Binds
    @Singleton
    abstract fun bindMaintenanceRepository(impl: MaintenanceRepositoryImpl): MaintenanceRepository

    @Binds
    @Singleton
    abstract fun bindInspectionRepository(impl: InspectionRepositoryImpl): InspectionRepository

    @Binds
    @Singleton
    abstract fun bindReferenceRepository(impl: ReferenceRepositoryImpl): ReferenceRepository

    @Binds
    @Singleton
    abstract fun bindDemoVessel(impl: DemoVesselInstaller): DemoVessel

    /** The system clock. A test swaps this one binding for a [com.deckwatch.data.repository.FixedTimeSource]. */
    @Binds
    @Singleton
    abstract fun bindTimeSource(impl: SystemTimeSource): TimeSource
}

/**
 * The two collaborators the repositories are built on that are plain classes rather than
 * interfaces with a single implementation.
 *
 * [DispatcherProvider] is provided once for the whole app: `main` is the real
 * [Dispatchers.Main] here, and a test graph swaps this one binding for a
 * `DefaultDispatcherProvider(main = UnconfinedTestDispatcher())`. The default `io` and `default`
 * values are the standard pools.
 *
 * [DueEngine] is stateless and reads "today" from the system clock; a test constructs its own with
 * a fixed clock rather than replacing this binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryProvidersModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider =
        DefaultDispatcherProvider(main = Dispatchers.Main)

    @Provides
    @Singleton
    fun provideDueEngine(): DueEngine = DueEngine()
}
