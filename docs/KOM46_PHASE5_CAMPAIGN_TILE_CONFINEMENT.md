# KOM-46 Phase 5: campaign conquest-tile confinement

## Scope and baseline

Phase 5 adds a physical referee for explicit CAMPAIGN hired records. ORDINARY
followers retain native movement and teleport freedom. No strategic route,
conflict, population, split/merge, stewardship, or player UI redesign is included.

The requested branch was already checked out. Initial `git status --short` showed
only `.gradle-client2/` and `run-server-phase7/`; earlier KOM-46 work was present in
existing commit `5c24117`. That baseline and both excluded directories were
preserved. No commit, push, Linear update, or Minecraft launch was performed.

## Changed files

- `src/main/java/kome/common/data/KOMECampaignTileConfinementService.java`: new referee, structured results, bounded diagnostics, loaded entity adapter and native Halt after a verified illegal return.
- `src/main/java/kome/common/data/KOMEServerTileAwareness.java`: optional in-pass boundary guard, immediate corrected resample, lifecycle cleanup; standalone observation remains usable without world-data dependencies.
- `src/main/java/kome/common/KOMEAddon.java`: installs the referee before awareness starts.
- `src/main/java/kome/common/command/KOMECommandTroops.java`: reuses bounded arrival search for safe physical returns; documents existing synchronous arrival ordering.
- `src/main/java/kome/common/data/KOMEWorldData.java`: derives company recruitment-source summaries from unit provenance when campaign composition is recalculated.
- `src/main/java/kome/common/data/KOMEArmyCompany.java`: preserves deliberately empty mixed-source summaries through NBT round trips.
- `src/test/java/kome/common/data/KOMECampaignTileConfinementServiceTest.java`: 29 deterministic referee tests.
- `src/test/java/kome/common/data/KOMECampaignTileConfinementHookTest.java`: 17 hook, loaded entity, native Follow/Halt/teleport, placement, and performance tests.
- `src/test/java/kome/common/data/KOMECompanyReorganizationServiceTest.java`: source-summary and persistence assertions after split/merge.
- `src/test/java/kome/common/data/KOMEServerTileAwarenessTest.java`: exposes existing inert fixture constructors to the integration tests.
- `src/test/java/kome/common/data/KOMEServerTileAwarenessIsolationTest.java`: updates lifecycle wiring expectations while retaining dependency isolation checks.
- This report.

## Enforcement and authority

`KOMECampaignTileConfinementService` determines explicit CAMPAIGN eligibility,
selects the authoritative tile, checks sampled
physical evidence, returns illegal displacements, and emits structured results.
It never writes record/company strategic tiles or membership.

The record must have a current tile present in conquest metadata. If its company
ID is nonempty, that company must exist and its current tile must agree. Missing
or contradictory metadata yields `STRATEGIC_CONTRADICTION`, with a reconciliation
diagnostic and no arbitrary relocation. An unassigned valid CAMPAIGN record is
still confined. Recruitment provenance and native squadron values play no role.

The hook runs inside KOM-60's existing server tick END sampling pass, after world
movement, combat, pushing, and teleport updates. It does not depend on semantic
tile-change notifications alone: unchanged samples also check strategic authority
and record eligibility. Only explicit CAMPAIGN records enter full evaluation.
There is no extra global entity/world scan or new LivingUpdate geometry resolver.

At normal 20 TPS, a positive crossing is corrected at the end of that tick (about
50 ms); relocation after this listener is checked at the following tick END.
This follows server tick cadence under lag, not a wall-clock exemption.

AVAILABLE + RESOLVED outside the authoritative tile is positive contradiction.
All other availability states, unloaded entities, gaps, invalid/outside geometry,
unsupported dimensions, and unresolved results remain UNKNOWN and do not return
an NPC. Samples from a superseded resolver view are rejected. Correction uses
the same immutable geometry publication, and the tracker resamples a returned
entity before publishing fresh observations. Queued pre-correction transitions
are historical evidence; consumers must still validate `current()` freshness.

## Legal strategic arrival ordering

The existing movement flow remains unchanged:

1. Departure saves native entity data and removes physical entities; authoritative tiles remain at the origin during travel.
2. `processArrivals` synchronously recreates and verifies destination entities. Join events only discover them; they do not run confinement.
3. Once every spawn is verified, arrival replaces UUID references and updates each record tile, then the order and company tile. A failed spawn rolls back verified spawns before returning.
4. KOM-60 first samples at server END, after the entire operation completes. It sees matching destination record/company authority and accepts the new physical tile.

No authorized-transition token or timer is necessary. Neither a planned route,
SPAWNING status, nor WAITING_NEXT_STEP grants physical permission to cross.
After arrival, the same rule confines the loaded unit to its new strategic tile.

## Safe return and bounded behavior

The original Phase 5 correction preferred a recent per-unit safe physical anchor
inside the authoritative tile, falling back to strategic arrival placement when
that anchor was missing, expired or unsafe. Live testing found that a border-safe
return let native Follow/teleport AI repeatedly press against confinement.

The narrow live-test correction removes that transient position anchor entirely.
Illegal returns now use this hierarchy:

1. The authoritative tile's canonical KOME RALLY/arrival waypoint (or the existing
   strategic arrival helper's legacy physical tile anchor when no waypoint exists).
   Use its exact position only after tile geometry and live safety validation.
2. The existing bounded strategic arrival search around that same origin: at most
   48 candidate columns and six heights per column, inside the authoritative tile,
   with solid support, body/mount clearance, loaded terrain and no liquids.

Live search probes restore the original entity position in `finally`. Neither
owner position, border-safe observations nor unchecked polygon centroids select
the return destination. `resolveConfinementStationTarget` isolates origin policy
from the referee and strategic identity. Future Battle/Force Deployment Areas and
applicable Siege Complex Exterior Deployment Areas can be prioritized there;
neither area type nor new strategic metadata is implemented in V1.

A return moves the NPC and its riding chain (bounded to eight bodies), clears
navigation paths, cancels momentum/fall distance, and verifies the resulting tile
and safety. Only after verification it invokes native `hiredNPCInfo.halt()`.
LOTR v36.15 has WARRIOR/FARMER task types, not a HALTED task enum: Halt sets native
movement state, clears the attack target and sends the native client update.
If Guard was active, the existing `setGuardMode(false)` path first clears it so
`isHalted()` is true. Ownership, squadron, task type, level/XP, classification,
population and strategic records remain unchanged. The valid riding relationship
is preserved; the ordinary mount is not rehired or given a new KOME class/task.
Ordinary units never enter this path.

Within-tile walking, fighting, following and same-tile summons never invoke Halt.
Legal strategic movement and its existing intentional arrival Halt remain
unchanged; this referee does not add an arrival Halt or any route exemptions.

There is at most one actual relocation attempt per sampled unit per tick. Failed
placement/verification or adapter errors latch retries off; the unit is retained.
Recovery requires an in-tile observation, entity reload/replacement, geometry
change, or strategic repair. There is no timed permission to stay outside a tile.
Distinct subsequent positive crossings remain subject to correction; ignoring
them would authorize the bypass. Clearing paths and momentum prevents the same
physical impulse from perpetuating a return loop. Native Halt also disables
Follow AI execution/continuation, preventing automatic re-teleport ping-pong.
An explicit later Ready/horn command or external relocation is still subject to
the normal same-tile/cross-tile rule, not permanently exempted.

Audit rows include UUID, detachment ID, authoritative/observed tiles, result, and
reason. Boundary pressure and strategic diagnostics coalesce over 100 ticks;
new placement failures are recorded immediately and then latched. The existing
central audit stream caps storage at 500 rows. No chat notifications were added.

Native summon horns call `LOTRHiredNPCInfo.tryTeleportToHiringPlayer(true)` directly.
Inspection of the bundled native method found no cancellable teleport event.
Post-relocation correction therefore covers summon, ordinary teleport, following,
pathfinding, chasing, knockback, pushing, other-mod movement, and loaded entity
reload without intercepting ordinary follower behavior. Same-tile teleports are
legal. UNKNOWN evidence remains UNKNOWN even if a teleport caused it.

## Performance and source metadata follow-up

The added eligibility work is an O(1) record lookup per tracked hired entity inside
the existing awareness pass. Full strategic checks are O(1) per loaded CAMPAIGN
unit. Ordinary hires cause no confinement inspection or placement search.
Stationary positions reuse cached KOM-60 geometry; within-tile movement uses its
existing resolution. Additional geometry/safety work occurs only for return
candidates and verification. Transient state is O(loaded CAMPAIGN units).

The focused workload tracks 301 hires, of which one is CAMPAIGN. Across 100
stationary ticks, it adds zero geometry resolutions, checks that one CAMPAIGN unit
100 times, checks zero ORDINARY units in the service, and performs no extra scans.
Moving only the campaign entity produces exactly one additional KOM-60 lookup.

Split formerly copied the parent's `sourceTileId`; merge retained the survivor's.
Those values could misdescribe mixed recruitment provenance. Campaign composition
refresh now derives the common unit source when all members agree, otherwise
stores empty. Split recalculates parent and child; merge recalculates survivor.
Admission uses the same refresh. NBT loading formerly converted an explicit empty
company source into its current tile; that fallback now applies only when the key
is absent, preserving mixed summaries on restart and transaction rollback.
UNIT source values remain authoritative and unchanged. No grouping, schema, or UI
authority was added to company source metadata.

## Acceptance coverage

| Requested cases | Deterministic coverage |
| --- | --- |
| 1-3 | ORDINARY legacy metadata freedom, full within-tile movement, adjacent crossing return |
| 4-8, 23 | Before/after NBT equality for unit, company, and route; unchanged population, ownership, season/conflict indicators and movement history |
| 9-10 | Positive contradiction; every UNKNOWN availability and unresolved geometry status; superseded geometry hook rejection |
| 11-13 (revised) | Border-safe position ignored; canonical waypoint priority; obstructed origin uses bounded arrival fallback; bounded failure latch and retained entity |
| 14-17 | Follow/push/pathfinding relocation scenarios; actual native summon method with cross-tile, same-tile and ORDINARY cases |
| 18-20 | Committed legal-hop guard state, new-tile confinement and route-status non-exemptions; existing actual movement/arrival orchestration regressions |
| 21-22 | Unassigned record, missing tile/company, contradictory record/company metadata |
| 24 | 400 independent external crossings yield one attempt each and four coalesced audit rows; failures stop searches/returns across 500 repeated evaluations; native Follow gates stay off for 200 ticks after one successful return/Halt |
| 25-26 | Transient reset preserves record NBT; phase-1 coherence after fresh corrected evidence; actual tracker is AVAILABLE in the corrected tile immediately |
| 27-30 | Actual split/merge membership workflows remain confined; unit provenance/squadron ignored for authority; shared/mixed summaries and NBT round trips |
| Performance | Actual END-hook workload: 301 hires, one campaign, 100 stationary ticks, zero added geometry lookups |

The hook tests run real LOTR summon, Halt and Follow AI execution/continuation
gates in an inert world. Network sends and the combat-target callback are stubbed
because the fixture has no network/combat event bus; fixture squadron data is
seeded without watcher networking. Tests do not run full AI scheduling,
networking, chunk persistence or the saved-native-NPC spawn stack in a live server.

## Validation and remaining work

Original Phase 5 focused and requested regression run: **166 tests, zero failures/errors**. Includes
coherence, admission, reconciliation, reorganization, both movement suites, and
both KOM-60 awareness suites. `./gradlew.bat build --no-daemon --console=plain`
succeeded: 136 suites, 1264 tests, zero failures/errors, four skips. Final Git
whitespace/status/stat results are included in the final handoff.

Narrow live-test correction validation:

- First: both confinement suites, 46 tests (29 service + 17 hook), zero failures/errors/skips.
- Then: those suites plus coherence, admission, reconciliation, reorganization,
  both strategic movement/arrival suites and both KOM-60 awareness suites:
  172 tests, zero failures/errors/skips.
- `./gradlew.bat build --no-daemon --console=plain`: BUILD SUCCESSFUL; 136 suites,
  1270 tests, zero failures/errors, four skips.
- The correction adds six hook tests and updates existing service/hook
  expectations. New checks cover waypoint precedence, obstructed origin fallback,
  real Follow shutdown, native Guard-to-Halt, within-tile combat/follow freedom,
  and legal arrival versus WAITING_NEXT_STEP illegal crossing. Existing tests now
  assert canonical stationing, native Halt ordering and native identity retention.
- No commit, push, Linear write or Minecraft launch. This correction touches only
  the confinement service, troop placement helper, two confinement test files and
  this report; all other existing Phase 5 changes are preserved.

User-reported live Phase 5 acceptance confirmed positive crossing detection,
legal A -> B arrival, WAITING_NEXT_STEP confinement in B, same-tile summon,
cross-tile summon correction, mounted return and restart behavior. It identified
only the native Follow ping-pong UX defect addressed by this narrow correction.
No Minecraft instance was launched during the correction. Manual retest:

1. In a controlled test world, give tile A a known safe canonical arrival waypoint well inside the tile. Record company/unit tiles, route/allowance, membership, population, class/provenance, native squadron and XP/level. Set the CAMPAIGN unit to native Follow; keep an ORDINARY follower as control.
2. Follow/walk/fight and summon entirely inside A: no new Halt. Lead the campaign follower into B while the player stays there: expect one return to A's station, native HALTED state, zero momentum, and no automatic follow/teleport ping-pong over at least 10 seconds. The ORDINARY control crosses freely. Repeat after Ready with push/knockback/pathfinding displacement.
3. Ready the unit, then use the native summon horn from B: it must return to A's canonical station and Halt. Ready and summon inside A: it remains legal. Repeat the illegal case mounted; confirm links are preserved, both bodies safely returned/stopped and the rider Halted.
4. Perform legal KOME A -> B -> C movement. Verify destination authority and no erroneous return to A; retain the movement system's existing arrival behavior. Ready in B during WAITING_NEXT_STEP, cross manually into A/C: return to B's station and Halt without altering the route. Complete the next legal hop.
5. Obstruct only the canonical origin: expect safe bounded nearby placement in A and Halt. Obstruct every candidate in a controlled test: expect one failure audit, retained NPC, no repeated teleport/search and no success Halt. Restore safety and reload/recover using the existing failure-latch recovery path.
6. After successful returns, verify fresh coherent KOM-60 observations and all recorded strategic/economic/native identity invariants. Repeat after reconnect/restart to confirm no dependence on border anchors.

Remaining KOM-46 work from this handoff is the correction retest and any subsequent
phases specified separately. ConflictRecord and the other excluded redesigns
were intentionally outside Phase 5's requested scope. No ticket completion or
publication is implied.
