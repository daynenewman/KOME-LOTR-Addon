# KOME alliance system

Current source of truth for alliance schema 7.

## Directional relationship

One canonical faction-pair record contains one formal relationship and two independent `KOMEAllianceStageProgress` directions. Acceptance starts both directions at Stage 0. Each faction deposits its own rolled goods and satisfies its own fixed milestone. Only that faction's recognized acting king may claim its next stage.

The shared LOTR relation is derived from the lower directional stage:

| Lower stage | Shared relation |
|---:|---|
| 0-1 | Neutral |
| 2 | Friends |
| 3-4 | Allies |

The lower shared relation never suppresses a higher faction's directional benefit.

## Stages

| Stage | Name | Directional benefit | Requirements to claim |
|---:|---|---|---|
| 0 | Formal Alliance | Relationship accepted; no stage benefit | Acceptance |
| 1 | Agricultural Access | Hire the partner faction's supported farmer/farmhand units | That direction's Stage 1 rolled goods quota |
| 2 | Trade Entitlement | Unlock one persistent Produce merchant slot for that partner | That direction's Stage 2 rolled goods quota |
| 3 | Territorial Passage | Move the progressing faction's companies through partner-controlled tiles | Stage 3 goods quota plus 10 approved Build hours credited by the progressing faction to Builds whose population owner is the partner |
| 4 | Military Partnership | Voluntary delegation and restricted kingless wartime stewardship | Stage 4 goods quota plus a new qualifying company deployment after Stage 3 during an active war where the partner is defending and both factions are on the same side |

The Stage 3 hour threshold is stored as half-hours (`allianceStageThreeRequiredHalfHours`, default 20) and can be changed with `/alliance config stage3hours <hours>`. Approved offensive and defensive hours combine. Pending, rejected, removed, deleted, pre-break, wrong-owner, and wrong-contributor-faction hours do not qualify.

Stage 4 requires a non-empty company with at least one live tracked offensive combat unit. The company owner must still be pledged to the progressing faction. The war membership of the progressing faction must be strictly later than the Stage 3 claim, the partner must be defending, and the company must be in a tile currently controlled by the partner. Captured default territory controlled by somebody else does not qualify. A qualifying deployment is recorded once.

Every claimed stage is monotonic during the current relationship. Later loss of milestone inputs does not downgrade it.

## Requests, kings, and kingless factions

Requests are server-filtered. A recognized king cannot request a hostile Enemy/Mortal Enemy partner. A king-to-king accepted request starts both directions at Stage 0.

When the receiver is kingless, the default LOTR relation determines automatic acceptance:

- Neutral -> Stage 1 in both directions;
- Friends -> Stage 2 in both directions;
- Allies -> Stage 3 in both directions;
- hostile -> rejected.

Automatic acceptance never grants Stage 4. Losing a king after acceptance preserves both directions, benefits, quotas, and claimed stages. There is no grace timer or automatic downgrade. A new recognized king inherits the faction's state and claim authority.

## Ledger and quotas

`KOMEContainerAllianceLedger` and `KOMEAllianceInventory` remain the authoritative inventory/storage path. The unified ladder reuses the existing quota catalog internally: Stage 1 resolves the compatibility Civil-1 pool, Stage 2 Trade-2, Stage 3 Military-2, and Stage 4 Military-3. Those old identifiers remain serialized solely so existing inventories and quota configuration migrate without item loss; they are not active parallel tracks.

Goods deposited by one direction never become the other direction's credit. Requirements roll once and the GUI reads typed schema-7 records.

## Directional benefits

- Stage 1 farmer hiring checks the hiring faction's stage toward the unit faction.
- Stage 2 sets `produceMerchantSlotUnlocked`. The future integration API is `KOMEWorldData.hasProduceMerchantSlot(faction, partner)` and `getUnlockedMerchantPartners(faction)`. This redesign does not implement a Produce economy or merchant entity.
- Stage 3 passage checks the moving company's owning faction toward the tile controller. There is no alliance-related waypoint restriction.
- Stage 4 voluntary delegation lets the owner king delegate selected companies to the partner king. The controller may issue valid movement/war orders and return them, but may not rename, split, merge, disband, permanently transfer, or change source funding. The owner may reclaim at any time.

For a kingless partner, Stage 4 grants no peacetime control. Restricted stewardship exists only while the partner is defending in an active war and the assisting king is on its side. Commands are limited to current opponents and valid defended/opposing tiles. Hired units remain owned and funded by the kingless faction. Authority ends immediately when the war, side membership, stage, or alliance validity ends.

## Breaking

Breaking is bilateral and confirmed. It clears relationship state, both stages, rolled active requirements, fixed milestones, Stage 1 hiring, Stage 3 passage, delegation, and alliance-created wartime stewardship; then reapplies the default LOTR relation.

Stage 2 merchant-slot entitlement is intentionally preserved as a persistent placeholder. Reforming and reclaiming Stage 2 does not duplicate it. This exception is a temporary product decision documented in the decision log.

## Operator surface

Normal play uses the GUI. Operators can inspect/repair with:

```text
/alliance list|status ...
/alliance request|accept|break <factionA> <factionB>
/alliance roll|goods|claimGoods ...
/alliance stage <actingFaction> <partnerFaction> <0-4>
/alliance clear <factionA> <factionB>
/alliance config difficulty <easy|standard|hard>
/alliance config stagequota <1-4> <stack-equivalents>
/alliance config stage3hours <hours>
/alliance config quota item ...
```

There are no grace or alliance-waypoint commands.
