package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class KOMECanonicalConquestLevelTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void bundledLevelsCoverEveryActiveTileAndFreshWorld() {
        Set<String> ids = KOMEConquestTileDefaults.getKnownTileIds();
        assertEquals(621, ids.size());
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        KOMEWorldData fresh = new KOMEWorldData("levels-fresh");
        fresh.initializeIntegratedWorld();
        assertEquals(ids.size(), fresh.conquestTiles.size());
        for (String id : ids) {
            KOMETileOwnershipDefaults.Entry bundled = KOMETileOwnershipDefaults.forTile(id);
            assertNotNull("Missing level metadata for " + id, bundled);
            assertTrue("Invalid bundled level for " + id,
                bundled.waypointLevel >= 1 && bundled.waypointLevel <= 3);
            KOMEConquestTile tile = fresh.getConquestTileIfPresent(id);
            assertNotNull("Missing fresh tile " + id, tile);
            assertEquals("Fresh level for " + id, bundled.waypointLevel, tile.waypointLevel);
            counts.put(tile.waypointLevel, counts.containsKey(tile.waypointLevel)
                ? counts.get(tile.waypointLevel) + 1 : 1);
        }
        assertEquals(495, counts.get(1).intValue());
        assertEquals(63, counts.get(2).intValue());
        assertEquals(63, counts.get(3).intValue());
        assertEquals(3, fresh.getConquestTileIfPresent("T326").waypointLevel);
        assertEquals(3, fresh.getConquestTileIfPresent("T470").waypointLevel);
        assertEquals(2, fresh.getConquestTileIfPresent("T225").waypointLevel);
        assertEquals(2, fresh.getConquestTileIfPresent("T417").waypointLevel);
        assertEquals(2, fresh.getConquestTileIfPresent("T352").waypointLevel);
        assertEquals(3, fresh.getConquestTileIfPresent("T381").waypointLevel);
    }

    @Test public void savedHigherLevelsSurviveRestartAndHigherBaselinePromotesWithoutOwnershipChange() {
        KOMEWorldData source = new KOMEWorldData("levels-source");
        source.initializeIntegratedWorld();
        source.getConquestTileIfPresent("T001").waypointLevel = 3;
        source.getConquestTileIfPresent("T001").claim("gondor", 120L);
        source.getConquestTileIfPresent("T225").waypointLevel = 3;
        source.getConquestTileIfPresent("T326").waypointLevel = 1;
        source.getConquestTileIfPresent("T470").waypointLevel = 0;

        KOMEWorldData loaded = roundTrip(source);
        assertEquals(3, loaded.getConquestTileIfPresent("T001").waypointLevel);
        assertEquals("gondor", loaded.getConquestTileIfPresent("T001").projectRulingFaction());
        assertEquals(120L, loaded.getConquestTileIfPresent("T001").claimedWorldTime);
        assertEquals(3, loaded.getConquestTileIfPresent("T225").waypointLevel);
        assertEquals(3, loaded.getConquestTileIfPresent("T326").waypointLevel);
        assertEquals(3, loaded.getConquestTileIfPresent("T470").waypointLevel);

        loaded.getConquestTileIfPresent("T001").claim("rohan", 240L);
        loaded.applyWaypointDefaults(false);
        assertEquals(3, loaded.getConquestTileIfPresent("T001").waypointLevel);
        assertEquals("rohan", loaded.getConquestTileIfPresent("T001").projectRulingFaction());
        KOMEWorldData restarted = roundTrip(loaded);
        assertEquals(3, restarted.getConquestTileIfPresent("T001").waypointLevel);
        assertEquals(3, restarted.getConquestTileIfPresent("T225").waypointLevel);
        assertEquals("rohan", restarted.getConquestTileIfPresent("T001").projectRulingFaction());
        assertEquals(240L, restarted.getConquestTileIfPresent("T001").claimedWorldTime);
    }

    @Test public void ordinaryResponseSelectionReadsBaselineLevels() {
        KOMEWorldData data = new KOMEWorldData("levels-battle");
        data.initializeIntegratedWorld();
        assertEquals(20L * 60_000L, KOMEOrdinaryBattlePlanner.responseDuration(
            data.getConquestTileIfPresent("T001").waypointLevel, 20, 35, 50).durationMillis);
        assertEquals(35L * 60_000L, KOMEOrdinaryBattlePlanner.responseDuration(
            data.getConquestTileIfPresent("T225").waypointLevel, 20, 35, 50).durationMillis);
        assertEquals(50L * 60_000L, KOMEOrdinaryBattlePlanner.responseDuration(
            data.getConquestTileIfPresent("T326").waypointLevel, 20, 35, 50).durationMillis);
    }

    private static KOMEWorldData roundTrip(KOMEWorldData source) {
        NBTTagCompound saved = new NBTTagCompound();
        source.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("levels-restored");
        restored.readFromNBT(saved);
        return restored;
    }
}
