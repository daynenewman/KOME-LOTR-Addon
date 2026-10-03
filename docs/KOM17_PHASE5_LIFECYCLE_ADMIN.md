# KOM-17 Phase 5: lifecycle completion and administration

Phase 5 completes the ConflictRecord foundation without implementing battle resolution. Conflict
ending is always an explicit validated operation; diplomacy, departures, timers, player presence,
and Siege Complex checkpoints remain read-only resolution inputs.

## Explicit ending and final snapshots

`KOMEConflictService.endWithMovementHandoff` requires the normalized tile, expected Conflict ID,
expected revision, non-empty reason, authoritative timestamp/actor context, and a typed lifecycle or
admin-forced source. Stale identity/revision and repeated ending fail closed without audit or
movement mutation.

A successful operation increments the revision once, transitions ORDINARY or ENCIRCLEMENT to
ENDED, closes open faction-continuity intervals, and records Encirclement end metadata. It preserves
commitments, players, original garrison and relief history, timers, combat episodes, per-complex
capture/checkpoints, assault/lead state, diagnostics, and final transition metadata. It selects no
winner and changes no conquest ownership.

## Live readiness diagnostics

`KOMEConflictLifecycleService` derives current pairwise HOSTILE/NON_HOSTILE/UNKNOWN results from
live LOTR relations only. It reports active factions, hostile pairs, unknown diplomacy, unresolved
detachment references, and movement holds. `confidentlyNoHostilePair` is false whenever hostility or
required active references are unknown. Diagnostics never end or mutate a conflict.

## Movement-hold handoff

An ended conflict cannot retain active `CONFLICT_HELD` authority. Its validated orders transition
atomically to persisted `CONFLICT_RELEASED_PAUSED`: the Conflict ID/timestamp linkage is cleared,
all scheduling timestamps are disabled, and the company remains stationed with its order/cohort
links, queued route, indexes, and allowance intact. Current movement controls and automatic ticks
cannot resume this state. KOM-47 owns the future explicit release/resume policy.

## Admin commands

- `/kome conflict inspect <tile>` prints immutable identity/revision/timestamps, commitments and
  origins, faction/player continuity, garrison, timer/episode/Encirclement state, Siege Complex
  catalog/substates/checkpoints/assault leads, live hostility, movement holds, and unresolved state.
- `/kome conflict end <tile> <expectedConflictId> <reason>` performs the explicit forced-end
  transaction. The expected ID prevents stale administration from ending a replacement conflict.
- `/kome repair conflict <tile> preview|apply` previews or applies only deterministic derived-link
  repair. Preview is mutation-free; apply is idempotent. Missing or competing authority remains
  unresolved instead of being guessed.

All three surfaces use the existing `/kome` staff permission. Inspection, preview, stale requests,
and no-op repair are not audited. Successful end, forced end, deterministic repair, and movement
hold handoff use the central audit stream.

## Persistence and deferrals

No root or conflict schema bump is required. The post-conflict movement status is an additive
schema-6 movement value with strict cross-section validation. Active and ENDED records retain the
Phase 2 codec and allocator high-water behavior.

KOM-18 and later battle tickets own victory, conquest, timers, deployment, damage, retreat/death,
and siege gameplay. KOM-47 owns movement release/resume and route redesign. Phase 5 performs none
of those behaviors automatically.
