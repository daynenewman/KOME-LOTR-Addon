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

### KOM-79 Phase 2 Uruk-hai Rage presentation

[`ClientUrukRageState`](../../../../src/main/java/com/lotrcharactercreation/client/trait/ClientUrukRageState.java)
replaces the Rage label, number, and rectangular bar with an edge-only
[`uruk_rage_vessels.png`](../../../../src/main/resources/assets/lotrcharactercreation/textures/gui/uruk_rage_vessels.png)
atlas. A precomputed nine-frame stroke-width animation,
[`uruk_rage_vessels_swell.png`](../../../../src/main/resources/assets/lotrcharactercreation/textures/gui/uruk_rage_vessels_swell.png),
widens the existing branches by up to one source pixel on each side. Both
atlases are generated by
[`generate-vessel-overlay.ps1`](../../../../tools/uruk-hud/generate-vessel-overlay.ps1)
and leave the central 160 by 160 pixels of each frame transparent. The renderer runs in
the Forge `Post(ALL)` pass, scales to the current `ScaledResolution`, performs no
per-frame texture creation, and restores texture, blend, alpha, color, depth-enable,
and depth-mask state through a scoped attribute push. At zero Rage without a flash
it submits no geometry. Rage 1-49 maps linearly from transparent to 0.60 normal
opacity. At 50 and above the original vessel core stays at constant color and 0.60
opacity. A two-beat cycle selects progressively wider strokes, with a period
decreasing from 30 ticks at 50 Rage to 16 ticks at 100. Phase 2C's faint halo was
rejected in owner live testing. Phase 2D replaces it with nine full-stroke frames
in a 1024-square sheet, advancing adjacent pixel coverage to the actual stroke's
alpha at the peak. Existing core pixels (source alpha at least 100) retain their
alpha in every frame. Only one textured quad is submitted; no extra halo is drawn,
and neither branch anchors nor screen geometry move. Width is quantized to nine
states and apparent size depends on resolution. The owner has live-approved the
opacity progression, thickness heartbeat, and existing five-tick flash: a
confirmed hit recolors only the original vessel pixels near white at full texture
opacity, including when Rage falls to zero, then returns to the remaining Rage
display. The original texture is always used during the flash and below 50 Rage.

The existing [`UrukRageStateMessage`](../../../../src/main/java/com/lotrcharactercreation/network/UrukRageStateMessage.java)
remains a boolean plus float. In
[`UrukHaiTraitService.tryHeavyBlow`](../../../../src/main/java/com/lotrcharactercreation/trait/UrukHaiTraitService.java),
the server owns the direct-melee, race, Rage-probability, damage, knockback,
and 50-Rage consumption decision. Phase 2B's **owner-approved balance change**
requires at least 50 Rage at the authoritative damage event. Below 50, empowered
chance is zero; from 50 to 100, the existing `Rage / 100` probability applies.
A successful empowered blow subtracts exactly 50 Rage, leaving 0, 25, or 50 from
starting values of 50, 75, or 100. Damage bonus and knockback strength are unchanged.
Immediately after its forced remaining-Rage sync,
it sends one [`UrukHeavyBlowActivationMessage`](../../../../src/main/java/com/lotrcharactercreation/network/UrukHeavyBlowActivationMessage.java)
only to the attacker. Its monotonic event ID rejects duplicate and older events in
the current connection. Client scheduling is fenced to the play connection that
received the packet. Race deactivation clears a pending flash while retaining the
same-connection event fence; death clears the flash; connect, disconnect, and world
unload reset all Uruk client state. Ordinary Rage changes cannot create the cue.

The owner has live-approved the native LOTR default cue and custom resource-pack
replacement as well as the heartbeat, flash and sound triggering. Phase 2D
fixed the invalid `players` sound category to Minecraft 1.7.10's `player`. Phase
2E uses the installed LOTR Mod v36.15's native `lotr:orc.hurt` audio, which has
`orc/hurt1.ogg` and `orc/hurt2.ogg` variants in `assets/lotr/sounds/`. No native
Uruk-specific vocalization is registered in that installed version. KOME's
[`sounds.json`](../../../../src/main/resources/assets/lotrcharactercreation/sounds.json)
keeps `lotrcharactercreation:uruk.empowered_hit` and registers both native OGG
files as `type: "file"` entries. Minecraft 1.7.10's loader resolves those entries
to `lotr:sounds/orc/hurt1.ogg` and `lotr:sounds/orc/hurt2.ogg`. The earlier
`type: "event"` entry for `lotr:orc.hurt` was silent: that loader uses the whole
qualified event name as its path, looking up `lotr:lotr:orc.hurt` rather than
`lotr:orc.hurt`. The earlier optional KOME file entry produced missing-file
warnings without a pack, so it was removed. No LOTR audio is copied into KOME's
JAR. The KOME event remains separate from `lotr:orc.hurt`; replacing KOME's
event cannot change normal Orc hurt playback.

Minecraft 1.7.10 loads a resource pack's `sounds.json` after the mod's and honors
`replace: true` for the same event. A personal pack therefore replaces the
native-backed KOME event with an OGG at the established
`assets/lotrcharactercreation/sounds/uruk/empowered_hit.ogg` path. The pack must
include its own `sounds.json`; adding only the OGG no longer selects it.
The server activation, owner-only packet 29, connection-fenced client callback
and duplicate event filter remain unchanged. Playback uses volume 1, pitch 1,
no repetition and no positional attenuation; Master and Players volume settings
still apply.

Phase 2F advances the exact matching-artifact handshake from g20 to g21 because
g20 clients lack the decoder for owner-only discriminator 29. Forge calls KOME's
[`@NetworkCheckHandler`](../../../../src/main/java/kome/common/KOMEAddon.java)
on both sides during mod-list admission; it requires the exact
[`KOMEPopulationWire.VERSION`](../../../../src/main/java/kome/common/network/KOMEPopulationWire.java).
Existing g20 development clients and servers require matching g21 builds before
combined testing. No Rage packet shape or server combat decision changes. The
client now retains each KOME cue's `ISound` instance and stops only those instances
on death, race deactivation, world unload, disconnect, or reconnect; naturally
finished cues are removed from tracking. Longer personal recordings can therefore
be stopped at the same lifecycle boundaries without stopping normal Orc hurt audio.

To replace the cue **in a local JAR**, make a backup of the release JAR, open the
copy as a ZIP archive, add an Ogg Vorbis file at
`assets/lotrcharactercreation/sounds/uruk/empowered_hit.ogg`, and replace the
KOME `uruk.empowered_hit` entry in its `sounds.json` with the pack entry below.
Install only the edited copy in the instance's `mods` directory so the mod is
not loaded twice.

To override the cue with an ordinary Minecraft 1.7.10 resource pack, create and
enable a pack with this layout:

```text
KOME Uruk Cue/
  pack.mcmeta
  assets/lotrcharactercreation/sounds.json
  assets/lotrcharactercreation/sounds/uruk/empowered_hit.ogg
```

Use this `pack.mcmeta`:

```json
{"pack":{"pack_format":1,"description":"Local KOME Uruk empowered-hit cue"}}
```

Use this `assets/lotrcharactercreation/sounds.json`:

```json
{
  "uruk.empowered_hit": {
    "replace": true,
    "category": "player",
    "sounds": [
      { "name": "lotrcharactercreation:uruk/empowered_hit", "type": "file" }
    ]
  }
}
```

Encode `empowered_hit.ogg` as Ogg Vorbis; mono 44.1 kHz is recommended for a short
player cue. Place the pack in the instance's `resourcepacks` directory, enable it,
then reload resources with F3+T. The pack's event loads its same-path OGG and
replaces the native-backed KOME event. Removing the pack restores the
native Orc fallback after resource reload. A malformed replacement
may fail playback and should be corrected; it cannot affect server damage,
Rage consumption, knockback or the visual flash. Use only audio the player has
the right to use; no movie recording or native LOTR audio is bundled.

[Focused Uruk HUD tests](../../../../src/test/java/com/lotrcharactercreation/client/trait/UrukRageHudTest.java)
cover zero-Rage invisibility, linear intensity through the exact 50-Rage threshold,
accelerating bounded thickness heartbeat, stable core opacity and color,
full-opacity flash at zero remaining Rage,
duplicate/stale rejection, lifecycle resets, both wire codecs, sound registration,
and all width frames' anchored core, stronger peak coverage and transparent-center
contracts. The sound test uses Minecraft's real category parser, verifies both
native Orc variants are registered and decodable from the installed LOTR JAR,
checks the KOME-path pack replacement entry, and checks nonattenuated playback
parameters. The earlier cross-mod event entry was silent; the owner subsequently
live-approved the revised native default and custom resource-pack replacement. The
[server Rage rules tests](../../../../src/test/java/com/lotrcharactercreation/trait/UrukHeavyBlowRulesTest.java)
cover the approved eligibility, probability, and fixed cost.
Multiplayer mismatch admission and audio stopping across death, race change,
world unload, and reconnect require live testing. Compatibility with every damage,
fire, portal, potion, and LOTR overlay combination also remains pending.

[Character documentation](../../../KOME_CHARACTER_CREATION.md); [legacy compatibility tests](../../../../src/test/java/com/lotrcharactercreation/CharacterCreationLegacyCompatibilityTest.java), [network hardening](../../../../src/test/java/com/lotrcharactercreation/network/LegacyC2SNetworkHardeningTest.java), [skin protocol](../../../../src/test/java/com/lotrcharactercreation/network/CustomSkinManifestProtocolTest.java), [module isolation](../../../../src/test/java/kome/integration/CharacterCreationIsolationTest.java).

```powershell
.\gradlew.bat test --tests 'com.lotrcharactercreation.CharacterCreationLegacyCompatibilityTest' --tests 'com.lotrcharactercreation.network.LegacyC2SNetworkHardeningTest' --tests 'com.lotrcharactercreation.network.CustomSkinManifestProtocolTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Verify incomplete-stage login, back/recreation/finalization, respawn/dimension change, remote-player tracking and custom-skin reconnect with a client. Save player NBT and skin files independently. Symlink checks can be environment-skipped; report that explicitly. Character queue semantics differ from KOME's bounded round-robin queue and require their own validation.
