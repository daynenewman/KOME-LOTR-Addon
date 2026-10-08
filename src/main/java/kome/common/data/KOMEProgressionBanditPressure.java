package kome.common.data;

import lotr.common.entity.npc.IBandit;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/** Adjusts only a native, already eligible regional bandit roll near the owning carrier. */
public final class KOMEProgressionBanditPressure {
    public static final int LOCAL_CAP=4;
    private KOMEProgressionBanditPressure() {}
    public static double chance(double normal,World world,int x,int z){
        if(normal<=0||!Double.isFinite(normal)||world==null||world.isRemote)return normal;
        EntityPlayer nearest=null;
        for(Object value:world.playerEntities){EntityPlayer candidate=(EntityPlayer)value;
            if(candidate.capabilities!=null&&candidate.capabilities.isCreativeMode)continue;
            if(Math.abs(candidate.posX-x)>48||Math.abs(candidate.posZ-z)>48)continue;
            // Native bandits may choose any local player. Do not boost a shared encounter.
            if(nearest!=null)return normal;nearest=candidate;
        }
        KOMEWorldData data=KOMEWorldData.get(world);
        if(!data.isProgressionEnabled()||nearest==null||!nearest.isEntityAlive()||!carrying(data.getProgression(nearest.getUniqueID()),nearest))return normal;
        int count=0;
        for(Object value:world.getEntitiesWithinAABB(LOTREntityNPC.class,AxisAlignedBB.getBoundingBox(x-96,0,z-96,x+96,world.getActualHeight(),z+96)))
            if(value instanceof IBandit&&((LOTREntityNPC)value).isEntityAlive()&&++count>=LOCAL_CAP)return normal;
        return Math.min(1D,normal*2D);
    }
    static boolean carrying(KOMEPlayerProgression p,EntityPlayer player){
        if(p==null||player.inventory==null)return false;
        lotr.common.fac.LOTRFaction pledge=lotr.common.LOTRLevelData.getData(player).getPledgeFaction();
        KOMEKnightCommission a=p.getKnightService().assignment();
        if(a!=null&&a.type==KOMEKnightCommission.Type.STOLEN_GOODS&&a.live()
                &&KOMEProgressionFactionResolver.matches(a.faction,pledge)&&KOMEKnightCommissionService.hasProperty(player.inventory.mainInventory,a,player.getUniqueID()))return true;
        KOMESerfKnightTrialAssignment t=p.getSerfKnightProgression().getTrialAssignment();
        if(t!=null&&t.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE&&"recovery".equals(t.trialId)
                &&KOMEProgressionFactionResolver.matches(t.factionKey,pledge))
            for(ItemStack stack:player.inventory.mainInventory)if(KOMESerfKnightRecoveryService.isAssignedTo(stack,t,player.getUniqueID()))return true;
        return false;
    }
}
