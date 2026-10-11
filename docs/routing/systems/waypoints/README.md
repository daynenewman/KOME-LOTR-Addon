# Public waypoints, approval, visibility, and travel

[Index](../../INDEX.md) · [Travel flow](../../flows/waypoint-travel.md)

Owns KOME public waypoint proposals/registry, native waypoint bridges, access decisions, and visibility publication. Native LOTR still owns target/cooldown/final teleport state; tile rally links are separate strategic metadata.

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

Use [shared prerequisites](../../INDEX.md). Reproduce propose → revise → approve, stale approval rejection, reconnect visibility, unlock, target request, and final teleport with a connected client. Native cooldown/under-attack/sleep/config guards still apply. A correct server unlock does not prove the client permission cache is current; see the [projection gap](../client-network/README.md).
