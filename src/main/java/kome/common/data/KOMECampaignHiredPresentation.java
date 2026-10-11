package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.network.LOTRPacketEntityUUID;
import lotr.common.network.LOTRPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/** A client-spawn acknowledgment may refresh native state, never establish hire authority. */
public final class KOMECampaignHiredPresentation {
    private KOMECampaignHiredPresentation() { }
    public static boolean refresh(EntityPlayerMP viewer,int dimension,int entityId) {
        if(viewer==null||viewer.worldObj==null||viewer.worldObj.isRemote
            ||viewer.worldObj.provider.dimensionId!=dimension)return false;
        Entity physical=viewer.worldObj.getEntityByID(entityId);
        if(!(physical instanceof LOTREntityNPC)||physical.isDead)return false;
        LOTREntityNPC npc=(LOTREntityNPC)physical;
        KOMEWorldData data=KOMEWorldData.get(viewer.worldObj);
        KOMEHiredUnitRecord record=data.hiredUnits.get(npc.getUniqueID());
        if(!KOMEHiredUnitClassification.isCampaignUnit(record)||data.isVirtualMovingHiredUnit(record)
            ||npc.hiredNPCInfo==null||!npc.hiredNPCInfo.isActive
            ||npc.hiredNPCInfo.getTask()!=LOTRHiredNPCInfo.Task.WARRIOR
            ||!viewer.getUniqueID().equals(record.owner)||npc.hiredNPCInfo.getHiringPlayer()!=viewer
            ||viewer.playerNetServerHandler==null||viewer.playerNetServerHandler.netManager==null
            ||!viewer.playerNetServerHandler.netManager.isChannelOpen())return false;
        // The client has installed this entity already. Use the same contracts as native tracking.
        LOTRPacketHandler.networkWrapper.sendTo(new LOTRPacketEntityUUID(entityId,npc.getUniqueID()),viewer);
        npc.hiredNPCInfo.sendBasicData(viewer);
        return true;
    }
}
