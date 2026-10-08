package kome.common.data;

import java.util.Set;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;

/** Reversible native home-area lease for a currently protected actor, without invulnerability. */
public final class KOMEProgressionProtectedActors {
    private static final String LEASE="KOMEProtectedHome";
    private KOMEProgressionProtectedActors() {}
    public static void reconcile(KOMEWorldData data,LOTREntityNPC npc){
        Set<KOMEProgressionNpcRoleLease> leases=data.progressionNpcRoleLeases.get(npc.getUniqueID());
        boolean protectedRole=false;
        if(leases!=null)for(KOMEProgressionNpcRoleLease lease:leases){
            if(lease.role==KOMEProgressionNpcRoleLease.Role.DEFENSE_PROTECTED)protectedRole=true;
            if(lease.role==KOMEProgressionNpcRoleLease.Role.COMMISSION_BENEFICIARY
                    ||lease.role==KOMEProgressionNpcRoleLease.Role.LORDSHIP_GUARD){
                KOMEPlayerProgression p=data.progressions.get(lease.player);
                KOMEKnightCommission a=p==null?null:p.getLordship().assignment()!=null?p.getLordship().assignment().objective:p.getKnightService().assignment();
                KOMEKnightCommission.Actor actor=a==null?null:a.protectedActor();
                if(a!=null&&a.stage==KOMEKnightCommission.Stage.ACTIVE&&a.type!=KOMEKnightCommission.Type.DANGEROUS_ESCORT
                        &&actor!=null&&actor.id.equals(npc.getUniqueID().toString()))protectedRole=true;
            }
        }
        NBTTagCompound root=npc.getEntityData();
        if(!protectedRole){
            if(root.hasKey(LEASE,10)){
                NBTTagCompound saved=root.getCompoundTag(LEASE);
                if(saved.getBoolean("HadHome"))npc.setHomeArea(saved.getInteger("X"),saved.getInteger("Y"),saved.getInteger("Z"),saved.getInteger("Range"));else npc.detachHome();
                root.removeTag(LEASE);
            }
            return;
        }
        if(!root.hasKey(LEASE,10)){
            NBTTagCompound saved=new NBTTagCompound();saved.setBoolean("HadHome",npc.hasHome());
            ChunkCoordinates home=npc.getHomePosition();saved.setInteger("X",home.posX);saved.setInteger("Y",home.posY);saved.setInteger("Z",home.posZ);
            saved.setInteger("Range",(int)npc.func_110174_bM());
            saved.setInteger("AnchorX",(int)Math.floor(npc.posX));saved.setInteger("AnchorY",(int)Math.floor(npc.posY));saved.setInteger("AnchorZ",(int)Math.floor(npc.posZ));
            root.setTag(LEASE,saved);
        }
        NBTTagCompound saved=root.getCompoundTag(LEASE);int x=saved.getInteger("AnchorX"),y=saved.getInteger("AnchorY"),z=saved.getInteger("AnchorZ");
        npc.setHomeArea(x,y,z,16);
        if(npc.getAttackTarget()!=null&&npc.getAttackTarget().getDistanceSq(x,y,z)>16*16){npc.setAttackTarget(null);npc.getNavigator().clearPathEntity();}
        if(npc.ticksExisted%20==0&&npc.getDistanceSq(x,y,z)>20*20){npc.setAttackTarget(null);npc.getNavigator().tryMoveToXYZ(x+0.5,y,z+0.5,1D);}
    }
}
