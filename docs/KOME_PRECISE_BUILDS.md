# Precise Builds — Integration Checkpoint D

## Storage and reset policy

Each contribution has one authoritative nonnegative `long centiHours`: 100 centi-hours equals 1.00 hour. Only `APPROVED` contributions enter the derived, checked Build total. There is no separately writable approved-total cache, half-hour authority, or population-rate cache.

The integrated root remains `KOMEDataSchemaVersion=2`; faction population remains independently versioned at 2. Build collection `BuildDataSchemaVersion` advances from 1 to 2, and each Build carries `BuildSchemaVersion=2`. Contribution time is exclusively `CentiHours` (NBT long). Required fields, explicit NORMAL/DEFENSIVE type, status, duplicate IDs, and aggregate bounds are validated.

Development worlds created before Checkpoint D require reset. No conversion from `HalfHours`, missing schema, or old Build contribution splits is provided. Do not open a world you need to preserve with this development schema. Unsupported or malformed Build records fail visibly through the existing root write-block mechanism before any loaded collection becomes active. Errors name the offending index and Build ID; initialization and saving remain blocked. Unrelated dev state and independently versioned systems are preserved.

## Input and review

`KOMEBuildTime` owns parsing, formatting and checked arithmetic. Ordinary decimals with at most two fractional digits are accepted, including 0, 0.01, 0.25, 10.25 and 24.50. The maximum is 92233720368547758.07 hours. Negative values, scientific notation, missing digits, NaN/infinity, excess precision (including trailing zeros), and overflow fail visibly. Formatting always uses two places without floating-point conversion.

The registering builder remains the initial Build manager, so the initial exact contribution is approved immediately. Later contributions by the current manager are also approved immediately; non-manager contributions remain PENDING until explicitly reviewed. Manager status is derived from the server-side Build and actor identity, never supplied by the client. Exact validation and checked aggregate limits apply before either path mutates state. Existing manager/admin review permissions, foreign-construction grants, ruler-based manager reconciliation and enemy-destruction permissions remain.

- Approve: PENDING → APPROVED. Repeating approval is a no-op.
- Reject: PENDING or APPROVED → REJECTED. Approved time stops contributing immediately; the record remains. Repeating rejection is a no-op.
- Administrative adjust: PENDING or APPROVED → APPROVED with the exact reviewed amount. Repeating the same approved amount is a no-op. The audit records old/new hours, actor, timestamp and reason.
- Remove approved contribution: APPROVED → REMOVED, preserving its duration/history. Repeating removal is a no-op.
- Re-review of REJECTED/REMOVED is not supported; submit a new contribution.
- Existing Build deletion deactivates the Build and records rejection/removal of its contributions. No population refund occurs.

Build type cannot be changed through registration/review/repair after creation. There is no new type-conversion workflow. NORMAL hours drive population rates; DEFENSIVE hours remain available through `approvedDefensiveCentiHours()` but produce zero population, including after capture.

Validation and aggregate overflow checks precede mutation, auditing and dirty marking. Registration audits REGISTER, SUBMIT and manager APPROVE in that order; later manager submissions record SUBMIT followed by APPROVE. Unapproved time does not contribute. The new Build-specific lifecycle history retains the newest 250 entries on append and load, independently of the existing bounded central audit. Central BUILD records remain available for administrative inspection.

## Commands and existing UI

The existing Tile Command registration/contribution form accepts exact decimal hours. Its +/- 0.50 buttons preserve any hundredths and report invalid results instead of clamping. Pending and approved hours are displayed exactly; current-manager submissions retain immediate server-authoritative approval.

`/build inspect <id>` shows type, exact approved/proposed hours, status, reviewer/reason and lifecycle history. Existing staff-only `/build sethours <id> <normal|defensive> <hours>` repairs the exact total by retiring prior approved records without erasing their amounts and appending the reviewed replacement. It does not change type. New staff-only `/build adjust <id> <contribution> <hours> [reason]` adjusts one review amount through the same service. Manager reassignment is now audited.

Build action packets carry `long centiHours`; Tile Command Build/contribution views carry `long approvedCentiHours` / `long centiHours`. These wire fields replace int half-hours symmetrically. **Client and server must run the same Checkpoint D build; old packet payloads are incompatible.** Discriminators, queue ownership and unrelated population packets are unchanged. Map marker tags explicitly include BuildType and have a separate marker decoder, not the strict full-record persistence reader.

The alliance Stage-3 threshold's existing persisted half-hour setting is not Build storage. Its adapter converts the unchanged threshold exactly to centi-hours; partner contributions use exact approved centi-hours. No progression requirement or threshold changes.

## Derived population rate and deferred work

For approved centi-hours A, configured centi-hours per point C, multiplier basis points M and fixed rate scale S, the rate is positive-half-up(A × S × M / (C × 10,000)). BigInteger arithmetic aggregates exact numerators before final faction rounding. Native M is 10,000; capture M comes from the sole KOMEConfigRegistry. Informational-rate saturation is retained; bank arithmetic is unchanged.

Identity, tile, original builder faction, population-owning faction, current tile-controller resolution and exact review records remain available for future per-Build attribution. No KOM-71 Pending Build Hours, bottleneck, Rate Ceiling, development allocation or recruitment unlocking state is introduced.

Checkpoint E now consumes the exact unsaturated faction rate for centi-payouts and reconciles the shared daily scheduler; see [population configuration and payouts](KOME_POPULATION_CONFIGURATION.md). These changes do not alter the Checkpoint D Build lifecycle or manager auto-approval. Defensive segments, gate-health formulas, siege, unit-cost and diplomacy changes remain outside this work.
