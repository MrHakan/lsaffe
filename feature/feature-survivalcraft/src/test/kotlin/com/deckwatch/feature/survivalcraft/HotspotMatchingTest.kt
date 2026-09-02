package com.deckwatch.feature.survivalcraft

import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.survivalcraft.schematic.SchematicHotspot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HotspotMatchingTest {

    private fun hotspot(key: String, type: String?, ordinal: Int = 0) = SchematicHotspot(
        key = key,
        labelEn = key,
        labelTr = key,
        x = 0.5f,
        y = 0.5f,
        childTypeKey = type,
        ordinal = ordinal,
    )

    @Test
    fun `a hotspot with no matching child stays in its add state`() {
        val matches = HotspotMatching.match(
            hotspots = listOf(hotspot("winch", "LSA_LIFEBOAT_WINCH")),
            children = emptyList(),
        )
        assertThat(matches.single().child).isNull()
    }

    @Test
    fun `a child of the hotspot type is matched`() {
        val winch = TestData.equipment(typeKey = "LSA_LIFEBOAT_WINCH", tag = "WNC-1")
        val matches = HotspotMatching.match(
            hotspots = listOf(hotspot("winch", "LSA_LIFEBOAT_WINCH")),
            children = listOf(winch),
        )
        assertThat(matches.single().child?.id).isEqualTo(winch.id)
    }

    @Test
    fun `a child of another type never matches`() {
        val engine = TestData.equipment(typeKey = "LSA_LIFEBOAT_ENGINE", tag = "LBE-1")
        val matches = HotspotMatching.match(
            hotspots = listOf(hotspot("winch", "LSA_LIFEBOAT_WINCH")),
            children = listOf(engine),
        )
        assertThat(matches.single().child).isNull()
    }

    @Test
    fun `a hotspot without a catalogue type only ever matches an explicit binding`() {
        val plug = TestData.equipment(typeKey = "LSA_LIFEBOAT_ENGINE", tag = "DP-1")
        val unbound = HotspotMatching.match(listOf(hotspot("drain_plugs", null)), listOf(plug))
        assertThat(unbound.single().child).isNull()

        val bound = plug.withHotspotBinding("drain_plugs")
        val matched = HotspotMatching.match(listOf(hotspot("drain_plugs", null)), listOf(bound))
        assertThat(matched.single().child?.id).isEqualTo(plug.id)
    }

    @Test
    fun `the explicit binding beats the type-and-ordinal fallback`() {
        val fwd = TestData.equipment(
            id = "fwd",
            typeKey = "LSA_ONLOAD_RELEASE_GEAR",
            tag = "ORG-02",
        ).withHotspotBinding("hook_fwd")
        val aft = TestData.equipment(id = "aft", typeKey = "LSA_ONLOAD_RELEASE_GEAR", tag = "ORG-01")

        val matches = HotspotMatching.match(
            hotspots = listOf(
                hotspot("hook_fwd", "LSA_ONLOAD_RELEASE_GEAR", ordinal = 0),
                hotspot("hook_aft", "LSA_ONLOAD_RELEASE_GEAR", ordinal = 1),
            ),
            children = listOf(fwd, aft),
        ).associate { it.hotspot.key to it.child?.id }

        // Tag order alone would have given ORG-01 to the forward hook; the binding overrides it.
        assertThat(matches["hook_fwd"]).isEqualTo("fwd")
        assertThat(matches["hook_aft"]).isEqualTo("aft")
    }

    @Test
    fun `two hotspots of one type take the unclaimed children in tag then ordinal order`() {
        val first = TestData.equipment(id = "a", typeKey = "LSA_ONLOAD_RELEASE_GEAR", tag = "ORG-01")
        val second = TestData.equipment(id = "b", typeKey = "LSA_ONLOAD_RELEASE_GEAR", tag = "ORG-02")

        val matches = HotspotMatching.match(
            hotspots = listOf(
                hotspot("hook_aft", "LSA_ONLOAD_RELEASE_GEAR", ordinal = 1),
                hotspot("hook_fwd", "LSA_ONLOAD_RELEASE_GEAR", ordinal = 0),
            ),
            children = listOf(second, first),
        ).associate { it.hotspot.key to it.child?.id }

        assertThat(matches["hook_fwd"]).isEqualTo("a")
        assertThat(matches["hook_aft"]).isEqualTo("b")
    }

    @Test
    fun `one child is never handed to two hotspots`() {
        val only = TestData.equipment(id = "only", typeKey = "LSA_ONLOAD_RELEASE_GEAR", tag = "ORG-01")
        val matches = HotspotMatching.match(
            hotspots = listOf(
                hotspot("hook_fwd", "LSA_ONLOAD_RELEASE_GEAR", ordinal = 0),
                hotspot("hook_aft", "LSA_ONLOAD_RELEASE_GEAR", ordinal = 1),
            ),
            children = listOf(only),
        )
        assertThat(matches.mapNotNull { it.child?.id }).containsExactly("only")
    }

    @Test
    fun `the binding is written without disturbing other attributes`() {
        val item = TestData.equipment(attributesJson = """{"lastServiceDate":19000,"maker":"X"}""")
        val bound = item.withHotspotBinding("engine")
        assertThat(bound.boundHotspotKey()).isEqualTo("engine")
        assertThat(bound.attributesJson).contains("lastServiceDate")
        assertThat(bound.attributesJson).contains("\"maker\":\"X\"")
    }

    @Test
    fun `an unreadable attribute bag never throws`() {
        val broken = TestData.equipment(attributesJson = "not json")
        assertThat(broken.boundHotspotKey()).isNull()
        assertThat(broken.withHotspotBinding("engine").boundHotspotKey()).isEqualTo("engine")
    }
}
