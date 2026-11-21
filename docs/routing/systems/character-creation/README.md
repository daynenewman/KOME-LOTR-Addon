# Character creation, race, appearance, and custom skins

[Index](../../INDEX.md)

Owns the integrated Character Creation stage flow, starting faction/location, race/sex/appearance, recreation, body/eye/traits, and custom-skin catalog/transfer. It does not own KOME progression rank.

October 10: `StartingFactionApplication.tryApply` applies an additive -1 to the
unique native enemy/mortal-enemy factions before committing its existing once-only
starting-faction marker. Preview, Back, completed-player login and recreation do
not enter this path. Appearance preview clicks use `AppearancePreviewSwing` and
the native model swing field, without animating or attacking as the live player.
`KOMEElvenFootGlow` reads synchronized race data and uses bounded native client
particles for completed Elven characters. These visual changes require client
acceptance; consult [testing report](../../../PROGRESSION_TESTING_FIXES_20261010.md).

## Authority and entry points

- [LOTRCharacterCreation](../../../../src/main/java/com/lotrcharactercreation/LOTRCharacterCreation.java), [ModConfiguration](../../../../src/main/java/com/lotrcharactercreation/config/ModConfiguration.java): module initialization, `lotrcharactercreation.cfg`, race events, `/character`, `/lotrcreation`, `/lotrrace`; [KOMEAddon](../../../../src/main/java/kome/common/KOMEAddon.java) invokes the integrated lifecycle.
- [CharacterCreationFlowService](../../../../src/main/java/com/lotrcharactercreation/creation/CharacterCreationFlowService.java), [CharacterRecreationService](../../../../src/main/java/com/lotrcharactercreation/creation/CharacterRecreationService.java), [StartingFactionApplication](../../../../src/main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java), [StartingWaypointApplication](../../../../src/main/java/com/lotrcharactercreation/waypoint/StartingWaypointApplication.java): staged selection/finalization, native allegiance and starting location.
- [PlayerRaceData](../../../../src/main/java/com/lotrcharactercreation/race/PlayerRaceData.java): persisted player NBT `lotrcharactercreation` under the player's persisted compound. [CommonRaceTraitEventHandler](../../../../src/main/java/com/lotrcharactercreation/trait/CommonRaceTraitEventHandler.java) applies trait behavior. Respawn/login/tracking handlers are registered through module proxies; follow [module source](../../../../src/main/java/com/lotrcharactercreation/) for the exact race/render symptom.
- [ModNetwork](../../../../src/main/java/com/lotrcharactercreation/network/ModNetwork.java), [ServerCustomSkinSyncService](../../../../src/main/java/com/lotrcharactercreation/network/ServerCustomSkinSyncService.java), [ServerCustomSkinLibrary](../../../../src/main/java/com/lotrcharactercreation/appearance/ServerCustomSkinLibrary.java): independent staged request queues, validated manifests/chunks and skin files under `config/lotrcharactercreation/custom_skins`; skins are not KOME faction NBT.
- [Character screens](../../../../src/main/java/com/lotrcharactercreation/client/gui/) and client appearance/renderer services consume stage/appearance messages. [Access transformer declarations](../../../../src/main/resources/META-INF/lotrcharactercreation_at.cfg) belong to integration compatibility.

Dependencies: native LOTR player/waypoint state, [KOME pledge/progression](../progression/README.md), [movement and render compatibility](../movement-compatibility/README.md), [runtime/module registration](../runtime-state/README.md). Automatic starting allegiance is configurable, so starting location alone does not establish pledge.

## Verification

KOM-79 Phases 1 and 1B change only the Dwarf client presentation in
[`ClientDwarfTraitState`](../../../../src/main/java/com/lotrcharactercreation/client/trait/ClientDwarfTraitState.java).
The former left-side stamina/feast labels, numbers, and bars are replaced by two
9-pixel-high Cirth rows flanking the hotbar at its vertical center. The renderer consumes the
same synchronized stamina, feast, and exhausted fields, plus the existing local
stamina prediction; server drain, digestion, NBT, and packet fields are unchanged.
[`DwarfRuneHudLayout`](../../../../src/main/java/com/lotrcharactercreation/client/trait/DwarfRuneHudLayout.java)
calculates fractional clipping and GUI-pixel placement from Minecraft's 182-pixel
hotbar bounds. Where a scaled screen cannot fit both side rows, they move above the
taller Forge status stack, using `left_height` and `right_height`; exceptionally
narrow widths stack the rows so they cannot overlap. A completed FOOD overlay pass
is required, so canceled or absent vanilla hunger also hides these rows. Hidden
GUI, creative mode, disconnect, and world unload retain their previous
visibility/reset behavior.

Owner-observed runtime acceptance approves the rune artwork and colors, the
left/right hotbar placement, and GUI scaling. Automated partial-fill tests pass.
This evidence does not establish explicit live acceptance of the exhaustion-red
state, the very-narrow-screen fallback, every intermediate partial-fill state, or
every LOTR overlay combination; those combinations remain pending.

The original pixel atlas is
[`dwarf_cirth.png`](../../../../src/main/resources/assets/lotrcharactercreation/textures/gui/dwarf_cirth.png),
generated from the hand-authored bitmap in
[`generate-cirth-atlas.ps1`](../../../../tools/dwarf-hud/generate-cirth-atlas.ps1).
Its fixed **Angerthas Moria** values follow Tolkien's *The Lord of the Rings*,
Appendix E, table of values: ENDURANCE uses 46, 22, 9, 42, 12, 48, 22, 54, 46;
FEASTING uses 3, 46, 48, 54, 8, 39, 22, 19. The English soft `c` in
ENDURANCE is represented by the /s/ certh 54. This is a letter-count-preserving
English sound substitution, not a Khuzdul translation. The [Tolkien Estate's
writing-systems summary](https://www.tolkienestate.com/scholarship/arden-smith-writing-systems/)
distinguishes the Dwarvish Moria mode from historical runes; the [Appendix E
table](https://ctan.math.utah.edu/ctan/tex-archive/fonts/cirth/cirth.pdf)
provides the numbered values. No external font is bundled or loaded at runtime.

[Focused Dwarf HUD tests](../../../../src/test/java/com/lotrcharactercreation/client/trait/DwarfRuneHudLayoutTest.java)
cover fill, partial glyphs, warning, scaled positioning, visibility, exact atlas
pixels, and preservation of the four-field Dwarf state packet. These automated
checks are distinct from the recorded owner-observed runtime acceptance above.

[Character documentation](../../../KOME_CHARACTER_CREATION.md); [legacy compatibility tests](../../../../src/test/java/com/lotrcharactercreation/CharacterCreationLegacyCompatibilityTest.java), [network hardening](../../../../src/test/java/com/lotrcharactercreation/network/LegacyC2SNetworkHardeningTest.java), [skin protocol](../../../../src/test/java/com/lotrcharactercreation/network/CustomSkinManifestProtocolTest.java), [module isolation](../../../../src/test/java/kome/integration/CharacterCreationIsolationTest.java).

```powershell
.\gradlew.bat test --tests 'com.lotrcharactercreation.CharacterCreationLegacyCompatibilityTest' --tests 'com.lotrcharactercreation.network.LegacyC2SNetworkHardeningTest' --tests 'com.lotrcharactercreation.network.CustomSkinManifestProtocolTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Verify incomplete-stage login, back/recreation/finalization, respawn/dimension change, remote-player tracking and custom-skin reconnect with a client. Save player NBT and skin files independently. Symlink checks can be environment-skipped; report that explicitly. Character queue semantics differ from KOME's bounded round-robin queue and require their own validation.
