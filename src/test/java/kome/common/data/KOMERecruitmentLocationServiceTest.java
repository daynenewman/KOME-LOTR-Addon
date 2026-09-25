package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMERecruitmentLocationServiceTest {
    @Test public void controlledDefaultCapitalBypassesOnlyRateThreshold() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            addTile(data, "T388", "gondor", "gondor");
            data.factionCapitals.put("gondor", new KOMEFactionCapitalRecord("gondor", "T388",
                lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID,
                78016.5D, 80.0D, 66240.5D, 1L, "TEST", "SERVER"));
            KOMERecruitmentLocationService.Decision capital =
                KOMERecruitmentLocationService.evaluate(data, " Gondor ", "t388");
            assertTrue(capital.legal); assertTrue(capital.capital);
            assertEquals(0L, capital.effectiveRateUnits.longValueExact());

            data.conquestTiles.get("T388").claim("mordor", 2L);
            assertFalse(KOMERecruitmentLocationService.evaluate(data, "gondor", "T388").legal);

            data.conquestTiles.get("T388").defaultRulingFaction = "rohan";
            data.conquestTiles.get("T388").claim("gondor", 3L);
            KOMERecruitmentLocationService.Decision relocated =
                KOMERecruitmentLocationService.evaluate(data, "gondor", "T388");
            assertTrue(relocated.capital); assertFalse(relocated.legal);
            assertTrue(relocated.reason.contains("default territory"));
        }
    }

    @Test public void nonCapitalUsesOnlyCurrentDevelopedTileRate() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); addTile(data, "T100", "gondor", "gondor");
            KOMEPlayerBuild build = build(data, "B", "T100", "gondor", 10000L, 4999L);
            assertFalse(KOMERecruitmentLocationService.evaluate(data, "gondor", "T100").legal);
            build.developedNativeCentiHours = 5000L;
            assertTrue(KOMERecruitmentLocationService.evaluate(data, "gondor", "T100").legal);

            build.developedNativeCentiHours = 2500L;
            build(data, "B2", "T100", "gondor", 2500L, 2500L);
            assertTrue(KOMERecruitmentLocationService.evaluate(data, "gondor", "T100").legal);

            KOMEPopulationService.grantCenti(data, "gondor", 100000L);
            build.developedNativeCentiHours = 0L;
            data.builds.get("B2").developedNativeCentiHours = 0L;
            assertFalse(KOMERecruitmentLocationService.evaluate(data, "gondor", "T100").legal);
        }
    }

    @Test public void capturedNondefaultTerritoryNeverBecomesRecruitmentSpawn() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); addTile(data, "T100", "rohan", "gondor");
            build(data, "CAPTURED", "T100", "rohan", 20000L, 20000L);
            KOMERecruitmentLocationService.Decision result =
                KOMERecruitmentLocationService.evaluate(data, "gondor", "T100");
            assertEquals(10000000L, result.effectiveRateUnits.longValueExact());
            assertFalse(result.legal);
            assertTrue(result.reason.contains("default territory"));
        }
    }

    @Test public void explicitSelectionRevalidatesAndFallbackIsSortedLegalOnly() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            addTile(data, "T101", "gondor", "gondor");
            addTile(data, "T100", "gondor", "gondor");
            addTile(data, "T099", "rohan", "gondor");
            build(data, "A", "T101", "gondor", 5000L, 5000L);
            build(data, "B", "T100", "gondor", 5000L, 5000L);
            build(data, "C", "T099", "gondor", 5000L, 5000L);
            UUID player = UUID.randomUUID();
            assertEquals("T100", data.resolveRecruitmentTile(player, "gondor"));
            assertTrue(data.setActiveRecruitmentTile(player, "gondor", "T101"));
            assertEquals("T101", data.resolveRecruitmentTile(player, "gondor"));
            data.conquestTiles.get("T101").claim("rohan", 1L);
            assertEquals("T100", data.resolveRecruitmentTile(player, "gondor"));
            assertTrue(data.clearActiveRecruitmentTile(player, "gondor"));
            assertEquals("T100", data.resolveRecruitmentTile(player, "gondor"));
        }
    }

    @Test public void capturedRateUsesDevelopedNotApprovedCapacity() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); addTile(data, "T100", "gondor", "rohan");
            KOMEPlayerBuild build = build(data, "B", "T100", "gondor", 10000L, 4000L);
            KOMEPopulationRateContribution row = KOMEPopulationRateService
                .getPopulationRateContributions(data, KOMEConfigRegistry.population()).get(0);
            assertEquals(4000L, row.developedNativeCentiHours);
            assertEquals(6000L, row.pendingNativeCentiHours);
            assertEquals(4000000L, row.nativeDevelopedRateUnits.longValueExact());
            assertEquals(2000000L, row.currentRateUnits.longValueExact());
            build.developedNativeCentiHours = 0L;
            assertEquals(0L, KOMEPopulationRateService.getPopulationRateContributions(data).get(0)
                .currentRateUnits.longValueExact());
        }
    }

    private static KOMEWorldData world() { return new KOMEWorldData("recruitment"); }

    private static void addTile(KOMEWorldData data, String id, String defaults, String controller) {
        KOMEConquestTile tile = new KOMEConquestTile(id);
        tile.defaultRulingFaction = defaults; tile.claim(controller, 0L);
        data.conquestTiles.put(id, tile);
    }

    private static KOMEPlayerBuild build(KOMEWorldData data, String id, String tile,
            String faction, long approved, long developed) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = id; build.displayName = id; build.tileId = tile;
        build.populationFaction = faction; build.originalBuilderFaction = faction;
        build.type = KOMEBuildType.NORMAL; build.active = true;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H-" + id; contribution.centiHours = approved;
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution); build.developedNativeCentiHours = developed;
        build.validateContributions(); data.builds.put(id, build); return build;
    }
}
