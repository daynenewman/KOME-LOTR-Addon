# KOME Build system

Current source of truth for Build schema 1.

## Model and persistence

A `KOMEPlayerBuild` is a persistent, indefinitely extensible project at an exact world location. It stores a stable ID, display name, conquest tile, dimension and X/Y/Z coordinates; original builder identity and faction; current manager identity; immutable population-owning faction; timestamps; active/deletion audit state; map-marker state; committed offensive/defensive population; and `KOMEBuildContribution` records.

Contributions retain contributor UUID/name, contributor faction at submission, offensive and defensive half-hours, pending/approved/rejected/removed status, submit/decision/removal times, decision actor, and audit reason. Active totals are derived only from approved, non-removed records, grouped by player and by contributor faction. Pending records produce no population and no alliance credit.

Builds are stored in `KOMEWorldData` under `Builds`; the world-level `BuildDataSchemaVersion` is 1. A Build never completes. More approved half-hours may be added indefinitely.

## Placement and ownership

Placement is validated server-side by `KOMEBuildService.canPlace`.

- The tile must be claimed and controlled by the player's faction, a Friendly faction, or an Allied faction.
- The player's own faction is always a selectable population owner once placement is valid.
- A foreign owner must be Friendly or Allied with both the player's faction and the current tile controller. This two-edge check prevents the three-faction exploit.
- Neutral, Enemy, and Mortal Enemy owners are rejected even if a modified client submits them.
- Default homeland does not override current control. A player cannot place in enemy-occupied homeland.

The selected owner receives every unit of generated population. The builder receives contribution credit. Population ownership never follows the manager, builder, or later tile controller.

## Hours, approval, and conversion

Hours are represented canonically as integer half-hours. `KOMEBuildPopulationService.parseHalfHours` accepts ordinary decimal text representing a non-negative whole or half hour (`0`, `.5`, `0.5`, `1`, `1.0`, `1.5`, and so on). It rejects blank, negative, NaN, infinite, exponent, malformed, quarter-hour, and other non-half-hour input. The GUI converts accepted text to canonical integer half-hours before transmission; the authoritative service independently rejects negative or empty contribution values, and unsupported decimal increments cannot be represented by the wire format. The default conversion is 5 population per half-hour (10 per hour), stored as `buildPopulationPerHalfHour` and configurable through `/build config populationPerHalfHour <value>`.

Offensive half-hours generate offensive population; defensive half-hours generate defensive population. The manager's own submissions approve immediately. Any other player's submission remains pending until the current manager or an administrator approves or rejects it.

Approved credit follows the contributor and the contributor's faction recorded at contribution time. Population remains assigned to the Build owner. Removing an approved contribution reverses generated population, active player/faction totals, and any unclaimed Stage 3 milestone total.

## Management and succession

The current manager may rename the Build, review pending submissions, remove approved contributions, and request normal deletion. The original builder starts as manager.

`KOMEWorldData.reconcileBuildManagers` retains the manager only while that player remains an eligible representative of the Build's population faction. Otherwise it transfers management to that faction's current recognized king. Pending submissions remain attached to the Build and therefore follow the new manager.

If no eligible player or king exists, the Build and all population remain intact, its manager is cleared, and normal management is unavailable. An operator can inspect and repair it with `/build inspect` and `/build reassign`. Population ownership is never transferred as part of succession.

## Deletion and hostile destruction

Both actions are authoritative, remove the map marker immediately, remove pending records, reverse active hours and population, and retain a soft-deletion audit record.

- **Normal deletion**: current manager or operator; always requires GUI confirmation.
- **Hostile destruction**: recognized king of the tile controller or operator; only when the Build owner is currently hostile, the controller owns the tile now, and the tile is part of that controller's original/default homeland. It cannot remove Neutral/Friendly/Allied Builds or enemy Builds in foreign conquered territory.

Before removing hours, deleting, or destroying, the service computes the resulting source-pool and tile-effective capacity. It blocks the mutation if offensive or defensive capacity would fall below committed living-unit funding or player allocation. The denial states the committed amount and how much must be released. KOME never creates negative capacity, silently orphans a unit, or auto-kills a unit.

`KOMEBuildService.canDeleteBuild` and `canDestroyEnemyBuild` are non-mutating server preflights. They use the same authority and safety checks as the mutation paths and provide the exact reason returned to Tile Command.

## Tile Command GUI

`KOMEGuiConquestCapture` is Tile Command. Its default tab is **Builds**, followed by **Population** and **Allocations**.

The Build list supports multiple projects and owners in one tile. Detail groups owner/status, builder/manager, coordinates, and offensive/defensive hours, generated population, committed population, and available population before the contribution audit. Normal GUI flows provide creation, half-hour contribution, pending review, approval/rejection, rename, contribution removal, deletion, and eligible hostile destruction.

Creation uses the selected tile/current world coordinates supplied by the authoritative open-screen context. The owner selector contains only server-provided eligible factions and explains the difference between credit and population ownership. Offensive and defensive hours are typed fields with adjacent minus/plus controls; each button changes its field by 0.5, and valid typed input is normalized to whole/half-hour display. The population preview recalculates immediately.

Detail exposes one **Destroy Build** button rather than separate deletion and hostile-destruction controls. Its server-authored mode selects normal deletion for a manager/operator or the existing hostile-homeland path for an eligible controller king/operator. If neither path is legal, the same button is disabled and displays the authoritative reason. Both modes use `KOMEGuiConfirmationDialog`; no destructive packet is sent on Cancel or Escape.

## Map integration

`KOMEConquestMapOverlay` draws active Builds at stored X/Z coordinates. A stable ID-derived offset distinguishes co-located markers. Hover shows name, owner, and coordinates. Left-click opens Tile Command focused on that Build. Deleted/inactive/hidden Builds are absent after the next authoritative conquest-data sync.

## Operator repair surface

`/build` is permission level 2 and is not required for ordinary gameplay:

```text
/build list [tile]
/build inspect <id>
/build pools <tile>
/build reassign <id> <onlinePlayer>
/build remove <id>
/build sethours <id> <offensive|defensive> <hours>
/build config populationPerHalfHour <value>
```

`sethours` accepts half-hour increments and refuses totals below committed population. `remove` uses the same safety boundary as the GUI.

## Edge cases

- Old worlds start with an empty Build collection; legacy population is not fabricated into Builds.
- Capture changes usability, not ownership or Build coordinates.
- Contributions before alliance formation can count toward a later Stage 3 attempt if still active.
- Once Stage 3 is claimed, later Build deletion does not downgrade the claimed stage. Removed hours cannot count after an alliance break because the new relationship has a later start timestamp.
- Integer division floors foreign 50% access for odd totals.
- All mutation permissions and numeric effects are recalculated on the server; GUI text is informational.
