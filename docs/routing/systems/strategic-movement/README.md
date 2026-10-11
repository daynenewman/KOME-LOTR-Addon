# Strategic routes and physical arrival

[Index](../../INDEX.md) Â· [Daily flow](../../flows/daily-movement.md)

Owns campaign route preview/confirmation, enduring daily credit, departures, physical transport/arrival, confinement, retries, and recovery. It does not own swimming/crawling; see [movement compatibility](../movement-compatibility/README.md).

[KOM-59 T351/T352](../../../kom59-ithilien-route-20261010/README.md) supplies a corrected open default for new permitted direct routes in both directions. Effective saved barriers and military passage checks still apply; stored detours are not automatically rewritten. [Focused regressions](../../../../src/test/java/kome/common/data/KOMEIthilienRouteCorrectionTest.java) cover command search, departure authority, override reload and queued-route preservation.

## Authority and entry points

- [KOMECommandTroops](../../../../src/main/java/kome/common/command/KOMECommandTroops.java): `previewCompanyMove`, `moveCompany`, `processMovementTick`, `processArrivals`, `scheduleNextRouteStep`, `anchorMovementSchedule`, `resetDailyMovementAllowances`; `/troops` and [company move confirmation](../../../../src/main/java/kome/client/gui/KOMEGuiCompanyMoveConfirm.java) reach these paths through registered packets.
- [KOMEArmyMovementOrder](../../../../src/main/java/kome/common/data/KOMEArmyMovementOrder.java), [KOMEArmyCompany.getTilesPerDay](../../../../src/main/java/kome/common/data/KOMEArmyCompany.java), [KOMEMovementDayService](../../../../src/main/java/kome/common/data/KOMEMovementDayService.java): persisted route/status, company movement credit, configured mounted/foot entitlement. [KOMEWorldData](../../../../src/main/java/kome/common/data/KOMEWorldData.java) also stores movement boundary/history.
- [KOMEMovementRoutePreview](../../../../src/main/java/kome/common/data/KOMEMovementRoutePreview.java), [KOMEMovementDepartureGuard.evaluate](../../../../src/main/java/kome/common/data/KOMEMovementDepartureGuard.java), [KOMEMovementAccessService](../../../../src/main/java/kome/common/data/KOMEMovementAccessService.java): effective edge and current authority checks before staging/credit spending.
- [KOMEConflictMovementService](../../../../src/main/java/kome/common/data/KOMEConflictMovementService.java), [KOMEConflictMovementHandoff](../../../../src/main/java/kome/common/data/KOMEConflictMovementHandoff.java), [KOMEStrategicArrivalPlacement.preflight](../../../../src/main/java/kome/common/data/KOMEStrategicArrivalPlacement.java): validated conflict receipt/holds, explicit release seam, placement policy before chunks/spawning.
- [KOMEEntitySnapshots](../../../../src/main/java/kome/common/data/KOMEEntitySnapshots.java), [KOMEStrategicDeploymentResolver](../../../../src/main/java/kome/common/data/KOMEStrategicDeploymentResolver.java), [KOMEHaltedUnitProtection](../../../../src/main/java/kome/common/data/KOMEHaltedUnitProtection.java): NPC/mount state, placement clearance, stationary protection. [KOMEEvents](../../../../src/main/java/kome/common/data/KOMEEvents.java) owns tick/join/death observations and marker synchronization.

Dependencies: [company identity/hiring](../hiring-companies/README.md), [graph versus raster](../geography/README.md), [passage/delegation](../politics-diplomacy/README.md), [conflict/tactical authority](../conflict-tactical/README.md), [phase/capital](../campaign-lifecycle/README.md), [client projections](../client-network/README.md).

[KOM-59 approved mountain cut](../../../kom59-mountain-separation-20261010/README.md) separates five raster contacts without changing route defaults, bridge/pass edges, configured arrivals or persisted movement. Exclusion metadata does not authorize strategic traversal.

## Verification and gaps

References: [KOM-80 queued edge checks](../../../kom80-mountain-barriers/README.md), [tile confinement](../../../KOM46_PHASE5_CAMPAIGN_TILE_CONFINEMENT.md), [conflict arrivals](../../../KOM17_PHASE4_MOVEMENT_INTEGRATION.md), [ended holds](../../../KOM17_PHASE5_LIFECYCLE_ADMIN.md). Tests: [movement](../../../../src/test/java/kome/common/command/KOMECommandTroopsMovementTest.java), [credit](../../../../src/test/java/kome/common/data/KOMEMovementAllowanceTest.java), [recovery](../../../../src/test/java/kome/common/command/KOMEMountainBarrierRecoveryTest.java), [departure guard](../../../../src/test/java/kome/common/command/KOMEMovementDepartureGuardTest.java), [arrival placement](../../../../src/test/java/kome/common/data/KOMEStrategicArrivalPlacementTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.command.KOMECommandTroopsMovementTest' --tests 'kome.common.data.KOMEMovementAllowanceTest' --tests 'kome.common.command.KOMEMountainBarrierRecoveryTest' --tests 'kome.common.command.KOMEMovementDepartureGuardTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Test queued-edge changes and retreat/resume/force-arrival paths, save/reload credit, UUID/mount/HP preservation, failed spawn rollback, and marker/UI state. Startup anchors missed days; population catch-up does not grant missed movement days. The audit's hardcoded speed defect is fixed here.

Conflict release methods exist but have no production caller at this baseline; an ended hold must not be assumed to resume automatically. Arrival placement defaults to ordinary placement; the provider seam can retain pending exterior requirements, but no specialized provider is installed in production. Neither seam proves complete battle/siege resolution.
