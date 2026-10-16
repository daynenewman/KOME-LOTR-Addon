# Manual gameplay acceptance history

Only player-observed results belong in the manual counts. Keep previous attempts,
failures and later retests. Automated JUnit/Gradle/native-fixture results are
separate evidence and must not turn untested items into passes.

| Build / source | Features / session | Manual date | Passed | Failed | Untested | Significant defects | Repair / retest |
| --- | --- | --- | ---: | ---: | ---: | --- | --- |
| 1.0.10-service-g14; `5091843` + uncommitted language recovery; gameplay `4b9b92c` | Latest L01–L14: protection, counterattack, report/exercise isolation, four followers, controls, cleanup, automatic assignment, book/map | Not run; checklist prepared 2026-10-16 | 0 | 0 | 14 | No returned manual results; unknown behavior remains unaccepted | Awaiting first session; no manual retest recorded |
| Same source/build | Optional X01–X30: other missions, GUI scale, save/death/legacy, natural promotion, seat replacement, company regression, multiplayer/battle/permissions | Not run; checklist prepared 2026-10-16 | 0 | 0 | 30 | Prerequisites may be unavailable; no returned manual defects | Awaiting selected optional sessions |

The essential file labels tests 01–14; history uses **L01–L14** to distinguish
them from extended X01–X30. Current pending total: **0 passed, 0 failed, 44 untested**.
This is a pending inventory, not a completed acceptance run.

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

No completed human Yes/No checklist for this current source was found during this
setup. This does not erase or invalidate older evidence retained elsewhere.

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
