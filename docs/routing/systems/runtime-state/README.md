# Runtime, persistence, configuration, and diagnostics

Phase 1 **cdbc051** adds a nested progression migration: `wanderer`/`serf` read as
`commoner`, with `RankFoundationVersion=1` and original `LegacyStartingRank` saved
additively. Higher rank keys, service/history NBT and native alignment/pledge storage
are preserved. No root-schema bump or birth award is introduced. Matching wire
identity is now g15; earlier g14 notes are historical. See
[migration fixtures and evidence](../../../development/PROGRESSION_PHASE1_REPORT.md).

[Index](../../INDEX.md) · [Save/load flow](../../flows/save-load.md)

Owns addon startup, server lifecycle, canonical strategic storage, validated configuration, and administrative diagnostics. Domain services own their rules; this guide locates shared infrastructure.

Pass 3 adds `ServiceRecognition` v1 (migration v1) inside existing player progression,
with at most two earned service/recommendation records. Root schema 12 and strategic
schemas remain unchanged. Native regional-seat receipts live in the NPC entity's
normal persisted data under the regional-seat tag (version 1), not a new world placement ledger.
`KOMEServicePromotionService.migrate` retires old trials once on login/report with
loaded encounter cleanup; orphan actors/controllers reconcile on natural load.
No migration promotion, item reward or ceremony is replayed. Exact evidence and
legacy boundaries are in the [report](../../../PROGRESSION_PASS3_SERVICE_PROMOTION_REPORT.txt).

October 15 dev sync preserves the service migration and strategic schemas.
`KOMECorePlugin` registers both `KOMEMapInputTransformer` for native map input and
`KOMEProgressionSeatTransformer` for native regional-seat receipts, with all existing
Campaign health, waypoint, Aqua and integrated mob hooks retained. Automated ASM and
server-loading checks are distinct from pending live Forge/client acceptance.

Pass 4 adds only an optional version-1 native respawner receipt under
`KOMEProgressionServiceLocation.REPLACEMENT_TAG` in ordinary Forge entity data.
The existing registered `KOMEProgressionSeatTransformer` observes copy and successful
native spawn; it does not add packets, root schema versions, NPCs or saved-world
registries. Existing regional proof remains version 1 and is rebound to a verified
replacement UUID. Pre-existing untagged NPCs/carriers are not auto-adopted. Headless
native NBT round-trips and ASM checks remain separate from live save/restart acceptance.

## Authority and entry points

- [KOMEAddon.preInit](../../../../src/main/java/kome/common/KOMEAddon.java), `init`, `postInit`, `serverStarting`, and [KOMECommonProxy.init](../../../../src/main/java/kome/common/KOMECommonProxy.java): module initialization, events, commands, GUI/network registration. [KOMECorePlugin](../../../../src/main/java/kome/core/KOMECorePlugin.java) registers KOME, LOTRMoreMobs, and Aqua ASM hooks.
- [KOMEWorldData.get](../../../../src/main/java/kome/common/data/KOMEWorldData.java), `initializeIntegratedWorld`, `readFromNBT`, `writeToNBT`: server `MapStorage` / `WorldSavedData` named `KOME_ServerRules`; client lookup returns a projection. Root schema is 12 at the October 10 refresh; nested sections have separate versions. Initialization occurs at server START, not merely on lookup.
- [KOMEEvents.onServerTick](../../../../src/main/java/kome/common/data/KOMEEvents.java): initialization, queued requests, shared-data deduplication, campaign cadence. [KOMEPopulationPayoutRuntime.onStartup](../../../../src/main/java/kome/common/data/KOMEPopulationPayoutRuntime.java) anchors movement and startup population/development state.
- [KOMEConfigRegistry](../../../../src/main/java/kome/common/config/KOMEConfigRegistry.java), [KOMECampaignReadinessValidator](../../../../src/main/java/kome/common/config/KOMECampaignReadinessValidator.java): `kome.cfg`, effective snapshots/readiness; integrated modules have separate configuration owners.
- [KOMEAdminDiagnosticsCommands](../../../../src/main/java/kome/common/command/KOMEAdminDiagnosticsCommands.java), [KOMEAdminRepairService](../../../../src/main/java/kome/common/data/KOMEAdminRepairService.java), [KOMEAuditService](../../../../src/main/java/kome/common/data/KOMEAuditService.java): `/kome` inspection, guarded repair tokens, bounded persisted audit history. See [scoped diagnostics](../../../KOM-40-admin-diagnostics.md) for command syntax and repair boundaries.

## Dependencies and checks

Pass 5 adds nested commission NBT version 2 fields (tier, phase, seeded complication,
theft/alarm/target, acceptance timestamp, commander and bounded battle evidence).
Missing tier reads as zero: accepted legacy objectives retain original rules.
Original six type IDs/order and old histories remain; new types append. Root
world schema and g14 wire identity are unchanged; text/marker projections use
existing packet contracts. Knight service saves a separate testing-session flag;
forced actor reward-denial provenance is saved in entity NBT. UUID slots/leases are
reserved before spawn. New encounters never respawn individual missing UUIDs:
loaded absence triggers bounded transactional recovery; unloaded chunks wait.
No distant force loads or follower teleport restoration occur. Deceased legitimate
Liege recovery retains report-ready evidence; betrayal cleanup remains separate.
See [Pass 5 report](../../../PROGRESSION_PASS5_KNIGHT_FIELD_SERVICE_REPORT.txt) for
backup/recovery, reload tests and outstanding live restart acceptance.

Storage consumers include every strategic guide. Physical entities/chunks, native LOTR data, [character NBT/skins](../character-creation/README.md), and [gate/ram ledgers](../physical-siege/README.md) have additional save authorities. [Networking](../client-network/README.md) publishes projections; saving a client cache cannot repair server data.

KOM-90 level reconciliation uses the existing `ConquestTiles[].WaypointLevel` field and root schema. After saved tiles load, `applyWaypointDefaults` raises an invalid or lower saved level to the curated active-tile baseline without lowering a valid higher level or changing ownership. After authoritative Build records load, `KOMEConquestBuildPromotionService.promoteLoadedTiles` raises tiles that qualify from currently approved active Build hours; it never lowers a saved level. Existing ordinary battle response timers retain their stored duration snapshot; newly started episodes select from the current tile level. The [geography route](../geography/README.md) records the bundled baseline, and the [Build route](../build-population/README.md) records promotion authority.

[Atomic-load tests](../../../../src/test/java/kome/common/data/KOMEWorldDataAtomicLoadTest.java), [schema tests](../../../../src/test/java/kome/common/data/KOMEWorldDataSchemaTest.java), and [actual diagnostics-command tests](../../../../src/test/java/kome/common/command/KOMEAdminDiagnosticsCommandsTest.java) cover candidate publication, mandatory sections, and non-loading inspection paths.

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMEWorldDataAtomicLoadTest' --tests 'kome.common.data.KOMEWorldDataSchemaTest' --tests 'kome.common.command.KOMEAdminDiagnosticsCommandsTest' --no-daemon --max-workers=2
```

Follow [shared prerequisites](../../INDEX.md). On a disposable world, save/stop/reload and compare serialized domain records and module state; a clean exit alone proves no persistence invariant. Malformed mandatory data rejects publication and blocks writes. Do not bypass this with default regeneration. Preview tokens are transient and actor/world/current-state bound. Diagnostics must not load an unavailable dimension merely to inspect it; inspect command callers as well as the service.

Historical schema numbers in README and dated validation documents are not the current root schema. [KOMESeasonResetService](../../../../src/main/java/kome/common/data/KOMESeasonResetService.java) and [KOMESeasonResetDeployment](../../../../src/main/java/kome/common/data/KOMESeasonResetDeployment.java) now own durable reset return/recovery. [KOMEDailyCoordinator](../../../../src/main/java/kome/common/data/KOMEDailyCoordinator.java), [KOMEDailyJournal](../../../../src/main/java/kome/common/data/KOMEDailyJournal.java), and [KOMEWorldCheckpoint](../../../../src/main/java/kome/common/data/KOMEWorldCheckpoint.java) provide resumable population sequencing/checkpoints, with unavailable owning adapters explicitly blocked. [KOMEEvents.processCampaignTick](../../../../src/main/java/kome/common/data/KOMEEvents.java) routes active reset before governance/daily/ordinary campaign processing. See [campaign lifecycle](../campaign-lifecycle/README.md) and [daily flow](../../flows/daily-movement.md).

## KOM-18 integration

The integrated writer keeps root schema 12 and adds ConflictData v3 recovery while retaining Join Battle receipts. Recovery WAL v1, Campaign health v1, PhysicalLocator v1 and ConflictMovementRelease v2 remain independently versioned. Root-11/v3 and root-12/v2 upgrades preserve their existing sections; legacy conflict coverage does not invent combat participants or recovery grants.

Recovery startup opens and validates the root-world WAL before publishing the detached world candidate. Daily and reset checkpoints resolve that same root world, even when called from a dimension with separate MapStorage. See [integration tests](../../../../src/test/java/kome/common/data/KOMEOrdinaryBattleDevIntegrationTest.java), [startup tests](../../../../src/test/java/kome/common/data/KOMERecoveryStartupTest.java), and [restart tests](../../../../src/test/java/kome/common/data/KOMEOrdinaryBattleRestartTest.java).
