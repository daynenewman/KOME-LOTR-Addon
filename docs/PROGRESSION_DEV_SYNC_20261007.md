# Progression/dev synchronization verification - 2026-10-07

## Preserved progression work

Original and continuing branch:
`elijahrnovak/kom-72-implement-revised-branch-based-player-progression-system`.
Initial HEAD and previous shared dev baseline: `f4f9530`.
Checkpoint: `29e0a03` (149 relevant source, test, resource and report files).

Retained local backup references:

- `backup/progression-sync-20261007-195559`: initial committed state.
- `backup/progression-sync-20261007-195559-checkpoint`: all current progression work.
- The pre-existing `backup/progression-before-dev-merge` remains intact.

Included the October 3 UX, October 5 hierarchy and October 6/7 gameplay reports.
Preserved character creation pause/resume and appearance/alignment initialization;
Master pledge, service, courier, optional gifts and promotion; persistent Liege
relationships, direct Quest interaction, commissions and Lordship trials; NPC
authority, names and ruler lifecycle; verified existing escort shelters, following
without teleport, moving markers and Show Me; faction lost items, handover,
bandit pressure, safe encounters and border geography; book/tracker/dialogue,
small secondary rewards and quest persistence/recovery.

No gameplay redesign was introduced in the synchronization pass. Unsupported
refuge generation remains disabled as documented in the gameplay report.
Generated JARs, build outputs, runtime files, caches and local dependencies were
excluded from commits. The UX report's trailing blank EOF line was normalized
to satisfy the staged whitespace check.

## Incoming contributor work

Fetched with `git fetch origin --prune`. Incoming `origin/dev`: `b826ef3`, with
45 commits since the prior synchronization. Retained the complete history:

- KOM-17 persistent tile conflict records, lifecycle, movement handoff and tools.
- KOM-25 siege geometry, deployment areas, tactical persistence, editors and locks.
- KOM-29 live-state faction defeat detection and muster/campaign ordering.
- KOM-40 bounded admin diagnostics and guarded repair plans.
- KOM-47 persistent daily movement allowance, HP preservation, route confirmation,
  arrival placement and movement diagnostics.
- KOM-75 conflict-scoped emergency defense and autonomous defender authority.
- KOM-80 mountain barriers, reviewed geography and queued route revalidation.

## Four conflict resolutions and compatibility

1. `KOMEEvents.java`: retain emergency-defense entity reconciliation first, with
   its existing early return for managed defenders. Ordinary NPCs then receive
   ruler singleton/incarnation reconciliation and progression rank migration.
   Preserve both campaign tick ordering and progression item/escort/ruler hooks.
2. `KOMEPacketHandler.java`: preserve published dev tactical IDs 48-51. Append the
   previously unpublished MasterOfferResponse at ID 52, retaining its server-thread
   wrapper. Other IDs and retired holes remain unchanged.
3. `KOMEPacketRegistrationTest.java`: assert every retained tactical registration
   plus the new response at 52, uniqueness, ordering, side and handler identity.
   Expected totals: 47 packets, 20 wrapped server handlers plus the tactical
   request's separate bounded queue.
4. `CharacterCreationIsolationTest.java`: retain progression NPC-only teleport/home
   metadata checks and dev's emergency-defense NPC-marker allowance, while keeping
   the prohibition on KOME-owned character creation config/player storage.

Reviewed the automatic merges in the client proxy, commands and world data.
Progression shelter/rank/relationship data remains additive beside dev schema 11
conflict, tactical, movement and emergency-defense persistence. No duplicate client
registrations or replacement coremod implementations were introduced.

Added `progressionAndIncomingConflictAuthoritiesSurviveTheSameWorldSave`, exercising
real observed shelter data, NPC Prince authority and active conflict identity and
revision through the same initialized world save/load/resave transaction.

## Verification

Environment: existing Gradle wrapper 8.5, cached user Gradle dependencies, Java 17
runner and repository Java 8 compilation/toolchain. No build configuration changes.

Focused run: `gradlew.bat --offline test --console=plain` with filters for progression,
character creation/alignment, Serf/Master, courier, commissions, Lordship, rulers,
visuals, packets, transformers, world data, conquest, recruitment, muster, tactical,
emergency defense, conflict, movement, waypoints, geography and hired units.

- 1,756 total: **1,753 passed, 0 failed, 3 skipped**, 207 suites.

Final full run: `gradlew.bat --offline test build --console=plain`.

- 2,819 total: **2,814 passed, 0 failed, 5 skipped**, 284 suites.
- **BUILD SUCCESSFUL**, exit 0; 18 actionable tasks, 3 executed, 15 up-to-date.
- JAR, sources and reobfuscation completed through the ordinary build.
- `git diff --check` and staged whitespace checks passed.

An earlier full run had one failure in the newly added test's initial fixture:
the fixture saved an uninitialized world, whose reload correctly created automatic
tile waypoints. Corrected the test to save initialized canonical world data; retained
the full NBT equality assertion. Existing tests were not weakened or disabled.

Existing conditional skips:

- `CustomSkinLibraryFoundationTest.symbolicLinkSkinIsRejectedWhenSupported`.
- `ClientCustomSkinCacheTest.symbolicLinkAtHashPathIsNeverAcceptedWhenSupported`.
- `KOMEPublicWaypointTransformerTest.originalProductionAndBuildJarsHaveExactCompatibleHooks`.
- `KOMEWaypointTransformerTest.configuredProductionV3615JarHasVerifiedClassFingerprintAndTransforms`.
- `KOMEWaypointTransformerTest.configuredBuildDependencyV3615JarHasVerifiedClassFingerprintAndTransforms`.

The first two require platform symbolic-link support/permission; the last three
require optional external production/build JAR inputs. Dependency-bytecode
transformer tests still execute against the available LOTR classes.

Automated source/JVM/bytecode validation is complete. No Minecraft client, dedicated
server or multiplayer session was launched. Live rendering, pathfinding, restart
timing and third-party mod combinations retain the documented gameplay-report
limitations. Client and server should use the same combined build, including the
updated visual-marker payload and the complete packet registry.

Ignored local verification evidence: `build/progression-sync-focused.log`,
`build/progression-sync-focused-counts.json`, `build/progression-sync-full-build.log`
and `build/progression-sync-full-counts.json`.

## Stash preservation

All original stash objects remain untouched:

- `61f81972fe98afe0497ddd376d571ebb019883ad` - local artifacts before prior sync.
- `1f7c28e4ab11eb929b64d247de5800048fc9b34a` - KOM-40 handoff.
- `7d9c99bc5cfa9d5bd96dbe81fe4203449d007221` - waypoint transformer work.

No stashes were applied, popped or dropped. No history rewrites, force pushes,
branch deletions, resets or cleanup commands were used.
