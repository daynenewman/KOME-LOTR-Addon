# Client presentation and networking

Phase 1 **cdbc051** uses matching **1.0.11-commoner-g15** artifacts. The canonical
Commoner key is new; g14 clients/servers are rejected by the exact-version fence.
Packet IDs/layouts, viewer/revision/dimension guards and military messages remain.
The rank page shows Commoner -> Knight -> Lord -> Prince; tracker allegiance uses
actual native pledge, which never promotes. Earlier g14 sections describe previous
checkpoints. [Full evidence and remaining Phase 1 work](../../../development/PROGRESSION_PHASE1_REPORT.md).

October 17 book polish (**be12f0f**) routes Knight presentation through the existing
measured rank-content path. Current work, promotion and privileges share scissoring,
scroll extent and row hit testing; duplicate Knight summary/faction text is omitted.
The ladder highlights current standing once, headings wrap, and Show Me alone also
reserves footer space. No packet shape or authority changes. Long-text measurement,
scroll/footer/input regressions use `KOMEProgressionBookLayoutTest`; actual Minecraft
appearance remains pending in [the latest checklist](../../../testing/PLAYTEST_LATEST.md).

The first-feedback repair (**f2df9ec**) changes only the Master menu's displayed
ordinary escort/recovery/defense instructions in
[KOMEPacketSerfdomMasterAction](../../../../src/main/java/kome/common/network/KOMEPacketSerfdomMasterAction.java).
Existing saved objective text replaces the misleading “details pending” placeholder;
message fields, action IDs, completion authority and g14 compatibility remain.
[Projection tests](../../../../src/test/java/kome/common/data/KOMEMasterDutyPresentationTest.java)
verify current instructions and report state without granting credit. See
[the repair report](../../../development/FIRST_GAMEPLAY_REPAIR_REPORT.md) for
automated evidence and the still-pending repaired-build client acceptance.

Pass 3/dev sync historical matching-artifact version: `1.0.10-service-g14`.
The existing Rank page, tracker, relationship actions and native offer shells now
publish pledge-based Serf standing and direct three-service promotions. Recommendation
and mentor context comes from server progression history. Rank-derived ability rows
and connection/revision/dimension fences remain unchanged. Old service-g10 and dev integration-g13 artifacts cannot
mix with g14. Native invasion reporting and map objectives target the current Liege;
no additional Prince/trial objective is injected. See
[implementation and acceptance limits](../../../PROGRESSION_PASS3_SERVICE_PROMOTION_REPORT.txt).

Pass 2 historical matching-artifact version: `1.0.9-integration-g9`.
Packet 9 appends bounded, immutable rank-ability outcomes (ID, rank eligibility,
availability, domain reason and optional original-start target). Packet 5 adds
server-authored Build submission eligibility/reason. No packet ID is reused or
added. g8 and g9 cannot mix. `KOMEClientProxy.acceptProgressionData` publishes
abilities only after the existing viewer/connection/revision/dimension fences.
Disconnect/world transitions clear the book outcomes. The existing five-second
progression check compares ability explanations as well as rank/pledge/history;
changed office, bank or company/conflict context triggers a new snapshot.
Context-dependent rows never promise final action authorization. See
[Pass 2](../../../PROGRESSION_PASS2_INTEGRATION_AND_ABILITIES_REPORT.txt).

Pass 1 historical matching-artifact version is `1.0.9-integration-g8`. Packet IDs
remain unchanged. ProgressionData now carries UUID, canonical rank key, enabled
mode, dimension and monotonic publication revision, with bounded text/counts.
The client proxy queues a detached snapshot and fences it by connection/world
epoch; one `KOMEClientData` projection is accepted before book/title publication.
Old/duplicate revisions and wrong viewers/connections/dimensions are rejected.
Disconnect clears the projection; dimension unload clears values but retains the
revision fence and requests fresh state on the next usable world. Native pledge,
rank/admin/completion/mode changes refresh from server authority. g7 and g8 must
not mix. [Pass 1 report](../../../PROGRESSION_CLEANUP_PASS1_REPORT.txt) separates
automated evidence from outstanding connected-client acceptance.

[Index](../../INDEX.md)

The combined progression/KOM-84 contract uses matching-artifact protocol
`1.0.10-service-g14`. Published Join Battle IDs 53–58 are retained; the previously
unpublished progression identity packet moves to 59. Visual marker ID 43 includes
the authoritative current-objective flag, and Standing eligibility retains its
expanded payload. The existing network check rejects older artifacts. Progression
objective map views bypass conquest viewport save/restore, and marker reflection
uses the native LOTR base class so subclasses remain supported. See
[testing report](../../../PROGRESSION_TESTING_FIXES_20261010.md).

Owns authenticated request scheduling, packet codecs/publication, client snapshots, menus/maps/HUDs, and server-record presentation. Server domain services remain authoritative; client caches are transient.

October 11 matching-artifact protocol is `1.0.9-integration-g7`. Marker packet 43
retains its registration ID and adds native item-icon metadata and duty/relief
roles; g6 clients/servers must not mix with g7. The native Quest packet dispatches
to an eligible Prince without changing registration IDs. Personal objective
enemy markers drive the shared native-body hull, including an occluded depth
pass bounded to 96 blocks. GL state restoration and codec/ownership tests are
automated evidence; driver/shader appearance and connected multiplayer behavior
remain live-test requirements. The Knight Service & Rank page uses the same native
book framework and authoritative map focus. See [October 11 report](../../../PROGRESSION_KNIGHT_LORDSHIP_PASS_20261011.md).

## Entry points and boundaries

- [KOMEPacketHandler.init](../../../../src/main/java/kome/common/network/KOMEPacketHandler.java), `enqueueServerTask`, `runPendingServerTasks`: registrations and server-thread dispatch. [KOMEServerTaskQueue](../../../../src/main/java/kome/common/network/KOMEServerTaskQueue.java) bounds/fences KOME requests; requester validity is rechecked at execution.
- [KOMEPacketConquestData](../../../../src/main/java/kome/common/network/KOMEPacketConquestData.java) and [KOMEConquestSnapshotPublisher](../../../../src/main/java/kome/client/KOMEConquestSnapshotPublisher.java): canonical map snapshots and client publication. Inspect [KOMEPopulationProjection](../../../../src/main/java/kome/common/data/KOMEPopulationProjection.java) when totals disagree, not only the GUI.
- [KOMEClientProxy](../../../../src/main/java/kome/client/KOMEClientProxy.java), [KOMEClientTaskQueue.resetSession](../../../../src/main/java/kome/client/KOMEClientTaskQueue.java), [KOMEClientData.resetClientState](../../../../src/main/java/kome/common/data/KOMEClientData.java): client-thread application and connection/world transitions.
- [KOMEGuiConquestCapture](../../../../src/main/java/kome/client/gui/KOMEGuiConquestCapture.java), [KOMEConquestMapOverlay](../../../../src/main/java/kome/client/KOMEConquestMapOverlay.java), [KOMEGuiCompanyList](../../../../src/main/java/kome/client/gui/KOMEGuiCompanyList.java), [KOMEGuiLordMenu](../../../../src/main/java/kome/client/gui/KOMEGuiLordMenu.java): visible consumers and intent creation.
- [KOMEPacketServerRecordRequest](../../../../src/main/java/kome/common/network/KOMEPacketServerRecordRequest.java), [KOMEGuiServerRecords](../../../../src/main/java/kome/client/gui/KOMEGuiServerRecords.java): server-built permitted records, pagination, display. [KOMEGuiTheme](../../../../src/main/java/kome/client/gui/KOMEGuiTheme.java) and [KOMEGuiScrollPanel](../../../../src/main/java/kome/client/gui/KOMEGuiScrollPanel.java) own shared layout.

Join Battle packet registration and [KOMEPopulationWire](../../../../src/main/java/kome/common/network/KOMEPopulationWire.java) now use service-g14 (the earlier Join Battle integration used g5). [Disconnect evidence](../../../pr26-joinbattle-fix-20261009/README.md) records why an older client lacking response 54 must fail the exact-version handshake. [KOMEGuiJoinBattle](../../../../src/main/java/kome/client/gui/KOMEGuiJoinBattle.java) is a client projection; physical entry/receipt authority remains server-owned in the [conflict route](../conflict-tactical/README.md).

Dependencies: [runtime/session ownership](../runtime-state/README.md), [population](../build-population/README.md), [geography](../geography/README.md), [progression](../progression/README.md), [waypoint synchronization](../waypoints/README.md). Integrated Character/Aqua/siege channels use their own dispatch; KOME queue guarantees do not automatically apply to them.

## Verification and gaps

Pass 5 retains matching `1.0.10-service-g14`, packet IDs, fields and registrations.
Knight tier/phase/complication and objective evidence are authoritative server save
data. Existing progression text and visual-location projections present the current
stage: rescue refuge, reinforcement commander/return, theft escape and personal
report. No commission-selection menu or client evidence authority exists.
`KOMEKnightCommissionPresentation`, `KOMEProgressionTrackerSnapshot` and
`KOMEVisualLocationService` own the presentation. Stealth objectives have no
decorative kill-count bar. Packet registration, server-only loading and transformer
registry tests retain existing fences. Native sounds/particles execute in the
disposable Forge fixture; visible/audible connected-client acceptance is pending.
See [Pass 5 report](../../../PROGRESSION_PASS5_KNIGHT_FIELD_SERVICE_REPORT.txt).

Existing references: [server queue](../../../KOME_SERVER_TASK_QUEUE.md), [conquest sync](../../../KOME_CONQUEST_SYNC.md), [records](../../../KOME_SERVER_RECORDS.md), [GUI handoff](../../../KOME_GUI_HANDOFF.md). Tests: [server queue](../../../../src/test/java/kome/common/network/KOMEServerTaskQueueTest.java), [client queue](../../../../src/test/java/kome/client/KOMEClientTaskQueueTest.java), [sync](../../../../src/test/java/kome/common/network/KOMEConquestSyncTest.java), [record presentation](../../../../src/test/java/kome/client/gui/KOMEGuiServerRecordsPresentationTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.network.KOMEServerTaskQueueTest' --tests 'kome.client.KOMEClientTaskQueueTest' --tests 'kome.common.network.KOMEConquestSyncTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Trace persistence → packet construction/delivery → client projection → GUI eligibility. Test disconnect/reconnect, dimension change, rejected requests, and reopened screens with a connected client before claiming visible correctness.

The previous ProgressionData-to-book-only gap is corrected in Pass 1: the handler
calls `acceptProgressionData`, which publishes the accepted authoritative rank,
completion/mode/faction projection into `KOMEClientData.progressions` before the
book/title update. Server action checks remain independent of this transient cache.

## KOM-18 merge compatibility

The merged g14 contract retains dev's progression identities at 59, its objective/eligibility payloads, and KOM-84's correlated Join Battle shapes at 53-58. The removed KOM-89 presentation workaround belonged to the incompatible 1.0.8 artifact; the exact-version handshake rejects that artifact before packet handling. No custom hired-presentation queue or packet is restored. ID 60 remains unused. Dev-only offline identity opt-in and its credential guards remain in Gradle.

## KOM-84 reconciled packet and workflow boundary

ID 60 remains unused. Destination queries use 61/62, confirmed single-company movement uses 63/64,
source metadata/source-scoped destination queries use 65/66, and Rally sessions use 67/68.
KOM-84 payloads are unchanged by discriminator reassignment. Matching g14 artifacts are required;
service-g10, integration-g13 and earlier incompatible artifacts are rejected. Server intents retain the bounded server-thread handlers;
tactical ID 48 continues to own its separate bounded queue.

Map Move Here, Move To, Rally, root Join and tile-local Units share connection-fenced client state.
Rally executes only frozen server-preview inclusions through fresh canonical company submissions;
Units uses read-only source metadata. Join screen ownership/correlation, success-only close and
non-pausing command screens remain client presentation rules, not deployment authority.
Progression and destination browsing both reset on connect/disconnect/world unload;
progression viewer/revision/epoch fences and movement connection/sequence fences remain separate.
Both the native map input transformer and progression regional-seat transformer are registered.
Verify registration/protocol/public-access tests, company network tests and Join GUI tests alongside
Campaign health/recovery/reconstruction and ordinary battle suites after reconciliation.

## KOM-91 return surface recovery (Slice 1)

Join Battle return placement keeps the exact persisted pre-entry pose first. If unsupported,
`KOMEJoinBattlePhysicalAccess.resolveReturnPlacement` uses the center column and deterministic
nearest-first columns within the existing +/-8 X/Z envelope. Each column probes nine support
blocks near saved Y and five near the native heightmap, deriving standing Y from collision-box
tops and retaining full terrain/liquid/body and mount/rider clearance checks. There is no full
vertical column scan: at most 289 height lookups, 4,046 support-block probes and 4,047 placement
validations including the exact-pose check. These are candidate-generation limits; existing
collision/footprint validation performs its normal bounded local checks as well.

The canonical captured tile resolver fences fallbacks to the original player return tile when
that anchor resolves. Mounted placement uses the persisted mount base but the player's return
anchor owns this fence. An unresolved source receives only the same local physical search;
no tile, spawn or strategic destination is substituted. There is no flight exemption inferred
from current capabilities. No receipt/schema, conflict-end, registry, retry or transfer-phase
change was part of Slice 1. Slice 2 below extends only egress recovery and diagnostics.

Regressions: `KOMEJoinBattleReturnGeometryTest`, `KOMEJoinBattleSurfaceRecoveryTest`, and the
ordinary-resolution integration in `KOMEOrdinaryBattleResolutionServiceTest`. Retain existing
mounted publication, entry/persistence, Formal Retreat and canonical tile-resolution suites.
Automated verification does not substitute for live return/terrain confirmation.

## KOM-91 same-source-tile fallback and diagnostics (Slice 2)

`KOMEJoinBattlePhysicalAccess.prepareEgress` keeps exact/local recovery first, then
uses the read-only `KOMEJoinBattleReturnAuthority`: saved WorldData RALLY first,
packaged `KOMETileGameplayDefaults.getArrivalDefault` second, with duplicate anchors
removed. Every canonical anchor, rider offset and search candidate must resolve to
the original persisted player source tile in the source dimension. Gaps, exclusions,
other tiles and historical out-of-tile arrival exceptions never authorize fallback.
Unresolved original anchors receive exact/local recovery only. Entry rollback still
uses the original resolver without the canonical fallback extension.

Each of at most two canonical anchors reuses Slice 1's center-inclusive +/-8 surface
search and physical checks. Across original, saved and packaged searches the maximum
is 867 height lookups, 12,138 support probes and 12,141 placement validations; existing
footprint/collision queries remain additional bounded work. Closure still follows
verified physical application only. Mounted interrupted-egress discovery loads bounded
original/fallback areas and the current same-source-tile player area to find the exact
recorded UUID; that discovery pose does not authorize a return destination or missing-evidence
recreation. UUID/type/profile, snapshot, attachment, uniqueness, rollback and publication
contracts remain in force. Formal Retreat gates run before physical preparation.

`KOMEJoinBattleEgressService` classifies real failed attempts without receipt fields.
The existing capped persistent audit records receipt/player/conflict/source-tile identity
and concise classification/reason. It compares the latest retained event for that exact
receipt, so identical retries (including after restart) do not append again; changed
category/reason does. The global audit cap remains 500. Login waits, five-second polling,
60-second physical-failure backoff and durable-receipt restart recovery remain unchanged.

`KOMEPacketJoinBattleViewResponse.forPlayer` uses the existing `reasonText` field for
RETURNING. `pendingReason` maps classifications to concise player wording without raw
receipt/conflict/player IDs, using runtime failure state or the last retained audit after
restart. No packet shape/protocol change. Truly impossible source authority stays pending;
operator repair remains separate work.

`KOMEJoinBattleSameTileFallbackTest` covers real search/application/closure, unrelated
later Join, mounted transfer/interruption, boundary rejection, default rejection, NBT
restart, diagnostic suppression/change, player text and Formal Retreat precedence.
See [Slice 2 evidence and concurrency boundary](../../../kom91-slice2/README.md).
These deterministic tests do not claim connected-client or live acceptance for Slice 2.

## KOM-88 progression tracker render isolation

`KOMEProgressionTrackerOverlay.drawTracker` owns an exception-safe all-attribute and
matrix scope around its textures, progress bar, text, item and chat-edit outline.
Both the HUD and chat redraw use that same protected path. Native LOTR alignment
renders later at FML render-tick END and relies on inherited alpha/blend state;
the tracker restores its incoming state rather than overriding native alignment.
Minecraft 1.7.10 binds textures directly, so no ResourceLocation restoration cache
is introduced. `KOMEProgressionTrackerOverlayTest` checks scope coverage and normal/
exceptional cleanup in bytecode; manual live visual acceptance remains required.

## KOM-97 destination browser budget continuation

Move Here and Attack both open `KOMECompanyDestinationSelector` through
`KOMEConquestMapOverlay.openCompanySelector`. The destination query already returns
completed rows, a catalogue token and the last completed company ID when its shared
arrival budget expires. The next request excludes that completed prefix and evaluates
the first deferred company with a fresh budget. Routes exceeding the individual
limit remain explicit unknown-eligibility rows, never eligible or denied guesses.

The selector now accepts `ARRIVAL_BUDGET_EXCEEDED` as a recoverable page only with
`hasMore`, a nonempty token and a strictly advancing cursor (and ordered rows ending
at that cursor when present). It applies the same retained-catalogue identity check
as ordinary pages. The existing bounded rolling window and All/Ready/Arrive Now
filters retain only authoritative projections; zero matches do not end continuation.
The panel offers Continue instead of restarting the prefix with Refresh. Filter and
browse follow-ups still stop after three pages per gesture; throttle responses wait
for their server deadline and retry the outstanding cursor. Nonadvancing evidence
fails closed without an automatic retry loop. No server or protocol change.

Verification: `KOMECompanyDestinationQueryTest`, `KOMECompanyDestinationSelectorTest`,
`KOMECompanyDestinationPagingTest`, `KOMECompanyDestinationBrowsingTest`, and
`KOMEMapRadialInteractionTest` pass (176 tests). The paging regression first reproduced
ERROR on a real budget-exhausted query, then traversed ten companies in four bounded
pages without skips or duplicate accepted rows. Radial adapter tests cover both
Move Here and Attack, including movement confirmation and adaptive Join eligibility.
Move To/source queries, Rally and Units retain their separate authority and flows.
These automated checks do not establish connected-client/live acceptance.
