package com.deckwatch.core.database.converter

import androidx.room.TypeConverter
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.ClassSociety
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.StatusFlag
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.VerificationStatus
import com.deckwatch.core.model.VesselType
import kotlinx.serialization.json.Json

/**
 * The single JSON codec used for every structured column in the database.
 *
 * `ignoreUnknownKeys` keeps a database written by a newer build readable by an older one, which
 * matters because a full-vessel export (§13) can be imported onto a phone running an older
 * version. `encodeDefaults` keeps the stored JSON self-describing rather than relying on the
 * defaults of whichever model version reads it back.
 */
internal val databaseJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Room converters for every non-primitive column type in the schema.
 *
 * Two rules, both deliberate:
 * - **Enums are stored by `name`, never by ordinal.** An ordinal silently changes meaning when an
 *   enum constant is inserted; a name does not, and a name is also what the HTML/JSON export
 *   (§13) writes, so the same token appears in the file and in the table.
 * - **Structured values are stored as kotlinx.serialization JSON text.** They are read whole and
 *   never queried by their internals, so a text column costs nothing and keeps the schema flat.
 *
 * Every converter is declared with non-null parameter and return types. Room wraps them in a null
 * check for nullable columns, so `ClassSociety?` and `Map<String, String>?` need no second
 * overload.
 */
class DeckWatchTypeConverters {

    // ---- Enums, stored as name strings -------------------------------------------------------

    @TypeConverter
    fun fromConditionGrade(value: ConditionGrade): String = value.name

    @TypeConverter
    fun toConditionGrade(value: String): ConditionGrade = ConditionGrade.valueOf(value)

    @TypeConverter
    fun fromSeverity(value: Severity): String = value.name

    @TypeConverter
    fun toSeverity(value: String): Severity = Severity.valueOf(value)

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromDeficiencyStatus(value: DeficiencyStatus): String = value.name

    @TypeConverter
    fun toDeficiencyStatus(value: String): DeficiencyStatus = DeficiencyStatus.valueOf(value)

    @TypeConverter
    fun fromFlagState(value: FlagState): String = value.name

    @TypeConverter
    fun toFlagState(value: String): FlagState = FlagState.valueOf(value)

    @TypeConverter
    fun fromClassSociety(value: ClassSociety): String = value.name

    @TypeConverter
    fun toClassSociety(value: String): ClassSociety = ClassSociety.valueOf(value)

    @TypeConverter
    fun fromVesselType(value: VesselType): String = value.name

    @TypeConverter
    fun toVesselType(value: String): VesselType = VesselType.valueOf(value)

    @TypeConverter
    fun fromStatusFlag(value: StatusFlag): String = value.name

    @TypeConverter
    fun toStatusFlag(value: String): StatusFlag = StatusFlag.valueOf(value)

    @TypeConverter
    fun fromIntervalKind(value: IntervalKind): String = value.name

    @TypeConverter
    fun toIntervalKind(value: String): IntervalKind = IntervalKind.valueOf(value)

    @TypeConverter
    fun fromPerformedBy(value: PerformedBy): String = value.name

    @TypeConverter
    fun toPerformedBy(value: String): PerformedBy = PerformedBy.valueOf(value)

    @TypeConverter
    fun fromVerificationStatus(value: VerificationStatus): String = value.name

    @TypeConverter
    fun toVerificationStatus(value: String): VerificationStatus = VerificationStatus.valueOf(value)

    @TypeConverter
    fun fromEquipmentGroup(value: EquipmentGroup): String = value.name

    @TypeConverter
    fun toEquipmentGroup(value: String): EquipmentGroup = EquipmentGroup.valueOf(value)

    @TypeConverter
    fun fromRegulationSection(value: RegulationSection): String = value.name

    @TypeConverter
    fun toRegulationSection(value: String): RegulationSection = RegulationSection.valueOf(value)

    // ---- Structured values, stored as JSON text ----------------------------------------------

    @TypeConverter
    fun fromStringList(value: List<String>): String = databaseJson.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = databaseJson.decodeFromString(value)

    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String = databaseJson.encodeToString(value)

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> = databaseJson.decodeFromString(value)

    @TypeConverter
    fun fromPlanPointList(value: List<PlanPoint>): String = databaseJson.encodeToString(value)

    @TypeConverter
    fun toPlanPointList(value: String): List<PlanPoint> = databaseJson.decodeFromString(value)

    @TypeConverter
    fun fromDeckPlan(value: DeckPlan): String = databaseJson.encodeToString(value)

    @TypeConverter
    fun toDeckPlan(value: String): DeckPlan = databaseJson.decodeFromString(value)

    @TypeConverter
    fun fromAttributeDefinitionList(value: List<AttributeDefinition>): String =
        databaseJson.encodeToString(value)

    @TypeConverter
    fun toAttributeDefinitionList(value: String): List<AttributeDefinition> =
        databaseJson.decodeFromString(value)

    @TypeConverter
    fun fromEquipmentGroupList(value: List<EquipmentGroup>): String =
        databaseJson.encodeToString(value)

    @TypeConverter
    fun toEquipmentGroupList(value: String): List<EquipmentGroup> =
        databaseJson.decodeFromString(value)
}
