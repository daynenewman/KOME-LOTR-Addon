# Campaign lifecycle integration progress

## Scope and authority

Execute the user-provided goal objective (2026-10-03): KOM-30, KOM-78, available KOM-48, KOM-82 and KOM-42. No merge, deployment, existing-world mutation, branch deletion, geography, full siege referee, progression redesign or active KOM-25/72/75 work. Work sequentially in this isolated checkout.

Rules: Draft 0.4 PDF, SHA-256 `da1c50af347992829e9f410f4e34e1a5e8770129bc56dda125dd98487ec87b84`, with [accepted addendum](https://linear.app/kome-development/document/draft-04-rules-alignment-and-accepted-decision-addendum-2026-10-03-688f1d1f6535). Read current selected Linear descriptions/comments plus KOM-40/47/24. Older reconnaissance is evidence, not policy.

## Checkpoints

1. Discovery in progress. Branch `dayne/campaign-lifecycle-20261003`, current origin/dev base `f3743f27b9a9d3979a8f99f40685bc2e46b48ed3`. GitHub query confirms only PR #24 open (KOM-28); its branch is untouched. No dependence on that unmerged code.
2. KOM-30: initial state/command/permission integration verified by 320 passing focused tests. First code checkpoint ready; further cross-system review/runtime evidence will follow.
3. KOM-78: real conflict projection and non-loading delivery preflight implemented; 54 focused tests passed. Physical delivery is concretely blocked by faction-owned troop/control/persistence support (see below).
4. KOM-48: available durable coordination; incomplete movement/starvation contracts must stay visible.
5. KOM-82: coverage matrix and available audit/inspection integrations.
6. KOM-42: authoritative cross-system regressions, full gate, disposable Forge, self-review and publication.

## Requirement to authority map

| Requirement | Existing authority | Planned integration / boundary |
|---|---|---|
| Defeat, war, season | KOMEFactionDefeatService, KOMEWarSeasonState, KOMEWarService/KOMEWar | Reuse published defeat; separate per-player/per-war governance, retaining native pledge and progression |
| Player identity and control | KOMEWorldData.getPlayerFactionKey, LOTR pledge, ruler/delegation/stewardship services | Central governance decisions at service and server entry points; no pledge-release cleanup |
| ALLY host validation | KOMEDiplomacyService and LOTR-backed adapter | Revalidate effective ALLY and active war membership; explicit denied reasons |
| Population and troop classification | KOMEPopulationService, KOMEHiredUnitClassification, recruitment/admission services | Preserve bank, purchased provenance, native identity and HP; no second charge |
| Capital/muster/deployment | KOMEFactionCapitalService, KOMEMusterService, KOMERecruitmentDeploymentService | Canonical conflict CLEAR/ENCIRCLED/UNKNOWN; inspect faction-owned control gap before spawning |
| Conflict | KOMEConflictService, lifecycle/movement adapters, persistence | Already delivered KOM-17; no second conflict engine |
| Daily execution | KOMEEvents.processCampaignTick, population payout runtime/processor, development, movement command services | Durable staged contracts; population-only offline catch-up; retain functioning runtime until all required authority is ready |
| Audit/admin/notification | KOMEAuditService, KOMEAdminDiagnostics/RepairService, conflict tools, KOMENotificationService | Reuse bounded audit and preview/apply; completion summaries only after durable completion |
| Persistence | KOMEWorldData schema 7, transactional candidate loader | Preserve conflict/muster/defeat data; reserve compatibility with separately owned KOM-28 journal |

## Preservation

Initial local evidence: `outputs/campaign-lifecycle/preservation-before.json` (ignored). Captures 21 worktrees, statuses/diffs, stash identity, runtime roots and observed Java process 44196. This checkout was clean and detached before creating the branch. First sandbox inventory hit Git safe-directory checks; rerun succeeded with per-command safe.directory, without global config edits. Fetch/branch metadata required sandbox escalation and succeeded. No other worktree was written.

## Open policies / required dependencies

- Encircled muster garrison versus relief and old-season pending reserves remain TBD.
- KOM-47 canonical daily allowance completion and KOM-24 starvation/scheduling remain unavailable; not all existing movement is unavailable.
- Future siege outcomes/repairs remain pending their owning authorities.
- PR #24 reset is unmerged; refetch before final integration and preserve its journal if it merges.
- Requested model is GPT-6 Astra / Extra High; no current-thread model setter is exposed by the available tools. Do not claim a model switch was performed.

## Governance implementation notes

New `KOMEPlayerGovernance`, `KOMEGovernanceService`, `KOMEGovernanceCombat` and `/governance status [warId] | submit <warId> | exile <warId> <host>` preserve pledge, progression, assets and population. State keys include player/season/war. Defeat defaults known players to civilian Submitted; explicit valid exile can follow. Submitted/exile transitions are idempotent and audited. Invalid exile relation falls back to Submitted; permissions deny immediately before reconciliation. Current implementation requires a distinct, undefeated ALLY host on the same war side. Per-war records survive native pledge changes and season reset without carrying restrictions into the next season.

Integrated server checks: conquest claim, campaign recruitment/admission, delegation/transfer, stewardship, movement departure/revalidation, canonical conflict commitment, combat damage (including native hires), gate operation/repair, ram impact and Finale. Native ordinary civilian gameplay remains on existing paths. Low-level conflict contracts remain caller-validated; future siege/player participation adapters still require governance integration.

Schema 9 holds governance; schema 8 remains reserved for unmerged KOM-28. Loader accepts existing 6/7, strictly requires governance on 9, and preserves existing authorities. Before merge with future reset code, incorporate schema 8 and its journal rather than overwriting it.

Physical muster blocker verified in production: `KOMEHiredUnitRecord.writeToNBT` dereferences owner/controller UUID; admission requires a player owner; movement validates owner equality and reconstructs native hired state. A null owner or ruler UUID is not a safe faction-force contract. Available work is canonical safety/preflight/pending integration; real faction-owned delivery, durable entity receipts and save reconciliation remain blocked by this contract, not by missing KOM-17.

## Next checkpoint plan (KOM-48)

Keep existing runtime for campaigns requiring unavailable movement/starvation/deployment authorities. Add a persisted, bounded stage journal and deterministic-clock coordinator for available services; only empty movement/conflict/starvation work is a provable no-op. Production preflight must visibly block the owning stage when those authorities are needed. Run the complete available pipeline only when all required stages are ready/inapplicable; legacy functioning runtime remains active otherwise. Development precedes payout; startup payout catch-up remains the exception and startup anchors skip offline non-payout days. Persist each actual stage with canonical WorldData; summary audit and an at-most-once notification claim commit before external chat. Never present blocked fallback execution as canonical daily completion. Add operator inspection and real-service/file checkpoint tests. PR #24 owns reset checkpoint code independently; do not cherry-pick or duplicate its reset service.

Current commit: governance `14d90b1767bbfac0865de6b9eb8bdb64938419bc` (320 passing focused tests). Muster checkpoint: 54 focused tests passed. Remaining required work: muster checkpoint; daily available coordinator; KOM-40 original requirement matrix and admin lifecycle inspection; integration tests; full clean gate; disposable Forge commands/save/restart; self-review; final dev refresh/integration; preservation verification; push/draft PR/Linear updates/final handoff.
