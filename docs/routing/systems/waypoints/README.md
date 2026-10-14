# Public waypoints, approval, visibility, and travel

[Index](../../INDEX.md) · [Travel flow](../../flows/waypoint-travel.md)

Owns KOME public waypoint proposals/registry, native waypoint bridges, access decisions, and visibility publication. Native LOTR still owns target/cooldown/final teleport state; tile rally links are separate strategic metadata.

Pass 1 synchronizes legacy travel permission reads with the server progression
snapshot and uses its current faction context on the client once available.
Request/final server guards, native cooldown/attack/sleep/discovery and territory
policy remain unchanged. Pass 1 did not implement original-start return;
Pass 2 now adds `KOMEWaypointAccessService.hasTravelPermission` for the exact
applied `PlayerRaceData` starting waypoint at Serf-or-higher standing.
`originalStartingWaypoint` never invents a missing or legacy destination.
Native request, addon request and final guard share this permission supplement.
Native discovery/region, cooldown, safety, sleep and territory rules still apply.
The Rank-page return row uses the existing waypoint request packet. There is no
new teleport service or universal rank travel ladder. See
[Pass 2](../../../PROGRESSION_PASS2_INTEGRATION_AND_ABILITIES_REPORT.txt) and
[Pass 1](../../../PROGRESSION_CLEANUP_PASS1_REPORT.txt).

## Authority and entry points

- [KOMEPublicWaypointRegistry.propose](../../../../src/main/java/kome/common/data/KOMEPublicWaypointRegistry.java), `adjust`, `approveProposal`, `reject`, `move`, `remove`: revision-checked proposals and approved identities/history/quarantine. The `PublicWaypoints` section persists in [KOMEWorldData](../../../../src/main/java/kome/common/data/KOMEWorldData.java).
- [KOMECommandConquest](../../../../src/main/java/kome/common/command/KOMECommandConquest.java): public proposal/review/administration and tile waypoint links. [KOMEPublicWaypoint](../../../../src/main/java/kome/common/data/KOMEPublicWaypoint.java), [KOMEWaypointProposal](../../../../src/main/java/kome/common/data/KOMEWaypointProposal.java), [KOMETileWaypointLink](../../../../src/main/java/kome/common/data/KOMETileWaypointLink.java) distinguish approved destination, request, and strategic rally link.
- [KOMEWaypointAccessService.evaluatePlayer](../../../../src/main/java/kome/common/data/KOMEWaypointAccessService.java), `allowNativeRequest`, `allowFinalTravel`: native/fellowship/public policy and final travel revalidation. [KOMEPacketWaypointTravelRequest](../../../../src/main/java/kome/common/network/KOMEPacketWaypointTravelRequest.java) checks fast-travel permission and native restrictions before setting a target.
- [KOMEPublicWaypointSync](../../../../src/main/java/kome/common/network/KOMEPublicWaypointSync.java), [KOMEPacketPublicWaypoints](../../../../src/main/java/kome/common/network/KOMEPacketPublicWaypoints.java), [KOMEPublicWaypointClientState](../../../../src/main/java/kome/common/data/KOMEPublicWaypointClientState.java): recipient-specific revision/chunk publication, client registry projection.
- [KOMEWaypointTransformer](../../../../src/main/java/kome/core/KOMEWaypointTransformer.java), [KOMEPublicWaypointTransformer](../../../../src/main/java/kome/core/KOMEPublicWaypointTransformer.java), [KOMEConquestMapOverlay](../../../../src/main/java/kome/client/KOMEConquestMapOverlay.java): native request/final travel and visible map hooks.

Dependencies: [exact tile containment/control](../geography/README.md), [FAST_TRAVEL permission](../progression/README.md), [client session/projection](../client-network/README.md), [capital/rally consumers](../campaign-lifecycle/README.md). Approval, visibility, progression, and final travel are independent checks.

## Verification and gaps

References: [public access policy](../../../KOME_PUBLIC_ACCESS_POLICY.md), [waypoint handoff](../../../public-waypoints-20261002/HANDOFF.md), [resolver](../../../KOME_TILE_WORLD_RESOLUTION.md). Tests: [workflow](../../../../src/test/java/kome/common/data/KOMEWaypointWorkflowTest.java), [access](../../../../src/test/java/kome/common/data/KOMEWaypointAccessServiceTest.java), [sync](../../../../src/test/java/kome/common/network/KOMEPublicWaypointSyncTest.java), [native hook](../../../../src/test/java/kome/core/KOMEWaypointTransformerTest.java), [public hook](../../../../src/test/java/kome/core/KOMEPublicWaypointTransformerTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMEWaypointWorkflowTest' --tests 'kome.common.data.KOMEWaypointAccessServiceTest' --tests 'kome.common.network.KOMEPublicWaypointSyncTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Reproduce proposal/revision/approval, stale rejection, reconnect visibility, unlock, request and final teleport with a connected client. Native safeguards still apply. Pass 1 fixes the source-wired permission projection; visible/native final travel still needs connected acceptance.
