package com.enovak.lotrmoremobs.siege.client.gui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Ensures link and relink button identifiers remain disjoint at protocol limits. */
public class GuiGateManagementButtonIdTest {
    @Test public void linkAndRelinkRangesCannotOverlapAtMaximumListSize() {
        assertTrue(KOMEGateActionButtonIds.isLink(1000));
        assertTrue(KOMEGateActionButtonIds.isLink(1000 + 65534));
        assertFalse(KOMEGateActionButtonIds.isLink(100000));
        assertTrue(KOMEGateActionButtonIds.isRelink(100000));
        assertFalse(KOMEGateActionButtonIds.isRelink(99999));
    }
}
