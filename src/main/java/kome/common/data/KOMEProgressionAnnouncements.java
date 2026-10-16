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
        try { sender.broadcast(player.getCommandSenderName()+" has been raised to "+KOMEProgressionIdentity.standing(f.codeName(),p.getCanonicalRank())+"!"); }
        catch (RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger("KOME").warn("Promotion announcement failed after standing was saved", failure); }
        try {
        net.minecraft.nbt.NBTTagCompound explosion=new net.minecraft.nbt.NBTTagCompound();
        explosion.setByte("Type",(byte)0);explosion.setIntArray("Colors",new int[]{f.getFactionColor()});
        net.minecraft.nbt.NBTTagList explosions=new net.minecraft.nbt.NBTTagList();explosions.appendTag(explosion);
        net.minecraft.nbt.NBTTagCompound fireworks=new net.minecraft.nbt.NBTTagCompound();fireworks.setByte("Flight",(byte)0);fireworks.setTag("Explosions",explosions);
        net.minecraft.item.ItemStack rocket=new net.minecraft.item.ItemStack(net.minecraft.init.Items.fireworks);
        net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setTag("Fireworks",fireworks);rocket.setTagCompound(tag);
        player.worldObj.spawnEntityInWorld(new net.minecraft.entity.item.EntityFireworkRocket(player.worldObj,player.posX,player.posY+1D,player.posZ,rocket));
        } catch (RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger("KOME").warn("Promotion firework failed after standing was saved", failure); }
    }
    public static boolean isPromotion(KOMEProgressionRank before,KOMEProgressionRank after,boolean forced){return !forced&&before!=null&&after!=null&&after.order>before.order;}
}
