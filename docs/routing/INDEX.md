# KOME task routing

1. Pick the closest symptom below; read that guide and the linked flow if applicable.
2. Inspect the named entry point and its tests in your checkout. Follow related guides across authority, storage, and presentation boundaries.
3. Search beyond the map if callers, symptoms, or current code disagree. Update affected routes with behavior changes; this is a navigation aid, not an exhaustive audit.

Verified against code baseline **b826ef35a0fb88ce9b2d58129ed307e9986b43d7** on 2026-10-06. The starting reference was the October 2 system audit at **585e12a1c91cfa38ef8ed5d2da5e3580d1b8a238**. [Provenance and status changes](PROVENANCE.md) records its preserved location, subsequent changes, and contradictions. Guide claims describe source wiring, not live multiplayer acceptance.

After PR #26 merged, the affected runtime/persistence, reset/governance, daily sequencing and Join Battle routes were refreshed against dev **9b765f5c8e8b8fb141d5a4b55c361377f109955f** on **2026-10-10**. Other guides retain their original targeted-review provenance; a passing link/symbol checker is not a new audit of every gameplay claim. See [current-dev refresh](PROVENANCE.md).

| Task or symptom | System guide | Primary code entry points | Related systems | Verification |
|---|---|---|---|---|
| Incorrect tile detection, borders, map/HUD location | [Geography](systems/geography/README.md) | [KOMETileWorldResolver.resolveWorldPosition](../../src/main/java/kome/common/data/KOMETileWorldResolver.java); [KOMEMapBorders](../../src/main/java/kome/client/KOMEMapBorders.java) | Client sync; movement graph; Build placement | KOMETileWorldResolverTest; KOMETileGameplayParityTest; KOMEMapRenderLifecycleTest |
| Builds produce incorrect population | [Builds and population](systems/build-population/README.md) | [KOMEPopulationDevelopmentService.processLiveDueBoundaries](../../src/main/java/kome/common/data/KOMEPopulationDevelopmentService.java); [KOMEPopulationPayoutProcessor.processLiveDueBoundaries](../../src/main/java/kome/common/data/KOMEPopulationPayoutProcessor.java) | Season; ownership; hiring; [payout flow](flows/build-population.md) | KOMEPopulationDevelopmentServiceTest; KOMEPopulationPayoutProcessorTest |
| Hiring charges incorrect population or groups the wrong company | [Hiring and companies](systems/hiring-companies/README.md) | [KOMECampaignRecruitmentService.recruit](../../src/main/java/kome/common/data/KOMECampaignRecruitmentService.java); [KOMEUnitPopulationCostService.calculate](../../src/main/java/kome/common/data/KOMEUnitPopulationCostService.java) | Population; progression; geography; [hire flow](flows/troop-hiring.md) | KOMECampaignRecruitmentServiceTest; KOMEUnitPopulationCostServiceTest |
| Troops fail to move, arrive, retreat, or return | [Strategic movement](systems/strategic-movement/README.md) | [KOMECommandTroops.processMovementTick](../../src/main/java/kome/common/command/KOMECommandTroops.java); [KOMEMovementDepartureGuard.evaluate](../../src/main/java/kome/common/data/KOMEMovementDepartureGuard.java) | Diplomacy; conflicts; companies; physical entities; [daily flow](flows/daily-movement.md) | KOMECommandTroopsMovementTest; KOMEMountainBarrierRecoveryTest; KOMEMovementDepartureGuardTest |
| Waypoint approval, visibility, or travel fails | [Waypoints](systems/waypoints/README.md) | [KOMEPublicWaypointRegistry.approveProposal](../../src/main/java/kome/common/data/KOMEPublicWaypointRegistry.java); [KOMEWaypointAccessService.allowFinalTravel](../../src/main/java/kome/common/data/KOMEWaypointAccessService.java) | Progression; geography; native LOTR; client sync; [travel flow](flows/waypoint-travel.md) | KOMEWaypointWorkflowTest; KOMEPublicWaypointSyncTest; KOMEWaypointTransformerTest |
| Alliance access, ruler, construction, or delegation permission fails | [Politics and diplomacy](systems/politics-diplomacy/README.md) | [KOMEDiplomacyService](../../src/main/java/kome/common/data/KOMEDiplomacyService.java); [KOMERulerAuthorization](../../src/main/java/kome/common/data/KOMERulerAuthorization.java) | Movement; Builds; stewardship; progression | KOMEDiplomacyServiceTest; KOMECompanyDiplomacyAuthorizationTest; KOMERulerAuthorizationTest |
| Rank, NPC relationship, courier, quest, or unlock is wrong | [Progression](systems/progression/README.md) | [KOMEProgressionAutoCompleter](../../src/main/java/kome/common/data/KOMEProgressionAutoCompleter.java); [KOMEProgressionNpcInteractionService.interact](../../src/main/java/kome/common/data/KOMEProgressionNpcInteractionService.java); [KOMEObjectiveInvasionBridge](../../src/main/java/kome/common/data/KOMEObjectiveInvasionBridge.java) | Native pledge; character starting faction; waypoints; client projection | KOMEProgressionHardeningServiceTest; KOMESerfKnightIntegratedJourneyTest; KOMELordshipTrialIntegrationTest; KOMEKnightLordshipPassTest |
| War, season, defeat, capital, muster, or kingless defense is wrong | [Campaign lifecycle](systems/campaign-lifecycle/README.md) | [KOMEEvents.processCampaignTick](../../src/main/java/kome/common/data/KOMEEvents.java); [KOMEFactionDefeatService.reconcile](../../src/main/java/kome/common/data/KOMEFactionDefeatService.java) | Population; diplomacy; conflicts; movement | KOMEFactionDefeatServiceTest; KOMEMusterServiceTest; KOMEEmergencyDefenseMobilizationServiceTest |
| Conflict holds troops, or tactical area/siege configuration is invalid | [Conflicts and tactical geometry](systems/conflict-tactical/README.md) | [KOMEConflictService](../../src/main/java/kome/common/data/KOMEConflictService.java); [KOMETacticalEditService](../../src/main/java/kome/common/data/KOMETacticalEditService.java) | Movement; emergency defense; physical gates; storage | KOMEConflictMovementServiceTest; KOMETacticalAreaEditorSessionTest; KOMESiegeReadinessResolverTest |
| Gate creation/access/health/repair or ram control fails | [Physical siege](systems/physical-siege/README.md) | [TileEntitySiegeGate](../../src/main/java/com/enovak/lotrmoremobs/siege/tile/TileEntitySiegeGate.java); [RamControlManager](../../src/main/java/com/enovak/lotrmoremobs/siege/ram/RamControlManager.java) | Defensive Builds; tactical gate references; integrated mobs | TileEntitySiegeGateKOMEHealthTest; GateManagementKOMEProtocolTest; disposable gate/ram checks |
| State disappears after restart, load rejects data, config/repair is wrong | [Runtime and state](systems/runtime-state/README.md) | [KOMEWorldData.readFromNBT](../../src/main/java/kome/common/data/KOMEWorldData.java); [KOMEAddon](../../src/main/java/kome/common/KOMEAddon.java) | Owning guide; integrated module saves; [save/load flow](flows/save-load.md) | KOMEWorldDataAtomicLoadTest; KOMEWorldDataSchemaTest; KOMEAdminDiagnosticsCommandsTest |
| GUI disagrees with server, stale reconnect data, map sync fails | [Client and networking](systems/client-network/README.md) | [KOMEPacketHandler](../../src/main/java/kome/common/network/KOMEPacketHandler.java); [KOMEClientProxy](../../src/main/java/kome/client/KOMEClientProxy.java) | Owning service; projection builder; wire protocol; GUI | KOMEServerTaskQueueTest; KOMEClientTaskQueueTest; KOMEConquestSyncTest |
| Character creation, race, appearance, custom skin, respawn fails | [Character creation](systems/character-creation/README.md) | [CharacterCreationFlowService](../../src/main/java/com/lotrcharactercreation/creation/CharacterCreationFlowService.java); [PlayerRaceData](../../src/main/java/com/lotrcharactercreation/race/PlayerRaceData.java) | Native faction/waypoint; movement compatibility; rendering | CharacterCreationLegacyCompatibilityTest; LegacyC2SNetworkHardeningTest |
| Swimming/crawling, camera/arms, armor, or movement mode fails | [Movement compatibility](systems/movement-compatibility/README.md) | [PacketSendKey](../../src/main/java/com/fuzs/aquaacrobatics/network/message/PacketSendKey.java); [KOMECorePlugin.getASMTransformerClass](../../src/main/java/kome/core/KOMECorePlugin.java) | Character size/traits; LOTRMoreMobs settings; client rendering | AquaProductionMappingsTest; AquaModelPoseTest; AquaLotrSpecialArmorPoseBridgeTest |
| Mumak formation/hiring/spawning, pickup filter, special mob hook fails | [Integrated mobs](systems/integrated-mobs/README.md) | [Main](../../src/main/java/com/enovak/lotrmoremobs/Main.java); [LOTREntityMumakil](../../src/main/java/com/enovak/lotrmoremobs/entity/animal/LOTREntityMumakil.java) | Physical siege; native trade; module network/config | MumakilConfigTest; manual formation/pickup/reload checks |

## Verification tooling

Run documentation checks from the repository root:

```powershell
python docs/routing/validate.py
git diff --check
```

Guide test commands are targeted **future gameplay validation**, not tests executed for this documentation change. Tooling is [Gradle 8.5](../../gradle/wrapper/gradle-wrapper.properties), [GTNH/RetroFuturaGradle](../../settings.gradle.kts), and [JUnit 4.13.2](../../build.gradle.kts), targeting [Minecraft 1.7.10 / Forge 10.13.4.1614](../../gradle.properties). Builds require the ignored `libs/LOTRMod v36.15.jar` and `libs/geckolib-unofficial-1.7.10-1.0.4.jar` declared in the build; verify their presence in the actual checkout before validation. They are present in the Pass 1 environment; do not alter build configuration to compensate.

For heavy Java/Forge validation, coordinate with `Local\KOME-Heavy-Validation` and use disposable fixtures. The existing [validation helper](../../tools/kom11/validate.ps1) accepts `-Mode Build` (offline full test/build) or `-Mode Native` (launches a disposable Forge roster check); it does not accept test filters. Focused guide commands need the same mutex coordination. Add `--offline` only with a populated dependency cache. Some transformer tests need original LOTR input jars; report skips independently from passes. JUnit, disposable Forge, connected client, and live acceptance are separate evidence levels.

[Verification record and routing walkthroughs](VERIFICATION.md) gives the completed documentation checks and representative investigations. Historical [test plan](../KOME_TEST_PLAN.md) describes older alliance/capacity rules; use the selected guide's current tests and invariants.

## Progression cleanup Pass 1

The October 13 [implementation report](../PROGRESSION_CLEANUP_PASS1_REPORT.txt) records retired survival gates, Crown-checked new Campaign hiring, manual claim denial, protocol g8 progression projection and exact validation. Existing armies, native population costs, peaceful ruler transfers and Emergency Defense are retained. KOM-87 warrants and the newer dev KOM-18 resolver are integration dependencies, not delivered by this pass.

## Progression Pass 2 integration

The [Pass 2 report](../PROGRESSION_PASS2_INTEGRATION_AND_ABILITIES_REPORT.txt) supersedes the preceding resolver dependency: dev `6d1669a` is merged locally at `8aea11b`. Ordinary battle resolution, ConflictData 3, recovery WAL 1 and Campaign health 1 are retained. Rank eligibility is derived through `KOMEProgressionPermissionRegistry.Ability` and `KOMEProgressionAbilityPolicy`; domain services retain final authority. New battle entry requires Knight, new Build submissions Serf, and new company ownership/delegation Lord. Existing command and conflict maintenance remain available after rank correction. The native Rank page shows current/next privileges through the fenced g9 projection. Return travel uses only the exact applied character-creation starting waypoint. Warrants, personal troop ceilings, fiefs and specialist rewards remain deferred. Automated validation is distinct from outstanding live acceptance.

## Progression Pass 3 service and promotion

The [Pass 3 report](../PROGRESSION_PASS3_SERVICE_PROMOTION_REPORT.txt) and
[progression route](systems/progression/README.md) describe committed pledge-to-Serf,
three ordinary Master duties earning Knight and three regional Liege commissions
earning Lord directly from the current superior. Recommendations authorize the
next relationship through saved provenance; promotion is already earned. New
regional admission uses native generation/home evidence, with unproven old
placements failing closed and existing accepted relationships preserved. Trials
are retired with idempotent cleanup; reusable objectives remain. NPC Crown/capital
integration is established for future royal service, while Lord-to-Prince's legacy
path remains available. Matching artifacts use `1.0.10-service-g10`. Pass 2 abilities,
military authority, population and strategic cadence remain intact. Native world,
client GUI and multiplayer acceptance are still pending.

## Progression Pass 3 dev sync

The [October 15 local sync report](../PROGRESSION_PASS3_DEV_SYNC_REPORT.txt) records Pass 3 integration with dev **b593f5f**: company destination,
source, Rally and Units workflows; map radial input; correlated Join Battle;
same-source-tile return recovery; destination budget continuation; tracker GL state
restoration. The shared movement validator retains the Crown/declared-war guard.
Matching artifacts use `1.0.10-service-g14`; movement IDs 61-68 and progression/
Join Battle IDs are retained. See the [client/network](systems/client-network/README.md)
and [movement](systems/strategic-movement/README.md) routes for authority boundaries.
Automated integration verification does not establish live Minecraft acceptance.

## Progression Pass 4 reliability

The [Pass 4 report](../PROGRESSION_PASS4_RELIABILITY_REPORT.txt) records native trader
replacement provenance and safe Master preparation fallback. Existing accepted
relationships/history and g14 stay intact. Untagged legacy seats and old queued
respawners remain closed to new admission. Native class/generation audits find
plausible Masters in 22 factions and regional combat traders in 23; Fangorn and
Half-troll Master availability remain unsupported. Automated NBT/ASM/service tests
are distinct from the report's pending fresh-world and older-copy live checklist.
