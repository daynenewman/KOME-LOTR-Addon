# KOM-29: live faction defeat detection

Branch: `dayne/kom-29-defeat-detection`, based exactly on dev commit
`fa6a43cc4876f68fd59f361fa6182b21ae26d974`. PR targets `dev`.

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
- The server START campaign path reconciles after live population development/payout
  succeeds. Without a lost capital, it avoids a Build scan. Transitions run only during
  WAR/FINALE, preserving setup/reset lifecycle. Inspection is read-only in every phase.
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

## Remaining acceptance checks

- Real players should refresh Server Records, inspect the new war-detail card at
  supported window sizes, and confirm scrolling/labels and non-operator inspection.
- Live multiplayer encirclement/siege gameplay was not exercised. Automated coverage
  uses the existing hostile-pressure hook and actual population/ownership services.

No merge or deployment was performed.
