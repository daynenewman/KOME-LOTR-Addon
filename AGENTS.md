# Repository routing

1. Begin repository tasks with [docs/routing/INDEX.md](docs/routing/INDEX.md).
2. Select routes for the requested behavior or symptom; read only the relevant system guides and flows, then inspect their linked code and tests.
3. Follow dependencies when a task crosses systems. Trace server authority, persistence, packets, and client presentation separately.
4. Treat the map as navigation, not an investigation boundary. Expand searches when a route is incomplete or disagrees with code; code in the current checkout takes precedence over historical documentation.
5. Update affected routing guides when changing entry points, dependencies, behavior, or verification. Keep implemented, disconnected, planned, and runtime-verified behavior distinct.
6. Preserve existing worktrees, branches, stashes, uncommitted work, worlds, and runtimes. Use disposable validation when gameplay checks are needed.

Do not require every guide or the full historical audit for every task. Validation prerequisites and evidence limits are in the routing index.

## Scoped and overnight development

- Follow [the overnight procedure](docs/development/OVERNIGHT_WORKFLOW.md) for unattended work. Read the assigned approved brief and verify [current status](docs/development/CURRENT_STATUS.md) against Git and source. Complete only that assignment; do not start another feature because time remains.
- Before editing, inventory branch/HEAD, index, dirty files, worktrees and stashes. Run `scripts/kome-overnight-backup.ps1` and retain its verified snapshot path. Preserve unrelated edits and stage only your own verified paths/hunks; never use blanket staging in a dirty checkout.
- Make manageable checkpoints with acceptance checks. Autonomously inspect dependencies, implement within the approved design, compile, diagnose failures and repair regressions. Ordinary technical failures are work to resolve, not a reason to request routine confirmation.
- Stop safely for an unresolved product/design decision, dangerous operation, missing user-controlled prerequisite or significant architectural conflict. Save evidence and a resumable handoff stating the exact decision needed. Never weaken tests, delete valid calls, add empty stubs or replace current classes with older snapshots to make checks pass.
- Preserve canonical authority and serialization contracts. Follow the owning routes for progression, GUI, military, networking and persistence; do not duplicate their detailed rules here. Apply wording changes to current code and retain machine IDs, NBT keys, enum values, packet contracts and strings consumed by code.
- Run `scripts/kome-validate.ps1 -Mode Fast` with task-relevant `-Tests` during development, and `-Mode Full` before declaring automated completion. Use the shared heavy-validation mutex. Passing JUnit/build checks, disposable Forge checks, connected-client/GUI checks and actual multiplayer acceptance are separate evidence levels.
- Create small local commits only for verified work within the assignment. Preserve inherited staged work; inspect `git diff --cached` before committing. Do not push, open PRs, install built jars, alter original worlds, change global machine security settings or expand permissions beyond the task without explicit authorization.
- Update `docs/development/CURRENT_STATUS.md` and produce a concise morning handoff: tested source/commits, outcomes, commands/counts/skips/logs, outstanding live acceptance, blockers and one next action. Never label unfinished or untested gameplay as accepted.
