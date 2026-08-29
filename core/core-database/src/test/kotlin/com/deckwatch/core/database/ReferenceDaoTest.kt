package com.deckwatch.core.database

import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.model.VerificationStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReferenceDaoTest : DeckWatchDatabaseTest() {

    private val typeDao get() = database.equipmentTypeDao()
    private val cardDao get() = database.regulationCardDao()
    private val templateDao get() = database.roundTemplateDao()
    private val noteDao get() = database.userNoteDao()

    @Test
    fun `equipment type round-trips with its attribute schema intact`() = runTest {
        val original = Fixtures.equipmentType()
        typeDao.upsert(original)

        val stored = typeDao.observeAll().first().single()
        assertThat(stored).isEqualTo(original)
        assertThat(stored.toModel().toEntity()).isEqualTo(original)

        val medium = stored.attributeSchema.first()
        assertThat(medium.key).isEqualTo("extinguishingMedium")
        assertThat(medium.kind).isEqualTo(AttributeKind.ENUM)
        assertThat(medium.affectsTasks).isTrue()
        assertThat(medium.taskKeysByValue).containsEntry("CO2", listOf("FE_CYLINDER_WEIGHT_CHECK"))
        assertThat(stored.attributeSchema[1].monthlyChecklist).isTrue()
        assertThat(stored.commonPscFindings).hasSize(2)

        typeDao.upsert(original.copy(nameEn = "Portable extinguisher"))
        assertThat(typeDao.getByKey(original.typeKey)?.nameEn).isEqualTo("Portable extinguisher")

        typeDao.deleteByKey(original.typeKey)
        assertThat(typeDao.observeAll().first()).isEmpty()
    }

    @Test
    fun `catalogue search and group filter find a type`() = runTest {
        typeDao.upsertAll(
            listOf(
                Fixtures.equipmentType(),
                Fixtures.equipmentType(typeKey = "LSA_LIFEBUOY").copy(
                    group = EquipmentGroup.LSA,
                    nameEn = "Lifebuoy",
                    nameTr = "Can simidi",
                ),
            ),
        )

        assertThat(typeDao.search("lifebuoy").first().map { it.typeKey }).containsExactly("LSA_LIFEBUOY")
        assertThat(typeDao.search("can simidi").first().map { it.typeKey }).containsExactly("LSA_LIFEBUOY")
        assertThat(typeDao.search("EXTINGUISHER").first().map { it.typeKey })
            .containsExactly("FFE_PORTABLE_EXTINGUISHER")
        assertThat(typeDao.observeByGroup(EquipmentGroup.LSA).first().map { it.typeKey })
            .containsExactly("LSA_LIFEBUOY")
    }

    @Test
    fun `re-seeding the catalogue keeps user-defined types`() = runTest {
        typeDao.upsertAll(
            listOf(
                Fixtures.equipmentType(),
                Fixtures.equipmentType(typeKey = "MY_OWN_THING").copy(isUserDefined = true),
            ),
        )

        typeDao.deleteBundled()

        assertThat(typeDao.getAll().map { it.typeKey }).containsExactly("MY_OWN_THING")
    }

    @Test
    fun `regulation card round-trips and is searchable by citation title and what`() = runTest {
        val original = Fixtures.regulationCard()
        cardDao.upsert(original)
        cardDao.upsert(
            Fixtures.regulationCard(refKey = "FSS_CH4").copy(
                section = RegulationSection.FFE,
                citation = "FSS Code Ch.4",
                title = "Fire extinguishers",
                what = "Portable extinguishers are inspected monthly.",
            ),
        )

        val stored = cardDao.getByKey("SOLAS_III_20_6")
        assertThat(stored).isEqualTo(original)
        assertThat(stored?.toModel()?.toEntity()).isEqualTo(original)
        assertThat(stored?.flagNotes).containsEntry("RMI", "Reports countersigned by the Master.")
        assertThat(stored?.verificationStatus).isEqualTo(VerificationStatus.NEEDS_PERIODIC_REVIEW)
        assertThat(stored?.detailBullets).hasSize(2)

        assertThat(cardDao.search("III/20").first().map { it.refKey }).containsExactly("SOLAS_III_20_6")
        assertThat(cardDao.search("survival craft").first().map { it.refKey })
            .containsExactly("SOLAS_III_20_6")
        assertThat(cardDao.search("monthly").first().map { it.refKey }).containsExactly("FSS_CH4")
        assertThat(cardDao.search("extinguisher").first().map { it.refKey }).containsExactly("FSS_CH4")
        assertThat(cardDao.search("nothing here").first()).isEmpty()

        assertThat(cardDao.observeBySection(RegulationSection.SOLAS).first()).hasSize(1)
        assertThat(cardDao.getByKeys(listOf("SOLAS_III_20_6", "FSS_CH4"))).hasSize(2)

        cardDao.deleteByKey("FSS_CH4")
        assertThat(cardDao.observeAll().first().map { it.refKey }).containsExactly("SOLAS_III_20_6")
        cardDao.deleteAll()
        assertThat(cardDao.observeAll().first()).isEmpty()
    }

    @Test
    fun `round template round-trips with its group list`() = runTest {
        val original = Fixtures.roundTemplate()
        templateDao.upsert(original)

        val stored = templateDao.observeAll().first().single()
        assertThat(stored).isEqualTo(original)
        assertThat(stored.toModel().toEntity()).isEqualTo(original)
        assertThat(stored.includesGroups)
            .containsExactly(EquipmentGroup.LSA, EquipmentGroup.EMERGENCY_ESCAPE).inOrder()

        templateDao.upsert(original.copy(titleEn = "Weekly LSA sweep"))
        assertThat(templateDao.getByKey("WEEKLY_LSA")?.titleEn).isEqualTo("Weekly LSA sweep")

        templateDao.deleteByKey("WEEKLY_LSA")
        assertThat(templateDao.observeAll().first()).isEmpty()
    }

    @Test
    fun `user note round-trips and can be found by its attachments`() = runTest {
        val original = Fixtures.userNote()
        noteDao.upsert(original)
        noteDao.upsert(
            Fixtures.userNote(id = "note-2").copy(
                title = "Spare parts",
                body = "Order two 5 kg CO2 charges.",
                folder = "Stores",
                regulationRefKey = null,
                equipmentTypeKey = null,
                isFavourite = false,
            ),
        )

        val stored = noteDao.getById("note-1")
        assertThat(stored).isEqualTo(original)
        assertThat(stored?.toModel()?.toEntity()).isEqualTo(original)

        assertThat(noteDao.observeAll().first()).hasSize(2)
        assertThat(noteDao.observeByFolder("Stores").first().map { it.id }).containsExactly("note-2")
        assertThat(noteDao.observeForRegulationCard("SOLAS_III_20_6").first().map { it.id })
            .containsExactly("note-1")
        assertThat(noteDao.observeForEquipmentType("FFE_PORTABLE_EXTINGUISHER").first().map { it.id })
            .containsExactly("note-1")
        assertThat(noteDao.search("charges").first().map { it.id }).containsExactly("note-2")

        noteDao.upsert(original.copy(title = "Rotterdam agent"))
        assertThat(noteDao.getById("note-1")?.title).isEqualTo("Rotterdam agent")

        noteDao.deleteById("note-1")
        assertThat(noteDao.observeAll().first().map { it.id }).containsExactly("note-2")
    }
}
