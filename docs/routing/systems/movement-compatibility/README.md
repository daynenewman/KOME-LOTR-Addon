# Aqua movement, pose, armor, and camera compatibility

[Index](../../INDEX.md)

Owns integrated Aqua swimming/crawling, player resizing, water/render behavior, and LOTR armor/arm bridges. Strategic company routes are in [strategic movement](../strategic-movement/README.md).

## Entry points and state

- [Aqua CommonProxy](../../../../src/main/java/com/fuzs/aquaacrobatics/proxy/CommonProxy.java), [ConfigHandler](../../../../src/main/java/com/fuzs/aquaacrobatics/config/ConfigHandler.java), [PlayerMovementMode](../../../../src/main/java/com/enovak/lotrmoremobs/config/PlayerMovementMode.java): `aquaacrobatics.cfg`, movement hooks/networking, LOTRMoreMobs server movement setting.
- [PacketSendKey.fromBytes](../../../../src/main/java/com/fuzs/aquaacrobatics/network/message/PacketSendKey.java), `registerServerTaskHandler`, `ServerTaskHandler`: crawl intent, separate Netty intake and server END queue. Crawl/pose state belongs to live player movement/resize helpers; no KOME strategic save section owns it.
- [KOMECorePlugin.getASMTransformerClass](../../../../src/main/java/kome/core/KOMECorePlugin.java), [AquaAcrobaticsCore](../../../../src/main/java/com/fuzs/aquaacrobatics/core/AquaAcrobaticsCore.java), [Aqua ASM package](../../../../src/main/java/com/fuzs/aquaacrobatics/core/asm/): six player/biome/world transformers and mapping initialization.
- [Aqua client/model package](../../../../src/main/java/com/fuzs/aquaacrobatics/client/), [FirstPersonArmRenderContext](../../../../src/main/java/com/fuzs/aquaacrobatics/client/model/FirstPersonArmRenderContext.java), [PlayerModelArms](../../../../src/main/java/com/lotrcharactercreation/client/model/PlayerModelArms.java): pose, armor, water/camera and arm rendering. Follow the registered client proxy and matching transformer for the affected render path.

Dependencies: [character body/eye/traits](../character-creation/README.md), [LOTRMoreMobs server settings](../integrated-mobs/README.md), [coremod/runtime](../runtime-state/README.md). Native physical movement state is distinct from persisted race data and strategic movement credit.

## Verification and known gaps

[Production mappings](../../../KOME_AQUA_PRODUCTION_MAPPINGS.md) and [player arm rendering](../../../KOME_PLAYER_ARM_RENDERING.md). Tests: [production ASM](../../../../src/test/java/com/fuzs/aquaacrobatics/core/asm/AquaProductionMappingsTest.java), [pose](../../../../src/test/java/com/fuzs/aquaacrobatics/core/asm/AquaModelPoseTest.java), [LOTR armor bridge](../../../../src/test/java/com/fuzs/aquaacrobatics/client/model/AquaLotrSpecialArmorPoseBridgeTest.java).

```powershell
.\gradlew.bat test --tests 'com.fuzs.aquaacrobatics.core.asm.AquaProductionMappingsTest' --tests 'com.fuzs.aquaacrobatics.core.asm.AquaModelPoseTest' --tests 'com.fuzs.aquaacrobatics.client.model.AquaLotrSpecialArmorPoseBridgeTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Connected-client checks must cover swim/crawl transitions, race sizes/eyes, first/third-person armor/arms, boats, reconnect and server movement mode. ASM/JUnit alone does not prove visible compatibility.

The audit's narrow crawl defects remain in this checkout: `fromBytes` omits a negative ordinal guard; an unbounded `ConcurrentLinkedQueue` is drained until empty at END. The existing channel/living-player checks do not supply KOME queue admission limits or generation fencing. No dedicated malformed/flood test is linked because none was found in the relevant Aqua test tree; the suites above cover different boundaries.
