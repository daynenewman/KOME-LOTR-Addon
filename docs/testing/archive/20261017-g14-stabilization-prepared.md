# Latest gameplay acceptance - Master access and Recovery

Prepared 2026-10-17 after book checkpoint `be12f0f`, Recovery `cd07aef` and Master access `1ca850d`.
Final tested source: **39f19a9** plus preserved uncommitted language recovery and
its combat-lifecycle test; HEAD alone does not reproduce the jar.
Jar: `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar` (18,639,829 bytes).
SHA-256: `64a9d95c8b89a376d899c0fb77b39e2cc11b7a5598630dbccb2b02d5d66b7dd3`.
Built, not installed. Match this exact hash when preparing client/server testing.
Minecraft 1.7.10, Forge 10.13.4.1614, Java 8, LOTR v36.15,
geckolib-unofficial-1.7.10-1.0.4; addon 1.0.10-service-g14 on matching client/server.
Full `test build reobfJar`: **4,169 passed, five skipped**, no failures/errors.
No Minecraft acceptance is inferred from automated results.
[Report](../../development/PROGRESSION_STABILIZATION_REPORT.md) / [history](../PLAYTEST_HISTORY.md).

**Tester / play date:**
**Installed jar/hash:**
**Disposable world/copy, faction, NPC names:**
**Singleplayer/dedicated, player count, operator status:**

Use disposable profiles/worlds. Bold only Yes or No in Result; leave unavailable
setups unselected and explain in Notes. Record exact failed steps and messages.
Use a pledged Serf in faction territory. Native trade/alignment rules still apply.
Ordinary combat soldiers, Fangorn and Half-troll Master roles are unsupported.
Use the duty the Master assigns normally; no staff-forced duty counts as earned service.

## A - Recruitment (one faction/profile, then optional alternate factions)

### R01 - Orc or Uruk trader
A living adult, friendly, trade-eligible same-faction civilian trader with a service
indicator has a usable Quest interaction that opens the service offer.
**Result:** Yes / No
**Notes:**

### R02 - Elf smith
A friendly, trade-eligible same-faction Elf smith with a service offer has a usable
Quest interaction alongside the ordinary native controls.
**Result:** Yes / No
**Notes:**

### R03 - Native resident and later spawn
Supported same-faction civilians at a native home and later-spawned supported
civilians use the same Master admission rules in faction territory.
**Result:** Yes / No
**Notes:** Record faction, occupation and origins; unavailable origins remain untested.

### R04 - Dorwinion regression
Accepting a valid Dorwinion Master offer still establishes service and permits a normal duty.
**Result:** Yes / No
**Notes:**

## B - Recovery (stay with one Master until R09)

For R05-R08, wait for a normal Property Recovery duty. Leave these untested if another
duty is assigned. Show Me marks an approximate search area; search within 40 blocks.

### R05 - Physical item
The assigned Recovery item exists on accessible ground within the marked search area.
**Result:** Yes / No
**Notes:** Describe terrain, item and map location if missing.

### R06 - Full inventory
With a full inventory, touching the item leaves the search objective active until the
item actually enters your inventory after you make room.
**Result:** Yes / No
**Notes:**

### R07 - Reload
Saving/reloading during the search preserves the duty and its single assigned item.
**Result:** Yes / No
**Notes:**

### R08 - Correct handover
Holding the recovered item and returning to its issuing Master completes the duty once.
**Result:** Yes / No
**Notes:**

### R09 - Leave during Recovery
On a separate disposable Serf profile with unfinished Recovery, leaving Master service
removes that old duty before you enter another Master's service.
**Result:** Yes / No
**Notes:** Use the Ranks-page Leave Master service control; do not erase the world.

## C - Relationship persistence

### R10 - New service survives reload
After R09, joining another eligible Master and reloading does not restore the old Recovery duty.
**Result:** Yes / No
**Notes:**

## D - Protected Knight regressions (separate Knight profile)

Use an already-earned disposable Knight with a current Liege. For an operator-only
exercise profile, pledge normally, target a living adult unhired same-faction native
Lord/Prince commander within eight blocks, and run `/kome progression relationship force knight`.
This changes the disposable character and proves no earned promotion. Stand near the
current Liege; `/kome progression commission start protect_liege 1` prepares a staff
exercise, then accept through Quest. Genuine active work is protected: finish it or
use another profile. `/kome progression commission reset` and then `end` clean staff
work only. Use a non-peaceful difficulty and enough open ground for four followers.
Run L11 on an earned profile outside the staff testing session. Exercise outcomes do
not prove earned progression. [Optional setup details](../PLAYTEST_EXTENDED.md).

### F01 - Patrol stability
An accepted protection exercise keeps its prepared patrol soldiers present without repeated spawning/disappearing.
**Result:** Yes / No
**Notes:**

### L11 - Normal commission assignment
The current Liege's Quest interaction still issues one normal saved commission.
**Result:** Yes / No
**Notes:**

## E - Book readability (reuse the Serf and Knight profiles above)

### B01 - Clear current work
At Serf and Knight standing, the Ranks / Service & Rank page shows one current
objective, separate promotion requirements and privileges, without repeating a faction summary.
**Result:** Yes / No
**Notes:** Check idle service and an active duty/commission; unavailable states remain untested.

### B02 - Scroll, names and controls
With a long NPC name/objective, scrolling to the top, middle and bottom keeps text
inside the parchment content area and clear of Show Me / Leave service controls.
**Result:** Yes / No
**Notes:** Check privilege tooltips, tabs and Show Me still respond; include GUI scale if a problem appears.

## Optional deeper coverage

[Extended checks](../PLAYTEST_EXTENDED.md) retain navigation, Show Me, promotion,
persistence, multiplayer and other mission coverage; unavailable setups are untested.
