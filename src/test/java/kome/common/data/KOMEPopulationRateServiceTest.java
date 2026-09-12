package kome.common.data;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMEPopulationRateServiceTest {
    @Test public void fixedRatesUseExactHalfHourSource() {
        assertEquals(1_000_000L, KOMEPopulationRateService.rate(20, 10).getFixedUnitsPerDay());
        assertEquals(500_000L, KOMEPopulationRateService.rate(10, 10).getFixedUnitsPerDay());
        assertEquals(50_000L, KOMEPopulationRateService.rate(1, 10).getFixedUnitsPerDay());
        assertEquals(500_000L, KOMEPopulationRateService.rate(20, 20).getFixedUnitsPerDay());
    }

    @Test public void nativeNormalBuildsAggregateAndCapturedAndDefensiveDoNotProduce() {
        KOMEWorldData data = data();
        KOMEPlayerBuild first = build(data, "B2", "gondor", KOMEBuildType.NORMAL, 10);
        KOMEPlayerBuild second = build(data, "B1", "gondor", KOMEBuildType.NORMAL, 20);
        KOMEPlayerBuild defensive = build(data, "B3", "gondor", KOMEBuildType.DEFENSIVE, 40);
        assertEquals(1_500_000L, KOMEPopulationService.getDailyPopulationRate(data, "gondor").getFixedUnitsPerDay());
        assertEquals("B1", KOMEPopulationService.getPopulationRateContributions(data).get(0).buildId);
        data.conquestTiles.get("T1").claim("rohan", 0L);
        assertEquals(0L, KOMEPopulationService.getDailyPopulationRate(data, "gondor").getFixedUnitsPerDay());
        assertEquals("CAPTURED_DEFERRED_KOM9", KOMEPopulationService.getPopulationRateContributions(data).get(0).status);
        assertFalse(KOMEPopulationService.getPopulationRateContributions(data).toString().contains(defensive.id));
    }

    private static KOMEWorldData data() {
        KOMEWorldData data = new KOMEWorldData("rate");
        KOMEConquestTile tile = new KOMEConquestTile("T1"); tile.claim("gondor", 0L); data.conquestTiles.put("T1", tile);
        return data;
    }
    private static KOMEPlayerBuild build(KOMEWorldData data, String id, String faction, KOMEBuildType type, int hours) {
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = id; build.tileId = "T1"; build.populationFaction = faction;
        build.type = type; build.active = true; KOMEBuildContribution c = new KOMEBuildContribution();
        c.id = "H" + id; c.halfHours = hours; c.status = KOMEBuildContribution.APPROVED; build.contributions.add(c);
        data.builds.put(id, build); return build;
    }
}
