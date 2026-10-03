package kome.common.data;

import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.biome.WorldChunkManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

/** Executes the real native offer response adapter and server-side presentation paths. */
public class KOMEKnightCommissionInteractionTest {
    static KOMESerfKnightStabilizationTest.TrialCaptain captain(KOMEKnightCommissionGameplayTest.Session s)throws Exception {
        KOMESerfKnightStabilizationTest.TrialCaptain npc=KOMEAccessFixture.allocate(KOMESerfKnightStabilizationTest.TrialCaptain.class);
        npc.worldObj=s.f.world;npc.setUniqueID(UUID.randomUUID());npc.setEntityId(12345);npc.posX=s.f.player.posX;npc.posY=65;npc.posZ=s.f.player.posZ;npc.accessible=true;
        Field watcher=Entity.class.getDeclaredField("dataWatcher");watcher.setAccessible(true);net.minecraft.entity.DataWatcher dw=new net.minecraft.entity.DataWatcher(npc);dw.addObject(10,"");watcher.set(npc,dw);
        npc.hiredNPCInfo=new LOTRHiredNPCInfo(npc);npc.questInfo=new LOTREntityQuestInfo(npc){@Override public void sendData(net.minecraft.entity.player.EntityPlayerMP p){}};
        s.f.world.loadedEntityList.add(npc);s.p.getSerfKnightProgression().setLiege(KOMEProgressionNpcRankService.referenceOf(npc));return npc;
    }
    static KOMEKnightCommission offered(KOMEKnightCommissionGameplayTest.Session s,LOTREntityNPC liege,Type type){KOMEKnightCommission a=new KOMEKnightCommission(type,KOMEProgressionNpcRankService.referenceOf(liege));a.x=640;a.z=640;a.destinationX=1000;a.destinationZ=1000;a.place="Refuge";s.p.getKnightService().offer(a);return a;}
    @Test public void nativeQuestAcceptsOnlyAfterResponseAndReopeningNeverRerolls()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){LOTREntityNPC liege=captain(s);KOMEKnightCommission a=offered(s,liege,Type.BORDER_INCURSION);assertTrue(KOMEProgressionOfferBridge.prepareStandingTrialInteraction(s.f.player,liege));assertTrue(((KOMELiegeOfferQuest)liege.questInfo.getOfferFor(s.f.player)).isCommissionOffer());assertEquals(Stage.OFFERED,a.stage);assertTrue(KOMEProgressionOfferBridge.prepareStandingTrialInteraction(s.f.player,liege));assertSame(a,KOMEKnightCommissionService.prepare(s.f.player,liege));assertTrue(KOMEProgressionOfferBridge.handleResponse(liege.questInfo,s.f.player,false));assertEquals(Stage.OFFERED,a.stage);KOMEProgressionOfferBridge.prepareStandingTrialInteraction(s.f.player,liege);KOMEProgressionOfferBridge.handleResponse(liege.questInfo,s.f.player,true);assertEquals(Stage.ACTIVE,a.stage);s.reload();assertEquals(a.token,s.p.getKnightService().assignment().token);assertEquals(a.x,s.p.getKnightService().assignment().x,0);}}
    @Test public void nativeReportConsumesPropertyAndAwardsOneServiceWithoutPromotion()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){LOTREntityNPC liege=captain(s);KOMEKnightCommission a=offered(s,liege,Type.STOLEN_GOODS);KOMEKnightCommissionGameplayTest.goods(a);a.stage=Stage.READY_TO_REPORT;s.f.player.inventory.mainInventory[0]=KOMEKnightCommissionService.property(a,s.f.player.id);assertTrue(KOMEProgressionOfferBridge.prepareStandingTrialInteraction(s.f.player,liege));assertTrue(KOMEProgressionOfferBridge.handleResponse(liege.questInfo,s.f.player,true));assertNull(s.f.player.inventory.mainInventory[0]);assertTrue(s.p.getKnightService().completedTypes().contains(Type.STOLEN_GOODS));assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());}}
    @Test public void missingReportPropertySendsOwnerBackToRecoverableFieldDuty()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){LOTREntityNPC liege=captain(s);KOMEKnightCommission a=offered(s,liege,Type.STOLEN_GOODS);KOMEKnightCommissionGameplayTest.goods(a);a.stage=Stage.READY_TO_REPORT;a.threatResolved=true;a.encounterCreated=true;assertTrue(KOMEKnightCommissionService.acceptOrReport(s.f.player,liege));assertEquals(Stage.ACTIVE,a.stage);assertTrue(s.p.getKnightService().completedTypes().isEmpty());}}
    @Test public void serverRejectsWrongFactionWrongRankAndRemoteResponse()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){LOTREntityNPC liege=captain(s);offered(s,liege,Type.RELIEF);s.p.setCanonicalRank(KOMEProgressionRank.SERF);assertFalse(KOMEKnightCommissionService.eligible(s.f.player,liege));s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT);s.f.pledge(LOTRFaction.GONDOR);assertFalse(KOMEKnightCommissionService.eligible(s.f.player,liege));s.f.pledge(LOTRFaction.ROHAN);s.f.player.posX+=20;assertFalse(KOMEKnightCommissionService.acceptOrReport(s.f.player,liege));assertEquals(Stage.OFFERED,s.p.getKnightService().assignment().stage);}}
    @Test public void failedChargeReturnsThroughLiegeAndPreservesCredit()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){LOTREntityNPC liege=captain(s);KOMEKnightCommission a=offered(s,liege,Type.RELIEF);a.stage=Stage.FAILED;assertTrue(KOMEKnightCommissionService.acceptOrReport(s.f.player,liege));assertNull(s.p.getKnightService().assignment());assertEquals(Stage.FAILED,s.p.getKnightService().history().get(0).stage);}}
    @Test public void questShellRoundTripsCommissionIdentity()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){LOTREntityNPC liege=captain(s);KOMEKnightCommission a=offered(s,liege,Type.RELIEF);KOMELiegeOfferQuest shell=KOMELiegeOfferQuest.createCommission(LOTRLevelData.getData(s.f.player),liege,10,a);assertNotNull(shell);KOMEProgressionOfferBridge.registerQuestType();NBTTagCompound tag=new NBTTagCompound();shell.writeToNBT(tag);KOMELiegeOfferQuest loaded=new KOMELiegeOfferQuest(LOTRLevelData.getData(s.f.player));loaded.readFromNBT(tag);assertTrue(loaded.isCommissionOffer());assertEquals(a.token,loaded.commissionToken());assertEquals(0,loaded.getCoinBonus());assertEquals(0,loaded.getAlignmentBonus(),0);}}
    @Test public void ordinaryClickDoesNotAcceptPreviewAndKnightQuestPacketOpensOffer()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        KOMEProgressionOfferBridge.registerQuestType();
        LOTREntityNPC liege=captain(s);KOMEKnightCommission a=offered(s,liege,Type.RELIEF);
        assertFalse(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,liege));assertEquals(Stage.OFFERED,a.stage);
        // LOTR's actual presentation transport is replaced with an inert packet recorder in plain JUnit.
        KOMEProgressionOfferBridge.OfferSender old=KOMEProgressionOfferBridge.offerSender;final List<NBTTagCompound> sent=new ArrayList<NBTTagCompound>();
        try{KOMEProgressionOfferBridge.offerSender=(player,npc,tag)->{assertSame(liege,npc);sent.add(tag);};
            new kome.common.network.KOMEPacketRelationshipAction.Handler().onMessage(new kome.common.network.KOMEPacketRelationshipAction(liege.getEntityId(),kome.common.network.KOMEPacketRelationshipAction.LIEGE,kome.common.network.KOMEPacketRelationshipAction.SERVICE),s.f.context);
            assertEquals(1,sent.size());assertEquals(a.token,sent.get(0).getString("KOMECommission"));assertEquals(Stage.OFFERED,a.stage);
        }finally{KOMEProgressionOfferBridge.offerSender=old;}
    }}
    @Test public void summaryMarkersAndHudHideTokensAndTrackReportState()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){LOTREntityNPC liege=captain(s);KOMEKnightCommission a=offered(s,liege,Type.BORDER_INCURSION);a.stage=Stage.ACTIVE;String summary=KOMEProgressionSummary.text(s.p,"Rohan","rohan");assertFalse(summary.contains(a.token));assertFalse(summary.contains(a.liege.entityUuid));assertTrue(KOMEVisualLocationService.markersFor(s.p).stream().anyMatch(m->m.role==KOMEVisualMarker.Role.COMMISSION));assertTrue(KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).visible);a.stage=Stage.READY_TO_REPORT;assertEquals(1,KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).completion,0);assertFalse(KOMEVisualLocationService.markersFor(s.p).stream().anyMatch(m->m.role==KOMEVisualMarker.Role.COMMISSION));}}
    @Test public void generationUsesRealTerritoryAndExcludesBrokenCivilianTemplates()throws Exception {try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        LOTREntityNPC liege=captain(s);s.f.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;s.f.player.dimension=LOTRDimension.MIDDLE_EARTH.dimensionID;
        // Native prototype construction in plain JUnit has no server NPC watcher transport.
        s.f.world.isRemote=true;
        KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);biome.heightBaseParameter=0.2F;biome.npcSpawnList=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestSpawnList.class);
        KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);manager.biome=biome;s.f.world.provider.worldChunkMgr=manager;
        // The native catalog resolves faction classes; commission construction is an inert spawn seam.
        KOMEKnightCommissionService.npcFactory=(world,className)->{try{return s.npc(className.contains("Rohan")?LOTRFaction.ROHAN:LOTRFaction.MORDOR);}catch(Exception e){throw new RuntimeException(e);}};
        KOMEKnightCommission relief=KOMEKnightCommissionService.generate(s.f.player,liege,Type.RELIEF);assertNotNull(relief);assertFalse(relief.goods.isEmpty());assertFalse(relief.civilianClass.isEmpty());assertTrue("Loaded destinations are surface validated",s.f.world.terrainProbes>0);
        KOMEKnightCommissionService.npcFactory=s.oldFactory;
        assertFalse(KOMEKnightCommissionService.civilian(KOMEKnightCommissionService.construct(s.f.world,LOTREntityRohirrimWarrior.class.getName()),LOTRFaction.ROHAN));
        Field relations=lotr.common.fac.LOTRFactionRelations.class.getDeclaredField("defaultMap");relations.setAccessible(true);Map defaults=(Map)relations.get(null);Map oldRelations=new HashMap(defaults);
        List oldMobs=lotr.common.world.spawning.LOTRInvasions.DUNLAND.invasionMobs;
        try{
            // Plain JUnit does not run LOTR.initAllProperties/createMobLists; provide native boot inputs.
            lotr.common.fac.LOTRFactionRelations.setDefaultRelations(LOTRFaction.ROHAN,LOTRFaction.DUNLAND,lotr.common.fac.LOTRFactionRelations.Relation.ENEMY);
            lotr.common.world.spawning.LOTRInvasions.DUNLAND.invasionMobs=Arrays.asList(new lotr.common.world.spawning.LOTRInvasions.InvasionSpawnEntry(LOTREntityDunlendingWarrior.class,10));
            for(Type type:Type.values()){
                KOMEKnightCommission generated=KOMEKnightCommissionService.generate(s.f.player,liege,type);assertNotNull(type.name(),generated);
                if(type!=Type.RELIEF)assertTrue(KOMESerfKnightDefenseService.hostile(LOTRFaction.ROHAN,KOMEProgressionFactionResolver.resolve(generated.enemyFaction)));
                assertEquals(generated.writeToNBT(),KOMEKnightCommission.readFromNBT(generated.writeToNBT()).writeToNBT());
            }
        }finally{defaults.clear();defaults.putAll(oldRelations);lotr.common.world.spawning.LOTRInvasions.DUNLAND.invasionMobs=oldMobs;}
        KOMEKnightCommissionService.npcFactory=(world,className)->null;assertNull(KOMEKnightCommissionService.generate(s.f.player,liege,Type.RELIEF));
    }}
}
