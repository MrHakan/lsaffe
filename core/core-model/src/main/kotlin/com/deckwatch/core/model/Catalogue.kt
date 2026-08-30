package com.deckwatch.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One attribute definition in a type's dynamic schema — §9.3. */
@Serializable
data class AttributeDefinition(
    val key: String,
    val kind: AttributeKind,
    val labelEn: String,
    val labelTr: String = "",
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    /** Changing this value re-derives the equipment's task set. */
    val affectsTasks: Boolean = false,
    /** True for booleans that belong to the type's monthly inspection checklist — §9.3. */
    val monthlyChecklist: Boolean = false,
    val unit: String? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val helpEn: String = "",
    val helpTr: String = "",
    /**
     * Optional mapping: attribute value -> extra task keys that apply when the
     * attribute has that value (only meaningful when [affectsTasks] is true).
     */
    val taskKeysByValue: Map<String, List<String>> = emptyMap(),
)

/**
 * One block of the equipment guide for a type — §9.1.
 *
 * This is the "what an officer needs to know about this thing" content: construction and markings,
 * the figures that get asked at an inspection, the tests that are specific to the type, and the
 * traps. It sits beside the regulation cards rather than inside them, because a card states a
 * requirement while this explains the equipment the requirement is about.
 *
 * English only, like the regulatory content it accompanies (C8): a paraphrase of a technical
 * figure into another language is where errors get introduced.
 */
@Serializable
data class TechnicalNote(
    val heading: String,
    val bullets: List<String> = emptyList(),
)

/** One entry in the bundled equipment type catalogue — §9.1. */
@Serializable
data class EquipmentType(
    val typeKey: String,
    val group: EquipmentGroup,
    val subGroup: String,
    val nameEn: String,
    val nameTr: String,
    val symbolKey: String,
    val defaultTagPrefix: String,
    val attributeSchema: List<AttributeDefinition> = emptyList(),
    val taskKeys: List<String> = emptyList(),
    val regulationRefs: List<String> = emptyList(),
    val helpTextEn: String = "",
    val helpTextTr: String = "",
    val commonPscFindings: List<String> = emptyList(),
    /** The equipment guide of §9.1: construction, figures, type-specific tests, and the traps. */
    val technicalNotes: List<TechnicalNote> = emptyList(),
    val isUserDefined: Boolean = false,
)

/** A bundled regulatory note card — §8.2. */
@Serializable
data class RegulationCard(
    val refKey: String,
    val section: RegulationSection,
    /** e.g. "SOLAS III/20.6" — shown as the card badge. */
    val citation: String,
    val title: String,
    /** WHAT — one-sentence plain statement. */
    val what: String,
    /** HOW OFTEN — colour-coded chip text, e.g. "Weekly". */
    val howOften: String,
    /** BY WHOM — chip text. */
    val byWhom: String,
    /** EVIDENCE — e.g. "Log book entry, signed". */
    val evidence: String,
    /** 3–8 short bullet points. */
    val detailBullets: List<String> = emptyList(),
    /** Flag code (RMI/LIB/PAN) -> difference note. Empty if no real difference. */
    val flagNotes: Map<String, String> = emptyMap(),
    /** Equipment type keys this card applies to. */
    val appliesToTypeKeys: List<String> = emptyList(),
    val sourceRef: String,
    val contentVersion: Int = 1,
    /** ISO date the content was last reviewed against the instrument. */
    val lastReviewed: String = "",
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    /** Optional Turkish plain-language summary — C8. */
    val summaryTr: String = "",
    /** For FLAG cards: the notice revision and capture date string. */
    val revisionNote: String = "",
)

/** The user's own note — §8.1 MY NOTES. */
@Serializable
data class UserNote(
    val id: String,
    val title: String,
    val body: String,
    val folder: String = "",
    /** Optional attachment to a regulation card. */
    val regulationRefKey: String? = null,
    /** Optional attachment to an equipment type. */
    val equipmentTypeKey: String? = null,
    val isFavourite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Metadata for one symbol in the library — §10. */
@Serializable
data class SymbolInfo(
    val key: String,
    val nameEn: String,
    val nameTr: String,
    /** LSS / FES / MES / EES / SIS / APP. */
    val series: String,
    /** Whether the pictogram may be tinted with a media colour — §10.3. */
    val mediaTintable: Boolean = false,
)

/** Parsed dynamic attribute values keyed by attribute key. */
typealias AttributeValues = JsonObject
