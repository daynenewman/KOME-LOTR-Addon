# Current development status

Recorded **2026-10-17** after progression stabilization. Automated completion is
verified; the new build awaits Minecraft acceptance.

## Completed scope

- **Priority 1 - Master access:** `1ca850d` exposes validated Master Quest offers in
  native trader/smith/farmer menus. Discovery and acceptance share native friendliness,
  trade, rank, pledge and territory checks. Eight native roles across three origin
  states are covered. Unsupported civilian occupations were not made eligible.
- **Priority 2 - Recovery and relationships:** `cd07aef` confirms actual inventory
  possession, binds recovery to its issuing Master and checks clear supported sites.
  Master departure cancels unfinished ordinary objectives; completed departed service
  and original duty issuers are saved separately from new service. No new cooldown.
- **Priority 3 - Book polish:** `be12f0f` removes repeated Knight context and uses
  measured shared content for work, promotion and privileges. Long headings wrap;
  scroll/input/footer measurements agree. `39f19a9` completes integrated wording/
  marker regression corrections. Parchment, tabs, tooltips and Show Me remain.
- **Priority 4 - Investigation:** current progression places no buildings. The
  reported intersection's origin remains unconfirmed; native restrictions and a
  non-destructive follow-up plan are documented. No generation change was justified.
- **Priority 5 - Backlog:** [prioritized proposals](PROGRESSION_BACKLOG.md) cover all
  requested future ideas, dependencies and balancing. No larger mechanic was added.

[Detailed causes, files, checkpoints and evidence](PROGRESSION_STABILIZATION_REPORT.md).

## Tested executable state

- Branch: `elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
- Source checkpoint: **39f19a9**, including `1ca850d`, `cd07aef` and `be12f0f`, plus
  the preserved inherited language recovery and its untracked combat-lifecycle test.
  **HEAD alone does not contain those inherited inputs.** Documentation commits after
  this checkpoint do not change the tested executable source.
- Final command: `scripts/kome-validate.ps1 -Mode Full` -> `test build reobfJar`,
  offline, no daemon, max-workers=2, shared heavy-validation mutex.
- **4,169 passed / 5 skipped / 0 failures / 0 errors**, 401 suites, 4,174 total.
  Two Windows symlink fixtures and three optional original/build waypoint-jar checks
  remain skipped. These are not gameplay passes.
- Evidence: `outputs/overnight-validation/20261017-093401-380-Full-96bbeab9/`.
  Before/after validation input SHA-256 matched:
  `fbe0440249c53d3c041d00492e90ffe478555c890fc52daca987b189d1618af2`.
- Jar: `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar`, **18,639,829 bytes**,
  SHA-256 **64a9d95c8b89a376d899c0fb77b39e2cc11b7a5598630dbccb2b02d5d66b7dd3**.
  Version remains g14; use the hash to distinguish this build. Built, not installed.
- Focused checks: resumed baseline 50, Recovery checkpoint 104, book checkpoint 31,
  integrated correction 56 tests; counts overlap and must not be summed as unique.
  Exact commands/counts and failure diagnosis are in the report and evidence summaries.
- Documentation QA: 32 documents, 866 local links, 1,130 symbol references and 48
  exact test selectors passed using the PowerShell equivalent of the routing
  checker (Python unavailable on PATH). Fourteen latest answers remain blank;
  whitespace checks passed. Evidence: `outputs/stabilization/documentation-checks.json`.

## Preservation

Original overnight baseline `fc60f53`; resume began at `1ca850d` with unfinished
Master-history work and separate language changes. Fresh source/Git backup:
`outputs/overnight-backups/20261017-091558-351-aef34bf4`, **6,793 hashed files**,
exit 0 and COMPLETE.json. Initial assignment backup:
`outputs/overnight-backups/20261016-233750-020-94f2c944`.
Failed intervening snapshots are not recovery points. Backups exclude worlds.

Inherited language work remains unstaged and separate from implementation commits.
One primary worktree and the original three stashes are retained. Hash comparison
verified 2,228 pre-existing files unchanged outside the explicit change list.
No push, PR, jar installation, original-world write or machine security change.

## Manual evidence and next action

Latest user summary: **11 passed / 2 failed / 2 untested**; patrol behavior appears
successful, rank navigation/normal commissions and Dorwinion recruitment are working.
Runtime hash, environment and individual answer sheet were not supplied. Preserve
that observation separately from this new build in [history](../testing/PLAYTEST_HISTORY.md)
and [the summary archive](../testing/archive/20261016-service-g14-second-feedback-summary.md).
The earlier 4/1/9 session remains historical evidence.

**Next action:** follow [the 14-question latest checklist](../testing/PLAYTEST_LATEST.md)
on disposable profiles/worlds: recruitment first, one normal Recovery duty, then a
separate leave/rejoin profile and existing Knight regression/book checks. All new
answers are blank. [30 optional checks](../testing/PLAYTEST_EXTENDED.md) remain untested.
Connected-client appearance, natural terrain/pathfinding, generated-world density,
actual earned promotion and multiplayer acceptance are still pending. The original
missing item's precise cause and screenshot intersection origin remain unconfirmed.
The proposed staff-exercise notice and future gameplay backlog are not implemented.
