# Gameplay separation and V2 integration — local candidate

This implements the reviewed plan. Only the worktree candidate changed; the isolated runtime and all saved-world data were left alone.

## Recovery and baseline

Verified recovery package:
`C:/Users/dayne/Documents/KOME-Recovery/before-gameplay-separation-20260920T001624Z-c9fef0ac`.
It contains 1,108 pre-existing tracked/untracked files, exact hashes, binary tracked/index diffs, branch/HEAD/status/worktree metadata, and a verified history bundle. Source inventory/hashes were rechecked after copying. No prior recovery package or other worktree was modified.

Branch remains `dayne/tile-world-resolution-dev`; starting checkpoint was
`04726458229057706e83b059e98e009f9aa4ac7e`, empty index. Existing HUD, Build and audit changes were preserved. The source geometry baseline includes the approved 95-cell pilot:
`a5cd6cf91b3fc1b662cffffde36a250a6e6687bcb8b2b7cd9aa06809b944b866`.

Before replacing inference, `DerivedAudit` ran the original compiled production algorithms against that exact installed baseline: 621 centers and 6,268 derived records, captured in `original-derived.tsv`. Its SHA-256 is `97f31e11e33705df4eb7b4c10c355d3741b042401225c7b00edde78a8fb88efb`. The exact old inference source is preserved as `original-KOMEConquestTileDefaults.java.txt` (not compiled or packaged). No table was derived from V1 or V2.

A new focused harness also ran **original production routing and WorldData** before replacement. It captured 6,055 stable expected rows into `src/test/resources/kome/tile/gameplay-baseline/production-paths.tsv`: complete graph/types/names/passability/special-passage flags, reference coordinates, route markers, every tile's arrival/link/ownership/anchor state across fresh initialization, delayed linking, overrides, NBT load and restart linking, plus stored leg coordinates and actual private command BFS routes. Timestamps were intentionally excluded from comparisons. Expectations were frozen before source replacement; the one-time capture branch was removed from normal tests. First harness compilation corrected the field name to the existing `ownerFaction`; it did not change production or captured behavior.

## Implemented source/resource scope

| File | Change |
| --- | --- |
| `src/main/java/kome/common/data/KOMETileGameplayDefaults.java` | One immutable validated loader, version/checksum checks, bounded reads, stable routing/arrival point roles, explicit candidates, defensive route DTOs. |
| `src/main/java/kome/common/data/KOMEConquestTileDefaults.java` | Keeps existing tile identity and exact lookup delegation. Graph/marker compatibility APIs now use explicit defaults; over 1,000 lines of runtime raster inference/corrections removed. Their corrected outputs are preserved in data. |
| `src/main/java/kome/common/data/KOMEWorldData.java` | Uses explicit arrival fields and ordered waypoint candidates. Manual keys/links, saved overrides, refresh thresholds and initialization phases retain their original behavior. |
| `src/main/java/kome/common/command/KOMECommandTroops.java` | Routing heuristic uses stable reference points. BFS, authorization, edge semantics, movement timing and stored-order handling unchanged. |
| `src/main/java/kome/common/command/KOMECommandConquest.java` | Existing staff auto/suggest path uses explicit candidates. Manual link/unlink and permission guards unchanged. |
| `src/main/java/kome/common/KOMEAddon.java` | Validates the whole gameplay dataset at post-init, before world initialization. Invalid resources fail explicitly, with the offending resource and recovery instruction. |
| `src/main/resources/assets/kome/config/kome_tile_route_defaults.csv` | 1,319 baseline connections: 944 OPEN, 319 RIVER, 56 BRIDGE. Existing handcrafted open/removal/remap effects are incorporated. |
| `.../kome_tile_gameplay_points.csv` | 621 routing-reference and distinct arrival-default records, with 19 documented legacy containment exceptions. |
| `.../kome_tile_waypoint_candidates.csv` | 271 ordered candidates; manual consumption still permits alternatives on T364 and T375. No new waypoint feature. |
| `.../kome_tile_route_markers.csv` | Exact 58 bridge and 319 river markers with preserved associations/multiplicity. |
| `.../kome_tile_gameplay_manifest.properties` / `.../kome_tile_gameplay_provenance.json` | Version 1, baseline revision and cross-file hashes; independently captured input provenance. |
| `src/main/resources/assets/kome/map/reset_conquest_tile_ids.png` | Exact selected V2 mask, installed only after separation parity passed. |

No changes to client visual centroid computation, HUD code, Build behavior, Events timing, ownership authority, packets, schema or protocol. The `postInit` preflight is a small addition to the plan's initially listed caller files: it prevents partial world setup from being the first place corrupt gameplay data is discovered. No new library or runtime cache regeneration.

The loader builds private maps/lists completely, then publishes once through a volatile reference. Failed loads publish nothing; construction of an invalid alternative leaves the current valid dataset intact. Runtime reload/edit infrastructure is intentionally absent. Logical Middle-earth point/marker dimensions come from existing LOTR configuration. Existing waypoint-link dimension behavior is not redesigned. None of these tables contain mutable ownership or alternate geographic shapes.

## Parity and installation gate

The first replacement passed the exact original 6,055-row production-path snapshot. The expanded pre-install gate passed **60/60**, covering both baseline and selected V2 in isolated classloaders plus production routing, initialization and saved-data suites. `gate-TEST-*.xml` and `separation-gate-counts.json` preserve that evidence.

Under both masks the new gameplay defaults remain identical:
- 1,319 effective default pairs, exact types/passability/bridge flags and marker coordinates.
- All 621 original routing references and arrival defaults.
- All default link selections, initial arrival creation, subsequent delayed linking, manual override precedence, reload and stored arrival coordinates.
- Original T064→T148 route remains `T064,T116,T109,T135,T148` in the authorized production fixture.
- **Zero** of V2's former 77 automatically added gameplay pairs or 15 classification flips remain. Geometric contact alone no longer adds a gameplay route; T041/T042 stays RIVER.

Then the current production baseline and the selected PNG/CSV hashes were verified. Both images passed the production loader; all 12,800,000 cells were compared, with exactly 93,103 listed gap edits and no other pixel/identity changes. The validator made 558,618 additional world-corner/fractional/map-position checks and seven protected controls. A separate CSV check verified every previous RGBA, destination ID, duplicate-free cell and exact world bounds before copying the candidate bytes.

Installed PNG SHA-256:
`46af1f42854c4c12b7f9ce69b3aad7ad4513340b000631f62514937debf0dbed`.
Approved CSV SHA-256:
`08a5ba7af076f053eacf9c995ca395f9737916bdafb37d73bdb5372d1ccb53f5`.
See `installation.json` and `candidate-validation.log`.

## Regression coverage and commands

New `KOMETileGameplayParityTest` executes production initialization, delayed linking, full graph reads, actual private BFS, manual overrides, saved active-leg coordinates, and repeated NBT reload. `KOMETileGameplayDefaultsTest` exercises both mask variants, mixed/missing/malformed/oversized resources, unsupported versions, unknown IDs/waypoints, duplicate routes, immutable collections, defensive edges, configurable point dimensions, and concurrent first publication with client and PNG access forbidden. Failed initial loading remains explicit on repeated calls.

`KOMEV2GeometryTest` compares every pixel to the retained pilot baseline and exact approved TSV, tests all new cells through the production resolver at block corners/fractional upper edges, and pins the installed hash. The historical pilot test still proves the original 95-cell delta against its retained baseline and checks all 95 cells in production. Its 14 former junction exclusions now use exact approved V2 assignments; protected controls remain. No test assertion was changed to accommodate a gameplay difference.

Focused installed suite: **203 discovered, 203 passed, zero skipped/failed/errored**. It includes resolver, resource audit, HUD, client adapter, Build interaction/validation, public access, gameplay defaults, routing, WorldData and schema tests. A preliminary broad `*Build*` pattern was expanded by Windows to `build.gradle.kts`; fully qualified patterns fixed command selection without changing tests.

```powershell
.\gradlew.bat test --tests 'kome.*Tile*' --tests 'kome.*Build*' --tests 'kome.*PublicAccess*' --tests 'kome.*KOMECommandTroopsMovementTest' --tests 'kome.*KOMECampaignBoundaryMovementTest' --tests 'kome.*KOMEWorldData*' --no-daemon --console=plain
.\gradlew.bat clean test build --no-daemon --console=plain
```

Final `clean test build`: **791 discovered, 789 passed, two skipped, zero failed/errored; BUILD SUCCESSFUL (1m32s)**. The skips are the existing Windows symbolic-link capability tests in `CustomSkinLibraryFoundationTest` and `ClientCustomSkinCacheTest`. No assertions or behavior were weakened to accommodate them.

Production/reobfuscated artifact: `build/libs/KOME-LOTR-Addon-1.0.8.jar`, SHA-256 `479cca8f5463a65921eb51bae0bf0db0a1947bb054fc7cd29078cb1c199fa5b6`. All seven packaged gameplay/geometry resources match source hashes; the new loader is present and removed inference classes are absent. The offline exporter reproduced all four CSV hashes in `build/reports/original-gameplay-export`. `git diff --check`, cached checks, and added/modified source whitespace checks passed. Final details are in `final-validation.json`. Tests use in-memory worlds/NBT and classloader isolation; they do not prove native Forge startup, live multiplayer, visual borders/riverbanks, safe destination terrain, or actual save-directory I/O. No running validation environment was refreshed.

## Authoring and future changes

Reproduce the original exported tables without touching production:

```powershell
python docs/gameplay-separation-implementation-20260919/export-baseline.py --output build/reports/original-gameplay-export
```

The exporter pins the original mask and captured inference source/output hashes; it never reads the installed mask as a gameplay input. Normal builds do not run this script. It reproduces the original baseline, not future authored gameplay revisions.

Intentional global gameplay edits must change the relevant CSV rows explicitly, update the revision and each affected SHA-256 in the manifest, and review the resulting graph/routing/destination diff. Preserve canonical IDs. Add/remove connections or change existing route types explicitly; update corresponding route markers. Change a routing reference only when a route-order change is intended. Changing an arrival or waypoint association must not implicitly move its routing reference. Validate new destinations against the authoritative raster and actual terrain; do not create new legacy-exception annotations to evade review.

Existing authorized `/troops route` overrides, `/troops arrival`/`waypoint`, and `/conquest waypoint link/unlink` remain the world-local mechanisms. Removing a saved override reveals packaged defaults. No existing world is migrated or backfilled by installation beyond its unchanged lifecycle behavior. Any future migration requires separate approval.

## Limitations and manual acceptance

All 19 originally invalid center/destination points remain explicitly preserved compatibility data. Eight lack a default waypoint link: T242, T290, T395, T397, T447, T573, T634, T638. Five remain outside their own tile under V2 (T242,T395,T397,T447,T573). They need separately reviewed destination corrections; this work neither relocates entities nor invents safe coordinates.

V2 keeps mapped water and conflicting evidence, broad/uncertain areas, and disconnected T423. It is still a coarse 128-block raster; source-map colors do not prove generated banks. All 14 pilot-excluded junction cells are now intentionally filled under V2, including T171 assignments. The exact R1 probe remains a gap, though approved land cells in the larger region may change. Only one pilot transition has prior user visual acceptance.

After a separately authorized matching-artifact runtime refresh: check startup diagnostics; verify protected console coordinates and normal-player HUD transitions; inspect T041/T042's unchanged river route and saved bridge/blocked overrides; test invalid Build rejection/cancellation; and save/restart a copied world containing manual arrivals and an active movement. The first console check can be `conquest resolve 100 21119 -384` (T149), using the actual configured dimension. Current test-world state, progression and operator status were not accessed or changed here.


## Final preservation

All 1,108 pre-existing tracked/untracked files were checked against recovery hashes: 1,100 unchanged, exactly eight intentionally modified (five common Java files, the main documentation, installed PNG, and historical pilot test). In particular, the existing client/HUD/Build changes and every prior audit/proposal remain byte-for-byte unchanged. The protected stash object is still present; the index is empty; branch/HEAD are unchanged. No other worktree, earlier recovery package, test-world file, runtime configuration/progression/operator state, server process, commit, remote, or Linear record was changed. See `final-git-status.txt` for the full unstaged/untracked inventory.
