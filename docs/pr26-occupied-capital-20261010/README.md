# Occupied-capital production acceptance — October 10, 2026

**PASS after a demonstrated persistence repair.** One non-operator authenticated client, `_Danye_`, on **127.0.0.1:51327**. [Exact results/configuration](validation.json), [human observation](human-observation.json), [native server](server-proof.txt), [native client](client-proof.txt), [archive/hash manifest](published-manifest.json).

## Candidate and actual failure

Source parent `4925e931f6909bf24a6cbdbd44290bfa51c1df67` incorporates dev `f154deaed18ac3f1a7770ffa3bda8a88382a9e35`. The two changed source files and their exact hashes are in [source-manifest.json](source-manifest.json); the containing publication commit pins the repaired source. Matching running production client/server JAR SHA-256 **`e20756089e48d0962b486ad14fa11407cb0782ad709931b296da5e9ed24dab53`**, protocol **1.0.9-integration-g5**. Profile **KOME PR26 Occupied Capital Repair**; Java 8u482, Forge 10.13.4.1614, LOTR 36.15; 25 identical common configurations, client/server heaps 1536/1280 MiB, view distance 2, online authentication and whitelist, empty ops.

Original g5 JAR `ec5181d81faa279a59fa228f3f830f6564e704ca631a533271b2dba3bac4c823` reproduced **`IllegalStateException: Physical locator attached to an ineligible hired-unit state`** during reset checkpoint serialization. The valid origin locator still addressed T348 after the production mounted return changed the unit/company tile to T388. This prevented a durable reset save. The valid baseline, exception, earlier fixture/observer failures and failing-before regression remain in [evidence.zip](evidence.zip) and [failure.json](failure.json).

`KOMESeasonResetDeployment.apply` now clears the stale address through the existing locator authority and checkpoints that invalidation **before physical relocation**, including receipt replay. Normal live observation can subsequently verify the new address. No schema, protocol, governance, HP or ownership rule changes. [Class comparison](class-reuse.json) verifies only `KOMESeasonResetDeployment.class` differs from g5; other packaged classes are identical.

## Validation

| Check | Observed result |
|---|---|
| Focused regression failed before repair | Expected durable locator invalidation before move; old code retained the locator |
| Five affected focused suites | **53 passed**, no skips/failures/errors |
| Required clean test/build | **3,085 discovered; 3,080 passed; five skipped; zero failures/errors** |
| Real production first return | C2 occupies capital reference X78080.5/Y200/Z66304.5, T388 |
| Real production second mounted return | C3 chooses clear X78078.5/Y200/Z66302.5; first survivor remains at reference |
| Identity and health | Original four UUIDs, original mounted attachments; C2 rider/mount 3.25/7.125 HP; C3 5.5/9.25 HP |
| Durable retry | Exactly two COMPANY_RETURN audits, tokens 1:C2 and 1:C3; retry adds no audit or move; native journal readback passes |
| Actual client reconnect | User replied **“yes!”** to exactly two mounted pairs, separate positions, no extras/disconnect |
| Post-reconnect native evidence | One entity per original server UUID; original HP/attachments; both tracked for authenticated player; client entity-ID correlation refreshed after reload, one pair each |
| Retained governance | CF1/C1/SUBMITTED and governance/war/conflict/capital/ownership/reset authority sections unchanged; prior [human governance PASS](../pr26-governance-acceptance-20261010/README.md) reused with original identity |

## Fixture and evidence limits

Two direct-API Campaign survivor fixtures use a **detached reset coordinator journal** with the production native deployment/Anvil save path. Its actual atomic `checkpointResetFile` persists `reset-journal.dat`; global governance/battle/capitals/ownership/reset state is retained. This is a production mounted-return/retry check, **not a new full global season-reset acceptance pass**. Original first company already returned before the second return. The repaired JVM cold-loaded the same saved survivors before staging; no new full canonical reset cold-restart gate is claimed. Original UUIDs and coordinates are in [fixture.properties](fixture.properties).

Validation-only `LivingHealEvent` cancellation for these four UUIDs prevents LOTR's ordinary passive rider/mount regeneration from hiding exact partial HP. No production class/config change implements that control; stock passive healing is outside this check. Earlier natural-healing and observer failures are retained and separated from the actual stale-locator product defect. Vanilla horse UUIDs are not sent in Minecraft 1.7 mob spawn: native server UUIDs correlate to client entities through fresh network IDs and mounted links. A stale-ID observer failure after reconnect was corrected by refreshing that read-only map; the human check was not repeated.

Original g5 runtime/profile/world are preserved after orderly save/stop; the repair uses copies. Frozen **51326** remains untouched: 89 hashes, original process start times, 25 worktree heads/statuses, local branch refs and stash match. [Preservation](preservation.json) explicitly records remote-tracking ref changes against the historical baseline; their refresh/prune provenance is unknown and none were rolled back.

**This focused check is complete. Multiplayer remains deferred.** No lower-spec or simultaneous-player acceptance, no broader ticket closure, dev merge or shared deployment. KOM-58/59 geography decisions and unfinished owning dependencies remain explicit. The runtime and fixtures remain ready on 51327; no further human retest is needed for this repair.
