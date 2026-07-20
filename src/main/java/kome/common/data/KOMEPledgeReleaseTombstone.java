package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/** Prevents a released, unloaded NPC from returning as an active ghost. */
public class KOMEPledgeReleaseTombstone {
    public UUID unitUuid;
    public UUID formerOwner;
    public String formerFaction = "";
    public String fundingSource = "";
    public String sourceTile = "";
    public String sourceFaction = "";
    public UUID sourcePlayer;
    public KOMEPopulationType populationType = KOMEPopulationType.OFFENSIVE;
    public int populationAmount;
    public String releaseReason = "";
    public boolean populationReturned;
    public boolean entityRemoved;
    public boolean quarantined;
    public long createdTimestamp;
    public long lastRetry;
    public long completedTimestamp;

    public boolean complete() {
        return populationReturned && entityRemoved || quarantined && entityRemoved;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("UnitUuid", unitUuid == null ? "" : unitUuid.toString());
        nbt.setString("FormerOwner", formerOwner == null ? "" : formerOwner.toString());
        nbt.setString("FormerFaction", KOMEAlliance.normalizeFactionKey(formerFaction));
        nbt.setString("FundingSource", fundingSource == null ? "" : fundingSource);
        nbt.setString("SourceTile", KOMEConquestTile.normalizeId(sourceTile));
        nbt.setString("SourceFaction", KOMEAlliance.normalizeFactionKey(sourceFaction));
        nbt.setString("SourcePlayer", sourcePlayer == null ? "" : sourcePlayer.toString());
        nbt.setString("PopulationType", populationType == null ? KOMEPopulationType.OFFENSIVE.key : populationType.key);
        nbt.setInteger("PopulationAmount", Math.max(0, populationAmount));
        nbt.setString("ReleaseReason", releaseReason == null ? "" : releaseReason);
        nbt.setBoolean("PopulationReturned", populationReturned);
        nbt.setBoolean("EntityRemoved", entityRemoved);
        nbt.setBoolean("Quarantined", quarantined);
        nbt.setLong("CreatedTimestamp", createdTimestamp);
        nbt.setLong("LastRetry", lastRetry);
        nbt.setLong("CompletedTimestamp", completedTimestamp);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        unitUuid = uuid(nbt.getString("UnitUuid"));
        formerOwner = uuid(nbt.getString("FormerOwner"));
        formerFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("FormerFaction"));
        fundingSource = nbt.getString("FundingSource");
        sourceTile = KOMEConquestTile.normalizeId(nbt.getString("SourceTile"));
        sourceFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("SourceFaction"));
        sourcePlayer = uuid(nbt.getString("SourcePlayer"));
        KOMEPopulationType type = KOMEPopulationType.forName(nbt.getString("PopulationType"));
        populationType = type == null ? KOMEPopulationType.OFFENSIVE : type;
        populationAmount = Math.max(0, nbt.getInteger("PopulationAmount"));
        releaseReason = nbt.getString("ReleaseReason");
        populationReturned = nbt.getBoolean("PopulationReturned");
        entityRemoved = nbt.getBoolean("EntityRemoved");
        quarantined = nbt.getBoolean("Quarantined");
        createdTimestamp = Math.max(0L, nbt.getLong("CreatedTimestamp"));
        lastRetry = Math.max(0L, nbt.getLong("LastRetry"));
        completedTimestamp = Math.max(0L, nbt.getLong("CompletedTimestamp"));
    }

    private static UUID uuid(String value) {
        try { return value == null || value.length() == 0 ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
