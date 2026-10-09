# PR review and assigned-task closeout — October 7, 2026

One closeout checklist for PR #30, PR #26 and Dayne's eight active tickets.

No merge, shared deployment, geography repaint, optional in-world borders or tile editor.

Current Linear descriptions/comments and prior reports were read before validation.

KOM-47 is Done and merged; KOM-48 and KOM-26 remain separate owning tasks.

## October 9 integration with current dev

Latest correction: [Join Battle disconnect evidence](../pr26-joinbattle-fix-20261009/README.md). The human fatal disconnect is retained FAIL: an older `9b267eb...` g4 client decoded the new integrated server's response ID 54, which it lacked. Join Battle had retained g4 after adding IDs 53-58. The narrow g5 identity correction rejects that mismatch before gameplay packets. **66 focused tests passed; clean gate 3,079 passed / five skipped / zero failures/errors**. New production JAR `ec5181d81faa279a59fa228f3f830f6564e704ca631a533271b2dba3bac4c823`: actual loaded client/server hashes match; automated authenticated non-operator command and GUI Refresh round trips pass. **Human retest PASS** on **KOME PR26 Join Battle g5**, **51327**: user confirmed T388 panel, no-active-battle text, disabled Join and continued connection. Frozen **51326** remains unchanged.

PR #26 now incorporates dev `f154deaed18ac3f1a7770ffa3bda8a88382a9e35` in an isolated private checkout. Four conflicts are resolved, preserving root schema 12, reset/recovery receipts, surviving mounted HP and governance/daily journals alongside Join Battle's ConflictData v2/formal-retreat authority. [Exact integration evidence](../pr26-integration-20261009/README.md) includes two reproduced product fixes, retained failures and raw source/artifact hashes.

Initial integration gate: **333 focused tests passed; clean test/build 3,078 passed, five skipped, zero failures/errors; eight disposable deobfuscated Forge restart gates plus one actual production linkage gate passed**. That production JAR was `263e8ff420b0e84c81f9b802a58b7d46182dee4158bfa41c60afc213b9e43eb0`; the current g5 artifact is identified above. The PR remains draft. Its current published head is recorded in GitHub metadata; dated reports pin exact parents and validated source/artifact hashes to avoid circular commit identities.

The frozen `9fc21f5b...44f9ef` acceptance runtime is unchanged. Earlier human passes, including R1 HUD/map, remain evidence for their original artifacts. **Multiplayer is explicitly deferred; no second account setup or pass is required/claimed now.** The g5 no-active-battle UI/disconnect check now passes; actual battle/governance denial and changed mounted landing/reconnect remain broader single-player acceptance. Geography decisions, lower-spec measurements and unfinished dependencies still prevent broader ticket closure.

## Frozen human-acceptance candidate — mounted reload correction

Frozen matching JAR: `9fc21f5b469d8d31e4ac54c039aaf8a5b9948a429c061c3a271b7681a544f9ef`.

Profile: **KOME Mounted Reload Fix**, `kome-review-mounted-reload-fix-20261007`;

server: `build/pr-review-20261007/reload-fix-runtime/server`, `127.0.0.1:51326`.

[Exact candidate](reload-candidate.json) records hashes and configuration. The world

copies only the cleanly stopped previous disposable world; all earlier candidates

and the original rider/horse UUIDs remain preserved. No replacement was spawned.

The c494 candidate's reset return passed the user's mounted-rider/T388 observation,

and first cold load preserved partial HP and once-only receipts. Subsequent human

reconnect **failed**: the rider was absent from the tracked world list while still

ticking through its horse. The actual production guard reproduced rejection of a

legitimate same-UUID reload while its old copy remained queued for chunk unloading.

`KOMESeasonResetDeployment.find` now ignores queued unloads, using verified MCP/SRG

field names; an unavailable unload list fails explicitly. Active duplicate rejection

and virtual receipt requirements remain intact. Two new behavioral regressions

failed before the fix. The intermediate test fixture needed distinct vanilla entity

IDs because constructor-bypassed entities otherwise compare equal; that was corrected.

Fresh gate: **2,890 tests / 2,885 passed / five skipped / zero failures/errors**.

Six fresh deobfuscated Forge reset launches passed interruption/recovery/completion

in both chunk orders; [archive and source manifest](reload-validation.json) retain

exact identities. The two earlier governance/daily launches are reused for unchanged

authority. In the actual production loader, active duplicate rejection passed and

the legitimate queued-unload replacement was accepted. The original mounted rider

loaded from native disk and remained tracked after authenticated reconnect; the

user confirmed rider visibility, command menu and T388 HUD. A further actual repaired-server cold restart also passed the human reconnect check: [native identities/partial HP and receipts](reload-live-restart.json) stayed unchanged. The [dimension round trip](dimension-round-trip.json) passed both human HUD observations and native fresh callbacks. Those reload observations do not establish multiplayer or lower-spec acceptance; later paid-hire evidence is recorded separately.

Source correction and initial evidence were published to PR #26 at
`c2765f7e4a26941b2172affb30113c39e7ee60d6`. The latest human/native
records, including R1 HUD/map passes, are published as a documentation follow-up
with unchanged 1,026 main-source hashes. The candidate remains based on dev
`69dbad5a8d2d1cef461e8327b961ce9194d8241f`.

At the October 9 pre-integration checkpoint, dev was `f154deaed18ac3f1a7770ffa3bda8a88382a9e35` and PR #26 reported conflicts. [The historical conflict preview](reconciliation-preview-20261009.txt) remains preserved; the current validated resolution is recorded above. PR #30 was draft/open and conflict-free at `9901f4f`; its checker provenance remains its pinned baseline and dev `69dbad5`, not f154dea.

Neither PR was merged. See [publication checkpoint](publication-checkpoint-20261009.json).

## October 7 resumed acceptance — dev 69dbad5

After the user resumed, dev had advanced to

`69dbad5a8d2d1cef461e8327b961ce9194d8241f` and PR #26 reported conflicts.

The isolated clone preserves the validated fixes at `a4cf06c`, then reconciles

that day's dev at `d7d4a5a4e0bf810ce321b923b6482cb7d5406406`.

The reset hold remains before progression NPC observation/reconciliation; both

character-storage isolation checks remain. Root schema 12 and new dev's progression

shelter persistence coexist. No shared checkout or dev branch was merged or deployed.

Fresh complete gate: **2,888 discovered / 2,883 passed / five skipped / zero failures/errors**.

Eight fresh disposable Forge launches passed mounted interruption, recovery and

completion in both chunk-load orders, plus governance and daily command/save/cold-restart

checks. [Exact resumed evidence](resume-validation.json) links the archived tests,

logs and native fixture sources. These use deobfuscated classes and Java 8u492;

they do not certify a production-JAR client or live multiplayer.

That earlier matching production JAR is

`c49456694be28a870ed29a47dc66c3ec32e098fb7d9ef0b8cacd8756aa36c726`.

See [resumed-candidate.json](resumed-candidate.json) for exact configuration.

That earlier profile: **KOME Mounted Reset Acceptance dev 69dbad5**;

server: `build/pr-review-20261007/reset-runtime/server`, loopback `127.0.0.1:51326`.

It copies only the cleanly stopped disposable world, preserving B1. Earlier

profiles, worlds and artifacts remain separately recorded. Prior human passes

retain their original candidate identity and are not silently promoted to this JAR.

The production JAR passed the six native resolver controls and isolated-data company

diagnostic/once-only movement audit probes. The authenticated non-operator was connected during these checks.

The initial Mordor fixture was killed by an orc before the menu check; that check

is inconclusive. The replacement direct-API fixture is in an enclosed bedrock arena

at Rohan T348, X 48704.5/Y 200/Z 53568.5; its native Gondor destination is T388,

X 78016.5/Y 200/Z 66240.5, also enclosed. User saw the replacement rider dismounted;

the helper had omitted LOTR's NPC-horse ownership/taming setup. Correcting the helper

retains the surviving safe UUIDs and uses ordinary stock mount/halt APIs.

At that fixture setup no paid recruitment or human reset/restart pass existed. Later mounted-return and repaired cold-reconnect passes are recorded above; multiplayer remains untested.

PR #30's exact archived head still passes its original checker. Its 26 files were

also overlaid in a separate private dev-69dbad5 checkout: **25 documents, 695 links,

853 symbol references and 48 exact JUnit selectors passed**, with whitespace checks

passing. This refresh confirms routing references/discoverability, not a new semantic

audit of every guide; the guide provenance remains explicitly pinned to b826ef35.

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

| PR #26 current-dev integration | PASS: exact dev f154dea parents/source/JAR, 333 focused and 3,078 full passing tests; eight native restart gates plus production linkage. [Evidence](../pr26-integration-20261009/README.md). Later g5 correction and clean gate recorded below. | Draft; no-active-battle human UI/disconnect check now passes. Actual battle/governance and changed mounted placement/reconnect acceptance remain. Multiplayer deferred. No dev merge or deployment. |
| PR #26 Join Battle protocol disconnect | Original human FAIL retained with actual old-client/server identities and first undefined-discriminator-54 exception. Narrow g5 handshake repair: 66 focused / 3,079 full tests passed; exact production loaded hashes, real command/GUI Refresh and non-operator proofs; human T388 blocked-panel/no-disconnect retest PASS. [Evidence](../pr26-joinbattle-fix-20261009/README.md). | This scoped failure is resolved. Actual active-battle entry/governance and wider multiplayer acceptance remain separate; no full ticket closure is inferred. |

| KOM-43 canonical exact geometry, shared lookup, immutable publication | PASS: unchanged resolver/resource classes; historical whole-raster/gameplay oracles and fresh native six-state controls. | Parent remains open for KOM-57/58/59/60/63 acceptance below. |

| KOM-57 O(1), load/reload and bounded immutable snapshot | PASS: unchanged automated performance/lifecycle evidence; native reload and failed-replacement tests retained. | Representative lower-spec startup/lookup/memory/tick/FPS comparison and multi-player workload; no new index without measured need. |

| KOM-58 explicit geography metadata | PASS: merged validated schema and zone-aware runtime; zero production exclusions is explicit. | Approve exact cells, type and reason; [region/cell proposals](geography/README.md) prepared. Unknown gaps remain unknown. |

| KOM-59 raster coverage and mountain contacts | PASS: read-only atlas/whole-raster inventory and protected controls retained. 43 regions, 26,664 concern cells, five pairs with 99 exact contact cells have prepared alternatives. | Select side/width/classification and review topology/reference impact before any repaint; Harnen T455/T654 needs exact live IDs/version/location. |

| KOM-60 hired lifecycle and movement | PASS: prior actual native fixtures cover initialize, stationary dedupe, T401→T442, chunk unload/reload, inactive dismissal, removal, stop and cold reload. | Older fixtures were API setups. Current real native Campaign Hire, separate ordinary hire/dismissal and survivor reconnect pass with exact payments, audit and tile queries in [paid-hire-lifecycle.json](paid-hire-lifecycle.json). Ordinary strategic movement/transition and wider company flows remain separate; no simultaneous-player claim. |

| KOM-60 player boundary, reconnect, respawn, dimensions | PASS: prior human T401→T442 without No tile, reconnect T442, Respawn T401; native connection/incarnation callbacks and character-creation transition to dimension 100. | The current repaired production dimension round trip also passed: HUD hidden in Overworld and T388 restored, with two fresh native dimension callbacks. Moving reconnect and simultaneous-player consistency remain; portal/fast-travel permissions were not exercised. |

| KOM-63 HUD/map, hover, selection | PASS: prior human menu/map borders and scoped hover alignment. New user reported `t401` to selection test, `it looks in order` after three wheel notches out, accurate fill/Tile Command at Normal scale (2, 3840×2054), and accurate resized alignment (2, 2648×1452). | Current R1 gap HUD says No tile; right-clicking the centered player-marker point opens no Tile Command. [Exact gap evidence](gap-acceptance.json). Further settings/edge/corner combinations remain outside these observations; unknown gaps are not classified by these passes. |

| KOM-63 non-operator Build and permissions | PASS: prior admin denial and atomic mismatch rejection; denied-state disabled Create; eligible form opening and Build List cancellation with all 77 sections/zero Builds/sequence unchanged. Initial unpledged assumption corrected to Dale pledge/unclaimed T401. Screenshot overlap repaired with failing-before regression; user reported improvement and subsequent screenshot shows separated readable rows. Valid non-operator B1 creation passed: screenshot and native NBT agree on 1.00 approved / 0.00 developed / 1.00 awaiting development, Normal, Gondor, T401; creator-manager initial hours auto-approve. [Creation evidence](build-creation.json). Malformed 1.001-hour input displays the expected validation message and all 77 sections remain unchanged. [Rejection evidence](build-invalid-hours.json). | Saved B1 reconnect also passed; other Build permission/rejection flows and second-player synchronization remain. Gondor pledge and tile claim are disposable fixtures, not ordinary pledge/claim acceptance. |

| KOM-63 multi-player consistency | No live multi-player pass claimed. | Deferred by user: second authenticated player on matching candidate; compare HUD/map/ownership/Build/transition/reconnect updates simultaneously. |

| KOM-63 measured client/server performance | PASS: prior actual 21-native-hire and one-client total tick windows; actual high-spec world/map FPS with exact settings. Current repaired candidate: [60 actual client samples](reload-measured-results.json) ranged 14–121 FPS (mean 103.07, median 120) across world/menu/map and 854×480 → 3840×2054 resize. First map sample was 14 FPS at zoom 1; do not describe this run as all 120 FPS. Server 100-tick window averaged 1.883281 ms, p95 2.5551 ms, max 4.0695 ms, 522,461,112 bytes used heap. Workload: one player plus original mounted fixture; no incremental cost comparison. | No isolated tile-cost comparison, multi-player capacity or representative lower-spec hardware result; do not generalize high-spec measurements. |

| KOM-28 reset inside/outside-native decisions and mounted HP | PASS: six fresh reset Forge launches on the repaired source preserve UUIDs, 3.25/7.125 HP, receipts/population and both chunk orders. Earlier c494 human menu and mounted-return/T388 checks passed; native first cold load retained exact partial HP and 1/1/1 ownership/return/completion audits. Subsequent human reconnect failed, prompting the queued-unload guard correction and renewed matching acceptance. | Repaired production human reconnect now passes, including a further actual cold restart; saved first-load partial HP and once-only receipts remain exact. Simultaneous-player synchronization and future physical siege/assault cleanup remain separate. |

| PR #26 governance permissions/client synchronization | PASS: historical native command and deterministic permission/persistence tests, fresh complete regression gate. | Human permission changes and synchronized UI/commands on two clients; synthetic permission tests do not establish that. |

| PR #26 daily development-before-payout and once-only effects | PASS: historical actual Forge/cold-restart transcripts and fresh coordinator/development tests. | Required stages with unavailable owning adapters stay blocked, including KOM-48 movement coordinator integration; connected-client summary observation remains. |

| KOM-82 available audit/inspection integration | PASS: new company coherence/credit diagnostics and once-only credit-restoration audit; actual production-loader isolated-data probe and behavioral regressions. [Coverage matrix](KOM-82-coverage.md) accounts for original KOM-40 targets. | Real paid Campaign Hire and ordinary dismissal now validate available UNIT audits on the production JAR. Company physical repair without an unambiguous ordinary service is not invented. Episode/sortie/full siege and physical muster authorities, full coordinator order and associated client acceptance remain dependent. Discord transport is optional. |

## Evidence and tracking

[Human observations](human-observations.json) retains exact replies and pending tests.

[Linear update drafts](linear-updates.md) correct obsolete #24/#25/open/head/schema and

KOM-47 dependency wording; none has been posted or any status changed.

Historical [consolidation evidence](../pr-consolidation-evidence-20261005/)

and [October 6 acceptance](../tile-acceptance-20261006/README.md) remain dated, separate evidence.

The final protected-state comparison covers existing worktree HEAD/status, user refs,

stash and runtime file metadata; intentional new task files are excluded. It is not

a retroactive byte snapshot of pre-existing uncommitted changes.

## Prior session stop

User stopped further testing after confirming saved B1 survives reconnect with

1.00/0.00/1.00h and no duplicate. At that stop, no test was pending; the layout-world server was saved and stopped.

That historical stop predates the resumed current-dev candidate above. Validated

source reconciliation and reports are now committed in the private clone; publication

and current mounted acceptance are tracked separately.

## October 7 acceptance pause and October 9 resume

User explicitly paused after the R1 gap HUD/map checks. On October 9, multiplayer and second-account setup were subsequently deferred in favor of current-dev integration. Existing acceptance client/server and disposable world were left unchanged. On October 9 the user resumed publication and multiplayer preparation. Historical pause state is retained in the gap record; live multiplayer remains pending. No merge, deployment or Linear posting occurred.
