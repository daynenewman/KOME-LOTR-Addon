# Routing provenance and status boundaries

[Index](INDEX.md) · [Validation](VERIFICATION.md)

## Starting audit

The reused package is **KOME system reference — 2 October 2026**, preserved locally at:

```text
C:/Users/dayne/.codex/worktrees/ead1/KOME-LOTR-Addon/docs/system-audit-20261002/
```

It is absent from this checkout's tracked documentation. The location is recorded as provenance, not a portable repository link; future use of this map does not require that worktree or the full audit. The package was read in place, not copied or edited.

| Audit input reused | Contribution |
|---|---|
| README.md and SYSTEM_GUIDE.md | Subsystem boundaries, authority versus projections, task entry points |
| sections/CAMPAIGN.md | Build/rate/bank/hire/movement/muster and gate flow boundaries |
| sections/INFRASTRUCTURE.md | Startup, persistence, queue ownership, Character Creation and Aqua |
| sections/INTEGRATED_MOBS.md | Formation/ram/pickup/native trade and entity authority boundaries |
| EVIDENCE.md | Pinned code/test provenance and verification limits |
| FEATURE_MATRIX.md and GAP_AND_DEPENDENCY_PLAN.md | Historical implementation/verification taxonomy and disconnected-path findings, rechecked against current callers |
| diagrams/08-work-dependencies.dot and diagram inventory | Cross-system dependency structure; nine architecture/state/economy/network/time/combat/module diagram families were located |

The audit also contains FEATURE_MATRIX.md, GAP_AND_DEPENDENCY_PLAN.md, subsystem matrices, inventories, testing findings, and an offline index.html. Its code baseline is **585e12a1c91cfa38ef8ed5d2da5e3580d1b8a238**, not the audit worktree's detached **fa6a43cc4876f68fd59f361fa6182b21ae26d974**. Audit test counts are historical and are not fresh validation of this routing branch.

## Checkout verified

On **2026-10-06**, this worktree began clean and detached at **b826ef35a0fb88ce9b2d58129ed307e9986b43d7** (PR #29 merge). The documentation branch starts at that exact commit. No remote refresh or live deployment inference was needed. Registration, authoritative mutations, persistence, consumers, and relevant test declarations were inspected against this checkout, with changed-source paths compared to the audit baseline. This was targeted routing verification, not a repeated full audit.

The following table preserves the original October 6 comparison. Its root-schema and pending-reset claims are superseded by the October 10 refresh below.

| Audit finding or older description | October 6 checkout evidence/status |
|---|---|
| Root schema 6 | [KOMEWorldData](../../src/main/java/kome/common/data/KOMEWorldData.java) uses 11, with independent mandatory conflict/tactical/Emergency Defense/movement sections. Historical nested/root numbers must be checked separately. |
| Progression branch excluded; cyclic fresh progression | Progression through Lordship and hardening now exists in [progression code/tests](systems/progression/README.md). Do not reuse the old blanket defect without tracing the current task. |
| Defeat PR #19 and diagnostics PR #20 excluded | Both are present: [campaign lifecycle](systems/campaign-lifecycle/README.md) and [runtime diagnostics](systems/runtime-state/README.md). Their dated validation is not fresh runtime evidence here. |
| Conflict and tactical controllers absent | Persistent conflict lifecycle, movement commitments/holds, tactical geometry/editors, and Emergency Defense are present. [Conflict guide](systems/conflict-tactical/README.md) distinguishes those slices from the remaining battle/siege/capture loop. |
| Movement speed settings ignored | [KOMEArmyCompany.getTilesPerDay](../../src/main/java/kome/common/data/KOMEArmyCompany.java) now reads configured entitlement; persisted company credit/guards/placement context exist. [Movement guide](systems/strategic-movement/README.md) describes remaining unwired seams. |
| Muster physical delivery pending | Still pending: production [KOMEMusterService.processDue](../../src/main/java/kome/common/data/KOMEMusterService.java) supplies `UNAVAILABLE`, not a deployed force. |
| Split/merge and war expiry disconnected | Still no production split/merge caller or automatic inactivity-expiry caller found; eligibility/inspection and service declarations are not workflows. |
| Foreign farmhand permission consumes legacy Stage 1 | The native hire event still calls [KOMEAllianceAuthority.canFactionHireAlliedFarmhand](../../src/main/java/kome/common/data/KOMEAllianceAuthority.java); [politics routing](systems/politics-diplomacy/README.md) records the unresolved canonical relation/entitlement mismatch. |
| Aqua crawl decoder/queue weaknesses | Negative ordinal guard and admission/drain bounds remain absent in [PacketSendKey](../../src/main/java/com/fuzs/aquaacrobatics/network/message/PacketSendKey.java). |
| Older tile capacity, native auto-company, four-stage alliance descriptions | Current authorities are faction bank/developed rate, explicit campaign classification, and native bilateral relations. Historical docs are linked with status notes, not silently rewritten. |

## October 10 current-dev refresh

The primary `dev` checkout was clean and fast-forwarded without pruning to PR #26 merge **9b765f5c8e8b8fb141d5a4b55c361377f109955f**, matching `origin/dev` with zero ahead/behind. PR #30 was checked in an isolated clone; existing worktrees, local branches, stash and both acceptance runtimes remain protected. This documentation-only integration incorporates that dev tree; it does not change gameplay source.

| Earlier route claim | Inspected current source and boundary |
|---|---|
| Root schema 11 | [KOMEWorldData](../../src/main/java/kome/common/data/KOMEWorldData.java) now uses root 12; [KOMEConflictPersistence](../../src/main/java/kome/common/data/KOMEConflictPersistence.java) independently uses ConflictData v2. |
| Daily/reset authorities absent | [KOMESeasonResetService](../../src/main/java/kome/common/data/KOMESeasonResetService.java), [KOMESeasonResetDeployment](../../src/main/java/kome/common/data/KOMESeasonResetDeployment.java), [KOMEDailyCoordinator](../../src/main/java/kome/common/data/KOMEDailyCoordinator.java) and durable journals are present. Reset preempts ordinary campaign processing. Daily population sequencing is available; owning movement/conflict/starvation/event adapters remain explicitly blocked when required. |
| Campaign cadence only follows the legacy loop | [KOMEEvents.processCampaignTick](../../src/main/java/kome/common/data/KOMEEvents.java) starts the payout session, routes active RESET, reconciles governance, then tries the coordinator before falling back to the existing movement/muster/development/payout loop. |
| No Join Battle route | [KOMEJoinBattleService](../../src/main/java/kome/common/data/KOMEJoinBattleService.java) owns eligible projection and selected-company revalidation, including existing governance military restrictions; [KOMEJoinBattleEntryService](../../src/main/java/kome/common/data/KOMEJoinBattleEntryService.java) owns physical entry/receipt recovery. Source wiring is separate from complete battle/siege outcomes. |
| New packets can reuse the older protocol identity | [KOMEPopulationWire](../../src/main/java/kome/common/network/KOMEPopulationWire.java) now advertises integration-g5; old peers missing Join Battle response registrations must be rejected before gameplay packets. |

[Published integration evidence](../pr26-integration-20261009/README.md), [disconnect correction](../pr26-joinbattle-fix-20261009/README.md), [focused governance acceptance](../pr26-governance-acceptance-20261010/README.md) and [occupied-capital repair](../pr26-occupied-capital-20261010/README.md) keep their exact source/artifact identities, failures and scope. Their JUnit/native/human passes are not fresh gameplay execution by this routing refresh. Multiplayer remains deferred and representative lower-spec performance remains pending.

The historical client progression-cache gap and unfinished muster/battle owning authorities are not silently declared fixed. The newer reset/daily/Join Battle routes were traced through their actual callers, persistence and projection. Other guides were checked for link/symbol/selector presence, not re-audited exhaustively.

## Unresolved or separate work

The separately authorized [KOM-59 T351/T352 correction](../kom59-ithilien-route-20261010/README.md) starts from clean dev **ff5053e56990dc601a1f0fe22d9e26e3affc1da0**. It changes one packaged river edge to open and removes that edge's river marker; geography and strategic movement guides record the new behavior. Historical separation inputs/golden, other route defaults, raster/IDs/coordinates, saved overrides and stored queued paths remain protected. Actual generated terrain and the reported server state remain unverified; this is not blanket geography or multiplayer acceptance.

- At the original October 6 baseline, daily/reset return authorities were absent. That absence is superseded by PR #26 and the October 10 refresh below; it is not a current-dev gap.
- The separate client progression-cache correction is absent: packet → proxy updates GUI/title summaries, while permission checks can read a different client progression projection. The map routes this symptom; it does not implement a fix.
- Explicit conflict-release and specialized arrival placement seams have no production consumer/provider installed; complete battle/siege result handling and automatic capture integration remain unproven/unbuilt boundaries.
- Development/payout timing across multiple owed boundaries uses sequential current-state loops. Historical per-boundary rate expectations require a rules decision; no invented resolution is recorded.
- README schema/current-system text and several dated documents disagree with current code. This task adds a routing entry link and qualified pointers; it does not re-author those historical documents.

Source wiring, JUnit, historical disposable Forge reports, current connected-client behavior, and live user acceptance are different evidence levels. No gameplay Java, resources, build configuration, tracker, world, or runtime was changed by this documentation task.
