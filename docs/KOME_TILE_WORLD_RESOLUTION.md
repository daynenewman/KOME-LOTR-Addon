# Tile world-resolution foundation

## Authority and scope

The approved policy for this checkpoint is exact raster-cell sampling. The packaged
`assets/kome/map/reset_conquest_tile_ids.png` and its color-to-ID mapping are the
geometry source. No screenshot, polygon, biome, ownership color, or nearest-tile
heuristic supplies geometry.

**Transparent or unmapped pixels remain unowned gaps until an authoritative tile
resource intentionally assigns them. Runtime code must not infer ownership from
neighboring pixels.** Unknown opaque colors are malformed input and prevent
snapshot publication; the existing retired-ID rule explicitly classifies retired
colors as gaps. A gap means no tile, not an uncapturable tile.

## Architecture audit at cd9af14

| Owner / caller | Classification and responsibility |
| --- | --- |
| `KOMEConquestTile` | Stable normalized ID, current/default ruling faction, compatibility owner field, claim/transfer records, anchor. No area geometry. `projectRulingFaction()` is the read-only owner projection; `currentRulingFaction()` can repair legacy fields. |
| `KOMEConquestTileDefaults` | Existing packaged color/ID authority, retired IDs, map-derived centers, adjacency and automatic route/river/bridge markers. `getKnownTileIds()` supplies existing IDs; no independent ID registry is introduced. |
| `KOMETileOwnershipDefaults`, `KOMEWaypointDefaults` | CSV native ownership, level and region defaults. Not geometry or capturability. Ownership defaults validate against the existing ID authority. |
| `KOMEWorldData` | Server `WorldSavedData`, `ConquestTiles` NBT records, Builds, routes, movement records and waypoint links. `getConquestTile()` can create records/arrival points; resolver must never call it. |
| `KOMEClientData`, `KOMEPacketConquestData` | Client presentation copies of server records. Client data inherits WorldData; it is not authoritative ownership. |
| `KOMEBuildService.create`, `KOMEPacketBuildAction` | Gameplay registration. Before this checkpoint, only the packet checked coordinates; the service could register without the check. Existing Build load/projection/lifecycle does not perform spatial validation. |
| `KOMEConquestMapOverlay` | Presentation: hover/selection, loaded-unit tooltip, textures, highlights, center labels and screen projection. Hover used floor plus integer truncation; unit lookup used floor; both sampled a separately decoded client mask. |
| `KOMEConquestTileDefaults.getTileIdAtMapPosition` | Common compatibility lookup formerly rounded, clamped and searched eight pixels. Used by Build validation and automatic waypoint matching. |
| `KOMEWorldData.ensureAutomaticTileWaypointLinks`, `KOMECommandConquest.automaticWaypointForTile` | Initialization / administrative waypoint matching via the compatibility lookup. Their existing invocation, distance ranking, persistence and manual-override rules are not redesigned. The shared sampling policy intentionally changes which coordinates qualify. |
| `KOMECommandTroops`, `KOMEArmyMovementOrder`, `KOMEArmyCompany`, `KOMEHiredUnitRecord`, movement access/recovery services | Gameplay uses stored tile IDs, route IDs and adjacency, not an entity-position polygon lookup. Center distance is a route heuristic. No movement-rule integration is added. |
| `KOMEConquestRouteEdge`, `KOMETileWaypoint`, `KOMETileWaypointLink` | Persisted route metadata and point locations; no area geometry. Existing waypoint-link creation hard-codes dimension 100; that separate legacy limitation is not changed here. |
| `KOMECommandConquest`, conquest action/open-capture packets, war/ruler services | Capture/transfer authorization uses tile IDs, faction/claim/ruler state. No independent area capturability field exists. UI `canClaim` is viewer eligibility, not terrain metadata. |
| `KOMEConfigRegistry`, `LOTRDimension` | Existing KOME configuration and LOTR configurable dimension ownership. No new configuration registry. |
| `LOTRGenLayerWorld` | Common-side map image dimensions, origin and scale. Map Y corresponds to world Z. Native LOTR biome/conquest/region objects are not KOME tiles. |
| Existing tests / archived docs | Alliance tests cover default IDs; Build, schema, packet and population tests cover persisted records. Old Build tests use synthetic coordinates. Archived population/tile and movement documents describe older systems, not alternate geometry authority. No polygon implementation or exact raster resolver existed. |

Bulk mask reads for center/adjacency construction and existing texture generation
are not coordinate-to-tile gameplay lookups. Their algorithms remain distinct
from exact single-cell resolution. Other world-to-screen conversions position
markers only. The unused defaults import in the alliance command is not a caller.

## Coordinate derivation

The existing common and client conversions use
`mapX = worldX / scale + originX` and the same relationship for Z. Therefore:

```
maskX = floor((worldX + originX * scale) * maskWidth / (scale * mapWidth))
maskY = floor((worldZ + originZ * scale) * maskHeight / (scale * mapHeight))
```

The checked LOTR v36.15 constants are origin (810,730), scale 128. Both current
images are 3200 by 4000, so current cells span 128 blocks. Runtime values, not
independently copied constants, supply the transform. The waypoint helper's
half-cell offset is a waypoint position convention, not tile boundary authority.

The numerator uses `Math.multiplyExact` and `Math.addExact` on `long`; the
positive denominator uses checked multiplication. `Math.floorDiv` provides
mathematical floor for negative coordinates. Construction evaluates both ends
of the entire signed 32-bit world-coordinate range to prove every runtime
calculation safe; a transform that can overflow is rejected. No binary
floating-point arithmetic decides an authoritative mask boundary.

The common compatibility adapters accept legacy entity/packet doubles and UI map
positions. `new BigDecimal(double)` preserves the exact supplied value; exact
decimal arithmetic and FLOOR select an integer world block before the same
integer resolver runs. NaN, infinity and values whose floored block is outside
the signed 32-bit range return INVALID_COORDINATE. Stored Build positions keep
their original fractional values. For the current integral 128-block cells this
also preserves exact map-cell sampling. A future mask with sub-block boundaries
would require revisiting presentation-to-block semantics before activation.

LOTR map dimensions come from the header of the same packaged
`assets/lotr/map/map.png` used by `LOTRGenLayerWorld`; a disagreement with already
loaded positive LOTR dimensions rejects loading. This avoids instantiating a
biome generator to obtain dimensions. Origins, scale and dimension ID come from
the existing LOTR fields. The resolver does not use the waypoint helper's
separate half-cell position convention.

## Result and boundary policy

| State | Meaning |
| --- | --- |
| RESOLVED | Exact cell identifies an active stable tile ID. Contains original integer world X/Z, dimension and sampled mask X/Y. |
| IN_BOUNDS_GAP | Cell has alpha <= 24 (the existing transparency threshold), or a color explicitly excluded by the existing retired-ID authority. No tile is returned. |
| OUTSIDE_MASK | Floored cell is outside the half-open raster extent. No clamping. |
| UNSUPPORTED_DIMENSION | A valid snapshot exists but the explicit dimension differs from configured Middle-earth. |
| INVALID_SNAPSHOT | No validated snapshot is available, including explicit invalidation or failed initial load. |
| INVALID_COORDINATE | A legacy/UI input is nonfinite or cannot designate a supported integer block. |

`resolvedTileId()` is present only for RESOLVED. `capturable()` is empty because
no authoritative area-capturability metadata exists. Current ownership remains
in WorldData and is never copied into results or geometry. Ownership changes
therefore cannot invalidate the raster. Viewer `canClaim`, traversal, rivers,
bridges, mountains, passes, deserts and waypoints remain separate concepts.

There is no polygon boundary/overlap policy: each accepted integer block samples
one floor-selected raster cell. A coordinate on a cell's left/top edge belongs
to that cell; the right/bottom edge belongs to the next cell. The raster cannot
encode overlapping identities in one pixel. Duplicate color or tile mappings
fail validation rather than choosing an entry by iteration order.

The real mask regression is `(2291,58) = gap`, `(2292,58) = T001`. With the checked
LOTR transform these cells start at world `(189568,-86016)` and
`(189696,-86016)` respectively. These are resource-derived control points, not
coordinates inferred from a screenshot.

## Snapshot validation and publication

`KOMETileRasterSnapshot` reads a PNG header before decoding. Positive width and
height must be at most 8192 each, with at most 16,777,216 cells (64 MiB of retained
cell data at the limit; the shipped mask retains about 48.8 MiB). Decoder memory
is transient and separate from this retained allocation. The mapping is limited
to 1 MiB and 65,535 unique colors. Missing resources, invalid images/dimensions,
empty mapping, malformed RGB/ID rows, duplicate colors/IDs and unknown active IDs
fail visibly. Unknown opaque colors fail even if they do not occur near a lookup.
Transparent pixels ignore their RGB and remain gaps.

The existing defaults class remains the packaged identity authority and now uses
the shared strict parser. Its ID lookup no longer requires center, adjacency or
biome initialization. A candidate snapshot validates active IDs against that
authority and recognizes its existing retired-ID exclusions. The validation
sets are not retained. The snapshot's private palette contains ID references
only, not ownership or independent tile records.

Cells store a compact palette index and original active alpha. All arrays are
privately owned; palette projections are immutable; rendering receives a detached
ARGB copy. Runtime resolution performs a fixed amount of integer arithmetic,
one array access and one palette access: O(1), without scans, neighbor searches,
per-coordinate caching, image decoding, WorldData access or dirty marking.
Full raster scans occur only at construction and in pre-existing bulk rendering
and center/adjacency generation, never in coordinate resolution.

`KOMETileWorldResolver.INSTANCE` is the sole production service. `KOMEAddon.postInit`
explicitly calls `reloadBundled()` on both physical sides. Lookups do not perform
lazy loading. A volatile immutable State publishes a fully built snapshot and
diagnostic together; writers serialize loading/publication, while readers capture
one State and take no locks. Failed replacement retains the last valid snapshot
and exposes the rejection diagnostic. Failed initial loading stays INVALID_SNAPSHOT
and logs the diagnostic. `invalidate()` explicitly disables lookup until a valid
reload. Future authoritative resource/configuration editing must use this boundary;
no editing, polling or save migration is implemented here. Ownership/configuration
unrelated to geometry does not trigger reconstruction.

Clients use the same packaged geometry as servers, not resource-pack geometry.
Matching addon/LOTR assets and dimension configuration are required, as with the
existing protocol; this checkpoint adds no asset synchronization or handshake.
The overlay notices a replaced snapshot and rebuilds its existing texture copies.

## Reconciled callers and read-only inspection

| Changed caller | Why it belongs in this foundation |
| --- | --- |
| Defaults map-position helper | Removes round/clamp/eight-pixel search; accepts explicit dimension and delegates to the common resolver. No fallback remains. |
| Conquest overlay hover | Uses the common map adapter for selected identity. Gaps/outside return no selection. Screen-to-map projection is presentation only. |
| Conquest overlay loaded-unit tooltip | Uses the marker's explicit dimension and common world-position adapter. Stored company movement tile is unchanged. |
| Conquest overlay resource setup | Uses validated common raster/palette copies so a client resource override cannot become a competing identity source. Existing texture/highlight algorithms remain. |
| Build service `create` | Checks dimension and coordinates before Build construction, IDs, contributions, audit or dirty marking. All non-RESOLVED states and mismatched expected tile IDs reject explicitly. |
| Build action packet | Removes its redundant coordinate helper; keeps its player-dimension check and invokes the validated service. Wire fields, order, discriminator and protocol are unchanged. |
| Existing waypoint-match call sites | Supply the explicit Middle-earth map dimension to the delegated helper. No new invocation, waypoint creation/approval feature or persistence repair is introduced. |
| Existing `/conquest resolve <dimension> <worldX> <worldZ>` subcommand | Staff inspection prints the typed result before any WorldData access. No new public command or mutation packet. |

No persisted Build is deleted, relocated, reconciled, or spatially revalidated
on load. Existing permissions, contributions, population rates and Build lifecycle
continue unchanged after successful registration. Rejected registration cannot
create a Build, consume an ID, append audit data, alter aggregates or dirty WorldData.
Existing initialization/admin waypoint matching intentionally inherits the approved
sampling rule; a waypoint formerly assigned through a gap can now be unmatched
when that existing operation is invoked. Existing saved links are not rewritten
by a resolver call.

## Validation and remaining manual checks

Focused tests exercise the real raster regression, all current integer mask
boundaries on both axes, an independent pixel oracle, rational conversion against
BigInteger, signed integer extremes, overflow rejection, malformed resources,
maximum allocation, defensive copies, invalid replacement, atomic publication,
concurrent readers, client/server equivalence, configurable dimension IDs and
atomic Build rejection. Existing Build suites use scoped real-mask coordinates
instead of synthetic Overworld coordinates. A separate classloader denies client,
LWJGL and WorldData classes while loading the common service and resolving T001.

Automated classloader tests are not a live Forge dedicated-server or multiplayer
run. Before live use, on an isolated test world with matching artifacts:

1. Check startup logs and `/conquest resolve <configured Middle-earth ID> 189568 -86016`
   reports IN_BOUNDS_GAP; `189696 -86016` reports T001.
2. Check hover and loaded-unit tooltips at these cell edges under different zooms
   and GUI scales. Check negative outer bounds show no selection.
3. Attempt Build registration in a gap, outside the map and outside Middle-earth;
   confirm explicit rejection and unchanged records. Register at a valid controlled
   tile and verify its original precise position persists.
4. Cold-reload existing Builds/links and verify no new spatial reconciliation.
5. Validate dedicated-server startup and client connection using the configurable
   dimension ID. Exercise an explicit snapshot replacement and texture refresh in
   a development harness before any future editor enables runtime replacement.

Deferred: visible borders/toggles, tile editing/splitting/combining, public waypoint
creation/approval, movement integration/rules, capture redesign, uncapturable-area
metadata, biome registration, protocol/schema changes and KOM-71. No new behavior
from those features is activated by this checkpoint.

## Historical validation on the original tile baseline

These results belong to the original uncommitted implementation on `cd9af14`,
not to the reconciled dev candidate. Current results are recorded below.

- Focused resolver/tile, client adapter, Build and base-isolation run: 54 tests,
  zero failures/errors/skips.
- Full `gradlew.bat test --no-daemon --console=plain`: 677 tests, zero failures/errors,
  two existing platform-dependent symbolic-link tests skipped.
- `gradlew.bat clean test build --no-daemon --console=plain`: successful, including
  the same 677-test result and reobfuscated addon packaging.
- Tracked/staged diff whitespace checks and the new-file whitespace check passed.
  No source/protocol/configuration or build-dependency declaration changes were
  made outside the documented caller reconciliation. Existing ignored stock LOTR
  and GeckoLib dependencies were supplied locally for the isolated build.

## Change inventory

Paths below are relative to this worktree. Nothing was staged or committed.

| Area | Files |
| --- | --- |
| New common model/service (`src/main/java/kome/common/data/`) | `KOMETileRasterSnapshot.java`, `KOMETileResolution.java`, `KOMETileWorldResolver.java` |
| Existing common data | `KOMEConquestTileDefaults.java`, `KOMEBuildService.java`, `KOMEWorldData.java` (explicit dimension at the existing matching call only) |
| Existing runtime entry points (`src/main/java/kome/`) | `common/KOMEAddon.java`, `common/command/KOMECommandConquest.java`, `common/network/KOMEPacketBuildAction.java`, `client/KOMEConquestMapOverlay.java` |
| New common tests (`src/test/java/kome/common/data/`) | `KOMETileRasterSnapshotTest.java`, `KOMETileWorldResolverTest.java`, `KOMETileBuildValidationTest.java`, `KOMETileResolverIsolationTest.java`, `KOMETileTestResources.java` (scoped fixture) |
| New client test | `src/test/java/kome/client/KOMETileClientAdapterTest.java` |
| Existing tests with real-coordinate fixtures | `common/data/KOMEPreciseBuildTest.java`, `common/data/KOMERedesignSystemsTest.java`, `common/data/KOMEForeignConstructionServiceTest.java`, `common/data/KOMEPopulationProjectionTest.java`, `common/network/KOMECanonicalBuildPacketTest.java` under `src/test/java/kome/` |
| Reconciliation tests from current dev | `src/test/java/kome/common/command/KOMEPublicCommandTest.java`, `src/test/java/kome/common/network/KOMEPublicAccessPacketTest.java` |
| Documentation | `docs/KOME_TILE_WORLD_RESOLUTION.md` |

## Reconciliation onto current dev (2026-09-18)

Destination: `KOME-LOTR-Addon-Tile-Resolution-Dev`, branch
`dayne/tile-world-resolution-dev`, based exactly on
`d6bb856f2a06d39f15458b042bb48071286655de`. A fresh origin fetch still matched
that SHA. No branches were merged and no commit was made.

The original `KOME-LOTR-Addon-Tile-Resolution` remains on
`cd9af14a9533bebb0bda9e8d32f914cac81dad9f` with its 22-path delta and empty
index. Its file hashes were checked against the recovery package
`C:/Users/dayne/Documents/KOME-Recovery/tile-world-resolution-20260918T224207Z-1bac8a21`.
The recovery package is read-only input to this reconciliation.

All 22 original paths are included. Seventeen are byte-identical to the backed-up
files; three overlapping production files were integrated as individual changes
into dev; this document and `KOMETileResolverIsolationTest` were extended. Two
additional current-dev test files were adapted, giving 24 changed paths overall
(14 modified, 10 untracked). No original tile change was omitted.

| Overlap | Final decision |
| --- | --- |
| `KOMECommandConquest` | Retain `KOMEPublicCommand`, dev's early permission guards, public-tile filtering and pure ownership projection. Add the staff-only resolver before WorldData access; include it in administrative-action classification and only staff help/completion. Preserve explicit dimension at existing automatic waypoint matching. |
| `KOMEPacketBuildAction` | Retain dev's `getPublicConquestTile` guard, player-dimension check and early return after rejection. Remove only the obsolete packet coordinate validator; `KOMEBuildService.create` validates coordinates before any registration writes. Existing Build actions are not spatially revalidated. |
| `KOMEWorldData` | Add only the dimension argument at the existing waypoint matching call. Preserve `getPublicConquestTile`, `progressionForInspection`, pure ownership reads, schemas and persistence. |
| `KOMEPublicAccessPacketTest` | Use the scoped real T100 geometry and configured dimension for successful creation fixtures. The operator foreign-construction test passes geometric/player-dimension preconditions and asserts the actual authorization denial. Keep all existing permission assertions. |
| Public tile identity | `getKnownTileIds()` remains owned by defaults; public selection requires a known, non-retired, existing WorldData record. Geometry does not create records or provide ownership. Malformed identity input throws explicitly; it cannot publish a partial or empty successful authority. |

Six new reconciliation tests cover: valid queued non-operator registration;
spatial packet rejection with unchanged serialized WorldData, counters, audit,
dirty flag and no refresh packets; unknown/retired/absent public-tile rejection;
public inspection without record creation or ownership repair; staff coordinate
inspection with a sender that throws on any world access; and malformed identity
loading in a fresh classloader without partial publication. Existing tests now
also check hidden/denied non-staff resolver access and manager approval of an
existing Build whose stored position is outside supported geometry.

Population, Build lifecycle, configuration, Aqua/racial arms, public privacy,
root/nested schemas, packet registration and protocol remain dev's implementations.
The only production adaptation beyond the original tile delta is fitting resolver
inspection into dev's administrative/public command separation. The resolver,
raster, result, Build service and client adapter retain their backed-up behavior.
No HUD, visible border, editor, waypoint feature, movement/capture rule, new packet,
save migration or KOM-71 implementation was added.

### Combined automated validation

Actual XML results from this worktree, not historical totals:

| Run | Discovered | Passed | Skipped | Failures / errors |
| --- | ---: | ---: | ---: | ---: |
| Focused resolver, raster, client, isolation, Build, public-access, schema and base-isolation tests | 171 | 171 | 0 | 0 / 0 |
| `gradlew.bat clean test build --no-daemon --console=plain` | 741 | 739 | 2 | 0 / 0 |

Both commands succeeded. The full run also built the reobfuscated production JAR.
The resolver's compiled class version is 52 (Java 8); no build/dependency settings
were changed. The existing local LOTR/GeckoLib JARs were copied into ignored
`libs/` in this worktree only. Gradle ran under the installed JDK 21 launcher
and the existing project's Java 8 target/toolchain configuration.

The two conditional skips are:

- `CustomSkinLibraryFoundationTest.symbolicLinkSkinIsRejectedWhenSupported`
- `ClientCustomSkinCacheTest.symbolicLinkAtHashPathIsNeverAcceptedWhenSupported`

Focused command, run from the destination worktree:

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMETile*' --tests 'kome.client.KOMETileClientAdapterTest' --tests 'kome.common.data.KOMEPreciseBuildTest' --tests 'kome.common.data.KOMEForeignConstructionServiceTest' --tests 'kome.common.data.KOMERedesignSystemsTest' --tests 'kome.common.network.KOMECanonicalBuildPacketTest' --tests 'kome.common.data.KOMEPopulationProjectionTest' --tests 'kome.common.command.KOMEPublic*' --tests 'kome.common.network.KOMEPublicAccessPacketTest' --tests 'kome.common.data.KOMEBaseIsolationTest' --tests 'kome.common.data.KOMEWorldDataSchemaTest' --no-daemon --console=plain
.\gradlew.bat clean test build --no-daemon --console=plain
```

### Pending interactive validation procedure

No live server/client validation or deployment was performed for this candidate.
Use an isolated test world and matching candidate client/server artifacts after
separately authorized installation. Keep existing saves available for comparison.
These commands use LOTR's default Middle-earth ID `100`; substitute the configured
ID on both sides if customized. The runtime never hard-codes that ID. Server
console commands omit the leading `/`; in-game staff commands include it.

1. First run `/conquest resolve 100 189568 -86016` as staff. Require
   `IN_BOUNDS_GAP`, mask `2291,58`, with no tile. Check startup logs for any
   `[KOME] Tile raster load rejected` diagnostic; do not continue on failure.
2. Run the remaining controls below. They derive from the packaged 3200x4000
   mask, origin `(810,730)` and scale `128`, checked by the automated tests.
   The T100 point is the real fixture's first active pixel, also inspected in
   the packaged resource during reconciliation.

   | Command | Required result |
   | --- | --- |
   | `/conquest resolve 100 189695 -86016` | IN_BOUNDS_GAP; last block before the T001 cell |
   | `/conquest resolve 100 189696 -86016` | RESOLVED T001; mask 2292,58 |
   | `/conquest resolve 100 189697 -86016` | RESOLVED T001; immediately after the boundary |
   | `/conquest resolve 100 -103681 -86016` | OUTSIDE_MASK; mask X=-1 |
   | `/conquest resolve 100 77824 -12928` | RESOLVED T100; mask 1418,629 |
   | `/conquest resolve 0 189696 -86016` | UNSUPPORTED_DIMENSION on the default configuration |

3. Join as a non-operator. `/kome` and `/kome tile T100` must open the existing
   public screens. `/conquest get T100` must remain available. The same
   `/conquest resolve` command must be denied; non-staff help and tab completion
   must not advertise it. Compare staff access without weakening other guards.
4. In Middle-earth, navigate to the control locations. Open the preserved map
   through `/kome` -> Tiles. At different zooms and GUI scales, confirm gap and
   outside locations have no tile hover/selection, while the adjacent T001 cell
   selects T001. Check loaded-unit position tooltips against the resolver; do
   not change stored movement IDs to force agreement.
5. For Build checks use an account already authorized by T100's current
   controller (inspect `/conquest get T100` and `/build grants T100`). At
   X=77824.5, Z=-12927.25 in Middle-earth, open `/kome tile T100` -> Builds ->
   Create Build. Confirm the displayed player coordinates and dimension, enter
   a test name, Normal type and 1.25 hours, choose an eligible population owner,
   then Confirm Build. `/build list T100` and `/build inspect <returned-ID>` must
   show one new Build with the original precise location and 1.25 approved hours
   for its original manager. Use a safe Y appropriate to the test world; Y does
   not select a raster cell. Normal permission rules still apply.
6. Reopen `/kome tile T100` from gap X=189568,Z=-86016, outside
   X=-103681,Z=-86016, and an unsupported dimension, then attempt registration.
   Require the corresponding explicit rejection and no new Build, ID/audit/
   aggregate/dirty mutation or success-style packet refresh. Also try an
   otherwise-valid location with a faction lacking construction permission;
   require the permission denial. UI/list inspection checks visible records;
   internal counters, dirty flags and packet absence are additionally covered by
   the behavioral tests and require an instrumented server for live observation.
7. Inspect an existing saved Build, save/restart the isolated server and inspect
   it again; positions, IDs and lifecycle must remain unchanged. Verify ordinary
   manager review still works without validating the old stored location. Finish
   matching-client reconnect, public/private command, population/payout and
   Aqua swimming/crawling/racial-arm smoke checks. Snapshot replacement/texture
   refresh needs a development harness; no reload/editing command was added.

If the known Create Build GUI issue prevents steps 5-6, record the exact screen,
input and failure and leave those checks blocked. It is a separate issue; do not
change the GUI, ownership, capture rules or permissions to bypass it. Automated
service/packet success is not evidence that the interactive flow has passed.

## Separate follow-up: Create Build owner choices

The isolated T132/Weathertop check exposed a pre-existing GUI-packet omission,
confirmed in both the original cd9af14 baseline and dev d6bb856. The server
packet builder left selectablePopulationOwners empty, so the client correctly
disabled Create Build even for a pledged same-faction viewer. The isolated
player had 96/96 progression; progression is not this button's enablement gate.

This follow-up is separate from the raster foundation. populateBuildViews now
clears the owner list and fills it through
KOMEBuildService.selectablePopulationOwners(data, viewerFaction, tile.id).
The existing public-tile guard runs before construction. Eligible owners remain
server-authoritative; repeated population replaces stale choices. Existing
creation authorization, spatial validation, rejection returns, progression
behavior, wire format and schema are unchanged. No client permission bypass or
GUI redesign is included.

Four added regressions in KOMEPublicAccessPacketTest exercise the actual
production builder: same-faction Dunedain choices and their wire round-trip;
unpledged public inspection with no creation choices; unauthorized foreign
inspection with no choices even for an operator; and repeated population with
no duplicates or stale choices. Existing unknown/retired tile, public-inspection,
creation-rejection and lifecycle tests remain in the focused validation set.

Manual verification after installing matching rebuilt client/server artifacts:
keep the player non-operator, pledged to Dunedain. In Middle-earth dimension 100,
X=24128, Z=-832 resolves to T132 (mask 998,723). Run /kome tile T132 and open
Builds. Require Create Build to be enabled, then verify Dunedain is selectable
in the creation form. Complete a test Build and inspect it with /build list T132
and /build inspect <returned-ID>. An enabled button alone does not prove creation
succeeded. Repeat unauthorized and invalid-coordinate rejection checks from the
procedure above. Actual in-game creation remains pending user validation.

Follow-up automated validation: focused packet/Build/public-access/protocol
selection discovered and passed 90 tests, with no failures or skips. The full
`gradlew.bat clean test build --no-daemon --console=plain` succeeded with 745
discovered, 743 passed, zero failures/errors, and the same two existing
conditional symlink skips listed above. Production JAR SHA-256:
`86AE2BD96CE01ACCB23962D83AF5D976C9C34E8DACC5F4B83838C2BA9CC49985`.

## Separate follow-up: opening-click submission

The live owner-list follow-up exposed another pre-existing UI defect. In
Minecraft/Forge 1.7.10, GuiScreen.mouseClicked iterates the live buttonList by
index. Create Build called initGui inside that loop, replacing the list with
form controls. The later Confirm Build button occupied the same footer position,
so the opening mouse press also dispatched confirmation. initGui itself did not
send a packet. The confirm handler normalized zero default hours and sent a
create packet with an empty name, which the existing server sanitized to New
Build. Normal server authorization and spatial validation then accepted it.

The smallest local fix defers this screen's requested initGui rebuild until the
current superclass mouse dispatch finishes. Vanilla/Forge dispatch, sound,
pre/post events and release handling remain intact. Opening initializes the
form without submission; a separate confirmation press sends the create packet.
No server validation, progression, authorization, schema or protocol was changed.

KOMEGuiBuildInteractionTest executes the real screen layout, action handler,
GuiScreen mouse dispatch and packet sender. Only native rendering/audio and the
network transport are stubbed. Its five cases cover opening/initialization and
release, deliberate confirmation with edited values, Build List cancellation,
Escape cancellation, and scaled coordinates. All five reproduced unwanted
submission before the production fix. Tests reuse RFG's existing lwjgl2Classpath
at test runtime; no new library version or production dependency was added.

Read-only console inspection found B1, B2 and B3 in T132, all active New Build
records owned by Dunedain, built/managed by _Danye_, Normal with 0.00 hours.
Their registration timestamps are 1789782563757, 1789782566705 and 1789782568155.
They match the reported accidental submissions and are preserved, not deleted.

Manual retest: reopen /kome tile T132, then Builds -> Create Build. Require the
form to stay open without a fourth record. Edit the name and hours; Build List
or Escape must leave the record count unchanged. Reopen, enter a distinct name
and 1.25 hours, then deliberately click Confirm Build once. Require exactly one
new record with those values. This automated reproduction/fix is not a claim
that the updated interaction has been observed in-game.

Interaction-fix validation: focused GUI/packet/Build/public-access selection
passed 97/97. Full clean test/build succeeded: 750 discovered, 748 passed,
zero failures/errors, and the same two conditional symlink skips. Tracked and
untracked candidate whitespace checks passed. Matching isolated production JAR
SHA-256: `7D8615584C4F86A29BFA99AAC721598178E82CA963272EE785B4F177C20ACB98`.

The same isolated client and server were stopped gracefully before replacement.
The client required invoking its verified normal Minecraft shutdown method via
a local JDK attach helper after a window-close request did not end its game loop;
the normal Stopping log and process exit were verified. This helper lives only
in the validation directory and is not included in the addon. World, config,
progression and empty operator list were preserved through replacement, with
SHA-256 verification of the stopped backup at `C:\Users\dayne\Documents\KOME-Validation\tile-resolution-20260918-200409-d095d7\backups\before-click-fix-20260918-210403`. B1-B3 were retained.
In-game form validation remains pending the user's next test.

Post-refresh readiness: dedicated server reached Done on 127.0.0.1:57858,
online-mode=true, with the empty operator list. Read-only console checks still
report exactly B1-B3 in T132, the canonical gap at (2291,58), and T001 at
(2292,58). Matching client launch was initiated but paused at Prism's
"Low free memory" dialog; no unrelated processes or preserved memory settings
were changed. User interaction is required before the live form retest.

## Foundation audit and hardening (2026-09-18 local / 2026-09-19 UTC)

This follow-up starts from 28 changed paths (17 modified, 11 untracked), not the
older 24-path reconciliation inventory. Branch and base remain
`dayne/tile-world-resolution-dev` / `d6bb856f2a06d39f15458b042bb48071286655de`.
A byte-verified 28-file recovery, binary tracked/index patches, status inventory,
SHA-256 manifest and verified full-history Git bundle were created before edits:
`C:/Users/dayne/Documents/KOME-Recovery/tile-foundation-audit-20260919T034256Z-642402be`.
The owner-list and opening-click fixes are preserved. The user subsequently
reported successful non-operator Build creation, public tile access, map pan/zoom/
hover and denial of the staff resolver command. These are user-reported checks,
not new observations of a client session by this audit. The separate window-resize
investigation and its files are untouched.

### Confirmed correction and expanded evidence

A failed/uninitialized map adapter incorrectly claimed world `(0,0)` although it
had no transform to derive a world position. A regression reproduced this before
the correction. `resolveMapPosition` now returns INVALID_SNAPSHOT with neither a
world nor mask coordinate. Integer callers retain their actually supplied world
coordinates on failure. No accepted cell identity or gameplay rule changed.

Nine added tests cover a complete production-cell audit, fractional real-resource
boundaries and all four outer edges, negative subnormal/signed-zero and integer
limits, assigned/assigned and assigned/gap transitions, absent-transform diagnostics,
LOTR-header disagreement with last-valid-snapshot preservation, actual player and
LOTR unit position fields, the actual private map-hover path shared with selection,
and unpledged/foreign/nonnumeric Build rejection through the registered server
queue. Hover tests use two viewports, five zooms and three pan offsets; native GL
texture creation is bypassed with the already-validated palette. They do not prove
visual texture rendering or native mouse behavior.

Existing spatial packet-rejection tests now use the production server queue and
check both before dispatch and after rejection. The persisted-Build test also
loads the entire WorldData with geometry present and unavailable, asserting exact
Build-record equality for an out-of-coverage, unsupported-dimension saved record.
Its fixture includes the manager's persisted faction: dev already reconciles
managers and normalizes defaults at world load. That existing lifecycle behavior
is preserved, not incorrectly attributed to spatial validation. Resolver calls
still cannot perform either reconciliation or dirty marking.

### Reproducible production-resource audit

Run from this worktree:

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMETileResourceAuditTest' --no-daemon --console=plain
```

Outputs: `build/reports/tile-resource-audit/audit.md`, `tiles.csv`, and
`components.csv`. The test reads the real packaged PNG/mapping/ownership metadata,
checks every cell against the resolver at an independently calculated lower world
corner, and writes deterministic coverage/component counts and representative
coordinates. CSV component bounds use inclusive mask coordinates. Four-neighbor
connectivity here is an audit convention, not movement adjacency or a validity rule.

| Measurement | Result |
| --- | ---: |
| Mask and LOTR map dimensions | 3200 x 4000 |
| Cells checked | 12,800,000 |
| Mapped IDs / active IDs | 645 / 621 |
| Existing retired exclusions | 25 (T045 has no mapping row) |
| Active assigned cells | 3,973,869 |
| Transparent cells (alpha <= 24) | 8,826,087 |
| Retired-color cells treated as gaps | 44 |
| Unknown opaque colors / ambiguous mapping entries | 0 / 0 |
| Active IDs without raster coverage | 0 |
| Ownership metadata rows / duplicate or unknown IDs | 645 / 0 |
| Active IDs missing ownership metadata | 0 |
| Nontransparent partial-alpha cells | 0 |
| Active IDs with multiple four-connected components | 1: T423 |
| Active tiles consisting of one isolated pixel | 46 |
| Gap components / enclosed gap components | 791 / 790 |
| Differing cardinal cell pairs / pairs involving a gap | 253,787 / 253,694 |

Mask SHA-256: `3ee80b95947f99ca4bdc355c0acf3dc832d41e964b5fe54e880f35665068e6d1`.
Mapping SHA-256: `d8674747229c7a9cbfdebcd217f1423d722ff4cdacb882d55607491cea36d9e5`.
LOTR map SHA-256: `1c79d0610dfbcfa970aa0dd929ce80cb19b1feb89fe57777b293b166282230f3`.
All four outer mask edges are gaps. Active coverage's overall mask bounding box
is X=81..3099, Y=58..3829; the rectangle itself does not imply tile coverage.

**Geographic observations requiring an author decision, not automatic errors:**

- T423 has a 5,431-cell component and a separate four-connected one-cell component
  at mask (1489,1379), world lower corner (86912,83072).
- T002 is a one-cell tile at mask (1297,61), world (62336,-85632); T003 is another
  at (1292,64), world (61696,-85248). One pixel covers 128 x 128 world blocks.
- An enclosed one-cell gap is at mask (673,790), world (-17536,7680). An enclosed
  two-cell seam starts at (1510,1543), world (89600,104064), directly below an
  assigned/assigned boundary. The largest enclosed gap has 26,539 pixels and
  starts at (2806,1308), world (255488,73984).
- T001 has seven connected cells. Its immediate predecessor (2291,58) remains a
  gap. Large transparent areas, narrow seams and these islands may be intentional.
  No gap, island, color, ownership default or retirement rule was changed.

### Position callers, bounds and performance contract

For an existing server-side player or unit, no new adapter or location cache is
needed. Once its current world exists, the common-side call is:

```java
KOMETileResolution location = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(
    entity.worldObj.provider.dimensionId, entity.posX, entity.posZ);
```

Call again with its current fields after movement, teleportation or dimension
transfer. Tests use actual EntityPlayerMP and LOTREntityGondorSoldier fields; they
exercise lookup with changed positions, not the engine's teleport procedure.
Only RESOLVED exposes a tile ID. This is geographic identity, not ownership,
capturability, traversal eligibility, stored company station, or a Build's tile.
No movement, combat, capture or per-tick location infrastructure was added.

For the shipped transform, the mask's half-open world extent is
X=[-103680,305920), Z=[-93440,418560). Cells increase toward positive world X/Z;
Y/altitude does not participate. Entity inputs are floored to signed 32-bit block
coordinates, including tiny negative doubles; their original precise values are
not rewritten. NaN/infinity or a floored block outside that range is explicitly
INVALID_COORDINATE. Snapshot/dimension failures never select a default tile.
LOTR loads its configurable dimension in pre-initialization; KOME captures it at
post-initialization. Geometry/configuration replacement must explicitly reload,
not mutate a published transform. Client and server still require matching assets
and configured dimension; no handshake/protocol change was introduced.

The integer hot path captures one volatile State, performs checked long arithmetic
and indexes one cell/palette entry. It does no I/O, decoding, tile scans, mutable
ownership reads, locking or persistent writes. The double/map compatibility paths
use bounded BigDecimal conversion and allocate temporary values plus the result;
no performance rewrite or per-position cache was justified by a demonstrated
failure. The retained raster array is 51,200,000 bytes, excluding small palettes;
image decode and client texture copies are additional memory. The full-cell audit
is test-only; it is not a gameplay benchmark or proof of low-end-PC frame rate.
Existing center/adjacency/bridge scans and screen projection arithmetic remain
presentation/route infrastructure, not alternate containing-tile decisions.

### Returning-player acceptance checklist

First join `127.0.0.1:57858` using the isolated matching profile, then as the
non-operator player run `/build list T132`. Confirm the previously created Build
is still present before making another record. B1-B3 are preserved accidental
records; do not delete them as part of this test.

Staff **server console** (no leading slash, no operator grant):

```text
conquest resolve 100 189568 -86016
conquest resolve 100 189696 -86016
conquest resolve 0 189696 -86016
conquest resolve 100 -103681 0
conquest resolve 100 305920 0
conquest resolve 100 0 -93441
conquest resolve 100 0 418560
conquest resolve 100 89599 103936
conquest resolve 100 89600 103936
conquest resolve 100 24128 -832
build list T132
```

Expected: gap (2291,58), T001 (2292,58), unsupported dimension, four OUTSIDE_MASK
results, T444 then T454, and T132. Use the actual configured Middle-earth ID if it
is changed in a future test; this isolated environment currently uses 100.

Non-operator **client** checks still needed:

1. `/build inspect <ID from /build list T132>`: verify the successful Build's name,
   coordinates, hours and manager after the server restart. Existing manager
   reconciliation remains dev's behavior if a manager actually changes faction.
2. `/kome tile T132` -> Builds -> Create Build: opening/cancel/Escape creates
   nothing; one deliberate confirmation creates exactly one record. The previous
   successful creation is user-reported; cancellation/repeat confirmation remains
   a separate manual check. Do not change operator or progression status.
3. With staff-console assistance, visit the verified gap X=189568,Z=-86016 in
   Middle-earth, reopen `/kome tile T132`, and attempt creation. Expect
   IN_BOUNDS_GAP and no added record. Repeat outside coverage X=-103681,Z=-86016,
   in an unsupported dimension, and at T001's valid position while selecting
   T132 (tile mismatch). The form captures the viewer position when opened;
   reopen it after moving. Staff must confirm a safe standing Y and dimension
   before any `tp <player> <X> <Y> <Z>`; the resolver does not validate terrain/Y.
   No arbitrary terrain height or unverified dimension-transfer command is given.
4. Verify map hover/right-click behavior around the same boundaries under pan,
   zoom and GUI scale. Try a faction without construction authorization at a
   spatially valid location using an appropriate test account; require permission
   denial. Internal counters/dirty flags and absence of response packets are
   automated assertions, not directly visible client evidence.

Geography decisions, live invalid-coordinate Build rejection, visual map behavior
at exact boundaries, and cross-dimension player interaction remain manual work.
The window-maximize investigation remains deferred and unchanged.


### Final evidence for this audit pass

- Focused resolver/raster/client-adapter/GUI/Build/public-access selection: 108
  discovered, 108 passed, zero skipped/failed/errored. The subsequently strengthened
  persisted-Build suite passed 4/4 before the final clean build.
- Final `./gradlew.bat clean test build --no-daemon --console=plain`: BUILD SUCCESSFUL;
  759 discovered, 757 passed, 2 skipped, 0 failed, 0 errored. Both skips are existing
  conditional custom-skin symlink tests because creating symbolic links is unavailable
  in this Windows test environment. Java 8-compatible production reobfuscation passed.
- Production artifact: `build/libs/KOME-LOTR-Addon-1.0.8.jar`.
  SHA-256: `4445D6F3D32842CCA0105D1746B43E7894C41B1EEA8C675B05CB466FFC0A7DE9`.
  Installed server and isolated Prism client copies match that hash.
- Isolated server PID 38296 was verified, then stopped through its console `stop`;
  it saved players/worlds and exited successfully. No client process was running.
  Stopped-world/configuration/profile backup:
  `C:\Users\dayne\Documents\KOME-Validation\tile-resolution-20260918-200409-d095d7\backups\before-foundation-audit-20260919T040619Z`.
  All 96 copied files matched source SHA-256, were rechecked before installation,
  and all non-JAR files remained byte-identical through artifact replacement.
- Restarted server PID 11564 reached Done and was observed listening exclusively at
  `127.0.0.1:57858`; online-mode remained true, configured Middle-earth ID remained
  100, and ops.json remained `[]`. No EULA/configuration/progression edits were made.
- At 23:07:32 local time, all ten resolver console checks in the checklist passed:
  canonical gap/T001, unsupported dimension, all four outside edges, T444/T454
  one-block transition, and T132. B1-B4 remained present. B4 `asdasdasdasd` retained
  manager/builder `_Danye_`, NORMAL type, 0.00 hours, coordinates displayed as
  `100:24127,67,-835`, H1 approval and the same three audit entries before/after
  restart. No records were deleted, migrated or spatially revalidated.
- Startup had no resolver resource-validation, linkage or client-classloading
  failures. Existing legacy Forge signature warning/version-check JSON error and
  waypoint-default warnings remain; one initial 2.089-second tick-lag warning was
  observed. These are not evidence of low-end-PC performance or new tile failures.
- The matching client profile is prepared, not launched for unattended gameplay.
  Open Prism Launcher and launch instance `tile-resolution-20260918-200409-d095d7`,
  then connect to `127.0.0.1:57858`. Account selection and visual acceptance remain
  the user's actions. First in-game check: `/build inspect B4`; expect the preserved
  record above. No new multiplayer/visual acceptance is claimed.
- Tracked/cached diff whitespace checks and all 29 changed/untracked text-file
  whitespace/conflict-marker checks passed. The index is empty. Branch remains
  `dayne/tile-world-resolution-dev`, HEAD remains
  `d6bb856f2a06d39f15458b042bb48071286655de`.
- Against the new pre-edit recovery, only this document, KOMETileWorldResolver,
  KOMETileClientAdapterTest, KOMETileBuildValidationTest, KOMETileWorldResolverTest,
  and KOMEPublicAccessPacketTest changed; KOMETileResourceAuditTest was added.
  All other pre-existing candidate files remained byte-identical, including both
  prior Build fixes. No resize work, protected worktree/recovery, or stash was changed.

Final unstaged/untracked inventory (17 modified, 12 untracked; includes earlier
reconciled tile/Build work as well as this audit's changes):

```text
 M build.gradle.kts
 M src/main/java/kome/client/KOMEConquestMapOverlay.java
 M src/main/java/kome/client/gui/KOMEGuiConquestCapture.java
 M src/main/java/kome/common/KOMEAddon.java
 M src/main/java/kome/common/command/KOMECommandConquest.java
 M src/main/java/kome/common/data/KOMEBuildService.java
 M src/main/java/kome/common/data/KOMEConquestTileDefaults.java
 M src/main/java/kome/common/data/KOMEWorldData.java
 M src/main/java/kome/common/network/KOMEPacketBuildAction.java
 M src/main/java/kome/common/network/KOMEPacketConquestOpenCapture.java
 M src/test/java/kome/common/command/KOMEPublicCommandTest.java
 M src/test/java/kome/common/data/KOMEForeignConstructionServiceTest.java
 M src/test/java/kome/common/data/KOMEPopulationProjectionTest.java
 M src/test/java/kome/common/data/KOMEPreciseBuildTest.java
 M src/test/java/kome/common/data/KOMERedesignSystemsTest.java
 M src/test/java/kome/common/network/KOMECanonicalBuildPacketTest.java
 M src/test/java/kome/common/network/KOMEPublicAccessPacketTest.java
?? docs/KOME_TILE_WORLD_RESOLUTION.md
?? src/main/java/kome/common/data/KOMETileRasterSnapshot.java
?? src/main/java/kome/common/data/KOMETileResolution.java
?? src/main/java/kome/common/data/KOMETileWorldResolver.java
?? src/test/java/kome/client/KOMETileClientAdapterTest.java
?? src/test/java/kome/client/gui/KOMEGuiBuildInteractionTest.java
?? src/test/java/kome/common/data/KOMETileBuildValidationTest.java
?? src/test/java/kome/common/data/KOMETileRasterSnapshotTest.java
?? src/test/java/kome/common/data/KOMETileResolverIsolationTest.java
?? src/test/java/kome/common/data/KOMETileResourceAuditTest.java
?? src/test/java/kome/common/data/KOMETileTestResources.java
?? src/test/java/kome/common/data/KOMETileWorldResolverTest.java
```
