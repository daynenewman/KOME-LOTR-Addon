# Legacy KOME Troop Movement Status (Schema 6)

> Archived on 2026-07-26. Stage 3/4 movement authority is documented in `KOME_ALLIANCE_SYSTEM.md`.

> Read this together with the schema-5 war/transfer/departure addendum below; earlier owner-only route descriptions do not override current Military T2 or Wartime Stewardship checks.

This document describes the current troop movement implementation as of the company-based movement pass. It focuses on how the backend state is stored, how movement is started and completed, and how that data reaches the front-end GUIs.

## High-Level Model

Troop movement is currently company-based.

A company is a group of specific offensive hired-unit records. Moving a company moves all unit records in that company together. Movement changes physical location only. It must not change population funding.

The main rule is:

- `currentTile` changes only when the movement arrives and the units are respawned.
- `sourceTileId`, `sourceFaction`, `sourceType`, `sourcePlayer`, and allocation fields stay unchanged.
- Population used and allocation used do not change during movement.
- Defensive units and farmhands cannot be moved.

## Core Backend Classes

### `KOMEHiredUnitRecord`

Path:
`src/main/java/kome/common/data/KOMEHiredUnitRecord.java`

This is the source of truth for an individual hired unit.

Important fields:

- `entity`: current physical entity UUID, or the last known entity UUID while moving.
- `owner`: player UUID that owns the unit.
- `type`: `OFFENSIVE` or `DEFENSIVE`.
- `cost`: population cost.
- `farmhand`: true for farmhand records.
- `mounted`: true for mounted offensive units.
- `sourceType`: `TILE_POOL` or `PLAYER_RESERVE`.
- `sourcePlayer`: reserve source player when reserve-funded.
- `sourceTileId`: tile pool source when tile-funded.
- `sourceFaction`: faction that owns the funding source.
- `allocationTileId`, `allocationFaction`, `allocationPlayer`: allocation consumed by tile-funded units.
- `currentTile`: physical station tile. During movement it remains the origin tile until arrival.
- `companyId`: company membership.
- `movementOrderId`: non-empty means the unit is in transit.
- `movingEntityData`: saved entity NBT used to respawn the unit on arrival.

`isMoving()` is currently true when `movementOrderId` is non-empty.

### `KOMEArmyCompany`

Path:
`src/main/java/kome/common/data/KOMEArmyCompany.java`

This groups specific hired-unit records into one moveable company.

Important fields:

- `id`
- `owner`
- `ownerName`
- `faction`
- `name`
- `currentTile`
- `units`: UUID list of unit records/entities in this company.
- `totalPopulation`
- `mountedPopulation`
- `groundPopulation`
- `status`: `stationed` or `moving`
- `movementOrderId`

Speed rule:

- Mounted-only company: `2` tiles per day.
- Any ground population: `1` tile per day.

The helper `getTilesPerDay()` implements that rule.

### `KOMEArmyMovementOrder`

Path:
`src/main/java/kome/common/data/KOMEArmyMovementOrder.java`

This stores an active or completed movement order.

Important fields:

- `id`
- `companyId`
- `companyName`
- `owner`
- `ownerName`
- `ownerFaction`
- `originTile`
- `destinationTile`
- `units`: unit UUIDs selected for movement.
- `routeTiles`: currently origin and destination only.
- `population`
- `mountedUnits`
- `groundUnits`
- `mountedPopulation`
- `groundPopulation`
- `distanceTiles`
- `tilesPerDay`
- `filter`: currently `"company"`.
- `createdAtMillis`
- `departureMillis`
- `arrivalMillis`
- `status`: `moving`, `arrived`, `cancelled`, or `pending_spawn`.
- `pendingSpawnReason`: persisted reason if arrival time passed but physical respawn failed.

`hasArrived(now)` returns true for both `moving` and `pending_spawn` orders whose `arrivalMillis` has passed.

## World Data Storage

Path:
`src/main/java/kome/common/data/KOMEWorldData.java`

Movement-related maps:

- `hiredUnits`: individual units keyed by entity UUID.
- `armyCompanies`: companies keyed by company id.
- `armyMovements`: movement orders keyed by movement order id.

Movement timing override:

- `movementSecondsPerTileOverride`
- `0` means normal timing.
- Positive values mean test speed in real seconds per tile.

This is persisted in world data.

## Movement Command Flow

Path:
`src/main/java/kome/common/command/KOMECommandTroops.java`

### Create Company

Command:

```text
/troops createcompany <tileId> <name>
```

Current behavior:

- Validates the player controls the tile.
- Selects all unassigned offensive units owned by the player at that tile.
- Skips farmhands, defensive units, moving units, and units already assigned to a company.
- Sets `record.companyId`.
- Creates `KOMEArmyCompany`.
- Stores the company in `data.armyCompanies`.
- Calls `data.syncConquestTiles()`.

Current limitation:

- There is no granular per-unit selection yet. It creates a company from all eligible unassigned offensive units at the tile.

### Preview Company Movement

Command:

```text
/troops previewmove <companyId> <destinationTileId>
```

Current behavior:

- Runs server-side validation.
- Calculates temporary route distance.
- Sends `KOMEPacketCompanyMoveConfirmGui` to the client.

Current route limitation:

- `estimateRouteDistance(...)` currently returns `1`.
- No tile adjacency or pathfinding is active yet.
- Route summary says this is temporary endpoint validation.

### Confirm Company Movement

Command:

```text
/troops movecompany <companyId> <destinationTileId>
```

Current behavior:

1. Validates the company and destination again on the server.
2. Refreshes company population totals.
3. Requires each company unit to be:
   - offensive
   - owned by the player
   - currently stationed at the company tile
   - not already moving
   - physically loaded
4. Saves each physical entity to `movingEntityData`.
5. Sets each unit `movementOrderId`.
6. Creates `KOMEArmyMovementOrder`.
7. Sets company status to `moving`.
8. Stores the order in `data.armyMovements`.
9. Removes physical entities from the origin with a KOME-managed movement despawn.
10. Syncs conquest data.

Important:

- The code intentionally does not release or consume population when movement starts.
- Existing population/allocation usage remains attached to the original funding source.

## Movement Validation

Movement currently validates:

- Company exists.
- Player owns company, unless operator/admin.
- Company faction matches player faction.
- Company is stationed, not already moving.
- Company has at least one unit.
- Origin tile is controlled by the company/player faction.
- Destination tile is controlled by the company/player faction.
- Destination has a physical troop anchor.
- Destination anchor is in the same dimension as the player.
- Each unit is offensive, owned by the company owner, not moving, and stationed at the company current tile.

Friendly movement only is supported right now. Enemy movement, battles, interceptions, and allied movement permissions are not implemented.

## Physical Despawn And Respawn

### Movement Start

Before despawning, each unit entity is saved using entity NBT:

- `snapshotEntity(entity)`
- stored in `record.movingEntityData`

Then the physical entity tree is removed:

- `removeMovementEntityTree(world, entity)`

This uses `setDead` and `world.removeEntity`.

Mounted units are handled recursively through the mount/riding entity relationship.

### Stale Entity Removal

Stale moving entities are removed by:

```java
KOMECommandTroops.removeStaleMovingEntities(data, world)
```

This scans loaded entities and removes any physical entity whose hired-unit record is marked moving.

This is called:

- on player login
- on server tick while movement data exists
- when a moving NPC tries to join the world

Purpose:

- Prevent a moving unit from reappearing at the origin after relog/reload.
- Prevent death cleanup from treating movement despawn as normal death.

### Arrival Processing

Arrival processor:

```java
KOMECommandTroops.processArrivals(data, world, nowMillis, announce)
```

It now runs every second from `KOMEEvents.onServerTick`.

It checks all movement orders:

- If `now >= arrivalMillis`, try to process arrival.
- If destination cannot spawn yet, set `status = pending_spawn`.
- Persist `pendingSpawnReason`.
- Retry on later ticks.

Spawn validation currently checks:

- destination tile exists
- destination tile has anchor
- anchor dimension matches processor world
- destination anchor chunk can be loaded
- saved moving entity data exists
- a safe spawn position can be found
- entity can be recreated and spawned

Only after successful physical spawn:

- unit `currentTile = destinationTile`
- unit `movementOrderId = ""`
- unit `movingEntityData = null`
- unit entity UUID is updated to the new spawned entity UUID
- company `currentTile = destinationTile`
- company `status = stationed`
- company `movementOrderId = ""`
- order `status = arrived`
- conquest data syncs

## Movement Timing

Normal timing:

- mounted-only company: 2 tiles per real day
- ground/mixed company: 1 tile per real day

Test timing:

```text
/troops movetime set <secondsPerTile>
/troops movetime get
/troops movetime reset
```

When the override is active:

```text
durationMillis = distanceTiles * secondsPerTile * 1000
```

Current limitation:

- Because route distance is currently always `1`, a movement with `/troops movetime set 60` takes about 60 seconds unless route distance is later improved.

Admin test completion:

```text
/troops movement complete <orderId>
```

This forces `arrivalMillis` to now and attempts arrival/spawn immediately.

If spawning fails, the command reports the pending-spawn reason.

## Front-End Wiring

The current UI is mostly command-driven, with packets used to open client GUIs.

### Tile Command To Company List

Tile Command has a movement entry point through the troop/company controls.

The GUI path ultimately sends:

```text
/troops companies <tileId>
```

The server builds a list of `KOMECompanyGuiEntry` objects and sends:

```java
KOMEPacketCompanyListGui
```

Client opens:

```java
KOMEGuiCompanyList
```

### Company List GUI

Path:
`src/main/java/kome/client/gui/KOMEGuiCompanyList.java`

Shows:

- companies stationed at the selected tile
- unit count
- total population
- mounted population
- ground population
- movement speed
- company status

Actions:

- `Create Company` sends `/troops createcompany <tileId> <name>`
- `Choose Destination` opens the LOTR map and starts destination-selection mode
- `Back` sends `KOMEPacketConquestOpenCapture(tileId)`
- `Refresh` sends `/troops companies <tileId>`

### Map Destination Selection

Path:
`src/main/java/kome/client/KOMEConquestMapOverlay.java`

When `Choose Destination` is clicked:

```java
KOMEConquestMapOverlay.beginDestinationSelection(company.id, company.name, company.tile)
```

Then the map opens. On click:

```text
/troops previewmove <companyId> <tileId>
```

Important:

- The client only selects a tile.
- The server still validates the movement.
- The destination selection currently sends a chat command instead of a packet.

### Confirm Movement GUI

The server sends:

```java
KOMEPacketCompanyMoveConfirmGui
```

Client opens:

```java
KOMEGuiCompanyMoveConfirm
```

Shows:

- company name
- origin tile
- destination tile
- route summary
- unit count
- total population
- mounted/ground breakdown
- speed
- travel time

Confirm sends:

```text
/troops movecompany <companyId> <destinationTileId>
```

Cancel closes the GUI.

## Unit Command / Unit Details Wiring

Path:
`src/main/java/kome/client/gui/KOMEGuiPopulationUnits.java`

The Unit Command screen shows per-unit details from `KOMEUnitGuiEntry`.

Relevant movement display:

- `movementStatus`
- `movementOrderId`
- `currentTile`
- `destinationTile`
- `etaMillis`
- source/funding fields
- can move / cannot move reason

The Move button does not move one unit directly. It sends:

```text
/troops companies <selected.currentTile>
```

So movement is still company-based.

## Schema-5 war, transfer, and departure addendum

Company movement now persists native faction, temporary authority type, authorized war IDs, legal opponent context, population source, stewardship reservation, permanent transfer offer, and withdrawal/demobilization state. The company GUI exposes these fields directly from the server.

Wartime Stewardship routes by native identity. It may enter native land, current Military T2 passage, or the union of opposing factions in every authorizing active war. The actor must remain the recognized, actually pledged supporting king; ordinary members and operators playing normally cannot command the company. Checks run at GUI/action construction, preview, dispatch, departure, every step/arrival, pending spawn, tile change, war mutation/end, alliance downgrade, supporting-king loss/replacement, native-king creation, and restart. It grants no conquest-claim authority.

Supporting factions enroll automatically on a kingless native side when Military T3 is effective. This recorded war membership is not removed when temporary authority becomes dormant. Ending one overlapping war removes only that war ID and its opponents; another valid active war keeps the company authorized and prevents premature withdrawal.

When a war enters ENDING, an affected company in former opponent territory becomes `WAR_ENDED_HALTED`. Stay/Resume/Continue/retarget/recruit are rejected; only Retreat is accepted. Retreat follows the physical traveled route and prefers native territory before Military T2 staging. With no safe route, records remain `PENDING_ADMIN_RESOLUTION`; units are not teleported or deleted.

At a safe tile, stewardship-created units demobilize without drops and exact native population returns once. Pre-existing native/orphan companies instead lose temporary control and become dormant. Persistent tombstones protect unloaded entities.

Permanent transfer commands are owner offer, recipient accept/reject, and owner cancel. The recipient must be online and actually pledged to the same native faction. All units and reserve/allocation funding validate before one atomic mutation; moving, stewardship, malformed, or underfunded companies fail unchanged. Temporary delegation is never permanent transfer.

Pledge departure cancels owned movement before snapshot/entity cleanup. A moving unit is removed from its snapshot path and cannot reappear at origin/destination. The Companies screen `Departure` button sends a typed sender-only request and opens a scrollable server-authored preview; refresh preserves its context and Back returns to the Companies screen. The command preview remains available for diagnostics.

## Map And Tile Command Troop Counts

Map troop summaries are built in:

`KOMEPacketConquestData.buildTroopSummaries(...)`

Tile Command troop summary is built in:

`KOMEPacketConquestOpenCapture.summarizeTroops(...)`

Counting rule:

- Stationed count excludes units with `movementOrderId`.
- Outgoing moving count is based on active movement orders whose `originTile` matches.
- Incoming moving count is based on active movement orders whose `destinationTile` matches.

This prevents moving troops from being counted as stationed and moving at the same time.

## Commands Useful For Debugging

```text
/troops moving
```

Shows active movement orders for the player:

- order id
- company id
- owner
- origin/destination
- distance
- created time
- arrival time
- current server time
- remaining or overdue state
- unit count/population
- pending-spawn reason
- physical spawned count while moving

```text
/troops movement complete <orderId>
```

Admin-only. Immediately attempts arrival/spawn.

```text
/troops tile <tileId>
```

Shows stationed records and incoming/outgoing movements.

```text
/troops company <companyId>
```

Shows company composition, movement status, and physical spawned count.

```text
/troops unit <unitIdPrefix>
```

Shows individual unit details, funding source, current tile, company, and movement status.

```text
/troops debugtile <tileId>
```

Shows server-side tile owner, claimant, pending transfer, and physical anchor.

## Known Wiring Risks

### 1. GUI Actions Use Chat Commands

Several front-end actions send chat commands:

- create company
- refresh company list
- preview move
- confirm move
- open companies from Unit Command

This works, but it means GUI flow depends on command parsing and chat command side effects. It also makes it easier for UI state to close before the next screen opens.

Possible cleanup:

- Replace movement GUI actions with dedicated server packets.
- Keep commands as debug/admin/manual tools.

### 2. Preview And Confirm Run Separate Server Validation

Preview validates destination and opens the confirmation screen.

Confirm sends `/troops movecompany`, which validates again.

That is good for safety, but there is no pending-order token. If the company changes between preview and confirm, confirm may fail or behave differently.

Possible cleanup:

- Add a short-lived pending movement proposal id.
- Confirm by proposal id instead of repeating company/destination only.

### 3. Route Distance Is Temporary

`estimateRouteDistance(...)` currently returns `1`.

This means:

- all movement is currently one-tile distance for timing
- map route legality is endpoint-only
- there is no adjacency/pathfinding yet

Possible cleanup:

- Add tile adjacency data.
- Server computes route with BFS.
- Store full `routeTiles` in the movement order.

### 4. Destination Requires A Physical Anchor

Movement requires destination tiles to have an anchor.

Anchors are set with:

```text
/troops anchor <tileId>
```

If a tile is claimed but has no anchor, movement validation rejects it or arrival cannot spawn there.

Possible cleanup:

- Auto-set anchor on claim using claimant location.
- Store a safer tile rally point during claim/capture.

### 5. Physical Entity Must Be Loaded To Start Movement

`moveCompany(...)` currently requires every unit entity to be physically loaded and alive before movement starts.

If records say a unit is stationed but the entity is not loaded, movement fails.

Possible cleanup:

- Use saved spawn/unit record data when physical entity is not loaded.
- Or require player to be near the unit and show that clearly in GUI.

### 6. Moving Unit UUID Changes On Arrival

When a moving unit respawns, it gets a new entity UUID.

The code updates:

- `record.entity`
- `data.hiredUnits` key
- `order.units[index]`
- `company.units[index]`

This is correct, but it is delicate. Any missed list/map can create stale IDs.

Possible cleanup:

- Add a stable `unitRecordId` separate from entity UUID.
- Use entity UUID only as the current physical instance id.

### 7. `currentTile` Means Last Stationed Tile During Movement

While moving, unit `currentTile` stays as the origin until arrival.

The GUI should treat moving units as “In transit” and use the movement order for origin/destination.

Possible cleanup:

- Add explicit `locationStatus` or `inTransit` fields to unit GUI entries.
- Avoid interpreting `currentTile` as physically stationed when `movementOrderId` is non-empty.

### 8. Pending Spawn Is Safe But Needs GUI Surfacing Everywhere

Backend now stores `pendingSpawnReason`, but not every GUI may expose it.

Currently debug commands are clearer than the GUI.

Possible cleanup:

- Add `pendingSpawnReason` to movement/unit GUI packets.
- Show “Arrival overdue - pending spawn” in Tile Command, Unit Command, and Company List.

## Recommended Next Debugging Steps

1. Use `/troops moving` immediately after creating a movement order.
2. Confirm `arrivalMillis - createdAtMillis` matches expected travel time.
3. After timer expires, run `/troops moving` again.
4. If status is `pending_spawn`, inspect `pendingSpawnReason`.
5. Run `/troops debugtile <destinationTile>`.
6. Confirm the destination has an anchor and the correct owner.
7. Run `/troops company <companyId>` and confirm physical spawned count is `0` while moving.
8. Use `/troops movement complete <orderId>` to force a spawn attempt and get a direct success/failure message.

## Best Next Refactor Target

The highest-value wiring cleanup is to replace the chat-command GUI flow with packets:

- `RequestCompanyList`
- `CreateCompany`
- `PreviewCompanyMove`
- `ConfirmCompanyMove`

That would make the GUI/server contract explicit and reduce accidental state loss from screens closing while chat commands open the next screen.

The second highest-value cleanup is adding a stable unit record id. Right now entity UUIDs are doing too much work: they identify records, physical entities, and movement payload entries. Movement respawn necessarily changes entity UUIDs, so this is a fragile point.
