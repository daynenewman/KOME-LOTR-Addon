# Progression UX and interaction pass — 2026-10-03

Working branch: `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.

The work reuses the canonical progression records, saved assignment NBT, relationship/role services, existing server-thread packet handlers, native offer overlays, native LOTR following, and native LOTR map marker rendering. Rank architecture, commission types and balance, Lord responsibilities, Lord → Prince, Fangorn requirements, and War Service were not redesigned. Nothing was committed, pushed, or merged.

## Changes made

1. **Wanderer guidance:** the server projects “Find a Master to serve” into the existing progression tracker and Ranks activity panel. Establishing a Master replaces it immediately through the existing synchronization path. No achievement flag was added.
2. **Live alignment bar:** corrected LOTR's player lookup to use the calling thread's effective side. Integrated-server mutations now reach the native server alignment broadcast instead of incorrectly finding a client-world player. Native alignment packets and ticker animation remain responsible for updates.
3. **Initial alignment faction:** character faction application sets LOTR's viewing faction and regional preference once, from the authoritative selected/pledged faction. Native persistence and later manual selection remain in control.
4. **Serf escort destination:** assignments save a named faction destination and complete on the charge's physical arrival. Raw displacement has no completion role.
5. **Escort teleportation:** temporary progression escort charges lease LOTR's `teleportAutomatically` flag. The original value is saved on the NPC and restored when the lease ends.
6. **Optional parting gift:** removed gift collection from promotion validation, the mandatory checklist, and readiness reconciliation. Actual service, trial, relationship, and alignment checks remain.
7. **Physical parting gift:** the Master issues the saved native pouch into the world once. Full inventory and failed item spawning cannot block promotion. Failed issuance remains available from the former Master.
8. **Pouch icon:** fixed the native render path. LOTR's multipass pouch has no base `getIconIndex()` icon; the progression renderer now requests the actual item render-pass icon. A regression checks the native medium pouch and its packaged texture resource.
9. **Progression drop sound:** deliberate progression item issuance uses one server-side helper, which plays `random.pop` only after a successful spawn. Courier issuance/replacements, parting gifts, and recovery/property issuance share it. Death loot and ordinary equipment drops do not.
10. **Actionable NPC recognition:** server markers now carry actionability. Current relationship NPCs receive overhead icons only for a relevant available action; objective recipients and protected actors receive their existing meaningful item markers. Native prospective relationship offers remain selective.
11. **Show Me:** added to the existing progression GUI. It opens the native LOTR map, focuses the saved objective, and outlines the existing marker. It does not create a waypoint or marker.
12. **Liege Quest:** current progression Lieges use the native-looking Quest button directly for service, reports, and Lordship Trials. Obsolete KOME service offer shells are cleared; a second native miniquest acceptance is unnecessary.
13. **Character preview:** appearance selection and skin review share corrected body/head yaw and pitch. Their cursor origin now uses the rendered head centre and preview scale, including race scale.
14. **Presentation agreement:** tracker, Ranks panel, summary, map targets, and NPC markers follow the same authoritative assignment state. Completed standing trials direct the player back to the Master even after Liege death.
15. **Persistence and cleanup:** saved escort destinations survive reactivation as well as reload; teleport overrides reconcile on entity load/update; unissued former-Master rewards close on confirmed loss without losing historical identity; mismatched pledges cannot issue or display the gift.
16. **Focused coverage:** added behavior tests for guidance, alignment, escort arrival/persistence/teleport leases, reward issuance/idempotency/cleanup, map focus, actionable markers, contextual Liege Quest actions, and cursor math.
17. **Regression and build:** requested progression groups, touched packet/GUI/marker/transformer checks, full suite, and normal Gradle build passed; exact results are below.
18. **Scope:** existing rank and gameplay architecture remains in place. The working branch is left for review and live testing.

## Escort behavior

A new standing escort chooses the nearest suitable, visible, same-faction native waypoint within 256–3,000 blocks, including named towns such as Edoras where suitable. If none is usable, it checks a loaded adult faction civilian location within 256–1,500 blocks. Only then does it reuse the existing safe faction-geography destination selector for a procedural roadside refuge, normally 500–1,500 blocks away.

Selection reuses the existing territory, biome, coordinate, and loaded-surface checks. It does not force chunks to load. The assignment stores destination X/Z, display name, and dimension before activation; binding a charge merges encounter data into that saved destination. Reopening, relogging, restarting, or reactivating the same assignment does not choose another destination. Legacy active escorts missing a destination migrate once; malformed saved destinations fail closed.

The **NPC** must reach the saved destination's 32-block radius in the correct dimension, with its player escort within 16 blocks. Player arrival alone and distance travelled are insufficient. An absent unloaded NPC is not assumed dead and is not replaced.

The native follower AI remains responsible for navigation. The saved NPC tag records the original teleport setting, then disables automatic teleport during an active escort. Success, failure, abandonment, relationship/pledge invalidation, and stale-marker reconciliation restore that setting and release the temporary hiring/role state. Unloaded charges restore when they next load. Dangerous commission/Lordship escort charges use the same lease; ordinary recruits and Lordship guards retain their normal settings.

## Liege interaction flow

Right-clicking the current progression Liege opens the existing LOTR interaction GUI. Quest is the service entry point.

| State | Quest action |
| --- | --- |
| No commission, eligible service remains | Generate/persist one eligible commission and accept it directly. If no safe commission can be generated, provide status dialogue. |
| Saved offered commission | Accept that same saved assignment; no miniquest acceptance screen. |
| Active commission | Remind the player of the current objective; the progression view exposes Show Me when a geographic marker exists. |
| Ready commission report | Validate/report through the existing service, consume required report items where applicable, and award one service record. |
| Failed commission | Run the existing failure acknowledgment/cleanup path; another click may request the next attempt. |
| Eligible for Trial of Lordship | Generate/persist and begin the existing Trial directly. |
| Active Trial | Continue/remind using the same saved Trial; expose Show Me for its location. |
| Trial ready to report | Use the existing report, survivor, alignment, and promotion checks. |
| Failed Trial | Use the existing failure/retry path. |
| Service exhausted and Trial requirements unmet | Explain that service is fulfilled and more faction standing is needed. |
| Serf with current Liege | Assign/start or continue the existing Trial of Standing through Quest, including physical delivery/activation. |

Repeated clicks reuse the current assignment token, objective, geography, and actors. Reports remain idempotent. Prospective Liege establishment keeps its existing relationship offer. Unrelated NPC miniquests and non-progression Lieges retain native behavior.

## NPC marker rules

- Prospective Master/Liege: existing player-specific native offer indicator, only when an actual eligible offer exists. No icons are added to every possible candidate.
- Current Master: active duty delivery/status, another duty when cadence permits, or completed-service promotion when alignment permits.
- Master/former Master: native pouch while an unissued, valid farewell reward is available to the matching pledge. Issuance removes the pouch marker; a Serf still eligible for promotion can retain a Master interaction icon.
- Current Serf Liege: unfinished standing Trial interaction.
- Current Knight Liege: commission issue/status/report, active Trial interaction/report, or eligible Trial availability.
- Active objective NPC: courier recipient, standing escort charge, defense beneficiary, commission/Trial charge or beneficiary.
- No progression overhead icon: unrelated NPCs, exhausted/unavailable service, Master duty cooldown with no active duty/reward, completed/failed field actors, dead actors, or inactive higher-rank progression relationships.

Historical relationship locations may remain on the map with `actionable=false`; that does not produce an overhead icon. The existing native distance, fade, speech displacement, and visibility/depth behavior is preserved. Snapshots are per-player and sent only when their signature changes, with immediate interaction updates and the existing one-second progression reconciliation cadence.

## Parting gift

The gift is an **optional bonus**, physically dropped as the native medium pouch with the existing faction-appropriate contents and color. Pickup is ordinary item pickup and never a promotion requirement.

Pending contents are saved once. The issuance flag is recorded after a successful world spawn, so repeated interactions/ticks and normal save/restart cannot mint another pouch. A failed spawn retains the pending reward; promotion still succeeds, and the former Master can issue it later. Confirmed former-Master loss clears unavailable reward presentation using the existing deceased reference while preserving the historical former-Master identity.

The earlier gift design had no item-loss replacement rule. Successfully issued pouches therefore use normal native item pickup/despawn rules; losing or failing to pick up one does not reissue it and does not affect rank. Existing courier/recovery replacement rules remain in force.

## Map integration

Show Me resolves the current server-authored marker in the player's dimension, with valid saved coordinates and an active geographic role. Relationship-only markers and the moving standing escort charge do not enable it.

The button creates a native `LOTRGuiMap`, then sets its current and previous map positions using LOTR's world-to-map scale/origin and clears pending pan movement. The focused progression marker receives a gold outline in the existing native map marker pass. Repeated use changes only the view/focus; it never mutates assignments or adds markers. Completion/failure removes geographic targets from the authoritative snapshot; session reset clears client focus.

## Alignment synchronization

The audited LOTR implementation used physical proxy side in `LOTRPlayerData.getPlayer()`. In an integrated server, that could resolve the client-world player during a server mutation and suppress the native server synchronization path. The narrow transformer switches that lookup to FML's effective calling-thread side.

Alignment gain/loss still uses LOTR's native mutation/broadcast and UUID-addressed alignment packets. The existing initialized alignment ticker consumes the updated value and animates both increases and decreases. No new alignment polling or shared progression broadcast was added. Native world-visible alignment synchronization is retained; viewing-faction selection is sent through LOTR's owner-specific update path.

Character faction application sets the native viewing faction and regional preference once after successful selection/pledge application. LOTR saves both preferences; later manual selection and relog use those native values instead of repeatedly forcing the chosen faction.

## Files changed

All paths below are relative to the repository root. “New” identifies newly added files.

| File | Purpose |
| --- | --- |
| `src/main/java/com/lotrcharactercreation/client/gui/GuiAppearanceSelection.java` | Use the race-scaled rendered head origin for cursor offsets. |
| `src/main/java/com/lotrcharactercreation/client/gui/GuiManSkinReview.java` | Use the skin-review rendered head origin. |
| `src/main/java/com/lotrcharactercreation/client/render/AppearancePreviewOrientation.java` (new) | Shared separable yaw, pitch, and head-origin math. |
| `src/main/java/com/lotrcharactercreation/client/render/AppearancePreviewRenderer.java` | Apply cursor-facing orientation. |
| `src/main/java/com/lotrcharactercreation/client/render/ManSkinReviewPreviewRenderer.java` | Reuse the corrected orientation in skin review. |
| `src/main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java` | Apply the selected faction's native default view once. |
| `src/main/java/kome/client/KOMELiegeQuestButtonOverlay.java` | Open the current Liege's progression status/Show Me surface after Quest. |
| `src/main/java/kome/client/KOMEProgressionMapFocus.java` (new) | Validate geographic targets and open/focus the native map. |
| `src/main/java/kome/client/KOMEVisualMarkerClientState.java` | Clear map focus with the marker session. |
| `src/main/java/kome/client/KOMEVisualRenderBridge.java` | Respect actionable icons, use native pouch render-pass icons, outline map focus. |
| `src/main/java/kome/client/gui/KOMEGuiProgression.java` | Add contextual Show Me to the existing UI. |
| `src/main/java/kome/client/gui/KOMEGuiSerfdomMaster.java` | Label promotion independently of gift acceptance. |
| `src/main/java/kome/common/data/KOMECourierService.java` | Route physical courier issuance through the progression drop helper. |
| `src/main/java/kome/common/data/KOMEEvents.java` | Reconcile gift/marker presentation promptly and route Liege clicks to Quest GUI. |
| `src/main/java/kome/common/data/KOMEKnightCommissionService.java` | Lease/restore escort teleport and centralize deliberate property drops. |
| `src/main/java/kome/common/data/KOMELiegeProgressionInteraction.java` (new) | Contextual authoritative current-Liege Quest dispatch. |
| `src/main/java/kome/common/data/KOMELordshipTrialService.java` | Scope teleport lease to the protected charge; restore through cleanup. |
| `src/main/java/kome/common/data/KOMEPartingGiftService.java` | Persist/issue the optional physical pouch once and reconcile former-Master availability. |
| `src/main/java/kome/common/data/KOMEProgressionEscortFollowing.java` (new) | Reversible saved lease of native follower teleport behavior. |
| `src/main/java/kome/common/data/KOMEProgressionItemDrops.java` (new) | Narrow server-only successful-spawn/pop-sound helper. |
| `src/main/java/kome/common/data/KOMEProgressionNpcInteractionService.java` | Promote independently of gift collection and support former-Master issuance. |
| `src/main/java/kome/common/data/KOMEProgressionOfferBridge.java` | Current Liege eligibility without a second service offer; retain prospective/native offers. |
| `src/main/java/kome/common/data/KOMEProgressionRankSummary.java` | Actual mandatory requirements, Wanderer guidance, destination/promotion activity text. |
| `src/main/java/kome/common/data/KOMEProgressionSummary.java` | Destination-oriented escort and gift-independent promotion wording. |
| `src/main/java/kome/common/data/KOMEProgressionTrackerSnapshot.java` | Wanderer objective, saved escort destination, completed-trial promotion guidance. |
| `src/main/java/kome/common/data/KOMESerfKnightEscortService.java` | Saved destination selection, physical charge arrival, and follower cleanup. |
| `src/main/java/kome/common/data/KOMESerfKnightProgression.java` | Gift-independent readiness, destination-preserving reactivation, former-Master reward loss. |
| `src/main/java/kome/common/data/KOMESerfKnightRecoveryService.java` | Use centralized deliberate item spawn/sound for recovery issuance. |
| `src/main/java/kome/common/data/KOMESerfKnightService.java` | Remove gift promotion gate, prepare escort geography, align dialogue and death reconciliation. |
| `src/main/java/kome/common/data/KOMESerfdomMasterService.java` | Physical optional reward and inventory-independent promotion. |
| `src/main/java/kome/common/data/KOMEVisualLocationService.java` | Authoritative actionable/actor/destination markers and optional reward cleanup. |
| `src/main/java/kome/common/data/KOMEVisualMarker.java` | Escort/defense roles and actionability in the existing projection. |
| `src/main/java/kome/common/network/KOMEPacketRelationshipAction.java` | Route current Liege Quest to direct service/standing Trial. |
| `src/main/java/kome/common/network/KOMEPacketSerfdomMasterAction.java` | Project gift-independent promotion availability. |
| `src/main/java/kome/common/network/KOMEPacketVisualMarkers.java` | Carry authoritative actionability in the existing marker packet. |
| `src/main/java/kome/core/KOMEAlignmentSyncTransformer.java` (new) | Correct native player lookup side without replacing native synchronization. |
| `src/main/java/kome/core/KOMECorePlugin.java` | Register the narrow alignment transformer alongside existing transforms. |
| `src/main/java/kome/core/KOMEVisualLocationTransformer.java` | Native relationship renderer requests actual item render-pass icons. |
| `src/test/java/com/lotrcharactercreation/client/render/AppearancePreviewOrientationTest.java` (new) | Cursor-axis, yaw relationship, and head-origin regressions. |
| `src/test/java/com/lotrcharactercreation/faction/CharacterAlignmentViewTest.java` (new) | Selected default, initialized ticker gain/loss, native save/reload/manual choice. |
| `src/test/java/kome/client/KOMEProgressionMapFocusTest.java` (new) | Valid targets, repeated use, native camera fields, and reset. |
| `src/test/java/kome/client/KOMEVisualRenderBridgeTest.java` | Native medium pouch resource/render pass and inactive overhead suppression. |
| `src/test/java/kome/common/data/KOMECanonicalPlayerRankTest.java` | Preserve interaction ordering while using current-Liege Quest GUI. |
| `src/test/java/kome/common/data/KOMECourierDispatchDropTest.java` | Verify centralized courier pop sound and retained handoff sound. |
| `src/test/java/kome/common/data/KOMEKnightCommissionInteractionTest.java` | Direct Quest acceptance/report and assignment idempotency. |
| `src/test/java/kome/common/data/KOMELordshipTrialIntegrationTest.java` | Direct Quest Trial start/report while retaining force and promotion checks. |
| `src/test/java/kome/common/data/KOMEProgressionFollowupTest.java` | Full-inventory physical gift, issuance/reload, and independent owners. |
| `src/test/java/kome/common/data/KOMEProgressionHardeningServiceTest.java` | Repeated Quest reports/reconnect cannot duplicate credit. |
| `src/test/java/kome/common/data/KOMEProgressionOfferBridgeTest.java` | Prospective offer preservation with current-Liege Quest ownership. |
| `src/test/java/kome/common/data/KOMEProgressionRankSummaryTest.java` | Mandatory checklist excludes the optional gift. |
| `src/test/java/kome/common/data/KOMEProgressionUxInteractionTest.java` (new) | Thirteen focused state, physical interaction, persistence, cleanup, and icon regressions. |
| `src/test/java/kome/common/data/KOMESerfKnightIntegratedJourneyTest.java` | Full standing journey promotes without claiming a gift. |
| `src/test/java/kome/common/data/KOMESerfKnightProgressionTest.java` | Gift-independent readiness and physical destination completion. |
| `src/test/java/kome/common/data/KOMESerfKnightStabilizationTest.java` | Saved escort geography and teleport restoration across live cleanup/reload paths. |
| `src/test/java/kome/core/KOMEAlignmentSyncTransformerTest.java` (new) | Actual native bytecode side correction, retained broadcast, idempotency. |
| `src/test/java/kome/core/KOMEVisualLocationTransformerTest.java` | Native clone preservation and render-pass lookup regression. |
| `src/test/java/kome/core/KOMEWaypointTransformerTest.java` | Exact shared transformer registration including the new transform. |
| `src/test/java/kome/integration/CharacterCreationIsolationTest.java` | Preserve player/config isolation; narrowly allow the saved NPC teleport lease. |
| `docs/PROGRESSION_UX_PASS_20261003.md` (new) | This review and live-testing report. |

Inventory: **38 production files, 20 test files, and this report**.

## Tests

Final focused regression invocation used package-qualified filters to avoid Windows wildcard filename expansion:

```text
gradle --offline test
  --tests 'kome.*Progression*' --tests 'kome.*Serf*'
  --tests 'kome.*KnightCommission*' --tests 'kome.*LordshipTrial*'
  --tests 'kome.*Visual*' --tests 'kome.*LiegeQuest*'
  --tests 'kome.*Alignment*' --tests 'com.*Alignment*'
  --tests 'com.*AppearancePreviewOrientation*'
  --tests 'kome.common.network.*'
  --tests 'kome.*CanonicalPlayerRank*' --tests 'kome.*CourierDispatchDrop*'
  --tests 'kome.*WaypointTransformer*'
  --tests 'kome.integration.CharacterCreationIsolationTest'
```

Result: **672 tests discovered; 669 passed, 3 skipped, 0 failed/errors. BUILD SUCCESSFUL in 14s.**

The final full-suite results include these requested groups (groups overlap):

| Group | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| Serf progression/duties | 104 | 0 | 0 |
| Knight Commission | 47 | 0 | 0 |
| Lordship Trial | 83 | 0 | 0 |
| Progression hardening | 74 | 0 | 0 |
| Packet/network tests | 137 | 0 | 0 |
| Visual marker/render/transformer tests | 18 | 0 | 0 |
| Liege Quest GUI tests | 3 | 0 | 0 |
| Map focus tests | 2 | 0 | 0 |
| Alignment tests | 11 | 0 | 0 |
| Preview orientation tests | 2 | 0 | 0 |
| Focused UX interaction tests | 13 | 0 | 0 |

Final full invocation: **`gradle --offline test build --console=plain`**, Gradle 8.5, Temurin Java 8.0.452, existing offline dependency cache.

Full suite: **1,910 tests discovered in 222 classes; 1,905 passed, 5 skipped, 0 failed/errors.**

Skipped tests are existing environment assumptions: two symbolic-link tests where Windows link creation is unavailable, and three checks requiring separately configured original/build LOTR validation jars. Tests using the actual LOTR dependency classes and shared transformer hooks passed. No test was disabled or weakened to suppress a failure; obsolete gift/acceptance/source-location expectations were replaced with the requested contracts while retaining authority, cleanup, and isolation checks.

The complete suite also ran existing public waypoint, campaign/recruitment, civilian muster, tile/geography, networking, and transformer regressions.

## Build

**BUILD SUCCESSFUL in 27s; 18 actionable tasks, 5 executed and 13 up-to-date.** Normal assembly, source JAR, reobfuscation, check, and build tasks completed.

Review artifact: `build/libs/KOME-LOTR-Addon-1.0.8.jar`.
Additional artifacts: `KOME-LOTR-Addon-1.0.8-dev.jar`, `kome-1.0.8-sources.jar`.

`git diff --check` passed. Temporary bytecode dumps and diagnostic test-filter files were removed.

## Remaining live-testing concerns

Minecraft was not launched. Automated checks do not prove these visual/runtime details:

- Native NPC pathfinding, terrain crossings, the suitability of selected waypoints/refuges, and the practical distance/radius feel.
- Escort separation and recovery feel with automatic teleport disabled, including real chunk unload/reload and ordinary recruits following beside an escort.
- LOTR map camera centring/interpolation, focused-marker outline clarity, and UI placement on different resolutions.
- NPC icon size, visibility/fade, speech overlap, and simultaneous native miniquest/progression indicators.
- Actual GPU pouch texture rendering; tests verify native item metadata, registered render-pass selection, packaged resource availability, and injected bytecode.
- Cursor-follow visual feel across all race/sex models; tests verify signs, relative yaw, and scaled head origin.
- Integrated and dedicated multiplayer alignment gain/loss while the HUD is already open, initial faction selection, and later manual bar selection across relog.
- Physical gift pickup with a full inventory, normal native despawn, and repeated interaction/restart in an actual world.

Use the same new build on client and server for live testing: the existing visual-marker packet now includes actionability. Packet IDs and other integrated registrations were preserved.
