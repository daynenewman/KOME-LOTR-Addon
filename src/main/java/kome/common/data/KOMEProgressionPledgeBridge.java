package kome.common.data;

import cpw.mods.fml.common.FMLCommonHandler;
import lotr.common.LOTRPlayerData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** Native pledge setter hook; updates the opening objective in the same server turn. */
public final class KOMEProgressionPledgeBridge {
    private KOMEProgressionPledgeBridge(){}
    public static void changed(LOTRPlayerData nativeData){
        if(nativeData==null||FMLCommonHandler.instance().getEffectiveSide().isClient())return;
        MinecraftServer server=MinecraftServer.getServer();if(server==null)return;
        for(Object value:server.getConfigurationManager().playerEntityList){
            EntityPlayerMP player=(EntityPlayerMP)value;
            if(!player.getUniqueID().equals(nativeData.getPlayerUUID()))continue;
            KOMEWorldData data=KOMEWorldData.get(player.worldObj);
            String faction=nativeData.getPledgeFaction()==null?"":nativeData.getPledgeFaction().codeName();
            data.getProgression(player.getUniqueID()).observeOfferPledge(faction);
            KOMEPledgeReleaseService.observePledge(data,player,faction,System.currentTimeMillis());
            data.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,data.getProgression(player.getUniqueID()));
        }
    }
}
