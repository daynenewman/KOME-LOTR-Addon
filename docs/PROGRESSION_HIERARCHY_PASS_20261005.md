# Progression hierarchy and live-testing corrections — started 2026-10-05, completed 2026-10-06

Branch: elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system.

The saved player ladder remains **Wanderer → Serf → Knight → Lord → Prince**.
Player King is the existing political office, not a sixth saved progression rank.
No Lord duties, Lord → Prince requirements, Prince duties, War Service, new Knight
commissions, new Serf duty categories, or player-King accession mechanics were added.
Existing higher-rank advancement systems were retained.

The working tree already contained an uncommitted progression UX pass and character
creation changes. Those were retained. Nothing was committed, pushed, or merged.

## Native/system audit

Audited the local LOTRMod v36.15.jar, including native map bytecode, NPC initial-spawn,
NBT/name behavior, biome faction containers, invasion definitions, captain classes
and structure references. The region matrix reads actual native biome constructor
references, including inherited regional definitions.

Reused the canonical NPC rank enum/records, Liege policy, relationship state and
role leases; native pledge/alignment and the pledge-release lifecycle; unit-trader
eligibility, NPC construction/equipment/home areas; native conquest/invasion spawn
data; native miniquest presentation and accepted quest data; native LOTRGuiMap camera
fields and the existing marker packet/render bridge; existing medallion/crown art;
KOMERulerService player King records; authoritative capital records; and existing
encounter tokens, actor identities, death hooks and cleanup.

Important native findings:

- Natural spawning inserts an entity before its initial-spawn callback. A transient
  provisional record handles that ordering; initial spawn assigns the final weighted
  rank once. NBT-loaded NPCs take a conservative migration path.
- LOTR rejects ordinary calls to its NPC name setter. Authority titles use the
  existing synchronized name field, retaining recognizable personal names.
- Native trader respawn is disabled only for canonical rulers so it cannot compete
  with the ten-minute canonical respawn lifecycle.
- Fangorn has Ents/Huorns, but no suitable native unit-trader/captain.

## Live-testing fixes, individually

| Request | Result |
| --- | --- |
| 1. Opening objective | Unpledged Wanderers see **Pledge to a faction**; pledged Wanderers see **Find a Master to serve**. Neither retains an old meter. The native pledge setter triggers server refresh; login projection reads the actual pledge. |
| 2. Pledge before Master | Selection checks a valid playable pledge, matching native faction, rank, adulthood, identity and range. Rejection is explained. Offers carry a persisted pledge revision and displayed offer UUID; the queued response packet rejects replay. Native boolean-only responses cannot accept Master offers. |
| 3. Courier priority | The client suppresses both ordinary recipient interaction and fallback book GUI after vanilla sends entity interaction. Server delivery precedes offers/trading, validates assignment/revision/recipient/pledge, and consumes exactly one matching held book. Wrong recipients/stale books do not consume it; delivered assignments cannot complete twice. Reading elsewhere remains available. |
| 4. Show Me | Opens the native map and sets camera/previous positions and movement fields from saved coordinates. Yellow emphasis lasts ten monotonic seconds. Repeating it restarts the timer; no waypoint or assignment is created. The active marker outlives the emphasis. |
| 5. Native miniquest indicators | Player-specific markers, eligibility and role leases suppress offer/accepted-quest overhead indicators for relevant NPCs. Hidden participant entries cover witnesses/Trial guards without another visible icon. Native quest data/capability is retained. |
| 6. Persistent medallions | Overhead identity no longer requires actionable state. Master/Liege identity is separate from availability/cooldown. |
| 7. Daily Master dialogue | Current-day completion produces the existing “Return tomorrow” speech. Server cadence is checked before generation, including courier preparation. No extra duty is issued. |
| 8. Available-duty wording | Tracker wording is **Talk with your Master**. |
| 9. Category separation | New Provision requests use local edible/drinkable goods only. Profession excludes food/drink and retains occupation/material profiles. |
| 10. Quantities | New requests use the stackability rules below, including actual drink vessel and metadata-dependent stack limit. |
| 11. Per-item progress | Book names and current/required counts are projected per item. Qualifying inventory plus deposits count, capped at the requirement. Wrong metadata/vessel/strength does not count. Inventory refresh is server-authored; delivery updates immediately. Native language fallback prevents implementation keys. Liege relief goods use the same progress concept. |
| 12. Alignment transition | Completion switches to **Seek a prospective Liege**, empty progress text and zero completion. The HUD omits the old meter. Explicit tests cover the transition. |
| 13. Discovery guidance | The book describes greater standing and faction-specific native structures rather than a generic “find a Liege.” |
| 14. Master until fealty | Duties/alignment completion retain the medallion. Successful Liege fealty records formal release. Failed assignment acceptance rolls the state back, including release. Reload retains this distinction. |

## Opening progression

1. **Unpledged Wanderer:** Pledge to a faction.
2. **Pledged Wanderer:** Find a Master to serve.
3. **Accepted matching offer:** canonical Serf; Master medallion persists.
4. **Duty available:** Talk with your Master.
5. **Today's duty complete:** server cadence blocks another duty; Master acknowledges tomorrow.
6. **Goods duty:** book shows each qualifying quantity and its saved requirement.
7. **Duties complete, alignment incomplete:** live faction alignment requirement.
8. **Alignment reached:** Seek a prospective Liege, without the old alignment meter.
9. **Seeking:** find the faction's Lord-ranked native leader in the locations below.
10. **Successful Liege fealty/Trial acceptance:** Liege medallion; Master relationship released.
11. **Existing Trial/farewell lifecycle:** retained; the former Master reference remains
    a service witness for the established gift/knighthood path.
12. **Knight:** only a matching Lord-ranked NPC is a valid progression Liege.

## Provision versus profession

**Provision:** edible items/drinks from existing local harvest/drink catalogs.
No newly requested equipment, tools, ore, building supplies or crafting components.

**Profession:** existing occupation/material profiles, with edible/drinkable items
filtered out. Non-edible ingredients such as wheat, seeds, sugar or eggs remain valid
profession materials where appropriate.

| Actual requested stack | Units per type | Distinct types in that group |
| --- | --- | --- |
| Non-stackable | 1–16 | 1–2 |
| Stackable | 32–64 | 2–3 |

Mixed requests can contain both groups. A stackable group with fewer than two native
candidate types is omitted rather than filled with invented goods. Units may occupy
multiple legal stacks; no overfull stack is created. Drink vessel/strength are saved
and matched. Generated identities, quantities and deposits survive reload.

Previously issued goods requests are retained exactly, including legacy quantities
and mixed supply requests. They are not silently rerolled/reclassified mid-duty.
The new category/quantity rules apply at issuance.

## Liege discovery and NPC hierarchy

Guidance is grounded in native structure classes referencing the relevant captains,
including Gondor's fiefdom/settlement definitions. It does not promise a captain at
an arbitrary waypoint or claim every capital is a prebuilt city.

| Player rank | Required progression superior |
| --- | --- |
| Serf | Matching unranked Master |
| Knight | Matching **LORD** NPC |
| Lord | Matching **PRINCE** NPC |
| Prince | Existing player King, otherwise canonical **KING** NPC |

Serfs seeking a Trial approach the Lord they will serve as Knights. Higher relationship
selection/replacement uses existing fealty architecture without unfinished duties.

New appropriate captains receive **75% LORD / 25% PRINCE** once. A 100,000-assignment
statistical test checks approximately 3:1. Explicit/saved ranks and established
relationships are not rerolled. Titles use Captain/Marshal/Chieftain/Warlord/High Lord
etc. where appropriate; no second saved rank enum was introduced.

## Ruler system and faction roster

The following behavior applies to **every supported row**:

- One persistent faction ruler slot, with 0 or 1 active canonical NPC.
- Capital comes from KOMEFactionCapitalService, honoring its current stored record.
  The table lists existing default waypoints, not replacement coordinates.
- Native faction-appropriate captain; bounded safe placement within 24 blocks,
  correct dimension, loaded terrain/body clearance, and native home radius 24.
- Persistent UUID/incarnation/name; duplicate join rejection, including copied NBT
  with changed UUID; deterministic saved-record and loaded-instance reconciliation.
  Duplicate retirement drops no inventory/rewards.
- Crown above the NPC and on the native map. Snapshot replacement prevents duplicate
  crowns; off-screen rulers are clipped rather than piled onto map borders.
- Confirmed death starts one **600,000 ms runtime** countdown. No early respawn.
  Remaining time is saved; shutdown pauses it. Runtime advances without nearby
  players or loaded capital chunks; reconstitution waits for safe loaded terrain.
- Player King takes political precedence. The NPC remains world representation,
  but Prince fealty to it is blocked and presentation points to the player King.
- No new player-to-player duties or accession system.

| Faction | Native ruler type (LOTREntity…) | Ruler title | Default capital | Lord discovery |
| --- | --- | --- | --- | --- |
| Hobbit | HobbitShirriff | Thain | MICHEL_DELVING | Shire taverns |
| Bree | BreeCaptain | Mayor | BREE | Watch offices |
| Dúnedain | RangerNorthCaptain | Chieftain | FORNOST | Northern Ranger camps |
| Blue Mountains | BlueDwarfCommander | Lord | THORIN_HALLS | Strongholds |
| High Elves | HighElfLord | High King | MITHLOND_NORTH | High Elven towers; Rivendell halls |
| Gundabad | GundabadOrcMercenaryCaptain | Overlord | MOUNT_GUNDABAD | Orc camps; ruined Dwarven towers |
| Angmar | AngmarOrcMercenaryCaptain | Overlord | CARN_DUM | Towers; hillman chieftain houses |
| Wood Elves | WoodElfCaptain | King | THRANDUIL_HALLS | Woodland Realm watchtowers |
| Dol Guldur | DolGuldurOrcChieftain | Overlord | DOL_GULDUR | Orc towers |
| Dale | DaleCaptain | King | DALE_CITY | Fortresses |
| Durin's Folk | DwarfCommander | King | EREBOR | Dwarven towers |
| Lothlórien | GaladhrimLord | Lord | CARAS_GALADHON | Native Elf lord-houses |
| Dunland | DunlendingWarlord | Chieftain | WULFBURG | Hill-forts |
| Isengard | UrukHaiMercenaryCaptain | Overlord | ISENGARD | Uruk camps |
| Rohan | RohirrimMarshal | King | EDORAS | Fortresses |
| Gondor | GondorianCaptain | Steward | MINAS_TIRITH | Military settlements/fiefdoms |
| Mordor | MordorOrcMercenaryCaptain | Overlord | BARAD_DUR | Towers; Black Uruk forts |
| Dorwinion | DorwinionCaptain | Master | DORWINION_COURT | Captains' tents |
| Rhûn | EasterlingWarlord | King | RHUN_CAPITAL | Easterling fortresses |
| Near Harad | UmbarCaptain | Lord | UMBAR_CITY | Umbar/Southron fortresses; war camps |
| Morwaith | MoredainChieftain | High Chieftain | GREAT_PLAINS_EAST | Chieftains' huts |
| Taurethrim | TauredainChieftain | King | JUNGLE_CITY_CAPITAL | Chieftains' pyramids |
| Half-trolls | HalfTrollWarlord | High Chieftain | SHADOW_POINT | Warlords' houses |

**Fangorn:** DERNDINGLE remains authoritative, but no NPC ruler was fabricated.
Native v36.15 supplies no suitable captain/unit-trader/quest host. Existing
Fangorn/player-ruler systems remain intact.

Legacy explicit Kings are adopted deliberately. Capital binding can safely relocate
an existing ruler once; ordinary ticking uses home behavior instead of repeated
teleporting. King death no longer immediately promotes an arbitrary Prince or
schedules the former King to return as a Prince. Old restoration data remains readable.

## Defensive encounter selection

Choices come from the actual location's native biome faction containers, including
zero-base-weight conquest forces, and native biome invasion lists. Conquest choices
precede invasion-only choices. Forces must be hostile, distinct from the defender
and not friendly in either direction; native unit definitions are validated.

No Hobbiton-specific lists or worldwide generic fallback. The 24-region native matrix
checks permitted forces, excludes friends/same faction, and checks Shire threats.
Regions without attackers omit Defense before Serf Trial issuance; Knight/Lordship
generation declines unavailable scenarios. Saved objectives are not rerolled by map
use or reload.

## Defensive encounter persistence

- Active role leases veto native natural despawn; bindings survive unload.
- Confirmed uncancelled deaths count regardless of final-blow source. Existing
  contribution/presence and Lordship survivor rules remain.
- Unloaded entities are inconclusive. Missing enemies recover only after repeated
  evidence in a loaded neighborhood, using saved class/location/token information.
- Recovery replaces one slot. Recorded deaths never replenish slots.
- Three repairs per missing slot bound recovery; persistent corruption causes
  ordinary recoverable failure rather than an endless spawning loop.
- Completion/failure/abandonment/allegiance invalidation remove or release actors.
  Stale bindings retire on load. Global hostile despawn policy is unchanged.

## Red enemy outline

Only the current player's server-authored active enemy UUIDs qualify. Loaded living
objective NPCs at **32–96 blocks** use native textured geometry in a stencil pass:
the center silhouette is masked, then thin red offset rims are drawn. Textures remain
intact. Native overhead extras and shadow/fire rendering are excluded. GL state and
the reserved Forge stencil bit are released afterward.

Close, unrelated, dead, unloaded and no-longer-bound mobs receive no outline.
A usable client stencil buffer is required; actual GPU appearance/performance needs
in-game verification.

## Marker priority

**Canonical crown → persistent Master/Liege medallion → farewell/action progression
icon.** Native indicators are suppressed for that player's relevant NPCs. Hidden
participant entries suppress clutter without another visible icon. Ordinary
indicators can return after relevance ends.

## Migration

- Ordinary Masters stay unranked; no random upgrade.
- Valid Serf/Knight Lieges without records receive one stable LORD record.
  Explicit PRINCE/KING records remain.
- Lord→Lord or Knight→Prince/King relationships release under exact policy without
  changing player rank or promoting NPCs; completed evidence remains.
- New captain assignment is weighted once; NBT migration is conservative/stable.
- Formal Master release persists; the service witness remains for farewell/promotion.
- King slot/incarnation/runtime/location/name/capital-binding fields are additive.
- Saved goods/deposits stay exact, including pre-pass category mixes/quantities.

## Tests and build

Focused/system regressions: **628 tests, 628 passed, 0 failed, 0 skipped**.
The four new focused classes contain **55 cases**, including the 24-region matrix.
An additional missing-enemy budget case expands existing coverage.

Full suite: **1,966 tests; 1,961 passed, 0 failed, 5 skipped**.
The following final XML groups overlap:

| Area | Tests | Failed |
| --- | ---: | ---: |
| New focused classes | 55 | 0 |
| Serf | 104 | 0 |
| Courier | 25 | 0 |
| Relationships/markers | 29 | 0 |
| Knight Commissions | 48 | 0 |
| Lordship Trials | 83 | 0 |
| Progression hardening | 74 | 0 |
| Ruler/rank/death | 36 | 0 |
| Native miniquest/offer bridge | 24 | 0 |
| Faction/conquest/regional selection | 150 | 0 |
| Rendering/visual/map focus | 38 | 0 |

Five existing conditional skips: two symbolic-link skin tests and three configured
external production/build waypoint-jar checks. No test was disabled for this pass.
Available dependency bytecode transformer checks remain exercised.

Commands: gradlew.bat test; normal gradlew.bat build --console=plain.
Build: **BUILD SUCCESSFUL**, exit 0; 18 actionable tasks, 5 executed, 13 up-to-date.
Artifact: build/libs/KOME-LOTR-Addon-1.0.8.jar (17,068,660 bytes at verification).
Git diff --check: **passed**, exit 0.
Logs/count summaries are under ignored build/hierarchy-* files. No game installation
was changed by the build.

## Remaining live-testing concerns

Verification was source/bytecode audit and automated tests, not a launched game.

- Native pathfinding/home behavior, escorts and attackers navigating settlements.
- Camera centering at different zoom/window sizes and other coremod combinations.
- Ten-second emphasis while reopening/repeating Show Me; persistent markers afterward.
- Title/personal-name readability with real native names.
- 3:1 rarity in world generation; native survival/despawn can affect long-term density.
- Terrain/roof/cave placement at actual capitals; unavailable terrain must wait safely.
- Ruler death/restart/cooldown, native mounted captain initialization, respawn, and
  copied/chunk-restored ruler reconciliation.
- Red silhouette/stencil support, range cutoff and actual GPU/frame cost.
- Regional plausibility at biome boundaries/capitals; no fabricated force in regions
  lacking native attacker data.
- Multiplayer suppression: one player's relationship must not hide another player's
  ordinary indicator. Authority names/crowns are shared world identity.
- Finish or intentionally reset old mixed goods requests when testing new issuance;
  saved requests are deliberately not rerolled.

## Files changed

Inventory below distinguishes this pass from pre-existing uncommitted work and lists
every changed production/test/resource path with its purpose.

| Scope | File | Purpose |
| --- | --- | --- |
| Pre-existing; preserved | [src/main/java/com/lotrcharactercreation/client/gui/GuiAppearanceSelection.java](../src/main/java/com/lotrcharactercreation/client/gui/GuiAppearanceSelection.java) | Existing character creation/alignment/appearance integration; retained without edits in this pass. |
| Pre-existing; preserved | [src/main/java/com/lotrcharactercreation/client/gui/GuiManSkinReview.java](../src/main/java/com/lotrcharactercreation/client/gui/GuiManSkinReview.java) | Existing character creation/alignment/appearance integration; retained without edits in this pass. |
| Pre-existing; preserved | [src/main/java/com/lotrcharactercreation/client/render/AppearancePreviewRenderer.java](../src/main/java/com/lotrcharactercreation/client/render/AppearancePreviewRenderer.java) | Existing character creation/alignment/appearance integration; retained without edits in this pass. |
| Pre-existing; preserved | [src/main/java/com/lotrcharactercreation/client/render/ManSkinReviewPreviewRenderer.java](../src/main/java/com/lotrcharactercreation/client/render/ManSkinReviewPreviewRenderer.java) | Existing character creation/alignment/appearance integration; retained without edits in this pass. |
| Pre-existing; preserved | [src/main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java](../src/main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java) | Existing character creation/alignment/appearance integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/client/KOMEClientProxy.java](../src/main/java/kome/client/KOMEClientProxy.java) | Register client priority, offer-response and outline hooks. |
| Pre-existing; preserved | [src/main/java/kome/client/KOMELiegeQuestButtonOverlay.java](../src/main/java/kome/client/KOMELiegeQuestButtonOverlay.java) | Existing KOMELiegeQuestButtonOverlay progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/client/KOMEProgressionOfferClientBridge.java](../src/main/java/kome/client/KOMEProgressionOfferClientBridge.java) | Send displayed Master offer UUID from native GUI. |
| This pass | [src/main/java/kome/client/KOMEProgressionTrackerOverlay.java](../src/main/java/kome/client/KOMEProgressionTrackerOverlay.java) | Hide meters for nonnumeric objectives. |
| This pass | [src/main/java/kome/client/KOMEVisualMarkerClientState.java](../src/main/java/kome/client/KOMEVisualMarkerClientState.java) | Player-specific relevance lookup and snapshot lifecycle. |
| This pass | [src/main/java/kome/client/KOMEVisualRenderBridge.java](../src/main/java/kome/client/KOMEVisualRenderBridge.java) | Persistent icon priority, native indicator suppression and map rendering. |
| Pre-existing; preserved | [src/main/java/kome/client/gui/KOMEGuiProgression.java](../src/main/java/kome/client/gui/KOMEGuiProgression.java) | Existing KOMEGuiProgression progression/UX integration; retained without edits in this pass. |
| Pre-existing; preserved | [src/main/java/kome/client/gui/KOMEGuiSerfdomMaster.java](../src/main/java/kome/client/gui/KOMEGuiSerfdomMaster.java) | Existing KOMEGuiSerfdomMaster progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/common/command/KOMECommandKome.java](../src/main/java/kome/common/command/KOMECommandKome.java) | Validate authority and pledge before admin relationship mutation. |
| This pass | [src/main/java/kome/common/data/KOMECourierService.java](../src/main/java/kome/common/data/KOMECourierService.java) | Deliver one matching held bound book exactly once. |
| This pass | [src/main/java/kome/common/data/KOMECurrentLiege.java](../src/main/java/kome/common/data/KOMECurrentLiege.java) | Central exact-rank and political authority validation. |
| This pass | [src/main/java/kome/common/data/KOMEEvents.java](../src/main/java/kome/common/data/KOMEEvents.java) | Courier priority, joins/deaths, inventory refresh and independent ruler runtime. |
| This pass | [src/main/java/kome/common/data/KOMEKnightCommission.java](../src/main/java/kome/common/data/KOMEKnightCommission.java) | Persist missing-slot observation and repair counts. |
| This pass | [src/main/java/kome/common/data/KOMEKnightCommissionService.java](../src/main/java/kome/common/data/KOMEKnightCommissionService.java) | Regional forces and finite missing-enemy recovery. |
| This pass | [src/main/java/kome/common/data/KOMELordshipTrialService.java](../src/main/java/kome/common/data/KOMELordshipTrialService.java) | Shared missing-enemy recovery; retains participation/survivor rules. |
| Pre-existing; preserved | [src/main/java/kome/common/data/KOMEPartingGiftService.java](../src/main/java/kome/common/data/KOMEPartingGiftService.java) | Existing KOMEPartingGiftService progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/common/data/KOMEPlayerProgression.java](../src/main/java/kome/common/data/KOMEPlayerProgression.java) | Persist offer pledge identity/revision. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionAutoCompleter.java](../src/main/java/kome/common/data/KOMEProgressionAutoCompleter.java) | Publish exact goods and sovereign presentation. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionEncounterMarker.java](../src/main/java/kome/common/data/KOMEProgressionEncounterMarker.java) | Entity-only ruler slot/incarnation identity. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionLiegePolicy.java](../src/main/java/kome/common/data/KOMEProgressionLiegePolicy.java) | Central hierarchy and player-King precedence seam. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionNpcInteractionService.java](../src/main/java/kome/common/data/KOMEProgressionNpcInteractionService.java) | Real daily cooldown acknowledgement and policy reuse. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionNpcRankRecord.java](../src/main/java/kome/common/data/KOMEProgressionNpcRankRecord.java) | Persistent LORD ranks and additive ruler lifecycle fields. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionNpcRankService.java](../src/main/java/kome/common/data/KOMEProgressionNpcRankService.java) | Weighted birth, stable migration, titles and ruler adoption. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionNpcRoleLease.java](../src/main/java/kome/common/data/KOMEProgressionNpcRoleLease.java) | Defense enemy role. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionNpcRoles.java](../src/main/java/kome/common/data/KOMEProgressionNpcRoles.java) | Scoped enemy persistence and actor indexing. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionNpcSuccessionService.java](../src/main/java/kome/common/data/KOMEProgressionNpcSuccessionService.java) | Legacy King-death entry point uses delayed reconstitution. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionOfferBridge.java](../src/main/java/kome/common/data/KOMEProgressionOfferBridge.java) | Offer identity/revision validation and exact superior policy. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionRankSummary.java](../src/main/java/kome/common/data/KOMEProgressionRankSummary.java) | Native discovery guidance and readable goods names. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionRelationshipLifecycle.java](../src/main/java/kome/common/data/KOMEProgressionRelationshipLifecycle.java) | Rank-aware recovery and player-King supersession. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionSummary.java](../src/main/java/kome/common/data/KOMEProgressionSummary.java) | Descriptive discovery, alignment gating, historical Master wording. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionTrackerService.java](../src/main/java/kome/common/data/KOMEProgressionTrackerService.java) | Inventory-driven server book refresh. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionTrackerSnapshot.java](../src/main/java/kome/common/data/KOMEProgressionTrackerSnapshot.java) | Pledge opening, Talk-with-Master and clean alignment transition. |
| This pass | [src/main/java/kome/common/data/KOMESerfKnightDefenseService.java](../src/main/java/kome/common/data/KOMESerfKnightDefenseService.java) | Regional attackers, saved actor identity/location and finite recovery. |
| Pre-existing; preserved | [src/main/java/kome/common/data/KOMESerfKnightEscortService.java](../src/main/java/kome/common/data/KOMESerfKnightEscortService.java) | Existing KOMESerfKnightEscortService progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/common/data/KOMESerfKnightProgression.java](../src/main/java/kome/common/data/KOMESerfKnightProgression.java) | Formal Master release separate from historical witness/availability. |
| Pre-existing; preserved | [src/main/java/kome/common/data/KOMESerfKnightRecoveryService.java](../src/main/java/kome/common/data/KOMESerfKnightRecoveryService.java) | Existing KOMESerfKnightRecoveryService progression/UX integration; retained without edits in this pass. |
| Pre-existing; preserved | [src/main/java/kome/common/data/KOMESerfKnightRelationshipService.java](../src/main/java/kome/common/data/KOMESerfKnightRelationshipService.java) | Existing KOMESerfKnightRelationshipService progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/common/data/KOMESerfKnightService.java](../src/main/java/kome/common/data/KOMESerfKnightService.java) | Central policy, transactional rollback and regional feasibility. |
| This pass | [src/main/java/kome/common/data/KOMESerfProfessionAssignment.java](../src/main/java/kome/common/data/KOMESerfProfessionAssignment.java) | Actual stackability, quantity/type rules and stable requirements. |
| This pass | [src/main/java/kome/common/data/KOMESerfProfessionCatalog.java](../src/main/java/kome/common/data/KOMESerfProfessionCatalog.java) | Exclude consumed food/drink from occupation materials. |
| This pass | [src/main/java/kome/common/data/KOMESerfProvisioningAssignment.java](../src/main/java/kome/common/data/KOMESerfProvisioningAssignment.java) | New quantities/type groups, variable saved schema and names. |
| This pass | [src/main/java/kome/common/data/KOMESerfProvisioningCatalog.java](../src/main/java/kome/common/data/KOMESerfProvisioningCatalog.java) | Local edible/drinkable goods only. |
| This pass | [src/main/java/kome/common/data/KOMESerfdomMasterService.java](../src/main/java/kome/common/data/KOMESerfdomMasterService.java) | Matching pledge and cadence before generation. |
| This pass | [src/main/java/kome/common/data/KOMESerfdomOfferQuest.java](../src/main/java/kome/common/data/KOMESerfdomOfferQuest.java) | Captured persisted pledge revision. |
| This pass | [src/main/java/kome/common/data/KOMEVisualLocationService.java](../src/main/java/kome/common/data/KOMEVisualLocationService.java) | Persistent identities, crowns, enemies and hidden participants. |
| This pass | [src/main/java/kome/common/data/KOMEVisualMarker.java](../src/main/java/kome/common/data/KOMEVisualMarker.java) | Enemy/participant roles without parallel waypoint state. |
| This pass | [src/main/java/kome/common/data/KOMEWorldData.java](../src/main/java/kome/common/data/KOMEWorldData.java) | Deterministic King reconciliation and relationship migration. |
| This pass | [src/main/java/kome/common/network/KOMEPacketHandler.java](../src/main/java/kome/common/network/KOMEPacketHandler.java) | New queued response at unused ID 48; existing IDs retained. |
| This pass | [src/main/java/kome/common/network/KOMEPacketRelationshipAction.java](../src/main/java/kome/common/network/KOMEPacketRelationshipAction.java) | Central superior policy for Trial interaction. |
| Pre-existing; preserved | [src/main/java/kome/common/network/KOMEPacketSerfdomMasterAction.java](../src/main/java/kome/common/network/KOMEPacketSerfdomMasterAction.java) | Existing KOMEPacketSerfdomMasterAction progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/common/network/KOMEPacketVisualMarkers.java](../src/main/java/kome/common/network/KOMEPacketVisualMarkers.java) | Bounded capacity 64 for rulers and objective actors. |
| Pre-existing; preserved | [src/main/java/kome/core/KOMECorePlugin.java](../src/main/java/kome/core/KOMECorePlugin.java) | Existing KOMECorePlugin progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/core/KOMEProgressionNpcDespawnTransformer.java](../src/main/java/kome/core/KOMEProgressionNpcDespawnTransformer.java) | Initial-spawn/NBT rank hooks and scoped despawn veto. |
| This pass | [src/main/java/kome/core/KOMEProgressionOfferTransformer.java](../src/main/java/kome/core/KOMEProgressionOfferTransformer.java) | Native Master replay-blocking response boundary. |
| This pass | [src/main/java/kome/core/KOMEVisualLocationTransformer.java](../src/main/java/kome/core/KOMEVisualLocationTransformer.java) | Player-specific overhead suppression and outline overlay guard. |
| This pass | [src/test/java/kome/client/KOMEVisualRenderBridgeTest.java](../src/test/java/kome/client/KOMEVisualRenderBridgeTest.java) | Focused VisualRenderBridge coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/KOMEAccessFixture.java](../src/test/java/kome/common/KOMEAccessFixture.java) | Correct headless air collision to native air semantics. |
| Pre-existing; preserved | [src/test/java/kome/common/data/KOMECanonicalPlayerRankTest.java](../src/test/java/kome/common/data/KOMECanonicalPlayerRankTest.java) | Existing KOMECanonicalPlayerRank regression coverage; retained. |
| Pre-existing; preserved | [src/test/java/kome/common/data/KOMECourierDispatchDropTest.java](../src/test/java/kome/common/data/KOMECourierDispatchDropTest.java) | Existing KOMECourierDispatchDrop regression coverage; retained. |
| This pass | [src/test/java/kome/common/data/KOMEKnightCommissionGameplayTest.java](../src/test/java/kome/common/data/KOMEKnightCommissionGameplayTest.java) | Focused KnightCommissionGameplay coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMEKnightCommissionInteractionTest.java](../src/test/java/kome/common/data/KOMEKnightCommissionInteractionTest.java) | Focused KnightCommissionInteraction coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMELordshipTrialGenerationTest.java](../src/test/java/kome/common/data/KOMELordshipTrialGenerationTest.java) | Focused LordshipTrialGeneration coverage and updated assertions for requested behavior. |
| Pre-existing; preserved | [src/test/java/kome/common/data/KOMELordshipTrialIntegrationTest.java](../src/test/java/kome/common/data/KOMELordshipTrialIntegrationTest.java) | Existing KOMELordshipTrialIntegration regression coverage; retained. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionFollowupTest.java](../src/test/java/kome/common/data/KOMEProgressionFollowupTest.java) | Focused ProgressionFollowup coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionHardeningFactionTest.java](../src/test/java/kome/common/data/KOMEProgressionHardeningFactionTest.java) | Focused ProgressionHardeningFaction coverage and updated assertions for requested behavior. |
| Pre-existing; preserved | [src/test/java/kome/common/data/KOMEProgressionHardeningServiceTest.java](../src/test/java/kome/common/data/KOMEProgressionHardeningServiceTest.java) | Existing KOMEProgressionHardeningService regression coverage; retained. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionNpcDeathLifecycleTest.java](../src/test/java/kome/common/data/KOMEProgressionNpcDeathLifecycleTest.java) | Focused ProgressionNpcDeathLifecycle coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionNpcRankTest.java](../src/test/java/kome/common/data/KOMEProgressionNpcRankTest.java) | Focused ProgressionNpcRank coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionOfferBridgeTest.java](../src/test/java/kome/common/data/KOMEProgressionOfferBridgeTest.java) | Focused ProgressionOfferBridge coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionRankSummaryTest.java](../src/test/java/kome/common/data/KOMEProgressionRankSummaryTest.java) | Focused ProgressionRankSummary coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionTrackerSnapshotTest.java](../src/test/java/kome/common/data/KOMEProgressionTrackerSnapshotTest.java) | Focused ProgressionTrackerSnapshot coverage and updated assertions for requested behavior. |
| Pre-existing; preserved | [src/test/java/kome/common/data/KOMESerfKnightIntegratedJourneyTest.java](../src/test/java/kome/common/data/KOMESerfKnightIntegratedJourneyTest.java) | Existing KOMESerfKnightIntegratedJourney regression coverage; retained. |
| Pre-existing; preserved | [src/test/java/kome/common/data/KOMESerfKnightProgressionTest.java](../src/test/java/kome/common/data/KOMESerfKnightProgressionTest.java) | Existing KOMESerfKnightProgression regression coverage; retained. |
| Pre-existing; preserved | [src/test/java/kome/common/data/KOMESerfKnightStabilizationTest.java](../src/test/java/kome/common/data/KOMESerfKnightStabilizationTest.java) | Existing KOMESerfKnightStabilization regression coverage; retained. |
| This pass | [src/test/java/kome/common/data/KOMESerfProfessionAssignmentTest.java](../src/test/java/kome/common/data/KOMESerfProfessionAssignmentTest.java) | Focused SerfProfessionAssignment coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMESerfProvisioningAssignmentTest.java](../src/test/java/kome/common/data/KOMESerfProvisioningAssignmentTest.java) | Focused SerfProvisioningAssignment coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/network/KOMEPacketRegistrationTest.java](../src/test/java/kome/common/network/KOMEPacketRegistrationTest.java) | Focused PacketRegistration coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/core/KOMEVisualLocationTransformerTest.java](../src/test/java/kome/core/KOMEVisualLocationTransformerTest.java) | Focused VisualLocationTransformer coverage and updated assertions for requested behavior. |
| Pre-existing; preserved | [src/test/java/kome/core/KOMEWaypointTransformerTest.java](../src/test/java/kome/core/KOMEWaypointTransformerTest.java) | Existing KOMEWaypointTransformer regression coverage; retained. |
| Pre-existing; preserved | [src/test/java/kome/integration/CharacterCreationIsolationTest.java](../src/test/java/kome/integration/CharacterCreationIsolationTest.java) | Existing CharacterCreationIsolation regression coverage; retained. |
| This pass | [docs/PROGRESSION_HIERARCHY_PASS_20261005.md](../docs/PROGRESSION_HIERARCHY_PASS_20261005.md) | Complete review report and file inventory. |
| Pre-existing; preserved | [docs/PROGRESSION_UX_PASS_20261003.md](../docs/PROGRESSION_UX_PASS_20261003.md) | Existing progression UX report; retained. |
| Pre-existing; preserved | [src/main/java/com/lotrcharactercreation/client/render/AppearancePreviewOrientation.java](../src/main/java/com/lotrcharactercreation/client/render/AppearancePreviewOrientation.java) | Existing character creation/alignment/appearance integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/client/KOMECourierInteractionPriority.java](../src/main/java/kome/client/KOMECourierInteractionPriority.java) | Prioritize delivery over client NPC/book GUI use. |
| This pass | [src/main/java/kome/client/KOMEProgressionEnemyOutline.java](../src/main/java/kome/client/KOMEProgressionEnemyOutline.java) | Player-specific distant native-geometry red silhouette. |
| This pass | [src/main/java/kome/client/KOMEProgressionMapFocus.java](../src/main/java/kome/client/KOMEProgressionMapFocus.java) | Native camera centering and ten-second emphasis. |
| Pre-existing; preserved | [src/main/java/kome/common/data/KOMELiegeProgressionInteraction.java](../src/main/java/kome/common/data/KOMELiegeProgressionInteraction.java) | Existing KOMELiegeProgressionInteraction progression/UX integration; retained without edits in this pass. |
| Pre-existing; preserved | [src/main/java/kome/common/data/KOMEProgressionEscortFollowing.java](../src/main/java/kome/common/data/KOMEProgressionEscortFollowing.java) | Existing KOMEProgressionEscortFollowing progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionGoodsPresentation.java](../src/main/java/kome/common/data/KOMEProgressionGoodsPresentation.java) | Per-item inventory/deposit progress for duties and relief. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionGoodsRules.java](../src/main/java/kome/common/data/KOMEProgressionGoodsRules.java) | Shared food/type/count rules and native language fallback. |
| Pre-existing; preserved | [src/main/java/kome/common/data/KOMEProgressionItemDrops.java](../src/main/java/kome/common/data/KOMEProgressionItemDrops.java) | Existing KOMEProgressionItemDrops progression/UX integration; retained without edits in this pass. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionNativeAuthority.java](../src/main/java/kome/common/data/KOMEProgressionNativeAuthority.java) | Audited faction captain/title/structure roster. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionPledgeBridge.java](../src/main/java/kome/common/data/KOMEProgressionPledgeBridge.java) | Immediate server refresh from native pledge setter. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionRegionalEnemies.java](../src/main/java/kome/common/data/KOMEProgressionRegionalEnemies.java) | Native regional conquest/invasion-compatible hostile forces. |
| This pass | [src/main/java/kome/common/data/KOMEProgressionRulerService.java](../src/main/java/kome/common/data/KOMEProgressionRulerService.java) | Canonical slots, capital safety, singleton, crown and runtime respawn. |
| This pass | [src/main/java/kome/common/network/KOMEPacketMasterOfferResponse.java](../src/main/java/kome/common/network/KOMEPacketMasterOfferResponse.java) | Validate displayed offer UUID server-side. |
| This pass | [src/main/java/kome/core/KOMEAlignmentSyncTransformer.java](../src/main/java/kome/core/KOMEAlignmentSyncTransformer.java) | Native pledge hook; retains integrated-server side fix. |
| Pre-existing; preserved | [src/test/java/com/lotrcharactercreation/client/render/AppearancePreviewOrientationTest.java](../src/test/java/com/lotrcharactercreation/client/render/AppearancePreviewOrientationTest.java) | Existing character creation/alignment/appearance integration; retained without edits in this pass. |
| Pre-existing; preserved | [src/test/java/com/lotrcharactercreation/faction/CharacterAlignmentViewTest.java](../src/test/java/com/lotrcharactercreation/faction/CharacterAlignmentViewTest.java) | Existing character creation/alignment/appearance integration; retained without edits in this pass. |
| This pass | [src/test/java/kome/client/KOMEHierarchyPresentationTest.java](../src/test/java/kome/client/KOMEHierarchyPresentationTest.java) | Focused HierarchyPresentation coverage and updated assertions for requested behavior. |
| Pre-existing; preserved | [src/test/java/kome/client/KOMEProgressionMapFocusTest.java](../src/test/java/kome/client/KOMEProgressionMapFocusTest.java) | Existing KOMEProgressionMapFocus regression coverage; retained. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionHierarchyCorrectionsTest.java](../src/test/java/kome/common/data/KOMEProgressionHierarchyCorrectionsTest.java) | Focused ProgressionHierarchyCorrections coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionRegionalEnemyMatrixTest.java](../src/test/java/kome/common/data/KOMEProgressionRegionalEnemyMatrixTest.java) | Focused ProgressionRegionalEnemyMatrix coverage and updated assertions for requested behavior. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionRegionalFixture.java](../src/test/java/kome/common/data/KOMEProgressionRegionalFixture.java) | Explicit native regional raid data in generation fixtures. |
| This pass | [src/test/java/kome/common/data/KOMEProgressionRulerLifecycleTest.java](../src/test/java/kome/common/data/KOMEProgressionRulerLifecycleTest.java) | Focused ProgressionRulerLifecycle coverage and updated assertions for requested behavior. |
| Pre-existing; preserved | [src/test/java/kome/common/data/KOMEProgressionUxInteractionTest.java](../src/test/java/kome/common/data/KOMEProgressionUxInteractionTest.java) | Existing KOMEProgressionUxInteraction regression coverage; retained. |
| Pre-existing; preserved | [src/test/java/kome/core/KOMEAlignmentSyncTransformerTest.java](../src/test/java/kome/core/KOMEAlignmentSyncTransformerTest.java) | Existing KOMEAlignmentSyncTransformer regression coverage; retained. |

No production resource or texture files changed in this pass. Existing crown/medallion resources are reused.
