# KOME redesign test plan

## Automated suite

Run from `KOME-LOTR-Addon`:

```powershell
.\gradlew clean test build --no-daemon
```

The suites cover model serialization/migration, alliance authority/progression, quota ledger isolation, Build placement/approval/removal/deletion/destruction/succession, split population and committed-capacity safety, automatic source-tile companies, Stage 3/4 edge cases, war/stewardship cleanup, record presentation, scroll/scissor math, transformer registration, and base-isolation checks. Exact executed counts belong in `KOME_REDESIGN_COMPLETION_REPORT.md` after the final clean run.

## Migration test procedure

- Copy a schema-6 world containing asymmetric tracks, pending request, ledger goods, merchant entitlement, kingless state, allocations, living units, companies, and an active war.
- Record world-data hash and representative values.
- Start with schema 7; inspect loaded/merged/quarantined log counts.
- Verify each direction follows the conservative mapping in `KOME_ALLIANCE_MIGRATION.md`.
- Verify grace/waypoint-alliance tags do not affect behavior and are absent after save.
- Verify merchant entitlement and recoverable goods persist.
- Verify legacy tile totals become native source-faction pools, not Builds.
- Verify allocations/living-unit funding reconcile and no capacity is negative.
- Restart and repeat the checks.

## Manual multiplayer checklist

### Builds

- Create in own, Friendly, and Allied controlled tiles; reject Neutral/enemy.
- Exercise the two-edge foreign owner selector with a hostile third faction.
- Create multiple Builds and multiple owners in one tile.
- Verify exact coordinate marker, tooltip, co-located offset, click-through, rename, and deletion disappearance.
- Type `0`, `.5`, `0.5`, whole hours, and `.5` increments; verify normalization, immediate preview, and exact server result.
- Reject blank, negative, NaN, Infinity, exponent, malformed, `0.1`, `0.25`, and `0.75` typed input; verify the canonical integer-half-hour packet boundary and server rejection of forged negative/empty contributions.
- Exercise offensive and defensive minus/plus controls at zero and large values; each step is exactly 0.5 and never underflows.
- Confirm manager hours are immediate and another player's are pending.
- Approve/reject; verify player/faction credit and offensive/defensive population.
- Remove approved hours and verify totals/milestone reverse.
- Change pledge/king; verify succession and no-manager fallback.
- Verify the single Destroy Build button selects manager/operator deletion or homeland-only hostile destruction without changing either permission rule.
- Verify the single button is disabled with the exact server reason when neither path is legal, and that confirmation text identifies the selected mode.
- Fund living units/allocations and verify unsafe remove/delete/destruction is blocked with actionable numbers.

### Population

- Confirm controller-owned 100%, foreign 50%, owner 0% while occupied, and reclaim 100%.
- Verify odd-total rounding, Base vs Build rows, capture, seasonal reset, restart, unit death, and no duplication/negative values.
- Verify every faction card's used/available/inaccessible segments and physical/usable numeric totals at 100%, captured 50%, zero, fully used, and near-integer-limit inputs.
- Hover Base and verify the explanatory tooltip; hover both graphs and verify exact segment values.
- Confirm a Build-funded unit returns to its exact source.

### Companies

- First combat hire in a tile creates one company; later hires reuse it.
- Rename and verify source tile/ID do not change.
- Move the company away, hire again from the source tile, and verify one company/no duplicate entity. Confirm the new unit spawns normally beside the player and remains associated despite temporary separation.
- Re-test permanent transfer, movement, delegation/reclaim, pledge departure, and war-end cleanup.

### Alliances

- Accept king-to-king and verify Stage 0 both directions.
- Progress sides independently; verify shared relation uses lower stage and higher directional benefit remains.
- Verify Stage 1 farmer, persistent Stage 2 entitlement, Stage 3 passage, and Stage 4 delegation.
- Test kingless Neutral/Friends/Allies auto-start at 1/2/3 and hostile rejection; never Stage 4.
- Remove/add kings and verify no timer/downgrade.
- Break and verify bilateral reset, movement/delegation/stewardship revocation, default relation restoration, and unique merchant entitlement retention.
- Verify no alliance waypoint restriction remains.

### Stage 3

- Count approved partner-owned Build hours from qualifying contributor faction, including pre-alliance contributions.
- Exclude pending, wrong owner/faction, removed, deleted, and pre-break-deleted hours.
- Combine offensive/defensive to 10 hours.
- Claim, then delete hours and confirm claimed stage persists.

### Stage 4 and war

- Start a new defensive war after Stage 3, join the partner side, and deploy a valid company into partner-controlled land.
- Test company already present before side join and company arriving afterward.
- Reject empty/stale company, unpledged owner, old historical war, membership not later than claim, and captured default land not controlled by partner.
- Verify once-only evidence.
- Exercise voluntary restrictions and kingless war-only restrictions; end war and confirm immediate revocation/cleanup.

## GUI matrix

At Small, Normal, Large, Auto, and 854x480:

- Long Build/faction/player names and reasons are wrapped/tooltipped.
- Many Builds, pools, pending submissions, alliance records, and controlled tiles scroll to the final row.
- Fixed buttons do not move with content scroll.
- No text/card/button overlap or clipping.
- Build creation keeps name, owner, coordinate, offensive, defensive, preview, and footer controls aligned and reachable.
- Build detail keeps grouped identity and capacity cards readable, with equal action sizing and one Destroy Build action.
- Population cards keep both segmented bars, legends, numeric totals, access state, and hover targets inside the viewport.
- Back/Escape returns correctly and sends no destructive mutation.
- Selected tile, Build, alliance tab, player, war filter, and valid scroll survive refresh.
- Map markers are clickable without blocking normal map interaction.
- Delete, Destroy, Break, claim/capture, transfer, and departure paths show confirmation.
- Test real alliance ledger slot hitboxes, hover, drag, shift-click, deposit, claim, direction switch, and inventories. Screenshot twins are not sufficient.
