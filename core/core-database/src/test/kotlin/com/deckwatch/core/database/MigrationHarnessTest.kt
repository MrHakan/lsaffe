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
 * At version 1 there is nothing to migrate, so this test proves the two things that make future
 * migration tests possible at all, and which break silently if someone changes the build:
 * 1. The schema JSON for the current version is actually **exported and packaged** where
 *    [MigrationTestHelper] can find it. If `exportSchema` were turned off, or the schemas stopped
 *    being added to the test assets, `createDatabase` fails here rather than on the day someone
 *    writes migration 1 → 2 and has no baseline to migrate from.
 * 2. A database created from that schema validates against the entity definitions in code, so the
 *    committed JSON and the `@Entity` classes cannot drift apart.
 *
 * **Adding version 2:** bump [DeckWatchDatabase.VERSION], commit the new `2.json`, add the
 * `Migration(1, 2)` to the database builder, and add a test here that calls
 * `helper.createDatabase(TEST_DB, 1)`, inserts a representative row of every table the migration
 * touches, closes it, then `helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)` and
 * asserts the data survived. Validating is not enough on its own — C10 is about not losing data,
 * so assert on the rows, not only on the schema.
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
    fun `version 1 schema is exported, packaged and consistent with the entities`() {
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

    private companion object {
        const val TEST_DB = "migration-harness.db"
    }
}
