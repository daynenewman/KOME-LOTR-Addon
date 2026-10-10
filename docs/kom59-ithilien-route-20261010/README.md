# KOM-59: T351/T352 route correction

Base dev: `ff5053e56990dc601a1f0fe22d9e26e3affc1da0`. This is a user-authorized, single-edge default correction, not approval of the remaining KOM-58/59 geography proposals.

## Cause and exact scope

The raster supplies two real shared-border segments, but the independent packaged route graph marked `T351,T352,river`. The actual command BFS consequently rejected the direct edge and could use the two-hop `T352 → T358 → T351` path when territory permissions allowed it.

| Raster contact | World boundary (X/Z, 128 blocks per cell) |
|---|---|
| T351 `(1431,1163)` / T352 `(1431,1164)` | Z 55552; X 79488–79616 |
| T351 `(1430,1164)` / T352 `(1431,1164)` | X 79488; Z 55552–55680 |

The [historical classifier](../gameplay-separation-implementation-20260919/original-KOMEConquestTileDefaults.java.txt) tested decorative overlay colors with `blue > 80 && blue > red + 18 && blue > green + 5`, without native biome identity. Lavender `(202,192,239)` near the contact passed as water. Read-only reproduction matched the [historical derivation](../gameplay-separation-implementation-20260919/original-derived.tsv): two of three gap-scan samples were river and none bridge, freezing the river flag. The native LOTR v36.15 map identifies the actual contact cells as Nindalf and Ithilien Wasteland, not named river or mountain biomes. This does not prove generated terrain is dry or traversable. All three contact cells remain `river_bank_uncertainty` in the [geography atlas](../tile-geography-review-20261002/review-cells.csv).

Only `T351,T352,river` becomes `T351,T352,open`, and its automatic river marker at world `(79488,80,55424)`, map `(1431,1163)`, is removed. Marker Y=80 is historical display data, not a measured surface height. No other routes are regenerated. The gameplay manifest revision is `baseline-pilot-20260919-kom59-t351-t352-20261010`; current route/marker checksums and the explicit original-output delta are in [provenance](../../src/main/resources/assets/kome/config/kome_tile_gameplay_provenance.json).

## Persistence and authority

Tile IDs, raster, mapping, routing references, arrival coordinates, waypoint candidates, ownership defaults, packets and schemas are unchanged. Saved `RouteEdges` overrides remain authoritative; river/blocked overrides still block the direct edge after NBT reload. Removing an override explicitly reveals the new open default. Existing bridge/pass overrides are not migrated or removed. Physical Minecraft bridge blocks do not automatically authorize strategic passage.

New authorized routes can use the one-hop edge in either direction. Origin/destination permissions, native military passage and hostile-terminal authority remain unchanged. Existing queued `T352 → T358 → T351` routes retain their stored path, indices, timing and destination through reload; installation does not automatically replan orders or move entities.

Actual generated terrain, the reported server's running artifact, saved route overrides and player/company permissions remain unverified. Nearby river/mountain decisions and unresolved geography cells remain outside this patch. Multiplayer is deferred. Acceptance runtimes on 51326 and 51327 are not replaced or tested.

## Regression coverage and evidence

[Focused production-path tests](../../src/test/java/kome/common/data/KOMEIthilienRouteCorrectionTest.java) exercise the actual command route search, effective-edge/departure authority and transactional WorldData NBT save/load: direct routes both ways, retained river/blocked overrides, normal origin/destination permissions, unchanged queued detour, and absent automatic marker.

Both existing parity comparisons apply [one checked delta](../../src/test/java/kome/common/data/KOMETileGameplayParityTest.java) to the immutable historical 6,055-row golden: exactly one complete edge row changes and exactly one complete marker row is removed. All 6,054 resulting rows compare strictly, under both baseline and current raster. Historical input files, golden snapshot and baseline exporter are intact; the exporter still produces the historical baseline and must not overwrite current corrected assets.

The [validation record](validation.json) contains exact source/resource/artifact hashes, commands, outcomes, retained failures and evidence limits. No Forge launch, human acceptance, multiplayer pass or actual-world migration is claimed by JUnit save/load regressions.

Final focused gate: **32 passed, zero failures/errors/skips**. One clean `test build`: **3,090 tests, 3,085 passed, five skipped, zero failures/errors**. Routing checker: **25 documents, 746 local links, 920 symbol references, 48 exact JUnit selectors; PASS**. Working/staged/base-to-head whitespace checks pass. Both built JARs contain exactly the corrected resources and unchanged raster. [Archived evidence](evidence.zip) retains failed attempts, final logs and JUnit XML. The two failed focused runs were invalid fixture timestamps/owner identity, corrected without production Java changes. The routing failure was a backticked SHA parsed as a symbol; formatting was corrected without changing the checker. Five skipped tests remain separate from passes.

| Scoped requirement | Result and evidence |
|---|---|
| New authorized direct routes both ways | PASS — actual command BFS and departure guard, before/after NBT reload |
| Saved river/blocked overrides | PASS — exact metadata retained; direct departure blocked both ways after reload; explicit override removal restores open default |
| Normal permissions | PASS — neutral origin/destination deny passage despite open edge |
| Existing queued detour | PASS — canonical owned order retains its complete serialized fields through two reloads |
| Automatic river marker absent | PASS — 318 remaining river markers, 58 bridge markers; all other parity rows remain exact |
| Protected state | PASS — 26 existing worktree heads/branches/statuses, local heads/stash, 109 recorded runtime file hashes and four controller/server start times unchanged |
| Actual terrain/server state and multiplayer | UNVERIFIED / DEFERRED — no new live claim |
