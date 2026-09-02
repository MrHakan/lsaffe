# DATA MODEL

Authoritative schema: MASTER_PROMPT §6, implemented in `core:core-database`
(Room + SQLCipher) with domain models in `core:core-model`.

Conventions:

- All IDs are String UUIDv4 — export/import and multi-device merge never
  collide on integers.
- Timestamps (`createdAt`, `updatedAt`, `conditionSetAt`, …) are epoch-millis
  UTC.
- **Dates** (service dates, due dates, `manufactureDate`, …) are **epoch-days**
  so timezone drift never shifts a due date.
- Soft deletes via `deletedAt` on equipment; deletions propagate on import
  instead of resurrecting.
- `EquipmentEntity.nextDueDate`/`nextDueTaskKey` are denormalised by the due
  engine so the deck view colours markers without a join.
- Dynamic type-specific attributes live in `attributesJson`, validated against
  the type's `attributeSchema` (see §9.3).

Tables: `vessels`, `decks`, `zones`, `categories`, `equipment_category_xref`,
`equipment`, `task_definitions`, `task_instances`, `rounds`, `round_items`,
`deficiencies`, plus reference tables seeded from `data-seed` JSON
(`equipment_types`, `regulation_cards`, `user_notes`, `round_templates`).

## Implementation notes

- Database version 1; schema exported to `core/core-database/schemas/`.
  `MigrationHarnessTest` is the scaffold every future migration test extends.
- Encryption: SQLCipher (`net.zetetic:sqlcipher-android`); a random 32-byte
  database key is wrapped by an AES/GCM key in the Android Keystore and the
  wrapped bytes are kept in private preferences. Tests open the same schema
  through the plain framework SQLite factory.
- Reference tables (`equipment_types`, `regulation_cards`, `round_templates`,
  `user_notes`) are filled by `SeedInitializer` on first run and on a content
  version bump; user notes and user-defined rows are never touched by
  seeding. Plan presets and symbol metadata are read straight from the seed
  assets.
- Dynamic per-type data stored inside `equipment.attributesJson`: the
  attribute values of §9.3, plus `inventory` (survival-craft checklist) and
  `hotspotKey` (schematic binding) — no extra tables.
- Rounds double as drill records (`templateKey = DRILL_<typeKey>`) and sweep
  records (`templateKey = SWEEP_<deckCode>`).
- Export/import payload: `DeckWatchExportPayload` (schemaVersion 1) reuses
  these models directly; equipment tombstones (`deletedAt`) travel with it so
  deletions propagate on merge.

Every migration ships with a test.
