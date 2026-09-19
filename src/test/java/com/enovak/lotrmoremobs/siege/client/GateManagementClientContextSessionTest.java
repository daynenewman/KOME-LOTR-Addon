package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.management.KOMEGateManagementSnapshot;
import java.lang.reflect.Field;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GateManagementClientContextSessionTest {
    @Test public void staleScreenCannotClearReplacementContext() {
        GateManagementClientContext.Session session =
            new GateManagementClientContext.Session();
        long oldScreen = session.open();
        long replacementScreen = session.open();

        assertFalse(session.clearIfOwned(oldScreen));
        assertTrue(session.isActive());
        assertEquals(replacementScreen, session.getGeneration());
        assertTrue(session.isOwnedBy(replacementScreen));
    }

    @Test public void activeScreenClearsItsOwnContext() {
        GateManagementClientContext.Session session =
            new GateManagementClientContext.Session();
        long activeScreen = session.open();

        assertTrue(session.clearIfOwned(activeScreen));
        assertFalse(session.isActive());
        assertFalse(session.isOwnedBy(activeScreen));
        assertTrue(session.getGeneration() > activeScreen);
    }

    @Test public void replacementGenerationOwnsTheAuthoritativeSession() {
        GateManagementClientContext.Session session =
            new GateManagementClientContext.Session();
        long first = session.open();
        long replacement = session.open();

        assertTrue(replacement > first);
        assertFalse(session.isOwnedBy(first));
        assertTrue(session.isOwnedBy(replacement));
    }

    @Test public void staleCleanupLeavesReplacementSnapshotInstalled() throws Exception {
        Field sessionField = GateManagementClientContext.class.getDeclaredField("SESSION");
        sessionField.setAccessible(true);
        GateManagementClientContext.Session session =
            (GateManagementClientContext.Session) sessionField.get(null);
        Field snapshotField = GateManagementClientContext.class.getDeclaredField("komeSnapshot");
        snapshotField.setAccessible(true);
        GateManagementClientContext.clear();
        try {
            long oldScreen = session.open();
            long replacementScreen = session.open();
            KOMEGateManagementSnapshot replacement = snapshot("B2");
            snapshotField.set(null, replacement);

            assertFalse(GateManagementClientContext.clearIfOwned(oldScreen));
            assertSame(replacement, GateManagementClientContext.getKomeSnapshot());
            assertTrue(GateManagementClientContext.isCurrentGeneration(replacementScreen));

            assertTrue(GateManagementClientContext.clearIfOwned(replacementScreen));
            assertNull(GateManagementClientContext.getKomeSnapshot());
            assertFalse(GateManagementClientContext.isActive());
        } finally {
            GateManagementClientContext.clear();
        }
    }

    private static KOMEGateManagementSnapshot snapshot(String buildId) {
        return new KOMEGateManagementSnapshot(true, false, false, buildId, "Fortress",
            "G1", "3x4", "5,000", 3, 4, 1000,
            Collections.<KOMEGateManagementSnapshot.BuildOption>emptyList(),
            Collections.<KOMEGateManagementSnapshot.RelinkOption>emptyList());
    }
}
