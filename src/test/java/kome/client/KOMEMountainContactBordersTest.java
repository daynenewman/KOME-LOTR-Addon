package kome.client;

import java.util.HashSet;
import java.util.Set;
import java.io.InputStream;
import kome.common.data.KOMEConquestTileDefaults;
import kome.common.data.KOMETileRasterSnapshot;
import kome.common.data.KOMETileTestResources;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** KOM-80: map contact is geographic evidence, not strategic route permission. */
public class KOMEMountainContactBordersTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void reportedContactsRenderExactlyTheCanonicalSharedCellEdges() throws Exception {
        KOMETileRasterSnapshot snapshot = KOMETileTestResources.real();
        // Preserve KOM-80's exact historical observations against the immutable pre-cut mask.
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream mask = loader.getResourceAsStream("kome/tile/mountain-separation-approved/before-mask.png");
                InputStream mapping = loader.getResourceAsStream("assets/kome/map/reset_conquest_tile_ids.txt")) {
            assertHistoricalContacts(KOMETileRasterSnapshot.load(mask, mapping, snapshot.transform,
                KOMEConquestTileDefaults.getKnownTileIds(), KOMEConquestTileDefaults.getRetiredTileIds()));
        }
        int[] pixels = snapshot.copyArgbPixels();
        KOMEMapBorders borders = new KOMEMapBorders.Cache().get(snapshot, pixels);

        // Only the separately hash-checked 45-cell authority removes these five contacts.
        assertContact(snapshot, pixels, borders, "T435", "T654", 0, 0);
        assertContact(snapshot, pixels, borders, "T455", "T654", 0, 0); // Harnen remains unverified and unchanged.
        assertContact(snapshot, pixels, borders, "T400", "T420", 0, 0);
        assertContact(snapshot, pixels, borders, "T329", "T355", 0, 0);
        assertContact(snapshot, pixels, borders, "T352", "T356", 0, 0);
        assertContact(snapshot, pixels, borders, "T218", "T220", 0, 0);
    }

    private static void assertHistoricalContacts(KOMETileRasterSnapshot snapshot) {
        int[] pixels = snapshot.copyArgbPixels();
        KOMEMapBorders borders = new KOMEMapBorders.Cache().get(snapshot, pixels);

        assertContact(snapshot, pixels, borders, "T435", "T654", 2, 0);
        assertContact(snapshot, pixels, borders, "T455", "T654", 0, 0);
        assertContact(snapshot, pixels, borders, "T400", "T420", 1, 9);
        assertContact(snapshot, pixels, borders, "T329", "T355", 3, 10);
        assertContact(snapshot, pixels, borders, "T352", "T356", 6, 2);
        assertContact(snapshot, pixels, borders, "T218", "T220", 11, 3);
    }

    private static void assertContact(KOMETileRasterSnapshot snapshot, int[] pixels,
            KOMEMapBorders borders, String first, String second,
            int expectedVertical, int expectedHorizontal) {
        String pair = first + "/" + second;
        int a = 0xFF000000 | snapshot.colorsById().get(first);
        int b = 0xFF000000 | snapshot.colorsById().get(second);
        Set<Long> expected = new HashSet<Long>();
        int vertical = 0, horizontal = 0;
        // Independently enumerate only actual cardinal cell contacts, once each.
        // Diagonal proximity or a gap does not create a shared rendered edge.
        for (int y = 0; y < snapshot.height; y++) {
            for (int x = 0; x < snapshot.width; x++) {
                int index = y * snapshot.width + x;
                int here = label(pixels[index]);
                if (here != a && here != b) continue;
                int other = here == a ? b : a;
                if (x + 1 < snapshot.width && label(pixels[index + 1]) == other) {
                    expected.add(edgeKey(true, x + 1, y));
                    vertical++;
                }
                if (y + 1 < snapshot.height && label(pixels[index + snapshot.width]) == other) {
                    expected.add(edgeKey(false, y + 1, x));
                    horizontal++;
                }
            }
        }
        assertEquals(pair + " vertical contacts", expectedVertical, vertical);
        assertEquals(pair + " horizontal contacts", expectedHorizontal, horizontal);

        Set<Long> rendered = new HashSet<Long>();
        borders.visitEdges(0, 0, snapshot.width, snapshot.height,
            (isVertical, line, start, end, negative, positive) -> {
                if (!(negative == a && positive == b || negative == b && positive == a)) return;
                for (int along = start; along < end; along++) {
                    assertTrue(pair + " duplicate rendered edge",
                        rendered.add(edgeKey(isVertical, line, along)));
                }
            });
        assertEquals(pair + " rendered edge coordinates", expected, rendered);
    }

    private static int label(int argb) {
        return (argb >>> 24) > 24 ? 0xFF000000 | (argb & 0xFFFFFF) : 0;
    }

    private static long edgeKey(boolean vertical, int line, int along) {
        return (vertical ? Long.MIN_VALUE : 0L) | ((long) line << 32) | (along & 0xFFFFFFFFL);
    }
}
