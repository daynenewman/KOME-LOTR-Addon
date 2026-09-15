# Population configuration — Integration Checkpoint C

`KOMEConfigRegistry` owns the settings in `config/kome.cfg`. No standalone population configuration class or independent reader is used. This document describes the configuration foundation on the integration branch; Checkpoint D now supplies precise Build hours; later checkpoints own actual fractional payouts and general population packet/UI conversion.

## Keys and units

Existing public keys and effective defaults are preserved. Formatting in inspection is exact decimal text.

| Key | Default | Format / valid range | Typed accessor |
| --- | --- | --- | --- |
| `population.hoursPerPopulationPoint` | `10` (display `10.00`) | Ordinary decimal, at most 2 places; 0.01–92233720368547758.07 | `population().getHoursPerPopulationPointCentiHours()`: long, 100 units/hour |
| `population.capturedBuildMultiplier` | `0.5` (display `0.50`) | Ordinary decimal, at most 4 places; 0.0000–1.0000 | `population().getCapturedBuildMultiplierBasisPoints()`: long, 10,000 units/1.00 |
| `population.populationCapEnabled` | `false` | Boolean | `population().isPopulationCapEnabled()` |
| `population.populationCapValue` | `TBD` | `TBD` when disabled, or positive ordinary decimal with at most 2 places; 0.01–92233720368547758.07 | `population().getPopulationCapCenti()`: OptionalLong, 100 units/population |
| `dailyBatch.timezone` | `America/Chicago` | Region ID present in Java's timezone database and containing `/` | `dailyBatch().getTimezone()`: ZoneId |
| `dailyBatch.localTime` | `20:00` | Existing strict `HH:mm` | `dailyBatch().getLocalTime()`: LocalTime |

The cap remains optional and disabled by default. A configured value is retained while disabled but does not limit the bank. An enabled cap requires a value. The current dev rule that configured cap values must be positive is retained, including when disabled; `0` remains invalid. `TBD` remains the established optional sentinel rather than being replaced with the old reviewed branch's `0.00`.

Hours and multiplier govern the current generation calculation through their exact typed values. They do not change the fixed-point bank or unit cost units established in Checkpoint B.

The decimal parser trims surrounding whitespace and accepts a signed negative token only so range validation can report it; negative values outside the setting's range fail. A decimal requires digits before and after its optional point. Blank text, NaN, infinity, exponent notation, malformed numbers, excess fractional digits (including extra trailing zeros), overflow, and out-of-range values fail. Conversion uses BigDecimal and longValueExact; it never passes through float/double, rounds, or truncates.

Timezone validation accepts region aliases such as `US/Central` and `Etc/UTC` when supported by the installed timezone database. It rejects `CST`, `EST`, `PST`, bare `UTC`/`GMT`, raw offsets, and unknown IDs. No host-zone or UTC fallback occurs. This checkpoint does not change boundary or DST calculations.

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

For example, 1,000 approved centi-hours (10.00 hours) with `10.50` hours per point yields 952381 fixed rate units (0.952381 population/day); the default captured multiplier yields 476190 units. Current payout processing retains its existing whole-point grants and fractional rate remainder. A configuration accepted as ready can be used immediately by rate, contribution, command/server-record, and payout paths.

The payout cap compares `getPopulationCapCenti()` directly against the centi bank. Only complete whole-population grants fitting in the exact remaining room are issued: a bank of 23.50 under a 24.50 cap can receive 1; a bank of 24.00 cannot. The configured cap is never floored or clamped to int range. An already-full bank receives nothing; disabling the cap ignores its configured amount. Existing transactional payout overflow checks remain: an unrepresentable grant or balance fails the batch without partially applying it.

Three legacy projections remain temporarily, with no production callers:

- `getHoursPerPopulationPoint()` requires an exact whole int and throws for fractional or out-of-int-range values. All production rate paths instead use `getHoursPerPopulationPointCentiHours()`.
- `getCapturedBuildMultiplier()` produces a compatibility-only double. All production calculations instead use `getCapturedBuildMultiplierBasisPoints()`.
- `getPopulationCapValue()` floors/bounds a compatibility-only int projection. Payout enforcement instead uses the exact optional centi cap.

Structural tests prohibit calls to these legacy projections anywhere in KOME production source. Whole-unit payout/remainder scheduling, season gating, offline catch-up, and movement ordering remain unchanged. Unit override maps and the current unit cost calculator, including its mounted surcharge, are untouched. War, season, diplomacy, movement, siege, and feature defaults are preserved.

See [Checkpoint D Build model](KOME_PRECISE_BUILDS.md) for storage, review, and development-world reset requirements. Checkpoint E still owns centi-payout and scheduler reconciliation; neither is activated by the precise Build-time conversion.
