package kome.common.data;

import java.util.*;
import kome.common.network.KOMEPacketCampaignHiredInfo;
import kome.common.network.KOMEPacketHandler;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/** Ephemeral tracking work only; classification and native ownership are revalidated at publication. */
public final class KOMECampaignHiredPresentation {
    private static final int LIMIT=512;
    private static final Map<LOTREntityNPC,Set<EntityPlayerMP>> pending=new LinkedHashMap<>();
    private KOMECampaignHiredPresentation() { }
    public static void tracking(EntityPlayer viewer,Entity target) {
        if(!(viewer instanceof EntityPlayerMP)||!(target instanceof LOTREntityNPC)||target.worldObj.isRemote)return;
        LOTREntityNPC npc=(LOTREntityNPC)target;
        if(npc.hiredNPCInfo==null||!npc.hiredNPCInfo.isActive
            ||!viewer.getUniqueID().equals(npc.hiredNPCInfo.getHiringPlayerUUID()))return;
        // Strategic arrival is deliberately not published until its verified rekey completes.
        if(!pending.containsKey(npc)&&pending.size()>=LIMIT)return;
        pending.computeIfAbsent(npc,key->new HashSet<>()).add((EntityPlayerMP)viewer);
    }
    public static void stoppedTracking(EntityPlayer viewer,Entity target) {
        Set<EntityPlayerMP> viewers=pending.get(target);
        if(viewers!=null){viewers.remove(viewer);if(viewers.isEmpty())pending.remove(target);}
    }
    public static void flush() {
        Map<LOTREntityNPC,Set<EntityPlayerMP>> batch=new LinkedHashMap<>(pending);pending.clear();
        for(Map.Entry<LOTREntityNPC,Set<EntityPlayerMP>> entry:batch.entrySet()){
            LOTREntityNPC npc=entry.getKey();
            if(npc.isDead||npc.worldObj==null||npc.worldObj.isRemote||npc.worldObj.getEntityByID(npc.getEntityId())!=npc)continue;
            KOMEWorldData data=KOMEWorldData.get(npc.worldObj);
            KOMEHiredUnitRecord record=data.hiredUnits.get(npc.getUniqueID());
            if(!KOMEHiredUnitClassification.isCampaignUnit(record)||data.isVirtualMovingHiredUnit(record)
                ||!npc.hiredNPCInfo.isActive||npc.hiredNPCInfo.getTask()!=LOTRHiredNPCInfo.Task.WARRIOR
                ||!record.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))continue;
            for(EntityPlayerMP viewer:entry.getValue())
                if(viewer.worldObj==npc.worldObj&&record.owner.equals(viewer.getUniqueID())
                    &&viewer.playerNetServerHandler!=null&&viewer.playerNetServerHandler.netManager!=null
                    &&viewer.playerNetServerHandler.netManager.isChannelOpen())
                    KOMEPacketHandler.network.sendTo(new KOMEPacketCampaignHiredInfo(npc),viewer);
        }
    }
    public static void clear(){pending.clear();}
}
