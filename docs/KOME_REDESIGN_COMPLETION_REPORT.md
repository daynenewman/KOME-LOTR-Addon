# KOME redesign completion report

Final verification date: 2026-07-26.

## Outcome

The full Build, split-population, automatic-company, directional-alliance, and affected-GUI redesign is implemented in the KOME addon. No base LOTR source or jar was modified.

- Alliance schema: **7**
- Build schema: **1**
- Population schema: **2**
- Addon version: **1.0.7**

## Delivered behavior

- Persistent Builds with exact coordinates, stable IDs, selectable population owner, strict typed whole/half-hour input, configured-rate preview, pending/approved/removed contribution audit, manager succession, map markers, normal deletion, restricted enemy destruction, and committed-capacity protection.
- Separate Base/native and Build population by tile/source faction with controller-owned 100% and foreign 50% effective access. Player-facing text says Base Population while the compatibility fields remain unchanged.
- Builds-first Tile Command with a responsive creation grid, grouped Build detail, one context-aware Destroy Build action, faction-pool population graphs, an Allocations tab, and complete normal GUI workflows.
- One auto-created/reused company per owner/source tile; immutable source identity and owner rename action.
- One formal relationship containing two independent Stage 0-4 directions, lower-stage shared relation, king/kingless request rules, one authoritative ledger, Stage 3 Build-hours milestone, and Stage 4 qualifying deployment/delegation/stewardship.
- No runtime grace downgrade and no alliance-related waypoint restriction.
- Typed schema-7 alliance records and updated Server Records.
- Armed allied/hostile claim confirmation is initialized at screen level and remains visible on the default Builds tab.

## Migration

Schema 6 and older alliances migrate each faction direction independently to the highest stage supported by a clearly earned corresponding benefit. Ambiguous data uses the lower safe stage; directions and ledger goods are never merged. A clearly earned merchant entitlement is retained separately.

Build schema 1 initializes a clean empty collection for old worlds. Population schema 2 converts legacy tile totals to native source-faction pools using saved source, then controller, then legacy faction as fallback. It never invents Builds. Existing allocations, living units, funding provenance, companies, and wars are reconciled after all source records load.

Obsolete grace and alliance-waypoint fields are ignored and removed on the next save. Legacy internal tokens used by existing NBT/quota/recovery records remain compatibility identifiers only.

## Production source changes

### Added

- `kome.client.gui.KOMEGuiAllianceUnified`
- `kome.common.command.KOMECommandBuild`
- `kome.common.data.KOMEAllianceStageProgress`
- `kome.common.data.KOMEBuildContribution`
- `kome.common.data.KOMEBuildPopulationService`
- `kome.common.data.KOMEBuildService`
- `kome.common.data.KOMEPopulationGraph`
- `kome.common.data.KOMEPlayerBuild`
- `kome.common.network.KOMEPacketBuildAction`

### Modified

- Client/routing: `KOMEClientProxy`, `KOMEConquestMapOverlay`, `KOMEProgressionMenuOverlay`, `KOMEWaypointMapOverlay`
- GUI/presentation: `KOMEGuiAllianceLedger`, `KOMEGuiCompanyList`, `KOMEGuiConquestCapture`, `KOMEGuiServerRecords`, `KOMEGuiVisualCaptureController`, `KOMEServerRecordPresentation`
- Commands: `KOMECommandAlliance`, `KOMECommandTroops`, `KOMECommandWar`
- Core/data: `KOMEAddon`, `KOMEAlliance`, `KOMEAllianceAuthority`, `KOMEAllianceFactionLedger`, `KOMEAllianceInventory`, `KOMEAllianceProgressionService`, `KOMEAllianceRecordBuilder`, `KOMEAllianceTemporaryCommandPolicy`, `KOMEArmyCompany`, `KOMEClientData`, `KOMEEvents`, `KOMEHiredUnitRecord`, `KOMEPledgeReleaseService`, `KOMEServerRecordBuilder`, `KOMETilePopulation`, `KOMEWar`, `KOMEWarService`, `KOMEWartimeStewardshipService`, `KOMEWaypointAccessService`, `KOMEWorldData`
- Network: `KOMEPacketAllianceAction`, `KOMEPacketConquestCaptureGui`, `KOMEPacketConquestData`, `KOMEPacketConquestOpenCapture`, `KOMEPacketHandler`, `KOMEPacketTroopGuiAction`

### Removed

- `KOMEGuiAlliance`
- `KOMEGuiAllianceDetail`
- `KOMEGuiAlliancePermissions`
- `KOMEAlliancePermissions`
- `KOMEAllianceBenefits`
- `KOMEAllianceGraceService`

## Automated verification

Final command:

```powershell
.\gradlew clean test build --no-daemon --console=plain
```

Result: **BUILD SUCCESSFUL**, 25 actionable tasks (24 executed, 1 up-to-date).

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `KOMEGuiScrollPanelTest` | 4 | 0 | 0 | 0 |
| `KOMEGuiServerRecordsPresentationTest` | 7 | 0 | 0 | 0 |
| `KOMEAllianceModelTest` | 21 | 0 | 0 | 0 |
| `KOMEAllianceSystemsTest` | 49 | 0 | 0 | 0 |
| `KOMEBaseIsolationTest` | 5 | 0 | 0 | 0 |
| `KOMERedesignSystemsTest` | 63 | 0 | 0 | 0 |
| `KOMEWaypointTransformerTest` | 5 | 0 | 0 | 0 |
| **Total** | **154** | **0** | **0** | **0** |

Coverage includes Build placement/owner anti-exploit, strict typed whole/half-hour parsing, configured conversion, safe button bounds, manager/hostile-king destroy preflights and denial reasons, approval/removal/deletion/destruction/succession/persistence, committed-capacity safety, Base/native-versus-Build source separation, segmented graph math and overflow handling, capture/reclaim/reset, exact funding return, auto-company reuse/rename/away fallback/transfer, directional migration/stages/break, kingless behavior, Stage 3 reversal boundaries, strict Stage 4 time/unit/pledge/territory/once-only rules, war cleanup, records, scrolling, transformer structure, and addon/base isolation.

## GUI evidence

The property-gated client loaded KOME and LOTR, registered the waypoint transformer, injected `LOTRPlayerData.receiveFTBouncePacket`, rendered 14 deterministic screens, and shut down cleanly at each setting:

| Setting | Resolution | Screens |
|---|---:|---:|
| Small | 1280x720 | 14 |
| Normal | 1280x720 | 14 |
| Large | 1280x720 | 14 |
| Auto | 1600x900 | 14 |
| Smallest supported | 854x480 | 14 |
| **Total** | | **70** |

The linked evidence matrix is `gui-scale-verification/README.md`. It now includes dedicated Tile Command Build creation, Build detail, and Population captures at every setting. The responsive logical transform keeps drawing, text fields, buttons, dialogs/tooltips, mouse hitboxes, and scissoring aligned at 854x480. The allied/hostile claim spot-check still shows full consequences and reachable Confirm/Cancel over the Builds tab.

The screenshots are visual regression evidence and do not validate real text-field focus/input, destructive packet outcomes, or scrolling through every live-data row. The ledger image is a visual twin and does not validate the real authoritative container's slots, dragging, shift-click, or hitboxes. Those remain manual tests.

## Artifact and deployment

Release/reobfuscated addon:

```text
C:\Users\dayne\OneDrive\Desktop\The-Lord-of-the-Rings-main\KOME-LOTR-Addon\build\libs\KOME-LOTR-Addon-1.0.7.jar
Size: 11,656,697 bytes
SHA-256: FB4BE9ACE4ACE14C841A1DB1E29C9D8EA47E374B455CB0A38A274664137596EA
```

Deployed DEV copy:

```text
C:\Users\dayne\curseforge\minecraft\Instances\Lord of the Rings DEV\mods\KOME-LOTR-Addon-1.0.7.jar
Size: 11,656,697 bytes
SHA-256: FB4BE9ACE4ACE14C841A1DB1E29C9D8EA47E374B455CB0A38A274664137596EA
```

The DEV instance's untouched production LOTR artifact was hashed immediately before and after addon deployment:

```text
C:\Users\dayne\curseforge\minecraft\Instances\Lord of the Rings DEV\mods\LOTRMod v36.15.jar
Size: 28,936,383 bytes
SHA-256 before/after: 4F296E749C0D4739ECF859217A526B4218A2A45A768C08D3D551AF0D0D3D5635
Last-write timestamp unchanged: 2026-01-23T14:12:45.8835270-06:00
```

The adjacent `LOTR-Test-Server` E21B28... jar is a smaller development/test fixture and was neither used as the production artifact nor modified.

## Commits

- `4d07193` — persistent Builds and split-population foundations
- `cf0f9ff` — unified alliance stage workflow and GUI
- `d6cc4e7` — schema-7 and Build safety hardening
- `d692457` — current documentation and legacy archive split
- `61ab6ea` — screen-level conquest confirmation wiring
- `28e47ce` — refreshed 55-image schema-7 GUI evidence
- `a2f4219` — strict hour parsing, population graph math, and Build-destruction preflights
- `07efe3d` — responsive Tile Command Build/population visual cleanup
- `fa8f27f` — deterministic 14-screen scale-profile verification
- `12326ac` — safe half-hour button boundary clamping

No push was performed.

## Judgment calls and limitations

All judgments are expanded in `KOME_DECISION_LOG.md`. The release-relevant ones are:

- conservative per-direction legacy stage mapping;
- two-edge server validation for a foreign Build population owner;
- normal player-side spawn plus persistent association when the source-tile company is away, avoiding unsafe 1.7.10 remote chunk/entity manipulation;
- Build manager transfer to the population-owner king, otherwise preserved with no normal manager until operator repair;
- claimed Stage 3 remains unlocked after later deletion, while removed/deleted/pre-break hours cannot satisfy an unclaimed/new relationship;
- persistent, non-duplicating Stage 2 merchant entitlement without implementing a Produce economy;
- compatibility NBT tokens retained without active legacy gameplay.
- player-facing **Base Population** terminology retained the existing native backend/NBT source without a schema migration;
- the population bars reuse KOME's bordered progress-bar language but use a dedicated overflow-safe segment calculator because Allocations has no inaccessible third segment;
- one Destroy Build action chooses between the two existing server-authoritative mutation paths rather than merging their permissions.

## Manual tests still required

Automated tests and deterministic screenshots do not replace a populated multiplayer session. Complete the checklist in `KOME_TEST_PLAN.md`, especially:

- real ledger slots/hitboxes/deposit/claim/drag/shift-click;
- live map marker clicks with densely co-located Builds;
- live typed-field focus, Enter/focus-loss normalization, plus/minus stepping, and validation feedback at all GUI scales;
- live population graph hover values and scrolling through more than two faction pools;
- manager, hostile-king, denied, and committed-capacity Destroy Build confirmations and outcomes;
- multiple real players submitting/reviewing Build hours and manager succession;
- living-unit allocation safety across capture/reclaim/reset/restart;
- company-away hire appearance and later spatial convergence;
- full Stage 4 war/delegation/stewardship and war-end cleanup;
- migration of a copied production schema-6 world followed by a cold restart.
