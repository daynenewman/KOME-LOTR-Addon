# Client presentation and networking

[Index](../../INDEX.md)

The current matching-artifact protocol is `1.0.9-integration-g18`.
KOM-84/dev reconciliation previously used g13. Published Join Battle IDs 53–58 are retained; the previously
unpublished progression identity packet moves to 59. Visual marker ID 43 includes
the authoritative current-objective flag, and Standing eligibility retains its
expanded payload. The existing network check rejects older artifacts. Progression
objective map views bypass conquest viewport save/restore, and marker reflection
uses the native LOTR base class so subclasses remain supported. See
[testing report](../../../PROGRESSION_TESTING_FIXES_20261010.md).

Owns authenticated request scheduling, packet codecs/publication, client snapshots, menus/maps/HUDs, and server-record presentation. Server domain services remain authoritative; client caches are transient.

KOM-85 Phase 1 adds client-only territory palette/display controls and faction boundaries to the conquest map. See [territory presentation and verification](../geography/README.md). The existing completed public ownership projection is the sole input; no packet, server ownership, schema or gameplay behavior changes. `KOMEConquestMapOverlay.onMapSelectorMouseClicked` consumes these controls before native picking and captures their drag gesture; selector/radial panels retain priority where they cover a control. After independent review, the controls sit below native Zoom Out, and event picking and destination polling share `mapUiOwnsPointer` so a territory/native UI click cannot also issue a previewmove intent. Classic LOTR resolves canonical KOME/native aliases to native faction colors, including Utumno; Current KOME's legacy lookup/hash is preserved.

## Entry points and boundaries

KOM-85 Phase 1B retains the same client-only authority boundary. Visual settings (palette, mode, full-fill opacity and individual tile edges) use the left tan-square column; layer toggles use the right column. Both groups are handled by the pre-native click adapter and capture their drag gesture, while destination polling uses the shared UI-ownership guard. Opacity participates in the fill-cache key; tile-edge visibility changes only geometry dispatch. See the geography route for exact rendering values and automated evidence; live visual acceptance remains pending.

The Phase 1B forensic correction partitions faction bands into disjoint rectangles so ordinary source-alpha blending cannot accumulate at shared corners. Retained meshes use one scoped OpenGL client-array draw, with padded pan/resize reuse; hover/tile/destination layers and all visual/input settings remain separate. See the geography route for before/after counts, retained-memory bounds, increased construction cost and unexecuted GL-driver acceptance. There is no new authority input, packet, persistent preference or world data.

Phase 1C uses native interpolated position/zoom and a coverage-checked affine transform during the six-tick zoom animation. Up to three stable meshes share one 25 MB aggregate reservation; uncovered outward zoom builds the smaller endpoint's coverage once, while settled requests require exact zoom. Content/settings changes and reload/disconnect clear retained geometry. The focused native-zoom suite and prior regressions pass, but the strict fallback remains blocked when complete streamed geometry itself exceeds the budget: the inherited non-retained path would reconstruct repeatedly. See the geography route for evidence and the unresolved representation/budget tradeoff. Live smoothness and GL performance are unverified; labels, controls and persistence were unchanged by Phase 1C.

Phase 1D registers [KOMEMapTerritoryTransformer](../../../../src/main/java/kome/core/KOMEMapTerritoryTransformer.java) to draw only territory fill/shade/hover/borders using the already-interpolated map and mouse coordinates. Its waypoint follow-up places the single hook at the common terrain exit, before compass, roads, players, miniquests, both waypoint passes and region labels. Native renderers and preferences are left intact; no icons/text are copied or drawn twice. The compass argument/zLevel sequence and terrain/marker/label control-flow fingerprints are verified in development and SRG bytecode. A Pre-reset/Post-consumed frame marker prevents duplicate territory rendering; incompatible bytecode is left unchanged and uses the legacy Post fallback. The early pass restores depth/write, alpha/blend, texture and matrix state, including resource initialization. The geography route records the safety audit, owner-confirmed region labels and 183 passing focused tests; the new waypoint/player order still needs live acceptance. Territory cache policy and known far-zoom pan limitations are unchanged. There is no new authority, packet, persistent preference or world state.

The later editable-geography slice reuses the validated terrain-exit hook for river and automatic/manual/route bridge sprites, after territory and before native waypoint names. Source PNG canvases are 64 x 32 RGBA; logical sizes are 6 x 3 / 4 x 2 times exact animated zoom. Full resource canvases/alpha are preserved without bridge cropping. Frame-local tooltip ownership clears on Pre/Post, reload and disconnect. Native text is never redrawn; incompatible hooks omit geography sprites while retaining the accepted territory Post fallback. In that slice, troop/company, Build, route-line, panel and control presentation remained in Post; the stable-marker follow-up below moves Build sprites into the early pass. See the geography route for 260-test evidence and remaining live acceptance.

The deferred-border follow-up keeps the same public ownership projection and native render hook. Borders Only observes actual native position/zoom/animation and viewport changes, reuses prepared current-content meshes while navigating, and waits 250 ms of quiet before synchronous reconstruction. Temporary missing coverage is accepted; final retained geometry remains exact. Bounded complete-map attempts at zoom 0.125/0.25 require the source to fit both axes of the validated 64-pixel padded viewport; retained-byte affordability alone cannot promote partial views. The three-frame/25 MB cache is unchanged. Geometry/settings/ownership changes fence reuse; reload/disconnect clear navigation state. Full Color, icons, native labels, controls, persistence and authority are unchanged. Unretainable padded geometry can recover by retaining only its already-partitioned visible viewport through the existing settled-only streaming path. See the geography route for deferred-to-deferred measurements and the remaining limitation when even visible geometry cannot fit the hard budget.

Phase 1E uses [KOMEMapControlRenderer](../../../../src/main/java/kome/client/KOMEMapControlRenderer.java) for native 10x10 atlas frames, 14-pixel rows and original hovering-text tooltips. Appearance controls stay left; layer controls stay right. The editable-sheet follow-up aligns the right column at map maximum X-16 below the native waypoint actions, adds a fifth Build visibility toggle, and samples KOME pictograms from a documented editable PNG. The same covered-panel predicate gates tooltip and click ownership. [KOMEClientConfig](../../../../src/main/java/kome/client/KOMEClientConfig.java) extends its existing installation-local `config/kome-client.cfg` with a map category; [KOMEMapPreferences](../../../../src/main/java/kome/client/KOMEMapPreferences.java) holds the nine appearance/layer values (including default-on Build markers). Client initialization applies saved preferences before map registration; explicit changes save immediately, with no render-frame writes. Defaults remain Classic LOTR/full fill/50%/tile edges off/all layers on. Invalid values fall back safely and unrelated values remain intact. Temporary conquest visibility from destination/route workflows does not overwrite the saved choice. The geography route records exact atlas coordinates, keys and 212 passing tests. No server configuration, packet or world schema changes are involved; live control/font acceptance and the previously known pan limitations remain separate.

- [KOMEPacketHandler.init](../../../../src/main/java/kome/common/network/KOMEPacketHandler.java), `enqueueServerTask`, `runPendingServerTasks`: registrations and server-thread dispatch. [KOMEServerTaskQueue](../../../../src/main/java/kome/common/network/KOMEServerTaskQueue.java) bounds/fences KOME requests; requester validity is rechecked at execution.
- [KOMEPacketConquestData](../../../../src/main/java/kome/common/network/KOMEPacketConquestData.java) and [KOMEConquestSnapshotPublisher](../../../../src/main/java/kome/client/KOMEConquestSnapshotPublisher.java): canonical map snapshots and client publication. Inspect [KOMEPopulationProjection](../../../../src/main/java/kome/common/data/KOMEPopulationProjection.java) when totals disagree, not only the GUI.
- [KOMEClientProxy](../../../../src/main/java/kome/client/KOMEClientProxy.java), [KOMEClientTaskQueue.resetSession](../../../../src/main/java/kome/client/KOMEClientTaskQueue.java), [KOMEClientData.resetClientState](../../../../src/main/java/kome/common/data/KOMEClientData.java): client-thread application and connection/world transitions.
- [KOMEGuiConquestCapture](../../../../src/main/java/kome/client/gui/KOMEGuiConquestCapture.java), [KOMEConquestMapOverlay](../../../../src/main/java/kome/client/KOMEConquestMapOverlay.java), [KOMEGuiCompanyList](../../../../src/main/java/kome/client/gui/KOMEGuiCompanyList.java), [KOMEGuiLordMenu](../../../../src/main/java/kome/client/gui/KOMEGuiLordMenu.java): visible consumers and intent creation.
- [KOMEPacketServerRecordRequest](../../../../src/main/java/kome/common/network/KOMEPacketServerRecordRequest.java), [KOMEGuiServerRecords](../../../../src/main/java/kome/client/gui/KOMEGuiServerRecords.java): server-built permitted records, pagination, display. [KOMEGuiTheme](../../../../src/main/java/kome/client/gui/KOMEGuiTheme.java) and [KOMEGuiScrollPanel](../../../../src/main/java/kome/client/gui/KOMEGuiScrollPanel.java) own shared layout.

Join Battle packet registration and [KOMEPopulationWire](../../../../src/main/java/kome/common/network/KOMEPopulationWire.java) historically used integration-g13 (the earlier Join Battle integration used g5). [Disconnect evidence](../../../pr26-joinbattle-fix-20261009/README.md) records why an older client lacking response 54 must fail the exact-version handshake. [KOMEGuiJoinBattle](../../../../src/main/java/kome/client/gui/KOMEGuiJoinBattle.java) is a client projection; physical entry/receipt authority remains server-owned in the [conflict route](../conflict-tactical/README.md).

Dependencies: [runtime/session ownership](../runtime-state/README.md), [population](../build-population/README.md), [geography](../geography/README.md), [progression](../progression/README.md), [waypoint synchronization](../waypoints/README.md). Integrated Character/Aqua/siege channels use their own dispatch; KOME queue guarantees do not automatically apply to them.

### Native texture compatibility requirement

Whenever KOME presents an existing Minecraft/LOTR texture, bind its original namespaced ResourceLocation through Minecraft's normal resource manager and TextureManager. Preserve client resource-pack override priority and reload behavior. Do not copy native textures into KOME assets or permanently retain decoded native pixels. Validate nonstandard native UV contracts rather than assuming every texture pack preserves them; missing/incompatible assets use neutral KOME art. KOME custom art must also remain resource-pack replaceable.

KOM-85 [marker asset selection](../../../../src/main/java/kome/client/KOMEMapMarkerArt.java) follows this rule for original native shield/banner paths. [Owned texture loading](../../../../src/main/java/kome/client/KOMEMapMarkerTextures.java) uses a private TextureManager keyed by those original paths, isolating marker IO/null-image failures from globally shared native registrations. Reload deletes/replaces owned GL IDs, decodes/validates/uploads once and retains no decoded pixels. Rendering performs prepared binds without resource IO. Company identity and Normal/Defensive Build selection use already-published records; no packet, server request or schema change is added. Shared drawing/hover/picking bounds and precedence remain unchanged. See the [art/resource-pack contract](../../../../src/main/resources/assets/kome/textures/gui/map_faction_markers.README.md), [asset tests](../../../../src/test/java/kome/client/KOMEMapMarkerArtTest.java) and [bound-texture recovery tests](../../../../src/test/java/kome/client/KOMEMapMarkerTextureRecoveryTest.java). Review/live acceptance must cover a previously bound texture across failed/repaired reloads, valid/HD pack switches, original paths, owned cleanup and interactions. The global engine can still remove a pack before KOME's callback if an independently registered native texture elsewhere throws during reload; KOME does not globally intercept that behavior.

## Verification and gaps

Existing references: [server queue](../../../KOME_SERVER_TASK_QUEUE.md), [conquest sync](../../../KOME_CONQUEST_SYNC.md), [records](../../../KOME_SERVER_RECORDS.md), [GUI handoff](../../../KOME_GUI_HANDOFF.md). Tests: [server queue](../../../../src/test/java/kome/common/network/KOMEServerTaskQueueTest.java), [client queue](../../../../src/test/java/kome/client/KOMEClientTaskQueueTest.java), [sync](../../../../src/test/java/kome/common/network/KOMEConquestSyncTest.java), [record presentation](../../../../src/test/java/kome/client/gui/KOMEGuiServerRecordsPresentationTest.java).

```powershell
.\gradlew.bat test --tests 'kome.common.network.KOMEServerTaskQueueTest' --tests 'kome.client.KOMEClientTaskQueueTest' --tests 'kome.common.network.KOMEConquestSyncTest' --no-daemon --max-workers=2
```

Use [shared prerequisites](../../INDEX.md). Trace persistence Ã¢â€ â€™ packet construction/delivery Ã¢â€ â€™ client projection Ã¢â€ â€™ GUI eligibility. Test disconnect/reconnect, dimension change, rejected requests, and reopened screens with a connected client before claiming visible correctness.

At this baseline [KOMEPacketProgressionData.Handler](../../../../src/main/java/kome/common/network/KOMEPacketProgressionData.java) calls `updateProgressionData`; the client proxy updates progression UI/title summaries. Do not assume that also updates `KOMEClientData.progressions`, which permissions read. The separate waypoint-cache correction is not present here; investigate that boundary for a persisted unlock that still appears locked. Packet-specific application must be checked even when a general client queue exists.

## KOM-18 merge compatibility

The merged g13 discriminator layout retains dev's progression identities at 59, its objective/eligibility payloads, and KOM-84's correlated Join Battle shapes at 53-58. The KOM-85 company-visibility correction advances the matching-artifact handshake to g14 because conquest company/movement rows and their publication header changed; g13 clients cannot safely decode them. The removed KOM-89 presentation workaround belonged to the incompatible 1.0.8 artifact. No custom hired-presentation queue or packet is restored. ID 60 remains unused. Dev-only offline identity opt-in and its credential guards remain in Gradle.

## KOM-84 reconciled packet and workflow boundary

ID 60 remains unused. Destination queries use 61/62, confirmed single-company movement uses 63/64,
source metadata/source-scoped destination queries use 65/66, and Rally sessions use 67/68.
KOM-84 payloads are unchanged by discriminator reassignment. Matching g18 artifacts are required;
g13, g12 and 1.0.8 are rejected. Server intents retain the bounded server-thread handlers;
tactical ID 48 continues to own its separate bounded queue.

Map Move Here, Move To, Rally, root Join and tile-local Units share connection-fenced client state.
Rally executes only frozen server-preview inclusions through fresh canonical company submissions;
Units uses read-only source metadata. Join screen ownership/correlation, success-only close and
non-pausing command screens remain client presentation rules, not deployment authority.
Verify registration/protocol/public-access tests, company network tests and Join GUI tests alongside
Campaign health/recovery/reconstruction and ordinary battle suites after reconciliation.

## KOM-91 return surface recovery (Slice 1)

Join Battle return placement keeps the exact persisted pre-entry pose first. If unsupported,
`KOMEJoinBattlePhysicalAccess.resolveReturnPlacement` uses the center column and deterministic
nearest-first columns within the existing +/-8 X/Z envelope. Each column probes nine support
blocks near saved Y and five near the native heightmap, deriving standing Y from collision-box
tops and retaining full terrain/liquid/body and mount/rider clearance checks. There is no full
vertical column scan: at most 289 height lookups, 4,046 support-block probes and 4,047 placement
validations including the exact-pose check. These are candidate-generation limits; existing
collision/footprint validation performs its normal bounded local checks as well.

The canonical captured tile resolver fences fallbacks to the original player return tile when
that anchor resolves. Mounted placement uses the persisted mount base but the player's return
anchor owns this fence. An unresolved source receives only the same local physical search;
no tile, spawn or strategic destination is substituted. There is no flight exemption inferred
from current capabilities. No receipt/schema, conflict-end, registry, retry or transfer-phase
change was part of Slice 1. Slice 2 below extends only egress recovery and diagnostics.

Regressions: `KOMEJoinBattleReturnGeometryTest`, `KOMEJoinBattleSurfaceRecoveryTest`, and the
ordinary-resolution integration in `KOMEOrdinaryBattleResolutionServiceTest`. Retain existing
mounted publication, entry/persistence, Formal Retreat and canonical tile-resolution suites.
Automated verification does not substitute for live return/terrain confirmation.

## KOM-91 same-source-tile fallback and diagnostics (Slice 2)

`KOMEJoinBattlePhysicalAccess.prepareEgress` keeps exact/local recovery first, then
uses the read-only `KOMEJoinBattleReturnAuthority`: saved WorldData RALLY first,
packaged `KOMETileGameplayDefaults.getArrivalDefault` second, with duplicate anchors
removed. Every canonical anchor, rider offset and search candidate must resolve to
the original persisted player source tile in the source dimension. Gaps, exclusions,
other tiles and historical out-of-tile arrival exceptions never authorize fallback.
Unresolved original anchors receive exact/local recovery only. Entry rollback still
uses the original resolver without the canonical fallback extension.

Each of at most two canonical anchors reuses Slice 1's center-inclusive +/-8 surface
search and physical checks. Across original, saved and packaged searches the maximum
is 867 height lookups, 12,138 support probes and 12,141 placement validations; existing
footprint/collision queries remain additional bounded work. Closure still follows
verified physical application only. Mounted interrupted-egress discovery loads bounded
original/fallback areas and the current same-source-tile player area to find the exact
recorded UUID; that discovery pose does not authorize a return destination or missing-evidence
recreation. UUID/type/profile, snapshot, attachment, uniqueness, rollback and publication
contracts remain in force. Formal Retreat gates run before physical preparation.

`KOMEJoinBattleEgressService` classifies real failed attempts without receipt fields.
The existing capped persistent audit records receipt/player/conflict/source-tile identity
and concise classification/reason. It compares the latest retained event for that exact
receipt, so identical retries (including after restart) do not append again; changed
category/reason does. The global audit cap remains 500. Login waits, five-second polling,
60-second physical-failure backoff and durable-receipt restart recovery remain unchanged.

`KOMEPacketJoinBattleViewResponse.forPlayer` uses the existing `reasonText` field for
RETURNING. `pendingReason` maps classifications to concise player wording without raw
receipt/conflict/player IDs, using runtime failure state or the last retained audit after
restart. No packet shape/protocol change. Truly impossible source authority stays pending;
operator repair remains separate work.

`KOMEJoinBattleSameTileFallbackTest` covers real search/application/closure, unrelated
later Join, mounted transfer/interruption, boundary rejection, default rejection, NBT
restart, diagnostic suppression/change, player text and Formal Retreat precedence.
See [Slice 2 evidence and concurrency boundary](../../../kom91-slice2/README.md).
These deterministic tests do not claim connected-client or live acceptance for Slice 2.

## KOM-88 progression tracker render isolation

`KOMEProgressionTrackerOverlay.drawTracker` owns an exception-safe all-attribute and
matrix scope around its textures, progress bar, text, item and chat-edit outline.
Both the HUD and chat redraw use that same protected path. Native LOTR alignment
renders later at FML render-tick END and relies on inherited alpha/blend state;
the tracker restores its incoming state rather than overriding native alignment.
Minecraft 1.7.10 binds textures directly, so no ResourceLocation restoration cache
is introduced. `KOMEProgressionTrackerOverlayTest` checks scope coverage and normal/
exceptional cleanup in bytecode; manual live visual acceptance remains required.

## KOM-97 destination browser budget continuation

Move Here and Attack both open `KOMECompanyDestinationSelector` through
`KOMEConquestMapOverlay.openCompanySelector`. The destination query already returns
completed rows, a catalogue token and the last completed company ID when its shared
arrival budget expires. The next request excludes that completed prefix and evaluates
the first deferred company with a fresh budget. Routes exceeding the individual
limit remain explicit unknown-eligibility rows, never eligible or denied guesses.

The selector now accepts `ARRIVAL_BUDGET_EXCEEDED` as a recoverable page only with
`hasMore`, a nonempty token and a strictly advancing cursor (and ordered rows ending
at that cursor when present). It applies the same retained-catalogue identity check
as ordinary pages. The existing bounded rolling window and All/Ready/Arrive Now
filters retain only authoritative projections; zero matches do not end continuation.
The panel offers Continue instead of restarting the prefix with Refresh. Filter and
browse follow-ups still stop after three pages per gesture; throttle responses wait
for their server deadline and retry the outstanding cursor. Nonadvancing evidence
fails closed without an automatic retry loop. No server or protocol change.

Verification: `KOMECompanyDestinationQueryTest`, `KOMECompanyDestinationSelectorTest`,
`KOMECompanyDestinationPagingTest`, `KOMECompanyDestinationBrowsingTest`, and
`KOMEMapRadialInteractionTest` pass (176 tests). The paging regression first reproduced
ERROR on a real budget-exhausted query, then traversed ten companies in four bounded
pages without skips or duplicate accepted rows. Radial adapter tests cover both
Move Here and Attack, including movement confirmation and adaptive Join eligibility.
Move To/source queries, Rally and Units retain their separate authority and flows.
These automated checks do not establish connected-client/live acceptance.


The opt-in [map CPU profiler](../../../../src/main/java/kome/client/KOMEMapProfiler.java) samples the existing territory render/cache boundaries without changing their policy. It is disabled by default and enabled only by the Minecraft JVM flag `-Dkome.mapProfile=true`; the [geography guide](../geography/README.md) documents bounded summaries, live capture commands, timing limits and synthetic-versus-live evidence. No configuration key or network data is added.

## KOM-85 stable company shields and Build label priority

[KOMEPacketUnitMapMarkers](../../../../src/main/java/kome/common/network/KOMEPacketUnitMapMarkers.java) now projects recipient-visible committed Campaign membership independently of loaded NPCs. Exact company ID/member/owner/tile coherence and existing committed-lifespan checks exclude removed, returned, terminal, ordinary, farmhand and genuinely departed records. Stationed, locally halted and committed intermediate waiting companies remain eligible under the shared tile-presence decision below. Loaded living NPCs supply the weighted physical center; zero loaded units explicitly means strategic tile presence. No packet fields, codecs or persisted records change, and no chunks are loaded. The client uses the existing linked-waypoint/raster-center tile presentation anchor for that case, never a guessed physical location or tile faction. Missing anchors or mismatched/missing current company snapshots suppress strategic-only artwork until valid inputs arrive. Strategic tooltips do not claim a physical combatant or protection state. Company population is counted once and deducted once from generic stationed summaries.

### KOM-85 recipient-specific company visibility

[KOMECompanyMapProjection](../../../../src/main/java/kome/common/network/KOMECompanyMapProjection.java) is the server-side visibility and presentation boundary shared by conquest snapshots and unit-map markers. Ordinary recipients see companies whose canonical `KOMEArmyCompany.faction` matches their current LOTR pledge; `nativeFaction` remains artwork provenance only. A foreign company is visible solely to a currently valid temporary controller/delegate under existing command-authority validation, or to an authorized operator who has not opted out of administrative marker visibility. A bare owner UUID after faction loss does not grant visibility. Unpledged recipients have no faction-wide view. Visibility grants no command, movement, retreat or editing rights; the tile-local Units browser and command handlers continue to authorize requests separately.

The server filters `ArmyCompanies` and company-linked `ArmyMovements` **before** recipient comparison, caching and chunking. Company rows carry only ID, name, strategic/native faction, current tile and presentation status. Movement rows carry the linked ID/name, route tiles, map position, status, population/count and ETA fields needed by the accepted map/capture display. They omit owner/controller/delegation, unit UUIDs, movement credit and recovery internals. Client decoding now reads these reduced rows rather than persistence NBT. Section names and discriminators stay fixed, but the conquest payload and header are a new g14 contract; a publication serial prevents older completed or partial batches from restoring revoked company data. Reconnect still clears the client snapshot and marker session. First pledge, manual/automatic controller changes and admin opt-out changes refresh conquest publication; the periodic marker stream follows the same predicate. Tile capture suppresses incoming movement ETA for foreign/unpledged non-admin viewers. Public territory, geography and the existing global `TroopSummaries` projection are unchanged; aggregate statistics may still reveal population and movement counts at a tile but contain no exact company IDs or routes.

[KOMEUnitMapMarkerPublisher](../../../../src/main/java/kome/client/KOMEUnitMapMarkerPublisher.java) receives a codec-copied full replacement through the common proxy. The existing client task queue coalesces that stream; connection identity and lifecycle generation fence application. Disconnect/reconnect discard old work, world unload invalidates pending publications and clears the marker list. No network thread mutates that client list. Native faction/resource-pack selection remains in the existing marker-art boundary.

Conquest snapshot intake now also passes the packet's actual client connection identity to [KOMEConquestSnapshotPublisher](../../../../src/main/java/kome/client/KOMEConquestSnapshotPublisher.java). A packet from a disconnected/replaced connection is rejected before chunk assembly, and queued callbacks are generation-fenced. World unload discards incomplete assembly and pending publication while retaining the current connection's serial floor; a fresh forced snapshot from the same connection can then advance it. A completed company snapshot gates marker drawing and aggregate deductions immediately after revocation, even if the older periodic marker list or a late marker packet still contains that ID. The marker stream continues to refresh about once per second; this correction adds no per-frame scan or extra marker broadcast. Capture packet regression coverage now serializes and decodes the actual response for a foreign viewer, preserving public tile/population fields while asserting zero incoming ETA and no company/order identity.

Build sprites now share the guarded terrain-exit seam after geography, before both native waypoint passes and region labels. Per-frame attempt ownership prevents duplicate submissions; Post consumes only the tooltip. Failed/incompatible hooks omit sprites and picking instead of covering native names. Native text, matrix/cache/territory work and panel precedence remain unchanged. The obsolete generic troop PNG path, decoder and dynamic texture slot are removed; aggregate counts keep existing text/zoom eligibility, and movement keeps its neutral diamond. The eight settled native zoom scales are 0.125, 0.25, 0.5, 1, 2, 4, 8 and 16; company shield sizes at those stops are 7, 8, 9, 9, 12, 14, 17 and 18 GUI pixels. Native scale 16 is the full-size endpoint, with 18x18 shields, 10x20 banners and 10x10 castles. See the [asset contract](../../../../src/main/resources/assets/kome/textures/gui/map_faction_markers.README.md) for the interpolated size policy. No independent Build capture authority is introduced: map and Build details identify population allegiance rather than current capture ownership.

### KOM-85 tile-presence reconnaissance (g15, superseded disclosure)

This section supersedes the preceding g14 claim that the global `TroopSummaries` census is sent unchanged.

A committed Campaign company with verified current members spots only its canonical tile for its personally authorized owner or currently valid controller/delegate. A movement-order ID alone does not remove presence: intermediate `WAITING_NEXT_STEP` and locally halted/held orders qualify when the order and company tile agree, a member has a committed local snapshot or locator, and no transit snapshot remains. Actual departure and pending reconstruction remove presence until arrival commits. Faction peers may see their faction's company but cannot use it as a spotter. The server reuses this recipient view for foreign company presentation rows and shield-marker publication; foreign movement routes remain private. An observed tile may contain several factions. `ArmyCompanies` rows include verified presentation `Population` and still omit owners, members, delegation and movement credit. A present company projects a stationed map status while its separate authorized order row retains route status. `TroopSummaries` counts verified waiting members once and carries recipient-visible stationed Campaign membership and directly authorized movement aggregates, not a mixed global troop census in unobserved tiles. The tile tooltip labels the sum as a visible Campaign total. The capture response suppresses foreign owner-faction troop counts in unobserved tiles and foreign movement counts/ETA regardless of observation. Public faction population-bank projections, geography, territory and server command rights are unchanged. The changed conquest payload requires matching g15 client/server artifacts; section names, discriminators, snapshot serials and client session fences remain unchanged. Clustering, watchtowers and route polish remain unimplemented.

### KOM-85 shield-only reconnaissance and soldier counts (g16, current)

The g15 presence decision above remains in force, including committed intermediate waiting and locally blocked companies. A personally authorized spotter now reveals a foreign company's **faction shield and surviving committed member count only**. The server substitutes a tile-local opaque display key and sends a spotted `ArmyCompanies` row containing that key, strategic faction, current tile and presentation status. Its `NativeFaction` field repeats the strategic faction solely for shield selection; the company's actual native provenance is withheld. Name, canonical ID and population cost are omitted. Its matching marker carries the shield key, tile and `soldierCount`, with no name, cost, mounted/protection flag or physical coordinates. Spotted shields have no detail hover or click inspection. Unobserved tiles receive no foreign row or marker. Same-faction, valid controller and operator projections retain their separately authorized detail.

`TroopSummaries` now sums only directly authorized company costs; observed foreign cost is never included in a mixed stationed total. Tile capture reconnaissance no longer unlocks the ruling faction's troop-cost breakdown. Public faction-wide population-bank projections are unchanged. The shield number uses one surviving committed Campaign member record per soldier, regardless of loaded NPCs or injury cost; a rider and mount remain one count unless separate canonical member records exist. It is best-effort at the existing publication cadence, not a live health feed. KOM-84's tile Units browser remains owner-filtered, and company detail browsing retains its independent owner/controller/operator authorization. g16 adds the marker `soldierCount` wire field, so matching g16 client/server artifacts are required; packet discriminators, session fences, snapshot serials, and world schemas are unchanged. Clustering, watchtowers, route styling and company editing remain unimplemented.

The independent g16 review closed three read-boundary gaps. Capture response costs now exclude Campaign members and linked orders unless the recipient's **current** company read view directly authorizes them; record owner UUID alone cannot restore a former faction's costs in `my*` fields or owner-faction totals. The `/troops companies`, `/troops company`, list, tile, unit, moving, arrivals, locate and route-find read paths, plus KOM-84's owner-filtered tile Units browser, apply that same detail decision to company-linked records. Normal non-Campaign units retain their owner access. Command/movement eligibility is separate and unchanged. Spotted display keys are now process-secret HMAC-SHA-256 values over recipient, canonical tile and company ID, rather than sorted tile positions. The client receives only the opaque result; separate snapshot and marker publishers derive the same key, removal cannot renumber a survivor, and a tile change produces a different key. Completed snapshot, marker-session and publication-serial fences still reject stale rows. This changes key values, not packet structure, so matching g16 artifacts remain sufficient; no new wire or world schema is added.

The subsequent g16 read-response correction applies that current read view to capture's `canMoveTroops` presentation bit, `/troops tile <company> status`, and command preview replies. Unreadable preview requests produce a generic denial before route evaluation; they receive no company name, origin, allowance, route or confirmation packet. Destination, source, exact-target and Rally catalogues apply the same read decision before producing company rows. Rally rechecks read visibility across an open session and publishes a generic error on revocation. These are response filters, not changes to `canPlayerConsiderCompany`, movement submission, or canonical company authority; the g16 wire layout and discriminator assignments remain unchanged.

Current valid temporary delegation or stewardship command authority also grants full company read visibility, even if a live pledge and the last recorded player faction briefly differ. The final movement executor revalidates the same delegation authority and can accept such an order; a bare historical owner UUID does not pass its current-faction movement check and still grants no privileged read. Map movement submission keeps canonical execution unchanged, but normalizes an unreadable rejected target and an unknown target to the same `UNKNOWN_COMPANY` response code and generic detail. Readable successes and rejections retain their existing detailed results. No packet layout or protocol version changes.

The subsequent former-owner guard uses [KOMECompanyMutationAuthority](../../../../src/main/java/kome/common/command/KOMECompanyMutationAuthority.java) at command and GUI mutation entry points. A historical owner must still have the company's current strategic faction as their live LOTR pledge; a valid temporary controller uses the existing delegation/stewardship validator, while recognized pledged rulers and operators retain their action-specific powers. Tendency, transfer/cancellation, reclaim, tile halt/resume, snapshot and GUI rename are checked before mutation or chunk/snapshot work. Movement-order controls resolve the current company instead of trusting the order's old owner UUID. Administrative rebuild remains operator-only. Same-faction read visibility and spotted enemy shield visibility never grant mutation. Native-faction rulers who can administrate a company with a different strategic faction receive the corresponding direct read exception. No packet or save layout changes.

The legacy `/troops companies` catalogue and `/troops company <id>` detail filters also use that native-faction ruler read exception from `KOMECompanyMapProjection`. A currently pledged, recognized ruler can inspect a company they may delegate even when another player owns it or its strategic faction differs. This does not expand ordinary same-faction peer detail access or any mutation right; former owners and spotted foreign companies remain subject to their existing read limits.

The `/troops company <id>` detail command returns the same `Company unavailable.` denial for an unknown ID and an existing ID the current server read decision does not permit inspecting. The denial does not echo the requested ID or scan physical company members. Authorized owners, controllers, rulers and operators retain detailed output; tile-only foreign spotting remains insufficient.

### KOM-85 opt-in administrative map vision

[KOMEAdminMapVision](../../../../src/main/java/kome/common/network/KOMEAdminMapVision.java) now owns a server-thread, connection-scoped map-vision flag, OFF on login and reconnect. `/kome adminvision on|off|status` controls it for level-2 operators; the older `/kome adminmarkers` spelling remains an alias. The persisted `AdminUnitMapMarkerOptOuts` record is retained for save compatibility but no longer grants map vision or receives new writes. An operator's explicit `/troops` and other administrative command reads remain authorized independently. Map-initiated company and tile Units browsers use the map flag instead of command OP status.

g17 fences map-origin capture, company-list, and population-Units responses to the
server publication current when each response was built. The response carries that
serial; the client checks it and the exact connection at task execution, after the
referenced snapshot has applied. An OFF/OP-loss replacement, world change, reconnect,
or later ON makes queued old responses ineligible. Explicit staff command list/Units
responses remain separate from map vision. This extends three response wire payloads
without changing discriminators, command authority, or persistent data.

g18 retains the execution-time serial and connection fence, and correlates each
client map-detail intent with an echoed request ID. If an unrelated newer conquest
snapshot makes the first response stale while that intent remains active, the client
discards its data and makes at most two read-only re-queries. The server rebuilds each
replacement under current pledge, company, ruler, delegation and map-vision rules;
only a current response for the same request and screen can open. Superseded,
dismissed, disconnected and world-replaced intents are discarded. The refresh never
replays a mutation command. The two map request packets and three detail responses
gain request IDs, requiring matching g18 artifacts; discriminators and saves do not
change. Explicit staff command responses remain independent of map vision.
Move Confirm Cancel explicitly invalidates its pending map-detail request; closing
that screen to null is not treated as intentional navigation. Population and Units
navigation retain their screen-close allowance.

Conquest company/movement rows, TroopSummaries, unit-map shield markers, capture-map troop presentation and map-launched destination/source/Rally catalogues all use the effective flag (opt-in **and** current OP permission). OFF and OP loss send a complete filtered conquest replacement before replacing marker lists. A cheap per-player tick check revokes an enabled flag on OP loss; merely regaining OP cannot re-enable it. The ordered client snapshot publisher replaces company, movement and summary maps together after the final chunk, with its existing serial and session fences. Marker coalescing moves a newer replacement behind an intervening conquest publication, preventing the old privileged TroopSummary from appearing as a standalone number after its shield disappears. When a completed view removes company, movement or summary information, open map browser/detail panels and pending destination selection are invalidated. Explicit command authority, canonical records, packet fields and world schemas are unchanged. Live multiplayer timing remains to be verified.

### Final company-marker review corrections

Company drawing and tooltip selection use one production traversal in KOMEConquestMapOverlay.renderCompanyMarkers. The client-only [KOMECompanyShieldClusters](../../../../src/main/java/kome/client/KOMECompanyShieldClusters.java) groups overlapping projected shield rectangles, including transitive overlap, in deterministic ID order. A singleton retains its shield and surviving-unit label; its above-shield label origin is two GUI pixels lower than the generic troop-summary label, with the same font and styling. A multi-company group draws at the arithmetic mean of its members' projected shield centers. Its compact composite reuses the same native or fallback shield resource for three layers at all eight native zoom stops, with two shields above and one centered below; horizontal offsets remain one or two pixels according to shield width. The ordinary label threshold still hides numbers below native scale 1. Its background-free bottom-right badge counts companies in subdued warm gold, using the singleton label's font, scale, outline and visibility threshold; it has no member-unit label. Mixed faction/art groups layer only the neutral shield. Primary-shield bounds determine cluster visibility; offset layers and badge text crossing a viewport edge are scissored. Picking uses only the visible shield or badge region inside the viewport, so clipped pixels create no off-map target. The independently polled Build picker checks the same visible cluster hit before sending a capture-detail request, so one press cannot open both a Build and a cluster even when poll and native click order differ. Native waypoint and map-control priority remain intact.

[KOMEGuiCompanyClusterRoster](../../../../src/main/java/kome/client/KOMEGuiCompanyClusterRoster.java) is now a map-side panel model, not a separate screen. It reuses the KOM-84 side-panel chrome and rectangle/input conventions but reads only the exact clicked cluster's authorized projection; it never sends a tile-wide Units query. Its compact right-side layout begins below the native/KOME right controls, owns only its visible rectangle, and leaves map panning and zooming available outside it. Row selection displays read-only projected information and creates no command capability; spotted rows show only shield and unit count. Before every draw, the panel re-resolves authorized rows and the projected group; departure, separation, added members, revocation or session replacement closes it before stale rows render. Equivalent marker replacement and unrelated territory revision leave it open. Marker publication and completed snapshot replacement also revalidate it. Connect, disconnect and remote-world unload advance the client-only session fence before any queued full reset. Closing sends no detail request, so it cannot refresh or reopen a stale query. The tile-wide Units browser and command authorization remain separate. This map-side presentation has source/test coverage but awaits Minecraft live acceptance.

KOMEClientData.hasCompleteConquestSnapshot distinguishes initial unavailability from authoritative absence. Only completeConquestUpdate, reached through the canonical completed-snapshot publisher, sets this client-only readiness flag. resetClientState clears it on the existing queued session reset; conquestRevision retains its existing meaning. Before the first complete snapshot, an unknown physical marker may use neutral art. After completion, missing company IDs are immediately ineligible for drawing, tooltips and aggregate deductions, even if an older unit-marker replacement arrives later. Incomplete replacement chunks do not change current authority, and current matching companies retain unloaded tile presence. No second company registry, wire field, server membership or authority change is added. Existing queue coalescing, connection/world fencing and native texture reloads are preserved. These source/test checks do not establish connected-client or GL-driver acceptance.
Focused verification passes 350 tests with no failures/errors/skips, preserving the previous 345 cases. Five new RED cases reproduce overlap/removal failures through production traversal and canonical packet publication. The normal Gradle jar task produces a fresh development artifact; packaged overlay, client data and marker publisher classes match current compiled bytes, obsolete troop texture references/assets are absent, and all six GUI PNGs match source bytes. Evidence: build/kom85-review-final-marker-blockers-* and build/kom85-final-marker-blockers-jar-verification.txt. No Minecraft launch, deployment or live-driver acceptance was performed.

The 50% icon-sizing follow-up changes only client sizing interpolation: shields become 9x9 at 50%, while banners/castles retain the 6x12/6x6 floor required by the accepted 37.5% sizes. A smooth monotonic size-only zoom delay is confined between 37.5% and 67.5%; all other named accepted stops, opacity, geography scaling, company identity/publication, Build ordering, input ownership and native resource-pack behavior remain unchanged. See the geography route and art contract for the exact curve and tradeoff.

The opt-in [native zoom marker trace](../../../../src/main/java/kome/client/KOMEMapMarkerTrace.java) observes the existing individual-company and Normal/Defensive Build sprite submissions without changing marker publication, render order or picking. With `-Dkome.mapMarkerTrace=true` on the Minecraft JVM it reports one bounded animated-to-settled comparison for each available category, including identity, original texture path, native zoom state, submitted GUI vertices, GL matrices, projected framebuffer size and loaded JAR SHA-256. The [geography guide](../geography/README.md) gives the capture command, log location and evidence limits. No client preference, packet or server state is added; the trace remains disabled by default.
