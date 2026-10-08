package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionHierarchyCorrectionsTest {
    @org.junit.Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    @Test public void wandererObjectiveUsesActualPledgeAndHasNoOldMeter()throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();KOMEPlayerProgression p=f.data.getProgression(f.player.id);f.pledge(null);
        assertEquals("Pledge to a faction",KOMEProgressionTrackerSnapshot.project(f.player,p).objective);
        assertFalse(KOMEProgressionTrackerSnapshot.project(f.player,p).hasNumericProgress());
        f.pledge(LOTRFaction.ROHAN);
        assertEquals("Find a Master to serve",KOMEProgressionTrackerSnapshot.project(f.player,p).objective);
        KOMEPlayerProgression loaded=new KOMEPlayerProgression();loaded.readFromNBT(p.writeToNBT());
        assertEquals("Find a Master to serve",KOMEProgressionTrackerSnapshot.project(f.player,loaded).objective);
        assertEquals(0F,KOMEProgressionTrackerSnapshot.project(f.player,loaded).completion,0F);
    }
    @Test public void masterPledgeChecksRejectNoneWrongFactionAndStaleAliases(){
        assertFalse(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertFalse(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"gondor","rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertFalse(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"retired_rohan","retired_rohan",KOMEProgressionNpcRank.UNRANKED,true).success);
        assertTrue(KOMESerfdomMasterService.validateMasterSelection(KOMEProgressionRank.WANDERER,"rohan","ROHAN",KOMEProgressionNpcRank.UNRANKED,true).success);
    }
    @Test public void realMasterServiceRequiresMatchingPledge()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.state.reset();s.p.setCanonicalRank(KOMEProgressionRank.WANDERER);s.f.pledge(null);
            assertFalse(KOMESerfdomMasterService.serve(s.f.player,s.f.data,s.master).success);assertFalse(s.state.getSerfdomMaster().isSet());
            s.f.pledge(LOTRFaction.GONDOR);assertFalse(KOMESerfdomMasterService.serve(s.f.player,s.f.data,s.master).success);
            s.f.pledge(LOTRFaction.ROHAN);assertTrue(KOMESerfdomMasterService.serve(s.f.player,s.f.data,s.master).success);
            assertEquals(KOMEProgressionRank.SERF,s.p.getCanonicalRank());assertTrue(s.state.hasMasterRelationship());
        }
    }
    @Test public void pledgeRevisionInvalidatesAChangedAndRestoredPledge(){
        KOMEPlayerProgression p=new KOMEPlayerProgression();long first=p.observeOfferPledge("rohan");
        assertEquals(first,p.observeOfferPledge("rohan"));p.observeOfferPledge("gondor");
        assertTrue(p.observeOfferPledge("rohan")>first);
        KOMEPlayerProgression loaded=new KOMEPlayerProgression();loaded.readFromNBT(p.writeToNBT());
        assertEquals(p.observeOfferPledge("rohan"),loaded.observeOfferPledge("rohan"));
    }
    @Test public void alignmentTransitionIsANewNonNumericObjective()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            for(KOMESerfKnightDutyType t:KOMESerfKnightDutyType.values()){s.state.assignDuty(t,null);s.state.completeDuty(t);}
            java.lang.reflect.Field a=lotr.common.LOTRPlayerData.class.getDeclaredField("alignments");a.setAccessible(true);
            ((Map)a.get(LOTRLevelData.getData(s.f.player))).put(LOTRFaction.ROHAN,149F);
            assertEquals("alignment",KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).iconKey);
            ((Map)a.get(LOTRLevelData.getData(s.f.player))).put(LOTRFaction.ROHAN,150F);
            KOMEProgressionTrackerSnapshot next=KOMEProgressionTrackerSnapshot.project(s.f.player,s.p);
            assertEquals("Seek a prospective Liege",next.objective);assertEquals("",next.progress);assertEquals(0F,next.completion,0F);
            assertTrue(s.state.hasMasterRelationship());assertFalse(next.hasNumericProgress());
        }
    }
    @Test public void masterMedallionSurvivesDutyCooldownAlignmentAndReloadUntilFealty()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.state.assignDuty(KOMESerfKnightDutyType.PROFESSION,null);s.state.completeDuty(KOMESerfKnightDutyType.PROFESSION);
            s.state.setLastAssignmentEpochDay(KOMESerfKnightService.calendarDayNow());
            List<KOMEVisualMarker> markers=KOMEVisualLocationService.markersFor(s.p,s.f.data,150D);
            assertEquals(KOMEVisualMarker.Role.SERFDOM_MASTER,markers.get(0).role);assertFalse(markers.get(0).actionable);
            assertTrue(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,s.master));
            assertTrue(s.f.player.messages.toString().contains("Return tomorrow"));assertFalse(s.state.hasActiveAssignment());
            KOMEPlayerProgression reload=new KOMEPlayerProgression();reload.readFromNBT(s.p.writeToNBT());assertTrue(reload.getSerfKnightProgression().hasMasterRelationship());
            for(KOMESerfKnightDutyType t:KOMESerfKnightDutyType.values()){s.state.assignDuty(t,null);s.state.completeDuty(t);}
            s.state.setLiege(ref("Liege","rohan"));
            assertFalse(s.state.hasMasterRelationship());assertEquals(KOMEVisualMarker.Role.KNIGHT_LIEGE,KOMEVisualLocationService.markersFor(s.p).get(0).role);
            reload.readFromNBT(s.p.writeToNBT());assertFalse(reload.getSerfKnightProgression().hasMasterRelationship());
        }
    }
    @Test public void exactAuthorityAndPlayerKingPrecedenceUseOnePolicy(){
        KOMEWorldData data=new KOMEWorldData("authority");
        assertTrue(KOMEProgressionLiegePolicy.accepts(data,KOMEProgressionRank.KNIGHT,KOMEProgressionNpcRank.LORD,"rohan","rohan"));
        for(KOMEProgressionNpcRank rank:new KOMEProgressionNpcRank[]{KOMEProgressionNpcRank.UNRANKED,KOMEProgressionNpcRank.PRINCE,KOMEProgressionNpcRank.KING})
            assertFalse(KOMEProgressionLiegePolicy.accepts(data,KOMEProgressionRank.KNIGHT,rank,"rohan","rohan"));
        assertFalse(KOMEProgressionLiegePolicy.accepts(data,KOMEProgressionRank.KNIGHT,KOMEProgressionNpcRank.LORD,"rohan","gondor"));
        assertTrue(KOMEProgressionLiegePolicy.accepts(data,KOMEProgressionRank.LORD,KOMEProgressionNpcRank.PRINCE,"rohan","rohan"));
        assertFalse(KOMEProgressionLiegePolicy.accepts(data,KOMEProgressionRank.LORD,KOMEProgressionNpcRank.LORD,"rohan","rohan"));
        assertTrue(KOMEProgressionLiegePolicy.accepts(data,KOMEProgressionRank.PRINCE,KOMEProgressionNpcRank.KING,"rohan","rohan"));
        KOMERulerService.assignRuler(data,"rohan",UUID.randomUUID(),"Player King");
        assertFalse(KOMEProgressionLiegePolicy.accepts(data,KOMEProgressionRank.PRINCE,KOMEProgressionNpcRank.KING,"rohan","rohan"));
    }
    @Test public void weightedPopulationHasLargeStableThreeToOneSampleAndPersistentRecords(){
        Random random=new Random(937041);int lord=0,prince=0;
        for(int i=0;i<100_000;i++){KOMEProgressionNpcRank rank=KOMEProgressionNpcRankService.naturalRank(random);if(rank==KOMEProgressionNpcRank.LORD)lord++;else{assertEquals(KOMEProgressionNpcRank.PRINCE,rank);prince++;}}
        assertTrue(lord/(double)prince>2.9&&lord/(double)prince<3.1);
        for(KOMEProgressionNpcRank rank:new KOMEProgressionNpcRank[]{KOMEProgressionNpcRank.LORD,KOMEProgressionNpcRank.PRINCE}){
            KOMEProgressionNpcRankRecord record=new KOMEProgressionNpcRankRecord(UUID.randomUUID(),"rohan",rank,"Aldor");
            assertEquals(rank,KOMEProgressionNpcRankRecord.readFromNBT(record.writeToNBT()).rank);
        }
    }
    @Test public void nativeAuthorityDefinitionsCoverTwentyThreeFactionsAndTheirActualStructures()throws Exception{
        assertEquals(23,KOMEProgressionNativeAuthority.definitions().size());assertNull(KOMEProgressionNativeAuthority.definition("fangorn"));
        for(KOMEProgressionNativeAuthority.Definition d:KOMEProgressionNativeAuthority.definitions().values()){
            assertTrue(lotr.common.entity.npc.LOTRUnitTradeable.class.isAssignableFrom(Class.forName(d.npcClass)));
            assertTrue(KOMEProgressionNativeAuthority.guidance(d.faction).contains(d.places));
            for(String structure:d.structure.split(",")){
                boolean found=false;for(String prefix:new String[]{"structure/","structure2/"})
                    found|=getClass().getResource("/lotr/common/world/"+prefix+"LOTRWorldGen"+structure+".class")!=null;
                assertTrue(d.faction+" "+structure,found);
            }
            String name=KOMEProgressionNativeAuthority.name(d.faction,KOMEProgressionNpcRank.PRINCE,"Aldor");
            assertTrue(name.startsWith("Aldor, "+d.princeTitle+" of "));assertFalse(name.contains("Westfold"));
        }
    }
    @Test public void runtimeGoodsCategoriesCannotOverlap()throws Exception{
        try(KOMEProgressionFollowupTest.NativeItems nativeItems=new KOMEProgressionFollowupTest.NativeItems()){
            for(String faction:KOMEAlliance.allFactionKeys()){
                for(KOMESerfProvisioningAssignment.Candidate c:KOMESerfProvisioningCatalog.foodsForFaction(faction)){
                    Item item=(Item)Item.itemRegistry.getObject(c.key);assertTrue(faction+" "+c.key,KOMEProgressionGoodsRules.consumable(item));
                }
                for(KOMESerfMaterialProfile profile:KOMESerfMaterialProfile.values())
                    for(KOMESerfProfessionAssignment.Candidate c:KOMESerfProfessionCatalog.candidatesFor(profile,faction))
                        assertFalse(profile+" "+c.key,KOMEProgressionGoodsRules.consumable((Item)Item.itemRegistry.getObject(c.key)));
            }
        }
    }
    @Test public void professionBookCountsQualifyingInventoryAndDepositsWithoutMutation()throws Exception{
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            String key=String.valueOf(Item.itemRegistry.getNameForObject(Items.iron_ingot));
            KOMESerfProfessionAssignment.Requirement r=new KOMESerfProfessionAssignment.Requirement(key,Item.getIdFromItem(Items.iron_ingot),0,"exact",48,8,"Iron Ingots");
            KOMESerfProfessionAssignment a=new KOMESerfProfessionAssignment("smith","Blacksmith","smith",Arrays.asList(r));
            s.state.assignDuty(KOMESerfKnightDutyType.PROFESSION,a.writeToNBT());s.f.player.inventory.mainInventory[0]=new ItemStack(Items.iron_ingot,10);
            s.f.player.inventory.mainInventory[1]=new ItemStack(Items.iron_ingot,20,1); // wrong metadata
            String objective=KOMEProgressionGoodsPresentation.objective(s.p,s.f.player.inventory.mainInventory);
            assertTrue(objective,objective.contains("18 / 48"));assertEquals(8,r.delivered);assertEquals(10,s.f.player.inventory.mainInventory[0].stackSize);
            s.f.player.inventory.mainInventory[0]=null;assertTrue(KOMEProgressionGoodsPresentation.objective(s.p,s.f.player.inventory.mainInventory).contains("8 / 48"));
            KOMEPlayerProgression reload=new KOMEPlayerProgression();reload.readFromNBT(s.p.writeToNBT());assertTrue(KOMEProgressionGoodsPresentation.objective(reload,s.f.player.inventory.mainInventory).contains("8 / 48"));
        }
    }
    private static KOMEProgressionNpcRef ref(String name,String faction){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,faction,0,0,65,0);}
    @Test public void regionWithoutNativeAttackersDoesNotIssueAnImpossibleDefenseTrial()throws Exception{
        try(KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture()){
            KOMEAccessFixture f=new KOMEAccessFixture();
            KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);biome.npcSpawnList=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestSpawnList.class);
            KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);manager.biome=biome;f.world.provider.worldChunkMgr=manager;
            List<KOMESerfKnightTrial> trials=KOMESerfKnightService.trialsFor(f.world,LOTRFaction.ROHAN,0,0);
            assertFalse(trials.stream().anyMatch(t->"defense".equals(t.id)));assertTrue(trials.stream().anyMatch(t->"escort".equals(t.id)));assertTrue(trials.stream().anyMatch(t->"recovery".equals(t.id)));
            KOMEProgressionRegionalFixture.threats(biome,LOTRFaction.ROHAN);
            assertTrue(KOMESerfKnightService.trialsFor(f.world,LOTRFaction.ROHAN,0,0).stream().anyMatch(t->"defense".equals(t.id)));
        }
    }
    @Test public void failedFealtyAssignmentDoesNotReleaseMasterOrHideTheirMedallion(){
        KOMEPlayerProgression p=new KOMEPlayerProgression();p.setCanonicalRank(KOMEProgressionRank.SERF);KOMESerfKnightProgression state=p.getSerfKnightProgression();
        state.setSerfdomMaster(ref("Master","rohan"));for(KOMESerfKnightDutyType t:KOMESerfKnightDutyType.values()){state.assignDuty(t,null);state.completeDuty(t);}
        state.setLastTrialAssignmentEpochDay(10);
        assertFalse(KOMESerfKnightService.acceptStandingTrial(state,ref("Liege","rohan"),KOMEProgressionNpcRank.LORD,true,new Random(1),10,UUID.randomUUID()).success);
        assertFalse(state.hasLiege());assertTrue(state.hasMasterRelationship());
        assertEquals(KOMEVisualMarker.Role.SERFDOM_MASTER,KOMEVisualLocationService.markersFor(p).get(0).role);
    }
    @Test public void nativeLanguageFallbackUsesActualItemNamesInsteadOfImplementationKeys(){
        String mutton=KOMEProgressionGoodsRules.nativeName("lotr:item.lotr:muttonCooked","item.lotr.stackableFood");
        assertNotNull(mutton);assertFalse(mutton.contains(".stackableFood"));assertTrue(mutton.toLowerCase(java.util.Locale.ROOT).contains("mutton"));
    }
    @Test public void legacyLiegeRanksMigrateOnceAndHigherLegacyRelationshipsReleaseWithoutPromotion(){
        KOMEWorldData data=new KOMEWorldData("migration");
        KOMEPlayerProgression knight=data.getProgression(UUID.randomUUID());knight.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        KOMEProgressionNpcRef liege=ref("Aldor","rohan");knight.getSerfKnightProgression().setLiege(liege);
        assertTrue(KOMEProgressionNpcRankService.migrateRelationships(data));assertTrue(knight.getSerfKnightProgression().hasLiege());
        assertEquals(KOMEProgressionNpcRank.LORD,data.progressionNpcRanks.get(UUID.fromString(liege.entityUuid)).rank);
        assertFalse(KOMEProgressionNpcRankService.migrateRelationships(data));
        knight.setCanonicalRank(KOMEProgressionRank.LORD);assertTrue(KOMEProgressionNpcRankService.migrateRelationships(data));
        assertFalse(knight.getSerfKnightProgression().hasLiege());assertEquals(KOMEProgressionRank.LORD,knight.getCanonicalRank());
        assertEquals(KOMEProgressionNpcRank.LORD,data.progressionNpcRanks.get(UUID.fromString(liege.entityUuid)).rank);
    }
    @Test public void nonstackableOnlyProfessionAndMixedGroupsHaveSeparateTypeAndUnitConstraints(){
        List<KOMESerfProfessionAssignment.Candidate> pool=new ArrayList<KOMESerfProfessionAssignment.Candidate>();
        for(int i=0;i<4;i++)pool.add(new KOMESerfProfessionAssignment.Candidate("tool"+i,i,0,1,"Tool "+i,KOMESerfProfessionAssignment.QuantityTier.NONSTACKABLE));
        for(int seed=0;seed<500;seed++){
            KOMESerfProfessionAssignment a=KOMESerfProfessionAssignment.generate("smith","Blacksmith","smith",pool,new Random(seed));
            assertTrue(a.requirements.size()>=1&&a.requirements.size()<=2);
            Set<String> unique=new HashSet<String>();for(KOMESerfProfessionAssignment.Requirement r:a.requirements){assertTrue(unique.add(r.itemKey));assertTrue(r.required>=1&&r.required<=16);}
        }
        for(int i=0;i<4;i++)pool.add(new KOMESerfProfessionAssignment.Candidate("material"+i,10+i,0,16,"Material "+i,KOMESerfProfessionAssignment.QuantityTier.STANDARD));
        for(int seed=0;seed<500;seed++){
            KOMESerfProfessionAssignment a=KOMESerfProfessionAssignment.generate("smith","Blacksmith","smith",pool,new Random(seed));int tools=0,materials=0;
            for(KOMESerfProfessionAssignment.Requirement r:a.requirements)if(r.itemKey.startsWith("tool")){tools++;assertTrue(r.required>=1&&r.required<=16);}else{materials++;assertTrue(r.required>=32&&r.required<=64);}
            assertTrue(tools>=1&&tools<=2);assertTrue(materials>=2&&materials<=3);
        }
    }
}
