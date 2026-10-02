package kome.common.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMETileRasterSnapshotTest.*;
import static kome.common.data.KOMETileResolution.Status.*;

public class KOMETileExclusionsTest {
    static byte[] image() throws IOException { return bytes(png(4, 1, 0xFF000001, 0, 0, 0)); }
    static byte[] bytes(ByteArrayInputStream input) {
        byte[] result = new byte[input.available()]; assertEquals(result.length, input.read(result, 0, result.length)); return result;
    }
    static String header(byte[] image) { return "schema=1\nwidth=4\nheight=1\nmask_sha256=" + KOMETileExclusions.sha256(image) + "\n"; }
    static String zones() { return "zone\triver-a\triver\tFixture only, not approved geography\n"
        + "zone\triver-b\triver\tSeparate fixture zone\nrun\t0\t1\t2\triver-a\nrun\t0\t2\t3\triver-b\n"; }
    static KOMETileRasterSnapshot load(String records) throws IOException {
        byte[] image = image();
        return KOMETileRasterSnapshot.load(new ByteArrayInputStream(image), text(MAPPING), text(header(image) + records),
            new KOMETileRasterSnapshot.Transform(173, 1, 1, 128, 4, 1), IDS, Collections.<String>emptySet());
    }

    @Test public void exactClassifiedCellBoundariesRemainSeparateFromIdentityGapsAndOutside() throws Exception {
        KOMETileRasterSnapshot s = load(zones());
        assertEquals("T001", s.resolve(173, -1, -1).tileId);
        KOMETileResolution a = s.resolve(173, 0, -1);
        assertEquals(CLASSIFIED_EXCLUSION, a.status); assertEquals("river-a", a.exclusion().get().id);
        assertEquals("river", a.exclusion().get().type); assertFalse(a.resolvedTileId().isPresent());
        assertEquals(Boolean.FALSE, a.capturable().get()); assertFalse(a.traversable().isPresent());
        assertEquals("river-a", s.resolve(173, 127, -1).exclusion().get().id);
        assertEquals("river-b", s.resolve(173, 128, -1).exclusion().get().id);
        assertEquals(IN_BOUNDS_GAP, s.resolve(173, 256, -1).status);
        assertFalse(s.resolve(173, 256, -1).capturable().isPresent());
        assertEquals(OUTSIDE_MASK, s.resolve(173, 384, -1).status);
        assertEquals(OUTSIDE_MASK, s.resolve(173, 0, -129).status);
        assertEquals(UNSUPPORTED_DIMENSION, s.resolve(0, 0, -1).status);
        assertFalse(s.resolve(0, 0, -1).exclusion().isPresent());
        assertEquals(2, s.exclusions.classifiedCells); assertEquals(2, s.exclusions.zoneCount());
        assertEquals(8, s.exclusions.cellIndexBytes());
        int[] rendering = s.copyArgbPixels(); rendering[1] = 0xFF000001;
        assertEquals(CLASSIFIED_EXCLUSION, s.resolve(173, 0, -1).status);
        assertEquals(0, s.copyArgbPixels()[1]); // No invented rendering/tile identity.
    }

    @Test public void openVocabularyDoesNotEmbedPermanentTraversalRules() throws Exception {
        for (String type : new String[] {"ocean", "mountain", "desert", "river", "bridge.pass-v2"}) {
            KOMETileResolution z = load("zone\tx\t" + type + "\tFixture\nrun\t0\t1\t2\tx\n").resolve(173, 0, -1);
            assertEquals(type, z.exclusion().get().type); assertFalse(z.traversable().isPresent());
        }
    }

    @Test public void bundledEmptySetKeepsAllProtectedControlsUnclassified() {
        KOMETileRasterSnapshot s = KOMETileTestResources.real(); int d = s.transform.dimension;
        assertEquals(0, s.exclusions.zoneCount()); assertEquals(0, s.exclusions.classifiedCells);
        assertEquals(0, s.exclusions.cellIndexBytes()); assertTrue(s.exclusions.diagnostic.contains("Validated"));
        assertEquals(IN_BOUNDS_GAP, s.resolve(d, 189568, -86016).status);
        assertEquals("T001", s.resolve(d, 189696, -86016).tileId);
        assertEquals(IN_BOUNDS_GAP, s.resolve(d, 34944, 640).status);
        assertEquals("T149", s.resolve(d, 20992, -384).tileId);
    }

    @Test public void malformedRowsAndTileOverlapRejectRatherThanClassify() throws Exception {
        String zone = "zone\tx\triver\tFixture\n";
        for (String records : new String[] {
            zone, zone + zone, "zone\tT001\triver\tFixture\n", "zone\tx\tRiver\tFixture\n",
            "zone\tx\triver\t   \n", zone + "run\t0\t1\t2\tunknown\n",
            zone + "run\t0\t0\t1\tx\n", zone + "run\t0\t1\t2\tx\nrun\t0\t1\t2\tx\n",
            zone + "run\t1\t1\t2\tx\n", zone + "run\t0\t-1\t2\tx\n",
            zone + "run\t0\t1\t5\tx\n", zone + "run\t0\t2\t2\tx\n",
            zone + "run\t0\t1\t2147483648\tx\n", zone + "run\t0\t01\t2\tx\n",
            zone + "run\t0\t1\t2\tx\nzone\ty\triver\tlate\n", "polygon\tx\t1\t2\n"}) {
            try { load(records); fail("Accepted malformed records: " + records); }
            catch (IOException expected) { assertNotNull(expected.getMessage()); }
        }
    }

    @Test public void missingMalformedHashSchemaDimensionsAndUtf8AreVisible() throws Exception {
        byte[] image = image(); String good = header(image);
        for (String metadata : new String[] {"", good.replace("schema=1", "schema=2"), good.replace("width=4", "width=5"),
                good.replace("height=1", "height=2"), good.replace(KOMETileExclusions.sha256(image), String.join("", Collections.nCopies(64, "0")))}) {
            rejected(image, metadata == null ? null : text(metadata));
        }
        rejected(image, null);
        rejected(image, new ByteArrayInputStream(new byte[] {(byte) 0xC3, (byte) 0x28}));
        char[] huge = new char[4 * 1024 * 1024 + 1]; Arrays.fill(huge, '#'); rejected(image, text(new String(huge)));
    }
    private static void rejected(byte[] image, ByteArrayInputStream metadata) throws Exception {
        try {
            KOMETileRasterSnapshot.load(new ByteArrayInputStream(image), text(MAPPING), metadata,
                new KOMETileRasterSnapshot.Transform(173, 1, 1, 128, 4, 1), IDS, Collections.<String>emptySet());
            fail("Bad metadata accepted");
        } catch (IOException expected) { assertNotNull(expected.getMessage()); }
    }

    @Test public void retiredCellsAreUnknownUntilExplicitlyAnnotated() throws Exception {
        byte[] image = bytes(png(4, 1, 0xFF000001, 0xFF000002, 0, 0));
        KOMETileRasterSnapshot s = KOMETileRasterSnapshot.load(new ByteArrayInputStream(image), text(MAPPING),
            text(header(image) + "zone\tx\tother\tExplicit fixture decision\nrun\t0\t1\t2\tx\n"),
            new KOMETileRasterSnapshot.Transform(173, 1, 1, 128, 4, 1), Collections.singleton("T001"), Collections.singleton("T002"));
        assertEquals(CLASSIFIED_EXCLUSION, s.resolve(173, 0, -1).status);
        assertEquals(IN_BOUNDS_GAP, s.resolve(173, 128, -1).status);
    }

    @Test public void failedMetadataReloadKeepsWholePriorSnapshotAndDiagnostic() throws Exception {
        KOMETileWorldResolver r = new KOMETileWorldResolver(); r.publish(load(zones()));
        KOMETileWorldResolver.ReadView old = r.readView(); byte[] image = image();
        assertFalse(r.reload(new ByteArrayInputStream(image), text(MAPPING), null,
            new KOMETileRasterSnapshot.Transform(173, 1, 1, 128, 4, 1), IDS, Collections.<String>emptySet()));
        assertTrue(r.loadDiagnostic().contains("Missing tile exclusion"));
        assertEquals("river-a", r.resolve(173, 0, -1).exclusion().get().id);
        assertEquals("river-a", old.resolveWorldPosition(173, 0, -1).exclusion().get().id);
        assertTrue(r.reload(new ByteArrayInputStream(image), text(MAPPING), text(header(image)),
            new KOMETileRasterSnapshot.Transform(173, 1, 1, 128, 4, 1), IDS, Collections.<String>emptySet()));
        assertEquals(IN_BOUNDS_GAP, r.resolve(173, 0, -1).status);
        assertEquals(CLASSIFIED_EXCLUSION, old.resolveWorldPosition(173, 0, -1).status);
    }

    @Test public void concurrentReadersSeeCompleteGeometryAndClassificationPair() throws Exception {
        KOMETileWorldResolver r = new KOMETileWorldResolver();
        KOMETileRasterSnapshot a = load(zones()), b = load(""); r.publish(a);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 20000; i++) {
                    KOMETileWorldResolver.ReadView view = r.readView();
                    KOMETileResolution x = view.resolveWorldPosition(173, 0, -1), y = view.resolveWorldPosition(173, 128, -1);
                    if (x.status == CLASSIFIED_EXCLUSION) {
                        assertEquals("river-a", x.exclusion().get().id); assertEquals("river-b", y.exclusion().get().id);
                    } else { assertEquals(IN_BOUNDS_GAP, x.status); assertEquals(IN_BOUNDS_GAP, y.status); }
                }
            } catch (Throwable t) { failure.set(t); }
        });
        reader.start(); for (int i = 0; i < 20000; i++) r.publish((i & 1) == 0 ? a : b);
        reader.join(10000); assertFalse(reader.isAlive()); if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
