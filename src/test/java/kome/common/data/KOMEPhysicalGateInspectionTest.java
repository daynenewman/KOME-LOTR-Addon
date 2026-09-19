package kome.common.data;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KOMEPhysicalGateInspectionTest {
    @Test public void completeRegularControllerBindingIsCapturedAsReliable() {
        UUID uuid = UUID.randomUUID();
        KOMEPhysicalGateInspection.Result result = inspect(uuid, 7, regularParts(), false);
        assertTrue(result.isLinkable());
        assertEquals(uuid, result.getGateUuid());
        assertEquals(100, result.getDimension());
        assertEquals(120, result.getControllerX());
        assertEquals(64, result.getControllerY());
        assertEquals(-40, result.getControllerZ());
        assertEquals(7, result.getStructureRevision());
        assertEquals("WIDTH_X", result.getOrientation());
        assertEquals(3, result.getDetectedWidth());
        assertEquals(4, result.getDetectedHeight());
        assertEquals(12, result.getProjectedArea());
        assertEquals(KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE,
            result.getStatus());
    }

    @Test public void filledMultiDepthGeometryIsAmbiguousNotReliable() {
        List<GatePartData> parts = regularParts();
        parts.add(new GatePartData(1, 0, 1, GateLeaf.LEFT));
        KOMEPhysicalGateInspection.Result result = inspect(UUID.randomUUID(), 2, parts, false);
        assertTrue(result.isLinkable());
        assertEquals(KOMEDefensiveGateRecord.DimensionDetectionStatus.AMBIGUOUS,
            result.getStatus());
        assertEquals(12, result.getProjectedArea());
    }

    @Test public void sparseProjectedEnvelopeIsIrregularAndStillLinkable() {
        List<GatePartData> parts = regularParts();
        for (int i = 0; i < parts.size(); i++) {
            GatePartData part = parts.get(i);
            if (part.getRelativeX() == 2 && part.getRelativeY() == 3) {
                parts.remove(i);
                break;
            }
        }
        KOMEPhysicalGateInspection.Result result = inspect(UUID.randomUUID(), 3, parts, false);
        assertTrue(result.isLinkable());
        assertEquals(KOMEDefensiveGateRecord.DimensionDetectionStatus.IRREGULAR,
            result.getStatus());
        assertEquals(11, result.getProjectedArea());
    }

    @Test public void incompleteOrBrokenPhysicalStateIsInvalid() {
        assertFalse(inspect(null, 1, regularParts(), false).isLinkable());
        assertFalse(inspect(UUID.randomUUID(), 0, regularParts(), false).isLinkable());
        KOMEPhysicalGateInspection.Result quarantined = inspect(UUID.randomUUID(), 1,
            regularParts(), true);
        assertFalse(quarantined.isLinkable());
        assertEquals(KOMEDefensiveGateRecord.DimensionDetectionStatus.INVALID,
            quarantined.getStatus());
        KOMEPhysicalGateInspection.Result inactive = KOMEPhysicalGateInspection.inspect(
            new KOMEPhysicalGateInspection.Snapshot(100, 120, 64, -40,
                UUID.randomUUID(), 1, true, false, true, GateOrientation.WIDTH_X,
                GateOpeningDirection.FORWARD, new GateHinge(1, 0), new GateHinge(3, 0),
                regularParts(), false));
        assertFalse(inactive.isLinkable());
    }

    @Test public void explicitReinspectionCapturesNewStructureRevision() {
        UUID uuid = UUID.randomUUID();
        assertEquals(4, inspect(uuid, 4, regularParts(), false).getStructureRevision());
        assertEquals(5, inspect(uuid, 5, regularParts(), false).getStructureRevision());
    }

    private static KOMEPhysicalGateInspection.Result inspect(UUID uuid, int revision,
            List<GatePartData> parts, boolean quarantined) {
        return KOMEPhysicalGateInspection.inspect(new KOMEPhysicalGateInspection.Snapshot(
            100, 120, 64, -40, uuid, revision, true, quarantined, true,
            GateOrientation.WIDTH_X, GateOpeningDirection.FORWARD,
            new GateHinge(1, 0), new GateHinge(3, 0), parts));
    }

    static List<GatePartData> regularParts() {
        List<GatePartData> parts = new ArrayList<GatePartData>();
        for (int y = 0; y < 4; y++) {
            parts.add(new GatePartData(1, y, 0, GateLeaf.LEFT));
            parts.add(new GatePartData(2, y, 0, GateLeaf.SPLIT_CENTER));
            parts.add(new GatePartData(3, y, 0, GateLeaf.RIGHT));
        }
        return parts;
    }
}
