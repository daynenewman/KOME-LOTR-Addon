package kome.common.data;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMETileResolution.Status.*;

/** Locks the approved, bounded authoring delta; it introduces no runtime assignment rule. */
public class KOMEWeathertopPilotTest {
    private static final String FIXTURE = "kome/tile/weathertop-pilot/";
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void historicalPilotBaselineDiffIsExactlyTheApproved95CellManifest() throws Exception {
        assertEquals("3ee80b95947f99ca4bdc355c0acf3dc832d41e964b5fe54e880f35665068e6d1", hash(FIXTURE + "before.png"));
        assertEquals("a5cd6cf91b3fc1b662cffffde36a250a6e6687bcb8b2b7cd9aa06809b944b866", hash("kome/tile/gameplay-baseline/mask.png"));
        BufferedImage before = image(FIXTURE + "before.png");
        BufferedImage after = image("kome/tile/gameplay-baseline/mask.png");
        assertEquals(3200, after.getWidth()); assertEquals(4000, after.getHeight());
        assertEquals(before.getWidth(), after.getWidth()); assertEquals(before.getHeight(), after.getHeight());
        Map<Integer, String> edits = edits();
        Map<Integer, String> colors = KOMEConquestTileDefaults.getTileIdsByColor();
        int changed = 0, t149 = 0, t132 = 0;
        for (int y = 0; y < after.getHeight(); y++) {
            int[] oldRow = before.getRGB(0, y, 3200, 1, null, 0, 3200);
            int[] newRow = after.getRGB(0, y, 3200, 1, null, 0, 3200);
            for (int x = 0; x < 3200; x++) {
                String expected = edits.get(y * 3200 + x);
                if (expected == null) {
                    if (oldRow[x] != newRow[x]) fail("Unlisted pixel changed at " + x + "," + y);
                } else {
                    assertTrue("Only unassigned transparent cells may change", (oldRow[x] >>> 24) <= 24);
                    assertNotEquals(oldRow[x], newRow[x]); assertEquals(255, newRow[x] >>> 24);
                    assertEquals(expected, colors.get(newRow[x] & 0xFFFFFF));
                    changed++; if (expected.equals("T149")) t149++; else t132++;
                }
            }
        }
        assertEquals(95, changed); assertEquals(52, t149); assertEquals(43, t132);
        // Every other RGBA cell is unchanged, including all assigned territory, R1 and junctions.
    }

    @Test public void everyApprovedCellResolvesThroughProductionAtAllFourBlockCorners() throws Exception {
        KOMETileWorldResolver resolver = KOMETileWorldResolver.INSTANCE;
        int dimension = KOMETileTestResources.dimension();
        for (Map.Entry<Integer, String> edit : edits().entrySet()) {
            int x = edit.getKey() % 3200, y = edit.getKey() / 3200;
            // Independently documented LOTR world bounds, not the resolver's transform helper.
            int worldX = (x - 810) * 128, worldZ = (y - 730) * 128;
            for (int dx : new int[] {0, 127}) for (int dz : new int[] {0, 127}) {
                KOMETileResolution result = resolver.resolve(dimension, worldX + dx, worldZ + dz);
                assertEquals(RESOLVED, result.status); assertEquals(edit.getValue(), result.tileId);
                assertEquals(x, result.maskX); assertEquals(y, result.maskY);
            }
            assertEquals(edit.getValue(), resolver.resolveWorldPosition(dimension,
                Math.nextDown((double) worldX + 128), Math.nextDown((double) worldZ + 128)).tileId);
        }
    }

    @Test public void correctedBoundaryUsesExactFractionalFloorOnBothWorldAndMapPaths() {
        KOMETileWorldResolver resolver = KOMETileWorldResolver.INSTANCE;
        int d = KOMETileTestResources.dimension();
        // Approved (974,727)/(975,727) meet at X=21120, Z in [-384,-256).
        double[] positions = {21119.5, Math.nextDown(21120D), 21120D, Math.nextUp(21120D), 21120.5};
        String[] expected = {"T149", "T149", "T132", "T132", "T132"};
        for (int i = 0; i < positions.length; i++) {
            KOMETileResolution result = resolver.resolveWorldPosition(d, positions[i], -383.5);
            assertEquals(RESOLVED, result.status); assertEquals(expected[i], result.tileId);
            assertEquals(i < 2 ? 974 : 975, result.maskX); assertEquals(727, result.maskY);
        }
        assertEquals("T149", resolver.resolveMapPosition(d, Math.nextDown(975D), 727.5).tileId);
        assertEquals("T132", resolver.resolveMapPosition(d, 975D, 727.5).tileId);
        assertEquals("T132", resolver.resolveMapPosition(d, Math.nextUp(975D), 727.5).tileId);
    }

    @Test public void junctionChangesFollowApprovedV2ManifestAndProtectedControlsRemainUnassigned() throws Exception {
        KOMETileWorldResolver resolver = KOMETileWorldResolver.INSTANCE;
        int d = KOMETileTestResources.dimension(), count = 0;
        Map<Integer, String> approvedV2 = KOMEV2GeometryTest.edits();
        try (BufferedReader reader = reader("excluded-junction-cells.csv")) {
            reader.readLine(); String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(","); int x = Integer.parseInt(cells[0]), y = Integer.parseInt(cells[1]);
                KOMETileResolution result = resolver.resolve(d, (x - 810) * 128, (y - 730) * 128);
                String approved = approvedV2.get(y * 3200 + x);
                if (approved == null) assertEquals(IN_BOUNDS_GAP, result.status);
                else { assertEquals(RESOLVED, result.status); assertEquals(approved, result.tileId); }
                count++;
            }
        }
        assertEquals(14, count);
        assertEquals(IN_BOUNDS_GAP, resolver.resolve(d, 34944, 640).status); // R1
        assertEquals(IN_BOUNDS_GAP, resolver.resolve(d, 189568, -86016).status); // (2291,58)
        assertEquals("T001", resolver.resolve(d, 189696, -86016).tileId);
        assertEquals(UNSUPPORTED_DIMENSION, resolver.resolve(d + 1, 21120, -384).status);
    }

    private Map<Integer, String> edits() throws Exception {
        Map<Integer, String> result = new LinkedHashMap<Integer, String>();
        try (BufferedReader reader = reader("approved-cells.tsv")) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split("\t");
                int x = Integer.parseInt(cells[0]), y = Integer.parseInt(cells[1]);
                assertTrue(cells[2].equals("T149") || cells[2].equals("T132"));
                assertNull(result.put(y * 3200 + x, cells[2]));
            }
        }
        assertEquals(95, result.size()); return result;
    }
    private BufferedImage image(String resource) throws IOException {
        try (InputStream input = stream(resource)) { return ImageIO.read(input); }
    }
    private BufferedReader reader(String name) { return new BufferedReader(new InputStreamReader(stream(FIXTURE + name), StandardCharsets.UTF_8)); }
    private InputStream stream(String resource) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(resource, input); return input;
    }
    private String hash(String resource) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = stream(resource)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
}
