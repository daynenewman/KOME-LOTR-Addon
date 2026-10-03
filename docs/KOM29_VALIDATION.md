# KOM-29: live faction defeat detection

Branch: `dayne/kom-29-defeat-detection`, originally based exactly on dev commit
`fa6a43cc4876f68fd59f361fa6182b21ae26d974`, now integrating `origin/dev` at
`585e12a1c91cfa38ef8ed5d2da5e3580d1b8a238` through a merge. PR targets `dev`.

Authority read: current Linear KOM-29 and its implementation comment; Draft 0.4
sections 9.3, 9.5, 9.6, 9.7 and 12.2; existing ownership, capital, population,
campaign-season and central-audit services. Canonical PDF SHA-256:
`da1c50af347992829e9f410f4e34e1a5e8770129bc56dda125dd98487ec87b84`.
Read the applicable user and parent AGENTS.md instructions and Java skill.

## Behavior and self-review

- `KOMEFactionDefeatService` requires a live captured capital and no tile currently
  producing at least 1 population/day for the faction. An unclaimed or unavailable
  capital does not establish capture. Ownership is read from the existing tile authority.
  Capture requires a recognized canonical controlling faction. Unrecognized ownership
  returns `NOT_READY` with the owner, tile and operator correction command, without
  repairing ownership, publishing defeat or recording `FACTION_DEFEAT`.
- The existing rate service owns aggregation and recipient policy, including captured
  foreign Build production. Tile threshold comparison uses its unrounded BigInteger
  numerators, avoiding display rounding that can turn a sub-one rate into `1.000000`.
  Only active normal Builds' currently developed approved hours contribute. Pending
  hours, defensive-only Builds and encirclement pressure add no alternative rate rule.
- The server START campaign path preserves its startup return. Live ticks reset movement
  allowances, process movement, process scheduled musters, then run population
  development/payout; defeat reconciles only after population processing succeeds.
  Without a lost capital, it avoids a Build scan. Transitions run only during WAR/FINALE,
  preserving setup/reset lifecycle. Inspection is read-only in every phase.
- The existing `KOMEWarSeasonState` stores faction/timestamp outcomes only. Successful
  season reset clears them. Recapture updates the live predicate, but cannot replay or
  reverse a completed defeat. No objective snapshot, duplicate ownership/population
  authority, Submitted/Exiled mechanics, automatic Finale or war-ending policy was added.
- Publication atomically updates the outcome and existing bounded central audit in
  memory, restoring outcome, trimmed audit entries and prior dirty state on failure.
  WorldData saves both together. The once-only latch survives audit trimming and restart;
  malformed defeat rows use the existing fail-closed root loader.
- `/war objectives <faction>` exposes current objectives to players and operators.
  Server Records' war detail has a public `Faction Defeat / Live Objectives` card;
  its DTO parser and scroll-height calculation include the new field.

## Initial implementation validation (4f7b3cf), 2026-10-02

- Focused gate: 56 tests passed. Final clean full gate:
  **1,385 passed, 2 skipped, 0 failures, 0 errors**; `clean test build --offline
  --no-daemon --console=plain`, including release reobfuscation. Explicit
  `KOME_LOTR_VALIDATION_DIRECTORY` verified both original and build LOTR jar hooks.
  The two skips are existing Windows symbolic-link tests in
  `CustomSkinLibraryFoundationTest` and `ClientCustomSkinCacheTest`.
- All 17 new defeat tests passed: capture/recapture; 0.999/1/1.001 rates; aggregation;
  native/foreign ownership and multiplier changes; sub-one display rounding; pending
  versus developed hours; encirclement pressure; defensive/capital/mixed objectives;
  changed/missing/unclaimed capitals; repeated evaluation and reset; compressed root
  save/load; audit/dirty rollback; lifecycle gates; live development before tick evaluation;
  public DTO/UI parsing; malformed root data; audit trimming without replay.
- Final release jar SHA-256:
  `6dab983349babb01c34ea27c76eb4f8cecb33fd1dccf78d8959ce06ca347e554`.
  The release contains zero bundled native `lotr/*.class` files.
- Actual Java 8 / Forge 10.13.4.1614 / original LOTR v36.15 dedicated server:
  a new loopback-only disposable world on port 25629 verified initial capital objective,
  capture, defeat, live recapture, capture again, one central defeat audit, save/clean
  stop, cold startup and the same outcome/timestamp with exactly one audit. Both runs
  stopped. The runtime used the exact final release hash above. This console fixture
  contained no productive Builds; rate and encirclement scenarios used automated fixtures.
- Full builds and Forge runs held Windows named mutex `Local\KOME-Heavy-Validation`.
  Outputs/world are task-owned under ignored `outputs/kom29` and
  `run-server-production-verification-kom29`. Primary checkout head/status and stash
  remained unchanged. Other worktree heads remained unchanged; two other active
  worktrees' working-file statuses changed during the task, without writes from this task.
  Existing worlds/runtimes were not modified. Original LOTR input hash stayed
  `4f296e749c0d4739ecf859217a526b4218a2a45a768c08d3d551af0d0d3d5635`.

## Invalid-capital-owner correction, 2026-10-02

- Started from `4f7b3cfd390d87471f61049741365f38d7bb3ade`. Self-review confirmed
  the shared evaluator checks the projected controller against the existing
  `KOMEAlliance.allFactionKeys()` authority before establishing capture. This covers
  inspection and both reconciliation evaluations without adding ownership repair or
  changing campaign persistence/publication.
- Three additional regressions cover invalid current and legacy ownership in memory;
  full WorldData NBT save/load and a second save/load; and unchanged native, unclaimed
  and legitimate captured-capital behavior. Invalid-owner assertions verify `NOT_READY`,
  an actionable reason, no capture/defeat predicate, repeated reconciliation with no
  defeat latch/timestamp/audit, unchanged ownership and audit list, and no dirty state.
  Both invalid-owner regressions failed against the original implementation.
- Focused gate: **59 passed** across defeat, population rate/development, season state
  and capital services. Final `clean test build --offline --no-daemon --console=plain`:
  **1,388 passed, 2 existing symbolic-link skips, 0 failures, 0 errors**, including all
  20 defeat tests and release reobfuscation. Both gates held `Local\KOME-Heavy-Validation`.
- Corrected release jar SHA-256:
  `61d968d18920ff429928a27b6fc288ad5e611f30267f4301a6afdae209da6477`.
  Logs, regression failure evidence and XML/count snapshots are task-owned under
  ignored `outputs/kom29-invalid-owner`.
- No Forge run was performed for this correction; native runtime evidence above is
  for the initial implementation. No worlds or runtimes were changed. The existing
  disposable runtime jar retains the initial release hash. Primary checkout and stash
  remained unchanged; other worktree heads stayed unchanged, with two other worktrees'
  working-file statuses changing independently during this task.

## Integration with current dev, 2026-10-02

- Merged `origin/dev` commit `585e12a1c91cfa38ef8ed5d2da5e3580d1b8a238` into
  feature head `1d2312d12f7ca843c746bb7cf52b1c9e6151b587`. The only merge conflict
  was `KOMEEvents.processCampaignTick`; it now preserves the startup return and the
  live sequence: movement allowance reset, movement, scheduled musters, live population,
  then defeat reconciliation only when the population result succeeds.
- Root schema remains **6**. The mandatory `CivilianMusters` section, muster schema 1,
  and `WarSeason.FactionDefeats` survive the shared root loader and candidate publication.
  Self-review compared all 27 incoming files with `origin/dev`: outside the intentional
  event integration, WorldData defeat additions and muster integration tests, every
  incoming file matches dev. Existing command/admin/configuration behavior is retained.
- Three new behavioral regressions execute the real campaign tick and services. They
  verify movement allowance reset and arrival before muster processing, muster before
  live payout, and defeat after successful payout; a rejected payout retains the pending
  muster but defers defeat until successful retry; startup anchors movement without
  running overdue movement/muster/defeat. Full root NBT save/load preserves both pending
  and arrived muster snapshots, the once-per-season call, defeat timestamp and audits.
  Repeated ticks/delivery checks/reconciliation after reload do not duplicate outcomes.
  Arrival uses the existing test receipt authority and inert movement-world fixture;
  it does not claim physical NPC deployment.
- Focused validation: **215 passed, 0 skipped, 0 failures/errors**, including all 27
  muster tests and 20 defeat tests plus movement, population, root schema/load, season,
  capital, configuration and public-access regressions.
- Full `clean test build --offline --no-daemon --console=plain` validation:
  **1,415 passed, 2 existing Windows symbolic-link skips, 0 failures/errors**, including
  release reobfuscation. Both focused and full gates held `Local\KOME-Heavy-Validation`.
  Integrated release jar SHA-256:
  `26d9fc694506a22c9deb4c65e4dee4e7f206bf15ba2ed4eee845c09911eb59cf`.
- Task-owned logs, XML snapshots and preservation comparisons are under ignored
  `outputs/kom29-dev-integration`. Primary checkout, other worktree heads/statuses and
  stash matched their pre-merge snapshots. Existing worlds/runtimes were not used or
  changed; the previous disposable runtime jar still has its original hash.

## Remaining acceptance checks

- Real players should refresh Server Records, inspect the new war-detail card at
  supported window sizes, and confirm scrolling/labels and non-operator inspection.
- Live multiplayer encirclement/siege gameplay was not exercised. Automated coverage
  uses the existing hostile-pressure hook and actual population/ownership services.
- The integrated build has not been run in a multiplayer Forge campaign. Verify the
  combined muster/deadline/restart and defeat UI paths there; muster physical deployment
  remains deferred to the existing conflict/deployment integration, as documented in
  [KOM-11 acceptance](KOM11_CIVILIAN_MUSTER.md).

No PR merge or deployment was performed.
