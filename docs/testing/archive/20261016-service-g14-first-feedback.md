# Latest gameplay acceptance — Pass 5 and language recovery

Feedback received **2026-10-16**; actual play date not supplied.
**Player-reported results: 4 passed, 1 failed, 9 untested.**
Installed runtime jar/hash and singleplayer/dedicated environment were not supplied;
the source/build identity below describes the issued checklist, not confirmed runtime provenance.

- Source HEAD: `5091843fe0d8ae4734ce20ce244f786c2a0f1bdc`, branch
  `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
- Gameplay checkpoint: Pass 5 `4b9b92c`, compared with Pass 4 `243aa4f`.
  HEAD also contains overnight tooling. The verified source **includes uncommitted
  language recovery**: 34 production classes, 15 existing test classes and a new
  combat/death test. Checking out HEAD alone does not reproduce that wording.
- Version: **1.0.10-service-g14**. Verified jar:
  `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar` (18,637,319 bytes).
  SHA-256: `e8cc7ef961cc15c874f30d3635e7d85b5568554db6181f3da18755448eeac1ec`.
- Prior automated verification: Fast 84 passed; Full 4,148 passed, five skipped,
  zero failures/errors. [Evidence and limits](../../development/OVERNIGHT_SETUP_REPORT.md).
  No new build was run for this documentation task. These are not manual passes.

Features: Liege assignment, actual protection combat, saved counterattack,
temporary patrol followers, exercise isolation and updated objectives/navigation.
Optional missions and recovery checks are in [extended sessions](../PLAYTEST_EXTENDED.md).
The [workflow](../README.md) explains results; [history](../PLAYTEST_HISTORY.md) retains them.

## Session record

**Tester/date:** Repository owner; play date unspecified (feedback received 2026-10-16).

**Installed jar/hash (client and server if separate):** Not supplied.

**World (new disposable / disposable copy), faction and Liege:** Not supplied.

**Environment (singleplayer / dedicated), player count, operator status:** Not supplied.

**Blocking problem, if any:** Patrol soldiers repeatedly spawn and disappear (07).

Bold only Yes or No in each Result. Neither bolded, or an explicit Unavailable,
means untested. Notes can be brief. If setup is unavailable, leave dependent tests
untested and record the missing prerequisite; do not mark them No.

## Shared setup — one disposable Knight and one Liege

1. Use Minecraft **1.7.10**, Java 8, Forge **10.13.4.1614**,
   **LOTR v36.15** and **geckolib-unofficial-1.7.10-1.0.4**, with the matching g14
   addon above. Client/server must match when separate. This task did not install
   the jar. Check the actual runtime jar before testing; record any difference.
2. Use a **fresh disposable world/profile** for the operator shortcut below.
   Enable cheats/operator permission level 2. Play combat in Survival with armor,
   a normal weapon and food; peaceful mode or invulnerability defeats the test.
   Prepare equipment before accepting an exercise: attackers may arrive promptly.
3. Pledge through the existing native LOTR faction interface to a supported
   playable faction, preferably Rohan or Gondor. Find a living adult, unhired,
   same-faction native military commander recognized as Lord or Prince. A native
   Rohirrim Marshal or Gondorian Captain is a candidate; class/name alone does
   not guarantee eligibility. Prefer a naturally generated regional seat. Do not
   spawn/rename an arbitrary NPC to simulate legitimate regional admission.
4. If you already have a genuine Knight and valid current Liege on a **disposable
   copy**, keep that relationship and skip the force command. Otherwise, only on
   the fresh test profile, stand within eight blocks with the intended commander
   clearly in view and no competing nearby target, then run:

   ```text
   /kome progression relationship force knight
   /kome progression status
   /kome progression why
   /kome progression commission list
   ```

   Force changes rank/relationships and seeds test Master duties. It requires a
   matching native pledge and suitable ranked NPC; it does not make an arbitrary
   NPC valid. Confirm canonical rank `knight` and the intended Liege in status.
   This shortcut tests field mechanics, **not earned Knight promotion or normal
   recommendation/seat admission**. No verified shortcut guarantees a suitable
   commander or terrain; if missing, use another disposable setup or mark unavailable.
5. Have no active genuine assignment. The exercise command refuses to overwrite
   one; finish it normally or use a different disposable profile. Do not reset it.
   Record the book's existing **Service reported** entries before the exercises.
6. Open the native LOTR menu using its configured key (see Minecraft Controls),
   select **KOME Progression**, then **Service & Rank** (or **Ranks** outside
   Knight standing). This is the book used below. For a Liege Quest, right-click
   the Liege and select **Quest** in the native interaction menu. Do not select
   Hire or Leave. Ordinary Talk alone is not a request for orders.

**Efficient order:** 01 → 12 → 13 → 02 → 03 → 04 → 14 → 05 → 06 →
07 → 08 → 09 → 10 → 11. Tests 12–14 use the same encounter, not extra trips.
Stop after 11; it may leave a genuine commission active for your next session.

## A. Essential Gameplay

### Test 01 — Receive the protection exercise

**Statement:** My Liege gives me the saved protection orders through Quest.

**Setup:** Shared setup; stand beside the current Liege with no active assignment.

**Steps:** Run `/kome progression commission start PROTECT_LIEGE 2`, then
`/kome progression commission complication counterattack` **before** selecting
Quest. Select Quest and read the assignment response. Do 12 and 13 now.

**Result:** Yes / No

**Notes:**

### Test 02 — Protect the actual Liege

**Statement:** The attackers threaten the same Liege who issued my orders.

**Setup:** Test 01 accepted; current Liege alive and nearby.

**Steps:** Close the book, stay near the Liege and observe the approaching enemies
before engaging. Fight promptly to keep him alive; do not attack him yourself.

**Result:** Yes / No

**Notes:**

### Test 03 — Counterattack

**Statement:** A second group attacks after the first group is defeated.

**Setup:** Protection exercise with the saved counterattack; Liege survives.

**Steps:** Personally damage the assigned enemies and defeat the first group.
Stay near the Liege and watch for the second group and its warning.

**Result:** Yes / No

**Notes:**

### Test 04 — Combat completion

**Statement:** Defeating both groups changes my objective to reporting to my Liege.

**Setup:** Same protection exercise, including the second group.

**Steps:** Personally contribute substantial real damage while protecting the
Liege; defeat the remaining assigned attackers. Read the objective. Do 14 now.

**Result:** Yes / No

**Notes:**

### Test 05 — Personal report

**Statement:** Reporting to my Liege closes the completed exercise.

**Setup:** Protection objective is ready to report; current Liege present.

**Steps:** Right-click the Liege to report. If the ordinary interaction remains
open, select Quest. Read his response; use `/kome progression commission status`
to confirm no active commission remains. Do not start new orders yet.

**Result:** Yes / No

**Notes:**

### Test 06 — Exercise credit isolation

**Statement:** The exercise adds no earned entry to Service reported.

**Setup:** Test 05 reported; retain the pre-exercise book record.

**Steps:** Reopen Service & Rank and compare Service reported with the earlier
record. An absent list means no earned entries, not a missing exercise reward.

**Result:** Yes / No

**Notes:**

### Test 07 — Four temporary followers

**Statement:** Accepting Lead the Patrol gives me four faction soldiers.

**Setup:** Beside the same Liege; previous exercise closed, testing session retained.

**Steps:** Run `/kome progression commission start FIELD_COMMAND 2`, then
`/kome progression commission complication none` before selecting Quest.
Accept through Quest and count the newly entrusted soldiers near the Liege.
Stay here for the short control checks; no trip to the patrol site is required.

**Result:** Yes / **No**

**Notes:** Player report: temporary patrol soldiers repeatedly spawn and disappear instead of remaining available as four stable followers.

### Test 08 — Wait order

**Statement:** A soldier I tell to wait stays where I left him.

**Setup:** Test 07; safe open ground near the Liege, no combat.

**Steps:** Sneak-right-click one following soldier once. After the wait response,
walk about ten blocks over clear ground and observe that soldier.

**Result:** Yes / No

**Notes:**

### Test 09 — Follow order

**Statement:** The waiting soldier follows me again when I speak normally.

**Setup:** The soldier from 08 is still waiting.

**Steps:** Return within interaction range; right-click him without sneaking.
After the follow response, walk about ten blocks over clear ground.

**Result:** Yes / No

**Notes:**

### Test 10 — End exercise cleanup

**Statement:** Ending the exercise removes its temporary soldiers.

**Setup:** Test patrol active; remain beside the Liege with all four soldiers loaded.

**Steps:** Run `/kome progression commission end`. Look for the four exercise
soldiers. Use `/kome progression commission status` to check the session ended
before continuing; the Liege and unrelated NPCs are not exercise soldiers.

**Result:** Yes / No

**Notes:**

### Test 11 — Ordinary assignment

**Statement:** My Liege chooses a normal commission when I request new orders.

**Setup:** Exercise ended; Knight with current Liege and fewer than three genuine
current-faction service types. No active assignment or testing session.

**Steps:** Select the Liege's Quest without a `commission start` command. Read
the assigned objective; there should be no commission-type selection menu.
If geography prevents a safe offer, note the message and leave this untested.

**Result:** **Yes** / No

**Notes:** Player report: Normal commission assignment works.

## B. GUI and Presentation — during the same protection encounter

### Test 12 — Current orders in the book

**Statement:** Service & Rank shows the protection objective I just accepted.

**Setup:** Immediately after 01, before finishing the battle.

**Steps:** Open KOME Progression → Service & Rank. Read the active order and
compare it with the Liege's protection instructions. Close promptly during combat.

**Result:** **Yes** / No

**Notes:** Player report: Active protection orders display correctly.

### Test 13 — Show Me navigation

**Statement:** Show Me centers the map on my current protection objective.

**Setup:** Book open with the active protection objective from 12.

**Steps:** Select Show Me, observe the highlighted location, then close the map.
Do not remain in the map while enemies attack the Liege.

**Result:** **Yes** / No

**Notes:** Player report: Show Me navigation works.

### Test 14 — Return wording

**Statement:** The reporting instruction identifies my current Liege by name.

**Setup:** After 04, before personally reporting in 05.

**Steps:** Open Service & Rank and read the reporting instruction. Compare the
named recipient with the Liege who issued these orders.

**Result:** **Yes** / No

**Notes:** Player report: Reporting instructions identify the current Liege.

## Availability and boundaries

The nine unselected results remain untested; the four passes and one failure above are the returned observations. This short session does not accept natural
promotion, every mission, death/reload, GUI scales or multiplayer; use the
extended checklist for those. A technical retry/safe-site rejection is not a
successful mission: record the message and the blocked tests.

Implemented partial content: rescue has one survivor; patrol investigation is
encounter discovery; raid is a native NPC field post, not a generated fortress;
protection is an assigned attack, not an ambient assassination system. Go for Help
needs another real available regional commander. Natural terrain/pathfinding,
stealth approachability, balance, fresh seat generation and multiplayer still need
human acceptance. Fangorn lacks a suitable native Liege; Fangorn/Half-troll Master
availability is unsupported. Proposed Rank-page staff-exercise notice, expanded
clue chains, fort discovery, new Lord-to-Prince royal service, warrants, fiefs and
specialist rewards are not implemented by this pass and have no acceptance tests here.

## Additional player feedback

- Fun: **3/5**.
- Difficulty: **4/5**.
- Clarity: **4/5**.
- Several Serf ? Knight duties were reported as not fully functional. No specific
  duty, step, symptom or reproduction was supplied; this is an unresolved report,
  not six confirmed failures or a diagnosis.
- Optional X01?X30 results were not returned and remain untested.
- Results were transcribed from the owner's explicit feedback, not inferred from
  screenshots or automated tests. No repaired-build manual retest has occurred.
