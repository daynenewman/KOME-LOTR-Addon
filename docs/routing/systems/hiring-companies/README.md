# Hiring, unit identity, and companies

Pass 2: [report](../../../PROGRESSION_PASS2_INTEGRATION_AND_ABILITIES_REPORT.txt).
New Campaign hiring and its Rank-page explanation share
`KOMEProgressionAbilityPolicy.recruitmentAuthorityDenial`; Lord is eligibility,
and pledged ruler office is the existing spending authority pending KOM-87.
New company ownership offers/acceptance and command delegation require Lord
recipients. Existing ownership, delegated control continuation, reorganization,
retreat and recovery remain governed by their established services. No
rank-based disbanding, reclassification, population grant or personal ceiling.

[Index](../../INDEX.md) · [Hire flow](../../flows/troop-hiring.md)

Owns ordinary versus campaign classification, population investment, recruitment transactions, company admission/coherence, and unit lifecycle records. Actual NPC/mount NBT is a second authority beside strategic records.

Pass 1: ordinary native bodyguards use native eligibility and the recruiter's
canonical faction bank, including native-eligible foreign bodyguards; they remain
ORDINARY without company enrollment. Farmhands keep zero population and their
separate existing foreign cooperation policy. Campaign hiring rechecks current
Lord standing and pledged Crown office, or explicit server admin authority, before
deployment/debit. Legacy Knight/hire flags never authorize it. No general recruitment
warrants/budgets exist here (KOM-87); non-ruler Lords receive that explicit blocker.
Emergency Defense is a separate retained reserve path. Existing troops, companies,
movement/recovery and costs are unchanged. See [Pass 1](../../../PROGRESSION_CLEANUP_PASS1_REPORT.txt).

## Entry points and storage

- [KOMEEvents](../../../../src/main/java/kome/common/data/KOMEEvents.java) routes native hire/join/death observations; [KOMENativeHireRegistrationService.registerOrdinaryCombatHire](../../../../src/main/java/kome/common/data/KOMENativeHireRegistrationService.java) debits/registers ORDINARY units without campaign enrollment.
- [KOMEPacketCampaignHire](../../../../src/main/java/kome/common/network/KOMEPacketCampaignHire.java), [KOMENativeTraderCampaignRecruitment](../../../../src/main/java/kome/common/data/KOMENativeTraderCampaignRecruitment.java), [KOMECampaignRecruitmentService.recruit](../../../../src/main/java/kome/common/data/KOMECampaignRecruitmentService.java): explicit campaign transaction; native payment/spawn, bank debit, classification, admission, and rollback.
- [KOMEUnitPopulationCostService.calculate](../../../../src/main/java/kome/common/data/KOMEUnitPopulationCostService.java), `reconcileBankedUnitCost`: configurable cost/high-water investment. [KOMEHiredUnitClassification.assignForCampaignWorkflow](../../../../src/main/java/kome/common/data/KOMEHiredUnitClassification.java) supplies explicit class authority.
- [KOMERecruitmentLocationService](../../../../src/main/java/kome/common/data/KOMERecruitmentLocationService.java), [KOMERecruitmentDeploymentService](../../../../src/main/java/kome/common/data/KOMERecruitmentDeploymentService.java), [KOMECampaignCompanyAdmissionService](../../../../src/main/java/kome/common/data/KOMECampaignCompanyAdmissionService.java), [KOMECompanyCoherenceService](../../../../src/main/java/kome/common/data/KOMECompanyCoherenceService.java): legal tile, safe physical placement, coherent detachment selection.
- [KOMEHiredUnitRecord](../../../../src/main/java/kome/common/data/KOMEHiredUnitRecord.java) and [KOMEArmyCompany](../../../../src/main/java/kome/common/data/KOMEArmyCompany.java) persist in [KOMEWorldData](../../../../src/main/java/kome/common/data/KOMEWorldData.java). [KOMECommandTroops](../../../../src/main/java/kome/common/command/KOMECommandTroops.java) and [KOMEGuiCompanyList](../../../../src/main/java/kome/client/gui/KOMEGuiCompanyList.java) expose company administration/intents.

Dependencies: [population bank/rate](../build-population/README.md), [progression permission/pledge](../progression/README.md), [capital/phase](../campaign-lifecycle/README.md), [tile resolver](../geography/README.md), [delegation/pledge cleanup](../politics-diplomacy/README.md), [movement](../strategic-movement/README.md), [special native trades](../integrated-mobs/README.md).

## Verification and gaps

Company names do not change stable identity. Ordinary native hires do not automatically become campaign companies. Cost reductions do not refund past investment; increases charge only above its high-water mark. Physical absence/unloaded chunks do not prove death. Native special trade overrides must not be presumed compatible with the direct campaign adapter.

References: [integration decisions](../../../KOME_DEV_INTEGRATION_DECISIONS.md), [population projections](../../../KOME_POPULATION_PROJECTIONS.md), [campaign confinement](../../../KOM46_PHASE5_CAMPAIGN_TILE_CONFINEMENT.md). Tests: [recruitment](../../../../src/test/java/kome/common/data/KOMECampaignRecruitmentServiceTest.java), [native registration](../../../../src/test/java/kome/common/data/KOMENativeHireRegistrationServiceTest.java), [cost](../../../../src/test/java/kome/common/data/KOMEUnitPopulationCostServiceTest.java), [company admission](../../../../src/test/java/kome/common/data/KOMEHiredUnitCompanyAdmissionTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMECampaignRecruitmentServiceTest' --tests 'kome.common.data.KOMENativeHireRegistrationServiceTest' --tests 'kome.common.data.KOMEUnitPopulationCostServiceTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). On disposable fixtures compare payment/bank, UUID/classification, company membership, physical NPC/mount insertion, rollback, and reload. [KOMECompanyReorganizationService](../../../../src/main/java/kome/common/data/KOMECompanyReorganizationService.java) has split/merge logic but no production caller at this baseline. No general ordinary-to-campaign conversion workflow is wired.

## KOM-85 map presentation continuity

The read-only company projection in [KOMEPacketUnitMapMarkers](../../../../src/main/java/kome/common/network/KOMEPacketUnitMapMarkers.java) separates strategic membership from physical NPC loading. It uses existing committed-lifespan, exact membership, owner and current-tile evidence. Recipient visibility shares the [client route's](../client-network/README.md) canonical-faction/delegation/operator predicate with conquest snapshots; it is separate from permission to command a company. The same current direct-read predicate now gates company-linked Campaign detail commands and capture-response costs; a former owner UUID alone does not restore access after a pledge change. Normal non-Campaign unit records keep their separate owner access. The g15 presence decision retains committed intermediate waiting and locally halted members despite their movement-order IDs, but excludes genuine transit and uncommitted reconstruction. A zero loaded-NPC count carries tile-level map presence only, not a live combatant or precise NPC locator; reconstructed UUIDs rejoin the same company ID through current membership. g16 shield labels count surviving committed member records, not population cost or loaded entities. Mounts are not separately counted without distinct canonical records. Foreign spotted markers carry only a faction shield and that count; details and population cost remain server-filtered. No company, movement, health, persistence or reconstruction authority is changed. Client publication and tile-anchor rules are documented in the client route. Focused server-builder/wire/art regressions: [KOMEStableCompanyMarkersTest](../../../../src/test/java/kome/client/KOMEStableCompanyMarkersTest.java).

The g16 former-owner mutation correction requires a live matching pledge for ordinary owner commands, and checks valid delegation, ruler or operator authority by action. Direct GUI rename now passes that check before calling `KOMEWorldData.renameHiringCompany`; the world-data method remains a trusted record mutation seam, not a player authorization boundary. Administrative company rebuild remains operator-only. `/troops station` skips Campaign records linked to a company the requester no longer commands as current-faction owner, and unlinked records with a different population faction. This correction does not grant a population refund or change pledge-release cleanup.
