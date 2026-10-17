# Current development status

Recorded **2026-10-17**. **Progression Phase 1 is partial:** the Commoner/allegiance
foundation is verified automatically; the new birth-alignment award is pending a
calculation decision. Minecraft acceptance remains outstanding.

## Delivered checkpoint

- **cdbc051**: Commoner -> Knight -> Lord -> Prince. Native allegiance is independent
  of rank; pledge observation grants no promotion. King remains a political office.
- Legacy `wanderer`/`serf` rank keys migrate to `commoner`, retaining the original
  starting key. Earned higher ranks, service history, duties and relationships are
  preserved by disposable NBT fixture checks. Native alignment is not rewritten.
- The book displays canonical rank names and actual allegiance status. Master
  service remains available to eligible Commoners. Existing duties, commissions,
  patrols, recommendations, military checks, navigation and Show Me are retained.
- Current source/display changes, migration limits and decisions are described in
  [the Phase 1 report](PROGRESSION_PHASE1_REPORT.md). The supplied approved scope is
  saved in [design documentation](../design/progression/PHASE1_APPROVED_SCOPE.md).
  The separately referenced full redesign document has not been located.

## Remaining Phase 1 decision

Character creation currently raises alignment to the native pledge minimum.
The owner was asked whether +100 replaces that grant or is added afterward.
Adding afterward can give 200 from zero where the minimum is 100. Replacing it
also needs a rule for a faction whose native pledge minimum exceeds +100.
**No new birth-bonus code or retroactive award was added.** Existing completion,
recreation and configurable automatic-allegiance behavior remain. One-time birth
award persistence and exploit/migration tests are still required before declaring
Phase 1 complete. Follow the report's resumable implementation plan after the answer.

## Verified executable

- Source: **cdbc051 plus preserved inherited language edits and its untracked
  combat-lifecycle test**. HEAD alone does not reproduce this jar.
- Final command: `scripts/kome-validate.ps1 -Mode Full` -> `test build reobfJar`.
  Offline, no daemon, max-workers=2, shared heavy-validation mutex.
- **4,174 passed / 5 skipped / zero failures/errors**, 402 suites, 4,179 total.
  Skips: two Windows symlink fixtures and three optional original/build waypoint
  jar checks. Focused runs: baseline 102; foundation 83; affected regressions 135.
  Counts overlap; exact evidence and failure diagnosis are in the report.
- Final evidence: `outputs/overnight-validation/20261017-174018-495-Full-84e8381e`.
  Input SHA before/after: `e487c3304b7c5779169077322e04db4041534a08fb6350fce02fb289a435f62c`.
- Jar: `build/libs/KOME-LOTR-Addon-1.0.11-commoner-g15.jar`, **18,640,038 bytes**.
  SHA-256: `16d41e488869c68fa3ef1b14f21feb9f36b19ebe1823a0a6eb2b3015afb8992f`.
  Matching g15 client/server artifacts required; g14 rejects the new rank key.
  Built, not installed. No actual Minecraft acceptance is inferred.

## Preservation and next action

Started at **eec1766**, empty index, 50 inherited modified paths and two inherited
untracked files. Verified backup:
`outputs/overnight-backups/20261017-172432-201-1c7c6930` (6,870 hashed files,
COMPLETE.json, exit 0). Assignment-only staging keeps inherited language work
separate. Original worktree and three stashes retained; no push, PR, installation,
original-world writes or destructive Git operation.

Next implementation action: settle the birth calculation, then implement its
once-only persisted award and focused creation tests before another Full pass.
Meanwhile [14 blank gameplay questions](../testing/PLAYTEST_LATEST.md) exercise
the implemented foundation efficiently on disposable profiles/copies. Historical
11/2/2 feedback and the g14 stabilization checklist are preserved in
[history](../testing/PLAYTEST_HISTORY.md). Patrol, rank navigation and commissions
were reported working previously; they still require acceptance on this g15 jar.

Phase 2 remains deferred: revised Master pool/letter handoff, new commission rules,
royal objectives, conquest changes, equipment/crafting and specialty advancements.
The [prior stabilization report](PROGRESSION_STABILIZATION_REPORT.md) retains earlier
repair and terrain-investigation evidence.
