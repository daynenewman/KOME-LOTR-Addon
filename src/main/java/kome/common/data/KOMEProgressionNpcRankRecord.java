package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Persisted authority for elevated NPC ranks only; LORD is always derived from hiring capability. */
public final class KOMEProgressionNpcRankRecord {
    public final UUID npcUuid;
    public final String factionKey;
    public String displayName;
    public final KOMEProgressionNpcRank rank;
    /** KING slot lifecycle. Remaining runtime pauses while the server is stopped. */
    public boolean rulerInitialized,rulerSpawned,capitalBound;
    public int incarnation,missingChecks;
    public long respawnRemainingMillis=-1L;
    transient long lastRuntimeNanos;
    transient boolean provisionalSpawn;
    public KOMEProgressionNpcRef rulerLocation=KOMEProgressionNpcRef.EMPTY;
    public KOMEProgressionNpcRankRecord(UUID npcUuid, String factionKey, KOMEProgressionNpcRank rank, String displayName) {
        if(npcUuid==null || rank==null || rank==KOMEProgressionNpcRank.UNRANKED) throw new IllegalArgumentException("Only NPC authority ranks may be persisted.");
        String faction=KOMEAlliance.normalizeFactionKey(factionKey); if(faction.length()==0) throw new IllegalArgumentException("NPC rank requires a faction key.");
        this.npcUuid=npcUuid; this.factionKey=faction; this.rank=rank; this.displayName=displayName==null ? "" : displayName.trim();
    }
    public NBTTagCompound writeToNBT() { NBTTagCompound tag=new NBTTagCompound(); tag.setString("UUID",npcUuid.toString()); tag.setString("Faction",factionKey); tag.setString("Rank",rank.key); tag.setString("Name",displayName);
        if(rank==KOMEProgressionNpcRank.KING){tag.setBoolean("RulerInitialized",rulerInitialized);tag.setBoolean("RulerSpawned",rulerSpawned);tag.setBoolean("RulerCapitalBound",capitalBound);tag.setInteger("RulerIncarnation",incarnation);tag.setLong("RulerRespawnRemaining",respawnRemainingMillis);tag.setTag("RulerLocation",rulerLocation.writeToNBT());}return tag; }
    public static KOMEProgressionNpcRankRecord readFromNBT(NBTTagCompound tag) {
        if(tag==null) return null;
        try { KOMEProgressionNpcRank rank=KOMEProgressionNpcRank.forKey(tag.getString("Rank")); KOMEProgressionNpcRankRecord r=new KOMEProgressionNpcRankRecord(UUID.fromString(tag.getString("UUID")),tag.getString("Faction"),rank,tag.getString("Name"));
            if(rank==KOMEProgressionNpcRank.KING){r.rulerInitialized=tag.getBoolean("RulerInitialized");r.rulerSpawned=tag.getBoolean("RulerSpawned");r.capitalBound=tag.getBoolean("RulerCapitalBound");r.incarnation=Math.max(0,tag.getInteger("RulerIncarnation"));r.respawnRemainingMillis=tag.hasKey("RulerRespawnRemaining")?Math.max(-1L,Math.min(KOMEProgressionRulerService.RESPAWN_MILLIS,tag.getLong("RulerRespawnRemaining"))):-1L;r.rulerLocation=KOMEProgressionNpcRef.readFromNBT(tag.getCompoundTag("RulerLocation"));}return r; }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
