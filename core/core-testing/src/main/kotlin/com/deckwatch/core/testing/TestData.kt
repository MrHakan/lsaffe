package com.deckwatch.core.testing

import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.Category
import com.deckwatch.core.model.ClassSociety
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.StatusFlag
import com.deckwatch.core.model.SymbolInfo
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TechnicalNote
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.UserNote
import com.deckwatch.core.model.VerificationStatus
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.VesselType
import com.deckwatch.core.model.Zone
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fixture builders for every domain model — every parameter has a sensible default, so a test
 * names only the fields it cares about.
 *
 * Ids are deterministic within a run (`vessel-1`, `deck-2`, …) so failure messages are readable.
 * Dates default to a fixed reference day rather than "now", so a fixture never drifts under the
 * due engine.
 */
object TestData {

    /** The fixed "today" these fixtures are built around: 1 January 2026. */
    val referenceDay: Long = LocalDate.of(2026, 1, 1).toEpochDay()

    /** [referenceDay] as epoch-millis, for `createdAt` / `updatedAt`. */
    val referenceMillis: Long = referenceDay * MILLIS_PER_DAY

    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    /** A readable, deterministic id: `vessel-1`, `vessel-2`, … */
    fun id(prefix: String): String =
        "$prefix-${counters.getOrPut(prefix) { AtomicInteger() }.incrementAndGet()}"

    /** Restart every id counter. Call from a `@BeforeEach` when a test asserts on literal ids. */
    fun resetIds() {
        counters.clear()
    }

    /** Epoch-day for a calendar date — all DeckWatch dates are epoch-days (§6). */
    fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    // ------------------------------------------------------------------ §6.1 vessel

    fun vessel(
        id: String = id("vessel"),
        name: String = "MV Example",
        imoNumber: String? = "9074729",
        callSign: String? = "V7EX1",
        flag: FlagState = FlagState.MARSHALL_ISLANDS,
        classSociety: ClassSociety? = ClassSociety.DNV,
        vesselType: VesselType = VesselType.BULK_CARRIER,
        grossTonnage: Int? = 32_000,
        buildDate: Long? = day(2012, 5, 20),
        safetyEquipmentCertExpiry: Long? = day(2027, 3, 15),
        lastAnnualSurveyDate: Long? = day(2026, 3, 10),
        isActive: Boolean = true,
        createdAt: Long = referenceMillis,
        updatedAt: Long = referenceMillis,
    ): Vessel = Vessel(
        id = id,
        name = name,
        imoNumber = imoNumber,
        callSign = callSign,
        flag = flag,
        classSociety = classSociety,
        vesselType = vesselType,
        grossTonnage = grossTonnage,
        buildDate = buildDate,
        safetyEquipmentCertExpiry = safetyEquipmentCertExpiry,
        lastAnnualSurveyDate = lastAnnualSurveyDate,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    // ------------------------------------------------------------------ §6.2 deck

    fun deck(
        id: String = id("deck"),
        vesselId: String = "vessel-1",
        name: String = "Upper Deck",
        shortCode: String? = "UD",
        levelIndex: Int = 0,
        plan: DeckPlan = deckPlan(),
        colorTint: Int? = null,
        notes: String? = null,
        createdAt: Long = referenceMillis,
        updatedAt: Long = referenceMillis,
    ): Deck = Deck(
        id = id,
        vesselId = vesselId,
        name = name,
        shortCode = shortCode,
        levelIndex = levelIndex,
        plan = plan,
        colorTint = colorTint,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /** §6.3 — the default is the bulker main-deck-shaped hull outline. */
    fun deckPlan(
        shape: PlanShape = PlanShape.SHIP_HULL,
        lengthRatio: Float = 1.0f,
        breadthRatio: Float = 1.0f,
        polygon: List<PlanPoint> = emptyList(),
    ): DeckPlan = DeckPlan(
        shape = shape,
        lengthRatio = lengthRatio,
        breadthRatio = breadthRatio,
        polygon = polygon,
    )

    fun zone(
        id: String = id("zone"),
        deckId: String = "deck-1",
        name: String = "Fwd Mooring Station",
        polygon: List<PlanPoint> = listOf(
            PlanPoint(0.1f, 0.1f),
            PlanPoint(0.9f, 0.1f),
            PlanPoint(0.9f, 0.4f),
            PlanPoint(0.1f, 0.4f),
        ),
        colorArgb: Int = 0x332196F3,
        sortOrder: Int = 0,
    ): Zone = Zone(id, deckId, name, polygon, colorArgb, sortOrder)

    fun category(
        id: String = id("category"),
        vesselId: String? = null,
        name: String = "Weekly Round",
        colorArgb: Int = 0xFF1B873F.toInt(),
        iconKey: String? = null,
        sortOrder: Int = 0,
    ): Category = Category(id, vesselId, name, colorArgb, iconKey, sortOrder)

    // ------------------------------------------------------------------ §6.5 equipment

    fun equipment(
        id: String = id("equipment"),
        vesselId: String = "vessel-1",
        deckId: String? = "deck-1",
        zoneId: String? = null,
        parentId: String? = null,
        typeKey: String = "FFE_PORTABLE_EXTINGUISHER",
        symbolKey: String = "FES001",
        tag: String = "FE-UD-01",
        name: String? = null,
        location: String? = "Stbd side, aft of provision crane",
        posX: Float = 0.5f,
        posY: Float = 0.5f,
        makerName: String? = "Example Maker",
        modelName: String? = "EX-6",
        serialNumber: String? = null,
        manufactureDate: Long? = day(2020, 6, 1),
        installedDate: Long? = day(2020, 9, 1),
        quantity: Int = 1,
        condition: ConditionGrade = ConditionGrade.GOOD,
        statusFlag: StatusFlag = StatusFlag.IN_SERVICE,
        attributesJson: String = """{"extinguishingMedium":"DRY_POWDER_ABC"}""",
        nextDueDate: Long? = null,
        nextDueTaskKey: String? = null,
        createdAt: Long = referenceMillis,
        updatedAt: Long = referenceMillis,
        deletedAt: Long? = null,
    ): Equipment = Equipment(
        id = id,
        vesselId = vesselId,
        deckId = deckId,
        zoneId = zoneId,
        parentId = parentId,
        typeKey = typeKey,
        symbolKey = symbolKey,
        tag = tag,
        name = name,
        location = location,
        posX = posX,
        posY = posY,
        makerName = makerName,
        modelName = modelName,
        serialNumber = serialNumber,
        manufactureDate = manufactureDate,
        installedDate = installedDate,
        quantity = quantity,
        condition = condition,
        statusFlag = statusFlag,
        attributesJson = attributesJson,
        nextDueDate = nextDueDate,
        nextDueTaskKey = nextDueTaskKey,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    // ------------------------------------------------------------------ §9 catalogue

    fun attributeDefinition(
        key: String = "extinguishingMedium",
        kind: AttributeKind = AttributeKind.ENUM,
        labelEn: String = "Extinguishing medium",
        labelTr: String = "Söndürücü madde",
        required: Boolean = true,
        options: List<String> = listOf("WATER", "FOAM_AFFF", "DRY_POWDER_ABC", "CO2"),
        affectsTasks: Boolean = true,
        monthlyChecklist: Boolean = false,
        taskKeysByValue: Map<String, List<String>> = mapOf(
            "CO2" to listOf("FE_CO2_CYLINDER_WEIGHT_CHECK"),
            "DRY_POWDER_ABC" to listOf("FE_POWDER_CONDITION_CHECK"),
        ),
    ): AttributeDefinition = AttributeDefinition(
        key = key,
        kind = kind,
        labelEn = labelEn,
        labelTr = labelTr,
        required = required,
        options = options,
        affectsTasks = affectsTasks,
        monthlyChecklist = monthlyChecklist,
        taskKeysByValue = taskKeysByValue,
    )

    fun equipmentType(
        typeKey: String = "FFE_PORTABLE_EXTINGUISHER",
        group: EquipmentGroup = EquipmentGroup.FFE,
        subGroup: String = "PORTABLE_APPLIANCES",
        nameEn: String = "Portable fire extinguisher",
        nameTr: String = "Portatif yangın söndürücü",
        symbolKey: String = "FES001",
        defaultTagPrefix: String = "FE",
        attributeSchema: List<AttributeDefinition> = listOf(attributeDefinition()),
        taskKeys: List<String> = listOf("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE"),
        regulationRefs: List<String> = listOf("SOLAS_II2_10_3"),
        technicalNotes: List<TechnicalNote> = emptyList(),
        isUserDefined: Boolean = false,
    ): EquipmentType = EquipmentType(
        typeKey = typeKey,
        group = group,
        subGroup = subGroup,
        nameEn = nameEn,
        nameTr = nameTr,
        symbolKey = symbolKey,
        defaultTagPrefix = defaultTagPrefix,
        attributeSchema = attributeSchema,
        taskKeys = taskKeys,
        regulationRefs = regulationRefs,
        technicalNotes = technicalNotes,
        isUserDefined = isUserDefined,
    )

    fun symbolInfo(
        key: String = "FES001",
        nameEn: String = "Fire extinguisher",
        nameTr: String = "Yangın söndürücü",
        series: String = "FES",
        mediaTintable: Boolean = false,
    ): SymbolInfo = SymbolInfo(key, nameEn, nameTr, series, mediaTintable)

    // ------------------------------------------------------------------ §6.6 tasks

    fun taskDefinition(
        key: String = "FE_MONTHLY_INSPECTION",
        appliesToTypeKeys: List<String> = listOf("FFE_PORTABLE_EXTINGUISHER"),
        titleEn: String = "Portable fire extinguisher — monthly check",
        titleTr: String = "Portatif yangın söndürücü — aylık kontrol",
        descriptionEn: String = "Visual check of pressure, seal, access and stowage.",
        intervalKind: IntervalKind = IntervalKind.MONTHLY,
        intervalMonths: Int? = null,
        toleranceDaysBefore: Int = 0,
        toleranceDaysAfter: Int = 0,
        performedBy: PerformedBy = PerformedBy.SHIP_STAFF,
        evidenceRequired: List<String> = listOf("Signed checklist"),
        regulationRefs: List<String> = listOf("MSC1_CIRC1432_EXTINGUISHERS"),
        flagOverrides: Map<String, String>? = null,
        sourceRef: String = "SOLAS II-2/14; MSC.1/Circ.1432",
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
        isUserDefined: Boolean = false,
    ): TaskDefinition = TaskDefinition(
        key = key,
        appliesToTypeKeys = appliesToTypeKeys,
        titleEn = titleEn,
        titleTr = titleTr,
        descriptionEn = descriptionEn,
        intervalKind = intervalKind,
        intervalMonths = intervalMonths,
        toleranceDaysBefore = toleranceDaysBefore,
        toleranceDaysAfter = toleranceDaysAfter,
        performedBy = performedBy,
        evidenceRequired = evidenceRequired,
        regulationRefs = regulationRefs,
        flagOverrides = flagOverrides,
        sourceRef = sourceRef,
        verificationStatus = verificationStatus,
        isUserDefined = isUserDefined,
    )

    fun taskInstance(
        id: String = id("instance"),
        equipmentId: String = "equipment-1",
        taskKey: String = "FE_MONTHLY_INSPECTION",
        dueDate: Long = referenceDay,
        windowOpens: Long = dueDate,
        windowCloses: Long = dueDate,
        status: TaskStatus = TaskStatus.PENDING,
        completedDate: Long? = null,
        completedBy: String? = null,
        serviceProvider: String? = null,
        certificateNumber: String? = null,
        findings: String? = null,
        conditionAfter: ConditionGrade? = null,
        createdAt: Long = referenceMillis,
        updatedAt: Long = referenceMillis,
    ): TaskInstance = TaskInstance(
        id = id,
        equipmentId = equipmentId,
        taskKey = taskKey,
        dueDate = dueDate,
        windowOpens = windowOpens,
        windowCloses = windowCloses,
        status = status,
        completedDate = completedDate,
        completedBy = completedBy,
        serviceProvider = serviceProvider,
        certificateNumber = certificateNumber,
        findings = findings,
        conditionAfter = conditionAfter,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    // ------------------------------------------------------------------ §6.7 / §6.8

    fun round(
        id: String = id("round"),
        vesselId: String = "vessel-1",
        templateKey: String = "WEEKLY_LSA",
        title: String = "Weekly LSA round",
        startedAt: Long = referenceMillis,
        completedAt: Long? = null,
        performedBy: String = "3/O",
        itemCount: Int = 0,
        doneCount: Int = 0,
        deficiencyCount: Int = 0,
    ): Round = Round(
        id = id,
        vesselId = vesselId,
        templateKey = templateKey,
        title = title,
        startedAt = startedAt,
        completedAt = completedAt,
        performedBy = performedBy,
        itemCount = itemCount,
        doneCount = doneCount,
        deficiencyCount = deficiencyCount,
    )

    fun roundItem(
        id: String = id("roundItem"),
        roundId: String = "round-1",
        equipmentId: String = "equipment-1",
        checkedAt: Long? = null,
        condition: ConditionGrade? = null,
        remark: String? = null,
    ): RoundItem = RoundItem(id, roundId, equipmentId, checkedAt, condition, remark)

    fun roundTemplate(
        key: String = "WEEKLY_LSA",
        titleEn: String = "Weekly LSA round",
        titleTr: String = "Haftalık LSA turu",
        includesGroups: List<EquipmentGroup> = listOf(EquipmentGroup.LSA),
    ): RoundTemplate = RoundTemplate(
        key = key,
        titleEn = titleEn,
        titleTr = titleTr,
        includesGroups = includesGroups,
    )

    fun deficiency(
        id: String = id("deficiency"),
        vesselId: String = "vessel-1",
        equipmentId: String? = "equipment-1",
        raisedDate: Long = referenceDay,
        raisedBy: String = "3/O",
        severity: Severity = Severity.MINOR,
        title: String = "Pressure gauge outside green band",
        description: String = "Gauge reads below the green band; extinguisher landed for service.",
        targetDate: Long? = null,
        status: DeficiencyStatus = DeficiencyStatus.OPEN,
    ): Deficiency = Deficiency(
        id = id,
        vesselId = vesselId,
        equipmentId = equipmentId,
        raisedDate = raisedDate,
        raisedBy = raisedBy,
        severity = severity,
        title = title,
        description = description,
        targetDate = targetDate,
        status = status,
    )

    // ------------------------------------------------------------------ §8 reference content

    fun regulationCard(
        refKey: String = "SOLAS_III_20_6",
        section: RegulationSection = RegulationSection.SOLAS,
        citation: String = "SOLAS III/20.6",
        title: String = "Weekly inspection of survival craft",
        what: String = "Survival craft, launching appliances and release gear are inspected weekly.",
        howOften: String = "Weekly",
        byWhom: String = "Ship's staff",
        evidence: String = "Log book entry, signed",
        detailBullets: List<String> = listOf(
            "All lifeboats and rescue boats visually inspected.",
            "Lifeboat engines run ahead and astern.",
            "General emergency alarm tested.",
        ),
        flagNotes: Map<String, String> = emptyMap(),
        appliesToTypeKeys: List<String> = emptyList(),
        sourceRef: String = "SOLAS Chapter III, Regulation 20",
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    ): RegulationCard = RegulationCard(
        refKey = refKey,
        section = section,
        citation = citation,
        title = title,
        what = what,
        howOften = howOften,
        byWhom = byWhom,
        evidence = evidence,
        detailBullets = detailBullets,
        flagNotes = flagNotes,
        appliesToTypeKeys = appliesToTypeKeys,
        sourceRef = sourceRef,
        verificationStatus = verificationStatus,
    )

    fun userNote(
        id: String = id("note"),
        title: String = "Extinguisher service contact",
        body: String = "Rotterdam service provider — see the agent's list.",
        folder: String = "",
        regulationRefKey: String? = null,
        equipmentTypeKey: String? = null,
        isFavourite: Boolean = false,
        createdAt: Long = referenceMillis,
        updatedAt: Long = referenceMillis,
    ): UserNote = UserNote(
        id = id,
        title = title,
        body = body,
        folder = folder,
        regulationRefKey = regulationRefKey,
        equipmentTypeKey = equipmentTypeKey,
        isFavourite = isFavourite,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private const val MILLIS_PER_DAY = 86_400_000L
}
