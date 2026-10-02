# KOM-40 scoped audit/repair foundation

Base: `fa6a43cc4876f68fd59f361fa6182b21ae26d974` (dev). This is a partial foundation for
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

This PR does not implement faction defeat, future siege/conflict systems, defensive-gate
relinking, company location/state repair, corrupted-conflict repair, combat/finale/season
milestone logging, a daily notification batch, global milestone notifications or Discord.
Existing stewardship/war repair commands are retained as legacy behavior, not expanded or
claimed as covered by these new plans. Broader blocked-action reason coverage and audit
coverage across all canonical actions remain KOM-40 work. Ambiguous corruption, unavailable
geometry and quarantined waypoint evidence require review; no guessed ownership, population,
ruler, capital or destination is created.

## Validation and remaining live acceptance

Final local validation on 2026-10-02: Gradle 8.5 `--offline --no-daemon test build`
passed under `Local\KOME-Heavy-Validation`. JUnit XML reports 1,394 tests: 1,389 passed,
zero failures/errors and five existing skips. All 24 added behavioral tests passed.
The production `KOME-LOTR-Addon-1.0.8.jar` SHA-256 is
`593fea40d97bb435112b09c4db7fb9a21ce332528f60988360012e9bdb1ec695`.
Task-local evidence: `outputs/kom40/full-build.log` and `build/test-results/test/` (ignored).
Self-review corrected the capital chunk-loading inspection path and retained confirmation
tokens/before-after audit values when cached ruler names are corrupt and oversized.

Behavioral JUnit tests use task-owned disposable in-memory world data and real domain
services. They exercise root permission rejection before world access, preview/apply
permissions, actor/world binding, expiration/capacity, replay rejection, stale target state,
changed LOTR relations/consent, online identity/name checks, bounded diagnostics/history,
NBT save/reload, write-blocked/client authority and preservation of valid state. Corrupted
waypoint links/write-blocked state are injected only into test fixtures.

Full Gradle builds use Windows named mutex `Local\KOME-Heavy-Validation`, copied ignored
dependency jars and outputs under this task's worktree. No repairs run against existing
user worlds. No merge, deployment or runtime modification is authorized by this PR.

Remaining acceptance checks in a disposable Forge world: operator versus ordinary-player
chat/dispatch behavior; preview then changed-state rejection; matching online-ruler rename;
public waypoint revision reaching a connected client; and save/restart inspection of audit
and repaired metadata. Automated NBT round trips do not prove Forge save scheduling or
client receipt. Broader issue acceptance remains deferred as listed above.
