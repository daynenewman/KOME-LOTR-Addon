# KOM-54 / dev reconciliation and merge readiness

Recorded: 2026-09-17. This is a decision record and release checklist, not a new
full source audit or a claim of successful multiplayer validation.

## How the integration was decided

The integration used the newer GitHub dev baseline, including Lije's merged
work, then adapted the required behavior from the reviewed KOM-54 branch.
It did not choose an author's entire implementation, use blanket ours/theirs
resolution, or cherry-pick the four original KOM-54 commits wholesale.

Decision authority was Draft 0.4, later explicit user/Linear decisions supplied
for this work, and existing implementations as reusable material. Being newer
did not automatically make an implementation correct. Where responsibilities
overlapped, the goal was one authoritative service and persisted representation,
not two engines operating together.

Historical references:

- Common original base: `238906c930088518844b1b0f221a5333569f3519`.
- Reviewed original KOM-54 HEAD: `a50428fe6a3d043ec40e571dcd880aa2c05e1e52`.
- Integration dev baseline: `b1842a3e36cd2a22d4fdf2f8569f02103b1851c1`.
- Completed integration HEAD: `cd9af14a9533bebb0bda9e8d32f914cac81dad9f`.

The original four commits remained behavioral/source references. Checkpoints
A-G are seven new commits on top of the dev baseline; they are not merge commits.
The review/correction/approval workflow is recorded in the task history. This
document does not attribute every dev line to a particular teammate.

## Concrete reconciliation decisions

| Responsibility | Retained from newer dev | Integrated KOM-54 behavior / final decision |
| --- | --- | --- |
| World data | Diplomacy, rulers, wars/seasons/inactivity/bonds, progression, waypoints, companies, movement, stewardship and audit state | Integrated schema and START-tick initialization; strict canonical parsing, isolated candidate loading and failure write blocking, without replacing WorldData with the old Slice A file |
| Population | Existing gameplay callers and provenance where still meaningful | One faction-wide long centi bank; no authoritative player/tile/allocation bank; derived Active and represented totals |
| Unit costs | KOMEUnitPopulationCostService, entity overrides, mounted surcharge and high-water model | Whole-unit populationSpent remains investment history; checked centi debit at bank boundary; farmhands zero; no lifecycle refunds |
| Configuration | KOMEConfigRegistry and its broader settings/guards | Exact centi-hours, basis-point multiplier and centi cap; no competing standalone configuration owner |
| Builds | Contribution review, server-authoritative manager auto-approval, foreign construction/ruler permissions and central audit | Explicit NORMAL/DEFENSIVE, precise centi-hours, strict schemas, idempotent review and separate bounded lifecycle history; Defensive produces zero population |
| Payout | Existing season gate and current world/config state | Exact rate aggregation and centi payout, persisted boundary/remainders, rollback-protected publication; population-only catch-up |
| Movement | Access-loss recovery, legal retreat and stewardship restrictions | Initial route allowance and successful-departure consumption; no retry/resume/restart refill; live movement/ownership before payout, no offline movement replay |
| Stewardship / pledge / transfer | Native identity, authorization, supporting controller, war membership, safe return, tombstones and cleanup | Funding uses canonical native bank; cleanup/transfer/delegation do not refund or charge an already-funded unit again |
| Networking / UI | Existing retained packet responsibilities and unrelated mod channels | START-tick server queue, unique handler classes, exact projections, client-thread publication and explicit protocol incompatibility |
| Legacy cleanup | Tactical composition, historical provenance and necessary recovery metadata | Delete obsolete bank models, mutation paths and wire slots; reserve removed IDs rather than repurposing them |

Active Population follows retained canonical combat-record membership and stable
funding attribution, not whether an entity happens to be loaded. Terminal
lifecycle cleanup removes its contribution without crediting Available.

Character Creation, LOTRMoreMobs, Aqua Acrobatics, the coremod/access-transformer
chain, gear restrictions and unrelated notifications were preservation targets,
not replacements. Preservation intent and automated coverage are not proof that
every combined modpack behavior has passed a live test.

## Checkpoint history

| Checkpoint | Commit | Main responsibility |
| --- | --- | --- |
| A | d8e283b | Integrated schema, initialization and server-thread packet foundation |
| B | f359946 | Canonical centi faction bank and permanent spending |
| C | 9d5c8b2 | Exact population configuration within the dev registry |
| D | 3eeaa5e | Exact centi-hour Build lifecycle |
| E | 07a8a9e | Exact daily payout lifecycle |
| F | d087ffb | Canonical projections and protocol |
| G | cd9af14 | Legacy population retirement and atomic load validation |

Detailed implementation records:

- [Configuration and payouts](KOME_POPULATION_CONFIGURATION.md)
- [Precise Builds](KOME_PRECISE_BUILDS.md)
- [Population projections](KOME_POPULATION_PROJECTIONS.md)
- [Legacy retirement and persistence](KOME_LEGACY_POPULATION_RETIREMENT.md)

Those documents contain checkpoint-specific historical wording; their deferred
work statements must be read against the later checkpoints, not as current gaps.
KOM-71 bottleneck/Rate Ceiling, defensive segments and gate-health implementation
were not added by this integration.

## Compatibility and deployment requirements

- Integrated root schema is 3; unsupported older development worlds fail closed.
  Back up existing worlds and test on a fresh world. Do not promise migration or
  delete a user's world as part of a merge/deployment.
- Current protocol is `1.0.8-integration-g1`. Client and server must use matching
  artifacts; old packet formats are not supported.
- Build/faction nested schemas remain distinct from root and network versions.
- The protected stash is not part of the integrated implementation and must not
  be applied implicitly.

## Aqua fix is a separate follow-up

The subsequent startup investigation found production-name/bytecode defects in
bundled Aqua transformers. It is not a new population reconciliation or Git
conflict resolution. The current work is on `dayne/fix-aqua-production-mappings`,
based on cd9af14, with uncommitted source/tests and the mapping audit.

See [Aqua mapping evidence and tests](KOME_AQUA_PRODUCTION_MAPPINGS.md).
That report records 17 focused tests and a successful clean build with 669 tests
discovered, 667 passed and two environment-conditional skips. Production-JAR and
offline ASM checks also passed. These are prior recorded runs, not reruns made
for this documentation task, and do not exercise a live modpack handshake/world.

The fixed JAR was subsequently deployed, with a backup, to the local
`Lord of the Rings DEV` instance. The user reported successful startup, fresh-world
loading, save/reload, a full client restart, swimming, crawling and restoration of
both visible arms using that artifact. Its source remains uncommitted.

## Readiness snapshot and required gates

Read-only GitHub ls-remote verification on 2026-09-17 returned:

- `dev`: `b1842a3e36cd2a22d4fdf2f8569f02103b1851c1`.
- `dayne/kom-54-dev-integration`: `cd9af14a9533bebb0bda9e8d32f914cac81dad9f`.

The completed A-G branch is already pushed and is seven commits ahead, zero
behind that dev baseline. Current working HEAD is also cd9af14 but on the Aqua
fix branch with unstaged changes. Index is empty. Protected stash identity is
`eb8f04e9845a521d918b2495b35bfd1b37974388`.

**Recommendation: do not assume ready to merge into dev.** No current remote
divergence was found, but clean ancestry is not a runtime acceptance test.

1. Test a matching dedicated server/client pair: connection, fresh-world startup,
   server save/restart and mismatched-client rejection.
2. Complete boat-specific validation and a full gameplay smoke test covering
   population hire/death/dismissal/farmhands, Normal/Defensive approval,
   capture/payout, movement and preserved diplomacy/ruler/stewardship/Character
   Creation behavior.
3. Review the exact Aqua diff and approve a separate fix commit. Validate the
   final candidate tree with `./gradlew.bat clean test build --no-daemon --console=plain`
   (PowerShell: `.\gradlew.bat clean test build --no-daemon --console=plain`).
   Require a clean index/tree after the authorized commit.
4. With explicit authorization, publish the reviewed fix and bring it into the
   integration candidate without rewriting the reviewed A-G history. Recheck live
   dev divergence before selecting the Git integration operation.
5. Review the complete candidate versus current dev, communicate schema reset
   and matching-artifact requirements, then obtain merge approval. If dev moves,
   inspect the new changes and rerun affected regression checks first.

Pushing a reviewed branch for collaboration and merging it into shared dev are
separate approvals. Merge into `dev` is not yet approved. This documentation task
does not push or merge. No new full audit or test run was performed here.
