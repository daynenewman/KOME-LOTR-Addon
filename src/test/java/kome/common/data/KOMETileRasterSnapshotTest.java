package kome.common.data;

import org.junit.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import static org.junit.Assert.*;
import static kome.common.data.KOMETileResolution.Status.*;

public class KOMETileRasterSnapshotTest {
    static final String MAPPING = "0,0,1=T001\n0,0,2=T002\n";
    static final Set<String> IDS = new HashSet<String>(Arrays.asList("T001", "T002"));

    static ByteArrayInputStream text(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    static ByteArrayInputStream png(int width, int height, int... argb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return png(image);
    }
    static ByteArrayInputStream png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        return new ByteArrayInputStream(bytes.toByteArray());
    }
    static KOMETileRasterSnapshot.Transform transform() { return new KOMETileRasterSnapshot.Transform(137, 1, 1, 4, 2, 1); }
    static KOMETileRasterSnapshot tiny(int first, int second) throws IOException {
        return KOMETileRasterSnapshot.load(png(2, 1, first, second), text(MAPPING), transform(), IDS, Collections.<String>emptySet());
    }

    @Test public void exactPixelGapsAlphaAndNoNeighborSearch() throws Exception {
        KOMETileRasterSnapshot s = tiny(0, 0xFF000001);
        assertEquals(IN_BOUNDS_GAP, s.resolve(137, -4, -4).status);
        assertEquals(IN_BOUNDS_GAP, s.resolve(137, -1, -4).status);
        assertEquals("T001", s.resolve(137, 0, -4).tileId);
        assertFalse(s.resolve(137, -1, -4).resolvedTileId().isPresent());
        assertFalse(s.resolve(137, 0, -4).capturable().isPresent());
        assertEquals(IN_BOUNDS_GAP, tiny(0x18000001, 0xFF000002).resolve(137, -4, -4).status);
        assertEquals("T001", tiny(0x19000001, 0xFF000002).resolve(137, -4, -4).tileId);
    }

    @Test public void floorAtNegativeAndPositiveBoundariesAndOutsideIsNeverClamped() throws Exception {
        KOMETileRasterSnapshot s = tiny(0xFF000001, 0xFF000002);
        for (int x : new int[] {-5, -4, -3, -1, 0, 1, 3, 4}) {
            KOMETileResolution r = s.resolve(137, x, -4);
            long expected = Math.floorDiv(x + 4, 4);
            assertEquals(expected, r.maskX);
            assertEquals(expected < 0 || expected >= 2 ? OUTSIDE_MASK : RESOLVED, r.status);
        }
        assertEquals(-1L, s.resolve(137, -5, -4).maskX);
        assertEquals(OUTSIDE_MASK, s.resolve(137, -4, -5).status);
        assertEquals(OUTSIDE_MASK, s.resolve(137, -4, 0).status);
        assertEquals(UNSUPPORTED_DIMENSION, s.resolve(0, 0, -4).status);
    }

    @Test public void rationalConversionMatchesIndependentBigIntegerOracleIncludingIntExtremes() throws Exception {
        KOMETileRasterSnapshot.Transform t = new KOMETileRasterSnapshot.Transform(137, -17, 23, 7, 3, 2);
        KOMETileRasterSnapshot s = KOMETileRasterSnapshot.load(png(2, 1, 0xFF000001, 0xFF000002), text(MAPPING), t, IDS, Collections.<String>emptySet());
        Random random = new Random(1977);
        for (int i = 0; i < 2000; i++) {
            int x = i == 0 ? Integer.MIN_VALUE : i == 1 ? Integer.MAX_VALUE : random.nextInt();
            int z = i == 0 ? Integer.MAX_VALUE : i == 1 ? Integer.MIN_VALUE : random.nextInt();
            KOMETileResolution r = s.resolve(137, x, z);
            assertEquals(floor(BigInteger.valueOf(x).add(BigInteger.valueOf(-119)).multiply(BigInteger.valueOf(2)), 21), r.maskX);
            assertEquals(floor(BigInteger.valueOf(z).add(BigInteger.valueOf(161)), 14), r.maskY);
        }
    }

    private static long floor(BigInteger numerator, long denominator) {
        BigInteger[] qr = numerator.divideAndRemainder(BigInteger.valueOf(denominator));
        return (qr[1].signum() < 0 ? qr[0].subtract(BigInteger.ONE) : qr[0]).longValueExact();
    }

    @Test public void overflowIsRejectedBeforePublication() throws Exception {
        try {
            KOMETileRasterSnapshot.load(png(3, 1, 0xFF000001, 0, 0), text(MAPPING),
                new KOMETileRasterSnapshot.Transform(137, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 2, 1), IDS, Collections.<String>emptySet());
            fail("Overflow must be rejected");
        } catch (ArithmeticException expected) { assertNotNull(expected.getMessage()); }
    }

    @Test public void missingAndMalformedResourcesFailVisibly() throws Exception {
        fails(null, text(MAPPING), "Missing tile mask");
        fails(png(2, 1, 0, 0), null, "Missing tile mapping");
        fails(text("not an image"), text(MAPPING), "readable image");
        fails(png(2, 1, 0xFF123456, 0), text(MAPPING), "Unknown opaque color");
        fails(png(2, 1, 0, 0), text("0,0,1=T999999\n"), "Unknown tile ID");
    }

    @Test public void duplicateColorsIdsAndMalformedMappingAreRejected() throws Exception {
        for (String mapping : new String[] {"", "0,0,1=\n", "0,0,1=bad-id\n", "256,0,1=T001\n", "-1,0,1=T001\n",
                "0,no,1=T001\n", "0,0=T001\n", "0,0,1=T001=extra\n", "0,0,1=T001\n0,0,1=T002\n",
                "0,0,1=T001\n0,0,1=T001\n", "0,0,1=T001\n0,0,2=T001\n"}) {
            fails(png(2, 1, 0, 0), text(mapping), "");
        }
        char[] oversized = new char[1024 * 1024 + 1]; Arrays.fill(oversized, '#');
        fails(png(2, 1, 0, 0), text(new String(oversized)), "byte limit");
        assertEquals("T001", KOMETileRasterSnapshot.readMapping(text(" 0,0,1= t001 \n")).get(1));
    }

    @Test public void retiredColorsUseExistingExplicitGapRule() throws Exception {
        KOMETileRasterSnapshot s = KOMETileRasterSnapshot.load(png(2, 1, 0xFF000001, 0xFF000002), text(MAPPING),
            transform(), Collections.singleton("T001"), Collections.singleton("T002"));
        assertEquals(IN_BOUNDS_GAP, s.resolve(137, 0, -4).status);
        assertEquals("T001", s.resolve(137, -4, -4).tileId);
    }

    @Test public void dimensionsAreBoundedBeforeAllocation() throws Exception {
        for (int[] shape : new int[][] {{0,1}, {-1,1}, {1,0}, {8193,1}, {8192,2049}, {Integer.MAX_VALUE,Integer.MAX_VALUE}}) {
            try { KOMETileRasterSnapshot.validateDimensions(shape[0], shape[1]); fail("Invalid dimensions"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("dimensions")); }
        }
        fails(png(new BufferedImage(8193, 1, BufferedImage.TYPE_BYTE_GRAY)), text("0,0,0=T001\n"), "dimensions");
    }

    @Test public void maximumAcceptedPixelBudgetActuallyDecodes() throws Exception {
        BufferedImage image = new BufferedImage(8192, 2048, BufferedImage.TYPE_BYTE_GRAY);
        KOMETileRasterSnapshot s = KOMETileRasterSnapshot.load(png(image), text("0,0,0=T001\n"),
            new KOMETileRasterSnapshot.Transform(137, 0, 0, 1, 8192, 2048), IDS, Collections.<String>emptySet());
        assertEquals(KOMETileRasterSnapshot.MAX_PIXELS, (long) s.width * s.height);
        assertEquals("T001", s.resolve(137, 8191, 2047).tileId);
        assertEquals(OUTSIDE_MASK, s.resolve(137, 8192, 2047).status);
    }

    @Test public void publishedArraysAndMappingsCannotBeMutatedByConsumers() throws Exception {
        Set<String> ids = new HashSet<String>(IDS);
        KOMETileRasterSnapshot s = KOMETileRasterSnapshot.load(png(2, 1, 0xFF000001, 0), text(MAPPING), transform(), ids, Collections.<String>emptySet());
        ids.clear();
        int[] rendering = s.copyArgbPixels(); rendering[0] = 0;
        assertEquals("T001", s.resolve(137, -4, -4).tileId);
        try { s.idsByColor().clear(); fail("Immutable map"); } catch (UnsupportedOperationException expected) { }
        try { s.colorsById().clear(); fail("Immutable map"); } catch (UnsupportedOperationException expected) { }
    }

    private static void fails(ByteArrayInputStream png, ByteArrayInputStream mapping, String message) throws Exception {
        try { KOMETileRasterSnapshot.load(png, mapping, transform(), IDS, Collections.<String>emptySet()); fail("Malformed resources accepted"); }
        catch (IOException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }
}
