# Server cadence, daily credit, movement, and campaign consequences

[Index](../INDEX.md) · [Movement](../systems/strategic-movement/README.md) · [Campaign lifecycle](../systems/campaign-lifecycle/README.md)

1. Trigger: [KOMEEvents.onServerTick](../../../src/main/java/kome/common/data/KOMEEvents.java), START, initializes each world data instance, then drains authenticated [KOME requests](../systems/client-network/README.md). Shared canonical data is deduplicated for the roughly one-second campaign pass. Waypoint/marker publication and alliance lifecycle reconciliation are adjacent work, not a single atomic daily transaction.
2. Startup gate: [KOMEPopulationPayoutRuntime.onStartup](../../../src/main/java/kome/common/data/KOMEPopulationPayoutRuntime.java) initializes/skips development, applies configured population catch-up, and anchors the movement schedule. Startup does not grant credit for every missed movement day.
3. Live order: `processCampaignTick` calls [KOMECommandTroops.resetDailyMovementAllowances](../../../src/main/java/kome/common/command/KOMECommandTroops.java), then `processMovementTick`, pending Emergency Defense, muster due processing, development/payout via `onLiveCheck`, and defeat reconciliation only after a successful payout result. Safe stewardship demobilization follows outside that method.
4. Departure/arrival: movement checks the current effective [graph and passage](../systems/geography/README.md), company authority, phase, and [KOMEMovementDepartureGuard.evaluate](../../../src/main/java/kome/common/data/KOMEMovementDepartureGuard.java) before staging/credit spending. `processArrivals` preflights [conflict receipts and placement](../systems/conflict-tactical/README.md), restores NPC/mount snapshots with preserved identity/health, then commits location/hold/history and schedules remaining steps. Failed physical placement remains retryable/pending according to the order's state.
5. Persistence/sync: company credit, movement boundary/order/history, hired snapshots and conflict commitments serialize through [KOMEWorldData](../../../src/main/java/kome/common/data/KOMEWorldData.java); spawned entities/chunks save separately. Conquest/company projections and [KOMEPacketUnitMapMarkers](../../../src/main/java/kome/common/network/KOMEPacketUnitMapMarkers.java) communicate visible location.

```mermaid
flowchart TD
  Start[Server START and successful startup] --> Credit[Reset due daily movement credit]
  Credit --> Movement[Revalidate departure and process arrival]
  Movement --> Defense[Pending Emergency Defense]
  Defense --> Muster[Muster due check]
  Muster --> Development[Live Build development]
  Development --> Payout[Phase-gated population payout]
  Payout -->|success| Defeat[Defeat reconciliation]
```

Verification: [boundary ordering](../../../src/test/java/kome/common/command/KOMECampaignBoundaryMovementTest.java), [credit](../../../src/test/java/kome/common/data/KOMEMovementAllowanceTest.java), [departure](../../../src/test/java/kome/common/command/KOMEMovementDepartureGuardTest.java), [queued barrier/recovery](../../../src/test/java/kome/common/command/KOMEMountainBarrierRecoveryTest.java), [movement](../../../src/test/java/kome/common/command/KOMECommandTroopsMovementTest.java). Check direct command/recovery and tick paths, cold restart, mounted HP/UUIDs, and client marker position separately. Conflict end produces paused holds; explicit release/resume and specialized exterior placement remain unwired seams. A `RESET` phase value does not establish the pending branch's complete reset/return pipeline in this checkout.
