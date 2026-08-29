package com.deckwatch.core.database

import com.deckwatch.core.database.entity.CategoryEntity
import com.deckwatch.core.database.entity.DeckEntity
import com.deckwatch.core.database.entity.DeficiencyEntity
import com.deckwatch.core.database.entity.EquipmentEntity
import com.deckwatch.core.database.entity.EquipmentTypeEntity
import com.deckwatch.core.database.entity.RegulationCardEntity
import com.deckwatch.core.database.entity.RoundEntity
import com.deckwatch.core.database.entity.RoundItemEntity
import com.deckwatch.core.database.entity.RoundTemplateEntity
import com.deckwatch.core.database.entity.TaskDefinitionEntity
import com.deckwatch.core.database.entity.TaskInstanceEntity
import com.deckwatch.core.database.entity.UserNoteEntity
import com.deckwatch.core.database.entity.VesselEntity
import com.deckwatch.core.database.entity.ZoneEntity
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.ClassSociety
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.StatusFlag
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.VerificationStatus
import com.deckwatch.core.model.VesselType

/**
 * Row builders for the database tests. Every field is given a distinct, non-default value so a
 * round-trip that silently drops or transposes a column fails instead of passing on defaults.
 */
internal object Fixtures {

    const val VESSEL_ID = "vessel-1"
    const val DECK_ID = "deck-1"

    fun vessel(
        id: String = VESSEL_ID,
        name: String = "MV Example",
        isActive: Boolean = true,
    ): VesselEntity = VesselEntity(
        id = id,
        name = name,
        imoNumber = "9074729",
        callSign = "V7AB1",
        mmsi = "538001234",
        flag = FlagState.MARSHALL_ISLANDS,
        flagOtherName = null,
        classSociety = ClassSociety.DNV,
        vesselType = VesselType.BULK_CARRIER,
        grossTonnage = 38_500,
        buildDate = 12_000L,
        safetyEquipmentCertExpiry = 20_500L,
        lastAnnualSurveyDate = 20_100L,
        nextDrydockDate = 21_000L,
        isActive = isActive,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    fun deckPlan(): DeckPlan = DeckPlan(
        shape = PlanShape.CUSTOM_POLYGON,
        lengthRatio = 0.85f,
        breadthRatio = 0.6f,
        polygon = listOf(PlanPoint(0f, 0f), PlanPoint(1f, 0.25f), PlanPoint(0.5f, 1f)),
        bowSharpness = 0.7f,
        sternRounding = 0.2f,
        bowAtTop = false,
        backgroundImageUri = "content://ga-plan/upper-deck",
        backgroundOpacity = 0.45f,
    )

    fun deck(
        id: String = DECK_ID,
        vesselId: String = VESSEL_ID,
        levelIndex: Int = 0,
        name: String = "Upper Deck",
    ): DeckEntity = DeckEntity(
        id = id,
        vesselId = vesselId,
        name = name,
        shortCode = "UD",
        levelIndex = levelIndex,
        plan = deckPlan(),
        colorTint = -0x33aabb,
        notes = "Main working deck",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_100L,
    )

    fun zone(id: String = "zone-1", deckId: String = DECK_ID): ZoneEntity = ZoneEntity(
        id = id,
        deckId = deckId,
        name = "Fwd Mooring Station",
        polygon = listOf(PlanPoint(0.1f, 0.1f), PlanPoint(0.4f, 0.1f), PlanPoint(0.4f, 0.5f)),
        colorArgb = -0x11223344,
        sortOrder = 3,
    )

    fun category(id: String = "cat-1", vesselId: String? = VESSEL_ID): CategoryEntity =
        CategoryEntity(
            id = id,
            vesselId = vesselId,
            name = "PSC Focus Items",
            colorArgb = -0x556677,
            iconKey = "psc",
            sortOrder = 2,
        )

    fun equipment(
        id: String = "eq-1",
        vesselId: String = VESSEL_ID,
        deckId: String? = DECK_ID,
        parentId: String? = null,
        tag: String = "FE-UD-001",
        zoneId: String? = "zone-1",
        deletedAt: Long? = null,
        nextDueDate: Long? = 20_300L,
    ): EquipmentEntity = EquipmentEntity(
        id = id,
        vesselId = vesselId,
        deckId = deckId,
        zoneId = zoneId,
        parentId = parentId,
        typeKey = "FFE_PORTABLE_EXTINGUISHER",
        symbolKey = "FES001",
        tag = tag,
        name = "Extinguisher by the galley door",
        location = "Stbd side, aft of provision crane",
        posX = 0.31f,
        posY = 0.72f,
        rotationDeg = 45f,
        makerName = "Acme Marine",
        modelName = "AM-9",
        serialNumber = "SN-4471",
        typeApprovalNumber = "MED-B-1234",
        manufactureDate = 18_000L,
        installedDate = 18_100L,
        quantity = 2,
        condition = ConditionGrade.MONITOR,
        conditionSetAt = 1_700_000_500_000L,
        statusFlag = StatusFlag.IN_SERVICE,
        attributesJson = """{"extinguishingMedium":"CO2","capacityKg":5}""",
        nextDueDate = nextDueDate,
        nextDueTaskKey = "FE_MONTHLY_INSPECTION",
        photoUris = listOf("content://photo/1", "content://photo/2"),
        notes = "Bracket repainted 2026-03",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_600_000L,
        deletedAt = deletedAt,
    )

    fun taskDefinition(key: String = "FE_MONTHLY_INSPECTION"): TaskDefinitionEntity =
        TaskDefinitionEntity(
            key = key,
            appliesToTypeKeys = listOf("FFE_PORTABLE_EXTINGUISHER", "FFE_WHEELED_EXTINGUISHER"),
            titleEn = "Portable fire extinguisher — monthly check",
            titleTr = "Portatif yangın söndürücü — aylık kontrol",
            descriptionEn = "Visual check of gauge, seal, bracket, access and label.",
            intervalKind = IntervalKind.MONTHLY,
            intervalMonths = 1,
            toleranceDaysBefore = 7,
            toleranceDaysAfter = 14,
            performedBy = PerformedBy.SHIP_STAFF,
            evidenceRequired = listOf("Signed checklist"),
            regulationRefs = listOf("SOLAS_II2_14", "MSC1_CIRC1432_EXTINGUISHERS"),
            flagOverrides = mapOf("LIB" to "Trained crew may perform the annual service."),
            sourceRef = "MSC.1/Circ.1432",
            verificationStatus = VerificationStatus.UNVERIFIED,
            lastReviewed = "2026-08-29",
            isUserDefined = false,
        )

    fun taskInstance(
        id: String = "ti-1",
        equipmentId: String = "eq-1",
        status: TaskStatus = TaskStatus.PENDING,
        dueDate: Long = 20_300L,
    ): TaskInstanceEntity = TaskInstanceEntity(
        id = id,
        equipmentId = equipmentId,
        taskKey = "FE_MONTHLY_INSPECTION",
        dueDate = dueDate,
        windowOpens = dueDate - 7,
        windowCloses = dueDate + 14,
        status = status,
        completedDate = null,
        completedBy = null,
        serviceProvider = null,
        certificateNumber = null,
        findings = null,
        conditionAfter = null,
        photoUris = listOf("content://photo/3"),
        attachmentUris = listOf("content://doc/1"),
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    fun round(id: String = "round-1", vesselId: String = VESSEL_ID): RoundEntity = RoundEntity(
        id = id,
        vesselId = vesselId,
        templateKey = "WEEKLY_LSA",
        title = "Weekly LSA round",
        startedAt = 1_700_000_000_000L,
        completedAt = null,
        performedBy = "3/O Yilmaz",
        itemCount = 24,
        doneCount = 11,
        deficiencyCount = 2,
        notes = "Heavy weather, boat deck deferred",
    )

    fun roundItem(id: String = "ri-1", roundId: String = "round-1"): RoundItemEntity =
        RoundItemEntity(
            id = id,
            roundId = roundId,
            equipmentId = "eq-1",
            checkedAt = 1_700_000_900_000L,
            condition = ConditionGrade.ACCEPTABLE,
            remark = "Label faded but legible",
            photoUris = listOf("content://photo/9"),
        )

    fun deficiency(
        id: String = "def-1",
        vesselId: String = VESSEL_ID,
        status: DeficiencyStatus = DeficiencyStatus.OPEN,
    ): DeficiencyEntity = DeficiencyEntity(
        id = id,
        vesselId = vesselId,
        equipmentId = "eq-1",
        raisedDate = 20_200L,
        raisedBy = "3/O Yilmaz",
        severity = Severity.MAJOR,
        title = "Extinguisher gauge below green band",
        description = "Gauge reads 8 bar against a 12–15 bar band.",
        correctiveAction = "Land ashore for recharge",
        targetDate = 20_240L,
        closedDate = null,
        closedBy = null,
        status = status,
        sparePartRequired = "CO2 charge, 5 kg",
        photoUris = listOf("content://photo/11"),
    )

    fun equipmentType(typeKey: String = "FFE_PORTABLE_EXTINGUISHER"): EquipmentTypeEntity =
        EquipmentTypeEntity(
            typeKey = typeKey,
            group = EquipmentGroup.FFE,
            subGroup = "PORTABLE_APPLIANCES",
            nameEn = "Portable fire extinguisher",
            nameTr = "Portatif yangın söndürücü",
            symbolKey = "FES001",
            defaultTagPrefix = "FE",
            attributeSchema = listOf(
                AttributeDefinition(
                    key = "extinguishingMedium",
                    kind = AttributeKind.ENUM,
                    labelEn = "Extinguishing medium",
                    labelTr = "Söndürücü madde",
                    required = true,
                    options = listOf("WATER", "FOAM_AFFF", "CO2"),
                    affectsTasks = true,
                    helpEn = "Determines which service and test tasks apply.",
                    taskKeysByValue = mapOf("CO2" to listOf("FE_CYLINDER_WEIGHT_CHECK")),
                ),
                AttributeDefinition(
                    key = "sealIntact",
                    kind = AttributeKind.BOOLEAN,
                    labelEn = "Seal/safety pin intact",
                    monthlyChecklist = true,
                ),
            ),
            taskKeys = listOf("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE"),
            regulationRefs = listOf("SOLAS_II2_10_3", "FSS_CH4"),
            helpTextEn = "Check monthly; service annually.",
            helpTextTr = "Aylık kontrol; yıllık servis.",
            commonPscFindings = listOf("Missing/illegible service label", "Obstructed access"),
            isUserDefined = false,
        )

    fun regulationCard(refKey: String = "SOLAS_III_20_6"): RegulationCardEntity =
        RegulationCardEntity(
            refKey = refKey,
            section = RegulationSection.SOLAS,
            citation = "SOLAS III/20.6",
            title = "Weekly inspection of survival craft",
            what = "Survival craft and launching appliances are inspected weekly.",
            howOften = "Weekly",
            byWhom = "Ship's staff",
            evidence = "Log book entry, signed",
            detailBullets = listOf(
                "All survival craft visually inspected.",
                "Lifeboat engines run ahead and astern.",
            ),
            flagNotes = mapOf("RMI" to "Reports countersigned by the Master."),
            appliesToTypeKeys = listOf("LSA_LIFEBOAT_TEC", "LSA_LIFERAFT_THROW_OVER"),
            sourceRef = "SOLAS III/20.6",
            contentVersion = 1,
            lastReviewed = "2026-08-29",
            verificationStatus = VerificationStatus.NEEDS_PERIODIC_REVIEW,
            summaryTr = "Can kurtarma araçları haftalık kontrol edilir.",
            revisionNote = "Captured 2026-08-29",
        )

    fun roundTemplate(key: String = "WEEKLY_LSA"): RoundTemplateEntity = RoundTemplateEntity(
        key = key,
        titleEn = "Weekly LSA round",
        titleTr = "Haftalık LSA turu",
        includesTypeKeys = listOf("LSA_LIFEBOAT_TEC", "LSA_LIFERAFT_THROW_OVER"),
        includesGroups = listOf(EquipmentGroup.LSA, EquipmentGroup.EMERGENCY_ESCAPE),
        descriptionEn = "SOLAS III/20 weekly inspection sweep.",
    )

    fun userNote(id: String = "note-1"): UserNoteEntity = UserNoteEntity(
        id = id,
        title = "Rotterdam service provider",
        body = "Ask agent to book the annual lifeboat exam before arrival.",
        folder = "Port calls",
        regulationRefKey = "SOLAS_III_20_6",
        equipmentTypeKey = "FFE_PORTABLE_EXTINGUISHER",
        isFavourite = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_700_000L,
    )
}
