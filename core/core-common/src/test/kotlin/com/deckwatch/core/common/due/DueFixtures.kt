package com.deckwatch.core.common.due

import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import java.time.LocalDate

/** Test-local fixtures for the due engine. Kept out of core-testing so the engine's own tests carry no module dependency. */

internal fun day(year: Int, month: Int, dayOfMonth: Int): Long =
    LocalDate.of(year, month, dayOfMonth).toEpochDay()

internal fun dateOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

internal fun taskDefinition(
    key: String = "TASK",
    intervalKind: IntervalKind = IntervalKind.MONTHLY,
    intervalMonths: Int? = null,
    toleranceDaysBefore: Int = 0,
    toleranceDaysAfter: Int = 0,
    performedBy: PerformedBy = PerformedBy.SHIP_STAFF,
    appliesToTypeKeys: List<String> = listOf("TYPE"),
): TaskDefinition = TaskDefinition(
    key = key,
    appliesToTypeKeys = appliesToTypeKeys,
    titleEn = key,
    titleTr = key,
    intervalKind = intervalKind,
    intervalMonths = intervalMonths,
    toleranceDaysBefore = toleranceDaysBefore,
    toleranceDaysAfter = toleranceDaysAfter,
    performedBy = performedBy,
)

internal fun attribute(
    key: String,
    kind: AttributeKind = AttributeKind.ENUM,
    affectsTasks: Boolean = true,
    taskKeysByValue: Map<String, List<String>> = emptyMap(),
): AttributeDefinition = AttributeDefinition(
    key = key,
    kind = kind,
    labelEn = key,
    affectsTasks = affectsTasks,
    taskKeysByValue = taskKeysByValue,
)

internal fun equipmentType(
    typeKey: String = "TYPE",
    taskKeys: List<String> = emptyList(),
    attributeSchema: List<AttributeDefinition> = emptyList(),
): EquipmentType = EquipmentType(
    typeKey = typeKey,
    group = EquipmentGroup.FFE,
    subGroup = "PORTABLE_APPLIANCES",
    nameEn = typeKey,
    nameTr = typeKey,
    symbolKey = "FES001",
    defaultTagPrefix = "FE",
    attributeSchema = attributeSchema,
    taskKeys = taskKeys,
)

internal fun equipment(
    id: String = "eq-1",
    typeKey: String = "TYPE",
    attributesJson: String = "{}",
    installedDate: Long? = null,
    manufactureDate: Long? = null,
): Equipment = Equipment(
    id = id,
    vesselId = "vessel-1",
    typeKey = typeKey,
    symbolKey = "FES001",
    tag = "FE-UD-01",
    attributesJson = attributesJson,
    installedDate = installedDate,
    manufactureDate = manufactureDate,
    createdAt = 0L,
    updatedAt = 0L,
)

internal fun taskInstance(
    id: String,
    taskKey: String,
    dueDate: Long,
    status: TaskStatus = TaskStatus.PENDING,
    equipmentId: String = "eq-1",
    completedDate: Long? = null,
    windowOpens: Long = dueDate,
    windowCloses: Long = dueDate,
): TaskInstance = TaskInstance(
    id = id,
    equipmentId = equipmentId,
    taskKey = taskKey,
    dueDate = dueDate,
    windowOpens = windowOpens,
    windowCloses = windowCloses,
    status = status,
    completedDate = completedDate,
    createdAt = 0L,
    updatedAt = 0L,
)

/** The §9.3 worked example: a portable extinguisher whose medium re-derives the task set. */
internal object Extinguisher {
    const val BASE_MONTHLY = "FE_MONTHLY_INSPECTION"
    const val BASE_ANNUAL = "FE_ANNUAL_SERVICE"
    const val CO2_WEIGHT = "FE_CO2_CYLINDER_WEIGHT_CHECK"
    const val POWDER_CAKING = "FE_POWDER_CONDITION_CHECK"
    const val RMI_HYDRO = "FE_HYDROSTATIC_RO_FACILITY"

    val type: EquipmentType = equipmentType(
        typeKey = "FFE_PORTABLE_EXTINGUISHER",
        taskKeys = listOf(BASE_MONTHLY, BASE_ANNUAL),
        attributeSchema = listOf(
            attribute(
                key = "extinguishingMedium",
                taskKeysByValue = mapOf(
                    "CO2" to listOf(CO2_WEIGHT),
                    "DRY_POWDER_ABC" to listOf(POWDER_CAKING),
                ),
            ),
            attribute(key = "pressureType", affectsTasks = false),
        ),
    )
}
