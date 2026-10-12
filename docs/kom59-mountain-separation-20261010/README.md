# KOM-59 approved mountain separation — 2026-10-10

Implements Dayne's approved **45-cell batch** from [decision package 4475f90](https://github.com/daynenewman/KOME-LOTR-Addon/blob/4475f90d6269408a3d2f170b97276d91dcf568b9/docs/kom59-geography-decisions-20261010/README.md), based on dev **26ba6cb7d1bbe7d4e0e4482b4f5855e15747c04d**. KOM-59 remains **In Progress**. This source change is not world adoption or deployment.

The exact [cell authority](recommended-cell-manifest.csv) retains SHA256 **f14472b0e865974634bc5b6e791e0ce872687f1fda6a56958ec038165a9b4b5e**. The copied proposal labels in those immutable CSVs describe their original review state; Dayne's subsequent approval authorizes this batch. Historical selections in analysis.json are not used. The exact [25 approved runs](recommended-exclusion-manifest.csv) expand to these cells only. [Geometry provenance](geometry-provenance.json) records the resulting resource identity.

| Approved pair | Removed cells | Shared edges before → after | Diagonal corners before → after | Active tile components after |
|---|---|---|---|---|
| T435/T654 | 2 from T654 | 2 → 0 | 3 → 0 | 1 each |
| T400/T420 | 10 from T420 | 10 → 0 | 17 → 0 | 1 each |
| T329/T355 | 4 from T329, 8 from T355 | 13 → 0 | 19 → 0 | 1 each |
| T352/T356 | 2 from T352, 4 from T356 | 8 → 0 | 8 → 0 | 1 each |
| T218/T220 | 15 from T218 | 14 → 0 | 22 → 0 | 1 each |

The whole-raster resource audit retains the preexisting unrelated T423 two-component territory; this batch does not repair or alter it.

All 45 cells become RGBA **0,0,0,0** in the canonical raster and receive one of five named mountain exclusions. Other gaps remain unclassified. The production resolver reports CLASSIFIED_EXCLUSION, no tile identity, capturable false and traversal unknown. Classification does not introduce movement authorization or a new strategic graph edge.

Canonical PNG: **ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7** → **89bd8ccc9ceff3e7d3f9271d61125b07f5f9b1122fb51726ab2f2f5a95364a12**. The exclusion header binds the resulting PNG exactly; metadata SHA256 **56c72566cdbe9717053de57597fc4ebf52a4fd0491663c737d3d65d95d641aa3**. Mismatched resource pairs reject publication and retain the previous valid snapshot. The existing loader's nonempty dense index uses **25,600,000 bytes per snapshot**; this is an allocation calculation, not measured FPS/tick/hardware acceptance.

## Scope and topology

[Decoded resource audit](geometry-audit.json) verifies exactly 45 changed RGBA cells, zero unrelated changes, all **621 active IDs**, and one connected component for all ten tiles in the five pairs. The untouched historical baseline remains in [golden test resources](../../src/test/resources/kome/tile/gameplay-baseline/). The historical V2 replay accepts only this independent checked delta; everything else remains strict. Existing golden gameplay comparisons retain the earlier T351/T352 edge/marker correction and receive no new gameplay delta.

[Protected corridor manifest](protected-corridors.csv) verifies the T239–T232–T220–T223–T233 corridor and bridges T409/T420, T425/T435, T444/T455, T322/T329 and T329/T338. T329/T331 retains its open route and connected contact: shared segments 86 → 85, diagonal corners 112 → 112. This single segment reduction is part of the approved cut. No route is regenerated.

All **22 other map/config resources** retain their pre-edit bytes in this checkout, including tile/color identities, ownership defaults, route defaults, route markers, waypoint candidates, configured arrivals and provenance. [Byte hashes](unchanged-resources.tsv) record them. The regression uses canonical Git bytes for ordinary text whose platform checkout changes line endings; versioned byte-pinned datasets remain strict. All **1,242 configured route/arrival point positions** avoid the removed cells. Gameplay revision stays baseline-pilot-20260919-kom59-t351-t352-20261010. Saved overrides and queued routes are neither rewritten nor migrated.

**Excluded:** conditional T340/T352, unresolved Harnen and the separate T364 proposal. No decision for those cases is implied. Normal shared borders remain directly traversable unless intended geography supplies a real barrier; decorative colors alone do not establish a barrier.

## Validation and retained evidence

Initial focused: **99 passed**, no skips. The one clean attempt retained **two failed historical geometry assertions** and five skips. Corrected focused checks: **7 passed**. Full test/build resumed after that clean attempt: **3,095 discovered, 3,090 passed, five skipped**, no failures/errors. Historical rendering counts remain checked against the immutable pre-edit mask; only the independently approved cell delta changes current expectations. Failed transcripts and XML are retained. Validated source commit: **294990b3ecf1935fa514e5ddc61b835c613ba43b**; the subsequent publication commit changes documentation/evidence only. Exact publication head is recorded in the draft PR.

[Validation report](validation.json) and [captured transcripts/XML](validation-evidence.zip) record focused production loader, adapters, hash rejection, complete pixel oracle, connectivity/pass checks, gameplay parity, movement/recovery and clean test/build. [Packaged-resource audit](packaged-resources.json) verifies the built development and reobfuscated production JARs. These are automated production-code/resource checks, not a Forge launch, human test, multiplayer result or target-world inspection.

[Preservation record](preservation.json) covers existing worktrees, branches, stashes, original decision package, published review checkout and runtime identities/configuration/artifacts. No existing world writes, runtime commands, restart, deployment, migration or merge occur. Frozen runtimes **51326/51327/51328** remain unchanged. The existing global reservation and 51328 leaf reservation are verified by controller identities and logs; offline checks serialize on their child mutex without releasing another owner's reservation. No source changes to the exclusion loader were necessary.

Documentation: routing checker **PASS** (25 documents, 748 links, 920 symbols, 48 selectors), all introduced package/guide links and five embedded crops resolve, and whitespace checks pass. The expanded check retained one inherited broken historical image link in KOME_TILE_WORLD_RESOLUTION.md (land-before-after.png); it already exists unchanged in base dev. See [documentation checks and retained limitation](documentation-checks.json).

## World-adoption gates

1. Obtain a consistent stopped **authoritative target production save**, including root state, player/LOTR state and Anvil physical entities. Current production-save and physical-entity impact remain **unknown**, not zero.
2. Inventory Builds, waypoint containment/links, companies, queued movements, conflicts and rider/mount positions against the exact 45 cells; review remediation per affected record before adoption. Stable IDs do not guarantee that physical coordinates remain inside a tile.
3. Prior [stopped-snapshot inventory](https://github.com/daynenewman/KOME-LOTR-Addon/blob/4475f90d6269408a3d2f170b97276d91dcf568b9/docs/kom59-geography-decisions-20261010/saved-reference-evidence/summary.json) covers only 15 of 57 historical checkpoints. It found no coordinate hits but includes 246 affected-tile waypoint/link records; 42 checkpoints were excluded/unavailable and 66 coordinate fields missing. Physical entities and the current production save were not covered. Reuse its evidence with those limits; do not infer zero migration risk.
4. World adoption/deployment requires separate authorization. Multiplayer remains deferred. Other KOM-59 geography and KOM-58 classification decisions remain open; this PR cannot close either ticket globally.

## Approved visual evidence

These are the unchanged annotated before/proposed-after crops from the approved package. The resource audit above independently checks that the implemented delta matches the manifest.

### T435-T654

![Approved before/after crop for T435-T654](visuals/T435-T654.png)

### T400-T420

![Approved before/after crop for T400-T420](visuals/T400-T420.png)

### T329-T355

![Approved before/after crop for T329-T355](visuals/T329-T355.png)

### T352-T356

![Approved before/after crop for T352-T356](visuals/T352-T356.png)

### T218-T220

![Approved before/after crop for T218-T220](visuals/T218-T220.png)
