package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.spawning.*;
import net.minecraft.world.World;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

/** Every native playable faction, real NPC constructors/catalogs/relations, representative valid territory. */
@RunWith(Parameterized.class)
public class KOMEProgressionHardeningFactionTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> factions(){List<Object[]> all=new ArrayList<>();for(LOTRFaction f:LOTRFaction.values())if(f.isPlayableAlignmentFaction())all.add(new Object[]{f});return all;}
    @Parameterized.Parameter public LOTRFaction faction;
    @Test public void playableFactionCoverage()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMELordshipTrialFixture f=new KOMELordshipTrialFixture();
                KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture()){
            f.s.chunks=false;f.s.f.world.isRemote=true;f.s.f.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;f.s.f.player.dimension=LOTRDimension.MIDDLE_EARTH.dimensionID;
            KOMEKnightCommissionService.npcFactory=f.s.oldFactory;
            LOTREntityNPC liege=null;
            for(Class<?> c:nativeData.captains){LOTREntityNPC npc=KOMEKnightCommissionService.construct(f.s.f.world,c.getName());
                if(npc!=null&&npc.getFaction()==faction&&KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)){liege=npc;break;}}
            boolean hasLiege=liege!=null;
            // Native Fangorn has no unit-trader NPC. Do not invent one or disguise this gap as a pass.
            if(faction==LOTRFaction.FANGORN){assertNull(liege);liege=KOMEKnightCommissionService.construct(f.s.f.world,LOTREntityEnt.class.getName());}
            else assertNotNull("Native Liege for "+faction,liege);
            assertNotNull(liege);assertEquals(faction,liege.getFaction());
            if(hasLiege)assertEquals(KOMEProgressionNpcRank.LORD,KOMEProgressionNpcRankService.effectiveRank(f.s.f.data,liege));
            f.s.f.pledge(faction);
            java.lang.reflect.Field alignments=lotr.common.LOTRPlayerData.class.getDeclaredField("alignments");alignments.setAccessible(true);
            ((Map)alignments.get(lotr.common.LOTRLevelData.getData(f.s.f.player))).put(faction,2000F);
            f.s.p.getSerfKnightProgression().releaseLiegeAfterPromotion();
            liege.posX=f.s.f.player.posX;liege.posY=65;liege.posZ=f.s.f.player.posZ;
            f.s.f.world.isRemote=false;
            assertEquals(hasLiege,KOMEProgressionOfferBridge.canReplaceLiegeFrom(f.s.f.player,liege));
            if(hasLiege){assertTrue(KOMESerfKnightRelationshipService.establishLiege(f.s.f.player,f.s.f.data,liege).success);assertTrue(KOMEKnightCommissionService.eligible(f.s.f.player,liege));}
            f.s.f.world.isRemote=true;
            CoverageSpawns spawns=KOMEAccessFixture.allocate(CoverageSpawns.class);spawns.faction=faction;
            KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);biome.heightBaseParameter=.2F;biome.npcSpawnList=spawns;
            KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);KOMEProgressionRegionalFixture.threats(biome,faction);manager.biome=biome;f.s.f.world.provider.worldChunkMgr=manager;
            EnumSet<Type> types=EnumSet.noneOf(Type.class);
            KOMEProgressionGameplayFixture.record(f.s.f.world,(int)liege.posX+800,(int)liege.posZ,faction.codeName());
            List<LOTRInvasions> regional=KOMEProgressionRegionalEnemies.choices(f.s.f.world,faction,0,0);
            if(!regional.isEmpty())KOMEProgressionGameplayFixture.border(f.s.f.world,regional.get(0).invasionFaction);
            for(Type type:Type.values()){
                KOMEKnightCommission a=KOMEKnightCommissionService.generate(f.s.f.player,liege,type);if(a==null)continue;types.add(type);
                assertEquals(faction,KOMEProgressionFactionResolver.resolve(a.faction));assertTrue(KOMEKnightCommissionLocations.usable(f.s.f.world,faction,a.destinationX,a.destinationZ));
                assertEquals(a.writeToNBT(),KOMEKnightCommission.readFromNBT(a.writeToNBT()).writeToNBT());
                if(type!=Type.RELIEF){LOTRFaction enemy=KOMEProgressionFactionResolver.resolve(a.enemyFaction);assertNotEquals(faction,enemy);assertTrue(KOMESerfKnightDefenseService.hostile(faction,enemy));
                    for(String name:a.enemyClasses)assertEquals(enemy,KOMEKnightCommissionService.construct(f.s.f.world,name).getFaction());}
            }
            assertTrue("Three usable templates for "+faction+": "+types,types.size()>=3);
            String soldier=KOMEProgressionTrialSoldiers.resolve(f.s.f.world,faction);assertNotNull("Soldier for "+faction,soldier);
            assertTrue(KOMELordshipTrialService.suitableGuard(KOMEKnightCommissionService.construct(f.s.f.world,soldier),faction));
            int trials=0;for(KOMELordshipTrial.Scenario scenario:KOMELordshipTrial.Scenario.values())if(KOMELordshipTrialService.generate(f.s.f.player,liege,scenario)!=null)trials++;
            assertTrue("At least one native-data trial template",trials>=1);
            List<Goods> provisions=KOMEKnightCommissionService.reliefGoods(faction.codeName(),new Random(4));assertFalse(provisions.isEmpty());
            for(Goods g:provisions){assertTrue(g.required<=32);assertNotNull(net.minecraft.item.Item.itemRegistry.getObject(g.itemKey));}
            for(String alias:faction.listAliases())assertSame(faction,KOMEProgressionFactionResolver.resolve(alias));
            System.out.println("COVERAGE|"+faction.codeName()+"|"+hasLiege+"|"+types.size()+"|"+(hasLiege&&trials>0)+"|"+soldier+"|true|true|"+types);
            assertEquals("Unloaded territory never forces terrain access",0,f.s.f.world.terrainProbes);
        }
    }
    static final class CoverageSpawns extends LOTRBiomeSpawnList {
        LOTRFaction faction;private CoverageSpawns(){super("coverage");}
        @Override public boolean isFactionPresent(World world,LOTRFaction f){return f==faction;}
        @Override public List<LOTRSpawnEntry> getAllSpawnEntries(World world){return Collections.emptyList();}
    }
}
