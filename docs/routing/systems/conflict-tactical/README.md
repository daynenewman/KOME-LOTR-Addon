# Conflict records and tactical geometry

[Index](../../INDEX.md)

Owns one canonical conflict record per tile, validated commitments/readiness, ordinary battle resolution, explicit ending/repair, and persistent tactical deployment areas/Siege Complex geometry with administrative editors. Fortified resolution and full battlefield interference enforcement remain separate unfinished systems.

Pass 1 retires the manual player claim-to-war route. `KOMEConquestClaimService.claim`
denies rank/flag claims without changing ownership, timers, diplomacy or wars;
the Tile Command button explains canonical battle resolution. Ruler peaceful
offer/accept and staff transfers remain separate. Pass 2 merged dev `6d1669a`
at `8aea11b`: ordinary lifecycle/resolution, ConflictData v3, recovery WAL and
Campaign health authority are now in this checkout. No progression resolver is
introduced. See [Pass 2](../../../PROGRESSION_PASS2_INTEGRATION_AND_ABILITIES_REPORT.txt).
Existing commitment, Join Battle, retreat/abandonment and Emergency Defense
services remain intact. See [Pass 1 evidence](../../../PROGRESSION_CLEANUP_PASS1_REPORT.txt).

## Authority and entry points

- [KOMEOrdinaryBattleLifecycleService](../../../../src/main/java/kome/common/data/KOMEOrdinaryBattleLifecycleService.java) owns server-active response timing, continuity, abandonment and recovery evidence. [KOMEOrdinaryBattleResolutionService](../../../../src/main/java/kome/common/data/KOMEOrdinaryBattleResolutionService.java) preflights live military/ownership/fortification authority and commits canonical ownership/history; [KOMEOrdinaryBattleOwnershipResolver](../../../../src/main/java/kome/common/data/KOMEOrdinaryBattleOwnershipResolver.java) selects surviving allied/default/continuity candidates. Fortified contexts fail closed rather than bypassing siege completion. `KOMEEvents.processCampaignTick` invokes lifecycle/resolution/recovery after movement and Emergency Defense.
- [KOMECompanyCommandAuthority.newMilitaryOperationDenial](../../../../src/main/java/kome/common/data/KOMECompanyCommandAuthority.java) supplements new player order validation in `KOMECommandTroops.validateCompanyMove`: native-hostile attacks outside declared war require the recognized ruler or staff. Existing queued movement, retreat and recovery are unchanged. New Join Battle participation requires Knight; registered participants retain maintenance eligibility.

- [KOMEConflictService.acceptValidatedCommitment](../../../../src/main/java/kome/common/data/KOMEConflictService.java), `releaseValidatedCommitment`, `endWithMovementHandoff`; [KOMEConflictRecord](../../../../src/main/java/kome/common/data/KOMEConflictRecord.java), [KOMEConflictContracts](../../../../src/main/java/kome/common/data/KOMEConflictContracts.java): expected IDs/revisions, faction participation, validated events and ended snapshots.
- [KOMEConflictMovementService](../../../../src/main/java/kome/common/data/KOMEConflictMovementService.java), [KOMEConflictLifecycleService.diagnose](../../../../src/main/java/kome/common/data/KOMEConflictLifecycleService.java), `previewRepair`, `applyRepair`; [KOMECommandKome](../../../../src/main/java/kome/common/command/KOMECommandKome.java): legal arrival/hold bridge, `/kome conflict` inspection/end/guarded repair.
- [KOMETacticalConfiguration](../../../../src/main/java/kome/common/tactical/KOMETacticalConfiguration.java), [KOMETacticalConfigurationCodec](../../../../src/main/java/kome/common/tactical/KOMETacticalConfigurationCodec.java), [KOMEConflictPersistence](../../../../src/main/java/kome/common/data/KOMEConflictPersistence.java): separate nested codecs within [KOMEWorldData](../../../../src/main/java/kome/common/data/KOMEWorldData.java).
- [KOMETacticalMembershipService](../../../../src/main/java/kome/common/data/KOMETacticalMembershipService.java), [KOMESiegeReadinessResolver](../../../../src/main/java/kome/common/data/KOMESiegeReadinessResolver.java), [KOMETacticalGateReferenceResolver](../../../../src/main/java/kome/common/data/KOMETacticalGateReferenceResolver.java): configured region membership, area capacity, safe gate reference/readiness.
- [KOMETacticalEditService](../../../../src/main/java/kome/common/data/KOMETacticalEditService.java), [KOMETacticalEditRuntime](../../../../src/main/java/kome/common/tactical/edit/KOMETacticalEditRuntime.java), [KOMETacticalEditSessionManager](../../../../src/main/java/kome/common/tactical/edit/KOMETacticalEditSessionManager.java), [KOMETacticalActivityLock](../../../../src/main/java/kome/common/tactical/KOMETacticalActivityLock.java): queued/revision-bound editing, preflight and active-use locks; [KOMEGuiTacticalAreaEditor](../../../../src/main/java/kome/client/gui/KOMEGuiTacticalAreaEditor.java) and [editor client](../../../../src/main/java/kome/client/tactical/) project drafts/snapshots.

- [KOMEJoinBattleService](../../../../src/main/java/kome/common/data/KOMEJoinBattleService.java), [KOMEJoinBattleEntryService](../../../../src/main/java/kome/common/data/KOMEJoinBattleEntryService.java): player-scoped projection/selection, active conflict/company/revision revalidation, governance military restriction, physical entry and durable receipt recovery. [KOMEGuiJoinBattle](../../../../src/main/java/kome/client/gui/KOMEGuiJoinBattle.java) presents the result; [KOMEJoinBattleServiceTest](../../../../src/test/java/kome/common/data/KOMEJoinBattleServiceTest.java) and [KOMEJoinBattleEntryServiceTest](../../../../src/test/java/kome/common/data/KOMEJoinBattleEntryServiceTest.java) locate behavioral coverage. [Artifact-specific governance acceptance](../../../pr26-governance-acceptance-20261010/README.md) proves the scoped denied panel, not complete physical battle entry.

Dependencies/consumers: [movement receipts/holds](../strategic-movement/README.md), [Emergency Defense](../campaign-lifecycle/README.md), [rulers/hostility](../politics-diplomacy/README.md), [tile geometry](../geography/README.md), [physical gates](../physical-siege/README.md), [network/session storage](../runtime-state/README.md).

## Verification and boundaries

Pass 5 [KOMEKnightBattlefieldService](../../../../src/main/java/kome/common/data/KOMEKnightBattlefieldService.java)
records bounded per-target positive damage and real deaths only for the assignee's
compatible active commission, exact assigned raster tile, committed campaign enemy,
pledge and existing governance/hostility permissions. Formal active registration
and a DEPLOYED Join Battle receipt are required; registration alone is insufficient.
A validated military commitment must exist, excluding raw administrative conflicts.
`KOMEOrdinaryBattleResolutionService` snapshots alive deployed presence immediately
before successful canonical closure (which may start return egress), then publishes
personal report readiness after its own winning outcome. No second resolver,
tile transfer or war permission is introduced. Owner/token/faction/timestamps and
consumed conflict identity reject stale/foreign/replayed evidence. Admin ending,
waypoint visits/unlocks/transfers, losses and spectators do not publish credit.
Support-only service, allied winners and fortified outcomes are not integrated.
Single-player field objectives remain available. See `KOMEKnightBattlefieldServiceTest`,
the closure/egress test in `KOMEOrdinaryBattleResolutionServiceTest`, and the
[Pass 5 report](../../../PROGRESSION_PASS5_KNIGHT_FIELD_SERVICE_REPORT.txt).

[Foundation](../../../KOM17_PHASE1_CONFLICT_FOUNDATION.md), [persistence](../../../KOM17_PHASE2_CONFLICT_PERSISTENCE.md), [lifecycle](../../../KOM17_PHASE3_CONFLICT_LIFECYCLE.md), [movement integration](../../../KOM17_PHASE4_MOVEMENT_INTEGRATION.md), and [admin/end handoff](../../../KOM17_PHASE5_LIFECYCLE_ADMIN.md) document the slices. Tactical editor details are in current code/tests.

Tests: [conflict movement](../../../../src/test/java/kome/common/data/KOMEConflictMovementServiceTest.java), [lifecycle](../../../../src/test/java/kome/common/data/KOMEConflictLifecycleServiceTest.java), [area sessions](../../../../src/test/java/kome/common/data/KOMETacticalAreaEditorSessionTest.java), [complex sessions](../../../../src/test/java/kome/common/data/KOMETacticalComplexEditorSessionTest.java), [readiness](../../../../src/test/java/kome/common/data/KOMESiegeReadinessResolverTest.java), [GUI](../../../../src/test/java/kome/client/tactical/KOMETacticalEditorGuiTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMEConflictMovementServiceTest' --tests 'kome.common.data.KOMEConflictLifecycleServiceTest' --tests 'kome.common.data.KOMETacticalAreaEditorSessionTest' --tests 'kome.common.data.KOMESiegeReadinessResolverTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Reject stale IDs/revisions and edits to actively used geometry; resolve unavailable gates/dimensions as unavailable. Inspect successful and rejected physical arrival independently of logical commitment. Ended conflicts retain history and release holds into a paused state; the newer explicit release seam still lacks production callers. Geometry/editor availability does not implement battle scheduling, encirclement/starvation, automatic siege outcomes, or gate-breach-driven capture.
