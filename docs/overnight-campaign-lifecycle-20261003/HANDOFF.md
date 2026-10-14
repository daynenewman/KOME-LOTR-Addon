# Campaign lifecycle handoff

Branch `dayne/campaign-lifecycle-20261003`; base/current dev `f3743f27b9a9d3979a8f99f40685bc2e46b48ed3`. [Draft PR #25](https://github.com/daynenewman/KOME-LOTR-Addon/pull/25) targets `dev`. Full-suite implementation HEAD: `95b62d7ad08aae2cb2133a683e981c0097dfba9f`; the final handoff commit changes only these three documents. The PR records its published SHA and final verification. No merge or deployment.

| Issue | Delivered in this branch | Pending |
|---|---|---|
| KOM-30 | Player/season/war governance, origin capture on defeat, self-service commands, server gameplay checks, invalid-host fallback, audit/inspection; assets and native identity preserved | Connected-client acceptance; future siege/player participation adapters must use the same authority |
| KOM-78 | Canonical CLEAR/ENCIRCLED/UNKNOWN projection; non-loading capital/snapshot/dimension/chunk preflight; pending reasons and uncertainty latch | Player owner/controller is required by unit persistence/control. Faction-owned force, entity-save receipt and reconciliation contracts are absent. Real delivery remains blocked. Encircled placement and old-season reserves remain undecided |
| KOM-48 | Bounded persisted stage journal, deterministic clock, atomic data checkpoints, real development before payout, dependency blocks, fallback scheduler, durable summary claim | Full movement KOM-47, starvation/scheduling KOM-24, active conflict referee and physical muster. Complete available batches run only where those stages are provably inapplicable |
| KOM-82 | Coverage matrix below; new lifecycle audit and bounded diagnostics; existing guarded repairs reused | Future siege/episode/sortie controllers and reset acceptance; Discord transport optional; no guessed physical repair |
| KOM-42 | Authoritative services, NBT and atomic-file tests for transitions, failures, clocks, restart, payout-only catch-up and asset preservation; full and disposable Forge gates passed | Connected-client/multiplayer acceptance; unimplemented siege and delivered muster outcomes cannot be certified |

## Original KOM-40 requirement coverage

Source coverage is not blanket runtime acceptance. Existing hooks are credited, not recreated. Test names below identify repository evidence; actual branch runs are in [VALIDATION.md](VALIDATION.md).

| Requirement | Authority, event and operational surface | Status / evidence / limit |
|---|---|---|
| Actionable blocked-action reasons | Domain Result/Decision; `/governance`; `/kome diagnostics` | Available boundaries implemented; governance/muster/admin tests. Future systems remain explicit unavailable authority |
| Build approval audit | `KOMEBuildService`, BUILD; `/build inspect`, Tile Command review | Existing; Build service/transaction tests |
| Population rate changes | `KOMEPopulationDevelopmentService`, POPULATION_DEVELOPMENT; payout prepared audit | Existing; real Build -> development -> payout tests and new daily file tests |
| Unit hires | `KOMECampaignRecruitmentService`, `KOMEEvents`, UNIT/HIRE | Existing; recruitment/permanent-spend tests; governance rejects before deployment/debit |
| Delegation | `KOMEWorldData` company audit; company diplomacy authorization; `/troops` | Existing; new Submitted/exile and saved transfer checks; delegation/transfer tests |
| Movement | WorldData movement audit/history and conflict ROUTE_HOLD | Existing transitions retained; full daily allowance/referee contract belongs to KOM-47 |
| Conflict transitions | `KOMEConflictService` COMMIT/DEPART/FORCED_END; lifecycle REPAIR; immutable ID/revision guards | Delivered KOM-17 credited; conflict service/lifecycle/movement/persistence tests |
| Gate recalculation/relink audit | `KOMEDefensiveGateLinkService` LINK/UNLINK/REFRESH/RELINK/CONFIRM_DIMENSIONS; `GateManagementManager` physical commit | Existing initial/relink health boundaries; gate link/health tests. Active-siege recalculation rules remain KOM-26 |
| Muster rolls/arrival | `KOMEMusterService` CALL/PENDING/DELIVERY_ATTEMPT/ARRIVE; `/muster` | Existing deterministic roll plus new real preflight/pending reasons. ARRIVE is receipt-gated; production delivery unavailable. Simulated tests are not spawned troops |
| Ownership changes | `KOMEWarService` TILE/HOSTILE_CAPTURE; WorldData OWNERSHIP_CHANGE | Existing, with governance at claim boundary; conquest/war tests |
| Combat episode starts | `KOMEConflictService.checkpointCombat` stores episode/timer checkpoints | Data contract exists; no production episode controller/publisher found. Immediate audit/announcement integration pending KOM-18/26; no fabricated outcomes |
| Finale trigger | `KOMECommandSeason`, SEASON/FINALE and `timeSensitive` | Existing immediate notification; new governance gate; season tests |
| Faction defeat | `KOMEFactionDefeatService`, CAMPAIGN/FACTION_DEFEAT | KOM-29 credited; governance origin captured in same in-memory rollback boundary; defeat/governance persistence tests |
| Reset returns | PR #24 / KOM-28 owns reset journal and return durability | Still open at final dev refresh. Its branch/service were not copied or modified; integration prerequisite if it merges first |
| Population rate inspection/repair | `/kome diagnostics population <faction>`; existing Build/development authority | Existing derived aggregation; no fake stored rate repair |
| Ruler inspection/repair | `KOMERulerService`, `/kome ruler`, guarded online-name repair | Existing; identity/permission/stale-preview tests |
| Capital records | `KOMEFactionCapitalService`; `/kome diagnostics capital`; explicit validated relocation | Existing non-loading diagnostics and unloaded-dimension command regression |
| Diplomacy inspection/repair | `KOMEDiplomacyService`, `/alliance`; obsolete pending request repair | Existing LOTR relation authority and stale-state tests |
| Gate relinking inspection/repair | Gate management UI/service; new `/kome diagnostics gate <build>` | Metadata only, physical state explicitly UNINSPECTED. Existing relink validates actual loaded controller/identity/health acceptance; no replacement invented |
| Company location/state | `/troops`, stewardship reconciliation; new `/kome diagnostics company <id>` | Available metadata inspection. Missing entities are not death; forced location repair blocked by movement/deployment/reconciliation contracts |
| Ownership repair | `KOMEAdminRepairService` snapshot + claim missing-alias repair | Existing actor/world-bound, expiring, single-use preview tokens; stale/ambiguous state rejected |
| Corrupted conflict repair | `KOMEConflictLifecycleService`; `/kome conflict inspect/end`, `/kome repair conflict ... preview/apply` | Existing deterministic repairs; unknown/unloaded references preserved. Future siege outcome repair not inferred |
| Same authorities as gameplay | New diagnostics read ordinary records/services; existing repairs reused | Root permission denial precedes world access; actual command regressions; no parallel repair engine |
| Bounded structured audit | `KOMEAuditService.MAX_ENTRIES=500`, persisted CentralAudit; paged `/kome audit` | Existing bounds; new events use them. Diagnostics <=20 lines and <=512 characters each; audit/admin tests |
| Daily summary after persistence | `KOMEDailyCoordinator` completion stage + `dailySummary` | Available-only completion and notification claim saved before send. Blocked fallback never claims completion. Crash after claim can omit chat, never replay claim |
| Immediate episodes/sorties/milestones | `KOMENotificationService.timeSensitive` | Finale path exists. Episode and sortie controllers remain unavailable; future hooks must run immediately from those authorities |
| Optional one-way Discord | Failure-isolated `KOMENotificationService.Sink` | Existing optional boundary retained; concrete transport/config absent, no gameplay dependency |

## Persistence and restart limits

- Schema 9 accepts roots 6/7 and strictly validates governance/daily sections. Schema 8 is reserved for unmerged KOM-28; preserve its journal/schema when integrating if merged.
- Daily effects and receipts share the canonical compressed file. Synced temporary bytes are atomically replaced; no non-atomic fallback. This does not certify entity/chunk saves or filesystem power-loss behavior.
- Startup keeps payout catch-up and skips offline development. Unfinished pre-population work is audited as interrupted rather than replayed. Same-boundary restart can finish remaining inapplicable stages after already persisted population receipts. Expired batches are not announced complete.
- Exile selection requires an undefeated canonical ALLY; selection does not create war membership. Participation requires ordinary host war-side authorization. An exiled owner cannot launder native troops into host authorization.
- Muster snapshots/uncertainty remain preserved; the current player-owned unit model cannot truthfully produce a faction-force entity-save receipt.

## First useful manual checks

Use a disposable server/client pair after review, never a preserved existing world.

1. As operator: `/kome diagnostics daily status`, `/kome diagnostics muster gondor`, `/kome diagnostics governance <uuid>`, `/kome audit list 1`. Confirm unavailable authority and non-loading inspection.
2. After a canonical test-war defeat: `/governance submit <warId>`. Verify civilian Build/trade/quest/travel remain usable and military claim/control/combat/gate actions are rejected.
3. `/governance exile <warId> <alliedHost>` with actual ALLY and host war membership. Verify host participation/delegation, preserved pledge/rank/HP/bank, and immediate denial plus persisted Submitted fallback when ALLY is removed.
4. At an available live daily boundary, inspect saved completion/one summary and repeat/restart. Add a campaign company: expect a KOM-47 block while existing movement/population processing continues.
5. Leave physical muster acceptance pending faction-force implementation; pending reserves/native factory probes/unit tests do not prove delivery.

## Final evidence

- Final full `test build` on `95b62d7`: **2,076 tests, 2,074 passed, 2 platform symlink skips, zero failures/errors**. An earlier clean full gate passed; later production corrections were followed by full gates. Focused counts and exact commands are in [VALIDATION.md](VALIDATION.md).
- Disposable Forge actual command dispatch, defeat/governance, controlled-clock daily processing, canonical save and cold restart passed. The last Forge restart used `4bd22b2`; the later offline-ram correction was verified at its actual impact method by regression and the final full gate. No connected client or physical muster deployment was tested.
- Jar SHA-256: `32724e5274ebb92d47b378f2a22a5128c776f879ecc9261328a7b5ae6a85be78`. This build was not deployed.
- All 20 other worktrees retained HEAD/status/tracked diffs; stash identity, inventoried runtime-directory metadata and original javaw PID/start time matched. Evidence is metadata/process based, not a full world/config byte comparison. No preserved world/runtime/config was written.
- PR #25 is a draft. Evidence comments were posted to KOM-30, KOM-78, KOM-48, KOM-82 and KOM-42 without changing assignees or claiming full issue acceptance. Remaining implementation paths and policy decisions are explicitly listed above; none are disguised as completed integration.
