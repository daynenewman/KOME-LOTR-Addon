# PR #26 integration evidence — October 9, 2026

This report supports the single [closeout checklist](../pr-review-20261007/README.md#closeout-checklist). PR #26 remains draft; no dev merge or deployment. Multiplayer is deferred.

This is the initial integrated `263e8ff...` artifact report. The subsequent human Join Battle disconnect exposed an old-g4/matching-protocol mismatch; [the correction report](../pr26-joinbattle-fix-20261009/README.md) preserves that failure and identifies the current g5 production JAR and connected native checks. Previous scoped passes below retain their exact artifact identities.

## Exact candidate

- PR parent: `4f73b345ff60a0ff78bae9e34470299cd4383642` (published human/R1 evidence).
- Integrated dev parent: `f154deaed18ac3f1a7770ffa3bda8a88382a9e35` (Join Battle/formal retreat).
- Pre-documentation source tree: `954e08cba64b9616a127bcd40e180a9c657c0bd7`.
- Production JAR SHA-256: `263e8ff420b0e84c81f9b802a58b7d46182dee4158bfa41c60afc213b9e43eb0`.
- [Exact source/resource hashes](source-manifest.json), [production class hashes](production-class-manifest.json), [validation manifest](validation.json) and [raw evidence archive](validation.zip).

The isolated private checkout is `build/pr26-integration-20261009/source`. It borrows existing objects read-only and fetches into its own Git directory. It neither switches nor resets any existing worktree.

## Integration decisions and actual defects

Four conflicts were resolved by inspecting both authorities: company mutation retains both RESET and formal-retreat holds; standing validation retains dev's block-only collision query, liquid rejection and explicit entity-volume rejection; the richer live-world test fixture remains; root schema **12** retains reset/governance/daily authority while ConflictData **v2** stores Join Battle receipts/sequence. Legacy ConflictData v1 migrates with no invented deployment receipts, and future root versions still fail closed.

The merged resolver exposed a reproducible reset defect: an already returned survivor occupied the capital reference and blocked retries/other companies. Only reset's search-reference preflight now permits entity occupancy. Every actual landing still checks support, blocks, liquids, tile identity and live entity volumes. Ordinary capital/Join Battle reference validation remains strict. Regression verifies partial return, no relocation/respawn of the first survivor, no overlapping final boxes and unchanged HP/receipts.

A second reproduced defect let a defeated/submitted player use another owner's committed company through Join Battle. The shared read-only projection/entry/retry selection now calls the existing war-scoped `militaryAction` authority. A previously issued token cannot bypass a later governance restriction. The new reason is appended to preserve existing wire ordinals. Native pledge/assets and exile rules are unchanged.

## Fresh validation

| Gate | Observed result | Limits |
|---|---|---|
| Affected Join Battle/reset/persistence/governance/daily/movement/formal-retreat/abandonment/physical-location suites | **333 passed**, no skips/failures/errors | JUnit fixtures; no live player |
| `clean test build --offline --no-daemon --console=plain --max-workers=2` | **3,083 discovered; 3,078 passed; five skipped; zero failures/errors** | Three externally configured LOTR-JAR checks and two Windows symlink checks skipped; exact names in manifest |
| Reset first/recover/completed, origin-first and destination-first | **Six fresh Forge JVM gates passed** | Native NPC/horse UUIDs, Anvil saves, interruption/retry, 3.25/7.125 HP, one receipt per company and one reset completion; no connected client |
| Governance/daily first + cold restart | **Two fresh Forge JVM gates passed** | Real command dispatch/canonical save, synthetic sender; permissions, development-before-payout and once-only effects; no synchronized human UI |
| Pending Join receipt beside reset + governance + interrupted daily journal | Compressed JUnit checkpoint and all six native reset phases passed | Synthetic offline player/receipt; native unspawned vanilla-horse NBT preserves UUID/7.125 HP; no physical Join entry/reconnect pass |
| Actual production JAR loader | **PASS** native occupied/free/block/liquid controls, safe mounted-size search, root 12/ConflictData 2 and initialized whole-root NBT round trip | One separate disposable server; no connected client or production cold-restart claim |

Gradle runs on Java 17.0.19, compiles legacy Java 8 targets, uses a 1 GiB daemon heap and two workers. Deobfuscated Forge is 10.13.4.1614 / Java 8u492, LOTR 36.15 and GeckoLib 1.0.4; disposable server properties use port 0, offline mode and view distance 2. Production linkage uses Java 8u482, a 1,280 MiB heap and loopback port 0. All exact commands, native proofs, fixture snapshots, canonical checkpoint files and logs are archived. The existing acceptance controller already owns `Local\KOME-Heavy-Validation`; verified controller/server IDs 43016/30672 remain untouched. Associated jobs serialize through the same task's child mutex; no fresh global-lock acquisition is claimed.

## Preserved failures

These are not silently overwritten by a green gate:

- Initial combined tests exposed stale schema expectations and fixture omissions for the newer collision queries; subsequent gates found an uninitialized test owner. Their XML/logs remain.
- The occupied-capital retry regression and both governance-bypass regressions failed before their product fixes.
- Initial native completed reset failed to find C2 because the validator loaded only the reference chunk. C2 was actually persisted at `78014.5,200,66238.5`, chunk `4875,4139`; C1 is at `78016.5,200,66240.5`, chunk `4876,4140`. Loading saved neighboring chunks fixes the validator, with no product change. The original failed world/proof stays preserved; the final six gates use fresh worlds.
- Two production probe attempts wrongly compared a wholly uninitialized root before/after read. The native diagnostic isolated the differences to `ConquestTiles`, `TileWaypoints` and `ConquestDefaultsInitialized`: normal default initialization. The final probe uses the normal live integrated initializer before serialization, retaining strict equality. Both failures remain archived.

There were **14 Forge attempts total**: 11 complete passing attempts and three fixture/probe failures. The accepted final gate is eight deobfuscated launches plus one production linkage launch; earlier partial successes do not inflate it. Legacy Forge signature/version-check warnings appear in logs and are not acceptance failures; explicit proof guards determine each gate.

## Preservation and remaining acceptance

All **25** existing worktree heads/statuses, user refs and stash are unchanged. **89** frozen runtime/profile/config/mod/fixture hashes match, with the same controller/server processes and start times. Background world saves may continue; this is not a byte-freeze claim for a running world.

The frozen client/server remains JAR `9fc21f5b469d8d31e4ac54c039aaf8a5b9948a429c061c3a271b7681a544f9ef`, profile **KOME Mounted Reload Fix**, server `127.0.0.1:51326`. Human R1 HUD/map, Build, zoom/scale/resize, respawn/reconnect, mounted cold reload and dimension passes keep their original artifact identities. Their observations were not repeated or promoted to the new JAR. Geometry/resolver/Build/options sources identified in the manifest remain unchanged; broader changed event/recovery/Join Battle paths require their own acceptance.

Next smallest single-player batch on a separate integrated client/server: enter `/kome joinbattle T388` once in a disposable world with no active conflict. Expect the new Join Battle panel to open, report no active battle and keep deployment disabled, without crash/disconnect. After that observation, stage the existing authoritative governance fixture for a submitted-player denial; actual mounted return/reconnect placement needs a scoped new-artifact check. Do not ask the user to execute this on the frozen old artifact.

Multiplayer and representative lower-spec measurements remain pending. KOM-58/59 cell/classification decisions stay explicit and unknown gaps remain unknown. KOM-48 and other unfinished owning authorities remain separate dependencies. Optional world borders and a tile editor remain deferred.
