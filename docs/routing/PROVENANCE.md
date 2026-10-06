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

| Audit finding or older description | Current checkout evidence/status |
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

## Unresolved or separate work

- Season reset/physical company-return work from other branches is not present merely because a phase/command exists here. Neither `KOMEDailyCoordinator` nor `KOMESeasonResetService` is in this production tree.
- The separate client progression-cache correction is absent: packet → proxy updates GUI/title summaries, while permission checks can read a different client progression projection. The map routes this symptom; it does not implement a fix.
- Explicit conflict-release and specialized arrival placement seams have no production consumer/provider installed; complete battle/siege result handling and automatic capture integration remain unproven/unbuilt boundaries.
- Development/payout timing across multiple owed boundaries uses sequential current-state loops. Historical per-boundary rate expectations require a rules decision; no invented resolution is recorded.
- README schema/current-system text and several dated documents disagree with current code. This task adds a routing entry link and qualified pointers; it does not re-author those historical documents.

Source wiring, JUnit, historical disposable Forge reports, current connected-client behavior, and live user acceptance are different evidence levels. No gameplay Java, resources, build configuration, tracker, world, or runtime was changed by this documentation task.
