package kome.common.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEWorldDataLineageTest {
    @Test public void independentlyNumberedCampaignRootsRequireTheirOriginalAuthority() {
        KOMEWorldDataLineage reset = KOMEWorldDataLineage.resolve(8, false, false, false);
        assertTrue(reset.resetRequired); assertFalse(reset.campaignRequired); assertFalse(reset.movementRequired);
        KOMEWorldDataLineage governance = KOMEWorldDataLineage.resolve(9, false, false, false);
        assertFalse(governance.resetRequired); assertTrue(governance.campaignRequired);
        KOMEWorldDataLineage combined = KOMEWorldDataLineage.resolve(10, false, false, false);
        assertTrue(combined.resetRequired); assertTrue(combined.campaignRequired); assertFalse(combined.tacticalRequired);
    }

    @Test public void devLineagesNeverAcquireMandatoryCampaignSectionsRetroactively() {
        KOMEWorldDataLineage tactical = KOMEWorldDataLineage.resolve(8, true, false, false);
        assertTrue(tactical.tacticalRequired); assertFalse(tactical.resetRequired); assertFalse(tactical.campaignRequired);
        KOMEWorldDataLineage defense = KOMEWorldDataLineage.resolve(8, false, true, false);
        assertTrue(defense.emergencyDefenseEight); assertFalse(defense.resetRequired);
        KOMEWorldDataLineage movement = KOMEWorldDataLineage.resolve(9, true, false, true);
        assertTrue(movement.movementRequired); assertTrue(movement.tacticalRequired); assertFalse(movement.campaignRequired);
        assertTrue(KOMEWorldDataLineage.resolve(9, false, true, false).emergencyDefenseNine);
        for (int schema : new int[] {10, 11}) {
            KOMEWorldDataLineage dev = KOMEWorldDataLineage.resolve(schema, true, true, schema == 11);
            assertTrue(dev.tacticalRequired); assertFalse(dev.resetRequired); assertFalse(dev.campaignRequired);
            assertEquals(schema == 11, dev.movementRequired);
        }
    }

    @Test public void currentRootRequiresAllAuthoritiesAndPresentLegacyMovementIsPreserved() {
        KOMEWorldDataLineage current = KOMEWorldDataLineage.resolve(12, true, true, true);
        assertTrue(current.resetRequired); assertTrue(current.campaignRequired);
        assertTrue(current.movementRequired); assertTrue(current.tacticalRequired);
        for (int schema : new int[] {6, 7}) {
            assertTrue(KOMEWorldDataLineage.resolve(schema, false, false, true).movementRequired);
            assertFalse(KOMEWorldDataLineage.resolve(schema, false, false, false).movementRequired);
        }
    }

    @Test public void mixedLineagesCannotDiscardPresentAuthority() {
        int[][] invalid = {{8,1,1,0}, {8,1,0,1}, {9,1,1,1}, {9,1,1,0},
            {9,0,1,1}, {9,1,0,0}, {10,1,1,1}, {10,1,0,0}, {10,0,1,0},
            {11,0,1,1}, {11,1,0,1}, {12,0,1,1}, {12,1,0,1}, {6,0,1,0}, {7,0,1,0}};
        for (int[] row : invalid)
            assertThrows(IllegalArgumentException.class,
                () -> KOMEWorldDataLineage.resolve(row[0], row[1] == 1, row[2] == 1, row[3] == 1));
    }
}
