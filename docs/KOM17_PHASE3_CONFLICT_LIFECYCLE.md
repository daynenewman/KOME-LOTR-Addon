# KOM-17 Phase 3: conflict commitment and lifecycle service

Phase 3 extends the single persisted `KOMEConflictService` authority created in Phases 1 and 2.
It does not install a movement hook or create a second conflict registry.

## Live hostility

`KOMEConflictService.currentHostility` resolves both faction identities and then reads the current
KOM-73/LOTR relation through `KOMEDiplomacyService`. `ENEMIES` and `MORTAL_ENEMIES` are hostile;
Neutral, Friend and Ally are non-hostile. An invalid or unavailable faction is `UNKNOWN`, never an
implicit Neutral. KOME political-war records, conflict participation, and fixed coalitions are not
inputs. A diplomacy change therefore affects the next query without rewriting conflict history.

## Validated strategic commitments

`ValidatedCommitmentRequest` is an already-authorized strategic-arrival fact. It contains the
destination tile, canonical Campaign Detachment and faction, typed conflict authority, arrival
timestamp/origin, optional movement-order identity, defensive context, an explicit initial-garrison
snapshot, and an exact expected conflict identity/revision. Creation requires
`VALIDATED_DEFENDER`; joining requires an `ACTIVE_PARTICIPANT` already registered in the conflict.
Phase 4 must obtain the defender/owner proof from the legal strategic-arrival context. Merely naming
an unrelated hostile faction cannot satisfy the existing-participant contract or the typed creation
contract.

The production entry point validates the canonical `KOMEArmyCompany` and every member through the
KOM-46 classification/coherence authority. Unknown physical evidence remains acceptable; a positive
physical contradiction or other incoherence fails closed. No coordinates, proximity, KOM-60 sample,
native squadron value, or recruitment source can call or satisfy this contract.

An accepted event atomically creates or joins one tile record, adds one commitment, and establishes
faction continuity in one published revision. Defensive context selects `ENCIRCLEMENT` even for a
known-zero original garrison. Each validated arrival commitment persists its faction, authority
kind/faction, creation/defensive classification, and initial-garrison faction association. Together
with the persisted tile, timestamp, origin, movement-order identity, and historical cohort members,
this provides exact typed replay comparison across restart. Expected conflict ID/revision is checked
before replay recognition. An exact replay returns a typed already-committed result without changing
revision; a materially different or stale request fails closed. Active cross-conflict duplication is
rejected; ENDED history does not block later reuse.

Phase 4 owns the only production call site from successful legal KOME strategic arrival. Route
dispatch, `processArrivals`, and movement holds remain deliberately unmodified in Phase 3.

## Participation continuity and departure

The first accepted commitment starts a faction continuity sequence. Additional commitments from the
same faction preserve its start. `ValidatedDepartureRequest` is an explicit terminal fact; it is not
inferred from missing runtime data. It removes one active commitment and closes continuity only when
all remaining commitments can be resolved and none belongs to that faction. Re-entry increments the
continuity sequence and records a new start.

Original trapped-garrison cohorts remain historical conflict facts after an explicitly terminated
detachment commitment. Missing or unloaded members remain unresolved, not dead. Later defender or
allied arrivals use `RELIEF`/exterior classifications and never enlarge the original cohort.

## Existing data mutation APIs

The Phase 1 service remains the narrow data authority for player registration/withdrawal,
response/capture timer checkpoints, combat-episode open/close checkpoints, Encirclement elapsed
checkpoints, and independent per-Siege-Complex catalog/state/progress/assault/lead checkpoints.
These operations do not tick clocks, decide eligibility, calculate siege damage, or resolve victory.
Multiple complexes may retain simultaneous assault identities without changing the umbrella state.

## Failure and audit behavior

Conflict ID/revision mismatches, non-CAMPAIGN classification, wrong strategic tile/faction,
incoherence, unresolved references, unknown/non-hostile diplomacy, and ambiguous remaining
commitments all reject without publication. No rejection infers death, withdrawal, peace, conquest,
relocation, or commitment removal. ENDED snapshots remain terminal and retain their final data.

Production commitment and departure entry points append one semantic event to the central KOME audit
stream after successful publication. The immutable `LastTransition` remains the record-local mutation
diagnostic. Read-only hostility queries, retries, and rejected/no-op requests emit no audit event.
