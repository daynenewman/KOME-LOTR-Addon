# KOME Stability and GUI Clarity Testing

This checklist is for the current stability sprint. It is intentionally limited to existing alliance, troop movement, population, server records, and related GUI behavior.

Do not use this checklist to add seasons, sieges, economy, enemy battles, or new war mechanics.

## Test Setup

Recommended multiplayer setup:

- Two players in different factions.
- One operator/admin account.
- At least two claimed tiles for a faction.
- At least one tile with a valid Arrival Point or Rally Point.
- At least one offensive warrior, one defensive warrior, and one farmhand.
- At least one pending alliance request and one accepted alliance.

Useful commands:

```text
/troops companies <tile>
/troops previewmove <companyId> <destinationTile>
/troops movecompany <companyId> <destinationTile>
/troops moving
/troops history
/troops locate order <orderId>
/troops locate company <companyId>
/troops unit <unitId>
/alliance list
/alliance get <senderFaction> <receiverFaction>
/alliance request <civil|military|trade> <senderFaction> <receiverFaction>
/alliance accept <civil|military|trade> <senderFaction> <receiverFaction>
/alliance break <civil|military|trade> <senderFaction> <receiverFaction>
/alliance goods <senderFaction> <receiverFaction>
/alliance claimGoods <senderFaction> <receiverFaction>
```

## Troop Movement Reliability

### Company Creation

1. Assign offensive units to a company through the LOTR Unit Overview company column.
2. Open the tile company list.
3. Confirm the company appears with:
   - company name
   - unit count
   - total population
   - mounted population
   - ground population
   - stationed/moving status
4. Confirm defensive units do not appear in movable companies.
5. Confirm farmhands do not appear in movable companies.
6. As a non-admin, confirm legacy manual company creation is not available as the intended normal flow.

Expected result:

- Normal company movement uses existing tracked company/unit assignment.
- Manual `/troops createcompany` remains operator-only legacy tooling.

### Movement Preview

1. Select a stationed company.
2. Choose a valid destination from the conquest map.
3. Confirm the preview GUI shows:
   - company name
   - route path
   - origin tile
   - destination tile
   - step count
   - speed
   - arrival point
4. Confirm chat includes a preview line with the company ID and route.

Expected result:

- Preview route and visible route path agree.
- Blocked destinations show a clear route-blocked message.

### Movement Start

1. Confirm the movement order from the preview screen.
2. Confirm chat output includes:
   - movement order ID
   - company ID
   - route
   - current status
   - progress
3. Run `/troops moving`.
4. Confirm the order appears as active or waiting for next step.

Expected result:

- Movement start saves entity NBT.
- Movement start assigns movement order IDs to unit records.
- Physical units are removed from the origin once the movement starts.
- Population funding fields do not change.

### Defensive And Farmhand Blocking

1. Try to include a defensive unit in a moving company.
2. Try to include a farmhand in a moving company.
3. Attempt movement.

Expected result:

- Movement is blocked.
- The chat reason names the blocked condition, such as defensive unit or farmhand.
- No partial movement order is created.

### Relog, Chunk Reload, And Server Restart

1. Start a movement.
2. Log out and back in.
3. Move away from the origin and return.
4. Restart the server if possible.
5. Recheck the origin.

Expected result:

- Moving units do not reappear at origin.
- Stale moving entities are removed.
- `/troops moving` still shows the order.
- The order can still arrive or retry pending spawn.

### Arrival

1. Let the movement arrive, or use admin movement timing controls for test speed.
2. Confirm units spawn at the destination Arrival Point or Rally Point.
3. Run `/troops unit <unitId>` or inspect company history.
4. Confirm:
   - unit `currentTile` is now the destination or current route step
   - company `currentTile` matches the arrived tile
   - final movement clears unit movement order IDs
   - company status returns to stationed at final arrival

Expected result:

- Physical location changes.
- Population source/funding fields stay unchanged.

### Movement History

1. Open Server Records.
2. Open Troop Movements.
3. Confirm records are newest first.
4. Select a movement record.
5. Select Company view.
6. Inspect a single company movement history.

Expected result:

- History can show individual orders.
- Company view can show records for one company.
- Route, current tile, next tile, progress, and status are understandable.

## Alliance System Validation

### Requests

1. Send Civil, Military, and Trade requests from an eligible sender faction.
2. Confirm `/alliance list` shows direction as `sender -> receiver`.
3. Confirm each line includes a stable direction ID like `sender>receiver`.
4. Try requesting an alliance with the same faction.
5. Try requesting an alliance with an enemy faction.

Expected result:

- Same-faction requests are blocked.
- Enemy-faction requests are blocked.
- Directional alliance state is not duplicated.

### Acceptance

1. Create a pending request.
2. Try accepting as the sender.
3. Try accepting as an unrelated player.
4. Try accepting as the receiving faction king.
5. Try accepting as staff.

Expected result:

- Sender cannot accept its own request.
- Unrelated players cannot accept.
- Receiving faction king can accept.
- Staff can accept.
- Accepted request becomes tier `0` for the requested/implied alliance type.

### Break And Revoke

1. Break one alliance type.
2. Confirm the other types remain if they were active.
3. Break the whole direction.
4. Confirm `/alliance get <sender> <receiver>` reports no alliance.
5. Confirm server records no longer show stale data.

Expected result:

- Single-type break removes only that type.
- Full break/clear removes the direction.
- Relations are resynced after removal.

### Quotas

1. Accept a Military or Trade alliance to tier `0`.
2. Open Alliance Detail.
3. Confirm Roll is available only for unrolled Military/Trade tier `0`.
4. Roll quota.
5. Confirm the quota appears in detail and ledger.
6. Try reroll as a non-admin.
7. Try reroll as staff.

Expected result:

- Non-admin reroll is blocked.
- Staff reroll works.
- Delivered progress resets when rerolled.

### Goods, Storage, And Claim

1. Try opening goods on a pending-only alliance.
2. Accept at least one alliance type.
3. Open goods as sender faction member.
4. Open goods as receiving faction king.
5. Try opening goods as unrelated player.
6. Deposit valid goods.
7. Claim as receiving faction king.
8. Try claiming as sender or unrelated player.

Expected result:

- Pending-only alliance goods are blocked.
- Sender members can deposit after acceptance.
- Receiving faction king can open and claim.
- Unrelated players cannot open or claim.
- Claim output clearly reports the claimed amount.

## GUI Clarity

### Alliance List

Verify the Alliance screen shows:

- current viewer faction
- king status
- alliance direction
- Civil/Military/Trade status chips
- pending state clearly
- New/List mode clearly
- disabled Send Request tooltip explaining why

### Alliance Detail

Verify the detail screen shows:

- selected direction
- selected alliance type
- current tier/status
- next objective
- progress
- quota status where applicable
- bottom action buttons
- disabled buttons with hover reasons

Important disabled cases:

- Accept disabled because viewer is not receiving king.
- Accept disabled because request is not pending.
- Ledger disabled because no accepted alliance type exists.
- Break disabled because selected type does not exist.

### Alliance Ledger

Verify the ledger shows:

- relationship direction
- viewer faction
- deposit permission
- claim permission
- active/pending alliance types
- current quota lines
- deposit helper text
- claim helper text

### Company List

Verify the company list shows:

- selected tile
- company name
- unit count
- population
- mounted/ground split
- speed
- status
- why a selected company cannot move

### Movement Confirm

Verify the movement confirm screen shows:

- company name
- full route path if available
- destination tile
- arrival point
- step count
- speed
- estimated route duration

### Movement History

Verify movement history shows:

- newest records first
- top-level modes for Companies, Active Movements, and Movement History
- company-specific history
- selected order details
- route path
- progress and status
- next step or completion details

Movement history navigation checks:

- Open Troop Movements from Server Records. Back from the base Movement History or Active Movements mode returns to Server Records.
- Open Companies, select a company, then select one movement order. Back returns to that same company movement list, preserving the company context and list position where practical.
- From Movement History or Active Movements, select one movement order. Back returns to the same all-movements mode rather than jumping to Companies or Server Records.
- Use Refresh while viewing a company or one movement order. The response should keep the current mode, selected company/order when still available, and safe-scroll back if the record no longer exists.
- If a company/order disappears or access changes, the detail panel should show a clear missing/unavailable message instead of crashing.

### Population And Server Records

Verify Population and Server Records still open and refresh normally after the sprint changes.

For Server Records, confirm:

- player list is readable
- selected player detail updates
- alliance summary matches `/alliance list`
- Troop Movements button opens movement history

## Regression Guardrails

Do not pass this sprint if any of these happen:

- Defensive units can move.
- Farmhands can move.
- Movement changes source population fields.
- Moving units reappear at origin.
- Arrival spawns units without updating `currentTile`.
- Alliance accept works for the wrong faction.
- Enemy factions can request alliances.
- Pending-only alliance goods can be opened or claimed.
- GUI buttons are hidden with no explanation for common locked actions.
