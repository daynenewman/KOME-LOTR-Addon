package kome.common.data;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayerMP;
/** Server playback at the receiving NPC, only after consumption succeeds. */
public final class KOMEProgressionExchangeSounds {
    private KOMEProgressionExchangeSounds() {}
    public static void handover(EntityPlayerMP player,LOTREntityNPC npc){if(player!=null&&npc!=null&&!player.worldObj.isRemote&&npc.worldObj==player.worldObj)player.worldObj.playSoundAtEntity(npc,"mob.horse.leather",0.5F,1F);}
}
