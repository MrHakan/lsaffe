package com.deckwatch.data.repository

import com.deckwatch.core.common.Dates
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two pieces of ambient state the repositories need — new ids and the current time — behind
 * interfaces, so a repository test can pin both and assert on exact values.
 *
 * The due engine takes the same view (`DueEngine(today = ...)`, §11): nothing that decides what a
 * row looks like is allowed to read the system clock directly.
 */
interface IdFactory {
    fun newId(): String
}

interface AppClock {
    /** Epoch-millis, for `createdAt` / `updatedAt` stamps. */
    fun nowMillis(): Long

    /** Epoch-days, the unit every date column in the schema uses (§6). */
    fun todayEpochDay(): Long
}

@Singleton
class UuidIdFactory @Inject constructor() : IdFactory {
    override fun newId(): String = UUID.randomUUID().toString()
}

@Singleton
class SystemAppClock @Inject constructor() : AppClock {
    override fun nowMillis(): Long = Dates.nowMillis()

    override fun todayEpochDay(): Long = Dates.todayEpochDay()
}
