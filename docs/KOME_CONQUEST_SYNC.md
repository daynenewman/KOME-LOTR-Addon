# KOM-74 conquest synchronization

## Review status (2026-09-27)

Prepared as a synchronization-only review on
`review/kom-74-conquest-sync-20260927`, based on validated checkpoint
`343c135431867df1dff8b757221e9a9e58b4f161`. Freshly fetched `origin/dev` remains
`38713a4a2559b0e80d6b96756cf47adcebb80200`; no reconciliation is required.
Production source, resources, tests and measurement tools are identical to that
checkpoint. Only documentation/evidence is added here. Reuse the 1,105 passing
tests, two existing symlink skips, 72 focused tests and final 16-test source-sync
rerun; no unchanged full build was repeated. The dated sections below describe
historical checkpoints; this section and the final review checks supersede their
pending-state wording.

KOM-77 map-opening work is excluded from this review. It is preserved locally on
`dayne/kom-77-map-opening`: run-cache commit `c147e1914b59f47595e5c33448c53d2eb2b85695`
and pixel-conversion checkpoint `01c171ded13b16b48cbe224c984df9b77a5bc334`.
The user accepts the remaining first-opening pause as non-blocking and deferred
further optimization. That does not establish complete visual acceptance.

## Scope and source of the redundant work

Baseline: `origin/dev` at `38713a4a2559b0e80d6b96756cf47adcebb80200`.
The old roughly 440 handler calls/second report is historical, not a current
network measurement. Current dev's hired-unit live updater already publishes
conquest data only when authoritative cost/mounted state changes. There is no
periodic full-map polling request from the map GUI.

Current triggers include login/company reconciliation, hire/removal, movement
state changes, ownership/Build/population/route/waypoint mutations, and delayed
waypoint initialization. Login reconciles companies (broadcast), then requests
a second snapshot. Delayed waypoint initialization previously visited the same
canonical WorldData through each loaded dimension and broadcast it repeatedly.
Every request previously projected/compressed/sent the entire snapshot, including
unchanged sections. A separate, recipient-filtered physical unit-marker stream
runs approximately once per second; this change does **not** change that stream,
its eligibility rules, or its cadence.

## Publication contract

- `KOMEWorldData.syncConquestTiles()` calls
  `KOMEPacketConquestData.sendIfChanged(data, player)` per connected player.
- The existing public projection is built once per request/recipient, then
  compared by exact NBT value equality against the recipient's last dispatch.
  There is no mask scan, probabilistic hash, dirty-flag shortcut, or time delay.
  Projection/comparison still costs O(projected records); unchanged requests
  avoid compression, network traffic, client decoding, and publication.
- Any changed field, removed row, zero value, or marker visibility change sends
  a **complete** ordered reset/continuation/completion sequence. We do not send
  partial deltas or change the wire schema. Empty state sends reset+complete.
- The chunker uses the same constructor/projection as local DTO users, removing
  the formerly duplicated eight-section projection implementation. Each chunk
  receives copied NBT, independent of both world records and the private cache.
- The cache uses player **object identity** (Minecraft entity equality is based
  on entity IDs). Context also includes connection, world and WorldData object
  identity, dimension, operator eligibility and pledge. It never shares a
  recipient's sent-state decision with another player.
- Logout, server session reset and both stopping/stopped hooks clear cache state.
  The cache is bounded to 256 recipients. Eviction can cause an extra full send,
  never suppress a required send. Cache contents are not saved.
- `sendChunked(data, player)` and `syncConquestTiles(player)` remain forced full
  resynchronization APIs. Respawn and dimension-change hooks use them because
  client world unload resets the incoming accumulator. Login uses the conditional
  path after reconciliation; a new connection always gets its initial baseline.
- Failed synchronous dispatch discards the sent baseline so the next request
  starts with a complete reset. As before, dispatch is not a client application
  acknowledgement. Network failure requires reconnection; exceptional client
  queue overload remains logged, with explicit resynchronization/reconnection
  available. This task adds no acknowledgement protocol or retry timer.
- All source projection/cache work runs on the existing server-thread call paths.
  No worker, additional periodic scan, debounce latency, or client packet is added.

The conquest snapshot's current projection is public and recipient-independent;
private alliance/GUI records and per-player physical markers keep their existing
separate filters. Op/pledge context changes invalidate even an equal public
snapshot on the next requested publication. If future conquest fields become
recipient-specific, filter **before** projection comparison; never reuse another
recipient's private data. Resource reload retains the client authoritative data
and only rebuilds rendering caches; reconnect and world replacement reinitialize
publication. Geometry, gameplay defaults, company policies, schemas 5/4/1/1 and
protocol g2 are unchanged. Existing ownership-color, population, waypoint,
immutable client handoff, ordering and bounded-queue fixes remain intact.

## Automated validation

The source-level structural wiring assertion now checks the conditional broadcast
entry point; forced recipient refresh coverage remains. New tests exercise the
production projection/chunker and registered respawn/dimension/logout handlers:
identical snapshots, ownership/population/zero/waypoint/company/movement/route
changes, explicit removals, marker visibility, independent recipients with equal
entity IDs, op/pledge changes, replacement/world/data identity, reconnect/session
reset, forced refresh, NBT isolation, dispatch failure and bounded cache eviction.
Existing publication, queue-bound, population/waypoint and public-access suites
are retained. Focused run: **72 passed, no failures/errors/skips**.

Final `clean test build`: **1,107 discovered, 1,105 passed, two skipped,
zero failures/errors**. The two existing custom-skin symlink tests skip when
symbolic links cannot be created in this Windows environment. Full XML evidence
is archived at `KOME-Validation/kom74-20260926T215215Z-9fc9b2/full-test-results.zip`.
A final test-only refinement restores the original claim timestamp as well as
ownership in the failed-dispatch/revert fixture; all **16** source-sync tests
passed again afterward. Production source/artifact was unchanged; no redundant
full rebuild was run. Whitespace and the complete added/modified file set were
reviewed; no geometry, defaults, schema or protocol resources changed.

## Reproducible bounded measurement

`tools/kom74/measure.gradle` is an opt-in init script; normal builds do not run it.
`KOMEConquestSyncMeasurement` reads a **copy** of a freshly saved, schema-compatible
`KOME_ServerRules.dat`, uses the production projection/codec/handler and completed
client snapshot publisher, and transports the encoded bytes over a real localhost
TCP socket. It changes ownership only in its in-memory copy, never a live world.

Two phases each request 100 broadcasts for one recipient, after ten forced full
warmup publications: identical requests, then ten deterministic owner changes
interspersed with ninety redundant requests. Both runs use the same saved file,
JVM heap, warmup, changes, observer and receiver. This is a tight controlled
publication workload, **not** 100 game ticks or an observed map refresh rate.

For baseline, export the packet source at the base commit to an external file:

```powershell
git show 38713a4a2559b0e80d6b96756cf47adcebb80200:src/main/java/kome/common/network/KOMEPacketConquestData.java
```

Save that output as UTF-8 `KOMEPacketConquestData.java` outside production sources.
Run from this worktree (substitute external absolute paths):

```powershell
.\gradlew.bat -I tools/kom74/measure.gradle measureConquestSync -PmeasurementBaselineSource=<exported-java> -PmeasurementWorld=<saved-copy> -PmeasurementOutput=<before.txt> --no-daemon --console=plain
.\gradlew.bat -I tools/kom74/measure.gradle measureConquestSync -PmeasurementWorld=<same-saved-copy> -PmeasurementOutput=<after.txt> --no-daemon --console=plain
```

The first command compiles the exact old sender into an isolated build directory
and puts it first on the measurement classpath; neither production source nor
installed resources are replaced. Reflection selects the broadcast entry point
available in each version. The new forced API permits identical codec warmup.

`payloadBytes` counts actual codec output; `tcpStreamBytes` includes the harness's
four-byte length framing. Neither is a packet-capture measurement of encrypted
Forge/FML traffic, TCP/IP headers or retransmissions. Handler calls and completed
snapshots are receiver counts, not inferred from bytes. Sender CPU excludes the
receiver wait; encode/decode/handler costs are instrumented wall durations.
Request-to-publication latency includes loopback scheduling and the completed
snapshot application. Receiver runs on a test thread, without Minecraft graphics;
these numbers cannot establish live client frame cost or low-end performance.
Windows CPU timer granularity, JIT/GC and other running processes limit timings.

## Matched results (2026-09-26)

Windows host, Java 17.0.19 measurement JVM, `-Xmx768m`, one simulated recipient,
real localhost TCP transport, no Minecraft renderer. Disposable runtime itself
uses Java 8 and the production Forge artifact. The fresh-world fixture SHA-256 is
`c200e9a9b54479a589fd0a1edb7fb51a6550b86b491e1f05563fee6301286450`.

| 100 requested broadcasts | Before unchanged | After unchanged | Before 10 changes | After 10 changes |
|---|---:|---:|---:|---:|
| Codec packets / handler calls | 2,100 | 0 | 2,100 | 210 |
| Completed client snapshots | 100 | 0 | 100 | 10 |
| Encoded conquest payload bytes | 3,242,200 | 0 | 3,243,010 | 324,301 |
| Measured TCP stream bytes (harness framing) | 3,250,600 | 0 | 3,251,410 | 325,141 |
| Sender-thread CPU, ms | 1,359.4 | 187.5 | 1,265.6 | 296.9 |
| Encode wall cost, ms | 1,317.8 | 0 | 1,185.1 | 142.5 |
| Receiver decode wall cost, ms | 329.5 | 0 | 309.0 | 45.3 |
| Receiver handler/publication wall cost, ms | 112.9 | 0 | 96.7 | 13.7 |
| Total workload elapsed, ms | 1,536.3 | 180.7 | 1,353.8 | 313.9 |

All ten actual owner changes arrived; their mean request-through-publication
latency was **13.47 ms before / 17.99 ms after**, maximum **14.22 / 26.20 ms**.
The measured per-change latency did not improve; this small single-run benchmark
includes JIT, copies, comparison, scheduling and CPU-timer granularity. There is
no intentional delay/debounce. The demonstrated benefit is fewer redundant
publications (100% fewer after an unchanged baseline; 90% fewer in the specified
mixed workload), not faster real changes or an FPS claim. One complete refresh
remains 21 chunks and roughly 32.4 KB of encoded payload per player. Projection
and comparison remain per-recipient work; these are not multi-player load tests.

Raw rows: `tools/kom74/measurement-results.txt`. The initial exploratory runs are
retained outside Git in the disposable directory; the table above uses only the
matched runs with equal forced warmup and deterministic timestamps. Native Forge
wire bytes and actual stationary/panning map costs have **not** been measured in
this task: no player connected to the new environment during collection.

## Disposable runtime and remaining acceptance

Only the new `KOME-Validation/kom74-20260926T215215Z-9fc9b2` server/profile is used.
It has a fresh compatible world, loopback `127.0.0.1:51763`, online mode enabled,
and an empty operator list. Existing Brodda/PR12 profiles, worlds and runtimes are
not used, stopped or modified. Prior user acceptance belongs to prior candidates.

Live idle-map and active-update native network measurements, interactive frame
cost and visual acceptance remain **pending a connected player**. No live FPS,
map visual or multiplayer improvement is claimed from the controlled benchmark.
The separate one-second marker stream remains a potential measured follow-up,
not an assumed cause of the historical rate.

Retest on **KOME KOM74 sync disposable**:

1. Connect to `127.0.0.1:51763`, open the conquest map and leave it still briefly
   for a native traffic/handler capture.
2. Pan/zoom, hover an eligible tile and verify ownership colors, borders and
   population/waypoint rows remain correct. Arrange one authorized disposable
   ownership/population change and verify immediate complete publication.
3. Disconnect/reconnect, then perform a dimension transition/respawn and confirm
   map information reloads. Keep the player non-operator.

KOM-74 stays In Progress until remaining live measurements/acceptance are recorded.
KOM-46's resolved KOM-60 dependency is not changed.


## Candidate runtime evidence

Production/reobfuscated artifact: `build/libs/KOME-LOTR-Addon-1.0.8.jar`.
SHA-256: `eab9872461259a89a22baa4fd0f8bb43848f836e64b0933f0d80a4a216a3f130`.
Both new disposable client/server copies match. The baseline server had zero
players, its process working directory/listener were verified, and save/stop
exited 0. Backup `backup-before-candidate-20260926T221840Z` under the disposable
root contains 81 world/configuration files (3,097,719 bytes), verified against
SHA-256 manifests both after copying and immediately before replacement.
The operator list remains empty; copied configuration/EULA decisions were
preserved. The candidate read the same fresh schema-compatible world and reached
`Done` at 17:22:14 local time, listening only on `127.0.0.1:51763` with online mode
true. No new KOME linkage/resource/initialization error was found. Baseline and
candidate both have the existing legacy Forge signature/version-check warnings
and unresolved stock waypoint-alias warnings; these were not repaired here.
The Prism profile is prepared but was not launched or visually accepted.

The installed tile mask remains
`ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`.
No established runtime/world, other worktree, prior audit or protected stash was
modified. Nothing was pushed, merged, published as a PR, or deployed to the shared
server. The local checkpoint includes only this scoped implementation, tests,
measurement tool/results and this report.


## Connected idle observation (2026-09-26, after checkpoint)

`_Danye_` connected to the matching KOM74 profile/server, non-operator. A
synchronized 30-second capture kept the same map position (1057.5039,825.5039)
and zoom1.0. It recorded **zero conquest requests/chunks/handler calls** and
**29 unit-marker updates /116 encoded bytes**. Actual encrypted stream totals
matched at sender/receiver: **33,835 server-to-client bytes** and **27,406
client-to-server bytes** across all game traffic. These exclude TCP/IP headers
and retransmissions; Netty buffer calls are not packet counts. Client marker
decode/handler wall costs totaled0.1581/0.0824ms; game-loop median/p95 were
13.30/15.06ms, including frame pacing and instrumentation. No ownership-texture
rebuild occurred. This is an idle observation, not before/after FPS acceptance.

A subsequent read-only refresh-request experiment was interrupted by a bandit
killing the player: the capture began on the death screen and ended on chat.
It is excluded from stationary-map comparisons. No forced-reference comparison,
meaningful gameplay-change test or visual acceptance is claimed. The ten
probe-issued requests did not change the sampled dirty flag; that does not
establish absence of unrelated background mutations. No permissions, progression
or gameplay records were edited by the observer.

Both processes' original method bodies were restored and verified to contain no
observer calls. No restart/artifact replacement was needed. External raw evidence,
observer source, cleanup verification and caveats are retained in
`KOME-Validation/kom74-20260926T215215Z-9fc9b2/live-sync/README.md`.
The earlier native-measurement limitation above now remains only for the matched
active/forced-reference comparison. Arrange a safe player location before the
next stationary capture. KOM-74 remains In Progress.


## Sheltered stationary live comparison (2026-09-26)

Player _Danye_ stayed in Middle-earth at 21276.5,66,19564.5, non-operator.
Read-only inspection before capture found health20, hurtTime0, enclosed solid
floor/walls/roof and no entities within the checked 8-block horizontal/4-block
vertical box. This is a point-in-time safety observation, not invulnerability.
All three synchronized 15-second captures started and ended on LOTRGuiMap at
posX1057.5039,posY826.5039,zoom1.0. Same client/server artifact and instrumentation.

| Measurement (15 seconds) | Idle | 10 conditional requests, 1 Hz | 10 forced full requests, 1 Hz |
|---|---:|---:|---:|
| Conquest encoded/decoded chunks and handler calls | 0 | 0 | 210 |
| Conquest payload bytes | 0 | 0 | 324,260 |
| Native encrypted stream server-to-client bytes | 18,851 | 17,990 | 345,442 |
| Native encrypted stream client-to-server bytes | 13,698 | 13,533 | 13,606 |
| Source send-method cumulative wall time (ms) | 0 | 19.924 | 191.4222 |
| Client conquest decode/handler cumulative wall time (ms) | 0/0 | 0/0 | 29.3917/19.913 |
| Ownership texture rebuilds | 0 | 0 | 0 |
| Game-loop median/p95 (ms) | 13.2863/15.1014 | 13.4168/15.3323 | 13.3336/15.2945 |
| Overlay cumulative wall time / calls | 6352.3515ms/1117 | 6299.192ms/1102 | 6295.4487ms/1109 |

Native byte totals match sender and receiver exactly; they include ALL Minecraft
traffic, excluding TCP/IP overhead, ACKs and retransmissions. Buffer call counts
are not packet counts. Source timings include nested codec work; do not add them
to codec totals. Frames include pacing and observer overhead. Overlay timing
uses the verified production onDrawMap method in this capture. Marker updates
remain 15/14/15 respectively (60/56/60 encoded bytes).

This comparison exercises the current production conditional path versus its
forced full-publication path, not an old binary before/after benchmark. It
isolates redundant-refresh suppression without editing world records. No FPS
improvement was demonstrated. Real-change live update latency, visual acceptance
and reconnect/dimension checks remain pending; controlled meaningful-change
measurements and automated tests are documented above. Sampled dirty flags were
false before/after all 20 diagnostic requests, not proof they never changed
between samples. No ownership, population, progression, Build or permission
mutations were requested. Operator list remains empty.

Original method bodies restored in both processes and bytecode verified with
zero temporary counter calls (6 server/8 client classes); inactive transformer
registration remains until process exit. No restart or artifact replacement.
External reproducible observer source, raw start/end readings, request records,
cleanup verification and file hashes: disposable live-sync-sheltered directory.
KOM-74 remains In Progress; KOM-46 relations unchanged.

## Final targeted review checks (2026-09-27)

**Reconnect initialization:** existing actual-client traces from
`first-map-open-after/candidate-final.tsv` show three distinct disconnect/connect
cycles, client session clearing, and a completed `Snapshot.publish` on the client
thread before each map opening (4.625, 4.921 and 2.153 seconds before opening).
These confirm initialization delivery, not every visual or private-data scenario.
No redundant reconnect was requested.

**Meaningful live delivery:** one bounded diagnostic sent a changed public owner
from a detached copy through production `sendIfChanged`, the real Forge/Netty
connection and the normal client publication path. The sole disposable player
was _Danye_. T132 progressed from dunedain to gondor in client state and back to
dunedain after a forced canonical refresh; the same detached object was used for
baseline, changed and repeated requests. All four client observations retained
330 published ownership rows and 269 waypoint links. This tests live meaningful
payload delivery, not an actual gameplay ownership mutation or visual acceptance.
No saved record, canonical owner, permission or progression value was edited.
The canonical public projection remained exactly equal at all four server-thread
checks; sampled dirty flags were false (not proof about unsampled background
activity). Detached references were cleared after restoration. No transformer,
listener, worker or periodic diagnostic was installed by this check.

The running disposable artifact includes separate KOM-77 rendering work. To
establish applicability, all 406 common and relevant client proxy/queue/snapshot
class files were compared byte-for-byte with the preserved KOM-74 artifact:
zero differences. The KOM-74 production JAR hash remains
`eab9872461259a89a22baa4fd0f8bb43848f836e64b0933f0d80a4a216a3f130`;
the running disposable JAR is
`c45c9e41bcb8d06e9bce3e482fc6b7dd08afbf572d217f0ee20f51a0a940d825`.
No claim is made that the full two JARs are identical. Archived full test XML was
recounted: 1,107 discovered, 1,105 passed, two skipped, zero failures/errors.
Archive SHA-256: `a4f278fc077b1fe5c6fab9dfffdaa99c9edde1d2a1c5a7c771de780f0e300ee1`.

Reproducible local diagnostics and hash evidence:
`KOME-Validation/kom74-20260926T215215Z-9fc9b2/review-delivery/`.
`DeliveryCheck.java` contains process/profile/player guards, server/client-thread
scheduling and detached-state construction. `validation.json` records relevant
class parity, XML counts and trace hashes. The compiled diagnostic stays outside
production artifacts and this PR.

Remaining checks: broad multi-player/privacy acceptance, live respawn/dimension
visual checks, and an ordinary authorized gameplay mutation followed by visual
confirmation. Automated respawn/dimension, permissions, removals/zero values,
ordering, failure and queue-bound coverage remains applicable. No general FPS,
first-opening, or per-change latency improvement is claimed. The demonstrated
benefit is elimination of redundant complete publications under the documented
workloads. No runtime restart/replacement, saved-world mutation, geography or
schema change was needed for review preparation; existing runtimes remain intact.