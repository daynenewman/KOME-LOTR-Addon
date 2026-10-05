package kome.common.data;

import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.*;
import lotr.common.fac.*;
import lotr.common.world.spawning.LOTRInvasions;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native faction prototypes and actual biome/waypoint generation, with boot data absent in plain JUnit supplied explicitly. */
public class KOMELordshipTrialGenerationTest {
    @Test public void allTemplatesGeneratePersistedFactionAppropriateForcesAndThreats()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            f.s.f.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;f.s.f.player.dimension=LOTRDimension.MIDDLE_EARTH.dimensionID;
            f.s.f.world.isRemote=true; // Native prototypes need no real server NPC watcher transport in plain JUnit.
            KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);biome.heightBaseParameter=.2F;biome.npcSpawnList=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestSpawnList.class);
            KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);manager.biome=biome;f.s.f.world.provider.worldChunkMgr=manager;
            KOMEKnightCommissionService.npcFactory=f.s.oldFactory;
            Field rel=LOTRFactionRelations.class.getDeclaredField("defaultMap");rel.setAccessible(true);Map relations=(Map)rel.get(null);Map old=new HashMap(relations);
            LOTRInvasions own=null;for(LOTRInvasions invasion:LOTRInvasions.values())if(invasion.invasionFaction==LOTRFaction.ROHAN){own=invasion;break;}assertNotNull(own);
            List oldOwn=own.invasionMobs,oldEnemy=LOTRInvasions.DUNLAND.invasionMobs;
            try{
                LOTRFactionRelations.setDefaultRelations(LOTRFaction.ROHAN,LOTRFaction.DUNLAND,LOTRFactionRelations.Relation.ENEMY);
                own.invasionMobs=Arrays.asList(new LOTRInvasions.InvasionSpawnEntry(LOTREntityRohirrimWarrior.class,10));
                LOTRInvasions.DUNLAND.invasionMobs=Arrays.asList(new LOTRInvasions.InvasionSpawnEntry(LOTREntityDunlendingWarrior.class,10));
                for(KOMELordshipTrial.Scenario scenario:KOMELordshipTrial.Scenario.values()){
                    KOMELordshipTrial t=KOMELordshipTrialService.generate(f.s.f.player,f.liege,scenario);assertNotNull(scenario.name(),t);
                    assertEquals(4,t.guardClasses.size());assertEquals(2,t.requiredSurvivors);assertEquals(5,t.objective.enemyClasses.size());
                    for(String type:t.guardClasses)assertTrue(KOMELordshipTrialService.suitableGuard(KOMEKnightCommissionService.construct(f.s.f.world,type),LOTRFaction.ROHAN));
                    assertTrue(KOMESerfKnightDefenseService.hostile(LOTRFaction.ROHAN,KOMEProgressionFactionResolver.resolve(t.objective.enemyFaction)));
                    assertEquals(t.writeToNBT(),KOMELordshipTrial.readFromNBT(t.writeToNBT()).writeToNBT());
                    assertTrue(t.objective.destinationX!=f.liege.posX||t.objective.destinationZ!=f.liege.posZ);
                }
                assertFalse(KOMELordshipTrialService.suitableGuard(KOMEKnightCommissionService.construct(f.s.f.world,LOTREntityRohanMan.class.getName()),LOTRFaction.ROHAN));
                assertFalse(KOMELordshipTrialService.suitableGuard(KOMEKnightCommissionService.construct(f.s.f.world,LOTREntityRohirrimWarrior.class.getName()),LOTRFaction.GONDOR));
                own.invasionMobs=Collections.emptyList();assertNotNull(KOMELordshipTrialService.generate(f.s.f.player,f.liege,KOMELordshipTrial.Scenario.BORDER_PATROL));
                assertTrue("Loaded destinations are surface validated",f.s.f.world.terrainProbes>0);
            }finally{relations.clear();relations.putAll(old);own.invasionMobs=oldOwn;LOTRInvasions.DUNLAND.invasionMobs=oldEnemy;}
        }
    }
}
