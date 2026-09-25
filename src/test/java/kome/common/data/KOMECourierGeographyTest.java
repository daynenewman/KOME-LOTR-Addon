package kome.common.data;

import kome.common.KOMEAccessFixture;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.biome.LOTRMusicRegion;
import lotr.common.world.spawning.LOTRBiomeSpawnList;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class KOMECourierGeographyTest {
    @Test public void deterministicUnloadedTerritoryDestinationIsInTravelRangeAndPersists() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();fixture.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;
        TestBiome biome=KOMEAccessFixture.allocate(TestBiome.class);biome.heightBaseParameter=0.2F;biome.npcSpawnList=KOMEAccessFixture.allocate(TestSpawnList.class);
        TestManager manager=KOMEAccessFixture.allocate(TestManager.class);manager.biome=biome;fixture.world.provider.worldChunkMgr=manager;
        KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(java.util.UUID.randomUUID().toString(),"Aldor","rohan",LOTRDimension.MIDDLE_EARTH.dimensionID,1000,64,1000);
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.create(master,fixture.world);
        assertNotNull(assignment);double distance=KOMESerfCourierAssignment.distanceFromOrigin(assignment);
        assertTrue(distance>=KOMESerfCourierAssignment.MIN_COURIER_DISTANCE);assertTrue(distance<=KOMESerfCourierAssignment.MAX_COURIER_DISTANCE);
        assertFalse(assignment.recipient.isSet());assertTrue(assignment.destinationKey.startsWith("territory:"));
        KOMESerfCourierAssignment loaded=KOMESerfCourierAssignment.readFromNBT(assignment.writeToNBT());
        assertEquals(assignment.destinationKey,loaded.destinationKey);assertEquals(assignment.destinationX,loaded.destinationX,0D);
        assertEquals(assignment.token,loaded.token);assertEquals(assignment.storyVariant,loaded.storyVariant);
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfCourierAssignment.java")),StandardCharsets.UTF_8);
        assertTrue(source.contains("inDefinedControlZone"));assertTrue(source.contains("npcSpawnList.isFactionPresent"));
        for(String forbidden:new String[]{"getChunkFromChunkCoords","loadChunk","provideChunk"})assertFalse(forbidden,source.contains(forbidden));
    }
    public static final class TestManager extends WorldChunkManager {TestBiome biome;private TestManager(){super();}@Override public BiomeGenBase getBiomeGenAt(int x,int z){return biome;}}
    public static final class TestSpawnList extends LOTRBiomeSpawnList {private TestSpawnList(){super("test");}@Override public boolean isFactionPresent(net.minecraft.world.World world,LOTRFaction faction){return faction==LOTRFaction.ROHAN;}}
    public static final class TestBiome extends LOTRBiome {private TestBiome(){super(250,false);}@Override public boolean isWateryBiome(){return false;}@Override public LOTRMusicRegion.Sub getBiomeMusic(){return null;}}
}
