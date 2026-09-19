package kome.common.data;

import org.junit.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

/** Reproducible, read-only audit of the shipped geography; observations never repair assets. */
public class KOMETileResourceAuditTest {
    @Test public void auditEveryProductionCellAndReportGeography() throws Exception {
        KOMETileRasterSnapshot snapshot = KOMETileTestResources.real();
        BufferedImage image = ImageIO.read(getClass().getClassLoader().getResource(KOMETileWorldResolver.MASK));
        int w = image.getWidth(), h = image.getHeight();
        assertEquals(3200, w); assertEquals(4000, h);
        assertEquals(w, snapshot.transform.mapWidth); assertEquals(h, snapshot.transform.mapHeight);
        // Independently checked LOTR v36.15 origin and power-of-two scale.
        assertEquals(810, snapshot.transform.originX); assertEquals(730, snapshot.transform.originZ);
        assertEquals(128, snapshot.transform.scale);
        Map<Integer, String> all;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(KOMETileWorldResolver.MAPPING)) {
            all = KOMETileRasterSnapshot.readMapping(input);
        }
        Set<String> retired = KOMEConquestTileDefaults.getRetiredTileIds();
        Map<Integer, String> active = KOMEConquestTileDefaults.getTileIdsByColor();
        // Cross-check ID references without loading or modifying WorldData ownership.
        Set<String> metadataIds = new HashSet<String>();
        String ownership = "assets/kome/config/kome_tile_ownership_defaults.csv";
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(ownership), StandardCharsets.UTF_8))) {
            assertTrue(reader.readLine().startsWith("tile_id,"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String id = KOMEConquestTile.normalizeId(line.substring(0, line.indexOf(',')));
                assertTrue("Duplicate ownership metadata ID: " + id, metadataIds.add(id));
                assertTrue("Unknown ownership metadata ID: " + id, all.containsValue(id) || retired.contains(id));
            }
        }
        Set<String> missingMetadata = new TreeSet<String>(active.values());
        missingMetadata.removeAll(metadataIds);
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        image = null;
        SortedMap<String, Stats> stats = new TreeMap<String, Stats>();
        stats.put("GAP", new Stats());
        for (String id : active.values()) stats.put(id, new Stats());
        long transparent = 0, retiredPixels = 0, partialAlpha = 0, transitions = 0, gapTransitions = 0;
        List<String> examples = new ArrayList<String>();
        for (int p = 0; p < pixels.length; p++) {
            int argb = pixels[p], alpha = argb >>> 24, rgb = argb & 0xFFFFFF;
            String id = null;
            if (alpha <= 24) transparent++;
            else {
                if (alpha != 255) partialAlpha++;
                if (!all.containsKey(rgb)) fail("Unmapped nontransparent RGB at " + p % w + "," + p / w);
                id = active.get(rgb);
                if (id == null) { assertTrue(retired.contains(all.get(rgb))); retiredPixels++; }
            }
            KOMETileResolution resolved = snapshot.resolve(snapshot.transform.dimension,
                (p % w - 810) * 128, (p / w - 730) * 128);
            if (id == null) {
                assertEquals(KOMETileResolution.Status.IN_BOUNDS_GAP, resolved.status);
                assertEquals("", resolved.tileId);
                pixels[p] = 0;
            } else {
                assertEquals(KOMETileResolution.Status.RESOLVED, resolved.status);
                assertEquals(id, resolved.tileId);
                pixels[p] = rgb + 1; // Reserve zero for gaps, including if an active color is RGB black.
            }
            Stats s = stats.get(id == null ? "GAP" : id);
            s.cells++;
            if (s.first < 0) s.first = p;
            if (p % w == 0 || p % w == w - 1 || p < w || p >= pixels.length - w) s.edgeCells++;
            if (p % w > 0 && pixels[p] != pixels[p-1]) {
                transitions++;
                if (pixels[p] == 0 || pixels[p-1] == 0) gapTransitions++;
                if (examples.size() < 8 && pixels[p] != 0 && pixels[p-1] != 0)
                    examples.add(active.get(pixels[p-1]-1)+" -> "+id+" at mask ("+p%w+","+p/w+") / world ("+(p%w-810)*128+","+(p/w-730)*128+")");
            }
            if (p >= w && pixels[p] != pixels[p-w]) { transitions++; if (pixels[p] == 0 || pixels[p-w] == 0) gapTransitions++; }
        }
        BitSet visited = new BitSet(pixels.length);
        int[] queue = new int[pixels.length];
        Path output = Paths.get("build/reports/tile-resource-audit");
        Files.createDirectories(output);
        List<String> components = new ArrayList<String>();
        components.add("tile,cells,first_mask_x,first_mask_y,world_x,world_z,min_x,min_y,max_x,max_y,touches_edge");
        long enclosedGaps = 0, singleton8 = 0;
        for (int first = 0; first < pixels.length; first++) {
            if (visited.get(first)) continue;
            int color = pixels[first], head = 0, tail = 1;
            queue[0] = first; visited.set(first);
            int minX = w, minY = h, maxX = -1, maxY = -1;
            while (head < tail) {
                int p = queue[head++], x = p % w, y = p / w;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                if (x > 0) tail = add(p-1, color, pixels, visited, queue, tail);
                if (x+1 < w) tail = add(p+1, color, pixels, visited, queue, tail);
                if (y > 0) tail = add(p-w, color, pixels, visited, queue, tail);
                if (y+1 < h) tail = add(p+w, color, pixels, visited, queue, tail);
            }
            String id = color == 0 ? "GAP" : active.get(color-1);
            Stats s = stats.get(id); s.components++; s.largest = Math.max(s.largest, tail);
            boolean edge = minX == 0 || minY == 0 || maxX == w-1 || maxY == h-1;
            if (color == 0 && !edge) enclosedGaps++;
            if (tail == 1) {
                s.singletons++;
                boolean isolated = true;
                for (int dy=-1; dy<=1; dy++) for (int dx=-1; dx<=1; dx++) {
                    int x=first%w+dx, y=first/w+dy;
                    if ((dx!=0 || dy!=0) && x>=0 && x<w && y>=0 && y<h && pixels[y*w+x]==color) isolated=false;
                }
                if (color != 0 && isolated) singleton8++;
            }
            components.add(id+","+tail+","+first%w+","+first/w+","+(first%w-810)*128+","+(first/w-730)*128+","+minX+","+minY+","+maxX+","+maxY+","+edge);
        }
        List<String> table = new ArrayList<String>();
        table.add("tile,cells,components_4,largest_component,single_pixel_components_4,edge_cells,first_mask_x,first_mask_y");
        List<String> missing = new ArrayList<String>(), disconnected = new ArrayList<String>();
        long assigned = 0;
        for (Map.Entry<String, Stats> entry : stats.entrySet()) {
            String id=entry.getKey(); Stats s=entry.getValue();
            table.add(id+","+s.cells+","+s.components+","+s.largest+","+s.singletons+","+s.edgeCells+","+(s.first<0 ? -1 : s.first%w)+","+(s.first<0 ? -1 : s.first/w));
            if (!id.equals("GAP")) {
                assigned+=s.cells;
                if (s.cells==0) missing.add(id);
                if (s.components>1) disconnected.add(id+" ("+s.components+")");
            }
        }
        assertEquals((long)w*h, assigned+transparent+retiredPixels);
        Files.write(output.resolve("tiles.csv"), table, StandardCharsets.UTF_8);
        Files.write(output.resolve("components.csv"), components, StandardCharsets.UTF_8);
        List<String> report = Arrays.asList("# Production tile raster audit", "",
            "Mask SHA-256: `"+hash(KOMETileWorldResolver.MASK)+"`",
            "Mapping SHA-256: `"+hash(KOMETileWorldResolver.MAPPING)+"`",
            "LOTR map SHA-256: `"+hash("assets/lotr/map/map.png")+"`", "",
            "Dimensions: "+w+"x"+h+"; cells: "+pixels.length+"; mapping IDs: "+all.size()+"; active: "+active.size()+"; retired exclusions: "+retired.size()+".",
            "Assigned cells: "+assigned+"; transparent cells (alpha <= 24): "+transparent+"; retired-colored cells: "+retiredPixels+"; nontransparent partial-alpha cells: "+partialAlpha+".",
            "Every cell matched the integer resolver against independent source-image identity. Unknown opaque colors / invalid active IDs: zero (asserted).",
            "Ownership metadata rows: "+metadataIds.size()+"; duplicate/unknown IDs: zero (asserted); active IDs without metadata: "+missingMetadata+". SHA-256: `"+hash(ownership)+"`.",
            "Active IDs without coverage: "+missing+".",
            "Disconnected active IDs under four-neighbor connectivity: "+disconnected.size()+"; "+disconnected+".",
            "Active one-pixel islands with no same-ID eight-neighbor: "+singleton8+".",
            "Gap components: "+stats.get("GAP").components+"; enclosed four-neighbor gap components: "+enclosedGaps+".",
            "Differing horizontal/vertical cell pairs: "+transitions+"; involving gaps: "+gapTransitions+".", "",
            "Representative horizontal assigned/assigned boundaries (right cell's inclusive lower world corner): "+examples, "",
            "See tiles.csv and components.csv for every count, bounding box and representative world coordinate. Connectivity is an audit measurement, not gameplay adjacency. Disconnection, islands, missing coverage and enclosed gaps are geographic observations, not automatic errors; no asset is modified.");
        Files.write(output.resolve("audit.md"), report, StandardCharsets.UTF_8);
    }
    private static int add(int p, int color, int[] pixels, BitSet visited, int[] queue, int tail) {
        if (!visited.get(p) && pixels[p] == color) { visited.set(p); queue[tail++] = p; }
        return tail;
    }
    private String hash(String resource) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=getClass().getClassLoader().getResourceAsStream(resource)) {
            byte[] bytes=new byte[8192]; int count;
            while((count=in.read(bytes))!=-1) digest.update(bytes,0,count);
        }
        StringBuilder hex=new StringBuilder(); for(byte b:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",b & 255));
        return hex.toString();
    }
    private static final class Stats { long cells; int first=-1, components, largest, singletons, edgeCells; }
}
