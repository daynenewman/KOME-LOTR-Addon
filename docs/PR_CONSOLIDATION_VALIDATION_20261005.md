# PR #24 / #25 / #26 consolidation validation

Prepared October 5, 2026 for the existing PR #26 branch. Publication was later
completed at `4b55b59e923028a9ed59233176e41235846efece`. A fresh GitHub read at
the start of the October 7 review found PR #26 open, draft and conflict-free against dev
`b826ef35a0fb88ce9b2d58129ed307e9986b43d7`; PRs #24 and #25 are closed as
superseded, with their original heads preserved. The earlier connector/push
failure was a temporary publication limitation, not the current state.

## Inputs

This section records the October 5 consolidation inputs. On October 7, after resumed
acceptance, dev advanced to `69dbad5a8d2d1cef461e8327b961ce9194d8241f`.
The isolated PR candidate incorporates that dev at
`d7d4a5a4e0bf810ce321b923b6482cb7d5406406`, retaining RESET precedence before new
progression NPC reconciliation and both character-storage isolation assertions.
Fresh validation found 2,888 tests: 2,883 passed, five skipped, zero failures/errors;
eight disposable deobfuscated Forge launches passed mounted recovery/completion in
both chunk orders and governance/daily command/save/restart checks. The matching
production JAR is `c49456694be28a870ed29a47dc66c3ec32e098fb7d9ef0b8cacd8756aa36c726`.
[Current closeout and evidence](pr-review-20261007/README.md) preserve the exact
artifact identity of earlier human passes and current unresolved acceptance.

- PR #26: `3b67b1ff84f1b834dd2a5bd01361d9b3d7c75b27`.
- Current dev: `b826ef35a0fb88ce9b2d58129ed307e9986b43d7`, including merged
  KOM-25 tactical configuration, KOM-75 Emergency Defense, and KOM-47 movement.
- Original PR #24: `d19d8807bf68fffd708b99d82aeb8e5db03ee5d9`.
- Original PR #25: `b59ec9af6f9bfee20e0049d5fe4bc6d085058b93`.

The existing combined PR already includes both independent implementations.
Current dev was incorporated and the resulting merge commit published; #24 and
#25 were then closed as superseded. Both original branches remain preserved.
The consolidated PR has not been merged into dev or deployed.

## Resolved behavior

- Root schema 12 writes reset, governance/daily, tactical configuration,
  Emergency Defense, company movement allowance/boundary, and survivor health.
- Read independently numbered historical formats according to their sections,
  rather than assigning different meanings to the same schema number.
- Each present campaign extension still receives strict decoding. Current roots
  require every integrated section; malformed candidates never publish.
- Preserve dev's KOM-47 movement service, persistent movement credit, exact HP,
  conflict handoffs, and client confirmation fixes.
- Preserve KOM-75's retirement of old Wartime Stewardship command grants.
- Reset virtual reconstruction now uses the canonical surviving-health record,
  including mount health, instead of restoring a stale entity snapshot's HP.
- Keep RESET precedence and the original receipt/checkpoint retry protections.
- Replace the obsolete KOM-47 blocker diagnostic with the pending KOM-48
  coordinator adapter. The existing movement runtime remains the owner until
  that adapter is implemented.

| Root | Accepted original formats | Upgrade |
| --- | --- | --- |
| 6 / 7 | Original campaign/dev; optional valid extensions | Preserve present sections; default only absent later authority |
| 8 | Campaign reset; tactical; Emergency Defense activity | Require each format's original mandatory authority |
| 9 | Campaign governance/daily; tactical movement; Emergency Defense commitments | Preserve exact original authority; reject mixed/partial formats |
| 10 | Combined campaign; combined tactical/Emergency Defense dev | Require campaign journals or dev tactical/defense sections according to format |
| 11 | Current dev tactical/Emergency Defense/movement | Preserve movement; add absent campaign journals without inventing credit |
| 12 | All integrated authority | Every integrated section required |

## Validation on the resolved code

Clean `test build`: **2,777 tests, 2,774 passed, 3 skipped, 0 failures/errors**.
The three skips require separately configured copies of original production
and build LOTR jars; stock dependency fingerprint checks passed. Native lineage
tests also compiled to Java 8 bytecode and passed independently.

All seven disposable Forge 10.13.4.1614 / LOTR 36.15 / Java 8 launches passed:
interrupted mounted reset; recovery in both chunk-load orders; completed cold
restart in both orders; governance/daily command processing and cold restart.
Assertions preserve original UUIDs, 3.25 / 7.125 HP, snapshots, receipts,
population, and once-only completion/audit effects. Obsolete virtual source
copies were rejected. Compact transcripts and full-suite counts are in
[pr-consolidation-evidence-20261005](pr-consolidation-evidence-20261005/).

Production jar SHA-256:
`d74e9c60095382390b5a0d5e5f7be78b8d3be6e344bdc5eb8b83c810726303e3`.
Stock LOTR dependency SHA-256:
`4f296e749c0d4739ecf859217a526b4218a2a45a768c08d3d551af0d0d3d5635`.
`git diff --check` and the staged diff check passed.

Validation used an isolated Linux checkout and newly created disposable worlds.
No existing user checkout, runtime, or world was accessed. This is not an audit
of the user's Windows worktrees. Historical evidence remains unchanged in
PR24_PR25_INTEGRATION_VALIDATION.md and the original handoff documents.

## Remaining scope

No connected client participated in the seven launches recorded above.
Separate October 6 Windows candidate evidence under `docs/tile-acceptance-20261006`
records a matching connection, scoped human tile HUD/reconnect/respawn/menu/map
checks and actual FPS/tick measurements, plus a production-options transformer
repair. Those artifacts differ from this historical Linux JAR and do not certify
reset/governance/daily client synchronization, multiplayer or lower-spec hardware.
The [October 7 review checklist](pr-review-20261007/README.md) records the newer
options/KOM-82 candidate and complete fresh regression gate separately.
KOM-47 is merged and its active blocking link on KOM-48 was removed in Linear.
KOM-48's coordinator adapter, KOM-24 starvation/scheduling, KOM-78 physical
muster delivery, and dependent full campaign acceptance remain incomplete.
