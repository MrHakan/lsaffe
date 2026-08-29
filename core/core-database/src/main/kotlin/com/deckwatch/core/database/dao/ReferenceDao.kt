package com.deckwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.deckwatch.core.database.entity.EquipmentTypeEntity
import com.deckwatch.core.database.entity.RegulationCardEntity
import com.deckwatch.core.database.entity.RoundTemplateEntity
import com.deckwatch.core.database.entity.UserNoteEntity
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.RegulationSection
import kotlinx.coroutines.flow.Flow

/** The bundled equipment type catalogue — MASTER_PROMPT §9. */
@Dao
interface EquipmentTypeDao {

    @Query("SELECT * FROM equipment_types ORDER BY typeGroup, subGroup, nameEn COLLATE NOCASE")
    fun observeAll(): Flow<List<EquipmentTypeEntity>>

    @Query("SELECT * FROM equipment_types WHERE typeGroup = :group ORDER BY subGroup, nameEn COLLATE NOCASE")
    fun observeByGroup(group: EquipmentGroup): Flow<List<EquipmentTypeEntity>>

    /** Catalogue search is the primary path: the LSA and FFE lists are long — §7.5 step 2. */
    @Query(
        """
        SELECT * FROM equipment_types
        WHERE nameEn LIKE '%' || :query || '%'
           OR nameTr LIKE '%' || :query || '%'
           OR typeKey LIKE '%' || :query || '%'
        ORDER BY typeGroup, subGroup, nameEn COLLATE NOCASE
        """,
    )
    fun search(query: String): Flow<List<EquipmentTypeEntity>>

    @Query("SELECT * FROM equipment_types WHERE typeKey = :typeKey")
    suspend fun getByKey(typeKey: String): EquipmentTypeEntity?

    @Query("SELECT * FROM equipment_types ORDER BY typeGroup, subGroup, nameEn COLLATE NOCASE")
    suspend fun getAll(): List<EquipmentTypeEntity>

    @Upsert
    suspend fun upsert(type: EquipmentTypeEntity)

    @Upsert
    suspend fun upsertAll(types: List<EquipmentTypeEntity>)

    @Query("DELETE FROM equipment_types WHERE typeKey = :typeKey")
    suspend fun deleteByKey(typeKey: String)

    /** Re-seeding replaces bundled rows only; the user's own types (§9.2) survive. */
    @Query("DELETE FROM equipment_types WHERE isUserDefined = 0")
    suspend fun deleteBundled()
}

/** The bundled regulatory note cards — MASTER_PROMPT §8. */
@Dao
interface RegulationCardDao {

    @Query("SELECT * FROM regulation_cards ORDER BY section, citation")
    fun observeAll(): Flow<List<RegulationCardEntity>>

    @Query("SELECT * FROM regulation_cards WHERE section = :section ORDER BY citation")
    fun observeBySection(section: RegulationSection): Flow<List<RegulationCardEntity>>

    /**
     * Notes-tab search across the three fields an officer actually searches by: the citation
     * ("III/20"), the title, and the WHAT sentence. `LIKE` is case-insensitive for ASCII in
     * SQLite, which is what the bundled English content needs.
     */
    @Query(
        """
        SELECT * FROM regulation_cards
        WHERE title LIKE '%' || :query || '%'
           OR citation LIKE '%' || :query || '%'
           OR what LIKE '%' || :query || '%'
        ORDER BY section, citation
        """,
    )
    fun search(query: String): Flow<List<RegulationCardEntity>>

    @Query("SELECT * FROM regulation_cards WHERE refKey = :refKey")
    suspend fun getByKey(refKey: String): RegulationCardEntity?

    @Query("SELECT * FROM regulation_cards WHERE refKey IN (:refKeys) ORDER BY section, citation")
    suspend fun getByKeys(refKeys: List<String>): List<RegulationCardEntity>

    @Upsert
    suspend fun upsert(card: RegulationCardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<RegulationCardEntity>)

    @Query("DELETE FROM regulation_cards WHERE refKey = :refKey")
    suspend fun deleteByKey(refKey: String)

    @Query("DELETE FROM regulation_cards")
    suspend fun deleteAll()
}

/** The bundled round templates — MASTER_PROMPT §19 item 5. */
@Dao
interface RoundTemplateDao {

    @Query("SELECT * FROM round_templates ORDER BY titleEn COLLATE NOCASE")
    fun observeAll(): Flow<List<RoundTemplateEntity>>

    @Query("SELECT * FROM round_templates WHERE key = :key")
    suspend fun getByKey(key: String): RoundTemplateEntity?

    @Upsert
    suspend fun upsert(template: RoundTemplateEntity)

    @Upsert
    suspend fun upsertAll(templates: List<RoundTemplateEntity>)

    @Query("DELETE FROM round_templates WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM round_templates")
    suspend fun deleteAll()
}

/** The user's own notes — MASTER_PROMPT §8.1 "MY NOTES". Never touched by content re-seeding. */
@Dao
interface UserNoteDao {

    @Query("SELECT * FROM user_notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<UserNoteEntity>>

    @Query("SELECT * FROM user_notes WHERE folder = :folder ORDER BY updatedAt DESC")
    fun observeByFolder(folder: String): Flow<List<UserNoteEntity>>

    /** Notes attached to a card surface on that card — §8.4. */
    @Query("SELECT * FROM user_notes WHERE regulationRefKey = :refKey ORDER BY updatedAt DESC")
    fun observeForRegulationCard(refKey: String): Flow<List<UserNoteEntity>>

    @Query("SELECT * FROM user_notes WHERE equipmentTypeKey = :typeKey ORDER BY updatedAt DESC")
    fun observeForEquipmentType(typeKey: String): Flow<List<UserNoteEntity>>

    @Query(
        """
        SELECT * FROM user_notes
        WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """,
    )
    fun search(query: String): Flow<List<UserNoteEntity>>

    @Query("SELECT * FROM user_notes WHERE id = :id")
    suspend fun getById(id: String): UserNoteEntity?

    @Upsert
    suspend fun upsert(note: UserNoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<UserNoteEntity>)

    @Query("DELETE FROM user_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
