package com.deckwatch.core.database

import androidx.room.withTransaction

/**
 * Runs a block inside one database transaction without exposing [DeckWatchDatabase] itself.
 *
 * `data-repository` writes across several DAOs at once — the due engine rewrites a whole item's
 * open task instances and then the denormalised `equipment.nextDueDate` (§11.1 step 5) — and those
 * writes must land together or not at all. Injecting the database type to get `withTransaction`
 * would undo the separation `DaoModule` keeps, so the transaction itself is the injected capability.
 */
interface TransactionRunner {
    suspend operator fun <T> invoke(block: suspend () -> T): T
}

/** The production runner: one Room transaction per call, re-entrant like Room's own. */
class RoomTransactionRunner(
    private val database: DeckWatchDatabase,
) : TransactionRunner {
    override suspend fun <T> invoke(block: suspend () -> T): T = database.withTransaction(block)
}
