# KOM-74 conquest synchronization

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
