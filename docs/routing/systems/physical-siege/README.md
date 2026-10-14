# Physical gates, rams, and defensive Build links

[Index](../../INDEX.md)

Owns LOTRMoreMobs physical gate creation/editing/access, health/damage/repair, ram control/crew identity, and the KOME defensive Build → gate health bridge. Strategic conflict/tile control is a separate authority.

## Authority and entry points

- [SiegeRegistry](../../../../src/main/java/com/enovak/lotrmoremobs/siege/SiegeRegistry.java), [GateCreationManager](../../../../src/main/java/com/enovak/lotrmoremobs/siege/creation/GateCreationManager.java), [GateEditSessionManager](../../../../src/main/java/com/enovak/lotrmoremobs/siege/edit/GateEditSessionManager.java), [GateManagementManager](../../../../src/main/java/com/enovak/lotrmoremobs/siege/repair/GateManagementManager.java): registration, validated creation/edit/management sessions and repair intent.
- [TileEntitySiegeGate.applySiegeDamage](../../../../src/main/java/com/enovak/lotrmoremobs/siege/tile/TileEntitySiegeGate.java), `beginRepair`, `writeToNBT`, `readFromNBT`: physical health/state/jobs in tile NBT; [SiegeGateOwnershipData](../../../../src/main/java/com/enovak/lotrmoremobs/siege/gate/SiegeGateOwnershipData.java) persists ownership/mutation recovery in world saved data.
- [EntityBattleRam](../../../../src/main/java/com/enovak/lotrmoremobs/siege/ram/EntityBattleRam.java), [RamControlManager](../../../../src/main/java/com/enovak/lotrmoremobs/siege/ram/RamControlManager.java), [SiegeRamCrewOwnershipData](../../../../src/main/java/com/enovak/lotrmoremobs/siege/ram/SiegeRamCrewOwnershipData.java): native hire, entity NBT, crew generation/identity ledger, bounded command/target requests. [BattleRamUnitTradeInjector](../../../../src/main/java/com/enovak/lotrmoremobs/hiring/BattleRamUnitTradeInjector.java) installs native trade entries.
- [KOMEDefensiveGateLinkService](../../../../src/main/java/kome/common/data/KOMEDefensiveGateLinkService.java), [KOMEDefensiveGateHealthCalculator](../../../../src/main/java/kome/common/data/KOMEDefensiveGateHealthCalculator.java), [KOMEPhysicalGateInspection](../../../../src/main/java/kome/common/data/KOMEPhysicalGateInspection.java): strategic Build link/max-health calculation and non-loading physical inspection; Build link persists in KOME data.
- [SiegeNetwork](../../../../src/main/java/com/enovak/lotrmoremobs/siege/network/SiegeNetwork.java), [GuiGateManagement](../../../../src/main/java/com/enovak/lotrmoremobs/siege/client/gui/GuiGateManagement.java), [GuiBattleRamControl](../../../../src/main/java/com/enovak/lotrmoremobs/siege/client/gui/GuiBattleRamControl.java): requests and gate/ram projections. [EntitySensesGateSightTransformer](../../../../src/main/java/com/enovak/lotrmoremobs/coremod/EntitySensesGateSightTransformer.java), [PathFinderGatePartTransformer](../../../../src/main/java/com/enovak/lotrmoremobs/coremod/PathFinderGatePartTransformer.java) bridge NPC sight/pathfinding.

Dependencies: [Build contributions](../build-population/README.md), [module startup/config](../integrated-mobs/README.md), [tactical gate references](../conflict-tactical/README.md), [runtime/coremods](../runtime-state/README.md). Health projection, physical health, and tile ownership must not be conflated.

## Verification and gaps

Existing [precise Build documentation](../../../KOME_PRECISE_BUILDS.md) covers defensive integration. Tests: [health bridge](../../../../src/test/java/com/enovak/lotrmoremobs/siege/tile/TileEntitySiegeGateKOMEHealthTest.java), [management protocol](../../../../src/test/java/com/enovak/lotrmoremobs/siege/network/GateManagementKOMEProtocolTest.java), [link service](../../../../src/test/java/kome/common/data/KOMEDefensiveGateLinkServiceTest.java), [inspection](../../../../src/test/java/kome/common/data/KOMEPhysicalGateInspectionTest.java).

```powershell
.\gradlew.bat test --tests 'com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGateKOMEHealthTest' --tests 'com.enovak.lotrmoremobs.siege.network.GateManagementKOMEProtocolTest' --tests 'kome.common.data.KOMEDefensiveGateLinkServiceTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). In disposable gameplay fixtures create/finalize/edit a gate, test access, ram impact/breach, paid repair, unload/reload, crew death/replacement, and client health packets. These tests cover the KOME bridge, not full ram gameplay or all gate editing/crash recovery. Ram breach does not itself establish a campaign battle result or capture. Preserve entity/tile/world-ledger identities and saved mutation locks.
