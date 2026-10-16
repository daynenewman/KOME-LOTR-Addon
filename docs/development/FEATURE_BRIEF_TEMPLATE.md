# Feature brief: [one bounded outcome]

**Status:** Proposed / Approved / Implementing / Needs live testing / Accepted / Blocked
**Approved by/date:** [owner and date; approval authorizes only this brief]
**Outcome:** [what the user/player can do and see; 2-4 sentences]

## Scope

- Included: [one complete feature or tightly connected infrastructure fix]
- Excluded: [adjacent roadmap items and unrelated refactors]
- Existing systems/routes: [agent verifies against current code before editing]
- Compatibility: [authority, old saves, wire contracts, permissions and UI constraints]
- Explicit operational authorization: local verified commits; no push, deployment,
  original-world writes, global machine changes or external messages by default.

## Acceptance and verification

- [ ] Happy path and authoritative state behave as specified.
- [ ] Applicable failure/death/relog/restart/stale-token and duplicate-reward cases.
- [ ] Appropriate focused selectors: [route/tests; agent may add justified coverage].
- [ ] For code/gameplay, Full `scripts/kome-validate.ps1 -Mode Full` passes; skips
  reported separately. Documentation-only work uses link/format/source checks.
- [ ] User-visible wording/layout follows actual state; relevant GUI scales reviewed.
- [ ] After the verified gameplay checkpoint, generate source-based latest/extended
  Yes/No checklists and a pending history entry using `docs/testing/README.md`
  (repository-root path, also valid when this template is copied under briefs/).
  Specify exact disposable setup and expected observations; preserve prior answers.
- [ ] Evidence distinguishes implemented code, automated tests and actual acceptance.

**Open decisions requiring owner approval:** [None, or exact unresolved decisions.]
**Technical discretion:** investigate and repair ordinary issues within this scope.
**Handoff:** tested checkpoint, inherited dirty work, commits, counts/logs/skips,
remaining acceptance, blockers and one next action. Stop after this brief.
