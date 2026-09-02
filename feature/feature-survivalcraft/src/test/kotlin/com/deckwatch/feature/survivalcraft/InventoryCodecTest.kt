package com.deckwatch.feature.survivalcraft

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.survivalcraft.inventory.InventoryCodec
import com.deckwatch.feature.survivalcraft.inventory.InventoryItem
import com.deckwatch.feature.survivalcraft.inventory.InventoryTemplates
import com.deckwatch.feature.survivalcraft.inventory.expirySummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InventoryCodecTest {

    private val today = TestData.referenceDay

    @Test
    fun `the list round-trips through the attribute bag`() {
        val items = listOf(
            InventoryItem(key = "hand_flares", required = 6, quantity = 6, expiryEpochDay = today + 400),
            InventoryItem(key = "bailer", required = 1, quantity = 1, condition = ConditionGrade.GOOD),
        )
        val json = InventoryCodec.encodeInto("{}", items)
        assertThat(InventoryCodec.decode(json)).isEqualTo(items)
    }

    @Test
    fun `writing the inventory preserves every other attribute`() {
        val original = """{"lastServiceDate":19000,"maker":"Example"}"""
        val written = InventoryCodec.encodeInto(
            original,
            listOf(InventoryItem(key = "torch", quantity = 1)),
        )
        assertThat(written).contains("lastServiceDate")
        assertThat(written).contains("Example")
        assertThat(InventoryCodec.decode(written).single().key).isEqualTo("torch")
    }

    @Test
    fun `an absent or unreadable attribute bag decodes to an empty list`() {
        assertThat(InventoryCodec.decode("{}")).isEmpty()
        assertThat(InventoryCodec.decode("not json")).isEmpty()
        assertThat(InventoryCodec.decode("""{"inventory":"nonsense"}""")).isEmpty()
    }

    @Test
    fun `apply writes the list onto the parent and stamps updatedAt`() {
        val parent = TestData.equipment(attributesJson = """{"maker":"Example"}""")
        val updated = InventoryCodec.apply(
            parent,
            listOf(InventoryItem(key = "water", quantity = 3)),
            updatedAt = 12_345L,
        )
        assertThat(updated.updatedAt).isEqualTo(12_345L)
        assertThat(InventoryCodec.decode(updated.attributesJson).single().quantity).isEqualTo(3)
        assertThat(updated.attributesJson).contains("Example")
    }

    @Test
    fun `merge shows every template row and keeps user-added rows`() {
        val template = InventoryTemplates.forKey(InventoryTemplates.BOAT)
        val stored = listOf(
            InventoryItem(key = "hand_flares", quantity = 6),
            InventoryItem(key = "spare_prop", quantity = 1, label = "Spare propeller"),
        )
        val merged = InventoryCodec.merge(template, stored)

        assertThat(merged).hasSize(requireNotNull(template).itemKeys.size + 1)
        assertThat(merged.first { it.key == "hand_flares" }.quantity).isEqualTo(6)
        // Untouched rows still appear, carrying the template's required quantity.
        assertThat(merged.first { it.key == "rocket_parachute_flares" }.required).isEqualTo(4)
        assertThat(merged.last().key).isEqualTo("spare_prop")
    }

    @Test
    fun `the fixed-gas template is addable and generates cylinder keys`() {
        val template = requireNotNull(InventoryTemplates.forKey(InventoryTemplates.CO2))
        assertThat(template.addable).isTrue()
        assertThat(InventoryCodec.nextAddedKey(template, emptyList())).isEqualTo("cylinder_1")
        val existing = listOf(
            InventoryItem(key = "cylinder_1"),
            InventoryItem(key = "cylinder_4"),
        )
        assertThat(InventoryCodec.nextAddedKey(template, existing)).isEqualTo("cylinder_5")
    }

    @Test
    fun `the expiry summary counts expired, due-soon and in-date rows`() {
        val items = listOf(
            InventoryItem(key = "hand_flares", expiryEpochDay = today - 5),
            InventoryItem(key = "rations", expiryEpochDay = today + 10),
            InventoryItem(key = "water", expiryEpochDay = today + 400),
            InventoryItem(key = "bailer"),
        )
        val summary = items.expirySummary(today)

        assertThat(summary.expired).isEqualTo(1)
        assertThat(summary.dueWithinLeadTime).isEqualTo(1)
        assertThat(summary.tracked).isEqualTo(3)
        assertThat(summary.soonestEpochDay).isEqualTo(today - 5)
        assertThat(summary.hasAttention).isTrue()
    }

    @Test
    fun `an inventory with no dated rows reports nothing to watch`() {
        val summary = listOf(InventoryItem(key = "bailer", quantity = 1)).expirySummary(today)
        assertThat(summary.tracked).isEqualTo(0)
        assertThat(summary.soonestEpochDay).isNull()
        assertThat(summary.hasAttention).isFalse()
    }
}
