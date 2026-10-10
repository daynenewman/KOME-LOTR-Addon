# Membership, rulers, diplomacy, and delegated authority

[Index](../../INDEX.md)

Owns ruler authorization, bilateral native LOTR relations/pending consent, foreign construction, voluntary company delegation, pledge cleanup, and restricted kingless stewardship. Legacy alliance records remain for compatibility; the historical four-stage ladder is not the current relation authority.

## Entry points and state

- [KOMEDiplomacyService.getRelation](../../../../src/main/java/kome/common/data/KOMEDiplomacyService.java), `requestIncrease`, `acceptPendingIncrease`, `worsenRelation`: native `LOTRFactionRelations` overrides own effective relations; KOME persists canonical consent records in [KOMEWorldData](../../../../src/main/java/kome/common/data/KOMEWorldData.java). Native LOTR player data owns pledge/alignment.
- [KOMERulerService](../../../../src/main/java/kome/common/data/KOMERulerService.java), [KOMERulerAuthorization](../../../../src/main/java/kome/common/data/KOMERulerAuthorization.java): persisted ruler UUID/name and acting-faction authorization. [KOMECommandKome](../../../../src/main/java/kome/common/command/KOMECommandKome.java) exposes ruler/admin operations.
- [KOMEForeignConstructionService](../../../../src/main/java/kome/common/data/KOMEForeignConstructionService.java), [KOMECompanyDiplomacyAuthorization](../../../../src/main/java/kome/common/data/KOMECompanyDiplomacyAuthorization.java), and company delegation/reclaim paths in [KOMECommandTroops](../../../../src/main/java/kome/common/command/KOMECommandTroops.java): specific grants and consequences; [KOMEMovementAccessService](../../../../src/main/java/kome/common/data/KOMEMovementAccessService.java) owns route passage checks.
- [KOMEPledgeReleaseService](../../../../src/main/java/kome/common/data/KOMEPledgeReleaseService.java), [KOMEWartimeStewardshipService](../../../../src/main/java/kome/common/data/KOMEWartimeStewardshipService.java): persisted cleanup/tombstones and restricted temporary authority; [KOMEEvents](../../../../src/main/java/kome/common/data/KOMEEvents.java) observes pledge/tick changes.
- [KOMECommandAlliance](../../../../src/main/java/kome/common/command/KOMECommandAlliance.java), [KOMEPacketAllianceAction](../../../../src/main/java/kome/common/network/KOMEPacketAllianceAction.java), [KOMEGuiAllianceUnified](../../../../src/main/java/kome/client/gui/KOMEGuiAllianceUnified.java), [KOMEDiplomacyClientSync](../../../../src/main/java/kome/common/data/KOMEDiplomacyClientSync.java): diplomacy intents and native/client projections.

Consumers/dependencies: [Build ownership/permission](../build-population/README.md), [movement](../strategic-movement/README.md), [companies](../hiring-companies/README.md), [wars and Emergency Defense](../campaign-lifecycle/README.md), [progression/pledge](../progression/README.md), [network records](../client-network/README.md).

## Verification and boundaries

References: [integration decisions](../../../KOME_DEV_INTEGRATION_DECISIONS.md), [public access policy](../../../KOME_PUBLIC_ACCESS_POLICY.md), [Emergency Defense authority](../../../KOM75_PHASE1_EMERGENCY_DEFENSE.md). [Alliance system](../../../KOME_ALLIANCE_SYSTEM.md) and [migration](../../../KOME_ALLIANCE_MIGRATION.md) explain historical state; their stage benefits are not sufficient evidence of current permission.

Tests: [diplomacy](../../../../src/test/java/kome/common/data/KOMEDiplomacyServiceTest.java), [ruler authority](../../../../src/test/java/kome/common/data/KOMERulerAuthorizationTest.java), [company diplomacy](../../../../src/test/java/kome/common/data/KOMECompanyDiplomacyAuthorizationTest.java), [foreign construction](../../../../src/test/java/kome/common/data/KOMEForeignConstructionServiceTest.java), [conflict-safe stewardship](../../../../src/test/java/kome/common/data/KOMEWartimeStewardshipConflictSafetyTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMEDiplomacyServiceTest' --tests 'kome.common.data.KOMERulerAuthorizationTest' --tests 'kome.common.data.KOMECompanyDiplomacyAuthorizationTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Relations, ruler UUID, player pledge, construction grant, delegation, and wartime authority must be inspected independently. Worsened relations revalidate movement/delegation/stewardship; an improvement needs the appropriate consent. Waypoint travel has its own policy. Preserve native save/sync effects as well as KOME records when changing relation behavior.

One unresolved exception remains: native foreign farmhand hiring in [KOMEEvents](../../../../src/main/java/kome/common/data/KOMEEvents.java) still calls [KOMEAllianceAuthority.canFactionHireAlliedFarmhand](../../../../src/main/java/kome/common/data/KOMEAllianceAuthority.java), which consumes legacy Stage 1. Do not infer that the canonical Friends/Allies relation grants this entitlement; the intended replacement rule remains a decision.
