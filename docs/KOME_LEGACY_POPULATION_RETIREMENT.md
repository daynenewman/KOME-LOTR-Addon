# Checkpoint G: legacy population retirement

## Scope and canonical ownership

This is retirement against Checkpoint F commit
`d087ffb8e566a931f00430ef33efe51e1cc02ca8`, not a gameplay redesign.
Only one spendable bank remains: `KOMEFactionPopulation.availablePopulationCenti`
(nonnegative long; 100 centi = 1.00). WorldData owns its faction map and persistence;
PopulationService exposes checked exact reads, grants and spending.

Active Population is the BigInteger sum of retained canonical combat records'
high-water `populationSpent * 100L`, excluding farmhands. Retained hired-index
membership, not entity loading, controls inclusion. Represented Population is exact
Available + Active. Neither aggregate is stored. Stable attribution uses
`populationOwningFaction`, then `sourceFaction`, never current pledge or tile ownership.

Daily rate remains exact BigInteger rate units (1,000,000 per population/day);
Build contributions store long centiHours. KOMEConfigRegistry is the single typed
configuration owner; KOMEUnitPopulationCostService is the unchanged cost/high-water
owner. No float/double or saturated adapter feeds population arithmetic.

## Pre-implementation caller and persistence matrix

Classification describes the audited responsibility, not a name match. The
REFACTOR BEFORE DELETE rows are completed in this change.

| Structure / previous owner | Previous production callers / persistence | Classification | Final action |
| --- | --- | --- | --- |
| KOMEPlayerPopulation; WorldData populations/getPopulation/getPopulationIfPresent | WorldData load/save, private dormant population-command row/capacity helpers, server-record historical player enumeration, client reset; Populations | DELETE | Model, map, getters, NBT and dormant callers removed; server-record player identity stays in names/pledges/progression/hired records |
| KOMETilePopulation; WorldData tilePopulations/pool selectors/native/effective/usable summaries | Dormant allocation, adjustment, stewardship-capacity helpers and old command/GUI paths; TilePopulations | DELETE | Model, map, selectors, summaries, pool adjustments and persistence removed |
| Tile claim/reset ownership bookkeeping formerly intertwined with pools | WorldData claimTile/resetConquestOwnershipToDefaults and removed preserveResetTilePopulationValue/reconcileClaimantAllocation | REFACTOR BEFORE DELETE | Keep tile.claim/current/default owner, claimant, arrival, audit and access revalidation in conquest tiles; remove only pool/allocation reconstruction/clamping |
| KOMEPlayerTilePopulationAllocation; WorldData populationAllocations and helper family | Dormant command/packet paths, claimant allocation recovery, pledge closeStaleAllocations; PopulationAllocations | DELETE | Ledger, map, mutations, selectors, summaries, load/save and ledger-only cleanup removed |
| PopulationDataSchemaVersion | Version for removed player/tile data, not canonical faction bank | DELETE | Removed writer/constant; reject tag at root load |
| Kingless stewardship available/global-cap/reserved/unallocated/pool helpers | Only old helper chain; company packet carried unused capacity ints | DELETE | Helpers and four company DTO capacity ints removed; no replacement cap |
| Legacy farmhand capacity and player reserve/army-used helpers | Dormant command summaries and obsolete inspection adapters | DELETE | Capacity calculations and old wire totals removed; informational farmhand count preserved |
| Legacy private population command handlers | manageTilePopulation/sendAllocationStatus/manageAllocation/buildFactionMilitaryCapacityRows/buildFactionTileCapacityRows/buildUnallocatedCapacityRow plus reserve/allocation/farmhand helpers; no live dispatcher | DELETE | Remove implementations; supported get/gui/units/tile/faction/rate syntax remains |
| PopulationGui player and tile rows | Command sender -> packet -> GUI had exact projection plus old ledger columns | REFACTOR BEFORE DELETE | Keep PlayerInvestment (UUID/name/BigInteger Active) and TileBreakdown (identity/controlling-faction projection); remove old columns/constructors |
| CaptureGui PopulationPoolView/split/allocation/farmhand fields; allocation tabs/graphs/text fields/actions | OpenCapture -> packet -> Tile Command and visual fixture | DELETE | Keep exact faction projection, Build workflow, waypoint inspection, recruitment and tactical values; remove pool UI/actions and KOMEPopulationGraph |
| canEditPopulation permission slot | Also guarded waypoint debug inspection in Tile Command | REFACTOR BEFORE DELETE | Rename canInspectWaypoint, retain identical admin/ruler permission check and wire position |
| Legacy common/client proxy and GUI String/int overloads | Old DTO shape only | DELETE | DTO-based display entry points remain |
| Packet 23 TilePopulationUpdate / 24 TileAllocationUpdate | Already disabled handlers; unreachable GUI send sites | DELETE | Classes, send sites and registrations removed; IDs reserved, not reused |
| Unit DTO armyUsed/armyTotal/farmhandsLimit and releasesTo | Inert split/capacity/refund compatibility slots | DELETE | Exact projection, farmhandsUsed count, investment, tactical cost, action fields and provenance remain |
| Whole Available grant/spend/get; whole/saturated Active and rate APIs; originalRate/currentRate wrappers | Compatibility/test callers; exact production API already present | DELETE | Tests and callers use centi/BigInteger; Rate retains only SCALE constant |
| Whole-hour/double-multiplier/whole-cap config accessors | No production consumers, old tests | DELETE | Exact centi-hours/basis-point/centi-cap APIs unchanged |
| Hired source/allocation fields, populationReturned; pledge tombstones | Attribution, inspection, transfer validation, stewardship authorization, cleanup/audit; HiredUnits/PledgeReleaseTombstones | PRESERVE | Keep IDs/flags/NBT, no ledger lookup or population credit |
| Legacy alliance-captain reservation fields | Read-only development migration whose only consumer released deleted tile pools | DELETE | Remove three fields/read paths and root-load reservation conversion; no replacement/refund |
| Company stewardshipReservation and war authorization reservation | Service writes on authorization, records authorization evidence, clears on demobilization | PRESERVE | Historical authorization/deployed-strength snapshot, not cap, bank or debit/refund permission |
| Tactical population/type/mounted/ground/company/movement data | Troop summaries, movement eligibility/history/markers, company UI and saved entity recovery | PRESERVE | No composition/path/access rule changed |
| Alliance progression deployed-support and Build-hour requirements | KOMEAllianceProgressionService, not an alternative spendable ledger | PRESERVE | Existing integer tactical requirement and exact half-hour threshold conversion remain |
| Canonical faction bank, exact projection/rates/config, unit high-water, daily processor | Canonical gameplay services; FactionPopulations/AvailablePopulationCenti and exact payout state | PRESERVE | No second authority or formula change |

Removed WorldData helper families include getTilePopulation*, getOrCreateTilePopulationPool,
getEffective*, getFactionEffectivePopulationSummary, getFactionTilePopulationTotal/Used,
findFactionTileWithPopulation, getKinglessStewardship*, findKinglessStewardshipPool,
getAllocation/getOrCreateAllocation/getAllocationsForTile/Faction, getTotalAllocated,
getTotalAllocationUsed, getPlayerAllocatedAvailable, findPlayerAllocated*,
allocatePopulation/unallocatePopulation, consumeAllocationForHire/releaseAllocationUsed,
reconcileClaimantAllocation, adjustTilePopulationTotal/getNativePopulationTotal,
getFactionFarmerPop/getLegacyFactionPopulationTotal/getFarmhandLimit,
getPlayerTilePopulationAllocated/getArmyPopulationUsed/getPlayerReservePopulationUsed,
getFactionPlayerReserveTotal/Used, getTrackedUnitCount, getFundingPool,
tilePopulationKey/populationAllocationKey and EffectivePopulationSummary.
The already no-op releasePopulationForOrdinaryUnitRemoval and its event/disband
calls are deleted; actual terminal record cleanup is retained.

## Provenance, cleanup and tactical field trace

| Fields / owner | Writes | Reads / reason retained |
| --- | --- | --- |
| Hired populationOwningFaction/sourceFaction/sourceType/sourcePlayer/sourceTileId | Hire event + recordCombatHirePayment/recordStewardshipCombatHirePayment; NBT restore; farmhand identity setup | PopulationService attribution, cost reconciliation, transfer funding predicate, pledge/stewardship source checks, audit and troop/unit inspection |
| Hired allocationTileId/allocationFaction/allocationPlayer | New canonical payment clears them; exact record NBT restore/save | Troop historical-provenance inspection and unit DTO audit identity only; no ledger resolution |
| Hired populationSpent/baseCost/cost/farmhand | Hire event, existing UnitPopulationCostService, record NBT; farmhands normalize all three to zero | High-water debit difference, exact Active/represented/projected investment; tactical cost separately describes combat strength |
| Hired populationReturned/releaseState; pledge tombstone fundingSource/sourceTile/sourceFaction | Pledge and stewardship cleanup copy provenance/set completion markers; admin resolution; NBT | Tombstone isResolved, stale-entity suppression, pending cleanup, alliance deployed-support and stewardship authorization summaries; never refund authorization |
| Hired entity/owner/controller/companyId/movementOrderId; movingEntityData/stationedEntityData | Existing hire, transfer/controller, company, movement/recovery/terminal lifecycle and NBT | Index identity, company membership, validated virtual despawn and restart recovery; unchanged |
| Company totalPopulation/groundPopulation/mountedPopulation and offensive/defensive type | Existing rebuild/movement accounting; NBT | Tactical composition, movement/day allowance inputs, markers/commands/UI; not spendable Available |
| Company stewardshipReservation; war StewardshipAuthorization.reservation | authorize/upsertAuthorization; cleared on safe demobilization; NBT | Authorization/audit snapshot only; it does not govern bank funding or impose a cap |

SOURCE_TILE_POOL, SOURCE_PLAYER_RESERVE, SOURCE_TILE_ALLOCATION, SOURCE_OTHER_LEGACY
and SOURCE_STEWARDSHIP_RESERVATION strings remain provenance identifiers, not
engines or migration paths. Fresh combat hires use the faction-bank source
(stewardship keeps its native-authority marker). Unknown/missing source defaults
and source-tile normalization in HiredUnitRecord retain existing semantics.
Transfer still rejects unsupported funding provenance; it does not invent a balance.
Farmhand source metadata is population-neutral.

`populationReturned` is a compatibility-named cleanup completion marker.
Its retained checks do not call a grant. Pledge tombstone/quarantine/results,
controller revocation, movement halt/safe return and company/entity removal remain.
No lifecycle refund API is introduced. Denied LOTR hires can still return LOTR
**coins** through refundDeniedHire; coins are not population.

Canonical bank increases are limited to:
1. Existing exact daily processor grants inside the existing WorldData payout transaction.
2. CombatHireDebit.rollback for the same incomplete hire, once; commit closes it permanently.
3. Explicit exact grant/set service APIs and persistence restoration, not automatic lifecycle credits.
   The exact setter is a package-local administrative/test boundary with no current production caller.
   Payout rollback restores captured old balances/maps on failed publication, not extra funding.

Death/dismissal/disband remove Active records without raising Available. Transfer,
delegation, pledge/ruler changes, withdrawal/demobilization and cost decreases do not
refund. Unit cost increases still debit only the excess high-water investment,
converted by checked wholeToCenti. No cost formula changed.

## Persistence / reset contract

Integrated root: `KOMEDataSchemaVersion = 3` (was 2).
Nested faction-population schema stays 2; Build collection and record schemas stay 2;
payout schema stays 1; alliance/diplomacy/war/etc schemas stay unchanged.

Removed root tags:
- Populations
- TilePopulations
- PopulationAllocations
- PopulationDataSchemaVersion

A nonempty root without a marker, any unsupported root (including F root 2), or
root 3 carrying any of those retired tags is rejected **before clearing/publishing
collections**. The data becomes write-blocked; initialize/markDirty/writeToNBT
cannot overwrite the rejected source. Diagnostics explicitly require a development
world reset. Even an old empty Build collection does not make root 2 acceptable.
A genuinely empty WorldSavedData still initializes through the existing idempotent
SERVER START lifecycle. No existing save is reset or deleted by this implementation.

No old ledger is converted, silently discarded under its old schema, or credited
into Available. The player/tile/allocation schema is not confused with nested
faction-bank schema. Nonpopulation alliance/Trade Post item-recovery, waypoint and
retired-tile handling already present in current dev is preserved, not expanded.

Every nonempty document is now parsed and reconciled in a short-lived, unregistered
KOMEWorldData candidate, using a deep copy of the supplied NBT. The active object
is not a rollback buffer. No candidate is installed in MapStorage or exposed to
gameplay. Builds, bank records, payout state, all later records, manager/default
reconciliation, item recovery, company membership and restart authorization checks
must finish before publication. Candidate dirty marking and audits remain isolated.

FactionPopulations is required even when there are no banks: it must be an NBT
list, and every nonempty element must be a compound. Both declared and actual
element types are checked (a noncompound cannot pass required compound fields).
Each record requires a string Faction and a long AvailablePopulationCenti.
Existing faction normalization is applied once; blank or duplicate normalized
keys, missing/wrong types and negative balances reject the document. Zero through
Long.MAX_VALUE remain exact. One validated temporary map supplies publication;
there is no second, weaker reparse or malformed-record default.

An escaping RuntimeException during candidate parsing or reconciliation becomes
an IllegalStateException naming the section and, for record loops, its index.
Only the active write-block/failure diagnostics change on rejection. All active
scalars, collections, record identities, audits and the prior dirty flag survive.
A valid prefix before a malformed later record is never published. Further reads,
initialization, markDirty and serialization are blocked. The supplied NBT remains
unchanged, and loaded nested snapshots do not alias it. JVM Errors are not converted
into recoverable corrupt-data rejections.

After success, explicit publication replaces collection contents and scalar/season
fields without parsing, service calls, validation or overridable mutation hooks.
The final built-in map/list/set and season container identities remain stable.
The existing server-thread ownership and synchronized load/initialization boundary
apply; this is not a new concurrent-reader API. Dirty state is prior-dirty OR
candidate-reconciliation-dirty. A genuinely empty root retains the existing
START-tick initialization behavior.

This is coherent in-memory publication before normal Minecraft saving, **not**
atomic/crash-durable filesystem persistence. No active-state serialization rollback,
production reflection, migration or second persistent owner is introduced.

### Preserved record policies

| Policy | Sections and behavior |
| --- | --- |
| Strict / load-blocking | Root/retired tags; canonical faction container, fields and nested schema; complete Build/contribution schema; payout schema, identity, cursor and remainders. Unhandled failures in progression/player-name/admin-marker/recruitment UUIDs, hired records, routes, movement/history/company readers and reconciliation reject the entire candidate. Existing optional defaults inside those readers remain unchanged. |
| Lenient / skipped or defaulted | Invalid bilateral diplomacy records; invalid optional last-known-faction, pledge quarantine/result and king UUID entries; invalid/empty foreign-construction records; optional UUIDs in tombstones, links, war/season/company metadata; unknown progression IDs, blank keys and bounded history entries. Existing empty/invalid record filters and scalar/season/waypoint defaults remain. A route's unknown edge type still normalizes to open; no new strict route schema was invented. |
| Quarantined / recovered | Malformed alliance RuntimeExceptions or invalid faction pairs retain their NBT in alliance quarantine; duplicate/merged alliance records and quotas keep existing reconciliation. Retired Trade Post inventories and already-supported provisional Produce pending stacks retain existing ledger recovery/quarantine and recovered-ID behavior. No population migration or credit is involved. |

Lenient behavior is not a promise to ignore arbitrary exceptions: anything not
explicitly handled by these existing policies crosses the common fail-closed
boundary. Central audit remains bounded, and specialized Build/delegation/pledge
histories remain separate. Live fresh-world/rejected-save checks are still required.

Other dev persistence remains: rulers, diplomacy, progression, wars/seasons/
inactivity/bonds/escrows, foreign construction, canonical Builds, faction bank,
payout schedule/remainders, hired records, companies/movements/history, recruitment,
waypoints/links/routes, central/specialized audits and pledge cleanup state.
Character Creation player NBT and imported-mod/coremod lifecycle remain separate.

## Packets and UI

Only protocol owner: `KOMEPopulationWire.VERSION = "1.0.8-integration-g1"`.
Existing Forge annotation/NetworkCheckHandler and payload headers share it. G1
peers pass; F1, base/old/missing/unknown versions fail both-side normal Forge
negotiation. No fallback decoder or new handshake owner exists.

IDs 23 and 24 are deliberately unregistered/reserved. All other IDs and sides
match F: 31 registrations, including 14 server-bound registrations. Registration
tests enumerate the exact retained class/ID/side matrix. Unique server handler
runtime classes and SERVER TICK START snapshot queue remain. CLIENT TICK START
publication queue, bounds/UTF-8/exact-digit limits, complete-payload defensive
validation, disconnect cleanup and NBT limits remain.

Changed wire payload cleanup:
- ID 0: exact projection + playerName/viewerFaction, PlayerInvestment identity and
  BigInteger active values, controlling-faction TileBreakdown projection. No
  reserve/allocation/graph columns.
- ID 3: exact projection + names/filter, informational farmhandsUsed and units.
  Removed armyUsed/armyTotal/farmhandsLimit; unit releasesTo removed.
- ID 5: removed split/native/build/usable/allocation/farmhand bank fields and
  PopulationPoolView rows. Retains exact faction projection, tactical counters,
  long Build hours, claim/recruitment/waypoint controls; canInspectWaypoint retains
  the old permission semantics.
- ID 25 company rows: removed stewardshipUnallocated/GlobalCap/Reserved/Available.
  Exact native bank/investment and tactical strength remain.
- IDs 7, 13 and 36 retain their F payload fields with G1 shared header.
No other retained packet payload is intentionally changed.

DTO-based GUI/proxy entry points replace the obsolete int/String overloads.
Population GUI retains overview, players and controlled tiles; Tile Command starts
on Builds with Builds/Canonical Population tabs only. No population allocation
controls, dormant graph or population mutation dispatch remains.
Unit/company actions and Build controls remain. Build-owner
selectablePopulationOwners is a Build registration choice, not a population pool;
it is deliberately retained. Troop/unit funding output labels historical provenance
and the canonical funding faction rather than a refundable reserve.

G1 artifacts must be deployed together on client and dedicated server. Automated
handler/codec/Forge network-check tests are not a real multiplayer connection test.

## Tests and review

New KOMELegacyPopulationRetirementTest covers:
- absent model classes/maps/helpers and sole long bank / derived BigInteger Active;
- 0, 1, 50, 1025, 2450 and Long.MAX_VALUE centi through three real NBT round trips;
- high-water/farmhand investment and no persisted Active/represented aggregate;
- old root 2 and all retired tags rejected without publishing over sentinel
  bank/hired state, dirtying, or writing over the input;
- terminal removal reasons (death, dismissal, disband) and pledge tombstone
  idempotence/provenance without refund;
- unsupported command/completion/dormant GUI surface and production-symbol checks.

Packet tests add the complete retained ID/side inventory, G1/F1 negotiation checks,
exact player/tile rows, retained tactical capture/Build fields and farmhand count
round trips. Existing queue/codec bounds, exact eligibility, unit lifecycle,
stewardship authorization/debit/cleanup, transfer, high-water, fractional config,
precise Build lifecycle, rate, payout/DST/catch-up/movement and schema suites remain
regression gates.

Legacy pool/graph/reservation-migration tests were retired, not rewritten as new
gameplay. Tests that formerly seeded unused ledgers now seed canonical bank/record
state. Whole/saturated accessor expectations use exact centi/BigInteger instead;
the above-long maximum rate expectation is exact, not Long.MAX_VALUE saturation.
No nonlegacy authorization or movement/payout expectations were loosened.

Validation commands:
```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMEWorldDataSchemaTest' --tests 'kome.common.data.KOME*Population*Test' --tests 'kome.common.data.KOMEHiredUnitLifecycleTest' --tests 'kome.common.data.KOMEAllianceSystemsTest' --tests 'kome.common.data.KOMERedesignSystemsTest' --tests 'kome.common.data.KOMEPreciseBuildTest' --tests 'kome.common.config.KOME*Test' --tests 'kome.common.network.KOME*Test' --tests 'kome.client.KOMEClientTaskQueueTest' --tests 'kome.common.command.KOMECommandTroopsMovementTest' --tests 'kome.common.command.KOMECampaignBoundaryMovementTest' --tests 'kome.common.data.KOMELegacyPopulationRetirementTest' --tests 'kome.client.gui.KOMECanonicalPopulationNavigationTest' --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat clean test build --no-daemon --console=plain
git diff --check
git diff --cached --check
```

## Remaining validation and scope boundaries

Manual validation still required: fresh G world startup and restart, visible
fail-closed rejection of an F development world using a disposable copy, rendered
GUI navigation/scales, matching G client/dedicated-server connection and F/G
rejection, live NPC hire/removal/stewardship/virtual movement with multiplayer.
No world deletion, live deployment or modpack restoration is performed here.

No KOM-71 Pending Build Hours, bottleneck/rate ceiling, recruitment formula,
siege/gate/conflict feature, new migration, background worker, or new bank exists.
Scheduler/DST/catch-up/movement allowance, Build approval, cost, configuration
ownership, diplomacy/war/season/waypoint rules are unchanged. KOM-71 must later
design its native-hour eligibility/rate ceilings against the existing exact Build
and rate seams; G neither anticipates those values nor unlocks recruitment.


## Initial validation before the persistence correction and changed-path inventory

Final focused selection: 356 tests passed, no failures/errors/skips.
Full suite and clean test/build: 638 discovered, 636 passed, no failures/errors,
2 environment-dependent Character Creation symbolic-link tests skipped:
`symbolicLinkSkinIsRejectedWhenSupported` and
`symbolicLinkAtHashPathIsNeverAcceptedWhenSupported`.
Both diff whitespace checks passed; no files staged. The skipped tests are not
claimed as passed. Earlier transient test/compile failures were corrected before
these final runs.

### Persistence-correction validation

Added 14 behavioral tests in KOMEWorldDataAtomicLoadTest: required typed bank
containers/records (including inconsistent declared/actual list types), normalized
duplicates and maximum long, early/middle/late UUID failures, rejected-prefix
isolation, candidate manager/audit reconciliation, clean/dirty sentinel preservation,
first-load/write blocking, valid full replacement, three real restart round trips,
source-NBT isolation, fresh initialization, optional skip/quarantine policies and
JVM Error propagation. Raw live state is compared without invoking serializers.
KOMERedesignSystemsTest's malformed-Build fixture now supplies the required empty
FactionPopulations list, preserving its original Build-ID diagnostic assertion.

Final focused persistence/lifecycle/Build/payout/movement/protocol selection:
260 tests passed, zero failures/errors/skips. The subsequent full test suite and
` .\gradlew.bat clean test build --no-daemon --console=plain ` both succeeded:
652 discovered, 650 passed, zero failures/errors, with the same two environment-
dependent Character Creation symbolic-link tests skipped. Both Git diff checks
passed. These automated results do not replace the manual validation listed above.

60 intended paths (35 production, 21 test, 4 documentation), including deletions and three new files:

```text
docs/KOME_LEGACY_POPULATION_RETIREMENT.md
docs/KOME_POPULATION_CONFIGURATION.md
docs/KOME_POPULATION_PROJECTIONS.md
docs/KOME_PRECISE_BUILDS.md
src/main/java/kome/client/KOMEClientProxy.java
src/main/java/kome/client/gui/KOMEGuiConquestCapture.java
src/main/java/kome/client/gui/KOMEGuiPopulation.java
src/main/java/kome/client/gui/KOMEGuiPopulationUnits.java
src/main/java/kome/client/gui/KOMEGuiVisualCaptureController.java
src/main/java/kome/common/KOMECommonProxy.java
src/main/java/kome/common/command/KOMECommandAlliance.java
src/main/java/kome/common/command/KOMECommandPopulation.java
src/main/java/kome/common/command/KOMECommandTroops.java
src/main/java/kome/common/config/KOMEConfigRegistry.java
src/main/java/kome/common/data/KOMEClientData.java
src/main/java/kome/common/data/KOMEEvents.java
src/main/java/kome/common/data/KOMEHiredUnitRecord.java
src/main/java/kome/common/data/KOMEPlayerPopulation.java
src/main/java/kome/common/data/KOMEPlayerTilePopulationAllocation.java
src/main/java/kome/common/data/KOMEPledgeReleaseService.java
src/main/java/kome/common/data/KOMEPopulationGraph.java
src/main/java/kome/common/data/KOMEPopulationRate.java
src/main/java/kome/common/data/KOMEPopulationRateContribution.java
src/main/java/kome/common/data/KOMEPopulationRateService.java
src/main/java/kome/common/data/KOMEPopulationService.java
src/main/java/kome/common/data/KOMEServerRecordBuilder.java
src/main/java/kome/common/data/KOMETilePopulation.java
src/main/java/kome/common/data/KOMEWorldData.java
src/main/java/kome/common/network/KOMECompanyGuiEntry.java
src/main/java/kome/common/network/KOMEPacketConquestCaptureGui.java
src/main/java/kome/common/network/KOMEPacketConquestData.java
src/main/java/kome/common/network/KOMEPacketConquestOpenCapture.java
src/main/java/kome/common/network/KOMEPacketHandler.java
src/main/java/kome/common/network/KOMEPacketPopulationGui.java
src/main/java/kome/common/network/KOMEPacketPopulationUnitsGui.java
src/main/java/kome/common/network/KOMEPacketTileAllocationUpdate.java
src/main/java/kome/common/network/KOMEPacketTilePopulationUpdate.java
src/main/java/kome/common/network/KOMEPopulationWire.java
src/main/java/kome/common/network/KOMEUnitGuiEntry.java
src/test/java/kome/client/gui/KOMECanonicalPopulationNavigationTest.java
src/test/java/kome/common/config/KOMEConfigRegistryTest.java
src/test/java/kome/common/config/KOMEPopulationConfigFoundationTest.java
src/test/java/kome/common/data/KOMEAllianceSystemsTest.java
src/test/java/kome/common/data/KOMECombatHirePopulationTest.java
src/test/java/kome/common/data/KOMEFactionPopulationTest.java
src/test/java/kome/common/data/KOMELegacyPopulationRetirementTest.java
src/test/java/kome/common/data/KOMEPopulationPayoutProcessorTest.java
src/test/java/kome/common/data/KOMEPopulationPermanentSpendTest.java
src/test/java/kome/common/data/KOMEPopulationProjectionTest.java
src/test/java/kome/common/data/KOMEPopulationRateServiceTest.java
src/test/java/kome/common/data/KOMEPopulationTestConfig.java
src/test/java/kome/common/data/KOMEPreciseBuildTest.java
src/test/java/kome/common/data/KOMERedesignSystemsTest.java
src/test/java/kome/common/data/KOMEUnitPopulationCostServiceTest.java
src/test/java/kome/common/data/KOMEWarSeasonStateTest.java
src/test/java/kome/common/data/KOMEWorldDataSchemaTest.java
src/test/java/kome/common/data/KOMEWorldDataAtomicLoadTest.java
src/test/java/kome/common/network/KOMECanonicalBuildPacketTest.java
src/test/java/kome/common/network/KOMEPacketRegistrationTest.java
src/test/java/kome/common/network/KOMEPopulationProtocolTest.java
```
