package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEMasterDutyReliabilityTest {
    private static KOMEProgressionGoodsValue.Matcher originalMatcher;
    @org.junit.BeforeClass public static void nativeTradeFixture() {
        originalMatcher=KOMEProgressionGoodsValue.matcher;
        KOMEProgressionGoodsValue.matcher=(entry,stack)->{
            net.minecraft.item.ItemStack offered=entry.createTradeItem();
            return offered.getItem()==stack.getItem()&&(offered.getItemDamage()==32767||offered.getItemDamage()==stack.getItemDamage());
        };
    }
    @org.junit.AfterClass public static void restoreTradeFixture(){KOMEProgressionGoodsValue.matcher=originalMatcher;}
    private static void credit(KOMEProgressionFollowupTest.Session s,KOMESerfKnightDutyType type) {
        s.state.assignDuty(type,new NBTTagCompound());s.state.completeDuty(type);
    }
    private static void inOrder(KOMEProgressionFollowupTest.Session s) throws Exception {
        s.f.world.rand=new Random(){@Override public int nextInt(int bound){return bound-1;}};
        s.f.world.provider.worldChunkMgr=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);
    }
    @Test public void unavailableCourierFallsBackToRealOrdinaryObjectiveWithoutErasingCredits() throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            credit(s,KOMESerfKnightDutyType.PROVISIONING);credit(s,KOMESerfKnightDutyType.DEFENSE);inOrder(s);
            KOMEProgressionNpcRef master=s.state.getSerfdomMaster();
            KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master);
            assertTrue(result.reason,result.success);assertEquals("recovery",s.state.getActiveAssignmentKind());
            assertTrue(s.state.ordinaryObjective());assertFalse(s.state.getDuty(KOMESerfKnightDutyType.COURIER).isAssigned());
            assertEquals(2,KOMEServicePromotionService.duties(s.state));assertEquals(master.entityUuid,s.state.getSerfdomMaster().entityUuid);
            assertEquals(KOMEProgressionRank.SERF,s.p.getCanonicalRank());
            NBTTagCompound accepted=s.state.writeToNBT();int drops=s.drops();
            assertFalse(KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master).success);
            assertEquals(accepted,s.state.writeToNBT());assertEquals(drops,s.drops());
        }
    }
    @Test public void failedEscortPreparationDoesNotBindChargeOrLeakTrialIntoFallback() throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            credit(s,KOMESerfKnightDutyType.PROVISIONING);credit(s,KOMESerfKnightDutyType.COURIER);inOrder(s);
            LOTREntityNPC charge=KOMEAccessFixture.allocate(KOMESerfKnightStabilizationTest.Charge.class);
            charge.worldObj=s.f.world;charge.setUniqueID(UUID.randomUUID());charge.posX=s.master.posX+2;charge.posY=64;charge.posZ=s.master.posZ;
            charge.hiredNPCInfo=new LOTRHiredNPCInfo(charge);s.f.world.loadedEntityList.add(charge);
            assertSame(charge,KOMESerfKnightEscortService.findCharge(s.f.player,s.state,s.master));
            KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master);
            assertTrue(result.reason,result.success);assertEquals("recovery",s.state.getActiveAssignmentKind());
            assertFalse(s.state.getDuty(KOMESerfKnightDutyType.ESCORT).isAssigned());assertFalse(charge.hiredNPCInfo.isActive);
            assertEquals(2,KOMEServicePromotionService.duties(s.state));
            assertNull(KOMEProgressionEncounterMarker.read(charge));
        }
    }
    @Test public void exhaustedFeasiblePoolIsNonMutatingAndExplainsFailedPreparation() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            credit(s,KOMESerfKnightDutyType.PROVISIONING);credit(s,KOMESerfKnightDutyType.RECOVERY);inOrder(s);
            NBTTagCompound before=s.p.writeToNBT();int entities=s.f.world.loadedEntityList.size();
            KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master);
            assertFalse(result.success);assertTrue(result.reason,result.reason.contains("Courier"));assertTrue(result.reason.contains("preserved"));
            assertEquals(before,s.p.writeToNBT());assertEquals(entities,s.f.world.loadedEntityList.size());assertEquals(0,s.drops());
            s.p.readFromNBT(before);assertEquals(before,s.p.writeToNBT());
            assertFalse(KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master).success);assertEquals(before,s.p.writeToNBT());
        }
    }
    @Test public void acceptedDeliveryIsFrozenAcrossRepeatedRequestAndReload() throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            credit(s,KOMESerfKnightDutyType.COURIER);credit(s,KOMESerfKnightDutyType.RECOVERY);inOrder(s);
            KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master);
            assertTrue(result.reason,result.success);assertEquals("provisioning",s.state.getActiveAssignmentKind());
            NBTTagCompound accepted=s.p.writeToNBT();s.p.readFromNBT(accepted);
            assertFalse(KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master).success);assertEquals(accepted,s.p.writeToNBT());
            assertFalse(s.state.getDuty(KOMESerfKnightDutyType.PROFESSION).isAssigned());assertEquals(0,s.drops());
        }
    }
    @Test public void fallbackKeepsRecommendationsAndReportedServiceAcrossRestart() throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            credit(s,KOMESerfKnightDutyType.PROVISIONING);credit(s,KOMESerfKnightDutyType.DEFENSE);inOrder(s);
            s.p.getServiceRecognition().earn(s.f.player.id,KOMEProgressionNpcRankService.referenceOf(s.master),
                KOMEProgressionRank.SERF,KOMEProgressionRank.KNIGHT,"Historical witnessed service",1L);
            NBTTagCompound history=s.p.writeToNBT().getCompoundTag("ServiceRecognition");
            assertTrue(KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master).success);s.p.readFromNBT(s.p.writeToNBT());
            assertEquals(history,s.p.writeToNBT().getCompoundTag("ServiceRecognition"));assertEquals(2,KOMEServicePromotionService.duties(s.state));
            assertEquals("recovery",s.state.getActiveAssignmentKind());
        }
    }
    @Test public void rejectedDispatchSpawnFallsBackWithoutLetterOrPartialCourierState() throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            credit(s,KOMESerfKnightDutyType.PROVISIONING);credit(s,KOMESerfKnightDutyType.DEFENSE);inOrder(s);
            KOMEProgressionServiceLocationTest.territory(s.f);
            s.state.setSerfdomMaster(KOMEProgressionNpcRankService.referenceOf(s.master));
            KOMECourierGeographyTest.TestManager manager=(KOMECourierGeographyTest.TestManager)s.f.world.getWorldChunkManager();
            manager.biome.npcSpawnList=KOMEAccessFixture.allocate(ContactList.class);
            KOMESerfCourierAssignment prepared=KOMESerfCourierAssignment.create(s.state.getSerfdomMaster(),s.f.world,s.state.variety);
            assertNotNull(prepared);assertTrue(KOMECourierRecipientSpawner.prepareCorrespondent(s.f.world,prepared,s.master));
            s.f.world.spawnSucceeds=false;
            assertTrue(KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master).success);
            assertEquals("recovery",s.state.getActiveAssignmentKind());
            assertFalse(s.state.getDuty(KOMESerfKnightDutyType.COURIER).isAssigned());assertNull(s.state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            assertEquals(0,s.drops());assertEquals(2,KOMEServicePromotionService.duties(s.state));
            NBTTagCompound accepted=s.state.writeToNBT();
            assertFalse(KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master).success);assertEquals(accepted,s.state.writeToNBT());assertEquals(0,s.drops());
        }
    }
    @Test public void randomizedOrderRetainsVarietyAcrossIndependentAcceptedAssignments() throws Exception {
        Set<String> chosen=new HashSet<String>();
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();MaterialIdentity material=new MaterialIdentity()) {
            for(int seed=0;seed<12;seed++)try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
                credit(s,KOMESerfKnightDutyType.COURIER);credit(s,KOMESerfKnightDutyType.RECOVERY);inOrder(s);
                final boolean first=seed%2==0;s.f.world.rand=new Random(){@Override public int nextInt(int bound){return first?0:bound-1;}};
                assertFalse("The variety fixture requires an eligible profession catalog",KOMESerfProfessionCatalog.candidatesFor(KOMESerfProfessionClassifier.classify(s.master).materialProfile,"rohan").isEmpty());
                KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master);
                assertTrue(result.reason,result.success);chosen.add(s.state.getActiveAssignmentKind());
            }
        }
        assertEquals(new HashSet<String>(Arrays.asList("provisioning","profession")),chosen);
    }
    public static class Villager extends lotr.common.entity.npc.LOTREntityRohanMan {
        public Villager(net.minecraft.world.World world){super(world);}
        @Override public void setupNPCGender(){} // Suppress server watcher publication in the headless World fixture.
        @Override public void setupNPCName(){}
        @Override public String getNPCName(){return "Eadric";}
    }
    public static class ContactList extends lotr.common.world.spawning.LOTRBiomeSpawnList {
        private ContactList(){super("test");}
        @Override public boolean isFactionPresent(net.minecraft.world.World world,lotr.common.fac.LOTRFaction faction){return faction==lotr.common.fac.LOTRFaction.ROHAN;}
        @Override public java.util.List<lotr.common.world.spawning.LOTRSpawnEntry> getAllSpawnEntries(net.minecraft.world.World world){return Arrays.asList(new lotr.common.world.spawning.LOTRSpawnEntry(Villager.class,10,1,1));}
    }
    private static final class MaterialIdentity implements AutoCloseable {
        final java.lang.reflect.Field field=net.minecraft.init.Items.class.getField("coal");
        final Object previous=field.get(null);
        MaterialIdentity()throws Exception {
            // KOMEAccessFixture intentionally uses generic food identities for vanilla items.
            // Supply a real non-food material identity for this catalog/variety assertion.
            java.lang.reflect.Field modifiers=java.lang.reflect.Field.class.getDeclaredField("modifiers");modifiers.setAccessible(true);
            field.setAccessible(true);modifiers.setInt(field,field.getModifiers()&~java.lang.reflect.Modifier.FINAL);
            net.minecraft.item.Item material=(net.minecraft.item.Item)net.minecraft.item.Item.itemRegistry.getObject("minecraft:coal");
            if(material==null){material=new net.minecraft.item.Item();java.lang.reflect.Method raw=net.minecraft.item.Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);raw.setAccessible(true);raw.invoke(net.minecraft.item.Item.itemRegistry,24173,"minecraft:coal",material);}
            field.set(null,material);
        }
        @Override public void close()throws Exception {field.set(null,previous);}
    }

}
