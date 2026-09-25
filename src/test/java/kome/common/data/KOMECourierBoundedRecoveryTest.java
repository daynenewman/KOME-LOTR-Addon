package kome.common.data;

import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketHandler;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECourierBoundedRecoveryTest {
    @Test public void unloadedDestinationWaitsWithoutChangingAssignmentOrDispatch() throws Exception {
        KOMEAccessFixture fixture=fixture();KOMEProgressionNpcRef master=master(fixture);
        KOMESerfCourierAssignment first=KOMESerfCourierAssignment.create(master,fixture.world);assertNotNull(first);
        KOMESerfKnightProgression state=fixture.data.getProgression(fixture.player.id).getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);
        assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.COURIER,first.writeToNBT(),10L).success);
        fixture.player.inventory.mainInventory[0]=KOMECourierService.message(first,fixture.player,master);
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        try {KOMEPacketHandler.network=fixture.network;at(fixture,first);
            for(int i=0;i<20;i++){fixture.world.testWorldTime=i*100L;KOMECourierService.tickPlayer(fixture.player);}
            KOMESerfCourierAssignment waiting=active(state);
            assertEquals(first.token,waiting.token);assertEquals(first.destinationKey,waiting.destinationKey);
            assertEquals(first.storyVariant,waiting.storyVariant);assertFalse(waiting.recipient.isSet());
            assertEquals(1,dispatchCount(fixture,first.token));assertFalse(state.getDuty(KOMESerfKnightDutyType.COURIER).isCompleted());
        }finally{KOMEPacketHandler.network=previous;}
    }
    @Test public void loadedRecipientThatBecomesHiredIsClearedWithoutTreatingUnloadAsDeath() throws Exception {
        KOMEAccessFixture fixture=fixture();KOMEProgressionNpcRef master=master(fixture);
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.create(master,LOTRWaypoint.EDORAS);
        KOMESerfKnightProgression state=fixture.data.getProgression(fixture.player.id).getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);
        TestNpc npc=KOMEAccessFixture.allocate(TestNpc.class);npc.id=UUID.randomUUID();npc.setUniqueID(npc.id);
        npc.worldObj=fixture.world;npc.posX=assignment.destinationX;npc.posZ=assignment.destinationZ;
        npc.hiredNPCInfo=new LOTRHiredNPCInfo(npc);npc.hiredNPCInfo.isActive=true;
        assignment.recipient=new KOMEProgressionNpcRef(npc.id.toString(),"Recipient","rohan",fixture.player.dimension,npc.posX,64,npc.posZ);
        assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.COURIER,assignment.writeToNBT(),10L).success);
        fixture.player.inventory.mainInventory[0]=KOMECourierService.message(assignment,fixture.player,master);
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        try {
            KOMEPacketHandler.network=fixture.network;
            KOMECourierService.tickPlayer(fixture.player);
            assertTrue("unloaded is inconclusive",active(state).recipient.isSet());
            fixture.world.loadedEntityList.add(npc);
            KOMECourierService.tickPlayer(fixture.player);
            assertFalse("loaded invalid recipient must be released",active(state).recipient.isSet());
            assertEquals(assignment.token,active(state).token);
        } finally {KOMEPacketHandler.network=previous;}
    }

    @Test public void validRecipientDeliveryAndMasterReportCompleteExactlyOnce() throws Exception {
        KOMEAccessFixture fixture=fixture();KOMEProgressionNpcRef master=master(fixture);
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.create(master,fixture.world);
        assertNotNull(assignment);
        KOMESerfKnightProgression state=fixture.data.getProgression(fixture.player.id).getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success);
        assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.COURIER,assignment.writeToNBT(),10L).success);
        fixture.player.inventory.mainInventory[0]=KOMECourierService.message(assignment,fixture.player,master);
        TestNpc npc=KOMEAccessFixture.allocate(TestNpc.class);npc.id=UUID.randomUUID();npc.setUniqueID(npc.id);
        npc.worldObj=fixture.world;npc.posX=assignment.destinationX;npc.posZ=assignment.destinationZ;
        npc.hiredNPCInfo=new LOTRHiredNPCInfo(npc);fixture.world.loadedEntityList.add(npc);
        assignment.recipient=KOMEProgressionNpcRankService.referenceOf(npc);state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,assignment.writeToNBT());
        at(fixture,assignment);
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        try {
            KOMEPacketHandler.network=fixture.network;
            assertEquals(npc.id.toString(),active(state).recipient.entityUuid);
            assertEquals(LOTRFaction.ROHAN,lotr.common.LOTRLevelData.getData(fixture.player).getPledgeFaction());
            assertEquals("rohan",active(state).masterFactionKey);
            assertEquals("LOTR and stored faction keys intentionally differ in case","ROHAN",LOTRFaction.ROHAN.codeName());
            assertTrue(active(state).recipient.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc)));
            assertTrue(active(state).atDestination(npc.worldObj.provider.dimensionId,npc.posX,npc.posZ,KOMECourierService.SETTLEMENT_RADIUS));
            assertTrue(fixture.player.getDistanceSqToEntity(npc)<=64D);
            assertTrue("recipient must pass live validation",KOMECourierService.validRecipient(fixture.player,npc,active(state),master));
            assertTrue("rewritten physical dispatch must still match",KOMECourierService.hasMessage(fixture.player,active(state),master));
            assertTrue(KOMECourierService.deliverToRecipient(fixture.player,fixture.data,npc));
            assertEquals(KOMESerfCourierAssignment.Stage.DELIVERED,active(state).stage);
            assertFalse(state.getDuty(KOMESerfKnightDutyType.COURIER).isCompleted());
            assertTrue(KOMECourierService.reportToMaster(fixture.player,fixture.data,fixture.data.getProgression(fixture.player.id)));
            assertTrue(state.getDuty(KOMESerfKnightDutyType.COURIER).isCompleted());
            assertFalse(KOMECourierService.reportToMaster(fixture.player,fixture.data,fixture.data.getProgression(fixture.player.id)));
        } finally {KOMEPacketHandler.network=previous;}
    }

    private static KOMEAccessFixture fixture() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();fixture.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;
        fixture.player.dimension=LOTRDimension.MIDDLE_EARTH.dimensionID;
        fixture.player.inventory=new InventoryPlayer(fixture.player);fixture.player.inventoryContainer=new TestContainer();
        fixture.pledge(LOTRFaction.ROHAN);fixture.data.getProgression(fixture.player.id).setCanonicalRank(KOMEProgressionRank.SERF);
        KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);
        biome.heightBaseParameter=0.2F;biome.npcSpawnList=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestSpawnList.class);
        KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);
        manager.biome=biome;fixture.world.provider.worldChunkMgr=manager;return fixture;
    }
    private static KOMEProgressionNpcRef master(KOMEAccessFixture fixture){return new KOMEProgressionNpcRef(
        UUID.randomUUID().toString(),"Master","rohan",fixture.player.dimension,
        LOTRWaypoint.EDORAS.getXCoord()+900,64,LOTRWaypoint.EDORAS.getZCoord());}
    private static void at(KOMEAccessFixture fixture,KOMESerfCourierAssignment assignment){fixture.player.posX=assignment.destinationX;fixture.player.posZ=assignment.destinationZ;}
    private static KOMESerfCourierAssignment active(KOMESerfKnightProgression state){return KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());}
    private static int dispatchCount(KOMEAccessFixture fixture,String token){int count=0;for(net.minecraft.item.ItemStack stack:fixture.player.inventory.mainInventory)
        if(stack!=null&&stack.hasTagCompound()&&stack.getTagCompound().hasKey("KOMECourier",10)
            &&token.equals(stack.getTagCompound().getCompoundTag("KOMECourier").getString("Assignment")))count++;return count;}
    private static final class TestContainer extends Container {public boolean canInteractWith(EntityPlayer player){return true;}}
    public static final class TestNpc extends lotr.common.entity.npc.LOTREntityRohirrimWarrior {
        UUID id;private TestNpc(){super(null);}
        @Override public UUID getUniqueID(){return id;}
        @Override public String getNPCName(){return "Recipient";}
        @Override public LOTRFaction getFaction(){return LOTRFaction.ROHAN;}
        @Override public boolean isEntityAlive(){return true;}
        @Override public boolean isChild(){return false;}
    }
}
