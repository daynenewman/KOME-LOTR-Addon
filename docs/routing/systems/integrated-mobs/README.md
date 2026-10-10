# LOTRMoreMobs creatures, formations, pickup, and hooks

[Index](../../INDEX.md)

Owns integrated Mumak entities/formations/trades/spawn replacement, pickup filter, special native mob hooks, and server gameplay settings. [Physical siege](../physical-siege/README.md) covers gates/rams; [Aqua compatibility](../movement-compatibility/README.md) covers player movement.

## Entry points and authority

- [Main](../../../../src/main/java/com/enovak/lotrmoremobs/Main.java), [CommonProxy](../../../../src/main/java/com/enovak/lotrmoremobs/proxy/CommonProxy.java), [MumakilConfig](../../../../src/main/java/com/enovak/lotrmoremobs/config/MumakilConfig.java): content/config/packet/event/spawn registrations, native trade injection, commands and shutdown cleanup. [KOMEAddon](../../../../src/main/java/kome/common/KOMEAddon.java) invokes this module; [ClientProxy](../../../../src/main/java/com/enovak/lotrmoremobs/proxy/ClientProxy.java) supplies render/UI hooks.
- [LOTREntityMumakil](../../../../src/main/java/com/enovak/lotrmoremobs/entity/animal/LOTREntityMumakil.java), [LOTREntityMumakilHowdahArcher](../../../../src/main/java/com/enovak/lotrmoremobs/entity/npc/LOTREntityMumakilHowdahArcher.java): entity NBT owns age/taming/equipment/formation and attachment identity. [MumakilWarFormationFactory](../../../../src/main/java/com/enovak/lotrmoremobs/spawning/MumakilWarFormationFactory.java) and [MumakilFormationReplacementService](../../../../src/main/java/com/enovak/lotrmoremobs/spawning/MumakilFormationReplacementService.java) create/recover native physical formations.
- [MumakilUnitTradeInjector](../../../../src/main/java/com/enovak/lotrmoremobs/hiring/MumakilUnitTradeInjector.java), [LOTRUnitTradeEntryMumakil](../../../../src/main/java/com/enovak/lotrmoremobs/hiring/LOTRUnitTradeEntryMumakil.java), [formation handlers](../../../../src/main/java/com/enovak/lotrmoremobs/handler/): native hiring, home/conquest/invasion replacement, death/credit and driver/archer behavior.
- [PlayerPickupFilterData](../../../../src/main/java/com/enovak/lotrmoremobs/pickupfilter/PlayerPickupFilterData.java), [PickupFilterRequestManager](../../../../src/main/java/com/enovak/lotrmoremobs/pickupfilter/PickupFilterRequestManager.java), [PickupFilterNetwork](../../../../src/main/java/com/enovak/lotrmoremobs/pickupfilter/PickupFilterNetwork.java), [CommandPickupFilter](../../../../src/main/java/com/enovak/lotrmoremobs/command/CommandPickupFilter.java), [GuiPickupFilter](../../../../src/main/java/com/enovak/lotrmoremobs/client/gui/GuiPickupFilter.java): player persisted NBT exclusions, server pickup cancellation and client snapshots.
- [MortalGandalfTransformer](../../../../src/main/java/com/enovak/lotrmoremobs/coremod/MortalGandalfTransformer.java), [RespawnMarkerProjectileCollisionTransformer](../../../../src/main/java/com/enovak/lotrmoremobs/coremod/RespawnMarkerProjectileCollisionTransformer.java): configured mortality and projectile collision bridges; [KOMECorePlugin](../../../../src/main/java/kome/core/KOMECorePlugin.java) also registers gate sight/path hooks.

Dependencies: native LOTR entities/trades/invasions, [runtime/coremods](../runtime-state/README.md), [module client networking](../client-network/README.md), [campaign hire/classification](../hiring-companies/README.md). A wild/tamed entity, hired formation, and KOME campaign company are different records; native formation creation does not establish strategic enrollment.

## Verification and limitations

[Mumak configuration test](../../../../src/test/java/com/enovak/lotrmoremobs/config/MumakilConfigTest.java) checks configuration only; [coremod configuration test](../../../../src/test/java/kome/core/KOMECoremodConfigurationTest.java) checks registration. [Character isolation](../../../../src/test/java/kome/integration/CharacterCreationIsolationTest.java) covers integration boundaries, not full creature behavior. The detailed integrated-mobs chapter remains at the preserved audit location in [provenance](../../PROVENANCE.md); no duplicate audit is required.

```powershell
.\gradlew.bat test --tests 'com.enovak.lotrmoremobs.config.MumakilConfigTest' --tests 'kome.core.KOMECoremodConfigurationTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Dedicated Mumak formation, ram lifecycle, and pickup-filter gameplay tests were not found in the current test tree. Use disposable manual checks for native purchase/payment rollback, formation UUID/attachment/death-slot reload, replacement failure preserving the original NPC, invasion credit, pickup exclusion/respawn/reconnect, and optional mob hooks. Configuration/registration tests cannot prove those behaviors or special-formation campaign transport.
