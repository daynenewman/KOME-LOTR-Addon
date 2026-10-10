# Tiles, ownership, map geometry, and location

[Index](../../INDEX.md)

Owns world/map coordinate resolution, bundled tile geometry, gameplay defaults, tile control, and map/HUD presentation. Raster contact, rendered borders, strategic graph edges, and authorized passage are independent.

[KOM-59 T351/T352 correction](../../../kom59-ithilien-route-20261010/README.md) opens the existing direct default edge and removes its false river marker. The historical decorative-color water inference was wrong for this pair. Raster/IDs/reference coordinates remain unchanged; saved overrides still win. This narrow approval does not resolve the atlas's uncertain cells or establish actual server terrain/permissions.

[KOM-59 geography decision package](../../../kom59-geography-decisions-20261010/README.md) contains five unapproved mountain-contact cuts, an independent conditional T340/T352 route/marker proposal, and the unresolved Harnen report. Exact manifests, annotated crops and historical stopped-save coverage are review evidence, not geographic approval or a current production-state inventory. T364 remains separate.

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
