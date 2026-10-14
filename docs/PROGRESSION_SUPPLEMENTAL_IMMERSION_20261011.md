# Supplemental progression immersion — 2026-10-11

This supplements the [combined implementation report](PROGRESSION_KNIGHT_LORDSHIP_PASS_20261011.md).
The working tree was inspected and the ongoing implementation was extended, not
restarted. The branch, existing saves, stashes and runtimes were preserved. Nothing
was committed, pushed or merged. Code/fixture verification is distinct from live
Minecraft acceptance; no Minecraft client or gameplay server was launched.

## 1. Feature status

| Supplemental requirement | Status |
|---|---|
| 1. Staged native military trial | Implemented in code; real battle playability awaits live testing. |
| 2. Believable stolen equipment | Implemented for armor, weapons/valuables through native equipment; shield-specific rendering is not added because the current property pool does not issue shields. |
| 3. Explicit Knight arrival/preparation | Implemented using existing stage plus saved preparation/activation flags. |
| 4. Prince discovery | Implemented: cultural host guidance and personal last-known identities; no unknown-Prince overlay/spawn. |
| 5. Native encounter variety | Implemented: weighted native units, bounded formation/site selection and limited heavy mix. Live difficulty balance remains unverified. |
| 6. Realistic journey selection | Implemented: preferred wider journeys, six-sample burden estimate and real shorter fallbacks. Actual walk times/path reachability remain unmeasured. |
| 7. Configurable safe Elf effect | Partially implemented: native nighttime appearance plus optional lighting adapter. Without a compatible engine there is no actual illumination. |
| 8. Rank responsibility continuity | Implemented through orders, temporary soldiers, survival, reporting and cultural authority; no new Lord/Prince duties. |
| 9. Technical/gameplay failure fairness | Implemented for touched encounter preparation/reconciliation paths; live fault/restart acceptance remains outstanding. |
| 10. Verification | Automated result recorded below; live visual/multiplayer tests remain required. |
| 11. Supplemental report | Implemented: this document. |

## 2. Native systems reused

- `LOTREntityInvasionSpawner` and `LOTRInvasions`: actual native invasion spawning,
  troop weighting, life/progress accounting, watchers, pacing, reversion and outcome.
- Native biome invasion/NPC-faction tables and diplomatic hostility, banner protection,
  spawn protection and mob-spawn rules. Existing KOME regional filters remain authoritative.
- Native NPC armor slots, idle/combat equipment, AI, hiring/follow/wait behavior and
  speech/announcement delivery. Temporary command does not create permanent army records.
- Existing KOME commission/trial records, tokens, actor leases, recovery/drop policy,
  saved shelter evidence, native map projection and book/rank snapshot.
- LOTR blue-flame particle batch and synchronized Character Creation race cache.
  The existing client config contains the new display options.

## 3. Staged invasion lifecycle

The existing saved stages are sufficient: OFFERED means orders await acceptance;
ACTIVE with `encounterCreated=false` means travel/preparation; ACTIVE with an initialized
native controller means battle; READY_TO_REPORT means earned field victory; REPORTED
means Prince conferral. Technical preparation uses saved `activationPending` and
`activationRetryAt`, not an artificial failed military trial.

At acceptance the Prince names the enemy faction, actual marked ground/biome and
coordinates, four entrusted soldiers and personal survival/victory/report conditions.
Rohan and Gondor orders have distinct openings; authority titles and host guidance
come from the existing cultural definitions. No nonexistent village/fort is named.
Four real temporary allied NPCs follow the player before any invading force exists.
Partial soldier issuance is rolled back without taking service or alignment.

At a 40-block staging approach, the server validates naturally loaded chunks, safe
open terrain, native faction/region eligibility, permitted composition and native
spawn protection. It saves the controller identity and successful encounter state.
The actual native controller starts with 30 invasion lives, within the native default
30–70 range. Ordinary Knight 5–10 encounter quotas are not used to certify its victory.
There is no exact-coordinate arrival requirement and no force loading.

Every native invader is bound by the controller's exact invasion UUID and the player's
assignment token. The outcome hook observes actual native `endInvasion` success.
Removal, chunk absence, environmental deaths, soldier losses and waiting are not
victory. A recorded participating player must survive; an online player must remain
alive at the field. Actual native victory while a previously participating owner is
offline is saved for their later report. Recorded player death remains failure through
logout/reload. A zero/uninitialized or aborted controller is technical recovery.

All soldiers may die. The Prince acknowledges a victory without recorded losses,
victory with losses, or a costly victory with the whole force lost. These differences
do not change qualification. The final right-click report bypasses the normal GUI only
when it can legitimately confer standing. Prince conferral archives the trial and
announces the normal public promotion exactly once.

## 4. Prince authority and discovery

Qualification remains three distinct credited commissions for the pledged faction and
2,000 current alignment. A Lord remains the Knight's service Liege while a separate
Prince authority is saved for the trial. Promotion transitions service to that Prince.
No commission evidence is discarded merely to seek higher authority.

The current Lord's Quest interaction acknowledges readiness and points toward native
cultural hosts: for example, High Marshals in Rohan's fortress context, rather than
inventing a named Prince at a guessed coordinate. Cultural title definitions explain
which native noble type can hold Prince standing. The supported noble infrastructure
assigns Prince standing on native initial spawning; existing-save nobles retain their
migrated authority. Rare availability in old explored worlds is not solved by forcing
new Princes into existence. Unsupported authority contexts remain explicit limitations.

A personally interacted, same-faction Prince is remembered in a bounded eight-entry
player service record. Its actual last-seen position survives reload and refreshes
from loaded movement evidence. Invalid/dead/reranked authorities cease to be useful
targets. A qualified player can use a known Prince target or the saved issuing Prince;
an unknown loaded Prince is not automatically revealed. If none is known, the book
provides general host guidance and Show Me has no fictional target. No global Prince
overlay or independent noble-discovery architecture was created.

## 5. Stolen equipment and recovery

The original sealed property wrapper caused the incorrect item identity. New property
uses the real native item and saved damage value with private assignment binding.
Valid old wrappers migrate without increasing the number of items. Bound item use,
armor equip, crafting, smelting, vanilla/native anvils and native selling are guarded
so recovery replacements cannot be converted into ordinary usable equipment.

Exactly one enemy UUID carries the item. Native armor uses its matching slot; a
leather cap appears on the thief's head and does not replace the combat weapon.
Weapons and other valuables use native idle hand equipment while native combat
equipment remains available to AI. Combat may temporarily switch the displayed idle
valuable to the weapon. Serf recovery equipment uses the same native processing/use guard. Actual native trial invaders retain normal native loot; only temporary force/ordinary quest equipment uses the existing cleared-drop policy. Hostile actors do not receive friendly guard commands. The current property pool has no shield assignment; a custom
shield/offhand renderer was not invented for a nonexistent generated item type.

The death reservation and actor death record protect a single persistent bound drop
against callback replay. Any uncancelled legitimate carrier death can release it,
including another player, allies or environmental damage. Ordinary matching equipment
does not complete the quest. Missing carrier recovery reuses one slot and loaded old
actors reconcile against its replacement UUID. Item revisions, finite replacement
allowance and stale-copy cleanup survive saves. The Serf lost-object trial has no
hostile thief and remains truthful about that encounter type.

## 6. Knight activation and failure recovery

Assignment generation saves destination proof, troop composition and stable spawn-area
coordinates before travel. Location encounters do not spawn hostiles simply because
the assignment is accepted or another player loads the destination. The owner must
approach on the authoritative server. The force becomes initialized only after its
required spawn transaction succeeds; partial actors are cleaned up.

Pre-engagement defense loss, unsafe terrain, unavailable chunks/actors and exhausted
technical enemy recovery produce preparation/retry/reassignment rather than failed
Liege penalties or free success. Enemy reconstruction is bounded and token/slot/class
bound. Confirmed protected-objective loss after meaningful engagement remains genuine
failure. Touched legacy trial activation follows the same technical distinction.
Neither guard absence nor guard deaths impose a Lordship survivor quota.

Technical reassignment waits for preparation checks and is persisted/rate-limited to
at most once per 1,200 world ticks. It does not reroll every click or reset credited
service. This limits deliberate invalidation/reassignment spam; it is not a complete
anti-grief model for other players editing an objective site.

The settlement icon is the saved spawn-location record, not an individual moving
resident/enemy. Its one objective anchor persists across reload and disappears when
the assignment ends. Attacker sites use collision, natural-ground, local structural
and tile-entity checks around the verified shelter approach. No monster-spawner block
or generated settlement is placed. Large native fortress extents remain a limitation.

## 7. Composition and geographic variation

Ordinary mandatory forces use 5–10 weighted validated native entries. Region, attacking
faction, actual hostility, hiring/mount/boss restrictions and adult NPC suitability
are checked. Melee/ranged/specialized/leader variety comes from existing faction tables,
not invented mobs. Dangerous giant/siege compositions are excluded from ordinary
charges; elite/berserker mixes are limited when ordinary alternatives exist. There is
no health scaling, and incidental escort ambushes retain their independent rules.

Token-based approach angles, bounded safe-site attempts and saved actual actor positions
provide variation while keeping a force near its objective. Defense uses 24–40 blocks
around the shelter approach and activates when the owner is within 40 blocks; other
ordinary attacking forces retain 48–64 block safety staging. These are local geometry
rules, not a verified whole-settlement perimeter model. Native pathfinding and real
reaction time require live tuning.

## 8. Travel and expected experience

Serf courier selection is 500–1,500 horizontal world blocks. Other Serf travel is not
uniform: recovery is 112–176 blocks; Standing escort uses real shelters from 256 up to
3,000; gathering duties do not generate a journey. Previously Knight profiles shared
the courier's 500–1,500 range.

Knight missions now prefer 1,000–3,000 blocks, with recorded shelters scored toward
about 2,000. Six intermediate biome samples estimate water/mountain burden without
full-world pathfinding. Recent destinations affect selection. Native region/control,
waypoint coordinates and actual hostile borders remain the basis for geography.

When broader travel is unavailable, real 500–999 and then 64–499 block sites remain
valid fallbacks. A sole legitimate nearby shelter is kept rather than inventing a
farther settlement. The remaining minimum avoids placing the encounter directly on
the issuing authority. If no safe geographic option exists, another assignment type
or explicit reassignment is used. Expected experience is wider faction service with
local exceptions, not a guarantee of exactly twice every route's walking time.

## 9. Elven appearance, illumination and configuration

Native Elf classes and actual synchronized Elf player race receive subtle blue foot
particles at night. Tormented Elf, other races, incomplete player creation, death,
invisibility, sneaking and player sleep are excluded. Celestial time prevents dark
daytime buildings/weather from enabling the effect; skyless dimensions are excluded.
Particles use the native LOTR batch and soften at dusk/dawn.

The optional AtomicStryker 1.7.10 Dynamic Lights adapter provides real client local
illumination only when that compatible engine is present. It uses light level 3,
unspawned/unsaved foot attachments and a maximum of sixteen nearby sources. No KOME
light blocks, terrain writes, server light packets or new lighting engine were added.
Vanilla light is monochrome; the decorative appearance is blue. The repository's
declared dependencies do not include Dynamic Lights, so the addon alone has cosmetic
appearance only. Actual installed-engine compatibility/propagation is not live verified.

The existing client configuration adds independent `elves.visualAura` and
`elves.dynamicIllumination` toggles, opacity clamped to 0–0.4 (default 0.28), and
particle interval clamped to 8–40 ticks (default 8). Reduced graphics halves density;
minimal particles disables appearance without pretending to disable a separate real
lighting integration. Config cannot change server lighting rules.

Eligibility/source cleanup runs on daylight, movement out of range, death/despawn,
invisibility, world changes and disconnect. The API source registers once and moves
with the feet. Failure disables the optional integration and kills its attachments.
Mock adapter tests cover movement, two sources, unregister/dead attachment and world
disconnect; they do not measure real light propagation, chunk cost, flicker or FPS.

## 10. Save and multiplayer safeguards

Owner UUID, assignment token, entity UUID, native invasion UUID and item revision remain
server authority. Nearby assignments do not share enemies. Native invaders belonging
to another controller/ordinary event do not become trial targets. Another player's
loaded chunks do not activate the owner's mission. Packet/report replay cannot duplicate
credits, rewards or promotion. Marker/lighting configuration is client presentation.

Version-1 trial saves continue their actual saved field objectives, remove the soldier
quota, retain service evidence and require a Prince for final Lord standing. They are
labelled saved trials, not retroactively described as native invasions. New trials use
the real controller. Guard/actor absence is not automatically death. Native controller
absence uses loaded-evidence grace and technical recovery. Unloaded obsolete entities
reconcile when they return; no chunks are forced merely to remove them.

There is no atomic transaction across every native chunk and player/world save. Sudden
termination during activation, conversion/recovery, disconnected owners and multiple
nearby objectives require live save/reload tests. Matching g7 artifacts are necessary
because marker roles and native item-icon payloads changed.

## 11. Exact automated results

- New focused tests: **31 passed, 0 failed, 0 skipped** (20 new Knight/Lordship functional cases, 6 presentation/config cases, 2 optional-light adapter lifecycle cases, 2 native hook cases, and 1 short-shelter fallback case added to the geography suite).
- Final focused run: **599 passed, 0 failed, 0 skipped**, 79 suites.
- Final full run: **3,210 total; 3,205 passed, 0 failed, 0 errors, 5 skipped**, 321 suites.
- Normal Gradle `build`: **SUCCESS**. Explicit `reobfJar`: **SUCCESS**.
- `git diff --check`: **PASS**, exit 0.
- Minecraft client/gameplay server, visual acceptance, connected multiplayer and FPS profiling: **not run**.

Commands used under `Local\KOME-Heavy-Validation`, with the existing cached JDK 17 / Gradle 8.5 and Java 8 target:

```powershell
.\gradlew.bat test --tests '*Knight*' --tests '*Lordship*' --tests '*Progression*' --tests '*Elven*' --offline --no-daemon --max-workers=2
.\gradlew.bat test build reobfJar --offline --no-daemon --max-workers=2
git diff --check
```

The five existing environment skips were:

1. `CustomSkinLibraryFoundationTest.symbolicLinkSkinIsRejectedWhenSupported` — Windows symlink capability unavailable.
2. `ClientCustomSkinCacheTest.symbolicLinkAtHashPathIsNeverAcceptedWhenSupported` — Windows symlink capability unavailable.
3. `KOMEPublicWaypointTransformerTest.originalProductionAndBuildJarsHaveExactCompatibleHooks` — configured original validation jars unavailable (`KOME_LOTR_VALIDATION_DIRECTORY` not supplied).
4. `KOMEWaypointTransformerTest.configuredProductionV3615JarHasVerifiedClassFingerprintAndTransforms` — configured original production-jar path unavailable.
5. `KOMEWaypointTransformerTest.configuredBuildDependencyV3615JarHasVerifiedClassFingerprintAndTransforms` — configured original build-jar path unavailable.

These skips are not counted as passes. The newly added hook tests read the actual classes on this checkout's test classpath; that is separate evidence from those original-jar fingerprint checks. The required local LOTR/Geckolib build dependencies were present. The unchanged Python routing validator was not executed because no Python executable was available; 171 affected documentation links were checked directly against local files: 0 missing targets.

Reobfuscated test artifact: `build/libs/KOME-LOTR-Addon-1.0.9-integration-g7.jar`. Existing g6/older artifacts were retained. Nothing was deployed into a Minecraft runtime.

See the combined report's testing section and local `build/knight-pass-*.log` evidence.
Early failed iterations were corrected; no failed iteration is presented as a passing
final result. Existing contract assertions were updated for the deliberately changed
survivor/authority/wrapper/tracker rules and stronger native-roster expectations.
The character-player-storage isolation rule was preserved: controller entity NBT access
was moved into the existing encounter marker boundary rather than exempting the bridge.

## 12. Required live tests

Use disposable worlds and matching artifacts. Exercise Prince orders, four soldiers
following during travel, no pre-arrival invaders, correct native faction composition,
safe approach activation, native HUD/progress/victory, every soldier casualty outcome,
player death and relog, owner logout with legitimate allied completion, restart before
and during battle, missing controller/actors, Prince death/replacement and old trial
saves. Verify no abandoned generated army remains after cancellation/reassignment.

Test a leather cap worn by the thief, combat weapon preservation, another-player/allied/
environmental carrier kills, single drop, item persistence, stale-revision rejection,
hand delivery and processing guards. Check large fortresses, terrain/water/cliffs,
nearby player builds and two players with nearby distinct objectives. Measure real
journey time and difficulty across several factions.

Verify native red body silhouettes through terrain, near/far targets, armor/held-item
appearance, effect cleanup, shaders/render mods and NVIDIA stability/FPS. Verify Elf
NPC/player eligibility, Tormented exclusion, daytime/indoor suppression, movement,
multiple Elves, chunk entry/exit, death/despawn, relog/dimensions, stronger lights,
both configuration toggles and the actual optional lighting engine. None of these
visual/playability claims was accepted through a live Minecraft session here.

## 13. Limitations and design questions

Large-settlement boundary geometry is approximate. No expensive walkability planner
or empirical route-time distribution was introduced. Safe native invasions may not be
available in every faction/location; the system explains inability rather than inventing
an army. Other mods can rewrite native invasion composition after initial validation.
Native Prince discovery may be slow in old explored worlds. English copy and the narrow
scrolling book layout still need live localization/visual review. Dynamic illumination
requires an optional engine and remains a client visual effect, not shared server
block light or a blue-light shader implementation. These limitations do not introduce
new Lord/Prince duties or alter the canonical rank ladder.
