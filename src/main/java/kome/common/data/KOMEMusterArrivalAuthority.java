package kome.common.data;

import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/** Production safety projection over the canonical conflict registry. Inspection never loads a world/chunk. */
public final class KOMEMusterArrivalAuthority implements KOMEMusterService.ArrivalAuthority {
    public static final KOMEMusterArrivalAuthority INSTANCE = new KOMEMusterArrivalAuthority();
    public static final String FORCE_UNAVAILABLE = "FACTION_OWNED_DEPLOYMENT_UNAVAILABLE: campaign unit persistence/control requires a player owner; no faction-force delivery or entity-save receipt authority exists";
    private KOMEMusterArrivalAuthority() { }

    @Override public KOMEMusterService.CapitalState capitalState(KOMEWorldData data, KOMEFactionCapitalRecord capital) {
        if (data == null || data.isWriteBlocked() || !data.isIntegratedRootInitialized() || capital == null)
            return KOMEMusterService.CapitalState.UNKNOWN;
        KOMEConquestTile tile = data.conquestTiles.get(capital.getCapitalTileId());
        if (tile == null) return KOMEMusterService.CapitalState.UNKNOWN;
        KOMEConflictRecord conflict = data.getConflictService().get(capital.getCapitalTileId());
        if (conflict != null && conflict.isActive())
            return conflict.getState() == KOMEConflictRecord.State.ENCIRCLEMENT
                ? KOMEMusterService.CapitalState.ENCIRCLED : KOMEMusterService.CapitalState.UNKNOWN;
        return capital.getFactionId().equals(tile.projectRulingFaction())
            ? KOMEMusterService.CapitalState.CLEAR : KOMEMusterService.CapitalState.UNKNOWN;
    }

    @Override public String deliveryBlockReason(KOMEWorldData data, KOMEMusterRecord record) {
        KOMEFactionCapitalRecord current = KOMEFactionCapitalService.getCapital(data, record.faction);
        if (current == null) return "CAPITAL_UNAVAILABLE: no authoritative capital";
        if (!current.writeToNBT().equals(record.capital.writeToNBT()))
            return "CAPITAL_SNAPSHOT_CHANGED: saved roster and deployment snapshot retained for review";
        KOMEStrategicDeploymentResolver.Validation metadata = KOMEStrategicDeploymentResolver.validateMetadata(
            record.capital.getCapitalTileId(), record.capital.getDeploymentDimensionId(), record.capital.getDeploymentX(),
            record.capital.getDeploymentY(), record.capital.getDeploymentZ());
        if (!metadata.valid) return "CAPITAL_DEPLOYMENT_INVALID: " + metadata.reason;
        World world = DimensionManager.getWorld(record.capital.getDeploymentDimensionId());
        if (world == null || world.provider == null || world.provider.dimensionId != record.capital.getDeploymentDimensionId())
            return "DEPLOYMENT_DIMENSION_UNAVAILABLE: load the capital dimension through normal gameplay before retrying readiness";
        int chunkX = MathHelper.floor_double(record.capital.getDeploymentX()) >> 4;
        int chunkZ = MathHelper.floor_double(record.capital.getDeploymentZ()) >> 4;
        if (world.getChunkProvider() == null || !world.getChunkProvider().chunkExists(chunkX, chunkZ))
            return "DEPLOYMENT_CHUNK_UNAVAILABLE: capital chunk is not loaded; roster retained";
        // Terrain footprint safety must be checked with each actual native rider/mount before delivery.
        // It is not meaningful to spawn probes while faction control and durable receipts are unavailable.
        return FORCE_UNAVAILABLE;
    }

    @Override public String deliver(KOMEWorldData data, KOMEMusterRecord record) {
        throw new IllegalStateException(FORCE_UNAVAILABLE);
    }
}
