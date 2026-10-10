# KOM-57 / KOM-60 / KOM-63 acceptance — October 6, 2026

The isolated client/server now runs PR #26 plus the options-crash repair below.
The matching client reconnected and the requested server-side kill succeeded;
the user confirmed fresh T401 HUD after Respawn. Baseline checks retain their original
artifact limits. All three tickets remain open. No merge,
shared deployment, geographic repaint, in-world border feature or tile editor.

## Candidate and reused evidence

- Live GitHub reads: remote dev `b826ef35a0fb88ce9b2d58129ed307e9986b43d7`;
  PR #26 open/unmerged at `4b55b59e923028a9ed59233176e41235846efece`, incorporating
  that dev revision. Neither advanced beyond the supplied baseline.
- Primary local dev remains at `050048409b3769601027133b462f6ab80ed5888b`.
  This chat's detached checkout remains at `b826ef3`. Both were preserved.
- Read current Linear descriptions/comments for KOM-57/58/59/60/63 and existing
  consolidation, tile geography, border and KOM-60 integration reports first.
  Read applicable `C:/Users/dayne/.codex/AGENTS.md` and the available routing
  index/geography guide in the separate `2708` documentation checkout.
- Reused the exact PR source's recorded full gate: **2,777 discovered, 2,774
  passed, three optional original external-JAR skips, zero failures/errors**.
  The recorded seven Forge campaign launches are historical evidence for that
  source, not these new tile/human observations.
- Earlier immutable publication, concurrency, failed-replacement/resource-bound,
  whole-raster oracle, protected controls, gameplay parity, Build/packet/HUD and
  render lifecycle regressions remain dated automated evidence. Earlier Brodda,
  PR12 HUD/reconnect and GUI screenshots retain their original artifact limits.
- Initial baseline packaging: `build -x test`, offline, Java 17 Gradle / Java 8 target,
  two workers, 1 GiB Gradle heap, under `Local\KOME-Heavy-Validation`.
  BUILD SUCCESSFUL. No JUnit run occurred in this initial phase. Before the
  later repair, all archived source bytes remained identical; all 33 KOME
  resources match the packaged JAR; all 1,972
  packaged classes use Java 8 bytecode. See exact count in `results.json`.

## Artifacts and configuration

Initial Windows production KOME JAR, used for the baseline observations:
`87b5aad2b055206f64cf5818c8b2d4a1fee3fd2a9bbca609decb9b17acd921c2`.
This is a fresh build of the same source, **not** the earlier Linux artifact
`d74e9c60095382390b5a0d5e5f7be78b8d3be6e344bdc5eb8b83c810726303e3`.

- Stock LOTR 36.15: `4f296e749c0d4739ecf859217a526b4218a2a45a768c08d3d551af0d0d3d5635`.
- GeckoLib 1.0.4: `36d9d0471bfb55271f0d6649b586fe99608dad61261fac4065c008276021fe23`.
- Raster remains `ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`;
  3200×4000 cells; production exclusions contain zero zones/cells.
- Forge 10.13.4.1614; Temurin Java 8u482; protocol `1.0.9-integration-g4`;
  root schema 12, Build 4, population development 1, capital 1.
- New world `tile-acceptance-fresh`, seed `5716063`, loopback `127.0.0.1:51326`;
  online authentication and whitelist enabled; empty operator list; server view
  distance 4; server heap flag 1280 MiB, client heap flag 1536 MiB.
- New Prism instance `kome-tile-acceptance-pr26-20261006`, displayed as
  **KOME Tile Acceptance PR26 Options Fix** after the repair (originally
  KOME Tile Acceptance PR26 4b55b59). Existing instance/world files were not
  copied into its saves. Configurations were copied into new directories, then
  the candidate generated/bound its current configuration.
- Test-only server task bridge executes probes at FML tick START. Production
  packet queue, connection fencing and limits are unchanged. Probe agents check
  the exact disposable server/profile directory before operating.
- Exact configuration/mod/runtime hashes and graphics settings are in
  [results.json](results.json). Raw observations are in
  [runtime-evidence.txt](runtime-evidence.txt).

## New observed passes and measured results

1. Production dedicated startup, save/stop and two cold starts succeeded.
   Native dimension configuration is Middle-earth 100, Utumno 101.
2. Native resolver: T401 at `(237248.5,87295.5)`, T442 at
   `(237248.5,87296.5)`; R1 remains an unclassified gap; outside-mask and
   Overworld are distinct. Console controls confirm canonical cell `(2291,58)`
   is a gap and `(2292,58)` is T001.
3. Native hired-NPC fixture: initialization, 218 stationary sampling ticks without
   duplicate transition, T401→T442, immediate stale-position fencing and fresh
   callback tokens. Real chunk unload returns NOT_TRACKED; native chunk reload
   restores the same UUID with a new incarnation.
4. Native active=false dismissal and `Entity.setDead` removal both clear the
   observation. Stopping with 19 tracked hires emits removals and leaves running
   false, zero entries/listeners/notifications and an inactive subscription.
   Cold restart reloads exactly those 19 active UUIDs. The dismissed NPC persists
   inactive; the removed NPC is absent. Benchmark fixtures/tickets were cleaned
   before manual testing.
5. Supported bundled reload succeeds and atomically replaces the view. Persisted
   KOME root comparison changes only `DailyJournal.LastObserved` and one appended
   `CONFIG/WORLD_BOUND` audit. Existing serialized authorities remain equal.
   These fixtures create **zero strategic hired-unit records**: they do not
   establish campaign hiring, payment, company membership or strategic arrival.
6. Five separate 100-tick windows, 21 native hires with native AI/position updates,
   25 forced test chunks and **zero connected players**: mean **2.117–2.574 ms**,
   median window mean **2.352 ms**, maximum sampled tick **44.735 ms**. Unpinned
   idle means were 1.672/2.068 ms; the pinned initialization window includes a
   218 ms chunk-load spike. These are total server timings; the changed workload
   does not isolate the incremental cost of tile tracking or prove multiplayer
   capacity. Full per-window heap/timing data is retained.
   Five further 100-tick windows with **one stationary connected player**, zero
   fixture hires and no forced test chunks averaged **1.276–2.052 ms**, median
   window mean **1.295 ms**, maximum sampled tick **52.934 ms**. The client was
   on the pause menu. This is a single-player idle workload, with no isolated
   awareness-cost or multiplayer capacity claim.
7. Actual connected client: authenticated join and fresh PLAYER initialization in
   Overworld; normal character creation then produces a fresh DIMENSION_CHANGED
   callback into Middle-earth T091. Both callbacks query AVAILABLE with matching
   session/incarnation/sample tokens. Current character selected Dale.
8. Thirty actual client FPS samples: **111–120 FPS, median 120**, during
   `GuiRaceSelection`, 854×480, GUI scale setting 3, render distance 12, VSync on,
   FPS cap 120. This measures that screen only. No map/world/far-zoom or lower-spec
   FPS acceptance is inferred. Host: Ryzen 5 7600X / RTX 4060 / about 15.2 GiB
   usable RAM; approximately 623 MiB free when both candidates were running.
   A later read-only 60-sample run measured **120 FPS** on `GuiIngameMenu`,
   3840×2054, with the same GUI scale/render-distance/VSync/cap settings.
   World FPS remained unmeasured; later current-map sampling is recorded below. Window size at the earlier human HUD checks
   was not independently captured. Per-sample dimensions/settings are retained.
9. User confirmed the non-operator command `/conquest resolve 100 189568 -86016`
   returns **“You do not have permission to use this conquest admin command.”**
   The client chat log independently records the exact denial at 20:52:18 CDT.
   This accepts that staff command only, not Build permissions.
10. User confirmed **T401→T442 with no “No tile” flash** while walking south
    across the prepared boundary. The production PLAYER transition at 20:57:33
    CDT independently confirms exact-cell T442 with fresh callback tokens.
    This accepts this boundary at the current graphics configuration.
11. User confirmed **T442 returns correctly** after disconnect/reconnect.
    The production callback clears the old player at 21:00:46 CDT and initializes
    the same UUID in T442 with incarnation 21 (previously 20) at 21:01:02 CDT.
    This accepts one stationary connected-client reconnect.

Fixtures are actual native Angmar Hillmen assigned owner/task/active state through
native APIs to an offline FakePlayer UUID. They are not a normal player's hire
transaction. Natural movement and explicit test relocation are distinguished in
the transcript. Helper failures (obsolete queue signature, unregistered chunk
callback, unloaded spawn and obsolete teleport signature/syntax) were corrected
in disposable tooling; they were not classified as product defects. No production
tile-service fix was needed from these baseline checks. A later client crash
blocked further menu/map acceptance and was repaired as described below.

## Client options crash and scoped repair

At 21:09:32 CDT the real client crashed while opening the LOTR menu, whose init
loads `LOTRGuiOptions`. The options transformer recognized MCP development names
only; the production LOTR JAR exposes SRG `func_73866_w_` and `func_146284_a`.
It threw `expected one init hook, found 0`, surfaced as NoClassDefFoundError.
The transcript is retained in [options-crash.txt](options-crash.txt).

The repair accepts those two SRG names alongside the existing MCP names. Exact
descriptor/count guards and idempotence remain intact. The new regression reads
the actual stock production JAR rather than the remapped test classpath. It
failed before the fix (one of two tests), then the focused options/title,
character isolation, Build interaction and HUD/adapter gate passed **49/49,
zero skips/failures/errors**. Packaging passed under the heavy-validation mutex.

Current matching client/server KOME SHA-256:
`9b267eb7c19f5cdd5f60a64c6db4927fb991bd640aaa27ab40e6726517c4ef61`.
The only changed packaged entry versus the baseline JAR is
`kome/core/KOMEFactionTitleOptionsTransformer.class`; every other entry,
including map resources and tile-service classes, is byte-identical.
All 1,972 classes still target Java 8. Source base remains PR #26 `4b55b59`.

The real restarted client's LaunchClassLoader successfully loaded and linked
the repaired options class/bridge at 21:19:09 CDT. This verifies the former
class-load crash. After Respawn, the user pressed L and confirmed **“Menu opens
normally”**, accepting the original menu-opening crash trigger. Individual
options button interaction remains unverified.
The user then selected Map and confirmed **“Map and tile borders appear
normally”**. Opening and visible border rendering are accepted on the repaired
candidate; hover/click alignment and other zoom/scale/resize combinations remain
separate checks.

After the repaired map opened, 60 actual client-thread DebugFPS samples at
map zoom **8.0**, **3840×2054**, GUI scale **3**, render distance **12**, VSync
on and cap **120** measured **120 FPS throughout** (21:25:45–21:26:47 CDT).
Exact settings, heap ranges and samples are in [map-current-fps.json](map-current-fps.json).
This is measured current-map performance on the recorded Ryzen 5/RTX 4060 host,
with one player, no fixture hires and no forced test chunks. Cursor movement was
not independently instrumented. It does not prove uncapped throughput, frame-time
percentiles, far-zoom performance or representative lower-spec acceptance.
Reviewable source/test changes are in [options-srg-fix.patch](options-srg-fix.patch).
Exact artifact, test reports and patch hash are in [fixed-candidate.json](fixed-candidate.json).
The correction is confined to the isolated candidate; shared branches/PR heads
were not changed.

The user reported `/kill` permission denial, corroborated by client chat at
21:08:26 CDT. This vanilla non-operator dispatcher restriction is documented by
`KOMEPublicCommand`; the instruction to use `/kill` was unsuitable. No death or
respawn pass was inferred. An attempted fallback teleport found the player
already disconnected and did not relocate them.

On the repaired matching candidate, a directory/UUID/dimension-guarded one-shot
test probe invoked vanilla `CommandKill` on the server thread at 21:18:15 CDT,
with `admin=false`. The client reported death; production awareness removed the
player as `NO_LONGER_RELEVANT`, exposing NOT_TRACKED with no stale observation.
This setup uses the native command without changing the account's operator state.
Persisted spawn data is verified at T401 `(237248,181,87295)`, forced, dimension
100. At 21:21:25 CDT, production awareness initialized the respawned player in
T401 with incarnation 3, tick 4705, AVAILABLE and matching callback tokens.
The user confirmed **“T401 appears correctly”** after clicking Respawn. This
accepts this same-dimension death/respawn across T442→T401 on the repaired
artifact; it does not establish other dimensions or multiplayer behavior.

## Ticket closure requirements

On the repaired candidate, five separate 100-tick windows with one connected
player measured mean tick times of **1.795–2.297 ms** while idle and
**2.110–2.534 ms** with native hired-NPC fixtures. The median of window means
changed **1.909→2.345 ms (+0.436 ms)**. Tracked NPC counts at the five populated
snapshots were **21, 21, 20, 20, 18**; three native fixtures had died by the final
query. All 21 fixtures were then removed and only the real player remained
tracked. No extra chunks were forced. This measures the combined entity/AI/mod
workload, player activity and instrumentation; it does not isolate tile-service
overhead or prove multiplayer/lower-spec performance.

During that sample the user changed screens. At zoom **1.0**, 15 current-map
FPS samples measured **74–103, median 84**; two world samples measured **120**;
41 chat samples measured **119–121, median 120**. All used 3840×2054, GUI scale
3, render distance 12, VSync on and cap 120. These short grouped samples are
measurements, not sustained far-zoom/world or human alignment acceptance.
Exact windows, timestamps, tracked counts, heap values, graphics configuration,
tooling hashes and limitations are in
[connected-populated-performance.json](connected-populated-performance.json).

The repaired authenticated non-operator client submitted a real `create` packet
for T091 with coordinates resolving to T442 at 21:33:55 CDT. The production
server rejected it with “The selected coordinates are not inside the confirmed
conquest tile.” Native server-thread `KOMEWorldData.writeToNBT` snapshots before
and after compare **all 77 serialized sections unchanged**, including Builds,
NextBuildSequence, foreign permissions, progression and audits. The actor stayed
non-operator. Exact request, artifacts, tooling hashes and native snapshots are
in [build-coordinate-rejection.json](build-coordinate-rejection.json).
This accepts this packet rejection and lack of serialized partial mutation;
human GUI cancellation, valid creation and other permission paths remain pending.

| Ticket | Accepted evidence now | Still prevents closure |
| --- | --- | --- |
| KOM-57 | Reused immutable/O(1)/bounds/concurrency and synthetic measurements; native-hire and connected idle/populated server timings; actual menu and zoom-8 map FPS, short zoom-1/world measurements | Representative lower-spec initialization/heap/lookup/FPS, sustained world/far-zoom workload and multiple-player performance; isolated awareness overhead remains unmeasured |
| KOM-60 | Current native physical initialization/crossing/freshness, unload/reload, dismissal/removal, populated stop and cold reload; real player initialization, one creation dimension change, user HUD walking boundary/reconnect and one same-dimension respawn | Normal player hire/recruitment lifecycle and campaign-unit movement/arrival/restart; broader dimensions/HUD; multiple real players; representative performance |
| KOM-63 | Reused whole-raster/Build/render automated evidence; new native controls/restarts/matching handshake; observed lifecycle subset, walking HUD/reconnect/respawn and non-op staff denial; repaired production options class-load crash; real authenticated non-op Build coordinate rejection with all 77 serialized sections unchanged | Border/hover/click/fill alignment at zooms/GUI scales/odd resizing/F3+T; human Build create/cancel, other rejection/access paths and persisted populated Builds; public/private access and staff help/completion permissions; broader dimensions and full hire/multiplayer/hardware acceptance; geographic decisions below; documented live route/recovery/barrier checks |

KOM-58 still needs approved exact cells, zone types/reasons and live classified
exclusion crossing. KOM-59 still needs dispositions for uncertain coverage,
topology-unsafe transfers and mountain contacts; 25,919 uncertain V2 additions
are not established errors. Harnen T455/T654 remains unreproduced in the audited
baseline and needs live IDs/version evidence. Raster contact does not establish
strategic adjacency or legal movement. No exclusions or geographic changes were
invented to make acceptance pass.

## Manual test state and handoff

User observations are recorded separately in
[human-observations.json](human-observations.json). The next test is the deliberate
right-click selection matching the hovered tile ID. The character was placed by staff test setup at
`(237248.5,181,87295.5)` in dimension 100, facing south; this setup is not normal
travel acceptance. The platform is confined to the disposable world.

The user completed the boundary walk and reconnect successfully. Staff set the
test character's spawn at `(237248,181,87295)` on the disposable platform.
The server executed the native kill and the user completed Respawn with fresh
T401 HUD, then confirmed that L opens the LOTR menu and Map shows tile borders.
The user confirmed **“everything looked good”** for the guided hover/fill/tooltip
alignment test. Nearby native samples recorded map zooms 8.0 and then 1.0 at GUI
scale 3 and 3840×2054; the exact zoom at the human observation was not separately
captured. This accepts the observed hover behavior, not every zoom/scale/resize.
Next: note a tooltip tile ID well inside its tile, then right-click the same spot.
Expected: the Tile Command screen opens for that same ID and stays connected.
Record the user's result before advancing.
Do not silently mark remaining rows passed.

Full local candidate/source/world/backups/controllers are retained under
`build/tile-acceptance-20261006`. The running controller owns
`Local\KOME-Heavy-Validation`; stop this task's server before other heavy work.
For a graceful stop, place a file containing `save-all` then `stop` in
`build/tile-acceptance-20261006/commands-fixed/` with extension `.pending`.
The new Prism profile can be closed normally. Existing worlds/runtimes must not
be stopped or edited as part of this handoff.

Preservation verification and exact limitations are recorded in
`preservation-verification.json`. No ticket status, branch, stash, merge or shared
deployment changes were made.

Final GitHub readback again confirms the same PR #26 head/base and remote dev.
Protected-state checks passed for all 25 existing worktree HEAD/status snapshots,
user branch/tag/remote refs, stash, five existing server runtime file inventories, and the original
Prism template profile. Runtime/profile comparison checks paths, sizes and UTC
modification timestamps; it is not a complete initial byte-hash audit.
The app added/replaced internal `refs/codex/turn-diffs` snapshots between turns;
those exact differences are retained separately in the preservation report.
