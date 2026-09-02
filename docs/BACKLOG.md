# BACKLOG

Deferred work. Nothing in this list is stubbed as fake UI in the app — a
feature is either implemented or absent and listed here. Items are grouped
by module; each came from the implementation report of that module.

## Cross-cutting
- Macrobenchmark module measuring deck-view frame timing on a seeded 20-deck
  vessel (CI informational job) — needs a root/settings change.
- Compose UI test suite (§17.2: add-equipment flow, quick-action grading,
  deck navigation, import conflict resolution) — only JVM/Robolectric unit
  tests ship today (≈600 across modules).
- JaCoCo coverage measurement; the ≥80 % bar on the due engine and the
  serialiser is asserted by test breadth, not by a measured number.
- Biometric/PIN app lock (§18) — off by default and not yet implemented.
- Release-please / git-cliff automated changelog pipeline; branch protection
  on `main` must be enabled in repository settings.
- `tagNumberFormat` is stored and previewed in Settings but
  `feature-equipment`'s tag suggestion still hard-codes `PREFIX-DECK-NN`.
- Notification *time* preference is stored, but the digest is posted by the
  03:00 recompute worker right after it runs; honouring the chosen hour needs
  a second scheduled work request in `data-repository`.
- Turkish plain-language summaries (`summaryTr`) exist on only three
  regulation cards and are not surfaced by the shared card component.
- Favourites on regulation cards are in-memory (§6 defines no table).

## Symbols and catalogue
- Fire-control-plan symbol set `SIS001`–`SIS052` (§10.3) and the remaining
  ISO 7010 red-set extensions; some catalogue types reuse the nearest
  available key (e.g. smoke-only lifebuoy → `LSS005`).
- Sub-component types without a catalogue entry (drain plug, sheave, limit
  switch, boat compass, fuel tank, SCBA mask/reducer/gauge/harness,
  fireman's suit/boots/gloves/helmet) so schematic hotspots can bind to a
  real type.

## Deck view
- `DeckPlan.backgroundImageUri` (traced GA plan) — draw-order slot exists,
  bitmap loading not wired.
- Free-form zone polygon drawing on the canvas (zones are rectangles from
  the list-mode editor today).
- Accessibility nodes are capped at 256 per deck; list mode is the complete
  non-graphical equivalent.

## Survival craft
- Dedicated free-fall lifeboat schematic (uses the davit-launched drawing).
- MES / fire pump / foam / water-mist schematics (data-only additions).
- Boat inventory expiries feed the on-screen summary but not the due engine.

## Export / import
- Tombstone enumeration: `EquipmentRepository` has no "including deleted"
  listing, so only tombstones referenced by a round item or deficiency are
  exported. Needs `observeEquipmentIncludingDeleted(vesselId)`.
- Import rollback cannot withdraw rows from repositories that expose only
  `upsert…` (task instances, rounds, deficiencies); the residue is named in
  the result rather than hidden. A repository-layer transaction would close
  this.
- Photo bytes are embedded in the HTML report but not re-importable from it;
  the `.dwbackup` archive carries photos.
- Isometric deck plan in reports (flat plan only, matching §13.3).

## Equipment
- Photo/signature capture flows (attributes of kind PHOTO/SIGNATURE store a
  URI; no camera integration yet). Photo thumbnails in the detail screen.
- Undo snackbar for hard-deleted records (zones, categories, notes) — only
  soft-deletable equipment has undo today.

## Backup
- Automatic weekly backups are never encrypted (a background job cannot
  prompt for a passphrase).
