# KOM-75 Phase 2 — Emergency Defense mobilization

Emergency Defense has no permanent reserve bank. A legal hostile strategic arrival that creates a
tile `ConflictRecord` may atomically move an exact whole-unit amount from the defending native
faction's existing Available Population into one conflict-scoped autonomous cohort.

The allocation uses current KOM-71 production authority:

`floor((availableCenti * attackedTileRate) / (totalFactionRate * unitCostCenti))`

Only complete standardized defenders are committed. For immediately eligible defenders, the
attack-start rates, Available Population basis, unit cost, intended count, and actual committed
amount are persisted and are never rebalanced. If a recognized ruler is still inside the activity
window, a durable observation receipt records that exact newly created Conflict ID instead. Only
that receipt may be re-evaluated. Its first eligible processing pass uses then-current population
and production rates, atomically replaces the receipt with one commitment, and never enlarges the
Encirclement original garrison. Ending or replacing the exact conflict expires the receipt.
Population generated after the attack remains Available Population.

`KOMEEmergencyDefenseCommitment` is keyed by Conflict ID. Its deterministic defender intents use
stable UUIDs and record PENDING, ACTIVE, DEAD, or refunded terminal disposition. These records are
not hired-unit records, player companies, Campaign Detachments, movement orders, or a second
conflict system. The ConflictRecord remains tile-conflict authority and already contains the native
defending faction's participation continuity. Emergency cohorts expose an explicit active military
count for downstream resolution without joining or enlarging Encirclement original garrison.

Deployment is bounded to four intents per campaign processing pass. Each NPC is a native autonomous
LOTR NPC with no hiring player. A persisted intent and entity NBT marker are written before/with the
external spawn, allowing restart reconciliation by exact UUID and preventing duplicate delivery.
Active reserve NPCs cannot naturally despawn. Physical absence is unresolved, never inferred death.

Each deployed NPC is also bound to a 24-block battlefield home centered on that intent's actual
safe deployment anchor. The radius deliberately reuses the shared 24-block deployment search
boundary: it permits ordinary melee maneuvering around the attack site while preventing pursuit
chains across multiple chunks. Vanilla/LOTR home-aware target selection rejects new targets outside
that area. Because the configured native NPC classes do not install vanilla's return-to-restriction
task, KOME adds a narrow priority return task which drops a target after it pulls the defender over
the boundary and paths back toward home. The exact center/radius lives in the entity's durable KOME
marker and is reapplied when that entity loads; pre-containment Phase 2 acceptance entities recover
the center from the same canonical safe deployment resolver rather than their wandered position.
This does not hire, halt, own, or strategically move the NPC.

Current deployment uses the canonical attacked-tile RALLY/arrival origin and the shared bounded safe
placement resolver. This is the current safe tile fallback; no Battle/Force or Siege deployment-area
authority exists yet. Physical placement is deliberately not consulted while legal arrival,
ConflictRecord and population/intent authority are published. Failure to prove a same-tile safe
point leaves the funded intent PENDING for bounded retry; it never rejects or rolls back the legal
hostile arrival. No player, capital, random, or cross-tile fallback is used.

The explicit template table is `kome_emergency_defense_templates.csv`. Rohan uses foot trade index 0;
Fangorn is an explicit Ent exception. Conventional equipment is finalized before publication. Its
weapon must accept Legendary (`strong4`), Swift (`meleeSpeed1`), and Long (`meleeReach1`). Armor uses
the strongest legal common protection (`protect2`, then `protect1`, otherwise none), the strongest
legal ranged protection (`protectRanged3` down through `protectRanged1`), and the strongest legal
fall protection on boots (`protectFall3` down through `protectFall1`). Application and validation use
the same `canApply` and compatibility checks; modifiers are never forced. `LOTRWeaponStats` enforces
20+ for normal conventional templates.

Hobbits and Bree intentionally have no armor, and the Dunedain Ranger set is an existing light-armor
exception that finalizes at 19. Four further templates are explicit native-equipment exceptions at
19 because substituting foreign armor would violate native identity: Dunland's full Dunlending set
has no stronger native alternative; Near Harad's full set reaches 19 and its warlord helmet uses the
same material; the Moredain set reaches 19 while Lion armor is weaker and no complete Bronze armor
set exists; and the Half-troll set has no stronger native alternative. Fangorn remains the separate
non-armor Ent case. Live integrated-world startup validates the fully initialized LOTR equipment and
requires each native-equipment exception to match its declared legal total exactly.

Conflict end changes the cohort to an ending disposition but never resumes movement or decides
victory. Confirmed dead defenders remain spent. Pending undeployed intents and positively loaded
survivors are demobilized and refunded exactly once; unloaded/missing active entities remain pending
until positively reconciled. King return or later diplomacy/activity changes never despawn or refund
an active conflict cohort.

Staff inspection lists every intent UUID, disposition, refund flag, and current loaded status. A
loaded entity also reports its dimension and coordinates; `loaded=false` remains UNKNOWN and never
implies death. Confirmed death accounting runs at Forge `LOWEST` after protection handlers because
1.7.10 `LivingDeathEvent` is cancelable.

Root schema 9 and Emergency Defense section schema 3 persist commitments and exact deferred
eligibility observations. Schema 8 to 9 retains Phase 1 activity timestamps and initializes both
registries empty. Existing schema-9/section-2 Phase 2 data retains commitments exactly and initializes
the new observation registry empty. Neither upgrade invents receipts for historical active conflicts,
nor infers battles, defenders, deaths, or refunds. Candidate loading and write validation remain
atomic and fail closed.

Allied command, player ownership, horns, strategic routing, reinforcement waves, victory, conquest,
and siege gameplay remain outside this phase.
