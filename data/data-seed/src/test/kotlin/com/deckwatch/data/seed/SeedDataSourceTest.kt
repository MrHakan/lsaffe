package com.deckwatch.data.seed

import androidx.test.core.app.ApplicationProvider
import com.deckwatch.core.common.ImoNumber
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.model.VerificationStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SeedDataSourceTest {

    private lateinit var dataSource: SeedDataSource

    @Before
    fun setUp() {
        dataSource = SeedDataSource(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `every seed asset parses`() = runTest {
        val bundle = dataSource.loadAll()

        assertThat(bundle.equipmentTypes).isNotEmpty()
        assertThat(bundle.taskDefinitions).isNotEmpty()
        assertThat(bundle.regulationCards).isNotEmpty()
        assertThat(bundle.roundTemplates).hasSize(7)
        assertThat(bundle.planPresets).hasSize(6)
        assertThat(bundle.symbols).isNotEmpty()
        assertThat(bundle.demoVessel.decks).hasSize(5)
    }

    @Test
    fun `seed integrity reports no problems`() = runTest {
        val problems = SeedIntegrity.validate(dataSource.loadAll())
        assertThat(problems).isEmpty()
    }

    @Test
    fun `catalogue meets the minimum bar and spans every group`() = runTest {
        val types = dataSource.loadEquipmentTypes()

        assertThat(types.size).isAtLeast(SeedIntegrity.MIN_EQUIPMENT_TYPES)
        assertThat(types.map { it.group }).containsAtLeast(
            EquipmentGroup.LSA,
            EquipmentGroup.FFE,
            EquipmentGroup.EMERGENCY_ESCAPE,
            EquipmentGroup.MACHINERY_CONTROLS,
            EquipmentGroup.SIGNAGE,
        )
        types.forEach {
            assertThat(it.commonPscFindings.size).isAtLeast(3)
            assertThat(it.nameTr).isNotEmpty()
        }
    }

    @Test
    fun `fire extinguisher implements the section 9-3 worked example`() = runTest {
        val extinguisher = dataSource.loadEquipmentTypes()
            .first { it.typeKey == "FFE_PORTABLE_EXTINGUISHER" }

        val monthly = extinguisher.attributeSchema.filter { it.monthlyChecklist }
        assertThat(monthly).hasSize(6)

        val medium = extinguisher.attributeSchema.first { it.key == "extinguishingMedium" }
        assertThat(medium.affectsTasks).isTrue()
        assertThat(medium.taskKeysByValue["CO2"]).contains("FE_CO2_CYLINDER_WEIGHT_CHECK")
        assertThat(medium.taskKeysByValue["DRY_POWDER_ABC"]).contains("FE_POWDER_CAKING_CHECK")
        assertThat(extinguisher.taskKeys).containsAtLeast(
            "FE_MONTHLY_INSPECTION",
            "FE_ANNUAL_SERVICE",
            "FE_FIVE_YEARLY_DISCHARGE",
            "FE_TEN_YEARLY_HYDROSTATIC",
        )
    }

    @Test
    fun `task definitions carry honest provenance and the section 11-4 tolerances`() = runTest {
        val tasks = dataSource.loadTaskDefinitions().associateBy { it.key }

        tasks.values.forEach {
            assertThat(it.sourceRef).isNotEmpty()
            assertThat(it.lastReviewed).isEqualTo("2026-08-29")
            assertThat(it.verificationStatus).isEqualTo(VerificationStatus.UNVERIFIED)
        }

        // HSSC +/- 3 months where §11.4 states it.
        listOf("SC_ANNUAL_THOROUGH_EXAM", "LR_ANNUAL_SERVICING").forEach { key ->
            val task = tasks.getValue(key)
            assertThat(task.toleranceDaysBefore).isEqualTo(90)
            assertThat(task.toleranceDaysAfter).isEqualTo(90)
        }
        assertThat(tasks.getValue("SC_WEEKLY_VISUAL_INSPECTION").toleranceDaysAfter).isEqualTo(3)
        assertThat(tasks.getValue("FE_MONTHLY_INSPECTION").toleranceDaysAfter).isEqualTo(7)

        // §11.5 overlays reach the tasks they belong to.
        assertThat(tasks.getValue("RG_FIVE_YEARLY_OVERLOAD_TEST").flagOverrides)
            .containsKey("RMI")
        assertThat(tasks.getValue("FE_ANNUAL_SERVICE").flagOverrides).containsKey("LIB")
        assertThat(tasks.getValue("LR_ANNUAL_SERVICING").flagOverrides).containsKey("PAN")
    }

    @Test
    fun `regulations meet the minimum bar across the five bundled sections`() = runTest {
        val cards = dataSource.loadRegulationCards()

        assertThat(cards.size).isAtLeast(SeedIntegrity.MIN_REGULATION_CARDS)
        val bySection = cards.groupBy { it.section }
        listOf(
            RegulationSection.SOLAS,
            RegulationSection.LSA,
            RegulationSection.FFE,
            RegulationSection.FLAG,
            RegulationSection.CLASS,
        ).forEach { assertThat(bySection[it]).isNotEmpty() }
        assertThat(bySection[RegulationSection.MY_NOTES]).isNull()

        bySection.getValue(RegulationSection.FLAG).forEach {
            assertThat(it.verificationStatus)
                .isEqualTo(VerificationStatus.NEEDS_PERIODIC_REVIEW)
            assertThat(it.revisionNote).isNotEmpty()
        }
        cards.forEach {
            assertThat(it.detailBullets.size).isAtLeast(3)
            assertThat(it.detailBullets.size).isAtMost(8)
        }
    }

    @Test
    fun `demo vessel materialises with a valid IMO number and live dates`() = runTest {
        val today = 20_000L
        val now = 1_700_000_000_000L
        val demo = dataSource.buildDemoVessel(today, now)

        assertThat(ImoNumber.isValid(demo.vessel.imoNumber)).isTrue()
        assertThat(demo.vessel.name).isEqualTo("MV Example")
        assertThat(demo.equipment.size).isAtLeast(55)
        assertThat(demo.deficiencies.size).isAtLeast(3)
        assertThat(demo.decks.map { it.levelIndex })
            .containsExactly(0, 10, 20, -10, -20)
        assertThat(demo.zones.size).isAtLeast(2)

        // Dates are day offsets in the asset and epoch-days once materialised.
        assertThat(demo.vessel.safetyEquipmentCertExpiry!!).isGreaterThan(today)
        assertThat(demo.vessel.buildDate!!).isLessThan(today)

        // §19.6 — a handful of tasks are already overdue when the demo loads.
        val overdue = demo.equipment.filter { it.nextDueDate != null && it.nextDueDate!! < today }
        assertThat(overdue.map { it.nextDueTaskKey }.toSet()).hasSize(5)
    }

    @Test
    fun `demo sub-components hang off their parents and stay on their decks`() = runTest {
        val demo = dataSource.buildDemoVessel(20_000L, 1_700_000_000_000L)
        val ids = demo.equipment.map { it.id }.toSet()
        val deckIds = demo.decks.map { it.id }.toSet()

        val children = demo.equipment.filter { it.parentId != null }
        assertThat(children.size).isAtLeast(9)
        children.forEach { assertThat(ids).contains(it.parentId) }
        demo.equipment.forEach {
            assertThat(deckIds).contains(it.deckId)
            assertThat(it.posX).isAtLeast(0f)
            assertThat(it.posX).isAtMost(1f)
            assertThat(it.posY).isAtLeast(0f)
            assertThat(it.posY).isAtMost(1f)
        }
        demo.equipment.forEach { assertThat(it.attributesJson).startsWith("{") }
    }

    @Test
    fun `demo ids are deterministic so reloading the demo does not duplicate it`() = runTest {
        val first = dataSource.buildDemoVessel(20_000L, 1L)
        val second = dataSource.buildDemoVessel(20_500L, 2L)

        assertThat(second.vessel.id).isEqualTo(first.vessel.id)
        assertThat(second.equipment.map { it.id }).isEqualTo(first.equipment.map { it.id })
    }

    @Test
    fun `symbols match the canonical key list`() = runTest {
        val symbols = dataSource.loadSymbols()

        assertThat(symbols.map { it.key }).containsNoDuplicates()
        assertThat(symbols.map { it.key }).containsAtLeast(
            "LSS001", "LSS003", "FES001", "MES001", "EES008", "APP_HRU", "APP_GENERIC",
        )
        symbols.forEach {
            assertThat(it.nameEn).isNotEmpty()
            assertThat(it.nameTr).isNotEmpty()
            assertThat(it.series).isIn(listOf("LSS", "FES", "MES", "EES", "SIS", "APP"))
        }
        assertThat(symbols.first { it.key == "FES001" }.mediaTintable).isTrue()
    }
}
