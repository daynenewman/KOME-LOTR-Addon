# Tile/geography review package - 2026-10-02

Feature base: current origin/dev `76fb78e7fc07a6d3421b5aa1c597e86ef36af6bf`.
Branch: `dayne/tile-geography-review-20261002`. Local review only; no merge/deploy.
See [progress](PROGRESS.md) for checkpoints and [preservation](preservation-before.json).

## KOM-58 metadata contract

`assets/kome/map/tile_exclusions.tsv` is mandatory for the bundled loader. Schema 1 pins the exact PNG SHA-256 and raster dimensions; bundled resources, not resource packs, supply the authority. UTF-8 must be valid, input <=4 MiB, <=65,535 unique named zones, and every zone must have cells. Mask input <=64 MiB and existing decoded dimension/pixel bounds apply. Tabs separate records; definitions precede runs. Example below is **test syntax only, not approved geography**:

```text
schema=1
width=4
height=1
mask_sha256=<exact PNG SHA-256>
zone	river-a	river	Explicit approved decision reference and reason
run	0	1	2	river-a
```

`run` means `y, xStartInclusive, xEndExclusive, zoneId`. Coordinates are nonnegative canonical cell indices. IDs/types match `[a-z][a-z0-9_.-]{0,63}`; reasons must be nonempty, <=256 characters with no control characters. Unknown references, duplicate IDs/runs, overlapping zones, outside cells or active tile overlap fail validation. Holes *inside* tile areas may be annotated only where the canonical cell is already a gap; changes to active geometry need a separate approved resource edit and persisted-reference impact check.

The production set is deliberately empty: **no geographic reasons have been approved**. The pinned mask stays `ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`. Tiles, IDs, ownership and rendering stay as before. Transparency/retired colors/biomes do not assign geographic purpose. Tile capturability remains unknown; an explicit exclusion reports false. Traversability remains unknown for every tile/zone, leaving future unit-specific river/boat/bridge/pass policy open. No movement or war rules are added.

Resolution adds `CLASSIFIED_EXCLUSION` with immutable optional `Zone{id,type,reason}` and empty tile ID. `IN_BOUNDS_GAP` remains unclassified. `OUTSIDE_MASK` means outside the existing raster extent; it does **not** establish an unapproved interior playable-region boundary. Unsupported dimensions and invalid resources/coordinates remain distinct. Geometry-only fixture loading explicitly reports metadata not loaded. Production loading validates geometry and annotations together before atomic publication; invalid replacement retains the complete last-valid snapshot and exposes rejection through `loadDiagnostic()`.

Awareness compares full immutable zone metadata as well as status/dimension/tile ID, suppresses stationary/same-zone events, and reports metadata replacement on the existing geometry-change path. HUD labels distinguish exclusions and refresh on zone/type changes. No packets, persistence schemas, strategic records or recruitment/company policies change.

## Validation and remaining acceptance

Focused implementation tests and `compileJava` passed. Final build, detailed measurement results and the coordinate review atlas will be appended in the next checkpoint. External Claude review was explicitly authorized but unavailable: OAuth expired and could not refresh. Local plan and diff self-review continue; no successful external review is claimed.

Manual blockers: approved zone cells/reasons and KOM-59 geography decisions; live multiplayer/physical hire/respawn checks on the combined candidate; measured client FPS/pan/zoom/reload at target GUI scales; representative low-spec hardware. These remain open and are not inferred from unit tests. KOM-77 remains explicitly deferred/non-blocking.
