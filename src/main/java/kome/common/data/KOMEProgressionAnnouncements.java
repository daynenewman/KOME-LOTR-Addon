package kome.common.data;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/** Call only at a successful earned transition, never during synchronization or repairs. */
public final class KOMEProgressionAnnouncements {
    interface Sender {void broadcast(String text);}
    static Sender sender=text->{MinecraftServer server=MinecraftServer.getServer();if(server!=null)server.getConfigurationManager().sendChatMsg(new ChatComponentText(EnumChatFormatting.GOLD+text));};
    private KOMEProgressionAnnouncements() {}
    public static void earned(EntityPlayerMP player,KOMEProgressionRank before,boolean forced){
        if(player==null||player.worldObj==null||player.worldObj.isRemote||forced)return;
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        if(!isPromotion(before,p.getCanonicalRank(),forced))return;
        lotr.common.fac.LOTRFaction f=lotr.common.LOTRLevelData.getData(player).getPledgeFaction();
        if(f==null)return;
        sender.broadcast(player.getCommandSenderName()+" has been raised to "+KOMEProgressionIdentity.standing(f.codeName(),p.getCanonicalRank())+"!");
    }
    public static boolean isPromotion(KOMEProgressionRank before,KOMEProgressionRank after,boolean forced){return !forced&&before!=null&&after!=null&&after.order>before.order;}
}
