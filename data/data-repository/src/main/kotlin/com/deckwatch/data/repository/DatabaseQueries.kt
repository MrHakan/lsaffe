package com.deckwatch.data.repository

import com.deckwatch.core.database.DeckWatchDatabase

/**
 * Reads a single-column list of ids with a raw query.
 *
 * The DAOs in `core-database` deliberately expose per-vessel *observation* as `Flow` and per-row
 * deletion by id; collecting a `Flow` from inside an open write transaction would have to wait on
 * a second connection, so purging a vessel's contents (`VesselRepositoryImpl.deleteVessel`,
 * `DemoVesselInstaller.uninstall`) needs a plain, in-transaction read instead. Every write still
 * goes through a DAO — this is the only raw statement in the module, and it is a read.
 */
internal fun DeckWatchDatabase.idsWhere(sql: String, vararg args: Any?): List<String> =
    query(sql, args).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                if (!cursor.isNull(0)) add(cursor.getString(0))
            }
        }
    }
