# KOM-40 scoped audit/repair foundation

Original base: `fa6a43cc4876f68fd59f361fa6182b21ae26d974` (dev). Integrated latest dev
`a8ee2665d69ed5eb51df1edf047c8395b37080a9` by merge. This is a partial foundation for
[KOM-40](https://linear.app/kome-development/issue/KOM-40/add-centralized-audit-logging-reason-strings-and-admin-repair-tools), not completion of the issue.

## Operator commands

All commands below require permission level 2 for `kome`. The root rejects unauthorized
callers before requesting world data. Inspection does not create banks, normalize owners,
reconcile gameplay state, load chunks or write world data.

```
/kome diagnostics population gondor
/kome diagnostics ruler gondor
/kome diagnostics capital gondor
/kome diagnostics diplomacy gondor rohan
/kome diagnostics ownership T100
/kome diagnostics waypoint <approved-waypoint-UUID>
/kome audit list [page]
/kome audit summary [page]
```

Diagnostics return at most 20 lines of 512 characters. Population displays the canonical
bank, the exact derived rate units, payout failure and a limited contribution sample with
an omitted-row count. Calculating an exact rate still traverses authoritative Builds;
this bounds output, not the work needed for a correct aggregate. Audit pages use one
header plus at most 18 rows, newest first for `list`, over the existing 500-entry stream.
Invalid pages (including overflow-sized integers) are rejected. No alternate history or
notification service is introduced.

## Preview and guarded application

```
/kome repair preview ownership T100
/kome repair preview diplomacy gondor rohan
/kome repair preview ruler gondor <online-player-name>
/kome repair preview waypoint <approved-waypoint-UUID>
/kome repair apply <token-from-preview>
```

Preview describes the proposed change and creates an ephemeral token without changing
domain state. `/kome ruler repair <faction> <player>` now uses the same preview workflow;
the no-player form remains inspection only. Plans are bound to the creating actor and
world object, expire after 120 seconds, and have a global capacity of 64 per command
instance. Restart discards them. The command rechecks permission at application, and the
service rejects client projections and write-blocked worlds. Application compares the
target snapshot, then revalidates current domain conditions before calling the domain
mutation method. Failed owner attempts consume the token; foreign callers cannot consume
another actor's token. A successful token cannot be reused.

| Domain | Allowed repair | Preserved / rejected |
|---|---|---|
| Ownership | Empty compatibility alias to an explicit supported current owner | Conflicting owners, legacy-only ownership, unknown owners, defaults, transfers and claim provenance |
| Diplomacy | Discard pending consent whose target no longer improves the current LOTR relation | Effective LOTR relation, valid requests; malformed consent requires review |
| Ruler | Refresh cached name from the matching online UUID | Office and UUID; missing ruler, offline/mismatched identity and stale names fail closed |
| Public waypoint | Relink unchanged coordinates to their uniquely resolved unoccupied tile | Coordinates, identity, wire ID, level and approval provenance; native, migrated and quarantined records are excluded |
| Population | No inferred repair; rate is derived from developed Builds, control and configuration | Bank balance, development allocations, payout cursor and remainders |
| Capital | No inferred replacement or default restoration | Authoritative record; inspection reports metadata readiness, loaded/unloaded chunk availability and relocation-block reason. Live standing safety remains explicitly unverified. Explicit `/kome capital relocate <faction> here` retains its existing validated authority |

Preview, application and service-level denials write structured `ADMIN/REPAIR_*` entries
to `KOMEAuditService`; successful rows include actor, subject, token and compact before/after
values. Public waypoint changes additionally retain the registry's existing detailed
before/after history. The existing periodic publisher observes its revision. Unauthorized
root calls do not read a world to append an audit; write-blocked/client worlds are never
mutated to record a denial.

Snapshot checks include raw ownership fields and claim metadata, ruler UUID/name, diplomacy
workflow metadata plus current LOTR relation, and waypoint record/revision plus current
geometry resolution. Destination occupancy and online ruler identity/name are checked again
at apply time. These are server-thread checks; no worker-thread or background repairs run.

## Deferred acceptance scope

These admin additions do not expand dev's faction defeat or conflict lifecycle, defensive-gate
relinking, company location/state repair, corrupted-conflict repair, combat/finale/season
milestone logging, a daily notification batch, global milestone notifications or Discord.
Existing conflict, stewardship and war repair commands are retained, not expanded or
claimed as covered by these new plans. Broader blocked-action reason coverage and audit
coverage across all canonical actions remain KOM-40 work. Ambiguous corruption, unavailable
geometry and quarantined waypoint evidence require review; no guessed ownership, population,
ruler, capital or destination is created.

## Validation and remaining live acceptance

Original foundation validation at `afcbc42d634e3ef463df116668073734537e2f7c`
on 2026-10-02: Gradle 8.5 `--offline --no-daemon test build`
passed under `Local\KOME-Heavy-Validation`. JUnit XML reports 1,394 tests: 1,389 passed,
zero failures/errors and five existing skips. All 24 added behavioral tests passed.
The production `KOME-LOTR-Addon-1.0.8.jar` SHA-256 is
`593fea40d97bb435112b09c4db7fb9a21ce332528f60988360012e9bdb1ec695`.
Task-local evidence: `outputs/kom40/full-build.log` and `build/test-results/test/` (ignored).
That revision avoided chunk provisioning inside the inspection service but still used
the loading `MinecraftServer.worldServerForDimension` lookup in the command. The
follow-up below corrects that remaining defect. Confirmation tokens/before-after audit
values are retained when cached ruler names are corrupt and oversized.

Behavioral JUnit tests use task-owned disposable in-memory world data and real domain
services. They exercise root permission rejection before world access, preview/apply
permissions, actor/world binding, expiration/capacity, replay rejection, stale target state,
changed LOTR relations/consent, online identity/name checks, bounded diagnostics/history,
NBT save/reload, write-blocked/client authority and preservation of valid state. Corrupted
waypoint links/write-blocked state are injected only into test fixtures.

Full Gradle builds use Windows named mutex `Local\KOME-Heavy-Validation`, copied ignored
dependency jars and outputs under this task's worktree. No repairs run against existing
user worlds. No PR merge, deployment or modification of existing runtimes is included.

### Capital lookup correction and disposable Forge evidence (2026-10-02)

Starting from `afcbc42d634e3ef463df116668073734537e2f7c`, the capital command now
uses `DimensionManager.getWorld`. An absent dimension passes null into
`inspectionReadiness`, preserving `Deployment world unavailable; live standing safety
is unverified.` It does not initialize the dimension or infer safe deployment.

The new regression invokes the actual command with the real server lookup implementation
available, intercepting the dimension registry and chunk provider. It covers an absent
dimension and a loaded dimension with both loaded and unloaded chunks, restores global
fixtures in `finally`, and fails on dimension initialization or chunk provision. Before
the fix it failed with this actual call path:

```
KOMEAdminDiagnosticsCommands.process:41
  MinecraftServer.worldServerForDimension:781
    DimensionManager.initDimension:227
      AssertionError: Inspection entered DimensionManager.initDimension
```

After the fix, Gradle 8.5 `--offline --no-daemon test --tests '*KOMEAdmin*'
--tests '*KOMEFactionCapitalServiceTest' build` passed under
`Local\KOME-Heavy-Validation`: 36 tests, zero failures/errors/skips, including retained
loaded-world coverage. This focused run is distinct from the historical full suite above.
Corrected production jar SHA-256:
`2135f43ae98a45cab3b9c09ec054da50df73314929973d9f8db3d1737f849dad`.
Self-review checked null propagation, loaded-world behavior, fixture restoration and scope.

The same jar ran under Forge 10.13.4.1614, LOTR v36.15 and Java 8 in the task-owned
`outputs/kom40/runtime-correction/kom40-disposable` world on loopback port 25640.
Each run held the same mutex through clean shutdown. Runtime libraries were copied;
no existing world or configuration was copied or repaired. Server-console checks passed:

1. Capital, population, ruler, diplomacy, ownership and audit commands dispatched through
   the real command manager. Capital reported loaded/unloaded chunk presence across runs
   while retaining explicit unverified safety.
2. Only the stopped disposable world's NBT was seeded with one obsolete Gondor/Rohan
   `friends` consent; the authoritative LOTR relation remained `allies`. Two previews
   were created, the first applied, and the second returned `State changed since preview;
   no repair applied; preview again`.
3. `save-all`, clean stop and cold restart retained `allies` with no pending consent.
   Live audit output and saved NBT retained two `REPAIR_PREVIEW` entries, one
   `REPAIR_APPLY` with before/after values, and one stale `REPAIR_DENIED`.

Task-local evidence (ignored): `outputs/kom40/capital-before-fix.xml`,
`capital-correction-build.log`, `forge-{initial,repair,restart}-probes.txt` and matching
stdout/stderr logs; `disposable-nbt.py` verifies saved metadata and audit after restart.
All disposable server processes stopped cleanly. Existing worlds, runtimes, primary
checkout, other worktrees and stash were preserved.

Remaining live acceptance: operator versus ordinary-player client chat/dispatch,
matching online-ruler rename, and public waypoint revision reaching a connected client.
No client was connected. Unloaded-dimension/no-initialization and no-chunk-provision
guarantees are covered by the actual-command regression, not a live dimension-unload
experiment. Broader KOM-40 acceptance remains deferred as listed above.

### Integration with current dev (2026-10-03)

Merged `origin/dev` at `a8ee2665d69ed5eb51df1edf047c8395b37080a9` into PR head
`66ffd7741385f7bb4d58c41ac1b8011eafc03f95`. Four conflicts in `KOMECommandKome`
were resolved by combining usage, dispatch, root completion and repair completion.
Diagnostics, token preview/apply, paged audit, conflict inspect/end/repair and progression
controls coexist. Conflict repair retains its own tile/preview/apply arguments; the
metadata repair token workflow remains separate. Staff checks precede world access.

The non-loading `DimensionManager.getWorld()` correction, output bounds, guarded repairs
and legacy ruler-name preview behavior are unchanged. Self-review compared all 282
incoming files with dev: outside the command resolution and PR #20's existing additive
diplomacy/capital helpers, they matched before the test-only schema correction below.
WorldData, conflict, progression, muster and faction-defeat persistence remain dev's code.

Three added actual-command regressions cover combined usage/completion; diagnostics and
both repair forms through root dispatch; conflict inspection, stale-ID rejection and
forced end; bounded paged audit; progression cooldown dispatch; and legacy ruler-name
preview followed by explicit token application. The existing unauthorized-root test now
also covers conflict, progression and all repair families without accessing the world.
Existing dimension-registry and chunk-provider interception tests remain enabled.

The first focused run found a stale schema-6 literal in the merged muster save/load
regression: current dev uses root schema **7**. That test now checks the canonical schema
constant; no production schema or persistence behavior was changed. The new forced-end
assertion was corrected to dev's `FORCED_END` audit action. Focused validation then passed
**230 tests, zero failures/errors/skips**.

Full `clean test build --offline --no-daemon --console=plain`:
**2,030 passed, two existing Windows symbolic-link skips, zero failures/errors**, including
release reobfuscation and all 10 diagnostics command regressions. Both focused and full
gates held `Local\KOME-Heavy-Validation`. Integrated release SHA-256:
`4cc3938923d11f040ea91a6f94026bea05c2c0212f977414f94a0aeefd01658a`.
Primary checkout, all other worktree heads/statuses and stash matched the pre-merge
snapshot; no existing worlds or runtimes were changed.

Task-local integration logs and XML/count snapshots are under ignored
`outputs/kom40-dev-integration`. No Forge run was performed for this integration; native
runtime evidence above applies to correction head `66ffd77`. The stopped disposable
runtime jar retains SHA-256 `2135f43ae98a45cab3b9c09ec054da50df73314929973d9f8db3d1737f849dad`.
Remaining live acceptance is unchanged: player/operator chat and dispatch, online ruler
rename and public-waypoint publication to a connected client. No PR merge or deployment.
