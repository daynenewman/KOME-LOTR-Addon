package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Persisted authority for elevated NPC ranks only; LORD is always derived from hiring capability. */
public final class KOMEProgressionNpcRankRecord {
    public final UUID npcUuid;
    public final String factionKey, displayName;
    public final KOMEProgressionNpcRank rank;
    public KOMEProgressionNpcRankRecord(UUID npcUuid, String factionKey, KOMEProgressionNpcRank rank, String displayName) {
        if(npcUuid==null || (rank != KOMEProgressionNpcRank.PRINCE && rank != KOMEProgressionNpcRank.KING)) throw new IllegalArgumentException("Only NPC Prince or King ranks may be persisted.");
        String faction=KOMEAlliance.normalizeFactionKey(factionKey); if(faction.length()==0) throw new IllegalArgumentException("NPC rank requires a faction key.");
        this.npcUuid=npcUuid; this.factionKey=faction; this.rank=rank; this.displayName=displayName==null ? "" : displayName.trim();
    }
    public NBTTagCompound writeToNBT() { NBTTagCompound tag=new NBTTagCompound(); tag.setString("UUID",npcUuid.toString()); tag.setString("Faction",factionKey); tag.setString("Rank",rank.key); tag.setString("Name",displayName); return tag; }
    public static KOMEProgressionNpcRankRecord readFromNBT(NBTTagCompound tag) {
        if(tag==null) return null;
        try { KOMEProgressionNpcRank rank=KOMEProgressionNpcRank.forKey(tag.getString("Rank")); return new KOMEProgressionNpcRankRecord(UUID.fromString(tag.getString("UUID")),tag.getString("Faction"),rank,tag.getString("Name")); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
