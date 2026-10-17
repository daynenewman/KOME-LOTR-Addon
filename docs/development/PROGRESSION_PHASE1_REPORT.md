# Progression Phase 1 - Commoner and allegiance checkpoint

Recorded 2026-10-17. **Partial Phase 1, automated checkpoint verified; Minecraft
acceptance pending.** Birth-alignment implementation awaits the calculation below.

## Delivered

Local implementation commit **cdbc051** separates permanent rank from native faction
allegiance. The active ranks are Commoner, Knight, Lord and Prince; King remains a
political office. Native pledge observation no longer grants rank or announces a
promotion. Commoners can use the existing validated Master service. Normal earned
Knight/Lord service, recommendations, commissions, patrols and military authority
remain in place for later adaptation.

The book uses canonical rank names and the four-rank ladder. Unpledged Commoners
see an allegiance objective; pledged Commoners see Master service. The tracker
reads actual pledge state. Existing parchment, measured layout, scrolling,
navigation, tooltips and Show Me were retained. Faction-specific higher titles
remain in identity/nameplate presentation; they are not save authority.

Commoner keeps the former starting-service permission floor. Build submission and
verified starting-waypoint return use Commoner; native faction, terrain, travel
and domain authorization still apply. Missing progression records fail closed.
Knight battle entry, Lord company/recruitment floors and Crown checks were retained.
No conquest, crafting, equipment, specialty or royal mechanics were redesigned.

## Confirmed causes and migration

- `recognizeCommittedPledge` explicitly promoted Wanderer to Serf, including from
  creation, native pledge observation, login and reconciliation. It now records
  only the historical allegiance achievement. Staff rank correction also no longer
  invents that allegiance marker from rank alone.
- Rank was already serialized by string keys, not enum ordinal. `wanderer` and
  `serf` now decode as Commoner and write `commoner`; additive
  `RankFoundationVersion=1` and `LegacyStartingRank` retain migration provenance.
- Valid `knight`, `lord`, `prince` ranks and their numeric service-recognition
  orders 2/3/4 are preserved. Existing duties, Master relationships, completion
  flags and recommendation records round-trip in disposable NBT fixtures.
- Deprecated Java aliases retain old service references without separate ranks.
  Historical achievement IDs, group IDs, relationship keys and packet IDs remain.
  Malformed/missing rank still falls back safely; achievements do not infer rank.
- Native LOTR alignment and pledge storage are not rewritten by rank migration.
  No new or retroactive birth award is granted in this checkpoint.
- Matching artifacts are now **1.0.11-commoner-g15**, because g14 cannot decode the
  canonical Commoner key. Packet layouts/registrations are unchanged; old g14 is
  explicitly rejected by the handshake regression test.

## Character creation decision needed

The supplied [approved Phase 1 scope](../design/progression/PHASE1_APPROVED_SCOPE.md)
is saved. The separately referenced full redesign document was not found; its
location was requested. No later-phase design was inferred from historical files.

Current `StartingFactionApplication.tryApply` first raises selected alignment to
the native pledge minimum, then confirms the pledge and sets the persisted
`startingFactionApplied` flag. Completion and recreation guards already prevent
ordinary reapplication. Automatic starting allegiance is an existing server option.

**Unresolved:** does the new +100 replace that minimum grant, or add afterward?
Adding afterward can produce 200 from zero where the native minimum is 100.
Replacing it also needs an explicit rule if +100 is below a faction's native
pledge minimum. The question remains pending; character-creation source was not
changed and no one-time birth-bonus regression is claimed.

After the calculation is settled, implement a durable award ledger in persisted
character data, guarded by first finalization and actual chosen birth faction.
Preserve old completed/applied flags without retroactive rewards. Validate failed
pledges, no-birth-faction choices, later pledges, repeat packets, reload, recreation
and allegiance changes with disposable player-data fixtures. Then run focused
creation/progression checks and Full again. This is remaining Phase 1 work, before
the revised Master-to-Knight system in Phase 2.

This follows the [overnight safe-stop rule](OVERNIGHT_WORKFLOW.md): “an unresolved
design choice” needs a resumable checkpoint. It does not represent Phase 1 completion.

## Changed systems and files

- Rank/save authority: `KOMEProgressionRank`, `KOMEPlayerProgression`,
  `KOMECanonicalRankService`, `KOMEProgressionAutoCompleter`.
- Compatibility/permissions: Master selection, native offer/eligibility bridge,
  permission registry and missing-record policy, starting-travel denial copy.
- Presentation: rank summary, faction title ladder, Knight book detection,
  allegiance tracker, identity fallback, Origins group and command rank choices.
- Network/build: progression default key, exact-version fence and Gradle version.
- Tests: new `KOMECommonerFoundationTest` plus updated rank, service, permission,
  client projection and protocol regressions. The implementation commit lists all
  45 paths; inherited language edits were excluded from that commit.

## Automated verification

All commands use `scripts/kome-validate.ps1`, offline Gradle, no daemon,
max-workers=2 and the shared heavy-validation mutex. Counts overlap; do not sum.

| Check | Result | Evidence under outputs/overnight-validation/ |
| --- | --- | --- |
| Pre-change focused baseline | 102 passed, zero skipped | `20261017-172840-002-Fast-b7895208` |
| Commoner/migration/Master/Recovery/book focused | 83 passed, zero skipped | `20261017-173557-904-Fast-674d04f3` |
| Affected client/permission/service/protocol suites | 135 passed, zero skipped | `20261017-173822-444-Fast-257fd8a3` |
| Final Full: test build reobfJar | 4,174 passed / 5 skipped / zero failures/errors; 402 suites, 4,179 total | `20261017-174018-495-Full-84e8381e` |

The first baseline attempt hit an existing transformed-jar Windows deletion lock;
unchanged retry passed. Initial changed-code focused/Full runs exposed obsolete
rank assumptions, which were updated with replacement authority/migration checks.
The first Full failure is retained at `20261017-173618-880-Full-bbe38636`.
Final source compilation, tests, build and reobfuscation all pass. Two symlink
fixtures and three optional original/build waypoint-jar checks remain skipped.

Final before/after validation input SHA-256 matched:
`e487c3304b7c5779169077322e04db4041534a08fb6350fce02fb289a435f62c`.

Verified jar: `build/libs/KOME-LOTR-Addon-1.0.11-commoner-g15.jar`, **18,640,038 bytes**.
SHA-256: `16d41e488869c68fa3ef1b14f21feb9f36b19ebe1823a0a6eb2b3015afb8992f`.
Built, not installed. The tested worktree includes preserved inherited language
changes and its combat-lifecycle test; **commit cdbc051 alone is not the jar input**.

## Preservation and manual follow-up

Started from `eec1766d86312fc9496ba650bb45d28a1a4bc144`, same progression branch,
empty index, 50 inherited modified paths and two inherited untracked files.
Verified backup: `outputs/overnight-backups/20261017-172432-201-1c7c6930`,
6,870 hashed files, COMPLETE.json, exit 0. Inherited language work remains separate;
the original worktree and three stashes are retained. No push, PR, jar installation,
valuable-save write, destructive Git operation or machine-security change.
Hash comparison verified **2,193 pre-existing files unchanged** outside the explicit
assignment change list. Documentation QA passed for 35 documents, 880 local links,
1,144 symbol references, 48 exact test selectors and 14 blank latest answers using
the PowerShell equivalent of the routing checker (Python unavailable on PATH).
Evidence: `outputs/stabilization/commoner-preservation.json` and
`outputs/stabilization/documentation-checks.json`; whitespace checks also passed.

[Fourteen focused Yes/No tests](../testing/PLAYTEST_LATEST.md) cover only this
implemented checkpoint and preserved gameplay. All answers are unselected.
No Minecraft, connected-client or multiplayer acceptance was performed. Earlier
11-pass / 2-fail / 2-untested feedback remains historical evidence for another build.
