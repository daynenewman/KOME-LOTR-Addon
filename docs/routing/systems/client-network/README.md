# Client presentation and networking

Pass 3 current matching-artifact version: `1.0.10-service-g10`.
The existing Rank page, tracker, relationship actions and native offer shells now
publish pledge-based Serf standing and direct three-service promotions. Recommendation
and mentor context comes from server progression history. Rank-derived ability rows
and connection/revision/dimension fences remain unchanged. Old g9 artifacts cannot
mix with g10. Native invasion reporting and map objectives target the current Liege;
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

October 10 progression integration uses matching-artifact protocol
`1.0.9-integration-g6`. Published Join Battle IDs 53–58 are retained; the previously
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

Join Battle packet registration and [KOMEPopulationWire](../../../../src/main/java/kome/common/network/KOMEPopulationWire.java) now use integration-g5. [Disconnect evidence](../../../pr26-joinbattle-fix-20261009/README.md) records why an older client lacking response 54 must fail the exact-version handshake. [KOMEGuiJoinBattle](../../../../src/main/java/kome/client/gui/KOMEGuiJoinBattle.java) is a client projection; physical entry/receipt authority remains server-owned in the [conflict route](../conflict-tactical/README.md).

Dependencies: [runtime/session ownership](../runtime-state/README.md), [population](../build-population/README.md), [geography](../geography/README.md), [progression](../progression/README.md), [waypoint synchronization](../waypoints/README.md). Integrated Character/Aqua/siege channels use their own dispatch; KOME queue guarantees do not automatically apply to them.

## Verification and gaps

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

The progression merge retains the matching-artifact integration-g8 contract and Pass 1 projection, including progression identities at 59 and Join Battle at 53–58. The removed KOM-89 presentation workaround belonged to the incompatible 1.0.8 artifact; the exact-version handshake rejects that artifact before packet handling. No custom hired-presentation queue or packet is restored. ID 60 remains unused. Dev-only offline identity opt-in and its credential guards remain in Gradle.
