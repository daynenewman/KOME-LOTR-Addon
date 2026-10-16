package kome.common.data;

import java.util.*;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEKnightFieldOperationsTest {
    @Test public void operatorCanRepeatExercisesAtOneLiegeAfterThreeEarnedReports()throws Exception{
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture();KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture()){
            KOMEProgressionHardeningGeographyTest.territory(f);f.s.p.getSerfKnightProgression().setLiege(KOMEProgressionNpcRankService.referenceOf(f.lord));f.credits(3);f.s.f.player.operator=true;
            KOMEProgressionRegionalFixture.threats((lotr.common.world.biome.LOTRBiome)f.s.f.world.getWorldChunkManager().getBiomeGenAt(0,0),LOTRFaction.ROHAN);
            KOMEKnightCommissionService.npcFactory=(world,type)->{try{return f.s.npc(type.contains("Dunlend")?LOTRFaction.DUNLAND:type.contains("Mordor")?LOTRFaction.MORDOR:LOTRFaction.ROHAN);}catch(Exception e){throw new RuntimeException(e);}};
            for(int i=0;i<4;i++){
                java.util.List<String> response=KOMEProgressionDevelopment.commission(f.s.f.player,new String[]{"start","PROTECT_LIEGE","2"});
                KOMEKnightCommission a=f.s.p.getKnightService().assignment();assertNotNull(response.toString(),a);assertEquals(f.lord.getUniqueID().toString(),a.liege.entityUuid);assertTrue(a.adminForced);assertTrue(f.s.p.getKnightService().testing());
                KOMEProgressionDevelopment.commission(f.s.f.player,new String[]{"complication",i%2==0?"counterattack":"none"});assertEquals(i%2==0?Complication.COUNTERATTACK:Complication.NONE,a.complication);
                assertTrue(KOMEKnightCommissionService.acceptOrReport(f.s.f.player,f.lord));KOMEProgressionDevelopment.finish(f.s.f.player);assertTrue(KOMEKnightCommissionService.acceptOrReport(f.s.f.player,f.lord));assertNull(f.s.p.getKnightService().assignment());assertEquals(3,f.s.p.getKnightService().history().size());assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
            }
            assertFalse(KOMEServicePromotionService.reason(f.s.p,f.s.p.getSerfKnightProgression().getLiege(),"rohan",2000).isEmpty());KOMEProgressionDevelopment.commission(f.s.f.player,new String[]{"end"});assertFalse(f.s.p.getKnightService().testing());assertEquals(3,f.s.p.getKnightService().qualifyingTypes("rohan").size());
        }
    }
    @Test public void actualLiegeAttackCompletesAtThatLiegeWithoutOwningOrDeletingHim()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            LOTREntityNPC liege=KOMEKnightCommissionInteractionTest.captain(s);KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(s,liege,Type.PROTECT_LIEGE);KOMEKnightFieldService.profile(a,2);a.complication=Complication.NONE;a.stage=Stage.ACTIVE;a.x=liege.posX;a.z=liege.posZ;a.enemyFaction="mordor";for(int i=0;i<3;i++)a.enemyClasses.add("Enemy");
            assertTrue(KOMEKnightCommissionService.activate(s.f.player,a));assertEquals(3,a.actors.size());assertFalse(liege.isEntityInvulnerable());assertNull(KOMEProgressionEncounterMarker.read(liege));
            for(Actor actor:new ArrayList<Actor>(a.actors))KOMEKnightFieldServiceTest.kill(s,a,(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(s.f.world,actor.id));
            assertEquals(Stage.READY_TO_REPORT,a.stage);assertTrue(KOMEKnightCommissionService.acceptOrReport(s.f.player,liege));assertTrue(liege.isEntityAlive());assertEquals(Collections.singleton(Type.PROTECT_LIEGE),s.p.getKnightService().qualifyingTypes("rohan"));
        }
    }
    @Test public void raidMustRecoverOfficersBoundPropertyAndEscapeBeforePersonalReport()throws Exception{
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture();KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture()){
            KOMEProgressionHardeningGeographyTest.territory(f);f.s.f.player.posX=0;f.s.f.player.posZ=0;KOMEProgressionGameplayFixture.border(f.s.f.world,LOTRFaction.DUNLAND);
            LOTREntityNPC liege=KOMEKnightCommissionInteractionTest.captain(f.s);
            KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(f.s,liege,Type.RAID_CAMP);KOMEKnightFieldService.profile(a,3);a.complication=Complication.NONE;a.stage=Stage.ACTIVE;a.x=a.destinationX=1000;a.z=a.destinationZ=1000;a.enemyFaction="dunland";a.civilianClass="EnemyOfficer";a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(Items.iron_ingot)),0,1));for(int i=0;i<3;i++)a.enemyClasses.add("Enemy");
            KOMEKnightCommissionService.npcFactory=(world,type)->{try{return f.s.npc(type.contains("Enemy")?LOTRFaction.DUNLAND:LOTRFaction.ROHAN);}catch(Exception e){throw new RuntimeException(e);}};
            f.s.f.player.posX=a.x;f.s.f.player.posZ=a.z;assertTrue(KOMEKnightCommissionService.validateDestination(f.s.f.world,a));assertTrue(KOMEKnightCommissionService.activate(f.s.f.player,a));assertEquals(a.targetId,a.carrierId);
            for(Actor actor:new ArrayList<Actor>(a.actors))KOMEKnightFieldServiceTest.kill(f.s,a,(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,actor.id));assertEquals(Stage.ACTIVE,a.stage);assertTrue(a.propertyDropped);
            EntityItem property=(EntityItem)f.s.f.world.loadedEntityList.stream().filter(e->e instanceof EntityItem&&KOMEKnightCommissionService.matchesProperty(((EntityItem)e).getEntityItem(),a,f.s.f.player.id)).findFirst().get();f.s.f.player.inventory.mainInventory[0]=property.getEntityItem().copy();property.setDead();KOMEKnightCommissionService.tickPlayer(f.s.f.player);assertEquals(Stage.ACTIVE,a.stage);assertEquals(Phase.ESCAPE,a.phase);
            f.s.f.player.posX+=160;KOMEKnightCommissionService.tickPlayer(f.s.f.player);assertEquals(Stage.READY_TO_REPORT,a.stage);f.s.f.player.posX=liege.posX;f.s.f.player.posZ=liege.posZ;assertTrue(KOMEKnightCommissionService.acceptOrReport(f.s.f.player,liege));assertNull(f.s.f.player.inventory.mainInventory[0]);assertTrue(f.s.p.getKnightService().qualifyingTypes("rohan").contains(Type.RAID_CAMP));assertTrue(f.s.f.data.wars.isEmpty());
        }
    }
    @Test public void fieldCommandEntrustsFourNativeFollowersAndLeavesNoStrategicCompany()throws Exception{
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture();KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture()){
            KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(f.s,f.lord,Type.FIELD_COMMAND);KOMEKnightFieldService.profile(a,2);a.complication=Complication.NONE;a.civilianClass="Guard";a.enemyFaction="mordor";for(int i=0;i<3;i++)a.enemyClasses.add("Enemy");
            assertTrue(KOMEKnightCommissionService.acceptOrReport(f.s.f.player,f.lord));assertEquals(4,a.actors.size());for(Actor actor:a.actors){LOTREntityNPC npc=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,actor.id);assertTrue(KOMEKnightCommissionService.activeEscort(f.s.f.data,npc));assertFalse(npc.hiredNPCInfo.teleportAutomatically);}
            f.s.f.player.posX=a.x;f.s.f.player.posZ=a.z;assertTrue(KOMEKnightCommissionService.activate(f.s.f.player,a));for(Actor actor:new ArrayList<Actor>(a.actors))if(actor.role==Role.ENEMY)KOMEKnightFieldServiceTest.kill(f.s,a,(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,actor.id));assertEquals(Stage.READY_TO_REPORT,a.stage);
            f.s.f.player.posX=f.lord.posX;f.s.f.player.posZ=f.lord.posZ;assertTrue(KOMEKnightCommissionService.acceptOrReport(f.s.f.player,f.lord));assertTrue(f.s.f.data.hiredUnits.isEmpty());assertTrue(f.s.f.data.armyCompanies.isEmpty());assertTrue(f.s.p.getKnightService().qualifyingTypes("rohan").contains(Type.FIELD_COMMAND));
        }
    }
}
