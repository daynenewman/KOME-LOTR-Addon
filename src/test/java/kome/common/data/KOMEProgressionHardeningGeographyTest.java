package kome.common.data;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEProgressionHardeningGeographyTest {
    static void territory(KOMELordshipTrialFixture f)throws Exception {
        f.s.f.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;f.s.f.player.dimension=LOTRDimension.MIDDLE_EARTH.dimensionID;
        KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);biome.heightBaseParameter=.2F;biome.npcSpawnList=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestSpawnList.class);
        KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);manager.biome=biome;f.s.f.world.provider.worldChunkMgr=manager;
    }
    static KOMEProgressionNpcRef origin(KOMELordshipTrialFixture f){return new KOMEProgressionNpcRef(f.liege.getUniqueID().toString(),"Captain","rohan",LOTRDimension.MIDDLE_EARTH.dimensionID,1000,65,1000);}
    @Test public void allProfilesRetainTheirRangeAndDestinationWithoutReroll()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        territory(f);f.s.chunks=false;
        KOMEProgressionGameplayFixture.record(f.s.f.world,2200,1000,"rohan");
        ((KOMECourierGeographyTest.TestManager)f.s.f.world.getWorldChunkManager()).borderZ=4000;
        for(Type type:Type.values()){KOMEKnightCommissionLocations.Destination d=KOMEKnightCommissionLocations.choose(origin(f),f.s.f.world,type,"fixed-seed"),again=KOMEKnightCommissionLocations.choose(origin(f),f.s.f.world,type,"fixed-seed");
            if(type==Type.BORDER_INCURSION){assertNull("No hostile neighboring territory exists in this fixture",d);assertNull(again);continue;}
            assertNotNull(type.name(),d);assertEquals(d.x,again.x,0);assertEquals(d.z,again.z,0);double distance=Math.hypot(d.x-1000,d.z-1000);assertTrue(distance>=1000&&distance<=3000);
            KOMEKnightCommission a=new KOMEKnightCommission(type,origin(f));a.x=a.destinationX=d.x;a.z=a.destinationZ=d.z;assertTrue(f.s.p.getKnightService().offer(a));
            f.s.reload();assertEquals(d.x,f.s.p.getKnightService().assignment().destinationX,0);assertEquals(a.token,f.s.p.getKnightService().assignment().token);f.s.p.getKnightService().discardOffer();
        }assertEquals(0,f.s.f.world.terrainProbes);
    }}
    @Test public void wrongDimensionAndInvalidOriginFailCleanly()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        territory(f);f.s.f.world.provider.dimensionId=12345;assertNull(KOMEKnightCommissionLocations.choose(origin(f),f.s.f.world,Type.RELIEF,"seed"));
        territory(f);KOMEProgressionNpcRef bad=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Captain","rohan",LOTRDimension.MIDDLE_EARTH.dimensionID,Double.NaN,65,1000);assertNull(KOMEKnightCommissionLocations.choose(bad,f.s.f.world,Type.RELIEF,"seed"));assertNull(f.s.p.getKnightService().assignment());
    }}
    @Test public void loadedInvalidSurfaceRejectsOfferRatherThanAcceptingBadCoordinates()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        territory(f);f.s.chunks=true;f.s.f.world.unsafeSurface=true;
        KOMEProgressionGameplayFixture.record(f.s.f.world,2200,1000,"rohan");
        net.minecraft.nbt.NBTTagCompound proof=f.s.f.data.progressionShelters.values().iterator().next();
        assertNull(KOMEKnightCommissionLocations.choose(origin(f),f.s.f.world,Type.RELIEF,"seed"));assertFalse(KOMEProgressionDestinations.usableArrival(f.s.f.world,proof));assertNull(f.s.p.getKnightService().assignment());
    }}
    @Test public void steepTerrainAndWrongFactionAreRejected()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        territory(f);f.s.chunks=false;((KOMECourierGeographyTest.TestManager)f.s.f.world.getWorldChunkManager()).biome.heightBaseParameter=2;
        assertNull(KOMEKnightCommissionLocations.choose(origin(f),f.s.f.world,Type.BORDER_INCURSION,"seed"));
        assertFalse(KOMEKnightCommissionLocations.usable(f.s.f.world,LOTRFaction.GONDOR,1000,1000));
    }}
    @Test public void commissionAndTrialGeographyDoNotDependOnCourierAssignment()throws Exception {
        for(String name:new String[]{"KOMEKnightCommissionLocations","KOMEKnightCommissionService","KOMELordshipTrialService"}){
            String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/"+name+".java")),StandardCharsets.UTF_8);assertFalse(source.contains("KOMESerfCourierAssignment"));
            for(String forceLoad:new String[]{"getChunkFromChunkCoords","loadChunk(","provideChunk("})assertFalse(source.contains(forceLoad));
        }
    }
    @Test public void soleRealShortShelterIsKeptInsteadOfInventingFartherSettlement()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        territory(f);f.s.chunks=false;KOMEProgressionGameplayFixture.record(f.s.f.world,1200,1000,"rohan");
        KOMEKnightCommissionLocations.Destination d=KOMEKnightCommissionLocations.choose(origin(f),f.s.f.world,Type.SETTLEMENT_DEFENSE,"short-only");
        assertNotNull(d);assertEquals(1200,d.proof.getInteger("RoofX"));assertEquals(d.proof.getDouble("ArrivalX"),d.x,0);assertEquals("EXISTING_VERIFIED",d.proof.getString("Status"));assertTrue(Math.hypot(d.x-1000,d.z-1000)<500);
    }}
}
