package com.deckwatch.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration, in order — MASTER_PROMPT C10.
 *
 * There is no `fallbackToDestructiveMigration` in this project, so a version bump without a
 * migration here is a crash on the officer's phone, not a silent wipe. That is the intended
 * trade: a vessel's register is not something to lose quietly.
 */

/**
 * 1 → 2: the equipment guide of §9.1.
 *
 * `equipment_types.technicalNotes` holds the construction, figures and type-specific tests shown
 * on an equipment type's page. It is added `NOT NULL DEFAULT '[]'` — an empty JSON array — so
 * every existing row is valid the instant the column exists; the next content import fills the
 * bundled types in. Nothing else changes, and no user data is touched: `equipment_types` holds
 * bundled catalogue rows plus the officer's own types, and neither loses a field here.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE equipment_types ADD COLUMN technicalNotes TEXT NOT NULL DEFAULT '[]'")
    }
}

/** In ascending order; registered by [createDeckWatchDatabase]. */
val DECKWATCH_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2)
