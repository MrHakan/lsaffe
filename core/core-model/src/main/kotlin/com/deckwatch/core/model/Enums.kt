package com.deckwatch.core.model

import kotlinx.serialization.Serializable

/** Quick-action condition grades. Colours are fixed in the design system. */
@Serializable
enum class ConditionGrade(val score: Int) {
    GOOD(4),
    ACCEPTABLE(3),
    MONITOR(2),
    DEFECTIVE(1),
    OUT_OF_SERVICE(0),
    NOT_CHECKED(-1),
}

@Serializable
enum class Severity { OBSERVATION, MINOR, MAJOR, CRITICAL_DETAINABLE }

@Serializable
enum class TaskStatus { PENDING, DUE_SOON, OVERDUE, DONE, SKIPPED, NOT_APPLICABLE }

@Serializable
enum class DeficiencyStatus { OPEN, IN_PROGRESS, CLOSED, DEFERRED_TO_OFFICE }

@Serializable
enum class FlagState { MARSHALL_ISLANDS, LIBERIA, PANAMA, OTHER }

@Serializable
enum class ClassSociety { DNV, LR, ABS, BV, CLASSNK, RINA, KR, CCS, IRS, OTHER }

@Serializable
enum class VesselType {
    BULK_CARRIER, TANKER_OIL, TANKER_CHEM, TANKER_LPG,
    CONTAINER, GENERAL_CARGO, RORO, PASSENGER, OFFSHORE, OTHER,
}

@Serializable
enum class StatusFlag {
    IN_SERVICE, OUT_OF_SERVICE, LANDED_ASHORE, AWAITING_SPARE, CONDEMNED, SPARE_STOCK,
}

@Serializable
enum class IntervalKind {
    WEEKLY, MONTHLY, QUARTERLY, ANNUAL, BIENNIAL,
    FIVE_YEARLY, TEN_YEARLY, TWENTY_YEARLY,
    AT_SURVEY, CUSTOM_MONTHS, EVENT_DRIVEN,
}

@Serializable
enum class PerformedBy {
    SHIP_STAFF, SHIP_STAFF_TRAINED,
    AUTHORISED_SERVICE_PROVIDER, MANUFACTURER,
    RO_SURVEYOR_ATTENDING, SHORE_FACILITY,
}

@Serializable
enum class PlanShape { RECTANGLE, SHIP_HULL, L_SHAPE, CUSTOM_POLYGON }

/** Content-accuracy state of a bundled regulatory statement — §8.5. */
@Serializable
enum class VerificationStatus { VERIFIED, UNVERIFIED, NEEDS_PERIODIC_REVIEW }

/** Top-level equipment catalogue groups — §7.5 step 2. */
@Serializable
enum class EquipmentGroup { LSA, FFE, EMERGENCY_ESCAPE, MACHINERY_CONTROLS, SIGNAGE, OTHER }

/** Notes-tab sections — §8.1. */
@Serializable
enum class RegulationSection { SOLAS, LSA, FFE, FLAG, CLASS, MY_NOTES }

/** Attribute field kinds — §9.3. */
@Serializable
enum class AttributeKind {
    TEXT, NUMBER, DECIMAL, DATE, BOOLEAN, ENUM, MULTI_ENUM, PRESSURE, WEIGHT, PHOTO, SIGNATURE,
}

@Serializable
enum class ThemeMode { DAY, NIGHT, BRIDGE }

@Serializable
enum class AppLanguage { ENGLISH, TURKISH }

@Serializable
enum class ListDensity { COMPACT, COMFORTABLE }
