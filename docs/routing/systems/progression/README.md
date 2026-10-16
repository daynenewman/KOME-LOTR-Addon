# Progression, rank, NPC relationships, and quests

[Index](../../INDEX.md)

Owns canonical rank and permission unlocks, NPC role/relationship authority, Serf/Knight tasks, courier correspondence, commissions, and legacy objective compatibility. Character race/appearance is a separate integrated system.

Pass 3 (2026-10-14) supersedes the historical trial/promotion paths below.
[Implementation and evidence](../../../PROGRESSION_PASS3_SERVICE_PROMOTION_REPORT.txt).
`KOMECanonicalRankService.recognizePledge` reads committed native allegiance;
creation, the native setter bridge, login and tick observation reconcile Wanderer
to Serf once. Master selection requires Serf and an ordinary native civilian
(including farmer-only hire traders) in native faction territory. No seat is
required for ordinary service. Three distinct reported, non-forced duties and
150 alignment earn Knight from the current Master. Three distinct current-faction
commissions and 2000 alignment earn Lord from the current Liege. Credits survive
an alignment shortfall; issuance has no calendar-day quota.

`KOMEServicePromotionService.report` owns earned conferral, relationship completion,
history, recommendation and synchronization. `KOMESerfServiceObjectives` reuses
escort/recovery/defense as ordinary duties. `KOMELordshipTrialService` hosts optional
native invasion commissions under its retained serialization IDs. Mandatory
Standing/Lordship offers are disabled; one-time migration retires old objectives
without rewarding or celebrating them and preserves genuine service/ranks.

New Knight Lieges may be Lords or Princes. `KOMEProgressionServiceLocation.regional`
requires native generated-seat provenance plus the generator's native home boundary.
`KOMEProgressionSeatTransformer` observes existing native structure spawn calls;
it creates no buildings/NPCs. Unproven pre-Pass-3 placements fail closed for new
admission; already accepted relationships remain usable. Fangorn's missing native
Liege remains an explicit gap. See [geography](../geography/README.md).

`KOMEServiceRecognition` v1 saves historical superior identity, accomplishments,
earned standing, recommendation UUID, recipient, faction, redemption/invalidation
and migration version inside existing player progression. `KOMERecommendationService`
uses native written-book presentation; saved evidence, native eligibility and
regional location authorize one relationship, never copied item NBT alone.
Redeemed letters remain keepsakes. Former mentors recognize real service without
retaining active role leases. Lost letters recover from the issuer or the self-only
`/progression recommendation` command, including unloaded/dead issuers and full
inventory retry. No-letter unsolicited petitions await an approved policy.

Lord recommendations address the existing NPC Crown at the authoritative capital.
`KOMEProgressionServiceLocation.royal` is a tested future integration seam independent
of the player King office. It issues no royal duties or admission. The existing
Lord-to-Prince checklist remains explicitly labelled legacy/pending redesign;
existing Lord/Prince ranks and higher relationships remain compatible.

The existing Rank page, tracker and map point final reports at the current superior.
Pass 2 privileges remain derived from canonical rank, with g14 matching artifacts
and unchanged strategic resource/office checks. Headless authority, save/load,
native bytecode and layout tests are distinct from pending Minecraft acceptance.

Pass 2 (2026-10-13): [implementation report](../../../PROGRESSION_PASS2_INTEGRATION_AND_ABILITIES_REPORT.txt).
`KOMEProgressionPermissionRegistry.Ability` supplies stable IDs, rank floors,
native icon keys and dependencies. `KOMEProgressionAbilityPolicy` reads current
canonical rank without creating records or storing unlock flags. Read-only
outcomes distinguish rank locks, unavailable context and action-specific checks.
Campaign hiring and the book share `recruitmentAuthorityDenial`. New Build
intents require Serf; new Join Battle entry Knight; new company transfers and
command delegation Lord. Existing legal company control and registered battle
participation are maintained after correction. The g9 projection carries bounded
ability rows in packet 9, with existing connection/revision/dimension fences.
Current service precedes privileges. Warfare rank eligibility is metadata only;
no specialist rewards are advertised. Standard gear remains native; exceptional
gear and quest property retain their own restrictions. Follow
[hiring](../hiring-companies/README.md), [conflicts](../conflict-tactical/README.md),
[politics](../politics-diplomacy/README.md) and [starting return](../waypoints/README.md).

Cleanup Pass 1 (2026-10-13): ordinary survival, mounts, trading, pouches and native
hires no longer depend on legacy permission flags. IDs/history remain intact;
the baseline permission ledger is hidden from the advancement tab, with command
inspection retained. Exceptional gear and bound-property guards remain active.
`KOMEProgressionPermissions.campaignRecruitmentDenial` checks new campaign hiring
against current Lord-or-higher standing plus pledged ruler authority (or explicit
server administrative authority). KOM-87 warrants are not implemented; rank alone
does not authorize bank spending. Existing armies/control/recovery are unchanged.
`KOMEPacketProgressionData` g8 publishes machine rank, UUID, mode, dimension and
revision into the shared client permission projection before book/title updates.
See [implementation and evidence](../../../PROGRESSION_CLEANUP_PASS1_REPORT.txt).

Historical October 11 Knight/Lordship pass (superseded by Pass 3): report-ready interactions precede native GUI
dispatch, binary tracker stages omit numerical bars, and provision/material
markers carry native icons. Knight encounters save travel/preparation separately
from successful activation and allow rate-limited technical reassignment.
`KOMEObjectiveInvasionBridge` binds a real native invasion controller and observes
its outcome through `KOMEObjectiveInvasionTransformer`. New Lordship trials and
Lord conferral require a same-faction Prince; the current Lord remains Knight
service provenance until promotion. Version-1 trials continue their actual saved
field objectives with no guard-survival quota and Prince-only final conferral.
`KOMEKnightBookPresentation` supplies compact relationship/history context;
`KOMEProgressionRankSummary` owns current work and standing. Personal known-Prince
locations are saved in `KOMEKnightServiceRecord`. Bound native stolen equipment
uses the existing item/actor tokens; `KOMEQuestPropertyUseGuard` protects native
processing entry points. See [combined report](../../../PROGRESSION_KNIGHT_LORDSHIP_PASS_20261011.md),
[supplemental report](../../../PROGRESSION_SUPPLEMENTAL_IMMERSION_20261011.md), and
[book audit](../../../KNIGHT_PROGRESSION_BOOK_AUDIT_20261011.txt). These are wired
implementation/fixture results, not live Minecraft acceptance.

October 10 testing pass: ordinary pouches and NPC trading follow native LOTR rules;
`baseline.pouches` and `baseline.npc_trade` remain compatibility records without
action gates. The later Pass 1 also retires basic survival restrictions; exceptional
equipment, native crafting/alignment and political restrictions remain active.
Native authority display names use `KOMEProgressionNativeAuthority` and the narrow
`KOMEProgressionNativeInteractionTransformer`; personal identity stays in the rank
record. `KOMEVisualLocationService` publishes the current navigation objective,
including Master/Liege return stages, separately from optional relationship markers.
`KOMEProgressionMapFocus` owns a temporary native map view; conquest viewport
preferences are preserved. Verified shelter arrival is six horizontal blocks and
two vertical blocks, with an unobstructed approach. Technical Standing activation
failures retain the same trial and refund its daily reservation; gameplay failures
retain the existing daily and failed-Liege consequences. See
[testing implementation and evidence](../../../PROGRESSION_TESTING_FIXES_20261010.md).

The October 15 dev sync retains this service journey alongside the map movement and
Join Battle return workflows. New hostile command, map and Rally orders share
`KOMECompanyMovementService.validateCompanyMove`, which retains the Crown/declared-war
guard from `KOMECompanyCommandAuthority.newMilitaryOperationDenial`. Rank alone
does not authorize a new hostile operation. Lord-to-Prince remains deferred.

## Authority and entry points

- [KOMEPlayerProgression](../../../../src/main/java/kome/common/data/KOMEPlayerProgression.java), [KOMEProgressionAchievement](../../../../src/main/java/kome/common/data/KOMEProgressionAchievement.java), [KOMECanonicalRankService](../../../../src/main/java/kome/common/data/KOMECanonicalRankService.java), [KOMEProgressionPermissions.has](../../../../src/main/java/kome/common/data/KOMEProgressionPermissions.java): persisted player progression in [KOMEWorldData](../../../../src/main/java/kome/common/data/KOMEWorldData.java), definitions, canonical rank, action gates. Native pledge/alignment and NPC/entity NBT are additional authorities.
- [KOMEProgressionAutoCompleter.runForPlayer](../../../../src/main/java/kome/common/data/KOMEProgressionAutoCompleter.java), `applyUnlocks`, `syncPlayer`, [KOMEEvents](../../../../src/main/java/kome/common/data/KOMEEvents.java): login/player tick, item/entity/NPC events, unlock recomputation and synchronization.
- [KOMEProgressionNpcInteractionService.interact](../../../../src/main/java/kome/common/data/KOMEProgressionNpcInteractionService.java), [KOMEProgressionNpcRankService](../../../../src/main/java/kome/common/data/KOMEProgressionNpcRankService.java), [KOMEProgressionRelationshipLifecycle](../../../../src/main/java/kome/common/data/KOMEProgressionRelationshipLifecycle.java): NPC rank/role leases, master/liege selection, death/release/succession.
- [KOMECourierService](../../../../src/main/java/kome/common/data/KOMECourierService.java), [KOMESerfKnightRelationshipService](../../../../src/main/java/kome/common/data/KOMESerfKnightRelationshipService.java), [KOMEKnightCommissionService](../../../../src/main/java/kome/common/data/KOMEKnightCommissionService.java), [KOMELordshipTrialService](../../../../src/main/java/kome/common/data/KOMELordshipTrialService.java): concrete task authorities; follow their models, encounter markers, recovery and ownership checks for a quest symptom.
- [KOMECommandProgression](../../../../src/main/java/kome/common/command/KOMECommandProgression.java), [KOMEPacketProgressionData](../../../../src/main/java/kome/common/network/KOMEPacketProgressionData.java), [KOMEGuiProgression](../../../../src/main/java/kome/client/gui/KOMEGuiProgression.java), [KOMEGuiRelationshipHub](../../../../src/main/java/kome/client/gui/KOMEGuiRelationshipHub.java), [KOMEProgressionTrackerOverlay](../../../../src/main/java/kome/client/KOMEProgressionTrackerOverlay.java): admin/intents, summaries, tasks, tracking.
- [KOMEProgressionOfferTransformer](../../../../src/main/java/kome/core/KOMEProgressionOfferTransformer.java), [KOMEProgressionNpcDespawnTransformer](../../../../src/main/java/kome/core/KOMEProgressionNpcDespawnTransformer.java), [KOMEFactionTitleTransformer](../../../../src/main/java/kome/core/KOMEFactionTitleTransformer.java): native offers, protected role entities, and titles. Registrations are in [KOMECorePlugin](../../../../src/main/java/kome/core/KOMECorePlugin.java).

Consumers: [waypoint travel](../waypoints/README.md), [hiring](../hiring-companies/README.md), [ruler/diplomacy](../politics-diplomacy/README.md), [geographic eligibility](../geography/README.md), [client permissions/records](../client-network/README.md). [Character starting faction](../character-creation/README.md) can affect pledge but does not own KOME rank.

## Verification and boundaries

Existing [integration decisions](../../../KOME_DEV_INTEGRATION_DECISIONS.md) and [GUI handoff](../../../KOME_GUI_HANDOFF.md) predate parts of this implementation. Tests provide current detail: [hardening](../../../../src/test/java/kome/common/data/KOMEProgressionHardeningServiceTest.java), [permission registry](../../../../src/test/java/kome/common/data/KOMEProgressionPermissionRegistryTest.java), [integrated journey](../../../../src/test/java/kome/common/data/KOMESerfKnightIntegratedJourneyTest.java), [Lordship](../../../../src/test/java/kome/common/data/KOMELordshipTrialIntegrationTest.java), [schema](../../../../src/test/java/kome/common/data/KOMEProgressionSchemaTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMEProgressionHardeningServiceTest' --tests 'kome.common.data.KOMEProgressionPermissionRegistryTest' --tests 'kome.common.data.KOMESerfKnightIntegratedJourneyTest' --tests 'kome.common.data.KOMELordshipTrialIntegrationTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Inspect the exact achievement/permission ID, canonical rank, persisted assignment, native pledge, NPC role identity, and visible projection. Replays/death/reload must not duplicate rewards or reuse foreign encounter items. This checkout contains progression through Lordship added after the audit; its earlier cyclic fresh-progression finding must not be treated as an unchanged blanket status. Higher-rank transitions and configured TODO boundaries require task-specific inspection; presence of an enum or title does not prove every rank journey is complete.
