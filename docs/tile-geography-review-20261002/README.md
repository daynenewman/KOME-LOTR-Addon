# Tile/geography review package - 2026-10-02

Feature base: current origin/dev `76fb78e7fc07a6d3421b5aa1c597e86ef36af6bf`.
Branch: `dayne/tile-geography-review-20261002`. Local review only; no merge/deploy.
See [progress](PROGRESS.md), [preservation baseline](preservation-before.json) and
[verified preservation](preservation-after.json). Independent [Claude report](claude-review.txt)
and [finding assessment](claude-review-assessment.md) are included.

Current source status: the [approved KOM-59 45-cell mountain batch](../kom59-mountain-separation-20261010/README.md) supersedes the historical empty production set below. This October 2 package remains evidence for its original artifact; other proposals remain unresolved.

## Historical KOM-58 metadata contract

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

## Automated evidence

Final `.\gradlew.bat clean test build --no-daemon --console=plain`: **1,177 discovered, 1,175 passed, two existing Windows symbolic-link skips, zero failures/errors**. Exact skipped cases and suite totals: [build-results.json](build-results.json); build transcript: [clean-build.txt](clean-build.txt). All 6,055 historical/current gameplay comparisons remain valid with the old-mask test receiving its own explicitly empty, hash-pinned annotations. Production validation was not loosened.

Whole-raster independent ImageIO oracle verifies all **12,800,000 cells**: 4,067,979 assigned; 8,732,021 unclassified gaps; zero approved exclusions. Accepted-manifest replay verifies the 95 pilot + 93,103 V2 + 4,915 final edits; **621 IDs / 622 components**, exact replay and protected controls preserved. Loader/lookup/concurrent atomic publication/failed replacement/reload, negative fractional boundaries, metadata bounds/malformed UTF-8/duplicate/overlap checks, player and hire observations, metadata replacement, HUD cache refresh, map borders, Build authority/cancellation and packaging tests passed.

Production artifact: `build/libs/KOME-LOTR-Addon-1.0.8.jar`, 15,287,819 bytes, SHA-256 `5bd7e0565481b92c8d0e442ed3b71f4f38760c028228e86c3c6071e199c54412`. Packaged mask and metadata equal source bytes; no base `lotr/` classes bundled. New class bytecode major version 52 (Java 8). **This artifact has not been installed, started, deployed or live-accepted.** No save/protocol/schema change.

Local self-review checked metadata lifecycle/array ownership, strict canonical overlap rejection, all resolution consumers, server/common side isolation, full-zone transition equality, HUD refresh, failed reload retention and packaging. The only full-suite regression was the historical-mask fixture hash mismatch; corrected and final gate passed. After restored authentication, the approved read-only Claude review completed at `cccb227`. It found no functional correctness defect. Verified documentation, populated-measurement, bundled-failure-test and evidence-traceability gaps were corrected; [assessment](claude-review-assessment.md) records every disposition. The final clean gate includes the new production classloader regression (missing/malformed metadata both initially and after a valid load). Historical [82-test focused results](focused-results.json) are explicitly labeled and superseded by the final gate. Fresh captured transcripts retain their complete lines; older transcript whitespace cleanup is historical and disclosed in PROGRESS.

## KOM-57 / KOM-60 / KOM-63 measured work

Opt-in reproduction (not part of test pass/fail):

```powershell
.\gradlew.bat -I docs/tile-geography-review-20261002/measure.gradle tileGeographyMeasure --no-daemon --console=plain
```

[Raw trials](measurements.txt) / [summary and limitations](measurement-summary.json). Current host: AMD Ryzen 5 7600X, 6 cores / 12 logical, 16,331,325,440 bytes RAM; measurement JVM OpenJDK 21.0.8, 512 MiB maximum heap; five trials per workload. Output code remains Java 8. Warm bundled reload median 321.30 ms (309.64-350.61); integer mixed-cell lookup 18.04 ns (15.98-23.39); floored double lookup 110.64 ns (109.58-143.85). Both production and populated fixtures cycle through the same seeded 8,192 spatial samples. Microbenchmark values include JIT/cache/current-host effects and do not promise game-session costs.

Exact primitive raster: 51,200,000 bytes (48.83 MiB). Production exclusion index: **zero bytes** while unclassified. Approximate post-GC production process heap: 58,757,736 bytes, including runtime/classes. The populated **benchmark-only** fixture explicitly annotates all 8,732,021 canonical gaps with one `benchmark-only` zone, 29,165 runs / 755,402 UTF-8 bytes; it never changes production resources or assigns real geographic purposes. Its index retains 25,600,000 bytes (24.41 MiB). Mixed positions include 5,573 classified samples / 8,192, plus tiles/outside cells. Populated integer lookup median 21.30 ns (20.97-25.94); double lookup 112.14 ns (109.97-114.41). Metadata parse median 25.02 ms (17.47-244.65; first populated-parser JIT included); complete fixture snapshot load 336.67 ms (328.49-347.80). Populated post-GC heap 135,539,160 bytes retains **both original and fixture rasters**, plus runtime/class state; it is not the single-snapshot footprint or a heap delta solely attributable to annotations. These measurements cover the stated dense one-zone fixture, not worst-case 4 MiB metadata/many zones, lower-spec hardware or whole-game cost. Approved future metadata needs its own measurement.

For 2,000 inert physical entities (100 players / 1,900 hires), 200 ticks/trial: stationary median 0.052 ms/tick (zero resolver reads/events after initialization); moving within tile 0.229 ms/tick (2,000,000 reads / zero transitions over five trials); alternating T401/T442 boundary 0.286 ms/tick (2,000,000 reads / 2,000,000 transitions). 100-entity workload also recorded. These execute real tracker code against test physical entities; exclude AI, chunk loading, network, server scheduling and ordinary game costs.

Headless border extraction median 103.71 ms (98.38-150.07), primitive cache 2,609,460 bytes, 99,816 edge runs and 60,474 fill runs. Border+highlight quad generation measured at 854x480 and 1920x1080, zoom 0.15/1/8 with panning. At 1920x1080 far zoom: median 0.763 ms (0.745-0.779), about 100,154 quads/frame. This measures CPU geometry only; **GL, GPU, actual FPS, cold first-opening and lower-spec acceptance remain unmeasured**. KOM-77 remains explicitly deferred/non-blocking; no first-opening optimization added.

## KOM-59 coordinate review

Open [review.html](review.html) in a browser (works directly from disk; no server/network required), select a region or type canonical mask X/Y and click Locate. [Full CSV](review-cells.csv) contains current IDs/gaps, half-open world bounds, historical concern and pending disposition. [Overview](overview.png) and 43 regional raster crops provide real LOTR-map context with canonical tile edges. The overview is downsampled and can hide individual-cell markers; use full regional crops/CSV for decisions. [Atlas summary](atlas-summary.json) records exact input/output counts. Highlight colors mean *review groups*, never geographic type.

26,731 evidence rows / **26,664 unique cells**: all 25,919 uncertain V2 additions, 48 withheld transfer cells, 691 withheld gap cells, the blocked 55-cell proposal, separate (974,727) optional transfer, 14 excluded pilot junctions and three protected controls; some groups overlap. [Historical provenance](historical-provenance.json) records exact original hashes, absolute source-docs root, owning checkout HEAD and extraction predicates. The large originals remain untracked in their preserved owning checkout; HEAD is context, not a content proof. [Row-level verification](provenance-verification.json) rechecks every hash and all 26,731 compact coordinates/groups/notes/source labels against those originals. [Compact inputs](review-inputs.csv) are committed for atlas reproduction. Browser controls verified for gap (2291,58), T001 (2292,58), retained T149 (974,727), and region pagination. No geographic decision was inferred from biome names/background colors. Optional further shape redesigns without an exact approved proposal remain out of scope.

```powershell
python docs/tile-geography-review-20261002/build-review.py
python docs/tile-geography-review-20261002/verify-evidence.py provenance
python docs/tile-geography-review-20261002/verify-evidence.py build
python docs/tile-geography-review-20261002/verify-evidence.py measure
python docs/tile-milestone-checkpoint-20260920/verify-geometry.py
```

Run `build` after the clean Gradle gate and `measure` after the opt-in workload; summaries derive from JUnit XML/JAR bytes and captured trial values rather than transcription. Per-suite XML hashes and complete transcript hashes are recorded. Use `provenance --historical-root <preserved-docs-directory>` if the originals move; their exact hash-pinned bytes are required. The scripts use the existing Python 3.11+ runtime. The atlas needs the existing Pillow/NumPy installation and the exact stock LOTR dependency JAR; it writes only this review folder. It does not regenerate geometry, scan worlds or overwrite historical audits. Protected controls and installed PNG hash are asserted. Geometry corrections require exact approved cell lists, topology analysis and a stopped-save reference impact review before application. This milestone has **no migration to apply**: no IDs/ownership/geometry/schemas/state changed.

## Exact remaining blockers / next acceptance

| Issue | Remaining decision/check | Concrete package and expected observation |
| --- | --- | --- |
| KOM-58 | Approved classification cells/types/reasons absent | Review atlas cells; supply explicit zone ID/type/reason and exact gap-cell runs. Existing tiles cannot be overwritten by annotations. Live exclusion crossing cannot be claimed while production classification is empty. |
| KOM-59 | 25,919 uncertain additions, withheld topology/banks/junctions, blocked 55, optional Weathertop transfer | Use atlas/CSV current identities and half-open world bounds. (974,727) remains T149 at world X [20992,21120), Z [-384,-256). Retain (2291,58) gap and (2292,58) T001 unless explicitly approved otherwise. A concern is not proof of error. |
| KOM-60 | Combined-candidate real multiplayer, populated physical hire lifecycle/respawn | In a separately authorized disposable environment with matching JARs and configured Middle-earth dimension: two non-operator players and relevant active hire cross T401/T442 at world X=237248.5, Z=87296 in both directions. Use actual safe terrain Y checked by staff. Re-query session/incarnation/observedTick in callbacks; stationary position suppresses CHANGED events. Exercise teleport, respawn/replacement, hire/unhire, chunk unload/reload, disconnect/reconnect, restart and configured dimension changes. Historical runtime reports do not accept this candidate. |
| KOM-63 | Live Build permission/cancellation/rejection and exact border/HUD interaction | In an authorized tile (actor ownership must be verified from live state), cancel Build confirmation and reject mismatched coordinates: no partial record/debit/success refresh. Verified T149/T132 boundary X=21120, Z=-383.5 is a spatial control, not permission to build. Verify hover/click/fills/borders at zoom 0.15/1/8, GUI Small/Normal/Large/Auto, odd 853x479 resize, F3+T, reconnect and HUD toggle. Automated cases passed; these actual-client outcomes remain unperformed. |
| KOM-57 / KOM-63 | Representative lower-spec initialization/memory/tick/frame impact | Rerun opt-in workload on target hardware and record CPU/RAM/JVM; separately capture actual game server tick timing/client FPS with populated world, panning/far zoom, reload/reconnect and multiple players. Current host synthetic results do not satisfy this hardware/manual gate. |

Use the configured Middle-earth dimension, not a hardcoded ID. Spatial diagnostic controls: gap (189568,-86016), T001 (189696,-86016), R1 gap (34944,640), raster outside (-103681,0); unsupported dimension must be different from the configured one. Do not teleport before verifying terrain height. Native waypoint alias limitations documented in `docs/KOME_SERVER_TILE_AWARENESS.md` remain separate. Other developers' company/recruitment ownership and the preserved KOM-77 branch are untouched. No issue status/assignee/dependency edits, push, merge or deploy.
