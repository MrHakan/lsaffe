package com.deckwatch.core.testing

import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Equipment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [EquipmentRepository] backed by a [MutableStateFlow] map.
 *
 * Soft deletes are honoured throughout (§6.5): a record with a non-null `deletedAt` disappears
 * from every observation but stays in the map so [undelete] can bring it back — the 10-second
 * undo of C10.
 */
class FakeEquipmentRepository(
    private val idFactory: () -> String = ::randomId,
    private val clock: () -> Long = Dates::nowMillis,
) : EquipmentRepository {

    val equipment = MutableStateFlow<Map<String, Equipment>>(emptyMap())

    /** equipmentId -> categoryIds — the `equipment_category_xref` table of §6.4. */
    val categoryXref = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    private fun live(): Collection<Equipment> = equipment.value.values.filter { it.deletedAt == null }

    override fun observeEquipment(vesselId: String): Flow<List<Equipment>> =
        equipment.map { current -> current.alive().filter { it.vesselId == vesselId } }

    override fun observeEquipmentOnDeck(deckId: String): Flow<List<Equipment>> =
        equipment.map { current -> current.alive().filter { it.deckId == deckId } }

    override fun observeChildren(parentId: String): Flow<List<Equipment>> =
        equipment.map { current -> current.alive().filter { it.parentId == parentId } }

    /** Unplaced equipment lives in an inbox until positioned — §6.5. */
    override fun observeUnplaced(vesselId: String): Flow<List<Equipment>> =
        equipment.map { current ->
            current.alive().filter { it.vesselId == vesselId && it.deckId == null }
        }

    override suspend fun getEquipment(id: String): Equipment? = equipment.value[id]

    override suspend fun upsertEquipment(equipment: Equipment) {
        this.equipment.update { it + (equipment.id to equipment) }
    }

    override suspend fun setCondition(id: String, grade: ConditionGrade, atMillis: Long) {
        mutate(id) { it.copy(condition = grade, conditionSetAt = atMillis, updatedAt = atMillis) }
    }

    override suspend fun move(id: String, deckId: String?, zoneId: String?, posX: Float, posY: Float) {
        mutate(id) {
            it.copy(deckId = deckId, zoneId = zoneId, posX = posX, posY = posY, updatedAt = clock())
        }
    }

    override suspend fun softDelete(id: String, atMillis: Long) {
        mutate(id) { it.copy(deletedAt = atMillis, updatedAt = atMillis) }
    }

    override suspend fun undelete(id: String) {
        mutate(id) { it.copy(deletedAt = null, updatedAt = clock()) }
    }

    /**
     * Duplicate ×N with auto-incremented tags — §7.5. The copies are stacked at the source's
     * position; the user drags them apart afterwards.
     */
    override suspend fun duplicate(id: String, count: Int): List<String> {
        val source = equipment.value[id] ?: return emptyList()
        val prefix = source.tag.trimEnd { it.isDigit() }
        var next = nextTagNumber(source.vesselId, prefix)
        val now = clock()
        val copies = (0 until count).map {
            val copy = source.copy(
                id = idFactory(),
                tag = "$prefix${next++}",
                nextDueDate = null,
                nextDueTaskKey = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
            copy
        }
        equipment.update { current -> current + copies.associateBy { it.id } }
        return copies.map { it.id }
    }

    override suspend fun setCategories(equipmentId: String, categoryIds: List<String>) {
        categoryXref.update { it + (equipmentId to categoryIds) }
    }

    override fun observeCategoryIds(equipmentId: String): Flow<List<String>> =
        categoryXref.map { it[equipmentId].orEmpty() }

    /**
     * The next free numeric suffix for a tag prefix on a vessel, e.g. `FE-UD-` -> 8 when
     * `FE-UD-07` is the highest in use — §7.5 tag auto-numbering.
     */
    override suspend fun nextTagNumber(vesselId: String, prefix: String): Int {
        val highest = live()
            .filter { it.vesselId == vesselId && it.tag.startsWith(prefix) }
            .mapNotNull { it.tag.removePrefix(prefix).takeWhile { char -> char.isDigit() }.toIntOrNull() }
            .maxOrNull()
        return (highest ?: 0) + 1
    }

    private fun mutate(id: String, transform: (Equipment) -> Equipment) {
        equipment.update { current ->
            val existing = current[id] ?: return@update current
            current + (id to transform(existing))
        }
    }

    private fun Map<String, Equipment>.alive(): List<Equipment> =
        values.filter { it.deletedAt == null }.sortedBy { it.tag }
}
