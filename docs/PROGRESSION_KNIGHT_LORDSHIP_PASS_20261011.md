# Knight / Lordship implementation pass — 2026-10-11

Branch: `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
The initial working tree was clean. Work remains uncommitted on that branch; no
push, merge, stash manipulation, world replacement or runtime replacement was
performed. The supplemental request was incorporated into the same implementation.

**Evidence:** source inspection, actual v36.15 class/bytecode inspection and
automated tests. Minecraft was not launched. Visual appearance, real invasion
playability, real dynamic-light propagation and multiplayer acceptance remain
unverified. “Implemented” below means code is wired; it does not mean live acceptance.

| Original request | Status | Result / evidence limit |
|---|---|---|
| 1. Direct superior reports | Implemented | Valid report clicks use existing authority/handover services and consume the click once. |
| 2. Binary tracker objectives | Implemented | Stage/type-specific counters; conversation/travel/recovery/report objectives omit bars and fractions. |
| 3. Provision icon | Implemented | Bread on HUD, map and Master head; profession material metadata is retained. |
| 4. Real lost-item identity | Implemented | Native item plus assignment NBT; compatible wrapper migration and processing/use guards. |
| 5. Stolen-item carrier | Implemented | One persisted carrier; armor in its native slot, other property in the native idle hand. |
| 6. Through-wall red silhouette | Partially implemented | Occluded native-model hull implemented; GPU appearance/compatibility not live verified. |
| 7. Shared objective highlighting | Implemented | Personal mandatory actor identities, including native trial invaders; incidental enemies excluded. |
| 8. Rendering safety | Partially implemented | Bounded range, state restoration and no framebuffer/full-world render; no live profiling. |
| 9. Knight travel | Implemented | Wider preferred journeys, bounded route-quality estimate and real shorter fallbacks. Actual walking times unmeasured. |
| 10. 5–10 Knight enemies | Implemented | Validated native weighted troop entries; heavy/siege exclusions and limited elite mix. |
| 11. Defense activation fairness | Implemented | Arrival preparation, rollback/retry and explicit technical reassignment. Live failure reproduction remains outstanding. |
| 12. Settlement perimeter | Partially implemented | 24–40 block safe-ground search around verified shelter anchor; not a full native fortress-boundary survey. |
| 13. Stable settlement marker | Implemented | Saved assignment spawn-area anchor; no moving resident/enemy or vanilla spawner block. |
| 14. Prince authority / migration | Implemented | New Prince-only trial issuance and Prince-only Lord conferral; explicit old-trial continuation. |
| 15. Native invasion | Implemented | Actual LOTR invasion entity/lifecycle; no generic cluster advertised as an invasion. Live encounter remains unverified. |
| 16. Player survival / no guard quota | Implemented | Recorded player death fails an accepted active attempt; real invasion victory and final report required. |
| 17. Elf racial eligibility | Implemented | Native Elf hierarchy and synchronized player Elf race; Tormented Elf excluded. |
| 18. Night restriction | Implemented | World celestial time and sky-bearing dimension; indoor darkness does not enable it. |
| 19. Faint actual illumination | Partially implemented | Optional compatible Dynamic Lights adapter; declared addon dependencies alone provide cosmetic particles only. |
| 20. Elf visual verification | Partially implemented | Eligibility/config/lifecycle tests; no Minecraft visual/FPS verification. |
| 21–23. Knight book audit, structure, context | Implemented | Current service first, real standing requirements, relationship and compact credited history. |
| 24. Focused book audit | Implemented | [Audit](KNIGHT_PROGRESSION_BOOK_AUDIT_20261011.txt). |
| 25. Preserve gameplay | Partially implemented | Broad automated regression coverage; live save/world/multiplayer regression acceptance remains required. |
| 26. Focused automated tests | Implemented | 31 new tests plus updated/strengthened existing contracts. |
| 27. Full verification | Implemented | Focused/full test, build, reobfuscation and whitespace results are recorded below; live acceptance remains outstanding. |

## A. NPC interactions and tracker

`KOMEProgressionNpcInteractionService` and the early `KOMEEvents` interaction
path recognize ready Knight reports and completed Lordship reports before normal
NPC GUI dispatch. The existing commission/report and promotion services recheck
rank, pledge, faction, authority identity and physical interaction distance.
Stolen property must actually be held and bound before a direct handover can
occur. Reports clear/archive the assignment and retain token/history evidence;
repeated clicks or native offer responses cannot grant the same deed/promotion
again. The report click does not call `prepare` to issue another commission.
Nonreport active/offered commissions keep normal interaction behavior. Serf
direct goods/courier/Standing handovers, speech, rewards, sounds and former-Master
gift/acknowledgment paths remain in their existing services.

The native Quest packet now dispatches to an eligible Prince as well as an exact
current Liege. New trial issuance remains an explicit Quest action. A Lord can
acknowledge readiness and give cultural discovery guidance, but cannot confer Lord
standing. Disabled reasons distinguish unfinished service, current commissions,
alignment, faction, authority identity and rank. Safe geography that cannot be
arranged is explained truthfully by the issuing NPC.

`KOMEProgressionTrackerSnapshot` derives quantitative presentation from actual
combat/goods stages. Travel, item retrieval, escort arrival and reports are binary.
Actual relief amounts, defeated enemies, distinct-service totals and alignment
thresholds retain quantities. The native overlay already gates its bar on numeric
progress; no global string replacement is used to suppress valid counters.

Provision uses `Items.bread` even when a different requested food/drink appears
in the detailed goods list. Master provision and relief map/overhead markers use
the same bread asset. Profession uses its requested native material stack and
metadata where available, with the existing material fallback. Native indicator
priority is retained. Marker role/item payload changes require matching
`1.0.9-integration-g7` client/server artifacts; packet registration IDs are preserved.

## B. Lost item and enemy visibility

The Knight bug was a **sealed-item wrapper**: `KOMEItemStolenProperty` had a paper
base texture, partial delegated icon/name behavior and was not an actual armor
item. New property is the native `ItemStack` named in the saved goods record,
including its damage metadata. A leather cap is now the native leather helmet.
The private `KOMEStolenProperty` compound retains owner, token, contents, damage
and revision binding. Ordinary renamed/unbound items do not satisfy it.

Existing valid wrappers remain accepted as migration input and are converted in
owning inventory or loaded item entities without adding another copy. The item
registration is retained so old saves can load. Stale inventory/world copies are
reconciled against the assignment and revision. Recovery replacements invalidate
old revisions and retain the existing finite allowance. Bound property cannot be
used/equipped by players or converted through vanilla/LOTR crafting, smelting,
anvils or native NPC selling. These narrow guards leave ordinary items alone.
Serf recovery equipment uses the same processing/use guards. Third-party conversion systems outside those native entry points need their own
compatibility review.

One attacker UUID is saved as carrier. Native armor uses slot `4 - armorType`,
so a cap is worn while the thief keeps its normal weapon. Other valuables use
native idle-item equipment; attack AI retains its combat weapon and may switch
to it during combat. No bespoke item renderer or offhand inventory was invented.
No shield-specific alternative is generated by the current curated property pool.

An uncancelled carrier death creates one bound, non-despawning item, independent
of killer type. The persisted drop reservation and dead actor slot prevent death
callback replay from dropping another copy. Temporary-force/ordinary commission equipment drops are
cleared through the existing encounter drop policy. Genuine native invasion enemies retain their native loot, and hostile actors cannot receive friendly guard commands. Missing carrier recovery
rebinds the same actor slot; obsolete entities/items reconcile on load. Technical
encounter rollback invalidates property revisions. The Serf recovery assignment
contains no hostile thief; it remains a real lost object and no enemy is fabricated.

The shared red effect remains an inverted hull of the **native main NPC model**.
An occluded `GL_GREATER` pass runs before the normal model writes its depth; the
normal visible `GL_LEQUAL` rim follows the model. Both passes disable depth writes.
Color, blend, depth, lighting, texture units, matrix mode and matrix/attribute
state are restored. There is no framebuffer allocation, bounding-box substitute,
recursive entity renderer, full-world entity rerender or forced chunk load.
The effect is bounded to 96 blocks and retains native frustum/model visibility.
Armor and held-item renderers remain native; the added hull outlines the body,
not every independent equipment layer. Shader/armor readability needs live testing.

Personal server marker snapshots contain only live actors for the player's active
mandatory objective. Standing defense, ordinary Knight forces and bound native
trial invaders share this system. Incidental Standing escort ambushes, unrelated
invasions, wild hostiles, civilians and other players' assignments are excluded.

## C. Knight combat and geography

The comparable Serf courier profile is 500–1,500 horizontal world blocks, sampled
within native faction territory. Serf recovery is 112–176 blocks; Standing escort
uses actual observed shelters from 256 up to 3,000 blocks. Provision/profession
duties have no generated journey, so their gathering time is not a coordinate
distribution. Previously all Knight profiles used 500–1,500 blocks too.

Knight journeys now prefer 1,000–3,000 blocks, with approximately 2,000 favored
for recorded shelters. This targets roughly twice a typical comparable courier
journey without changing every Serf service. Six biome samples estimate water
and mountain burden. Recent destination penalties encourage variety. Native
waypoint coordinates, verified shelter records and actual hostile territorial
boundaries remain the location sources. Searches are bounded; there is no world
pathfinding or speculative settlement generation. If broader territory has no
valid site, 500–999 and finally 64–499 block safe local fallbacks preserve a real
assignment. Otherwise another commission type is tried. Straight-line distance
and biome burden are estimates, not a promise about actual walking time.

Ordinary combat generation selects 5–10 validated entries using native invasion
troop weights. Faction, hostility, regional presence, adulthood, hiring/mount/boss
restrictions and safe terrain are rechecked. Siege/destructive giants and related
unsafe classes are excluded from these ordinary groups; elite/berserker mixes are
limited where ordinary alternatives exist. No health multiplier or new mob class
was added. Incidental escort ambush generation was not changed to this quota.

Defense already deferred spawning until approach; inspection did not support a
claim that all forces originally spawned at commission acceptance. The concrete
defects found were partial spawn transactions becoming FAILED, actors targeting
a protected generated resident before meaningful participation, and missing/dying
entity evidence becoming player failure. Live symptom attribution remains a
reproduction task. The corrected flow saves destination/composition first,
travels without hostiles, verifies already-loaded terrain near arrival, and marks
`encounterCreated` only after initialization succeeds. Partial actors roll back.
Defense starts within 40 blocks of the objective and initially targets the owner;
attackers use natural open ground 24–40 blocks from the saved shelter approach.
Other field forces retain their safe 48–64 block staging search.

Missing chunks are not death evidence. Enemy recovery remains finite and uses
the saved slot/class/faction/token. Exhausted technical recovery permits retry or
reassignment rather than a permanent blocker. Pre-participation resident loss or
a force cleared before owner participation returns defense to technical preparation;
real protected-objective loss after engagement remains gameplay failure. No free
success is granted for a missing or uninitialized force. Reassignment is server
rate-limited and saved: after preparation delay, no faster than once per 1,200
world ticks. Credited service/alignment and unrelated Serf penalties are preserved.

The settlement marker uses the saved commission spawn-area/arrival anchor.
Resident markers are suppressed for defense, so it never follows an individual
resident or enemy. The anchor persists through reload and disappears with the
assignment. This is an existing KOME stable spawn-location record, not a permanent
vanilla spawner. The safety search recognizes local roofs/support, natural ground,
collision and tile entities; full native settlement extents are not exposed by this
record, so large fortified-perimeter placement still needs live acceptance.

## D. Lordship authority and invasion

The requirements remain three distinct **credited, faction-attributed** Knight
commissions and 2,000 **current** faction alignment, with same-faction Prince
authority. Administrative/unknown-provenance history does not create qualification.
The current Lord relationship persists while a separate Prince trial authority is
saved. Final Prince conferral transitions the relationship to that Prince without
erasing completed commission evidence. King remains a separate political office.

The Prince gives orders naming the actual enemy faction and saved destination,
using the existing cultural authority definitions and speech mechanism. Four
temporary native allied soldiers are entrusted at acceptance and follow native
commands during travel. No invading force exists before staging. At 40-block
approach, terrain/chunks, native regional hostility, composition, mob-spawn rules,
banner/spawn protection and the native controller's eligibility are verified.

`KOMEObjectiveInvasionBridge` creates an actual `LOTREntityInvasionSpawner`, sets
the validated `LOTRInvasions` type, selects native defending bonus context and
calls native `startInvasion(player, 30)`. Thirty is the lower end of the native
30–70 default, not an ordinary 5–10 commission quota. The native controller owns
weighted composition, spawn pacing, its live cap, kill credit, progress reversion,
watchers, bonuses and victory. Native spawned NPCs are associated by exact invasion
UUID before entering the world. Unrelated native invaders are never adopted.
Unsafe compositions or unavailable mechanics produce preparation/reassignment,
never an invented invasion or arbitrary generated block damage.

The narrow native outcome hook observes `endInvasion(boolean)`. Controller removal,
missing NPCs, soldier deaths and timers are not victory. Native victory requires
recorded meaningful owner participation and no recorded player death; an online
owner must still be alive at the field. A previously participating offline owner's
actual native victory is retained for their later personal report. Logout does not
clear a recorded death. All accompanying guards can die without failing the trial.
Final speech distinguishes no recorded guard losses, some losses and a costly
victory with every soldier lost. No soldier return/survivor quota blocks promotion.

Technical activation/continuation failure retains a retryable preparation state.
Partially entrusted forces are cleaned up; missing native controllers receive a
loaded-evidence grace period and technical recovery, not a false victory. Guards
are retained during field preparation retries and released/cleaned on reassignment
or ending. Prince death/rank loss removes that authority binding so an eligible
replacement Prince can receive the preserved evidence. A new native attempt does
not depend on keeping the former Lord alive.

**Version-1 save migration:** existing active, offered and completed generic trials
keep their original objective, token, actor identities, destination, stage and service
evidence. They are labelled saved trials and continue their actual original field
objective; they are never retroactively described as native invasions. The saved
survivor quota is ignored and becomes zero. Final Lord promotion requires a Prince,
including for a completed old trial. Unbound legacy Prince authority can be selected
at reporting. Existing Lords are not demoted. Promotion/report history remains once-only.

## E. Elven illumination

Native NPC eligibility uses `LOTREntityElf`, excluding `LOTREntityTormentedElf`.
Players use completed synchronized character race `ELF`, not allegiance or texture.
Death, invisibility, sneaking and player sleeping suppress the effect. Night uses
the dimension's celestial angle, not per-block darkness; skyless dimensions do not
receive a daylight-independent aura. Native blue foot particles fade around dusk/dawn.

Actual illumination is **conditional**, separate from particles. The optional
adapter calls the established AtomicStryker 1.7.10 Dynamic Lights registration API
([primary implementation](https://raw.githubusercontent.com/AtomicStryker/atomicstrykers-minecraft-mods/1.7.10/DynamicLights/src/main/java/atomicstryker/dynamiclights/client/DynamicLights.java)).
It uses an unspawned, unsaved foot-position attachment, light level 3 and at most
16 nearby sources. The engine supplies real client block-light propagation, roughly
two neighboring blocks in dark conditions, and retains stronger existing light.
Vanilla light is monochrome; the appearance sprite is blue. No KOME block placement,
terrain replacement, server light authority, light packets or forced chunk loads
were introduced. The engine is not bundled/declared here; without a compatible
client installation, illumination is unavailable and the effect is cosmetic only.

Sources register once, move with the feet and unregister on ineligibility, departure,
world change, disconnect or daylight. API failure disables the optional adapter
and marks its attachments dead. Mock API tests verify adapter lifecycle, not real
engine propagation or render-mod compatibility. Client configuration in the existing
KOME client file adds `elves.visualAura`, `elves.dynamicIllumination`,
`elves.visualIntensity` (0–0.4, default 0.28) and `elves.particleInterval`
(8–40 ticks, default 8; reduced particles halve density). Defaults enable the visual
effect and compatible optional integration. The existing particle budget is eight
emissions per client tick and the scan is limited to naturally loaded nearby entities.

## F. Knight book UX

The native book now puts only current service first, followed by real standing
requirements, current Lord/Prince relationship and compact credited-type history.
Travel, battle, delivery/recovery, report and technical preparation have distinct
copy/navigation. Inactive trial descriptions appear only as qualification guidance,
not current work. Binary book requirements omit redundant quotas. Full details and
remaining usability issues are in [the focused audit](KNIGHT_PROGRESSION_BOOK_AUDIT_20261011.txt).

## G. Files changed

The exact source/resource/test/documentation list is recorded in
[the change manifest](PROGRESSION_KNIGHT_LORDSHIP_FILES_20261011.txt). Principal new
implementation files are `KOMEObjectiveInvasionBridge`, its outcome transformer,
`KOMEQuestPropertyUseGuard` and transformer, `KOMEKnightBookPresentation`, and
`KOMEElvenDynamicLights`. Existing progression/services/records, native marker
projection/packet/rendering, NPC events, book/HUD, client config and English language
resources were extended. Protocol/build version changes identify the required matching
artifact. Test-only Dynamic Lights contract classes are not included in production jars.

## H. Testing

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

Logs/evidence are in `build/knight-pass-Focused.log`,
`build/knight-pass-Full.log`, `build/knight-pass-focused-results.json` and
`build/test-results/test`. Early iterations exposed real regressions in native
targeting and former-Master acknowledgment, which were corrected. Obsolete wrapper,
0/1, soldier-quota and Lord-authority assertions were updated to the requested
contracts while retaining replay/provenance/death/reload checks. No tests were disabled.

No live Minecraft client, native invasion battle, dedicated-server gameplay session,
multiplayer pair, shader/OptiFine combination or NVIDIA profiling was run. Required
live checks include every Serf/Knight/trial state, all casualty outcomes, player death
and relog, real invasion victory, carrier equipment/drop/recovery, settlement arrival
and perimeter, chunk unloading/server restart, authority replacement, old world saves,
map/book layout, through-wall body hull and Elf light appearance/cleanup with the
actual optional lighting engine. Use disposable worlds and matching g7 artifacts.

## I. Remaining risks

- Native invasion spawn/kill behavior and other mods' spawn rewrites can differ from
  inert tests. Recheck actual faction rosters, mounted/formation additions, progress
  reversion and environmental deaths in a disposable world.
- Shelter proof is local structural evidence, not a whole-fortress footprint or
  navigation proof. Route samples estimate terrain; actual walking/pathfinding and
  attacker perimeter/reaction time require live tuning.
- Save reconciliation deliberately waits for loaded evidence. No atomic transaction
  spans all native entity chunk saves and player/world records; abrupt termination
  during activation must be exercised. Corrupt/removed native item definitions and
  third-party converters need additional compatibility review.
- Old trials continue real old objectives and require a Prince for final reporting.
  Native Prince scarcity in explored old saves may make discovery slow; no Prince
  is force-spawned. Last-known locations can be stale while unloaded.
- OpenGL restoration is source/test evidence, not NVIDIA/shader certification.
  The added hull covers the native body, not every equipment renderer.
- Dynamic illumination depends on a compatible optional client engine and its light
  update implementation. This checkout alone does not produce real nearby light.
  Real propagation, multiple-Elf performance and render-mod interactions are unverified.
- Multiplayer ownership is server-bound and packet replay is tested, but live latency,
  simultaneous nearby encounters, disconnected owners and allied/native final blows
  still require connected-client acceptance.
