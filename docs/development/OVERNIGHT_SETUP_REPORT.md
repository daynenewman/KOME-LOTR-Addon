# Overnight setup report

Completed on **2026-10-16**, using Windows PowerShell 5.1 and the existing Java 8 /
offline Gradle environment. No gameplay source or existing gameplay test was
changed by setup. No new feature, Minecraft runtime, scheduled job or model session
was started. No push or PR was made.

## Inspection and preservation

Started on branch
`elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system` at
`e9ac9896a6ea05fd043ecccc49c4c8920a988ac5`, following implementation checkpoint
`4b9b92c` and reliability checkpoint `243aa4f`. Git log, index, dirty files, refs,
worktree and all three stashes were inventoried. The index was initially empty.

The separate language repair had 50 tracked modifications and two untracked
files; all remain unchanged and outside the setup commit. All 2,217 initial files
outside setup scope were hash-compared successfully. Original routing instructions
remain the exact content prefix of `AGENTS.md`. Root `README.md`, `build.gradle.kts`,
`gradle.properties`, `.gitattributes` and existing validation helpers are unchanged.

Kit SHA-256:
`9a24ae77142da45a0c161123fc661236d7c71fefa3169b218c1526e163e76a74`.
Its four existing development documents and two scripts matched the already
extracted local copies exactly. Its proposed AGENTS additions were merged into
the current instructions; its provisional README was replaced with an accurate
development entry point. No archive was extracted over source or root build files.

## Files added, adopted or modified

| Files | Result |
| --- | --- |
| `AGENTS.md` | Original routing retained; scoped autonomy, checkpoints, safe stops, validation, local commits and handoff added. Detailed gameplay rules stay in existing routes. |
| `.gitignore` | Supplied ZIP and local backup folders excluded; existing output/build/dependency exclusions retained. |
| `docs/routing/INDEX.md` | New general workflow/tools discoverable without changing existing domain/Native routes. |
| `scripts/kome-dev-common.ps1` | Shared native Git, UTF-8/long-path, hashing, snapshot and input-fingerprint helpers. No global Git configuration edits. |
| `scripts/kome-overnight-backup.ps1` | Verified source/dependency snapshot plus full raw Git metadata, index and stash reflogs. |
| `scripts/kome-overnight-restore.ps1` | Integrity-first recovery into a new external directory; state comparison and Git object verification. |
| `scripts/kome-validate.ps1` | Fast compilation/focused tests; Full test/build/reobfuscation; shared mutex, native exit codes, input fingerprints and unique evidence. |
| `scripts/test-overnight-tools.ps1` | Dependency-free disposable integration tests, including corruption/overwrite refusal and failure propagation. |
| `docs/development/README.md` | Development entry point. |
| `docs/development/OVERNIGHT_WORKFLOW.md` | One-brief procedure, autonomous repair, safe stops, validation/recovery commands and honest acceptance levels. |
| `docs/development/CURRENT_STATUS.md` | Actual tested Git/source state and separate ongoing language repair. |
| `docs/development/FEATURE_BRIEF_TEMPLATE.md` | Scope, approval, compatibility and acceptance specification. |
| `docs/development/OVERNIGHT_START_PROMPT.md` | Reusable app prompt and exact Windows CLI launch sequence. |
| `docs/development/briefs/STAFF_EXERCISE_VISIBILITY.md` | Proposed first feature only; not approved or implemented. |
| `docs/development/OVERNIGHT_SETUP_REPORT.md` | This evidence record. |

Only those 15 setup paths belong to the local setup commit. Identify that commit
with `git log -1 --format='%H %s' -- scripts/kome-validate.ps1`; the final handoff
also supplies its SHA. The report records the baseline above rather than claiming
its own commit hash was known before committing it.

## Existing tools reused or preserved

The root Gradle wrapper, current build configuration, dependency jars, JUnit suites
and `Local\KOME-Heavy-Validation` coordination are reused. `gradle-local.ps1`,
`tools/kom11/validate.ps1`, `tools/campaign-lifecycle/validate.ps1`, other domain
tools and historical evidence remain intact. The new helper supplies general Fast
selectors, Full reobfuscation and isolated evidence absent from the older domain
helpers; it does not replace their Native checks. The routing validator is reused.

The kit's bundle-only backup could omit older stash reflogs; raw primary-checkout
Git recovery now retains them. Its compile-only default Fast mode now includes
focused tests. Windows long-path handling, PowerShell 5.1 JSON arrays, native
stderr/exit handling and edits that leave Git status identical were verified/fixed.

## Backup and recovery evidence

- Pre-edit safety copy: `outputs/overnight-setup/baseline-20261016-151818/`, 6,662
  hash-verified source/Git/dependency files, including the incoming kit and edits.
- Real scripted snapshot: `outputs/overnight-backups/20261016-153050-124-f8354fdc/`,
  6,668 hashed files with `COMPLETE.json` and manifest integrity checks.
- Actual recovery destination:
  `C:\Users\elija\AppData\Local\Temp\KOME-Overnight-Recovery-6877ad2d803047899076f0524e03bd54`.
  It was new and separate from the source. HEAD, branch refs, staged/unstaged/
  untracked state, dependency bytes and all three stash entries matched; `git fsck`
  passed. Original checkout and existing backups were not overwritten.
- Disposable tests additionally proved staged/unstaged binary edits, tracked
  deletions/renames, literal bracket/Unicode filenames, older stash content,
  ignored dependency recovery, world exclusion, active-lock refusal, corruption
  rejection before destination creation and refusal to overwrite existing paths.
- Generated evidence and local snapshots are ignored by Git. The ZIP remains
  local and ignored. No saves were copied, modified or reset by the scripts.

Recovery command and supported-layout limits are in
[the workflow](OVERNIGHT_WORKFLOW.md#backup-and-recovery). Backups retain private
Git configuration/hooks; keep them private. Same-disk copies are not disk-loss
protection. No backup or test fixture is automatically deleted.

## Validation results

| Check | Result and evidence |
| --- | --- |
| Windows tool integration | **39 checks PASS**; `outputs/overnight-tool-tests/20261016-153542-662-04945add/summary.json`. |
| Fast default | **84 passed**, five suites; no failures/errors/skips; `outputs/overnight-validation/20261016-153727-384-Fast-40512900/`. |
| Full | **4,148 passed, five skipped**, 395 suites, 4,153 tests, zero failures/errors; `outputs/overnight-validation/20261016-153817-547-Full-18810856/`. `test build reobfJar --no-daemon --max-workers=2 --console=plain --offline` succeeded. |
| Input stability | Before/after source/dependency/index fingerprints and Git state match for both final successful runs. |
| Intentional real failure | Nonexistent selector `kome.infrastructure.ThisTestMustNotExist` produced exit **1** with logs; `outputs/overnight-validation/20261016-153633-845-Fast-eca46dc8/`. Expected negative test, not an existing gameplay failure. |
| Native exit/preflight/lock handling | Disposable wrapper exit **7** propagated; invalid arguments returned **2**; occupied build mutex returned **3**; invalid evidence and mid-run source edits failed nonzero. |
| Routing validator | PASS: 25 documents, 821 local links, 1,110 symbol references, 48 exact JUnit selectors. |
| Documentation and diff | Development local links and `git diff --check` pass. Final status/report edits were checked separately after the source/Java validation freeze. |
| Preservation | Original routing prefix and all protected files/helpers match; no new gameplay changes. |
| Codex launch syntax | Installed `codex-cli 0.163.0` accepts the documented flags in `--help` mode, without starting a model run. |

The existing five skips are the two Windows symbolic-link fixtures and three
waypoint transformer checks requiring an optional external original/build jar
pair. They match the previously documented baseline; no test was disabled. No
existing compilation or test regression was discovered. Detailed XML, skipped
test names, command arguments, source state and jar inventory hashes are retained
per run; `outputs/overnight-setup/` contains consolidated logs and preservation
evidence. Old jars are intentionally retained in `build/libs`, so an inventory
entry is not proof that an old artifact was rebuilt.

Current verified reobfuscated addon:
`build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar`, 18,637,319 bytes;
SHA-256 `e8cc7ef961cc15c874f30d3635e7d85b5568554db6181f3da18755448eeac1ec`.
No jar was installed into a runtime.

## Launch instructions and first feature

1. Review [current status](CURRENT_STATUS.md) and the separate uncommitted language
   repair. Do not include it in another feature's commits.
2. Copy [the template](FEATURE_BRIEF_TEMPLATE.md), specify exactly one outcome and
   its acceptance checks, resolve design choices, and mark that brief **Approved**.
3. Open this checkout in Codex and paste the complete app prompt from
   [OVERNIGHT_START_PROMPT.md](OVERNIGHT_START_PROMPT.md), appending the approved
   brief path. Alternatively run that file's complete Windows CLI block from the
   repository root after replacing `MY_APPROVED_FEATURE.md`.
4. Leave the terminal/machine awake and connected. The agent makes a verified
   backup, investigates, implements within scope, compiles/tests, repairs ordinary
   regressions, runs Full, commits only verified assigned changes locally, and
   writes current status plus a morning handoff. Approval-only conflicts stop safely.
5. In the morning inspect commits and the handoff, then perform the recorded
   disposable Minecraft acceptance checks. Do not automatically start another brief.

The CLI sequence uses stdin, JSONL logging, final-message output and automatic
tool review with workspace-write, as checked against installed help and
[official non-interactive documentation](https://learn.chatgpt.com/docs/non-interactive-mode).
It explicitly grants the existing cache and Git metadata directories needed for
validation/local commits. No global config, credentials, sandbox-bypass flag or
scheduled task was installed. A real model feature run was deliberately not
started during infrastructure setup; OS/policy, account, network, sleep and context
limits can still interrupt a future night. Prompt instructions do not guarantee
Minecraft correctness or unlimited execution.

Recommended first feature, after reviewing the inherited repair: **clearly label
staff commission exercises on the existing Rank page**, including the no-earned-
credit/reward boundary. [The proposed brief](briefs/STAFF_EXERCISE_VISIBILITY.md)
reuses persisted testing state and existing summary/sync contracts. It is bounded,
has measurable lifecycle/UI acceptance, changes no combat or reward policy, and
is not already exposed by the inspected Rank summary. It remains unapproved.
