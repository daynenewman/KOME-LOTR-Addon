package kome.client;

import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache;
import com.lotrcharactercreation.race.PlayerRace;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import lotr.client.fx.LOTREntityBlueFlameFX;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/** Native blue flame sprite in LOTR's particle batch. No GL hooks, block light, or packets. */
public final class KOMEElvenFootGlow {
    static boolean visible(PlayerRace race,boolean invisible,boolean sneaking,boolean sleeping,int particles,double distanceSq){
        return race==PlayerRace.ELF&&!invisible&&!sneaking&&!sleeping&&particles<2&&distanceSq<=24*24;
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        Minecraft mc=Minecraft.getMinecraft();
        if(event.phase!=TickEvent.Phase.END||mc.theWorld==null||mc.thePlayer==null||mc.isGamePaused()||mc.gameSettings.particleSetting>=2||lotr.client.LOTRClientProxy.customEffectRenderer==null)return;
        int emitted=0;
        for(Object value:mc.theWorld.playerEntities){
            EntityPlayer player=(EntityPlayer)value;
            ClientPlayerAppearanceCache.SynchronizedPlayerAppearance appearance=ClientPlayerAppearanceCache.getInstance().get(player);
            if(appearance==null||!appearance.isCharacterCreationComplete()||!visible(appearance.getRace(),player.isInvisible(),player.isSneaking(),player.isPlayerSleeping(),mc.gameSettings.particleSetting,mc.thePlayer.getDistanceSqToEntity(player)))continue;
            int interval=mc.gameSettings.particleSetting==0?8:16;
            if((player.ticksExisted+player.getEntityId())%interval!=0||!mc.thePlayer.canEntityBeSeen(player))continue;
            double angle=mc.theWorld.rand.nextDouble()*Math.PI*2,radius=0.28+mc.theWorld.rand.nextDouble()*0.12;
            double x=player.posX+Math.cos(angle)*radius,z=player.posZ+Math.sin(angle)*radius;
            lotr.client.LOTRClientProxy.customEffectRenderer.addEffect(new FootParticle(player,x,player.boundingBox.minY+0.06,z));
            if(++emitted>=8)break;
        }
    }
    private static final class FootParticle extends LOTREntityBlueFlameFX {
        private final EntityPlayer owner;
        FootParticle(EntityPlayer player,double x,double y,double z){
            super(player.worldObj,x,y,z,0,0.003,0);owner=player;
            // Keep native sprite geometry; low opacity and short lifetime supply the subtle effect.
            particleMaxAge=12;particleAlpha=0.28F;
            particleRed=0.35F;particleGreen=0.55F;particleBlue=0.9F;noClip=false;
        }
        @Override public void onUpdate(){if(owner.isInvisible()||owner.isSneaking()||!owner.isEntityAlive()){setDead();return;}super.onUpdate();}
    }
}
