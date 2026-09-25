package kome.common.data;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KOMEBuildOwnerSelectionTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    @Test public void gondorViewerCanSelectGondorOnGondorControlledClaimedTile() {
        KOMEWorldData data = dataWithClaimedTile("T388", "gondor");

        assertEquals(Collections.singletonList("gondor"),
            KOMEBuildService.selectablePopulationOwners(data, "gondor", "T388"));
    }

    @Test public void blankViewerHasNoSelectablePopulationOwners() {
        KOMEWorldData data = dataWithClaimedTile("T388", "gondor");

        assertTrue(KOMEBuildService.selectablePopulationOwners(data, "", "T388").isEmpty());
    }

    @Test public void tileIdsAreNormalizedForPopulationOwnerSelection() {
        KOMEWorldData data = dataWithClaimedTile("T388", "gondor");

        List<String> canonical = KOMEBuildService.selectablePopulationOwners(data, "gondor", "T388");
        assertEquals(canonical, KOMEBuildService.selectablePopulationOwners(data, "gondor", "t388"));
    }

    @Test public void forgedIneligiblePopulationOwnerIsRejectedAtCanonicalCreateBoundary() {
        KOMEWorldData data = dataWithClaimedTile("T388", "gondor");

        // Verified Minas Tirith/T388 position: authorization, not spatial failure, is under test.
        assertTrue(KOMEBuildService.validateCoordinates("T388", KOMETileTestResources.dimension(),
            78016.5D, 64D, 66240.5D).allowed);
        try {
            KOMEBuildService.create(data, "Forged Build", "t388", KOMETileTestResources.dimension(),
                78016.5D, 64D, 66240.5D,
                UUID.randomUUID(), "Builder", "gondor", "mordor",
                KOMEBuildType.NORMAL, 100L, 1L);
            fail("Expected the canonical Build service to reject an ineligible population owner");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("foreign population owner"));
        }
        assertTrue(data.builds.isEmpty());
    }

    private static KOMEWorldData dataWithClaimedTile(String tileId, String controller) {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile(tileId);
        tile.defaultRulingFaction = controller;
        tile.claim(controller, 0L);
        data.conquestTiles.put(tile.id, tile);
        return data;
    }
}
