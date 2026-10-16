# Latest gameplay acceptance — patrol repair and Master duties

Prepared **2026-10-16**. **This repaired build awaits Minecraft retesting.**

- Repair source: `f2df9ecd3bd121ad6c865450771ffc7fdcd3fe5a` (patrol checkpoint `547553a`), on branch
  `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
  The inherited uncommitted language recovery remains part of the verified source.
  HEAD alone does not include that separate wording work.
- Version **1.0.10-service-g14**, Minecraft **1.7.10**, Java 8, Forge **10.13.4.1614**,
  **LOTR v36.15**, **geckolib-unofficial-1.7.10-1.0.4**.
  Match client/server addon and dependencies when separate.
- Verified jar: `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar`,
  SHA-256 `7c29222a8edc4571547f4e519dcfee660a51d733e199a6926100eacd6710af66`. The prior g14 jar is a different build.
  This task did not install the new jar; verify the installed file before testing.
- Final automated validation: **4,152 passed, five skipped**, zero failures/errors;
  build and reobfuscation passed. [Report and duty findings](../development/FIRST_GAMEPLAY_REPAIR_REPORT.md).
  Automated success does not pass any Result below.
- Original feedback: **4 passed / 1 failed / 9 untested**; ratings fun 3/5,
  difficulty 4/5, clarity 4/5. [Original answers](archive/20261016-service-g14-first-feedback.md)
  remain in [history](PLAYTEST_HISTORY.md). The repaired build starts untested.

The repair addresses occupied patrol placement and the repeated preparation loop.
It also replaces the Master menu's misleading “details pending” text for implemented
escort/recovery/defense with actual duty instructions. The nonspecific report of
broken Master duties is still unresolved; the six conditional checks identify
which stage needs repair. No new progression mechanics were added.

## Session record

**Tester / actual play date:**

**Installed client/server jar and hash:**

**World (new disposable / disposable copy), faction, Master/Liege:**

**Singleplayer / dedicated, player count, operator status:**

**Blocking message or unavailable prerequisite:**

Bold **Yes** or **No** only in Result. Neither bolded, or explicit Unavailable,
means untested. Keep Notes brief: exact step, requested item/NPC, visible response,
repeatability and optional screenshot/log. [Return-processing rules](README.md).

## Efficient session order

**Knight session:** F01 → 08 → 09 → F02 → 10 → 12 → 13 → 14 → 11.
Stay with one Liege. Do not travel to the patrol encounter site for the control
tests. 14 uses a verified staff completion shortcut for report wording, so this
session does not claim that the protection fight passed.

**Serf session:** use a separate disposable profile and do whichever of M01–M06
the Master actually assigns, in assignment order. Leave other types untested.
Normally only three distinct duties are needed before Knight; Provisioning and
Trade Assistance are alternatives. Do not force six types onto one character or
repeatedly reset ranks. Additional types may need a later fresh profile.

### Knight setup — operator level 2

1. Use a new disposable test world/profile, or a safely stopped disposable copy
   with a valid earned Knight and current Liege. Prepare armor/food before
   protection tests; use Survival and non-peaceful combat conditions.
2. Pledge through the native faction interface to a supported playable faction,
   preferably Rohan or Gondor. Keep an existing valid current Liege. If using a
   fresh operator test character, stand within eight blocks of a living adult,
   unhired, same-faction native commander of Lord/Prince standing, clearly targeted,
   and run `/kome progression relationship force knight`.
   A native Rohirrim Marshal/Gondorian Captain is a candidate, not guaranteed
   eligible. This changes the test character's relationship/rank; it does not
   prove natural promotion or regional recommendation admission.
3. Check `/kome progression status`, `/kome progression why` and
   `/kome progression commission status`. If an earlier **staff** exercise is
   stuck, use `/kome progression commission reset`, then
   `/kome progression commission end`. Genuine active work is protected: finish
   it normally or use another disposable profile. Do not use `resetDuty`.
4. Stand near the Liege on reasonably open ground with enough room for four NPCs.
   Accept saved orders through right-click → **Quest**. Normal Talk alone does not
   accept the commission. Open the native LOTR menu with its configured Controls
   key, select **KOME Progression**, then **Service & Rank** (or **Ranks**).
5. If patrol preparation still cannot succeed, the expected recovery is a clear
   fresh-orders instruction, not flickering partial groups. Record that message
   and the location; arrange a new staff exercise explicitly. An unprepared group
   is not a passed stability test. Never edit actor data or valuable saves.

### Natural Serf setup — six duty checks are conditional

Use a separate new disposable character/world or an earned Serf copy. Pledge to
a supported native faction and enter service through an eligible ordinary civilian
Master's native offer/Accept interaction. He must be a living, adult, unhired,
same-faction civilian/farmer in faction territory; meet native friendliness/trade
requirements. A military Liege is not the ordinary Master setup.

Right-click your current Master for work, or use **Request a duty** when his menu
is open. Read the assigned duty in the book; requests choose feasible duties
automatically. No verified command selects a particular Serf duty type. Operator
`/kome progression status` and `/kome progression why` are read-only diagnostics;
`/progression get` reads your standing without operator access.

Before hand-in, note **Duties reported** in Ranks. Completion should record the
duty; the third genuine type at 150 faction alignment may instead confer Knight
and move this record into your service history. Note that recognition if it occurs.
Do not use forced completion, forced credits or `finishDuty` for M01–M06.
Unavailable duties are untested; an assigned duty failing despite its prerequisites
is a No with the exact symptom. Do not infer that all six duties failed.

## A. Essential Gameplay

### Test F01 — Stable patrol followers

**Statement:** Four temporary patrol soldiers remain available while my exercise is active.

**Setup:** Knight setup below; beside the valid current Liege, with no active genuine assignment.

**Steps:** Run `/kome progression commission start FIELD_COMMAND 2`, then `/kome progression commission complication none` before accepting through Quest. Count the soldiers for 60 seconds while remaining nearby. Record any repeated spawn/disappearance. This retests the original failed 07.

**Result:** Yes / No

**Notes:**

### Test 08 — Wait order

**Statement:** A soldier I tell to wait stays where I left him.

**Setup:** F01 stable; safe open ground, no combat.

**Steps:** Sneak-right-click one following soldier once. After the wait response, walk about ten blocks over clear ground and observe that soldier.

**Result:** Yes / No

**Notes:**

### Test 09 — Follow order

**Statement:** The waiting soldier follows me again when I speak normally.

**Setup:** The soldier from 08 is still waiting.

**Steps:** Return within interaction range; right-click him without sneaking. After the follow response, walk about ten blocks over clear ground.

**Result:** Yes / No

**Notes:**

### Test 10 — End exercise cleanup

**Statement:** Ending the exercise removes its temporary soldiers.

**Setup:** After F02, with the patrol active and all four soldiers loaded beside the Liege.

**Steps:** Run `/kome progression commission end`. Look for those four soldiers and check `/kome progression commission status` before continuing. Note any effect on the Liege or unrelated NPCs.

**Result:** Yes / No

**Notes:**

### Test M01 — Provisioning credit

**Statement:** My completed Provisioning duty is recorded as earned Master service.

**Setup:** Natural Serf setup below; Provisioning actually assigned; note the earlier Duties reported count.

**Steps:** Collect the exact requested foods and drink/vessel shown in the book. Right-click your current Master to deliver, or use Deliver provisions if his menu is open. Finish the listed quantities and check the service count/recognition. Record the requested item details and response if delivery fails.

**Result:** Yes / No

**Notes:**

### Test M02 — Trade Assistance credit

**Statement:** My completed Trade Assistance duty is recorded as earned Master service.

**Setup:** Natural Serf setup; Trade Assistance actually assigned instead of Provisioning.

**Steps:** Collect the exact listed materials, including their variants. Deliver to the current Master by right-click or Deliver materials. Finish the requirements and check the service count/recognition. Note the Master's occupation and any rejected item.

**Result:** Yes / No

**Notes:**

### Test M03 — Courier return report

**Statement:** Returning to my Master after delivering the letter records my Courier service.

**Setup:** Natural Serf setup; Courier actually assigned; collect the physical letter issued by the Master.

**Steps:** Read the letter/book and follow Show Me to the assigned correspondent. Select the assignment letter in your hand and right-click that named NPC. Return to your current Master and report (right-click or Report to Master). Check the service count/recognition. Note the exact stage if blocked.

**Result:** Yes / No

**Notes:**

### Test M04 — Local Escort report

**Statement:** Reporting after the traveler reaches the marked refuge records my Local Escort service.

**Setup:** Natural Serf setup; Local Escort actually assigned, eligible independent adult same-faction charge within 48 blocks and a verified refuge 256–3,000 blocks away.

**Steps:** Read the duty, interact with the Master if activation needs retry, and lead the bound traveler physically to the marked refuge. Keep close; normal interaction follows, sneak interaction waits. Once the traveler arrives, return to the Master and report. Check the service count/recognition.

**Result:** Yes / No

**Notes:**

### Test M05 — Property Recovery hand-in

**Statement:** Returning the assigned recovered item records my Property Recovery service.

**Setup:** Natural Serf setup; Property Recovery actually assigned.

**Steps:** Follow the search marker, find and pick up the assignment's item, then return to your current Master with that item selected in your hand and right-click. Check the service count/recognition. An ordinary duplicate item is not the assigned property.

**Result:** Yes / No

**Notes:**

### Test M06 — Local Defense report

**Statement:** Reporting the completed defense records my Local Defense service.

**Setup:** Natural Serf setup; Local Defense actually assigned, protected NPC present and both assigned attackers active.

**Steps:** Personally damage an assigned attacker, keep the protected NPC alive and defeat the assigned attackers. Return to the current Master and report. Check the service count/recognition. Record the protected NPC, personal hits and remaining targets if the objective stalls.

**Result:** Yes / No

**Notes:**

## B. GUI and Presentation — previously passing regressions

### Test 12 — Active protection orders

**Statement:** Service & Rank shows the protection objective I just accepted.

**Setup:** After patrol cleanup, beside the same Liege with no assignment.

**Steps:** Run `/kome progression commission start PROTECT_LIEGE 2`, then `/kome progression commission complication none` before selecting Quest. Accept, open KOME Progression → Service & Rank and read the active order. Check promptly; this is still a live attack.

**Result:** Yes / No

**Notes:**

### Test 13 — Show Me navigation

**Statement:** Show Me centers the map on my current protection objective.

**Setup:** The active protection order from 12; keep the Liege alive.

**Steps:** Select Show Me, observe the highlighted location and close the map promptly. Continue with 14. Do not remain in the map while attackers threaten the Liege.

**Result:** Yes / No

**Notes:**

### Test 14 — Liege named in report instructions

**Statement:** The reporting instruction identifies my current Liege by name.

**Setup:** The staff protection exercise from 12–13, still active.

**Steps:** For this GUI-only check, run `/kome progression finishDuty`, then open Service & Rank and compare the named report recipient with the issuing Liege. This staff shortcut accepts no combat or earned progression. End the exercise with `/kome progression commission end` before 11.

**Result:** Yes / No

**Notes:**

## C. Persistence and Recovery

### Test F02 — Patrol survives relogging

**Statement:** Rejoining the same world leaves my active patrol with four stable followers.

**Setup:** After 08–09, before 10; all four soldiers alive, safe and loaded.

**Steps:** Record `/kome progression commission status`, leave and rejoin the disposable world/server, and observe the patrol for another 60 seconds. Compare its count and note any duplicates. This checks relogging; a dedicated server restart remains optional extended coverage.

**Result:** Yes / No

**Notes:**

## D. Regression Testing — previously passing ordinary assignment

### Test 11 — Normal commission assignment

**Statement:** My Liege chooses a normal commission when I request new orders.

**Setup:** All exercises ended; Knight with current Liege, no active assignment, testing session false and fewer than three genuine current-faction service types.

**Steps:** Select the Liege's Quest without a commission start command. Read the automatically assigned objective; no commission-type selection menu is expected. If legitimate geography prevents an offer, record the message and mark unavailable. Leave genuine orders intact.

**Result:** Yes / No

**Notes:**

## E. Multiplayer and Edge Cases — optional

Use [extended coverage](PLAYTEST_EXTENDED.md) for dedicated server restart,
two-player permissions/ownership, migration, death and battle evidence. These
need their stated disposable environments and are not required for the short
first retest.

## Acceptance limits

All 15 Results above are blank. Old 08–14 retain their original expectations;
F01 expands the original failed 07 into sustained stability, and F02/M01–M06 are
new checks. Old passes are historical observations, not passes on this jar.

The code fix and automated patrol tests are verified; natural NPC AI/pathfinding,
the repaired runtime behavior and individual Master adventures still need a player.
Fangorn lacks a suitable native Liege; Fangorn/Half-troll Master availability is
unsupported. Missing eligible NPCs, safe geography or assignment types must be
reported as unavailable. Proposed features and royal-service redesign are outside
this checklist.
