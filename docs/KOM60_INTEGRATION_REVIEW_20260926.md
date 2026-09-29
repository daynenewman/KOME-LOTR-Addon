# KOM-60 publication / dev reconciliation

## Scope and bases

- Original reviewed tip: `31f8fb26fb12ccc198813c6356acb4c3e063985a`, clean,
  with all six tracker/queue/rendering/population/waypoint commits preserved.
- Fetched dev: `fcabe500d80771a7e2ee5f90e709e4f4a90b8a88`.
  Before reconciliation the original branch was six ahead / five behind, with
  merge base `2559141cbc44a8e1f44811643381dd35673ccc59`.
- Review branch: `review/kom-60-dev-20260926`, isolated checkout. Neither the
  original branch nor actual dev nor any running client/server/world was modified.
- The existing 1,007-pass/two-skip build's complete source/resource hash inventory
  matched the original tip. Its live evidence does not validate the combined tree.

## Nine overlapping paths

| Path | Resolution / compatibility |
| --- | --- |
| `KOMEClientProxy` | Resolve conflict by retaining the verified client-thread predicate and dev's snapshot publisher; world unload invalidates pending/queued generations and clears tooltip state. |
| `KOMEClientTaskQueue` | Keep bounded FIFO/reset and nonrecursive immediate client-thread work. Preserve dev's latest-complete-snapshot slot for off-thread bursts, with the same immediate dispatch protection for this path. |
| `KOMEAddon` | Keep early tracker session startup and idempotent cleanup alongside dev's required native waypoint request guard. |
| `KOMECommonProxy` | Keep dual-bus tracker registration and dev's dedicated-server-rejecting client publication boundary. |
| `KOMEClientData` | Keep completed ownership projection. Remove the now-redundant tooltip batch assembler; pending data belongs to the single client publisher. Retain public tooltip clearing and dev's revision semantics. |
| `KOMEPacketConquestData` | Resolve conflict by feeding dev's publisher with independently materialized validated records; retain receiver-owned decoded data to avoid redundant recompression and preserve addressed empty population rows. |
| `KOMEPopulationWire` | Retain dev's `1.0.8-integration-g2` identity and strict validation with the allocation-free equivalent UTF-8 check. |
| `KOMEClientTaskQueueTest` | Preserve tests from both branches. Fixture proxies use the actual publisher; the immediate-dispatch fixture binds it to the correct queue. The decode-isolation fixture starts a reset generation as dev requires. No behavior assertions removed. |
| `KOMEPopulationConfigFoundationTest` | Preserve dev's development/ceiling semantics and the prior behavioral shutdown check, allowing both configuration and tracker cleanup. |

Dev independently added complete conquest-snapshot publication. The combined
publisher publishes its completed ownership projection before incrementing revision,
retains no partial population/waypoint records in public state, and rejects queued
publications from an invalidated session/world generation. Non-reset continuation
cannot initialize a session. After a completed baseline, existing non-reset delta
semantics remain supported, including an explicitly empty addressed population row.
Only completed authoritative states are coalesced. There is at most one incoming
and one retained completed state, plus the bounded publication queue; no raster scan
is added. Server send frequency remains KOM-74's separate follow-up.

Tracker/resolver sources and geometry/gameplay-default resources are unchanged from
the reviewed KOM-60 tip. Dev's population, recruitment, diplomacy, build configuration
and native-request-guard behavior remain intact. No company policy is introduced.

## Compatibility and evidence limits

Current dev requires **root schema 5, Build schema 4, population-development schema 1,
capital schema 1, protocol g2**. Root-schema mismatches fail closed; there is no
supported migration for the older schema-4 disposable world in this task. Matching
client/server artifacts and a compatible fresh disposable world are required for
combined live acceptance. Do not install this build into the established older world.
The declared stock LOTR v36.15 and GeckoLib build inputs were supplied to this isolated
checkout without modifying their original copies; normal build isolation tests verify
the exact stock LOTR hash. No earlier worktree/audit output is a build dependency.

Earlier live evidence: actual player and genuinely hired Brodda crossed T442 -> T401
with one transition each and fresh callback queries. Player initialization, stationary
deduplication, reconnect/dimension changes, hire reload and populated stop cleanup
were observed. Population/waypoint refresh fixes have instrumented live evidence.
The user's latest acceptance covers **population-menu flicker only**. Combined-client
rendering/reconnect, waypoint visual acceptance, player respawn, broader hire lifecycle,
multiplayer and low-end performance coverage remain pending; synthetic workloads do
not prove them. Keep KOM-74 traffic optimization separate.

## KOM-46 handoff

Use the unchanged server-thread API documented in
[KOME_SERVER_TILE_AWARENESS.md](KOME_SERVER_TILE_AWARENESS.md):
`KOMEServerTileAwareness.INSTANCE.current(entityUuid)` and `subscribe(listener)`.
Query availability before using an observation. During callbacks, re-query and compare
session/incarnation/observedTick with the historical event. Unavailable, unloaded,
removed or unresolved locations are not strategic movement, arrivals or splits.
Company eligibility/membership, detachment policy and campaign-hire policy remain
KOM-46/KOM-76 responsibilities; the tracker mutates none of them.

The review branch can be fetched for API integration immediately after publication.
Keep KOM-46's KOM-60 dependency until the reviewed implementation is merged into dev
(or the team explicitly approves another shared integration baseline). Publishing a
PR alone does not make the API available on dev or complete KOM-60.

## Validation

Combined focused tests: **67 passed, zero skips/failures/errors**.
One `.\gradlew.bat clean test build --no-daemon --console=plain` passed:
**1,091 discovered, 1,087 passed, four skipped, zero failures/errors**.
Two skips are existing Windows symlink cases; two optional transformer tests point
to legacy `../LOTR-Test-Server/mods` JARs absent from this isolated checkout.
Mandatory declared-dependency fingerprint/transform tests passed against the actual
stock LOTR build input. No skip was treated as a pass. The combined tracker,
lifecycle, real packet/chunk builder, public-access, geometry and gameplay suites
passed. No assertion was removed to accommodate reconciliation.

Combined production JAR SHA-256:
`2fff9fbced22dbb308a5e451c888ca5d386d1025e559008c30eda44c5f2f0c11`.
All source/resource hashes were rechecked against the clean build before commit.
Original `31f8fb2` runtime acceptance is not relabelled as combined live evidence.
Review-branch live acceptance requires a separate compatible disposable environment;
none was started or changed during publication preparation.
