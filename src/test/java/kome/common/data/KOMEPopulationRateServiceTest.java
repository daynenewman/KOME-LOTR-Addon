package kome.common.data;

import org.junit.Test;
import kome.common.config.KOMEConfigRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.OptionalInt;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMEPopulationRateServiceTest {
    @Test public void fixedRatesUseExactHalfHourSource() {
        assertEquals(1_000_000L, KOMEPopulationRateService.rate(20, 10).getFixedUnitsPerDay());
        assertEquals(500_000L, KOMEPopulationRateService.rate(10, 10).getFixedUnitsPerDay());
        assertEquals(50_000L, KOMEPopulationRateService.rate(1, 10).getFixedUnitsPerDay());
        assertEquals(500_000L, KOMEPopulationRateService.rate(20, 20).getFixedUnitsPerDay());
    }

    @Test public void nativeNormalBuildsAndDefensiveBuildsRemainSeparated() {
        KOMEWorldData data = data();
        KOMEPlayerBuild first = build(data, "B2", "gondor", KOMEBuildType.NORMAL, 10);
        KOMEPlayerBuild second = build(data, "B1", "gondor", KOMEBuildType.NORMAL, 20);
        KOMEPlayerBuild defensive = build(data, "B3", "gondor", KOMEBuildType.DEFENSIVE, 40);
        assertEquals(1_500_000L, KOMEPopulationService.getDailyPopulationRate(data, "gondor").getFixedUnitsPerDay());
        assertEquals("B1", KOMEPopulationService.getPopulationRateContributions(data).get(0).buildId);
        data.conquestTiles.get("T1").claim("rohan", 0L);
        assertEquals(750_000L, KOMEPopulationService.getDailyPopulationRate(data, "rohan").getFixedUnitsPerDay());
        assertEquals("CAPTURED", KOMEPopulationService.getPopulationRateContributions(data).get(0).status);
        assertFalse(KOMEPopulationService.getPopulationRateContributions(data).toString().contains(defensive.id));
    }

    @Test public void captureRecaptureAndForeignTransferRedirectCurrentRate() {
        KOMEWorldData data=data(); build(data,"B","gondor",KOMEBuildType.NORMAL,1);
        data.conquestTiles.get("T1").claim("rohan",0L);
        KOMEPopulationRateContribution row=KOMEPopulationService.getPopulationRateContributions(data).get(0);
        assertEquals("gondor",row.populationFaction); assertEquals("rohan",row.receivingFaction); assertEquals(25_000L,row.currentRate.getFixedUnitsPerDay()); assertEquals("0.5",row.multiplier);
        data.conquestTiles.get("T1").claim("mordor",0L); assertEquals(25_000L,KOMEPopulationService.getDailyPopulationRate(data,"mordor").getFixedUnitsPerDay()); assertEquals(0L,KOMEPopulationService.getDailyPopulationRate(data,"rohan").getFixedUnitsPerDay());
        data.conquestTiles.get("T1").claim("gondor",0L); assertEquals(50_000L,KOMEPopulationService.getDailyPopulationRate(data,"gondor").getFixedUnitsPerDay());
    }

    @Test public void capturedRatesAggregateBeforeFixedPointRounding() {
        KOMEWorldData data=data(); build(data,"B1","gondor",KOMEBuildType.NORMAL,1); build(data,"B2","gondor",KOMEBuildType.NORMAL,1); data.conquestTiles.get("T1").claim("rohan",0L);
        assertEquals(50_000L,KOMEPopulationService.getDailyPopulationRate(data,"rohan").getFixedUnitsPerDay());
    }

    @Test public void configuredCapturedMultiplierChangesImmediately() throws Exception {
        KOMEWorldData data=data(); build(data,"B","gondor",KOMEBuildType.NORMAL,20); data.conquestTiles.get("T1").claim("rohan",0L);
        KOMEConfigRegistry.ValidatedConfig config=KOMEConfigRegistry.currentValidated(); Field field=KOMEConfigRegistry.ValidatedConfig.class.getDeclaredField("population"); field.setAccessible(true); Object prior=field.get(config); KOMEConfigRegistry.PopulationSettings old=(KOMEConfigRegistry.PopulationSettings)prior;
        Constructor<KOMEConfigRegistry.PopulationSettings> c=KOMEConfigRegistry.PopulationSettings.class.getDeclaredConstructor(int.class,double.class,boolean.class,boolean.class,OptionalInt.class,boolean.class); c.setAccessible(true);
        try { field.set(config,c.newInstance(old.getHoursPerPopulationPoint(),0.25D,old.isOfflinePopulationCatchUp(),old.isPopulationCapEnabled(),old.getPopulationCapValue(),old.isEncirclementPopulationSuppressionEnabled())); assertEquals(250_000L,KOMEPopulationService.getDailyPopulationRate(data,"rohan").getFixedUnitsPerDay()); }
        finally { field.set(config,prior); }
    }

    @Test public void uncontrolledBuildRetainsOriginalButProducesNothing() {
        KOMEWorldData data=data(); KOMEPlayerBuild b=build(data,"B","gondor",KOMEBuildType.NORMAL,20); b.tileId="missing"; KOMEPopulationRateContribution row=KOMEPopulationService.getPopulationRateContributions(data).get(0);
        assertEquals("UNCONTROLLED",row.status); assertEquals(1_000_000L,row.originalRate.getFixedUnitsPerDay()); assertEquals(0L,row.currentRate.getFixedUnitsPerDay()); assertTrue(row.receivingFaction.length()==0);
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
