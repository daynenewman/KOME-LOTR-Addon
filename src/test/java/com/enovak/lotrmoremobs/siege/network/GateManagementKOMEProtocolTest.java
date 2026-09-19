package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.siege.management.KOMEGateManagementSnapshot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GateManagementKOMEProtocolTest {
    @Test public void managementSnapshotRoundTripsLinkAndRelinkIdentifiers() {
        KOMEGateManagementSnapshot snapshot = new KOMEGateManagementSnapshot(true, false,
            true, "B1", "Fortress", "G2", "Needs confirmation",
            "Unavailable — confirm dimensions", 6, 8, 1000,
            Arrays.asList(new KOMEGateManagementSnapshot.BuildOption("B3",
                "Outer Wall (B3) — 100h — 15,159 HP")),
            Arrays.asList(new KOMEGateManagementSnapshot.RelinkOption("B4", "G7",
                "Keep Gate (B4/G7) — Relink")));
        GateManagementOpenPacket sent = new GateManagementOpenPacket(100, 1, 64, -2,
            true, true, true, snapshot);
        ByteBuf bytes = Unpooled.buffer();
        sent.toBytes(bytes);
        GateManagementOpenPacket received = new GateManagementOpenPacket();
        received.fromBytes(bytes);

        assertEquals(100, received.getDimensionId());
        assertTrue(received.canAdminister());
        assertEquals("B1", received.getKomeSnapshot().getBuildId());
        assertEquals("G2", received.getKomeSnapshot().getRecordId());
        assertEquals(1, received.getKomeSnapshot().getEligibleBuilds().size());
        assertEquals("B3", received.getKomeSnapshot().getEligibleBuilds().get(0).getBuildId());
        assertEquals("G7", received.getKomeSnapshot().getRelinkOptions().get(0).getRecordId());
    }

    @Test public void allKomeMutationActionsAreKnownAndIdentifierPayloadsAreBounded() {
        for (int action : new int[] {GateManagementActionPacket.KOME_LINK,
                GateManagementActionPacket.KOME_UNLINK,
                GateManagementActionPacket.KOME_REFRESH,
                GateManagementActionPacket.KOME_RELINK,
                GateManagementActionPacket.KOME_CONFIRM_DIMENSIONS}) {
            assertTrue(GateManagementActionPacket.isKnownAction(action));
            assertTrue(GateManagementActionPacket.isValidRequestText(action, "B1|G2|6|8"));
        }
    }
}
