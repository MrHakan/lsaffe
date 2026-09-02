package com.deckwatch.feature.report

import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.DefaultDispatcherProvider
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Writes an import — §13.5 ("be transactional: either the whole import applies or none of it
 * does").
 *
 * ## The transaction guarantee, stated honestly
 *
 * The repository interfaces of `core-common` expose no cross-repository transaction. There is no
 * `withTransaction { }` that spans vessels, equipment, rounds and deficiencies, so a literal
 * all-or-nothing write is not available to this module. What is implemented instead is a
 * **two-phase apply**, and this is exactly what it does and does not promise:
 *
 * **Phase 1 — plan and validate, with the database untouched.**
 * [ImportMerger.plan] builds every write up front, then [ImportMerger.validate] checks that every
 * foreign key in the plan resolves, either to another record in the plan or to a row already in
 * the database. If anything is missing the import is [ImportOutcome.Rejected] and **not one byte
 * is written**. Every malformed, partial or mis-referenced file — which is the entire realistic
 * failure population for a file that arrived over WhatsApp — is caught here, before any write.
 *
 * **Phase 2 — write in dependency order, journalling every change.**
 * Writes go vessel → decks → zones → categories → user types → task definitions → equipment →
 * category links → task instances → rounds → round items → deficiencies → notes, so a referenced
 * row always exists before the row that references it. Each write first records how to undo
 * itself: an *overwrite* remembers the previous record from the in-memory [LocalSnapshot]; a
 * *creation* remembers how to remove the new row.
 *
 * **On a failure in phase 2, the journal is replayed backwards.** Overwrites are restored exactly,
 * from the snapshot. Creations are removed where the repository interface allows it:
 *
 * | Kind | Overwrite | Creation |
 * |---|---|---|
 * | vessel, deck, zone, category, user note | restored exactly | hard-deleted |
 * | equipment | restored exactly | **soft-deleted** — `EquipmentRepository` has no hard delete, so the row survives as a tombstone, invisible everywhere in the app |
 * | category links | restored exactly (the previous id set is re-set) | previous id set is re-set |
 * | user-defined type, task definition, task instance, round, round item, deficiency | restored exactly | **cannot be removed** — these repositories expose only `upsert…` |
 *
 * So the honest statement is: **an import is all-or-nothing against every failure it can detect,
 * and best-effort against a failure that strikes mid-write.** In the second case the outcome is
 * [ImportOutcome.RolledBack], which names in [ImportOutcome.RolledBack.unrecoverable] every row
 * that could not be withdrawn, so the user is told rather than left to discover it. Closing that
 * last gap needs delete methods on `MaintenanceRepository` and `InspectionRepository`, or a
 * transaction boundary on the repository layer — both outside this module.
 */
class ImportApplier(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val inspectionRepository: InspectionRepository,
    private val referenceRepository: ReferenceRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(Dispatchers.Main),
    private val clock: () -> Long = Dates::nowMillis,
) {

    @Inject
    constructor(
        vesselRepository: VesselRepository,
        equipmentRepository: EquipmentRepository,
        maintenanceRepository: MaintenanceRepository,
        inspectionRepository: InspectionRepository,
        referenceRepository: ReferenceRepository,
    ) : this(
        vesselRepository = vesselRepository,
        equipmentRepository = equipmentRepository,
        maintenanceRepository = maintenanceRepository,
        inspectionRepository = inspectionRepository,
        referenceRepository = referenceRepository,
        dispatchers = DefaultDispatcherProvider(Dispatchers.Main),
        clock = Dates::nowMillis,
    )

    /**
     * Everything currently stored that [incoming] could collide with.
     *
     * Soft-deleted equipment is fetched by id for every equipment row in the file: the repository
     * cannot *list* tombstones, but it can return one when asked by id, and knowing whether the
     * local side already applied a deletion is what stops an import resurrecting it.
     */
    suspend fun snapshot(incoming: DeckWatchExportPayload): LocalSnapshot =
        withContext(dispatchers.io) {
            val vessels = vesselRepository.observeVessels().first()
            val decks = vessels.flatMap { vesselRepository.observeDecks(it.id).first() }
            val zones = decks.flatMap { vesselRepository.observeZones(it.id).first() }
            val categories = (
                vesselRepository.observeCategories(null).first() +
                    vessels.flatMap { vesselRepository.observeCategories(it.id).first() }
                ).distinctBy { it.id }
            val live = vessels.flatMap { equipmentRepository.observeEquipment(it.id).first() }
            val liveIds = live.map { it.id }.toSet()
            val tombstones = incoming.equipment
                .map { it.id }
                .filterNot { it in liveIds }
                .distinct()
                .mapNotNull { equipmentRepository.getEquipment(it) }
            val equipment = live + tombstones
            val links = equipment.flatMap { item ->
                equipmentRepository.observeCategoryIds(item.id).first()
                    .map { EquipmentCategoryLink(item.id, it) }
            }.toSet()
            val instances = equipment.flatMap { maintenanceRepository.observeTaskInstances(it.id).first() }
            val rounds = vessels.flatMap { inspectionRepository.observeRounds(it.id).first() }
            val roundItems = rounds.flatMap { inspectionRepository.observeRoundItems(it.id).first() }
            val deficiencies = vessels.flatMap { inspectionRepository.observeDeficiencies(it.id).first() }

            LocalSnapshot(
                vessels = vessels.associateBy { it.id },
                decks = decks.associateBy { it.id },
                zones = zones.associateBy { it.id },
                categories = categories.associateBy { it.id },
                userTypes = referenceRepository.observeEquipmentTypes().first().associateBy { it.typeKey },
                taskDefinitions = maintenanceRepository.observeTaskDefinitions().first().associateBy { it.key },
                equipment = equipment.associateBy { it.id },
                categoryLinks = links,
                taskInstances = instances.associateBy { it.id },
                rounds = rounds.associateBy { it.id },
                roundItems = roundItems.associateBy { it.id },
                deficiencies = deficiencies.associateBy { it.id },
                userNotes = referenceRepository.observeUserNotes().first().associateBy { it.id },
            )
        }

    /** Validate, then write. See the class documentation for exactly what is guaranteed. */
    suspend fun apply(plan: WritePlan, local: LocalSnapshot): ImportOutcome =
        withContext(dispatchers.io) {
            val violations = ImportMerger.validate(plan, local)
            if (violations.isNotEmpty()) return@withContext ImportOutcome.Rejected(violations)

            val journal = Journal()
            try {
                write(plan, local, journal)
                ImportOutcome.Applied(written = plan.writeCount, deletions = plan.equipmentDeletions.size)
            } catch (error: Throwable) {
                val restored = journal.rollback()
                ImportOutcome.RolledBack(
                    message = error.message ?: error::class.java.simpleName,
                    restored = restored,
                    unrecoverable = journal.unrecoverable.toList(),
                )
            }
        }

    // ------------------------------------------------------------------ writing

    private suspend fun write(plan: WritePlan, local: LocalSnapshot, journal: Journal) {
        for (vessel in plan.vessels) {
            val previous = local.vessels[vessel.id]
            journal.record(previous, { vesselRepository.upsertVessel(it) }, { vesselRepository.deleteVessel(vessel.id) })
            vesselRepository.upsertVessel(vessel)
        }
        for (deck in plan.decks) {
            val previous = local.decks[deck.id]
            journal.record(previous, { vesselRepository.upsertDeck(it) }, { vesselRepository.deleteDeck(deck.id) })
            vesselRepository.upsertDeck(deck)
        }
        for (zone in plan.zones) {
            val previous = local.zones[zone.id]
            journal.record(previous, { vesselRepository.upsertZone(it) }, { vesselRepository.deleteZone(zone.id) })
            vesselRepository.upsertZone(zone)
        }
        for (category in plan.categories) {
            val previous = local.categories[category.id]
            journal.record(
                previous,
                { vesselRepository.upsertCategory(it) },
                { vesselRepository.deleteCategory(category.id) },
            )
            vesselRepository.upsertCategory(category)
        }
        for (type in plan.userTypes) {
            val previous = local.userTypes[type.typeKey]
            journal.record(
                previous,
                { referenceRepository.upsertUserDefinedType(it) },
                { journal.giveUp("Equipment type ${type.typeKey}") },
            )
            referenceRepository.upsertUserDefinedType(type)
        }
        for (definition in plan.taskDefinitions) {
            val previous = local.taskDefinitions[definition.key]
            journal.record(
                previous,
                { maintenanceRepository.upsertTaskDefinition(it) },
                { journal.giveUp("Task definition ${definition.key}") },
            )
            maintenanceRepository.upsertTaskDefinition(definition)
        }
        for (item in plan.equipment) {
            val previous = local.equipment[item.id]
            journal.record(
                previous,
                { equipmentRepository.upsertEquipment(it) },
                { equipmentRepository.softDelete(item.id, clock()) },
            )
            equipmentRepository.upsertEquipment(item)
        }
        writeCategoryLinks(plan, local, journal)
        for (instance in plan.taskInstances) {
            val previous = local.taskInstances[instance.id]
            journal.record(
                previous,
                { maintenanceRepository.upsertInstances(listOf(it)) },
                { journal.giveUp("Task instance ${instance.id}") },
            )
            maintenanceRepository.upsertInstances(listOf(instance))
        }
        for (round in plan.rounds) {
            val previous = local.rounds[round.id]
            journal.record(
                previous,
                { inspectionRepository.upsertRound(it) },
                { journal.giveUp("Round ${round.id}") },
            )
            inspectionRepository.upsertRound(round)
        }
        for (item in plan.roundItems) {
            val previous = local.roundItems[item.id]
            journal.record(
                previous,
                { inspectionRepository.upsertRoundItem(it) },
                { journal.giveUp("Round item ${item.id}") },
            )
            inspectionRepository.upsertRoundItem(item)
        }
        for (deficiency in plan.deficiencies) {
            val previous = local.deficiencies[deficiency.id]
            journal.record(
                previous,
                { inspectionRepository.upsertDeficiency(it) },
                { journal.giveUp("Deficiency ${deficiency.id}") },
            )
            inspectionRepository.upsertDeficiency(deficiency)
        }
        for (note in plan.userNotes) {
            val previous = local.userNotes[note.id]
            journal.record(
                previous,
                { referenceRepository.upsertUserNote(it) },
                { referenceRepository.deleteUserNote(note.id) },
            )
            referenceRepository.upsertUserNote(note)
        }
        for (deletion in plan.equipmentDeletions) {
            val previous = local.equipment[deletion.equipmentId]
            journal.record(
                previous,
                { equipmentRepository.upsertEquipment(it) },
                { equipmentRepository.undelete(deletion.equipmentId) },
            )
            equipmentRepository.softDelete(deletion.equipmentId, deletion.deletedAt)
        }
    }

    /**
     * `setCategories` replaces an item's whole category set, so the links are applied per
     * equipment item as a union of what is already there and what the file adds — an import adds
     * memberships, it does not silently strip the ones this device already had.
     */
    private suspend fun writeCategoryLinks(plan: WritePlan, local: LocalSnapshot, journal: Journal) {
        val incoming = plan.categoryLinks.groupBy({ it.equipmentId }, { it.categoryId })
        for ((equipmentId, categoryIds) in incoming) {
            val previous = local.categoryLinks.filter { it.equipmentId == equipmentId }.map { it.categoryId }
            val merged = (previous + categoryIds).distinct()
            if (merged == previous) continue
            journal.record(
                previous,
                { equipmentRepository.setCategories(equipmentId, it) },
                { equipmentRepository.setCategories(equipmentId, previous) },
            )
            equipmentRepository.setCategories(equipmentId, merged)
        }
    }

    /**
     * The undo log. Entries are pushed as writes happen and replayed in reverse on failure, so a
     * record overwritten twice lands back on its original value rather than an intermediate one.
     */
    private class Journal {
        private val entries = ArrayDeque<suspend () -> Unit>()
        val unrecoverable = mutableListOf<String>()

        /**
         * @param previous the record as it stood before this write, or null for a creation.
         * @param restore how to put [previous] back.
         * @param remove how to withdraw a newly created record.
         */
        fun <T : Any> record(previous: T?, restore: suspend (T) -> Unit, remove: suspend () -> Unit) {
            val entry: suspend () -> Unit = if (previous == null) remove else ({ restore(previous) })
            entries.addLast(entry)
        }

        /** Called by a `remove` that cannot actually remove anything; the row stays. */
        fun giveUp(description: String) {
            unrecoverable += description
        }

        /** Replay backwards. A failing undo step is skipped: one bad step must not stop the rest. */
        suspend fun rollback(): Int {
            var restored = 0
            while (entries.isNotEmpty()) {
                val undo = entries.removeLast()
                runCatching { undo() }.onSuccess { restored++ }
            }
            return restored
        }
    }
}
