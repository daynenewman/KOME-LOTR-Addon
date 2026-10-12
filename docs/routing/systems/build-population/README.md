# Builds and faction population

[Index](../../INDEX.md) · [Payout flow](../../flows/build-population.md)

Owns Build placement/contributions/manager review and the developed rate → faction bank economy. Population authority is faction-wide, not the older tile-capacity or allocation model.

## Authority and entry points

- [KOMEPlayerBuild](../../../../src/main/java/kome/common/data/KOMEPlayerBuild.java) / [KOMEBuildContribution](../../../../src/main/java/kome/common/data/KOMEBuildContribution.java): exact location, manager, population owner, approved/developed centi-hours. [KOMEBuildService.create](../../../../src/main/java/kome/common/data/KOMEBuildService.java), `addSubmission`, `decideSubmission`, `removeApprovedContribution`, `deleteBuild`, `destroyEnemyBuild` enforce lifecycle rules.
- [KOMEPacketBuildAction](../../../../src/main/java/kome/common/network/KOMEPacketBuildAction.java), [KOMECommandBuild](../../../../src/main/java/kome/common/command/KOMECommandBuild.java), [KOMEGuiConquestCapture](../../../../src/main/java/kome/client/gui/KOMEGuiConquestCapture.java): GUI intents and `/build` administration.
- [KOMEPopulationDevelopmentService.processLiveDueBoundaries](../../../../src/main/java/kome/common/data/KOMEPopulationDevelopmentService.java): eligible pending Normal Build hours, rate ceiling, stable development order. [KOMEPopulationRateService.getExactDailyPopulationRates](../../../../src/main/java/kome/common/data/KOMEPopulationRateService.java) derives rates.
- [KOMEPopulationPayoutProcessor](../../../../src/main/java/kome/common/data/KOMEPopulationPayoutProcessor.java), [KOMEPopulationPayoutRuntime](../../../../src/main/java/kome/common/data/KOMEPopulationPayoutRuntime.java), [KOMEDailyBoundary](../../../../src/main/java/kome/common/data/KOMEDailyBoundary.java): configured time boundaries, startup policy, exact remainder accounting.
- [KOMEFactionPopulation](../../../../src/main/java/kome/common/data/KOMEFactionPopulation.java), [KOMEPopulationService](../../../../src/main/java/kome/common/data/KOMEPopulationService.java), [KOMEPopulationProjection](../../../../src/main/java/kome/common/data/KOMEPopulationProjection.java): centi-population bank, spending, derived views. Builds, banks, development cursors, payout cursor/remainders persist in [KOMEWorldData](../../../../src/main/java/kome/common/data/KOMEWorldData.java).

Dependencies/consumers: [tile control](../geography/README.md), [construction permission](../politics-diplomacy/README.md), [season gates/muster/defeat](../campaign-lifecycle/README.md), [hire spending](../hiring-companies/README.md), [defensive gates](../physical-siege/README.md), [client projections](../client-network/README.md).

## Invariants and verification

Approved time is not yet developed time. Captured pending development freezes; already-developed rate uses configured capture effects. Available bank, daily rate, and active investment are different quantities. Rates use exact `BigInteger` units before rounding; payout keeps fractional remainder and preflights a boundary. Payout is gated to WAR/FINALE; development has its own lifecycle. Ordinary combat spending has no automatic death/dismissal refund; Emergency Defense has a separate reserve/refund authority.

Current references: [precise Builds](../../../KOME_PRECISE_BUILDS.md), [population configuration](../../../KOME_POPULATION_CONFIGURATION.md), [projections](../../../KOME_POPULATION_PROJECTIONS.md), [legacy retirement](../../../KOME_LEGACY_POPULATION_RETIREMENT.md). [Build system](../../../KOME_BUILD_SYSTEM.md) and [population system](../../../KOME_POPULATION_SYSTEM.md) contain older half-hour/capacity rules; compare with code rather than adopting them.

Tests: [precise Builds](../../../../src/test/java/kome/common/data/KOMEPreciseBuildTest.java), [development](../../../../src/test/java/kome/common/data/KOMEPopulationDevelopmentServiceTest.java), [payout](../../../../src/test/java/kome/common/data/KOMEPopulationPayoutProcessorTest.java), [permanent spend](../../../../src/test/java/kome/common/data/KOMEPopulationPermanentSpendTest.java), [Build packet](../../../../src/test/java/kome/common/network/KOMECanonicalBuildPacketTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMEPopulationDevelopmentServiceTest' --tests 'kome.common.data.KOMEPopulationPayoutProcessorTest' --tests 'kome.common.network.KOMECanonicalBuildPacketTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Compare approved/developed time, exact rate, bank/remainder, ownership, phase, and saved boundary before/after a live tick and cold restart. The development/payout loops are sequential, not one universal atomic daily transaction; historical per-boundary rate reconstruction remains a rules/acceptance question.
