# Validation evidence

Historical test counts are not this branch's evidence.

- Read-only repository, Linear and GitHub discovery performed.
- `git fetch origin dev` succeeded with required Git metadata access; base `f3743f27b9a9d3979a8f99f40685bc2e46b48ed3`.
- GitHub CLI is absent from PATH; GitHub connector is available.
- Process inventory via Get-Process succeeded; CIM inventory was denied by sandbox. Existing javaw PID 44196 is untouched.
- All heavy Gradle/Forge commands must hold `Local\KOME-Heavy-Validation`. Use disposable worlds in this checkout only.

Focused tests, clean full gate, runtime dispatch/save/restart, final tested/published HEAD and preservation comparisons will be recorded here as they occur. Connected-client, multiplayer and hardware acceptance remain untested.

## Governance gates

All commands use `tools/campaign-lifecycle/validate.ps1`, Gradle 8.5 offline, Java toolchains, max workers 2 and `Local\KOME-Heavy-Validation`. Logs are retained in ignored `outputs/campaign-lifecycle`.

- `governance-first`: 51 tests passed, zero failures/errors/skips. Covers initial governance, defeat, delegation, claim, movement-access and campaign recruitment selections.
- `governance-expanded`: compile failure from a private ruler-map access introduced here; corrected to existing accessor.
- `governance-expanded-corrected`: 320 tests, 23 failures. Added governor mistakenly rejected existing ownerless/system movement fixtures; two conflict tests hard-coded schema 7/8. Corrections preserve the existing non-player movement validation and use current-schema expectations.
- `governance-regression-fixed`: 319/320 passed; remaining failure was duplicate muster rejection precedence after defeat. Restored ALREADY_USED response before the new participation check, with no mutation.
- `governance-checkpoint`: **320 tests passed, zero failures/errors/skips**, including 12 new governance tests, production compilation, movement/conflict, gate/ram, pledge, recruitment and schema regressions. The first full run will follow integration. A diagnostic `diff --check` with an incorrect `core.autocrlf=false` override reported CRLF line endings as whitespace; normal repository settings are authoritative and used below.

The new root version is 9; no existing world has been loaded or written by this branch. Local dependency jars were copied read-only from the primary checkout into ignored `libs/`; the primary files were not modified.

## Muster gate

- `muster-checkpoint`: **54 tests passed**, zero failures/errors/skips. Canonical conflict projection, non-loading dimension/chunk preflight, capital snapshots, pending-state persistence, uncertainty latch, governance and admin diagnostics covered. First attempt exposed one expectation for the former placeholder authority; corrected to the concrete dimension-unavailable result.
- These are service/NBT tests. No physical delivery or entity/world-save receipt is claimed; the faction-owned control contract is unavailable.

## Daily coordinator gate

- `daily-first`: compile failed because the new checkpoint helper could not access the canonical data name; made that existing constant package-visible.
- `daily-compiled`: **158 tests passed**, zero failures/errors/skips, including eight new coordinator tests over real population services and compressed atomic canonical-file saves. Covers each stage-save failure, repeat boundary, cold restart, offline payout-only catch-up, skipped interrupted development, blocked movement/starvation, deterministic clock, schedule changes and malformed journal fail-closed loading.
- Checkpoint guarantees cover canonical data only: synced temporary bytes plus atomic replacement, not entity/chunk durability or acknowledged chat delivery. Notification claim is persisted before sending; a crash after that claim may omit a summary but cannot replay it.

## Operations and cross-system review

- `operations-first`: 176 tests, one new test fixture failure (missing Build type); corrected fixture and asserted the real repair-denial response.
- `operations-reviewed`: **189 tests passed**, zero failures/errors/skips. Includes existing guarded/stale admin repairs and conflict/gate checks, actual root lifecycle diagnostics, audit bounds, new exile authorization and preserved native unit NBT/HP/progression/bank regressions.
- Review correction: host selection no longer invents a same-side selection policy; actual participation still requires ordinary host war-side authority. Native hired-unit combat checks its own funding/native faction rather than borrowing its exiled owner's host affiliation.

## Full and real Forge validation

- `clean-full`: clean `test build` passed in 1m44s: 2,074 tests, 2,069 passed, 5 skipped, zero failures/errors. Two skips are platform symlink support. Three optional original-jar compatibility inputs were absent from this worktree-relative default; the existing real jars were then supplied read-only using `KOME_LOTR_VALIDATION_DIRECTORY` and the focused transformer gate passed with no skips.
- `forge-first` and `forge-restart`: real Forge 10.13.4.1614 / LOTR 36.15 / Java 8 disposable server launches both passed, including explicit PASS evidence files. Test-only mod is in `tools/campaign-lifecycle/java`; it is not shipped in the main jar. Actual command dispatcher exercised submit/exile/status, lifecycle diagnostics, audit, non-operator rejection and save-all. It uses a Forge FakePlayer (no connected client).
- First phase invoked actual defeat and available daily services, using a fixed future boundary inside real Forge. It saved one development step (100 centi-hours), 10 centi-population, and one summary. Cold process startup retained origin/Submitted restriction and all three values without repeating effects. This is controlled-clock runtime evidence, not an observed wall-clock 8 PM session.
- Native factories/registration: 24 factions, 47 roster entries, 5 mounted entries resolved; no entities spawned. Physical faction-force delivery, rider/mount placement and entity-save recovery remain blocked and unverified.
- Legacy Forge signature/version-check/default-waypoint warnings were present; the verification checks and orderly saves/shutdowns passed.
- Runtime proof uncovered an avoidable repeated disk sync while a clock remains behind a saved boundary; changed the coordinator to save only the first identical block. The regression now asserts repeated blocked checks do not write. A final full gate follows this production correction.

Commands (PowerShell, isolated checkout):

```powershell
& ./tools/campaign-lifecycle/validate.ps1 -Label clean-full -GradleArgs @('clean','test','build')
& ./tools/campaign-lifecycle/validate.ps1 -Label forge-first -GradleArgs @('-I','tools/campaign-lifecycle/verify-forge.gradle','runServer','-PcampaignVerificationPhase=first')
& ./tools/campaign-lifecycle/validate.ps1 -Label forge-restart -GradleArgs @('-I','tools/campaign-lifecycle/verify-forge.gradle','runServer','-PcampaignVerificationPhase=restart')
```

The first Forge phase refuses to overwrite an existing disposable world. Preserve its evidence before a later clean. Local proof: `build/campaign-lifecycle/forge/campaign-first.txt` SHA-256 `53ae680750ed88f9f5d78aa7f6e043773029cec6302156b4548bb31d90904c19`; restart `e93c7c207032d970e120f8368fcaca53dcb79bf2164a6ea9231ae49872532572`. Logs: `outputs/campaign-lifecycle/forge-first.log` (`386b15fdf082703f95089245039051a8c07a4cd4dd27348a01defe3bb1bf26b7`), `forge-restart.log` (`fe5aaf66f17781720b05748bdb49082d4942fb3b5c1bbe32160608f3e6fe07f1`). These ignored local files are not deployment artifacts.

## Preservation check

All 20 other pre-existing worktrees have matching HEAD, normal porcelain status and tracked diff; stash identity matches. Inventoried ignored runtime directory timestamps match and javaw PID 44196 retains its original start time. Initial evidence covers directory metadata/process identity, not every world's bytes; no claim of a full world/config hash comparison. Two apparent status differences were caused by `--untracked-files=all` expanding directories; matching the original normal display resolved both. No other worktree/world/runtime/config was written. Local inventory/comparison JSON is under `outputs/campaign-lifecycle`.

## Offline ram authorization correction

`final-full` on 4bd22b2 passed: 2,074 tests, 2,072 passed, two platform symlink skips, zero failures/errors. `forge-final-restart` also passed. Further source review then identified a live-player lookup bypass for ram commanders who logged out. Ram impacts now authorize the persisted commander UUID. Two additional regressions cover missing/offline actors and invoke the actual private impact boundary on an offline commander's ram before any animation/damage/geometry access.

`offline-ram-guard` initially could not compile the actual ram test because the existing GeckoLib dependency was unavailable to the test source set. Added that same existing jar to testCompileOnly/testRuntimeOnly (no new library/version). `offline-ram-linked` then passed all 16 governance tests, zero failures/errors/skips. The final full gate below includes this correction.

## Final publication gate

`publication-full` on `95b62d7ad08aae2cb2133a683e981c0097dfba9f`: **2,076 tests, 2,074 passed, 2 skipped, zero failures/errors; test/build successful in 42 seconds**. The only skips are the platform-dependent symlink tests in `CustomSkinLibraryFoundationTest` and `ClientCustomSkinCacheTest`. Original LOTR jar compatibility inputs were supplied read-only, and their checks passed. The new regression count is 29; focused checkpoint counts overlap.

```powershell
$env:KOME_LOTR_VALIDATION_DIRECTORY = 'C:\Users\dayne\OneDrive\Desktop\The-Lord-of-the-Rings-main\LOTR-Test-Server\mods'
& ./tools/campaign-lifecycle/validate.ps1 -Label publication-full -GradleArgs @('test','build')
```

Final jar `build/libs/KOME-LOTR-Addon-1.0.8.jar` SHA-256: `32724e5274ebb92d47b378f2a22a5128c776f879ecc9261328a7b5ae6a85be78`. Local log/receipt: `outputs/campaign-lifecycle/publication-full.log` and `publication-gate.json`. No deployment occurred.

The final handoff commit changes only PROGRESS.md, VALIDATION.md and HANDOFF.md. Its published SHA, equality with the tested source/build/tool tree, final Gradle up-to-date verification and remote-head verification are recorded in [draft PR #25](https://github.com/daynenewman/KOME-LOTR-Addon/pull/25), avoiding a self-referential commit hash in this file. The final offline-ram correction has actual-impact regression/full-suite evidence; Forge was last restarted at `4bd22b2`, before that correction. Connected-client, multiplayer, hardware and real faction-force delivery remain unverified.
