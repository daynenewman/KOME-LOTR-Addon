# KOM-17 Phase 4: Strategic movement integration

Phase 4 connects the persisted ConflictRecord authority to the existing KOME strategic movement
arrival seam. It does not add a second movement system or implement battle resolution.

## Hostile terminal destinations

Normal passage remains unchanged. A coherent CAMPAIGN detachment may additionally route to an
explicit terminal destination when current LOTR diplomacy proves hostility to the authoritative
tile defender, or (for an existing conflict) to a relevant active participant. The exception is
never accepted for an intermediate route tile, so enemy territory does not become transit.
UNKNOWN owner or diplomacy authority fails closed. KOME war records and physical NPC presence are
not inputs.

## Legal-arrival receipt and publication order

`KOMEConflictMovementService` prepares an immutable server-side receipt from the movement order,
detachment, current tile authority, live diplomacy, defensive context, and expected ConflictRecord
identity/revision. Its accepted-arrival timestamp is the persisted server movement order's scheduled
arrival, making a safe process retry reconstruct the same event. Client timestamps, KOM-60
observations, proximity, and physical walking cannot produce the receipt.

`KOMECommandTroops.processArrivals` retains its existing cohort recreation and verification gate.
Only after every entity is recreated and verified does it publish UUID replacements and strategic
tile state, invoke the existing `KOMEConflictService`, and place the route on conflict hold. A
conflict rejection restores the order, company, unit records, UUID keys, and spawned cohort before
entering bounded spawn retry state. Failed cohort recreation never invokes conflict commitment.

## Defender, Encirclement, and original garrison

New conflict creation uses `KOMEConquestTile.projectRulingFaction()` as validated defender/owner
authority. An active, structurally valid DEFENSIVE Build in the destination tile selects
ENCIRCLEMENT; otherwise the record is ORDINARY. Defender population qualification and missing
future Siege Complex geometry are not creation conditions.

The original trapped garrison is snapshotted only from coherent CAMPAIGN detachments already
strategically stationed in the tile. The defending faction and current LOTR FRIEND/ALLY factions
qualify. Incoming attackers, ORDINARY records, moving detachments, source provenance, native LOTR
squadrons, and physical observations do not. Zero original garrison is valid. Later qualifying
allied arrivals join an existing Encirclement as exterior RELIEF and never enlarge the cohort.

Verified movement UUID replacements may explicitly rekey original cohort member identity without
changing rights, commitment identity, or treating a missing UUID as death.

## Conflict-owned route hold

Successful commitment sets the persisted movement-order status `CONFLICT_HELD`, stores the owning
Conflict ID and hold timestamp, and preserves the route, remaining steps/indexes, cohort, and
allowance. The company and unit records retain their order link while stationed in the conflict
tile. Automatic step scheduling, ordinary movement controls, daily allowance resets, new dispatch,
split/merge, admission into that detachment, ordinary stewardship disband, and empty-shell cleanup
cannot bypass the commitment. Pledge-release cleanup also retains the committed detachment, units,
and held order rather than manufacturing a departure. Conflict end or diplomacy change does not
auto-resume the route.

Schema 6 remains the root schema. The movement-order fields are additive and conflict-held orders
are cross-validated against the persisted ConflictRecord, company, tile, cohort, and unit links on
load and before write. Restart therefore restores the hold without scheduling departure.

## Auditing and deferrals

The existing conflict service records the create/join commitment audit. The adapter records one
`CONFLICT/ROUTE_HOLD` audit on the semantic hold transition; exact replay is a no-op. There is no
physical-polling audit path.

KOM-47 still owns release/resume policy, final route and daily-batch behavior, and the known route
UI issue. KOM-18 through KOM-26 retain battle, participation, damage, retreat, Encirclement,
starvation, Siege Complex geometry, and Active Siege gameplay responsibilities.
