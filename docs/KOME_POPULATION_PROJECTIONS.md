# Checkpoint F: exact population projections and eligibility

## Authority and units

- `KOMEFactionPopulation.availablePopulationCenti` remains the only spendable bank. The unchanged nested schema stores `AvailablePopulationCenti` as a nonnegative long; 100 centi = 1.00 population.
- `KOMEPopulationService.getAvailablePopulationCenti` reads without creating a bank. Existing checked grant/spend operations remain authoritative.
- `KOMEPopulationProjection` is an immutable, uncached read model, not a second bank or configuration snapshot. `of` projects one faction; `all` sorts faction keys and shares one rate/liveness read within that request.
- Active Population is an exact BigInteger sum of living combat records' `populationSpent * 100L`. The stored investment remains an int in whole units. Attribution is the normalized `populationOwningFaction`, falling back to `sourceFaction`, never the owner's current pledge.
- Membership in the persisted hired-unit index is the Active Population authority. The same retained record counts identically with a loaded entity (even one awaiting terminal cleanup), an unloaded entity, a missing world/server, or after restart. Projection performs no entity queries. Authoritative terminal events remove records; there is no stored Active aggregate or expiry heuristic.
- `KOMEWorldData.removeTerminatedHiredUnit` owns ordinary death/dismissal cleanup and is idempotent. Deliberate virtual despawns preserve a record only when its saved entity snapshot, active authoritative movement order, order membership and company identity agree. `KOMEEvents` calls `reconcileHiredUnitMovementLinks` at SERVER START: invalid links are cleared, saved virtual entities become stationed recovery snapshots, and investment/provenance remain unchanged. Entity-join/stale-entity suppression uses the same validated virtual predicate; a dangling label cannot delete a physical unit. This does not change pathing or allowances.
- Pledge release, stewardship demobilization and explicit disband retain their existing terminal/tombstone workflows; movement spawn/recovery rekeys the same invested record with rollback on failed spawn. No terminal path credits population. A missing entity by itself never expires a retained record; genuinely lost records require an authoritative cleanup decision.
- Farmhands contribute zero and never spend population. Existing record loading/reconciliation normalizes cost, baseCost and populationSpent to zero.
- Represented population is exact Available + Active, using BigInteger so the sum cannot wrap.
- `formatCenti` is the reusable exact decimal formatter: 0, 1, 50, 100 and 2450 centi display as 0.00, 0.01, 0.50, 1.00 and 24.50. Long.MAX_VALUE displays as 92233720368547758.07.
- Rates use the existing scale of 1,000,000 units per population/day. Projection consumes `getExactDailyPopulationRates`, the same unsaturated BigInteger result used by payout. No floating point or independent projection formula exists. Build audit rows now retain exact BigInteger original/current rate units too.
- Legacy long/int Active and Available adapters remain only for old API/test callers. Their documented deterministic floor/saturation never feeds F eligibility or live canonical displays.

## Permanent spending

Checkpoint B's existing `KOMEPopulationService.CombatHireDebit` is the actual transaction boundary in this repository (there is no separate KOMECombatHirePopulationService class).

`KOMEEvents.handleHiredUnit` is the normal and stewardship hire entry point: it selects the population-owning/native faction, debits via `beginCombatHireDebit`, records the hire, then commits the token. Only a failure within that incomplete transaction may roll back the exact debit once. A committed or rolled-back token rejects another use.

`KOMEUnitPopulationCostService` remains the sole cost/high-water reconciler. Increases debit only the excess over populationSpent; decreases neither refund nor lower that investment. No formula changed in F.

Death/dismissal (`KOMEEvents.releaseIfTracked`, `KOMEWorldData.removeInactiveLoadedHiredUnits`, troop disband), transfer, delegation, pledge cleanup, ruler change, stewardship withdrawal/demobilization and movement/recovery contain no canonical credit. Historical `populationReturned` flags are cleanup/tombstone completion markers, not refund authorization. Source-tile capture does not reattribute investment or credit a bank.

Canonical bank increases remain exact daily payout publication and the scoped incomplete-hire rollback. Explicit grant APIs remain available to approved admin/test callers; F adds no grant command or automatic grant.

## Eligibility

- `lord.early_beginnings` keeps its existing 200-population threshold, now comparing exact represented population against checked 20,000 centi. Other progression entries, completion semantics and alliance progression are unchanged.
- Recruitment keeps controlled-tile plus positive population eligibility. The legacy positive allocation/reserve source is replaced with positive represented faction population: zero fails; one cent succeeds. It does not spend population or make an unaffordable hire affordable.
- Active recruitment tile choice remains persisted by player/faction. Eligibility is recalculated on every query, not cached or persisted as another counter. An ineligible saved choice is not deleted; it can become valid again when canonical conditions hold.
- Canonical getters do not create faction/player/tile pools, reconcile companies, repair ownership, dirty data or audit. Inspection uses `projectRulingFaction` and `projectToNBT` rather than ownership-repairing accessors. Mutation services keep their existing lifecycle responsibilities.

## Wire contract and deployment

One protocol identity: `KOMEPopulationWire.VERSION = "1.0.8-integration-f1"`.

KOME's Forge mod annotation advertises that identity; its NetworkCheckHandler requires the same remote KOME version. The normal Forge ModList connection negotiation rejects missing/old/unknown KOME versions in either direction before normal play. Both peers must deploy the matching F artifact. Changed packets also carry that version as their first bounded UTF-8 field; these payload checks are not a second handshake owner. Pre-F payloads are not compatible in either direction and have no fallback decoder. The Gradle/JAR base version and resource metadata remain 1.0.8; the runtime Forge identity is the annotation version. Direct network-check tests do not constitute live multiplayer handshake testing, and this does not claim protection against arbitrary custom payload delivery outside the normal handshake.

The shared projection order is: faction UTF-8, availablePopulationCenti long, activePopulationCenti decimal string, dailyRateUnits decimal string, capEnabled boolean, capCenti long. Represented population is derived from those exact values, not sent as a redundant authority.

Unsigned decimal strings accept only `0|[1-9][0-9]*`, at most 64 digits; no sign, exponent, fraction, whitespace or leading zeros. The bound exceeds the mathematical maximum of rates/Active derived from Java-sized collections and canonical record ranges. All changed decoders reject trailing bytes. These are transport bounds, not population caps:

| Boundary | Exact limit / enforcement |
| --- | --- |
| General changed packet | 2,097,152 bytes; bounded temporary sender buffer, receiver check before decoding |
| Exact unsigned number | 64 decimal digits |
| Collection | 4,096 rows; count checked before iteration/allocation |
| Text | 4,096 strict UTF-8 bytes; cheap UTF-16 character precheck, then strict byte check before length prefix; applies to NBT keys and text too |
| Packet 13 compressed NBT | 1 through 32,767 GZIP bytes, in the existing signed-short envelope; sender preflights compression before writing the packet |
| Packet 13 decompressed NBT | 2,097,152 actual uncompressed bytes AND Minecraft NBTSizeTracker's 2,097,152-byte accounting; nesting limited to 512; either bound may reject first |

Sender failure leaves the destination payload buffer unchanged. No truncation or fractional narrowing is used. Packet 13 does **not** support 2 MiB of compressed NBT; it retains Forge 1.7.10's narrower signed-short wire format. This is not a new transport or protocol version.

| ID / receiver | Previous relevant payload | F payload | Producer -> consumer / authority |
| --- | --- | --- | --- |
| 0 CLIENT PopulationGui | Available/Active whole ints; saturated rate long; split-ledger rows | Header + shared exact projection; player Active BigInteger strings and tile faction projections | population command -> population GUI; read-only |
| 3 CLIENT PopulationUnitsGui | Army total/used ints and current cost int | Header + faction projection; each unit adds populationSpentCenti long; current cost int remains explicitly whole-cost metadata | population units command -> Unit Command GUI; read-only |
| 5 CLIENT ConquestCaptureGui | Tile split pools and allocation ints | Header + controlling-faction projection; old slots inert; D approvedCentiHours/contribution centiHours remain long | OpenCapture -> Tile Command; read-only |
| 7 SERVER ConquestOpenCapture | Tile/focus strings | Header + bounded tile/focus strings | client map -> queued server projection; no credit or reconciliation |
| 13 CLIENT ConquestData | NBT tile population splits | Header; TroopSummaries.FactionPopulationProjection: AvailablePopulationCenti and CapCenti long, ActivePopulationCenti and DailyRateUnits decimal strings | conquest sync -> client map; read-only |
| 25 CLIENT CompanyListGui | Tactical population int and stewardship capacity ints | Header; each company adds exact investedPopulationCenti string and native-faction projection; old tactical fields retained | troops command -> company GUI; read-only |
| 36 SERVER BuildAction | Stable type string, centiHours long, coordinate doubles | Header + bounded strings; exact D fields and service validation unchanged | Build GUI -> queued authorized Build service mutation, never a population-bank mutation |

All IDs/sides remain unchanged and unique. IDs 9/12 (progression/server-record text), 26/27/29/30 (movement/tactical markers/history), 34 (pledge) and 23/24 (disabled legacy ledger mutation) were inspected; they do not carry canonical numeric banks and their payloads remain unchanged. Server-record text now contains exact formatted projections.

Build view decoding validates stable type, contribution status and nonnegative long hours. BuildAction intent still passes through the existing server-authoritative type/hour/coordinate/permission validation; no client manager flag is trusted. Coordinate doubles and GUI layout floats are unrelated to population arithmetic.

The five changed client receivers (0, 3, 5, 13, 25) validate and defensively copy the complete payload before enqueueing exactly one client task. GUI construction/display happens inside that task. Conquest decodes all lists into private temporary maps; its one task replaces all seven map sections and revision on the client thread, so rendering cannot interleave with partial publication. An invalid later row/list enqueues nothing. This is packet-level publication, not a new transaction across the existing multi-packet conquest synchronization. Marker records remain read-only partial DTOs, never passed into strict authoritative Build persistence.

`KOMEClientTaskQueue` is owned and registered only by `KOMEClientProxy`. CLIENT TICK START drains a FIFO snapshot; newly appended work waits for a later tick. Capacity is 128 pending tasks. Overflow rejects the newest task with RejectedExecutionException rather than dropping an older state update. A task RuntimeException is logged and later tasks continue. Connect/disconnect clears old work and queues client-thread session reset; disconnected enqueue is rejected. The common proxy contains only the enqueue interface, no client class initialization. No background thread is created.

Checkpoint A's server-bound wrappers, unique handler runtime classes, queue snapshot drain at SERVER TICK START and stop cleanup are untouched. Unrelated client packets and Character Creation/imported-mod channels are unchanged; this correction makes no all-channels threading claim.

## Presentation

Population overview shows faction Available, Active, exact daily rate, exact cap/uncapped status and farmhand exclusion. Player rows show living faction-funded investment, not personal banks. Controlled-tile rows repeat their faction's bank/rate, not independent tile balances.

Unit rows show permanent investment and funding provenance, not refundable tile capacity. Company views show permanent investment separately from tactical mounted/ground strength. Tile Command starts on **Builds**, with normal buttons for **Builds** and **Canonical Population**. Population displays Available, Active, represented total, exact daily rate, cap and the distinction between permanent unit investment and tactical offensive/defensive strength. Allocation navigation is not offered and both tabs' action dispatcher lacks population/allocation mutation sends. Existing Build workflow/validation and the preexisting Create selectablePopulationOwners behavior remain unchanged. Population tab completion lists only supported commands.

Console, packet DTO and server-record tests verify exact values. Live Minecraft rendering at different GUI scales, live entity spawning and multiplayer handshake/disconnect testing remain manual validation; no live-server claim is made.

## Retained compatibility inventory (Checkpoint G)

| Structure/callers | Remaining purpose and classification |
| --- | --- |
| KOMEPlayerPopulation; WorldData populations/getPopulation/getPopulationIfPresent, load/save/old summaries | Persisted legacy data, no combat bank. ServerRecordBuilder includes its keys only when enumerating historical players. Unreachable private command helper methods still reference it. |
| KOMETilePopulation; WorldData tile pool lookup/effective summaries, adjustment/allocation helpers, load/save and tile ownership bookkeeping | Legacy ledger/tile metadata only. No live canonical projection, hiring debit, cap, payout or eligibility reads it. |
| KOMEPlayerTilePopulationAllocation; WorldData allocation helpers/load/save; pledge cleanup | Historical allocation and cleanup records. Pledge removal can discard ledger records but cannot grant faction population. Normal hiring no longer consumes them. |
| KOMECommandPopulation manageTilePopulation/sendAllocationStatus/manageAllocation/buildFactionMilitaryCapacityRows/buildFactionTileCapacityRows/buildUnallocatedCapacityRow and related reserve/farmhand helpers | Private legacy code not dispatched by processCommand; retained for G rather than a broad deletion in F. |
| KOMEPacketTilePopulationUpdate / KOMEPacketTileAllocationUpdate | Existing disabled mutation paths; no canonical credit. Old population/allocation controls and pool graph helpers remain dormant in GUI source. |
| PopulationGui legacy int fields/constructors, CaptureGui PopulationPoolView and split fields, CompanyGuiEntry stewardship int fields, common/client proxy legacy overloads | Internal compatibility slots; canonical senders use exact DTO fields and do not populate old ledgers as banks. Not backward wire compatibility. |
| WorldData getKinglessStewardshipAvailable/GlobalCap/Unallocated and old pool selectors | Compatibility-only helpers, no current production caller outside their helper chain. Current stewardship authorization/native debit/withdrawal is preserved. |
| KOMEPopulationType, company/movement population/mounted/ground ints, marker and movement packets | Tactical composition/path/access/history metadata, not Available or Active. Do not delete these as if they were bank fields. |
| Hired-unit provenance, source/allocation fields, populationReturned; pledge tombstones | Audit/cleanup identity and idempotence. Not permission to refund committed spending. |
| PopulationService whole-unit APIs; PopulationRate long adapter; RateContribution originalRate/currentRate | Compatibility/tests only for live projection purposes. Exact rate rows and faction DTOs use BigInteger; whole cost inputs convert exactly at the canonical debit boundary. |
| KOMEAllianceProgressionService | Build-hour and deployed-support requirements, not a player/tile population bank. Existing rules unchanged. |

## Scope and persistence

Root, faction-population, Build and payout schemas are unchanged. F adds no persisted Active counter or migration. Existing fresh-world/reset policy and unsupported-schema write blocking remain. No development-save conversion is introduced.

No scheduler, DST, catch-up, movement allowance/pathing, unit-cost, Build approval/manager/lifecycle, cap formula, config ownership, war/season, diplomacy, foreign construction, siege, gate or ConflictRecord redesign is included. Read-only movement-access lookups now project ownership without repairing it; access decisions are unchanged.

G may remove the dormant compatibility code after a separate caller review. KOM-71 Pending Build Hours, bottleneck/rate ceiling and recruitment changes remain unimplemented; preserve the existing exact rate/payout and read-only eligibility seams for that later design.

## Reviewed file inventory

31 modified tracked files:

```text
src/main/java/kome/client/KOMEClientProxy.java
src/main/java/kome/client/KOMEConquestMapOverlay.java
src/main/java/kome/client/gui/KOMEGuiCompanyList.java
src/main/java/kome/client/gui/KOMEGuiConquestCapture.java
src/main/java/kome/client/gui/KOMEGuiPopulation.java
src/main/java/kome/client/gui/KOMEGuiPopulationUnits.java
src/main/java/kome/common/KOMEAddon.java
src/main/java/kome/common/KOMECommonProxy.java
src/main/java/kome/common/command/KOMECommandPopulation.java
src/main/java/kome/common/command/KOMECommandTroops.java
src/main/java/kome/common/data/KOMEBuildService.java
src/main/java/kome/common/data/KOMEConquestTile.java
src/main/java/kome/common/data/KOMEEvents.java
src/main/java/kome/common/data/KOMEMovementAccessService.java
src/main/java/kome/common/data/KOMEPopulationRateContribution.java
src/main/java/kome/common/data/KOMEPopulationRateService.java
src/main/java/kome/common/data/KOMEPopulationService.java
src/main/java/kome/common/data/KOMEProgressionAutoCompleter.java
src/main/java/kome/common/data/KOMEServerRecordBuilder.java
src/main/java/kome/common/data/KOMETileTroopSummary.java
src/main/java/kome/common/data/KOMEWorldData.java
src/main/java/kome/common/network/KOMECompanyGuiEntry.java
src/main/java/kome/common/network/KOMEPacketBuildAction.java
src/main/java/kome/common/network/KOMEPacketCompanyListGui.java
src/main/java/kome/common/network/KOMEPacketConquestCaptureGui.java
src/main/java/kome/common/network/KOMEPacketConquestData.java
src/main/java/kome/common/network/KOMEPacketConquestOpenCapture.java
src/main/java/kome/common/network/KOMEPacketPopulationGui.java
src/main/java/kome/common/network/KOMEPacketPopulationUnitsGui.java
src/main/java/kome/common/network/KOMEUnitGuiEntry.java
src/test/java/kome/common/data/KOMEPopulationPayoutProcessorTest.java
```

Nine new files (including four correction support/test files):

```text
docs/KOME_POPULATION_PROJECTIONS.md
src/main/java/kome/common/data/KOMEPopulationProjection.java
src/main/java/kome/common/network/KOMEPopulationWire.java
src/test/java/kome/common/data/KOMEPopulationProjectionTest.java
src/test/java/kome/common/network/KOMEPopulationProtocolTest.java
src/main/java/kome/client/KOMEClientTaskQueue.java
src/test/java/kome/client/KOMEClientTaskQueueTest.java
src/test/java/kome/client/gui/KOMECanonicalPopulationNavigationTest.java
src/test/java/kome/common/data/KOMEHiredUnitLifecycleTest.java
```

## Validation evidence after the correction

Focused selection: 144 tests, no failures/errors/skips:

```powershell
.\gradlew.bat test --tests kome.common.data.KOMEPopulationProjectionTest --tests kome.common.data.KOMEHiredUnitLifecycleTest --tests kome.client.KOMEClientTaskQueueTest --tests kome.client.gui.KOMECanonicalPopulationNavigationTest --tests kome.common.network.KOMEPopulationProtocolTest --tests kome.common.network.KOMECanonicalBuildPacketTest --tests kome.common.network.KOMEPacketRegistrationTest --tests kome.common.data.KOMEPopulationPermanentSpendTest --tests kome.common.data.KOMEFactionPopulationTest --tests kome.common.data.KOMEUnitPopulationCostServiceTest --tests kome.common.data.KOMEPreciseBuildTest --tests kome.common.data.KOMEPopulationPayoutProcessorTest --tests kome.common.data.KOMEPopulationRateServiceTest --tests kome.common.data.KOMEWorldDataSchemaTest --tests kome.common.command.KOMECampaignBoundaryMovementTest --no-daemon --console=plain
```

Both full validation commands passed (647 discovered, 645 passed, 2 environment-dependent Character Creation symbolic-link tests skipped, no failures/errors):

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat clean test build --no-daemon --console=plain
git diff --check
git diff --cached --check
```

Checkpoint F adds 43 tests in its five new test suites (21 added by this correction), covering exact formatting, stable record/high-water attribution, farmhand normalization, eligibility boundaries, three restart round trips, retired-ledger isolation, command/server-record agreement, lifecycle cleanup, client scheduling, navigation and codec bounds. The existing payout packet fixture was adapted to the exact DTO; payout expectations were not changed.

## Correction verification scope

The correction replaces the load-dependent test expectation with persisted membership. Added tests use an inert server/world with a loaded dead NPC, an unloaded list, missing dimension/server, and real NBT restart round trips. Terminal cleanup tests invoke the production lifecycle service; source assertions verify event wiring. Valid virtual and invalid/missing/terminal/mismatched movement links are covered without live entity spawning.

Queue tests invoke the real five handlers on a simulated Netty thread, observe no GUI dispatch/map changes until drain, and exercise complete map replacement, defensive copies, malformed rows, bounds, FIFO, task failure, START phase and disconnect reset. GUI navigation coverage is structural, not rendered Minecraft QA. Codec tests cover all seven changed decoders' trailing-byte rejection, both network-check directions, UTF-8 and row limits, signed-short NBT lengths 32,766/32,767/32,768 and decompressed-size rejection. Existing server queue/registration, precise Build, exact payout, rate, cost, schema and permanent-spend suites remain regression gates.
