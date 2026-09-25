package kome.common.data;

import lotr.common.LOTRDimension;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEPopulationGrandfatherImportTest {
    @org.junit.Rule public final KOMETileTestResources tileGeometry = new KOMETileTestResources();

    @Test public void authorizedImportCreatesFullyDevelopedCanonicalBuildOnly() {
        KOMEWorldData data = world(); UUID admin = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.importGrandfatheredNormal(data, true,
            "Old City", "T100", KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 80.0D, KOMETileTestResources.z(),
            admin, "Operator", "gondor", 8000L, 10L);
        assertEquals(KOMEBuildType.NORMAL, build.type);
        assertEquals(8000L, build.approvedCentiHours());
        assertEquals(8000L, build.developedNativeCentiHours);
        assertEquals(0L, build.pendingNativeCentiHours());
        assertEquals(8000000L, KOMEPopulationRateService.getExactDailyPopulationRates(
            data, kome.common.config.KOMEConfigRegistry.population()).get("gondor").longValueExact());
        assertTrue(data.centralAudit.stream().anyMatch(e -> "GRANDFATHER_IMPORT".equals(e.action)));

        KOMEBuildService.addSubmission(data, build, admin, "Operator", "gondor", 3000L, true, 20L);
        assertEquals(11000L, build.approvedCentiHours());
        assertEquals(8000L, build.developedNativeCentiHours);
        assertEquals(3000L, build.pendingNativeCentiHours());
    }

    @Test public void unauthorizedOrInvalidImportDoesNotPartiallyMutate() {
        KOMEWorldData data = world(); UUID admin = UUID.randomUUID();
        long sequence = data.nextBuildSequence; int audits = data.centralAudit.size();
        try {
            KOMEBuildService.importGrandfatheredNormal(data, false, "Old City", "T100",
                LOTRDimension.MIDDLE_EARTH.dimensionID, 0D, 64D, 0D,
                admin, "Player", "gondor", 8000L, 10L);
            fail("Expected operator rejection");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("administrators")); }
        assertTrue(data.builds.isEmpty()); assertEquals(sequence, data.nextBuildSequence);
        assertEquals(audits, data.centralAudit.size());

        try {
            KOMEBuildService.importGrandfatheredNormal(data, true, "Old City", "T100",
                KOMETileTestResources.dimension(), KOMETileTestResources.x(), 80D,
                KOMETileTestResources.z(),
                admin, "Operator", "gondor", 0L, 10L);
            fail("Expected zero-hour rejection");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("positive")); }
        try {
            KOMEBuildService.importGrandfatheredNormal(data, true, "Old City", "T100",
                KOMETileTestResources.dimension(), KOMETileTestResources.x(), 80D,
                KOMETileTestResources.z(),
                admin, "Operator", "wanderer", 8000L, 10L);
            fail("Expected unsupported-faction rejection");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("unsupported")); }
        try {
            KOMEBuildService.importGrandfatheredNormal(data, true, "Old City", "T999999",
                KOMETileTestResources.dimension(), KOMETileTestResources.x(), 80D,
                KOMETileTestResources.z(),
                admin, "Operator", "gondor", 8000L, 10L);
            fail("Expected unknown-tile rejection");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("unknown")); }
        assertTrue(data.builds.isEmpty()); assertEquals(sequence, data.nextBuildSequence);
        assertEquals(audits, data.centralAudit.size()); assertFalse(data.isDirty());
    }

    @Test public void developedAllocationClampsWhenApprovalIsReducedAndIsAudited() {
        KOMEWorldData data = world(); UUID admin = UUID.randomUUID();
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "B"; build.tileId = "T100"; build.populationFaction = "gondor";
        build.originalBuilderFaction = "gondor"; build.type = KOMEBuildType.NORMAL;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H"; contribution.centiHours = 8000L;
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution); build.developedNativeCentiHours = 8000L;
        data.builds.put(build.id, build);
        contribution.centiHours = 3000L;
        KOMEPopulationDevelopmentService.clampDevelopedToApproved(data, build,
            admin, "Operator", 20L);
        assertEquals(3000L, build.developedNativeCentiHours);
        assertEquals(0L, build.pendingNativeCentiHours());
        assertTrue(data.centralAudit.stream().anyMatch(e -> "DEVELOPED_CLAMP".equals(e.action)));
    }

    private static KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("grandfather");
        KOMEConquestTile tile = new KOMEConquestTile("T100");
        tile.defaultRulingFaction = "gondor"; tile.claim("gondor", 0L);
        data.conquestTiles.put(tile.id, tile); return data;
    }
}
