# KOM-21 A0: independent player battle lifecycle foundation

Baseline: clean KOM-21 branch at `8d63c36` (merged KOM-91). This slice adds only new
files. It does not install any live gameplay behavior or change existing schemas.

## Architecture and invariants

- `KOMEPlayerBattleLifecycle` is immutable. Its key is player UUID plus canonical
  exact conflict ID, never tile alone, a revision, an entity incarnation, or a faction.
- Facts: tile ID, optional original receipt identity, interruption generation, first
  cause (`DEATH`/`DISCONNECT`), interruption/deadline timestamps, active flag, optional
  successful re-entry timestamp, optional Formal Retreat timestamp, optional conflict
  terminal timestamp. There are no coordinates, mounts, companies, snapshots, online
  state, cached support, or faction claims.
- Generation zero is a receipt-free retreat-bar record with no interruption facts.
  The first interruption is generation one. Only successful completion permits a
  genuinely new interruption to increment the generation and calculate another deadline.
- `KOMEPlayerBattleLifecycleRegistry` owns mutations and coherent immutable snapshots.
  It has no singleton, WorldData/world/entity/client/company/ConflictRecord dependency.
  It stores the latest generation per key, not an unbounded interruption event log.
- `beginInterruption` requires an expected generation. A first begin uses zero;
  updates to an existing interruption use its current generation. Exact replay of
  the begin that created a still-active generation is an unchanged result. Old begins
  cannot reopen a completed generation; mismatched updates cannot overwrite a newer
  generation. A1 must capture the event's time and observed generation, not synthesize
  a new event/deadline on reconnect or replay.
- An active duplicate (including death followed by logout and logout after deadline
  expiry) keeps its original cause, generation, timestamp and deadline. Config changes
  do not recalculate that deadline. The domain accepts an explicit nonnegative delay;
  the 30-second value exists only as a test example, not a production constant.
- Query: `NONE`, `COOLDOWN_ACTIVE`, `COOLDOWN_EXPIRED`, `FORMAL_RETREAT_BARRED`.
  One millisecond before deadline is active; exact equality is expired. Querying does
  not clear anything and does not establish conflict/company/physical eligibility.
- `completeReentry` acknowledges external verified success for the exact generation.
  It rejects premature completion, is idempotent on repeat, and cannot erase a retreat
  bar. A completion acknowledgement is not itself an admission authorization.
- Formal Retreat is an explicit monotonic bar, requiring no receipt, registration,
  faction, or live player. Its first timestamp is retained. Death/disconnect and stale
  completion cannot remove it. A different conflict on the same tile is independent.
- Tile identity cannot change for one exact conflict. An existing original receipt
  binding cannot silently change. Integer overflow and regressed new-interruption
  timestamps fail before publication.

## Terminal handling and semantic cleanup

`markConflictTerminal` stages every affected row before publication, clears active
interruption flags, and records an exact-conflict terminal marker even for an empty
conflict. Contradictory timestamps reject the entire transition. Repeated termination
preserves the first timestamp. Retained bars remain visible as historical exact-conflict
facts; other terminal interruptions query as `NONE`.

`pruneTerminalRecords` can remove only rows for an explicitly terminal conflict.
It never evicts active-conflict state for capacity or age. The integrating owner must
settle external receipt/delivery work before electing to discard historical rows.
Exact terminal markers survive pruning and codec round-trip, so late callbacks cannot
recreate discarded history. A0 deliberately retains one small marker per terminal
conflict; it has no unsafe LRU or automatic marker deletion. Future marker compaction
requires an explicit safe identity/history policy, not a guessed highest-ended ID.

## Standalone codec

`KOMEPlayerBattleLifecyclePersistence` is an explicit **version 1** NBT format with
`Version`, ordered `Records`, and ordered `TerminalConflicts`. It returns a detached
registry or throws. Required fields and numeric tag types are strict; booleans must
be bytes zero/one. Optional timestamps are typed when present. UUIDs/identifiers must
be canonical. Unknown fields, unsupported versions, unknown causes, invalid lifecycle
combinations, duplicate keys, mixed tile identity, and inconsistent terminal markers
fail closed. A missing section is not silently interpreted as a fresh empty registry;
future world migration must explicitly construct empty initial state for an old save.

The writer takes one coherent snapshot. The decoder neither mutates its input nor
replaces an existing registry; there is no runtime stale-snapshot replacement API.
NBT is not retained inside the immutable domain objects.

## Deferred integration

No death/logout/respawn/login hooks, Join denial, Formal Retreat hookup, WorldData
storage, root migration, receipt mutation, protocol change, config registration,
teleportation, or mount behavior is part of A0. The accepted later death re-entry
policy is unmounted; surviving mounts are not searched for or reunited by this model.
KOM-91 final mounted egress remains untouched.

No generic delivery-pending state is included. A bare flag without a durable delivery
operation identity and physical recovery evidence cannot close the crash window.
The future delivery slice must persist its own intent linked to this exact key and
generation before physical effects, and call completion only after verified success.
It must preserve the original Join receipt/return obligation and combine lifecycle
status with canonical conflict/support and KOM-20/KOM-23 admission authority.

## Verification and concurrency boundary

All commands use `Local\KOME-Heavy-Validation` and the existing Gradle cache.

1. `gradlew.bat compileJava compileTestJava test --offline --no-daemon --max-workers=2 --tests "*PlayerBattleLifecycle*"`
   passed: **44 tests, two suites, zero failures/errors/skips**.
2. `gradlew.bat test --offline --no-daemon --max-workers=2 --tests "*JoinBattle*" --tests "*FormalRetreat*"`
   passed: **225 tests, 21 suites, zero failures/errors/skips**.

Tests cover deadline boundaries, fixed-delay idempotency, death/logout deduplication,
compressed in-memory NBT restart, successive generations, stale callbacks, receipt-free
retreat bars, player/conflict isolation, terminal pruning/tombstones, immutable views,
overflow, malformed input, strict types, duplicates, deterministic encoding and detached
publication. No WorldData mocks, Minecraft runtime, full test rerun or full build.

Compared additions with local KOM-20 `c4cb198` and KOM-23 `808c886`. All production and
test additions use new paths; no existing production/test file or routing guide is
modified. No referee/support/indexing, Encirclement, Join/retreat, Events, WorldData,
conflict codec/model or protocol surface is touched. Nothing is staged or committed.

Final `git diff --name-only` and cached diff are empty; all six additions remain
untracked. `git diff --check` passes; a supplemental whitespace check of all new files
also passes. Neither concurrent tip contains any of the new paths. HEAD remains
`8d63c36`. Verdict: **READY FOR INTEGRATION SLICE**. This is readiness of an isolated
foundation, not live enforcement of cooldown, retreat admission, or physical re-entry.
