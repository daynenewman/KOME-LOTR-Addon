package kome.common.data;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Fully validated, immutable derived raster. All arrays are private and exclusively owned. */
public final class KOMETileRasterSnapshot {
    public static final int MAX_DIMENSION = 8192;
    public static final int MAX_PIXELS = 16 * 1024 * 1024;
    private static final int MAX_MAPPING_BYTES = 1024 * 1024;
    private static final int MAX_TILE_COLORS = 65535;
    // Existing mask/renderer rule: alpha <= 24 is transparent for tile selection.
    private static final int TRANSPARENT_ALPHA = 24;

    public final int width;
    public final int height;
    public final Transform transform;
    // Low 16 bits: palette index (0 = gap). High byte: original alpha for existing textures.
    private final int[] cells;
    private final String[] tileIds;
    private final int[] colors;
    private final Map<Integer, String> idsByColor;
    private final Map<String, Integer> colorsById;

    private KOMETileRasterSnapshot(int width, int height, Transform transform, int[] cells,
            String[] tileIds, int[] colors, Map<Integer, String> idsByColor) {
        this.width = width;
        this.height = height;
        this.transform = transform;
        this.cells = cells;
        this.tileIds = tileIds;
        this.colors = colors;
        this.idsByColor = Collections.unmodifiableMap(new LinkedHashMap<Integer, String>(idsByColor));
        Map<String, Integer> reverse = new HashMap<String, Integer>();
        for (Map.Entry<Integer, String> entry : idsByColor.entrySet()) reverse.put(entry.getValue(), entry.getKey());
        colorsById = Collections.unmodifiableMap(reverse);
    }

    /** Caller owns the streams. Known/retired IDs are consulted during construction, never retained. */
    public static KOMETileRasterSnapshot load(InputStream imageInput, InputStream mappingInput,
            Transform transform, Set<String> knownIds, Set<String> retiredIds) throws IOException {
        if (imageInput == null) throw new IOException("Missing tile mask resource");
        if (mappingInput == null) throw new IOException("Missing tile mapping resource");
        if (transform == null || knownIds == null || retiredIds == null) {
            throw new IllegalArgumentException("Transform and existing tile authority are required");
        }
        Map<Integer, String> mapping = readMapping(mappingInput);
        Map<Integer, String> active = new LinkedHashMap<Integer, String>();
        for (Map.Entry<Integer, String> entry : mapping.entrySet()) {
            String id = entry.getValue();
            if (retiredIds.contains(id)) continue; // Existing authority explicitly excludes these colors.
            if (!knownIds.contains(id)) throw new IOException("Unknown tile ID: " + id);
            active.put(entry.getKey(), id);
        }
        if (active.isEmpty()) throw new IOException("Tile mapping has no active IDs");

        try (ImageInputStream input = new MemoryCacheImageInputStream(imageInput)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Tile mask is not a readable image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!"png".equalsIgnoreCase(reader.getFormatName())) throw new IOException("Tile mask must be PNG");
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height); // Before image decode or raster allocation.
                transform.validateFor(width, height);
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != width || image.getHeight() != height) {
                    throw new IOException("Tile mask dimensions changed during decode");
                }
                String[] ids = new String[active.size() + 1];
                int[] colors = new int[ids.length];
                Map<Integer, Integer> palette = new HashMap<Integer, Integer>();
                int index = 1;
                for (Map.Entry<Integer, String> entry : active.entrySet()) {
                    ids[index] = entry.getValue();
                    colors[index] = entry.getKey();
                    palette.put(entry.getKey(), index++);
                }
                int[] cells = new int[Math.multiplyExact(width, height)];
                int[] row = new int[width];
                for (int y = 0; y < height; y++) {
                    image.getRGB(0, y, width, 1, row, 0, width);
                    for (int x = 0; x < width; x++) {
                        int argb = row[x];
                        if ((argb >>> 24) <= TRANSPARENT_ALPHA) continue;
                        int rgb = argb & 0xFFFFFF;
                        Integer cell = palette.get(rgb);
                        if (cell == null) {
                            String mapped = mapping.get(rgb);
                            if (mapped != null && retiredIds.contains(mapped)) continue;
                            throw new IOException("Unknown opaque color " + Integer.toHexString(rgb) + " at " + x + "," + y);
                        }
                        cells[y * width + x] = (argb & 0xFF000000) | cell;
                    }
                }
                return new KOMETileRasterSnapshot(width, height, transform, cells, ids, colors, active);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Shared strict parser; KOMEConquestTileDefaults remains the packaged identity authority. */
    static Map<Integer, String> readMapping(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing tile mapping resource");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_MAPPING_BYTES) throw new IOException("Tile mapping exceeds byte limit");
            bytes.write(buffer, 0, count);
        }
        Map<Integer, String> mapping = new LinkedHashMap<Integer, String>();
        Set<String> ids = new HashSet<String>();
        BufferedReader reader = new BufferedReader(new StringReader(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("=", -1);
            if (parts.length != 2) throw new IOException("Invalid tile mapping at line " + lineNumber);
            String[] rgb = parts[0].split(",", -1);
            String id = KOMEConquestTile.normalizeId(parts[1]);
            if (rgb.length != 3 || !KOMEConquestTile.isCanonicalTileId(id)) {
                throw new IOException("Invalid color or tile ID at line " + lineNumber);
            }
            int color = 0;
            for (String component : rgb) {
                int value;
                try { value = Integer.parseInt(component.trim()); }
                catch (NumberFormatException e) { throw new IOException("Invalid RGB at line " + lineNumber, e); }
                if (value < 0 || value > 255) throw new IOException("RGB outside 0..255 at line " + lineNumber);
                color = (color << 8) | value;
            }
            if (mapping.containsKey(color)) throw new IOException("Duplicate tile color at line " + lineNumber);
            if (!ids.add(id)) throw new IOException("Duplicate tile mapping for " + id);
            if (mapping.size() >= MAX_TILE_COLORS) throw new IOException("Too many tile colors");
            mapping.put(color, id);
        }
        if (mapping.isEmpty()) throw new IOException("Empty tile mapping");
        return Collections.unmodifiableMap(mapping);
    }

    static void validateDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_PIXELS) throw new IOException("Unsupported raster dimensions: " + width + "x" + height);
    }

    public KOMETileResolution resolve(int dimension, int worldX, int worldZ) {
        if (dimension != transform.dimension) {
            return KOMETileResolution.unavailable(KOMETileResolution.Status.UNSUPPORTED_DIMENSION,
                dimension, worldX, worldZ, "Only the configured Middle-earth dimension is supported");
        }
        long x = transform.maskCoordinate(worldX, transform.originX, width, transform.mapWidth);
        long y = transform.maskCoordinate(worldZ, transform.originZ, height, transform.mapHeight);
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return result(KOMETileResolution.Status.OUTSIDE_MASK, worldX, worldZ, x, y, "", "Outside authoritative mask coverage");
        }
        int palette = cells[(int) y * width + (int) x] & 0xFFFF;
        return palette == 0
            ? result(KOMETileResolution.Status.IN_BOUNDS_GAP, worldX, worldZ, x, y, "", "Exact cell is a gap")
            : result(KOMETileResolution.Status.RESOLVED, worldX, worldZ, x, y, tileIds[palette], "Exact cell; capturability unknown");
    }

    private KOMETileResolution result(KOMETileResolution.Status status, int x, int z, long mx, long my, String id, String reason) {
        return new KOMETileResolution(status, transform.dimension, x, z, true, mx, my, true, id, reason);
    }

    /** A detached rendering copy, not an alternative lookup authority. */
    public int[] copyArgbPixels() {
        int[] result = new int[cells.length];
        for (int i = 0; i < result.length; i++) {
            int cell = cells[i];
            result[i] = (cell & 0xFF000000) | colors[cell & 0xFFFF];
        }
        return result;
    }

    public Map<Integer, String> idsByColor() { return idsByColor; }
    public Map<String, Integer> colorsById() { return colorsById; }

    /** Values captured from the existing LOTR transform/dimension owners at load time. */
    public static final class Transform {
        public final int dimension, originX, originZ, scale, mapWidth, mapHeight;

        public Transform(int dimension, int originX, int originZ, int scale, int mapWidth, int mapHeight) {
            if (scale <= 0 || mapWidth <= 0 || mapHeight <= 0) throw new IllegalArgumentException("Invalid LOTR transform dimensions/scale");
            this.dimension = dimension;
            this.originX = originX;
            this.originZ = originZ;
            this.scale = scale;
            this.mapWidth = mapWidth;
            this.mapHeight = mapHeight;
        }

        private void validateFor(int width, int height) {
            // Prove every supported int input safe, before publishing the affine transform.
            for (int bound : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                maskCoordinate(bound, originX, width, mapWidth);
                maskCoordinate(bound, originZ, height, mapHeight);
            }
        }

        private long maskCoordinate(int world, int origin, int maskSize, int mapSize) {
            long shifted = Math.addExact((long) world, Math.multiplyExact((long) origin, scale));
            long numerator = Math.multiplyExact(shifted, maskSize);
            long denominator = Math.multiplyExact((long) scale, mapSize);
            return Math.floorDiv(numerator, denominator);
        }
    }
}
