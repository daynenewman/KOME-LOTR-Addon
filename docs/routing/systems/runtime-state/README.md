# Runtime, persistence, configuration, and diagnostics

[Index](../../INDEX.md) · [Save/load flow](../../flows/save-load.md)

Owns addon startup, server lifecycle, canonical strategic storage, validated configuration, and administrative diagnostics. Domain services own their rules; this guide locates shared infrastructure.

## Authority and entry points

- [KOMEAddon.preInit](../../../../src/main/java/kome/common/KOMEAddon.java), `init`, `postInit`, `serverStarting`, and [KOMECommonProxy.init](../../../../src/main/java/kome/common/KOMECommonProxy.java): module initialization, events, commands, GUI/network registration. [KOMECorePlugin](../../../../src/main/java/kome/core/KOMECorePlugin.java) registers KOME, LOTRMoreMobs, and Aqua ASM hooks.
- [KOMEWorldData.get](../../../../src/main/java/kome/common/data/KOMEWorldData.java), `initializeIntegratedWorld`, `readFromNBT`, `writeToNBT`: server `MapStorage` / `WorldSavedData` named `KOME_ServerRules`; client lookup returns a projection. Root schema is 12 at the October 10 refresh; nested sections have separate versions. Initialization occurs at server START, not merely on lookup.
- [KOMEEvents.onServerTick](../../../../src/main/java/kome/common/data/KOMEEvents.java): initialization, queued requests, shared-data deduplication, campaign cadence. [KOMEPopulationPayoutRuntime.onStartup](../../../../src/main/java/kome/common/data/KOMEPopulationPayoutRuntime.java) anchors movement and startup population/development state.
- [KOMEConfigRegistry](../../../../src/main/java/kome/common/config/KOMEConfigRegistry.java), [KOMECampaignReadinessValidator](../../../../src/main/java/kome/common/config/KOMECampaignReadinessValidator.java): `kome.cfg`, effective snapshots/readiness; integrated modules have separate configuration owners.
- [KOMEAdminDiagnosticsCommands](../../../../src/main/java/kome/common/command/KOMEAdminDiagnosticsCommands.java), [KOMEAdminRepairService](../../../../src/main/java/kome/common/data/KOMEAdminRepairService.java), [KOMEAuditService](../../../../src/main/java/kome/common/data/KOMEAuditService.java): `/kome` inspection, guarded repair tokens, bounded persisted audit history. See [scoped diagnostics](../../../KOM-40-admin-diagnostics.md) for command syntax and repair boundaries.

## Dependencies and checks

Storage consumers include every strategic guide. Physical entities/chunks, native LOTR data, [character NBT/skins](../character-creation/README.md), and [gate/ram ledgers](../physical-siege/README.md) have additional save authorities. [Networking](../client-network/README.md) publishes projections; saving a client cache cannot repair server data.

[Atomic-load tests](../../../../src/test/java/kome/common/data/KOMEWorldDataAtomicLoadTest.java), [schema tests](../../../../src/test/java/kome/common/data/KOMEWorldDataSchemaTest.java), and [actual diagnostics-command tests](../../../../src/test/java/kome/common/command/KOMEAdminDiagnosticsCommandsTest.java) cover candidate publication, mandatory sections, and non-loading inspection paths.

```powershell
.\gradlew.bat test --tests 'kome.common.data.KOMEWorldDataAtomicLoadTest' --tests 'kome.common.data.KOMEWorldDataSchemaTest' --tests 'kome.common.command.KOMEAdminDiagnosticsCommandsTest' --no-daemon --max-workers=2
```

Follow [shared prerequisites](../../INDEX.md). On a disposable world, save/stop/reload and compare serialized domain records and module state; a clean exit alone proves no persistence invariant. Malformed mandatory data rejects publication and blocks writes. Do not bypass this with default regeneration. Preview tokens are transient and actor/world/current-state bound. Diagnostics must not load an unavailable dimension merely to inspect it; inspect command callers as well as the service.

Historical schema numbers in README and dated validation documents are not the current root schema. [KOMESeasonResetService](../../../../src/main/java/kome/common/data/KOMESeasonResetService.java) and [KOMESeasonResetDeployment](../../../../src/main/java/kome/common/data/KOMESeasonResetDeployment.java) now own durable reset return/recovery. [KOMEDailyCoordinator](../../../../src/main/java/kome/common/data/KOMEDailyCoordinator.java), [KOMEDailyJournal](../../../../src/main/java/kome/common/data/KOMEDailyJournal.java), and [KOMEWorldCheckpoint](../../../../src/main/java/kome/common/data/KOMEWorldCheckpoint.java) provide resumable population sequencing/checkpoints, with unavailable owning adapters explicitly blocked. [KOMEEvents.processCampaignTick](../../../../src/main/java/kome/common/data/KOMEEvents.java) routes active reset before governance/daily/ordinary campaign processing. See [campaign lifecycle](../campaign-lifecycle/README.md) and [daily flow](../../flows/daily-movement.md).
