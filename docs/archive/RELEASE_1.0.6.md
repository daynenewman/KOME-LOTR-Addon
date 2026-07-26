# KOME LOTR Addon 1.0.6 (historical)

> Archived release note. It does not describe the current schema-7 implementation.

Release prep notes for the 1.0.6 conquest map cleanup update.

## Highlights

- Updated conquest default ownership for Rhudel, Dunland, Rohan, Mordor, Gondor, Half-trolls, High Elves, Angmar, Isengard, and unassigned border tiles.
- Updated conquest map colors for Durin's Folk, Rhudel, and Gundabad.
- Retired small border-only conquest tiles so they no longer behave as playable tiles.
- Tightened automatic river blocker detection to reduce false river borders while preserving bridge marker placement.
- Added explicit open-edge fixes for false river blockers and remapped the Harad bridge to connect `T470 <-> T472`.
- Removed the generated cyan river-edge texture layer while keeping small river blocker testing markers.

## Validation

- `./gradlew build` passes.
- Built release jar: `build/libs/KOME-LOTR-Addon-1.0.6.jar`

## Manual Testing Focus

- Verify retired tiny tiles no longer open conquest tile UI or participate in movement routing.
- Verify the listed false river edges are open in `/troops route edge`.
- Verify the Harad bridge marker connects `T470 <-> T472`.
- Verify conquest map colors for Durin's Folk, Rhudel, and Gundabad.
