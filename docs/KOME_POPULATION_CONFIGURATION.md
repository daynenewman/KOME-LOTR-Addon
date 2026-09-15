# Population configuration and payouts — Integration Checkpoint E

`KOMEConfigRegistry` owns the settings in `config/kome.cfg`. No standalone population configuration class or independent reader is used. Checkpoint D supplies precise Build hours; Checkpoint E pays the canonical faction bank in centi-population. General population packet/UI conversion remains deferred.

## Keys and units

Existing public keys and effective defaults are preserved. Formatting in inspection is exact decimal text.

| Key | Default | Format / valid range | Typed accessor |
| --- | --- | --- | --- |
| `population.hoursPerPopulationPoint` | `10` (display `10.00`) | Ordinary decimal, at most 2 places; 0.01–92233720368547758.07 | `population().getHoursPerPopulationPointCentiHours()`: long, 100 units/hour |
| `population.capturedBuildMultiplier` | `0.5` (display `0.50`) | Ordinary decimal, at most 4 places; 0.0000–1.0000 | `population().getCapturedBuildMultiplierBasisPoints()`: long, 10,000 units/1.00 |
| `population.populationCapEnabled` | `false` | Boolean | `population().isPopulationCapEnabled()` |
| `population.offlinePopulationCatchUp` | `true` | Boolean; population only | `population().isOfflinePopulationCatchUp()` |
| `population.populationCapValue` | `TBD` | `TBD` when disabled, or positive ordinary decimal with at most 2 places; 0.01–92233720368547758.07 | `population().getPopulationCapCenti()`: OptionalLong, 100 units/population |
| `dailyBatch.timezone` | `America/Chicago` | Region ID present in Java's timezone database and containing `/` | `dailyBatch().getTimezone()`: ZoneId |
| `dailyBatch.localTime` | `20:00` | Existing strict `HH:mm` | `dailyBatch().getLocalTime()`: LocalTime |

The cap remains optional and disabled by default. A configured value is retained while disabled but does not limit the bank. An enabled cap requires a value. The current dev rule that configured cap values must be positive is retained, including when disabled; `0` remains invalid. `TBD` remains the established optional sentinel rather than being replaced with the old reviewed branch's `0.00`.

Hours and multiplier govern the current generation calculation through their exact typed values. They do not change the fixed-point bank or unit cost units established in Checkpoint B.

The decimal parser trims surrounding whitespace and accepts a signed negative token only so range validation can report it; negative values outside the setting's range fail. A decimal requires digits before and after its optional point. Blank text, NaN, infinity, exponent notation, malformed numbers, excess fractional digits (including extra trailing zeros), overflow, and out-of-range values fail. Conversion uses BigDecimal and longValueExact; it never passes through float/double, rounds, or truncates.

Timezone validation accepts region aliases such as `US/Central` and `Etc/UTC` when supported by the installed timezone database. It rejects `CST`, `EST`, `PST`, bare `UTC`/`GMT`, raw offsets, and unknown IDs. No host-zone or UTC fallback occurs. Checkpoint E uses the shared daily-boundary policy documented below.

## Publication, readiness, and guards

The existing registry had a bootstrap default snapshot, complete candidate parsing, and volatile publication; it did not have an explicit configuration-loaded flag. Readiness now derives from the validation-complete property of that same active immutable snapshot. Bootstrap defaults are inspectable but not ready for world binding. A successfully validated load is ready. An invalid initial load remains unready and throws visibly. An invalid reload preserves the last valid active snapshot and its readiness.

`readValidated` remains a detached candidate read; it does not publish or change readiness. `applyValidated` and `load` use the existing `KOMEConfigChangeGuard`. A rejected candidate cannot partially publish other valid fields.

After Checkpoint A successfully initializes world data at SERVER TICK START, `onWorldInitialized` binds that existing world as the source of the configuration lock. Binding is idempotent. Both `FMLServerStoppingEvent` and `FMLServerStoppedEvent` clear the world reference through the same idempotent configuration cleanup; the latter also covers an abnormal stop that bypasses stopping. Dimension unload does not unlock global configuration. A subsequent integrated server in the same JVM can bind its own world. No second initialization routine, population readiness singleton, or persisted configuration schema is introduced.

Hours, capture multiplier, cap enabled/value, timezone, and local time require restart once a world is bound. A change request receives the existing DEFERRED result with exact blocking keys; it is not scheduled automatically. Equivalent decimal text is a no-op. Existing in-progress daily-transaction and active-siege key locks remain. Unrelated settings retain their previous guard classification.

No accepted or rejected request recalculates Builds, balances, payout remainders/cursors, historical payouts, or movement. Configuration audit entries are the only added persisted side effect.

Publication is an in-memory validated-snapshot guarantee, not an atomic filesystem transaction. Forge's existing `Configuration` construction/loading may create or recover files before typed validation. After validation and guards succeed, the snapshot is published before audit logging and Forge save; a later logging/save failure does not roll back that already-valid snapshot. Validation or guard rejection leaves the previous valid snapshot intact. `load(File)` supplies idle activity and is used in production only during startup pre-initialization; it is not a runtime reload command. These existing boundaries are not redesigned here.

## Inspection and audit

Existing `/kome config`, `/kome config population`, and `/kome config dailyBatch` display active typed values. Population inspection also shows the cap in centi-population, registry readiness, world lock, and last apply status. The audit/status names distinguish ACCEPTED, REJECTED, and DEFERRED candidate outcomes from active values.

Before world binding, outcomes are written to the server log. After binding, the existing bounded central audit records CONFIG/WORLD_BOUND, CONFIG/ACCEPTED, CONFIG/REJECTED, or CONFIG/DEFERRED entries. Accepted entries identify changed keys and effective population values; validation failures identify the failing key and requirement. No complete configuration file or unrelated raw values are logged. Existing `/kome audit` remains the audit inspection path. No reload command is added.

## Exact current consumers and temporary boundaries

Checkpoint D stores approved Build time as centi-hours. With `A` approved centi-hours, `C` configured centi-hours per population point, `M` multiplier basis points, and rate scale `S = 1,000,000`, the exact rate numerator is `A * S * M` and denominator is `C * 10,000`. Native Builds use `M = 10,000`; captured Builds use the configured basis points. `BigInteger` arithmetic supports the entire validated configuration range without intermediate overflow or floating-point conversion. Positive half-up rounding occurs only at final fixed-rate conversion. Contributions are combined before faction-total rounding. The existing saturation of an informational fixed rate at `Long.MAX_VALUE` is retained; authoritative bank mutations remain checked.

For example, 1,000 approved centi-hours (10.00 hours) with `10.50` hours per point yields 952381 fixed rate units (0.952381 population/day); the default captured multiplier yields 476190 units. Payout uses the unsaturated `BigInteger` faction result, not the informational `long` projection. A configuration accepted as ready can be used immediately by rate, contribution, command/server-record, and payout paths.

The payout cap compares `getPopulationCapCenti()` directly against the centi bank. Grants fill exact remaining room: a bank of 24.00 under a 24.50 cap can receive 0.50. The configured cap is never floored or clamped to int range. An already-full bank receives nothing; disabling the cap ignores its configured amount. Cap-blocked whole centi-population is discarded, never stored as future debt; an earned sub-centi remainder survives. An unrepresentable grant or balance fails that entire boundary before any faction, remainder, cursor, audit, or dirty-state mutation.

Three legacy projections remain temporarily, with no production callers:

- `getHoursPerPopulationPoint()` requires an exact whole int and throws for fractional or out-of-int-range values. All production rate paths instead use `getHoursPerPopulationPointCentiHours()`.
- `getCapturedBuildMultiplier()` produces a compatibility-only double. All production calculations instead use `getCapturedBuildMultiplierBasisPoints()`.
- `getPopulationCapValue()` floors/bounds a compatibility-only int projection. Payout enforcement instead uses the exact optional centi cap.

Structural tests prohibit calls to these legacy projections anywhere in KOME production source. Payout no longer calls whole-unit grant APIs. Unit override maps and the current unit cost calculator, including its mounted surcharge, are untouched. War, season, diplomacy, siege, and feature defaults are preserved.

## Exact centi accrual and atomic boundaries

There are 1,000,000 fixed rate units per population and 100 centi per population: `RATE_UNITS_PER_CENTI = 10,000`. For each faction, add the exact daily rate to its prior remainder, divide by 10,000 for generated centi, and retain the modulus. Remainders are always `0 <= remainder < 10,000`, persist in rate units, and survive rate/capture changes or a temporarily zero rate. No floating point, whole-population intermediate, or authoritative saturation is used.

Every boundary preflights all factions, exact caps, checked bank additions, remainder results and the next cursor before publication on the server thread. A failed boundary leaves persisted gameplay state unchanged and remains retryable; earlier completed boundaries in the same catch-up remain committed. A transient error is reported through the existing runtime failure logger and admin inspection. Invalid readiness or persisted state fails without silent fallback.

Planning uses a pure ownership projection: normalized current controller when present, otherwise normalized legacy owner, without repairing either field. Each contribution row carries its resolved multiplier in basis points; exact aggregation and multiplier display consume that same value, never a status-string interpretation.

Each fresh anchor, frozen boundary, offline skip, paid boundary and reconciled anchor includes a prepared central-audit entry in one rollback-protected publication. A runtime failure before completion restores existing bank objects and balances, removes newly created banks, and restores all remainders, cursor/schedule identity, initialization, the complete audit stream (including trimmed entries), and the prior dirty flag. Rollback bypasses overridable WorldData mutation hooks. Earlier complete catch-up transitions remain committed and are explicitly counted in the result.

Console logging is best effort only after the logical commit. A console failure cannot reject committed gameplay or enable a duplicate retry. This is coherent in-memory publication before normal Minecraft saving, not crash-durable filesystem transactions. Live entity-spawn and multiplayer validation remain outstanding.

## Persistence and development-world reset

The integrated root stays `KOMEDataSchemaVersion=2`; Build and faction schemas remain unchanged. Payout state now requires `PopulationPayoutDataSchemaVersion=1`, `PopulationPayoutInitialized` (boolean), `LastPopulationPayoutBoundaryMillis` (long), `PopulationPayoutTimezone` / `PopulationPayoutLocalTime` (strings), and `PopulationPayoutRemainders` (compound list of canonical `Faction` and long `RemainderUnits`). Uninitialized state has cursor -1, empty schedule strings and no remainders. Initialized cursors must identify an actual boundary under their persisted schedule.

**Pre-Checkpoint E development worlds require reset, including worlds with no Builds.** Missing/incompatible payout schema, malformed schedules/cursors, duplicate or invalid faction entries, or out-of-range remainders fail closed before world collections publish. Existing root write blocking prevents saving over rejected data. No conversion, old-schedule guessing, or development-save migration is provided.

## Shared schedule, startup and season rules

`KOMEDailyBoundary` is a stateless calculator built from the registry or persisted schedule identity, not a second configuration owner or timer. It uses calendar dates in the configured zone. A DST gap resolves to the first valid instant after the gap; an overlap uses the earlier offset, once only. Daily steps are not fixed 24-hour durations. Population and normal daily movement share this calculator; old world-owned movement timezone/time fields are retired. The existing test-speed overrides remain independent of daily mode. `/troops movetime daily set` directs operators to `kome.cfg` and restart instead of changing a second schedule.

A fresh world anchors at the latest boundary at or before startup without historical payout. Existing worlds process every due old-schedule population boundary when catch-up is enabled; disabled catch-up consumes them without accrual. No offline movement, muster, starvation or conflict replay occurs. On restart with a different timezone/time, reconcile due boundaries using the persisted old schedule first, then persist the new schedule and latest new boundary as an unpaid anchor. Failure retains the old schedule and failed cursor for retry. No guessed historical configuration or season transitions are replayed.

The existing current-season payout gate is preserved: a frozen boundary advances the cursor but accrues no population or new remainder, preserving previously earned remainder. It cannot be collected later after the freeze ends.

At SERVER TICK START, startup reconciliation precedes queued packet work. On the existing periodic campaign check, observed daily movement allowance resets and due movement/arrival effects finish before population rates are read and paid, followed by the existing stewardship cleanup. Each canonical world-data instance is processed once across dimensions. New routes receive their intended allowance; only successful departure consumes it. Blocked attempts, retry, resume, retreat, reload and restart never replenish it; startup only rebases the next future movement reset.

## Payout audit and inspection

The existing bounded central audit and server log receive INITIALIZED, PAID, FROZEN, SKIPPED and RECONCILED population records. Paid records include exact rate units, prior/next remainder, generated/granted/cap-blocked centi, resulting bank, effective hours, multiplier, cap, catch-up flag and schedule identity. Operator `/population rate` additionally shows persisted/active schedules, UTC/local last/next boundaries, remainder scale/values, recent central population records and the latest transient failure without mutating state. No new GUI or packet payload is introduced.

See [Checkpoint D Build model](KOME_PRECISE_BUILDS.md) for unchanged Build storage and review. Manager auto-approval, defensive exclusion and permanent combat spending are preserved. KOM-71 and Checkpoints F/G are not activated.
