# Completed border milestone review - 2026-09-20

## Findings, highest severity first

- **P1 fixed: unbounded retained texture buffers.** The bundled 1.7.10
  `TextureManager.deleteTexture` deletes a GL name but leaves its object in
  `mapTextureObjects`. Repeated `getDynamicTextureLocation` registration used new
  keys after each snapshot/session/reload. Each abandoned ownership array was
  51,200,000 bytes; shade/label arrays accumulated on resource reload too. The
  overlay now uses five stable names, calls `DynamicTexture.deleteGlTexture`
  (which resets its GL ID), and replaces released entries with one pixel-free
  no-op texture. Neither old pixels nor reusable stale GL names remain registered.
- **P2 fixed: texture-pass GL state leakage.** `drawMapTexture` previously saved
  only enable flags while changing current color/binding and inheriting blending.
  It now saves/restores current, texture, color-buffer and enable state in a
  `finally` block and selects source-alpha blending explicitly. Border drawing
  already restored its changed server GL attributes; that discipline is tested.
- **P2 fixed: per-frame Tessellator buffer churn.** Forge's actual legacy
  Tessellator grows the raw array for a large submission, then shrinks arrays over
  0x20000 ints after drawing. A single 68,726-quad pass therefore repeatedly grew
  and discarded a multi-megabyte buffer. Batches now contain at most 2,047 quads,
  fitting below the initial 0x10000-int growth guard including its reserved quad.
  Vertex order, coordinates, colors and geometry are unchanged. The previous
  measured zoom-0.25 view becomes 34 border submissions plus one hover submission,
  rather than two oversized passes; target-hardware frame time remains unmeasured.

## Geometry, isolation and privacy

No new raster extraction or selection defect was found. Full V2 edge equivalence,
protected gaps, snapshot replacement and unavailable-state clearing still pass.
Production-generated preview quads are byte-for-byte equal to the pre-review
outputs, so existing offline previews remain applicable.

The **actual bundled LOTR JAR** differs from the adjacent decompiled source:
its private marker `transformMapCoords` uses integer `mapWidth/2`, whereas its
terrain UV projection uses floating `mapWidth/zoom/2`. Odd-sized viewports can
therefore place native markers half a GUI pixel from the terrain center. The
new test calls the bundled method and records that discrepancy explicitly;
KOME keeps the fractional terrain/hover transform rather than introducing marker
truncation into authoritative selection. Native outer-image clipping also rounds
its destination edge to an integer (up to half a GUI pixel). No LOTR class was
patched. Check outer edges and odd GUI sizes visually; this is not live GL proof.

All corrections are in the existing client overlay; common resolver/gameplay
classes and proxy isolation are unchanged. Borders contain public canonical IDs/
colors only. Ownership fill still consumes the existing conquest cache, visible
Build markers retain `active`/`markerVisible`/dimension filtering, and live-unit
packets retain owner/admin filtering. No new packet or private metadata path was
added. Dedicated-server resolver isolation and existing public-access tests passed.

## Validation and artifact

- Added `KOMEMapRenderLifecycleTest`: five tests exercising actual disconnect/
  reload callbacks with the real TextureManager over 50 cycles, pre-registration
  release, production GL-state bytecode guards, production batch vertex ordering/
  bounds, and the bundled LOTR transform. Only texture GPU allocation/deletion and
  batch GPU submission are test doubles; no live graphics context was claimed.
- Focused resolver/border/adapter/isolation tests: **27 passed**.
- `./gradlew.bat clean test build --no-daemon --console=plain`:
  **813 discovered, 811 passed, 2 skipped, 0 failed/errors**, 1m 11s.
  The existing custom-skin symlink tests skip without Windows symlink privileges.
- Initial test compilation required the bundled private-member access and legacy
  no-argument Tessellator constructor; both were corrected. An initial transform
  assertion exposed the native integer-marker/fractional-terrain distinction above.
  No production expectation or geographic assertion was weakened.
- Packaged mask/gameplay resources match source bytes; Java 8 bytecode verified.
  No full suite repeated after this successful reviewed build.

Production JAR: `build/libs/KOME-LOTR-Addon-1.0.8.jar`.
SHA-256: `cfd32d8df548cbecf5b081619a3350346445ca797cfb7f5878964b64e09edec1`.
This supersedes the initial border candidate hash in `final-validation.json`.
Full machine-readable results: `review-validation.json`.

## Preservation and next manual check

Pre-edit verified recovery:
`C:/Users/dayne/Documents/KOME-Recovery/border-review-20260920T035850Z-de7f6339`.
Only `KOMEConquestMapOverlay.java`, the new review test and review documentation
were changed. Existing source/resources, resize work, earlier recoveries, stash,
other worktrees and running installations were not changed. Changes remain
unstaged/uncommitted. No world operations, external review, deployment or restart.

**First manual check, after separately authorized installation:** open the conquest
map at T171/T132 near world X=18816, Z=2112; pan and hover across the shared line at
several zoom levels. The pointer, thin border and selected ID must agree. Then
check R1, odd GUI scaling/resizing, and F3+T/reconnect recovery using the existing
manual checklist. Live rendering, resource reload, driver state and performance
remain pending; automated tests do not establish their acceptance.
