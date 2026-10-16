# Start an overnight session

Use one approved feature brief. This setup does not authorize executing a proposed
feature or a queue of unrelated features.

## Codex app / interactive session

Open this repository in Codex. Paste the following and append the approved brief
path (or its full text):

```text
Read AGENTS.md, docs/routing/INDEX.md, docs/development/OVERNIGHT_WORKFLOW.md,
docs/development/CURRENT_STATUS.md and the approved brief below. Verify the status
against current Git and source. Complete only this assigned brief.

Before edits, inspect HEAD/branch, staged/unstaged/untracked work, stashes and
worktrees. Make a verified source backup with scripts/kome-overnight-backup.ps1
and record its path. Preserve all inherited work. Establish the baseline and plan
manageable checkpoints. Inspect owning routes and code, then implement one complete
feature within the approved design. Autonomously compile, test, diagnose and repair
ordinary regressions; do not stop for routine implementation choices.

Run task-specific Fast tests during development and Full before declaring automated
completion. Preserve authority, serialization, network and GUI behavior. Do not
delete valid calls, add stubs, suppress tests or replace current classes with older
snapshots. Keep live Minecraft/GUI/multiplayer acceptance separate from test results.

Stop safely for an unresolved design decision, dangerous operation, significant
architectural conflict or user-controlled missing prerequisite. Preserve evidence
and explain exactly what approval is needed. Do not improvise new gameplay policy.

Stage only your verified paths/hunks and inspect the index. Leave unrelated staged
and dirty work untouched. Make local commits only for this assignment. Do not push,
open PRs, install jars, change global security settings or alter original saved worlds.

Update CURRENT_STATUS.md with the tested source/Git state, commits, test counts,
skips/logs, outstanding live checklist and blockers. Give a concise morning handoff.
Do not start another feature after this brief or claim acceptance without evidence.

Approved brief: [REPLACE WITH THE APPROVED BRIEF PATH OR TEXT]
```

## Optional Codex CLI on this Windows machine

The installed CLI was checked as `codex-cli 0.163.0`. First authenticate using your
normal Codex sign-in and verify `codex --version` and `codex exec --help`. From the
repository root, replace the brief path below with your approved brief:

```powershell
$brief = (Resolve-Path -LiteralPath 'docs/development/briefs/MY_APPROVED_FEATURE.md').Path
if ((Get-Content -LiteralPath $brief -Raw -Encoding UTF8) -notmatch '(?m)^\*\*Status:\*\* Approved\s*$') {
    throw 'Review and mark the specific brief Approved before launching.'
}
$repo = (Get-Location).Path
$cache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$run = Join-Path $repo ('outputs/overnight-sessions/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $run -ErrorAction Stop | Out-Null
$prompt = Get-Content -LiteralPath 'docs/development/OVERNIGHT_START_PROMPT.md' -Raw -Encoding UTF8
$prompt = $prompt.Substring($prompt.IndexOf('Read AGENTS.md,'))
$prompt = $prompt.Substring(0, $prompt.IndexOf('```'))
$prompt = $prompt.Replace('[REPLACE WITH THE APPROVED BRIEF PATH OR TEXT]', $brief)
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$prompt | codex exec --approve-for-me -C $repo --add-dir $cache --add-dir (Join-Path $repo '.git') --json -o (Join-Path $run 'morning.md') - 2> (Join-Path $run 'stderr.log') |
    Tee-Object -FilePath (Join-Path $run 'events.jsonl')
$nightExit = $LASTEXITCODE
Write-Host "Codex exit: $nightExit; handoff/evidence: $run"
```

`--approve-for-me` uses workspace-write with automatic tool approval review;
explicit cache/Git directory access supports Gradle and authorized local commits.
It does not approve undefined product decisions. Do not use sandbox-bypass flags.
Policy/OS restrictions may still block an operation; hand it off with the exact
reason. Validate access before an unattended night. The model/account defaults
remain yours; no model, credential, global config or scheduled task is installed.

This invocation reads the prompt from stdin and stores JSONL events plus the last
message. It is one run, not an automatic retry/feature loop. Keep the terminal,
machine, network and account limits available. Resume a stopped session explicitly
after reviewing its evidence; never blindly replay a completed task.
These CLI capabilities are documented in [official non-interactive guidance](https://learn.chatgpt.com/docs/non-interactive-mode)
and checked against the installed CLI's help. No model run was started by setup.
