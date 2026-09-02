package com.deckwatch.feature.survivalcraft.drill

import com.deckwatch.core.model.Round
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * SOLAS III/19 drill records — §7.6.
 *
 * **Persistence choice.** A drill is a [Round], not a new table:
 *
 *  * `templateKey` = `DRILL_<parent typeKey>`, e.g. `DRILL_LSA_LIFEBOAT_TOTALLY_ENCLOSED`, so
 *    the vessel's round history already segregates drills from inspection rounds.
 *  * `startedAt` / `completedAt` = the drill **date**, converted to epoch-millis at UTC midnight,
 *    because `Round.startedAt` is epoch-millis everywhere else in the app while the officer picks
 *    an epoch-day in a `DateField`. [DrillLog.toMillis] / [DrillLog.toEpochDay] are the only
 *    places that conversion happens.
 *  * `performedBy` = who ran it.
 *  * `notes` = a small JSON object, [DrillNotes]: the craft's `equipmentId` (a `Round` has no
 *    equipment column, and a vessel has several boats), whether the craft was actually **launched**
 *    — the flag the days-since-last-launch counter of SOLAS III/19.3.3.3 runs on — and free
 *    remarks. Notes that are not JSON (hand-written, imported) degrade to `launched = false` with
 *    the raw text kept as the remark, so nothing is ever lost.
 */
@Serializable
data class DrillNotes(
    val equipmentId: String = "",
    val launched: Boolean = false,
    val remarks: String = "",
)

/** One drill as the panel shows it. */
data class DrillRecord(
    val roundId: String,
    val equipmentId: String,
    val dateEpochDay: Long,
    val performedBy: String,
    val launched: Boolean,
    val remarks: String,
    val title: String,
)

object DrillLog {

    private const val MILLIS_PER_DAY = 86_400_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** `DRILL_<typeKey>` — the template key every drill for this craft type is filed under. */
    fun templateKey(typeKey: String): String = "DRILL_$typeKey"

    fun toMillis(epochDay: Long): Long = epochDay * MILLIS_PER_DAY

    fun toEpochDay(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_DAY)

    fun encodeNotes(notes: DrillNotes): String = json.encodeToString(DrillNotes.serializer(), notes)

    /** Tolerant of legacy / hand-written notes: anything unparseable becomes the remark. */
    fun decodeNotes(raw: String?): DrillNotes {
        if (raw.isNullOrBlank()) return DrillNotes()
        return runCatching { json.decodeFromString(DrillNotes.serializer(), raw) }
            .getOrElse { DrillNotes(remarks = raw) }
    }

    /** The vessel's rounds filtered down to this craft's drills, newest first. */
    fun recordsFor(equipmentId: String, typeKey: String, rounds: List<Round>): List<DrillRecord> {
        val template = templateKey(typeKey)
        return rounds
            .filter { it.templateKey == template }
            .mapNotNull { round ->
                val notes = decodeNotes(round.notes)
                if (notes.equipmentId.isNotBlank() && notes.equipmentId != equipmentId) {
                    null
                } else {
                    DrillRecord(
                        roundId = round.id,
                        equipmentId = notes.equipmentId.ifBlank { equipmentId },
                        dateEpochDay = toEpochDay(round.startedAt),
                        performedBy = round.performedBy,
                        launched = notes.launched,
                        remarks = notes.remarks,
                        title = round.title,
                    )
                }
            }
            .sortedByDescending { it.dateEpochDay }
    }

    /**
     * Days since the craft was last **launched** (not merely swung out), or null when no launch
     * has ever been recorded. A future-dated record yields 0 rather than a negative count.
     */
    fun daysSinceLastLaunch(records: List<DrillRecord>, todayEpochDay: Long): Long? {
        val last = records.filter { it.launched }.maxOfOrNull { it.dateEpochDay } ?: return null
        return (todayEpochDay - last).coerceAtLeast(0L)
    }

    /** The most recent drill of any kind, launched or not. */
    fun lastDrillDay(records: List<DrillRecord>): Long? = records.maxOfOrNull { it.dateEpochDay }
}
