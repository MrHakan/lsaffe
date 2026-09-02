package com.deckwatch.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration harness — MASTER_PROMPT C10 ("every DB migration is tested").
 *
 * The first test proves the two things that make every migration test possible at all, and which
 * break silently if someone changes the build:
 * 1. The schema JSON for the current version is actually **exported and packaged** where
 *    [MigrationTestHelper] can find it. If `exportSchema` were turned off, or the schemas stopped
 *    being added to the test assets, `createDatabase` fails here rather than on the day someone
 *    writes migration 1 → 2 and has no baseline to migrate from.
 * 2. A database created from that schema validates against the entity definitions in code, so the
 *    committed JSON and the `@Entity` classes cannot drift apart.
 *
 * **Adding a version:** bump [DeckWatchDatabase.VERSION], commit the new schema JSON, add the
 * `Migration` to [DECKWATCH_MIGRATIONS], and add a test here that creates the database at the old
 * version, inserts a representative row of every table the migration touches, closes it, then runs
 * `runMigrationsAndValidate` and asserts the data survived. Validating is not enough on its own —
 * C10 is about not losing data, so assert on the rows, not only on the schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class MigrationHarnessTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DeckWatchDatabase::class.java,
    )

    @Test
    fun `the current schema is exported, packaged and consistent with the entities`() {
        val db = helper.createDatabase(TEST_DB, DeckWatchDatabase.VERSION)

        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        db.close()

        assertThat(tables).containsAtLeast(
            "vessels",
            "decks",
            "zones",
            "categories",
            "equipment_category_xref",
            "equipment",
            "task_definitions",
            "task_instances",
            "rounds",
            "round_items",
            "deficiencies",
            "equipment_types",
            "regulation_cards",
            "round_templates",
            "user_notes",
        )

        // Re-opening at the same version runs Room's own schema validation against the entities.
        helper.runMigrationsAndValidate(TEST_DB, DeckWatchDatabase.VERSION, true).close()
    }

    @Test
    fun `migrating 1 to 2 adds the equipment guide column and keeps every row`() {
        helper.createDatabase(TEST_DB, 1).use { old ->
            // A bundled type and one the officer made: the migration must not favour either.
            old.execSQL(insertTypeV1("FFE_PORTABLE_EXTINGUISHER", "Portable extinguisher", 0))
            old.execSQL(insertTypeV1("USER_MY_OWN_THING", "Spare gear locker", 1))
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val rows = migrated.query(
            "SELECT typeKey, nameEn, technicalNotes, isUserDefined FROM equipment_types ORDER BY typeKey",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3)))
                }
            }
        }
        migrated.close()

        assertThat(rows).containsExactly(
            // The new column defaults to an empty JSON array, which decodes to an empty list.
            listOf("FFE_PORTABLE_EXTINGUISHER", "Portable extinguisher", "[]", 0),
            listOf("USER_MY_OWN_THING", "Spare gear locker", "[]", 1),
        )
    }

    /** A version-1 `equipment_types` row: the column list is the schema before the migration. */
    private fun insertTypeV1(typeKey: String, nameEn: String, isUserDefined: Int): String = """
        INSERT INTO equipment_types (
            typeKey, typeGroup, subGroup, nameEn, nameTr, symbolKey, defaultTagPrefix,
            attributeSchema, taskKeys, regulationRefs, helpTextEn, helpTextTr,
            commonPscFindings, isUserDefined
        ) VALUES (
            '$typeKey', 'FFE', 'Portable', '$nameEn', '', 'FES001', 'FE',
            '[]', '[]', '[]', '', '', '[]', $isUserDefined
        )
    """.trimIndent()

    private companion object {
        const val TEST_DB = "migration-harness.db"
    }
}
