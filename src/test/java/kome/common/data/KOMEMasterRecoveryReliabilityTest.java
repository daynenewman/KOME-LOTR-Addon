package kome.common.data;

import java.util.UUID;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import org.junit.Test;
import static org.junit.Assert.*;

/** Physical recovery and relationship transitions through the production services. */
public class KOMEMasterRecoveryReliabilityTest {
    private static KOMEProgressionNpcRef replacement() {
        return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"New Master","rohan",0,0,65,0);
    }
    private static KOMESerfKnightTrialAssignment recovery(KOMEProgressionFollowupTest.Session s) {
        KOMESerfKnightTrialAssignment a=KOMESerfKnightTrialAssignment.create(
            KOMESerfKnightTrial.forId("recovery"),s.state.getSerfdomMaster(),20,0);
        a.data.setBoolean("OrdinaryDuty",true);
        a.data.setTag("RecoveryItem",new ItemStack(Items.gold_ingot).writeToNBT(new NBTTagCompound()));
        s.state.setTrial(a);s.state.assignDuty(KOMESerfKnightDutyType.RECOVERY,a.writeToNBT());return a;
    }
    private static EntityItem activate(KOMEProgressionFollowupTest.Session s) {
        s.f.world.testChunkProvider=(net.minecraft.world.chunk.IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(
            KOMEMasterRecoveryReliabilityTest.class.getClassLoader(),new Class[]{net.minecraft.world.chunk.IChunkProvider.class},
            (proxy,method,args)->method.getName().equals("chunkExists")?true:method.getReturnType()==boolean.class?false:null);
        recovery(s);assertTrue(KOMESerfKnightRecoveryService.activate(s.f.player,s.p,s.master));
        return (EntityItem)s.f.world.loadedEntityList.stream().filter(e->e instanceof EntityItem).findFirst().get();
    }
    @Test public void rejectedPickupKeepsSearchUntilActualInventoryPossession()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            EntityItem item=activate(s);
            for(int i=0;i<s.f.player.inventory.mainInventory.length;i++)s.f.player.inventory.mainInventory[i]=new ItemStack(Items.stick,64);
            new KOMEEvents().onRecoveryItemPickup(new EntityItemPickupEvent(s.f.player,item));
            assertFalse(s.state.getTrialAssignment().data.getBoolean("RecoveryRetrieved"));
            assertNotNull(KOMESerfKnightRecoveryService.searchMarker(s.state.getTrialAssignment()));
            s.f.player.inventory.mainInventory[0]=item.getEntityItem().copy();item.setDead();
            KOMESerfKnightRecoveryService.tickPlayer(s.f.player);
            assertTrue(s.state.getTrialAssignment().data.getBoolean("RecoveryRetrieved"));
            assertNull(KOMESerfKnightRecoveryService.searchMarker(s.state.getTrialAssignment()));
        }
    }
    @Test public void activationReloadAndTickKeepOneRealAssignedObject()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            EntityItem item=activate(s);String token=s.state.getTrialAssignment().assignmentToken;
            assertTrue(KOMESerfKnightRecoveryService.isAssignedTo(item.getEntityItem(),s.state.getTrialAssignment(),s.f.player.id));
            assertEquals(Integer.MAX_VALUE,item.lifespan);
            s.state.readFromNBT(s.state.writeToNBT());
            assertEquals(token,s.state.getTrialAssignment().assignmentToken);
            assertFalse(KOMESerfKnightRecoveryService.reconcileLoadedItem(s.f.data,item));
            EntityItem duplicate=new EntityItem(s.f.world,item.posX,item.posY,item.posZ,item.getEntityItem().copy());
            assertTrue(KOMESerfKnightRecoveryService.reconcileLoadedItem(s.f.data,duplicate));assertTrue(duplicate.isDead);
            s.f.player.posX=item.posX;s.f.player.posY=item.posY;s.f.player.posZ=item.posZ;
            KOMESerfKnightRecoveryService.tickPlayer(s.f.player);
            assertEquals(1,s.drops());assertFalse(item.isDead);
        }
    }
    @Test public void leavingCancelsAllOrdinaryStagesAndInvalidatesLoadedAndUnloadedItems()throws Exception {
        for(KOMESerfKnightTrialAssignment.Stage stage:KOMESerfKnightTrialAssignment.Stage.values())
            try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
                EntityItem item=activate(s);KOMESerfKnightTrialAssignment a=s.state.getTrialAssignment();
                s.state.updateTrialAssignment(a.withStage(stage,a.data));
                KOMEProgressionEncounterCleanup.cleanup(s.f.player,s.p);
                assertTrue(KOMESerfKnightService.leaveSerfdomMaster(s.state).success);
                assertTrue(item.isDead);assertNull(s.state.getTrialAssignment());assertFalse(s.state.hasActiveAssignment());
                s.state.setSerfdomMaster(replacement());assertNull(s.state.getTrialAssignment());
                EntityItem later=new EntityItem(s.f.world,0,65,0,KOMESerfKnightRecoveryService.assignedStack(a,s.f.player.id));
                assertTrue(KOMESerfKnightRecoveryService.reconcileLoadedItem(s.f.data,later));assertTrue(later.isDead);
            }
    }
    @Test public void departedHistoryAndOriginalIssuersSurviveRepeatedLoadWithoutNewMasterCredit()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            KOMEProgressionNpcRef original=s.state.getSerfdomMaster();
            s.state.assignDuty(KOMESerfKnightDutyType.COURIER,null);s.state.completeDuty(KOMESerfKnightDutyType.COURIER);
            recovery(s);s.state.leaveSerfdomMaster();s.state.setSerfdomMaster(replacement());
            NBTTagCompound saved=s.state.writeToNBT();
            for(int i=0;i<2;i++)s.state.readFromNBT(saved);
            assertEquals(1,s.state.departedMasterService().tagCount());
            NBTTagCompound history=s.state.departedMasterService().getCompoundTagAt(0);
            assertEquals(original.entityUuid,KOMEProgressionNpcRef.readFromNBT(history.getCompoundTag("Completed").getCompoundTag("courier").getCompoundTag("Issuer")).entityUuid);
            assertEquals(0,KOMEServicePromotionService.duties(s.state));assertFalse(s.state.hasActiveAssignment());
            s.state.reset();assertEquals(0,s.state.departedMasterService().tagCount());
        }
    }
    @Test public void deathCancelsOrdinaryObjectiveAndRetainsCompletedService()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.state.assignDuty(KOMESerfKnightDutyType.COURIER,null);s.state.completeDuty(KOMESerfKnightDutyType.COURIER);
            recovery(s);s.state.handleMasterDeath(false);
            assertNull(s.state.getTrialAssignment());assertFalse(s.state.hasActiveAssignment());
            assertTrue(s.state.getDuty(KOMESerfKnightDutyType.COURIER).isCompleted());
        }
    }
    @Test public void surfaceRejectsNarrowLedgesObstructionsAndMissingTerrain()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            assertTrue(KOMESerfKnightRecoveryService.safeSite(s.f.world,0,65,0));
            net.minecraft.block.Block air=new net.minecraft.block.Block(net.minecraft.block.material.Material.air){};
            s.f.world.structureBlocks.put("1,64,0",air);
            assertFalse(KOMESerfKnightRecoveryService.safeSite(s.f.world,0,65,0));
            s.f.world.structureBlocks.clear();s.f.world.structureBlocks.put("1,65,0",net.minecraft.init.Blocks.planks);
            assertFalse(KOMESerfKnightRecoveryService.safeSite(s.f.world,0,65,0));
            s.f.world.structureBlocks.clear();s.f.world.unsafeSurface=true;
            recovery(s);assertFalse(KOMESerfKnightRecoveryService.activate(s.f.player,s.p,s.master));assertEquals(0,s.drops());
        }
    }
    @Test public void foreignMasterCannotReceiveOrClaimAnOldAssignment()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            EntityItem item=activate(s);s.f.player.inventory.mainInventory[0]=item.getEntityItem().copy();
            s.state.setSerfdomMaster(replacement());
            KOMESerfKnightRecoveryService.onPickup(s.f.player,item.getEntityItem());
            assertFalse(s.state.getTrialAssignment().data.getBoolean("RecoveryRetrieved"));
            assertFalse(KOMESerfKnightRecoveryService.deliver(s.f.player,s.p,s.master));
            s.state.readFromNBT(s.state.writeToNBT());
            assertNull(s.state.getTrialAssignment());assertFalse(s.state.hasActiveAssignment());
        }
    }
    @Test public void assignedItemHandoverCompletesOnceOnlyForItsIssuingMaster()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            EntityItem item=activate(s);s.f.player.inventory.mainInventory[0]=item.getEntityItem().copy();item.setDead();
            KOMESerfKnightRecoveryService.tickPlayer(s.f.player);
            assertTrue(KOMESerfKnightRecoveryService.deliver(s.f.player,s.p,s.master));
            assertEquals(KOMESerfKnightTrialAssignment.Stage.OBJECTIVE_COMPLETE,s.state.getTrialAssignment().stage);
            assertNull(s.f.player.inventory.mainInventory[0]);
            assertFalse(KOMESerfKnightRecoveryService.deliver(s.f.player,s.p,s.master));
        }
    }
    @Test public void missingRecordedObjectFailsRecoverablyWithoutManufacturingAnother()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            EntityItem item=activate(s);item.setDead();
            s.f.player.posX=item.posX;s.f.player.posY=item.posY;s.f.player.posZ=item.posZ;
            for(int i=0;i<30;i++){s.f.world.testWorldTime=i*40L;KOMESerfKnightRecoveryService.tickPlayer(s.f.player);}
            assertEquals(KOMESerfKnightTrialAssignment.Stage.FAILED,s.state.getTrialAssignment().stage);
            assertEquals(1,s.drops());assertEquals(0,KOMEServicePromotionService.duties(s.state));
        }
    }
    @Test public void rejectedWorldSpawnRetainsRetryableAssignmentWithoutItem()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            activate(s);s.state.retireMandatoryTrial(false);s.state.cancelTestDuty(KOMESerfKnightDutyType.RECOVERY);
            recovery(s);s.f.world.spawnSucceeds=false;
            assertFalse(KOMESerfKnightRecoveryService.activate(s.f.player,s.p,s.master));
            assertEquals(KOMESerfKnightTrialAssignment.Stage.ASSIGNED,s.state.getTrialAssignment().stage);
            assertTrue(s.state.getTrialAssignment().data.getBoolean("ActivationPending"));assertEquals(1,s.drops());
        }
    }
}
