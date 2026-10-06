# Hiring, unit identity, and companies

[Index](../../INDEX.md) · [Hire flow](../../flows/troop-hiring.md)

Owns ordinary versus campaign classification, population investment, recruitment transactions, company admission/coherence, and unit lifecycle records. Actual NPC/mount NBT is a second authority beside strategic records.

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
