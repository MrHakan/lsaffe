package com.deckwatch.core.testing

import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.Vessel

/**
 * All five repository fakes wired together, sharing one in-memory world.
 *
 * The maintenance fake drives a real [DueEngine] against the equipment, catalogue and vessel
 * stores held here, so a test that seeds a vessel and calls
 * [com.deckwatch.core.common.repository.MaintenanceRepository.recomputeDueForVessel] gets the same
 * task instances the app would produce (§11.2).
 *
 * @param today the epoch-day the due engine treats as today. Defaults to [TestData.referenceDay]
 *   so fixtures do not drift with the wall clock; pass [Dates.todayEpochDay] for a live clock.
 */
class FakeRepositories(
    today: () -> Long = { TestData.referenceDay },
    leadTimeDays: Int = DueEngine.DEFAULT_LEAD_TIME_DAYS,
    clock: () -> Long = { TestData.referenceMillis },
) {
    val vessels: FakeVesselRepository = FakeVesselRepository(clock = clock)
    val equipment: FakeEquipmentRepository = FakeEquipmentRepository(clock = clock)
    val reference: FakeReferenceRepository = FakeReferenceRepository()
    val inspections: FakeInspectionRepository = FakeInspectionRepository()
    val maintenance: FakeMaintenanceRepository = FakeMaintenanceRepository(
        equipment = equipment,
        reference = reference,
        vessels = vessels,
        engine = DueEngine(today),
        leadTimeDays = leadTimeDays,
        today = today,
        clock = clock,
    )

    /** Seed a vessel, its decks, its equipment and the catalogue / task definitions in one call. */
    @Suppress("LongParameterList") // A seeding helper; every list defaults to empty.
    suspend fun seed(
        vessel: Vessel? = null,
        decks: List<Deck> = emptyList(),
        equipmentItems: List<Equipment> = emptyList(),
        types: List<EquipmentType> = emptyList(),
        definitions: List<TaskDefinition> = emptyList(),
        recomputeDue: Boolean = true,
    ) {
        vessel?.let { vessels.upsertVessel(it) }
        decks.forEach { vessels.upsertDeck(it) }
        types.forEach { reference.seedEquipmentType(it) }
        definitions.forEach { maintenance.upsertTaskDefinition(it) }
        equipmentItems.forEach { equipment.upsertEquipment(it) }
        if (recomputeDue) {
            equipmentItems.map { it.vesselId }.distinct().forEach { maintenance.recomputeDueForVessel(it) }
        }
    }
}
