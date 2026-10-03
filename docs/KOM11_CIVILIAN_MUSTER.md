# KOM-11 civilian muster

Authority: [KOM-11](https://linear.app/kome-development/issue/KOM-11/implement-once-per-season-civilian-muster), canonical Draft 0.4 sections 5.6 and 12.4 (pages 8–9 and 19), and the explicit implementation boundary approved for this PR: persist a faction-owned force; **physical spawning stays pending until the conflict service confirms safe arrival**. No fake player owner or separate conflict system is introduced.

## Calling and selection

`/muster call` is available to the faction's recognized ruler. `/muster status` shows the caller's faction records, including older pending seasons. The server derives faction, season, capital, rate, budget, seed and roster; the command accepts none of those values from the player.

A call requires War or Finale, no earlier call by that faction in that season, an authoritative capital, a coherent enemy CAMPAIGN company within the configured strategic graph radius, and at least one affordable native unit. Failed eligibility, roster preparation or scheduling does not consume the season. Synchronization on canonical WorldData serializes duplicate calls.

Threat detection uses the existing company-coherence service and active war opposition, preserving native company identity under delegation. Unloaded physical observations do not erase coherent strategic records. Empty companies, missing/ordinary members and incoherent locations cannot trigger muster. Distance is bounded breadth-first search through the existing route-neighbor/edge authority: same tile is distance 0, each passable edge is one step, and blocked river/mountain edges do not count as traversable routes. The default radius is 2; coordinates/pixel distance are never a substitute.

The canonical developed Daily Population Rate (including its existing captured-build rules) is snapshotted at call time. `KOMEPopulationService.musterBudgetUnits` multiplies its exact millionth units by the configured multiplier, default 21. Selection and spending use `BigInteger`; no float conversion, saturation, rounded-up fractional allowance, Available Population grant or bank debit occurs. Unspent allowance remains in the muster record and is not spendable elsewhere.

`assets/kome/config/kome_muster_roster.csv` supplies native LOTR trade-table references and default weights for all 24 supported factions. These defaults favor common reserves over the second roster entry, 5:3. Fangorn uses the native Ent factory because LOTR has no Ent captain recruitment table. Rohan references only the two mounted Rohirrim trades. The server probes actual native faction, registered entity/mount IDs and max health, then uses the existing unit population-cost service, including health + 25 mounted cost and configured cost overrides. Probes never spawn or hire an NPC.

Selection sorts stable roster keys, uses an audited server-generated seed and draws only from affordable, positively weighted, native candidates. It stops when no eligible unit fits the remainder. Ordinary-size forces draw individual units. Very large budgets draw weighted batches based on remaining affordable unit count / 4096 (minimum 1); a single remaining candidate fills its affordable count directly. This reduces expensive per-unit work without imposing a population cap. The seed, exact rate/budget/spend, call-time capital, deadline and selected counts/costs/weights are persisted and audited. Restart never recalculates costs or rerolls the saved roster.

## Configuration

The existing typed `muster` category owns these settings:

| Key | Default | Behavior |
|---|---:|---|
| `threatDistanceTiles` | 2 | Passable strategic graph steps |
| `budgetDailyPopulationMultiplier` | 21 | Exact call-time temporary allowance |
| `arrivalDelayHours` | 24 | Elapsed wall-clock hours from the successful call, independent of daily payout boundaries |
| `encircledCapitalArrivalPolicy` | TBD | GARRISON/RELIEF values remain visible unresolved choices; neither enables deployment |
| `rosterWeightOverrides` | empty | Comma-separated known roster key=nonnegative integer; zero disables that candidate |

Example: `rohan:rohirrim_marshal:2=8,rohan:rohirrim_marshal:3=2`. Keys come from the bundled roster CSV. Unknown, duplicate, negative or malformed overrides reject the whole config candidate. Overrides appear in `/kome config muster`, exports and config change review. Existing calls retain their original costs, weights, budget and deadline after configuration changes.

## Persistence and arrival boundary

Root schema **6** adds mandatory muster sub-schema 1 and `CivilianMusters`. Existing development schema 5 is rejected under the repository's existing no-migration policy; missing or malformed muster authority cannot reset season use silently. Do not install this PR into an existing world as a validation step. This change does not modify or migrate any existing world.

The record owns faction/season usage, calling ruler, call/deadline times, seed, capital deployment snapshot, rate/budget/spend, exact native roster and lifecycle. It is separate from player-owned companies/hired records and their normal population investment. Old-season pending records survive reset; they do not deploy into another season. A new season can call its own muster.

The existing server START campaign tick processes due records. Before the deadline they remain SCHEDULED. After it, production has no conflict/deployment authority in this base and therefore retains them as PENDING_TBD / `CAPITAL_CONFLICT_STATE_UNKNOWN`. No troops are physically spawned by this PR. This is the approved boundary, not an automated deployment acceptance claim.

`KOMEMusterService.ArrivalAuthority` is the narrow integration boundary for the future authoritative conflict/deployment owner. It must report CLEAR/ENCIRCLED/UNKNOWN and provide a **durable, idempotent delivery receipt keyed by faction/season** before the record becomes ARRIVED. This prevents duplicate service-level delivery after successful persistence; the adapter must reconcile the separate entity/world-save crash window. It must not simply return a receipt before real delivery has committed. Encircled and unknown states remain pending, retaining the deadline and exact roster. An uncertain/failed delivery latches `DEPLOYMENT_CONFIRMATION_REQUIRED`, including across restart; only explicit adapter reconciliation followed by `retryConfirmedDelivery` permits another attempt. Repeated unchanged pending checks do not add audit spam. Arrival checks also serialize on WorldData.

Open decisions: encircled garrison versus exterior relief deployment; faction-force command/control and registration in the future company/conflict model; handling unarrived reserves after Finale/reset; physical deployment and cross-save crash recovery. The stale capital-service comment implying that relief was already decided is corrected to preserve this boundary.

## Verification and remaining acceptance

JUnit exercises real canonical rate calculation, company assessment, graph edges, WorldData NBT persistence and config validation. Fake native candidates isolate selection arithmetic; fake delivery receipts test service-level scheduling/idempotence and **do not prove physical NPC deployment**. Coverage includes eligibility, 0/1/2/3 graph distance, blocked edges, exact fractional limits, weighted seed determinism, huge budgets, duplicate calls/arrival checks, native/Rohan restrictions, configuration snapshots, restart, malformed saves, season changes and uncertain-delivery retry latching.

`tools/kom11/verify-native.gradle` supplies a test-only mod for a fresh disposable headless Forge server in `build/kom11/forge`. It resolves all faction rosters through real native factories and checks faction identity, mounted Rohan entries and absence of entity insertion. It writes `native-rosters.tsv`; this verifies factory readiness, not gameplay balance or deployment.

Run full builds and disposable Forge checks through the committed mutex helper (requires the repository's existing local LOTR/geckolib jars and cached Gradle dependencies):

```powershell
.\tools\kom11\validate.ps1 -Mode Build
.\tools\kom11\validate.ps1 -Mode Native
```

Both acquire `Local\KOME-Heavy-Validation`; the Forge task is bounded to four minutes and uses a newly named disposable world each run. The Native helper requires its explicit passing result file, not merely a zero Gradle exit. Its first trial exposed and fixed relative-path resolution in the init script; that unsuccessful disposable launch is not counted as verification.

Verified on 2026-10-02: **1,394 JUnit tests, zero failures/errors, five existing skips; all 24 muster tests pass; full build/reobfuscation passes**. Real Forge 10.13.4.1614 / Java 8 native probing passed for **24 factions / 47 entries**, with no entity insertion. [Native roster evidence](../tools/kom11/native-rosters-20261002.tsv) includes both Rohan riders at cost 45 and native Ent at cost 100. The final source was self-reviewed for exact arithmetic, duplicate calls/arrivals, restart preservation, malformed authority, and reentrant/uncertain delivery; delivery intent is latched before invoking an external adapter. No multiplayer physical deployment acceptance is claimed.

Release jar SHA-256: `32e8a7ac7e658d0e73890affb59266aac377f0a0f529737182e06219321a47b4`. The primary checkout and stash match the before-run snapshot. Two other active worktrees advanced their own commits during this run; this task did not write to either. All other observed worktree heads/statuses remained unchanged. Existing worlds and runtimes were not used; only new disposable worlds under this worktree were created.

Remaining live acceptance in a disposable multiplayer campaign:

1. As a non-operator recognized ruler, use `/muster call` with a real hostile coherent campaign company at distance 2; confirm exact budget, audit seed/result, unchanged bank and one-season rejection. Move the enemy beyond 2 and verify denial on an unused faction/season.
2. Exercise all native faction mixes and cost overrides, especially mounted Rohan, with representative developed/captured build rates; balance weights through playtests.
3. Observe the configured deadline, restart before/after it, and confirm production PENDING_TBD retains the same roster/capital/deadline. Physical spawning is intentionally unavailable until the conflict/deployment integration lands.
4. After that integration, validate CLEAR arrival, unloaded capital chunks, faction control, encircled/unknown holds, NPC death/reload lifecycle, and crash recovery between force delivery and WorldData/entity saves. None of these physical deployment checks are claimed by the simulated receipt tests.
