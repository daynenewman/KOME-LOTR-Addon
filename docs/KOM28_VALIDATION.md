# KOM-28: durable company returns during season reset

> Combined PR #24/#25 schema-10 validation and corrections: [integration report](PR24_PR25_INTEGRATION_VALIDATION.md). The evidence below describes the original independent PR head.

Validated 2026-10-03 (America/Chicago), against freshly fetched `origin/dev`
`f3743f27b9a9d3979a8f99f40685bc2e46b48ed3`.
Branch: `dayne/kom-28-company-reset`.
The final validation includes dev's KOM-80 integration; the task commit was rebased
without conflicts and preserves its queued-route barrier revalidation.

## Behavior and authority

`/season reset` begins/resumes the reset coordinator; server ticks retry it while
RESET is active. `/season status` shows bounded pending-company reasons.
`/season complete-reset` refuses completion until the journal is complete and
company identities/membership still agree. Repeating completion does not advance
the season or audit again. The existing startup-only population path remains
intact; ordinary movement, muster, population and defeat ordering is unchanged
outside RESET.

1. Restore ownership through `KOMEWorldData.resetConquestOwnershipToDefaults`, once
   per journaled season. Determine native faction through the existing stewardship
   authority and validate company identity/location with the coherence service.
2. End active canonical `ConflictRecord`s through `endWithMovementHandoff`. Ended
   records retain historical commitments and siege checkpoints; they confer no
   active commitment. Cancel scheduling/retreat state through the movement boundary
   and clear stewardship/temporary control through its service. Never demobilize,
   refund or replace company membership.
3. Keep companies inside native territory in place. Return foreign companies to
   the authoritative capital deployment anchor. Missing capitals, unsafe anchors,
   absent dimensions, unknown physical units and contradictory live locations
   remain pending with an inspection reason. A changed capital location pauses the
   attempt until the journaled location is restored; changed audit metadata alone
   does not prevent retry.
4. Move loaded entity objects with unchanged UUIDs and surviving HP. Stationary
   snapshots may locate/load existing entities, but cannot spawn replacements.
   Virtual movement snapshots may hydrate the same saved identity, rider/mount
   tree and exact fractional HP; no UUID stripping, healing or new purchases.
   Original stored snapshots remain available. Cross-dimension anomalies remain
   pending rather than guessing a transfer.
5. Journal source chunks before moving. Write the canonical `KOME_ServerRules.dat`
   atomically with propagated I/O failure before external effects. Use the existing
   Anvil chunk loader to save affected chunks, drain pending I/O and read native
   chunk NBT back. Require destination UUID/receipt/position/HP evidence and absence
   from old source chunks before publishing the strategic location and one
   `SEASON/COMPANY_RETURN` audit containing company, origin, destination and reason.
   Receipts permit replay without duplicate outcomes; obsolete virtual rider/mount
   copies are rejected, including after journal rollover.

Root schema **8** adds the reset journal and unit receipt metadata. Schemas 6 and 7
upgrade without losing their existing authorities; schema-6 conflict migration
still infers no conflicts. Current-schema malformed/missing journals fail closed.
Older builds reject schema 8 instead of silently dropping pending reset state.
Civilian muster, faction defeat and canonical conflict persistence remain intact.

## Automated evidence

All Gradle executions held the Windows named mutex `Local\KOME-Heavy-Validation`.
Build runtime: Zulu JDK 17.0.19; project-configured legacy Java test runtime.
No dependency or Gradle configuration changes.

| Gate | Result |
| --- | --- |
| Focused reset, season, conflict lifecycle/persistence, world data, movement, muster, packet access and character-creation isolation | **191 passed**, no failures/errors/skips |
| `gradlew.bat clean test build --offline --no-daemon --console=plain` | **BUILD SUCCESSFUL**, 2m 4s |
| Full suite | **2,066 tests: 2,064 passed, 2 skipped, zero failures/errors** |
| New reset regressions | **19 passed** |
| `git diff --check` | Passed |

The skips are the existing platform-conditional symbolic-link checks in
`CustomSkinLibraryFoundationTest` and `ClientCustomSkinCacheTest`.

New regressions cover native/foreign decisions after ownership reset, unloaded
units, missing capitals, unavailable deployment, canonical encirclement/held-route
cleanup, preserved UUIDs/snapshots/fractional HP/mounts, interruption after physical
delivery, failed intent writes, repeated reset/completion, root and actual-file
save/load, schema upgrade/rejection, startup ordering, capital retry metadata,
stale virtual mounts, and native Anvil receipt/HP/origin-copy verification.

Self-review corrected swallowed legacy save errors, missing source-chunk retry
information, stale mount copies, and capital retry comparison. Existing schema
assertions were updated deliberately. The character-creation isolation test permits
NPC receipt NBT only in the reset adapter; player persistence identifiers remain
forbidden and player entities are rejected by the return adapter.

Built artifact: `build/libs/KOME-LOTR-Addon-1.0.8.jar`.
SHA-256: `611CAA3ECCA9EC5CE734ABBB25955B0C8AA7D807049A30BD577D084D2DA9C0FD`.
Task-local logs, XML and summaries: ignored `outputs/kom28/`.

## Remaining live acceptance

No Forge server or connected client was launched for this change. Automated
Minecraft-entity and Anvil-file fixtures are not live-server acceptance.

- On a disposable cloned Forge world, exercise loaded, unloaded and virtual
  companies, including mounted/damaged survivors, and verify exact UUID/HP,
  inventory/snapshot continuity and client-visible company positions.
- Stop/restart during partial return and after chunk persistence; load old origin
  and destination chunks in both orders. Confirm no duplicate riders/mounts or
  repeated return audit, and verify `complete-reset` remains blocked when unsafe.
- Exercise unavailable capital terrain/dimension and actual disk-write rejection,
  then recover and retry. Validate normal movement/muster/population/defeat resumes
  after completion with a connected client.
- Future physical siege/assault cleanup remains unresolved: this change ends the
  current canonical conflict and preserves its diagnostic history. It does not
  invent a siege winner, assault resolution or siege-entity lifecycle.

## Preservation

The existing 17 worktree heads and recorded `git status --porcelain=v1` states
matched the pre-task snapshot; the stash matched exactly. Changes and generated
outputs stayed in the dedicated worktree or disposable test temporary directories.
Existing worlds and runtimes were not launched, stopped, deployed to or edited.
Git metadata checks do not claim byte-level verification of ignored external files.
No PR merge or deployment was performed.
