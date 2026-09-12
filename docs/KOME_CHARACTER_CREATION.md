# KOME Character Creation

Current source of truth for the KOM-45 integration of Character Creation into KOME.

## Purpose and provenance

Character Creation began as a standalone mod developed by Ben. KOM-45 embeds that subsystem directly in the combined KOME addon. `kome.common.KOMEAddon` is the sole Forge `@Mod` container; it delegates Character Creation lifecycle work to `com.lotrcharactercreation.LOTRCharacterCreation` during KOME pre-initialization, initialization, and server startup.

The existing `com.lotrcharactercreation` package, `lotrcharactercreation` configuration and NBT names, and `lotrcreation` network-channel name are retained intentionally for compatibility. They do not represent a second loaded mod.

Do not install the former standalone Character Creation jar beside KOME. LOTR Mod v36.15 remains an external, required dependency and is not modified or bundled by this integration.

Key implementation entry points are:

| Responsibility | Source |
| --- | --- |
| KOME lifecycle and command registration | `kome.common.KOMEAddon` |
| Character Creation lifecycle bridge | `com.lotrcharactercreation.LOTRCharacterCreation` |
| Persistent player state | `com.lotrcharactercreation.race.PlayerRaceData` |
| Staged server authority | `com.lotrcharactercreation.creation.CharacterCreationFlowService` |
| Safe administrative editing | `com.lotrcharactercreation.creation.CharacterRecreationService` |
| Body definitions and refresh | `RaceBodyDefinition`, `PlayerRaceSizeService`, `PlayerRaceEyeService` |
| Appearance catalogs and validation | `AppearancePresetRegistry`, `AppearanceSelectionRules` |
| Server custom-skin authority | `ServerCustomSkinLibrary` and `CustomSkinSnapshot` |
| Client custom-skin state | `ClientCustomSkinManager` and `ClientCustomSkinCache` |
| Character Creation networking | `com.lotrcharactercreation.network.ModNetwork` |

## Supported character flow

The playable races are Man, Elf, Dwarf, Hobbit, Orc, and Uruk-hai.

Creation proceeds through server-derived stages rather than a client-owned stage value:

1. Race.
2. Sex for Man, Elf, Dwarf, and Hobbit. Orc and Uruk-hai use the inherent `none` value and skip this screen.
3. Optional starting faction or Wanderer.
4. Appearance.
5. Confirmation and finalization.

`CharacterCreationFlowService.getNextRequiredStage` derives the next stage from persisted choices. Every selection and Back request is checked against that stage on the server. Race, sex, faction, and preset membership are validated again before state is changed, and finalization is accepted only from the confirmation stage.

Normal completed characters are immutable: a completed player cannot change race, sex, or appearance by sending selection packets. The only supported exception is the server-owned administrative recreation session described below. Reconnecting during incomplete first-time creation resumes the next required stage; a completed character is not reopened, and one-time effects cannot be replayed by repeated login or packet submission.

## Starting faction, allegiance, and waypoint

Starting-faction choices are filtered by race and apply only to Character Creation. They do not restrict the ordinary LOTR pledge system after creation.

The `characterCreation.automaticStartingAllegiance` option in `config/lotrcharactercreation.cfg` defaults to `true`:

- When enabled, a non-Wanderer choice grants only the minimum alignment required for the selected LOTR faction and establishes the pledge, subject to LOTR's authoritative pledge checks.
- When disabled, the choice determines only the starting location; alignment and pledging remain normal LOTR gameplay.
- Wanderer creates no automatic starting pledge and performs no starting teleport. The one-time gates are still marked complete so the character can finish safely.

Starting allegiance and starting location have separate persisted one-time guards: `startingFactionApplied` and `startingWaypointApplied`. The selected waypoint code is stored before teleport so a failed attempt retries the same location rather than rerolling on every login.

Waypoint selection uses visible LOTR waypoints belonging to the chosen faction. Two curated rules cover gaps in LOTR v36.15 data:

- Dúnedain of the North and Morwaith may use a non-hidden, unaligned waypoint at an exclusive center of their faction control zone when no direct faction waypoint exists.
- Durin's Folk uses its regular faction waypoint set, including Erebor, plus explicit East Peak and West Peak candidates.

Successful first-time completion applies allegiance and waypoint at most once, refreshes race-derived state, synchronizes appearance, and sends LOTR's full player data to the client. Administrative recreation bypasses both starting-effect services.

## Appearance and rendering

`AppearancePresetRegistry` owns the immutable built-in catalog. Pools are race-, sex-, and where applicable group- or faction-specific. Man, Elf, Dwarf, and Hobbit have male/female pools; Orc and Uruk-hai use inherent, sexless pools. Man additionally offers the current Minecraft account skin through the dedicated `MINECRAFT_ACCOUNT` source type.

Player rendering uses adapters around LOTR NPC-style models, race-appropriate armor models, synchronized first-person arms, and race-specific held-item positioning. Appearance synchronization covers the local player, tracking clients, respawn and dimension lifecycle, and LOTR map player markers. Rendering falls back to a built-in preset of the correct race and sex when an external texture is unavailable; the saved logical preset ID is not rewritten merely because client content is pending.

The following v1 rendering fixes are part of the integrated result:

- Local LOTR map heads use the entity-aware synchronized appearance lookup; remote heads remain profile-UUID based.
- A missing female Dwarf texture falls back to `dwarf_standard_f_0`, not a male texture.
- Hobbit third-person fishing rods use the original fishing rod for correction classification in both cast and uncast states.

### Body and model values

These are the current authoritative values in `RaceBodyDefinition` and `RacePlayerRenderer`:

| Race | Width | Height | Eye height | Render scale | Player model |
| --- | ---: | ---: | ---: | ---: | --- |
| Man | 0.60 | 1.80 | Vanilla/default calculation | 1.0 | `PlayerManModelAdapter` / `LOTRModelHuman` |
| Elf | 0.60 | 1.80 | 1.53 | 1.0 | `PlayerElfModelAdapter` / `LOTRModelElf` |
| Dwarf | 0.50 | 1.50 | 1.275 | 0.8125 | `PlayerDwarfModelAdapter` / `LOTRModelDwarf` |
| Hobbit | 0.45 | 1.20 | 1.02 | 0.75 | `PlayerHobbitModelAdapter` / `LOTRModelHobbit` |
| Orc | 0.50 | 1.55 | 1.3175 | 0.85 | `PlayerOrcModelAdapter` / `LOTRModelOrc` |
| Uruk-hai | 0.60 | 1.80 | 1.53 | 1.0 | `PlayerOrcModelAdapter` / `LOTRModelOrc` |

Man deliberately retains the vanilla/default eye-height path. Dwarf render scale `0.8125` is also intentional: live comparison found it matches a native LOTR Dwarf NPC more closely than `0.85`, which made the player visibly taller by roughly one to two Minecraft pixels. Width, collision height, eye height, and visual render scale are separate values.

Body, eye-height, presentation, and derived traits are refreshed on login, respawn, dimension change, race selection, recreation transitions, and completion. Remote clients receive the synchronized appearance rather than deriving another player's race from local data.

## Implemented gameplay traits

Traits are server-authoritative where they affect gameplay and are active only after `characterCreationComplete`. Starting or resuming administrative recreation immediately removes completed-only derived state; completing the new character applies the newly selected race state.

| Race | Implemented v1 gameplay traits |
| --- | --- |
| Man | Standard health and movement; a 25% additional proc opportunity for held-item LOTR on-kill enchantment handling. |
| Elf | `+10` maximum health; poison removal; an additional normal-food saturation contribution; `+0.05` jump velocity; a 25% chance to shove a direct melee attacker; hostile-NPC grapple with cooldown and cleanup; susceptibility to LOTR Elf-bane damage. |
| Dwarf | `+6` maximum health; 25% correct-tool mining-speed bonus; stamina-backed sprint bonus; feast overflow from food, digestion, and feast-scaled knockback resistance; susceptibility to LOTR Dwarf-bane damage. |
| Hobbit | `-4` maximum health; half fall damage; a 50% chance for one extra mature crop item; reduced hostile LOTR NPC acquisition range until engaged; chargeable hand pebble, sling, and conker attacks; additional underwater air loss. |
| Orc | Sheltered/night bonuses to maximum health, speed, night vision, and natural regeneration; exposed-daylight penalties to health, speed, breaking, melee damage, and periodic nausea; no new hunger effect from rotten flesh or maggoty bread; Orc-draught damage tolerance; susceptibility to LOTR Orc-bane damage. |
| Uruk-hai | `+6` maximum health; `+0.50` knockback resistance; 25% projectile-damage reduction; 25% axe/log chopping-speed bonus; damage-fed rage with probabilistic heavy blows; the same hunger-food and Orc-draught tolerance as Orcs; susceptibility to LOTR Orc-bane damage. |

Model choice, body dimensions, eye height, first-person arms, sounds, armor presentation, and held-item transforms are presentation/body behavior, not additional gameplay traits.

## Server-authoritative custom skins

The server is the authority for approved external appearances. Its source layout is:

```text
config/lotrcharactercreation/custom_skins/
  <race>/
    <sex>/
      <group>/
        <filename>.png
```

Directory names use the existing serialized race, sex, and supported appearance-group identifiers. Hobbit uses the `default` group directory. Accepted filenames are lowercase `.png` files whose stems contain only lowercase ASCII letters, digits, `_`, or `-`.

Each accepted entry retains the compatible deterministic ID:

```text
custom_<race>_<sex>_<group-token>_<filename-stem>
```

Content hashes are deliberately not part of preset IDs, so changing bytes does not rewrite player NBT. The immutable `ServerCustomSkinLibrary` snapshot contains validated metadata and exact PNG bytes. Validation includes canonical path and symlink containment, file and library limits, PNG structure and CRC checks, full ImageIO decoding, exact race dimensions, deterministic-ID and built-in-ID collision checks, and SHA-256 over the transferred bytes.

Operational limits are:

- 256 KiB per PNG.
- 512 accepted entries.
- 32 MiB total validated external content.
- 64x64 images for Man, Elf, Dwarf, and Hobbit.
- 64x32 images for Orc and Uruk-hai.

### Manifest, download, and cache

On login the server proactively sends the current manifest. The client validates the complete paged manifest and atomically activates connection-scoped metadata. It then validates its content-addressed cache and requests only missing or invalid hashes. The server sends requested PNG data in bounded chunks, and the client independently validates byte count, SHA-256, PNG structure, decode result, and dimensions before promoting a temporary file.

The client cache is:

```text
config/kome/client_skin_cache/
  sha256/
    <first-two-hash-characters>/
      <64-character-lowercase-sha256>.png
```

Cache destinations derive only from validated hashes; server filenames never become client write paths. Cache blobs persist across disconnects, while active catalogs, transfers, failures, and DynamicTexture mappings are connection-scoped. A reconnect reuses valid content without downloading it again. A missing or corrupt blob is downloaded again. If the same preset ID is advertised with a new hash, the old DynamicTexture is invalidated and cannot masquerade as the new content.

Clients no longer need a manually matching `custom_skins` directory. A legacy local-import path remains for compatibility, but it is not required for server distribution and does not replace the connected server manifest as authority.

Custom-skin hot reload is not part of v1. After adding, changing, or removing a server skin, restart the server so it rebuilds the snapshot, then have clients reconnect and accept the new manifest.

## Networking

Character Creation uses the existing Forge `SimpleNetworkWrapper` channel named `lotrcreation`. Discriminators are fixed at `0` through `28` with no collisions:

| ID | Direction | Purpose |
| ---: | --- | --- |
| 0 | S2C | Require/open the next Character Creation stage |
| 1 | C2S | Race selection |
| 2 | S2C | Race-selection result |
| 3 | C2S | Starting-faction selection |
| 4 | S2C | Starting-faction selection result |
| 5 | S2C | Starting-faction application result |
| 6 | S2C | Starting-waypoint application result |
| 7 | S2C | Player appearance synchronization |
| 8 | S2C | Open appearance selection |
| 9 | C2S | Appearance selection |
| 10 | S2C | Appearance-selection result |
| 11 | S2C | Open sex selection |
| 12 | C2S | Sex selection |
| 13 | S2C | Sex-selection result |
| 14 | C2S | Finalization request |
| 15 | C2S | Back navigation |
| 16 | S2C | Dwarf stamina/feast state |
| 17 | S2C | Uruk-hai rage state |
| 18 | S2C | Elf grapple state |
| 19 | C2S | Elf grapple attack request |
| 20 | S2C | Custom-skin manifest begin |
| 21 | S2C | Custom-skin manifest page |
| 22 | S2C | Custom-skin manifest end |
| 23 | S2C | Custom-skin transfer start |
| 24 | S2C | Custom-skin transfer chunk |
| 25 | S2C | Custom-skin transfer end |
| 26 | C2S | Client manifest-ready acknowledgement |
| 27 | C2S | Missing-content request page |
| 28 | C2S | Transfer result |

All legacy C2S actions still pass through stage, authorization, value, and target validation. Admission is additionally bounded to 32 queued actions per player and 2,048 globally, with disconnect and server-session cleanup. Identifier strings are decoded with explicit UTF-8 byte limits before gameplay validation.

Phase 7 skin synchronization retains separate limits: at most 64 identities per request page, 23 KiB content chunks, two simultaneous transfers per connection, bounded server-main-thread actions, finite retries, and declared-size/count checks before allocation. Network handlers enqueue work onto the appropriate main thread; DynamicTexture creation, replacement, and deletion remain on the client/render thread.

## Persistence

Character Creation state is stored with ordinary Forge player persistence:

```text
ForgeData
  PlayerPersisted
    lotrcharactercreation
```

The important keys are:

| Key | Purpose |
| --- | --- |
| `race` | Serialized authoritative race ID. |
| `sex` | Serialized male, female, or inherent `none` value. |
| `appearancePreset` | Built-in, account-skin, or deterministic external preset ID. |
| `appearanceInitialized` | Confirms the saved preset for the current selection path. |
| `raceSelectionComplete` | Marks completion of the race stage. |
| `startingFaction` | Serialized starting-faction or Wanderer choice. |
| `factionSelectionComplete` | Marks completion of the faction stage. |
| `startingFactionApplied` | One-time allegiance application guard. |
| `startingWaypoint` | Selected LOTR waypoint code used for stable retry. |
| `startingWaypointApplied` | Independent one-time teleport/application guard. |
| `characterCreationComplete` | Enables normal completed-character body, rendering, and traits. |
| `characterEditAuthorized` | Server-owned authorization for an administrative recreation session; absent means unauthorized. |
| `dwarfStamina` | Persistent Dwarf stamina resource. |
| `dwarfFeast` | Persistent Dwarf feast resource. |
| `dwarfStaminaExhausted` | Persistent sprint restart gate. |
| `elfGrappleCooldownTicks` | Persistent Elf grapple cooldown. |
| `elfGrappleCleanupPending` | Ensures interrupted grapple mount state is cleaned safely. |

Forge cloning preserves this namespace through death. Login, respawn, dimension change, and reconnect reapply body and derived state from these fields. Missing legacy fields default conservatively; `characterEditAuthorized` is false unless explicitly set by the server. External logical preset IDs remain persisted even while client content is unavailable.

## Safe administrative recreation

The supported command is:

```text
/kome character recreate <online-player>
```

The subcommand requires command permission level 2 and may be run by an authorized player or the console. The target must be online. It is idempotent for an already-authorized recreation and refuses to layer a recreation session over an unrelated incomplete first-time character.

Starting recreation sets the target-specific, persisted `characterEditAuthorized` gate, reopens the race stage, and marks creation incomplete. It immediately refreshes body, eye height, completed-only traits, first-person state, and synchronized appearance so the old completed race does not linger while the GUI is open. Existing race, sex, and preset data are not destructively erased until the normal staged choices replace or invalidate them.

The player can then choose a new race, legal sex, faction screen option, and appearance, including a server-authorized external preset. Completion uses normal selection validation but deliberately bypasses first-time allegiance and waypoint services. It preserves dimension, position, rotation, inventory, progression, live LOTR pledge, and alignment. It also preserves both one-time applied flags. The authorization persists across a mid-session disconnect and is cleared automatically only after successful completion.

## Commands

| Command | Permission | Role |
| --- | ---: | --- |
| `/character` | 0 | Reopen the next required screen for the executing player while their character is incomplete. It cannot edit a completed character. |
| `/kome character recreate <player>` | 2 for this subcommand | Supported, safe online-player recreation workflow. |
| `/lotrcreation` | 2 | Player-only status/debug command. The legacy `complete` and `reset` mutations are retained as disabled compatibility entries and direct staff to the safe workflow. |
| `/lotrrace ...` | 2 | Player-only OP/debug inspection and mutation tooling. It is not the supported normal recreation path. |

`/lotrcreation complete` does not set completion or any stage flag. `/lotrcreation reset` does not clear state. Useful status and `/lotrrace` debugging remain available to operators; ordinary players cannot invoke their mutation paths.

## Alignment HUD synchronization

First-time completion ends with `LOTRLevelData.sendPlayerData(player)`, LOTR v36.15's native full player-data synchronization. Starting allegiance remains server-authoritative; this call does not grant alignment, set a pledge, or replay any Character Creation effect. It only ensures that the local client's LOTR alignment cache and HUD immediately reflect the authoritative result.

As a development troubleshooting note, an offline-mode dedicated server can assign a UUID different from the dev client's session UUID. LOTR's incremental UUID-keyed alignment updates may then miss the local HUD even though the server value is correct. The full post-creation sync avoids a relog at the lifecycle point KOME controls; production online-mode identity should not exhibit that artificial mismatch.

## Authority and safety model

- Creation stages and all gameplay selections are owned and validated by the server.
- A completed client cannot authorize itself or mutate race, sex, or appearance.
- Recreation authorization is server-owned, player-specific, persisted for safe resume, and cleared by successful completion.
- Client strings, legacy request admission, skin manifest pages, requests, chunks, transfers, and retries are explicitly bounded.
- The server's immutable custom-skin snapshot is the only authority for external preset metadata and bytes.
- Clients validate downloaded data independently and can write only to hash-derived cache paths.
- Client custom-skin catalogs are connection-scoped, preventing one server's metadata or textures from becoming authoritative on another server, including in an integrated-server JVM.
- Common/server code uses sided proxies and handlers; client rendering and DynamicTexture classes remain client-side.

## Verification status

The final KOM-45 verification checkpoint completed with:

| Scope | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Character Creation | 137 | 0 | 0 | 2 environment-dependent |
| Full project | 311 | 0 | 0 | 4 |

- `gradlew build --no-daemon`: **SUCCESS**
- `git diff --check`: **PASS**
- Final manual regression: **PASS**

Manual coverage included all six races and their physical presentation, creation lifecycle, Wanderer, pledge/alignment and one-time waypoint behavior, built-in and Minecraft-account Man appearances, server-only custom-skin download/cache reuse, two-client appearance synchronization, LOTR map heads, death/respawn, reconnect, dimension change, safe administrative recreation including reconnect, immediate alignment HUD refresh, and dedicated-server startup.

The automated environment-dependent skips are recorded as skips, not failures; no unperformed behavior is implied by the totals above.

## Deliberate v1 boundaries

The following are deliberately deferred and are not Character Creation defects:

- Vanilla/normal-Minecraft character opt-out.
- Custom-skin hot reload; v1 uses server restart plus client reconnect.
- Additional playable races.
- Mount and sleep visual perfection.
- Optional OptiFine and third-party renderer polish.

These boundaries do not introduce alternate creation, identity, or custom-skin authority paths.

## Deployment and operations

1. Install LOTR Mod v36.15 and the combined KOME jar using the project's normal client/server deployment. Everyone on the server should use the matching KOME build.
2. Do not install the former standalone Character Creation jar and do not replace or patch the LOTR jar.
3. Retain `config/lotrcharactercreation.cfg`; review `characterCreation.automaticStartingAllegiance` before opening a production world.
4. Place approved external PNGs only in the server's `config/lotrcharactercreation/custom_skins/<race>/<sex>/<group>/` hierarchy.
5. Restart the server after any custom-skin addition, change, or removal. Clients reconnect, receive the manifest, and populate `config/kome/client_skin_cache/sha256/` automatically.
6. Use `/kome character recreate <player>` for a completed online player who needs race/sex/appearance recreation. Do not use legacy direct-completion or reset workflows.

Invalid external files are excluded with diagnostics and cannot enter the authoritative manifest. Client cache files are disposable downloaded content; deleting or corrupting one causes validation failure and a new download on the next connection.

## Milestone commits

The integration's principal milestones in `Ben's-Branch` history are:

| Commit | Milestone |
| --- | --- |
| `c7123d7` | Character Creation: fix local LOTR map head |
| `6fdbe9b` | Character Creation: fix female Dwarf fallback |
| `45f6e69` | Character Creation: fix Hobbit fishing rod position |
| `212055a` | Character Creation: add custom skin library foundation |
| `5827cf6` | Character Creation: add client custom skin cache |
| `aff3281` | Character Creation: add server custom skin sync |
| `87db7be` | Character Creation: add safe admin recreation |
| `51909cb` | Character Creation: fix alignment sync after creation |
| `0c36aae` | Remove tracked Phase 7 runtime artifact |
| `34629c3` | Character Creation: refresh state when recreation begins |
| `047d9a9` | Character Creation: disable unsafe direct completion |
| `206dd9c` | Character Creation: harden legacy C2S requests |
