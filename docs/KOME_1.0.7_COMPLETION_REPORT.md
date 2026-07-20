# KOME 1.0.7 completion report

Final source and verification record for the schema-5 alliance/war revision and the dedicated GUI completion pass, updated 2026-07-19.

## Completion statement

The requested GUI pass is implemented in the final source. This pass changed presentation and confirmed GUI wiring only; it did not add another alliance, war, population, quota, migration, Produce, inventory, or progression model.

The authoritative Alliance ledger remains `KOMEContainerAllianceLedger`. The visual capture harness is property-gated and is not registered during normal play.

## GUI classes changed or added

| Class | Result |
|---|---|
| `KOMEGuiTheme` | Shared dark-parchment/stone palette, panels, cards, faction badges, status chips, progress bars, warning banners, wrapping/tooltips, and scaled scissoring |
| `KOMEGuiButton` | Shared default, dark, tab, destructive, hover, selected, and disabled states |
| `KOMEGuiScrollPanel` | Added reusable clamped scrolling, scaled scissoring, and proportional scrollbar |
| `KOMEGuiConfirmationDialog` | Added reusable wrapped confirmation modal with destructive and cancel paths |
| `KOMEGuiAlliance` | Redesigned mutual-pair list, authority header, eligible-only request flow, and compact layout |
| `KOMEGuiAllianceDetail` | Redesigned Overview, Requirements, Benefits, Military, warnings, pending state, and destructive confirmation |
| `KOMEGuiAllianceLedger` | Redesigned direction/status/progress presentation around the unchanged authoritative container |
| `KOMEGuiServerRecords` | Completed visual War list/detail, retained selection/filter/scroll state, and warning presentation |
| `KOMEGuiConquestCapture` | Completed automatic allied/hostile capture confirmation with authoritative consequences |
| `KOMEGuiPledgeDeparture` | Completed warning-led, wrapped, scrollable departure preview |
| `KOMEGuiVisualCaptureController` | Added property-gated deterministic capture of 11 GUI states |

`KOMEClientProxy` conditionally registers the capture controller only with `-Dkome.guiCapture=true`. `build.gradle.kts` supplies the existing deobfuscated LOTR development jar to `runClient` for capture resources without changing the packaged addon dependency or production LOTR jar.

## Shared visual components

- Panels/backgrounds and hierarchy cards
- Styled buttons with hover, selected, disabled, and destructive states
- Tabs
- Faction badges
- Status chips
- Progress bars
- Warning banners
- Scroll panels with scaled, clamped scissoring
- Wrapped text and tooltips, including disabled-action reasons
- Confirmation dialogs

## Before and after

Before this pass, KOME screens mixed light/local rendering, fixed geometry, locally implemented scissoring, text-only state, and multi-click destructive conventions. The ledger was functionally authoritative but visually disconnected, while War records, allied capture, and pledge departure exposed data without a complete common presentation.

After this pass, all in-scope screens use one dark KOME visual language, responsive compact layouts, shared scrolling and state primitives, fixed reachable action areas, readable warnings and reasons, retained refresh state, and explicit confirmation for destructive actions. The ledger redesign surrounds the original slots and storage model rather than duplicating them.

The final release correction anchors the Overview track selectors outside the scrolling viewport, prevents scroll-local buttons from being created unless their complete hitbox is visible, and keeps transient server text inside the fixed action bar. Scrolling can no longer move the Civil/Military/Trade selectors over the primary page tabs or paint controls across the bottom actions.

## GUI scale verification

The deterministic capture suite rendered 11 states at each required setting: Alliance list, create/request, Overview, Requirements, Benefits, Military, break confirmation, Alliance ledger, War records, allied-tile confirmation, and pledge-departure preview.

| Setting | Display | Screenshots | Result |
|---|---:|---:|---|
| Small | 1280x720 | 11 | Pass |
| Normal | 1280x720 | 11 | Pass |
| Large | 1280x720 | 11 | Pass |
| Auto | 1600x900 | 11 | Pass |
| Smallest supported | 854x480, Auto | 11 | Pass |
| **Total** | | **55** | **Pass** |

The linked matrix is in [`gui-scale-verification/README.md`](gui-scale-verification/README.md). It verifies compact layouts, visible/reachable actions, working scroll viewports, wrapped long names/reasons, and confirmation overlays. Selected Alliance tab and War mode/filter/selection are retained across authoritative refresh in source.

The ledger screenshot uses a container-free visual twin because LOTR's global pouch hook dereferences a live player when a `GuiContainer` is opened by the menu-only capture harness. The production `KOMEGuiAllianceLedger` has the same layout and responsive transform and retains the authoritative container and storage path. The twin does not replace manual testing of the real container slots, click/drag/release mapping, and hitboxes in a populated client/server session.

## Automated verification

Command: `./gradlew clean test build` in `KOME-LOTR-Addon`.

- Result: `BUILD SUCCESSFUL`
- Tasks: 25 actionable; 24 executed; 1 up-to-date
- Test suites: 5
- Tests: 75
- Failures: 0
- Errors: 0
- Skipped: 0

Final post-correction command: `./gradlew test build` after the Alliance-detail fixed-navigation correction.

- Result: `BUILD SUCCESSFUL`
- Tasks: 24 actionable; 22 executed; 2 up-to-date
- Tests: 75
- Failures: 0
- Errors: 0
- Skipped: 0
- Note: a second `clean` was prevented by a Windows lock on the shared `build/rfg/recompiled_minecraft-1.7.10.jar`; compilation, tests, reobfuscation, and packaging all completed successfully without deleting that cache.

Root compatibility command: `./gradlew compileJava` in the root LOTR project.

- Result: `BUILD SUCCESSFUL`
- Tasks: 9 actionable; 9 up-to-date

## Release artifact and production integrity

Addon artifact: `build/libs/KOME-LOTR-Addon-1.0.7.jar`

- Size: 11,641,381 bytes
- SHA-256: `0B021BD64DDA8467D9DD03D7779642B6B49BC0EB49C7C3A43BBC2C396D516DDC`

### Authoritative production artifact

Only the following byte-for-byte artifact is the supported untouched LOTR v36.15 production jar:

| Field | Value |
|---|---|
| Preserved source path | `C:\Users\dayne\OneDrive\Desktop\The-Lord-of-the-Rings-main\LOTR-Test-Server\mods\LOTRMod v36.15.jar.original` |
| Preserved filename | `LOTRMod v36.15.jar.original` |
| Deployment filename used in isolated runs | `LOTRMod v36.15.jar` |
| Size | 28,936,383 bytes |
| SHA-256 before verification | `4F296E749C0D4739ECF859217A526B4218A2A45A768C08D3D551AF0D0D3D5635` |
| SHA-256 after verification | `4F296E749C0D4739ECF859217A526B4218A2A45A768C08D3D551AF0D0D3D5635` |
| ZIP entries | 10,297 |
| Manifest | `Manifest-Version: 1.0`; `FMLCorePluginContainsFMLMod: true`; `FMLCorePlugin: lotr.common.coremod.LOTRLoadingPlugin` |
| `mcmod.info` identity | The Lord of the Rings Mod, Update 36.15, author `Mevans`, original LOTR wiki URL |
| `lotr/common/LOTRPlayerData.class` size | 107,955 bytes |
| `LOTRPlayerData.class` SHA-256 | `67B3303BF84D66FEE4F5284C0E34D630817A1C366F7A592AAB3455EDC6DF439E` |
| Persisted KOME hook in class | No |

The isolated server, client, and class-load runs each used a copied file with the same 28,936,383-byte size and `4F296...` hash. The preserved source was never overwritten.

### Development/test fixture that must not be deployed

`C:\Users\dayne\OneDrive\Desktop\The-Lord-of-the-Rings-main\LOTR-Test-Server\mods\LOTRMod v36.15.jar` is not the production artifact. It is a locally rebuilt development/test fixture:

| Field | Value |
|---|---|
| Path | `C:\Users\dayne\OneDrive\Desktop\The-Lord-of-the-Rings-main\LOTR-Test-Server\mods\LOTRMod v36.15.jar` |
| Filename | `LOTRMod v36.15.jar` |
| Size | 21,251,540 bytes |
| SHA-256 | `E21B28A77C688F469CC9F08FBD3EA0C4D31F2A6ACDFF3DC6E810495F522C61A3` |
| ZIP entries | 10,125 |
| Manifest | `Manifest-Version: 1.0`; `FMLCorePluginContainsFMLMod: true`; `FMLCorePlugin: lotr.common.coremod.LOTRLoadingPlugin` |
| `mcmod.info` identity | The Lord of the Rings Mod, Update 36.15, credits/author `Hummel009`, GitHub URL |
| `lotr/common/LOTRPlayerData.class` size | 112,900 bytes |
| `LOTRPlayerData.class` SHA-256 | `D495524E27358296FBC584756D9CE3EDEFB1AD3836D96955473D6876EDFB2321` |
| Persisted KOME hook in class | No |
| Deployment status | **Development/test fixture; must not be deployed** |

The E21B jar has the same 10,125-entry layout and byte-identical `LOTRPlayerData.class` as the workspace's `build/libs/lotr-dev-local-dev.jar`. Its ZIP timestamps end on 2026-05-24, and it contains local-source entries such as `LOTRTerritoryData`, `LOTRGuiPopulationAdmin`, and the placeholder `com/myname/mymodid/Tags.class`, while omitting 199 entries present in the 4F296 distribution. Its `mcmod.info` is the local Hummel009 fork identity rather than the Mevans distribution identity.

Therefore E21B is an older locally rebuilt development/test jar with source-level differences. It is not a byte-for-byte production artifact, not an in-place KOME-patched jar, and not a jar containing KOME's runtime hook. KOME's transformer remains runtime-only. Regardless of whether individual rebuilt references are runtime-deobfuscated by Forge, E21B is unsuitable for deployment because its provenance and contents differ from the supported production binary.

## Production-jar runtime verification

No alliance, war, Produce, population, quota, migration, or GUI source was changed during this verification.

### Clean dedicated server

Run directory: `run-server-production-verification-final`

Mods contained only:

- `KOME-LOTR-Addon-1.0.7.jar` — `0B021B...`
- copied `LOTRMod v36.15.jar` — 28,936,383 bytes, `4F296...`

Evidence:

- [FML server log](../run-server-production-verification-final/logs/fml-server-latest.log), lines 64-66: KOME core plugin and `kome.core.KOMEWaypointTransformer` registered.
- FML log line 132: five mods identified; line 807: five mods loaded successfully.
- [Server log](../run-server-production-verification-final/logs/latest.log), line 95: `Done (3.424s)!`.
- Server log lines 96 and 99: controlled `stop`, then world save.
- Harness result: exit code 0; `DoneSeen=True`; `StopSent=True`; `TimedOut=False`; no stderr lines.

`LOTRPlayerData` is lazy-loaded and a server with no joining player does not load that class before `Done`. Exact transformation was therefore verified separately without changing either deployable jar.

### Exact production-class injection

Run directory: `run-production-final-evidence/server-classload`

The run added one explicit 1,603-byte verification-only mod, `KOME-Production-ClassLoad-Verifier.jar` (`C8DB12...`), whose only action is `Class.forName("lotr.common.LOTRPlayerData")` during post-init. It is not part of KOME, is not a dependency, must not be deployed, and exists only to force the otherwise lazy class to load. The deployable KOME and LOTR jars were unchanged.

Evidence:

- [FML class-load server log](../run-production-final-evidence/server-classload/logs/fml-server-latest.log), lines 66-68: KOME transformer registration.
- Line 790: verification fixture requests the exact class.
- Line 791: `[KOME] Secured LOTRPlayerData.receiveFTBouncePacket with the final waypoint gate.`
- Line 792: the loaded class source is the copied `.../server-classload/mods/LOTRMod v36.15.jar!/lotr/common/LOTRPlayerData.class`, whose enclosing jar hashes to `4F296...`.
- Line 843: all six mods, including the transparent verifier, loaded successfully.
- [Class-load server log](../run-production-final-evidence/server-classload/logs/latest.log), line 96: `Done (6.158s)!`; lines 97 and 100: controlled stop and world save.
- Harness result: exit code 0; `SecuredLOTRPlayerData=True`; `ExactClassSourceLogged=True`; `DoneSeen=True`; `StopSent=True`; `TimedOut=False`; no stderr lines.

This proves the final KOME 1.0.7 transformer accepts and injects the untouched production `LOTRPlayerData.class` at runtime while leaving the jar bytes unchanged.

### Isolated client startup

Run directory: `run-production-final-evidence/client`

The isolated client mods directory contained only KOME 1.0.7 and the copied 28,936,383-byte `4F296...` LOTR jar.

- [Client log](../run-production-final-evidence/client/logs/latest.log), lines 18-19: KOME transformer registered.
- Line 65: five mods identified.
- Lines 79 and 92: LOTR logger found and all 144 LOTR packets registered.
- Line 142: five mods loaded successfully, including `kome` and `lotr`.

The launcher's automatic localhost connection screen did not establish a TCP session in this automation environment, so no multiplayer behavior claim is based on it. The required isolated client startup and KOME/LOTR load completed successfully; exact runtime injection is established by the controlled server class-load run above.

No deployment to the real server or client profile occurred. The E21B fixture, the class-load verifier, and all development jars are explicitly non-deployable.

## Documentation state

`KOME_GUI_HANDOFF.md` is the current source-based handoff and contains no unfinished required visual-pass TODO. Superseded one-way alliance, coin, farmer milestone, T4, old record-format, old screen-inventory, and pre-pass GUI descriptions are isolated in `archive/KOME_GUI_HANDOFF_LEGACY_PRE_VISUAL_PASS.md`.

The only remaining items are non-blocking multiplayer user-acceptance testing on a populated server and optional authored faction icon art.
