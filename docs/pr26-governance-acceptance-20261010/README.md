# Join Battle governance acceptance checkpoint — October 9, 2026

At the governance checkpoint, the **KOME PR26 Join Battle g5** client/server ran on **127.0.0.1:51327**. Both loaded production JARs were SHA256 **ec5181d81faa279a59fa228f3f830f6564e704ca631a533271b2dba3bac4c823**, protocol **1.0.9-integration-g5**, published source head **4925e931f6909bf24a6cbdbd44290bfa51c1df67**. No product source changes, rebuild or runtime replacement occurred during this governance check. The later occupied-capital defect repair uses a copied runtime; this report retains its original artifact and observations.

Authenticated `_Danye_` / `a976a4b7-0614-49c0-b992-61a3a89bd773` remains non-operator. The live server denies elevated command permission and `ops.json` is empty. The existing world is `pr26-integration-disposable`; the player remains at dimension 100, T388, X78017.5/Y200/Z66242.5. Heaps remain client 1536 MiB/server 1280 MiB; view distance 2, online mode/whitelist enabled. All 25 common configuration hashes still match. Server-properties bytes differ from the prelaunch manifest because Minecraft rewrote its timestamp/order at the original 14:41:36 startup; exact key/value comparison to the retained prelaunch file has zero differences.

## Staged fixture and observed checks

1. Saved canonical world authority and character records before setup. The live character's prior pledge was **Dale**, and there were zero wars, companies, hired records or T388 conflicts. Changed only this disposable native pledge to Gondor.
2. Created the Gondor/Mordor fixture war **war-00001** through `KOMEWarService.createWarResult`, entering season 1/WAR. Created active ordinary conflict **CF1**, revision 4, at T388 through existing conflict authorities; Gondor and Mordor participate.
3. Registered canonical Campaign company **C1**, owned by fixture UUID `f107f077-470a-49ee-93c2-692b51fbcfb1`, with one offensive Campaign record `1f52a2fa-07d0-49d7-82d4-c3b8755ec36d`, strategic tile T388 and population cache 20. Committed this explicit fixture through the conflict data authority. This registered member is synthetic, with **UNKNOWN_PHYSICAL_STATE**, which current eligibility permits. No physical NPC, population debit, validated legal arrival or actual entry is claimed. The fixture owner is not a second authenticated account.
4. **Native baseline PASS:** `KOMEJoinBattleService.evaluate(data, livePlayer, T388)` and `validateSelectedCompany(..., C1)` both return **ALLOWED**. The production packet factory issues a fresh player-scoped action token. The baseline checkpoint predates any defeat or submitted restriction.
5. Published explicit Gondor defeat through canonical `KOMEWorldData.publishFactionDefeat`, with a governance-fixture audit. This is setup injection, not acceptance of the capital-capture/full defeat predicate. Existing defeat retention automatically creates **SUBMITTED revision 1** for known Gondor members. Called **`KOMEGovernanceService.choose(..., SUBMITTED, "", ...)`**, which returns allowed as an idempotent confirmation of that retained state. No direct governance-map injection occurred.
6. **Native restriction PASS:** both live-player eligibility and selected-company revalidation return **GOVERNANCE_RESTRICTED**; the packet issues no action token and carries exactly **“Your current war governance status prevents Join Battle participation.”** Company/member NBT and immutable conflict authority are unchanged by the restriction.
7. **Actual connected-client automation PASS:** the existing authenticated client sends `/kome joinbattle T388`, receives the response, initializes/renders T388's panel with that exact reason, disables Join, and remains connected. The actual Refresh button request/response repeats that result. The observer closes the panel to the world without joining. This is native automated evidence, separate from human acceptance.
8. `save-all` logs **Saved the world at 15:15:35** local. Parsed persisted `KOME_ServerRules.dat` matches the restricted checkpoint for companies, hired units, conflicts, governance and war season. No cold restart was performed for this fixture.

The probe failures remain in the archive and server log: initial covariant-method reflection ambiguity; a package-private classification lookup; and a rejected noncanonical fixture company ID. The partial fixture was corrected with the existing canonical ID allocator and retained world. These were validation-tool/setup failures, not product defects. The initial V2 compilation also failed because its output directory was missing; no agent ran from that attempt.

## One human check — PASS

In the existing g5 client on 51327, press T and enter:

```text
/kome joinbattle T388
```

Expected: T388's panel says **“Your current war governance status prevents Join Battle participation.”**, **Select / Join Battle** is disabled, and the client stays connected. **PASS on October 10:** the [user-supplied screenshot](human-governance-20261010.png) shows T388, CF1 r4 ORDINARY, Gondor, BLOCKED, that exact reason, and disabled Select / Join Battle. A subsequent [read-only current-session check](screenshot-session-proof-20261010.txt) independently verifies the actual loaded g5 JAR, authenticated identity, same panel and continued connection. The server separately confirms non-operator permission and retained restriction. [Human observation](human-observation.json) records image evidence separately from native connection proof; no verbal yes/no reply was invented.

## Evidence and closeout delta

[Exact validation/configuration/checkpoint hashes](validation.json), [server native proof](server-proof.txt), [client command/GUI proof](client-production-proof.txt), [archived probes, snapshots and logs](evidence.zip), [preservation check](preservation.json).

| Requirement | Observed result | Remaining |
|---|---|---|
| Otherwise eligible baseline before restriction | Native production authority PASS, including selected-company revalidation and token issuance | Physical legal arrival/entry remains outside this governance fixture |
| Defeated/submitted Join Battle permission | Native production authority and real one-account command/packet/UI/Refresh PASS; human screenshot PASS for exact denial and disabled Join; read-only current connection PASS | This focused check is complete; physical entry and multiplayer remain outside its scope |
| Non-operator status | Native elevated permission denied; empty ops | No promotion required |
| Frozen runtime and protected work | 25 original worktrees, user refs/stash, 89 frozen file hashes and original frozen controller/server start times unchanged | Port 51326 remains untouched |
| Multiplayer | Deferred | No second-account setup or multiplayer pass |

This published checkpoint preserves the original g5 artifact identity. Prior human failures/passes and earlier pending/native records remain intact. The later [occupied-capital repair](../pr26-occupied-capital-20261010/README.md) retains the governance fixture and byte-identical governance/Join Battle classes; it does not require a repeated human governance check. No broader ticket closure is inferred.

## October 10 checkpoint refresh

[Refreshed checkpoint](resume-20261010.json) reuses the completed October 9 baseline and governance checks. Same server/controller process IDs and 51327 listener remain; both candidate JAR hashes and all 25 common configurations match. Hired units, conflicts, governance and war season match the restricted checkpoint. The sole company change is automatic movement allowance initialization to one credit at `America/Chicago@20:00`; all other company fields and governance eligibility inputs match. No fixture recreation or product rebuild occurred. [Refreshed preservation](preservation-20261010.json) again passes for 25 worktrees, refs/stash, 89 frozen hashes and original 51326 process start times.

At the initial checkpoint refresh, the old client had exited overnight. Reopening the same g5 profile was requested through Prism's [documented instance/account/server launch options](https://prismlauncher.org/wiki/getting-started/command-line-interface/); no new client process was observed at that time. The existing g5 profile subsequently launched as client PID **45260**, authenticated as `_Danye_` to 51327 at **11:43:22 America/Chicago**, and produced the user's screenshot. The read-only native observation at **11:46:51** confirms the matching production JAR, connection, panel and non-operator server state. Disconnect repair and this scoped governance check are now complete. [Exact human closeout and archive hashes](human-closeout-20261010.json) retain the earlier pending record and native archive. Multiplayer remains deferred.
