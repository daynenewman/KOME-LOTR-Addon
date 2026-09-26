# KOM-60 server tile awareness / KOM-46 handoff

## Publication review (2026-09-26)

The user reports the population-menu flicker is fixed. This is specific acceptance
of that symptom, not complete visual, waypoint, lifecycle or multiplayer coverage.
Current dev reconciliation and validation are recorded in
[KOM60 integration review](KOM60_INTEGRATION_REVIEW_20260926.md).
The historical entries below retain their original evidence and artifact identities;
old "no push" and local-only statements describe those earlier checkpoints.

The combined implementation uses dev's `KOMEConquestSnapshotPublisher` for all
conquest sections. It retains completed ownership colours, population/waypoint
publication and generation invalidation without a second tooltip batch assembler.
The server observation API, sampling/event contract and tracker code are unchanged.
Dev's protocol and save requirements apply; the older disposable client/world is
not upgraded or used as evidence of this combined runtime.

## Status and scope (2026-09-21)

Implementation branch: `dayne/kom-60-server-tile-awareness`, from freshly fetched
`origin/dev` at `2559141cbc44a8e1f44811643381dd35673ccc59`.
The current KOM-60 and KOM-46 issue requirements were read before implementation.
This is a transient physical-observation interface. It does not complete company
normalization, geographic cleanup, explicit uncapturability, or multiplayer/performance
acceptance. KOM-60 has not been marked complete.

The installed raster, tile identity definitions, gameplay defaults, client HUD,
Build authorization, schemas and packets are unchanged. No observation writes
WorldData, records, ownership, movement legs, or company membership.


## Consumption review - 2026-09-22

Reviewed the actual implementation and generated Forge/Minecraft 1.7.10 sources.
Five new regressions reproduced defects in the prior implementation before fixes:

- **P2: reentrant shutdown/replacement.** Stopping from a callback cleared
  subscriptions before queued REMOVED events arrived. Replacement/respawn could
  write a new entry after a removal callback stopped the service. Replacement
  is now published as pending before callbacks; shutdown clears observations
  immediately but retires listeners only after queued notifications drain.
- **P2: recursive control operations.** Restarting during shutdown notification
  could reset the session and discard queued events. Recursive END sampling
  could advance observations ahead of the remaining callbacks. Session restart
  and recursive sampling now throw before modifying state; normal queries and
  subscription closure remain reentrant. RuntimeException still disables only
  the offending listener if it is not handled by that listener.
- **P2: subscription history.** A subscriber created inside one callback could
  receive other events already queued in that pass. A private publication
  sequence now limits each subscription to events created after subscription.
- **Consumer-contract correction:** the prior example implied that an event's
  location was always still current when delivered. Earlier callbacks can move,
  unload, replace entities or invalidate geometry. Events are historical; the
  revised example re-queries and compares session/incarnation/sample tokens.
  No direct stale-coordinate leak was found in the query itself.

Additional regressions cover pre-END freshness, eligibility loss/rehire, a
consumer ignoring an event made stale by another listener, and production
`KOMEAddon.serverAboutToStart`/`serverStopped` with the default singleton hooks.
The existing exact boundary, snapshot/failure-recovery, UUID/respawn/reconnect,
death/unload, listener isolation, HUD and Build checks remain applicable.

No company policy, strategic arrival, membership change, ownership change or
schema/protocol behavior was added. The lifecycle fixes change only transient
publication and callback handling. Updated validation is recorded below; earlier
974-test evidence and its JAR hash are historical.



### Review validation and checkpoint readiness

- 214 focused tests passed. After the production fixes, one clean
  `.\gradlew.bat clean test build --no-daemon --console=plain` passed:
  **985 discovered, 983 passed, 2 existing Windows symlink skips, 0 failures/errors**.
- Nine review regressions were added, bringing awareness/isolation coverage to
  32 tests. Five new cases failed against the pre-fix implementation and passed
  after correction. The 6,055 production gameplay expectations remain identical.
- Reviewed production JAR: `build/libs/KOME-LOTR-Addon-1.0.8.jar`
  (15,170,228 bytes), SHA-256
  `b0a9b764b38c95c7adfeec6ba543414e568818f10d4201b9e7b6d99d468c863f`.
- Final synthetic workload, same environment/limitations as the original run:
  2,000 entities, 200 ticks/phase; stationary 18.0822 ms total (0.0904 ms/tick),
  moving 80.355 ms total (0.4018 ms/tick), 400,000 moving lookups and no duplicate
  same-tile events. Not a live multiplayer or low-end performance claim.
- Exact resolver, HUD, resource hashes, gameplay defaults and schema/packet code
  are unchanged by this review. Source hashes stayed unchanged during the final
  build. All changed and untracked text files passed whitespace checks.
- **Ready for a local checkpoint and disposable live testing**, not completion
  or availability on dev. Existing worlds/runtimes were not started or modified.
  The next live check should subscribe a read-only consumer and observe a player
  and hire crossing the same known boundary, then unload/reload and reconnect.
  Confirm stale/unavailable states produce no strategic arrival or split.
- Review changed only the tracker, its two test suites and this handoff.
  The complete uncommitted milestone still has five modified and five new files,
  with an empty index. No company policies were implemented.

## Integration points and authorities

| Existing path | Integration / contract |
| --- | --- |
| `KOMEAddon.serverAboutToStart` | Start one server-thread session **before world loading**. Forge 1.7.10 calls this before `loadAllWorlds` on both dedicated and integrated servers; `serverStarting` would miss initial entity-join events. |
| `KOMECommonProxy.init` | Register one `KOMETileAwarenessEvents` instance on Forge and FML buses. No client proxy dependency. |
| Forge `EntityJoinWorldEvent`, LOWEST | Discover relevant loaded entities after existing high-priority KOME spawn guards. Canceled joins are ignored. |
| Forge `LivingUpdateEvent`, LOWEST | Discover an NPC hired after it was loaded, after existing normal-priority hiring/denial handling. This is event-driven, not a world scan. |
| FML login / player tick END | Discover live server players. Tick discovery also handles a replaced player instance. |
| FML respawn / logout | Invalidate the old incarnation; initialize the new one on the next sample. Late removal of an old instance cannot remove its replacement. |
| FML server tick END, LOWEST | Sample retained entities after normal world/entity updates. Existing `KOMEEvents.onServerTick` strategic reconciliation stays at START and is unchanged. |
| Chunk/world unload; final liveness check | Remove observations. Only the unloading chunk's supplied entity lists are visited. The tick check uses WorldServer's O(1) entity-ID map to detect other removals. No chunk request or load. |
| `serverStopping` and `serverStopped` | Idempotent cleanup, including failed-start/crash paths that skip normal stopping. Clear entity references and subscriptions. |

Relevant units are live `LOTREntityNPC` instances with active hired info, a hiring
player UUID, and task WARRIOR or FARMER, matching existing KOME hiring support.
Unhired NPCs, mounts, virtual armies and unloaded records are not observed.
This deliberately includes loaded physical hires before/without a reconciled
company record; the consumer can join by UUID when its authoritative record exists.

`KOMEHiredUnitRecord.entity` remains the join key. Its `companyId`,
`currentTile`, `sourceTileId` and `movingEntityData` remain persisted strategic
state. `KOMEWorldData.rebuildCompaniesFromUnits` and movement-link reconciliation
are not redirected to physical observations. Existing spawn guards for virtual
movement/released hires still run before discovery.

## Exact consumer API

All running-session calls and callbacks must execute on the server thread.
No cross-thread polling is supported. The implementation throws on wrong-thread
access rather than pretending a concurrent map makes Entity fields safe.

- `KOMEServerTileAwareness.INSTANCE.current(UUID)` returns immutable `Current`.
- `Current.availability` is AVAILABLE, PENDING_SAMPLE, NOT_TRACKED,
  SERVER_STOPPED, STALE_POSITION or STALE_GEOMETRY.
- `Current.observation()` is present **only** for AVAILABLE.
- `Observation`: `entityId` (stable entity UUID), `kind` (PLAYER/HIRED_UNIT),
  `session`, `incarnation`, `observedTick`, actual fractional `x/z`, and immutable
  typed `location` (`KOMETileResolution` with dimension, sampled block/cell,
  status, stable tile ID and diagnostic).
- `subscribe(Listener)` returns an idempotently closeable `Subscription`.
- `Listener.onTransition(Transition)`: entity UUID/kind, session/tick,
  `type`, `cause`, optional `previous()` and `current()` observations.
- `currentTick()`, `trackedCount()`, `resolutionCount()` are read-only session
  diagnostics; counts do not expose or persist another registry.

Subscribe once per server session, from a consumer lifecycle hook at
`FMLServerStartingEvent` or later, after KOME's about-to-start handler. Startup
entities are pending until the first END sample, so this subscription sees their
initial observations. A late subscriber can query the UUIDs it already owns;
subscription does not replay history or enumerate all entities. This includes
already queued events when subscribing from inside a callback. A new subscriber
can receive a later reentrant event only if it was created after subscription.
A late subscriber may receive REMOVED without an earlier INITIALIZED event; its
previous observation supplies the incarnation to invalidate.

Query example inside existing server-side company logic:

```java
KOMEServerTileAwareness.Current current =
    KOMEServerTileAwareness.INSTANCE.current(unitRecord.entity);
if (current.availability == KOMEServerTileAwareness.Availability.AVAILABLE) {
    KOMETileResolution location = current.observation().get().location;
    if (location.status == KOMETileResolution.Status.RESOLVED) {
        String physicalTile = location.tileId;
        // Compare with company policy here; do not overwrite strategic state implicitly.
    } else {
        // Preserve the exact typed state. A gap is not an uncapturable tile.
    }
}
// PENDING/STALE/NOT_TRACKED/STOPPED: no current physical position is available.
// Never substitute unitRecord.currentTile as a fresh physical observation.
```

Subscription example (store the handle in the consumer's server-session state):

```java
KOMEServerTileAwareness service = KOMEServerTileAwareness.INSTANCE;
KOMEServerTileAwareness.Subscription subscription = service.subscribe(event -> {
    if (event.kind != KOMEServerTileAwareness.Kind.HIRED_UNIT) return;
    if (event.type == KOMEServerTileAwareness.Type.REMOVED) {
        KOMEServerTileAwareness.Observation removed = event.previous().get();
        // Invalidate a consumer cache only if its session/incarnation match removed.
        // Never remove company membership. This UUID may already have a replacement.
        return;
    }

    KOMEServerTileAwareness.Current query = service.current(event.entityId);
    if (!query.observation().isPresent()) return; // Unknown is not movement or a split.
    KOMEServerTileAwareness.Observation now = query.observation().get();
    KOMEServerTileAwareness.Observation published = event.current().get();
    if (now.session != published.session || now.incarnation != published.incarnation
            || now.observedTick != published.observedTick) return;
    KOMETileResolution location = now.location;
    if (location.status != KOMETileResolution.Status.RESOLVED) {
        // Keep the typed unresolved result; never substitute a strategic tile.
        return;
    }
    // A fresh physical location is available. Independently read current record,
    // owner, permissions and company policy. This event is not strategic arrival.
});
// On consumer shutdown (server thread): subscription.close();
```

Callbacks should be short, read-only with respect to location tracking, and must
not block. An optional listener throwing RuntimeException is unsubscribed and
logged once; remaining listeners and entity observations continue. A consumer
that deliberately moves an entity during notification makes a subsequent query
STALE_POSITION until the next END sample; event payloads remain historical facts.

## Transition semantics

| Type | Meaning |
| --- | --- |
| INITIALIZED | First non-failure observation for an incarnation; previous is absent. A first gap/outside/unsupported result is explicit initialization too. |
| CHANGED | Semantic location changed (status, dimension or resolved tile ID); previous and current are present. Includes tile-to-gap, unsupported dimension and recovery from failure. |
| RESOLUTION_FAILED | INVALID_SNAPSHOT or INVALID_COORDINATE. Current carries the failure, never the previous tile. Previous absent distinguishes initial failure from loss of an existing resolution. |
| REMOVED | Previously observed incarnation became unavailable. Previous is present, current absent. Observations are removed before callbacks. |

Causes distinguish first discovery/login/respawn/replacement, position,
dimension or geometry changes, disconnect/death/discharge, entity/chunk/world
unload and server stop. Teleports are detected from actual fields; no invented
teleport event or distinction from large movement. A respawn/reconnect gets a
new incarnation even at the same tile. Removal before a first sample has no
transition because no location observation was published.

No event repeats while status + dimension + tile ID are unchanged. Coordinates
and observation timestamps still advance when moving within the same tile or
unresolved region. Changing between two gap cells is not a tile transition.
Altitude alone is irrelevant to the X/Z geometry.

Every sample pass publishes all results/removals before notifying listeners.
FIFO notifications preserve publication order, not a frozen world. During a
callback, queries see all state changes completed so far, including changes by
earlier callbacks. A later queued INITIALIZED/CHANGED event may therefore be
historical by delivery time; re-query before using it. Removal can refer to an
old incarnation while a replacement is already PENDING_SAMPLE or AVAILABLE.
Never clear another incarnation merely because its UUID matches a REMOVED event.

Queries, subscription add/close, and lifecycle removals are reentrant. Stop makes
queries SERVER_STOPPED immediately, delivers queued removals to still-active
listeners, then closes subscriptions. Restart and recursive sampling during
notification are rejected before state changes. Closing a subscription prevents
its remaining callbacks. Listener order follows subscription order; a failing
listener is disabled without preventing other listeners receiving the event.
A transition does not imply permission, ownership, traversability or capture
eligibility. Unsupported dimensions and unknown geography remain distinct.

## Freshness, snapshots and bounded work

Sampling is once per server tick at END. A change is detected by the next END
sample (normally within one 20-TPS tick, approximately 50 ms). Server lag or a
consumer running after this handler can delay it until the following END;
there is no wall-clock guarantee.

A current query checks live entity identity, eligibility, actual world,
dimension, exact X/Z bits and resolver publication identity. Between mutation
and resampling it returns STALE_POSITION/STALE_GEOMETRY **without an observation**.
AVAILABLE means a fresh typed observation, not necessarily RESOLVED.
A held immutable Observation is a historical value; re-query for current facts.
Session/incarnation numbers are process-local, not persistent identifiers.
Before this tick's END, AVAILABLE can still carry the previous sample's
observedTick when actual position/world/publication/eligibility remain unchanged.
That is valid at query time, not a promise about the remainder of the tick.
currentTick/observedTick count END sampling passes, not World time or wall time.

Only sampled endpoints are observed: movement away and back between samples can
produce no event. Teleports do not enumerate crossed tiles. A geometry change or
recovery from resource failure can produce CHANGED without physical movement.
Neither absence of an event nor an unresolved/removal event authorizes a split,
strategic arrival, transfer, capture or inferred uncapturability. Re-read existing
ownership/permissions; eligible-to-eligible owner/task changes are not spatial
transitions. Distinct incarnation tokens protect replacement/reconnect identities,
not simultaneous duplicate-UUID entities (which violate the engine identity contract).

The existing resolver now exposes `ReadView readView()`: one immutable captured
publication with `resolveWorldPosition(dimension, x, z)`. It binds exact resolution
to that publication even if another snapshot is atomically published. The tracker
captures one view per pass and skips unchanged positions only when world,
dimension and view identity also match. Same-position snapshot replacement is
therefore re-evaluated. Equivalent results emit no redundant event.

Invalidation clears the previous tile at the next sample (queries are stale
immediately). Initial loading failure resolves INVALID_SNAPSHOT. A rejected
reload retains the last valid raster under the established resolver contract;
it revalidates observations but does not invent a failure or tile transition.
No resource reading/decoding, ownership access, tile scan or lock occurs during
tracking resolution. Fractional coordinates use the existing exact conversion:
floor to signed integer blocks, then checked integer raster conversion. Negative
values, NaN/infinity and unsupported ranges retain existing typed behavior.

Work/memory scale with currently tracked live players and supported hires.
Unchanged samples allocate no result/observation/string. Changed positions use
one O(1) raster lookup each; transitions and explicit queries allocate immutable
payloads. One transient UUID map exists for this server session, not a second
tile authority, persistent player field or per-coordinate cache. No client
synchronization or HUD change is necessary for this server-only consumer.

## Validation and workload

Focused validation: 184 tile/tracking/HUD/Build/public-access/gameplay tests
passed; the subsequent tracking-only run also passed the additional regression
for vanilla's join-before-respawn-event ordering.

The first complete run exposed an existing test that demanded an exclusive
one-statement serverStopped body. It now invokes the production shutdown
handler and asserts that the configuration lock is cleared, retaining the
idempotence, new-world binding and lifecycle annotation checks. The affected
configuration/tracking suites passed before the final complete rerun.

Final clean-build results and candidate identity are recorded below.

The production-hook tests use inert actual EntityPlayerMP/LOTR NPC subclasses,
real Forge/FML event objects and real raster resources. They cover fractional
negative boundaries, tile/gap/outside/dimension transitions, respawn/reconnect,
startup discovery, final death, chunk/world unload, live-map removal, server
stop, subscription cleanup/failure isolation, snapshot replacement/failure,
freshness, event deduplication and a minimal company consumer that leaves
strategic fields untouched. The JUnit event fixture explicitly supplies FML's
cancelability behavior because the plain test JVM does not run its event transformer.

Dedicated-server isolation is checked with a classloader denying client/LWJGL
and WorldData classes while loading hooks and exercising the service lifecycle.
These tests do not launch Forge, simulate a real network reconnect, or establish
multiplayer/live gameplay acceptance.

The bounded workload test uses 2,000 inert entities (100 players, 1,900 hires),
50 warmup ticks, 200 stationary ticks and 200 moving ticks with real production
sampling. It asserts zero stationary lookups and exactly 400,000 moving lookups.
Same-tile movement emits zero repeated transitions. Timings include the position
update loop but exclude entity AI, chunk generation, network and real consumer
logic, discovery and Forge event-dispatch overhead. The fixture's entity-ID
index uses a HashMap; live WorldServer uses its existing IntHashMap. They are a
regression diagnostic, not a low-end gameplay benchmark.


### Original implementation evidence (before consumption review)

- `.\gradlew.bat clean test build --no-daemon --console=plain`: **BUILD SUCCESSFUL**.
  976 discovered, **974 passed**, 2 skipped, 0 failed, 0 errors. This includes
  23 new awareness/isolation tests and the unchanged 6,055-row gameplay parity
  comparison through production routing/initialization paths.
- The existing skips are
  `CustomSkinLibraryFoundationTest.symbolicLinkSkinIsRejectedWhenSupported` and
  `ClientCustomSkinCacheTest.symbolicLinkAtHashPathIsNeverAcceptedWhenSupported`;
  Windows did not permit symbolic-link creation in the test environment.
- Workload environment: Windows 11, AMD Ryzen 5 7600X (12 logical processors),
  Java test runtime 1.8.0_492. Final run: 200 stationary ticks **14.4856 ms total**
  (**0.0724 ms/tick**), 200 moving ticks **77.2718 ms total**
  (**0.3864 ms/tick**). Each tick sampled 2,000 tracked entities. Moving phase:
  exactly 400,000 lookups, zero duplicate same-tile events. Results are one
  bounded synthetic run with the limitations above, not a latency guarantee.
- Production/reobfuscated JAR:
  `build/libs/KOME-LOTR-Addon-1.0.8.jar` (15,169,853 bytes).
  SHA-256: `b5eb78ccfebcb48b487c3bdff0d6ba94e17bf6c66eddb34e123e97cc4150b40e`.
  No runtime installation or startup was performed.
- Installed mask remains
  `ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`.
  All production resources, schemas, packets, client code, Build and movement
  implementations are unchanged from the fetched base.
- Reviewed all changed/new Java files and documentation; tracked/index whitespace
  checks and a scan including untracked text files passed. Production/test hashes
  were checked after the final build; no concurrent source changes were found.
- Final intended scope: five production files (new service/hooks; additive resolver
  ReadView and lifecycle registration), three test files (two new suites and the
  shutdown configuration test), and two documentation files. Changes are unstaged
  and uncommitted. Existing worktrees, audits, recoveries, worlds and protected
  stash `eb8f04e9845a521d918b2495b35bfd1b37974388` were not modified.

## KOM-46 decisions and remaining acceptance

KOM-46 can consume this interface now. It must still decide grouping distance,
same-tile/nearby requirements, detachment identity, atomic membership changes,
permission checks, funding/commitment preservation, reconciliation audit rules
and how unknown physical presence affects strategic operations. None is inferred
by this implementation. Unloaded units have no physical observation and must
not be classified using the last observed position.

Pending isolated live checks (no existing runtime was changed):

1. With a test consumer subscribed on the server thread, move a non-operator
   player and an eligible hire across a known raster edge. Confirm one transition
   each and matching typed results. The existing resolver diagnostic is staff-only;
   no operator grant or new command is required by the API.
2. At verified mask controls, check Rhun X=237248.5 with Z immediately below
   87296 (T401) and at 87296 (T442), protected R1 X/Z=34944.5/640.5 (gap), and
   X=189696, Z=-86016 (T001; immediately lower X is the protected gap).
   These are X/Z probes, **not surface-safe teleport commands**.
3. Observe respawn, reconnect, dimension transfer, hire discharge/death, chunk
   unload/reload and server restart. Verify old incarnations are not reused and
   the HUD/Build behavior remains unchanged.
4. Measure with real loaded units and a real KOM-46 consumer before claiming
   multiplayer or broad performance acceptance. Geographic/uncapturable
   classification remains separate and does not block typed tracking.


## Disposable hire validation: client publication fix (2026-09-22)

The first actual player hire succeeded: native Angmar Hillman UUID
`3a96f11b-3bb6-42c9-ada3-a1cd9e7a781d` was admitted through normal LOTR/KOME
hiring, charged 20 Angmar population and initialized by the production tracker.
The observer saw movement followed by CHUNK_UNLOADED removal after the player
connection closed. Removal queries exposed NOT_TRACKED, with no current location.
This is genuine hire initialization/unload evidence; reloading, respawn and broader
hire lifecycle acceptance remain pending.

The client then disconnected with a reproducible failure outside the tracker:
`RejectedExecutionException: KOME client task queue is full; newest task rejected`,
from `KOMEPacketConquestData.Handler.onMessage`. This was the first packet failure;
later "client is disconnected" errors were secondary. The server remained running.
The packet/queue code predates the KOM-60 checkpoint. No tracker client packet exists.

Forge 1.7.10's actual stack dispatched the conquest chunks on the Client thread
through Minecraft's network pump. Deferring each chunk until the next START tick
unnecessarily accumulated a burst beyond the bounded 128-task queue. Client-owned
publication now uses Minecraft's verified `func_152345_ab()` thread check: drain
older queued/reset work before enqueueing, then drain the new work when already
on that thread. Off-thread work retains the same 128-task bound and START-tick
handoff. Reentrant publication waits for a subsequent drain, preserving bounded
execution and preventing recursive draining. Disconnected work remains rejected.
There is no larger/unbounded queue, silent conquest-chunk drop, protocol change,
server behavior change, or alteration to hiring/ownership/population rules.

Regressions exercise the actual conquest packet handler with 512 chunks delivered
between ticks, preserving reset/all rows/completion; full older FIFO work before
client publication; disconnect/reconnect clearing; and reentrant drain suppression.
Existing off-thread bounds/deep-copy/failure isolation checks remain intact.
Validation and the replacement artifact identity are recorded in the disposable
validation report; interactive acceptance requires reconnecting with the rebuilt
client and observing the existing hire. Do not create duplicate hires as a retry.

Fix validation: 57 focused tests passed; clean test/build passed with 988 discovered,
986 passed, two existing Windows symlink skips, zero failures/errors.
Replacement production JAR SHA-256: `c4cf486d6fe7328c355b731718f22e4260f7eae561d6d45a2cfea3baa5a12645`.
The reviewed tracker source and installed geometry are unchanged. Live reconnect with the corrected client passed at the tested location; original
failed-hire logs and stopped-world backup are retained in the disposable validation directory.


## Separate conquest-map performance correction (2026-09-23)

Actual eligible hire Brodda (UUID `3a96f11b-3bb6-42c9-ada3-a1cd9e7a781d`)
and player `_Danye_` reloaded in T442 on disposable cycle 4. Both initial callbacks
queried AVAILABLE with matching session/incarnation/sample tokens. After 2,820 ticks
there were still only two initialization events; within-tile hire motion was not
reported as a transition. The player then crossed T442 -> T401 at tick 8448,
Z 87296.48798860183 -> 87295.78866675834. Brodda followed at tick 8500,
Z 87296.07643927682 -> 87295.78094259379. Each emitted one POSITION_CHANGED event
with a fresh matching callback query. This is physical observation acceptance at
one boundary, not company policy, strategic arrival, or general performance acceptance.

The user reported slow map rendering. Six live thread samples showed active costs
in conquest NBT publication copying and ownership-texture rebuilding; those samples
alone do not establish their relative impact. Bounded method timings subsequently
measured the same stationary map view (posX 2663.5493, posY 1412.1355, zoom 8).

The claim texture previously invalidated on every completed conquest revision,
even if only troops, population, routes, or identical ownership were refreshed.
The renderer now compares exact effective mask-color -> faction-RGB maps at revision
changes (O(number of visible tile records), not a second raster scan). It rebuilds
and uploads the full texture only when those render inputs change. Only known IDs
in the existing client projection contribute colors; removed visibility/claims
clear the corresponding fill. Ownership reads use the pure compatibility projection.
Geometry replacement/failure, resource reload, and client session clearing invalidate
both texture and color cache. A failed upload is not cached as successful. No hash-only
fingerprint, ignored update, new geometry authority, or gameplay change is introduced.

`KOMEPacketConquestData.fromBytes` retains a private defensive copy only after complete
wire validation, separate from its publicly mutable packet fields. The handler reads
that private snapshot and materializes independently owned records before enqueueing.
This removes a redundant encode/compress/decompress/validate cycle from real incoming
packets. Locally constructed packets still use full codec validation. The bounded FIFO,
client-thread dispatch, reset/completion ordering, disconnect handling and the separate
queue-overflow fix remain unchanged. Protocol bytes and limits are unchanged.

Focused regressions cover repeated identical colors, actual color/visibility changes,
failed uploads, reconnect/resource/geometry cache clearing, public known-ID filtering,
decoded message mutation isolation, and the actual decoded 512-chunk production handler
burst. Existing malformed-packet, public-access, border, thread and queue-bound tests
remain required. No tests or instrumentation replace live visual acceptance.

Temporary measurement instrumentation is confined to the disposable environment's
`map-performance` directory, outside production source/JARs. The same three method
probes time ownership texture rebuilds, typed conquest handler calls, and Minecraft
frame-loop wall time. Frame-loop time includes update/render/wait work; it is not GPU
render time or a portable FPS benchmark. Handler timings exclude `fromBytes` in both
versions; the overall frame measure includes decoding. Observer and timer overhead
must be consistent, and refresh volume must be reported alongside timing.

### Validation and measured result

73 focused tests passed. Final clean test/build: **993 discovered, 991 passed,
2 existing Windows symlink skips, 0 failures/errors**. The skipped methods are
`CustomSkinLibraryFoundationTest.symbolicLinkSkinIsRejectedWhenSupported` and
`ClientCustomSkinCacheTest.symbolicLinkAtHashPathIsNeverAcceptedWhenSupported`.
Production JAR SHA-256:
`70271acc79e627be6696e16d5485ec9ba7358baf4ae3a542fce806a628f205bc`.

| Instrumented stationary map measurement | Before | After |
|---|---:|---:|
| Summed frame-loop wall time | 76.27 s | 117.04 s |
| Completed frame-loop samples | 577 | 14,046 |
| Ownership texture rebuilds | 578 | 0 |
| Typed conquest handler calls | 33,682 | 51,502 |
| Handler calls / summed frame-loop second | 441.60 | 440.04 |
| Mean handler time (excluding decode) | 0.982 ms | 0.0622 ms |
| Median frame-loop time | 135.59 ms | 8.47 ms |
| 95th percentile frame-loop time | 156.94 ms | 10.03 ms |

The after view exactly matched posX/posY/zoom of the baseline. A first after sample
at zoom 1 was excluded; the matched comparison uses zoom 8. Windows differ in length;
reported rates are normalized by summed frame-loop wall time, not an assumed FPS.
A method in flight at measurement boundaries can make rebuild count differ slightly
from completed frame samples. The initial new-client texture upload precedes the
after measurement; zero means no unnecessary repeat uploads, not no initial texture.
This is measured local improvement under comparable refresh activity, with the same
method timers and server tracker observer. It is not proof of all hardware, zooms,
GPU performance or multiplayer loads. No production instrumentation was installed.

Orderly disposable shutdown produced SERVER_STOP removals for the real player and
hire; callback queries returned SERVER_STOPPED without an observation. Post-stop
inspection found zero entries/listeners/notifications and inactive subscription.
The stopped-world/config backup was SHA-256 verified (117 files) before replacement.
Cycle 5 started successfully on `127.0.0.1:54190`, online-mode true, empty operator
list, with identical client/server JAR hashes. Prism reconnected successfully.
A normal post-restart save retained Build, progression, faction population, capital,
route, waypoint-link and movement sections exactly. Brodda's identity, ownership,
source, company, cost and 20-population payment are identical. Only his loaded entity
position/rotation and the company's UpdatedAtMillis differed in those records;
no membership or destination change was made by this correction.

Evidence and temporary diagnostic sources are outside the repository at
`C:/Users/dayne/Documents/KOME-Validation/kom60-20260923T004907Z-65b1ff/map-performance/`:
`comparison.json`, raw start/stop reports, `build.json`, `restart-preservation.json`,
`restart-differences.json`, and `stopped-backup/manifest.json`. Source recovery is
under `recovery/`; these are local evidence, not build dependencies.

Remaining manual acceptance: pan/zoom and hover normally, inspect ownership colors,
and repeat after reconnect/resource reload. Automated color-change tests are not a
live ownership-change visual check. Player respawn and remaining KOM-46 availability/
publication checks remain separate; neither issue is complete and the dependency stays.


## Interactive map follow-up - 2026-09-25

The user rejected the previous visual check: hover worked, but panning felt slow
and colors appeared to change. The earlier stationary measurement is not interactive
acceptance. KOM-60/KOM-46 remain open and unpublished; KOM-74 tracks synchronization
volume separately. This correction is separate from tracker and queue-overflow work.

Confirmed rendering defect: a complete conquest batch could be followed by the reset
chunk of the next batch before a frame consumed its revision. The renderer then read
partially populated mutable tile rows. A production handler/queue regression reproduced
missing/recolored inputs before the fix. KOMEClientData now publishes an immutable,
render-only tile-ID/owner projection on completion, before advancing the revision;
the overlay consumes that completed projection. Reset/disconnect clears it. It contains
only the existing public projection and is not an ownership authority. Actual completed
ownership changes, removals, resource reload, geometry changes and reconnect still
invalidate correctly. No packets are discarded or reordered, and queue bounds remain.

A second measured cost was temporary allocation in strict text validation and faction
normalization. validateText now counts UTF-8 bytes and validates surrogate pairs without
allocating/encoding a throwaway buffer. Wire encoding is unchanged. Canonical lowercase
ASCII faction keys skip NFD/regex work; noncanonical input uses the same normalization
and alias rules with precompiled patterns. Tests compare the actual wire encoder for
all BMP code units and size/surrogate boundaries, and compare legacy faction behavior
for aliases, Unicode and US/Turkish locales. No protocol or faction policy changed.

The live diagnosis did NOT reproduce ownership texture corruption: before pan, after
pan away/return, and after zoom out/return, effective colors matched uploaded colors,
whole CPU/GPU ownership texture CRCs matched (1040711219), and zero pixels differed.
Texture ID/size and active texture unit remained stable, with no GL error. Captures
show expected yellow hover fill/outline. The packet interleaving reproduction establishes
a real defect, but does not prove it explains every color change reported by the user.
No speculative GL, border, geometry or gameplay-default alteration was made.

Validation: 122 focused tests passed. One clean test/build discovered 997 tests:
995 passed, two existing Windows symlink skips, zero failures/errors. Skips are the
same two methods listed above. Production JAR SHA-256:
`a63d0116f2479be21be17b27aadc296d18b14a1eb3a1793aebdd4dfdd184f280`.

Diagnostic sources, JFR recordings, screenshots, raw timings, source recovery,
116-file SHA-256 verified stopped-world/config backup, build counts and saved-state
comparison are outside the repository at
`C:/Users/dayne/Documents/KOME-Validation/kom60-20260923T004907Z-65b1ff/interactive-map-20260925/`.
They are temporary instrumentation, not dependencies of the production artifact.
Cycle 6 uses matching client/server artifacts, loopback port 54190, online mode and
empty ops. Startup completed; the existing unrelated LOTR playerdetails API DNS failure
was logged after connection, without a disconnect. Save comparison retained Builds,
progression, population, capitals, routes, waypoint links, movement records and Brodda's
identity/authority/20-population payment. Observed changes were only loaded hire X/Z,
X/Z motion and rotation, and company UpdatedAtMillis. Server stop cleared observations
and subscriptions; real player and hire reload initialized successfully after restart.


### Matched interactive evidence and remaining limits

Both accepted runs used the live client at **3840x2054**, GUI scale unchanged, map
center (2663.5493,1412.1355), zoom 1, centered hover, the same 80x40-map-pixel
pan loop and zoomPower 0 -> -3 -> 0, identical temporary method timers/JFR and
server observer. Each recording lasted about 29 seconds. The first after-run
(`after-hover`) launched at 854x480 and is explicitly excluded; the comparison
uses `before-hover` versus `after-matched`. `compare_interactive.py` requires all
six framebuffer images to match the original render size and all effective/uploaded
color maps and GPU/CPU texture checks to agree.

| Matched metric | Before | After |
|---|---:|---:|
| Pan median / p95 frame-loop time | 8.50 / 10.07 ms | 8.36 / 9.51 ms |
| Zoom median / p95 frame-loop time | 8.61 / 23.98 ms | 8.50 / 21.47 ms |
| Client-thread allocation, complete window | 477.34 MB/s | 197.90 MB/s |
| JVM GC time, complete window | 41 ms | 72 ms |
| Pan mean border CPU time per draw | 0.279 ms | 0.279 ms |
| Zoom mean border CPU time per draw | 0.993 ms | 0.989 ms |
| Pan mean ownership draw/cache time | 0.076 ms | 0.037 ms |
| Zoom mean ownership draw/cache time | 0.097 ms | 0.041 ms |
| Pan mean conquest handler / decode | 0.073 / 0.173 ms | 0.044 / 0.099 ms |
| Zoom mean conquest handler / decode | 0.074 / 0.182 ms | 0.041 / 0.095 ms |
| Pan / zoom repeat ownership texture builds | 0 / 0 | 0 / 0 |
| Pan conquest packets per frame-loop second | 437.84 | 440.08 |

Mean full overlay time during zoom was 4.91 -> 4.81 ms; native LOTR map draw was
1.54 -> 1.56 ms. These nested method timings overlap and must not be added as
independent frame-budget slices. Border extraction/rendering cost did not materially
improve and was not altered. Zoom still has frame spikes. Panning in this scripted
path was already near the 120 FPS cap; this does not fully reproduce the reported
physical dragging latency. Allocation decreased, but this short window's GC time
increased; it is not evidence of a GC-pause improvement. Screenshots/GPU readbacks
are excluded from frame timings but contribute allocation and GC overhead in both
runs. JFR identifies active costs, not precise causal percentages. No broad low-end,
all-region, or multiplayer-performance acceptance is claimed.

The ownership texture ID differs across client restart (expected), stays constant
within each run, and never diverges from CPU contents. No hover/selection styling
was removed. The entire installed mask still hashes to
`ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`;
explicit gameplay defaults and tracker sources are unchanged.

Live retest, still pending user acceptance:
1. Open the map, move the pointer away from a chosen tile, pan away and back at the
   same zoom. Its ownership color should remain stable; yellow hover emphasis may
   follow the pointer normally.
2. Drag and zoom through several regions, checking responsiveness, borders and hover.
   Report the region/zoom if a slow view remains; the measured zoom spikes are not
   declared resolved by the narrower allocation fixes.
3. Close/reopen, then reconnect and confirm ownership colors return correctly.

KOM-74 retains the roughly 440-packet/second follow-up. The visual check remains
pending, neither KOM-60 nor KOM-46 is complete, and the dependency/publication gate
is unchanged. No push or PR is part of this correction.

## Population hover publication correction (2026-09-26)

The user reported population rows disappearing during stationary hover on dad6374.
This failed visual check supersedes any assumption of complete map acceptance.
The server sends one replacement conquest snapshot as ordered section chunks:
`reset` begins the snapshot and `complete` ends it. Empty sections are omitted.
Population rows are complete records, not field patches. The old handler cleared
live `troopSummaries` at the first chunk, before later population chunks arrived.
The live tooltip therefore observed absence during otherwise unchanged refreshes.
This clearing behavior predates dad6374; the ownership optimization did not make
population publication atomic. Approximately 20 full refreshes/second expose the
race; changing that traffic remains KOM-74, not part of this fix.

`KOMEClientData.applyConquestPopulation` now assembles rows on the client thread
and publishes only at `complete`. The previous completed projection remains visible
while a replacement is incomplete. Omitted sections in ordinary chunks do not clear
it; omission from a completed reset snapshot does. An addressed empty row explicitly
removes the tile. Known faction zero remains 0.00; a tactical-only row with no public
faction projection renders no invented faction values. Malformed missing projection
fields remain rejected by the existing strict decoder. No wire/schema change, timer,
server authorization change, or suppression of meaningful packets is introduced.

Population projections in this packet are public faction information, not a
viewer-private population permission channel. A completed withdrawal replaces/removes
old values rather than retaining them. Existing public-access tests remain green.
Client world unload and connection lifecycle clear published and pending projections;
a generation token drops previously queued work, and a new reset is required before
accepting remaining chunks after a clear. Queue ordering, bounds, immutable packet
handoff and the earlier overflow fix are retained. Other conquest sections are not
made atomic by this narrow fix.

Regression coverage exercises the actual server chunk builder, wire codec, handler,
client queue, connection/world lifecycle hooks and production tooltip formatter:
repeated partial refreshes; real population updates and zero; omitted versus addressed
clear; completed withdrawal/tactical-only data; malformed rows; replacement batches;
queued old-world work; disconnect/reconnect. The pre-fix test failed with
`Published hover population vanished`. Focused validation passed 82 tests. One clean
`test build` discovered 1,006 tests: **1,004 passed, two skipped, zero failures/errors**.
Both skips are the existing unsupported Windows symlink cases in custom-skin tests.
All built source hashes were rechecked before the local checkpoint.

### Disposable runtime evidence

The stopped world/configuration and old artifacts were copied and SHA-256 verified
(116 files) before replacement. Evidence, diagnostic sources, backup and raw captures:
`C:/Users/dayne/Documents/KOME-Validation/kom60-20260923T004907Z-65b1ff/population-hover-20260926/`.
Matching server/client production JAR SHA-256:
`83aad48c0b9784511ba8a14bc81ed22d95d401f85162217ffbec64610611ac1d`.
The unchanged mask remains
`ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7`.

Temporary external instrumentation sampled the actual hover renderer, population
lines and incoming packet flags; it is not in the production commit or artifact.
The initial moving-hover capture is excluded from the stationary comparison.

| 15-second capture | Hover frames | Missing summaries | Completed refreshes | Missing population-line sets |
|---|---:|---:|---:|---:|
| Before, stationary T325 | 1,795 | 70 | 301 | Not instrumented |
| After, stationary T325 | 1,629 | 0 | 346 | 0 / 1,629 |
| After, T024 with real bank change | 1,797 | 0 | 300 | 0 / 1,797 |

T325 showed Rohan population 0.00 throughout. For T024, a guarded disposable-only
server-thread diagnostic called the existing population service to grant exactly
100 centi-population to the already-existing Angmar bank, then spent precisely that
amount to restore its original zero balance. The actual hover lines changed
**0.00 -> 1.00 -> 0.00**, while active population stayed **20.00**. No player teleport
or additional hire was performed. Map focus used resolver-verified tile anchors.
These observations prove the measured render inputs remained complete and refreshed;
they are not a claim of user visual acceptance or an FPS benchmark.

After restoration and save-all, saved comparison retained Builds, progression,
population balances, routing and movement data, and Brodda's identity/authority/
20-population payment. Differences were only Brodda's ordinary X/Z/rotation,
company UpdatedAtMillis, and one normal CONFIG/WORLD_BOUND restart audit entry;
all prior audit entries remained intact. Configuration values and empty ops remained
unchanged (server/splash properties regenerated timestamp comments only). Cycle 8
listens at **127.0.0.1:54190**, online mode enabled. Startup and connection succeeded;
the existing unrelated LOTR playerdetails API DNS failure remains in the log.

Manual acceptance remains open: hold the pointer over an owned tile for 20 seconds
and confirm all three faction population lines stay visible; then close/reopen the
map and reconnect once. Check colors, borders and hover normally. No push/PR or
KOM-46 dependency removal is authorized by these instrumented results. KOM-74
retains the separate synchronization-traffic follow-up.

## Waypoint hover publication correction (2026-09-26)

The user confirms population rows stay visible with de364ec, but reports linked
LOTR waypoint names flickering to `Missing`. This is population-specific acceptance,
not complete map acceptance. The same defect remained in a separate section:
`KOMEPacketConquestData.Handler` cleared live waypoint links at `reset`, while the
server sends `TileWaypointLinks` near the end of the multi-chunk snapshot.
The hover renderer and selected-tile panel directly read that incomplete map.

The existing population assembler is now `applyConquestTooltip`, with waypoint rows
included in the same client-thread completed-snapshot publication and generation
lifecycle. `clearConquestTooltip` clears both published and pending maps on world
unload/disconnect. The server still owns waypoint associations. No name is invented,
no timer is added, and rendering/geometry/packet formats are unchanged. Non-reset
omission leaves links intact; omission from a completed reset removes links. The
existing decoder still accepts only nonblank tile/key rows: there is no newly
invented per-row deletion protocol. Changed links publish at completion, and old
world/connection tails cannot resurrect links. This correction does not claim that
all other conquest sections are atomically published.

Three additional tests cover repeated partial waypoint updates/name changes,
completed removal/abandoned batches, and actual world-unload/disconnect/reconnect
hooks with pending/queued old links. The existing real server chunk-builder test
also checks population and waypoint publication together, including removal. Four
tests failed against de364ec before the production change. Focused packet, queue,
privacy and waypoint checks: **70 passed**. One clean test/build: **1,009 discovered,
1,007 passed, two existing Windows symlink skips, zero failures/errors**.

A 15-second live linked-tile baseline at T325 showed `Grimslade` alternating with
`Missing`: 955 missing of 1,669 actual hover draws over 301 completed refreshes.
An earlier capture at T304 is excluded from this defect comparison: the server
has no waypoint association for T304, so `Missing` there is correct. Diagnostic
sources and raw data, source recovery, build evidence and the verified 116-file
stopped-world/configuration backup are outside the repository in the existing
KOM60 disposable runtime's `waypoint-hover-20260926` directory.
Matching installed production client/server SHA-256:
`a7b2530cc4eb89fdc995687efb1b6b5ffca70639390ae6796ab71667dc3fbe1f`.
Only the disposable profiles were refreshed after graceful save/stop. KOM-74 retains
the separate refresh-traffic optimization; no push or PR follows from these checks.

After refresh, the actual T325 hover renderer produced **1,503 draws, zero missing
links and zero `Missing` lines over 349 completed refreshes in 15 seconds**;
`Grimslade` remained the only observed name. This is instrumented live evidence,
not user visual acceptance. The client was subsequently relaunched to remove all
temporary observation hooks. The server remains online-mode, loopback-only at
127.0.0.1:54190; startup/reconnect succeeded, with the pre-existing unrelated LOTR
playerdetails DNS failure still logged.

Post-refresh saved comparison preserved Builds, progression, faction balances,
waypoint associations, routing/movement records and Brodda's identity/payment.
Differences were normal hire rotation, company UpdatedAtMillis, and one expected
CONFIG/WORLD_BOUND restart audit entry; the prior audit remained intact. Empty ops
and configuration values were retained, with only regenerated timestamp comments
in server/splash properties. Built source hashes and backup hashes were reverified.

Next manual check: hover over a tile with a waypoint for 20 seconds. Its waypoint
name and population rows should remain steady. Tiles without links should still
show `Missing`. Then close/reopen the map and reconnect once; no prior-session
waypoint should leak into the new view. KOM-60/KOM-46 remain open with the existing
dependency, and overall map acceptance remains pending.
