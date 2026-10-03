# KOM-17 Phase 1: in-memory conflict foundation

This phase introduces only models, mutation contracts and deterministic tests. It has no production
singleton/caller, world-data field, NBT codec, schema change, event hook, movement integration or
command. It does not authorize battle actions, resolve victory, tick timers or run starvation.

## Authority and identity

`KOMEConflictService` owns one latest immutable `KOMEConflictRecord` per normalized tile. A registry
must later belong to one canonical world-data authority, not to each consumer independently.
There are exactly three umbrella states: `ORDINARY`, `ENCIRCLEMENT`, `ENDED`.

Creation requires either an absent predecessor or the exact ended predecessor ID/revision. It never
replaces an active record. Ending is an explicit caller-authorized operation, not an inferred victory.
A fresh conflict gets a new `CF<n>` identity and revision 1. Accepted mutations increment revision
once; rejected/coalesced operations do not. `Long.MAX_VALUE` is the allocator exhaustion sentinel.

Every mutation carries a caller timestamp, actor and reason. A mutation cannot precede the last
mutation timestamp. Last-transition metadata and the previous/current snapshots are available for
the later central-audit adapter; Phase 1 writes no audit stream.

No arbitrary registry insertion or public mutable collections are exposed. Drafts are package-local
construction details and are validated before immutable publication.

## Participation and references

Commitments reference canonical Campaign Detachment IDs, not copied troop membership. Inputs are
server-adapter contracts: asserting CAMPAIGN class or supplying an order ID is **not** proof of legal
arrival. Future adapters must validate canonical detachment coherence and strategic authorization
before calling them. ORDINARY inputs and duplicate active commitments are rejected.

Faction continuity is an explicit ledger, independent of whichever detachment remains. Participation
has no side/coalition choice and does not freeze hostility. Presence alone registers nothing; late
participation is supported. Live diplomacy will use the tri-state hostility resolver contract.

Player registration/withdrawal stores facts only. Initial registration does not implement re-entry;
KOM-19/21 must supply any later authorized re-entry contract and eligibility rules.

UNKNOWN, MISSING, INCOHERENT and AMBIGUOUS diagnostics retain reference identity. Recording one never
infers peace, death, withdrawal, conquest or relocation. Resolved detachment projections require
explicit class/faction/strategic location rather than defaulting missing metadata to ORDINARY.

## Encirclement and nested complex state

Original garrison cohorts are captured only in the Encirclement creation command. Their member UUIDs
are a rights/provenance snapshot, not a replacement troop-membership authority. Later relief cannot
append to them. UNKNOWN members are not confirmed terminal; zero original garrison does not end the
umbrella record. Future canonical UUID rekeys/terminal facts require explicit validated updates.

Complex substates are keyed by stable complex IDs and are independent. Active assault identity and
optional lead authority belong to a complex's active-assault substate, never a global ACTIVE_SIEGE
state or fixed faction side. Progress currently holds typed secured-segment ID checkpoints: no
geometry, percentage progress or physical gate HP. Checkpoint commands store validated controller
data; they do not decide assault/capture permissions or tactical progression.

An unavailable catalog differs from an available empty catalog. Binding a manifest cannot silently
drop already referenced complexes or replace a known manifest. Changed manifests need a later
explicit reconciliation contract, not reinterpretation of this initialization command.

Ending preserves timers, episode and complex-local capture/progress/assault/lead state as part of the
final diagnostic snapshot. It also retains historical commitment/player/garrison/reference facts,
closes active faction continuity, and records Encirclement end metadata. Those facts confer no active
authority when `isActive()` is false. A fresh record inherits none of the old record's participation,
cohorts, catalog, clocks, diagnostics or progress.

## Passive clocks and future persistence

Timer/episode/Encirclement ledgers accept explicit data checkpoints, never consult a wall clock, and
do not choose ticking/offline policy. Arrivals do not restart them. Episode identities use
`CF<n>:E<sequence>`; a same-episode timer checkpoint cannot silently reset its elapsed/duration
snapshot. Phase controllers own episode detection and semantic authorization.

Phase 2 implements conflict persistence and the narrow one-way root-schema upgrade described
in `KOM17_PHASE2_CONFLICT_PERSISTENCE.md`; Phase 1 remains the codec-independent model foundation.

Focused validation:

```powershell
.\gradlew.bat test --tests kome.common.data.KOMEConflictServiceTest --tests kome.common.data.KOMEConflictModelTest --no-daemon --console=plain
```
