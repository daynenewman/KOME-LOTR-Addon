# Feature brief: clearly label staff commission exercises

**Status:** Proposed
**Approved by/date:** Not approved; recommendation only. Do not implement during setup.
**Outcome:** An operator testing a Knight commission sees a clear staff-exercise
notice on the existing Rank page, including that no earned credit or rewards are
granted. It remains visible while the persisted testing session is active and
disappears after the operator ends it.

## Scope

- Reuse `KOMEKnightServiceRecord.testing`, existing server-authored Rank summary
  text, and `/kome progression commission` lifecycle. Inspect the progression and
  client/network routes first; current code is authoritative.
- Include active, report-ready and empty-but-still-testing states, plus reload.
- Exclude new missions, commands, reward/rank rules, military rights, save schemas,
  packet IDs, universal UI redesign and unrelated progression cleanup.

## Proposed acceptance

- [ ] Exercise notice accurately reflects the server's saved testing flag.
- [ ] Normal earned service has no exercise notice or changed behavior.
- [ ] Ending an exercise clears the notice after the existing synchronization.
- [ ] Existing operator authorization and non-qualifying reward safeguards remain.
- [ ] Summary, lifecycle, reload and network regressions pass; Full passes.
- [ ] A disposable client verifies native Rank-page readability at relevant GUI scales.

Approve the wording and bounded outcome in a new session before work starts.
First review the separate uncommitted language integration repair identified in
[current status](../CURRENT_STATUS.md), so a new feature does not absorb it.
