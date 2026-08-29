# MASTER PROMPT — "DeckWatch" *(working title)*
### Android application for shipboard LSA / FFE inspection, tracking and regulatory reference
**Spec version:** 1.0 · **Date:** 2026-08-29 · **Target agent:** autonomous coding agent
**Owner persona:** 3rd Officer (Safety Officer) on a merchant vessel

---

## 0. HOW TO USE THIS DOCUMENT

You are an expert Android engineer building a production-quality application from scratch. This document is the single source of truth. Read it **completely** before writing a line of code.

Rules of engagement:
1. **Do not ask for clarification on anything already specified here.** Where this document is silent, choose the option that a professional Android team would choose in 2026, note the decision in `DECISIONS.md`, and continue.
2. **Build in the phase order given in §16.** Each phase must compile, pass its tests, and produce an installable debug APK before you move to the next.
3. **Never stub a feature and call it done.** If a feature is deferred, it goes in `BACKLOG.md`, not into a fake UI.
4. **Offline-first, always.** The app must be fully functional with airplane mode on, permanently. No network calls are required for any core function. Ships have no internet.
5. **The regulatory content in this app is a reference aid, not a legal authority.** Every regulatory screen carries the disclaimer in §17.6. Do not present the app's guidance as a substitute for the vessel's certificates, the maker's manual, class rules or the flag Administration's instructions.

---

## 1. PRODUCT IN ONE PARAGRAPH

DeckWatch is an offline Android app for the officer responsible for Life-Saving Appliances (LSA) and Fire-Fighting Equipment (FFE) on board a merchant ship. It does two things. **First**, it is a curated, searchable regulatory notebook: SOLAS, the LSA Code, the FSS Code, the key IMO circulars, flag-state notices for the Marshall Islands, Liberia and Panama, and class-society practice — organised so the officer can answer "how often, by whom, and what evidence do I need?" in under fifteen seconds. **Second**, it is a spatial equipment register: the officer builds their vessel deck by deck as a stack of isometric plan views, drops IMO-symbol equipment markers onto each deck, records type-specific attributes and condition, and the app computes what is overdue, what is due this month, and what needs a shore service provider. Everything can be exported to a single self-contained HTML file that opens in any browser and can be sent over WhatsApp or e-mail to the Chief Officer, the office, or a surveyor — and re-imported later on another phone.

---

## 2. NON-NEGOTIABLE CONSTRAINTS

| # | Constraint |
|---|---|
| C1 | **100% offline.** No mandatory network permission for core function. The app must work forever with no server. |
| C2 | **All data local**, in an encrypted-at-rest Room database. No telemetry, no analytics SDK, no crash reporter that phones home by default. |
| C3 | **Single APK**, no Play Services dependency, no Firebase. Must install and run on a phone with no Google account. |
| C4 | **minSdk 26 (Android 8.0), targetSdk 36, compileSdk 36.** Kotlin 2.x, Jetpack Compose with Material 3 Expressive. |
| C5 | **Portrait and landscape**, phone and 7"–11" tablet. The deck view especially must be usable on a tablet. |
| C6 | **Glove-friendly touch targets:** minimum 48dp, primary actions 56dp. This is used on deck, in wind, sometimes with gloves. |
| C7 | **High-contrast light theme is the default** (sunlight on deck). A true-dark theme and a red night-vision theme (bridge at night) are both required. |
| C8 | **Bilingual UI: English (default) and Turkish.** All strings in `strings.xml` / `values-tr/strings.xml`. Regulatory reference content stays in English (it is quoted terminology) with an optional Turkish plain-language summary field. |
| C9 | **No paid or licence-encumbered assets** may be committed. See §10 on the IMO symbol licensing problem — this is a real constraint, not a formality. |
| C10 | The app **must never silently lose data**. Every destructive action is undoable for 10 seconds; every DB migration is tested. |

---

## 3. TECH STACK — FIXED

```
Language          Kotlin 2.1+ (K2), Java 17 toolchain
UI                Jetpack Compose (BOM latest stable), Material 3 + M3 Expressive
Architecture      Single-activity, MVVM + unidirectional data flow
                  UI State -> ViewModel (StateFlow) -> UseCase -> Repository -> DAO
DI                Hilt
Persistence       Room (KSP) + SQLCipher (net.zetetic:android-database-sqlcipher)
Preferences       DataStore (Proto or Preferences)
Navigation        Navigation Compose, type-safe routes (kotlinx.serialization)
Async             Coroutines + Flow
Background        WorkManager (due-date recomputation, reminder notifications)
Images            Coil 3 (equipment photos)
Serialization     kotlinx.serialization (JSON for backup/import)
Charts            Hand-rolled Compose Canvas (no chart library dependency)
Rendering (2.5D)  Compose Canvas + custom gesture/transform layer. NO 3D engine.
Testing           JUnit5, Turbine, Robolectric, Compose UI Test, Room migration tests
Static analysis   ktlint + detekt, both wired into CI and failing the build
Build             Gradle 8.x, Kotlin DSL, version catalog (libs.versions.toml)
```

**Explicitly forbidden:** Filament, SceneView, OpenGL/Vulkan, three.js, WebView-based UI for the main app, React Native, Flutter, any cloud SDK, any ad SDK.

---

## 4. REPOSITORY LAYOUT

```
deckwatch/
├─ app/                          # Application module, DI graph, MainActivity, navigation host
├─ core/
│  ├─ core-model/                # Pure Kotlin domain models, no Android deps
│  ├─ core-database/             # Room entities, DAOs, migrations, SQLCipher setup
│  ├─ core-datastore/            # Settings, active vessel, theme
│  ├─ core-designsystem/         # Theme, colour, typography, spacing, shared composables
│  ├─ core-common/               # Date/interval utils, Result types, dispatchers
│  └─ core-testing/              # Test fixtures, fake repositories, seed builders
├─ feature/
│  ├─ feature-notes/             # Tab 1 — regulatory reference
│  ├─ feature-vessel/            # Vessel + deck management
│  ├─ feature-deckview/          # 2.5D isometric deck stack renderer + editor
│  ├─ feature-equipment/         # Equipment detail, dynamic attribute forms
│  ├─ feature-inspection/        # Rounds, quick-condition, due engine, history
│  ├─ feature-survivalcraft/     # Lifeboat / liferaft / rescue boat dedicated views
│  ├─ feature-report/            # HTML export + import, PDF-lite, sharing
│  └─ feature-settings/          # Backup, theme, language, flag selection
├─ data/
│  ├─ data-repository/           # Repository implementations
│  └─ data-seed/                 # Bundled JSON: regulations, equipment catalogue, symbols
├─ build-logic/                  # Convention plugins
├─ .github/workflows/            # CI + release pipelines (§15)
├─ docs/
│  ├─ DECISIONS.md
│  ├─ DATA_MODEL.md
│  ├─ SYMBOL_LICENSING.md
│  └─ BACKLOG.md
└─ MASTER_PROMPT.md              # this file
```

---

## 5. INFORMATION ARCHITECTURE — TOP LEVEL

Bottom navigation, **four** destinations:

| Tab | Icon | Purpose |
|---|---|---|
| **1. Notes** | book | Regulatory reference: SOLAS / LSA / FFE / FLAG / Class. Read-only bundled content + the user's own notes. |
| **2. Vessel** | layers | The 2.5D deck stack. The heart of the app. Build decks, place equipment, tap to inspect. |
| **3. Due** | calendar-check | Cross-vessel work list: overdue, due this week, due this month, due before next survey. |
| **4. More** | dots | Vessel manager, reports & export, settings, backup, about. |

A persistent **vessel selector** lives in the top app bar of tabs 2–4. The app supports multiple vessels (an officer changes ship); one is "active" at a time.

---

## 6. DATA MODEL

This is the authoritative schema. Implement it in Room exactly, with the stated indices. All IDs are `String` UUIDv4 (so that export/import and multi-device merge work without integer collisions). All timestamps are `Long` epoch-millis UTC; all *dates* (service dates, due dates) are `Long` epoch-days so timezone drift never shifts a due date.

### 6.1 Vessel

```kotlin
@Entity(tableName = "vessels")
data class VesselEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imoNumber: String?,          // 7 digits, validated with the IMO check-digit algorithm
    val callSign: String?,
    val mmsi: String?,
    val flag: FlagState,             // enum: MARSHALL_ISLANDS, LIBERIA, PANAMA, OTHER
    val flagOtherName: String?,
    val classSociety: ClassSociety?, // DNV, LR, ABS, BV, CLASSNK, RINA, KR, CCS, IRS, OTHER
    val vesselType: VesselType,      // BULK_CARRIER, TANKER_OIL, TANKER_CHEM, TANKER_LPG,
                                     // CONTAINER, GENERAL_CARGO, RORO, PASSENGER, OFFSHORE, OTHER
    val grossTonnage: Int?,
    val buildDate: Long?,            // epoch-days, drives keel-laid-based rule applicability
    val safetyEquipmentCertExpiry: Long?,   // epoch-days — drives "due before next survey"
    val lastAnnualSurveyDate: Long?,
    val nextDrydockDate: Long?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 6.2 Deck — the vertical stack

```kotlin
@Entity(
    tableName = "decks",
    foreignKeys = [ForeignKey(VesselEntity::class, ["id"], ["vesselId"], onDelete = CASCADE)],
    indices = [Index("vesselId"), Index(value = ["vesselId", "levelIndex"], unique = true)]
)
data class DeckEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    val name: String,                // "Upper Deck", "A Deck", "Bridge Deck", "Engine Room 2nd Flat"
    val shortCode: String?,          // "UD", "A", "BR" — shown on the stack spine
    val levelIndex: Int,             // 0 = the first deck the user created ("ground").
                                     // Positive = above it, negative = below it.
                                     // NOT necessarily contiguous — leave gaps for later inserts.
    val plan: DeckPlan,              // see 6.3
    val colorTint: Int?,             // ARGB, user-assignable per deck
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

**The `levelIndex` rule.** First deck created gets `levelIndex = 0`. "Add deck above" gives the new deck `max(levelIndex) + 10`. "Add deck below" gives `min(levelIndex) - 10`. "Insert between" gives the midpoint. Step of 10 leaves room to insert without a renumber migration. The stack is always rendered sorted by `levelIndex` descending (highest deck at the top of the screen).

### 6.3 Deck plan geometry

A deck plan is a normalised 2D outline in a unit coordinate space so that plans scale to any screen and any vessel size.

```kotlin
@Serializable
data class DeckPlan(
    val shape: PlanShape,            // RECTANGLE, SHIP_HULL, L_SHAPE, CUSTOM_POLYGON
    val lengthRatio: Float = 1.0f,   // relative to the vessel's longest deck
    val breadthRatio: Float = 1.0f,
    val polygon: List<PlanPoint> = emptyList(),   // used when shape == CUSTOM_POLYGON
    val bowAtTop: Boolean = true,    // orientation of the plan on screen
    val backgroundImageUri: String? = null,       // user may photograph the GA plan and trace on it
    val backgroundOpacity: Float = 0.35f
)
@Serializable data class PlanPoint(val x: Float, val y: Float)  // both in 0.0..1.0
```

`SHIP_HULL` is a built-in parametric outline: a rectangle with a parameterised bow taper (`bowSharpness` 0..1) and a rounded stern. Provide 6 presets: *Bulker Main Deck, Tanker Main Deck, Container Main Deck, Accommodation Block, Engine Room Flat, Bridge Deck*. The user must be able to build a usable deck in under 20 seconds without drawing anything.

### 6.4 Zone — categories on two axes

* **Spatial zones** (where): a named, coloured region drawn on a deck plan — "Fwd Mooring Station", "Pump Room", "Galley", "Muster Station A", "Engine Casing".
* **Logical categories** (what): a user-definable tag applied to equipment regardless of location — "Weekly Round", "Annual Service Due 2026", "Chief Officer's List", "PSC Focus Items".

```kotlin
@Entity(tableName = "zones", indices = [Index("deckId")])
data class ZoneEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val name: String,
    val polygon: List<PlanPoint>,    // TypeConverter -> JSON
    val colorArgb: Int,
    val sortOrder: Int
)
@Entity(tableName = "categories", indices = [Index("vesselId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val vesselId: String?,           // null == global category available on every vessel
    val name: String,
    val colorArgb: Int,
    val iconKey: String?,
    val sortOrder: Int
)
@Entity(tableName = "equipment_category_xref", primaryKeys = ["equipmentId","categoryId"])
data class EquipmentCategoryXref(val equipmentId: String, val categoryId: String)
```

### 6.5 Equipment — the core record

```kotlin
@Entity(
    tableName = "equipment",
    indices = [Index("vesselId"), Index("deckId"), Index("zoneId"),
               Index("typeKey"), Index("nextDueDate")]
)
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    val deckId: String?,             // null == "unplaced", lives in an inbox until positioned
    val zoneId: String?,
    val parentId: String?,           // for equipment mounted inside/on other equipment,
                                     // e.g. a lifeboat's own extinguisher, or a liferaft's HRU
    val typeKey: String,             // FK into the bundled equipment type catalogue, §9
    val symbolKey: String,           // FK into the symbol library, §10
    val tag: String,                 // ship's own identifier — "FE-UD-07", "LB No.1"
    val name: String?,               // optional friendly name
    val location: String?,           // free text, e.g. "Stbd side, aft of provision crane"
    val posX: Float,                 // 0..1 within the deck plan
    val posY: Float,
    val rotationDeg: Float = 0f,
    val makerName: String?,
    val modelName: String?,
    val serialNumber: String?,
    val typeApprovalNumber: String?, // MED / wheelmark / USCG approval no.
    val manufactureDate: Long?,      // epoch-days
    val installedDate: Long?,
    val quantity: Int = 1,
    val condition: ConditionGrade,   // §7.3 quick action
    val conditionSetAt: Long?,
    val statusFlag: StatusFlag,      // IN_SERVICE, OUT_OF_SERVICE, LANDED_ASHORE,
                                     // AWAITING_SPARE, CONDEMNED, SPARE_STOCK
    val attributesJson: String,      // dynamic, type-specific — §9.3
    val nextDueDate: Long?,          // denormalised soonest due, recomputed by the due engine
    val nextDueTaskKey: String?,
    val photoUris: List<String>,     // TypeConverter
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?             // soft delete, for undo + merge on import
)
```

### 6.6 Maintenance task definition and instances

The interval rules live in **data**, not in code, so they can be corrected without a release.

```kotlin
@Entity(tableName = "task_definitions")
data class TaskDefinitionEntity(
    @PrimaryKey val key: String,     // "FE_MONTHLY_INSPECTION", "LB_ANNUAL_THOROUGH_EXAM"
    val appliesToTypeKeys: List<String>,
    val titleEn: String,
    val titleTr: String,
    val descriptionEn: String,
    val intervalKind: IntervalKind,  // WEEKLY, MONTHLY, QUARTERLY, ANNUAL, BIENNIAL,
                                     // FIVE_YEARLY, TEN_YEARLY, TWENTY_YEARLY,
                                     // AT_SURVEY, CUSTOM_MONTHS, EVENT_DRIVEN
    val intervalMonths: Int?,
    val toleranceDaysBefore: Int,    // e.g. 90 for the ±3-month HSSC window
    val toleranceDaysAfter: Int,
    val performedBy: PerformedBy,    // SHIP_STAFF, SHIP_STAFF_TRAINED,
                                     // AUTHORISED_SERVICE_PROVIDER, MANUFACTURER,
                                     // RO_SURVEYOR_ATTENDING, SHORE_FACILITY
    val evidenceRequired: List<String>,  // "Signed checklist", "Statement of fitness",
                                         // "Service report", "Certificate"
    val regulationRefs: List<String>,    // reference keys into the notes DB, §8
    val flagOverrides: Map<String, String>?, // flag code -> short note on the difference
    val isUserDefined: Boolean = false
)
@Entity(tableName = "task_instances",
        indices = [Index("equipmentId"), Index("dueDate"), Index("status")])
data class TaskInstanceEntity(
    @PrimaryKey val id: String,
    val equipmentId: String,
    val taskKey: String,
    val dueDate: Long,               // epoch-days
    val windowOpens: Long,
    val windowCloses: Long,
    val status: TaskStatus,          // PENDING, DUE_SOON, OVERDUE, DONE, SKIPPED, NOT_APPLICABLE
    val completedDate: Long?,
    val completedBy: String?,        // free text: rank/name, or service company
    val serviceProvider: String?,
    val certificateNumber: String?,
    val findings: String?,
    val conditionAfter: ConditionGrade?,
    val photoUris: List<String>,
    val attachmentUris: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 6.7 Inspection rounds

```kotlin
@Entity(tableName = "rounds")
data class RoundEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    val templateKey: String,         // "WEEKLY_LSA", "MONTHLY_FFE", "PRE_ARRIVAL_PSC", custom
    val title: String,
    val startedAt: Long,
    val completedAt: Long?,
    val performedBy: String,
    val itemCount: Int,
    val doneCount: Int,
    val deficiencyCount: Int,
    val notes: String?
)
@Entity(tableName = "round_items", indices = [Index("roundId"), Index("equipmentId")])
data class RoundItemEntity(
    @PrimaryKey val id: String,
    val roundId: String,
    val equipmentId: String,
    val checkedAt: Long?,
    val condition: ConditionGrade?,
    val remark: String?,
    val photoUris: List<String>
)
```

### 6.8 Deficiencies

```kotlin
@Entity(tableName = "deficiencies", indices = [Index("equipmentId"), Index("status")])
data class DeficiencyEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    val equipmentId: String?,
    val raisedDate: Long,
    val raisedBy: String,
    val severity: Severity,          // OBSERVATION, MINOR, MAJOR, CRITICAL_DETAINABLE
    val title: String,
    val description: String,
    val correctiveAction: String?,
    val targetDate: Long?,
    val closedDate: Long?,
    val closedBy: String?,
    val status: DeficiencyStatus,    // OPEN, IN_PROGRESS, CLOSED, DEFERRED_TO_OFFICE
    val sparePartRequired: String?,
    val photoUris: List<String>
)
```

### 6.9 Enums

```kotlin
enum class ConditionGrade(val score: Int) {  // the "quick action" grades
    GOOD(4),          // green   — fully serviceable, no remark
    ACCEPTABLE(3),    // lime    — serviceable, minor cosmetic remark
    MONITOR(2),       // amber   — serviceable but degrading, watch it
    DEFECTIVE(1),     // orange  — not fully serviceable, deficiency raised
    OUT_OF_SERVICE(0),// red     — must not be used, landed/condemned
    NOT_CHECKED(-1)   // grey
}
enum class Severity { OBSERVATION, MINOR, MAJOR, CRITICAL_DETAINABLE }
enum class TaskStatus { PENDING, DUE_SOON, OVERDUE, DONE, SKIPPED, NOT_APPLICABLE }
enum class FlagState { MARSHALL_ISLANDS, LIBERIA, PANAMA, OTHER }
```

---

## 7. TAB 2 — THE VESSEL VIEW (2.5D DECK STACK)

This is the signature feature.

### 7.1 The three view modes

The Vessel tab has one screen with three modes, toggled by a segmented control in the app bar. State is remembered per vessel.

**A. STACK MODE (default).** All decks rendered as isometric parallelograms, stacked vertically with a fixed vertical gap, highest `levelIndex` at the top. Each deck is drawn as its plan outline skewed into an isometric projection, with a thin extruded edge (6–10dp) on the near sides to give it physical thickness. Equipment markers sit on the deck surface. The whole stack pans and pinch-zooms as one object. This reads as a stack of floors without any 3D engine.

**B. DECK MODE.** One deck fills the screen. It may be viewed **flat (true plan, top-down)** or **isometric** — a toggle, because flat is far better for precise placement and isometric is better for looking at. Vertical swipe (or the deck spine on the right edge) moves to the deck above/below with a spring animation: the current deck slides and fades out, the next slides in from the correct direction.

**C. LIST MODE.** No graphics. A grouped list: Deck → Zone → Equipment. Fast, accessible, works with TalkBack. Every graphical mode must have a full list-mode equivalent — never make the graphics the only path to a function.

### 7.2 Isometric rendering specification

Implement in `feature-deckview` with Compose `Canvas` and a `DrawScope`. No third-party renderer.

**Projection.** Use a fixed dimetric projection matrix rather than a general 3D pipeline:

```
screenX = (planX - planY) * cos(θ) * scale
screenY = (planX + planY) * sin(θ) * scale - levelZ * deckHeight * scale
```

with `θ = 30°` as the default, exposed as a user setting `isoAngle` in the range 0° (flat plan) to 35°. Setting it to 0 collapses the isometric into the flat plan view — so mode B's flat/iso toggle is a single animated float, not a second renderer. Animate transitions between angles with a spring.

**Deck height.** `deckHeight` is a constant screen-space value (default 64dp at scale 1.0), not a physical value. Provide a "spread" slider 0.5×–3× so the user can fan the decks apart to see a lower deck, then collapse them again. Fanning is the single most satisfying interaction in the app — make it buttery.

**Draw order.** Painter's algorithm, bottom deck first. Within a deck: plan fill → background image (if any) → zone polygons → grid (optional) → deck outline → equipment markers → labels → selection overlay.

**Occlusion and focus.** When one deck is focused, decks above it render at 25% alpha and are non-interactive; decks below render at 60% alpha. A long-press on any deck isolates it (all others hidden) until released.

**Performance budget.** 60fps with 20 decks and 600 equipment markers on a mid-range 2022 phone. To achieve this:
- Precompute each deck's projected outline `Path` and cache it, keyed by `(planHash, isoAngle, scale)`.
- Cull markers outside the viewport before drawing.
- Draw markers as pre-rasterised `ImageBitmap` at three LODs (16dp / 24dp / 32dp), chosen by zoom. Below a threshold zoom, do not draw individual markers at all — draw one aggregated dot per zone, coloured by the worst condition in that zone.
- Hoist all animation into `graphicsLayer` where possible; never recompose the canvas on every gesture frame — drive it from a `MutableState<Transform>` read inside `drawWithCache`.
- Add a Macrobenchmark module measuring frame timing on a seeded 20-deck vessel. The benchmark is part of CI (informational, non-blocking).

**Gestures.**

| Gesture | Stack mode | Deck mode |
|---|---|---|
| Drag | pan the stack | pan the deck |
| Pinch | zoom 0.4×–4× | zoom 0.4×–6× |
| Two-finger vertical drag | change fan spread | — |
| Vertical swipe (single finger, on the spine) | select deck | go to deck above/below |
| Tap deck surface | focus that deck / enter deck mode | deselect |
| Tap marker | open the equipment bottom sheet (§7.4) | same |
| Long-press marker | pick up and drag to reposition (with haptic) | same |
| Long-press empty plan | "Add equipment here" at that coordinate | same |
| Double-tap | zoom to fit that deck | zoom to fit |

Repositioning by drag must snap to an optional grid and show live coordinates. A dragged marker that is dropped outside the deck outline returns to its origin with a shake animation.

**Deck spine.** A vertical rail on the right edge listing every deck as a small pill with its `shortCode`, a coloured dot for its worst condition, and a count badge for overdue items. Tapping a pill flies the camera to that deck. This is the fast navigation path on a 20-deck vessel.

### 7.3 The quick-action condition control

The single most-used control in the app. It must be reachable in **one tap from the marker** and completable in **two**.

Tapping a marker opens a bottom sheet whose top row is five large condition chips — Good / Acceptable / Monitor / Defective / Out of Service — colour-coded per §6.9, each 56dp, with both icon and text. Tapping one:
1. writes `condition` and `conditionSetAt` immediately,
2. fires a haptic tick,
3. shows an undo snackbar for 10 seconds,
4. if the grade is DEFECTIVE or OUT_OF_SERVICE, expands an inline "raise deficiency" form pre-filled with the equipment, today's date and a suggested severity — but never *forces* the user to fill it; they can dismiss and come back.

A "sweep mode" toggle in the app bar lets the officer walk a deck and grade item after item without the sheet closing: after grading, the sheet advances to the next unchecked item on the same deck, with the marker highlighted on the plan behind. This turns a weekly round into a two-minute job. Sweep mode writes a `RoundEntity` automatically.

### 7.4 Equipment bottom sheet

Three-stage sheet (peek / half / full):
- **Peek:** tag, type name, symbol, condition chips, next due date with a colour-coded countdown.
- **Half:** + location, maker/model/serial, last inspection, open deficiencies, quick "Log inspection" and "Take photo" buttons.
- **Full:** the complete record — dynamic attributes (§9.3), full task list with per-task due dates and history, photo gallery, notes, the applicable regulation cards (§8.4) pulled from the task definitions, and destructive actions (move deck, duplicate, delete).

### 7.5 Adding equipment

The flow, in four steps:
1. **Long-press the deck** (or FAB → Add equipment) → the *Equipment Catalogue* opens as a full-height sheet.
2. **Catalogue**: search field at the top, then two tabs — *By category* (LSA / FFE / Emergency & Escape / Machinery & Controls / Signage / Other) and *Recent*. Each entry shows its IMO symbol, name, and a one-line description. Categories are collapsible; the LSA and FFE lists are long, so search is the primary path.
3. **Pick a type** → an *Add Equipment* form appears, pre-populated: symbol auto-selected, tag auto-suggested from a per-type counter and deck code (`FE-UD-03`), position set to where the user long-pressed, deck and zone inferred. Required fields are tag only. Everything else can be filled later.
4. **Type-specific attributes appear inline** based on the type's attribute schema (§9.3) — for a fire extinguisher: extinguishing medium, capacity, pressure type, last service, last hydrostatic test, last discharge test. Each attribute that drives a due date shows the interval it will generate and the resulting due date, live, as the user types.

A **"duplicate ×N"** action creates N copies with auto-incremented tags, placed in a row/grid the user then drags apart — essential when a deck has 14 identical extinguishers.

### 7.6 Survival craft — the dedicated view

Lifeboats, rescue boats, liferafts and their launching appliances get their own detail screen.

Opening a lifeboat from the deck plan pushes a **Survival Craft screen** with:
- A **schematic elevation of the boat and davit** drawn as vector art in Compose Canvas — hull, canopy, davit arms, falls, hooks, winch — with **hotspot markers** on the components that get inspected: on-load release gear (fwd/aft hooks), hydrostatic interlock, falls/wire, winch brake, sheaves, limit switches, painter, engine, fuel, batteries, sprinkler system (for tankers), air support system, compass, radio, drain plugs, boat equipment inventory. Tapping a hotspot opens that sub-component's own record. Sub-components are real `EquipmentEntity` rows with `parentId` set to the boat — no special-case tables.
- A **boat inventory checklist** rendered from the LSA Code Chapter IV item list, with per-item quantity, expiry and condition (flares, first-aid kit, rations, water, bailer, sea anchor, hatchet, torch, etc.). Items with expiry dates feed the due engine.
- A **davit / launching appliance** panel with the MSC.402(96) task set: weekly, monthly, annual thorough examination, 5-yearly overload operational test, winch brake dynamic test, fall renewal/turn-end-for-end.
- A **drill log** panel: SOLAS III/19 drill records, launch dates, days-since-last-launch counter.

The same pattern (schematic + hotspots + inventory + tasks) is reused for: **liferaft & HRU**, **rescue boat & fast rescue boat**, **MES / evacuation slide**, and — on the FFE side — **fixed CO₂ / foam / water-mist system** (cylinder bank schematic with per-cylinder weight records), **fire pump & emergency fire pump**, **SCBA set** (cylinder pressure and hydro-test records), and **fireman's outfit locker**. Build the lifeboat one first as the reference implementation, then generalise it into a `SchematicScreen` driven by a JSON hotspot definition so the rest are data, not code.

---

## 8. TAB 1 — REGULATORY NOTES

### 8.1 Structure

A bundled, versioned reference library plus the user's own notes on top of it. Content ships as JSON in `data-seed` and is loaded into Room on first run (and migrated on content-version bump, preserving user notes).

Top level of the Notes tab — six sections:

| Section | Contents |
|---|---|
| **SOLAS** | Chapter II-2 (fire safety), Chapter III (life-saving), Chapter V where it touches LSA/FFE. Regulation-level cards. |
| **LSA** | LSA Code chapters I–VII; survival craft, personal appliances, visual signals, launching appliances; the SOLAS III/20 maintenance regime; MSC.402(96). |
| **FFE** | FSS Code chapters; SOLAS II-2/14 maintenance plan; MSC.1/Circ.1432 as amended (fire protection systems and appliances); MSC.1/Circ.1318/Rev.1 (fixed CO₂). |
| **FLAG** | Three sub-sections: Marshall Islands (RMI), Liberia, Panama. Each is a list of the relevant Marine Notices / Circulars with their key requirements and, crucially, **how they differ from plain SOLAS**. |
| **CLASS** | What a class surveyor looks for at Annual / Intermediate / Renewal Safety Equipment survey; IACS UR Z17 service-supplier categories; typical PSC deficiency codes for LSA/FFE. |
| **MY NOTES** | The user's own notes, freely organised into folders, with the option to attach a note to any regulation card or any equipment type. |

### 8.2 The note card

Every bundled entry is a card with a fixed shape:

```
┌────────────────────────────────────────────┐
│ [SOLAS III/20.6]              ⭐ 🔖  ⋮      │
│ Weekly inspection of survival craft         │
├────────────────────────────────────────────┤
│ WHAT      one-sentence plain statement      │
│ HOW OFTEN Weekly  (chip, colour-coded)      │
│ BY WHOM   Ship's staff  (chip)              │
│ EVIDENCE  Log book entry, signed            │
├────────────────────────────────────────────┤
│ Detail — 3–8 short bullet points            │
├────────────────────────────────────────────┤
│ FLAG NOTES  🇲🇭 RMI · 🇱🇷 LIB · 🇵🇦 PAN     │
│ (expands only if there is a real difference)│
├────────────────────────────────────────────┤
│ Applies to: Lifeboat, Rescue boat, Liferaft │
│ [Show my equipment] [Add my note]           │
└────────────────────────────────────────────┘
```

The **WHAT / HOW OFTEN / BY WHOM / EVIDENCE** quadrant is mandatory on every card.

### 8.3 Interval quick-reference

A dedicated screen, reachable from the Notes tab header and from the Due tab: a filterable matrix of *equipment type × interval × performed by*, with the flag column showing where RMI / Liberia / Panama diverge. Sticky headers, horizontal scroll, and a per-row link to the underlying card. Seed it from §19.

### 8.4 Contextual regulation surfacing

Every task instance and every equipment type links to its regulation cards via `regulationRefs`. On the equipment full sheet, an "Applicable requirements" section lists them; tapping opens the card in a dialog without leaving the equipment.

### 8.5 Content accuracy rules — READ CAREFULLY

The bundled content is a professional reference and will be used to plan real safety work. Therefore:
- Every card carries `sourceRef` (the exact instrument, e.g. `MSC.402(96) §6.2`) and `contentVersion` and `lastReviewed` (a date).
- Where the agent building this app is **not certain** of a figure, the card must say so explicitly with a `verificationStatus` of `UNVERIFIED` and render an amber "Verify against the current instrument" strip. **Do not invent regulation numbers, paragraph numbers, or intervals.** A missing card is acceptable; a confidently wrong interval is not.
- Flag-state notices change. Each flag section shows the notice revision and date it was captured from, and a "Check for a newer revision" link that opens the registry's public notice index in a browser (this is the *only* place the app touches the network, and it is user-initiated).
- The disclaimer in §17.6 is shown on first entry to the Notes tab and is permanently visible in the tab's footer.

---

## 9. EQUIPMENT TYPE CATALOGUE

### 9.1 Structure

The catalogue ships as `data-seed/src/main/assets/equipment_catalogue.json`, loaded into a Room table. Each entry:

```json
{
  "typeKey": "FFE_PORTABLE_EXTINGUISHER",
  "group": "FFE",
  "subGroup": "PORTABLE_APPLIANCES",
  "nameEn": "Portable fire extinguisher",
  "nameTr": "Portatif yangın söndürücü",
  "symbolKey": "FES001",
  "defaultTagPrefix": "FE",
  "attributeSchema": [ /* §9.3 */ ],
  "taskKeys": ["FE_MONTHLY_INSPECTION","FE_ANNUAL_SERVICE",
               "FE_FIVE_YEARLY_DISCHARGE","FE_TEN_YEARLY_HYDROSTATIC"],
  "regulationRefs": ["SOLAS_II2_10_3","FSS_CH4","MSC1_CIRC1432_EXTINGUISHERS",
                     "RES_A951_23"],
  "helpTextEn": "…",
  "commonPscFindings": ["Missing/illegible service label",
                        "Pressure gauge outside green band",
                        "Obstructed access", "Missing safety pin/seal"]
}
```

### 9.2 Required catalogue coverage

Ship **at minimum** the following. Group headings are the catalogue's category tree.

**LSA — Survival craft & launching**
Lifeboat (totally enclosed) · Lifeboat (partially enclosed) · Free-fall lifeboat · Lifeboat davit / launching appliance · Free-fall launching appliance · Lifeboat winch · On-load release gear · Hydrostatic interlock · Falls / wire ropes · Fall preventer device · Rescue boat · Fast rescue boat · Rescue boat davit · Liferaft (throw-over inflatable) · Liferaft (davit-launched) · Liferaft cradle · Hydrostatic release unit (HRU) · Liferaft painter & weak link · Marine evacuation system (MES) · Embarkation ladder · Pilot ladder · Rescue boat engine · Lifeboat engine · Boat sprinkler system · Boat air support system

**LSA — Personal appliances**
Lifebuoy (plain) · Lifebuoy with self-igniting light · Lifebuoy with self-activating smoke signal · Lifebuoy with light and smoke · Lifebuoy with buoyant line · Lifejacket (adult) · Lifejacket (child) · Lifejacket (infant) · Lifejacket light · Lifejacket whistle · Immersion suit · Anti-exposure suit · Thermal protective aid · Inflatable lifejacket (with cylinder & cartridge)

**LSA — Visual signals & communications**
Rocket parachute flare · Hand flare · Buoyant smoke signal · Line-throwing appliance · EPIRB · SART / AIS-SART · Two-way VHF radiotelephone (survival craft) · General emergency alarm · Public address system · Muster station · Muster list · Daylight signalling lamp

**FFE — Portable & mobile**
Portable fire extinguisher (water) · (foam) · (dry powder) · (CO₂) · (wet chemical) · (clean agent) · Wheeled/mobile fire extinguisher · Portable foam applicator unit · Water fog applicator · Fire blanket · Fire bucket · Sand box

**FFE — Fire main & hoses**
Fire hydrant · Fire hose · Fire nozzle (jet/spray dual purpose) · Fire hose box/reel · International shore connection · Fire main isolating valve · Main fire pump · Emergency fire pump · Fire monitor · Water spray system · Deck foam system · Foam concentrate tank

**FFE — Fixed systems**
Fixed CO₂ system · CO₂ cylinder bank · CO₂ release station · Fixed foam system (hi-ex / low-ex) · Water mist system · Sprinkler system · Sprinkler section valve · Dry powder system (gas carriers) · Inert gas system · Galley hood extinguishing system · Local application fire-fighting system (LAFFS)

**FFE — Detection & alarm**
Fire detection & alarm panel · Smoke detector · Heat detector · Flame detector · Manual call point · Fire alarm bell/sounder · Gas detector (fixed) · Portable gas detector / multi-gas meter · Sample extraction smoke detection

**FFE — Personal protective & breathing**
Fireman's outfit · SCBA set · SCBA spare cylinder · SCBA compressor · EEBD · Fire-resistant lifeline · Fireman's axe · Safety lamp · Fire-fighter's radio (two-way portable, explosion-proof)

**Fire integrity & controls**
Fire door (A-class / B-class, hinged / sliding, self-closing) · Fire damper · Ventilation closing device · Remote ventilation stop · Fuel oil quick-closing valve · Fuel oil pump remote stop · Lube oil pump remote stop · Emergency generator · Emergency switchboard · Emergency battery · Skylight closing device · Watertight door

**Plans, documentation & signage**
Fire control plan (and its outside container) · Muster list · SOPEP/SMPEP locker · Escape route sign · Low-location lighting · Emergency escape trunk · Safety plan · Damage control plan · Training manual · LSA maintenance manual · Fire safety operational booklet

**Other / user-defined** — the user can create a custom type with a custom attribute schema and custom tasks. This escape hatch is mandatory.

### 9.3 Dynamic attribute schema

Attribute definitions are data. Supported field kinds: `TEXT`, `NUMBER`, `DECIMAL`, `DATE`, `BOOLEAN`, `ENUM`, `MULTI_ENUM`, `PRESSURE`, `WEIGHT`, `PHOTO`, `SIGNATURE`.

```json
{
  "key": "extinguishingMedium",
  "kind": "ENUM",
  "labelEn": "Extinguishing medium",
  "labelTr": "Söndürücü madde",
  "required": true,
  "options": ["WATER","FOAM_AFFF","DRY_POWDER_ABC","DRY_POWDER_BC",
              "CO2","WET_CHEMICAL","CLEAN_AGENT"],
  "affectsTasks": true,
  "helpEn": "Determines which service and test tasks apply."
}
```

`affectsTasks: true` means changing this value re-derives the equipment's task set. Example: selecting `CO2` adds a *cylinder weight check* task; selecting `DRY_POWDER_ABC` adds a *powder condition / caking check*.

**Worked example — fire extinguisher.** Attributes: extinguishing medium (enum, drives tasks) · nominal capacity (decimal + unit kg/L) · pressure type (stored-pressure / cartridge-operated) · gauge reading (decimal, bar, with a green-band validity range per medium) · manufacture date (date; drives the 10-year hydrostatic and the maker's end-of-life) · last annual service date + next due (auto) · service provider name · service report number · last 5-yearly test discharge date · last hydrostatic test date · IMO symbol placement confirmed (boolean) · access unobstructed (boolean) · seal/safety pin intact (boolean) · bracket/stowage secure (boolean) · instructions legible (boolean) · spare charge available (boolean + count).

The last six booleans are the **monthly inspection checklist** for this type — they render as a compact checklist in the quick-action sheet, and a full sweep of them writes a `TaskInstance` completion for `FE_MONTHLY_INSPECTION`. Build the schema so that any type can declare a `monthlyChecklist` group of booleans and get this behaviour for free.

---

## 10. THE IMO SYMBOL LIBRARY

### 10.1 What the standards are

- **IMO Resolution A.1116(30)**, *Escape route signs and equipment location markings* (adopted Dec 2017, effective 1 Jan 2019). Supersedes A.760(18), consolidates A.952(23). Table 1 — signs coded `MES`, `LSS`, `EES`, `FES`, plus `PSS`/`WSS`/`MSS`; Table 2 — mandatory-action signs for launching survival craft; Table 3 — `SIS001`–`SIS052`, the shipboard fire control plan symbols required by SOLAS II-2/15.2.4.
- **ISO 24409-1:2020** (design principles) and **ISO 24409-2** (the catalogue that assigns the LSS/FES/MES/EES codes and cross-references ISO 7010 E-, F-, M-, P-, W-series numbers).
- **ISO 17631:2022**, shipboard plans, harmonised with ISO 24409-2 and ISO 7010.
- **IMO Resolution A.952(23)** (2003), the pre-2019 fire control plan symbol set, still found on older vessels.

**Colour and shape convention (ISO 3864-1 via ISO 24409-1):** life-saving, escape and first-aid signs are **green ground, white symbol, square/oblong**. Fire-fighting equipment signs are **red ground, white symbol, square/oblong**. Prohibition is a red-bordered circle with a 45° bar, warning is a yellow triangle, mandatory action is a blue circle. Fire-control-*plan* symbols use a separate media colour code: grey = CO₂/nitrogen, brown = other gases, white = powder, yellow = foam, green = water, orange = sprinkler/HP water.

### 10.2 Licensing — a real constraint, handle it explicitly

The ISO standards and their artwork are copyrighted and sold; there is no official free vector set. Commercial vendors sell complete A.1116(30) packs under restrictive terms.

**Therefore:** the app ships **originally-drawn** vector icons. Redraw each pictogram to the geometry and colour specification published in the standards — do **not** copy vendor SVG files or trace vendor artwork. Record the position in `docs/SYMBOL_LICENSING.md`, including: what was drawn from scratch, which reference was used for geometry, and a note that any organisation deploying the app in a regulated signage context should obtain an ISO artwork licence. Do not commit any file whose provenance you cannot state.

Implementation: each symbol is a Compose `ImageVector` in `core-designsystem`, generated into `SymbolLibrary.kt` as a `Map<String, ImageVector>` keyed by the standard's code. A `SymbolPickerSheet` presents them grouped and searchable by code, English name and Turkish name.

### 10.3 Required symbol coverage

Ship **at minimum** the following keys. Where a real code exists, use it; where the app needs a marker the standards do not define, prefix the key with `APP_`.

**Life-saving (`LSS`, green):** `LSS001` lifeboat · `LSS002` rescue boat · `LSS003` liferaft · `LSS004` davit-launched liferaft · `LSS005` lifebuoy · `LSS006` lifebuoy with line · `LSS007` lifebuoy with light · `LSS008` lifebuoy with line and light · `LSS008_1` lifebuoy with light and smoke · `LSS009` lifejacket · `LSS010` child's lifejacket · `LSS011` infant's lifejacket · `LSS012` SART · `LSS013` survival-craft distress signal · `LSS014` rocket parachute flare · `LSS015` line-throwing appliance · `LSS016` two-way VHF radiotelephone · `LSS017` EPIRB · `LSS018` embarkation ladder · `LSS019` marine evacuation slide · `LSS020` marine evacuation chute · `LSS021` immersion suit / survival clothing · `LSS022` liferaft knife.

**Fire-fighting (`FES`, red):** `FES001` fire extinguisher · `FES002` fire hose reel · `FES003` collection of fire-fighting equipment / fire locker · `FES004` fire alarm call point · `FES005` fixed fire-extinguishing battery · `FES006` wheeled fire extinguisher · `FES007` portable foam applicator unit · `FES008` water fog applicator · `FES009` fixed fire-extinguishing installation · `FES010` fixed fire-extinguishing bottle · `FES011` remote release station · `FES012` fire monitor. Plus ISO 7010 red-set extensions: fire blanket · fire hydrant · unconnected fire hose · fire ladder · fire emergency telephone (F006) · directional arrow (F007, four rotations) · fire alarm flashing light · firefighters' portable radio.

**Escape (`MES`, green):** `MES001` shipboard assembly / muster station · `MES002`/`MES003` emergency exit left/right · `MES004`–`MES011` door operation signs · directional arrow variants · low-location lighting marking.

**Emergency equipment (`EES`, green):** `EES001` first aid · `EES002` emergency telephone · `EES003` eyewash · `EES004` safety shower · `EES005` stretcher · `EES006` medical grab bag · `EES007` oxygen resuscitator · `EES008` **EEBD** · `EES009` doctor · `EES010` AED · `EES012` general alarm · `EES013` break to obtain access.

**Fire control plan (`SIS001`–`SIS052`)** — structural: A-class and B-class divisions, main vertical zone, fire doors, ventilation remote shut-off, fire damper, closing devices, remote controls. Appliances and controls: fire pump and remote control, emergency fire pump, fuel/lube oil remote shut-offs, remote release station, international shore connection, fire hydrant, section valves, fixed installation/battery/bottle, monitor, hose and nozzle, extinguishers, fire locker, protected space, emergency generator/battery/switchboard, detection panel, call points, detector-monitored spaces; escape routes; EEBD.

**Media-colour variants.** Symbols that carry a media colour code must be renderable in each of the six media colours from one vector by tinting. Do not draw six copies.

**Total target: ~150 distinct pictograms**, plus tint and rotation variants. Deliver in batches per §16.

### 10.4 Marker rendering on the plan

An equipment marker is: the symbol tile (rounded square, standard ground colour) + a condition ring (2dp, colour per `ConditionGrade`) + an optional badge (top-right) for overdue count + the tag label below at zoom ≥ 1.5×. Out-of-service equipment renders with a diagonal hatch overlay. Selected equipment gets an animated pulse ring.

---

## 11. THE DUE-DATE ENGINE

A pure-Kotlin, fully unit-tested module in `core-common` + `data-repository`. It has no Android dependencies so it can be tested on the JVM.

### 11.1 Responsibilities

1. For each equipment item, derive its applicable `TaskDefinition` set from `typeKey` + attributes (`affectsTasks`) + vessel flag + vessel type + build date.
2. For each definition, compute the next `TaskInstance` from the last completion (or from `installedDate` / `manufactureDate` when there is no completion).
3. Apply the tolerance window (`toleranceDaysBefore` / `After`) and the HSSC anniversary rule where the definition says `AT_SURVEY`: the window is the Safety Equipment Certificate anniversary date ±3 months.
4. Classify: `OVERDUE` (past `windowCloses`), `DUE_SOON` (inside the window or within the user's lead-time setting, default 30 days), `PENDING`.
5. Denormalise the soonest result onto `EquipmentEntity.nextDueDate` / `nextDueTaskKey` so the plan view can colour markers without a join.

### 11.2 When it runs

- On any write to equipment, attributes, or task completion (immediate, in-transaction).
- Daily at 03:00 local via a `PeriodicWorkRequest`, to move items across the date boundary and post notifications.
- On vessel switch and on app cold start.

### 11.3 Notifications

Local notifications only. A daily digest at a user-set time (default 08:00): "3 overdue, 7 due this week." Tapping opens the Due tab pre-filtered. Per-item reminders are opt-in per task definition. Respect notification permission on API 33+; the app must be fully usable if the permission is denied.

### 11.4 Rules the engine must encode correctly

Encode these as **data** in `task_definitions.json`, with `verificationStatus` on each. The intervals below are the seed values; each must carry its source reference and be marked `UNVERIFIED` where the building agent cannot confirm it against the instrument.

| Task | Interval | Performed by | Source |
|---|---|---|---|
| Survival craft, launching appliances & on-load release gear — visual inspection | Weekly | Ship's staff | SOLAS III/20 |
| Lifeboat engine run | Weekly | Ship's staff | SOLAS III/20 |
| General emergency alarm test | Weekly | Ship's staff | SOLAS III/20 |
| Lifeboats moved from stowed position (unless weather prevents) | Monthly | Ship's staff | SOLAS III/20 |
| LSA inspection using the maintenance checklist | Monthly | Ship's staff | SOLAS III/20 |
| Portable fire extinguisher — visual/monthly check | Monthly | Ship's staff | SOLAS II-2/14, MSC.1/Circ.1432 |
| Fire detector / manual call point sample test | Monthly | Ship's staff | MSC.1/Circ.1432 |
| SCBA cylinder pressure check | Weekly (officer check monthly) | Ship's staff | MSC.1/Circ.1432 |
| Portable fire extinguisher — annual service | ≤ 12 months | Competent person (Res. A.951(23)); **flag-dependent whether trained crew may do it** | MSC.1/Circ.1432 |
| Lifeboat / rescue boat / launching appliance / release gear — **annual thorough examination & operational test** | 12 months (HSSC ±3 months) | **Authorised service provider or manufacturer, authorised per make and type** | MSC.402(96), SOLAS III/20 |
| Inflatable liferaft servicing | 12 months (HSSC ±3 months; **extension to 17 months by Administration dispensation** — Liberia and Panama provide for this) | Approved servicing station (Res. A.761(18)) | SOLAS III/20 |
| HRU replacement / servicing | 24 months (disposable) or per maker | Approved station | SOLAS III/20 |
| Inflatable lifejacket service | 12 months | Approved station / trained crew per flag | SOLAS III/20 |
| Immersion suit air-pressure test | 3 years if suit < 10 years old | Per MSC/Circ.1047, MSC/Circ.1114 | Flag notices |
| Fixed gas system — content check | 2 years | Authorised service facility | MSC.1/Circ.1432, MSC.1/Circ.1318/Rev.1 |
| Fire extinguisher test discharge (min. one per type) | 5 years | Service facility | MSC.1/Circ.1432 |
| All fire detectors tested | 5 years | Service facility | MSC.1/Circ.1432 |
| SCBA / EEBD cylinder hydrostatic test | 5 years | Certified facility | MSC.1/Circ.1432 |
| On-load release gear **overload operational test at 1.1 × fully-loaded mass** | 5 years | Authorised provider; **RMI requires an RO surveyor in attendance** | MSC.402(96) |
| Winch brake dynamic test | 5 years | Authorised provider | MSC.402(96) |
| Falls turned end-for-end / renewed | Per SOLAS III/20 & maker | Ship's staff / provider | SOLAS III/20 |
| Fire extinguisher hydrostatic test | 10 years | Facility certified by a government agency or RO | MSC.1/Circ.1432 |
| Fixed-system flexible hoses replaced | 10 years | Service facility | MSC.1/Circ.1432 |
| CO₂ cylinder hydrostatic sampling | 10% at 10 years, escalating to 100% at 20 years | Service facility | MSC.1/Circ.1318/Rev.1 |
| Pyrotechnics (flares) expiry | Per maker's marked expiry (typically 4 years — **read from the item, never assume**) | Renewal | SOLAS III |
| EPIRB battery / hydrostatic release | Per maker; annual performance test | Ship's staff + shore test | SOLAS IV |

### 11.5 Flag overlays

`FlagState` selects an overlay layer applied on top of the base definitions. Seed these overlays with the following (each carrying its own `verificationStatus`, and each surfaced in the app as a **"Flag note"** on the task and on the regulation card):

- **Marshall Islands (RMI)** — Marine Notice **2-011-37** *Life-Saving Appliances and Systems* and **2-011-14** *Maintenance and Inspection of Fire Protection Systems and Appliances*; **2-011-10** *Fire Control Plans, Escape Route Signs and Lifesaving Symbols*; **Technical Circular 1** *Shipboard Equipment and Service Provider Approvals*. Distinctive points to encode: five-yearly overload test and overhaul with an **RO surveyor in attendance**; bulldog grips prohibited on load-bearing wire terminations; fixed-gas cylinders to hold **≥95% of nominal charge**; extinguisher 10-yearly hydrostatic by a facility certified by a government agency or RO; immersion suits under 10 years tested at ≤ 3-year intervals; reports countersigned by the Company representative or Master.
- **Liberia** — Marine Notices **SAF-001** (lifesaving equipment), **SAF-004** (drills), **SAF-005** (survival craft, rescue boat and launching appliances — testing, servicing, maintenance), **SAF-006** (lifejackets), **SAF-007** (immersion suits & TPAs), **FIR-001** (fire-protection systems and appliances), **FIR-002** (paint/flammable liquid lockers). Distinctive points: annual extinguisher servicing may be done by **trained ship's crew** (competence per STCW A-VI/3) as an alternative to a shore facility; fixed CO₂ cylinders **≥90% of nominal charge**; liferaft servicing extension to **17 months** by dispensation; a published list of authorised lifeboat service providers.
- **Panama** — Merchant Marine Circular **MMC-281** *Guidelines for the Maintenance and Inspection of Fire-Protection Systems and Appliances* (superseding MMC-96 and MMC-226) and **MMC-258** *Authorized Service Providers … Lifeboats, Rescue Boats, Launching Appliances and Release Gear*. Distinctive points: onboard maintenance plan must state which tasks are crew-performed (advanced fire-fighting trained) and which need specially trained persons; records may be paper **or computer-based on board** — cite this in the app's "is this app acceptable as a record?" help topic; external technicians must provide inspection reports on completion; provider certificates valid 3 years; ±3-month window on annual LSA performance tests; extensions up to 17 months for certain inflatable equipment with Administration authorisation.
- **Class / IACS** — **UR Z17** *Procedural Requirements for Service Suppliers* governs approval of the firms doing this work: Annex 1 §4 (fire-extinguishing equipment and systems), §5 (inflatable liferafts, lifejackets, HRUs, MES), §13 (lifeboats/rescue boats, launching appliances and release gear — the MSC.402(96) category). Approvals renew at intervals not exceeding 5 years. A service supplier may certify only its own employees unless the flag approves otherwise.

**Mandatory caveat to encode in the app:** notice numbers and revisions change. Every flag card shows the revision and capture date and is marked `verificationStatus = NEEDS_PERIODIC_REVIEW`. The app must never assert that a specific revision is current.

---

## 12. THE DUE TAB

A work list, not a dashboard. Segments: **Overdue** · **This week** · **This month** · **Before next survey** · **Planned**. Each row: symbol, tag, deck, task title, due date with days delta, who must do it (chip), and a swipe-right "mark done" / swipe-left "defer with reason".

Filters: by deck, by zone, by category, by group (LSA / FFE), by performed-by (so the officer can pull the "things I need a shore contractor for" list before the next port), by condition.

**Export the Due list** as HTML or as clipboard text.

A **"Survey prep"** mode: given the Safety Equipment Certificate expiry date, list everything that falls due before it, grouped by whether the ship can do it or a provider must, with an estimated shore-service shopping list.

---

## 13. HTML EXPORT / IMPORT

### 13.1 Why it exists

The export must be **one file**, **self-contained**, **openable by double-click**, and **small enough to send over a messaging app**.

### 13.2 Format

A single `.html` file. Structure:

```
<!doctype html>
  <style>            all CSS inline, no external references
  <script>           all JS inline; no CDN, no network fetch of any kind
  <div id="report">  static HTML generated by the app —
                     the report is fully readable with JavaScript disabled
  <script id="deckwatch-data" type="application/json">
                     the complete dataset as JSON — this is what makes the file re-importable
  <script>           the interactive layer: filtering, deck switching, an SVG
                     re-render of each deck plan with its equipment markers
```

Images: embedded as `data:` URIs, downscaled to max 1280px long edge and JPEG q75. The export dialog offers **No photos / Deficiency photos only / All photos** with a live size estimate.

### 13.3 Export scopes

| Scope | Contents |
|---|---|
| **Full vessel backup** | Everything. This is the re-importable one. |
| **Due list** | The current Due-tab filter as a printable table. |
| **Inspection round report** | One round: items, conditions, remarks, photos, signature block. |
| **Deficiency report** | Open deficiencies with photos and target dates. |
| **Deck sheet** | One deck: the plan as SVG with numbered markers plus a legend table. |
| **PSC / survey pack** | Equipment register + certificate status + last 12 months of rounds + open deficiencies. |

### 13.4 The report design

The HTML report must look professional: a header block with vessel name, IMO number, flag, class, report type, generation timestamp and app version; a summary strip with counts by condition and by due status; then the content. Print stylesheet with A4 page breaks, repeated table headers, and no dark backgrounds. Deck plans re-rendered as inline SVG (isometric or flat, matching the app) so they print cleanly at any size.

### 13.5 Import

`Import from HTML` reads back the `deckwatch-data` JSON block. It must:
- Validate a `schemaVersion` and refuse gracefully on mismatch, with a clear message.
- Show a **preview and merge dialog** before writing anything: N vessels, N decks, N equipment, N conflicts.
- Merge by `id` with a per-record `updatedAt` comparison. Conflicts are resolved by an explicit user choice: *keep mine · take theirs · keep both (duplicate with suffix)*. Never auto-overwrite.
- Handle soft-deleted records (`deletedAt`) correctly so a deletion on one device propagates rather than resurrecting.
- Be transactional: either the whole import applies or none of it does.

Also support **JSON export/import** (the same payload without the HTML wrapper) and **CSV export** of the equipment register and the due list.

### 13.6 Sharing

Write to the app's cache dir, expose via a `FileProvider`, and fire an `ACTION_SEND` chooser. Default filename: `DeckWatch_{VESSELNAME}_{REPORTTYPE}_{yyyyMMdd_HHmm}.html`. Also offer "Save to Downloads" via the Storage Access Framework, and a "Share last report" shortcut.

---

## 14. DESIGN LANGUAGE

**Positioning:** a precision instrument, not a consumer app. Dense, calm, confident, no decoration that does not carry information.

**Colour.** A dark navy / slate neutral ramp as the structural colour. One accent — a signal amber — used *only* for "needs your attention". Condition colours are semantic and fixed: `GOOD` #1B873F · `ACCEPTABLE` #6FA82C · `MONITOR` #E8A317 · `DEFECTIVE` #E5661B · `OUT_OF_SERVICE` #C2261B · `NOT_CHECKED` #8A8F98. LSA signage green #009639, FFE signage red #C8102E — used for symbol grounds only, never as UI chrome.

Three themes: **Day** (high contrast, near-white ground, for sunlight), **Night** (true dark, OLED-friendly), **Bridge** (red-dominant, everything else desaturated, for night watch — no white above 40% luminance). Theme switch is one tap from the app bar, and follows an optional automatic schedule.

**Typography.** One variable sans for UI (Inter or the system default), one monospace for tags, serials and certificate numbers — a monospace with disambiguated 0/O and 1/l/I prevents real mistakes.

**Density.** Compact by default with a comfortable-density setting. List rows 56dp compact / 72dp comfortable.

**Motion.** Purposeful only. The deck fan, the deck-to-deck slide, the marker pulse and the sheet transitions are the only animations that exceed 200ms. Respect `Settings.Global.ANIMATOR_DURATION_SCALE` and reduced-motion.

**Empty states.** Every empty screen teaches: an illustration, one sentence of what goes here, and the single button that starts it. The first-run empty state on the Vessel tab offers *"Add your first deck"* with the six plan presets visible immediately.

**Accessibility.** Full TalkBack labelling including the deck canvas (announce markers as "Fire extinguisher F-E-U-D-0-3, condition good, next due 12 March"). Minimum contrast 4.5:1 in all three themes. Every graphical interaction has a list-mode equivalent. Support font scaling to 200% without clipping.

**Onboarding.** Four screens maximum, skippable, then a guided "build your first deck" flow. Ship a **demo vessel** ("MV Example", 5 decks, ~60 equipment items, a few overdue and a couple of deficiencies) that the user can load in one tap and delete in one tap.

---

## 15. GITHUB — CI AND AUTOMATED FULL RELEASE

Three workflows: `ci.yml` (push/PR: ktlint+detekt, unit tests, assembleDebug, artifact upload), `release.yml` (tag `v*.*.*` or manual: signed universal + per-ABI APKs, AAB, SHA-256 checksums, generated changelog, GitHub Release), `nightly.yml` (nightly develop build). Signing config reads keystore from env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and falls back to debug keystore locally. Version from `VERSION_NAME`/`VERSION_CODE` env vars. ABI splits + universal APK, R8 full mode, resource shrinking. Dependabot weekly for gradle + actions. Branch protection on `main`. README badge row. Issue template for "Regulatory content correction". Required secrets documented in README: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

---

## 16. BUILD PHASES

| Phase | Deliverable |
|---|---|
| **0 — Foundation** | Repo, version catalogue, convention plugins, Hilt, Compose scaffold, 4-tab navigation, theme (all three variants), ktlint/detekt, `ci.yml` green. |
| **1 — Data layer** | Full Room schema from §6, SQLCipher, DAOs, migration test harness, seed loader, fake repositories in `core-testing`, demo vessel seed. |
| **2 — Vessel & decks** | Vessel CRUD, deck CRUD with the `levelIndex` insert-above/below/between mechanic, plan presets, zones. List mode fully working. |
| **3 — The 2.5D renderer** | Stack mode, deck mode, flat/iso animated toggle, fan spread, gestures, deck spine, LOD markers, culling, Macrobenchmark baseline. |
| **4 — Equipment core** | Catalogue (first 60 types), add flow, dynamic attribute forms, marker placement and drag, duplicate ×N, equipment sheet all three stages. |
| **5 — Symbols** | First 60 symbols (LSS + FES + core MES/EES), symbol picker, tinting, `SYMBOL_LICENSING.md`. |
| **6 — Inspection & due engine** | Quick-action condition chips, sweep mode, rounds, task definitions seed, due engine with full unit-test coverage, Due tab, notifications. |
| **7 — Regulatory notes** | Notes tab, card component, all six sections seeded, interval matrix, contextual surfacing, user notes, search. |
| **8 — Survival craft views** | Lifeboat schematic + hotspots + inventory + davit tasks; then generalise to `SchematicScreen`; liferaft, rescue boat, fixed CO₂, SCBA. |
| **9 — Export / import** | HTML export (all six scopes), photo size tiers, SVG deck rendering, import with merge/conflict UI, JSON and CSV, sharing. |
| **10 — Catalogue & symbol completion** | Remaining equipment types and symbols to the §9.2/§10.3 coverage lists. |
| **11 — Polish & release** | Onboarding, empty states, accessibility pass, Turkish localisation review, backup/restore, `release.yml` green, v1.0.0 tagged. |

---

## 17. DEFINITION OF DONE

### 17.1 Functional
Every requirement in §§5–13 implemented, no placeholder screens, demo vessel loads and looks impressive.

### 17.2 Quality
- Unit test coverage ≥ 80% on `core-model`, `core-common`, the due engine and the export/import serialiser.
- Every Room migration has a test.
- Compose UI tests for: add-equipment flow, quick-action grading, deck navigation, import conflict resolution.
- ktlint and detekt clean, zero warnings suppressed without a comment explaining why.
- No `!!` outside test code. No `GlobalScope`. No blocking calls on the main dispatcher.

### 17.3 Performance
Cold start < 1.5s on a mid-range device. Deck view holds 60fps at 20 decks / 600 markers. Export of a 300-item vessel with deficiency photos completes in < 5s and produces a file under 10MB.

### 17.4 Robustness
Process death and restore preserves navigation and unsaved form state. Rotation loses nothing. The app survives a corrupted import file, a truncated HTML file, and a 200MB photo without crashing.

### 17.5 Documentation
`README.md`, `DATA_MODEL.md`, `DECISIONS.md`, `SYMBOL_LICENSING.md`, `CONTRIBUTING.md`, and a `docs/REGULATORY_SOURCES.md` listing every instrument cited with its capture date.

### 17.6 The disclaimer — verbatim

Show this on first run, in the Notes tab footer, in Settings → About, and in the footer of every exported HTML report:

> **DeckWatch is a planning and record-keeping aid. It is not a certificate, not a substitute for the vessel's approved plans, the manufacturer's manuals, class rules or the flag Administration's instructions, and it does not discharge any statutory obligation. Regulatory content is a summary captured on a stated date and may be superseded. Always verify against the current instrument and the vessel's own documentation. The Master's and the Company's responsibilities under SOLAS and the ISM Code are unaffected.**

---

## 18. SETTINGS, BACKUP AND SECURITY

- **Database encryption** via SQLCipher with a key held in the Android Keystore. Optional biometric/PIN app lock, off by default.
- **Backup**: manual "Export full backup" (encrypted `.dwbackup` = the JSON payload + photos in a zip, optionally passphrase-protected) and an automatic weekly backup to a user-chosen SAF folder, keeping the last 8. Restore with the same merge dialog as import.
- **Android auto-backup disabled** (`android:allowBackup="false"`).
- **Data export on uninstall warning**: if the user has never taken a backup, prompt for one on the 30th day of use.
- Settings: theme + schedule, language, density, due lead-time, notification time, default flag, isometric angle, grid snap, tag auto-numbering format, photo quality, units (metric default), first day of week.

---

## 19. SEED CONTENT — MINIMUM BAR

Ship these seeded on first run:
1. **`equipment_catalogue.json`** — the types in §9.2 with attribute schemas and task links.
2. **`task_definitions.json`** — the tasks in §11.4 plus the flag overlays in §11.5, each with `sourceRef`, `verificationStatus` and `lastReviewed`.
3. **`symbols.json` + `SymbolLibrary.kt`** — the keys in §10.3.
4. **`regulations.json`** — the note cards for §8.1's six sections. Minimum 120 cards. Every card carries WHAT / HOW OFTEN / BY WHOM / EVIDENCE.
5. **`round_templates.json`** — Weekly LSA, Weekly FFE, Monthly LSA, Monthly FFE, Pre-arrival PSC self-check, Pre-survey SEC check, Post-drill check.
6. **`demo_vessel.json`** — MV Example: 5 decks (Upper Deck as level 0, A Deck and Bridge Deck above, Engine Room 2nd Flat and Engine Room Floor below), ~60 items including 2 lifeboats with sub-components, 3 liferafts, 20 extinguishers, a fixed CO₂ system, 4 SCBA sets, 3 open deficiencies and 5 overdue tasks.
7. **`plan_presets.json`** — the six deck outlines in §6.3.

---

## 20. NAMING, LICENCE, HOUSEKEEPING

- App name: **DeckWatch**. Package `com.deckwatch.app`.
- Licence: MIT for the code. State clearly in `README.md` that the **regulatory content and any symbol artwork have their own provenance**, documented in `docs/REGULATORY_SOURCES.md` and `docs/SYMBOL_LICENSING.md`, and are not covered by the code licence.
- Conventional Commits. Semantic versioning. `main` always releasable.
- No secrets, keystores, or `local.properties` in the repository. `.gitignore` must cover `*.jks`, `*.keystore`, `local.properties`, `.env`.

---

## 21. WHAT "GOOD" LOOKS LIKE

When this is finished, a 3rd Officer joining a new ship should be able to: open the app, create the vessel from its IMO number, lay out eight decks in ten minutes using presets, walk the ship once with sweep mode and end up with a complete graded equipment register, see immediately that four items are overdue and two need a shore contractor before Rotterdam, export a deck sheet to print for the weekly round, and WhatsApp the Chief Officer a one-file report that opens on his phone and looks like it came from the office.

That is the bar. Build to it.
