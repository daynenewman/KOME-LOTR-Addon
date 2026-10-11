package kome.common.data;

import kome.common.KOMEAccessFixture;
import kome.common.network.*;
import lotr.common.entity.npc.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class KOMECampaignHiredPresentationTest {
    private KOMEAccessFixture f;
    private KOMECampaignMovementReconstructionTest.MovementNpc npc;
    private KOMEHiredUnitRecord record;
    private cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous;
    private Object priorType;
    @Before public void setup() throws Exception {
        priorType=net.minecraft.entity.EntityList.classToStringMapping.get(KOMECampaignMovementReconstructionTest.MovementNpc.class);
        f=new KOMEAccessFixture();f.world.rand=new Random(4);
        npc=new KOMECampaignMovementReconstructionTest.MovementNpc(f.world);
        f.world.isRemote=true;
        npc.hiredNPCInfo.hireUnit(f.player,false,null,new LOTRUnitTradeEntry(npc.getClass(),0,0F),"North Guard",null);
        f.world.isRemote=false;
        record=new KOMEHiredUnitRecord();record.entity=npc.getUniqueID();record.owner=f.player.getUniqueID();
        f.data.hiredUnits.put(record.entity,record);f.world.loadedEntityList.add(npc);
        previous=KOMEPacketHandler.network;KOMEPacketHandler.network=f.network;
        KOMECampaignHiredPresentation.clear();
    }
    @After public void reset(){KOMECampaignHiredPresentation.clear();KOMEPacketHandler.network=previous;
        if(priorType!=null)net.minecraft.entity.EntityList.classToStringMapping.put(npc.getClass(),priorType);
        else net.minecraft.entity.EntityList.classToStringMapping.remove(npc.getClass());
    }
    @Test public void ordinaryNativeHireBaselineRemainsOwnedAndReceivesNoCampaignPublication(){
        assertTrue(npc.hiredNPCInfo.isActive);assertEquals(record.owner,npc.hiredNPCInfo.getHiringPlayerUUID());
        assertEquals(LOTRHiredNPCInfo.Task.WARRIOR,npc.hiredNPCInfo.getTask());
        KOMECampaignHiredPresentation.tracking(f.player,npc);KOMECampaignHiredPresentation.flush();assertTrue(f.network.messages.isEmpty());
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit(record));
    }
    @Test public void campaignPublishesExactNativeStateAfterTrackingAndRecordPublication(){
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        f.data.hiredUnits.remove(record.entity);
        KOMECampaignHiredPresentation.tracking(f.player,npc);assertTrue(f.network.messages.isEmpty());
        f.data.hiredUnits.put(record.entity,record);KOMECampaignHiredPresentation.flush();
        KOMEPacketCampaignHiredInfo p=packet();
        assertEquals(record.entity,p.physicalId);assertEquals(record.owner,p.nativeInfo.hiringPlayer);
        assertEquals(npc.hiredNPCInfo.getTask(),p.nativeInfo.task);assertEquals("North Guard",p.nativeInfo.squadron);
        assertEquals(npc.hiredNPCInfo.xpLevel,p.nativeInfo.xpLvl);assertTrue(npc.hiredNPCInfo.isActive);
        KOMECampaignHiredPresentation.flush();assertEquals(1,f.network.messages.size());
    }
    @Test public void haltedAndReadyCampaignPresentationUsesSameOwnerTask(){
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        NBTTagCompound nativeNbt=new NBTTagCompound();npc.hiredNPCInfo.writeToNBT(nativeNbt);
        nativeNbt.getCompoundTag("HiredNPCInfo").setBoolean("CanMove",false);
        f.world.isRemote=true;npc.hiredNPCInfo.readFromNBT(nativeNbt);f.world.isRemote=false;
        assertTrue(npc.hiredNPCInfo.isHalted());
        KOMECampaignHiredPresentation.tracking(f.player,npc);KOMECampaignHiredPresentation.flush();assertEquals(record.owner,packet().nativeInfo.hiringPlayer);
        f.network.messages.clear();f.world.isRemote=true;npc.hiredNPCInfo.ready();f.world.isRemote=false;
        assertFalse(npc.hiredNPCInfo.isHalted());
        KOMECampaignHiredPresentation.tracking(f.player,npc);KOMECampaignHiredPresentation.flush();assertEquals(record.owner,packet().nativeInfo.hiringPlayer);
    }
    @Test public void disconnectedUntrackedFailedSpawnAndStoppedSessionDoNotPublish(){
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        KOMECampaignHiredPresentation.tracking(f.player,npc);KOMECampaignHiredPresentation.stoppedTracking(f.player,npc);
        KOMECampaignHiredPresentation.flush();assertTrue(f.network.messages.isEmpty());
        KOMECampaignHiredPresentation.tracking(f.player,npc);KOMECampaignHiredPresentation.clear();
        KOMECampaignHiredPresentation.flush();assertTrue(f.network.messages.isEmpty());
        KOMECampaignHiredPresentation.tracking(f.player,npc);f.player.connected=false;
        KOMECampaignHiredPresentation.flush();assertTrue(f.network.messages.isEmpty());f.player.connected=true;
        KOMECampaignHiredPresentation.tracking(f.player,npc);f.world.loadedEntityList.clear();
        KOMECampaignHiredPresentation.flush();assertTrue(f.network.messages.isEmpty());
    }
    @Test public void persistedNativeHireStillPublishesOnRestartAndVerifiedReplacement() throws Exception {
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        priorType=net.minecraft.entity.EntityList.classToStringMapping.put(npc.getClass(),"kom89.presentation");
        record.stationedEntityData=KOMEEntitySnapshots.snapshot(record,npc);
        NBTTagCompound recordNbt=record.writeToNBT();
        KOMEHiredUnitRecord restored=new KOMEHiredUnitRecord();restored.readFromNBT(recordNbt);
        f.data.hiredUnits.put(restored.entity,restored);record=restored;
        NBTTagCompound physical=KOMECampaignHealth.movementSnapshot(record,record.stationedEntityData);
        KOMECampaignHealth.prepareReconstruction(physical);
        KOMECampaignMovementReconstructionTest.MovementNpc replacement=new KOMECampaignMovementReconstructionTest.MovementNpc(f.world);
        replacement.readFromNBT(physical);
        UUID old=record.entity;
        KOMECampaignHealth.reconstructed(record,old,replacement.getUniqueID(),null,null);
        f.data.hiredUnits.remove(old);record.entity=replacement.getUniqueID();f.data.hiredUnits.put(record.entity,record);
        f.world.loadedEntityList.clear();f.world.loadedEntityList.add(replacement);
        KOMECampaignHiredPresentation.tracking(f.player,replacement);KOMECampaignHiredPresentation.flush();
        assertEquals(record.owner,packet().nativeInfo.hiringPlayer);assertEquals(record.entity,packet().physicalId);
        assertTrue(replacement.hiredNPCInfo.isActive);assertEquals(LOTRHiredNPCInfo.Task.WARRIOR,replacement.hiredNPCInfo.getTask());
    }
    private KOMEPacketCampaignHiredInfo packet(){assertEquals(1,f.network.messages.size());return (KOMEPacketCampaignHiredInfo)f.network.messages.get(0);}
}
