# KOM-17 Phase 2: Conflict persistence

Phase 2 raises the `KOMEWorldData` root schema from 5 to 6 and adds the canonical conflict section:

- `ConflictDataSchemaVersion = 1`
- `NextConflictSequence`
- `ConflictRecords`

The registry stores the latest immutable `KOMEConflictRecord` snapshot for each normalized conquest
tile, including an `ENDED` snapshot when that is the tile's latest conflict. A later conflict replaces
that ended registry entry with a fresh `CF<n>` record; the central audit stream remains the broader
history authority.

## Narrow schema-5 upgrade

Schema 5 is the only older root schema accepted. It has no canonical conflict authority, so loading a
valid schema-5 document preserves its existing authorities, creates an empty conflict registry, sets
the next conflict sequence to `1`, and marks the candidate dirty so the next save writes schema 6.
The upgrade does not inspect companies, routes, diplomacy, builds, physical observations, or any
other state to infer conflicts, commitments, garrisons, or Encirclements. Unknown future root schemas
remain rejected.

## Strict and atomic loading

Schema-6 conflict data is decoded into a detached `KOMEConflictService`. Required NBT types, enum
values, normalized tile keys, IDs, UUIDs, timestamps, nested collection uniqueness, aggregate record
invariants, duplicate tile/Conflict ID authority, active detachment uniqueness, and allocator
high-water consistency are validated before candidate publication. A failure write-blocks the live
`KOMEWorldData` under the existing policy and publishes none of the candidate. Source NBT is copied;
no mutable tag is retained by the live registry.

Structural decoding deliberately does not resolve world-dependent references. Missing or temporarily
unavailable detachments, factions, entities, or Siege Complex definitions remain represented by their
persisted identities and diagnostics. Loading never infers death, withdrawal, peace, conquest,
relocation, or commitment removal.

## Allocator and ended snapshots

`NextConflictSequence` is the persisted monotonic allocator high-water. It must be positive and
strictly greater than every loaded `CF<n>` identity, including ended/replaced identities still present
in the latest-per-tile registry. Contradiction rejects the candidate rather than risking reuse.

`ENDED` records retain their final timers, combat episode, per-complex state, capture/progress
checkpoints, active-assault identity, lead authority, continuity facts, diagnostics, and transition
metadata. Ending removes active gameplay authority; it does not erase the diagnostic snapshot. A
fresh conflict is constructed cleanly and inherits none of that conflict-local state.
