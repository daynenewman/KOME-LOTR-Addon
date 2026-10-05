package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Hook for a later royal-respawn system: a dead NPC King returns only as a Prince. */
public final class KOMEProgressionNpcRoyalRestoration {
    public final UUID formerKingUuid;
    public final String factionKey, capitalTileId;
    public final int dimension;
    public final double x, y, z;
    public KOMEProgressionNpcRoyalRestoration(UUID uuid, String faction, KOMEFactionCapitalRecord capital) {
        if(uuid==null || capital==null) throw new IllegalArgumentException("Former King and canonical capital are required.");
        formerKingUuid=uuid; factionKey=KOMEAlliance.normalizeFactionKey(faction); capitalTileId=capital.getCapitalTileId(); dimension=capital.getDeploymentDimensionId(); x=capital.getDeploymentX(); y=capital.getDeploymentY(); z=capital.getDeploymentZ();
    }
    public NBTTagCompound writeToNBT(){NBTTagCompound tag=new NBTTagCompound();tag.setString("UUID",formerKingUuid.toString());tag.setString("Faction",factionKey);tag.setString("RestoreRank",KOMEProgressionNpcRank.PRINCE.key);tag.setString("CapitalTile",capitalTileId);tag.setInteger("Dimension",dimension);tag.setDouble("X",x);tag.setDouble("Y",y);tag.setDouble("Z",z);return tag;}
    public static KOMEProgressionNpcRoyalRestoration readFromNBT(NBTTagCompound tag,KOMEWorldData data){try{if(!KOMEProgressionNpcRank.PRINCE.key.equals(tag.getString("RestoreRank")))return null; String faction=KOMEAlliance.normalizeFactionKey(tag.getString("Faction")); KOMEFactionCapitalRecord capital=KOMEFactionCapitalService.getCapital(data,faction); if(capital==null||!capital.getCapitalTileId().equals(tag.getString("CapitalTile")))return null; return new KOMEProgressionNpcRoyalRestoration(UUID.fromString(tag.getString("UUID")),faction,capital);}catch(Exception invalid){return null;}}
}
