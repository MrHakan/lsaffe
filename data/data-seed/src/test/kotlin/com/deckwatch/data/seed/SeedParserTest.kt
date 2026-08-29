package com.deckwatch.data.seed

import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.PlanShape
import com.deckwatch.core.model.Severity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [SeedParser] is pure, so it is exercised without an Android context. */
class SeedParserTest {

    @Test
    fun `unknown keys are ignored so a newer content version still loads`() {
        val types = SeedParser.parseEquipmentTypes(
            """
            [{
              "typeKey": "FFE_TEST", "group": "FFE", "subGroup": "PORTABLE_APPLIANCES",
              "nameEn": "Test", "nameTr": "Test", "symbolKey": "FES001",
              "defaultTagPrefix": "FE", "futureFieldAddedLater": 42,
              "attributeSchema": [{
                "key": "medium", "kind": "ENUM", "labelEn": "Medium", "labelTr": "Madde",
                "somethingNew": true
              }]
            }]
            """.trimIndent(),
        )

        assertThat(types).hasSize(1)
        assertThat(types.single().group).isEqualTo(EquipmentGroup.FFE)
        assertThat(types.single().attributeSchema.single().kind).isEqualTo(AttributeKind.ENUM)
    }

    @Test
    fun `omitted optional fields fall back to the model defaults`() {
        val tasks = SeedParser.parseTaskDefinitions(
            """
            [{
              "key": "T1", "appliesToTypeKeys": ["FFE_TEST"], "titleEn": "T", "titleTr": "T",
              "intervalKind": "WEEKLY", "performedBy": "SHIP_STAFF"
            }]
            """.trimIndent(),
        )

        val task = tasks.single()
        assertThat(task.intervalKind).isEqualTo(IntervalKind.WEEKLY)
        assertThat(task.performedBy).isEqualTo(PerformedBy.SHIP_STAFF)
        assertThat(task.intervalMonths).isNull()
        assertThat(task.toleranceDaysBefore).isEqualTo(0)
        assertThat(task.flagOverrides).isNull()
        assertThat(task.isUserDefined).isFalse()
    }

    @Test
    fun `plan presets parse their hull parameters`() {
        val presets = SeedParser.parsePlanPresets(
            """
            [{
              "key": "P", "nameEn": "P", "nameTr": "P",
              "plan": {"shape": "SHIP_HULL", "bowSharpness": 0.45, "lengthRatio": 1.0}
            }]
            """.trimIndent(),
        )

        val plan = presets.single().plan
        assertThat(plan.shape).isEqualTo(PlanShape.SHIP_HULL)
        assertThat(plan.bowSharpness).isEqualTo(0.45f)
        assertThat(plan.breadthRatio).isEqualTo(1.0f)
    }

    @Test
    fun `demo day offsets become epoch days relative to load time`() {
        val seed = SeedParser.parseDemoVessel(
            """
            {
              "vessel": {"name": "MV Test", "buildDaysAgo": 100,
                         "safetyEquipmentCertExpiryDaysAhead": 200},
              "decks": [{"key": "UD", "name": "Upper Deck", "levelIndex": 0,
                         "planPresetKey": "P"}],
              "equipment": [{
                "key": "E1", "deckKey": "UD", "typeKey": "FFE_TEST", "symbolKey": "FES001",
                "tag": "FE-01", "installedDaysAgo": 30, "nextDueDaysAhead": -5,
                "attributes": {"extinguishingMedium": "CO2"},
                "dateAttributesDaysAgo": {"lastAnnualServiceDate": 400}
              }],
              "deficiencies": [{"key": "D1", "equipmentKey": "E1", "raisedDaysAgo": 3,
                                "severity": "MAJOR", "title": "Overdue"}]
            }
            """.trimIndent(),
        )
        val presets = SeedParser.parsePlanPresets(
            """[{"key": "P", "nameEn": "P", "nameTr": "P", "plan": {"shape": "RECTANGLE"}}]""",
        )

        val today = 20_000L
        val demo = SeedDataSource.buildDemoVessel(seed, presets, today, 5L)

        assertThat(demo.vessel.buildDate).isEqualTo(today - 100)
        assertThat(demo.vessel.safetyEquipmentCertExpiry).isEqualTo(today + 200)
        val item = demo.equipment.single()
        assertThat(item.installedDate).isEqualTo(today - 30)
        assertThat(item.nextDueDate).isEqualTo(today - 5)
        assertThat(item.attributesJson).contains("\"lastAnnualServiceDate\":${today - 400}")
        assertThat(item.attributesJson).contains("\"extinguishingMedium\":\"CO2\"")
        val deficiency = demo.deficiencies.single()
        assertThat(deficiency.severity).isEqualTo(Severity.MAJOR)
        assertThat(deficiency.raisedDate).isEqualTo(today - 3)
        assertThat(deficiency.equipmentId).isEqualTo(item.id)
    }

    @Test
    fun `integrity check reports every broken reference rather than the first`() {
        val bundle = SeedBundle(
            equipmentTypes = SeedParser.parseEquipmentTypes(
                """
                [{"typeKey": "FFE_TEST", "group": "FFE", "subGroup": "S", "nameEn": "T",
                  "nameTr": "T", "symbolKey": "NOPE", "defaultTagPrefix": "FE",
                  "taskKeys": ["MISSING_TASK"], "regulationRefs": ["MISSING_REF"]}]
                """.trimIndent(),
            ),
            taskDefinitions = emptyList(),
            regulationCards = emptyList(),
            roundTemplates = emptyList(),
            planPresets = emptyList(),
            symbols = emptyList(),
            demoVessel = SeedParser.parseDemoVessel(
                """
                {"vessel": {"name": "MV Test"},
                 "equipment": [{"key": "E1", "typeKey": "UNKNOWN_TYPE",
                                "symbolKey": "NOPE", "tag": "X", "parentKey": "GHOST"}]}
                """.trimIndent(),
            ),
        )

        val problems = SeedIntegrity.validate(bundle)

        assertThat(problems).isNotEmpty()
        assertThat(problems.any { it.contains("MISSING_TASK") }).isTrue()
        assertThat(problems.any { it.contains("MISSING_REF") }).isTrue()
        assertThat(problems.any { it.contains("NOPE") }).isTrue()
        assertThat(problems.any { it.contains("UNKNOWN_TYPE") }).isTrue()
        assertThat(problems.any { it.contains("GHOST") }).isTrue()
        assertThat(problems.any { it.contains("minimum bar is 70") }).isTrue()
        assertThat(problems.any { it.contains("minimum bar is 120") }).isTrue()
    }
}
