# Progression gameplay audit — 2026-10-06

The initial tree already contained the October 3 UX and October 5 hierarchy passes.
Their changes are retained. Baseline: 1,966 tests, 1,961 passed, 5 skipped, no failures.

## Existing systems and changes needed

| Area | Existing implementation / native integration | Finding and intended change |
| --- | --- | --- |
| Creation | `CharacterCreationFlowService`, `PlayerRaceData`, `ModNetwork`, mandatory GUIs | Confirmed choices already persist; next stage is derived from those flags. ESC currently ignores input or rewinds. Use normal pause GUI and defer authoritative stage screens until pause/options close. |
| Tracker/book | `KOMEProgressionTrackerSnapshot`, `KOMEProgressionSummary`, diff-based publications | Remove courier copy timer from HUD; derive sequential objectives from saved encounter state rather than introduce another quest acceptance. |
| Naming | native `LOTRFamilyInfo`, NPC rank records, synchronized custom name field | Hierarchy pass sets vanilla custom name tags, producing overhead labels. Prefer native family-name synchronization and preserve personal names in authority records. |
| Map/indicators | `KOMEVisualLocationService`, native `LOTRGuiMap`, cloned native accepted-miniquest icon renderer | Retain native rendering, medallions and Show Me. Refresh standing escort UUID references. Filter ruler markers by actual pledge. Restore actionable prospective indicators. |
| Master/gift | `KOMEPartingGiftService`, `KOMESerfdomMasterService`, former Master reference | Promotion is already gift-independent. Both ticking and knighthood currently drop gifts; remove those issuance routes, retain explicit claim transaction. |
| Commissions | `KOMEKnightCommissionService`, persisted actors/token/revision, Lordship trial | Preserve types/quotas and reporting. Add short stage projections, direct bound-item handover and conservative destination validation. |
| Escort | native hired NPC `ready`, `halt`, navigator, persisted teleport lease | Retain no teleport. Add local follow/wait/recalculate interaction and scoped targeting restraint. Missing loaded actor evidence must lead to existing failed-trial route. |
| Lost items | standing recovery NBT, existing sealed stolen-property KOME item | Replace new gold/silver-only selections with curated native faction equipment; preserve legacy selections. Require real item return. Restore despawn protection to death drops. |
| Defense | native invasion lists, regional enemy helper, bound attackers/death reconciliation | Preserve quotas/participation/finite replacement budget. Move initial spawning to open terrain around 50 blocks and restrict protected actors with reversible native home-area state. |
| Geography | native faction control zones, biome faction lists/invasions, waypoints | Existing `roadside refuge` labels describe arbitrary coordinates. Waypoints and civilian presence alone do not prove a building. Require loaded physical shelter evidence. Find actual defending/hostile territorial adjacency or decline incursion matchup. |
| Rewards | existing item-drop sound/physical issuance | Modest rewards require saved reservation flags; avoid changing alignment or promotion quotas. |

## Native evidence inspected

Shipped `libs/LOTRMod v36.15.jar` and available local native decompilation; signatures
and bytecode captured under ignored `outputs/progression-gameplay-20261006/`.
Native NPC naming, family name synchronization, speech transport, hired following,
halt/ready, quest renderer distance/fade/depth, map coordinates, `LOTRWaypoint`,
`LOTRStructures`, village generation/cache, NPC spawner probability gate and biome
faction lists were inspected.

`LOTRVillagePositionCache` describes possible/generated placement calculations,
not a reliable persisted inventory of existing world buildings. Native generators
can modify substantial terrain and do not expose an atomic, authoritative
player-construction-safe placement transaction. KOME conquest ownership is
political ownership of map tiles, not authoritative block provenance.

The actual bandit hook is in `LOTREventSpawner.spawnBandits`, separate from ordinary
NPC spawn entries. Its biome event probability is adjusted locally; ordinary NPC
spawn weights and invasion rolls are untouched. An executable transformed-class
test verifies the shipped native bytecode. Previously observed physical shelters
are now saved in a bounded KOME world registry so verified evidence can be selected
again without loading its chunks.

## Safety decision and limitations

Automatic refuge generation remains disabled in this pass. No heuristic can certify
every player block in Minecraft 1.7.10; generic generators cannot safely be forced
into an uncertain site. Existing verified shelter discovery is bounded and uses only
loaded world data. Assignments fail cleanly when no real destination is established.
This deliberately leaves generation, terrain blending, and generated-refuge records
unimplemented. No world blocks are written by the new destination code.

Physical roof/wall evidence establishes a shelter, not its lore identity or original
builder. Unless stronger native identity exists, dialogue says “the shelter marked
on your map,” never invents a settlement name. Live navigation and the visual extent
of the shelter still require Minecraft testing.
