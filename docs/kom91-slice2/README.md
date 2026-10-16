# KOM-91 Slice 2: same-source-tile return and diagnostics

## Pre-edit concurrency boundary

Starting checkout: clean KOM-91 branch at `2325c5e`.
Compared `git diff 2325c5e...808c886 -- src/main/java/kome/common/data/KOMEJoinBattlePhysicalAccess.java`
before production edits. KOM-23 adds Encirclement checks in `deploy` and
`deployMounted`: before ordinary entry, continuing mounted entry, destructive
mounted transfer, and publication after native spawn callbacks.

KOM-91 intends to change only `prepareEgress`, return-resolution helpers,
`recoverArmedEgress`, `egress`, and `verifyMountedEgress` in that shared file.
No `deploy`/`deployMounted` changes or entry semantics are authorized.
The existing local resolver remains shared with entry rollback; canonical tile
fallback is a separate egress-only wrapper, so entry rollback keeps its behavior.

## Authority decision

WorldData's saved RALLY is the effective arrival authority, including configured
overrides and defaults already materialized by WorldData. Read it without creating
or refreshing waypoints. Prefer a same-source-tile RALLY; otherwise consider
`KOMETileGameplayDefaults.getArrivalDefault(sourceTile)`. Resolve all anchors using
`KOMETileWorldResolver`; reject wrong dimensions, other tiles, gaps, exclusions,
and unresolved cells. Never apply legacy destination exceptions.

Both valid canonical anchors may be searched, in that order, with duplicate coordinates
removed. If saved RALLY has no safe surface, packaged arrival can still succeed. Search
is exact/local first, then each validated canonical anchor using the same center-inclusive
+/-8 surface resolver. Maximum across three searches: 867 height lookups, 12,138 support
probes, 12,141 placement validations, plus the existing bounded collision/footprint work.
No tile-wide or vertical world scan is added. Original player/mount anchors stay immutable.

## Packaged-data audit

The deterministic audit against real packaged geometry found 15 default arrivals outside
their own tile: T042 (gap), T062 (T074), T242 (T235), T312 (T290), T395 (T382),
T397 (T390), T423 (T417), T447 (T437), T503 (gap), T508 (gap), T542 (gap),
T567 (T564), T573 (T569), T585 (gap), T655 (gap). No packaged coordinates or
legacy exceptions were edited. These anchors are rejected for Join Battle returns;
a valid saved same-tile RALLY can still recover such a source.

## Diagnostics and recovery

Runtime failures: `RETURN_DIMENSION_UNAVAILABLE`, `LOCAL_SURFACE_NOT_FOUND`,
`SOURCE_TILE_UNRESOLVED`, `SOURCE_TILE_FALLBACK_UNAVAILABLE`,
`SOURCE_TILE_FALLBACK_OUTSIDE_TILE`, `MOUNT_RETURN_UNRESOLVED`,
`PHYSICAL_APPLICATION_FAILED`, `FINALIZATION_FAILED`.

The existing central audit emits `join-battle/egress-pending` after a real failed
attempt, with receipt as subject, player UUID as actor, category/concise reason, and
old conflict/source tile in details. Reason detail is limited to 240 characters.
Identical latest retained category/reason for the same receipt is suppressed, even
after restart. Changes append; the existing total cap stays 500. If a row is evicted,
a subsequent failed attempt can establish a new diagnostic. Strategic wait/login/
in-flight/backoff checks alone do not produce a physical-failure event.

RETURNING uses the existing packet `reasonText` and whitelisted category wording;
technical IDs remain in admin audit, not the pending player reason. Runtime retry
state can be reconstructed from receipts; retained audit supplies explanatory text
before a fresh attempt. Five-second polling and 60-second physical-failure backoff
are preserved. No schema, codec, protocol or registry-index change.

Mounted fallback uses existing snapshot/phase/UUID/type/profile/uniqueness checks and
ordered terrain-publication/transfer/rider verification. Interrupted recovery also
checks bounded canonical fallback areas and the current same-source-tile player area
for the recorded UUID. Current location is discovery evidence only, never selection of
a return tile. Missing armed recovery evidence remains pending. Formal Retreat's
strategic-batch and legacy quarantine gates still run before physical preparation.

Truly corrupt source authority, permanently missing dimensions, or no valid bounded
surface remain pending with diagnostics. A separate admin repair mechanism is still
needed to resolve those exceptional cases; this change never fabricates completion.

## Verification

Focused command: `test --tests '*JoinBattle*' --tests '*FormalRetreat*' --tests '*OrdinaryBattle*'
--tests '*TileWorldResolver*' --tests '*TileGameplayParity*' --tests '*ConflictPersistence*'
--tests '*WorldDataSchema*' --tests '*WorldDataAtomicLoad*' --rerun --no-daemon --console=plain`.
Passed: 403 tests, 32 suites, zero failures/errors/skips. Fifteen new deterministic
regressions are in `KOMEJoinBattleSameTileFallbackTest`; the geometry fixture gained an
optional tracker for real mounted transfer testing. Validation uses `Local\KOME-Heavy-Validation`.
No Minecraft runtime or client was launched. Slice 1 live acceptance is user-reported;
Slice 2 evidence here is deterministic automated verification only.

Full `test --rerun --no-daemon --console=plain`: BUILD SUCCESSFUL, 3,942 tests in
368 suites, 3,937 passed, zero failures/errors, five skipped. Two symlink cases skip
when this Windows environment cannot create symbolic links; three original-jar
transformer cases skip without the configured external LOTR validation jars.
`build --no-daemon --console=plain`: BUILD SUCCESSFUL (test task up-to-date after
the full rerun). Gradle emits the existing Java-version/plugin deprecation notices.

## Final concurrency verification

Compared again with KOM-23 `808c886` and current local KOM-20 `c4cb198`.
The sole shared changed file is `KOMEJoinBattlePhysicalAccess.java` with KOM-23;
there are no shared changed files with KOM-20. Extracted full method bodies of
`deploy` and `deployMounted` are exactly unchanged from `2325c5e`; KOM-23's versions
of those methods differ from the common baseline as expected. No guard was copied,
replaced or weakened. The methods remain logically disjoint under the user's clearance.

Exact KOM-91 shared-file changes: `prepareEgress` (existing signature plus data-aware
overload), `recoverArmedEgress` (existing signature plus data-aware overload),
`egress`, `verifyMountedEgress`; `ReturnResolution` constructor/`ok`/`failed` carry
runtime classification. Added `loadReturnArea`, `returnTileMatches`, and
`resolveEgressReturnPlacement`. The original `resolveReturnPlacement` algorithm,
`deploy`, `deployMounted`, all entry guards and mount transfer/publication primitives
are unchanged. `egressMounted` is unchanged and consumes the new safe preparation.

Other production changes: return-only `KOMEJoinBattleEgressService` preparation,
completion result/classification, failed-attempt audit deduplication and pending UI
reason; new read-only `KOMEJoinBattleReturnAuthority`; RETURNING reason construction
in `KOMEPacketJoinBattleViewResponse.forPlayer`. Receipt, conflict persistence,
deployment registry, Join eligibility, Formal Retreat and referee authority files
are unchanged. No staging, commit, push, Linear update or runtime launch.

Routing validation passed: 25 documents, 757 local links, 966 symbol references,
48 exact JUnit selectors. `git diff --check` passed. Final tree has five modified
tracked files and three untracked additions, all unstaged, on the original KOM-91
branch at `2325c5e`. Verdict: READY FOR LIVE TEST; no Slice 2 live acceptance claimed.
