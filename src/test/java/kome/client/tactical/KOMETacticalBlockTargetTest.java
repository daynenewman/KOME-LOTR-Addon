package kome.client.tactical;

import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETacticalBlockTargetTest {
    private static final class Cells implements KOMETacticalBlockTarget.Cells {
        int first = 20, second = 90, lastLoadedX = 200, furthestInspected = -1;
        @Override public boolean loaded(int x, int y, int z) {
            furthestInspected = Math.max(furthestInspected, x);
            return x <= lastLoadedX;
        }
        @Override public MovingObjectPosition collide(int x, int y, int z, Vec3 from, Vec3 to) {
            return x == first || x == second
                ? new MovingObjectPosition(x, y, z, 4, Vec3.createVectorHelper(x, y + 0.5D, z + 0.5D)) : null;
        }
    }
    @Test public void extendedPlacementStopsAtFirstPhysicalSurface() {
        Cells cells = new Cells();
        MovingObjectPosition hit = KOMETacticalBlockTarget.trace(cells, 0.5, 64.5, 0.5, 1, 0, 0, 128);
        assertNotNull(hit); assertEquals(20, hit.blockX);
        assertEquals(20, cells.furthestInspected);
        cells.first = 129;
        hit = KOMETacticalBlockTarget.trace(cells, 0.5, 64.5, 0.5, 1, 0, 0, 128);
        assertNotNull(hit); assertEquals(90, hit.blockX);
    }
    @Test public void unloadedChunkEndsPlacementWithoutInspectingBeyondIt() {
        Cells cells = new Cells(); cells.lastLoadedX = 15;
        assertNull(KOMETacticalBlockTarget.trace(cells, 0.5, 64.5, 0.5, 1, 0, 0, 128));
        assertEquals(16, cells.furthestInspected);
        assertNull(KOMETacticalBlockTarget.trace(cells, 0.5, 64.5, 0.5, 1, 0, 0, 129));
    }
}
