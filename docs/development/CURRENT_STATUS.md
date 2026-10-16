# Current development status

Recorded **2026-10-16** after the first gameplay feedback and focused repair.
Verify source, Git state and installed jar before beginning another task.

## Git and preservation

- Branch: `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
- Repair checkpoints: `547553a` (patrol placement/retry lifecycle) and
  `f2df9ecd3bd121ad6c865450771ffc7fdcd3fe5a` (Master-menu objective instructions).
  Baseline: `aa0647b`. The final documentation commit is discoverable with
  `git log -1 --format=%H -- docs/development/FIRST_GAMEPLAY_REPAIR_REPORT.md`.
- The inherited **50 tracked modifications** (34 production classes, 15 existing
  test classes and progression guide) remain unstaged, plus the existing untracked
  `docs/PROGRESSION_LANGUAGE_INTEGRATION_REPAIR.md` and
  `src/test/java/kome/common/data/KOMEProgressionCombatLifecycleTest.java`.
  Only this assignment's code hunks were committed, including in overlapping files.
  The verified jar includes that separate language recovery; HEAD alone does not.
- One primary worktree and all three original stashes retained. No branch reset,
  saved-world write, runtime jar installation or remote push.
- Verified pre-edit source/Git backup: **6,711 hashed files**, exit 0 and
  `COMPLETE.json`, `outputs/overnight-backups/20261016-161311-506-893426ae`.
  Backups exclude worlds. Prior and intermediate jars are retained in ignored
  `outputs/playtest-workflow/pre-repair-artifact/` and `patrol-only-artifact/`.

## Implemented and automated verification

Patrol preparation now tries nearby entity/block-clear positions using the actual
NPC footprint. A failed partial patrol waits for explicit fresh orders instead of
repeatedly spawning/cleaning during updates. Valid wait/follow, saved identities,
cleanup, exercise isolation and military records retain their existing behavior.
Master menus show implemented escort/recovery/defense instructions instead of the
placeholder. The six Master duty mechanics were reviewed and retained; their
unspecified physical failures are not claimed fixed. See the compact duty table
and exact scope in [the repair report](FIRST_GAMEPLAY_REPAIR_REPORT.md).

- Baseline compilation/focused checks: **42 passed** across six suites.
- Patrol/progression/NPC/courier/save/network/UI focused checks: **723 passed**,
  81 suites, zero failures/errors/skips.
- Master projection/patrol/authority/network focused checks: **55 passed**, eight
  suites, zero failures/errors/skips. Counts overlap; do not sum them as unique tests.
- Final Full `test build reobfJar`: **4,152 passed, five skipped**, 397 suites,
  4,157 total; zero failures/errors. Existing skips: two Windows symlink fixtures
  and three optional external original/build waypoint-jar checks.
- Final evidence: `outputs/overnight-validation/20261016-163416-043-Full-0cf3b08b/`;
  earlier baseline/reproduction/focused evidence is indexed in the report.
- Matching version/protocol: **1.0.10-service-g14**. Verified jar:
  `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar`, **18,637,579 bytes**,
  SHA-256 `7c29222a8edc4571547f4e519dcfee660a51d733e199a6926100eacd6710af66`.
  It was built, not installed. The previous g14 jar has a different hash.
- Earlier overnight tool verification (39 checks) and source/Git recovery evidence
  remain in [the setup report](OVERNIGHT_SETUP_REPORT.md).
- Documentation: 193 local links across 10 changed/new documents, archived 4/1/9
  counts and blank 15/30 current inventories checked. Routing, whitespace and scope
  checks passed; 2,222 unrelated original files match the pre-edit backup.
  Evidence: `outputs/playtest-workflow/first-feedback-verification.json`.

## Actual Minecraft feedback

First returned essential results: **4 passed / 1 failed / 9 untested**. Normal
assignment (11), active protection orders (12), Show Me (13) and named report
recipient (14) passed. Patrol soldiers (07) repeatedly spawned/disappeared.
Fun **3/5**, difficulty **4/5**, clarity **4/5**. Play date/runtime hash/environment
were not supplied; October 16 is receipt date. [History and original answers](../testing/PLAYTEST_HISTORY.md)
preserve these observations without attributing them to a confirmed installed hash.

**The repaired jar has no manual retest yet:** 15 essential and 30 optional checks
are untested. The historical 07 failure remains open pending observation; automated
passes do not close it. Several Master duties were reported unreliable without
specific examples. One confirmed menu placeholder was repaired; actual duty access,
native navigation, hand-in, relog/restart and multiplayer still need player evidence.

## Next action

Use [PLAYTEST_LATEST](../testing/PLAYTEST_LATEST.md): **F01 ? 08 ? 09 ? F02 ? 10**
first, then the previously passing 12?14 and 11. The six M01?M06 duty checks are
conditional on normal assignments on a separate disposable Serf profile; do not
force all six onto one character. Return bold Yes/No and exact failed-step Notes.
[Extended sessions](../testing/PLAYTEST_EXTENDED.md) retain optional deeper coverage.

No unrelated gameplay begins automatically. The inherited language recovery still
needs its own review/checkpoint. The [staff-exercise Rank-page notice](briefs/STAFF_EXERCISE_VISIBILITY.md)
remains **Proposed**, not approved or implemented. Use the existing approved-brief
and [overnight start procedure](OVERNIGHT_START_PROMPT.md) for later work.
