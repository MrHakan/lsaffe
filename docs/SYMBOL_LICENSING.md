# SYMBOL LICENSING

## Position

The IMO/ISO safety-sign pictograms (Res. A.1116(30), ISO 24409-2, ISO 7010,
ISO 17631) are published in copyrighted, paid standards; ISO licenses symbol
artwork separately and per symbol. There is no official free vector set.

**DeckWatch therefore ships only originally-drawn vector icons.** Every
pictogram in `core-designsystem`'s `SymbolLibrary` was drawn from scratch as a
Compose `ImageVector`:

- **What was drawn from scratch:** all of it — every path in every symbol.
- **Reference used for geometry:** the written descriptions and the colour /
  shape conventions of ISO 3864-1 (sign shapes and safety colours) and the
  public descriptions of the A.1116(30) sign categories (green square =
  life-saving/escape, red square = fire-fighting, blue circle = mandatory,
  etc.). No vendor SVG was copied, traced, or auto-converted. No standard's
  artwork file was reproduced.
- **Fidelity note:** because the icons are re-drawn interpretations, they are
  suitable as *in-app markers* but are **not** certified signage artwork. Any
  organisation deploying the app in a regulated signage context (printing
  signs, official fire control plans) should obtain an ISO artwork licence and
  use certified artwork.

## Provenance rule

Do not commit any symbol file whose provenance you cannot state in this
document. New symbols must append an entry to the batch list below.

## Batches

- **Batch 1 (foundation):** core LSS/FES/MES/EES marker set, drawn from
  scratch in Compose `ImageVector` path notation. Author: project maintainers.
- **Batch 2 (full canonical set, `core-designsystem/symbols`):** all 99 keys of
  `docs/SYMBOL_KEYS.md` — 23 `LSS`, 41 fire-fighting (12 `FES` + 29 `APP_*`
  red-set extensions), 6 `MES`, 12 `EES` and 17 neutral `APP_*` machinery,
  control, document and survival-craft markers. Every path was composed from
  scratch out of the geometric primitives in `SymbolPrimitives.kt`
  (rectangles, discs, annular bands, thick line segments, teardrops, a heart
  and a 12-gon cross) using `moveTo`/`lineTo`/`curveTo`/`arcTo` only. No vendor
  SVG, ISO/IMO artwork file or icon-library path string was copied, traced,
  auto-converted or measured off; the drawings are plain-language
  interpretations of the sign descriptions (e.g. lifebuoy = ring with four
  lashings, extinguisher = bottle with lever and nozzle, hydrant = post with
  side outlets, SCBA = cylinder with hose and mask). House rules: 24 x 24
  viewport, ~2 unit content margin, fills only (no strokes), white pictogram on
  a transparent ground so one vector serves the green/red/slate grounds and all
  six fire-control-plan media colours, and all sub-paths wound clockwise so
  overlapping shapes union. `SymbolLibraryTest` asserts the key list, the
  series/ground mapping, the media-tintable set and that every vector is a
  24dp white fill-only drawing. Author: project maintainers.
