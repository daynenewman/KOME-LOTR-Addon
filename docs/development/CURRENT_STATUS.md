# Current development status

Recorded **2026-10-16** after Windows setup verification. Verify this snapshot
against Git before starting work.

- Branch: `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
- Tested Git baseline before the local setup commit: `e9ac9896a6ea05fd043ecccc49c4c8920a988ac5`.
- Setup: local commit containing these development instructions/scripts. Obtain
  its exact SHA with `git log -1 --format=%H -- scripts/kome-validate.ps1` and the
  current HEAD with `git rev-parse HEAD`; this file cannot embed its own commit SHA.
- Inherited work remains **uncommitted**: 50 tracked modifications (34 progression
  production classes, 15 existing test classes and the progression routing guide),
  plus `docs/PROGRESSION_LANGUAGE_INTEGRATION_REPAIR.md` and
  `src/test/java/kome/common/data/KOMEProgressionCombatLifecycleTest.java`.
  It is preserved byte-for-byte and excluded from the setup commit.
- One primary worktree and all three original stashes remain. No branch reset,
  stash deletion, saved-world write, jar installation or remote push.
- Current build/protocol identity remains `1.0.10-service-g14`.

## Implemented and verified

Pass 5 field service and the separate language integration recovery are present
in the working source; see [the repair report](../PROGRESSION_LANGUAGE_INTEGRATION_REPAIR.md)
and [the progression route](../routing/systems/progression/README.md). The setup
adds only development infrastructure, not gameplay.

- PowerShell tool integration: **39 checks passed** in disposable repositories.
- Fast: **84 passed**, five suites, no failures/errors/skips.
- Full `test build reobfJar`: **4,148 passed, five skipped**, 395 suites and 4,153
  total tests; no failures/errors. The skips are the existing two Windows symlink
  and three optional external waypoint-jar checks.
- Real backup/recovery: 6,668 hashed files; source/Git recovered separately with
  matching HEAD, branch refs, staged/dirty state and all stash entries; `git fsck` passed.
- Logs and exact run paths: [setup report](OVERNIGHT_SETUP_REPORT.md).

**Actual Minecraft acceptance:** no client/server/gameplay session was launched
by setup. Pass 5 natural-world, GUI-scale, reload and multiplayer acceptance remains
pending as documented by its existing report. Automated passes do not accept those.

## Next action

Review the separate uncommitted language repair before authorizing its own commit
or a new gameplay task. Then approve one bounded brief and use
[the overnight start instructions](OVERNIGHT_START_PROMPT.md).
Recommended first feature: [a staff-exercise notice on the Rank page](briefs/STAFF_EXERCISE_VISIBILITY.md).
That brief is **Proposed**, not approved or started.
