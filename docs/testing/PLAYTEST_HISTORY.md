# Manual gameplay acceptance history

Only player-observed results belong in the manual counts. Keep previous attempts,
failures and later retests. Automated JUnit/Gradle/native-fixture results are
separate evidence and must not turn untested items into passes.

| Build / source | Features / session | Manual date | Passed | Failed | Untested | Significant defects | Repair / retest |
| --- | --- | --- | ---: | ---: | ---: | --- | --- |
| 1.0.10-service-g14; `5091843` + uncommitted language recovery; gameplay `4b9b92c` | Latest L01–L14: protection, counterattack, report/exercise isolation, four followers, controls, cleanup, automatic assignment, book/map | Not run; checklist prepared 2026-10-16 | 0 | 0 | 14 | No returned manual results; unknown behavior remains unaccepted | Awaiting first session; no manual retest recorded |
| Same source/build | Optional X01–X30: other missions, GUI scale, save/death/legacy, natural promotion, seat replacement, company regression, multiplayer/battle/permissions | Not run; checklist prepared 2026-10-16 | 0 | 0 | 30 | Prerequisites may be unavailable; no returned manual defects | Awaiting selected optional sessions |
| Issued checklist at `aa0647b`, intended 1.0.10-service-g14 + language recovery; installed runtime jar/hash not supplied | First owner feedback: L11–L14 passed; L07 failed; L01–L06 and L08–L10 untested; [archived original answers](archive/20261016-service-g14-first-feedback.md) | Actual play date unspecified; received 2026-10-16 | 4 | 1 | 9 | Four temporary patrol soldiers repeatedly spawn/disappear. Unspecified Serf duties also reported unreliable; no duty-specific failure counts inferred. | Patrol repair `547553a`; Master-menu wording repair `f2df9ec`. **No manual repaired-build retest yet.** |
| 1.0.10-service-g14; `f2df9ec` + inherited language recovery; jar SHA begins `7c29222a8edc` | Repaired [latest checklist](PLAYTEST_LATEST.md): F01–F02, unchanged L08–L14, M01–M06; plus optional X01–X30 | Not run; generated 2026-10-16 after final automated verification | 0 | 0 | 45 | Original L07 remains a historical manual failure pending F01 retest; Master physical-duty problems remain unspecified. | Awaiting owner observation; automated tests do not close the failure. |

The first issued file labels tests 01–14; history uses **L01–L14** to distinguish
them from extended X01–X30. The repaired checklist retains 08–14 for unchanged
expectations, adds F01–F02 and M01–M06, and maps failed L07 to sustained-stability
retest F01. Its 15 essential checks and 30 optional checks start untested.

As of feedback receipt **2026-10-16**, the original essential session is
**4 passed / 1 failed / 9 untested** (plus 30 untested optional checks). Ratings:
**fun 3/5, difficulty 4/5, clarity 4/5**. Installed build, actual play date,
singleplayer/dedicated environment and world/faction were not supplied. The
checklist build is intended provenance, not a confirmed runtime hash.

For the **repaired** jar: **0 passed / 0 failed / 45 untested**. These are distinct
build inventories; do not add the prepared rows to session totals or carry old
passes forward. The old L07 defect is not manually resolved by passing JUnit.

## Evidence retained from earlier work

- [Pass 5 implementation report](../PROGRESSION_PASS5_KNIGHT_FIELD_SERVICE_REPORT.txt):
  service tests and controlled disposable native Forge checks; its live human
  adventure/GUI/multiplayer checklist remains pending.
- [October 10 client fixture record](../progression-testing-20261010/client-verification.txt):
  controlled UI/Show Me/appearance checks on an earlier build, expressly not
  multiplayer or server objective issuance. Its screenshots still require visual
  review. Preserved as historical fixture evidence, not imported as current passes.
- [Language recovery report](../PROGRESSION_LANGUAGE_INTEGRATION_REPAIR.md) and
  [overnight setup evidence](../development/OVERNIGHT_SETUP_REPORT.md): restored
  implementation and automated verification, no new human gameplay session.

At initial checklist setup, no completed human results for that source were
available. The first explicit owner feedback is now transcribed and archived above;
this preserves older fixture evidence without turning it into current manual passes.

## Append a real session

Archive the returned checklist before generating a blank successor; follow
[the processing protocol](README.md). Append a row rather than replacing an
earlier result, even when the same test fails twice. Do not count sessions from
different jars as cumulative acceptance of one build.

For each session record:

- Manual date/tester, exact installed jar/version/hash, source commit and dirty
  inputs, singleplayer/dedicated, player count, operator status and disposable-world type.
- Link to the preserved completed checklist. Include attempted test IDs and
  passed/failed/untested counts, with unavailable prerequisites and blocked tests.
- Observable defects and evidence. Separate confirmed symptoms from suspected causes.
- Relevant repair commit and automated evidence, followed by a **separate manual
  retest date/result** for previously failed IDs and affected regressions.

When actual results arrive, replace the pending total summary with a clearly
dated per-build summary derived from those attempts; preserve the original
prepared rows and all failures. A later automated pass is not a successful manual
retest. A new implementation gets its own pending inventory and checklist identity.
