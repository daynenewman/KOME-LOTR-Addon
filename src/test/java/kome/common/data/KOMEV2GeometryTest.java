package kome.common.data;

import org.junit.Rule;
import org.junit.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

/** Approved V2 plus final 4,915-cell correction, preserving all pre-V2 territory. */
public class KOMEV2GeometryTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    static Map<Integer,String> edits() throws Exception {
        Map<Integer,String> edits = new HashMap<Integer,String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(KOMEV2GeometryTest.class.getClassLoader()
                .getResourceAsStream("kome/tile/gameplay-baseline/v2-approved-cells.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split("\t"); int x = Integer.parseInt(cells[0]), y = Integer.parseInt(cells[1]);
                assertTrue(x >= 0 && x < 3200 && y >= 0 && y < 4000);
                assertNull(edits.put(y * 3200 + x, cells[2]));
            }
        }
        assertEquals(93103, edits.size());
        Set<Integer> combined = new HashSet<Integer>(); int corrections = 0, gaps = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(KOMEV2GeometryTest.class.getClassLoader()
                .getResourceAsStream("kome/tile/gameplay-baseline/combined-approved-cells.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.split("\t"); int x = Integer.parseInt(f[0]), y = Integer.parseInt(f[1]), key = y * 3200 + x;
                assertTrue(x >= 0 && x < 3200 && y >= 0 && y < 4000);
                assertTrue("Duplicate combined edit", combined.add(key));
                if ("GAP".equals(f[3])) { assertNull(edits.get(key)); gaps++; }
                else { assertEquals("Unexpected original V2 assignment", f[3], edits.get(key)); corrections++; }
                edits.put(key, f[2]);
            }
        }
        assertEquals(4003, corrections); assertEquals(912, gaps); assertEquals(4915, combined.size());
        assertEquals(94015, edits.size()); return edits;
    }
    @Test public void exactApprovedMaskPreservesEveryPreviouslyAssignedPixel() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertEquals("ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7", hash(loader, KOMETileWorldResolver.MASK));
        assertEquals("a5cd6cf91b3fc1b662cffffde36a250a6e6687bcb8b2b7cd9aa06809b944b866", hash(loader, "kome/tile/gameplay-baseline/mask.png"));
        BufferedImage before = ImageIO.read(loader.getResource("kome/tile/gameplay-baseline/mask.png"));
        BufferedImage after = ImageIO.read(loader.getResource(KOMETileWorldResolver.MASK));
        assertEquals(before.getWidth(), after.getWidth()); assertEquals(before.getHeight(), after.getHeight());
        Map<Integer,String> edits = edits(); Map<Integer,String> colors = KOMEConquestTileDefaults.getTileIdsByColor();
        int changes = 0, dimension = KOMETileTestResources.dimension();
        for (int y = 0; y < 4000; y++) {
            int[] a = before.getRGB(0,y,3200,1,null,0,3200), b = after.getRGB(0,y,3200,1,null,0,3200);
            for (int x = 0; x < 3200; x++) {
                String id = edits.get(y * 3200 + x);
                if (id == null) { if (a[x] != b[x]) fail("Unapproved change at " + x + "," + y); continue; }
                assertTrue((a[x] >>> 24) <= 24); assertEquals(255, b[x] >>> 24); assertEquals(id, colors.get(b[x] & 0xFFFFFF));
                int wx = (x - 810) * 128, wz = (y - 730) * 128;
                for (int dx : new int[] {0,127}) for (int dz : new int[] {0,127})
                    assertEquals(id, KOMETileWorldResolver.INSTANCE.resolve(dimension, wx + dx, wz + dz).tileId);
                assertEquals(id, KOMETileWorldResolver.INSTANCE.resolveWorldPosition(dimension, Math.nextDown(wx+128D), Math.nextDown(wz+128D)).tileId);
                changes++;
            }
        }
        assertEquals(94015, changes);
    }
    @Test public void approvedCombinedBoundariesUseExactFractionalSampling() {
        int dimension = KOMETileTestResources.dimension();
        // Verified map-cell transitions: Rhun y=1412, Nindalf y=1138, land seam y=484.
        double[] x = {237248D, 73024D, -20032D};
        double[] z = {87296D, 52224D, -31488D};
        String[] north = {"T401", "T340", "T033"}, south = {"T442", "T351", "T035"};
        for (int i = 0; i < x.length; i++) {
            assertEquals(north[i], KOMETileWorldResolver.INSTANCE.resolveWorldPosition(dimension, x[i], Math.nextDown(z[i])).tileId);
            assertEquals(south[i], KOMETileWorldResolver.INSTANCE.resolveWorldPosition(dimension, x[i], z[i]).tileId);
            assertEquals(south[i], KOMETileWorldResolver.INSTANCE.resolveWorldPosition(dimension, x[i], Math.nextUp(z[i])).tileId);
        }
    }
    static String hash(ClassLoader loader, String resource) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try(InputStream in=loader.getResourceAsStream(resource)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        StringBuilder result=new StringBuilder();for(byte b:d.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
}
