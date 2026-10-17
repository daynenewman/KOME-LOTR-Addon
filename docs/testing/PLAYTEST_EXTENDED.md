# Optional gameplay acceptance sessions

Updated **2026-10-17** for stabilization source **39f19a9** plus preserved inherited
language recovery. All 30 optional answers remain untested. Earlier build details
are retained in [history](PLAYTEST_HISTORY.md) and [the prior repair report](../development/FIRST_GAMEPLAY_REPAIR_REPORT.md).
Use the exact jar/hash, native dependencies and environment in
[the latest essential checklist](PLAYTEST_LATEST.md). New jar SHA-256:
`64a9d95c8b89a376d899c0fb77b39e2cc11b7a5598630dbccb2b02d5d66b7dd3`.
Full verification: **4,169 passed, five skipped**, zero failures/errors; build and
reobfuscation passed. This is automated evidence, not manual acceptance.

Run only the sessions relevant to your time and available setup. This is a menu
of optional coverage, not a requirement to perform all 30 tests after every pass.
Follow [result and return rules](README.md); retain results in [history](PLAYTEST_HISTORY.md).

**Tester/date:**

**Installed client/server jar/hash:**

**World (new disposable / older disposable copy), faction and Liege:**

**Environment, player count, operator status:**

**Chosen tests and unavailable prerequisites:**

## Shared setups and efficient session choices

- **S — Staff field session:** use the essential checklist's disposable Knight,
  native pledge, valid current Liege and operator level 2. Begin each exercise
  within eight blocks of that Liege with no active genuine work. Run the specific
  `start` command below, then `/kome progression commission complication none`
  before accepting through the Liege's Quest, except for NATIVE_INVASION, which
  has no optional counterattack. Follow the book/Show Me to the actual site.
  Use `/kome progression commission status` for diagnostics and
  `/kome progression commission reset` **between staff exercises only**;
  `/kome progression commission end` when finished. Neither command replaces
  genuine work. A refused start changes no state; record the prerequisite and
  leave dependent tests untested. Do not use `finishDuty` to claim real combat.
- **N — Natural progression:** a different disposable character/world or an
  already-earned disposable copy with native pledge and genuine Master/Liege
  relationships. Never use forced rank, forced relationship, forced finish or
  staff exercise reports as earned evidence. For Knight service, end any testing
  session first and request automatic orders from the current Liege's Quest.
  Normal new Liege admission requires the earned recommendation and recognized
  regional seat. `/progression recommendation` recovers an already-earned letter
  if eligible; it does not manufacture one. `/progression get` reads your rank.
- **O — Older-world migration:** make a separate copy while the old runtime is
  fully stopped, retain an untouched backup, record its old jar/version and state,
  then load **only the copy** with the verified new jar. The source backup script
  excludes worlds and cannot substitute for this world copy. No save fixtures are
  created or modified by this documentation task.
- **M — Multiplayer:** a disposable dedicated server, matching jars, two actual
  connected clients (one operator and one nonoperator). Reuse S or N as specified.
  Natural battles additionally require established companies, legitimate Crown/
  declared-war authority, suitable tactical setup and an eligible real ordinary
  battle. See the [battle/Join Battle route](../routing/systems/conflict-tactical/README.md).
  No verified command can fake that acceptance state. Mark unavailable if it
  cannot be reached; do not relax political permissions or admin-end a battle.

For one longer session, choose one S mission from A, do X10–X13 during it, then
X14–X15 last because death interrupts it. X16 needs a separate report-ready
attempt. Keep N promotion, O migration and M battles as separate sessions; do not
repeatedly change one character's rank to simulate earned progress. X21 is the
final step of the N journey; run X22 first if its low-alignment condition occurs.

## A. Essential Gameplay — optional mission depth

### Test X01 — Rescue arrival

**Statement:** Bringing the rescued survivor to the marked refuge makes the rescue ready to report.

**Setup:** S; `/kome progression commission start RESCUE_PATROL 2`; safe refuge available.

**Steps:** Accept, reach the survivor, personally fight the captors and keep him
alive. Follow the updated refuge marker and physically lead him there. Observe
the objective when he reaches it; your arrival alone is not survivor arrival.

**Result:** Yes / No

**Notes:**

### Test X02 — Reinforcements at the second commander

**Statement:** The named second commander supplies four soldiers when I ask for help.

**Setup:** S; `/kome progression commission start GO_FOR_HELP 2`. Requires another
real loaded same-faction regional commander, 128–3,000 blocks from the refuge;
the command refuses when no valid contact is available.

**Steps:** Accept, reach and speak to the threatened refuge's beneficiary, then
travel to the named commander and interact. Count the newly supplied soldiers.

**Result:** Yes / No

**Notes:**

### Test X03 — Physical reinforcement return

**Statement:** Returning with a surviving reinforcement starts the refuge defense.

**Setup:** Continue X02 with at least one of its soldiers alive.

**Steps:** Lead the soldiers back along a traversable route. Observe the refuge
as an actual soldier arrives with you; do not teleport the followers into place.

**Result:** Yes / No

**Notes:**

### Test X04 — Raid withdrawal

**Statement:** Leaving the enemy post with its recovered property makes the raid ready to report.

**Setup:** S; `/kome progression commission start RAID_CAMP 3`; eligible hostile territory.

**Steps:** Accept, reach the marked native officer/guard post, personally fight
and recover the assigned property. Carry it more than 128 blocks from the post,
then read the objective. This is a field post, not fortress conquest.

**Result:** Yes / No

**Notes:**

### Test X05 — Steal the actual dispatch

**Statement:** An unseen theft from the assigned officer gives me the sealed dispatch.

**Setup:** S; `/kome progression commission start WAR_PLANS 3`; eligible hostile post.

**Steps:** Accept and approach cautiously. Crouch, keep the selected hand empty,
approach behind the marked officer outside the officer's and guards' view, and
right-click within three blocks. Observe the item received. A detected attempt
does not demonstrate the successful path; arrange a new exercise for another try.

**Result:** Yes / No

**Notes:**

### Test X06 — Visible approach alarm

**Statement:** Approaching the war-plans guards in their view alerts them to me.

**Setup:** S; a fresh WAR_PLANS exercise, separate from the successful theft attempt.

**Steps:** Approach the assigned post openly in Survival where a guard faces you.
Observe pursuit and the changed objective. Do not use this attempt for X05.

**Result:** Yes / No

**Notes:**

### Test X07 — Native invasion waves

**Statement:** Repel an Invasion produces a native invasion with real attackers.

**Setup:** S; `/kome progression commission start NATIVE_INVASION 3`; legitimate
native invasion geography/roster available. No extra complication command.

**Steps:** Accept via Quest, reach the assigned area and observe its invasion
controller/wave presentation and real attackers. Participate rather than spectate.
This checks activation; it does not accept victory, rewards or reload by itself.

**Result:** Yes / No

**Notes:**

### Test X08 — Relief needs defense

**Statement:** Delivering all requested relief supplies leaves defense as an unfinished objective.

**Setup:** S; `/kome progression commission start RELIEF 2`; eligible refuge and
the exact food/items and quantities listed in the orders.

**Steps:** Accept, reach the beneficiary and deliver the requested items through
the native interaction, holding the requested stack. Before personally contributing
combat damage, read the remaining objective. Continue immediately with X09.

**Result:** Yes / No

**Notes:**

### Test X09 — Relief combat completion

**Statement:** Completing the defense after delivery makes the relief mission ready to report.

**Setup:** X08 supplies delivered; beneficiary alive and assigned enemies present.

**Steps:** Personally damage the assigned enemies, protect the beneficiary and
resolve the attack. Read the updated objective; allies fighting alone are insufficient.

**Result:** Yes / No

**Notes:**

## B. GUI and Presentation

### Test X10 — Book layout at my GUI scale

**Statement:** The active orders remain readable at my chosen GUI scale.

**Setup:** S or N with active orders; open KOME Progression → Service & Rank.

**Steps:** At one recorded GUI scale/window size, read and scroll the full order;
check for clipped or overlapping text/buttons. Record the scale. Repeat as a
separate dated attempt for other supported scales rather than assuming they pass.

**Result:** Yes / No

**Notes:**

### Test X11 — Tracker phase change

**Statement:** The on-screen tracker changes to the new task when my mission advances.

**Setup:** X01 combat-to-refuge or X05 theft-to-escape transition; tracker visible.

**Steps:** Compare the tracker before and after that single transition. Record
which mission/transition you observed and any stale wording.

**Result:** Yes / No

**Notes:**

## C. Persistence and Recovery

### Test X12 — Offered orders survive relogging

**Statement:** Relogging preserves the same offered commission.

**Setup:** S, preferably PROTECT_LIEGE 2, saved by `start` but not yet accepted.

**Steps:** Record `/kome progression commission status`, leave and rejoin the
same disposable world/server, then compare the saved order's type, tier and token
in status before accepting it. Record differences in Notes.

**Result:** Yes / No

**Notes:**

### Test X13 — Active followers survive a clean restart once

**Statement:** A clean world/server restart restores one copy of each active patrol soldier.

**Setup:** S FIELD_COMMAND 2 accepted; four soldiers alive and loaded near the Liege.

**Steps:** Record their number and status actor IDs. Save, fully stop the
disposable world/server, restart and rejoin. Count and compare the same soldiers.
For dedicated coverage, record that the server process itself restarted.

**Result:** Yes / No

**Notes:**

### Test X14 — Active field death

**Statement:** Dying during an active field commission changes that attempt to failed.

**Setup:** S FIELD_COMMAND 2, tier above zero and active, not ready to report.

**Steps:** On this disposable character only, die through ordinary Survival
gameplay, respawn and inspect the objective or commission status before requesting
fresh orders. Do not modify death settings or valuable saves for this check.

**Result:** Yes / No

**Notes:**

### Test X15 — Earlier service survives death

**Statement:** Earlier earned Service reported entries remain after my field death.

**Setup:** Disposable copy with at least one genuine earlier report, recorded
before a staff exercise. Requires that history; an empty-history profile cannot test it.

**Steps:** Perform X14, reopen Service & Rank and compare the earlier earned entries.

**Result:** Yes / No

**Notes:**

### Test X16 — Report-ready evidence survives death

**Statement:** A completed commission remains ready to report after I die.

**Setup:** Separate S or N protection attempt completed, ready to report but unreported.

**Steps:** Record ready status, die through ordinary Survival gameplay on the
disposable character, respawn and compare the reporting objective before interacting.

**Result:** Yes / No

**Notes:**

### Test X17 — Lost dispatch recovery

**Statement:** Reporting a lost dispatch produces a failed attempt with instructions for fresh orders.

**Setup:** Separate WAR_PLANS attempt: dispatch stolen, still active before escape.

**Steps:** Leave the dispatch behind on the disposable test route and move away
without it. Return to the current Liege, hold Sneak and use his Quest to acknowledge
the loss, then inspect the failure message/status before another interaction clears it.
Do not merely put it in a different inventory slot; that is not a lost dispatch.

**Result:** Yes / No

**Notes:**

### Test X18 — Legacy relief requirements

**Statement:** An old delivery-only relief commission still completes through its saved delivery requirements.

**Setup:** O, with a genuine pre-Pass-5 relief assignment whose saved tier is zero.
Requires an actual older fixture; do not edit NBT to create it.

**Steps:** Load the copy with the verified jar, inspect the saved order and deliver
its requested supplies. Check readiness without inventing a new combat requirement.

**Result:** Yes / No

**Notes:**

## D. Regression Testing — natural progression or older copies

### Test X19 — Master grants Knight standing

**Statement:** Reporting my third distinct ordinary Master duty earns Knight standing.

**Setup:** N Serf, legitimate current Master, native pledge, at least 150 faction
alignment, two genuine completed duty types and a third distinct duty ready to report.
Reach this state through ordinary service; no force/finish commands.

**Steps:** Personally report the third duty to that Master and inspect your rank
with the book or `/progression get`. Record your faction's cultural rank title.

**Result:** Yes / No

**Notes:**

### Test X20 — Held property handover

**Statement:** My Liege receives the recovered stolen property from my hand.

**Setup:** N automatically assigned STOLEN_GOODS, actual raid fought and assigned
property recovered. Staff exercises bypass earned handover and cannot test this.

**Steps:** Return to the issuer with the recovered property selected in your hand
and right-click to report. Observe that the bound property is handed over.

**Result:** Yes / No

**Notes:**

### Test X21 — Third distinct commission grants Lord standing

**Statement:** Reporting my third distinct genuine commission to my current Liege earns Lord standing.

**Setup:** N Knight, at least 2,000 current-pledge alignment, two genuine reported
types and a third different automatically assigned commission ready to report.
Testing session ended. Normal recommendation/seat admission must have established
the Liege relationship; forced exercises do not qualify.

**Steps:** Personally report to the current Liege and inspect standing. There is
no new mandatory promotion trial or second superior required for this recognition.

**Result:** Yes / No

**Notes:**

### Test X22 — Insufficient alignment preserves Knight standing

**Statement:** My Liege keeps me at Knight standing when my completed service lacks 2,000 faction alignment.

**Setup:** N with three genuine distinct current-faction reports, below 2,000
alignment, testing session ended; only if this state naturally occurs in the test copy.

**Steps:** Speak to the current Liege and read his recognition response and rank.
Do this before X21; later earn the required alignment normally and return.
No verified alignment-setting command is prescribed here.

**Result:** Yes / No

**Notes:**

### Test X23 — Duplicate interaction credit

**Statement:** Speaking again after a report leaves the earned service entries unchanged.

**Setup:** N, a genuine commission just reported; record Service reported entries.

**Steps:** Interact again with the same Liege, then compare those entries.
Receiving new orders is not a second report of the previous deed.

**Result:** Yes / No

**Notes:**

### Test X24 — Native regional replacement admission

**Statement:** The legitimate replacement at a recognized regional seat accepts my earned recommendation.

**Setup:** O on an already-vacated, proven native seat with its normal replacement
pending; eligible native pledge, earned Knight recommendation and no current Liege.
Requires a real fixture; do not kill valuable NPCs or manufacture seat tags.

**Steps:** Let the normal replacement load/spawn, approach it and use the ordinary
Quest admission. Verify the replacement becomes your current Liege. An unproven
old placement is deliberately ineligible, so record that prerequisite as unavailable.

**Result:** Yes / No

**Notes:**

### Test X25 — Temporary patrol does not enlarge a company

**Statement:** A field exercise leaves my existing strategic company's unit count unchanged.

**Setup:** Disposable copy with an existing company and operator permission;
record its unit count through the existing company interface before S FIELD_COMMAND.

**Steps:** Accept the exercise, inspect that same company's count, end the exercise
and inspect again. Do not recruit, transfer units or issue company orders during it.

**Result:** Yes / No

**Notes:**

## E. Multiplayer and Edge Cases

### Test X26 — Nonoperator command denial

**Statement:** A nonoperator is refused permission to start a staff commission.

**Setup:** M; nonoperator connected with a valid Knight/Liege setup to isolate permission.

**Steps:** As that player, run `/kome progression commission start PROTECT_LIEGE 2`
and read the permission response. Do not grant that player operator access to continue.

**Result:** Yes / No

**Notes:**

### Test X27 — Dispatch belongs to its owner

**Statement:** The other player cannot pick up my dropped sealed dispatch.

**Setup:** M; owner successfully stole the assigned WAR_PLANS dispatch; second
player connected, inventory space available, both near the dropped item.

**Steps:** Owner drops the dispatch safely on the disposable route. Have the other
player attempt pickup before the owner recovers it. Do not destroy it for this check.

**Result:** Yes / No

**Notes:**

### Test X28 — Spectating earns no battle service

**Statement:** Watching an ordinary battle leaves my commission unfinished.

**Setup:** M + N active genuine BORDER_INCURSION, SETTLEMENT_DEFENSE or FIELD_COMMAND;
real battle on its assigned tile. Observe from safety without formal deployment
or personal combat, with no unrelated field objective completed.

**Steps:** Let that legitimate battle resolve normally and inspect your commission.
Do not use forced staff objectives or administrative resolution as substitutes.

**Result:** Yes / No

**Notes:**

### Test X29 — Genuine battle alternative

**Statement:** My qualifying battle participation makes the assigned commission ready to report.

**Setup:** M + N compatible active genuine commission from X28, assigned tile,
matching faction, authorized real ordinary battle and formal Join Battle deployment.
Requires real campaign enemies and a successful normal authoritative outcome.

**Steps:** Use the existing Join Battle interface, personally damage actual enemy
campaign combatants who die, survive through the successful normal resolution and
inspect the commission after normal egress. Administrative closure, exercise
targets, mere travel or allied/support-only participation do not qualify.

**Result:** Yes / No

**Notes:**

### Test X30 — Protect genuine active work

**Statement:** The staff reset command leaves my genuine active commission intact.

**Setup:** Operator on disposable N copy, genuine active automatically assigned
commission; record its type/token with commission status.

**Steps:** Run `/kome progression commission reset`, read the refusal and compare
the active commission in status. Do not run the broader legacy `resetDuty` command.

**Result:** Yes / No

**Notes:**

## Known limits

Human acceptance of these paths is pending, even where service/native fixtures
pass. Natural pathfinding, terrain, lag, native stealth sight and encounter balance
may make some sessions difficult. Rescue currently has one survivor; raids are NPC
field posts, not discovered fortresses. Go for Help requires an available second
real regional commander. Unsupported faction/seat availability and missing older
fixtures are unavailable tests, not failures. Broader support/allied/fortified
battle substitution is not implemented. No tests here claim the proposed staff
exercise badge, expanded clue chains, fortress discovery, redesigned Lord-to-Prince
service, warrants, fiefs or specialist rewards. Keep any older results as historical
evidence for their exact build; do not import them as passes on this one.
