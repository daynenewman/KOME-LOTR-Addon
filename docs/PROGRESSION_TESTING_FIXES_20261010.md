# Progression testing fixes — 2026-10-10

Started October 10; final verification/publication completed October 11, America/Chicago.

Branch: `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
Native target: LOTR Mod v36.15, Minecraft/Forge 1.7.10, Java 8 runtime.

This report separates source implementation, JVM regressions, scripted native Forge
execution, and controlled client screenshots. It does not certify a complete
multiplayer progression journey or physical escort pathfinding across every shelter.

## 1. Feature implementation status

| Requested feature | Status | Evidence and limits |
| --- | --- | --- |
| Protect unpublished work and integrate dev | Implemented | The 83-file previous pass was checkpointed at `a0705bf`; incoming dev is an ordinary merge. Backups and all three stashes are retained. |
| NPC authority names without repeated occupation | Implemented | One identity/rank formatter and a narrow native formatting adapter; native Forge name execution and multiple culture/epithet regressions. |
| Show Me center, maximum zoom, highlight and return targets | Implemented | Actual book mouse event to rendered LOTR map checked in an isolated client, including relationship-only navigation and preference restoration. Server target selection has separate regressions. |
| Destination shield marker | Implemented | Generic `Destination` label, existing native Bronze Adventurer's Shield front texture; traveler remains a separate moving marker. |
| Text-only escort tracker | Implemented | Empty numeric progress suppresses both fraction and HUD bar; escort icon remains. |
| Generic overhead gift pouch, colored physical reward | Implemented | Native ordinary two-pass pouch indicator; reward generation, faction color, contents, eligibility and once-only issuance remain unchanged. |
| Exact drink variant presentation and matching | Implemented | Native contents/item, vessel metadata, potency and relevant potion contents; legacy broad requirements remain broad. |
| Close, genuine traveler arrival | Implemented | Six horizontal blocks, two vertical blocks, actual server NPC position, exact usable saved point and native obstruction ray. Native scripted arrival checks; long-distance navigation remains unmeasured. |
| Suppress successful-escort dismissal message | Implemented | Scoped native message adapter around delivered follower release; gratitude and native cleanup retained. Ordinary dismissal and exception cleanup regressions. |
| Retire basic pouch/trade advancement gates | Implemented | Interaction/container/tick restrictions removed; old identifiers/NBT remain. Native alignment/hostility and other specialized permissions remain. |
| Starting hostile alignment | Implemented | Native enemy/mortal-enemy enumeration, additive -1 once at starting-faction finalization; existing completion/application markers prevent replay. |
| Avatar swing | Implemented | Native model swing field, six-tick preview clock, avatar-area left click only. Actual Elven preview screenshot and all supported model regressions. |
| Livestock mature-crop eating | Implemented | Explicit native cattle, sheep and pigs; mature edible crops only, bounded native-style AI and protection checks. Goats are not present as a supported native v36.15 livestock class and were not invented. |
| Agricultural worker deterrence | Implemented | Native `LOTRFarmhand` workers, including agricultural slaves, use short pursuit and nonlethal knockback only against active crop offenders. Native scripted behavior checks. |
| Elven foot appearance | Implemented as a native particle approximation | Synchronized Elven race, small bounded blue particles near feet; no block lighting or film-style illumination. Qualitative appearance is a limited controlled fixture observation. |
| Technical trial activation fairness | Implemented | Same trial retained for retry, daily reservation refunded, no failed-Liege penalty; real gameplay failure still has consequences. |
| Full verification and publication | Verification implemented; publication record follows | Full suite, native probes and reobfuscation pass. Git publication is recorded below. |

No new Lord/Prince progression design was introduced. Existing higher-rank trials
only receive the shared escort arrival, marker and successful-release corrections.
Known earlier audit boundaries, including unsupported Fangorn Liege hosting, remain.

## 2. NPC name formatting

The previous implementation wrote a KOME rank into native family information, then
LOTR's `getNPCFormattedName` appended the entity occupation again. This produced
`Gair, High Warlord of Dunland, the Dunlending Warlord`.

`KOMEProgressionNativeAuthority` now derives the display name from personal identity,
canonical NPC rank and the existing cultural title data. Rank records keep personal
identity in the compatible `Name` NBT field. Migration removes only exact known
KOME authority suffixes, including native-English/legacy-key variants. Commas,
epithets, unrelated titles and unrelated custom names are retained.

A common-side transformer adjusts the occupation argument only for a native noble
carrying a recognized KOME authority name. The native formatter and renderer still
own visibility, lettering and nameplate styling. Ordinary NPC names retain their
native occupation. Map labels use the same formatter; native NPC GUIs receive the
same command-sender name.

Current Prince-rank cultural examples:

| Faction | Display |
| --- | --- |
| Dunland | Gair, High Warlord of Dunland |
| Gondor | Gair, Lord of Gondor |
| Rohan | Gair, High Marshal of Rohan |
| High Elves | Gair, High Lord of High Elves |
| Durin's Folk | Gair, High Lord of Durin's Folk |
| Mordor | Gair, High Captain of Mordor |

`Gair, the Grey` remains that personal identity before its authority suffix.

## 3. Map behavior

Two real integration defects explained the earlier superficial focus success:

1. The conquest overlay restored its saved manual viewport on the first draw/tick
   of a new map, overwriting the progression center.
2. Marker reflection searched the focused subclass for private native fields and
   methods. Those belong to `LOTRGuiMap`, so the subclass could lose its markers.

The focused screen now receives its objective before native initialization and
reapplies the camera after `super.initGui`. Current and previous camera fields agree,
pan velocity is zero, zoom power/current/previous are 4, and the native scale is 16.
The conquest viewport save/restore path explicitly excludes this temporary view.
Marker reflection uses the native base class. Closing restores the previous native
zoom; opening the ordinary map restores its previous conquest viewport.

The marker packet carries a server-authored `objective` flag distinct from ordinary
actionability. Return/report targets use the current saved Master or Liege, including
delivered/deceased-recipient courier returns and recovered-keepsake returns. Optional
gift and unrelated ruler markers do not become objectives. Ruler deduplication keeps
the current relationship's objective flag. Moving actors use the latest published
identity/coordinates, and highlighting follows that identity without creating a marker.

The established yellow emphasis expires after ten monotonic seconds. The temporary
screen does not continuously force the camera while the player manually pans.

Controlled live capture verified the actual Show Me mouse event, power 4/scale 16,
an exact rendered center, visible marker/highlight, timeout, relationship-only return
focus and restored preferences. These are explicit UI fixtures; the server's real
assignment/relationship selection is covered independently, not simulated as a
completed multiplayer journey. Live verification also caught an initially misplaced
viewport guard; the strengthened regression now seeds real saved preferences.

## 4. Master drinks

Native `LOTRItemMug` encodes vessel and strength in item metadata, with each drink's
contents represented by its item identity. The existing saved requirement carries
item key/ID, metadata, vessel, strength, required/delivered counts and saved price.
Native display/language information identifies vessel material/type; native terms
are **Weak, Light, Moderate, Strong, Potent** (metadata 0–4). Nonbrewable drinks omit
potency. No invented vessel size or Roman-number strength scale is imposed.

Book progress uses a shared requirement name such as `Ale — Bottle, Strong: 0 / 3`.
Tracker descriptions use the same native contents/vessel/strength. Handover and
inventory projections share the native equivalent-drink matcher. Ordinary named
water bottles remain equivalent water; bottles carrying nonempty custom potion
effects are not silently treated as plain water. Cosmetic names do not restrict delivery.

Legacy requests without an exact vessel remain explicitly `any vessel or strength`;
saving does not narrow them. Existing quantities/deposits/prices are retained. New
Provision duties still choose 3–4 distinct types including an accessible ordinary-mug
water route. Stack limits, consumption, container conversions and native trade
availability were inspected; no new rare drink acquisition dependency was added.

## 5. Escort arrival and release

Arrival uses the existing proof's exact `ArrivalX/Y/Z`, not loose displacement from
the Liege, the player alone, or a nearby replacement point. Acceptance requires:

- Living, bound server NPC in the destination world, within **6 horizontal blocks**
  and **2 vertical blocks** of the saved usable arrival.
- Verified remaining shelter geometry and an unobstructed native collision ray to
  the arrival point. The saved point must have solid safe ground and clear body space.
- Owner nearby under the existing 16-block participation condition; dangerous escorts
  retain their required threat resolution and Lordship survivor rules.

The point is the previously verified clear approach/arrival location, so this does
not force an impossible doorway crossing or teleport the traveler. Invalid loaded
arrival geometry uses existing recovery/failure handling. Unloaded proof is retained
for later validation. The six-block choice is supported by native scripted geometry
checks, not a measurement of every generated building's navigation mesh.

The marker says `Destination` and samples the existing native shield's front face;
the traveler keeps its distinct entity marker. Escort HUD progress is empty, so its
fraction and bar are both absent.

Once real arrival is accepted, gratitude comes from that traveler, completion is
saved once, rewards retain their once-only reservation, and native follower ownership
is released. A scoped thread-local adapter suppresses only the native dismissal chat
during successful release. It restores the original teleport lease and preserves
native `dismissUnit` cleanup. Normal dismissal/desertion remains audible. Delivered
temporary Lordship charges are released rather than silently deleted; other cleanup
and surviving-guard reporting remain intact.

The overhead gift uses an ordinary native medium pouch with no faction-color NBT,
through LOTR's readable billboard and both texture/color passes. The actual dropped
pouch still gets native faction color and unchanged contents/eligibility.

## 6. Legacy permission changes

Retired exactly the old `baseline.npc_trade` and `baseline.pouches` restrictions:
prospective-Liege/general NPC interaction interception, pouch right-click denial,
trade/pouch/chest-with-pouch container denial, and recurring trade-container closure.
Compatibility permission reads now return native access for those two identifiers.
The registry calls them obsolete compatibility records and their descriptions explain
native behavior. Old stored advancement records and historical derived flags remain.

Native `canTradeWith`, alignment, hostility, prices and native inventory behavior are
not replaced. Combat hiring/population, political authority, gear, cooking, farming,
specialized crafting and quest-bound item rules remain enforced.

## 7. Starting alignment

`StartingFactionApplication.tryApply` reads the selected native faction's `ENEMY` and
`MORTAL_ENEMY` relation lists into an `EnumSet`, removes the selected faction and
applies `current alignment - 1` once per unique result through native player data.
Allies/neutral factions are absent. The selected alignment is not changed by this
penalty; the established pledge/minimum-alignment setup still runs normally.

This happens immediately before committing the existing authoritative
`StartingFactionApplied` marker during legitimate finalization. Completed characters
and already-applied starts exit first. Preview/Back/login/sync and ordinary recreation
do not apply it. A waypoint-finalization retry cannot replay it, and existing players
are not migrated into negative alignment. The existing native final player-data refresh
continues to synchronize the result.

## 8. Livestock and workers

The native rabbit AI is hard-typed to rabbits, so a bounded adapter reuses its movement,
proximity, eating-duration and consumption conventions. Exact native cattle, sheep
and pig classes receive one task; wolves, mounts, wild animals and arbitrary subclasses
are excluded. Wheat, carrots, potatoes, native lettuce/leeks/turnips/yams are edible
only at mature metadata 7. Pipeweed/flax, decorative plants and structure/container
blocks are excluded. Consumption removes the crop without item drops, with native
block-break feedback and eating sound; immature crops remain.

At most 32 nearby probes run after cooldown, navigation refreshes every ten ticks,
approach expires after 200 ticks, and eating requires 60 close ticks. Reset cooldown
is 600–1,199 ticks; deterrence gives the offender a 1,200-tick cooldown. No large
every-tick terrain scan or global offender registry was added.

Native `LOTRFarmhand` NPCs, including agricultural slaves, receive a movement task
alongside their existing rabbit defense. It selects only an active crop offender
within eight blocks and native sight, pursues at most 60 ticks/eight blocks from its
starting position, swings and knocks the animal away without damage or revenge
targets. It yields to ordinary combat and excludes unrelated hired warriors/progression
actors. Native farming resumes afterward. Existing rabbit combat is not replaced.

Both vanilla `mobGriefing` and native `LOTRMod.canGrief` must permit damage. Native
protective banners conservatively prohibit animal damage. No KOME protection bypass,
player authorization override, new texture or newly invented livestock class was added.

## 9. Elven visual effect and GL safety

`KOMEElvenFootGlow` uses native `LOTREntityBlueFlameFX` in **LOTR's** custom effect
renderer/texture sheet. It reads synchronized `PlayerRace.ELF` data, covering supported
Elven cultures/subtypes rather than faction membership. Completed visible, awake,
nonsneaking players within 24 blocks and native sight are eligible.

Full particles emit at most one per player per eight ticks; decreased particles use
16 ticks; minimal disables it. There is a global eight-particle/tick cap and 12-tick
life. Positions stay within 0.4 blocks of the feet with low blue opacity. Invisible
or sneaking owners immediately retire existing particles. Native depth/batching is
retained. No GL hooks, persistent world entities, collision changes, packets, spawning
rules, visibility calculations or block/dynamic light are added by this effect.

This is atmospheric sprite glow, not film-style lighting. Controlled captures and
native particle-count evidence are limited visual acceptance, not proof across all
skins, shaders, armor or GPUs.

The native pouch adapter needs small GL hooks to preserve current color/alpha across
native texture passes. Live testing found LWJGL 2's required 16-float query buffer;
using four threw inside the native renderer and leaked its matrix push. The buffer is
corrected, an actual LWJGL bounds regression is included, and the adapter now restores
entry modelview depth, matrix mode and scoped render state even on exceptions. Failures
are logged once instead of silently swallowed. The client probe rejects render errors.
No framebuffer/stencil/shader modification was introduced. This does **not** establish
the cause of the previously reported intermittent NVIDIA native crash.

Reviewed controlled client evidence: [centered destination](progression-testing-20261010/show-me-centered.png),
[expired highlight](progression-testing-20261010/show-me-highlight-expired.png),
[return to Liege](progression-testing-20261010/return-to-liege-centered.png),
[restored manual map](progression-testing-20261010/manual-map-restored.png),
[preview before](progression-testing-20261010/avatar-before-swing.png) and
[during swing](progression-testing-20261010/avatar-swing.png),
[ordinary overhead pouch](progression-testing-20261010/generic-pouch-front.png), and
[blue foot particles](progression-testing-20261010/elven-feet-and-generic-pouch.png).
The daylit foot effect is intentionally faint. These are actual Minecraft screenshots,
not generated mockups; fixture scope is recorded in the
[client evidence](progression-testing-20261010/client-verification.txt).

## 10. Compatibility and trial fairness

Incoming dev includes Join Battle IDs 53–58, strategic/formal-retreat recovery,
mounted-unit/reset/governance persistence, routing documentation, the T351/T352 route
correction and approved 45-cell mountain separation. All incoming history/resources
are retained. The merge resolved packet registration and overlapping isolation tests;
both progression escort leases and contributor reset deployment safeguards remain tested.

The previous unpublished identity packet collided with published ID 53. It moves to
**59**; all published IDs stay intact and registrations remain unique/sorted. Marker
ID 43 adds the objective flag and earlier Standing eligibility payload additions are
retained. The established matching-artifact policy is advanced consistently to
**`1.0.9-integration-g6`** in network identity, Forge metadata, Gradle version and
mcmod metadata. Older g5/1.0.8 clients are rejected; matching client/server builds are required.

Technical Standing activation means the NPC/item/hostile site could not become a
playable trial, including refused partial spawn issuance. Partial bound actors/items
are cleaned, the same token/story/destination and Liege are kept in `ASSIGNED` state,
and the previous daily reservation is restored. A five-second server-time retry gate
prevents repeated issuance spam. No failure record or fake completion/reward is added.
The pending marker persists across saves; successful activation consumes the current
day normally. Actor loss, destruction/loss after activation, nonparticipation and other
real gameplay failures still use the original daily limit and 20-minute failed-Liege
restriction. Dialogue distinguishes pending technical preparation from failure.

Save identifiers, legacy advancement NBT, player rank ladder, political hierarchy,
service deposits/prices, actor UUID/token bindings and contributor schema 12 are retained.

## 11. Verification

Final full command: `.\gradlew.bat --offline test build --console=plain`.

- **3,162 passed / 0 failed / 5 skipped**, 3,167 tests in 317 suites.
- **38 new focused regressions passed / 0 failed / 0 skipped**, across six suites,
  executed in the full run. The earlier focused/expanded runs were repeated as fixes
  changed; these final counts describe the final source.
- **BUILD SUCCESSFUL**, exit 0, 48 seconds; 18 actionable tasks, 3 executed and 15
  up-to-date. `reobfJar` executed successfully; normal JAR and sources tasks succeeded.
- **34 native dedicated Forge checks passed**, exit 0, including actual crop removal,
  worker deterrence, loaded valid/blocked arrival geometry, native name formatting,
  exact drinks and bootstrapped enemy relationships for four different factions.
- **16 controlled native client checks passed**, exit 0. Final render logs contain no
  GL errors or relationship-render failures. Screenshots were inspected; the map,
  preview swing, ordinary pouch and faint native blue foot particles are visible.
- `git diff --check`: passed. Network/transformer uniqueness, common/dedicated loading
  and contributor regression coverage passed without disabling tests.

Final suite subsets below are exact class-name filters and overlap; do not sum them:

| Area | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| Serf/Master and parting gift | 104 | 0 | 0 |
| Courier/Standing named suites | 18 | 0 | 0 |
| Knight commission named suites | 48 | 0 | 0 |
| Lordship named suites | 83 | 0 | 0 |
| Character module namespace | 152 | 0 | 2 |
| Join Battle/conflict/reset/movement/strategic/mountain/governance/emergency/muster | 409 | 0 | 0 |

The five existing skips are two platform symbolic-link support/permission assumptions
in skin-library/cache tests and three optional configured/original production-JAR
fingerprint inputs in waypoint transformer tests. Available dependency-bytecode checks
and native Forge/client execution ran. No test was removed or newly skipped.

Artifact: `build/libs/KOME-LOTR-Addon-1.0.9-integration-g6.jar`.
SHA-256: `252c34e4fcf0c74c8b64c090ed9920702ee73fe780b1af1d66e27436151f4507`.
Java 8 runtime was exercised by the native launchers. Logs/counts and the complete
review diff remain under ignored `outputs/progression-testing-20261010/`.

Integration warnings: existing Gradle 17-runner/legacy token deprecations and ordinary
deprecated/unchecked Java APIs; optional external JAR/symlink test conditions; native
Forge/LOTR update/player-details services produced nonfatal unavailable-network or
malformed-update-response warnings in disposable runs. One earlier client shutdown
showed the legacy duplicate-save-thread exception; the final client exited successfully.
The Python routing checker was unavailable in this environment; affected guides and
their local report/evidence links were reviewed, and the full Java/build checks passed.

Baseline checkpoint: cached full results **2,848 passed / 0 failed / 5 skipped**, 2,853
total; offline `test build` successful including reobfuscation before checkpoint.
Toolchain: cached Gradle 8.5, Java 17 Gradle runner, Java 8 compiler/runtime; dependencies
remain offline/cached. No tests were disabled. Revised expectations concern the requested
technical-failure behavior, delivered traveler survival, protocol/transformer inventory,
and a fixture standing above real ground. Native metadata was added to the headless
world so the actual Minecraft obstruction ray executes instead of a mocked success.

Earlier failures were fixed rather than hidden: incomplete generated Minecraft
classes were regenerated, the native chunk index was used for the AI fixture,
camera preference restoration was reproduced live, and the GL query buffer defect
was reproduced and corrected. Existing conditional skips remain independently reported.

Native probes use fresh disposable directories under ignored
`outputs/progression-testing-20261010/`. No existing game configuration, world, runtime
JAR or stash was replaced. Captured UI markers/race scene are explicitly test fixtures;
NPC movement across a complete escort journey, multiplayer timing, actual dismissal
speech on a connected server and long-term farm/ecology balance remain acceptance work.

## 12. Git publication

- Feature branch: `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
- Initial HEAD: `69dbad5a8d2d1cef461e8327b961ce9194d8241f`.
- Protected unpublished checkpoint: `a0705bf` (83 source/test/documentation files).
- Integrated dev baseline: `20e27ed` (refreshed again before publication).
- Backup references: `backup/progression-testing-20261010-start` and
  `backup/progression-testing-20261010-checkpoint`; older backup references retained.
- Stash objects unchanged: `61f81972fe98afe0497ddd376d571ebb019883ad`,
  `1f7c28e4ab11eb929b64d247de5800048fc9b34a`,
  `7d9c99bc5cfa9d5bd96dbe81fe4203449d007221`.
- Verified feature/dev code commit, push results, final branch and working-tree state:
  pending final publication record. No force-push, rebase, branch deletion or stash
  mutation is permitted or used.
