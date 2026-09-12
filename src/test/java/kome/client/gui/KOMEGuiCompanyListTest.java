package kome.client.gui;

import kome.common.network.KOMECompanyGuiEntry;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class KOMEGuiCompanyListTest {
    @Test
    public void haltedRecoverySummaryIncludesFinalDestination() {
        KOMECompanyGuiEntry entry = new KOMECompanyGuiEntry();
        entry.accessLossReason = "Military passage lost before entering T004 (owner Rohan).";
        entry.currentTile = "T003";
        entry.nextTile = "T004";
        entry.intendedDestinationTile = "T005";

        String summary = KOMECompanyRecoveryPresentation.haltedRecoverySummary(entry);

        assertTrue(summary.contains("Current T003"));
        assertTrue(summary.contains("Next T004"));
        assertTrue(summary.contains("Destination T005"));
    }
}
