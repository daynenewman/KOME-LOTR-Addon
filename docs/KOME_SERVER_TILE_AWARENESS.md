# KOM-60 server tile awareness / KOM-46 handoff

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
The reviewed tracker source and installed geometry are unchanged. Live reconnect
with the corrected client remains pending; original failed-hire logs and stopped-world
backup are retained in the disposable validation directory.
