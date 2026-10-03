package kome.common.data;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketHandler;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionFixesTest {
    public static class Worker extends LOTREntityRohanMan {
        private Worker(){super(null);}
        @Override public boolean isEntityAlive(){return true;}
        @Override public boolean isChild(){return false;}
        @Override public String getNPCName(){return "Worker";}
    }
    public static class Captain extends LOTREntityRohirrimMarshal {
        boolean accessible=true;
        private Captain(){super(null);}
        @Override public boolean isEntityAlive(){return true;}
        @Override public boolean isChild(){return false;}
        @Override public String getNPCName(){return "Captain";}
        @Override public boolean canTradeWith(EntityPlayer player){return accessible;}
    }
    private static Worker worker(KOMEAccessFixture f)throws Exception {
        Worker n=KOMEAccessFixture.allocate(Worker.class);n.worldObj=f.world;n.setUniqueID(UUID.randomUUID());n.questInfo=new InertQuestInfo(n);return n;
    }
    private static class InertQuestInfo extends LOTREntityQuestInfo {
        InertQuestInfo(LOTREntityNPC npc){super(npc);}
        @Override public void sendData(net.minecraft.entity.player.EntityPlayerMP p){ }
    }
    private static void alignment(KOMEAccessFixture f,float value)throws Exception {
        java.lang.reflect.Field field=LOTRPlayerData.class.getDeclaredField("alignments");field.setAccessible(true);
        ((Map)field.get(LOTRLevelData.getData(f.player))).put(LOTRFaction.ROHAN,value);
    }
    private static void duties(KOMESerfKnightProgression s){for(KOMESerfKnightDutyType t:KOMESerfKnightDutyType.values()){s.assignDuty(t,null);s.completeDuty(t);}}

    @Test public void acceptingMasterClearsOnlyThatPlayersOffersAndReloadedShells()throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.ROHAN);
        Worker master=worker(f),other=worker(f);f.world.loadedEntityList.add(master);f.world.loadedEntityList.add(other);
        KOMEAccessFixture stranger=new KOMEAccessFixture();
        KOMESerfdomOfferQuest shell=new KOMESerfdomOfferQuest(LOTRLevelData.getData(f.player));
        master.questInfo.setPlayerSpecificOffer(f.player,shell);other.questInfo.setPlayerSpecificOffer(f.player,shell);
        other.questInfo.setPlayerSpecificOffer(stranger.player,shell);
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper kome=KOMEPacketHandler.network;
        try {
            KOMEPacketHandler.network=f.network;
            assertTrue(KOMESerfdomMasterService.serve(f.player,f.data,master).success);
            assertNull(master.questInfo.getOfferFor(f.player));assertNull(other.questInfo.getOfferFor(f.player));
            assertSame(shell,other.questInfo.getOfferFor(stranger.player));
            assertTrue(KOMESerfdomMasterService.validateCurrentMasterInteraction(f.player,f.data,master,false).success);
            assertFalse(KOMESerfdomMasterService.validateMasterSelection(f.player,f.data,other,false).success);
            other.questInfo=new InertQuestInfo(other);other.questInfo.setPlayerSpecificOffer(f.player,shell);
            assertFalse(KOMEProgressionOfferBridge.canOffer(other.questInfo,f.player));
            assertFalse(KOMEProgressionOfferBridge.ensureSerfdomOffer(f.player,other));
            assertNull(other.questInfo.getOfferFor(f.player));
        }finally{KOMEPacketHandler.network=kome;}
    }

    @Test public void dutiesAlignmentAndOwnCaptainRequirementsUseOneGate()throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.ROHAN);
        KOMEPlayerProgression p=f.data.getProgression(f.player.id);p.setCanonicalRank(KOMEProgressionRank.SERF);
        KOMESerfKnightProgression s=p.getSerfKnightProgression();s.setSerfdomMaster(new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,0,64,0));
        Captain captain=KOMEAccessFixture.allocate(Captain.class);captain.worldObj=f.world;captain.setUniqueID(UUID.randomUUID());captain.accessible=true;
        captain.questInfo=new LOTREntityQuestInfo(captain);captain.hiredNPCInfo=new LOTRHiredNPCInfo(captain);
        alignment(f,150F);assertFalse(KOMEProgressionOfferBridge.canRequestStandingTrialFrom(f.player,captain));
        duties(s);alignment(f,149F);assertFalse(KOMEProgressionOfferBridge.canRequestStandingTrialFrom(f.player,captain));
        assertFalse(KOMESerfKnightService.acceptStandingTrial(s,f.data,captain,new Random(2),KOMESerfKnightService.calendarDayNow(),f.player.id).success);
        assertFalse(s.hasLiege());assertEquals("alignment",KOMEProgressionTrackerSnapshot.project(f.player,p).iconKey);
        alignment(f,150F);assertTrue(KOMEProgressionOfferBridge.canRequestStandingTrialFrom(f.player,captain));
        assertEquals("standing_trial_ready",KOMEProgressionTrackerSnapshot.project(f.player,p).iconKey);
        captain.accessible=false;assertFalse(KOMEProgressionOfferBridge.canRequestStandingTrialFrom(f.player,captain));
        captain.accessible=true;
        assertTrue(KOMESerfKnightService.acceptStandingTrial(s,f.data,captain,new Random(2),KOMESerfKnightService.calendarDayNow(),f.player.id).success);
        alignment(f,0F);assertTrue(s.hasLiege());assertEquals("trial",s.getActiveAssignmentKind());
        assertTrue(KOMEProgressionTrackerSnapshot.project(f.player,p).visible);
    }

    @Test public void verifiedFactionThresholdsIgnoreExceptionalCaptains(){
        assertEquals(150,KOMEStandingTrialEligibility.requiredAlignment("DORWINION"));
        assertEquals(150,KOMEStandingTrialEligibility.requiredAlignment("MORDOR"));
        assertEquals(150,KOMEStandingTrialEligibility.requiredAlignment("GONDOR"));
        assertEquals(200,KOMEStandingTrialEligibility.requiredAlignment("BLUE_MOUNTAINS"));
        assertEquals(200,KOMEStandingTrialEligibility.requiredAlignment("DURINS_FOLK"));
        assertEquals(250,KOMEStandingTrialEligibility.requiredAlignment("WOOD_ELF"));
        assertEquals(300,KOMEStandingTrialEligibility.requiredAlignment("HIGH_ELF"));
        assertEquals(300,KOMEStandingTrialEligibility.requiredAlignment("LOTHLORIEN"));
        assertEquals(300,KOMEStandingTrialEligibility.requiredAlignment("RANGER_NORTH"));
        assertEquals(200,KOMEStandingTrialEligibility.requiredAlignment("TAURETHRIM"));
        assertEquals(200,KOMEStandingTrialEligibility.requiredAlignment("HALF_TROLL"));
    }

    @Test public void emptySlotPreferenceRemainsAvailableForPouchRewards(){
        ItemStack[] inventory=new ItemStack[36];inventory[0]=new ItemStack(Items.bread);
        assertEquals(1,KOMECourierService.emptyBookSlot(inventory));
        Arrays.fill(inventory,new ItemStack(Items.bread));inventory[14]=null;
        assertEquals(14,KOMECourierService.emptyBookSlot(inventory));
        inventory[14]=new ItemStack(Items.bread);assertEquals(-1,KOMECourierService.emptyBookSlot(inventory));
    }

    @Test public void newDestinationsAreBoundedButOldDutiesKeepTheirCoordinates(){
        assertFalse(KOMESerfCourierAssignment.withinNewDestinationRange(0,0,499,0));
        assertTrue(KOMESerfCourierAssignment.withinNewDestinationRange(0,0,500,0));
        assertTrue(KOMESerfCourierAssignment.withinNewDestinationRange(0,0,1500,0));
        assertFalse(KOMESerfCourierAssignment.withinNewDestinationRange(0,0,1501,0));
        lotr.common.world.map.LOTRWaypoint w=lotr.common.world.map.LOTRWaypoint.EDORAS;
        KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,w.getXCoord()+900,64,w.getZCoord());
        NBTTagCompound old=KOMESerfCourierAssignment.create(master,w).writeToNBT();old.setInteger("Version",6);old.removeTag("MaximumTravelDistance");old.setDouble("DestinationX",master.x+4000);
        KOMESerfCourierAssignment loaded=KOMESerfCourierAssignment.readFromNBT(old);
        assertNotNull(loaded);assertEquals(master.x+4000,loaded.destinationX,0);assertTrue(loaded.withinRecipientRange(loaded.destinationX,loaded.destinationZ));
        assertEquals(loaded.destinationX,KOMESerfCourierAssignment.readFromNBT(loaded.writeToNBT()).destinationX,0);
    }
}
