# KOME LOTR Addon 1.0.4

Release prep notes for the 1.0.4 systems update.

## Highlights

- Reworked conquest ownership defaults with waypoint defaults, tile ownership defaults, balance reporting, and faction-only current/default ruling faction state.
- Added `/kome conquest reset`, `/kome conquest balance`, waypoint default tooling, and tile-to-LOTR-waypoint association support.
- Expanded company-based troop movement with route pathfinding, route constraints, bridges, arrival points, movement records, movement debug tooling, and persistence fixes.
- Improved conquest map visuals with troop icons, bridge icons, route previews, route error panels, persistent map viewport state, and destination selection behavior.
- Reworked population GUI data and layout with clearer personal, faction, tile, player, and order views.
- Added movement history records and GUI access from Server Records.
- Improved alliance ledger/detail/permissions GUIs and alliance command behavior.
- Centralized faction key normalization so KOME treats aliases consistently:
  - Rangers of the North / Ranger North -> Dunedain
  - High Elf / High Elves -> High Elves
  - Near Harad / Harad -> Harad
- Added documentation handoff files for GUI, population/tile systems, and troop movement status.

## Validation

- `./gradlew build` passes.
- Built release jar: `build/libs/KOME-LOTR-Addon-1.0.4.jar`

## Manual Testing Focus

- Fresh world initialization with waypoint and tile defaults.
- Existing world load without population loss.
- `/kome conquest reset` preserving population data.
- Route validation around bridges/rivers and allied passage.
- Step-based troop movement through route tiles, including restart/relog during movement.
- Movement records visibility and sorting.
- Population GUI totals for reserve, allocations, faction totals, and unallocated rows.
- Faction alias behavior for Dunedain, High Elves, Harad, and Rhudel.
