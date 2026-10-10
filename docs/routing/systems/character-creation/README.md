# Character creation, race, appearance, and custom skins

[Index](../../INDEX.md)

Owns the integrated Character Creation stage flow, starting faction/location, race/sex/appearance, recreation, body/eye/traits, and custom-skin catalog/transfer. It does not own KOME progression rank.

## Authority and entry points

- [LOTRCharacterCreation](../../../../src/main/java/com/lotrcharactercreation/LOTRCharacterCreation.java), [ModConfiguration](../../../../src/main/java/com/lotrcharactercreation/config/ModConfiguration.java): module initialization, `lotrcharactercreation.cfg`, race events, `/character`, `/lotrcreation`, `/lotrrace`; [KOMEAddon](../../../../src/main/java/kome/common/KOMEAddon.java) invokes the integrated lifecycle.
- [CharacterCreationFlowService](../../../../src/main/java/com/lotrcharactercreation/creation/CharacterCreationFlowService.java), [CharacterRecreationService](../../../../src/main/java/com/lotrcharactercreation/creation/CharacterRecreationService.java), [StartingFactionApplication](../../../../src/main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java), [StartingWaypointApplication](../../../../src/main/java/com/lotrcharactercreation/waypoint/StartingWaypointApplication.java): staged selection/finalization, native allegiance and starting location.
- [PlayerRaceData](../../../../src/main/java/com/lotrcharactercreation/race/PlayerRaceData.java): persisted player NBT `lotrcharactercreation` under the player's persisted compound. [CommonRaceTraitEventHandler](../../../../src/main/java/com/lotrcharactercreation/trait/CommonRaceTraitEventHandler.java) applies trait behavior. Respawn/login/tracking handlers are registered through module proxies; follow [module source](../../../../src/main/java/com/lotrcharactercreation/) for the exact race/render symptom.
- [ModNetwork](../../../../src/main/java/com/lotrcharactercreation/network/ModNetwork.java), [ServerCustomSkinSyncService](../../../../src/main/java/com/lotrcharactercreation/network/ServerCustomSkinSyncService.java), [ServerCustomSkinLibrary](../../../../src/main/java/com/lotrcharactercreation/appearance/ServerCustomSkinLibrary.java): independent staged request queues, validated manifests/chunks and skin files under `config/lotrcharactercreation/custom_skins`; skins are not KOME faction NBT.
- [Character screens](../../../../src/main/java/com/lotrcharactercreation/client/gui/) and client appearance/renderer services consume stage/appearance messages. [Access transformer declarations](../../../../src/main/resources/META-INF/lotrcharactercreation_at.cfg) belong to integration compatibility.

Dependencies: native LOTR player/waypoint state, [KOME pledge/progression](../progression/README.md), [movement and render compatibility](../movement-compatibility/README.md), [runtime/module registration](../runtime-state/README.md). Automatic starting allegiance is configurable, so starting location alone does not establish pledge.

## Verification

[Character documentation](../../../KOME_CHARACTER_CREATION.md); [legacy compatibility tests](../../../../src/test/java/com/lotrcharactercreation/CharacterCreationLegacyCompatibilityTest.java), [network hardening](../../../../src/test/java/com/lotrcharactercreation/network/LegacyC2SNetworkHardeningTest.java), [skin protocol](../../../../src/test/java/com/lotrcharactercreation/network/CustomSkinManifestProtocolTest.java), [module isolation](../../../../src/test/java/kome/integration/CharacterCreationIsolationTest.java).

```powershell
.\gradlew.bat test --tests 'com.lotrcharactercreation.CharacterCreationLegacyCompatibilityTest' --tests 'com.lotrcharactercreation.network.LegacyC2SNetworkHardeningTest' --tests 'com.lotrcharactercreation.network.CustomSkinManifestProtocolTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Verify incomplete-stage login, back/recreation/finalization, respawn/dimension change, remote-player tracking and custom-skin reconnect with a client. Save player NBT and skin files independently. Symlink checks can be environment-skipped; report that explicitly. Character queue semantics differ from KOME's bounded round-robin queue and require their own validation.
