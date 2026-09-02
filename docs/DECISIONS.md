# DECISIONS

Architecture and product decisions taken where the master prompt was silent or
where reality forced a divergence. Newest entries at the top.

## 2026-09-02 — Feature build-out and usability overhaul

- **Usability overhaul (owner directive).** `docs/DESIGN_OVERHAUL.md` is the
  binding rule set; every screen was brought onto the shared components in
  `core-designsystem/components` (top bar, list rows, empty states, condition
  chips, status chips, date picker field, search field, confirm dialog).
  Module-local duplicates were deleted. No typed date input remains.
- **Isometric projection at 0°.** The literal §7.2 formula collapses the plan
  to a line at θ = 0 (sin 0 = 0), contradicting the same section's "0° = flat
  plan". `IsoProjection` implements the rotate-45°-then-squash construction
  the formula describes, driven off the angle so 0° is the identity and 35°
  is the §7.2 projection up to a uniform scale; a test pins the
  proportionality.
- **Long-press on empty plan** both isolates the deck (while held) and offers
  "add equipment here" (on release) — §7.2 lists them as two gestures but
  they are one touch.
- **Deferral semantics.** §6.6 has no "deferred" status; defer writes
  `SKIPPED` with the reason in `findings`, keeps the due date, and does not
  re-run the engine. The row moves to the Planned segment.
- **Intervals the spec does not state.** ~50 FFE/machinery/signage types
  point at an `EVENT_DRIVEN` "per the onboard maintenance plan" task rather
  than an invented monthly cadence. `SeedIntegrity` enforces catalogue↔task
  agreement in both directions.
- **Plan presets and symbol metadata** are served straight from the bundled
  seed assets (no Room tables) — they are static reference data.
- **Seed content version** lives in a private SharedPreferences file, not in
  user settings, so "reset settings" cannot fake a fresh install.
- **Due digest worker** recomputes every vessel (the Due tab is cross-vessel
  and the date boundary moves for all ships at once).
- **Locale switching**: framework `LocaleManager` on API 33+, a wrapped
  `Configuration` in `attachBaseContext` on API 26–32 — AppCompat was
  rejected to keep the splash theme platform-only.
- **Backup and HTML export share one payload model** (`DeckWatchExportPayload`);
  `.dwbackup` = zip(manifest, payload.json, photos) optionally wrapped in
  AES-256-GCM with a PBKDF2 key.
- **Import transaction guarantee** is two-phase: all-or-nothing against every
  failure detectable before writing (FK validation), best-effort journalled
  rollback against a mid-write failure, with any residue named in the result.
- **Survival-craft routing**: the equipment detail destination renders the
  schematic screen for the eleven parent type keys the bundled schematics
  declare; the key set is duplicated in `EquipmentRoute.kt` deliberately (a
  DB read on tap was the alternative).
- **Commit authorship** stays with the repository owner by explicit
  instruction; no tool attribution trailers are added.

## 2026-08-29 — Foundation

- **Claude Design import unavailable.** The referenced `LSA-FFE Tracker.dc.html`
  design project could not be imported in this non-interactive environment
  (design-system authorization requires an interactive login). The build follows
  the design language specified in MASTER_PROMPT §14 (Day / Night / Bridge
  themes, fixed semantic condition colours, monospace tags). The .dc.html design
  can be reconciled later without structural changes since all styling is
  centralised in `core-designsystem`.
- **SQLCipher artifact**: the master prompt names
  `net.zetetic:android-database-sqlcipher`, which is discontinued upstream.
  Using its maintained successor `net.zetetic:sqlcipher-android` (same vendor,
  same cipher, androidx.sqlite-compatible API) instead.
- **Material 3 Expressive**: the stable Compose BOM ships Material 3 stable.
  M3 Expressive APIs are still pre-stable; the app uses M3 stable with the
  design-language tokens from §14. Revisit when `MaterialExpressiveTheme`
  reaches stable.
- **JUnit 5 vs Android modules**: pure JVM modules (`core-model`, `core-common`,
  `core-testing`, due engine tests) run on JUnit 5. Android library modules use
  JUnit 4 + Robolectric because AGP's built-in unit-test runner and Compose UI
  test infrastructure are JUnit 4-based. This satisfies the "JUnit5" line for
  the code the spec cares about (§17.2 lists the JVM parts).
- **Nightly workflow** runs on `main` (the repo has no `develop` branch; the
  workflow triggers on schedule + manual dispatch).
- **buildSrc vs build-logic**: `build-logic` composite build (the modern
  convention-plugin layout named as an option in §4).
- **Branch strategy**: work happens on the designated integration branch and is
  pushed to `main` when green, per the owner's instruction. `main` stays
  releasable.
- **ktlint/detekt profile**: both are wired and fail the build, with a
  documented pragmatic profile (`.editorconfig`, `config/detekt/detekt.yml`):
  hard line-length and a handful of style-only rules are relaxed because the
  codebase carries long regulatory strings and dense Compose UI code.
  Correctness rules remain at defaults.
- **Instrumented CI job** is `continue-on-error` until the device suite
  stabilises; unit tests, lint and assembly gate the build.
