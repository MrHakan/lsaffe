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

This document is expanded alongside the database module (entity-by-entity
notes, indices, migration history). Every migration ships with a test.
