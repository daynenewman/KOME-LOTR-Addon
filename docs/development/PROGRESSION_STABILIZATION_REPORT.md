# Overnight progression stabilization

Approved scope: the user's October 16 multi-stage progression stabilization brief.
Priorities run sequentially; larger gameplay suggestions remain design backlog.

Starting HEAD `fc60f538561140d5999094b28798fa63a7640828`; existing progression branch,
50 modified tracked files, two untracked files, empty index, one worktree and three
stashes. Inherited language recovery is part of tested source but is not swept into
these commits. Verified pre-edit backup (6,768 hashed files, exit 0, COMPLETE.json):
`outputs/overnight-backups/20261016-233750-020-94f2c944`.

## Priority 1 checkpoint

`1ca850d` adds Master offers to the existing Quest control in native trader,
smith and farmer interaction menus. Existing Talk, Trade, Exchange, Smith and Hire
controls remain. The same server validator now governs offer eligibility and
acceptance, including native friendliness/trading requirements. Opening checks
distance and world; acceptance retains the existing offer UUID/pledge revision.
No packet IDs, rank thresholds or civilian eligibility rules change.

Confirmed causes: offer discovery omitted hostility/trade requirements checked by
acceptance; the KOME Quest overlay excluded ordinary traders and only published
Liege eligibility. Native trader Talk can reach the ordinary quest hook, but no
explicit Master Quest entry was exposed. This is not evidence that every reported
NPC failure shares one cause. Native pledge-to-Serf is already correctly wired.
Native Orc/Uruk/Elf factories exist through inheritance; no speculative factory
replacement was needed. Master admission uses territory, not generated-seat proof.
Fangorn/Half-troll and ordinary combat soldiers remain unsupported Master roles.

Baseline Fast: 38 tests passed (six suites), no failures/errors/skips.
Focused Fast: 27 tests passed (five suites), no failures/errors/skips, evidence
`outputs/overnight-validation/20261016-234250-893-Fast-4df774f2`.
New coverage exercises eight real native NPC classes across civilian, native-home
and artificial-spawn states (24 combinations), pledge recognition, eligibility,
offer opening/acceptance, duty issuance, denials and nonoverlapping native controls.
Offers are controlled serialized shells; this does not establish random native
offer generation, generated-world density, connected client or multiplayer results.

Initial sandboxed Gradle attempt failed before compilation because the global
cache lock was unwritable. Authorized validation through the existing cache passed;
this was an environment restriction, not an inherited code failure.

At this Priority 1 checkpoint, subsequent stages and integrated Full were pending; see the final handoff below.

## Resumed October 17 / Priority 2 checkpoint

Fresh verified backup: `outputs/overnight-backups/20261017-091558-351-aef34bf4`,
6,793 hashed files, exit 0 and COMPLETE.json. Two earlier backup attempts failed
because the interrupted October 16 run overlapped source/index changes; neither
failed snapshot is a recovery point. Fresh baseline: 50 passed, six suites, no
failures/errors/skips (`20261017-091848-854-Fast-2b701318`).

`cd07aef` completes the unfinished Master history change present on resume and
repairs Recovery possession/placement. Native Forge fires EntityItemPickupEvent
before inventory insertion; a rejected pickup previously set RecoveryRetrieved
and removed the search marker. Retrieval now requires actual assigned inventory
possession. Activation, observation and handover also check the saved issuer.
Leaving or losing a Master cancels ordinary physical objectives. Voluntary departure
archives completed service separately from the next Master's credits; per-duty
issuers and departed history round-trip through additive NBT fields. Legacy duties
without issuer evidence remain unknown rather than being credited to a new issuer.
Existing completed credits after ordinary Master death and legacy Liege trials
retain their previous rules. No new switching cooldown was added.

Recovery sites require a supported, clear 3x3 approach, reject leaves/cactus and
collision, and suppress the item's initial sideways motion. The existing approximate
map offset remains 24-39 blocks; the marker now explains the 40-block search area.
Known recorded objects are not duplicated after disappearance: the existing bounded
loaded-site missing check ends the duty recoverably. No distant chunks are loaded.
This does not prove the cause of the original missing-item report or guarantee a
walkable route through every native terrain configuration.

Regression reproduction: premature pickup and missing history reload failed before
repair (`20261017-092055-283-Fast-ad1d49b3`); an additional fixture-position error
was repaired without changing assertions. Final focused Fast: **104 passed**, 12
suites, no failures/errors/skips, evidence
`outputs/overnight-validation/20261017-092407-059-Fast-510fce6e`.
Coverage includes physical creation, rejected spawn, pickup rejection/possession,
identity, duplicate load rejection, reload, correct handover, missing-object timeout,
surface rejection, every ordinary stage on departure, death and legacy regressions.
Minecraft acceptance remains pending. Priority 3 and final Full follow this checkpoint.

## Priority 3 checkpoint

`be12f0f` uses the existing measured rank-page path for Knight service too. The
separate Knight block repeated relationship/faction context and used different
height calculations. Current standing is highlighted once in the ladder; Wanderer
retains its body label. Current work, promotion rows and privileges keep their
existing parchment styling and divider. Idle objectives no longer recite the
same quotas as the promotion rows. Promotion headings wrap; text height, scroll
extent and expandable-row input share the same measurements. The footer reserves
space for Show Me even without a relationship-departure button. Native tabs,
tooltips, controls, packets and server eligibility remain.

Focused Fast: **31 passed**, 10 suites, no failures/errors/skips; evidence
`outputs/overnight-validation/20261017-092905-474-Fast-37e499ef`.
The actual screen's measured content, bottom scroll, Show Me-only footer and
expanded-row hit testing run with a controlled font and long name/objective.
Other tests cover faction-rank labels, active projections, quotas, packet and map
focus. A short fixture initially did not overflow; it was extended to exercise
the real overflow case. No visible connected-client/render acceptance is claimed.

## Priority 4 investigation (no generation change)

Current personal progression does not generate buildings. `KOMEProgressionDestinations`
observes existing roofs/walls and fails closed when no verified shelter exists;
`KOMEProgressionSeatTransformer` decorates existing native NPC spawn calls with
provenance. Commission encounters spawn temporary actors on validated terrain.
No progression call to a native building generator or block-writing operation was
found. This rules out a new building placement by these current progression paths;
it cannot identify the reported screenshot's origin without its world/location.

Read-only `javap -c -p` inspection of the shipped LOTR v36.15 jar shows native
`LOTRWorldGenRohanWatchtower.generateWithSetRotation` conditionally checking
surface suitability over -4..4 X/Z when `restrictions` is enabled, then placing
supports/building blocks. The base `setBlockAndMetadata` rotates coordinates and
calls the native WorldGenerator setter; that is not a general ownership/occupied
volume transaction. Native per-generator terrain checks must not be treated as
proof of protection for player construction. Evidence:
`outputs/stabilization/native-rohan-watchtower-bytecode.txt` and
`native-structure-base-bytecode.txt` in the same directory.

Cause remains unconfirmed. Next investigation needs dimension/coordinates,
structure identity and a disposable stopped copy with generation history where
available. Compare the same seed/native configuration with and without the addon;
inspect generation entry/caller and restriction flag before any change. A future
addon structure feature should preflight its full footprint, foundations, vertical
clearance, liquid/hazard/entity occupancy, tile entities and protected/claimed
space before any block write, reject unknown/unloaded sites, and test rejection
without world mutation. Native-wide safeguards require a separate reviewed design.
No valuable saves were opened and no generation rewrite was attempted.

## Priority 5 design backlog

See [the prioritized future backlog](PROGRESSION_BACKLOG.md). It records dependencies,
balancing and implementation order for every requested future improvement. Some
physical rewards, destination variety and rank-aware staff operations already
exist; their remaining work is identified rather than duplicated. These designs
were not implemented in this assignment.

## Final integrated verification / morning handoff

Final executable checkpoint: **39f19a9**, following **1ca850d**, **cd07aef** and
**be12f0f**, with the inherited language recovery still unstaged. `39f19a9` retains
the established report wording and updates the Recovery subtitle's exact regression
expectation without removing its distance or retrieval assertions.

The first integrated Full run (`20261017-093138-877-Full-6621e63b`) found two
presentation assertion regressions: a capitalized report instruction and the newly
nonblank search subtitle. Both were introduced by this assignment and repaired.
Focused correction: **56 passed**, nine suites, no skips/failures/errors, evidence
`outputs/overnight-validation/20261017-093313-130-Fast-388e654b`.

Final `scripts/kome-validate.ps1 -Mode Full` ran `test build reobfJar --no-daemon
--max-workers=2 --console=plain --offline` under the shared mutex: **PASS**, exit 0,
**4,169 passed / five skipped / zero failures/errors**, 401 suites, 4,174 total.
Evidence: `outputs/overnight-validation/20261017-093401-380-Full-96bbeab9/`.
Before/after full-input SHA-256 matched
`fbe0440249c53d3c041d00492e90ffe478555c890fc52daca987b189d1618af2`.
The five existing skips are two Windows symlink fixtures and three optional
external original/build waypoint-jar checks; they are not passes. Build and
reobfuscation completed; no Forge/client/world runtime was launched this session.

Artifact: `build/libs/KOME-LOTR-Addon-1.0.10-service-g14.jar`, **18,639,829 bytes**,
SHA-256 `64a9d95c8b89a376d899c0fb77b39e2cc11b7a5598630dbccb2b02d5d66b7dd3`.
Built, not installed. Existing g14 jars can have different hashes. HEAD alone
cannot reproduce this jar without the inherited source inputs recorded by validation.

Changed production entry points: `KOMELiegeQuestButtonOverlay`,
`KOMEProgressionEligibility`, `KOMEProgressionOfferBridge`, `KOMESerfdomMasterService`,
`KOMEPacketRelationshipAction`, `KOMESerfKnightProgression`,
`KOMESerfKnightRecoveryService`, `KOMEGuiProgression`, `KOMEProgressionRankLayout`
and `KOMEProgressionRankSummary`. Tests added/updated:
`KOMEMasterRecruitmentCompatibilityTest`, `KOMELiegeQuestButtonOverlayTest`,
`KOMEMasterRecoveryReliabilityTest`, `KOMEProgressionBookLayoutTest`,
`KOMEProgressionRankLayoutTest`, `KOMEProgressionBookReadabilityTest` and
`KOMEVisualLocationServiceTest`. Progression/client routing, current status, manual
checklists/history, this report and the future backlog are updated separately.

Preservation comparison: **2,228 pre-existing files** outside the explicit change
list match the fresh backup byte-for-byte. Original worktree, branch and three
stashes are retained; no push, PR, runtime installation, original world write or
security change. Inherited wording hunks were excluded from overlapping source
commits; unfinished history work from the interrupted approved assignment was
completed and verified in `cd07aef`.

Next action: use [the 14-question checklist](../testing/PLAYTEST_LATEST.md), grouping
recruitment and one naturally assigned Recovery before a separate leave/rejoin
profile, then Knight regression/book checks. All new answers are unselected.
The user-reported **11/2/2** summary and successful patrol observation are preserved
without invented individual answers or runtime identity. Natural-world availability,
actual lost-item visibility/pathfinding, connected-client appearance, earned
promotion and multiplayer remain live acceptance. The original item's exact loss
cause and screenshot structure origin remain unresolved. No future cooldown,
larger encounter redesign, reward rebalance or other backlog mechanic was introduced.

Documentation QA: Python was unavailable on PATH, so an equivalent read-only
PowerShell routing/link/symbol/selector/discoverability checker also included the
handoff and testing files: **32 documents, 866 local links, 1,130 symbol references,
48 exact JUnit selectors**, zero issues; all **14 latest Result fields blank**.
Evidence/script: `outputs/stabilization/documentation-checks.json` and
`outputs/stabilization/verify-docs.ps1`. `git diff --check` passed. No Gradle rerun
was needed for the subsequent documentation-only edits.
