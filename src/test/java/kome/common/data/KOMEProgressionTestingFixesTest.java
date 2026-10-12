package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRMod;
import lotr.common.item.LOTRItemMug;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionTestingFixesTest {
    private static KOMEProgressionNpcRef ref(String name){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,100,64,200);}
    @Test public void culturalAuthorityNamesPreserveEpithetsAndDoNotStack(){
        for(String faction:new String[]{"dunland","gondor","rohan","highelves","woodelf","durinsfolk","mordor","harad"}){
            String personal="Gair, the Grey";
            String name=KOMEProgressionNativeAuthority.name(faction,KOMEProgressionNpcRank.PRINCE,personal);
            assertTrue(name.startsWith(personal+", "));assertEquals(personal,KOMEProgressionNativeAuthority.personalIdentity(faction,name));
            assertEquals(name,KOMEProgressionNativeAuthority.name(faction,KOMEProgressionNpcRank.PRINCE,name));
            assertFalse(name.contains("lotr.faction."));
        }
    }
    @Test public void nativeOccupationIsRetainedForOrdinaryNpc()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            assertEquals("Farmer",KOMEProgressionNativeAuthority.nativeOccupation(s.master,"Aldor","Farmer"));
            assertEquals("Farmer",KOMEProgressionNativeAuthority.nativeOccupation(s.master,"Aldor, the Grey","Farmer"));
        }
    }
    @Test public void nobleNativeFormatterUsesOneAuthoritativeName()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();
            String name=KOMEProgressionNativeAuthority.name("rohan",KOMEProgressionNpcRank.PRINCE,"Aldor");
            assertEquals(name,KOMEProgressionNativeAuthority.nativeOccupation(s.liege(),name,"Rohirrim Marshal"));
            assertEquals("Rohirrim Marshal",KOMEProgressionNativeAuthority.nativeOccupation(s.liege(),"Aldor, the Tall","Rohirrim Marshal"));
        }
    }
    @Test public void returningCourierNavigatesToItsMasterOnly()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMESerfCourierAssignment a=s.courier();assertFalse(KOMEVisualLocationService.markersFor(s.p).get(0).objective);
            a.stage=KOMESerfCourierAssignment.Stage.DELIVERED;s.state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());
            List<KOMEVisualMarker> markers=KOMEVisualLocationService.markersFor(s.p);
            assertEquals(1,markers.stream().filter(m->m.objective).count());assertEquals(s.state.getSerfdomMaster().entityUuid,markers.get(0).entityUuid);assertTrue(markers.get(0).objective);
        }
    }
    @Test public void completedStandingObjectiveNavigatesToLiegeNotGift()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();List<KOMEVisualMarker> markers=KOMEVisualLocationService.markersFor(s.p);
            assertEquals(1,markers.stream().filter(m->m.objective).count());
            assertTrue(markers.stream().anyMatch(m->m.objective&&m.entityUuid.equals(s.state.getLiege().entityUuid)));
            assertFalse(markers.stream().anyMatch(m->m.role==KOMEVisualMarker.Role.MASTER_GIFT&&m.objective));
        }
    }
    @Test public void deceasedCourierRecipientNavigatesToMasterForInstructions()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMESerfCourierAssignment a=s.courier();a.confirmedRecipientDeath=true;s.state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());
            List<KOMEVisualMarker> markers=KOMEVisualLocationService.markersFor(s.p);assertEquals(1,markers.size());assertTrue(markers.get(0).objective);assertEquals(s.state.getSerfdomMaster().entityUuid,markers.get(0).entityUuid);
        }
    }
    @Test public void recoveredKeepsakeNavigatesToItsCurrentLiege()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();KOMEProgressionNpcRef liege=s.state.getLiege();NBTTagCompound data=new NBTTagCompound();data.setBoolean("RecoveryRetrieved",true);
            s.state.setTrial(new KOMESerfKnightTrialAssignment("recovery",UUID.randomUUID().toString(),liege,"rohan",10,KOMESerfKnightTrialAssignment.Stage.ACTIVE,0,data));
            List<KOMEVisualMarker> markers=KOMEVisualLocationService.markersFor(s.p);assertEquals(1,markers.size());assertTrue(markers.get(0).objective);assertEquals(liege.entityUuid,markers.get(0).entityUuid);
        }
    }
    @Test public void escortDestinationNameIsIndependentOfSavedShelterName()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMEProgressionNpcRef liege=ref("Liege");s.state.setLiege(liege);
            NBTTagCompound data=new NBTTagCompound();data.setDouble(KOMESerfKnightEscortService.DEST_X,400);data.setDouble(KOMESerfKnightEscortService.DEST_Z,700);data.setString(KOMESerfKnightEscortService.DEST_NAME,"the shelter marked on your map");
            s.state.setTrial(new KOMESerfKnightTrialAssignment("escort",UUID.randomUUID().toString(),liege,"rohan",1,KOMESerfKnightTrialAssignment.Stage.ACTIVE,0,data));
            KOMEVisualMarker destination=KOMEVisualLocationService.markersFor(s.p).stream().filter(m->m.role==KOMEVisualMarker.Role.ESCORT&&m.entityUuid.isEmpty()).findFirst().get();
            assertEquals("Destination",destination.title);assertEquals(400,destination.x,0);assertEquals(700,destination.z,0);
            KOMEProgressionTrackerSnapshot hud=KOMEProgressionTrackerSnapshot.project(s.f.player,s.p);
            assertEquals("Escort the traveler to safety",hud.objective);assertFalse(hud.hasNumericProgress());assertEquals("",hud.progress);
        }
    }
    static NBTTagCompound proof(){NBTTagCompound p=new NBTTagCompound();p.setString("Status","EXISTING_VERIFIED");p.setInteger("Dimension",0);p.setDouble("ArrivalX",100);p.setDouble("ArrivalY",64);p.setDouble("ArrivalZ",200);return p;}
    @Test public void arrivalUsesCloseHorizontalAndVerticalGeometry(){
        NBTTagCompound p=proof();assertTrue(KOMEProgressionDestinations.nearArrival(p,0,106,64,200));
        assertFalse(KOMEProgressionDestinations.nearArrival(p,0,107,64,200));assertFalse(KOMEProgressionDestinations.nearArrival(p,0,100,67,200));
        assertFalse(KOMEProgressionDestinations.nearArrival(p,1,100,64,200));assertFalse(KOMEProgressionDestinations.nearArrival(p,0,Double.NaN,64,200));
        p.removeTag("ArrivalY");assertFalse(KOMEProgressionDestinations.nearArrival(p,0,100,64,200));
    }
    @Test public void technicalActivationKeepsIdentityAndRefundsDayAcrossSave()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();
            s.state.setLiege(ref("Liege"));NBTTagCompound data=new NBTTagCompound();data.setLong("PreviousTrialDay",-1);
            String token=UUID.randomUUID().toString();s.state.setTrial(new KOMESerfKnightTrialAssignment("defense",token,s.state.getLiege(),"rohan",10,KOMESerfKnightTrialAssignment.Stage.ASSIGNED,0,data));s.state.setLastTrialAssignmentEpochDay(10);
            KOMEProgressionEncounterCleanup.activationFailed(s.f.data,s.f.world,s.state);
            assertEquals(-1,s.state.getLastTrialAssignmentEpochDay());assertTrue(s.state.hasLiege());assertEquals(0,s.state.failedLiegeRemaining(s.state.getLiege(),System.currentTimeMillis()));
            s.state.readFromNBT(s.state.writeToNBT());assertEquals(-1,s.state.getLastTrialAssignmentEpochDay());assertEquals(token,s.state.getTrialAssignment().assignmentToken);
            assertEquals(KOMESerfKnightTrialAssignment.Stage.ASSIGNED,s.state.getTrialAssignment().stage);assertFalse(s.state.isTrialCompleted());
            s.state.updateTrialAssignment(s.state.getTrialAssignment().withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,null));KOMEProgressionEncounterCleanup.activationSucceeded(s.state);
            assertEquals(KOMESerfKnightService.calendarDayNow(),s.state.getLastTrialAssignmentEpochDay());assertFalse(s.state.getTrialAssignment().data.getBoolean("ActivationPending"));
        }
    }
    @Test public void genuineFailureStillReleasesLiegeAndPreservesConsequences()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMEProgressionNpcRef liege=ref("Liege");s.state.setLiege(liege);s.state.setLastTrialAssignmentEpochDay(10);
            s.state.setTrial(new KOMESerfKnightTrialAssignment("defense",UUID.randomUUID().toString(),liege,"rohan",10,KOMESerfKnightTrialAssignment.Stage.ACTIVE,0,new NBTTagCompound()));
            KOMEProgressionEncounterCleanup.failTrial(s.f.data,null,s.state);assertFalse(s.state.hasLiege());assertEquals(10,s.state.getLastTrialAssignmentEpochDay());
            assertTrue(s.state.failedLiegeRemaining(liege,System.currentTimeMillis())>0);
        }
    }
    @Test public void basicNativePermissionsNoLongerDependOnStoredFlags()throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();assertTrue(KOMEProgressionPermissions.has(f.player,KOMEProgressionPermissions.POUCHES));assertTrue(KOMEProgressionPermissions.has(f.player,KOMEProgressionPermissions.NPC_TRADE));
        assertTrue(f.data.progressions.isEmpty());assertFalse(KOMEProgressionPermissions.has(f.player,KOMEProgressionPermissions.COOKING));assertFalse(KOMEProgressionPermissions.has(f.player,KOMEProgressionPermissions.FACTION_GEAR));
        assertEquals(KOMEProgressionPermissionRegistry.Status.OBSOLETE,KOMEProgressionPermissionRegistry.gate(KOMEProgressionPermissions.POUCHES).status);
    }
    @Test public void drinkBookNamesExposeTheActualNativeVariant()throws Exception{
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems()){
            LOTRItemMug ale=new LOTRItemMug(1F);KOMESerfProvisioningAssignment.Requirement r=new KOMESerfProvisioningAssignment.Requirement("missing:ale",Item.getIdFromItem(ale),0,3,0,"Ale","BOTTLE",true,3);
            assertTrue(r.requirementName().contains("Bottle"));assertTrue(r.requirementName().contains("Strong"));assertTrue(r.progressText().contains(r.requirementName()));
            ItemStack stack=new ItemStack(ale);LOTRItemMug.setVessel(stack,LOTRItemMug.Vessel.BOTTLE,true);LOTRItemMug.setStrengthMeta(stack,3);assertTrue(KOMESerfProvisioningService.matches(stack,r));
            LOTRItemMug.setStrengthMeta(stack,2);assertFalse(KOMESerfProvisioningService.matches(stack,r));
        }
    }
    @Test public void legacyDrinkDescriptionExplicitlyAcceptsEquivalentVessels(){
        KOMESerfProvisioningAssignment.Requirement r=new KOMESerfProvisioningAssignment.Requirement("missing:drink",1,0,3,0,"Ale","",true,-1);
        assertTrue(r.progressText().contains("any vessel or strength"));
        assertTrue(KOMESerfProvisioningService.matchesIdentity(new KOMESerfProvisioningService.StackView(1,903,1,"SKIN",4),r));
    }
    @Test public void successfulReleaseSuppressesOnlyItsNativeMessageEvenAfterExceptions()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            class Hired extends lotr.common.entity.npc.LOTRHiredNPCInfo {
                boolean fail;int releases;
                Hired(){super(s.master);}
                @Override public void dismissUnit(boolean desert){releases++;KOMEProgressionEscortFollowing.nativeDismissalMessage(s.f.player,new net.minecraft.util.ChatComponentText("dismissed"));if(fail)throw new IllegalStateException("test release error");isActive=false;}
            }
            Hired hired=new Hired();s.master.hiredNPCInfo=hired;hired.isActive=true;hired.teleportAutomatically=true;
            KOMEProgressionEscortFollowing.disable(s.master);assertFalse(hired.teleportAutomatically);
            KOMEProgressionEscortFollowing.releaseDelivered(s.master);assertEquals(1,hired.releases);assertFalse(hired.isActive);assertTrue(hired.teleportAutomatically);assertFalse(s.f.player.messages.contains("dismissed"));
            KOMEProgressionEscortFollowing.nativeDismissalMessage(s.f.player,new net.minecraft.util.ChatComponentText("ordinary dismissal"));assertTrue(s.f.player.messages.contains("ordinary dismissal"));
            hired.fail=true;try{KOMEProgressionEscortFollowing.releaseDelivered(s.master);fail("Expected fixture error");}catch(IllegalStateException expected){}
            KOMEProgressionEscortFollowing.nativeDismissalMessage(s.f.player,new net.minecraft.util.ChatComponentText("after failed release"));assertTrue(s.f.player.messages.contains("after failed release"));
        }
    }
    @Test public void nativeWaterBottleContentsMatterButPersonalItemNamesDoNot()throws Exception{
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems()){
            KOMESerfProvisioningAssignment.Requirement r=new KOMESerfProvisioningAssignment.Requirement("missing:water",Item.getIdFromItem(LOTRMod.mugWater),0,3,0,"Water","BOTTLE",true,-1);
            ItemStack water=new ItemStack(net.minecraft.init.Items.potionitem,1,0);assertTrue(KOMESerfProvisioningService.matches(water,r));water.setStackDisplayName("My flask");assertTrue(KOMESerfProvisioningService.matches(water,r));
            net.minecraft.nbt.NBTTagList effects=new net.minecraft.nbt.NBTTagList();NBTTagCompound effect=new NBTTagCompound();effect.setByte("Id",(byte)1);effect.setInteger("Duration",100);effects.appendTag(effect);water.getTagCompound().setTag("CustomPotionEffects",effects);assertFalse(KOMESerfProvisioningService.matches(water,r));
        }
    }
}
