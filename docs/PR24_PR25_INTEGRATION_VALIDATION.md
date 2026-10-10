# PR #24 + #25 integration validation

Historical October 4 schema-10 evidence. Current publication supersedes its
open-PR/readiness wording: PRs #24/#25 are closed as superseded; consolidated
PR #26 incorporates dev `b826ef35a0fb88ce9b2d58129ed307e9986b43d7` and root
schema 12. See [current review/acceptance checklist](pr-review-20261007/README.md).
The transcripts below retain their original revision and artifact limits.

Integration branch: `dayne/pr24-pr25-integration-20261004`. Prepared in the isolated `aff2` worktree on 2026-10-04 (America/Chicago). No merge or deployment.

## Refreshed inputs

- `origin/dev`: `f3743f27b9a9d3979a8f99f40685bc2e46b48ed3`.
- PR #24: `d19d8807bf68fffd708b99d82aeb8e5db03ee5d9`.
- PR #25: `b59ec9af6f9bfee20e0049d5fe4bc6d085058b93`.

The integration starts at PR #25 and applies PR #24 with explicit conflict resolution. Existing PR branches and every other checkout remain preserved. The original independent PR validation is historical; only this combined branch contains the corrections below.

## Unified persistence

Root schema **10** writes reset journals, hired-unit receipts, governance and daily coordination together. Older builds reject the newer root.

| Input | Required original authority | Upgrade behavior |
| --- | --- | --- |
| 6 | Population, development, season/defeat, muster, companies/units and existing canonical sections | Empty conflict registry only when all conflict section keys are absent; never infer conflicts. Preserve and validate any present conflict/reset/governance/daily extension. |
| 7 | Schema 6 authorities plus canonical conflicts | Add absent reset/governance/daily sections; preserve present extensions. |
| 8 | Schema 7 authorities plus reset journal and entity receipt fields | Require the reset journal; preserve all pending returns, source chunks, UUIDs, mounted snapshots and receipt tokens. Add absent governance/daily sections. |
| 9 | Schema 7 authorities plus governance and daily journal | Require governance/daily authority. Add an empty reset journal only if absent; validate and preserve a present reset section. |
| 10 | All of the above | All sections required; malformed candidates fail closed before publication. |

Wrong-type optional sections cannot silently become empty defaults. Wrong reset list element types are rejected rather than losing pending companies. Tests round-trip original layouts and cross-branch sections twice, retaining authoritative data. No existing user world was migrated.

## Integration corrections

- RESET precedes governance reconciliation, daily coordination and legacy movement/muster/defeat processing. Direct coordinator calls also stop before any checkpoint or cursor mutation during RESET.
- The population fallback skips development boundaries during RESET; frozen payout cursors advance without crediting population, so reset days do not develop later.
- All planned return receipts are saved before any company can perform physical effects, protecting later virtual survivors if an earlier company is interrupted.
- Completion uses a durable checkpoint. Failure restores RESET, season/defeat state and the prior audit list; retry advances the season and records completion once.
- Forge terrain clearance uses the block-only collision query. The legacy entity query dereferenced a null entity when returned survivors already occupied the capital, preventing recovery.
- Entity snapshots deeply copy mounted NBT. Forge otherwise shares live `ForgeData` references, letting new receipt stamps alter historical snapshots.

## Validation

Evidence is task-local under ignored `outputs/pr24-pr25/` and `outputs/campaign-lifecycle/`. Every Gradle/Forge invocation holds `Local\KOME-Heavy-Validation`. Build launcher: Zulu JDK 17; real Forge: 10.13.4.1614, LOTR 36.15, Java 8 in the deobfuscated development server.

Initial focused gate: 73 passed, no failures/errors/skips before the two Forge-discovered corrections. One earlier migration assertion failed on a derived company-name fixture mismatch and was corrected without weakening authoritative state checks. Final full validation is recorded below when complete.

### Disposable Forge method

`tools/pr24-pr25/verify-forge.gradle` builds a test-only mod, uses an ephemeral port and a task-owned world, rejects overwriting an existing first-phase world, and requires explicit PASS evidence instead of trusting the server exit code.

The first phase creates two native Gondor soldiers, each mounted on a native horse, with 3.25 / 7.125 HP. One company is stationary and one virtual. It interrupts after the first company's real Anvil return receipts but before strategic publication. After orderly shutdown, it inserts the original virtual source snapshot into the disposable Anvil chunk to model an obsolete chunk surviving a crash. The stopped fixture is cloned locally for both load orders.

Each recovery starts a new Forge JVM, verifies both regions initially unloaded, loads origin/destination in the selected order, rejects obsolete virtual riders/mounts, checks Submitted restrictions and the unchanged interrupted daily cursor, resumes the actual reset service, injects a completion checkpoint exception, retries, saves and stops. A further cold JVM checks completed returns and repeated completion. Exact UUID/HP, snapshots, population, receipts and audit counts are asserted. This models specific crash windows; it is not a hardware power-loss test.

Earlier failed disposable fixtures and logs are retained. They exposed the null-entity collision query and ForgeData snapshot aliasing; neither is counted as passing acceptance.

## Remaining scope and connected-client acceptance

No connected client participated. Client-visible positions, mount rendering, chat, real player combat/control permissions, reconnect timing and multiplayer remain pending.

- KOM-28 and KOM-30: delivered for review with the combined persistence/restart corrections; not merged or deployed.
- KOM-78: canonical safety and pending diagnostics delivered; faction-owned physical muster/control/receipt model remains unfinished. These existing purchased-company returns do not prove muster delivery. Encircled placement and old-season reserve policy remain undecided.
- KOM-48: available development-before-payout coordinator, durable stage receipts and reset precedence delivered. Full movement/allowance, starvation/scheduling and future siege/event authorities remain incomplete.
- KOM-82 and KOM-42: available hooks/diagnostics and deterministic/Forge integration evidence delivered; dependency-bound full campaign coverage remains incomplete. Optional Discord transport remains absent.

## Reproduction

Use `tools/campaign-lifecycle/validate.ps1` with `-GradleArgs`:

1. `@('-I','tools/pr24-pr25/verify-forge.gradle','runServer','-PverificationPhase=first','-PverificationOrder=origin-first')`.
2. After the server has exited, copy the task-owned `outputs/pr24-pr25/forge-origin-first` directory to the absent `forge-destination-first` sibling.
3. Run the same task with `-PverificationPhase=recover`, then `completed`, for each `verificationOrder`.
4. Run the existing campaign lifecycle Forge first/restart checks against this combined implementation.
5. Run `@('clean','test','build')`, with the existing LOTR compatibility jars supplied read-only through `KOME_LOTR_VALIDATION_DIRECTORY`.

Never copy into, launch, or modify a preserved user runtime/world. The test mod is excluded from the production jar.

### Completed reset runtime gates

All five final reset launches passed explicit checks: first interrupted return; origin-first recovery; destination-first recovery from the same stopped fixture; completed origin-first cold restart; completed destination-first cold restart. An earlier origin-first retry failed closed on occupied-capital terrain, then exposed live snapshot aliasing; those failed runs remain separate historical diagnostics.

Completion write denial is injected at the real service checkpoint boundary in Forge; atomic file failures are exercised in deterministic file tests. Native entity/chunk receipt readback is real Anvil I/O. This does not certify OS power-loss/fsync ordering or actual disk exhaustion inside a running server.

## Preservation evidence

All 21 other existing worktrees retain the recorded HEAD and porcelain status; stash object/name/description matches. Original `javaw` PID 44196 retains its start time. No existing checkout, world, configuration or runtime was edited, launched, stopped or deployed to. Shared Gradle caches/JDKs and copied dependency jars were reused. Preservation evidence is Git/process metadata and action scope, not a byte-for-byte audit of every ignored world file.

### Final combined full gate

`clean test build --offline --no-daemon --console=plain --max-workers=2` passed in 2m33s under the mutex: **2,104 tests, 2,102 passed, 2 skipped, zero failures/errors**. The skips are the existing Windows symbolic-link checks in `CustomSkinLibraryFoundationTest` and `ClientCustomSkinCacheTest`. Original LOTR compatibility inputs were supplied read-only and their tests passed. Nine additional integration regressions run alongside both PR suites; full validation includes the two Forge-discovered fixes.

Production jar: `build/libs/KOME-LOTR-Addon-1.0.8.jar`, SHA-256 `9DF21EB87C09E92410EBDBF5123E6492F488BD2B4E8780EF7EAB5D394BFDF0D5`. This jar was built, not deployed. `git diff --check` passed. Exact published revision and final remote-ref comparison are recorded in the PR evidence to avoid a self-referential commit hash here.

### Ordinary campaign runtime regression

The existing campaign lifecycle test mod also passed on this combined implementation in two fresh Forge JVMs: actual FakePlayer command dispatch, admin/non-admin checks, defeat capture, Submitted/Exiled choices, daily development before payout, atomic canonical checkpoint, save-all and cold restart. Restart retained one daily summary, 100 developed centi-hours and 10 centi-population without replay. Native factories resolved the original 47 entries (five mounted); this remains roster evidence, not physical muster delivery. No connected client was present.

Compact raw PASS transcripts and the full-suite summary are committed in [pr24-pr25-evidence](pr24-pr25-evidence/). Full logs, XML, preserved failure worlds and final disposable worlds remain task-local. The final jar hash still matched after all runtime checks.
