# Legacy archive: KOME GUI handoff before the schema-5 visual pass

> Archived on 2026-07-19. This file intentionally preserves obsolete one-way-alliance, coin/farmer/T4, old record-format, old screen-inventory, and unimplemented visual recommendations for historical context. It is not current implementation guidance. Use `../KOME_GUI_HANDOFF.md` instead.

> The original sections are a historical GUI inventory. The schema-5 addendum in this file and `KOME_SERVER_RECORDS.md` describe the current four-tab Alliance, Wars, conquest-confirmation, company, and pledge-departure UI. The withdrawn Production/Produce UI does not exist.

This document summarizes the current KOME GUI state so another AI/developer can continue design and implementation without reverse-engineering the whole addon first.

Project root for paths below:
`C:/Users/dayne/OneDrive/Desktop/The-Lord-of-the-Rings-main/KOME-LOTR-Addon`

## Current GUI Entry Points

### LOTR Menu Buttons

File: `src/main/java/kome/client/KOMEProgressionMenuOverlay.java`

Purpose: Injects KOME buttons into the LOTR Middle-earth menu.

Current buttons added to `LOTRGuiMenu`:

| Button ID | Label | Opens |
|---:|---|---|
| 2 | KOME Progression | `kome.client.gui.KOMEGuiProgression` |
| 3 | KOME Server Records | `kome.client.gui.KOMEGuiServerRecords` |
| 4 | KOME Alliances | `kome.client.gui.KOMEGuiAlliance` |
| 5 | KOME Population | `kome.client.gui.KOMEGuiPopulationLauncher` |

Button class used: `lotr.client.gui.LOTRGuiButtonMenu`.

Layout: Buttons are placed near the existing LOTR menu grid using fixed offsets from the menu center. This is a fragile layout if LOTR menu internals change or if more buttons are added.

## Screen Inventory

### Progression Screen

File: `src/main/java/kome/client/gui/KOMEGuiProgression.java`

Extends: `LOTRGuiMenuBase`

Purpose: Displays KOME progression achievements by group. The user can view completed tasks, roll random tasks, manually complete eligible tasks, remove manual completion, and highlight their pledged lord.

Screen size/layout:

| Property | Value |
|---|---:|
| `xSize` | 220 |
| `ySize` | 256 |
| Visible rows | 4 |
| Row height | 50 |

Textures/icons:

| Texture | Source |
|---|---|
| Page background | `LOTRGuiAchievements.pageTexture` |
| Achievement rows, checkmark, category bar, scrollbar | `LOTRGuiAchievements.iconsTexture` |
| Per-row icon | Drawn manually with rectangles and text (`?` / check mark) |

Buttons:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 0 | `<` | Previous progression group | Local state change |
| 1 | `>` | Next progression group | Local state change |
| 2 | `Find Lord` | Highlight pledged lord | Chat command `/progression findlord`, then closes GUI |

Non-`GuiButton` clickable areas:

| Area | Purpose | Trigger |
|---|---|---|
| Manual action box at each row right side | Mark complete, remove complete, or roll assignment | `/progression complete <id>`, `/progression uncomplete <id>`, or `/progression roll <id>` |
| Scrollbar | Drag to scroll rows | Local state |
| Mouse wheel | Scroll rows | Local state |

Data models:

| Data | Source |
|---|---|
| Completed IDs | Static state populated by `KOMEPacketProgressionData` |
| Assignment map | Static state populated by `KOMEPacketProgressionData` |
| Achievement definitions | `KOMEProgressionAchievement.ALL` and `KOMEProgressionAchievement.forGroup` |

Network:

| Direction | Packet/Command | Notes |
|---|---|---|
| Server -> client | `KOMEPacketProgressionData` | Updates static screen state |
| Client -> server | Chat commands | Current actions are chat-command based |

Known issues / needs:

- The current design is still Red Book-inspired and cramped.
- Long descriptions are truncated to two lines; tooltip shows full text, but the row itself can still feel dense.
- Button actions should eventually move from chat commands to proper packets.
- Needs a full custom redesign under the planned “Progression” revamp.

### Server Records Screen

File: `src/main/java/kome/client/gui/KOMEGuiServerRecords.java`

Extends: `LOTRGuiMenuBase`

Purpose: Displays public server records: player list, faction/rank, progress, population, pledged lord, alliances, and controlled tiles.

Screen size/layout:

| Property | Value |
|---|---:|
| `xSize` | `min(620, width - 36)` |
| `ySize` | `min(410, height - 44)` |
| List width | `max(260, min(340, xSize / 2 + 30))` |
| Visible rows | `max(5, (ySize - 102) / 32)` |
| Row height | 32 |

Textures/icons:

| Asset | Source |
|---|---|
| Background/panels/cards | Drawn manually with `Gui.drawRect` |
| Player/faction icon | Drawn manually as a colored faction badge with initials |

Buttons:

This screen does not use `GuiButton`; it draws button-like rectangles manually and handles clicks manually.

| Click Area | Label | Purpose | Trigger |
|---|---|---|---|
| `guiLeft + 14, guiTop + 14, 56x20` | `Menu` | Return to LOTR menu | `new LOTRGuiMenu()` |
| `guiLeft + xSize - 74, guiTop + 14, 60x20` | `Refresh` | Reload records | Sends `KOMEPacketServerRecordRequest` |
| Player rows | Player row | Select player detail | Local state |

Data models:

| Data | Source |
|---|---|
| Raw record lines | `KOMEPacketServerRecordData` |
| Parsed player rows | Internal `Record` class |
| Server-side source | `KOMEServerRecordBuilder` |

Record format parsed by GUI:

```text
SUMMARY <playerCount> <claimedTileCount>
PLAYER <uuid> <name> <faction> <rank> <progress> <population> <lord> <alliances> <tileCount> <tiles>
```

Network:

| Direction | Packet | Notes |
|---|---|---|
| Client -> server | `KOMEPacketServerRecordRequest` | Requests records |
| Server -> client | `KOMEPacketServerRecordData` | Can be chunked; GUI supports reset/complete flags |

Known issues / needs:

- Planned for a major custom redesign.
- Left list should only show player name, faction, and rank. Progression/tile counts were requested to be removed from the left-side list.
- Right detail cards should likely remain but need better hierarchy and scroll handling if content grows.
- Alliances wiring was recently fixed but should be re-tested with real two-faction data.
- Current faction icon is a colored initials badge, not a real LOTR faction crest.

### Alliance Screen

File: `src/main/java/kome/client/gui/KOMEGuiAlliance.java`

Extends: `LOTRGuiMenuBase`

Purpose: Manage one-way alliance requests and tier progression across Civil, Military, and Trade alliance types.

Screen size/layout:

| Property | Value |
|---|---:|
| `xSize` | 430 |
| `ySize` | 330 |
| Left list rows | `max(5, (ySize - 86) / 35)` |
| Left row height | 35 |
| Detail tier panel visible height | 126 |
| Tier panel height | 78 |

Textures/icons:

| Asset | Source |
|---|---|
| Background/panels/cards | Drawn manually with `Gui.drawRect` |
| No external texture | Current custom GUI is rect/text based |

Buttons in list/detail mode:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 1 | `Menu` | Return to LOTR menu | `new LOTRGuiMenu()` |
| 2 | `New` / `List` | Toggle create/list mode | Local state |
| 10 | `Civil` | Select Civil type | Local state |
| 11 | `Military` | Select Military type | Local state |
| 12 | `Trade` | Select Trade type | Local state |
| 20 | `Accept` | Accept pending selected type | Chat `/alliance accept <type> <sender> <receiver>` |
| 21 | `Ledger` | Open sender goods ledger | Chat `/alliance goods <sender> <receiver>`, then closes GUI |
| 22 | `Claim` | Receiver king claims ledger goods | Chat `/alliance claimGoods <sender> <receiver>` |
| 23 | `Roll` | Roll military/trade food quota | Chat `/alliance roll <military|trade> <sender> <receiver>` |
| 24 | `Break` | Break selected alliance type | Chat `/alliance break <type> <sender> <receiver>` |
| 25 | `Perms` / `Tiers` | Toggle permission summary vs tier sections | Local state |

Buttons in create mode:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 1 | `Menu` | Return to LOTR menu | `new LOTRGuiMenu()` |
| 2 | `List` | Return to list mode | Local state |
| 3 | `<` | Previous receiver faction | Local state |
| 4 | `>` | Next receiver faction | Local state |
| 5 | `Send Request` | Send selected alliance request | Chat `/alliance request <type> <viewerFaction> <receiverFaction>` |
| 6 | `<` | Previous alliance type | Local state |
| 7 | `>` | Next alliance type | Local state |

Mouse behavior:

| Input | Purpose |
|---|---|
| Left click row | Select alliance |
| Mouse wheel | Scroll list, or scroll selected detail panel |
| GL scissor | Clips tier/permission detail sections |

Data models:

| Data | Source |
|---|---|
| Alliance rows | `KOMEAllianceRecordBuilder` via `KOMEPacketAllianceData` |
| Viewer faction / king status | `VIEWER` row in alliance packet |
| Faction list | `LOTRFaction.values()` playable factions |
| Relations | `LOTRFactionRelations.getRelations` |
| Alliance state | `KOMEAlliance` |
| Ledger storage | `KOMEAllianceInventory` |

Record format parsed by GUI:

```text
SUMMARY <count>
VIEWER <factionKey> <factionName> <isKing:0|1> <factionHasKing:0|1>
ALLIANCE <keyA> <keyB> <displayA> <displayB> <civilTier> <militaryTier> <tradeTier> <lastUpdatedBy> <updatedWorldTime> <militaryFoodAssignment> <militaryFoodDelivered> <tradeFoodAssignment> <tradeFoodDelivered> <civilTradeDelivered> <militaryKillsDelivered> <tradeT2CoinsDelivered> <tradeFarmerPop> <militaryT4CoinsDelivered> <militaryT4Pop>
```

Network:

| Direction | Packet/Command | Notes |
|---|---|---|
| Client -> server | `KOMEPacketAllianceRequest` | Refresh alliance records |
| Server -> client | `KOMEPacketAllianceData` | Updates static alliance state |
| Client -> server | Chat commands | Most actions still use chat commands |

Known issues / needs:

- Planned for a major redesign.
- Action buttons should become packets instead of chat commands.
- Tier cards need more vertical space and better hierarchy.
- Create screen needs cleaner alignment and visual separation.
- Ledger opens vanilla inventory GUI, not an integrated custom alliance ledger screen.
- Claim button is currently only enabled for receiver king, but needs real multiplayer testing.

### Population Manager

File: `src/main/java/kome/client/gui/KOMEGuiPopulation.java`

Extends: `GuiScreen`

Purpose: Shows and adjusts player population totals, with quick access to unit breakdown.

Screen size/layout:

| Property | Value |
|---|---:|
| `PANEL_WIDTH` | 460 |
| `PANEL_HEIGHT` | 280 |
| Centered at | `width / 2`, `height / 2` |

Textures/icons:

| Asset | Source |
|---|---|
| Background/panels/cards/progress bars | Drawn manually with `drawRect` |
| No external texture | Current custom GUI is rect/text based |

Text fields:

| Field | Position | Purpose |
|---|---|---|
| `playerField` | `x + 28, y + 78, 180x18` | Target player name |
| `amountField` | `x + 28, y + 120, 80x18` | Amount to add/remove |

Buttons:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 0 | `Menu` | Return to LOTR menu | `new LOTRGuiMenu()` |
| 7 | `Units` | Open unit breakdown | Chat `/population units <player>`, then closes screen |
| 2 | `+ Off` | Add offensive pop | Chat `/population add <player> offensive <amount>` |
| 3 | `- Off` | Remove offensive pop | Chat `/population remove <player> offensive <amount>` |
| 5 | `+ Def` | Add defensive pop | Chat `/population add <player> defensive <amount>` |
| 6 | `- Def` | Remove defensive pop | Chat `/population remove <player> defensive <amount>` |

Data models:

| Data | Source |
|---|---|
| Population totals/used | `KOMEPlayerPopulation`, `KOMEWorldData.getArmyPopulationUsed` |
| Farmhands | `KOMEWorldData.getFarmhandsUsed`, `getFarmhandLimit` |

Network:

| Direction | Packet/Command | Notes |
|---|---|---|
| Server -> client | `KOMEPacketPopulationGui` | Opens/refreshes GUI |
| Client -> server | Chat commands | Add/remove/unit actions |

Known issues / needs:

- Planned for a major redesign.
- Add/remove commands keep screen up only if server sends fresh `KOMEPacketPopulationGui`; verify this after future changes.
- Should probably replace chat commands with packets and immediate GUI state refresh.
- “Set Off/Set Def” buttons were removed; do not reintroduce unless requested.
- Defensive population cost is no longer half.

### Population Launcher

File: `src/main/java/kome/client/gui/KOMEGuiPopulationLauncher.java`

Extends: `GuiScreen`

Purpose: Lightweight bridge screen opened from LOTR menu. Immediately sends `/population gui <playerName>` and closes.

Buttons: none.

Network:

| Trigger | Action |
|---|---|
| `initGui()` | Chat `/population gui <currentPlayerName>` then `mc.displayGuiScreen(null)` |

Known issues / needs:

- This should be replaced by a packet-based open request, or by opening a loading screen while waiting for server data.

### Population Units Screen

File: `src/main/java/kome/client/gui/KOMEGuiPopulationUnits.java`

Extends: `GuiScreen`

Purpose: Shows tracked hired units by type: Offensive, Defensive, Farmhands.

Screen size/layout:

| Property | Value |
|---|---:|
| Base panel origin | `width / 2 - 150`, `height / 2 - 100` |
| List width | 308 |
| Visible rows | 9 |
| Row height | 12 |

Textures/icons:

| Asset | Source |
|---|---|
| Background/list panels | Drawn manually with `drawRect` |
| No external texture | Current custom GUI is rect/text based |

Buttons:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 0 | `Back` | Return to population manager | Chat `/population gui <playerName>`, closes screen |
| 1 | `Offensive (#)` | Select offensive tab | Local state |
| 2 | `Defensive (#)` | Select defensive tab | Local state |
| 3 | `Farmhands (#)` | Select farmhand tab | Local state |

Data models:

| Data | Source |
|---|---|
| Lines | `KOMEPacketPopulationUnitsGui` |
| Parsed unit rows | Internal `UnitLine` parsing `UNITCAP` rows |

Expected line format:

```text
UNITCAP <entityUUID> <displayName> <kind> <cost> <mounted:true|false> <levelCap>
```

Network:

| Direction | Packet/Command | Notes |
|---|---|---|
| Server -> client | `KOMEPacketPopulationUnitsGui` | Opens screen with lines |
| Client -> server | Chat `/population gui <playerName>` | Back button |

Known issues / needs:

- No per-unit actions are currently available here.
- Unit cap adjustment is handled by a separate overlay on the LOTR unit overview, not this screen.

### Conquest Tile Menu

File: `src/main/java/kome/client/gui/KOMEGuiConquestCapture.java`

Extends: `GuiScreen`

Purpose: Tile-specific pop-up opened from the LOTR map. Allows claiming a tile, initiating/accepting/canceling king-to-king tile transfers, and opening troop movement.

Screen size/layout:

| Property | Value |
|---|---:|
| Content origin | `width / 2 - 120`, `height / 2 - 96` |
| Buttons origin | `width / 2 - 90`, `height / 2 + 54` |
| Main summary panel | 232x48 |

Textures/icons:

| Asset | Source |
|---|---|
| Background/summary | Drawn manually with `drawRect` |
| No external texture | Current custom GUI is rect/text based |

Buttons, normal mode:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 0 | `Claim` | Claim tile for viewer faction | Sends `KOMEPacketConquestClaim(tileId)`, returns to `LOTRGuiMap` |
| 1 | `Cancel` / `Back` | Return to map | `new LOTRGuiMap()` |
| 5 | `Sell/Trade` | Enter transfer mode | Local state |
| 8 | `Move Troops` | Open troop move screen | `new KOMEGuiTroopMove(tileId, offensivePop, mountedPop, groundPop)` |
| 6 | `Accept` | Accept pending tile transfer | Sends `KOMEPacketConquestTransfer(..., ACCEPT)`, returns to map |
| 7 | `Cancel Offer` | Cancel pending outgoing transfer | Sends `KOMEPacketConquestTransfer(..., CANCEL)`, returns to map |

Buttons, transfer mode:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 2 | `<` | Previous receiver faction | Local state |
| 3 | `>` | Next receiver faction | Local state |
| 4 | `Transfer` | Offer tile transfer | Sends `KOMEPacketConquestTransfer(..., OFFER)`, returns to map |
| 1 | `Cancel` | Return to map | `new LOTRGuiMap()` |

Data models:

| Data | Source |
|---|---|
| Tile owner/pending transfer | `KOMEConquestTile` via `KOMEPacketConquestCaptureGui` |
| Viewer faction | Server-provided `viewerFaction` in `KOMEPacketConquestCaptureGui` |
| Troop summary | Server-generated in `KOMEPacketConquestOpenCapture` |

Network:

| Direction | Packet | Notes |
|---|---|---|
| Client -> server | `KOMEPacketConquestOpenCapture` | Sent by map overlay on right click |
| Server -> client | `KOMEPacketConquestCaptureGui` | Opens tile menu |
| Client -> server | `KOMEPacketConquestClaim` | Claim tile |
| Client -> server | `KOMEPacketConquestTransfer` | Offer/accept/cancel transfer |

Known issues / needs:

- UI is functional but visually primitive.
- Uses `new LOTRGuiMap()` to return to the map; this may lose map state/zoom.
- Claiming rules are currently very permissive for players in their faction.
- Needs tighter integration with future battle/movement systems.

### Troop Move Screen

File: `src/main/java/kome/client/gui/KOMEGuiTroopMove.java`

Extends: `GuiScreen`

Purpose: Creates a troop movement order from an origin tile to a typed destination tile.

Screen size/layout:

| Property | Value |
|---|---:|
| Panel size | 300x205 |
| Origin | `width / 2 - 150`, `height / 2 - 105` |

Text fields:

| Field | Position | Purpose |
|---|---|---|
| `destinationField` | `x + 124, y + 58, 112x18` | Destination tile ID |
| `populationField` | `x + 124, y + 88, 70x18` | Population to move |
| `distanceField` | `x + 124, y + 118, 70x18` | Distance in tiles |

Buttons:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 0 | `Move` | Issue movement order | Chat `/troops move <origin> <destination> <population> <distance> <filter>`, returns to map |
| 1 | `Cancel` | Return to map | `new LOTRGuiMap()` |
| 2 | `Type: all/mounted/ground` | Cycle movement filter | Local state |

Data models:

| Data | Source |
|---|---|
| Origin tile | Constructor argument from `KOMEGuiConquestCapture` |
| Available pop/mounted/ground | Constructor arguments from tile menu |
| Server movement model | `KOMEArmyMovementOrder`, `KOMEHiredUnitRecord` |

Known issues / needs:

- Destination and distance are manual text fields; no pathfinding or tile adjacency support yet.
- Uses chat command instead of packet.
- Needs visual destination picking from the map.
- Needs battle integration and accidental encounter rules.

### Lord Menu

File: `src/main/java/kome/client/gui/KOMEGuiLordMenu.java`

Extends: `LOTRGuiMenuBase`

Purpose: Opens when shift-clicking a pledge lord/captain. Allows pledging, opening offerings, or highlighting current lord.

Screen size/layout:

| Property | Value |
|---|---:|
| `xSize` | 220 |
| `ySize` | 140 |

Textures/icons:

| Asset | Source |
|---|---|
| Buttons | `LOTRGuiButtonRedBook` |
| Background | Default darkened background; no custom panel texture |

Buttons:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 0 | `Pledge to this lord` | Pledge to selected lord | Sends `KOMEPacketLordAction(entityId, PLEDGE)`, closes |
| 1 | `Open offerings` | Open lord offering inventory | Sends `KOMEPacketLordAction(entityId, OFFERINGS)`, closes |
| 2 | `Highlight lord` | Client-side highlight of current entity | Calls `KOMEEntityHighlightOverlay.highlight(entityId, lordName)`, closes |

Data models:

| Data | Source |
|---|---|
| Entity ID/name/faction/currentLord | `KOMEPacketLordMenu` |
| Pledged lord | `KOMEPlayerProgression` |
| Offerings | `KOMEProgressionOfferingInventory` |

Known issues / needs:

- Highlight only works when the lord entity is loaded/nearby. There is also server-side `/progression findlord` behavior for stored location.
- Visual style is minimal.

## Overlay GUI Work

### Conquest Map Overlay

File: `src/main/java/kome/client/KOMEConquestMapOverlay.java`

Purpose: Draws conquest tile overlays on the LOTR map, handles hover/click behavior, draws claimed tile colors, borders, labels, and troop markers.

Textures/resources:

| ResourceLocation | File |
|---|---|
| `kome:map/reset_conquest_tile_ids.png` | `src/main/resources/assets/kome/map/reset_conquest_tile_ids.png` |
| `kome:map/reset_conquest_tile_ids.txt` | `src/main/resources/assets/kome/map/reset_conquest_tile_ids.txt` |
| `kome:map/reset_conquest_borders_thin.png` | `src/main/resources/assets/kome/map/reset_conquest_borders_thin.png` |
| `kome:map/reset_conquest_labels.png` | `src/main/resources/assets/kome/map/reset_conquest_labels.png` |

Also present but not currently referenced here:

| File |
|---|
| `src/main/resources/assets/kome/map/reset_conquest_borders.png` |
| `src/main/resources/assets/kome/map/reset_conquest_hitmap.png` |
| `src/main/resources/assets/kome/map/reset_conquest_overlay.png` |

Controls:

| Input | Purpose | Trigger |
|---|---|---|
| Left click small `C` button | Toggle conquest overlay visibility | Local static `showConquestTiles` |
| Right click hovered tile | Open conquest tile menu | Sends `KOMEPacketConquestOpenCapture(tileId)` |
| Hover tile | Draw highlight and tooltip | Local dynamic texture |

Rendering assumptions:

- Uses reflection to read private/static LOTR map fields: `mapXMin`, `mapXMax`, `mapYMin`, `mapYMax`, `mapWidth`, `mapHeight`, `zoomScale`, `posX`, `posY`, `isConquestGrid`, `hasOverlay`.
- Uses `LOTRGenLayerWorld.imageWidth/imageHeight` to map LOTR map coordinates to resource image pixels.
- Uses dynamic textures for hover and claimed overlays.
- Uses `GL11` and `Tessellator` directly to draw map-space overlays.

Data models:

| Data | Source |
|---|---|
| Claimed tiles | `KOMEClientData.INSTANCE.conquestTiles` from `KOMEPacketConquestData` |
| Movement orders | `KOMEClientData.INSTANCE.armyMovements` from `KOMEPacketConquestData` |
| Troop summaries | `KOMEClientData.INSTANCE.troopSummaries` from `KOMEPacketConquestData` |
| Tile IDs/centers | Loaded from `reset_conquest_tile_ids.txt` and tile mask image |

Known issues / needs:

- Reflection against LOTR map internals is fragile.
- Right-click opens menu; left-click is reserved for map panning and overlay toggle.
- Tile border assets should be treated carefully. User has repeatedly requested that good borders not be regenerated unnecessarily.
- Troop markers exist, but movement UX is still rough.

### Unit Trade Overlay

File: `src/main/java/kome/client/KOMEUnitTradeOverlay.java`

Purpose: Adds a population hire-type switch to unit trading GUI.

Button:

| Button ID | Label | Purpose | Trigger |
|---:|---|---|---|
| 60420 | Dynamic hire type label | Cycle next hire population type | Chat `/population hiretype <offensive|defensive>` |

Known issues / needs:

- Overlay relies on detecting LOTR unit trade GUI layout.
- Uses chat commands.

### Unit Overview Cap Overlay

File: `src/main/java/kome/client/KOMEUnitOverviewCapOverlay.java`

Purpose: Adds small `-`, `+`, and `x` controls to unit overview rows for level caps.

Button ID scheme:

| Constant | Meaning |
|---|---|
| `BUTTON_BASE` | Base ID for cap buttons |
| `BUTTONS_PER_ROW` | Per-row action count |
| `MAX_BUTTON_ROWS` | Max rows supported |

Button action mapping:

| Label | Action |
|---|---|
| `-` | Lower cap |
| `+` | Raise cap |
| `x` | Clear cap |

Network:

| Direction | Packet |
|---|---|
| Client -> server | `KOMEPacketUnitCapRequest` |
| Client -> server | `KOMEPacketUnitCapUpdate` |
| Server -> client | `KOMEPacketUnitCapSync` |

Known issues / needs:

- Not part of the four planned next pages, but it is active GUI work.
- Must be tested on real LOTR unit overview rows at different GUI scales.

### Quota Ledger Overlay

File: `src/main/java/kome/client/KOMEQuotaLedgerOverlay.java`

Purpose: Displays side ledger lines for progression/lord/alliance quota inventory deposits.

Network:

| Direction | Packet |
|---|---|
| Server -> client | `KOMEPacketQuotaLedger` |

Known issues / needs:

- Text clipping was previously an issue; keep side overlay width in mind.
- Used by both lord progression offerings and alliance ledger feedback.

### Entity Highlight Overlay

File: `src/main/java/kome/client/KOMEEntityHighlightOverlay.java`

Purpose: Draws world-space highlighting for entities/locations, currently used for lord highlighting.

Rendering:

- Uses raw `GL11` line drawing.
- Disables texture/lighting/depth for visibility.

Network:

| Direction | Packet |
|---|---|
| Server -> client | `KOMEPacketLordHighlight` |

Known issues / needs:

- Needs clearer UX for far-away/offline/unloaded pledged lord cases.

## Packet Inventory Relevant To GUI

File: `src/main/java/kome/common/network/KOMEPacketHandler.java`

Registered GUI-related messages:

| ID | Packet | Side | Purpose |
|---:|---|---|---|
| 0 | `KOMEPacketPopulationGui` | Client | Open/update population manager |
| 3 | `KOMEPacketPopulationUnitsGui` | Client | Open population units screen |
| 4 | `KOMEPacketHireType` | Client | Sync hire type |
| 5 | `KOMEPacketConquestCaptureGui` | Client | Open conquest tile menu |
| 6 | `KOMEPacketConquestClaim` | Server | Claim conquest tile |
| 7 | `KOMEPacketConquestOpenCapture` | Server | Request conquest tile menu |
| 9 | `KOMEPacketProgressionData` | Client | Sync progression screen data |
| 10 | `KOMEPacketQuotaLedger` | Client | Update quota ledger overlay |
| 11 | `KOMEPacketServerRecordRequest` | Server | Request server records |
| 12 | `KOMEPacketServerRecordData` | Client | Sync server records |
| 13 | `KOMEPacketConquestData` | Client | Sync conquest tiles/movements/troops |
| 14 | `KOMEPacketUnitCapRequest` | Server | Request selected unit cap |
| 15 | `KOMEPacketUnitCapUpdate` | Server | Update selected unit cap |
| 16 | `KOMEPacketUnitCapSync` | Client | Sync selected unit cap |
| 17 | `KOMEPacketAllianceRequest` | Server | Request alliance records |
| 18 | `KOMEPacketAllianceData` | Client | Sync alliance screen data |
| 19 | `KOMEPacketConquestTransfer` | Server | Offer/accept/cancel tile transfer |
| 20 | `KOMEPacketLordMenu` | Client | Open lord menu |
| 21 | `KOMEPacketLordAction` | Server | Pledge/open offerings |
| 22 | `KOMEPacketLordHighlight` | Client | Highlight lord/location |
| 32 | `KOMEPacketAllianceAction` | Server | Whitelisted request/accept/break/roll/ledger/claim intent; server recomputes identity and authority |
| 33 | `KOMEPacketPledgeDepartureRequest` | Server | Request only the sender's authoritative departure preview |
| 34 | `KOMEPacketPledgeDepartureData` | Client | Open/refresh the departure preview GUI |

## Core GUI Data Models

### `KOMEWorldData`

File: `src/main/java/kome/common/data/KOMEWorldData.java`

Server/world saved state backing most GUI data:

- `populations`: `UUID -> KOMEPlayerPopulation`
- `progressions`: `UUID -> KOMEPlayerProgression`
- `hiredUnits`: `UUID -> KOMEHiredUnitRecord`
- `conquestTiles`: `String -> KOMEConquestTile`
- `alliances`: `String -> KOMEAlliance`
- `armyMovements`: `String -> KOMEArmyMovementOrder`
- `playerNames`
- faction king maps
- progression enabled flag

### `KOMEClientData`

File: `src/main/java/kome/common/data/KOMEClientData.java`

Client mirror of selected `KOMEWorldData` fields. Used heavily by `KOMEConquestMapOverlay`.

### `KOMEAlliance`

File: `src/main/java/kome/common/data/KOMEAlliance.java`

One-way alliance record:

- Sender faction `factionA`
- Receiver faction `factionB`
- `civilTier`, `militaryTier`, `tradeTier`
- Assignment strings and delivered counters
- Claimable virtual goods
- 9-slot storage array (`STORAGE_SLOTS = 9`)

Tier constants:

| Constant | Value |
|---|---:|
| `NONE` | -1 |
| `PENDING` | -2 |

### `KOMEAllianceInventory`

File: `src/main/java/kome/common/data/KOMEAllianceInventory.java`

IInventory used by alliance ledger deposits.

Important current costs:

| Requirement | Cost |
|---|---:|
| Civil T1 coins | 1000 |
| Trade T1 coins | 5000 |
| Trade T2 coins | 10000 |
| Military T4 coins | 30000 |
| Military T4 population | 50 |

### `KOMEPlayerProgression`

File: `src/main/java/kome/common/data/KOMEPlayerProgression.java`

Tracks completed progression IDs, random assignments, quota delivered values, pledged lord info/location, and offering inventory.

### `KOMEProgressionAchievement`

File: `src/main/java/kome/common/data/KOMEProgressionAchievement.java`

Static achievement definitions consumed by progression GUI.

### `KOMEPlayerPopulation`

File: `src/main/java/kome/common/data/KOMEPlayerPopulation.java`

Tracks offensive/defensive population totals and used values. Farmhand limit derives from combined total.

### `KOMEHiredUnitRecord`

File: `src/main/java/kome/common/data/KOMEHiredUnitRecord.java`

Tracks hired unit owner, type, cost, farmhand status, current tile, level cap, and movement order link.

### `KOMEConquestTile`

File: `src/main/java/kome/common/data/KOMEConquestTile.java`

Tracks tile owner, claim time, and pending transfer fields.

### `KOMEArmyMovementOrder`

File: `src/main/java/kome/common/data/KOMEArmyMovementOrder.java`

Tracks troop movement from origin tile to destination tile with real-time arrival timestamp.

### `KOMETileTroopSummary`

File: `src/main/java/kome/common/data/KOMETileTroopSummary.java`

Client-side summary for troop markers on conquest map.

## Custom GuiButton Classes

No KOME-specific `GuiButton` subclass currently exists.

External/custom-ish button classes used:

| Class | Source | Used By |
|---|---|---|
| `lotr.client.gui.LOTRGuiButtonMenu` | LOTR Mod | `KOMEProgressionMenuOverlay` |
| `lotr.client.gui.LOTRGuiButtonRedBook` | LOTR Mod | `KOMEGuiProgression`, `KOMEGuiLordMenu` |
| `net.minecraft.client.gui.GuiButton` | Minecraft | Most KOME screens |

Recommendation: If redesigning all four planned screens, create a KOME button helper or subclass for consistent hover/disabled states. Minecraft 1.7.10 buttons are visually vanilla and inconsistent with current custom panel style.

## Rendering / Minecraft 1.7.10 Constraints

- Minecraft 1.7.10 GUI scaling uses `ScaledResolution`; scissor rectangles need scale conversion. `KOMEGuiAlliance.enableScissor` is a working example.
- `GuiScreen` coordinates are scaled screen coordinates, not raw display pixels.
- `drawRect` is the safest portable primitive; custom textures require correct `ResourceLocation` and texture dimensions.
- `drawTexturedModalRect` assumes 256x256 texture atlas coordinates unless overridden by the bound texture usage pattern.
- `FontRenderer` has limited wrapping; use `listFormattedStringToWidth` and manually cap lines.
- Avoid negative letter spacing or viewport-scaled fonts; Minecraft bitmap font is unforgiving.
- `LOTRGuiMenuBase` provides `guiLeft`, `guiTop`, `xSize`, `ySize`, and LOTR menu return behavior, but many screens set `buttonMenuReturn = null` to avoid the default back button.
- `LOTRGuiMap` internals are accessed by reflection in the conquest overlay. This is brittle and can break if the LOTR mod changes field names.
- Dynamic textures must call `updateDynamicTexture()` after data changes.
- OpenGL state leaks are easy. Use `GL11.glPushAttrib` / `glPopAttrib` when changing blend/depth/lighting/texture state.
- Forge SimpleNetworkWrapper packet IDs must stay unique and stable per channel.
- Client-only GUI classes must not be referenced from dedicated-server-only code paths except through proxy methods.
- Many current UI actions still use chat commands via `KOMEMinecraftClient.sendChat`. That works, but it is brittle, visible to command permission logic, and harder to localize. Prefer packets for future GUI actions.

## Planned Next GUI Work

The user specifically named these screens as planned next:

1. Progression
2. Server Records
3. Alliances
4. Population

Recommended order:

1. Build a small shared GUI style toolkit:
   - Panel/background helpers
   - KOME button style
   - Icon/faction badge renderer
   - Text wrapping helpers
   - Scroll panel helper
2. Replace chat-command GUI actions with packets where feasible.
3. Redesign `KOMEGuiAlliance` first because it has the highest current complexity and most gameplay actions.
4. Redesign `KOMEGuiServerRecords` second because it should become the Google Sheet replacement.
5. Redesign `KOMEGuiPopulation` and `KOMEGuiPopulationUnits` together because they share data and navigation.
6. Redesign `KOMEGuiProgression` last unless progression becomes active priority again; it is currently usable and the team is focused more on conquest/alliance/population.

## 2026-07-19 schema-5 GUI addendum

- Alliance pair tabs are Overview, Requirements, Benefits, and Military. Trade Posts and the provisional Production/Produce UI are completely absent.
- Benefits starts at T1, labels cards Active/Locked/Suspended/Planned, and reads shared common metadata. Trade T2 shows `Additional Produce Farmer Slot` and its future-integration description with no action.
- Requirements consumes authoritative per-side `TRACK` records for rolled/delivered item quota, activity, Military population, completion/waiver/grace/invalid state, and the exact remainder. It labels the viewer contribution, partner progress, contribution ledger, incoming claim, and requirement roll without reversing sides.
- Military consumes `MILITARY_CONTEXT` and `MILITARY_COMPANY` records. It distinguishes voluntary delegation from Wartime Stewardship and displays native faction, actual owner, recognized controller/king state, dormant/revocation reason, active authorizing wars, opponent union, one global population pool, tendency, movement/cleanup, and only the server-authorized limited actions.
- Conquest capture displays hostile/allied consequences, whether confirmation is armed, and the existing/new war destination. The second confirmation remains server state, not client state.
- Company cards carry native faction, actual owner, controller/authority, authorized wars, legal targets, population source, movement state, and cleanup state. `WAR_ENDED_HALTED` exposes Retreat only.
- Companies includes a `Departure` action that opens a typed, scrollable server-authored preview. It shows units/farmhands/companies, expected population returns and sources, movement cancellations, offers/completed-transfer behavior, pending/quarantine, and the removal warning.
- Server Records has Players/Wars pages and All/Active/Ending/Ended war filters. War detail contains factions, membership provenance (capture/manual/automatic T3), supporting factions and authorized king UUID/name, dormant/contradiction reasons, captures/admin history, controllers/reservations/revocations, withdrawals/demobilizations, warnings, and end reason.
- Alliance request/accept/break/roll/ledger/claim/company-navigation/history, company list/create/tendency/movement/reclaim/disband/move, recruitment-tile, population-unit navigation, and pledge-preview actions use typed packets. The server validates sender/current state and returns exact rejection chat plus authoritative refresh.
- Back/refresh preserve page, selected alliance pair, tab, war filter, and reasonable scroll where implemented. Operator View is explicit, session-only, defaults off, and changes visibility only; display text never grants access or T3 authority.

## Implementation Gaps / TODOs

### General

- Create consistent KOME visual language across all screens.
- Replace chat commands with packet actions:
  - `/alliance ...`
  - `/population ...`
  - `/progression ...`
  - `/troops ...`
- Add explicit refresh packets after state-changing GUI actions so screens update immediately.
- Add consistent disabled-state text/tooltips explaining why actions are unavailable.
- Ensure deopped players can use normal gameplay actions without OP-only bypasses.

### Progression

- Full custom redesign away from the current small Red Book page.
- Better long-description presentation.
- Better pledged lord display and “find lord” UX.
- Decide whether manual completion should remain visible for non-auto tasks only.

### Server Records

- Left-side player list should only show name, faction, and rank.
- Right-side detail view should better organize population, lord, alliances, and tiles.
- Add scrolling detail cards if alliances/tiles overflow.
- Consider real faction icons instead of colored initials.

### Alliances

- Redesign list/create/detail pages.
- Tier sections need more space and clearer next-action flow.
- Add an integrated ledger/progress view instead of opening vanilla inventory as a separate context.
- Continue converting remaining non-alliance chat conveniences where a dedicated packet materially improves the workflow.
- Re-test one-way alliance permissions for sender faction, receiver king, kingless factions, and deopped players.

### Population

- Redesign manager and units pages as one coherent workflow.
- Keep screen open and refresh instantly after add/remove actions.
- Remove admin-ish affordances from normal player view if they are not meant for regular players.
- Consider replacing player-name text field with current player by default and admin-only target picker.

### Conquest / Movement

- Tile menu needs better styling.
- Troop movement needs map-based destination selection and automatic distance/path calculation.
- Show clearer ownership/pending transfer state.
- Preserve map state when returning from popups instead of constructing a new `LOTRGuiMap`.
