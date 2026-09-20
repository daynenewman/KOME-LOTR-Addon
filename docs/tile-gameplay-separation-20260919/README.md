# Geometry / gameplay separation plan — 2026-09-19

Status: read-only investigation and implementation proposal. No production changes. **V2 zero extra river buffer is selected; installation remains unauthorized.** Mapped water and conflicting geographic evidence stay protected. This is not another geography-authoring pass.

## Baseline and evidence

- Branch `dayne/tile-world-resolution-dev`; HEAD `04726458229057706e83b059e98e009f9aa4ac7e`; empty index.
- Current production mask includes the approved 95-cell Weathertop pilot. SHA-256 `a5cd6cf91b3fc1b662cffffde36a250a6e6687bcb8b2b7cd9aa06809b944b866`.
- Selected candidate: `../land-seam-candidate-v2-20260919/candidate-buffer0.png`; SHA-256 `46af1f42854c4c12b7f9ce69b3aad7ad4513340b000631f62514937debf0dbed`.
- Existing V2 production-derived exports remain intact: `derived-baseline.tsv`, `derived-buffer0.tsv`, `route-changes-buffer0.csv`, `center-shifts-buffer0.csv`. This audit verified their 15 recorded compiled production-class hashes, and loaded both masks independently through the production snapshot/resolver for center and actual LOTR waypoint checks.
- New evidence: `center-containment.tsv`, `legacy-center-exceptions.tsv`, `waypoint-containment.tsv`, `automatic-waypoint-selection.tsv`, `visual-centroid-changes.json`, `analysis-summary.json`.
- The user's direct transition observation remains a **single-location pilot check**, not full visual or movement acceptance. No saved world was opened or changed for this investigation.

## Confirmed behavior at risk

| Derived output | Production → selected V2 | Actual consequence |
| --- | --- | --- |
| Automatic connections | 1,319 → 1,396; 77 additions | New graph neighbors in fresh AND loaded worlds unless explicit world overrides block them. 63 new OPEN, 14 new RIVER. Geographic contact is currently interpreted as gameplay connectivity. |
| Existing route classifications | 12 RIVER→OPEN; 3 OPEN→RIVER | Changes passability, route search, and route diagnostics without an intentional movement-data edit. No bridge classification changes. Baseline is 944 OPEN, 319 RIVER, 56 BRIDGE. |
| Common world-space means | 562/621 move; largest T035, 285.624 blocks | Server search ordering, automatic waypoint ranking, and default rally destinations. Not just label positions. |
| Client integer mask centroids | 262 change | Visual only: troop summary labels and drawn route/movement paths. Linked waypoint anchors take precedence; 132 changes would affect unlinked default anchors. These counts assume the measured default links, not unknown saved overrides. |
| River blocker markers | 108 move, 17 added, 12 removed | Presentation of automatic route restrictions drifts too; baseline 319 markers. |
| Bridge markers | 58 unchanged | No current V2 effect; preserve associations and multiple markers per edge (56 bridge routes). |
| Actual LOTR waypoints | All 274 have unchanged raster containment | No current candidate-induced link reassignment. Of 273 visible waypoints, 271 have assigned tiles and produce 269 default links; both default selections are identical. Manual-override scenarios were not run against a saved world. |

Example: T041/T042 changes from six river samples out of six to 18/115, crossing the existing 60% rule and becoming OPEN. Preserving water pixels alone therefore does not preserve route classification.

Holding the **baseline graph and route types fixed**, the center change alone alters 2,698 node/goal neighbor orderings. A bounded graph-only BFS projection finds T064→T148 switching from `T064,T116,T109,T135,T148` to `T064,T116,T109,T134,T148`. It uses the production ordering formula with hypothetical permission for every tile; it is evidence of a heuristic coupling, **not a live-world movement test**. It should become a production route-search regression with explicit authorized ownership fixtures.

### Center containment: do not treat a mean as a safe destination

Production resolver results:

| Points tested | Inside their own tile |
| --- | ---: |
| Original means / original raster | 602/621 |
| Original means / V2 raster | 606/621 |
| V2 means / V2 raster | 604/621 |

No originally valid old mean loses containment under this additive candidate. Nevertheless, newly computed means introduce failures for previously valid T120 (gap) and T380 (T399). T423's old point changes from gap to T417, not T423. Means are not guaranteed interior points for concave/disconnected territory.

The 19 original failures are listed with exact coordinates and resolver results in `legacy-center-exceptions.tsv`. Eleven have a default LOTR link that ultimately supplies a different arrival point. Eight have no default link: **T242, T290, T395, T397, T447, T573, T634, T638**. Five old fallback points remain outside their own tile in V2: **T242, T395, T397, T447, T573**. The other three become valid through approved-candidate additions; no relocation is necessary. No terrain-height or safe-spawn guarantee was tested.

This is a pre-existing defect, not authority to move units or rewrite arrivals. Preserve these exceptions explicitly during a compatibility separation; do not label them valid or silently generate replacement destinations. Require separately reviewed destination edits to correct them. Validate every newly authored/revised destination against the raster and terrain during authoring. Route-reference points need not be inside territory because they are only a stable routing heuristic.

## Complete caller trace

Paths below are relative to `src/main/java/`.

- `kome/common/data/KOMEConquestTileDefaults.java`: `ensureLoaded` (212) derives arithmetic means, axial near-boundary pairs, river fractions and bridge samples from raster/overlay/road/bridge resources. `applyExplicitEdgeCorrections` (759) applies 28 forced-open pairs and five removals; two bridge pair remaps also exist. Export the **post-correction baseline**, not raw samples. `getTileCenter` (88), `getAdjacentTiles` (102), `getAutomaticRouteEdge` (131) expose gameplay-derived values. `getAutomaticBridgeEdges` (146) has no external production caller; keep compatibility only if still needed. Marker lists (166,191) serve the client, and marker count serves route graph diagnostics. Sampling totals themselves have no persisted or external gameplay consumer beyond classification/marker construction.
- `kome/common/data/KOMEWorldData.java`: `getRouteEdge` (1267) resolves saved override over automatic default; `getRouteNeighbors` (1302) unions automatic graph and override endpoints. `ensureAutomaticTileWaypointLinks` (687) maps actual LOTR waypoints through the live raster, then ranks by common center. Manual links and keys consumed by manual links take precedence. `ensureDefaultArrivalPoint` (1003) feeds common centers into fallback RALLY and legacy anchors; linked waypoints/manual arrivals take precedence. `buildConquestBalanceReportLines` (849, adjacency use 889) is an administrative report, **not the fresh initialization adjacency pass** suggested by the earlier V2 report. Ownership still belongs to WorldData; curated ownership defaults cover all 621 active tiles and override waypoint-derived native defaults.
- `kome/common/data/KOMEEvents.java`: first server tick calls `initializeIntegratedWorld` (169); automatic waypoint reconciliation occurs once about 15 seconds after startup (200–204,231), including for existing worlds. Keep this lifecycle; replace its source data, not the event timing.
- `kome/common/command/KOMECommandTroops.java`: legal BFS uses effective graph and `edge.isPassable` (4426); `sortRouteNeighborsForGoal`/`routeCenterDistanceSq` (4450–4494) rank neighbors using common centers, also used by closest-reachable diagnostics (4711). Graph count/adjacency reports use defaults (4751,4761,4778). `requireArrivalTarget` (3185) uses saved RALLY or legacy anchor; `validateSpawnTarget` (3258) checks dimension/chunk availability, **not containing tile**. `scheduleNextRouteStep` (3016) stores the next target into an order. `targetFromOrderArrival` (3151) uses already persisted leg coordinates. No automatic relocation or route rebuild is performed just by reading the geographic resolver.
- `kome/common/command/KOMECommandConquest.java`: staff `waypoint auto/suggest` uses `automaticWaypointForTile` (406), another live-mask plus common-center selection. Manual link/unlink commands are already explicit editing mechanisms. Keep permission guards intact.
- `kome/client/KOMEConquestMapOverlay.java`: `computeTileCenters` (597) independently computes integer **visual** mask means; `tileAnchorScreenPosition` (1013) prefers synchronized waypoint links over these means. Used by troop labels (843), route preview (1169), and movement route drawing (1213). Common route markers are drawn at 1072/1105, with existing world override handling. Hover/click continue through the exact common resolver. No new visual-center authority is needed.
- `kome/common/data/KOMEMovementAccessService.java`: restart revalidation concerns ownership/access; it does not itself inspect the automatic edge type. Do not claim V2 alone cancels persisted movements on load. The graph changes affect searches and any actual edge consumers; stored current-leg destinations are independent.

### Fresh worlds versus existing worlds

**Fresh:** integrated initialization creates defaults and fallback arrivals before delayed waypoint linking. New means can therefore affect all initial fallback points; after default links settle, 352 tiles remain without a link and 302 of their fallback means differ in V2. The first-tick/delayed-link phases need separate parity tests.

**Existing:** `readFromNBT` (1858) uses a transactional candidate; loads saved waypoints, links and route overrides (2170–2200), then `applyWaypointDefaults` (2459). Later the startup pass recomputes automatic links. Manual arrivals/links remain authoritative. Existing fallback arrivals within 1,024 blocks of the current mean are retained: the maximum V2 mean shift is below that threshold, so a complete world whose fallback arrivals exactly equal the present baseline means would retain them. Missing/older/off-center data can take another branch; do not assert universal destination migration or universal safety. Linked automatic arrivals use a separate four-block refresh threshold and synchronize anchors. Current V2 links/waypoint positions themselves are unchanged.

Automatic route defaults are **not stored** in NBT; only explicit overrides are. Thus saving/reloading does not protect established default connections or route types. Already stored current-leg coordinates remain stored; future legs obtain the then-current arrival data. No saved-world migration was executed or proposed.

## Recommended separation

Add one immutable, validated **gameplay-defaults dataset**, keyed by the existing canonical tile IDs. It supplies connections, route types, route-reference points, fallback arrival defaults, and explicit eligible LOTR link candidates. It is not a tile registry, polygon system, alternate mask, ownership system, or per-world copy of defaults.

1. Keep the raster exclusively authoritative for geographic containment, HUD, Build location validation, selection/hover, and future geographic borders. Keep the exact transform, gaps, configurable Middle-earth dimension and O(1) resolver unchanged.
2. Replace runtime raster-derived gameplay generation with packaged baseline tables. An absent connection means no default connection; never discover one just because territories touch. Use existing `KOMEConquestRouteEdge` types and WorldData override precedence.
3. Preserve the **621 routing reference points** exactly (round-trip decimal doubles, no rounding) because route ordering demonstrably depends on them. Name their API `getRouteReference`, not geographic center. Preserve distinct fallback-arrival fields initialized from baseline behavior; future arrival edits must not incidentally reorder routes. The 19 legacy invalid points require explicit compatibility annotations, not silent repair; eight remain fallback destinations after default linking.
4. Preserve the full **271 visible assigned waypoint candidates**, including order, not merely the 269 winning defaults. Otherwise a manual link consuming a preferred key would lose the existing alternative-candidate behavior. Store reviewed tile association and priority based on original reference distance plus enum order for exact ties. WorldData still excludes manually consumed keys/tiles. Future raster edits report containment mismatches for review, never silently reassign links.
5. Leave existing client visual means geometry-derived. Maintain current linked-anchor precedence. Preserve baseline route marker records explicitly so drawn blockers agree with stable gameplay rules; keep multiple bridge markers per route. No redesign of rendering.
6. Load the complete gameplay dataset atomically. Validate canonical active IDs, complete required point rows, unique edge keys, supported types, valid waypoint keys/order, finite coordinates and marker associations. Resolve the logical Middle-earth dimension from LOTR configuration, never bake test dimension 100 into assets. Missing/invalid data must fail explicitly, never regenerate from a changed mask or act as an empty graph. Queries remain read-only; no new packets or WorldData schema.

## Bounded implementation plan (not executed)

### A. Capture and validate current defaults before changing loaders

Add these proposed resources under `src/main/resources/assets/kome/config/`:

- `kome_tile_route_defaults.csv`: exactly **1,319** canonical endpoint/type rows (944 OPEN,319 RIVER,56 BRIDGE), incorporating existing corrections/remaps.
- `kome_tile_gameplay_points.csv`: **621** rows with stable routing reference X/Z and separate fallback X/Y/Z (current default Y=80), plus explicit legacy destination exception reasons. Document baseline source hash and row role. Do not duplicate ownership, identity names, or dimensions.
- `kome_tile_waypoint_candidates.csv`: **271** ordered tile/key associations, preserving fallback candidates and current tie order.
- `kome_tile_route_markers.csv`: **58 bridge + 319 river** baseline marker records with their actual associations/coordinates; no runtime resampling. Preserve full multiplicity.

Use `derived-baseline.tsv` and the checked waypoint catalog as export inputs. Cross-check exported tables against current production APIs in a fresh JVM before replacing any source. Preserve existing handcrafted corrections in the exported results; retire the duplicate correction authority only after parity passes. Keep the old derivation as an offline diagnostic if useful, not as a runtime fallback.

### B. Narrow source changes

- **New:** `src/main/java/kome/common/data/KOMETileGameplayDefaults.java`: single common-side immutable table loader and explicit APIs. Validate against existing ID authority; return immutable values or defensive `KOMEConquestRouteEdge` copies because that class is mutable.
- **Change:** `src/main/java/kome/common/data/KOMEConquestTileDefaults.java`: retain identity/color/loading and exact geographic resolver delegation; delegate existing graph/marker compatibility APIs to the dataset. Remove gameplay pixel scanning and exception-swallowing initialization from that path. Do not duplicate registry authority.
- **Change:** `src/main/java/kome/common/data/KOMEWorldData.java`: use explicit fallback-arrival fields and waypoint candidates, retaining saved manual/automatic handling, precedence, refresh thresholds and first-tick/delayed-link lifecycle. Keep NBT fields and ownership logic unchanged. No mass backfill/migration.
- **Change:** `src/main/java/kome/common/command/KOMECommandTroops.java`: switch the route heuristic to `getRouteReference`; keep BFS, permissions, passability and movement timing unchanged. Arrival/waypoint commands remain the explicit world-local editing path.
- **Change:** `src/main/java/kome/common/command/KOMECommandConquest.java`: `auto/suggest` reads the explicit ordered catalog instead of deriving association from the live mask; retain manual link/unlink and staff checks.
- **Documentation:** `docs/KOME_TILE_WORLD_RESOLUTION.md`: data ownership, authoring procedure, exceptions and parity evidence.

No required changes to `KOMEConquestMapOverlay`, `KOMEEvents`, resolver/HUD/Build code, packets, schema, or movement access service. Compatibility graph/marker accessors avoid gratuitous client edits. Separate routing/fallback point roles are necessary; another visual-center implementation is not.

### C. Tests and acceptance gates

Add `src/test/java/kome/common/data/KOMETileGameplayDefaultsTest.java` for full baseline table/API parity, malformed resources, immutable publication, duplicate keys and server isolation. Validate the dataset against BOTH production and selected candidate masks without installing the candidate.

Extend/add focused production-entry tests:

- `KOMECommandTroopsMovementTest.java`: compare effective graph/types and authorized route choices under both raster fixtures; include T041/T042, all 77 candidate-only pairs, all 15 flips, plus the center-only T064→T148 projection promoted into a production BFS fixture. Exact path parity, not only neighbor counts.
- `KOMEWorldDataAtomicLoadTest.java` and a new `KOMETileGameplayInitializationTest.java`: fresh initialization, delayed links, persisted manual and automatic arrivals, older missing/default data, consumed-waypoint alternative selection, same-source threshold behavior, failed dataset load without partial publication. Verify identical state changes under baseline vs V2, not blanket zero dirtiness from initialization that already mutates defaults.
- `KOMEWorldDataSchemaTest.java`: unchanged root/nested NBT and protocol, unchanged saved current-leg coordinates and route overrides; do not recreate worlds.
- Existing tile resolver/HUD/Build suites: geography still follows the chosen raster; gameplay tables do not become a location authority. Preserve gap `(2291,58)`, T001 `(2292,58)`, exact fractional sampling, deliberate creation and rejection atomicity.
- Geometry authoring gate: all new/revised arrivals and linked waypoint associations resolve into their named tile. Unchanged pinned legacy exceptions remain explicitly reported; reject additions to that exception list without review. Surface-only map edits must produce **zero unapproved gameplay-data differences**.

Run focused tests then one clean test/build after implementation. Only afterward consider an isolated world-copy startup comparison, including first tick, delayed link pass, save/restart, manual overrides and active movement fixtures. Do not test by modifying the user's existing world. Installing V2 remains a separate approval.

## Future explicit gameplay edits

Global default changes edit the reviewed gameplay CSV rows in a separate gameplay-data diff: add/remove a connection, choose its existing route type, adjust marker records, change a routing reference only if intentionally changing route selection, or edit a fallback destination/waypoint association. Geometry changes alone never update these files automatically. Test containment and in-game safe destination terrain separately.

Existing world-local operations remain available through their current authorization: `/troops route addedge`, `removeedge` (a BLOCKED override, not deleting the default), `block`, `unblock`, `bridge add/remove`, `passage add/remove`; `/troops arrival set` or `/troops waypoint set` for manual destination overrides; `/conquest waypoint link/unlink` for explicit LOTR associations. Removing an override reveals the packaged default. Do not change command permission rules. Existing saves retain their overrides; any intentional migration of old default destinations needs its own approved plan rather than being hidden inside a raster update.

## Reproduce and scope

From this worktree, with the existing local Java 8, compiled candidate classes, NumPy and Pillow:

```powershell
python docs/tile-gameplay-separation-20260919/run-audit.py
```

This only writes the new audit directory. `analyze.py` computes client integer centroids directly from PNGs, checks recorded class hashes, summarizes production resolver results, and runs a clearly labeled graph-only projection. `CenterWaypointAudit.java` invokes production snapshot loading and exact position resolution on all 621 centers in both masks and all 274 LOTR waypoints; it never constructs WorldData. No Gradle suite/build, world loading, server process, or external service was used. The route/marker change counts are verified-existing V2 export evidence, not a repeated geography audit.

Preservation: the start manifest covers 1,094 pre-existing tracked/untracked files. End-of-task verification is recorded in `preservation-verification.json`. V1/V2 proposals, production code/resources/tests, existing HUD/Build/pilot/resize work, other worktrees, recovery packages, worlds and protected stash were not edited. All new files are confined to this audit/plan folder and remain untracked. No staging, commits, branches, installation, or Linear actions.
