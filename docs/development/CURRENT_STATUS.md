# Current development status

Recorded **2026-10-16** after gameplay-checklist workflow setup. Verify this snapshot
against Git before starting work.

- Branch: `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
- Source/checklist baseline HEAD: `5091843fe0d8ae4734ce20ce244f786c2a0f1bdc`
  (verified overnight infrastructure). Gameplay: Pass 5 `4b9b92c` over Pass 4
  `243aa4f`, plus the separate uncommitted language recovery below. Earlier Full
  verification ran at `e9ac989` with that same dirty Java source; setup changed no Java.
- The subsequent local documentation commit contains the gameplay acceptance
  workflow and checklists. Find its SHA with
  `git log -1 --format=%H -- docs/testing/PLAYTEST_LATEST.md`; check current HEAD
  with `git rev-parse HEAD`. This file cannot embed its own commit SHA.
- Inherited work remains **uncommitted**: 50 tracked modifications (34 progression
  production classes, 15 existing test classes and the progression routing guide),
  plus `docs/PROGRESSION_LANGUAGE_INTEGRATION_REPAIR.md` and
  `src/test/java/kome/common/data/KOMEProgressionCombatLifecycleTest.java`.
  It is preserved byte-for-byte and excluded from both workflow commits.
- One primary worktree and all three original stashes remain. No branch reset,
  stash deletion, saved-world write, jar installation or remote push.
- Current build/protocol identity remains `1.0.10-service-g14`.

## Implemented and verified

Pass 5 field service and the separate language integration recovery are present
in the working source; see [the repair report](../PROGRESSION_LANGUAGE_INTEGRATION_REPAIR.md)
and [the progression route](../routing/systems/progression/README.md). Workflow
setup adds development infrastructure and documentation, not gameplay.

- PowerShell tool integration: **39 checks passed** in disposable repositories.
- Fast: **84 passed**, five suites, no failures/errors/skips.
- Full `test build reobfJar`: **4,148 passed, five skipped**, 395 suites and 4,153
  total tests; no failures/errors. The skips are the existing two Windows symlink
  and three optional external waypoint-jar checks.
- Real backup/recovery: 6,668 hashed files; source/Git recovered separately with
  matching HEAD, branch refs, staged/dirty state and all stash entries; `git fsck` passed.
- Logs and exact run paths: [setup report](OVERNIGHT_SETUP_REPORT.md).
- Current verified jar: `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar`,
  SHA-256 `e8cc7ef961cc15c874f30d3635e7d85b5568554db6181f3da18755448eeac1ec`.
- Manual acceptance workflow: [generation/return protocol](../testing/README.md),
  **14 essential** and **30 optional** source-based observations. All **44 are
  untested**; [history](../testing/PLAYTEST_HISTORY.md) contains pending inventory,
  not fabricated passes. Existing older fixture evidence remains linked separately.
- Documentation verification: 161 local links across 11 documentation files;
  checklist fields/counts/blank answers, referenced commands and source/build
  identity checked. Routing: 25 documents, 824 links, 1,110 symbols, 48 selectors.
  Whitespace/scoped review passed; 2,224 unrelated files match the pre-edit backup.
  Evidence: `outputs/playtest-workflow/documentation-verification.json`.
  No Gradle or Minecraft run for this documentation-only task.
- Pre-documentation source/Git backup: 6,690 hashed files at
  `outputs/overnight-backups/20261016-154409-306-91a78de2`, exit 0 and verified
  `COMPLETE.json`. Source backups exclude worlds; no saved world was touched.

**Actual Minecraft acceptance:** no client/server/gameplay session was launched
by setup. Pass 5 natural-world, GUI-scale, reload and multiplayer acceptance remains
pending as documented by its existing report. Automated passes do not accept those.

## Next action

Run [the latest essential checklist](../testing/PLAYTEST_LATEST.md) on a disposable
setup: protection and its counterattack, report/exercise isolation, four temporary
followers and wait/follow cleanup, then normal Liege-assigned orders. Bold Yes/No
and return the document with Notes. Optional save/death, natural promotion and
multiplayer sessions remain in [extended coverage](../testing/PLAYTEST_EXTENDED.md).

The separate uncommitted language recovery still needs its own review/checkpoint;
do not sweep it into documentation or unrelated feature commits. Before new
gameplay, approve one bounded brief and use [the start instructions](OVERNIGHT_START_PROMPT.md).
The earlier recommended [staff-exercise Rank-page notice](briefs/STAFF_EXERCISE_VISIBILITY.md)
remains **Proposed**, not approved, implemented or included as accepted behavior.
