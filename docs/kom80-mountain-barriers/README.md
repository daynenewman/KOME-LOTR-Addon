# KOM-80: mountain contacts and strategic departure

Investigation baseline: latest fetched `origin/dev` on 2026-10-03,
`41f145773601ff84f754421ee7534d7265d42f74`. Isolated branch
`dayne/kom-80-mountain-barriers`; no merge or deployment.

Five reported contacts exist in canonical geometry and are reproduced by the production map-border
extractor. The named Harnen pair does not touch in this baseline. **None of the six pairs has a direct
default strategic route.** The corrected gameplay defect is a queued-departure authorization gap:
an old accepted route could cross an edge after its explicit passage was removed or blocked.
Geographic reshaping remains unresolved; this change does not claim the visible contacts are removed.

## Independent authorities and reproduction

| Reported pair | Shared cell edges / diagonal cell pairs | Contact cell bounds, inclusive (mask X,Y) | Default direct edge | Actual production BFS with every tile owned by Gondor |
| --- | --- | --- | --- | --- |
| Valley of Spiders T654 / Crossings of Poros T435 | 2 / 3 | (1481,1386)..(1482,1388) | absent | no route either direction |
| Valley of Spiders T654 / Crossings of Harnen T455 | 0 / 0 | no contact | absent | no route either direction |
| T420 / T400 | 10 / 17 | (1895,1318)..(1904,1320) | absent | T420–T409–T400; reverse is the reversed path |
| T329 / T355 (Eastern Guard) | 13 / 19 | (1854,1135)..(1864,1139) | absent | T329–T331–T355; reverse T355–T338–T329 |
| T352 (North Ithilien) / T356 (Durthang) | 8 / 8 | (1457,1158)..(1460,1164) | absent | T352–T358–T375–T381–T391–T356; reverse T356–T391–T357–T346–T326–T352 |
| Ost-in-Edhil T218 / Mount Caradhras T220 | 14 / 22 | (1158,838)..(1162,850) | absent | no route either direction |

Diagonal cell pairs include corners adjoining an existing shared edge; they are not additional
strategic routes. For direct-route tests, only the two endpoints have authorized ownership, excluding
alternative paths. Separate tests authorize every tile and preserve the legal detours above. These
are controlled offline permissions, not claims about a particular saved world's owners or alliances.

The issue's "Ostin Edel" corresponds to the packaged Ost-in-Edhil waypoint. Names come from the
ownership/waypoint tables: Poros=T435, Harnen=T455, Spiders=T654. T444, a bridge neighbor of Harnen,
does touch T654; it must not silently replace the reported T455. The issue's Harnen observation needs
live tile-ID/coordinate confirmation against the installed version. Nearest boundary cells are
T455=(1511,1544) and T654=(1524,1421), with squared center distance 15,298 cells squared.

1. **Geometry:** `reset_conquest_tile_ids.png`, its color mapping and the exact floor-based resolver
   define membership. Alpha <=24 is a gap; retired colors remain excluded from active tile IDs.
   `tile_exclusions.tsv` has zero classified zones. A gap does not prove a mountain or traversal rule.
2. **Rendered map borders:** `KOMEConquestMapOverlay` uses the canonical snapshot through
   `KOMEMapBorders.Cache`; the old `reset_conquest_borders*.png` artwork does not supply current
   borders. `KOMEMountainContactBordersTest` compares the production extractor's complete shared-edge
   coordinate sets for all six pairs with an independent cardinal-cell scan, including orientation
   and duplicate rejection. This is CPU rendering evidence; live OpenGL display remains unverified.
3. **Strategic defaults:** `KOMEConquestTileDefaults` delegates graph lookup to
   `KOMETileGameplayDefaults`, whose checksum-validated route CSV is independent of the raster.
   `KOMEWorldData.getRouteEdge` gives saved overrides precedence; neighbors include override endpoints.
   OPEN, BRIDGE and MOUNTAIN_PASS are passable; RIVER, MOUNTAIN and BLOCKED are not.
4. **Actual authorization:** `KOMECommandTroops.findLegalRoute` checks effective adjacency and edge
   passability before tile access. Initial company movement repeats this search. Territory permission
   in `KOMEMovementAccessService` is a separate check; friendly ownership or a hostile terminal
   exception cannot manufacture an edge. Queued scheduling previously omitted that edge check.

## Correction and evidence of the defect

Reproducible without editing a save: temporarily authorize T218/T220 as a mountain pass, plan the
legal T188–T218–T220 route, reach T218, then remove the pass while the order waits. The order still
contains its original next leg. Before this correction, departure succeeds even though the effective
T218/T220 edge is now absent. The regression uses real BFS, order NBT serialization/reload, override
removal, production scheduling and `tryDepart`; it also checks the reverse example from T223.

The pre-fix run against unchanged production code discovered eight tests: four passed and four failed
for the expected authorization bypasses (missing edge, removed pass, blocked edge and waiting tick).
The captured transcript and JUnit result are in [validation evidence](validation.json).

Self-review then reproduced three recovery-command failures after the departure guard was present:
a normal controller's `stop` then typed `resume` skipped departure; operator `complete` changed a
rejected waiting order into ARRIVED; and legal queued `complete` published arrival twice. The minimal
command correction routes every STOPPED resume through the existing recovery policy (which already
rejects plain stopped orders in the GUI), and prevents `complete` from forcing/replaying an arrival
when its tick did not leave a MOVING order. Exact order/company/unit/history NBT preservation is tested
for denied plain-stopped resume. Legal completion publishes arrival once.

The narrow guard checks the actual next pair in the stored route against the current effective edge,
before chunk access or unit snapshot staging, and again before scheduling commits the leg. Missing
or impassable edges use `ROUTE_EDGE_MISSING` / `ROUTE_EDGE_BLOCKED` with the existing waiting/retry
diagnostics. Rejection preserves movement allowance, route progress, destination fields, unit lists
and stationed snapshots. Restoring an explicit legal passage permits normal departure.

No raster inference, new route type, movement/conflict redesign, in-world borders, save migration,
automatic route replacement, or relocation is introduced. Already committed arrivals retain their
existing semantics. Saved OPEN/BRIDGE/MOUNTAIN_PASS overrides remain authoritative, including an
intentional override between an otherwise disconnected pair. No live world's overrides were inspected.

## Nearby audit and geographic decisions still required

[Nearby contacts](nearby-contacts.csv) covers 47 tiles: all reported endpoints plus their immediate
geometric or strategic neighbors, considering pairs with both endpoints in that set. It inventories
81 geometric and/or strategic pairs: 48 OPEN, 15 RIVER, six BRIDGE and 12 geometry-only contacts.
This is a defined local audit, not a claim to classify every mountain range on the map.
The [nearby geometry-only cell inventory](nearby-geometry-only-cells.csv) records exact coordinates,
world bounds and provenance for all 12 such pairs (431 unique coordinates).

The seven additional geometry-only contacts are:

| Pair | Shared cell edges | Disposition |
| --- | ---: | --- |
| T220 / T231 | 6 | no default edge; geographic edit unresolved |
| T220 / T233 | 24 | no default edge; preserve the T220–T223–T233 corridor |
| T223 / T231 | 24 | no default edge; geographic edit unresolved |
| T232 / T233 | 18 | historical explicit edge removal; preserve absence |
| T346 / T356 | 16 | no default edge; geographic edit unresolved |
| T356 / T357 | 47 | no default edge; geographic edit unresolved |
| T444 / T654 | 27 | no default edge; distinguish T444 from Harnen T455 |

Protected legal controls are the historically authored OPEN corridor
T239–T232–T220–T223–T233 and BRIDGE pairs T409/T420, T425/T435, T444/T455, T322/T329 and T329/T338.
Tests exercise real BFS and departure in both directions. A mountain-looking map region alone is not
evidence to delete these authored routes. The historical inference source only had cardinal proximity,
river/bridge recognition and explicit corrections; it had no authoritative general mountain mask.

All five reproduced requested contacts were absent in the pinned gameplay-baseline raster. Their
99 distinct participating cells were assigned during previously accepted V2/final edits: 85 last
edited in V2 and 14 in the final batch. This explains how visual contact arose without a gameplay
edge. It does not establish which endpoint's cells to remove, the width of a mountain gap, its full
extent, or its exclusion classification. [Exact contact records](requested-contact-cells.csv) identify
both cells, historical IDs, last edit phase and half-open world-cell minima; each cell spans 128 blocks
in X/Z. The six [pair crops](contacts/) include five contact views with shared edges in magenta and
the non-contact Harnen view. The stock map is visual context only.

An exact correction requires a reviewed cell manifest specifying removals/reassignments and any
named exclusion zones, including how legitimate pass corridors remain connected, followed by a
persisted-reference impact review. Deleting both contacting cells, removing only one tile's side,
or restoring an entire historical mountain strip are materially different decisions. None is chosen
implicitly here. This follows the explicit geographic-decision boundary in KOM-43/KOM-59 and avoids
silently undoing previously accepted cells.

## Raster and gameplay preservation

The audit compares every one of the 12,800,000 RGBA cells with the isolated base commit.
[Exact changed-cell set](changed-cells.json): `[]`. Mask SHA-256 remains
`ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`:
4,067,979 assigned cells, 8,732,021 unclassified gaps, 621 IDs and 622 tile components.
The existing exhaustive replay also passes the 95 pilot + 93,103 V2 + 4,915 final approved edits,
component identity and protected controls. All gameplay CSVs, markers, arrival/reference coordinates,
waypoint candidates and ownership defaults remain byte-identical to the base Git blobs. The mapping
and exclusion text have only Git's pre-existing CRLF checkout conversion; their normalized Git blobs
are unchanged. All checked packaged resources are byte-identical to their working-tree sources.
Thus there are no changed-cell membership/gameplay effects. Movement changes reject an uncommitted
leg whose effective edge is missing or blocked and close the associated command bypass/replay paths.
Explicit operator force-repair controls such as `advance` and `retarget` are unchanged; they are not
ordinary route authorization and are not covered by a claim that all operator commands enforce BFS.

Reproduce geometry evidence with the existing Python NumPy/Pillow runtime and ignored stock LOTR jar:

`libs/LOTRMod v36.15.jar` must match the recorded SHA-256, and the base Git commit object must be present.

```powershell
python docs/kom80-mountain-barriers/audit-geometry.py
python docs/tile-milestone-checkpoint-20260920/verify-geometry.py
```

## Validation and review

- Focused: **90 passed**, no skips/failures/errors, including 15 new KOM-80 tests.
- Clean `test build`: **2,047 discovered, 2,042 passed, five existing optional/platform skips**,
  no failures/errors; production reobfuscation passed under `Local\KOME-Heavy-Validation`.
- Skips: two Windows symlink capability tests and three opt-in production/build waypoint-transformer
  JAR checks whose system properties are not configured. Their exact names are in
  [validation.json](validation.json); no assertions were disabled for this change.
- Production artifact: `build/libs/KOME-LOTR-Addon-1.0.8.jar`, 17,170,464 bytes,
  SHA-256 `eab47d43f8684ef41a72c12aba3abcb8c1eacf5e99d8c46cd1a0ec84a233d994`.
  Java 8 bytecode verified; no base `lotr/` classes packaged. Route/default/geometry resources verified.
- Independent final review found no actionable remaining code issue. It checked override precedence,
  hostile/retreat behavior, unit staging order, allowance consumption, halted recovery policy,
  committed-arrival preservation and the recovery-command bypasses fixed above.
- The first focused integration run exposed two synthetic campaign-boundary fixtures without route
  edges. They now explicitly authorize their intended T001/T002 and T002/T003 routes; their original
  allowance/retry assertions remain. The first resume fix also required the existing recovery-option
  guard; final focused and full results above supersede those intermediate failures.

Transcripts: [original failure](reproduce.txt), [recovery failures](reproduce-recovery.txt),
[focused pass](focused.txt), [clean full pass](full.txt), [raster replay](geometry-replay.txt).
Transcripts preserve substantive output with trailing console whitespace removed.

```powershell
.\docs\kom80-mountain-barriers\validate.ps1 -Mode Focused
.\docs\kom80-mountain-barriers\validate.ps1 -Mode Full
```

The runner acquires the named mutex and uses the repository's Gradle 8.5 wrapper, existing dependencies
and offline cache. This run used Eclipse Adoptium JDK 21.0.9.10 for Gradle and the existing Java 8
compilation/test toolchain. No dependency versions or build configuration were changed.

[Preservation evidence](preservation.json) records read-only comparisons of 19 other worktrees and the
existing stash. All other HEADs/branches and the stash were unchanged. Eighteen other worktree statuses
were unchanged; the active KOM-28 checkout independently gained 14 changes during this task. No action
here wrote to that checkout or attempted to restore its older status. Worlds and runtimes were neither
opened nor refreshed; Git metadata checks are not byte-level verification of ignored files or worlds.

## Remaining live checks

Use a separately authorized disposable environment and matching client/server jars; this PR does
not refresh, start, stop, merge into, or deploy to any existing runtime.

1. Confirm all six reported locations on the actual installed conquest map at useful zoom levels;
   record Harnen's displayed tile ID and coordinates. Check the nearby contacts and named passes.
2. With suitable same-faction test companies and safe destination terrain, demonstrate the temporary
   pass/removal sequence above. Verify a waiting company stays at origin, spends no allowance, leaves
   physical hires and stationary data intact, and reports the edge diagnostic after save/restart.
3. Restore a legal pass and verify one departure/allowance charge. Exercise the protected bridges and
   Caradhras corridor, hostile terminal permission and retreat without granting terrain bypass.
4. Check client diagnostics/retry pause behavior and a multi-player observer. A previously committed
   arrival keeps the existing finish-leg behavior; this patch does not roll it back.

Offline fixtures and a successful jar build do not prove terrain safety, entity spawning, client FPS,
live Forge startup, connected multiplayer, or a particular saved world's manual route policy.
