# PR #24 / #25 / #26 consolidation validation

Prepared October 5, 2026 for the existing PR #26 branch. The resolved revision is
local and has not been published: the connected GitHub application rejected a
Git blob write with HTTP 403, `Resource not accessible by integration`, and this
environment has no authenticated Git push credential. PRs #24 and #25 remain
open until #26 receives the validated commit.

## Inputs

- PR #26: `3b67b1ff84f1b834dd2a5bd01361d9b3d7c75b27`.
- Current dev: `b826ef35a0fb88ce9b2d58129ed307e9986b43d7`, including merged
  KOM-25 tactical configuration, KOM-75 Emergency Defense, and KOM-47 movement.
- Original PR #24: `d19d8807bf68fffd708b99d82aeb8e5db03ee5d9`.
- Original PR #25: `b59ec9af6f9bfee20e0049d5fe4bc6d085058b93`.

The existing combined PR already includes both independent implementations.
Merge current dev into that branch, publish the resulting merge commit, then
close #24 and #25 as superseded. Preserve both original branches. Do not merge
the consolidated PR into dev or deploy a jar as part of this task.

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

No connected client participated. Client/multiplayer acceptance remains pending.
KOM-47 is merged and its active blocking link on KOM-48 was removed in Linear.
KOM-48's coordinator adapter, KOM-24 starvation/scheduling, KOM-78 physical
muster delivery, and dependent full campaign acceptance remain incomplete.
