package com.deckwatch.feature.survivalcraft

import androidx.compose.ui.geometry.Size
import com.deckwatch.feature.survivalcraft.inventory.InventoryTemplates
import com.deckwatch.feature.survivalcraft.schematic.SchematicCatalogue
import com.deckwatch.feature.survivalcraft.schematic.fitBox
import com.deckwatch.feature.survivalcraft.schematic.markerCentre
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SchematicGeometryTest {

    @Test
    fun `the drawing is letterboxed to its authored aspect ratio`() {
        // Wider than tall: the height is the limit, so the box is centred horizontally.
        val wide = fitBox(Size(400f, 100f), aspect = 2f)
        assertThat(wide.width).isEqualTo(200f)
        assertThat(wide.height).isEqualTo(100f)
        assertThat(wide.left).isEqualTo(100f)
        assertThat(wide.top).isEqualTo(0f)

        // Taller than the ratio allows: the width is the limit.
        val tall = fitBox(Size(200f, 400f), aspect = 2f)
        assertThat(tall.width).isEqualTo(200f)
        assertThat(tall.height).isEqualTo(100f)
        assertThat(tall.top).isEqualTo(150f)
    }

    @Test
    fun `a zero-sized or degenerate canvas never produces a NaN box`() {
        assertThat(fitBox(Size(0f, 0f), aspect = 1.4f).width).isEqualTo(0f)
        assertThat(fitBox(Size(100f, 100f), aspect = 0f).width).isEqualTo(100f)
    }

    @Test
    fun `a hotspot authored at the edge is pulled in so its whole target stays on screen`() {
        val canvas = Size(300f, 200f)
        val box = fitBox(canvas, aspect = 1.5f)
        val half = 24f

        val edge = markerCentre(box, canvas, x = 1f, y = 1f, halfPx = half)
        assertThat(edge.x).isEqualTo(300f - half)
        assertThat(edge.y).isEqualTo(200f - half)

        val origin = markerCentre(box, canvas, x = 0f, y = 0f, halfPx = half)
        assertThat(origin.x).isEqualTo(half)
        assertThat(origin.y).isEqualTo(half)

        val centre = markerCentre(box, canvas, x = 0.5f, y = 0.5f, halfPx = half)
        assertThat(centre.x).isEqualTo(150f)
        assertThat(centre.y).isEqualTo(100f)
    }

    @Test
    fun `every schematic that names an inventory template resolves it`() {
        SchematicCatalogue().all.forEach { definition ->
            val key = definition.inventoryTemplateKey ?: return@forEach
            assertThat(InventoryTemplates.forKey(key)).isNotNull()
        }
    }

    @Test
    fun `a schematic that declares the inventory panel also names a template`() {
        SchematicCatalogue().all.forEach { definition ->
            val declaresInventory = definition.panels.any { it.name == "INVENTORY" }
            assertThat(declaresInventory).isEqualTo(definition.inventoryTemplateKey != null)
        }
    }

    @Test
    fun `only craft that are actually drilled declare the drill log`() {
        val withDrills = SchematicCatalogue().all
            .filter { definition -> definition.panels.any { it.name == "DRILL_LOG" } }
            .map { it.key }
        assertThat(withDrills).containsExactly("LIFEBOAT", "RESCUE_BOAT")
    }
}
