package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

/** Immutable authoritative snapshot of one faction capital and deployment anchor. */
public final class KOMEFactionCapitalRecord {
    public static final int DATA_SCHEMA_VERSION = 1;
    private final String factionId;
    private final String capitalTileId;
    private final int deploymentDimensionId;
    private final double deploymentX;
    private final double deploymentY;
    private final double deploymentZ;
    private final long updatedAtMillis;
    private final String source;
    private final String actor;

    public KOMEFactionCapitalRecord(String factionId, String capitalTileId, int deploymentDimensionId,
            double deploymentX, double deploymentY, double deploymentZ, long updatedAtMillis,
            String source, String actor) {
        this.factionId = KOMEAlliance.normalizeFactionKey(factionId);
        this.capitalTileId = KOMEConquestTile.normalizeId(capitalTileId);
        this.deploymentDimensionId = deploymentDimensionId;
        this.deploymentX = deploymentX;
        this.deploymentY = deploymentY;
        this.deploymentZ = deploymentZ;
        this.updatedAtMillis = Math.max(0L, updatedAtMillis);
        this.source = clean(source);
        this.actor = clean(actor);
        validatePersistedShape();
    }

    public String getFactionId() { return factionId; }
    public String getCapitalTileId() { return capitalTileId; }
    public int getDeploymentDimensionId() { return deploymentDimensionId; }
    public double getDeploymentX() { return deploymentX; }
    public double getDeploymentY() { return deploymentY; }
    public double getDeploymentZ() { return deploymentZ; }
    public long getUpdatedAtMillis() { return updatedAtMillis; }
    public String getSource() { return source; }
    public String getActor() { return actor; }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Faction", factionId);
        nbt.setString("CapitalTile", capitalTileId);
        nbt.setInteger("DeploymentDimension", deploymentDimensionId);
        nbt.setDouble("DeploymentX", deploymentX);
        nbt.setDouble("DeploymentY", deploymentY);
        nbt.setDouble("DeploymentZ", deploymentZ);
        nbt.setLong("UpdatedAtMillis", updatedAtMillis);
        nbt.setString("Source", source);
        nbt.setString("Actor", actor);
        return nbt;
    }

    public static KOMEFactionCapitalRecord readFromNBT(NBTTagCompound nbt) {
        if (nbt == null) throw new IllegalArgumentException("Capital record is missing.");
        return new KOMEFactionCapitalRecord(nbt.getString("Faction"), nbt.getString("CapitalTile"),
            nbt.getInteger("DeploymentDimension"), nbt.getDouble("DeploymentX"),
            nbt.getDouble("DeploymentY"), nbt.getDouble("DeploymentZ"),
            nbt.getLong("UpdatedAtMillis"), nbt.getString("Source"), nbt.getString("Actor"));
    }

    private void validatePersistedShape() {
        if (!KOMEAlliance.allFactionKeys().contains(factionId))
            throw new IllegalArgumentException("Unsupported capital faction: " + factionId);
        if (!KOMEConquestTile.isCanonicalTileId(capitalTileId)
                || KOMEConquestTileDefaults.isRetiredTile(capitalTileId)
                || !KOMEConquestTileDefaults.getKnownTileIds().contains(capitalTileId))
            throw new IllegalArgumentException("Unknown, malformed, or retired capital tile: " + capitalTileId);
        KOMEStrategicDeploymentResolver.Validation metadata =
            KOMEStrategicDeploymentResolver.validateMetadata(capitalTileId, deploymentDimensionId,
                deploymentX, deploymentY, deploymentZ);
        if (!metadata.valid) throw new IllegalArgumentException(metadata.reason);
        if (source.length() == 0) throw new IllegalArgumentException("Capital source is required.");
        if (actor.length() == 0) throw new IllegalArgumentException("Capital actor is required.");
    }

    static String clean(String value) {
        return value == null ? "" : value.replace('|', ' ').replace(';', ' ')
            .replace((char) 10, ' ').replace((char) 13, ' ').trim();
    }
}
