# KOME GUI handoff

Current source of truth for the schema-7 Build/population/alliance redesign. Superseded screen inventories are in `archive`.

## Shared visual language

All current screens use `KOMEGuiTheme`, `KOMEGuiButton`, `KOMEGuiScrollPanel`, and `KOMEGuiConfirmationDialog`: dark brown/stone backgrounds, muted red headings, gold borders and selection, cream text, green completed/active state, amber pending/warning state, red denial/destructive state, and visibly dimmed disabled actions with readable reasons.

`KOMEGuiTheme` provides panels, cards, headers, faction badges, status chips, progress bars, warning banners, wrapped text/tooltips, and scaled scissoring. `KOMEGuiScrollPanel` clamps wheel/drag scroll and converts logical Minecraft GUI coordinates through `ScaledResolution` before setting the OpenGL scissor. Fixed navigation/actions are drawn outside scroll transforms.

## Tile Command

Class: `KOMEGuiConquestCapture`.

The title retains waypoint-first tile naming. Tabs are **Builds** (default), **Population**, and **Allocations**. Switching tabs preserves the selected tile; authoritative refresh keeps the active tab and valid Build selection.

### Builds

The list card click region and `View` button open detail. `Create Build` opens the in-GUI name, coordinates/tile, population-owner, offensive half-hour, defensive half-hour, ownership warning, preview, confirm, and cancel flow.

Build detail exposes Add Hours, Rename, pending Approve/Reject, active-contribution Remove, Delete, and eligible Destroy Enemy. Contribution rows scroll independently when necessary. Disabled buttons use the server reason. Delete/Destroy open confirmation and only send after Confirm.

### Population and allocations

Population is a scrollable faction-pool list showing native/Build, physical/effective, used, and available offensive/defensive values plus captured-half state. Allocations remains a separate tab with explicit add/reclaim controls; reclaim means reducing an allocation that is not already used by a living unit.

## Conquest-map Builds

Class: `KOMEConquestMapOverlay`.

Markers use saved coordinates and a stable ID offset so multiple Builds in one tile remain distinguishable. Hover region covers the marker and shows name, owner faction, and coordinates. Left-click opens Tile Command focused on the Build. Marker handling is inserted without consuming unrelated map buttons/drag behavior.

## Companies and units

Classes: `KOMEGuiCompanyList`, `KOMEGuiPopulationUnits`, `KOMEUnitOverviewCapOverlay`.

Manual company creation is retired. The server creates/reuses the one company keyed by owner UUID plus immutable source tile. Company list/detail exposes an obvious Rename action to the owner; renaming changes display text only. Existing move, tendency, delegation/reclaim, transfer, history, and destructive confirmation flows remain server-authorized.

## Unified Alliance

Class: `KOMEGuiAllianceUnified`.

This is the only active alliance list/create/detail GUI. The deleted `KOMEGuiAlliance`, `KOMEGuiAllianceDetail`, and permissions screen are archived only in Git history.

- **List**: one row per mutual relationship; clearly labels the viewer's directional stage, partner stage, shared relation, pending/kingless state, and current benefit.
- **Create/request**: cycles only typed server `REQUEST_OPTION_V2` candidates. Long names truncate with a wrapped tooltip/reason. Send is unreachable for server-ineligible factions even with a forged packet.
- **Detail**: retains relationship selection and tab on refresh. Tabs are Overview, Requirements, Benefits, and Military. The direction header uses “Your Progress Toward …” and “… Progress Toward …”; the shared relation is separate.
- **Requirements**: next rolled quota, delivered amount, fixed milestone, readiness, and Claim are server-authored for the selected direction.
- **Benefits**: Stage 1-4 benefits show Locked/Active/Persistent states without implying that the lower shared relation removes a directional benefit.
- **Military**: Stage 4 voluntary delegation, kingless wartime authority, qualifying war/company evidence, and exact denial reason.

Accept/Cancel Request/Break use appropriate permission state; Break always opens the confirmation dialog. No active screen renders Civil/Trade/Military columns, grace, or waypoint-alliance permissions.

## Ledger

Classes: `KOMEGuiAllianceLedger`, `KOMEContainerAllianceLedger`, `KOMEAllianceInventory`.

The visual layout surrounds the original authoritative slots; it does not create another inventory or progression path. The direction switch changes which faction's quota is displayed. Hover and click coordinates use the same responsive transform as drawing. Deposit/claim availability, requirement, progress, and denial reason are server-derived.

The deterministic visual capture uses a container-free ledger twin because the LOTR global pouch hook dereferences a live player in menu-only capture. It does not replace hands-on slot, drag, shift-click, or hitbox testing against the real container.

## Server Records, wars, pledge departure

`KOMEGuiServerRecords` presents directional alliance stage/shared relation plus Build count and split population summary. Its Controlled Tiles drilldown retains waypoint-first labels, final-row scrolling, selected player, and detail scroll across refresh. War mode/filter/selection are preserved; detail remains scrollable.

`KOMEGuiPledgeDeparture` and conquest claim warnings use the shared scroll and confirmation components. Allied/hostile capture and all destructive Build/alliance actions require explicit confirmation; Escape/Cancel sends no mutation.

## Responsive rules

Cards derive width from the current scaled screen. Long text is wrapped or ellipsized with a readable tooltip. Content viewports scroll; action bars do not translate with scroll. Every scroll range is computed from content bottom minus viewport bottom so the final row is reachable.

Required manual matrix: Small, Normal, Large, Auto, and 854x480; long Build/faction/player names; many Builds/pools/submissions; final-row scroll; tab/selection retention; map marker click; real ledger slots; back/Escape; every destructive confirmation.

## Changed or added GUI classes

- `KOMEGuiAllianceUnified` — new unified list/request/four-tab detail.
- `KOMEGuiConquestCapture` — Builds-first Tile Command, split pools, contributions, confirmations.
- `KOMEGuiAllianceLedger` — schema-7 direction and quota presentation over authoritative slots.
- `KOMEGuiCompanyList` — persistent source-tile company presentation and rename.
- `KOMEGuiServerRecords` / `KOMEServerRecordPresentation` — directional stage, split population, Build counts, retained tile drilldown.
- `KOMEConquestMapOverlay` — Build markers and click-through.
- `KOMEWaypointMapOverlay` — waypoint-first presentation retained.
- `KOMEProgressionMenuOverlay` — routes Alliance and Tile Command entry points to the redesigned screens.
- `KOMEGuiVisualCaptureController` — schema-7/Build deterministic fixtures.
- `KOMEClientProxy` — unified-screen routing and capture registration.
- Removed obsolete `KOMEGuiAlliance`, `KOMEGuiAllianceDetail`, `KOMEGuiAlliancePermissions`, and `KOMEAlliancePermissions`.
- Shared existing components reused: `KOMEGuiTheme`, `KOMEGuiButton`, `KOMEGuiScrollPanel`, `KOMEGuiConfirmationDialog`.

## Genuine remaining visual work

Only multiplayer user-acceptance validation remains, especially the live ledger container hitboxes and real map interaction with densely co-located markers. Optional authored faction/Build icon textures may replace generated marks later. No required schema-7 screen is left as a TODO.
