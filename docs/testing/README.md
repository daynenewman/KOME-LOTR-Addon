# Gameplay acceptance workflow

Start with [the latest essential checklist](PLAYTEST_LATEST.md). Use
[extended sessions](PLAYTEST_EXTENDED.md) only when their setup is available.
[History](PLAYTEST_HISTORY.md) records observed manual results separately from
automated verification. A passing build does not establish Minecraft acceptance.

## Produce a checklist after a gameplay pass

1. Read `AGENTS.md`, the routing index, overnight instructions, current status and
   the latest implementation report. Inspect branch, HEAD, index, worktree and
   stashes. Compare the last verified gameplay baseline with the implementation
   checkpoint and inspect any dirty source used by validation.
2. Trace the actual server behavior, NPC interaction, GUI projection, packets,
   save/recovery paths and relevant tests. Code takes precedence over reports.
   Include implemented paths and accessible partial implementations; list absent
   or inaccessible functionality as a limitation, never an expected passing test.
3. After automated verification and a local implementation checkpoint, generate
   `PLAYTEST_LATEST.md` with about 10–15 high-priority observations. Put optional
   GUI, persistence, migration, regression and multiplayer coverage in
   `PLAYTEST_EXTENDED.md`. Use categories A–E and shared setups in play order.
   Preserve stable test IDs for unchanged expectations; revise IDs when the
   expected behavior changes. Each statement must describe one observable result.
4. Identify source commit, dirty inputs, version, exact tested jar/hash, evidence
   date, client/server/native dependencies, permissions and known blockers.
   Verify every command, button name, path and prerequisite against current code.
   Explain unavailable prerequisites instead of inventing shortcuts. Separate
   staff exercises from genuine earned progression. Use disposable worlds only.
5. Preserve returned results before regenerating either checklist: archive the
   completed copy under `docs/testing/archive/<date>-<build>-<session>.md` and append
   its history entry. Keep earlier attempts and repair/retest links. Blank future
   checklists must not erase answers, notes or historical failures. Do not copy
   old passes onto a new build without a new observation.
6. Add an **awaiting manual testing** history row; update current status and the
   morning handoff with one next session. Commit the documentation after checking
   links, formatting, commands and source correspondence. Documentation-only work
   needs these checks, not another Gradle run. Gameplay/build changes still need
   the owning route's automated validation and Full verification.

Record unexpected crashes even when they prevent reaching a numbered test. An
unreachable later test remains untested; record the blocking defect separately.
Checklists are acceptance evidence, not authorization to install jars, edit real
saves, change server policy or implement unapproved features.

## Return and interpret results

Keep `**Result:** Yes / No` in each test. Bold only the answer:

- `**Result:** **Yes** / No` = Passed.
- `**Result:** Yes / **No**` = Failed.
- `**Result:** Yes / No` = Untested.
- An explicit `Untested` or `Unavailable` result = Untested; explain why in Notes.

Both answers bolded, or a bold answer conflicting with an explicit unavailable
result, needs clarification; provisionally count it as untested and flag it.
Do not interpret **Yes** or **No** appearing in a statement or Notes as an answer.
Count only the Result field, and count each test once per recorded attempt.
Notes are evidence about observations, not automatic proof of a cause.

Before returning a copy, fill in its session header: date, build/hash, world type,
singleplayer/dedicated, player count and operator status. For a failure, useful
Notes are the exact step, visible message, expected/actual behavior and whether it
repeats; include coordinates, screenshot or a log path when available. Avoid
private data. Do not require extra evidence before recording a clear failure.

On receipt, Codex should:

1. Preserve the completed checklist and append dated passed/failed/untested counts
   to history, including unattempted optional tests. Record build/source identity
   and environment; do not silently attribute a result from a different jar.
2. State each failed observable symptom. Group related failures by likely common
   cause, clearly separating confirmed behavior from suspected explanations.
3. Prioritize crashes, lost progression, broken core mechanics and severe GUI
   problems. Propose a small repair with relevant source/tests; a single No is
   insufficient grounds for a broad architectural rewrite.
4. Repair only within the authorized scope, preserve previously passing behavior,
   run appropriate automated checks and create a verified local checkpoint.
   Generate focused manual retests plus affected regressions. A test passes a
   retest only when the player observes it again on the repaired build.
5. Append the repair commit and subsequent retest result to history. Keep the
   original failure and any still-unavailable tests visible. Update current
   status; automated success must not close a pending manual defect.

## Current checklist evidence map

The October 16 checklist compares Pass 4 `243aa4f` with Pass 5 `4b9b92c`, then
checks the current `5091843` worktree, including the uncommitted language recovery.
The [Pass 5 report](../PROGRESSION_PASS5_KNIGHT_FIELD_SERVICE_REPORT.txt) describes
implementation boundaries; the [repair report](../PROGRESSION_LANGUAGE_INTEGRATION_REPAIR.md)
explains the preserved newer operations. Neither is a completed human checklist.

| Behavior / checklist area | Current code and meaningful regression coverage |
| --- | --- |
| Commands and safe exercise setup | [KOMECommandKome](../../src/main/java/kome/common/command/KOMECommandKome.java), [KOMEProgressionDevelopment](../../src/main/java/kome/common/data/KOMEProgressionDevelopment.java), [relationship force](../../src/main/java/kome/common/data/KOMESerfKnightRelationshipService.java); [field operations tests](../../src/test/java/kome/common/data/KOMEKnightFieldOperationsTest.java) |
| A: assignment, combat, reporting, followers | [commission service](../../src/main/java/kome/common/data/KOMEKnightCommissionService.java), [field service](../../src/main/java/kome/common/data/KOMEKnightFieldService.java), [follow/wait interaction](../../src/main/java/kome/common/data/KOMEProgressionEscortFollowing.java), [Liege Quest](../../src/main/java/kome/common/data/KOMELiegeProgressionInteraction.java); [field service tests](../../src/test/java/kome/common/data/KOMEKnightFieldServiceTest.java) |
| Optional rescue/help/raid/theft/invasion | [help service](../../src/main/java/kome/common/data/KOMEKnightHelpService.java), [native theft adapter](../../src/main/java/kome/common/data/KOMEKnightEspionage.java), [invasion service](../../src/main/java/kome/common/data/KOMELordshipTrialService.java); [help tests](../../src/test/java/kome/common/data/KOMEKnightHelpServiceTest.java), [native theft hooks](../../src/test/java/kome/core/KOMEKnightPickpocketTransformerTest.java) |
| B: wording, book, tracker, navigation and packets | [commission presentation](../../src/main/java/kome/common/data/KOMEKnightCommissionPresentation.java), [progression GUI](../../src/main/java/kome/client/gui/KOMEGuiProgression.java), [menu tile](../../src/main/java/kome/client/KOMEProgressionMenuOverlay.java), [Show Me](../../src/main/java/kome/client/KOMEProgressionMapFocus.java), [tracker snapshot](../../src/main/java/kome/common/data/KOMEProgressionTrackerSnapshot.java), [rank packet tests](../../src/test/java/kome/common/network/KOMEPacketProgressionRankSummaryTest.java) |
| C: save, death, cleanup and legacy state | [commission NBT](../../src/main/java/kome/common/data/KOMEKnightCommission.java), [service record](../../src/main/java/kome/common/data/KOMEKnightServiceRecord.java), [event entry points](../../src/main/java/kome/common/data/KOMEEvents.java); [combat/death lifecycle tests](../../src/test/java/kome/common/data/KOMEProgressionCombatLifecycleTest.java), [runtime route](../routing/systems/runtime-state/README.md) |
| D: genuine promotion, native seat recovery, prior mechanics | [promotion authority](../../src/main/java/kome/common/data/KOMEServicePromotionService.java), [Master reliability tests](../../src/test/java/kome/common/data/KOMEMasterDutyReliabilityTest.java), [promotion tests](../../src/test/java/kome/common/data/KOMEServicePromotionPass3Test.java), [progression route](../routing/systems/progression/README.md) |
| E: personal battle evidence / server authority | [battlefield service](../../src/main/java/kome/common/data/KOMEKnightBattlefieldService.java), [battle evidence tests](../../src/test/java/kome/common/data/KOMEKnightBattlefieldServiceTest.java), [conflict route](../routing/systems/conflict-tactical/README.md), [network route](../routing/systems/client-network/README.md) |

This map is maintenance evidence. Players can follow the checklists without
reading Java or performing technical diagnostics.
