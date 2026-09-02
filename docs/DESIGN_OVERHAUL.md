# DESIGN OVERHAUL — usability first

Owner directive (2026-09-02): *overhaul the project design for the best
usability*. This document is the binding rule set. Every screen in every
feature module is brought into line with it; the shared building blocks live in
`core-designsystem/components` and MUST be used instead of module-local
copies.

## Who we design for

A 3rd Officer on deck: gloves, wind, sunlight, in a hurry, often one-handed,
sometimes at night on a red-lit bridge. They know the equipment, not the app.
The app must be **obvious**, **fast** and **forgiving**.

## The ten rules

1. **One primary action per screen.** It is a 56dp button or FAB, bottom-right
   or a full-width bottom action bar. Secondary actions live in the top bar
   (max two icons) or an overflow menu. Never three competing buttons.
2. **Same chrome everywhere.** `DeckWatchTopBar`: title (one line), optional
   back, optional vessel selector slot, ≤2 actions + overflow. No custom
   headers per module.
3. **Big targets.** 48dp minimum, 56dp for primary and for the condition
   chips. List rows 56dp (compact) / 72dp (comfortable). Chips ≥40dp tall.
4. **Never type a date.** Every date is a `DateField` (tap → Material date
   picker, shows ISO date, has a clear button). Typed `YYYY-MM-DD` inputs are
   a defect.
5. **Condition is one control.** The five-grade `ConditionChipRow` from the
   design system — icon + label, semantic colour, selected state, 56dp — is
   the ONLY way a grade is set anywhere (sheet, round run, list mode).
6. **Status reads at a glance.** `StatusChip` (task status / deficiency
   status / severity / day delta) with the fixed semantic colours; the text
   says the thing ("3 d late", "Overdue", "Closed"); colour is never the only
   signal.
7. **Every empty screen teaches.** `EmptyState(icon, title, one sentence,
   one button)`. The Vessel tab's first-run empty state shows the six deck
   presets immediately.
8. **Destructive = confirm, then undo.** Delete/close/uninstall use
   `ConfirmDialog`; soft-deletable things additionally offer a 10-second undo
   snackbar. Nothing is lost silently (C10).
9. **Search first, browse second.** Long lists (catalogue, cards, register)
   open with `SearchField` at the top, keyboard-ready.
10. **Feedback every time.** Haptic tick on grading, snackbar on save/undo,
    progress on long work (export/import/seed), explicit error text next to
    the field that caused it.

## Information architecture (unchanged, tightened)

| Tab | Default content | Primary action |
|---|---|---|
| Vessel | Deck stack (or list mode) of the active vessel | Add equipment (long-press / FAB) |
| Due | Overdue segment of the active vessel | Mark done (swipe / row action) |
| Notes | Six sections + search | — (search) |
| More | Sectioned list: Vessel manager · Reports & export · Backup · Settings · About | — |

The vessel selector sits in the top bar of Vessel / Due / More. Switching a
vessel changes every tab.

## Forms

- Required fields marked with `*` and validated inline; the save button is
  enabled only when valid, and the first error scrolls into view.
- Numeric fields use numeric keyboards with a unit suffix (kg, L, bar).
- Enum fields are dropdowns; ≤4 options may be segmented buttons.
- Dynamic attribute forms group fields: identification → dates → checks.
- A "live consequence" line appears under any date that drives a due date
  ("→ next annual service 2027-03-12").

## Motion & themes

Only the deck fan, deck slide, marker pulse and sheet transitions exceed
200 ms; respect reduced motion. Day theme default; Night and Bridge one tap
away from the top bar overflow ("Theme"). Bridge: no white above 40 %
luminance.

## Accessibility

contentDescription on every icon-only control; markers announce
"<type> <tag spelled>, condition <grade>, next due <date>"; text containers
use `heightIn(min = …)` so 200 % font scaling never clips; contrast ≥ 4.5:1
in all three themes.

## Definition of done for the overhaul

- No module defines its own top bar, empty state, condition chip row, date
  input or status chip.
- Every screen has exactly one primary action or none.
- `grep -r "YYYY-MM-DD"` over feature modules returns nothing.
- Turkish strings reviewed for maritime terminology.
