# PR review and assigned-task closeout — October 7, 2026

One closeout checklist for PR #30, PR #26 and Dayne's eight active tickets.
No merge, shared deployment, geography repaint, optional in-world borders or tile editor.
Current Linear descriptions/comments and prior reports were read before validation.
KOM-47 is Done and merged; KOM-48 and KOM-26 remain separate owning tasks.

## Resumed acceptance — newer dev

After the user resumed, dev had advanced to
`69dbad5a8d2d1cef461e8327b961ce9194d8241f` and PR #26 reported conflicts.
The isolated clone preserves the validated fixes at `a4cf06c`, then reconciles
current dev at `d7d4a5a4e0bf810ce321b923b6482cb7d5406406`.
The reset hold remains before progression NPC observation/reconciliation; both
character-storage isolation checks remain. Root schema 12 and new dev's progression
shelter persistence coexist. No shared checkout or dev branch was merged or deployed.

Fresh complete gate: **2,888 discovered / 2,883 passed / five skipped / zero failures/errors**.
Eight fresh disposable Forge launches passed mounted interruption, recovery and
completion in both chunk-load orders, plus governance and daily command/save/cold-restart
checks. [Exact resumed evidence](resume-validation.json) links the archived tests,
logs and native fixture sources. These use deobfuscated classes and Java 8u492;
they do not certify a production-JAR client or live multiplayer.
The current matching production JAR is
`c49456694be28a870ed29a47dc66c3ec32e098fb7d9ef0b8cacd8756aa36c726`.
See [resumed-candidate.json](resumed-candidate.json) for exact configuration.
Current profile: **KOME Mounted Reset Acceptance dev 69dbad5**;
server: `build/pr-review-20261007/reset-runtime/server`, loopback `127.0.0.1:51326`.
It copies only the cleanly stopped disposable world, preserving B1. Earlier
profiles, worlds and artifacts remain separately recorded. Prior human passes
retain their original candidate identity and are not silently promoted to this JAR.

The production JAR passed the six native resolver controls and isolated-data company
diagnostic/once-only movement audit probes. The authenticated non-operator is connected.
An explicitly direct-API mounted fixture is at Mordor's T378 capital anchor
X 97728.5/Y 200/Z 59712.5; its native Gondor destination is T388,
X 78016.5/Y 200/Z 66240.5. Initial observed rider/mount HP is 3.25/7.125.
The current human check is opening the owned rider menu. This fixture is not paid
recruitment, and no human reset/restart or multiplayer pass has been recorded yet.

## PR readiness at the first review baseline

| PR | Verified starting head / base | Review result | Exact readiness |
|---|---|---|---|
| [#30](https://github.com/daynenewman/KOME-LOTR-Addon/pull/30) | `9901f4f8194930956d41dbfe48bd59b46f9fe87f` / dev `b826ef35a0fb88ce9b2d58129ed307e9986b43d7` | 26 documentation/instruction/checker files; no Java/resource changes. Routing checker PASS: 25 documents, 695 links, 853 symbol references, 48 exact JUnit selectors. Whitespace check PASS. No blocking finding or unresolved review thread found. | Review-ready and conflict-free. Still draft; merge authorization remains absent. GitHub reported no check runs/status entries; the empty combined `pending` status is not a failed check. Branch endpoint reports dev unprotected with no required status contexts. |
| [#26](https://github.com/daynenewman/KOME-LOTR-Addon/pull/26) | `4b55b59e923028a9ed59233176e41235846efece` / same dev | Fixed actual production options transformer crash and screenshot-reproduced Create Build overlap; added available KOM-82 company diagnostics and daily movement-credit audit. Corrected obsolete publication wording. Fresh full test/build PASS. | Source gate passes; wider connected-client/campaign/multiplayer/hardware acceptance remains open. Keep draft. Original #24/#25 are closed/superseded and their heads remain preserved. |

PR #30 was checked at its exact archived head, using its root AGENTS.md and routing
index/guides. PR #26 was reviewed against current dev and the historical campaign
transcripts. The seven historical Forge launches are reused only for unchanged
reset/governance/daily authorities, with their original source and artifact limits.

## Changes and new validation

- `KOMEFactionTitleOptionsTransformer`: supports the stock production SRG method
  names as well as MCP names, preserving exact descriptor/count checks and idempotence.
  The October 6 native crash, failing-before regression and user menu/map passes are
  retained in [the prior acceptance report](../tile-acceptance-20261006/README.md).
- `KOMEAdminDiagnostics`: company inspection uses the existing coherence service,
  canonical movement credits and cached physical observations. No world/chunk loading,
  inferred location repair, teleport or mutation. Output stays within 20 × 512 characters.
- `KOMEMovementDayService`: actual observed credit restoration appends one bounded
  `MOVEMENT/ALLOWANCE_RESTORED` audit. Repeat boundaries and NBT reload do not duplicate it;
  offline anchoring and schedule changes emit none. KOM-48's coordinator is not replaced.
- Focused four-suite gate: **41 passed, zero skips/failures/errors**.
  Full offline Gradle 8.5 `test build`, Java 17 compiler / Java 8 target, two workers,
  1 GiB Gradle heap, under `Local\KOME-Heavy-Validation`:
  Before the GUI repair: **2,782 discovered; 2,777 passed; five skipped; zero failures/errors**.
  The screenshot-reproduced Create Build overlap then received a failing-before
  layout regression and spacing repair. Final full gate: **2,783 discovered;
  2,778 passed; five skipped; zero failures/errors**, including all six actual-screen
  Build interaction tests. Coordinates, type controls, hours and validation/warning
  rows now have separate space. User reported the repaired form looks better on
  the matching final artifact; this does not certify every window size.
  Three optional externally configured LOTR-JAR checks and two Windows symlink checks
  are the skips. [Candidate manifest](candidate.json) lists the exact tests.
- Production Forge classloader/server-thread probe passed company output bounds,
  unchanged NBT after inspection, actual unauthorized root dispatch before world access,
  and once-only movement audit across native NBT round trip. These are isolated data
  fixtures; the sender is synthetic and the round trip is not a cold JVM restart.

## Pre-resume candidate and environment

Matching pre-resume production KOME client/server SHA-256:
`2816008d37c2171fdd41e0af2354f0dd7308fff868c73e020c77449bc9bbb5de`.
All 1,972 packaged classes use Java 8 bytecode. Versus the options-fixed October 6
candidate, the two KOM-82 classes and Tile Command layout differ; geometry and other authority classes
are byte-identical. Full mod/config hashes are in [candidate.json](candidate.json).
The earlier October 7 candidate `7ab04d...` remains preserved and is the artifact
for all selection/zoom/scale/resize/cancellation observations recorded before repair.
The layout repair changes only `KOMEGuiConquestCapture.class` relative to that candidate.

- Pre-resume Prism profile: **KOME PR Review Acceptance Layout Fix**,
  `kome-review-acceptance-layout-20261007`; heap 1536 MiB, Java 8u482, Forge 10.13.4.1614.
- Pre-resume server: `build/pr-review-20261007/layout-runtime/server`; world `review-acceptance-fresh`,
  seed 5716063, loopback `127.0.0.1:51326`, online authentication/whitelist enabled,
  no operators, view distance 4, heap flag 1280 MiB.
- The first October 7 world copied no terrain or canonical KOME root. Two native records for the prior
  disposable test character were copied into the new world to avoid repeating character
  creation; [character-fixture.json](character-fixture.json) records source/destination hashes.
  After clean save/stop, the layout-fix runtime uses a copy of that task-owned
  disposable world. Both earlier runtimes/profiles/worlds remain preserved.
- T401 platform: X 237248.5, Y 181, Z 87295.5; T442 starts at Z 87296.
  Native controls again passed T401/T442, unclassified R1 gap, T001, outside-mask
  and unsupported Overworld states. Production exclusions remain zero zones/cells.
- Native server reported `Done (3.110s)`; launcher request to Done spans about 13 s
  at log-second precision. This is one cold fresh-world launch, not a controlled
  incremental startup comparison. Initial idle timing includes platform/chunk loading;
  connected-client timing is a different workload and is not subtracted from it.
- The final layout candidate resumed the saved disposable world with `Done (0.893s)`.
  Its 60 one-second, actual client-thread samples in Tile Command at 3840×2054,
  Normal GUI scale (2), render distance 12, VSync enabled and 120-FPS cap were all
  120 FPS. One connected player, no hired fixtures/forced chunks: 100 native server
  tick samples averaged 1.608 ms, p95 2.089 ms, maximum 2.585 ms; used heap was
  439,437,872 bytes at the observation. These are total-workload samples, not
  incremental tile-cost or retained-snapshot measurements.
  [Final measurements](layout-measured-results.json) and [earlier measurements](measured-results.json)
  preserve their separate artifact identities, including the earlier resize dips.

## Closeout checklist

`PASS` below means the stated observed scope, not certification of an entire ticket.

| Ticket / requirement | Evidence and result | Still prevents closure / next action |
|---|---|---|
| KOM-43 canonical exact geometry, shared lookup, immutable publication | PASS: unchanged resolver/resource classes; historical whole-raster/gameplay oracles and fresh native six-state controls. | Parent remains open for KOM-57/58/59/60/63 acceptance below. |
| KOM-57 O(1), load/reload and bounded immutable snapshot | PASS: unchanged automated performance/lifecycle evidence; native reload and failed-replacement tests retained. | Representative lower-spec startup/lookup/memory/tick/FPS comparison and multi-player workload; no new index without measured need. |
| KOM-58 explicit geography metadata | PASS: merged validated schema and zone-aware runtime; zero production exclusions is explicit. | Approve exact cells, type and reason; [region/cell proposals](geography/README.md) prepared. Unknown gaps remain unknown. |
| KOM-59 raster coverage and mountain contacts | PASS: read-only atlas/whole-raster inventory and protected controls retained. 43 regions, 26,664 concern cells, five pairs with 99 exact contact cells have prepared alternatives. | Select side/width/classification and review topology/reference impact before any repaint; Harnen T455/T654 needs exact live IDs/version/location. |
| KOM-60 hired lifecycle and movement | PASS: prior actual native fixtures cover initialize, stationary dedupe, T401→T442, chunk unload/reload, inactive dismissal, removal, stop and cold reload. | Fixtures were native owner/task API setups, not ordinary paid campaign-hire transactions; human hire/dismiss/company flows remain. |
| KOM-60 player boundary, reconnect, respawn, dimensions | PASS: prior human T401→T442 without No tile, reconnect T442, Respawn T401; native connection/incarnation callbacks and character-creation transition to dimension 100. | Further human dimension change, moving reconnect and simultaneous-player consistency remain. |
| KOM-63 HUD/map, hover, selection | PASS: prior human menu/map borders and scoped hover alignment. New user reported `t401` to selection test, `it looks in order` after three wheel notches out, accurate fill/Tile Command at Normal scale (2, 3840×2054), and accurate resized alignment (2, 2648×1452). | Further settings/edge/corner combinations remain outside these observations. |
| KOM-63 non-operator Build and permissions | PASS: prior admin denial and atomic mismatch rejection; denied-state disabled Create; eligible form opening and Build List cancellation with all 77 sections/zero Builds/sequence unchanged. Initial unpledged assumption corrected to Dale pledge/unclaimed T401. Screenshot overlap repaired with failing-before regression; user reported improvement and subsequent screenshot shows separated readable rows. Valid non-operator B1 creation passed: screenshot and native NBT agree on 1.00 approved / 0.00 developed / 1.00 awaiting development, Normal, Gondor, T401; creator-manager initial hours auto-approve. [Creation evidence](build-creation.json). Malformed 1.001-hour input displays the expected validation message and all 77 sections remain unchanged. [Rejection evidence](build-invalid-hours.json). | Saved B1 reconnect also passed; other Build permission/rejection flows and second-player synchronization remain. Gondor pledge and tile claim are disposable fixtures, not ordinary pledge/claim acceptance. |
| KOM-63 multi-player consistency | No live multi-player pass claimed. | Second authenticated player on matching candidate; compare HUD/map/ownership/Build/transition/reconnect updates simultaneously. |
| KOM-63 measured client/server performance | PASS: prior actual 21-native-hire and one-client total tick windows; actual high-spec world/map FPS with exact settings. New startup and isolated native timing/FPS samples are being retained. | No isolated tile-cost comparison, multi-player capacity or representative lower-spec hardware result; do not generalize high-spec measurements. |
| KOM-28 reset inside/outside-native decisions and mounted HP | PASS: historical seven Forge launches preserve UUIDs, 3.25 rider / 7.125 mount HP, canonical receipts/population, both recovery chunk orders and once-only effects; unchanged packaged authority classes. | Connected-client mounted reset/restart observation and simultaneous-player synchronization; future physical siege/assault cleanup depends on owning authorities. |
| PR #26 governance permissions/client synchronization | PASS: historical native command and deterministic permission/persistence tests, fresh complete regression gate. | Human permission changes and synchronized UI/commands on two clients; synthetic permission tests do not establish that. |
| PR #26 daily development-before-payout and once-only effects | PASS: historical actual Forge/cold-restart transcripts and fresh coordinator/development tests. | Required stages with unavailable owning adapters stay blocked, including KOM-48 movement coordinator integration; connected-client summary observation remains. |
| KOM-82 available audit/inspection integration | PASS: new company coherence/credit diagnostics and once-only credit-restoration audit; actual production-loader isolated-data probe and behavioral regressions. [Coverage matrix](KOM-82-coverage.md) accounts for original KOM-40 targets. | Company physical repair without an unambiguous ordinary service is not invented. Episode/sortie/full siege and physical muster authorities, full coordinator order and associated client acceptance remain dependent. Discord transport is optional. |

## Evidence and tracking

[Human observations](human-observations.json) retains exact replies and pending tests.
[Linear update drafts](linear-updates.md) correct obsolete #24/#25/open/head/schema and
KOM-47 dependency wording; none has been posted or any status changed.
Historical [consolidation evidence](../pr-consolidation-evidence-20261005/)
and [October 6 acceptance](../tile-acceptance-20261006/README.md) remain dated, separate evidence.
The final protected-state comparison covers existing worktree HEAD/status, user refs,
stash and runtime file metadata; intentional new task files are excluded. It is not
a retroactive byte snapshot of pre-existing uncommitted changes.

## Session stop

User stopped further testing after confirming saved B1 survives reconnect with
1.00/0.00/1.00h and no duplicate. No test is pending user input. The new implementation
and reports are prepared in the isolated candidate, but have not been committed or
pushed to PR #26. The disposable world is saved and its server is stopped cleanly.
