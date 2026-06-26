# KOME Population and Tile System

This document describes how population and conquest tiles currently work in the KOME codebase. It is intended as a source document for discussing future population and tile design.

## Executive Summary

KOME currently has two connected but different population systems:

1. **Player population totals**
   - Offensive total
   - Defensive total
   - Combined total
   - Farmhand capacity
   - Faction-level population used by alliance requirements

2. **Tile population pools**
   - Offensive total and used population on each claimed tile
   - Defensive total and used population on each claimed tile
   - These pools currently fund new military unit hires

The distinction is important:

- The Population GUI primarily displays and edits **player totals**.
- The Tile Command GUI displays and edits **tile pools**.
- New warrior hires consume population from a **controlled tile**, not directly from the player's displayed pool.
- Farmhands use slots derived from player population and do not currently consume tile population.
- Alliance population requirements use faction-wide totals derived from player population, not tile population.

## Core Terms

### Total

The maximum amount of population assigned to a player or tile.

### Used

Population currently committed to tracked hired units.

### Available

```text
available = max(0, total - used)
```

### Offensive Population

Population assigned to units that may be moved between tiles through the troop movement system.

### Defensive Population

Population assigned to defensive units. Defensive units are deliberately excluded from troop movement.

### Farmhand Capacity

Farmhands use a separate slot count derived from player population:

```text
base farmhand slots = floor((player offensive total + player defensive total) / 25)
```

Farmhands do not currently consume offensive or defensive tile population.

## Player Population

Player population is stored per player in `KOMEPlayerPopulation`.

Each player has:

- Offensive total
- Defensive total
- Legacy offensive/defensive used fields
- Selected hire type

The selected hire type determines whether the player's next hired warrior is recorded as offensive or defensive.

The current cost calculation is the same for both types. Selecting defensive does not apply a discount or surcharge.

### What Player Population Currently Controls

Player totals are used for:

- Population totals shown in the Population GUI
- The combined army-capacity display
- Farmhand slot calculations
- Faction-wide alliance population requirements
- The `lord.early_beginnings` progression objective at 200 combined population
- A fallback level-up capacity check for old tracked units that have no tile assignment

Player totals do **not** directly fund normal new warrior hires in the current implementation. Those hires require tile population.

### Changing Player Population

Commands:

```text
/population get [player]
/population gui [player]
/population units [player]
/population hiretype [player] <offensive|defensive>
/population set <player> <offensive|defensive> <amount>
/population add <player> <offensive|defensive> <amount>
/population remove <player> <offensive|defensive> <amount>
```

Admins can manage any player.

A normal player may change only their own totals and must have the `Grow Population` progression permission.

The current GUI sends these commands directly. There is no automatic economy, building, timer, or resource cost attached to adding population in the population code itself.

## Tile Population

Every conquest tile may have a `KOMETilePopulation` record containing:

- Tile ID
- Associated faction
- Offensive total
- Offensive used
- Defensive total
- Defensive used
- Farmhand total
- Farmhand used

Farmhand tile fields exist in storage and the GUI, but the current hiring system does not allocate farmhands against them.

### Tile Ownership

Population can be edited only on a claimed tile.

The Tile Command GUI allows population editing when the viewer is:

- An operator/admin with command level 2, or
- The king of the faction that owns the tile

Population cannot be reduced below the amount already used on that tile.

### Changing Tile Population

From the Tile Command GUI, the owner king or an admin can add or remove offensive and defensive population.

Administrative commands:

```text
/population addtile <tileId> <offensive|defensive> <amount>
/population removetile <tileId> <offensive|defensive> <amount>
/population tile <tileId>
/population faction <faction>
```

`/population faction` totals the population pools across all conquest tiles currently owned by that faction.

### How the Faction Pool Is Calculated

The faction-wide tile pool is the sum of the relevant totals and used values on all claimed tiles owned by that faction.

```text
faction offensive available =
    sum(owned tile offensive totals) - sum(owned tile offensive used)

faction defensive available =
    sum(owned tile defensive totals) - sum(owned tile defensive used)
```

The code still keeps each tile's pool separate. A hire must fit completely inside one eligible tile; it cannot combine 10 available population from one tile with 15 from another.

## Hiring Units

### Required Progression Permission

The player must have the existing `Hire Units` progression permission.

If the permission is missing:

- The hire is rejected.
- The NPC is removed.
- The system attempts to refund the hire cost.

### Warrior Population Cost

For a normal warrior:

```text
base cost = ceiling(unit maximum health)
mounted surcharge = 25
final cost = base cost + mounted surcharge
```

Examples:

- A ground unit with 20 maximum health costs 20 population.
- A ground unit with 40 maximum health costs 40 population.
- A mounted unit with 20 maximum health costs 45 population.

The fallback cost is 25 if maximum health cannot be read.

### Choosing Offensive or Defensive

The player's current hire type is used:

```text
/population hiretype offensive
/population hiretype defensive
```

The unit then permanently records that population type unless some future system changes it.

### Selecting the Origin Tile

When a warrior is hired, KOME searches the hiring player's faction's claimed tiles for one tile with enough available population of the chosen type.

The current selection rule is:

1. Tile must be claimed by the player's faction.
2. Tile must have enough available population for the entire unit cost.
3. If multiple tiles qualify, the alphabetically lowest normalized tile ID is chosen.

The player does not currently choose the origin tile during hiring.

If no single controlled tile has enough population:

- The hire is denied.
- The NPC is removed.
- The system attempts to refund the coins.

### Leveling and Population

Tracked unit cost is recalculated as the unit's maximum health changes.

If a level-up increases the population cost:

- The unit's current tile must have enough additional available population.
- If enough population exists, the tile's used amount increases.
- If it does not, the level-up is rolled back and the owner receives a message.

This means keeping a small reserve on a tile is important if stationed units are expected to level.

### Death or Inactive Units

When a tracked warrior dies or becomes inactive:

- Its hired-unit record is removed.
- Its population cost is released from its recorded current tile.

Farmhands instead free one farmhand slot.

## Farmhands

Farmhands are handled separately from warriors.

Current rules:

- They require the `Hire Units` permission.
- Each farmhand consumes one farmhand slot.
- They do not consume offensive or defensive tile population.
- The unit record stores a nominal cost of 1, but this is not deducted from a tile pool.

Player farmhand capacity is derived from combined player population in blocks of 25.

Example:

```text
Player combined population: 249
Base farmhand slots: 9
```

## Troop Stationing and Movement

### Stationing

Tracked hired units have a `currentTile`.

Command:

```text
/troops station <tile> [all|unstationed]
```

This changes the recorded current tile for qualifying units.

### Moving Troops

Command:

```text
/troops move <fromTile> <toTile> <population> <distanceTiles> [all|mounted|ground]
```

Only units meeting all of these conditions may move:

- Owned by the issuing player
- Stationed at the origin tile
- Offensive type
- Not a farmhand
- Not already moving
- Matches the optional mounted/ground filter

The command selects complete units until their combined cost reaches or exceeds the requested population. It does not split a unit's population cost.

### Travel Speed

Current movement timing:

- Mounted-only armies: 2 tiles per real day
- Any army containing a ground unit: 1 tile per real day

The supplied `distanceTiles` value is trusted by the command. The code does not currently calculate map distance itself.

When the order arrives, each selected unit's `currentTile` changes to the destination.

## Tile Command Display

The Tile Command screen distinguishes between:

### Population Capacity

- Offensive used / total
- Defensive used / total
- Available capacity

### Stationed Units

- Offensive population physically recorded at the tile
- Defensive population physically recorded at the tile
- Mounted and ground offensive composition

### Movement Preview

- Incoming population
- Outgoing population
- Arrival estimate

Capacity and stationed-unit totals are related, but they are not generated from exactly the same fields. Capacity comes from `KOMETilePopulation`; stationed units come from hired-unit records.

## Alliance Uses of Population

Alliance population requirements use player/faction totals, not tile population.

### Trade Alliance

Trade Tier 2 requires:

- 50 farmer population
- 10,000 coins

Faction farmer population is calculated from faction members' player population:

```text
each player's farmer population =
    floor(player combined population / 25) * 25
```

For each sender-side Trade alliance already at Tier 2 or above, 50 farmer population is treated as spent.

That spending is derived from alliance state. It is not stored as a separate transaction.

### Military Alliance

Military Tier 4 requires:

- 50 faction population
- 30,000 coins

Faction population is the sum of combined player population for members of the sender faction.

For each sender-side Military alliance already at Tier 4 or above, 50 faction population is treated as spent.

Again, this spending is derived from alliance tier state.

### Important Distinction

Alliance requirements do not currently inspect:

- Tile population totals
- Tile available population
- Stationed armies
- Offensive versus defensive tile allocation

They inspect faction member player totals.

## Progression Uses of Population

The `Lord: Early Beginnings` progression objective completes when the player's combined population reaches at least 200.

Completing that objective unlocks the existing `Grow Population` baseline permission.

The progression tree also has a later `Prince/King: New Growth` step that grants the same baseline population-growth permission.

## Persistence

Population data is stored in KOME's world saved data:

```text
KOME_ServerRules
```

Stored data includes:

- Player populations
- Tile populations
- Hired-unit records
- Conquest tile ownership
- Alliance data
- Army movement orders

These records are intended to be world-specific.

## Current Implementation Limitations

These points are especially important for future planning.

### 1. Player and Tile Population Are Not a Single Economy

Player totals determine progression, farmhand capacity, and alliance requirements.

Tile totals determine whether normal warriors can be hired.

Increasing player population does not automatically distribute population to tiles. Increasing tile population does not increase the player's combined total.

### 2. New Warrior Hires Require a Tile

The current hire path requires a controlled conquest tile with enough available population.

A player can have a large player population total and still be unable to hire if no owned tile has a sufficient pool.

### 3. Origin Tile Selection Is Automatic

The system selects the alphabetically lowest qualifying tile. The player cannot choose which tile funds a new hire.

### 4. Movement Does Not Transfer Used-Population Accounting

This is the largest current technical inconsistency.

When an offensive unit moves:

- Its `currentTile` changes.
- The origin tile's used population is not released.
- The destination tile's used population is not increased.

When that moved unit later dies, population is released from its recorded destination tile instead of necessarily from the tile that originally funded it.

The same issue can occur when `/troops station` changes a unit's tile.

This can cause:

- Stranded used population on an origin tile
- Incorrect free population on a destination tile
- Capacity totals and stationed-unit totals disagreeing

### 5. Tile Transfer Keeps the Tile Pool

Faction aggregation uses the conquest tile's current owner. Therefore, when a tile is transferred, its recorded population pool effectively becomes part of the new owner's faction total.

There is no explicit migration, reset, compensation, or validation step during transfer.

Tracked units belonging to the old faction are not automatically moved or reassigned.

### 6. Tile Farmhand Fields Are Not Active

`farmhandTotal` and `farmhandUsed` exist on tile records and are displayed when nonzero, but current farmhand hiring uses player-level slots instead.

### 7. Population Growth Has No Built-In Cost Here

The `/population add` and tile editing controls directly change totals once permission checks pass.

There is currently no built-in:

- Food cost
- Coin cost
- Building requirement
- Growth timer
- Tile terrain modifier
- Maximum based on settlements

Those would be new systems and need explicit design.

### 8. Defensive Units Cannot Move

The movement code excludes defensive units completely. There is no redeployment, conversion, or evacuation path for them.

### 9. Movement Distance Is Manual

The player supplies `distanceTiles`. The server does not currently verify that the supplied distance matches the actual tile map.

## Planning Questions

These are useful questions for a population/tile redesign discussion.

### Population Source

- Should player population continue to exist separately from tile population?
- Should player totals represent national population while tile totals represent local manpower?
- Should population be generated by controlled tiles instead of manually added?
- Should settlement buildings, farms, terrain, or time control growth?

### Allocation

- Should a faction king allocate a faction-wide population pool among tiles?
- Should players own personal population, or should all population belong to the faction?
- Should hiring let the player select the source tile?
- Should a unit always remain financially attached to its source tile?

### Offensive and Defensive Roles

- Should defensive population be cheaper, stronger locally, or immobile?
- Should defensive units be convertible to offensive units at a cost?
- Should offensive units consume supply while traveling?
- Should a tile require a minimum defensive garrison?

### Movement

- Should used population move from origin to destination with the army?
- Should movement reserve destination capacity?
- What happens if the destination lacks capacity when the army arrives?
- Should actual map distance be calculated automatically?
- Should roads, terrain, mounts, or alliances affect travel speed?

### Conquest and Transfer

- Does a captured tile keep its population?
- Is population killed, displaced, converted, or temporarily unavailable?
- Do stationed enemy troops remain, retreat, or become prisoners?
- Should transferring a tile require resolving its stationed units first?

### Farmhands and Economy

- Should farmhands be local to tiles?
- Should farms generate population growth, food, or both?
- Should Trade alliance population costs consume farmhand slots permanently?
- Should farmhands and soldiers draw from the same population pool?

### Alliance Requirements

- Should alliance population requirements use player totals, tile totals, available totals, or stationed population?
- Should 50 population be permanently spent, temporarily reserved, or merely checked?
- Should the receiving faction gain anything from the committed population?

### User Interface

- Which screen should be the source of truth: Population, faction overview, or Tile Command?
- Should the Population GUI explain the difference between player and tile pools?
- Should faction kings have a population allocation dashboard?
- Should each unit show its source tile and current tile?

## Suggested Next Design Goal

A clean next step would be to decide on one authoritative model:

### Option A: Faction-and-Tile Model

- Population belongs to the faction.
- Tiles generate and store population.
- Units consume population from their current tile.
- Moving units transfers used capacity between tiles.
- Player population totals are removed or changed into a summary.

### Option B: Player-and-Deployment Model

- Population belongs to individual players.
- Tiles only track where units are deployed.
- Hiring consumes player capacity.
- Tile capacity represents a garrison limit rather than the source of the unit.

### Option C: National Plus Local Model

- Player/faction population is the national pool.
- Tile population is a local allocation from that pool.
- Allocating population to a tile reduces unallocated national population.
- Hiring consumes the tile's allocation.
- Moving units transfers local allocation or supply responsibility.

The current implementation is closest to Option C in appearance, but the national and local pools are not currently linked.

## Prompt for Further ChatGPT Planning

You can provide this document to ChatGPT with the following prompt:

> I am planning the KOME population and conquest tile systems for a Minecraft LOTR server addon. Read the attached implementation document carefully. Help me choose a coherent authoritative population model without inventing unrelated gameplay systems. First identify contradictions and player-facing confusion in the current implementation. Then compare faction-and-tile, player-and-deployment, and national-plus-local models. Recommend one model, explain migration concerns, and propose an incremental implementation plan that preserves existing hired units, alliances, progression, and conquest tile data.

## Primary Source Files

- `src/main/java/kome/common/data/KOMEPlayerPopulation.java`
- `src/main/java/kome/common/data/KOMETilePopulation.java`
- `src/main/java/kome/common/data/KOMEWorldData.java`
- `src/main/java/kome/common/data/KOMEEvents.java`
- `src/main/java/kome/common/data/KOMEHiredUnitRecord.java`
- `src/main/java/kome/common/command/KOMECommandPopulation.java`
- `src/main/java/kome/common/command/KOMECommandTroops.java`
- `src/main/java/kome/common/network/KOMEPacketTilePopulationUpdate.java`
- `src/main/java/kome/common/network/KOMEPacketConquestOpenCapture.java`
- `src/main/java/kome/client/gui/KOMEGuiPopulation.java`
- `src/main/java/kome/client/gui/KOMEGuiPopulationUnits.java`
- `src/main/java/kome/client/gui/KOMEGuiConquestCapture.java`
- `src/main/java/kome/common/data/KOMEAllianceInventory.java`

