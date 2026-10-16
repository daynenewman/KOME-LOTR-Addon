# Tiles, ownership, map geometry, and location

[Index](../../INDEX.md)

Owns world/map coordinate resolution, bundled tile geometry, gameplay defaults, tile control, and map/HUD presentation. Raster contact, rendered borders, strategic graph edges, and authorized passage are independent.

[KOM-59 T351/T352 correction](../../../kom59-ithilien-route-20261010/README.md) opens the existing direct default edge and removes its false river marker. The historical decorative-color water inference was wrong for this pair. Raster/IDs/reference coordinates remain unchanged; saved overrides still win. This narrow approval does not resolve the atlas's uncertain cells or establish actual server terrain/permissions.

[Approved KOM-59 mountain separation](../../../kom59-mountain-separation-20261010/README.md) removes exactly 45 canonical cells across five mountain contacts and classifies them using 25 hash-bound runs. Active IDs and gameplay graph/defaults stay unchanged; adoption into existing worlds requires an authoritative stopped-save and physical-entity inventory. Other geography proposals remain outside this approval.

Pass 3 personal-service geography is separate from strategic ownership and
recruitment-tile development. `KOMEProgressionServiceLocation.factionTerritory`
reads native Middle-earth faction control zones/biome NPC-faction presence for
ordinary Master service, with no settlement requirement. Regional Knight admission
uses persisted native generator/NPC/faction/dimension provenance and the native
home boundary through `KOMEProgressionServiceLocation.regional`. It does not infer
a fort from a roof, waypoint radius, noble title or biome. The narrow
`KOMEProgressionSeatTransformer` observes native generator spawn calls and places
nothing. Old unproven placements fail closed for new entry; existing service is
preserved. The faction-host inventory and compatibility gaps are in the
[Pass 3 report](../../../PROGRESSION_PASS3_SERVICE_PROMOTION_REPORT.txt).
Pass 4 extends `KOMEProgressionSeatTransformer` at the native trader respawner's
copy and successful spawn boundaries. `KOMEProgressionServiceLocation.captureReplacement`
stores a carrier-bound receipt only from a proven, native-home noble that should
respawn. `replacementSpawned` checks the same class/faction/dimension/home, rebinds
proof to the new UUID and consumes the receipt once. Forge entity data persists the
carrier through save/restart. Failed spawns retain the receipt; no NPC is created by
KOME and no player relationship/rank/history is transferred. Old untagged NPCs and
already queued pre-Pass-4 respawners remain unproven. Native village position caches
and predicted structure lists do not authorize adoption. Fangorn has neither a
qualifying civilian Master nor a regional Liege; Half-trolls have a regional Warlord
but no qualifying Master. Other audited factions have plausible native candidates,
with actual generated-world density and journeys requiring live acceptance.
`KOMEProgressionServiceLocation.royal` joins the existing persistent NPC King slot,
incarnation and authoritative capital tile for a future service seam; no royal
leadership quests or succession rules are implemented.

## Authorities and entry points

- [KOMETileWorldResolver.resolveWorldPosition](../../../../src/main/java/kome/common/data/KOMETileWorldResolver.java), `resolveMapPosition`, and [KOMETileResolution](../../../../src/main/java/kome/common/data/KOMETileResolution.java): explicit `RESOLVED`, `IN_BOUNDS_GAP`, `OUTSIDE_MASK`, unsupported/invalid states. Never substitute a nearby tile for a failed resolution.
- [Bundled raster and exclusions](../../../../src/main/resources/assets/kome/map/) supply geometry. [KOMEMapBorders](../../../../src/main/java/kome/client/KOMEMapBorders.java) and [KOMEConquestMapOverlay](../../../../src/main/java/kome/client/KOMEConquestMapOverlay.java) render it; rendering does not authorize movement.
- [KOMETileGameplayDefaults](../../../../src/main/java/kome/common/data/KOMETileGameplayDefaults.java) reads [gameplay config resources](../../../../src/main/resources/assets/kome/config/). [KOMEConquestTile](../../../../src/main/java/kome/common/data/KOMEConquestTile.java) and [KOMEConquestRouteEdge](../../../../src/main/java/kome/common/data/KOMEConquestRouteEdge.java) are persisted through [KOMEWorldData](../../../../src/main/java/kome/common/data/KOMEWorldData.java). Defaults and current owners are distinct.
- [KOMEConquestClaimService.claim](../../../../src/main/java/kome/common/data/KOMEConquestClaimService.java), [KOMEPacketConquestClaim](../../../../src/main/java/kome/common/network/KOMEPacketConquestClaim.java), and [KOMECommandConquest](../../../../src/main/java/kome/common/command/KOMECommandConquest.java): ownership changes and validation.
- [KOMEServerTileAwareness](../../../../src/main/java/kome/common/data/KOMEServerTileAwareness.java), [KOMETileAwarenessEvents](../../../../src/main/java/kome/common/data/KOMETileAwarenessEvents.java), [KOMEVisualLocationService](../../../../src/main/java/kome/common/data/KOMEVisualLocationService.java): server observations and location projections.

Consumers: [Build placement/rates](../build-population/README.md), [recruitment](../hiring-companies/README.md), [routes](../strategic-movement/README.md), [waypoint containment](../waypoints/README.md), [tactical geometry](../conflict-tactical/README.md), [client map sync](../client-network/README.md). Ownership changes also affect [diplomacy consequences](../politics-diplomacy/README.md) and [defeat](../campaign-lifecycle/README.md).

## Documentation and verification

[Resolver](../../../KOME_TILE_WORLD_RESOLUTION.md), [server awareness](../../../KOME_SERVER_TILE_AWARENESS.md), [gameplay/geometry separation](../../../tile-gameplay-separation-20260919/README.md), [border rendering](../../../tile-border-rendering-20260920/README.md), and [KOM-80 geometry-only contacts](../../../kom80-mountain-barriers/README.md) explain independent authorities. [October geography review](../../../tile-geography-review-20261002/README.md) is evidence/proposals, not blanket approval for edits.

Tests: [resolver](../../../../src/test/java/kome/common/data/KOMETileWorldResolverTest.java), [gameplay parity](../../../../src/test/java/kome/common/data/KOMETileGameplayParityTest.java), [render lifecycle](../../../../src/test/java/kome/client/KOMEMapRenderLifecycleTest.java), [queued barrier recovery](../../../../src/test/java/kome/common/command/KOMEMountainBarrierRecoveryTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMETileWorldResolverTest' --tests 'kome.common.data.KOMETileGameplayParityTest' --tests 'kome.client.KOMEMapRenderLifecycleTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Reproduce the exact dimension/world coordinate, resolver state, rendered segment, current owner, and effective route edge separately. Geographic edits require the approved exact cell manifest; contact or biome color alone does not establish intended capturability or a passable route. Direct claim code exists; do not infer that it requires a completed battle or gate breach.
