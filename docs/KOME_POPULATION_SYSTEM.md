# KOME population system

Current source of truth for population schema 2.

## Faction-owned tile pools

Population is keyed by conquest tile and source faction. It is never blended into an anonymous tile total. A tile can therefore contain native population and Build-generated population for several factions at once.

Each `KOMETilePopulation` preserves:

- tile ID and normalized source faction;
- native offensive and defensive baseline;
- total offensive and defensive physical capacity after Build reconciliation;
- used offensive and defensive capacity;
- farmhand totals/usage retained by the existing model.

Build population is recomputed from active approved Build contributions and added to the matching faction pool. Native values remain distinct, so the GUI can show native, Build, physical, effective, used/allocated, and available values independently.

## Access after conquest

For the current tile controller:

- controller-owned capacity is usable at 100%;
- every foreign-owned physical pool is usable at 50%, rounded down;
- the non-controlling owner has no active use through that occupied tile.

If the owner regains control, its pool returns to 100%. Capture, reclaim, and seasonal reset recompute effective access from the current controller; they never merge, erase, or relabel pools.

Example: Gondor controls a tile with 50 native + 50 Gondor Build population and 40 Mordor Build population. Physical total is 140. Gondor can use 100 Gondor population and 20 Mordor population, for 120 effective capacity. Mordor's ownership of 40 remains recorded.

## Allocations and funding

Player allocations are scoped to tile, faction, and player. `KOMEHiredUnitRecord` retains immutable funding provenance rather than reconstructing it from labels:

- source type;
- source tile;
- source faction;
- source Build when applicable;
- offensive/defensive type and cost;
- allocation player, tile, and faction;
- owner and current controller/authority context.

Living-unit use and allocations are reconciled after loading. Unit death/dismissal returns capacity to the recorded source. Capture only changes the effective ceiling; it does not change funding ownership. Level-up cost growth must fit the effective source capacity or is refused.

Build deletion and hour removal check both Build-specific committed capacity and the resulting faction-pool/effective tile capacity. Native-total reductions use the same lower-bound rule. An action is blocked if it would place a living unit or allocation above the new total.

## Determinism and restart behavior

Totals are derived in this order:

1. Load conquest ownership and explicit faction pools.
2. Load Builds and recalculate approved Build population by tile/faction/type.
3. Load allocations and unit source records.
4. Reconcile Build commitments and allocation use.
5. Calculate effective capacity from the current controller.

The same inputs produce the same result across restart, capture, reclaim, seasonal reset, manager change, company transfer, and player pledge changes. Counts clamp at zero and arithmetic guards against integer overflow.

## Migration

Population schema 2 converts a legacy anonymous tile record into an explicit source-faction pool. It prefers the saved source faction; if absent it uses the tile's current ruling faction, then the legacy faction field. Existing totals become the native baseline. Existing allocations and hired-unit records are preserved and repaired using reliable saved fields first. A unit with irrecoverable legacy source data is labeled as legacy/player-reserve funding rather than assigned to a fabricated Build.

No legacy population is converted into Build population.

## Presentation

Tile Command's **Population** tab lists one row/card per faction pool and exposes native, Build, physical, effective, committed, and available offensive/defensive totals, including the captured 50% state. Server Records summarizes split totals and Build count without collapsing ownership. `/build pools <tile>` provides the operator-readable source breakdown.
