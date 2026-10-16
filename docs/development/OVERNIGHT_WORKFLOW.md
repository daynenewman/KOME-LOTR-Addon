# Overnight development

One approved brief is the session boundary. Technical investigation, implementation,
regression repair and validation may continue without supervision within that
scope. Completing it does not authorize another roadmap item.

## Before bed

1. Finish other sessions that write this checkout. Close editors that might save
   the same files. The build mutex coordinates heavy validation, not source edits.
2. Review [current status](CURRENT_STATUS.md), then verify branch, HEAD, index,
   dirty worktree and stashes. The current uncommitted progression language repair
   is separate work; it must not be swept into a feature or setup commit.
3. Copy [the brief template](FEATURE_BRIEF_TEMPLATE.md) to a task-specific file.
   Fill in outcome, scope, acceptance, authorized operations and design decisions.
   Mark it **Approved** only when you approve that task. Keep one feature per brief.
4. Confirm local dependencies, an authenticated Codex session, enough free disk
   space and power/network availability. Prevent sleep using your normal Windows
   settings. Do not leave another Minecraft process writing a proposed test world.
5. Launch using [the start prompt and commands](OVERNIGHT_START_PROMPT.md).

## Codex procedure

1. Read `AGENTS.md`, the routing index and the brief. Treat current source as
   authoritative. Record exact Git state and inherited staged/unstaged/untracked
   work. Inspect only relevant routes, expanding when code disagrees.
2. Run `scripts/kome-overnight-backup.ps1` before editing. Confirm exit 0 and the
   `COMPLETE.json` marker. Record its path in the handoff. If the supported backup
   cannot protect this checkout, stop before editing; never stash/reset around it.
3. For code/gameplay work, establish the baseline with Fast and the relevant
   route's tests. Separate inherited failures from your own regressions. Make a
   short checkpoint plan covering authority, persistence/recovery, packets, presentation and tests where
   applicable. Routine implementation details are the agent's decisions.
4. Implement one complete feature through those checkpoints. Compile and run
   meaningful focused tests during development. Inspect failures, identify causes,
   repair within scope, then repeat the affected checks. Keep valid calls, behavior
   and assertions; do not use stubs, test suppression or historical source replacement.
5. For code/gameplay work, run Full on the final source. Inspect skips and the
   evidence summary. A failing, skipped or unavailable acceptance check is not a
   pass. Update affected routes.
   Documentation-only assignments use formatting, link, command and source checks;
   do not run another Gradle build solely for documentation changes.
6. Review the diff and explicitly stage only the assignment's verified files/hunks.
   Inspect `git diff --cached`; inherited staged changes must stay out of the
   commit. If safe separation is impossible, hand off that boundary for review.
   Create local commits after relevant checks pass; no push or PR.
7. After the verified implementation commit, follow [gameplay acceptance](../testing/README.md)
   to update the focused [latest checklist](../testing/PLAYTEST_LATEST.md), optional
   extended coverage and cumulative manual history for gameplay work. Use actual
   current source and exact build identity. Preserve returned results before
   regeneration; leave new answers unselected.
8. Update [current status](CURRENT_STATUS.md). Record the tested checkpoint and
   dirty input state, local commits, exact test counts/skips, evidence paths and
   remaining live checklist. Verify and locally commit the checklist/status
   documentation, then finish with a short morning summary. Stop.

Safe stop: an unresolved design choice, dangerous operation, unsupported migration,
significant architectural conflict, or missing user-controlled prerequisite needs
approval. Preserve the current state and backup, describe the problem and concrete
options, and end with a resumable checkpoint. Do not declare completion. Retry or
repair ordinary compile/test/environment mistakes that can be resolved within scope.

## Validation

Run from any working directory; these scripts locate their own repository.

```powershell
.\scripts\test-overnight-tools.ps1
.\scripts\kome-validate.ps1 -Mode Fast
.\scripts\kome-validate.ps1 -Mode Fast -Tests 'kome.common.data.KOMEKnight*','kome.common.data.KOMELordship*'
.\scripts\kome-validate.ps1 -Mode Full
```

Fast invokes `compileJava test` with five default smoke selectors covering
progression hardening, promotion, Master reliability, world schema and packet
registration. `-Tests` replaces that default set; choose the owning route's tests
for each feature. Full always invokes `test build reobfJar` without test filters.
Both use the root `gradlew.bat`, `--no-daemon --max-workers=2 --console=plain`,
and `--offline` by default. Set `-GradleUserHome` if needed; the default uses the
existing environment setting or the user's `.gradle`. `-Online` explicitly allows
normal Gradle dependency resolution when a cache must be populated.

`Local\KOME-Heavy-Validation` is shared with the existing tools. The default wait
is 900 seconds; `-LockTimeoutSeconds 0` fails immediately if another build is
active. Exit 0 means success; 2 means preflight failed; 3 means lock timeout;
otherwise Gradle's failing exit code is propagated (usually 1). Unexpected script
or evidence errors also fail nonzero. Inspect the message rather than assuming an
exit code alone identifies the cause.

Each run writes a unique directory in `outputs/overnight-validation/`, containing
`gradle.log`, `summary.json`, JUnit XML when the test task ran, and an inventory
of existing `build/libs` jars with hashes after successful Full builds. Older jars
are preserved; that inventory does not mean each jar was rebuilt. Check the current
version and exact output filename before any separately authorized installation.
Summary provenance includes HEAD, dirty state,
selectors, cache and commands, plus hashes of source/dependency bytes and the Git
index. Source/index/ref changes during a run invalidate
its success. Do not edit even documentation during a real validation run.

Existing `tools/kom11/validate.ps1`, `tools/campaign-lifecycle/validate.ps1`,
the Gradle wrapper and `gradle-local.ps1` remain available. The former's Native
mode is a separate disposable Forge check; Full does not launch Minecraft.
Use [routing prerequisites](../routing/INDEX.md) rather than duplicating gameplay
or dependency rules here. Passing builds do not establish live acceptance.

## Backup and recovery

```powershell
$snapshot = .\scripts\kome-overnight-backup.ps1
# Optional independent disk/location, explicitly selected by you:
# $snapshot = .\scripts\kome-overnight-backup.ps1 -DestinationRoot 'D:\KOME-Backups'
.\scripts\kome-overnight-restore.ps1 -Backup $snapshot -Destination 'C:\path\to\NEW-KOME-Recovery'
```

The default destination is ignored `outputs/overnight-backups/`. Snapshots retain
all tracked/nonignored untracked file bytes, deletions and staged state through a
complete raw `.git` copy, including branches, tags, object history, index and all
stash reflogs. The two locally supplied `libs` dependency jars are included when
present. SHA-256 checks cover copied files and the manifest; success is published
only after source stability and copied-byte checks pass. Failures leave evidence
without a success marker. Source files and refs are never changed by backup.

Recovery verifies integrity before creating a destination, refuses any existing
path or path inside the original checkout/backup, recreates the source and Git
metadata, compares HEAD/branch/refs/stashes/staged/dirty state, and runs `git fsck`.
Use only trusted backups: raw `.git` retains local config and hooks. Inspect the
recovered checkout before development. It may need its normal dependency cache.

These are source backups, **not world backups**. Ignored runtimes, saves, caches,
outputs, IDE state and other ignored files are excluded; nothing edits or resets
worlds. Same-disk snapshots protect edits, not disk loss: select independent
storage for disaster recovery and keep private backups out of Git.
The helper deliberately rejects linked/multiple worktrees, submodules, external
Git object alternates, external `core.worktree`, reparse points and active Git
locks. Those layouts need a separately approved recovery procedure; no automatic
conversion or deletion occurs. Do not run backup concurrently with any writer.

## Morning review

Read current status and the run's final handoff, inspect commits and inherited
dirty work separately, then follow [the latest disposable-world checklist](../testing/PLAYTEST_LATEST.md).
The handoff names the highest-priority next session; optional persistence, GUI and
multiplayer tests remain in [extended coverage](../testing/PLAYTEST_EXTENDED.md).
Return bold Yes/No answers and Notes using [the processing protocol](../testing/README.md).
Append observed results and repair/retest links to history; missing prerequisites
remain untested. Mark actual acceptance only from observed Minecraft evidence.
No new feature begins until its own brief is approved.
