package com.deckwatch.feature.inspection

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.testing.SequentialIds
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Materialising a round from a bundled template — §6.7, §19 (5). */
class RoundMaterialiserTest {

    private val types = mapOf(
        "FFE_PORTABLE_EXTINGUISHER" to TestData.equipmentType(
            typeKey = "FFE_PORTABLE_EXTINGUISHER",
            group = EquipmentGroup.FFE,
        ),
        "LSA_LIFEBUOY" to TestData.equipmentType(
            typeKey = "LSA_LIFEBUOY",
            group = EquipmentGroup.LSA,
            nameEn = "Lifebuoy",
            nameTr = "Can simidi",
            symbolKey = "LSS005",
            defaultTagPrefix = "LB",
            attributeSchema = emptyList(),
            taskKeys = emptyList(),
        ),
        "LSA_LIFERAFT" to TestData.equipmentType(
            typeKey = "LSA_LIFERAFT",
            group = EquipmentGroup.LSA,
            nameEn = "Liferaft",
            nameTr = "Can salı",
            symbolKey = "LSS003",
            defaultTagPrefix = "LR",
            attributeSchema = emptyList(),
            taskKeys = emptyList(),
        ),
    )

    private val extinguisher = TestData.equipment(
        id = "e-fe",
        deckId = "deck-upper",
        typeKey = "FFE_PORTABLE_EXTINGUISHER",
        tag = "FE-UD-01",
    )
    private val lifebuoy = TestData.equipment(
        id = "e-lb",
        deckId = "deck-boat",
        typeKey = "LSA_LIFEBUOY",
        tag = "LB-01",
    )
    private val liferaft = TestData.equipment(
        id = "e-lr",
        deckId = "deck-upper",
        typeKey = "LSA_LIFERAFT",
        tag = "LR-01",
    )
    private val unplaced = TestData.equipment(
        id = "e-unplaced",
        deckId = null,
        typeKey = "LSA_LIFEBUOY",
        tag = "LB-99",
    )
    private val all = listOf(extinguisher, lifebuoy, liferaft, unplaced)

    @Test
    fun `a group template picks up every type in that branch of the catalogue`() {
        val template = RoundTemplate(
            key = "WEEKLY_LSA",
            titleEn = "Weekly LSA round",
            titleTr = "Haftalık LSA turu",
            includesGroups = listOf(EquipmentGroup.LSA),
        )
        val matched = RoundMaterialiser.matchEquipment(template, all, types)
        assertThat(matched.map { it.id }).containsExactly("e-lb", "e-lr", "e-unplaced")
    }

    @Test
    fun `a type-key template picks up exactly the named types`() {
        val template = RoundTemplate(
            key = "MONTHLY_FFE",
            titleEn = "Monthly FFE round",
            titleTr = "Aylık FFE turu",
            includesTypeKeys = listOf("FFE_PORTABLE_EXTINGUISHER"),
        )
        val matched = RoundMaterialiser.matchEquipment(template, all, types)
        assertThat(matched.map { it.id }).containsExactly("e-fe")
    }

    @Test
    fun `type keys and groups union rather than intersect`() {
        val template = RoundTemplate(
            key = "PRE_ARRIVAL_PSC",
            titleEn = "Pre-arrival PSC self-check",
            titleTr = "Varış öncesi PSC öz kontrolü",
            includesTypeKeys = listOf("FFE_PORTABLE_EXTINGUISHER"),
            includesGroups = listOf(EquipmentGroup.LSA),
        )
        val matched = RoundMaterialiser.matchEquipment(template, all, types)
        assertThat(matched.map { it.id })
            .containsExactly("e-fe", "e-lb", "e-lr", "e-unplaced")
    }

    @Test
    fun `a type key outside the catalogue still matches, so user-defined types are not dropped`() {
        val custom = TestData.equipment(id = "e-custom", typeKey = "USER_DEFINED_THING", tag = "XX-01")
        val template = RoundTemplate(
            key = "CUSTOM",
            titleEn = "Custom",
            titleTr = "Özel",
            includesTypeKeys = listOf("USER_DEFINED_THING"),
        )
        val matched = RoundMaterialiser.matchEquipment(template, listOf(custom), types)
        assertThat(matched.map { it.id }).containsExactly("e-custom")
    }

    @Test
    fun `a template naming nothing matches nothing`() {
        val template = RoundTemplate(key = "EMPTY", titleEn = "Empty", titleTr = "Boş")
        assertThat(RoundMaterialiser.matchEquipment(template, all, types)).isEmpty()
    }

    @Test
    fun `soft-deleted equipment never joins a round`() {
        val deleted = lifebuoy.copy(id = "e-gone", deletedAt = TestData.referenceMillis)
        val template = RoundTemplate(
            key = "WEEKLY_LSA",
            titleEn = "Weekly LSA round",
            titleTr = "Haftalık LSA turu",
            includesGroups = listOf(EquipmentGroup.LSA),
        )
        val matched = RoundMaterialiser.matchEquipment(template, listOf(deleted), types)
        assertThat(matched).isEmpty()
    }

    @Test
    fun `items walk the stack in deck order with unplaced gear last`() {
        val template = RoundTemplate(
            key = "WEEKLY_LSA",
            titleEn = "Weekly LSA round",
            titleTr = "Haftalık LSA turu",
            includesGroups = listOf(EquipmentGroup.LSA),
        )
        val matched = RoundMaterialiser.matchEquipment(
            template = template,
            equipment = all,
            typesByKey = types,
            deckOrder = mapOf("deck-boat" to 0, "deck-upper" to 1),
        )
        assertThat(matched.map { it.id }).containsExactly("e-lb", "e-lr", "e-unplaced").inOrder()
    }

    @Test
    fun `materialising writes a round and one item per matching item`() {
        val template = RoundTemplate(
            key = "WEEKLY_LSA",
            titleEn = "Weekly LSA round",
            titleTr = "Haftalık LSA turu",
            includesGroups = listOf(EquipmentGroup.LSA),
        )
        val result = RoundMaterialiser.materialise(
            template = template,
            vesselId = "vessel-1",
            equipment = all,
            typesByKey = types,
            performedBy = "3/O",
            startedAtMillis = TestData.referenceMillis,
            idFactory = SequentialIds("round"),
        )

        assertThat(result.round.id).isEqualTo("round-1")
        assertThat(result.round.vesselId).isEqualTo("vessel-1")
        assertThat(result.round.templateKey).isEqualTo("WEEKLY_LSA")
        assertThat(result.round.title).isEqualTo("Weekly LSA round")
        assertThat(result.round.performedBy).isEqualTo("3/O")
        assertThat(result.round.itemCount).isEqualTo(3)
        assertThat(result.round.doneCount).isEqualTo(0)
        assertThat(result.round.completedAt).isNull()
        assertThat(result.items).hasSize(3)
        assertThat(result.items.map { it.roundId }.distinct()).containsExactly("round-1")
        assertThat(result.items.map { it.equipmentId })
            .containsExactly("e-lb", "e-lr", "e-unplaced")
        assertThat(result.items.map { it.id }).containsExactly("round-2", "round-3", "round-4")
    }

    @Test
    fun `a Turkish device gets the Turkish round title`() {
        val template = RoundTemplate(
            key = "WEEKLY_LSA",
            titleEn = "Weekly LSA round",
            titleTr = "Haftalık LSA turu",
            includesGroups = listOf(EquipmentGroup.LSA),
        )
        val result = RoundMaterialiser.materialise(
            template = template,
            vesselId = "vessel-1",
            equipment = all,
            typesByKey = types,
            performedBy = "3/O",
            startedAtMillis = TestData.referenceMillis,
            idFactory = SequentialIds("round"),
            turkish = true,
        )
        assertThat(result.round.title).isEqualTo("Haftalık LSA turu")
    }

    @Test
    fun `recount counts graded items and the not-fully-serviceable ones`() {
        val round = TestData.round(id = "round-1", itemCount = 0)
        val items = listOf(
            TestData.roundItem(id = "i1", roundId = "round-1", condition = ConditionGrade.GOOD),
            TestData.roundItem(id = "i2", roundId = "round-1", condition = ConditionGrade.DEFECTIVE),
            TestData.roundItem(id = "i3", roundId = "round-1", condition = ConditionGrade.OUT_OF_SERVICE),
            TestData.roundItem(id = "i4", roundId = "round-1", condition = null),
        )
        val counted = RoundMaterialiser.recount(round, items)
        assertThat(counted.itemCount).isEqualTo(4)
        assertThat(counted.doneCount).isEqualTo(3)
        assertThat(counted.deficiencyCount).isEqualTo(2)
    }
}
