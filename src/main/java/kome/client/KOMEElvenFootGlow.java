package kome.client;

import java.util.*;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache;
import com.lotrcharactercreation.race.PlayerRace;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import lotr.client.fx.LOTREntityBlueFlameFX;
import lotr.common.entity.npc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Native particles plus optional, explicitly separate real client illumination. */
public final class KOMEElvenFootGlow {
    private final KOMEElvenDynamicLights lights=new KOMEElvenDynamicLights();
    static boolean visible(PlayerRace race,boolean invisible,boolean sneaking,boolean sleeping,int particles,double distanceSq){
        return race==PlayerRace.ELF&&!invisible&&!sneaking&&!sleeping&&particles<2&&distanceSq<=24*24;
    }
    static boolean nativeElf(EntityLivingBase entity){return entity instanceof LOTREntityElf&&!(entity instanceof LOTREntityTormentedElf);}
    static boolean night(boolean hasNoSky,float angle){return !hasNoSky&&angle>0.25F&&angle<0.75F;}
    static boolean night(World world){return world!=null&&night(world.provider.hasNoSky,world.getCelestialAngle(1F));}
    static boolean eligible(EntityLivingBase entity){
        if(entity==null||!entity.isEntityAlive()||entity.isInvisible()||entity.isSneaking())return false;
        if(nativeElf(entity))return true;
        if(!(entity instanceof EntityPlayer))return false;
        EntityPlayer player=(EntityPlayer)entity;
        ClientPlayerAppearanceCache.SynchronizedPlayerAppearance appearance=ClientPlayerAppearanceCache.getInstance().get(player);
        return appearance!=null&&appearance.isCharacterCreationComplete()&&appearance.getRace()==PlayerRace.ELF&&!player.isPlayerSleeping();
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld==null||mc.thePlayer==null||!night(mc.theWorld)){
            lights.update(mc.theWorld,Collections.<EntityLivingBase>emptyList());return;
        }
        if(mc.isGamePaused())return;
        List<EntityLivingBase> elves=new ArrayList<EntityLivingBase>();
        for(Object value:mc.theWorld.loadedEntityList)if(value instanceof EntityLivingBase){
            EntityLivingBase entity=(EntityLivingBase)value;
            if(mc.thePlayer.getDistanceSqToEntity(entity)<=24*24&&eligible(entity))elves.add(entity);
        }
        elves.sort(Comparator.comparingDouble(e->mc.thePlayer.getDistanceSqToEntity(e)));
        lights.update(mc.theWorld,KOMEClientConfig.elvenDynamicGlobal()?elves:Collections.<EntityLivingBase>emptyList());
        if(!KOMEClientConfig.elvenAuraGlobal()||mc.gameSettings.particleSetting>=2||lotr.client.LOTRClientProxy.customEffectRenderer==null)return;
        int emitted=0,interval=KOMEClientConfig.elvenIntervalGlobal()*(mc.gameSettings.particleSetting==0?1:2);
        float angle=mc.theWorld.getCelestialAngle(1F);
        float fade=Math.min(1F,Math.min(angle-0.25F,0.75F-angle)/0.025F);
        for(EntityLivingBase elf:elves){
            if((elf.ticksExisted+elf.getEntityId())%interval!=0||!mc.thePlayer.canEntityBeSeen(elf))continue;
            double theta=mc.theWorld.rand.nextDouble()*Math.PI*2,radius=0.28+mc.theWorld.rand.nextDouble()*0.12;
            lotr.client.LOTRClientProxy.customEffectRenderer.addEffect(new FootParticle(elf,elf.posX+Math.cos(theta)*radius,elf.boundingBox.minY+0.06,elf.posZ+Math.sin(theta)*radius,fade));
            if(++emitted>=8)break;
        }
    }
    private static final class FootParticle extends LOTREntityBlueFlameFX {
        private final EntityLivingBase owner;
        FootParticle(EntityLivingBase elf,double x,double y,double z,float fade){
            super(elf.worldObj,x,y,z,0,0.003,0);owner=elf;
            particleMaxAge=12;particleAlpha=KOMEClientConfig.elvenIntensityGlobal()*fade;particleRed=0.35F;particleGreen=0.55F;particleBlue=0.9F;noClip=false;
        }
        @Override public void onUpdate(){if(owner.worldObj!=worldObj||!KOMEClientConfig.elvenAuraGlobal()||!eligible(owner)||!night(worldObj)){setDead();return;}super.onUpdate();}
    }
}
