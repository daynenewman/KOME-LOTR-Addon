# Authoritative conquest-map borders â€” local candidate

## Reviewed candidate update

The follow-up [review](REVIEW.md) fixed retained texture buffers, texture-pass GL
state restoration, and oversized Tessellator batches. Geometry/preview quads are
unchanged. Current build: **811 passed, two existing skips (813 discovered)**.
Current production JAR SHA-256: `cfd32d8df548cbecf5b081619a3350346445ca797cfb7f5878964b64e09edec1`.
The original measurements and validation below describe the initial candidate;
`review-validation.json` records the replacement. Neither candidate was installed.

## Scope and authority

The map now draws borders from the exact installed `KOMETileRasterSnapshot`, not
`reset_conquest_borders_thin.png`. The historical PNG is retained as an asset for
reference but is not loaded by production code. The mask remains:
`46af1f42854c4c12b7f9ce69b3aad7ad4513340b000631f62514937debf0dbed`.
No geometry cell, canonical ID, gameplay table, world record, network packet or
permission rule was changed. This candidate has **not** been installed in either
running validation profile. No server/client start, stop, or world edit occurred.

Recovery before edits:
`C:/Users/dayne/Documents/KOME-Recovery/before-authoritative-borders-20260920T032652Z-b70a5197`.
All 1,138 original tracked/untracked files were copied and hash-verified; status,
branch, HEAD, tracked/index binary diffs and a verified committed-history bundle
were preserved. Inventory/content checks after copying detected no concurrent edit.

## Rendering audit and correction

`KOMEConquestMapOverlay.onDrawMap` is the existing Forge post-map renderer. Native
LOTR draws its terrain first. KOME then draws ownership fill, desert shade, hover
fill, geographic borders, labels, routes and markers, and tooltips. C/R/B/T controls,
public inspection and the packet-filtered ownership/troop/Build data paths remain.
The separate desert shade, label, troop, and bridge art are unchanged.

Two old border sources conflicted with the current geography: static thin-border
art predated the pilot/V2 edits, and ownership/hover textures darkened whole edge
cells. Each of two neighbors could contribute a whole-cell edge strip, whose
visual width grew with zoom. Both edge treatments are retired. Ownership fill
still rebuilds only for an existing conquest revision, uses existing client data,
and uses nearest filtering supplied by Minecraft DynamicTexture/TextureUtil.
Hover no longer scans/uploads the 12.8-million-cell raster when its tile changes.
It paints only visible cached runs for the hovered canonical color.

`KOMEMapBorders` compares the canonical snapshot's detached decoded ARGB copy.
Transparency/retired IDs are already handled by the validated loader. It compares
normalized tile colors, ignoring opacity differences between assigned same-ID cells.
Every different-label cell adjacency is visited exactly once. Same-ID adjacency
has no edge. It merges consecutive collinear edges with the same two labels;
merging stops where the incident tiles change, including junctions. Diagonals keep
the exact raster staircase; no smoothing or invented polygon is introduced.

Assigned/assigned borders use a single centered dark stroke. Hover restyles that
same edge gold, rather than adding another outline, and retains a pale tile fill.
Gap and outer-map edges put the entire stroke on the assigned side, preserving
unassigned corridor width. The outside of the raster is treated as no assignment:
outer assigned cells have an inward edge; gap-to-outside has none. The normal
stroke is 1 GUI pixel, hover 1.5, independent of zoom; each is capped to one projected
cell when cells become smaller. Raster geometry and selection are never changed by
these visual strokes. No extra setting or screen was added; the existing C conquest
overlay toggle still controls the layer. GUI scaling scales the GUI consistently.

`KOMEMapViewport` is the shared forward/inverse affine transform used by rendered
borders, clipped KOME texture layers, and the actual hover/right-click path. It uses
LOTR's current posX/posY/zoomScale and GUI map rectangle every draw, with snapshot
map-to-raster proportions. Removing rounded texture-edge clipping prevents a
fractional-pixel divergence near outer bounds. GUI resizing changes the current
rectangle; no cached screen coordinates or native framebuffer workaround is added.
The unrelated Windows maximize problem is not addressed here.

The authoritative map is 3200x4000; one cell is 128x128 world blocks. World zero is
mask (810,730); +X goes right and +Z down. Rendering uses double GUI coordinates;
authoritative containment remains the unchanged exact common floor resolver.
Right-click/hover still call `tileAtMapPosition` through that resolver, with no
neighbor search, clamping, or center-derived geographic inference.

## Cache, lifecycle, privacy and limits

Edges and hover spans are primitive arrays with grid-line offsets. View queries
visit only indexed visible rows/columns and binary-search their first intersecting
run. There are no per-cell objects or unbounded coordinate caches. Construction is
bounded by **1,000,000 total edge/fill runs** (at most roughly 16 MB of packed run
payload plus indexes; temporary builder buffers can increase construction peak).
An excessive presentation fails explicitly without invalidating the common resolver.

One client-thread cache is keyed by **snapshot object identity**. It clears before
replacement and publishes only the fully built result. A null resolver clears old
geometry, fills, palette and centers. Construction failure clears the presentation,
logs once for that snapshot, and retries only a changed snapshot or resource reload.
Existing client-thread session reset now clears the geometry/ownership texture;
connect/disconnect is routed through the existing client task queue. The resource
reload listener also releases the other KOME map-art textures and rebuilds lazily.
Client resource packs still cannot redefine canonical server geometry.

All new code is in `kome.client`. It contains no WorldData mutation, ownership table,
route inference, gameplay references, or packet changes. Geographic borders reveal
only already-public canonical geometry. Retired colors remain gaps; unknown opaque
colors still fail the common loader. Dynamic ownership and private markers still
come from their unchanged server-filtered client caches.

The old full-size highlight texture and static-border DynamicTexture are no longer
allocated: each formerly held 51,200,000 bytes of CPU RGBA plus a corresponding GPU
texture. The existing decoded rendering copy and claimed texture remain; new border
cache measurements do not include those, the common snapshot, or driver overhead.

## Evidence and reproduction

`KOMEMapBordersTest` tests the production extraction and drawing/transform methods,
including shared/same-ID edges, transparent RGB/alpha, preserved corridor width,
hover restyling, junctions/staircases, all outer edges, viewport culling, odd/even GUI
rectangles, anisotropic mask/map scaling, negative world coordinates, snapshot
replacement/failure, production disconnect/reload clearing, and a pathological
checkerboard cap. It expands every emitted real V2 edge against original raster
neighbors and independently counts all horizontal/vertical transitions; source
pixels remain unchanged. Real controls include pilot (975,727), T171/T132
(957,746), junction (950,713), R1 (1083,735), and the protected northern gap.

Focused runs passed: 52 border/HUD/Build/gameplay/isolation cases plus 24 public-access
packet cases. An initial 18-case run is overlapping evidence, not an additional
unique-test count. The public-access filter was corrected to its actual package;
no test was removed or weakened. Final clean results: **808 discovered, 806 passed, two skipped, zero failed/errored**
(`BUILD SUCCESSFUL in 1m 9s`). The two existing skin-cache/library tests skip when
Windows symbolic-link creation is unavailable. See `final-validation.json`.
An initial successful clean build was repeated only after final review found that
the new strict viewport needed a collapsed/nonfinite-bounds guard during resize;
that production-path regression passed before the corrected final build.

Reproduce the presentation report and offline illustrations:

```powershell
.\gradlew.bat test --tests 'kome.client.KOMEMapBordersTest' --no-daemon --console=plain
python docs/tile-border-rendering-20260920/preview.py
```

The test writes `build/reports/tile-borders/metrics.txt` and `preview-quads.tsv`.
The Python script uses those **actual production-generated quads**, the installed
mask, the historical border artwork, and `assets/lotr/map/map.png` from the bundled
LOTR JAR. It uses nearest-neighbor categorical scaling and neutral coverage tint,
not actual ownership colors. It does not alter or reproduce full native textured
terrain, real dynamic markers, or live OpenGL rasterization. ID/probe annotations
are for review only. These images do not establish live visual acceptance.

- [T171/T132 land boundary](land-before-after.png) â€” first artifact to review.
- [Weathertop pilot](pilot-before-after.png).
- [Protected R1 corridor](river-before-after.png).
- [Corrected junction](junction-before-after.png).
- [T132 hover styling](selected-before-after.png).

## Existing manual rejection evidence

The prior completed post-submission check was reverified by parsing the original
pre-test NBT and post-test live NBT and checking the post snapshot hash. All serialized
KOME fields match. B1-B4 contents, four contributions, 12 Build-audit records and all
21 central-audit entries are identical; sequence remains 5; no named test Build
exists; population is zero. The client log independently records the 22:18:28 gap
rejection. No new live snapshot/submission/world operation was needed in this task.
Dirty=false at those reads is not continuous dirty-call instrumentation.
The user's single-location T171->T132 crossing and Cancel/Escape observations remain
recorded separately in the main document; neither proves all map geography.

## Manual acceptance after a separately authorized artifact refresh

1. Join as a normal player. Open the LOTR map with C conquest overlay enabled.
   Find T171/T132 near world X=18816, Z=2112: at several zoom levels, pan and hover
   across the thin border. Right-click must select the tile under the pointer;
   same-owner neighboring tiles must still have a visible shared border.
2. At verified safe terrain height, cross X=18816 at Z=2112. Compare the map boundary
   with the HUD: T171 west, T132 east, no artificial No tile strip. Recheck the pilot
   at X=21120, Z=-383.5 (T149 west / T132 east). Do not guess teleport Y.
3. Inspect R1 around world (34944,640), mask (1083,735): the gap remains unfilled,
   its banks remain separate at multiple zooms, and hovering the gap selects no tile.
   Also inspect the land junction around mask (950,713), world (17920,-2176).
4. Hover T132 and follow its gold boundary through a junction. Confirm pale fill,
   labels, route/Build/troop markers, and controls remain readable. Toggle C off/on.
5. Change GUI scale, drag-resize the window, and maximize/restore. Check border,
   background and mouse hit-testing together; report the separate first-maximize
   framebuffer issue independently if it prevents this check. Resource reload and
   reconnect must show fresh geometry/ownership, without stale old-world outlines.

No live border rendering, visual layout, frame rate, GL-driver behavior, or new
artifact dedicated-server startup is claimed. Existing routing override/active-leg
manual acceptance and legacy destination/geography decisions remain separate.

## Measured final candidate

Java 1.8.0_492, Windows 11, 12 reported logical processors. One final test extraction
(after snapshot PNG decoding and detached copy) took **117.0293 ms**. This is a
bounded diagnostic sample, not a gameplay/frame-rate benchmark. It excludes
texture uploads, existing visual-centroid work, native GL drawing and driver cost.

- 181,440 individual unit edges merged into **99,022 border runs**.
- **60,134** cached horizontal assigned spans for hover fill.
- Primitive arrays and line indexes: **2,591,316 bytes (2.47 MiB)**; object headers
  and temporary builder buffers are additional.

A 1200x700 GUI viewport centered at map (1000,730) emitted:

| Zoom | Border quads | T132 hover quads |
| --- | ---: | ---: |
| 0.25 | 68,726 | 124 |
| 0.5 | 39,931 | 124 |
| 1 | 19,085 | 124 |
| 2 | 7,674 | 124 |
| 4 | 1,912 | 124 |
| 8 | 239 | 86 |

The initial candidate used two Tessellator passes; the reviewed candidate splits
each into bounded 2,047-quad batches (see REVIEW.md).
There is no all-tile/per-cell traversal in these steady-state paths. Far-zoom draw
volume still needs live profiling on target hardware; subpixel corridors may not
have a visible screen sample even though no border quad encroaches on their area.

Initial production candidate (superseded by the review above): `build/libs/KOME-LOTR-Addon-1.0.8.jar`.
SHA-256: `d493c6970919105dbaed80f33910a3d5ab84061cacb412d71b316d80a22e8aea`.
Packaged classes use Java 8 bytecode. Packaged mask and every `kome_tile_*` gameplay
resource match source exactly. The obsolete border path is absent from the compiled
overlay. The existing runtime remains on its prior matching JAR; no refresh occurred.

### Changed files in this milestone

- `KOMEConquestMapOverlay.java`: retire static/full-cell outlines; use cached edges
  and hover spans, shared viewport, precise clipping and lifecycle cleanup.
- `KOMEClientProxy.java`: register the existing overlay for resource reload.
- New `KOMEMapBorders.java`: bounded immutable primitive runs, viewport culling,
  gap-preserving quads and snapshot-identity cache.
- New `KOMEMapViewport.java`: one GUI forward/inverse transform.
- New `KOMEMapBordersTest.java`: 17 focused extraction/render-input/lifecycle cases,
  full V2 edge equivalence and reproducible measured quad output.
- Main tile documentation and this report/preview package.

All earlier foundation/HUD/Build/gameplay defaults, geographic resources, and prior
audit/recovery files are preserved. Existing changes in the client proxy are carried
forward; its only new delta is reload-listener registration. No resize-feature source
was changed; the new guard merely skips drawing borders without a valid map rectangle.
