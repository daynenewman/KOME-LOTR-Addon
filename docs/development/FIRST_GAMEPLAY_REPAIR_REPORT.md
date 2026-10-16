# First gameplay feedback and focused repair — 2026-10-16

Verified repair checkpoints: **`547553a`** (patrol) and **`f2df9ec`** (Master-menu
instructions) over `aa0647b`, with the inherited
uncommitted progression language recovery still present and separate. No Master
duty redesign or unrelated gameplay change was made. No jar was installed or pushed.

## Actual feedback

The owner reported **4 passed / 1 failed / 9 untested** on the original essential
checklist: 11–14 passed; 07 failed; 01–06 and 08–10 untested. All 30 optional
checks remain untested. [The completed original checklist](../testing/archive/20261016-service-g14-first-feedback.md)
preserves the answers and **fun 3/5, difficulty 4/5, clarity 4/5**.
The actual play date, runtime jar/hash, world/faction and singleplayer/dedicated
environment were not supplied. October 16 is the feedback receipt date, not an
invented play date. The issued checklist's intended build is g14; runtime identity
is unconfirmed. The report that several Master duties are not fully functional
has no specific duty/step/symptom yet and is not counted as six confirmed failures.

## Patrol cause and exact repair

The source-level failure is reproducible. `startSoldiers` creates four guards one
at a time. `safeSite` previously found ground/air without testing native entity
occupancy; `spawn` rejected a chosen occupied point instead of trying another.
A partial failure invoked `technicalRetry`, removed the already-created guards
and cleared the actor list while retaining an active assignment. `tickPlayer`
then called `startSoldiers` again from that empty list **before its retry gate**.
An unchanged failed condition repeatedly created and removed the partial group.

New tests failed on the pre-repair production source with **0 instead of 4** guards
at an occupied first site, and **303 instead of 3** construction attempts over
2,000 update ticks after partial failure. A third valid-lifecycle test passed.
The exact obstruction/first rejection in the owner's runtime is unknown without
its world/logs; the reproducible source defects explain this class of flicker.

- [KOMEKnightCommissionService](../../src/main/java/kome/common/data/KOMEKnightCommissionService.java):
  FIELD_COMMAND guards rejected at their first spot search up to 64 nearby loaded
  candidates using the initialized native NPC's real bounding box, entity collision
  and block collision checks. Other objective placements retain their previous
  behavior. Actor UUID reservation, owner/token markers and role registration still
  precede world insertion. No collision check was disabled.
- The same service stops automatic FIELD_COMMAND preparation while
  `activationPending`. A rolled-back patrol waits for an explicit fresh-order
  interaction instead of repeated player-update spawns, including after the old
  timer expires. Existing cooldown, discard/reassignment and staff reset/end paths
  remain. Successful patrols continue through ordinary active updates.
- [KOMEKnightCommissionPresentation](../../src/main/java/kome/common/data/KOMEKnightCommissionPresentation.java):
  failed patrol preparation tells the player to return for fresh orders; it no
  longer promises that walking to the patrol site can restart that failed group.
- [KOMEPatrolFollowerLifecycleTest](../../src/test/java/kome/common/data/KOMEPatrolFollowerLifecycleTest.java):
  occupied placement, four stable owner-bound UUIDs through repeated real
  `KOMEEvents.onLivingUpdate` and commission updates, wait/follow, progression NBT
  reload, no automatic failed-preparation loop, and end cleanup of only the owned
  temporary soldiers. No strategic hired-unit or company records are created.
- [KOMEAccessFixture](../../src/test/java/kome/common/KOMEAccessFixture.java) adds opt-in
  entity occupancy checks; [the guard fixture](../../src/test/java/kome/common/data/KOMELordshipTrialFixture.java)
  permits its test subclass. Production ownership, death/report cleanup, rewards,
  Master mechanics, packet layout, schema and military authority are unchanged.

One further localized presentation defect was established during the duty audit:
`KOMEPacketSerfdomMasterAction.dutyStatus` published **“assigned; details pending”**
for implemented ordinary escort/recovery/defense, including active objectives.
Checkpoint `f2df9ec` uses the existing saved duty instructions for those ordinary
objectives. Provisioning/profession/courier prefixes, packet fields/action IDs,
completion checks and report authority are unchanged. This removes misleading
placeholder wording; it does not fix or accept an unidentified physical-duty bug.
[KOMEMasterDutyPresentationTest](../../src/test/java/kome/common/data/KOMEMasterDutyPresentationTest.java)
checks all three active descriptions, saved escort destination, unchanged NBT,
retained completed-report status and absence of premature earned credit.

The initial test draft needed a biome manager and ordinary bystander equipment
arrays initialized. Those fixture errors were corrected, not suppressed, and the
earlier failed logs remain. The final pre-repair reproduction had two real failures
and one pass; the repaired focused/full runs pass all three new tests.

## Existing Master duties: focused findings

Shared authority: a Serf needs a committed playable native pledge and a living,
adult, unhired ordinary same-faction civilian/farmer Master; friendliness, native
trading requirements, current identity, eight-block interaction range and betrayal
lockout apply. New Master selection requires faction territory. Duties are chosen
from the six implemented types, with completed types excluded. Provisioning and
Trade Assistance are alternatives after either completes. There is no daily quota;
three genuine distinct reports plus 150 alignment earn Knight from the current
Master. Staff-forced records do not qualify. Existing code already tries feasible
preparations without erasing earned work; a refused legitimate prerequisite is
not proof that completion is broken.

| Duty | Intended behavior | Verified evidence | Suspected defect | Recommended action |
| --- | --- | --- | --- | --- |
| Provisioning | Deliver the exact listed foods and water/vessel requirements to the current Master; partial counts persist and completion credits one duty. | Runtime assignment/catalog/eligibility, `KOMESerfProvisioningService`, direct click and DELIVER_PROVISIONS packet inspected; provisioning, goods-hardening and native-water tests pass. | None demonstrated. Wrong drink/vessel/metadata or unavailable ingredients may look like failed hand-in; cooked-food permission is filtered at generation. | M01: record exact requested item/tooltip, delivered counters and Master response. Do not substitute renamed/lookalike food or drinks. |
| Trade Assistance | Supply the Master's occupation-specific eligible exact materials; partial delivery completes one assigned duty. | Classifier/catalog, `KOMESerfProfessionService`, click and DELIVER_PROFESSION packet inspected; profession assignment/matching/reload/authority tests pass. | None demonstrated. Exact metadata, profession classification or the mutually exclusive material-duty pool may explain expectations. | M02 when assigned; record Master native type/trade and requested material metadata. Do not reset a character to force this alternative. |
| Courier | Collect the assignment-bound letter, hand it to the named bound recipient, then personally report to the Master for credit. | Route/correspondent preparation, owner/revision-bound held-book handover and `reportToMaster` inspected; dispatch drop, correspondence, resolution, geography, replacement and recovery tests pass. | None demonstrated. A destination alone is not the recipient; handover alone is not the final Master report. Natural terrain/recipient access still needs a client session. | M03: note whether failure is letter issuance, finding the named recipient, held delivery or return report. Lost letters use the existing Replace message action/cooldown. |
| Local Escort | Bind an independent same-faction adult within 48 blocks of the player, lead them physically to a verified shelter 256–3,000 blocks away, then report to the Master. | `findCharge`, destination proof, native follow/wait, actual NPC arrival, bounded missing-actor handling and ordinary hand-in inspected; escort/stabilization/gameplay/save tests pass. | Confirmed Master-menu placeholder fixed. No physical completion defect established. No eligible nearby charge/shelter prevents issuance; pathfinding/separation remain live risks. | M04: record target, destination, last visible position, follow/wait and whether the NPC actually arrived. Check the menu shows current instructions. |
| Property Recovery | Find the assignment-owned dropped item at the local search area, pick it up, hold it and return it to the current Master. | 112–176-block site selection, owner/token pickup, inventory reconciliation, held return, ordinary completion and cleanup inspected; recovery lifecycle/identity/loss/reload tests pass. | Confirmed Master-menu placeholder fixed; physical defect unconfirmed. An ordinary duplicate item cannot satisfy the private assignment; lost retrieved property fails recoverably. | M05: distinguish site/item visibility, pickup, held handover and credit. Record the current instructions/response rather than editing tags. |
| Local Defense | Protect an existing same-faction independent NPC 12–128 blocks from the Master, personally damage an assigned attacker, defeat both assigned attackers and report. | Regional-enemy prerequisites, exact attacker IDs/deaths, positive owner damage, ordinary completion and Master hand-in inspected; defense/participation/cleanup tests pass. | Confirmed Master-menu placeholder fixed; physical defect unconfirmed. Allies-only kills do not qualify; absent protected NPC/enemy pool prevents issuance. | M06: note protected NPC, personal hits, remaining targets, menu instructions and Master report response. |

Detailed owning paths remain in [the progression route](../routing/systems/progression/README.md).
`KOMEEvents` supplies the periodic escort/recovery/defense/courier updates and
interactions; book/tracker/map projections and Master-menu action prefixes were
checked against their server producers. The confirmed menu placeholder was fixed.
No disconnected duty or localized physical completion defect was established, so
Master duty mechanics were left unchanged. That is not proof that the reported
problems are absent.

Coverage limits: many tests call state/completion methods directly. In particular,
`KOMESerfKnightIntegratedJourneyTest` does not play through six physical duties or
prove natural candidate availability. Native AI/navigation, spawn geography,
client hand-in buttons and the six actual adventures need the conditional manual
checks. A specific failed duty and step is the next useful diagnostic input.

## Validation and preservation

| Check | Actual result / evidence under `outputs/overnight-validation/` |
| --- | --- |
| Baseline compile + six focused suites | **42 passed**, no skips/failures/errors; `20261016-161917-439-Fast-7756e707` |
| Corrected pre-repair patrol reproduction | **1 passed, 2 failed** as described above; `20261016-162413-537-Fast-5ba5dd45` |
| Repaired compile + progression/NPC/courier/save/network/UI regressions | **723 passed**, 81 suites, no skips/failures/errors; `20261016-162620-439-Fast-389d4a1e` |
| Master-menu projection + patrol/authority/save/network regressions | **55 passed**, eight suites, no skips/failures/errors; `20261016-163311-498-Fast-2babfb9b` |
| Final Full `test build reobfJar` | **4,152 passed, five skipped**, 397 suites / 4,157 tests, no failures/errors; `20261016-163416-043-Full-0cf3b08b` |

The earlier patrol-only Full also passed (4,151 passed / five skips,
`20261016-162737-208-Full-6bc7f67a`). Full was repeated because the later
Master-menu executable projection changed; the final run verifies both repairs.

The five skips remain two Windows symlink fixtures and three optional external
original/build waypoint-jar checks; no test was disabled. Full uses the existing
wrapper, Java 8/offline cache and shared validation mutex. It does not launch a
connected Minecraft client or establish manual acceptance of the repair.

Artifact: `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar`, **18,637,579 bytes**,
SHA-256 `7c29222a8edc4571547f4e519dcfee660a51d733e199a6926100eacd6710af66`.
Version/protocol remains g14; use this hash to distinguish it from the prior jar.
The previous verified jar was preserved under
`outputs/playtest-workflow/pre-repair-artifact/`; the intermediate patrol-only jar
is under `outputs/playtest-workflow/patrol-only-artifact/`.

Pre-edit source/Git backup: `outputs/overnight-backups/20261016-161311-506-893426ae`,
**6,711 hashed files**, exit 0 and verified `COMPLETE.json`. The repair commit was
staged using only the pre-edit-to-current patch, checked against the staged delta;
inherited language changes remain unstaged. Branches, all three stashes, source
snapshots and saved worlds are retained. No older classes replaced current code.

Documentation checks cover 10 changed/new documents, 193 local links, the exact
4/1/9 archived answers and blank 15/30 repaired-build inventories. A SHA-256 audit
matches 2,222 original files outside this assignment to the backup; source/index
separation preserves the inherited edits. Evidence is retained at
`outputs/playtest-workflow/first-feedback-verification.json`.

## Next session and unresolved acceptance

Use [PLAYTEST_LATEST](../testing/PLAYTEST_LATEST.md): first F01, 08, 09, F02 and
10 for a stable patrol, controls, relog and cleanup; then 12–14 and 11 for the
previously passing protection presentation/navigation/report wording and normal
assignment. M01–M06 are conditional natural-duty checks on a separate disposable
Serf profile; record whichever are actually assigned, leaving the others untested.

The new build has **no player-observed repair retest yet**. The old Test 07 failure
stays in history until a new observation resolves it. Fun/difficulty/clarity scores
are feedback, not a mandate to alter balance. Master defects, dedicated-server
behavior, actual entity/chunk reload, natural navigation and remaining extended
acceptance are unresolved; no broad redesign is justified by the available report.
