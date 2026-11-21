# Progression language integration repair — 2026-10-16

The incoming language edits replaced nine classes with Pass 3 implementations.
Four missing API errors exposed a wider rollback of Pass 4/5 gameplay. Recovery
uses the current local implementation, with compatible player-facing wording
retained. No calls were removed and no placeholder methods were introduced.

Recovery evidence:

- Current HEAD: `e9ac989` (Pass 5 validation documentation).
- Latest field-service implementation: `4b9b92c`.
- Master-duty reliability implementation: `243aa4f`.
- Before repair, all nine affected classes matched `632b171` after excluding
  comments and string contents, including the Master-duty fallback regression.
- Inspected Git log/reflog, worktree/stash inventories, the Pass 5 baseline at
  `outputs/progression-pass5/baseline-20261015-223222/`, and the preserved sibling
  Pass 4 backup's recovery instructions. These are older recovery sources;
  the current committed implementations take precedence.
- All 34 incoming edited files and their original binary Git diff are preserved
  under ignored `outputs/progression-language-repair/before/` and
  `language-before.patch`. Existing branches, stashes, backups and worlds remain.

| Restored class | Recovered behavior |
| --- | --- |
| `KOMEKnightCommissionService` | Tiered selection/generation, new field missions, finite positive four-argument damage evidence, active field player-death failure/cleanup, theft and bound raid property, physical followers, counterattacks, relief combat, transactional retry, test-report disposal and legitimate deceased-Liege reporting. |
| `KOMELordshipTrialService` | Four-argument damage evidence and counters, acceptance wall-clock timestamp, forced-test disposal and deceased-Liege report recovery. |
| `KOMEProgressionDevelopment` | Operator commission list/start/complication/status/reset/end commands, authentic Liege/rank checks, testing-session isolation and detailed diagnostics. |
| `KOMELiegeProgressionInteraction` | Finished staff exercises cannot resume earned service until the testing session ends. |
| `KOMEServicePromotionService` | Testing sessions cannot grant earned recognition or promotion. |
| `KOMEProgressionEscortFollowing` | Pass 5 followers retain interaction, pathing and no-teleport reconciliation. |
| `KOMEKnightCommissionPresentation` | New mission titles, phases, orders, failure reasons, theft/escape instructions, counterattack objectives and cultural confidence dialogue. |
| `KOMEProgressionTrackerSnapshot` | Tiered relief still displays its combat objective until the threat is resolved. |
| `KOMESerfdomMasterService` | Pass 4 tries feasible duties in shuffled order using detached preparation, preserves credits/state, and rolls back rejected courier issuance before fallback. |

All 34 edited production classes have the same Java operations as current HEAD
after excluding comments and string contents. Changed literals were reviewed as
display wording; machine IDs, NBT keys, command actions, markers and packet
contracts remain. Wording that obscured exceptional-equipment restrictions,
hand-held property handover or the range of available Master duties was corrected.
The report retains distinctions between automated evidence and live acceptance.

Existing presentation tests now expect the retained wording. Their gameplay,
authority, numeric-progress, secrecy, reward and persistence assertions remain.
`KOMEProgressionCombatLifecycleTest` additionally exercises actual damage events,
health-capped evidence, invalid/foreign hits, NBT reload, cancelled death events,
follower cleanup, earned-history preservation and legacy/report-ready boundaries.

Verification used `Local\KOME-Heavy-Validation`, Java 8 and the existing offline
Gradle cache; build configuration and dependencies were not changed.

| Verification | Result |
| --- | --- |
| Incoming `compileJava` | Reproduced exactly four errors: two damage signatures, `playerDeath`, and `commission`. |
| Restored `compileJava --offline --no-daemon --max-workers=2 --console=plain` | PASS. |
| Targeted progression/field/battle/Master-reliability/save/network tests | PASS: 82 suites, 852 tests; 850 passed, two skipped, zero failures/errors. Includes all four new event/lifecycle tests. |
| `test build reobfJar --offline --no-daemon --max-workers=2 --console=plain` | PASS in 1m 1s: 395 suites, 4,153 tests; 4,148 passed, five skipped, zero failures/errors. |
| `docs/routing/validate.py` | PASS: 25 documents, 814 local links, 1,110 symbol references, 48 exact JUnit selectors. |
| `git diff --check` | PASS. |

The five existing environment-dependent skips match the Pass 5 baseline:
two Windows symbolic-link fixtures lack support/privilege, and three waypoint
transformer fixtures lack the optional external original/build validation jar
pair (`KOME_LOTR_VALIDATION_DIRECTORY`). The two targeted skips are among those
three waypoint checks. No tests were disabled or removed for this repair.

Logs, before/after evidence and JUnit XML are retained under
`outputs/progression-language-repair/`: `compilebefore.log`, `compile.log`,
`focused.log`, `focused-results/`, `full.log`, `full-results/`,
`test-summary.json` and `preservation-verification.json`. Earlier failing runs
are retained separately. Existing tests changed only expected visible strings;
their Java operations and assertions remain, verified against HEAD.

Built artifact: `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar`, 18,637,319 bytes.
SHA-256: `ff31baae9c5f5cdb8f6554d47dcad80875141b80f919681f51626661ef4e65b1`.

No newer implementation was lost. Military authority, canonical battle outcomes,
root/nested save schemas, packet registration and `1.0.10-service-g14` remain
unchanged. No runtime/world was launched, no live multiplayer acceptance is
claimed, and no GitHub push was performed.
