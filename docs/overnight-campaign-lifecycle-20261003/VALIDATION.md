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
