# KOM-21 A0.5: pure player battle admission policy

Baseline: clean KOM-21 branch at `6f9d0d7`. Only new files are added. There is no
live admission enforcement, physical delivery, registry mutation, configuration,
packet change, schema change, or WorldData integration in this slice.

## Inputs and decision matrix

`KOMEPlayerBattleAdmission.Facts` selects one player UUID + exact conflict ID from
an immutable A0 registry snapshot. It also takes caller-supplied exact-conflict
activity, authoritative own-faction committed support, nonnegative current time,
and an optional pending delivery generation (zero means absent). It retains only
the selected immutable row and terminal fact, not the full registry or snapshot.

Evaluate in this order; the first matching row wins:

| Facts | Decision | Tactical admission | Offer re-entry |
| --- | --- | --- | --- |
| Exact conflict inactive, or persisted terminal marker present | `BLOCK_CONFLICT_ENDED` | No | No |
| Formal Retreat bar for this exact player/conflict | `BLOCK_FORMAL_RETREAT` | No | No |
| No active interruption | `ALLOW_EXISTING_TACTICAL_PARTICIPATION` | Lifecycle does not block | No |
| Active interruption, time before persisted deadline | `BLOCK_COOLDOWN` | No | No |
| Expired interruption, matching pending delivery intent supplied | `REENTRY_DELIVERY_PENDING` | No | No |
| Expired interruption, own-faction committed support supplied | `REENTRY_AVAILABLE` | No | Yes |
| Expired interruption, no own-faction committed support supplied | `BLOCK_NO_OWN_FACTION_SUPPORT` | No | No |

Formal Retreat overrides every cooldown/re-entry state. Ended conflicts report
terminal disposition even if a historical retreat bar remains. A terminal marker
cannot be overridden by the caller's active assertion, including after history is
pruned. Same tile with a new conflict ID and other players are independent.

The deadline is never recalculated. The exact deadline is expired; a positive
remaining duration is computed by overflow-safe subtraction of nonnegative values.
Negative query times reject; a nonnegative time earlier than interruption creation
still blocks until the original deadline. Countdown seconds round upward without
overflow. No supplied support claim is inspected or inferred by this policy.

## Future integration seams

- `Result.isTacticallyAdmitted()` is the pure lifecycle seam for KOM-20. Even an
  expired interruption remains tactically blocked until verified physical re-entry
  is explicitly acknowledged through A0's generation-checked completion operation.
  An allowed result is not proof of registration, geometry, support, or a receipt.
- `Result.shouldOfferReentry()` is true only for `REENTRY_AVAILABLE`. Ordinary Join
  is a separate caller responsibility. No company or anchor is chosen here.
- Optional `Result.reasonText()` provides cooldown, no-support, retreat, terminal,
  available and delivery-pending messages without technical identities.
- Pending delivery is an explicit caller fact scoped to the exact player/conflict
  and active interruption generation. Missing active state or a mismatched generation
  rejects the facts. Pending delivery suppresses duplicate actions even if current
  support has changed; the future delivery owner must revalidate support and settle
  or cancel its intent. Cooldown, retreat and terminal decisions take precedence.
- A0 still has no durable delivery intent. A1 must persist/recover its own intent
  before physical effects, coordinate intent settlement with lifecycle completion,
  and supply a matching current generation. This policy does not close that crash
  window. Callers must refresh coherent facts before acting; retained snapshots are
  deterministic historical facts, not live authorization tokens.
- Snapshot construction copies the registry. Live integration should consider
  capturing once per authoritative operation rather than once per referee event.
- KOM-91's original return obligation, mounted final egress, and all existing
  Join/retreat/strategic authorities remain untouched.

## Validation and concurrency

Both Gradle runs used the existing cache and `Local\KOME-Heavy-Validation` mutex:

1. `gradlew.bat compileJava compileTestJava test --offline --no-daemon --max-workers=2 --tests "*PlayerBattleAdmission*" --tests "*PlayerBattleLifecycle*"`
   passed: **73 tests, three suites, zero failures/errors/skips**. Includes 29 new
   admission tests plus all 44 A0 lifecycle tests.
2. `gradlew.bat test --offline --no-daemon --max-workers=2 --tests "*JoinBattle*" --tests "*FormalRetreat*"`
   passed: **226 tests, 22 suites, zero failures/errors/skips**. Gradle also selects
   the new receipt-free Formal Retreat admission test by its method name.

New tests cover normal participation without lifecycle interruption, exact deadline
boundaries, expired support decisions, explicit completion, stale completion and
delivery generations, receipt-free retreat bars, player/conflict isolation, terminal
handoff without cleanup, terminal tombstones after pruning, nonnegative clock
regression, maximal timestamps, countdown rounding, useful text, immutable facts,
and deterministic read-only queries.

`git diff --name-only 6f9d0d7` and the cached diff are empty: all three additions
remain untracked. `git diff --check` and a supplemental whitespace scan of the new
files pass. No existing production, test, or routing file changed. No staging,
commit, push, full suite/build, or Minecraft launch was performed.

Local KOM-20 tip `c4cb198` and KOM-23 tip `808c886` contain none of these new paths.
There is no shared-file or logical authority change to either branch. These are
future integration seams only, not a change to live referee or Encirclement behavior.

Verdict: **READY FOR LIVE-INTEGRATION RECONCILIATION**.
