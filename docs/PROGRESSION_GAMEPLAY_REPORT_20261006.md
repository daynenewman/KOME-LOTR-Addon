# Progression gameplay, immersion and reliability report — started 2026-10-06, completed 2026-10-07

The implementation is ready for code review and further Minecraft testing. It is
**not a claim of complete live validation**. No Minecraft client/server was launched.
No commit, push or merge was performed. Pre-existing uncommitted UX/hierarchy work
was preserved; the file inventory below distinguishes this pass from that work.

The canonical ladder remains Wanderer → Serf → Knight → Lord → Prince. King remains
a political office. Promotion quotas, Lord → Prince, Prince, player accession,
War Service, conquest ownership, ordinary miniquests and general hiring remain as
they were at the start of this pass.

## 1. Implementation summary by request

“Implemented” describes code behavior and automated coverage, not a live-game result.
“Partially implemented” identifies a material limitation or outstanding live-only
verification. Existing working behavior was retained where no replacement was needed.

| # | Request | Status | Result / limitation |
| --- | --- | --- | --- |
| 1 | Creation interruption/resumption | Implemented | Every mandatory screen opens vanilla pause on ESC. Latest server screen waits through pause/options. Confirmed NBT choices derive the exact next stage after login. No premature completion or packet authorization change. |
| 2 | Gender label | Implemented | Visible labels changed; SEX IDs, packets and saved fields retained. |
| 3 | Selection chat | Implemented | Removed race/gender/appearance success announcements; rejection feedback retained. |
| 4 | Faction rank prefix | Implemented | Own-faction rank line now reads Rank: followed by synchronized canonical faction title. Existing rank-title integration setting is honored. |
| 5 | Technical progression messages | Implemented | Automatic-step completion chat removed. |
| 6 | Tracker responsibilities | Implemented | Active commissions project current action and stage-local counts. Context and survivor instructions remain in the book. Existing higher-rank displays retained. |
| 7 | Courier copy cooldown | Implemented | Book section receives replacement timing; HUD shows message delivery 0/1. Replacement limits/cadence retained. |
| 8 | Binary escort | Implemented | Active escort reads Escort the traveler to safety, 0/1; only physical arrival with the charge can finish it. |
| 9 | Multiple commission/trial objectives | Implemented | Orders → travel → actual attacking-force deaths → recovery/escort/supplies → report, derived from saved encounter facts. No added miniquest acceptance, quotas or invented intermediate credit. |
| 10 | Native NPC name styling | Partially implemented | KOME custom name-tag plates removed in favor of native family-name synchronization. No custom renderer added. Native NPC names follow native visibility conventions; continuous always-visible overhead names are not forced. Scaling/visibility still need live review. |
| 11 | Ranked name format | Implemented | Personal name, faction-specific rank of actual faction. Authority record retains personal identity independently of display title. No invented territory. |
| 12 | Master/Liege map labels | Implemented | Master [saved name]; Liege title from saved authority record, with saved identity fallback. |
| 13 | Own-faction ruler only | Implemented | Runtime marker publication filters by actual pledge and canonical ruler record. Uninitialized ruler uses title only; initialized identity uses personal name/title/faction. Singleton/respawn retained. |
| 14 | Interaction indicators | Implemented | Prospective eligible Lieges regain an action icon through native icon rendering. Crown and relationship medallions retain priority; native quest indicator suppression stays player-specific. |
| 15 | Concise map tooltips | Implemented | Redundant actor/location descriptions removed; identity or objective title remains. Empty subtitle produces a single-line native-style hover. |
| 16 | Moving escort markers | Implemented | Standing escorts refresh exact bound UUID references. Commission actor positions/names persist. No moving marker after its active role ends. |
| 17 | Show Me | Implemented | Native map/camera and ten-second highlight retained; moving charge targeting uses updated marker positions. Static destinations stay separate. |
| 18 | Optional gift | Implemented | Existing gift-independent promotion gates preserved and tested. Collection is never a rank requirement. |
| 19 | Interaction-issued gift | Implemented | Ticking/conferral do not issue gifts. Explicit former/current Master interaction uses saved pending contents and the existing locked one-drop transaction; failed spawn leaves availability intact. |
| 20 | Advancement farewell | Implemented | Eligible Master's dialogue uses faction-specific native authority guidance and truthful structure categories. |
| 21 | Small quest narratives | Partially implemented | Master provisions/courier and commissions use curated dialogue with actual names, faction/enemy/item facts and verified/generic objectives. Every cultural faction does not yet have a distinct prose set; shared appropriate templates remain. |
| 22 | World-accurate descriptions | Implemented | Structure-dependent new assignments resolve physical evidence before narrative. Legacy unverified names are suppressed in new presentation. Native waypoint/region data is retained for ordinary geographic objectives without asserting a building exists. |
| 23 | Real escort destination | Partially implemented | New escorts require verified existing shelter evidence and actual arrival. No arbitrary coordinate is called a refuge. Complete path reachability cannot be proven by the available native navigator. |
| 24 | Existing refuge search | Partially implemented | Bounded saved-observation registry, loaded native NPC home/location witnesses and waypoint-site geometry. Native village prediction caches are not treated as existing buildings. No exhaustive unloaded-world structure search. |
| 25 | Generate faction refuge | Not implemented | Generation is disabled because no authoritative construction-safe native placement transaction was found. No forced template placement or new terrain blending. |
| 26 | Protect player constructions | Partially implemented | New destination code writes no blocks. Unsafe/unverified sites are rejected; attacker placement avoids manufactured surfaces/containers. An ownership-guaranteed automatic generation path is unavailable. |
| 27 | Refuge persistence/cleanup | Partially implemented | Observed shelter proof and objective proof persist; shelters are never deleted by cleanup. No generated-refuge template/status records because generation is disabled. |
| 28 | Appropriate escort behavior | Partially implemented | Native follower/civilian behavior reused; civilian attack targets cleared and soldier pursuit restrained near owner. No dedicated role-specific messenger/refugee behavior framework added. Native fleeing/pathfinding requires live review. |
| 29 | No escort teleport | Implemented | Saved native teleport lease retained; local recovery never teleports. Escort guidance does not change trial guards' prior teleport policy. |
| 30 | Escort recovery tools | Implemented | Local interaction follows/recalculates; sneak interaction waits/resumes using native halt/ready and navigator. Exact ownership/range/active charge validation. |
| 31 | Valuable faction lost items | Implemented | New selections use curated faction gift-equipment pools plus native silver, one item. Standing trials save the real native stack; Knight sealed property represents it with its native icon/name. Legacy gold/silver selections retained. |
| 32 | Lost-item persistence | Implemented | Active bound ground items retain maximum lifespan/age protection, including death drops and reload; stale items reconcile. Destruction cannot silently complete a quest. |
| 33 | Direct item handover | Implemented | Correct held item and exact Liege/assignment required; consume once, play existing item sound. Standing trial no longer completes from retrieved flag alone. |
| 34 | Regional bandit pressure | Implemented | Native bandit event-roll probability ×2 while active bound item carried, no global tables changed. Boost disabled in shared local player areas and at local bandit threshold; disabled progression leaves native probability unchanged. |
| 35 | Attacker distance | Implemented | Bounded initial search around 48–56 blocks; accepted positions 40–64 from center and ≥40 from saved Liege. Loaded natural surfaces, body space, no containers/manufactured footprint. |
| 36 | Protected actor restraint | Implemented | Reversible native home lease, local defense allowed, distant targets released and navigation toward role anchor. No invulnerability added. |
| 37 | Real border incursions | Partially implemented | Native defending territory plus hostile territory within 192 blocks; chosen neighbor must also be an eligible regional invasion force. Bounded 500–1,500 search declines unsupported geography rather than invent a border. No exhaustive frontier resolver. |
| 38 | Robust defense | Implemented | Existing token/UUID binding, finite quota, participation, non-player final blows, three-per-slot recovery, stale cleanup and distant outlines preserved. |
| 39 | Recovery audit | Partially implemented | Existing death/pledge/abandon/reload paths retained; missing standing actors, invalid shelter and real item loss gain recoverable failure. Arbitrary path obstructions and every third-party protection/entity mod still need live testing. |
| 40 | Failure consequences | Implemented | Existing failed-trial/Liege retry/cadence paths used. Knight property issuance limited to three site attempts; revisions invalidate earlier copies. No new punitive currency/system. |
| 41 | Next action after failure | Implemented | Failed commissions return to Liege; failed standing trials use existing replacement/leave-Liege path. No item-loss auto-success; book/tracker/NPC explain returning for service. |
| 42 | Secondary rewards | Implemented | Small physical coins or faction food, persisted reservation before issuance; no alignment/quotas changed. See reward table. |
| 43 | Persistent consequences | Implemented | Existing shelters remain, actual property consumed on return, service history/dialogue retained, ended objectives disappear. |
| 44 | Lifecycle/clutter | Implemented | No new generated buildings; shelter registry capped at 256. Temporary actors use existing cleanup. New escort completion does not manufacture a destination host. |
| 45 | Visual meanings | Implemented | Existing medallions/crown/native item icons/action renderer/outlines reused. |
| 46 | Single primary indicator | Implemented | Existing crown → relationship → progression action → native quest priority retained. Relationships stay visible independent of availability. |
| 47 | Natural language | Partially implemented | Touched progression text cleaned and packaged English fallback prevents exposed KOME keys. Untouched native/third-party text and every language translation were not comprehensively rewritten. |
| 48 | Server authority | Implemented | Existing server decisions preserved; new item/destination/spawn/reward/role checks run server-side. Client continuation/map changes authorize no gameplay. |
| 49 | Persistence/idempotency | Partially implemented | NBT/UUID/revision/reservation, repeated interactions, reload and stale actors/items covered automatically. No crash-atomic guarantee across Minecraft's separate save files or exhaustive live multiplayer restart certification. |
| 50 | Legacy compatibility/performance | Implemented | Java 8/Forge 1.7.10 build, bounded loaded-only operations, additive save fields, no global spawn-table edits or forced objective chunks. No live TPS benchmark. |
| 51 | Preserve gameplay | Implemented | Ladder/hierarchy/quotas and protected unrelated systems preserved; regression suite passes. |
| 52 | Character tests | Partially implemented | Real GUI key dispatch for all five stages and deferred pause/options tested; confirmed selection persistence/authorization tested. Actual integrated pause/disconnect still live-only. |
| 53 | Tracker/quest tests | Implemented | Sequential counts, reload, copy timer separation, direct Quest route and existing service/report suites pass. |
| 54 | NPC presentation tests | Partially implemented | Identity/ruler filter/priority/native renderer tests pass; font/depth appearance needs live Minecraft. |
| 55 | Gift tests | Implemented | Optional promotion, no tick/conferral issuance, explicit claim, failed spawn, reload and duplicate claim tests pass. |
| 56 | Geography tests | Partially implemented | Empty terrain rejected, physical roof/wall verified, registry reload/unloaded evidence, invalidation and safe actor terrain tested. Generated appearance/terrain safety unavailable because no generation occurs. |
| 57 | Escort tests | Partially implemented | UUID-moving/static markers, real arrival, no teleport, guard policy and cleanup covered. Fence/road/terrain navigation remains live-only. |
| 58 | Lost-item tests | Partially implemented | Pool variety, tokens, handover, protection, stale items/recovery and local native probability tests pass. Actual bandit encounter frequency needs live measurement. |
| 59 | Combat tests | Partially implemented | Distance/site safety, protected home restoration, finite recovery/non-player kills and native regional/border inputs tested. Live Liege survival/frontier locations remain untested. |
| 60 | Persistence/compatibility matrix | Partially implemented | Automated save, packet, pledge, death, unload, legacy and dedicated loading coverage passes. No actual fresh/existing world, dedicated server restart or multiplayer session launched. |
| 61 | Full regression | Implemented | Final focused/full suites, normal Gradle build and normal git diff --check run; exact counts below. |

## 2. Native LOTR/KOME integration

Reused native `LOTRSpeech.sendSpeech`, `LOTRFamilyInfo` synchronization, NPC faction
and personal names, faction-specific KOME authority definitions, native map camera
and marker drawing, the existing cloned native miniquest icon renderer, medallion/
crown/native item assets, hired-unit halt/ready/following, navigator, home areas,
biome faction spawn lists, control zones, invasions, bandit `LOTREventSpawner`
probability, Forge item events and KOME physical item-drop sound.

Extended saved commission/trial facts, relationship UUID references, encounter role
leases, existing recovery/cleanup, book/tracker publication, world NBT and reward
transactions. New narrow helpers: creation-screen continuation; observed shelter
proof; open-ground actor placement; reversible protected home lease; curated lost
items; reserved small rewards; KOME language fallback; native bandit probability
adapter/transformer. No new quest acceptance, dialogue tree, economy, renderer,
general AI framework or rank system.

## 3. Character creation

ESC opens `GuiIngameMenu` without sending a Back selection mutation. Explicit Back
buttons still work. In-flight stage replies replace the deferred screen, never the
pause/options screen. Returning to the world reopens the latest required screen.
Disconnect clears client continuation; login uses persisted authoritative race,
gender, faction and appearance confirmation flags to derive the next stage.
There is no second redundant serialized stage variable. Only valid finalization
sets creation complete. Underlying SEX fields/IDs remain compatible.

## 4. Presentation responsibilities

HUD: next action and its actual count. Book: assignment context, supplies, survivor
rules, faction standing and message-copy availability. Map: saved identities,
moving actors and separate static destinations. Overhead: crown or enduring
relationship, otherwise an eligible progression action, otherwise native quest.
The book copy countdown refreshes on bounded minute changes; opening/refreshing
the book obtains the current server time. It is not an every-tick countdown stream.

Show Me retains the native map camera and approximately ten-second emphasis;
updated escort actor markers can be focused without creating another waypoint.

## 5. Master and Liege interactions

Master ticks and rank conferral no longer drop the gift. Interaction claims a saved
pending native pouch once, with the existing pop sound. A rejected item spawn leaves
it pending. Successfully spawned gifts use ordinary pickup/despawn and are never
regenerated as lost-gift recovery. Former Master identity remains for the bonus.
Matching-faction and live-NPC safeguards remain.

The Master's advancement guidance uses real native authority hosts, such as the
fortresses of Rohan or military settlements of Gondor; it does not claim a particular
nearby village exists. Current Liege Quest uses the existing direct service route;
repeated requests retain the assignment token. Commissions project orders, travel,
actual combat, recovery/escort/delivery, then personal report. Promotion service
quotas and existing Lordship survivor/report rules are unchanged.

## 6. Escort/refuge system and build safety

Search uses up to 256 persisted observations of existing shelters, up to 32 nearby
loaded native faction home/location witnesses, and native waypoint candidates.
New physical verification needs loaded neighboring chunks, suitable faction terrain,
a clear arrival position, a manufactured roof patch and nearby wall/support evidence.
Proof saves faction, dimension, observed source identifier, roof coordinates and
arrival coordinates. A witness's class/waypoint identifies the observation source;
it is not falsely claimed to identify an exact native generation template.

Known evidence remains available across chunk unload and world reload, with checks
again when loaded. Missing/destroyed shelter fails through existing retry paths.
No planned building is narrated as existing. No automatic generation, new terrain
blending or permanent objective host NPC is introduced.

**Remaining risk to player builds:** roof/wall checks cannot prove original builder,
protection ownership, architecture authenticity or complete path reachability.
A player-built shelter may be recognized as an existing shelter. These checks are
not a guarantee of construction safety. This pass's shelter code writes no blocks;
therefore it cannot overwrite a player build to satisfy an assignment. Native NPC
combat and other mods' ordinary world effects remain outside that guarantee.
Automatic generation would require a separate authoritative protection/transaction
integration before it could safely be enabled.

Charges reuse native following and civilian AI, with combat/pursuit restraint.
Local follow/recalculate and sneak wait/resume retain saved hired state, and the
existing saved teleport lease is restored when the role ends. Standing escorts
restore the existing natural NPC; generated commission actors clean up with the
existing assignment lifecycle. Distant/unloaded absence is inconclusive; sustained
loaded missing-actor evidence produces an explained failure, not fake completion.

## 7. Lost items

New items vary among the existing faction gift-equipment pools and native silver.
Rohan uses its existing sword/spear/helmet/body fields, Gondor its equivalents, and
the existing other faction profiles/fallbacks remain. One native object is requested
for a new recovery. Saved legacy gold and three-silver assignments are unchanged.
Knight property retains KOME's sealed, non-craftable recovery wrapper; its contents
drive the actual native item icon and “Recovered [item]” display. This prevents
replacement attempts manufacturing usable weapons. Standing recovery stores the
selected native stack and requires its actual return.

Bound ground items receive age/lifespan protection on spawn/load and expiry, including
player death drops. Wrong items/owners/assignments/revisions cannot hand over.
The correct held item is consumed once and the item sound plays. Destroyed/lost
standing property ends the trial when returned without it, or fails after bounded
loaded missing evidence. Knight property has a finite three-attempt site issuance
budget, cooldown and revision invalidation. Old copies cannot complete or increase
the multiplier. Rewards remain unavailable without legitimate success.

Bandit pressure changes only the native regional event's local roll, multiplying its
existing probability by two (capped at probability 1). Native biome/type/terrain/
Forge eligibility, group size and ordinary lifetime remain unchanged. The boost
requires the valid active carrier, current pledge, enabled progression, no other
local non-creative player in the native 48-block area, and fewer than four nearby
bandits. This is a threshold for additional pressure, not a global hard ban on normal
native encounters. Shared areas retain normal rates. Actual observed encounter
frequency depends on native attempts, population, terrain and players and needs live
measurement.

## 8. Defense and borders

Initial attackers search an approximately 50-block ring with 32 bounded attempts.
Sites require loaded neighboring chunks, natural ground, modest height variation,
clear body space, no tile entities/containers or manufactured footprint and distance
from both protected center and Liege. Failure declines/fails through existing routes.
This is conservative actor placement, not proof of player block ownership.

Protected participants receive a saved reversible 16-block native home area. They
may defend locally, clear distant pursuit and navigate back when displaced. Original
home state returns after their role ends. No permanent invulnerability/hiring change.

Incursions use native control-zone/biome territory and hostile regional invasion
data. A neighbor within 192 blocks must be hostile, outside defending territory and
match the selected attacking force. Unsupported boundaries in the bounded range
are declined. No hardcoded invented frontier/capital incursion fallback.
Existing quotas, participation, UUID tokens, non-player deaths, finite missing-slot
reconstitution and scoped red outlines are retained.

## 9. Dialogue and actual world information

New structure-dependent assignments must resolve evidence first. Their language is
“the shelter marked on your map,” without inventing a city name from a civilian.
Unverified legacy place strings cannot leak into new commission speech. Plain
geographic objectives use “the ground marked on your map.” Enemy/item names come
from the actual assigned native faction/item. KOME text uses native localization
first and packaged English fallback on headless servers.

Representative supported examples (templates, not a claimed live assignment):

- Rohan, when the saved neighboring hostile force is native Dunland: “Our people
  look to us in troubled times. Warriors of [native Dunland faction name] have
  crossed into our lands near the ground marked on your map. Drive them back,
  then bring me your report.”
- Gondor settlement defense, after a real shelter is verified: “We must keep faith
  with those in our care. Our people at the shelter marked on your map
  have sent word of an attack. Go to their aid and see that they survive it.”
- Recovery: “Raiders have taken property entrusted to my care. Their trail leads
  toward the ground marked on your map. Recover [the actual saved item's display
  name] and bring it back to my hand.”

Shared templates remain for other cultures. No nearby imagined village, fortress or
refuge is asserted by these templates.

## 10. Secondary rewards

| Successful service | Reward | Issuance boundary |
| --- | --- | --- |
| Master provisions/profession | 2 native coin value; faction food fallback | Actual duty completion through interaction or existing menu |
| Courier | 2 native coin value; food fallback | Delivered message reported to Master; death-return fallback earns no extra reward |
| Standing escort/recovery/defense | 2 faction food units, capped by stack limit | Actual trial completion |
| Knight lost property | 2 native coin value; food fallback | Correct item returned and commission formally reported |
| Other Knight commissions | 2 faction food units, capped by stack limit | Commission formally reported |

No alignment reward, bonus promotion credit, Lord/Prince duty or economy is added.
Amounts are simple constants in `KOMEProgressionServiceRewards`. Native items must
be available. A persisted reservation precedes the physical spawn, so replay cannot
issue again; legacy completed services default to already reserved and receive no
retroactive windfall. Unlike the gift, a failed secondary-reward spawn is not retried.
This favors idempotency over guaranteed delivery of a minor bonus. Minecraft's
separate save files do not supply crash-atomic item/world transactions.

## 11. Files changed

The [complete file inventory](PROGRESSION_GAMEPLAY_FILES_20261006.md) lists each file changed/created by this pass and its
purpose. Earlier UX/hierarchy changes present at session start are not attributed
to this pass. Ignored `outputs/progression-gameplay-20261006` contains audit bytecode,
initial file hashes, logs, count JSON and change manifests; normal `build` artifacts
were regenerated by Gradle.

## 12. Verification

Baseline: **1,961 passed / 0 failed / 5 skipped**, 1,966 total.
Final expanded focused suites: **678 passed / 0 failed / 3 skipped**, 681 total.
Final full suite: **1,985 passed / 0 failed / 5 skipped**, 1,990 total.
Normal Gradle `build`: **successful**, including jar, sources and reobfuscation.
Normal `git diff --check`: **passed**. No tests were removed or newly disabled.
Changed expectations now exercise explicit gift claim, physical item return,
verified shelters and current-stage counts rather than earlier contradictory behavior.

Gradle 8.5 (matching wrapper), cached dependencies with `--offline`, Java 17 for the
build runner and the existing Java 8 compiler/toolchain. Build jar:
`build/libs/KOME-LOTR-Addon-1.0.8.jar`. No installed game/world jar was replaced.

Existing full-suite skips:

1. `CustomSkinLibraryFoundationTest.symbolicLinkSkinIsRejectedWhenSupported` —
   platform symlink support/permission assumption.
2. `ClientCustomSkinCacheTest.symbolicLinkAtHashPathIsNeverAcceptedWhenSupported`
   — platform symlink support/permission assumption.
3. `KOMEPublicWaypointTransformerTest.originalProductionAndBuildJarsHaveExactCompatibleHooks`
   — optional external original production/build jar inputs absent.
4. `KOMEWaypointTransformerTest.configuredProductionV3615JarHasVerifiedClassFingerprintAndTransforms`
   — optional configured external production jar input absent.
5. `KOMEWaypointTransformerTest.configuredBuildDependencyV3615JarHasVerifiedClassFingerprintAndTransforms`
   — optional configured external build jar input absent.

The new bandit transformer test uses the actual shipped LOTR class and JVM-loads/
invokes the transformed method with inert world inputs; it is not a source-pattern
test. GUI tests dispatch actual screen ESC methods with rendering-free native app
fixtures. These are automated JVM tests, **not Minecraft launch tests**.

## 13. Required live testing

Test fresh and existing worlds, integrated and dedicated servers, multiplayer,
disconnect/rejoin/restart, chunk unload, player/NPC death, abandonment, pledge
change and promotion. In particular:

- ESC/pause/options at every stage, including in-flight selection replies;
  reconnect/resume and inability to finalize missing creation steps.
- Native NPC name visibility/font/depth/distance, family-name synchronization,
  faction Rank prefix, own-faction ruler icon/title update and indicator priority.
- Moving escort markers, static destinations and Show Me's ten-second emphasis.
- Gift claim after promotion, full inventory, rejected spawn and duplicate clicks.
- Shelter observation coverage near each faction, actual roof/wall/approach quality,
  destroyed-site failure, registry reload and the absence of any new block writes.
- Native escort follow/wait/recalculate, fences, water/roads, civilian fleeing,
  no teleport and restoration of native behavior after release.
- Actual bandit encounter pressure, shared-player-area suppression, local density
  threshold, native type eligibility and stopping after loss/report/disable.
- Attackers around 50 blocks, accessible combat terrain, Liege/protected actor
  survival, native home restoration and real-world frontier choice.
- Quotas, participation/non-player final blows, finite recovery, stale cleanup,
  one-time rewards and legacy saves under actual server restart timing.

Generated refuge appearance/blending cannot be tested in this pass: generation is
disabled. A future implementation must establish protection-safe placement first.

## 14. Known limitations

No automatic refuge generation; no exhaustive unloaded structure/border search;
physical shelter evidence does not certify provenance, faction architecture or
complete pathfinding. Registry cap is 256 observed sites; invalid loaded records
are pruned during selection. Explore/load suitable faction structures before
requesting service where no evidence is available. Assignment creation declines
unsupported contexts, preserving existing retry/alternate assignment routes.

Native names follow native visibility, rather than forcing an always-visible
replacement plate. Shared faction dialogue remains for cultures without new prose.
Minor reward spawn failures consume their reservation; the gift alone retains a
failed issuance for another interaction. Exact bandit frequency, live multiplayer
timing/TPS, pathfinding and separate-save crash atomicity are not certified.

The safety decision is explicit: uncertain sites do not justify placing a building
or claiming a nonexistent refuge. Existing protections and ownership policies were
not redefined to pretend they prove individual block ownership.
