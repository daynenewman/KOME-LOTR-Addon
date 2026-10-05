# KOM-75 Phase 1: Emergency Defense authority

Phase 1 replaces the old fixed-war-side Wartime Stewardship eligibility with a native-faction
Emergency Defense readiness authority. It does not recruit, spawn, equip, move, or transfer control
of troops.

## Eligibility

`KOMEEmergencyDefenseService` derives current readiness from authoritative state:

- the conquest tile is currently owned by the native defending faction;
- the tile has an active `ConflictRecord` whose persisted creation event identifies that faction as
  the validated defender;
- at least one active commitment is currently hostile to the defender under live LOTR diplomacy;
- a kingless faction is immediately eligible while that attack exists;
- a ruled faction is eligible only after the configured
  `season.emergencyDefenseRulerInactivityDays` window (default 14 real days) has elapsed without a
  successful, positive-population combat hire.

No `KOMEWar` side/opponent union, physical NPC location, login state, source tile, or native LOTR
squadron metadata participates in this decision. `eligible` means the Emergency Defense authority
conditions are currently satisfied. `mobilized` (and the compatibility `active` alias) means an
actual durable Emergency Defense commitment exists.

## Military-recruitment activity

The persisted authority is one activity row per faction: observation start, optional last qualifying
hire timestamp, update timestamp, and typed source. The population debit transaction records ruler
activity only when a successful player combat hire commits. Rollback, rejection, farmhands,
zero-population hires, noncombat hires, and the explicit future `SYSTEM_EMERGENCY_RESERVE` source do
not reset the clock. Both ORDINARY native registration and CAMPAIGN recruitment use this transaction.

Unknown legacy history is never interpreted as inactivity. A recognized ruler receives an
`UNKNOWN_HISTORY_ANCHOR`; the full inactivity interval must then elapse before eligibility is possible.

## Persistence

The root `KOMEWorldData` schema is 8. `EmergencyDefenseDataSchemaVersion` is 1 and
`EmergencyDefenseActivities` is a strictly decoded list. Schema 7 is the only accepted predecessor.
The schema-7 -> schema-8 candidate load preserves all existing authority, creates conservative
unknown-history anchors for recognized rulers at load time, infers no hires or emergency armies, and
publishes only after complete validation. Invalid data remains atomic and write-blocking under the
existing world-data policy.

## Stewardship retirement and conflict safety

New legacy stewardship grants and KOMEWar-derived opponent access are disabled. Existing temporary
rows can still be revalidated for safe retirement. An active conflict commitment or
`CONFLICT_HELD` order prevents revalidation, ruler return, diplomacy change, pledge release, or
demobilization from rewriting the order, removing the company, deleting unit records, changing the
cohort, or removing conflict references. Temporary controller metadata may be revoked without
changing the native company or conflict authority.

Staff can inspect derived state without mutation or audit through:

`/kome emergencydefense inspect <faction>`

## Deferred work

Phase 2 owns conflict-scoped population spending, troop creation, equipment, and autonomous native
defense. The final KOM-75 design has no Phase 3: allied strategic command, allied ownership,
supporting-king control, command horns, and command leases are explicitly retired. KOM-75 adds no
strategic routing, campaign AI, victory, conquest, battle resolution, or succession behavior.
