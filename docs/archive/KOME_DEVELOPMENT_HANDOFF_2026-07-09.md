# Legacy KOME Project Status Handoff - July 9, 2026

> Historical handoff. Use `KOME_GUI_HANDOFF.md` and `KOME_REDESIGN_COMPLETION_REPORT.md` for current source status.

> Historical baseline. Alliance/war/economy/pledge behavior was superseded by schema 5 / addon 1.0.7 on 2026-07-19. Use `ALLIANCE_SYSTEM.md`, `ALLIANCE_ADMIN.md`, `ALLIANCE_MIGRATION.md`, `KOME_SERVER_RECORDS.md`, `KOME_PRODUCE_FARMER.md`, and the 2026-07-19 audit for current behavior.

This is a full project handoff for the KOME LOTR addon, not just a summary of one chat session. It is meant to give another developer or ChatGPT enough context to help design the next stage of KOME without reverse-engineering the whole codebase first.

Project root:

```text
C:\Users\dayne\OneDrive\Desktop\The-Lord-of-the-Rings-main\KOME-LOTR-Addon
```

## Current Release State

- Current mod version: `1.0.6`
- Current branch: `master`
- Current release commit: `c1096c6 Prepare release 1.0.6`
- GitHub `master` branch has been pushed.
- Local release tag `v1.0.6` exists.
- Remote `v1.0.6` tag may still need to be pushed manually because Git Credential Manager failed inside Codex.

Manual tag push command:

```powershell
cd "C:\Users\dayne\OneDrive\Desktop\The-Lord-of-the-Rings-main\KOME-LOTR-Addon"
git push origin v1.0.6
```

Build command:

```powershell
.\gradlew build
```

Release jar:

```text
build/libs/KOME-LOTR-Addon-1.0.6.jar
```

Dev jar:

```text
build/libs/KOME-LOTR-Addon-1.0.6-dev.jar
```

## Project Purpose

KOME is a Minecraft LOTR mod addon for server-scale roleplay, conquest, faction management, population, alliances, progression, and troop logistics.

The direction is to support a structured server campaign:

- Factions own and manage conquest tiles.
- Players build population and military capacity.
- Kings and faction leaders manage land, armies, and diplomacy.
- Troops can be tracked, stationed, grouped into companies, and moved.
- Progression unlocks permissions and roleplay goals.
- Seasons of war and off-seasons are planned, with conquest resets between seasons.

The addon is already beyond a single feature. It is a connected server-management layer over LOTR faction play.

## Core Architecture

### Main Mod Entry

Important files:

- `src/main/java/kome/common/KOMEAddon.java`
- `src/main/java/kome/common/KOMECommonProxy.java`
- `src/main/java/kome/client/KOMEClientProxy.java`
- `src/main/java/kome/common/network/KOMEPacketHandler.java`

Registered commands:

- `/alliance`
- `/conquest`
- `/kome`
- `/population`
- `/progression`
- `/troops`

Client overlays registered:

- conquest map overlay
- progression menu overlay
- quota ledger overlay
- unit overview cap overlay
- entity highlight overlay
- unit trade overlay
- chat sanitizer

Network packet IDs are registered in `KOMEPacketHandler`. Many GUI actions still use chat commands, while newer systems use packets.

### Persistent World Data

Main file:

- `src/main/java/kome/common/data/KOMEWorldData.java`

This is the main persistent state holder. It stores or coordinates:

- player population
- tile population
- tile ownership
- tile transfers
- progression state
- alliances
- hired unit records
- army companies
- movement orders
- waypoint links
- lord pledges
- movement timing overrides
- server record sync data

This file is a major hub. Future refactors should be cautious because many systems depend on it.

## Conquest And Tile Ownership

### Current State

The conquest system tracks claimed tiles, faction ownership, transfer requests, waypoint links, and map visualization.

Important files:

- `src/main/java/kome/common/command/KOMECommandConquest.java`
- `src/main/java/kome/common/data/KOMEConquestTileDefaults.java`
- `src/main/java/kome/common/data/KOMEConquestTile.java`
- `src/main/java/kome/common/data/KOMETileOwnershipDefaults.java`
- `src/main/resources/assets/kome/config/kome_tile_ownership_defaults.csv`
- `src/main/java/kome/client/KOMEConquestMapOverlay.java`
- `src/main/java/kome/common/network/KOMEPacketConquestData.java`
- `src/main/java/kome/common/network/KOMEPacketConquestClaim.java`
- `src/main/java/kome/common/network/KOMEPacketConquestTransfer.java`

Main command features:

- list conquest tiles
- claim tiles
- clear/reset tiles
- transfer/trade tiles
- accept or cancel transfers
- manage waypoint links
- apply default balance/ownership

### Tile Ownership Defaults

Default ownership is stored in:

```text
src/main/resources/assets/kome/config/kome_tile_ownership_defaults.csv
```

Recent default ownership updates:

- Rhudel: `T296`, `T273`, `T292`, `T312`, `T329`, `T331`
- Dunland: `T267`, `T299`
- Rohan: `T323`, `T286`
- Mordor: `T326`
- Gondor: `T358`
- Half-trolls: `T539`, `T549`, `T567`, `T564`, `T587`, `T551`, `T573`
- High Elves: `T101`, `T181`, `T168`
- Angmar: `T055`
- Neutral: `T250`
- Isengard: `T309`, `T305`
- `T563` was removed from Morwaith and left unassigned.

### Retired Tiny Tiles

Some tiny or awkward tiles have been retired and should act like borders rather than playable conquest tiles.

Retired tiles:

- `T045`
- `T327`
- `T257`
- `T271`
- `T291`
- `T293`
- `T294`
- `T295`
- `T424`
- `T456`
- `T458`
- `T459`
- `T520`
- `T521`
- `T522`
- `T526`
- `T527`
- `T528`
- `T529`
- `T530`
- `T531`
- `T532`
- `T545`
- `T546`
- `T571`

The client tile ID map skips retired tiles so they should not behave like selectable conquest tiles.

### Map Colors

Recent map color changes:

- Durin's Folk: bluish, `0x4B6182`
- Rhudel: in-game style gold/brown, `0xC49227`
- Gundabad: Minecraft dirt-like brown, `0x866043`

Files:

- `src/main/java/kome/client/KOMEConquestMapOverlay.java`
- `src/main/java/kome/client/gui/KOMEGuiServerRecords.java`

## River Blocking, Bridges, And Movement Edges

### Current State

The map has river blockers and bridge/passages that affect movement routing. The automatic river scan has been tightened because the previous detection marked too many normal tile borders as rivers.

Important file:

- `src/main/java/kome/common/data/KOMEConquestTileDefaults.java`

Current behavior:

- Automatic river blocker detection uses stricter water-between-tiles logic.
- Bridge marker placement is preserved through a broader bridge resolver.
- Small `R` river blocker test markers remain available.
- The large cyan generated river-edge overlay was removed because it cluttered the map.
- Existing bridges should be treated as correct unless a specific bridge issue is reported.

### Explicit Open Edges

These edges were explicitly opened because they were incorrectly river-blocked:

- `T190` - `T212`
- `T194` - `T212`
- `T353` - `T371`
- `T389` - `T408`
- `T389` - `T398`
- `T410` - `T416`
- `T111` - `T128`
- `T086` - `T115`
- `T408` - `T421`
- `T415` - `T425`

### Harad Bridge Correction

The Harad bridge was corrected from:

```text
T472 - T475
```

to:

```text
T472 - T470
```

Implementation uses a bridge remap helper in `KOMEConquestTileDefaults`.

### Recommended River Workflow

The automatic scan should be treated as a baseline, not perfect truth. The best future workflow is:

1. Keep automatic detection.
2. Maintain explicit forced-open edges.
3. Maintain explicit forced-blocked river edges.
4. Maintain explicit bridge pairs.
5. Use server testing to gather corrections in a simple list.

Suggested correction format:

```text
Incorrect river block, should be open:
T123 - T124

Missing river block, should be blocked:
T200 - T201

Wrong bridge:
Current: T300 - T301
Should be: T300 - T305
```

## Population System

### Current State

KOME currently has two connected but different population concepts:

- Player population totals
- Tile population pools

The distinction matters. The Population GUI mostly displays and edits player totals, while tile population pools fund normal warrior hires.

Important files:

- `src/main/java/kome/common/data/KOMEPlayerPopulation.java`
- `src/main/java/kome/common/data/KOMETilePopulation.java`
- `src/main/java/kome/common/data/KOMEPopulationType.java`
- `src/main/java/kome/common/command/KOMECommandPopulation.java`
- `src/main/java/kome/client/gui/KOMEGuiPopulation.java`
- `src/main/java/kome/client/gui/KOMEGuiPopulationLauncher.java`
- `src/main/java/kome/client/gui/KOMEGuiPopulationUnits.java`
- `src/main/java/kome/common/network/KOMEPacketPopulationGui.java`
- `src/main/java/kome/common/network/KOMEPacketPopulationUnitsGui.java`
- `src/main/java/kome/common/network/KOMEPacketTilePopulationUpdate.java`
- `src/main/java/kome/common/network/KOMEPacketTileAllocationUpdate.java`

### Player Population

Player population stores:

- offensive total
- defensive total
- legacy offensive/defensive used fields
- selected hire type

Player population controls:

- population totals shown in the Population GUI
- combined army-capacity display
- farmhand slot calculation
- faction-wide alliance population requirements
- some progression checks
- fallback capacity checks for older units without tile assignment

Player population does not directly fund most new warrior hires. Tile population does.

### Tile Population

Each conquest tile may store:

- tile ID
- owning faction
- offensive total
- offensive used
- defensive total
- defensive used
- farmhand total
- farmhand used

Tile pools currently fund new military unit hires. A hire must fit entirely within one eligible controlled tile. Population is not combined across multiple tiles for one hire.

### Hiring Rules

Current warrior cost:

```text
base cost = ceiling(unit maximum health)
mounted surcharge = 25
final cost = base cost + mounted surcharge
```

Examples:

- 20 health ground unit costs 20 population.
- 40 health ground unit costs 40 population.
- 20 health mounted unit costs 45 population.

Hires require the `Hire Units` progression permission.

The player's selected hire type decides whether the unit is offensive or defensive:

```text
/population hiretype offensive
/population hiretype defensive
```

### Farmhands

Farmhands are tracked separately:

- require `Hire Units`
- consume one farmhand slot
- do not currently consume offensive or defensive tile population
- farmhand capacity is derived from combined player population in blocks of 25

### Population Reset Behavior

Conquest reset behavior was updated so built/placed population survives a map reset. The intended rule is:

- If a tile/build has `250` population before reset, it should still have `250` population after conquest reset.

This is important for the future season system. Map reset should not erase player/faction investment.

## Planned Season System

Season logic is not fully implemented yet. The design direction is:

- War season: active conquest and troop conflict, likely around two months.
- Off-season: rebuilding, organization, diplomacy, population changes, and prep.
- Conquest resets happen between seasons.
- Population should not simply disappear on reset.

The next major design task is deciding usable vs unusable population.

Useful future model:

- `totalPopulation`: all population generated by buildings or permanent systems.
- `usablePopulation`: population allowed for current war-season actions.
- `lockedPopulation`: population that exists but cannot currently be spent.
- `seasonCarryoverPopulation`: optional limited amount carried into the next war season.

Open questions:

- Does usable population refresh at season start?
- Does off-season convert locked population into usable population?
- Can factions stockpile population?
- Is population tied to map control, buildings, players, or all three?
- What happens to population when a tile is conquered?
- Should captured tile population transfer, lock, decay, or remain with the original faction?
- Should war-season population be capped per faction for balance?

## Progression System

### Current State

Progression gives players unlocks, goals, lord pledges, random assignments, and manually completed objectives.

Important files:

- `src/main/java/kome/common/data/KOMEProgressionAchievement.java`
- `src/main/java/kome/common/data/KOMEProgressionData.java`
- `src/main/java/kome/common/data/KOMEProgressionLord.java`
- `src/main/java/kome/common/data/KOMEProgressionTaskGenerator.java`
- `src/main/java/kome/common/command/KOMECommandProgression.java`
- `src/main/java/kome/client/gui/KOMEGuiProgression.java`
- `src/main/java/kome/client/gui/KOMEGuiLordMenu.java`
- `src/main/java/kome/client/KOMEProgressionMenuOverlay.java`
- `src/main/java/kome/common/network/KOMEPacketProgressionData.java`
- `src/main/java/kome/common/network/KOMEPacketProgressionRequest.java`
- `src/main/java/kome/common/network/KOMEPacketLordMenu.java`
- `src/main/java/kome/common/network/KOMEPacketLordAction.java`
- `src/main/java/kome/common/network/KOMEPacketLordHighlight.java`

Baseline progression permissions include:

- `Hire Units`
- `Grow Population`

Main command features:

- status
- enable/disable progression
- get/list progression
- pledge to lord
- open offerings/quota/lord inventory
- find/highlight lord
- complete/uncomplete manual objectives
- roll/reroll objectives
- grant/revoke/grantall/reset admin controls

Known design state:

- The GUI is functional but cramped and Red Book-inspired.
- Some GUI actions still use chat commands instead of packets.
- A full custom progression redesign is planned.
- Long descriptions need better layout/tooltip treatment.

## Lord And Offering Systems

KOME has lord pledge and lord-related interaction systems connected to progression.

Important files:

- `src/main/java/kome/common/data/KOMEProgressionLord.java`
- `src/main/java/kome/client/gui/KOMEGuiLordMenu.java`
- `src/main/java/kome/common/network/KOMEPacketLordMenu.java`
- `src/main/java/kome/common/network/KOMEPacketLordAction.java`
- `src/main/java/kome/common/network/KOMEPacketLordHighlight.java`

Current purpose:

- let players pledge to a lord
- track lord-related progression
- highlight or locate pledged lord
- support offerings, quotas, or lord inventory concepts

Future design questions:

- Should lords provide season bonuses?
- Should lord offerings affect usable population or alliance standing?
- Should lord loyalty reset, decay, or persist between seasons?

## Alliance System

### Current State

The alliance system supports one-way alliance requests and alliance types with tier/progression concepts.

Important files:

- `src/main/java/kome/common/data/KOMEAlliance.java`
- `src/main/java/kome/common/data/KOMEAllianceData.java`
- `src/main/java/kome/common/data/KOMEAllianceType.java`
- `src/main/java/kome/common/command/KOMECommandAlliance.java`
- `src/main/java/kome/client/gui/KOMEGuiAlliance.java`
- `src/main/java/kome/client/gui/KOMEGuiAllianceDetail.java`
- `src/main/java/kome/client/gui/KOMEGuiAllianceLedger.java`
- `src/main/java/kome/client/gui/KOMEGuiAlliancePermissions.java`
- `src/main/java/kome/client/gui/KOMEAlliancePermissions.java`
- `src/main/java/kome/common/network/KOMEPacketAllianceRequest.java`
- `src/main/java/kome/common/network/KOMEPacketAllianceData.java`
- `src/main/java/kome/common/network/KOMEPacketQuotaLedger.java`

Alliance types:

- Civil
- Military
- Trade

Main command features:

- request alliance
- accept alliance
- break/revoke alliance
- list/get alliance data
- roll/reroll quotas
- view goods/storage
- claim goods
- set/clear alliance data
- show benefits

Known design state:

- Functional but still evolving.
- GUI is mostly custom rectangle/text drawing.
- Some actions still go through chat commands.
- Needs real two-faction testing for edge cases.
- Alliance benefits and season interactions should be clarified.

Future design questions:

- Should alliances survive season reset?
- Should allied movement be allowed through allied tiles?
- Should alliance tiers unlock shared bridges, trade routes, or population benefits?
- Should military alliances affect troop movement, battles, or defensive support?

## Troop Tracking, Companies, And Movement

### Current State

KOME tracks hired units and supports company-based movement. A company is a group of specific offensive hired-unit records that moves together.

Important files:

- `src/main/java/kome/common/data/KOMEHiredUnitRecord.java`
- `src/main/java/kome/common/data/KOMEArmyCompany.java`
- `src/main/java/kome/common/data/KOMEArmyMovementOrder.java`
- `src/main/java/kome/common/data/KOMEMovementPoint.java`
- `src/main/java/kome/common/data/KOMEHaltedMovementRecord.java`
- `src/main/java/kome/common/data/KOMETileWaypointLink.java`
- `src/main/java/kome/common/command/KOMECommandTroops.java`
- `src/main/java/kome/client/gui/KOMEGuiCompanyList.java`
- `src/main/java/kome/client/gui/KOMEGuiCompanyMoveConfirm.java`
- `src/main/java/kome/client/gui/KOMEGuiMovementHistory.java`
- `src/main/java/kome/common/network/KOMEPacketCompanyListGui.java`
- `src/main/java/kome/common/network/KOMEPacketCompanyMoveConfirmGui.java`
- `src/main/java/kome/common/network/KOMEPacketCompanyMovePreviewResult.java`
- `src/main/java/kome/common/network/KOMEPacketMovementHistoryData.java`
- `src/main/java/kome/common/network/KOMEPacketMovementHistoryRequest.java`
- `src/main/java/kome/common/network/KOMEPacketUnitMapMarkers.java`

### Movement Model

Main rule:

- Moving troops changes physical location, not population funding.

Population funding stays attached to the original source fields:

- `sourceTileId`
- `sourceFaction`
- `sourceType`
- `sourcePlayer`
- allocation fields

Physical position is tracked through:

- `currentTile`
- `company.currentTile`
- movement order origin/destination/route

### Company Movement Rules

Current movement is company-based:

- offensive units can move
- defensive units cannot move
- farmhands cannot move
- units must be owned by the player/company owner unless admin
- origin and destination currently must be friendly/controlled in the implemented flow
- movement requires physical troop anchors

Speed:

- mounted-only company: `2` tiles per day
- any ground population: `1` tile per day

### Physical Despawn And Respawn

When movement starts:

- the unit entity NBT is saved
- the physical entity is removed from the origin
- the unit record receives a movement order ID
- the company becomes moving

When movement arrives:

- the unit should respawn at the destination anchor
- `currentTile` updates on arrival
- population funding remains unchanged

The system includes stale moving entity cleanup to stop moving units from reappearing at origin after relog/reload.

### Movement Limitations

Known current limitations:

- Route/pathfinding is still incomplete in places.
- Earlier docs mention temporary endpoint validation.
- Enemy movement, battles, interceptions, sieges, and allied movement permissions are not fully implemented.
- Granular unit selection for company creation is limited; creating a company can group all eligible unassigned offensive units at a tile.
- Movement UI and command flow still need playtesting on a live server.

## Unit Caps And Unit Overview

KOME includes unit cap and unit overview systems.

Important files:

- `src/main/java/kome/common/data/KOMEUnitLevelCap.java`
- `src/main/java/kome/common/data/KOMEUnitCapState.java`
- `src/main/java/kome/common/data/KOMEUnitMarker.java`
- `src/main/java/kome/client/KOMEUnitOverviewCapOverlay.java`
- `src/main/java/kome/client/KOMEUnitCapClientState.java`
- `src/main/java/kome/client/KOMEUnitTradeOverlay.java`
- `src/main/java/kome/common/network/KOMEPacketUnitCapRequest.java`
- `src/main/java/kome/common/network/KOMEPacketUnitCapSync.java`
- `src/main/java/kome/common/network/KOMEPacketUnitCapUpdate.java`
- `src/main/java/kome/common/network/KOMEUnitGuiEntry.java`

Current purpose:

- track or display unit cap state
- provide client overlays for unit overview/caps
- support unit marker data used by map/GUI systems

Further testing needed:

- confirm caps sync correctly in multiplayer
- confirm overlays do not conflict with LOTR UI
- confirm unit cap changes are visible without relog

## Server Records

### Current State

The server records screen displays public player/faction state.

Important files:

- `src/main/java/kome/common/data/KOMEServerRecordBuilder.java`
- `src/main/java/kome/client/gui/KOMEGuiServerRecords.java`
- `src/main/java/kome/common/network/KOMEPacketServerRecordRequest.java`
- `src/main/java/kome/common/network/KOMEPacketServerRecordData.java`

Displayed concepts include:

- player list
- faction/rank
- progress
- population
- pledged lord
- alliances
- controlled tiles

Known design state:

- Functional but needs visual redesign.
- Left player list should probably stay compact: player name, faction, rank.
- Details can remain on the right with better hierarchy.
- Faction icons are currently colored badges/initials, not real crests.

## GUI Inventory

KOME adds buttons to the LOTR Middle-earth menu through:

- `src/main/java/kome/client/KOMEProgressionMenuOverlay.java`

Current menu buttons:

- KOME Progression
- KOME Server Records
- KOME Alliances
- KOME Population

Major GUI files:

- `KOMEGuiProgression`
- `KOMEGuiServerRecords`
- `KOMEGuiAlliance`
- `KOMEGuiAllianceDetail`
- `KOMEGuiAllianceLedger`
- `KOMEGuiAlliancePermissions`
- `KOMEGuiPopulation`
- `KOMEGuiPopulationLauncher`
- `KOMEGuiPopulationUnits`
- `KOMEGuiCompanyList`
- `KOMEGuiCompanyMoveConfirm`
- `KOMEGuiMovementHistory`
- `KOMEGuiConquestCapture`
- `KOMEGuiLordMenu`

Overall GUI status:

- Most screens are functional.
- Many are manually drawn with rectangles and text.
- Some actions use chat commands instead of packets.
- The UI is usable for testing but not yet polished.
- A future pass should standardize theme, spacing, scrolling, and input patterns.

## Commands Overview

### `/kome`

Important features:

- conquest reset
- conquest balance
- waypoint defaults reload/apply
- admin marker visibility

### `/conquest`

Important features:

- list/get/claim/clear/reset tiles
- purge legacy state
- transfer/trade/accept/cancel transfer
- waypoint link/autolink/nearest/default management

### `/population`

Important features:

- get/gui/units
- hire type
- tile/faction population status
- allocations
- allocate/unallocate
- add/remove tile population
- set/add/remove player population

### `/progression`

Important features:

- enable/disable/status
- get/list
- pledge
- lord inventory/offerings/quota
- find lord
- complete/uncomplete/roll/reroll
- grant/revoke/grantall/reset

### `/alliance`

Important features:

- benefits
- list/get
- request/accept
- break/revoke
- roll/reroll quota
- goods/storage
- claim goods
- set/clear

### `/troops`

Important features:

- list/tile/unit/company views
- create/rebuild/snapshot companies
- preview and move companies
- movement history
- arrivals
- locate
- route/bridge/passage/block helpers
- movement timing overrides
- waypoint/anchor helpers
- recruit/station helpers
- arrival and halted movement controls

`KOMECommandTroops.java` is large and central to troop logistics. Changes there should be made carefully and tested on a server.

## Current Testing Priorities

### Conquest Reset And Population

1. Give a tile a known population value, such as `250`.
2. Reset conquest.
3. Confirm population stays `250`.
4. Repeat for multiple factions and tile types.
5. Confirm ownership reset behavior still matches expectations.

### Tile Ownership

1. Start from reset/default ownership.
2. Confirm all recently assigned tiles go to the intended factions.
3. Confirm `T563` is unassigned.
4. Confirm retired tiles do not open normal tile UI or participate as playable conquest tiles.

### Rivers And Bridges

1. Toggle small river blocker markers.
2. Check all flagged river blockers with two players.
3. Confirm false river blockers listed above are open.
4. Confirm true river blockers still block.
5. Confirm bridges remain in the correct spots.
6. Specifically test `T472` to `T470` in Harad.

### Troop Movement

1. Hire offensive units from a tile with enough population.
2. Create a company.
3. Preview movement.
4. Move the company.
5. Confirm units despawn at origin.
6. Confirm arrival/respawn works.
7. Confirm population used remains tied to the original funding source.
8. Confirm defensive units and farmhands cannot move.

### Alliances

1. Create two factions with kings/authorized users.
2. Send alliance request.
3. Accept alliance.
4. Test each alliance type: Civil, Military, Trade.
5. Test quota roll/reroll.
6. Test goods ledger and claim flow.
7. Confirm server records display alliance data.

### Progression

1. Confirm baseline permissions unlock intended actions.
2. Confirm missing `Hire Units` blocks hiring.
3. Confirm missing `Grow Population` blocks normal population growth.
4. Test manual complete/uncomplete.
5. Test roll/reroll assignment.
6. Test lord pledge/highlight.

### Server Records And GUI

1. Open each KOME menu button from LOTR menu.
2. Confirm packet-backed screens refresh correctly.
3. Confirm screens fit common client resolutions.
4. Confirm faction colors and badges are legible.

## Known Gaps And Risks

- Season system is conceptual, not fully coded.
- Usable vs unusable population is not implemented yet.
- Combat/battle resolution between moving armies is not implemented.
- Enemy tile movement and allied movement rules need design.
- River blocker accuracy still needs live map testing.
- Some GUI actions still depend on chat commands.
- Movement/pathfinding needs more testing and likely more explicit route data.
- `KOMEWorldData` and `KOMECommandTroops` are large central files; changes can have broad side effects.
- GUI style is functional but inconsistent across screens.
- There is limited automated test coverage; most validation is manual multiplayer testing.

## Suggested Next Design Work

### 1. Season And Reset Rules

Define:

- war season length
- off-season length
- what conquest reset clears
- what conquest reset preserves
- when population becomes usable/unusable
- whether alliances and troop companies persist
- whether tile ownership defaults apply every season

### 2. Population Pools

Decide whether to split population into:

- total population
- usable war population
- locked/off-season population
- reserve population
- tile-local population
- faction-wide population

The most important design issue is preventing resets from punishing building investment while still keeping war seasons balanced.

### 3. Movement And War Rules

Define:

- whether armies can enter enemy tiles
- how battles begin
- how battles resolve
- whether allied territory can be crossed
- how rivers and bridges affect route planning
- whether armies can be intercepted
- what happens when a destination changes ownership mid-movement

### 4. Admin Tools

Useful future tools:

- start season
- end season
- reset conquest for season
- lock/unlock population
- recalculate usable population
- export tile ownership
- export population state
- export river blockers
- manually force edge open/blocked
- manually add/remove bridge pair
- inspect troop company route

### 5. GUI Cleanup

Priorities:

- standardize KOME theme
- move remaining chat-command GUI actions to packets
- improve progression layout
- simplify server records list
- improve alliance detail scrolling
- improve troop company selection
- add better map debug toggles

## Key Existing Docs

More detailed system docs already exist:

- `docs/KOME_GUI_HANDOFF.md`
- `docs/KOME_POPULATION_AND_TILES.md`
- `docs/troop-movement-current-status.md`
- `docs/RELEASE_1.0.4.md`
- `docs/RELEASE_1.0.5.md`
- `docs/RELEASE_1.0.6.md`

Use this handoff as the high-level project status, then use those documents for deeper implementation details.

## Recommended ChatGPT Prompt

Use this with the full handoff:

```text
I am developing a Minecraft LOTR server addon called KOME. Read this project status handoff and help me design the next stage of the system. Focus on season rules, conquest resets, usable vs unusable population, troop movement, alliances, and admin workflows. First summarize the current system, then identify the biggest design decisions, then propose simple testable rules we can run on a small multiplayer server.
```
