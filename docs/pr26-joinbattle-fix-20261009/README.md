# PR26 Join Battle disconnect correction - October 9, 2026

The human-reported `/kome joinbattle T388` fatal disconnect is recorded **FAIL**, with its original logs and actual artifact identities. This correction remains on draft PR #26; no dev merge or shared deployment. Multiplayer remains deferred.

## Demonstrated cause and trace

At **14:26:54**, the first relevant client exception was `io.netty.handler.codec.DecoderException`, caused by `java.lang.NullPointerException: Undefined message for discriminator 54 in channel kome`. [Exact exception](first-client-exception.log), [server connection/disconnect context](server-failure-context.log), [identity and hashes](failure.json).

The reported profile name was **KOME PR26 Integrated f154**, but the actual connecting client's logs came from `kome-tile-acceptance-pr26-20261006`, whose production JAR is `9b267eb7c19f5cdd5f60a64c6db4927fb991bd640aaa27ab40e6726517c4ef61`. It connected to 51326 first, then 51327 at 14:26:50. The intended integrated profile had no launch logs. The server and staged integrated client both had `263e8ff420b0e84c81f9b802a58b7d46182dee4158bfa41c60afc213b9e43eb0`. The failed JVMs are not retroactively claimed to have passed agent code-source verification: client log source/current immutable JAR and server startup/configured JAR establish this recorded mismatch.

The implementation allowed that mismatch: `KOMEAddon.acceptsRemoteKome` delegates to `KOMEPopulationWire.accepts`, which still advertised **1.0.9-integration-g4** after Join Battle/formal retreat added IDs **53-58**. The old g4 client therefore passed Forge's version check but had no registration for response **54**.

Trace: public `KOMECommandKome.processCommand` -> `KOMEJoinBattleService.evaluate` -> `KOMEPacketJoinBattleViewResponse.forPlayer` -> `KOMEPacketHandler.network.sendTo` / discriminator 54 -> Forge indexed codec. The old client fails there, **before** `KOMEPacketJoinBattleViewResponse.Handler`, the client task queue, `KOMEClientProxy.displayJoinBattleGui` or `KOMEGuiJoinBattle`. The server log records an ordinary disconnect. Its unrelated LOTR player-details DNS retries did not cause this decoder failure.

## Narrow repair and evidence

Only the protocol identity changes in product source: **1.0.9-integration-g5**. The existing exact-version Forge callback now rejects old g4 peers in either direction before gameplay packets arrive. Packet IDs and payloads, permissions, Join Battle authorities and persistence are unchanged. A focused regression fails on the old source and passes after the correction; existing version expectations advance to g5.

Source parent: `1dbd46cd60b4a341c23bc8b33ddddf43915b757b`, incorporating dev `f154deaed18ac3f1a7770ffa3bda8a88382a9e35`. Corrected production JAR SHA-256: **`ec5181d81faa279a59fa228f3f830f6564e704ca631a533271b2dba3bac4c823`**. Only `kome/common/KOMEAddon.class` (inlined mod version) and `kome/common/network/KOMEPopulationWire.class` differ from the prior production class manifest. [Exact validation/configuration manifest](validation.json), [logs, XML, native probes and proofs](validation.zip).

| Check | Observed result | Evidence level |
|---|---|---|
| g4 mismatch regression before repair | 1 test, 1 failure: old g4 accepted | JUnit, retained failed-before XML/log |
| Affected protocol/packet/public command/Join UI suites | **66 passed**, zero failures/errors/skips | JUnit |
| Required `clean test build --offline --no-daemon --console=plain --max-workers=2` | **3,084 discovered, 3,079 passed, five skipped, zero failures/errors** | Clean build/JUnit; same three external-LOTR and two Windows-symlink skips |
| Running production client and server | **Both loaded code-source JARs hash to ec5181d...** | Native Java 8u482 / Forge 10.13.4.1614 / LOTR 36.15 |
| Actual loaded handshake callback | g4 rejected, g5 accepted for Side.CLIENT and Side.SERVER | Native production callback; no second mismatched-client connection is claimed |
| Authenticated `/kome joinbattle T388` | Panel T388, `NO_ACTIVE_CONFLICT`, exact no-active-battle text, disabled Join, still connected | Automated real one-account command -> network -> handler -> initialized/rendered GUI |
| GUI Refresh -> request -> response | Same panel/reason/disabled Join, stays connected; Close returns to world | Automated actual production GUI action and network round trip |
| Permissions | `_Danye_` UUID `a976a4b7-0614-49c0-b992-61a3a89bd773` remains non-operator, elevated command permission denied, ops.json empty | Native server inspection and real public command |
| Frozen runtime | **89/89** original runtime/profile/config/fixture hashes unchanged | Preservation snapshot; 51326 controller/server untouched |

The production probes initially failed twice: the first treated Forge's `jar:file:` code-source URL as a plain filesystem URI; the second incorrectly treated vanilla player level-zero permission as KOME's public-command authority. Fresh probe classes correct those harness assumptions without product changes. Both failures/logs and probe versions remain archived. The final native proofs are explicit PASS; a clean server exit or an attach exit alone is not used as proof.

## Ready retest and limits

The new isolated profile is **KOME PR26 Join Battle g5**, instance ID `kome-pr26-joinbattle-g5-20261009`, connected to **127.0.0.1:51327**. It was launched by exact instance ID and verified in the running JVM. Its matching server uses a copy of the failed integrated disposable world after orderly stop (exit 0), retaining T388 fixtures and the same authenticated non-operator. Both prior worlds/profiles remain preserved. Twenty-five common configuration files match; heaps are client 1,536 MiB/server 1,280 MiB, view distance 2, online mode and whitelist enabled. Exact hashes/settings are in the manifest.

**Human retest PASS:** in the verified matching g5 client on 51327, press T and enter `/kome joinbattle T388`. Asked whether T388's Join Battle panel opened, “There is no active battle on this tile” appeared, Select / Join Battle was disabled and the client stayed connected, the user replied **“yes”**. This records all four expectations for one non-operator, one tile and no active conflict. It does not certify live battle deployment, governance changes, multiplayer or wider UI/layout acceptance.

The frozen **KOME Mounted Reload Fix** client/server on **51326** remains JAR `9fc21f5b469d8d31e4ac54c039aaf8a5b9948a429c061c3a271b7681a544f9ef`. Prior human/R1 evidence keeps its original identity. Reset, HP, governance/daily journals and persistence/recovery sources are unchanged by this repair; their existing source-specific restart proofs remain valid within their recorded scope. No new g5 persistence cold-restart pass is inferred. This report supports the one [closeout checklist](../pr-review-20261007/README.md#closeout-checklist).

## Later scoped acceptance

[Submitted-governance restriction](../pr26-governance-acceptance-20261010/README.md) passed native baseline/authority and human screenshot on this exact g5 artifact. The subsequent [occupied-capital repair](../pr26-occupied-capital-20261010/README.md) changes only reset deployment, with governance/Join Battle/network classes byte-identical. Its production return/retry and human reconnect pass separately. These checks are complete; actual physical battle entry and multiplayer remain separate.
