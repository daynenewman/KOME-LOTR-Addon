# Latest gameplay acceptance - Commoner and allegiance foundation

Prepared 2026-10-17. **Partial Phase 1 checkpoint cdbc051**, plus preserved inherited
language edits and its combat-lifecycle test; HEAD alone is not the jar input.
Jar: `build/libs/KOME-LOTR-Addon-1.0.11-commoner-g15.jar` (18,640,038 bytes).
SHA-256: `16d41e488869c68fa3ef1b14f21feb9f36b19ebe1823a0a6eb2b3015afb8992f`.
Built, not installed. Use matching g15 client/server artifacts.
Minecraft 1.7.10, Forge 10.13.4.1614, Java 8, LOTR v36.15,
geckolib-unofficial-1.7.10-1.0.4. Full test/build/reobfuscation: 4,174 pass,
five skipped, zero failures/errors. These are automated results only.
[Report](../development/PROGRESSION_PHASE1_REPORT.md) / [history](PLAYTEST_HISTORY.md).

**Tester / play date:**
**Installed jar/hash:**
**Disposable world/copies, faction, NPC names:**
**Singleplayer/dedicated, player count, operator status:**

Use one fresh unaffiliated character in a disposable world for A/B/D. Pledge later
using normal native rules; choosing a birth faction is not required for these tests.
Rohan or Dorwinion offers a shared setup. Use a living adult supported civilian
in its own faction territory with native trade/friendliness eligibility.
For C, use separate disposable copies of actual g14 saves and record the old rank,
Master/duty and higher-rank state before opening the copies with g15. Do not use
valuable originals. Missing profiles/duties remain untested with a note.
For E reuse the copied Knight profile and its current Liege.

Bold only Yes or No in each Result. No new +100 birth bonus is implemented in
this checkpoint, so no question below assumes that pending behavior. Genuine
earned progression and staff exercises must be recorded separately.

## A - One new character, then native pledge

### C01 - Initial standing
After finishing creation with no birth faction, does the progression book show Commoner?
**Result:** Yes / No
**Notes:**

### C02 - Pledge does not promote
After a normal native faction pledge, does the character remain Commoner?
**Result:** Yes / No
**Notes:**

### C03 - Reload
After saving, quitting and rejoining, is that pledge retained with Commoner standing?
**Result:** Yes / No
**Notes:**

## B - Stay with one supported Master

### C04 - Native service offer
Can the pledged Commoner accept a valid civilian's Quest offer and enter Master service?
**Result:** Yes / No
**Notes:** Record faction, occupation and natural/later-spawned origin.

### C05 - Ordinary assignment
Can that Master assign a normal duty that appears in the book?
**Result:** Yes / No
**Notes:** Record the duty; do not force it with staff commands.

### C06 - Service is distinct from rank
After reporting the first ordinary duty while below three promotion credits, does the player remain Commoner?
**Result:** Yes / No
**Notes:** This does not test a forced or genuine third-duty promotion.

## C - Existing-save copies

### C07 - Starting-rank migration
Does a copied former Serf save now display Commoner after loading and another save/reload?
**Result:** Yes / No
**Notes:** A former Wanderer copy may also be checked; record which was available.

### C08 - Existing Master duty
Is the copied character's pre-existing active duty still assigned to its original Master?
**Result:** Yes / No
**Notes:** Compare the recorded Master and duty; unavailable active duties remain untested.

### C09 - Earned higher standing
Does an existing Knight, Lord or Prince retain its earned rank after loading and save/reload?
**Result:** Yes / No
**Notes:** Record every rank actually checked; do not imply unavailable ranks passed.

## D - Book and map, reusing the above characters

### C10 - Clear rank ladder
Does Service & Rank show Commoner -> Knight -> Lord -> Prince with the current canonical rank clearly identified?
**Result:** Yes / No
**Notes:** Check available Commoner and higher-rank pages; King is not a ladder rank.

### C11 - Scroll and controls
With a long Master/Liege name, can you scroll through assignment, requirements and privileges without text covering controls or borders?
**Result:** Yes / No
**Notes:** Check top, middle and bottom positions, navigation and a privilege tooltip.

### C12 - Show Me
Does Show Me still focus the current duty's relevant Master or objective on the map?
**Result:** Yes / No
**Notes:** For Recovery, the marker is approximate; record the duty tested.

## E - Existing Knight regression, same copied Knight and Liege

### C13 - Patrol stability
If a normal field-command patrol is assigned, do its soldiers remain present and follow/wait normally?
**Result:** Yes / No
**Notes:** Leave untested if a different commission is assigned; do not force a patrol.

### C14 - Normal commission reporting
Can you complete and report a normally assigned Knight commission to the current Liege?
**Result:** Yes / No
**Notes:** Record type and visible completion; this is not proof of the deferred redesign.
