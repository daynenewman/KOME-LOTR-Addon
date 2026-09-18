# KOME public player access policy

Baseline: `dba6bd5cddcc4a8df295c130ab6be35db76ea75c`.
Implementation worktree: `KOME-LOTR-Addon-Public-Access`, branch
`dayne/public-kome-player-access`. This document is not merge or deployment approval.

## Observed blocker and verified cause

The original isolated production-server evidence in `VALIDATION-RESULT.txt` and
`validation-evidence/matching-client-20260917-220806` recorded a successful matching
G1 handshake, join and world synchronization, followed by non-operator access
failure. The corrected public-access build was subsequently validated under
`validation-evidence/public-access-20260918-123156`; the original entry blocker is
resolved without granting permanent OP access.

All nine KOME command classes declared permission level zero but inherited
`CommandBase.canCommandSenderUseCommand(ICommandSender)`. In Minecraft 1.7.10 that
delegates to `EntityPlayerMP.canCommandSenderUseCommand(int, String)`, whose
non-operator branch rejects custom commands even at level zero (apart from its
hard-coded vanilla command exceptions). This was verified in the project's
decompiled 1.7.10 source, not inferred from modern Minecraft behavior.

The menu/map/Tile Command did not have a global client operator gate. Several
entry buttons dispatch chat commands, including the population launcher and
company movement preview, so the dispatcher defect made existing public screens
unusable. `/kome` additionally had no ordinary no-argument landing action.

## Entry policy

`KOMEPublicCommand` changes only the command dispatcher gate: any non-null sender
may enter a KOME command. Each command still reports level zero; staff actions
check the authenticated sender at level two independently. This is a small base
class, not a permission registry or a replacement for domain services.

- `/kome` or `/kome gui`: open the existing canonical population overview for a
  player. Its Tiles button opens the existing conquest map; its menu provides the
  other existing KOME screens. The console receives text help rather than a GUI.
- `/kome tile <id>`: use the existing server Tile Command projection, with the
  same validation as packet 7.
- `/kome help`: public commands; staff also receive administrative root usage.
- `/population`, `/build`, `/conquest`, `/progression`, `/troops`, `/alliance`,
  `/war` and `/season`: public entry with action-specific checks. Existing
  player-only actions still require a player; console-safe inspection/admin
  commands retain their existing behavior.
- Root and nested tab completion hide operator-only actions from ordinary
  senders. Usage text is audience-specific. Domain-dependent actions can appear
  before the player qualifies; help/completion does not confer authority.

## Caller and enforcement matrix

Before this change every command row below was subject to the broken inherited
dispatcher gate, regardless of its declared level zero. Packet rows already used
authenticated server players and the START-tick queue, not command dispatch.
`Public` means inspection, not a right to mutate another faction's property.

| Entry / action | Read or mutation | Audience and final server enforcement | Evidence / tests |
| --- | --- | --- | --- |
| `/kome`, `gui`, `help`, `tile` | Read | Public; `KOMEPublicCommand`, existing overview and `sendTileCommand` | `KOMEPublicCommandTest`; queued public Tile Command test |
| `/population get/gui/units` | Read | Self only, including explicit self names used by existing GUI links; another player's target requires level two before lookup/projection | `KOMEPublicPrivacyTest`; operator/console target and denied-packet-output assertions |
| `/population faction/tile/rate` | Read | Public canonical faction aggregates; tile inspection uses the shared public filter; payout diagnostics remain level two | privacy/tile purity and projection tests |
| `/conquest get/list` and map -> packet 7 | Read | Public known, non-retired, existing map tiles; `getPublicConquestTile` | known/retired/unknown tile tests |
| `/conquest transfer/accept/cancel` and packet 19 | Mutation | Existing faction kings, faction allegiance, pending-offer checks; documented command staff overrides retained | forged transfer test; ruler/diplomacy suites |
| Map capture -> packet 6 | Mutation | Valid public tile, authenticated pledge, TAKE_WAYPOINTS progression, `KOMEConquestClaimService` season/war/confirmation rules | denied capture/raw-ownership test; movement/capture and season regressions |
| `/conquest claim/clear/clearAll/reset/purgeLegacy/waypoint` | Administrative mutation/inspection | Explicit level two before WorldData access; direct `claim` is a force path, not normal map capture | denied admin commands test |
| `/build list/inspect/grants` | Read | Public Build metadata and contributions; raw Build audit is level two | public Tile Command + precise Build regressions |
| `/build grant/revoke`; Build create/submit GUI -> packet 36 | Mutation | Owning ruler/foreign construction permission; server-derived contributor and faction | foreign-construction and forged-manager tests |
| Build approve/adjust/reject/rename/activate/delete -> packet 36 | Mutation | `KOMEBuildService.canReview` and existing manager/admin/destroy/own-pending-contribution rules, depending on action | genuine non-op manager approval and forged review denial; precise Build suite |
| `/build reassign/remove/sethours/adjust` | Administrative mutation | Level two; explicit repair path distinct from normal manager GUI review | denied admin commands; isolation regression |
| `/troops list/tile/unit/arrivals/locate` | Read | Exact units are self-scoped or an explicit operator inspection; tile/unit lookup cannot bypass `/population units` privacy | `KOMEPublicPrivacyTest`; raw ownership preservation assertions |
| `/troops companies/company/history` | Read | Companies retain owner/live-controller/native-stewardship-ruler scope; history retains existing faction scope, with level two for other/all factions | company/forged history tests; authorized history read-purity test |
| Company/recruitment/movement GUI -> packet 35 | Mutation or preview | Whitelisted intent -> commands/services; canonical owner/controller, current pledge, native stewardship, diplomacy, route/arrival/access, population and unit rules | denied company/recruitment test; movement, projection, stewardship regressions |
| Troop route/arrival/waypoint read commands | Read | Public existing metadata; company route inspection requires owner or existing explicit staff override | movement tests; source audit |
| Troop waypoint/arrival/station/recruit edits | Mutation | Existing narrower trusted role: owning-faction member for arrival, owning ruler for waypoint edits; own units, controlled tile and positive represented population for recruitment | owner/control checks; projection and movement suites |
| Troop debug/forced arrival/repair/timing/route-edge changes | Administrative mutation/inspection | Level two before action; ordinary resume/stay/retreat do not gain administrative overrides | denied admin commands and completions; campaign boundary tests |
| `/alliance list/get`, packet 17 | Read | Existing participant scope; staff operator-view request is checked server-side | unpledged inspection purity; alliance tests |
| `/alliance request/accept/cancel`, packet 32 | Mutation | `KOMEDiplomacyService` ruler/participant decisions; existing explicit staff cancellation override | diplomacy/ruler tests; command/packet audit |
| Alliance goods/claim | Mutation | Existing accepted relationship, sender membership/receiving ruler, inventory/container permissions | alliance authority and systems suites; no quota/population authority restored |
| `/progression status` | Read | Public enabled/disabled summary, console safe | console aggregate test |
| `/progression get/list` | Read | Self details; explicit other-player command targets require level two | privacy and absent-record tests |
| Progression pledge/offerings/roll/self-completion; packet 21 | Mutation | Existing lord/range/pledge/self-completable task policy; unknown lord action explicitly rejected | progression suites; source audit |
| Progression enable/disable/reroll/grant/revoke/grantall/reset | Administrative mutation | Existing level-two guards retained | denied admin commands and operator success |
| `/war list/status`, `/season status` | Read | Public gameplay summaries; war diagnostics selected explicitly by level two | public/operator redaction and war/season suites |
| `/season finale` | Mutation | Existing recognized-ruler / active-war season service | war-season tests; no lifecycle redesign |
| Other war/season lifecycle changes | Administrative mutation | Level two before world lookup, then existing service validation | denied admin commands; operator/isolation/season tests |
| Server Records -> packet 11 | Read | Public aggregate ranks, progress totals, faction population and war gameplay history; pledged-lord details and internal war slots redacted unless level two | public/staff redaction behavioral tests |
| `/kome config/audit/repair/ruler/character/conquest/waypointdefaults/adminmarkers` | Administrative read/mutation | Level two before WorldData; sensitive raw diagnostics/repair remain private | denied admin commands; operator access test |
| Unit-cap request/update -> packets 14/15 | Read/mutation | Loaded active hired unit; update requires recorded owner or existing explicit staff override | existing cap tests and handler audit |
| Movement history -> packet 28 | Read | Authenticated faction, or staff for all/other factions; reads lifecycle-maintained snapshots without reconciliation | forged and authorized history purity tests |
| Waypoint travel -> packet 31 | Mutation | Existing fast-travel enabled, valid waypoint, progression, native/diplomatic access, cooldown, under-attack and sleeping checks | waypoint/progression suites and handler audit |
| Pledge-departure preview -> packet 33 | Read | Sender's own canonical records only; packet has no target identity | pledge-release suites and handler audit |

## Tiles and GUI visibility

The server public-tile helper accepts only normalized canonical IDs present in
the existing map defaults, excludes `isRetiredTile`, and requires an existing
WorldData record. It neither creates a tile nor repairs/normalizes ownership.
All ordinary known tiles pass this identity filter, including tiles controlled
by other factions. Claiming them is a different, guarded operation.

The baseline has no separate mutable reserved/capturable boolean to invent or
relabel. Retired/system entries excluded by map metadata, unknown IDs and absent
records remain unavailable. Existing progression, ownership, war and season
restrictions remain in the action services. This work adds no capturability
inference from colors, distance or coordinates and no world-coordinate resolver.

Existing `KOMEConquestMapOverlay` selection sends packet 7 without an OP check.
`KOMEGuiConquestCapture` already exposes Builds and Canonical Population tabs.
Its controls use server-projected `canManage`, transfer/ruler, recruitment and
movement eligibility. Capture eligibility now also reflects the existing
TAKE_WAYPOINTS permission, which was already enforced on the server. Client
booleans are display hints only; server mutations recompute authority.

The population GUI hides/ignores another player's detail button for a non-operator.
Faction player-investment aggregates stay visible; the button is not authority:
the command repeats the self/operator check before looking up the target.
Client packet-publication code and wire layouts are unchanged.
No retired allocation/reserve tab or packet was restored.

## Exact privacy policy and read-only inspection

| Information | Audience |
| --- | --- |
| Exact unit IDs, names, positions, companies, movement and investment | Own units; another player's detailed list requires level two, including console targets |
| Detailed progression completion, assignments and pledged lord | Self; level two for other-player commands. Public Server Records retain only aggregate progress/rank and redact pledged-lord details |
| Available/Active/represented faction population, cap and Build rates | Public aggregates; same-faction player-investment breakdown remains an aggregate, not a route to private units |
| War ID/name, lifecycle state, side names and faction membership | Public |
| War capture counts/history and start/ending/end timestamps | Public gameplay history |
| War administrative events/counts, authorization UUIDs, support reasons, membership-source diagnostics, contradiction warnings, escrow/inactivity details, stewardship company/reservation details and free-form ending notes | Operator only |

`KOMEPublicCommand.privateInspectionTarget` authorizes before target resolution
or WorldData access. Explicit self names remain accepted for existing GUI links;
unknown/other names and selectors are denied to non-operators, not silently
substituted. Public completion offers no player names for population/progression
inspection. Command blocks and consoles do not gain target access merely by being
non-player senders: they need level two; no-target private forms require a player.

`KOMEWorldData.progressionForInspection` returns the existing authoritative record
or an unpublished baseline object. Command get/list neither insert, auto-complete,
update titles nor synchronize as a side effect of reading. The dormant
`KOMEPacketProgressionRequest` handler also uses this helper and retains its
self-only response; it is not registered in G1 and is not reactivated here.
Normal progression synchronization remains server-pushed on ID 9. Find-lord projects
the live location without rewriting the saved pledge-location fallback; absent
lord/offerings requests reject without creating a record. Valid roll/pledge/
completion actions retain canonical creating access and normal persistence.

Public population-tile, troop-tile and Build tile inspections use the existing
public tile helper; unit/company/arrival list filters and arrival/waypoint
inspection do too. Invalid/retired/absent tiles cannot become records through
inspection. Tactical route validation keeps its existing domain checks, without
granting public tile mutation or implementing coordinate resolution.

Public war command text uses a separate summary and strips formatting/control
characters from free-form labels. Ending reasons are withheld, not guessed to be
safe public prose. Server Records preserve the G1 row positions but put
`Operator-only` in diagnostic slots and `Private` in pledged-lord details.
Operator status is derived from the authenticated sender; help/UI visibility
does not select authority.

Player-target source audit:

| Caller | Classification |
| --- | --- |
| Population get/gui/units; progression get/list | Self-only by default; operator-targetable through the common helper |
| Progression reroll/grant/revoke/grantall/reset; Build reassign; root character/ruler targets | Administrative: explicit level two before `getPlayer(sender, ...)` |
| Troops pledge-release target/retry/resolve | Own preview/status; other targets and recovery are administrative |
| Troops company delegate/transfer targets | Gameplay-authorized mutation, not a private-record inspection; existing ownership, ruler/diplomacy and recipient checks retained |
| Company views / movement history | Existing company controller/native-stewardship-ruler scope / faction scope; neither is unrestricted per-player detail lookup |
| Population faction/tile/rate and aggregate Server Records | Public aggregates, not player-target commands |
| Dormant progression request and lord packets | Authenticated self only; no client-selected player identity |

All remaining command `getProgression` calls are mutations (with completion
prerequisites checked before record creation). Public get/list uses the read-only
helper; Server Records and permission/alliance reads use existing map entries.
Rejected private inspections do not generate a success audit or GUI response.

## Rejection purity and authority

All server registrations, discriminator IDs and anonymous handler identities
remain unchanged. IDs 6, 7, 11, 14, 15, 17, 19, 21, 28, 31, 32, 33, 35 and 36
still dispatch through the existing bounded-snapshot START-tick server queue.
The authenticated server player is the source of identity, privilege and pledge.
No admin/manager flag or client-provided faction is trusted as authorization.

Additional boundary corrections made necessary by making the entry public:

- Unknown/retired tile requests are rejected before a creating/repairing tile
  accessor. Capture, foreign-construction, transfer and recruitment permission
  reads use pure ownership projection, including legacy-owner fallback.
- Progression permission and unpledged alliance inspection use existing records
  without creating them as a side effect of inspection or denial.
- Unknown transfer/lord action codes are rejected rather than treated as a
  default action.
- Rejected Build/alliance requests do not send success-style refresh projections.
- Unauthorized all/foreign movement-history requests return before reconciliation
  rather than being silently downgraded to a different request.
- Company movement validation no longer rebuilds company membership before
  checking the caller. Live stewardship/delegation authorization is still
  checked by `canPlayerControlCompany`; login, war/ruler/diplomacy/restart hooks
  and movement ticks retain their existing reconciliation/withdrawal jobs.
- Route checks do not create missing tiles or synchronize ownership. Arrival
  validation projects the existing legacy-anchor fallback without creating a
  waypoint. Actual movement still uses the same pathing/arrival rules. Company
  refresh for movement occurs after permission, route, unit and arrival checks.
  Preview uses the canonical company record without repairing it.
- Forced `/troops arrive`, company rebuild and arrival backfill are administrative
  repairs, not ordinary player entry points.

No globally transactional gameplay rewrite is claimed. An authorized hostile
capture's existing confirmation staging/expiry is a deliberate state transition,
not an unauthorized permission failure. Authorized movement-history inspection
now reads snapshots maintained by movement lifecycle writes; it does not repair
missing history or mark dirty. An unauthorized unit-cap update can
receive the complete current cap correction, not partially decoded/mutated state.
These distinctions preserve the baseline mechanics and client contract.

## Remaining operator-check classification

There is no remaining global operator requirement around ordinary KOME entry or
ordinary tile selection. The production search was reviewed by call site:

| Check locations | Classification and reason retained |
| --- | --- |
| `KOMEPublicCommand.isStaff`, command `requireStaff`, Kome `hasStaffPermission`, Troops early admin-action checks | Administrative subcommands, repairs and sensitive diagnostics; not public dispatcher checks |
| Build command raw history; ServerRecordRequest administrative-war-history flag | Administrative read redaction; public Build/contribution/war status remains |
| Population command payout-state diagnostics | Administrative diagnostics only; canonical balances/rates stay public |
| War and Season mutators; Progression grant/reset; Conquest force/clear/reset/waypoint metadata | Administrative overrides, intentionally retained |
| Conquest ruler transfer/cancel helpers | Existing explicit staff override of the ruler role, not a public capture bypass |
| Troops history/all scopes, pledge-release target/retry/resolve, route-edge edits, rebuild, movetime, movement admin operations, arrival clear/tp/backfill/debug | Administrative data or forced-state operations |
| Troops owner/control, company authority, locate, snapshot, waypoint and arrival helpers | Existing narrowly scoped operator override of owner/ruler/member rules; temporary delegation/stewardship still checks canonical authorization |
| BuildAction and Tile Command `canManage` | Existing Build manager/admin review policy derived on server |
| Tile Command waypoint inspector and controllable-company projection | Existing staff diagnostic/owner override; does not gate tile opening |
| Alliance command participant/ledger/cancel checks; AllianceAuthority; AllianceInventory; AllianceRecordBuilder operator view | Existing scoped staff view/ledger/cancellation privileges, not unrestricted ordinary-player authority |
| UnitCapUpdate | Existing explicit owner-or-operator cap-management override |
| UnitMapMarkers | Existing operator marker scope, respecting marker opt-out |
| PledgeReleaseService notification audience | Owner/staff notification routing, not a permission grant |
| WaypointAccessService | Existing explicit administrative travel override, separate from native/diplomatic access |

No active permission-node registry, `isOp` or `isPlayerOpped` alternative was
introduced. The default Minecraft operator checks are not weakened globally.

## Protocol, persistence and scope

- Protocol remains `1.0.8-integration-g1`; IDs/sides and packet read/write layouts
  are unchanged. Retired IDs 23/24 remain unused. No new packet is introduced.
- Existing exact-codec string/count/number bounds, client publication and queue
  bounds are unchanged. This is not a retrofit of every older packet codec.
- Root schema remains 3; nested population, Build and payout schemas unchanged.
- No new persisted access flags, caches, population authority or migration.
- Canonical centi bank, informational Active/represented population, permanent
  spending and single-use failed-hire rollback are unchanged.
- Build centi-hour lifecycle, manager auto-approval, payout/DST/season scheduling,
  movement allowance/pathing and cost formulas are not redesigned.
- Aqua/Character Creation/racial-arm code, the tile worktree and KOM-71 are out
  of scope and unchanged.

## Automated verification

New `KOMEPublicCommandTest` executes all nine dispatcher gates with a sender that
reproduces vanilla's non-op level-zero rejection; executes public root/console
help, actual population packet creation, operator administration, denied admin
actions before world access, and public/staff help/completion filtering.

New `KOMEPublicAccessPacketTest` executes actual handlers/services with inert
World/player/network fixtures and the actual server queue. It covers queued
public Tile Command and exact data, public units/companies, all known tile IDs,
retired/unknown IDs, spoofed manager review, genuine non-op manager approval,
foreign ownership, insufficient population, transfer/history denial, raw tile
field preservation, company/recruitment denial, unpledged alliance reads,
administrative-history redaction and malformed packet rejection. Structural
assertions cover existing GUI entry/navigation, G1/schema3 and packet registration.

`KOMEAccessFixture` uses the existing project's reflection/Unsafe inert-fixture
style; it is not a live Forge server or connection test. GUI checks do not execute
OpenGL. New tests do not spawn live hired NPCs or prove every combination of
movement, diplomacy and season state. Existing regression suites cover those
services; live acceptance remains required.

`KOMEPublicPrivacyTest` exercises self/operator/console targets, exact-unit denial
without packets/audit/dirty changes, public aggregate tile filtering, absent
progression and dormant-handler baseline projection, legitimate self-roll NBT persistence,
lord-locator read purity, public/operator war and pledged-lord redaction,
non-player rejection before world access, authorized console administration and
movement-history read purity. It also executes Minecraft 1.7.10's actual
`CommandHandler.executeCommand` for public entry, self reads and denied private/
administrative commands. Players, server lookups and the network remain inert
fixtures; this is real dispatcher code, not a live Forge/network handshake test.

`KOMEBaseIsolationTest` now expects the additional early staff guards and shared
public command superclass. `KOMEWorldDataSchemaTest` expects the new read-only
public tile filter and normalizes CRLF when matching source excerpts. No runtime
schema expectations were weakened.

Validation commands (run from this worktree):

```powershell
.\gradlew.bat test --tests kome.common.command.KOMEPublicCommandTest --tests kome.common.network.KOMEPublicAccessPacketTest --tests 'kome.common.command.*MovementTest' --tests kome.common.data.KOMEAllianceSystemsTest --tests kome.common.data.KOMEKinglessStewardshipTest --no-daemon --console=plain
.\gradlew.bat test --tests 'kome.common.command.KOMEPublic*' --tests kome.common.network.KOMEPublicAccessPacketTest --tests 'kome.common.command.*MovementTest' --tests kome.common.data.KOMEAllianceSystemsTest --tests kome.common.data.KOMEKinglessStewardshipTest --tests kome.common.data.KOMEPopulationProjectionTest --tests kome.common.data.KOMEPopulationPayoutProcessorTest --tests kome.common.data.KOMEPreciseBuildTest --tests kome.common.network.KOMEPopulationProtocolTest --tests kome.common.data.KOMEWorldDataSchemaTest --tests kome.common.data.KOMEWorldDataAtomicLoadTest --tests 'com.fuzs.aquaacrobatics.core.asm.*Test' --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat clean test build --no-daemon --console=plain
git diff --check
git diff --cached --check
```

The broader focused pass also includes population projections/payout, network
codec/protocol, precise Builds, WorldData schema/atomic loading, Aqua transformer
and Character Creation model tests. The earlier, pre-correction run passed 696
tests (two platform-dependent symlink skips); that is historical evidence, not
validation of the privacy correction. Updated run results are recorded below.
Earlier Java 8 `javap` on the production JAR confirmed the
public override is `func_71519_b(ICommandSender): boolean`; its class-file version
is 52 and the coremod/access-transformer manifest entries remain intact.
Compilation/tests alone do not imply deployment or multiplayer success; the live
results below are recorded separately.

Privacy-correction automated validation on 2026-09-18: the final broader focused selection
passed 238 tests with no skips/failures/errors, including all 14 new privacy
tests. The full suite and subsequent clean test/build both succeeded: 710 tests,
708 passed, no failures/errors, and the same two platform-dependent symlink
tests skipped. Both Git diff checks passed.

## Live isolated validation

The reviewed production JAR was deployed to the isolated localhost validation
server and matching `Lord of the Rings DEV` client. Live results:

- Matching G1 client/server handshake and non-operator join passed.
- `/kome` opened successfully without operator status.
- Public canonical population, self progression and public war listing worked.
- Conquest-map panning worked; the map's right-click behavior was understood.
- Representative administrative KOME and war commands were denied.
- Disconnect/reconnect passed.
- `save-all`, clean shutdown, saved-world restart and post-restart reconnect passed.
- Root schema 3 loaded without write blocking or duplicate population initialization.
- No protocol, packet, Aqua, linkage or class-loading error was observed.
- Authentication used `online-mode=false` only on the isolated server bound to
  `127.0.0.1`; after testing, `server.properties` was restored byte-for-byte from
  its pre-validation backup with `online-mode=true`.

The Build-creation GUI concern is a separate pre-existing follow-up. It was not
treated as a public-access regression and no Build code was changed for it.

Remaining live gates are an operator comparison, deployment to the real shared
server, destructive/administrative action sampling without executing destructive
state changes, and a complete hire/movement/season/gameplay smoke test. The live
OP comparison and real shared-server deployment have not been performed.

The separate tile-resolution branch changes related tile call sites. Reconcile
those changes explicitly during later integration; do not import that branch or
replace its unresolved resolver as part of access control.

## Review inventory

29 modified tracked files, six new files; no deletions. All paths below are
relative to the isolated public-access worktree. Build outputs and the two
locally supplied, ignored LOTR/GeckoLib dependency JARs are not source changes.

```text
M src/main/java/kome/client/gui/KOMEGuiPopulation.java
M src/main/java/kome/common/command/KOMECommandAlliance.java
M src/main/java/kome/common/command/KOMECommandBuild.java
M src/main/java/kome/common/command/KOMECommandConquest.java
M src/main/java/kome/common/command/KOMECommandKome.java
M src/main/java/kome/common/command/KOMECommandPopulation.java
M src/main/java/kome/common/command/KOMECommandProgression.java
M src/main/java/kome/common/command/KOMECommandSeason.java
M src/main/java/kome/common/command/KOMECommandTroops.java
M src/main/java/kome/common/command/KOMECommandWar.java
M src/main/java/kome/common/data/KOMEAllianceInventory.java
M src/main/java/kome/common/data/KOMEAllianceRecordBuilder.java
M src/main/java/kome/common/data/KOMEConquestClaimService.java
M src/main/java/kome/common/data/KOMEForeignConstructionService.java
M src/main/java/kome/common/data/KOMEProgressionPermissions.java
M src/main/java/kome/common/data/KOMEProgressionLords.java
M src/main/java/kome/common/data/KOMEServerRecordBuilder.java
M src/main/java/kome/common/data/KOMEWorldData.java
M src/main/java/kome/common/network/KOMEPacketAllianceAction.java
M src/main/java/kome/common/network/KOMEPacketBuildAction.java
M src/main/java/kome/common/network/KOMEPacketConquestClaim.java
M src/main/java/kome/common/network/KOMEPacketConquestOpenCapture.java
M src/main/java/kome/common/network/KOMEPacketConquestTransfer.java
M src/main/java/kome/common/network/KOMEPacketLordAction.java
M src/main/java/kome/common/network/KOMEPacketMovementHistoryRequest.java
M src/main/java/kome/common/network/KOMEPacketProgressionRequest.java
M src/main/java/kome/common/network/KOMEPacketServerRecordRequest.java
M src/test/java/kome/common/data/KOMEBaseIsolationTest.java
M src/test/java/kome/common/data/KOMEWorldDataSchemaTest.java
NEW docs/KOME_PUBLIC_ACCESS_POLICY.md
NEW src/main/java/kome/common/command/KOMEPublicCommand.java
NEW src/test/java/kome/common/KOMEAccessFixture.java
NEW src/test/java/kome/common/command/KOMEPublicCommandTest.java
NEW src/test/java/kome/common/command/KOMEPublicPrivacyTest.java
NEW src/test/java/kome/common/network/KOMEPublicAccessPacketTest.java
```
