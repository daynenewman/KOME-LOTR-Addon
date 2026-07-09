package kome.common.data;

import lotr.common.LOTRDimension;
import lotr.common.world.genlayer.LOTRGenLayerWorld;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KOMEConquestTileDefaults {
    private static final String TILE_ID_MASK = "assets/kome/map/reset_conquest_tile_ids.png";
    private static final String TILE_ID_MAP = "assets/kome/map/reset_conquest_tile_ids.txt";
    private static final String MAP_OVERLAY = "assets/kome/map/reset_conquest_overlay.png";
    private static final String MAP_ROADS = "assets/kome/map/reset_conquest_roads.png";
    private static final String MAP_BRIDGE_MARKERS = "assets/kome/map/reset_conquest_bridges.png";
    private static final double DEFAULT_Y = 80.0D;
    private static final int ADJACENCY_SCAN_GAP = 3;
    private static final int RIVER_BORDER_TANGENT_RADIUS = 2;
    private static final int RIVER_BORDER_MIN_WATER_PIXELS = 3;
    private static final int BRIDGE_RESOLVER_RIVER_SEARCH_RADIUS = 6;
    private static final int BRIDGE_BORDER_NORMAL_RADIUS = 5;
    private static final int BRIDGE_BORDER_TANGENT_RADIUS = 2;
    private static final int BRIDGE_BORDER_MIN_ROAD_PIXELS = 6;
    private static final int BRIDGE_MARKER_MERGE_PIXELS = 1;
    private static final int BRIDGE_DOT_MAX_PIXELS = 18;
    private static final int BRIDGE_DOT_MAX_WIDTH = 7;
    private static final int BRIDGE_DOT_MAX_HEIGHT = 7;
    private static final int BRIDGE_DOT_WATER_RADIUS = 3;
    private static final int BRIDGE_TILE_PAIR_SEARCH_RADIUS = 22;
    private static final int EXPLICIT_BRIDGE_TILE_PAIR_SEARCH_RADIUS = 40;
    private static final Map<String, TileCenter> tileCenters = new HashMap<String, TileCenter>();
    private static final Map<String, Set<String>> tileAdjacency = new HashMap<String, Set<String>>();
    private static final Map<String, EdgeStats> automaticEdgeStats = new HashMap<String, EdgeStats>();
    private static final Map<String, EdgeStats> bridgeResolverEdgeStats = new HashMap<String, EdgeStats>();
    private static final String[][] EXPLICIT_OPEN_EDGES = new String[][] {
        {"T133", "T141"},
        {"T057", "T068"},
        {"T068", "T079"},
        {"T239", "T232"},
        {"T232", "T220"},
        {"T220", "T223"},
        {"T223", "T233"},
        {"T035", "T042"},
        {"T070", "T089"},
        {"T096", "T101"},
        {"T096", "T108"},
        {"T488", "T487"},
        {"T496", "T487"},
        {"T160", "T179"},
        {"T179", "T180"},
        {"T180", "T176"},
        {"T176", "T181"},
        {"T181", "T168"},
        {"T190", "T212"},
        {"T194", "T212"},
        {"T353", "T371"},
        {"T389", "T408"},
        {"T389", "T398"},
        {"T410", "T416"},
        {"T111", "T128"},
        {"T086", "T115"},
        {"T408", "T421"},
        {"T415", "T425"}
    };
    private static final String[][] EXPLICIT_BRIDGE_EDGE_REMAPS = new String[][] {
        {"T469", "T475", "T470", "T472"},
        {"T472", "T475", "T470", "T472"}
    };
    private static final String[][] EXPLICIT_REMOVED_EDGES = new String[][] {
        {"T233", "T232"},
        {"T108", "T101"},
        {"T035", "T050"},
        {"T089", "T119"},
        {"T054", "T062"}
    };
    private static final Set<String> RETIRED_TILE_IDS = new HashSet<String>();
    private static Map<Integer, String> cachedIdsByColor;
    private static BufferedImage cachedTileIdImage;
    private static boolean loaded;

    public static TileCenter getTileCenter(String tileId) {
        ensureLoaded();
        return tileCenters.get(KOMEConquestTile.normalizeId(tileId));
    }

    public static String getTileIdAtMapPosition(double mapX, double mapZ) {
        ensureLoaded();
        try {
            if (cachedIdsByColor == null || cachedTileIdImage == null || !LOTRGenLayerWorld.loadedBiomeImage()) {
                return "";
            }
            int x = (int) Math.round(mapX * cachedTileIdImage.getWidth() / (double) LOTRGenLayerWorld.imageWidth);
            int y = (int) Math.round(mapZ * cachedTileIdImage.getHeight() / (double) LOTRGenLayerWorld.imageHeight);
            return getNearestTileIdAtPixel(cachedIdsByColor, cachedTileIdImage, x, y, 8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String getNearestTileIdAtPixel(Map<Integer, String> idsByColor, BufferedImage image, int x, int y, int radius) {
        if (idsByColor == null || image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return "";
        }
        int clampedX = Math.max(0, Math.min(image.getWidth() - 1, x));
        int clampedY = Math.max(0, Math.min(image.getHeight() - 1, y));
        String exact = tileIdAtPixel(idsByColor, image, clampedX, clampedY);
        if (exact.length() > 0) {
            return exact;
        }
        for (int distance = 1; distance <= radius; distance++) {
            for (int sampleY = Math.max(0, clampedY - distance); sampleY <= Math.min(image.getHeight() - 1, clampedY + distance); sampleY++) {
                for (int sampleX = Math.max(0, clampedX - distance); sampleX <= Math.min(image.getWidth() - 1, clampedX + distance); sampleX++) {
                    if (Math.abs(sampleX - clampedX) != distance && Math.abs(sampleY - clampedY) != distance) {
                        continue;
                    }
                    String found = tileIdAtPixel(idsByColor, image, sampleX, sampleY);
                    if (found.length() > 0) {
                        return found;
                    }
                }
            }
        }
        return "";
    }

    private static String tileIdAtPixel(Map<Integer, String> idsByColor, BufferedImage image, int x, int y) {
        int argb = image.getRGB(x, y);
        if ((argb >>> 24) <= 24) {
            return "";
        }
        String tileId = idsByColor.get(argb & 0xFFFFFF);
        return tileId == null ? "" : KOMEConquestTile.normalizeId(tileId);
    }

    public static Set<String> getAdjacentTiles(String tileId) {
        ensureLoaded();
        Set<String> adjacent = tileAdjacency.get(KOMEConquestTile.normalizeId(tileId));
        return adjacent == null ? Collections.<String>emptySet() : new HashSet<String>(adjacent);
    }

    public static Set<String> getKnownTileIds() {
        ensureLoaded();
        Set<String> ids = new HashSet<String>(tileCenters.keySet());
        ids.addAll(tileAdjacency.keySet());
        ids.removeAll(RETIRED_TILE_IDS);
        return ids;
    }

    public static boolean isRetiredTile(String tileId) {
        ensureRetiredTilesLoaded();
        return RETIRED_TILE_IDS.contains(KOMEConquestTile.normalizeId(tileId));
    }

    public static KOMEConquestRouteEdge getAutomaticRouteEdge(String tileA, String tileB) {
        ensureLoaded();
        String key = KOMEConquestRouteEdge.key(tileA, tileB);
        EdgeStats stats = automaticEdgeStats.get(key);
        if (stats == null) {
            return null;
        }
        String edgeType = stats.isBridge() ? KOMEConquestRouteEdge.BRIDGE
            : stats.isRiver() ? KOMEConquestRouteEdge.RIVER : KOMEConquestRouteEdge.OPEN;
        KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge(tileA, tileB, edgeType);
        edge.name = stats.isBridge() ? "Automatic bridge crossing"
            : stats.isRiver() ? "Automatic river crossing" : "";
        return edge;
    }

    public static Set<KOMEConquestRouteEdge> getAutomaticBridgeEdges() {
        ensureLoaded();
        Set<KOMEConquestRouteEdge> bridges = new HashSet<KOMEConquestRouteEdge>();
        for (Map.Entry<String, EdgeStats> entry : automaticEdgeStats.entrySet()) {
            EdgeStats stats = entry.getValue();
            if (stats == null || !stats.isBridge()) {
                continue;
            }
            String[] tiles = entry.getKey().split("\\|");
            if (tiles.length != 2) {
                continue;
            }
            KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge(tiles[0], tiles[1], KOMEConquestRouteEdge.BRIDGE);
            edge.name = "Automatic bridge crossing";
            edge.manual = false;
            bridges.add(edge);
        }
        return bridges;
    }

    public static List<AutomaticBridgeMarker> getAutomaticBridgeMarkers() {
        ensureLoaded();
        List<AutomaticBridgeMarker> bridges = new ArrayList<AutomaticBridgeMarker>();
        for (Map.Entry<String, EdgeStats> entry : automaticEdgeStats.entrySet()) {
            EdgeStats stats = entry.getValue();
            if (stats == null || !stats.isBridge()) {
                continue;
            }
            String[] tiles = entry.getKey().split("\\|");
            if (tiles.length != 2) {
                continue;
            }
            for (BridgePixel pixel : stats.bridgePixels) {
                bridges.add(new AutomaticBridgeMarker(tiles[0], tiles[1], LOTRDimension.MIDDLE_EARTH.dimensionID,
                    imageToWorldX(pixel.x, pixel.imageWidth), DEFAULT_Y, imageToWorldZ(pixel.y, pixel.imageHeight),
                    pixel.x, pixel.y));
            }
        }
        return bridges;
    }

    public static int getAutomaticBridgeMarkerCount() {
        return getAutomaticBridgeMarkers().size();
    }

    public static List<AutomaticRiverBlockerMarker> getAutomaticRiverBlockerMarkers() {
        ensureLoaded();
        List<AutomaticRiverBlockerMarker> blockers = new ArrayList<AutomaticRiverBlockerMarker>();
        for (Map.Entry<String, EdgeStats> entry : automaticEdgeStats.entrySet()) {
            EdgeStats stats = entry.getValue();
            if (stats == null || !stats.isRiver() || stats.isBridge() || !stats.hasRiverMarker()) {
                continue;
            }
            String[] tiles = entry.getKey().split("\\|");
            if (tiles.length != 2) {
                continue;
            }
            int imageX = stats.riverMarkerX();
            int imageY = stats.riverMarkerY();
            blockers.add(new AutomaticRiverBlockerMarker(tiles[0], tiles[1], LOTRDimension.MIDDLE_EARTH.dimensionID,
                imageToWorldX(imageX, stats.riverMarkerImageWidth()), DEFAULT_Y,
                imageToWorldZ(imageY, stats.riverMarkerImageHeight()), imageX, imageY));
        }
        return blockers;
    }

    private static void ensureLoaded() {
        ensureRetiredTilesLoaded();
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Map<Integer, String> idsByColor = loadTileIds();
            InputStream input = KOMEConquestTileDefaults.class.getClassLoader().getResourceAsStream(TILE_ID_MASK);
            if (input == null) {
                return;
            }
            BufferedImage image;
            try {
                image = ImageIO.read(input);
            } finally {
                input.close();
            }
            if (image == null) {
                return;
            }
            cachedIdsByColor = idsByColor;
            cachedTileIdImage = image;
            int[] overlayPixels = loadImagePixels(MAP_OVERLAY, widthOf(image), heightOf(image));
            int[] roadPixels = loadImagePixels(MAP_ROADS, widthOf(image), heightOf(image));
            int[] bridgeMarkerPixels = loadImagePixels(MAP_BRIDGE_MARKERS, widthOf(image), heightOf(image));
            if (!LOTRGenLayerWorld.loadedBiomeImage()) {
                new LOTRGenLayerWorld();
            }
            if (LOTRGenLayerWorld.imageWidth <= 0 || LOTRGenLayerWorld.imageHeight <= 0) {
                return;
            }
            Map<Integer, long[]> totals = new HashMap<Integer, long[]>();
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = pixels[y * width + x];
                    if ((argb >>> 24) <= 24) {
                        continue;
                    }
                    int color = argb & 0xFFFFFF;
                    if (!idsByColor.containsKey(color)) {
                        continue;
                    }
                    long[] total = totals.get(color);
                    if (total == null) {
                        total = new long[3];
                        totals.put(color, total);
                    }
                    total[0] += x;
                    total[1] += y;
                    total[2]++;
                    for (int distance = 1; distance <= ADJACENCY_SCAN_GAP; distance++) {
                        if (x + distance < width) {
                            int other = pixels[y * width + x + distance];
                            int otherColor = other & 0xFFFFFF;
                            int otherAlpha = other >>> 24;
                            if (isAdjacentTilePixel(idsByColor, color, otherColor, otherAlpha)) {
                                boolean riverNearBorder = isRiverNearBorder(overlayPixels, width, height, x, y, distance, true);
                                boolean bridgeResolverRiver = isBridgeResolverRiverNearBorder(overlayPixels, width, height, x, y, distance, true);
                                registerPixelAdjacency(idsByColor, color, otherColor, otherAlpha,
                                    riverNearBorder, null, width, height, x + distance / 2, y);
                                registerBridgeResolverPixelAdjacency(idsByColor, color, otherColor, otherAlpha,
                                    bridgeResolverRiver, width, height, x + distance / 2, y);
                            }
                        }
                        if (y + distance < height) {
                            int other = pixels[(y + distance) * width + x];
                            int otherColor = other & 0xFFFFFF;
                            int otherAlpha = other >>> 24;
                            if (isAdjacentTilePixel(idsByColor, color, otherColor, otherAlpha)) {
                                boolean riverNearBorder = isRiverNearBorder(overlayPixels, width, height, x, y, distance, false);
                                boolean bridgeResolverRiver = isBridgeResolverRiverNearBorder(overlayPixels, width, height, x, y, distance, false);
                                registerPixelAdjacency(idsByColor, color, otherColor, otherAlpha,
                                    riverNearBorder, null, width, height, x, y + distance / 2);
                                registerBridgeResolverPixelAdjacency(idsByColor, color, otherColor, otherAlpha,
                                    bridgeResolverRiver, width, height, x, y + distance / 2);
                            }
                        }
                    }
                }
            }
            if (bridgeMarkerPixels != null) {
                scanExplicitBridgeMarkers(idsByColor, pixels, bridgeMarkerPixels, width, height);
            } else {
                scanAutomaticBridgeMarkers(idsByColor, pixels, roadPixels, overlayPixels, width, height);
            }
            applyExplicitEdgeCorrections();
            for (Map.Entry<Integer, long[]> entry : totals.entrySet()) {
                String tileId = idsByColor.get(entry.getKey());
                long[] total = entry.getValue();
                if (tileId == null || total[2] <= 0L) {
                    continue;
                }
                double imageX = total[0] / (double) total[2];
                double imageZ = total[1] / (double) total[2];
                tileCenters.put(KOMEConquestTile.normalizeId(tileId),
                    new TileCenter(LOTRDimension.MIDDLE_EARTH.dimensionID, imageToWorldX(imageX, width), DEFAULT_Y, imageToWorldZ(imageZ, height)));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void ensureRetiredTilesLoaded() {
        if (RETIRED_TILE_IDS.isEmpty()) {
            RETIRED_TILE_IDS.add("T045");
            RETIRED_TILE_IDS.add("T327");
            RETIRED_TILE_IDS.add("T257");
            RETIRED_TILE_IDS.add("T271");
            RETIRED_TILE_IDS.add("T291");
            RETIRED_TILE_IDS.add("T293");
            RETIRED_TILE_IDS.add("T294");
            RETIRED_TILE_IDS.add("T295");
            RETIRED_TILE_IDS.add("T424");
            RETIRED_TILE_IDS.add("T456");
            RETIRED_TILE_IDS.add("T458");
            RETIRED_TILE_IDS.add("T459");
            RETIRED_TILE_IDS.add("T520");
            RETIRED_TILE_IDS.add("T521");
            RETIRED_TILE_IDS.add("T522");
            RETIRED_TILE_IDS.add("T526");
            RETIRED_TILE_IDS.add("T527");
            RETIRED_TILE_IDS.add("T528");
            RETIRED_TILE_IDS.add("T529");
            RETIRED_TILE_IDS.add("T530");
            RETIRED_TILE_IDS.add("T531");
            RETIRED_TILE_IDS.add("T532");
            RETIRED_TILE_IDS.add("T545");
            RETIRED_TILE_IDS.add("T546");
            RETIRED_TILE_IDS.add("T571");
        }
    }

    private static double imageToWorldX(double imageX, int imageWidth) {
        double mapX = imageX * LOTRGenLayerWorld.imageWidth / (double) imageWidth;
        return (mapX - LOTRGenLayerWorld.originX) * LOTRGenLayerWorld.scale;
    }

    private static double imageToWorldZ(double imageY, int imageHeight) {
        double mapZ = imageY * LOTRGenLayerWorld.imageHeight / (double) imageHeight;
        return (mapZ - LOTRGenLayerWorld.originZ) * LOTRGenLayerWorld.scale;
    }

    private static int widthOf(BufferedImage image) {
        return image == null ? 0 : image.getWidth();
    }

    private static int heightOf(BufferedImage image) {
        return image == null ? 0 : image.getHeight();
    }

    private static int[] loadImagePixels(String resource, int expectedWidth, int expectedHeight) {
        InputStream input = null;
        try {
            input = KOMEConquestTileDefaults.class.getClassLoader().getResourceAsStream(resource);
            if (input == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                return null;
            }
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean isRiverNearBorder(int[] overlayPixels, int width, int height, int x, int y, int distance, boolean horizontal) {
        if (overlayPixels == null || distance <= 0) {
            return false;
        }
        int checkedLines = 0;
        int qualifyingLines = 0;
        int totalSamples = 0;
        int totalWater = 0;
        for (int tangent = -RIVER_BORDER_TANGENT_RADIUS; tangent <= RIVER_BORDER_TANGENT_RADIUS; tangent++) {
            int lineSamples = 0;
            int lineWater = 0;
            for (int step = 0; step <= distance; step++) {
                int sampleX = horizontal ? x + step : x + tangent;
                int sampleY = horizontal ? y + tangent : y + step;
                if (sampleX < 0 || sampleX >= width || sampleY < 0 || sampleY >= height) {
                    continue;
                }
                lineSamples++;
                if (isWaterColor(overlayPixels[sampleY * width + sampleX])) {
                    lineWater++;
                }
            }
            if (lineSamples <= 0) {
                continue;
            }
            checkedLines++;
            totalSamples += lineSamples;
            totalWater += lineWater;
            if (lineWater * 2 >= lineSamples) {
                qualifyingLines++;
            }
        }
        return checkedLines > 0 && totalWater >= RIVER_BORDER_MIN_WATER_PIXELS
            && (qualifyingLines >= 2 || qualifyingLines * 100 / checkedLines >= 60
                || totalSamples > 0 && totalWater * 100 / totalSamples >= 60);
    }

    private static boolean isWaterColor(int argb) {
        int red = argb >> 16 & 255;
        int green = argb >> 8 & 255;
        int blue = argb & 255;
        return blue > 80 && blue > red + 18 && blue > green + 5;
    }

    private static boolean isBridgeResolverRiverNearBorder(int[] overlayPixels, int width, int height, int x, int y, int distance, boolean horizontal) {
        if (overlayPixels == null || distance <= 0) {
            return false;
        }
        int samples = 0;
        int water = 0;
        for (int step = 0; step <= distance; step++) {
            int sampleX = horizontal ? x + step : x;
            int sampleY = horizontal ? y : y + step;
            samples++;
            if (isWaterColor(overlayPixels[sampleY * width + sampleX])) {
                water++;
            }
        }
        if (samples > 0 && water * 2 >= samples) {
            return true;
        }
        int centerX = horizontal ? x + distance / 2 : x;
        int centerY = horizontal ? y : y + distance / 2;
        int nearbyWater = 0;
        for (int sampleY = Math.max(0, centerY - BRIDGE_RESOLVER_RIVER_SEARCH_RADIUS); sampleY <= Math.min(height - 1, centerY + BRIDGE_RESOLVER_RIVER_SEARCH_RADIUS); sampleY++) {
            for (int sampleX = Math.max(0, centerX - BRIDGE_RESOLVER_RIVER_SEARCH_RADIUS); sampleX <= Math.min(width - 1, centerX + BRIDGE_RESOLVER_RIVER_SEARCH_RADIUS); sampleX++) {
                if (isWaterColor(overlayPixels[sampleY * width + sampleX])) {
                    nearbyWater++;
                    if (nearbyWater >= RIVER_BORDER_MIN_WATER_PIXELS) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void scanAutomaticBridgeMarkers(Map<Integer, String> idsByColor, int[] tilePixels, int[] roadPixels, int[] overlayPixels, int width, int height) {
        if (idsByColor == null || tilePixels == null || roadPixels == null || overlayPixels == null || width <= 0 || height <= 0) {
            return;
        }
        boolean[] visited = new boolean[roadPixels.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (visited[index] || !isRoadLayerPixel(roadPixels[index])) {
                    continue;
                }
                BridgeComponent component = collectPathComponent(roadPixels, visited, width, height, x, y);
                if (!isBridgeDotComponent(component, overlayPixels, width, height)) {
                    continue;
                }
                String[] pair = resolveBridgeTilePair(idsByColor, tilePixels, width, height, component.centerX(), component.centerY());
                if (pair == null) {
                    continue;
                }
                EdgeStats stats = getOrCreateExplicitBridgeStats(pair[0], pair[1]);
                stats.recordExplicitBridgePixel(new BridgePixel(component.centerX(), component.centerY(), width, height));
            }
        }
    }

    private static void scanExplicitBridgeMarkers(Map<Integer, String> idsByColor, int[] tilePixels, int[] markerPixels, int width, int height) {
        if (idsByColor == null || tilePixels == null || markerPixels == null || width <= 0 || height <= 0) {
            return;
        }
        boolean[] visited = new boolean[markerPixels.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (visited[index] || !isBridgeMarkerPixel(markerPixels[index])) {
                    continue;
                }
                BridgeComponent component = collectBridgeMarkerComponent(markerPixels, visited, width, height, x, y);
                if (component.count <= 0) {
                    continue;
                }
                String[] pair = resolveExplicitBridgeTilePair(idsByColor, tilePixels, width, height, component.centerX(), component.centerY());
                if (pair == null) {
                    continue;
                }
                String originalPairKey = KOMEConquestRouteEdge.key(pair[0], pair[1]);
                pair = remapExplicitBridgePair(pair);
                boolean remapped = !originalPairKey.equals(KOMEConquestRouteEdge.key(pair[0], pair[1]));
                EdgeStats resolverStats = bridgeResolverEdgeStats.get(KOMEConquestRouteEdge.key(pair[0], pair[1]));
                if (remapped || resolverStats != null && resolverStats.isRiver()) {
                    EdgeStats stats = getOrCreateExplicitBridgeStats(pair[0], pair[1]);
                    stats.recordBridgePixel(new BridgePixel(component.centerX(), component.centerY(), width, height));
                }
            }
        }
    }

    private static BridgeComponent collectPathComponent(int[] roadPixels, boolean[] visited, int width, int height, int startX, int startY) {
        BridgeComponent component = new BridgeComponent();
        List<Integer> queue = new ArrayList<Integer>();
        int startIndex = startY * width + startX;
        queue.add(Integer.valueOf(startIndex));
        visited[startIndex] = true;
        for (int cursor = 0; cursor < queue.size(); cursor++) {
            int index = queue.get(cursor).intValue();
            int x = index % width;
            int y = index / width;
            component.add(x, y);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nextX = x + dx;
                    int nextY = y + dy;
                    if (nextX < 0 || nextX >= width || nextY < 0 || nextY >= height) {
                        continue;
                    }
                    int nextIndex = nextY * width + nextX;
                    if (!visited[nextIndex] && isRoadLayerPixel(roadPixels[nextIndex])) {
                        visited[nextIndex] = true;
                        queue.add(Integer.valueOf(nextIndex));
                    }
                }
            }
        }
        return component;
    }

    private static BridgeComponent collectBridgeMarkerComponent(int[] markerPixels, boolean[] visited, int width, int height, int startX, int startY) {
        BridgeComponent component = new BridgeComponent();
        List<Integer> queue = new ArrayList<Integer>();
        int startIndex = startY * width + startX;
        queue.add(Integer.valueOf(startIndex));
        visited[startIndex] = true;
        for (int cursor = 0; cursor < queue.size(); cursor++) {
            int index = queue.get(cursor).intValue();
            int x = index % width;
            int y = index / width;
            component.add(x, y);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nextX = x + dx;
                    int nextY = y + dy;
                    if (nextX < 0 || nextX >= width || nextY < 0 || nextY >= height) {
                        continue;
                    }
                    int nextIndex = nextY * width + nextX;
                    if (!visited[nextIndex] && isBridgeMarkerPixel(markerPixels[nextIndex])) {
                        visited[nextIndex] = true;
                        queue.add(Integer.valueOf(nextIndex));
                    }
                }
            }
        }
        return component;
    }

    private static boolean isBridgeDotComponent(BridgeComponent component, int[] overlayPixels, int width, int height) {
        if (component == null || component.count <= 0 || component.count > BRIDGE_DOT_MAX_PIXELS) {
            return false;
        }
        if (component.width() > BRIDGE_DOT_MAX_WIDTH || component.height() > BRIDGE_DOT_MAX_HEIGHT) {
            return false;
        }
        return hasWaterNear(overlayPixels, width, height, component.centerX(), component.centerY(), BRIDGE_DOT_WATER_RADIUS);
    }

    private static String[] resolveBridgeTilePair(Map<Integer, String> idsByColor, int[] tilePixels, int width, int height, int centerX, int centerY) {
        Map<String, Integer> nearest = new HashMap<String, Integer>();
        int radius = BRIDGE_TILE_PAIR_SEARCH_RADIUS;
        for (int y = Math.max(0, centerY - radius); y <= Math.min(height - 1, centerY + radius); y++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(width - 1, centerX + radius); x++) {
                int argb = tilePixels[y * width + x];
                if ((argb >>> 24) <= 24) {
                    continue;
                }
                String tileId = idsByColor.get(argb & 0xFFFFFF);
                if (tileId == null) {
                    continue;
                }
                tileId = KOMEConquestTile.normalizeId(tileId);
                int dx = x - centerX;
                int dy = y - centerY;
                int distance = dx * dx + dy * dy;
                Integer current = nearest.get(tileId);
                if (current == null || distance < current.intValue()) {
                    nearest.put(tileId, Integer.valueOf(distance));
                }
            }
        }
        String bestA = null;
        String bestB = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> first : nearest.entrySet()) {
            for (Map.Entry<String, Integer> second : nearest.entrySet()) {
                if (first.getKey().compareTo(second.getKey()) >= 0) {
                    continue;
                }
                EdgeStats stats = automaticEdgeStats.get(KOMEConquestRouteEdge.key(first.getKey(), second.getKey()));
                if (stats == null || !stats.isRiver()) {
                    continue;
                }
                int distance = first.getValue().intValue() + second.getValue().intValue();
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestA = first.getKey();
                    bestB = second.getKey();
                }
            }
        }
        return bestA == null ? null : new String[] {bestA, bestB};
    }

    private static String[] resolveExplicitBridgeTilePair(Map<Integer, String> idsByColor, int[] tilePixels, int width, int height, int centerX, int centerY) {
        Map<String, Integer> nearest = collectNearestTiles(idsByColor, tilePixels, width, height, centerX, centerY, EXPLICIT_BRIDGE_TILE_PAIR_SEARCH_RADIUS);
        String[] riverPair = chooseNearestPair(nearest, true, bridgeResolverEdgeStats);
        if (riverPair != null) {
            return riverPair;
        }
        String[] adjacentPair = chooseNearestPair(nearest, false, bridgeResolverEdgeStats);
        return adjacentPair != null ? adjacentPair : chooseNearestAnyPair(nearest);
    }

    private static String[] remapExplicitBridgePair(String[] pair) {
        if (pair == null || pair.length != 2) {
            return pair;
        }
        String key = KOMEConquestRouteEdge.key(pair[0], pair[1]);
        for (String[] remap : EXPLICIT_BRIDGE_EDGE_REMAPS) {
            if (remap.length == 4 && key.equals(KOMEConquestRouteEdge.key(remap[0], remap[1]))) {
                return new String[] {KOMEConquestTile.normalizeId(remap[2]), KOMEConquestTile.normalizeId(remap[3])};
            }
        }
        return pair;
    }

    private static Map<String, Integer> collectNearestTiles(Map<Integer, String> idsByColor, int[] tilePixels, int width, int height, int centerX, int centerY, int radius) {
        Map<String, Integer> nearest = new HashMap<String, Integer>();
        for (int y = Math.max(0, centerY - radius); y <= Math.min(height - 1, centerY + radius); y++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(width - 1, centerX + radius); x++) {
                int argb = tilePixels[y * width + x];
                if ((argb >>> 24) <= 24) {
                    continue;
                }
                String tileId = idsByColor.get(argb & 0xFFFFFF);
                if (tileId == null) {
                    continue;
                }
                tileId = KOMEConquestTile.normalizeId(tileId);
                int dx = x - centerX;
                int dy = y - centerY;
                int distance = dx * dx + dy * dy;
                Integer current = nearest.get(tileId);
                if (current == null || distance < current.intValue()) {
                    nearest.put(tileId, Integer.valueOf(distance));
                }
            }
        }
        return nearest;
    }

    private static String[] chooseNearestPair(Map<String, Integer> nearest, boolean riverOnly) {
        return chooseNearestPair(nearest, riverOnly, automaticEdgeStats);
    }

    private static String[] chooseNearestPair(Map<String, Integer> nearest, boolean riverOnly, Map<String, EdgeStats> edgeStats) {
        String bestA = null;
        String bestB = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> first : nearest.entrySet()) {
            for (Map.Entry<String, Integer> second : nearest.entrySet()) {
                if (first.getKey().compareTo(second.getKey()) >= 0) {
                    continue;
                }
                EdgeStats stats = edgeStats.get(KOMEConquestRouteEdge.key(first.getKey(), second.getKey()));
                if (stats == null || (riverOnly && !stats.isRiver())) {
                    continue;
                }
                int distance = first.getValue().intValue() + second.getValue().intValue();
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestA = first.getKey();
                    bestB = second.getKey();
                }
            }
        }
        return bestA == null ? null : new String[] {bestA, bestB};
    }

    private static String[] chooseNearestAnyPair(Map<String, Integer> nearest) {
        String bestA = null;
        String bestB = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> first : nearest.entrySet()) {
            for (Map.Entry<String, Integer> second : nearest.entrySet()) {
                if (first.getKey().compareTo(second.getKey()) >= 0) {
                    continue;
                }
                int distance = first.getValue().intValue() + second.getValue().intValue();
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestA = first.getKey();
                    bestB = second.getKey();
                }
            }
        }
        return bestA == null ? null : new String[] {bestA, bestB};
    }

    private static EdgeStats getOrCreateExplicitBridgeStats(String first, String second) {
        String a = KOMEConquestTile.normalizeId(first);
        String b = KOMEConquestTile.normalizeId(second);
        String key = KOMEConquestRouteEdge.key(a, b);
        EdgeStats stats = automaticEdgeStats.get(key);
        if (stats == null) {
            stats = new EdgeStats();
            automaticEdgeStats.put(key, stats);
        }
        Set<String> aSet = tileAdjacency.get(a);
        if (aSet == null) {
            aSet = new HashSet<String>();
            tileAdjacency.put(a, aSet);
        }
        aSet.add(b);
        Set<String> bSet = tileAdjacency.get(b);
        if (bSet == null) {
            bSet = new HashSet<String>();
            tileAdjacency.put(b, bSet);
        }
        bSet.add(a);
        return stats;
    }

    private static void applyExplicitEdgeCorrections() {
        for (String[] pair : EXPLICIT_REMOVED_EDGES) {
            if (pair.length == 2) {
                removeAdjacency(pair[0], pair[1]);
            }
        }
        for (String[] pair : EXPLICIT_OPEN_EDGES) {
            if (pair.length == 2) {
                forceOpenAdjacency(pair[0], pair[1]);
            }
        }
    }

    private static void forceOpenAdjacency(String first, String second) {
        String a = KOMEConquestTile.normalizeId(first);
        String b = KOMEConquestTile.normalizeId(second);
        if (a.length() == 0 || b.length() == 0 || a.equals(b)) {
            return;
        }
        String key = KOMEConquestRouteEdge.key(a, b);
        EdgeStats stats = new EdgeStats();
        stats.forceOpen();
        automaticEdgeStats.put(key, stats);
        Set<String> aSet = tileAdjacency.get(a);
        if (aSet == null) {
            aSet = new HashSet<String>();
            tileAdjacency.put(a, aSet);
        }
        aSet.add(b);
        Set<String> bSet = tileAdjacency.get(b);
        if (bSet == null) {
            bSet = new HashSet<String>();
            tileAdjacency.put(b, bSet);
        }
        bSet.add(a);
    }

    private static void removeAdjacency(String first, String second) {
        String a = KOMEConquestTile.normalizeId(first);
        String b = KOMEConquestTile.normalizeId(second);
        automaticEdgeStats.remove(KOMEConquestRouteEdge.key(a, b));
        Set<String> aSet = tileAdjacency.get(a);
        if (aSet != null) {
            aSet.remove(b);
            if (aSet.isEmpty()) {
                tileAdjacency.remove(a);
            }
        }
        Set<String> bSet = tileAdjacency.get(b);
        if (bSet != null) {
            bSet.remove(a);
            if (bSet.isEmpty()) {
                tileAdjacency.remove(b);
            }
        }
    }

    private static BridgePixel findBridgeNearBorder(int[] overlayPixels, int width, int height, int x, int y, int distance, boolean horizontal) {
        if (overlayPixels == null || distance <= 0) {
            return null;
        }
        int centerX = horizontal ? x + distance / 2 : x;
        int centerY = horizontal ? y : y + distance / 2;
        int roadTouchingWater = 0;
        int roadX = 0;
        int roadY = 0;
        for (int tangent = -BRIDGE_BORDER_TANGENT_RADIUS; tangent <= BRIDGE_BORDER_TANGENT_RADIUS; tangent++) {
            for (int normal = -BRIDGE_BORDER_NORMAL_RADIUS; normal <= BRIDGE_BORDER_NORMAL_RADIUS; normal++) {
                int sampleX = horizontal ? centerX + normal : centerX + tangent;
                int sampleY = horizontal ? centerY + tangent : centerY + normal;
                if (sampleX < 0 || sampleX >= width || sampleY < 0 || sampleY >= height) {
                    continue;
                }
                if (isRoadColor(overlayPixels[sampleY * width + sampleX]) && hasWaterNear(overlayPixels, width, height, sampleX, sampleY, 2)) {
                    roadTouchingWater++;
                    roadX += sampleX;
                    roadY += sampleY;
                    if (roadTouchingWater >= BRIDGE_BORDER_MIN_ROAD_PIXELS) {
                        return new BridgePixel(roadX / roadTouchingWater, roadY / roadTouchingWater, width, height);
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasWaterNear(int[] overlayPixels, int width, int height, int x, int y, int radius) {
        for (int sampleY = Math.max(0, y - radius); sampleY <= Math.min(height - 1, y + radius); sampleY++) {
            for (int sampleX = Math.max(0, x - radius); sampleX <= Math.min(width - 1, x + radius); sampleX++) {
                if (isWaterColor(overlayPixels[sampleY * width + sampleX])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRoadColor(int argb) {
        int alpha = argb >>> 24;
        int red = argb >> 16 & 255;
        int green = argb >> 8 & 255;
        int blue = argb & 255;
        return alpha > 90 && red < 70 && green < 70 && blue < 70;
    }

    private static boolean isRoadLayerPixel(int argb) {
        int alpha = argb >>> 24;
        int red = argb >> 16 & 255;
        int green = argb >> 8 & 255;
        int blue = argb & 255;
        return alpha > 24 && green > 70 && red < 95 && blue < 95 && green > red + 35 && green > blue + 35;
    }

    private static boolean isBridgeMarkerPixel(int argb) {
        int alpha = argb >>> 24;
        int red = argb >> 16 & 255;
        int green = argb >> 8 & 255;
        int blue = argb & 255;
        return alpha > 24 && red > 140 && blue > 100 && green < 100 && red > green + 45 && blue > green + 35;
    }

    private static boolean isAdjacentTilePixel(Map<Integer, String> idsByColor, int color, int otherColor, int otherAlpha) {
        return otherAlpha > 24 && color != otherColor && idsByColor.containsKey(otherColor);
    }

    private static void registerPixelAdjacency(Map<Integer, String> idsByColor, int color, int otherColor, int otherAlpha,
            boolean riverGap, BridgePixel bridgePixel, int imageWidth, int imageHeight, int markerX, int markerY) {
        if (!isAdjacentTilePixel(idsByColor, color, otherColor, otherAlpha)) {
            return;
        }
        addAdjacency(idsByColor.get(color), idsByColor.get(otherColor), riverGap, bridgePixel, imageWidth, imageHeight, markerX, markerY);
    }

    private static void registerBridgeResolverPixelAdjacency(Map<Integer, String> idsByColor, int color, int otherColor, int otherAlpha,
            boolean riverGap, int imageWidth, int imageHeight, int markerX, int markerY) {
        if (!isAdjacentTilePixel(idsByColor, color, otherColor, otherAlpha)) {
            return;
        }
        addBridgeResolverAdjacency(idsByColor.get(color), idsByColor.get(otherColor), riverGap, imageWidth, imageHeight, markerX, markerY);
    }

    private static void addBridgeResolverAdjacency(String first, String second, boolean riverGap, int imageWidth, int imageHeight, int markerX, int markerY) {
        String a = KOMEConquestTile.normalizeId(first);
        String b = KOMEConquestTile.normalizeId(second);
        if (a.length() == 0 || b.length() == 0 || a.equals(b)) {
            return;
        }
        String key = KOMEConquestRouteEdge.key(a, b);
        EdgeStats stats = bridgeResolverEdgeStats.get(key);
        if (stats == null) {
            stats = new EdgeStats();
            bridgeResolverEdgeStats.put(key, stats);
        }
        stats.add(riverGap, null, imageWidth, imageHeight, markerX, markerY);
    }

    private static void addAdjacency(String first, String second, boolean riverGap, BridgePixel bridgePixel, int imageWidth, int imageHeight, int markerX, int markerY) {
        String a = KOMEConquestTile.normalizeId(first);
        String b = KOMEConquestTile.normalizeId(second);
        if (a.length() == 0 || b.length() == 0 || a.equals(b)) {
            return;
        }
        String key = KOMEConquestRouteEdge.key(a, b);
        EdgeStats stats = automaticEdgeStats.get(key);
        if (stats == null) {
            stats = new EdgeStats();
            automaticEdgeStats.put(key, stats);
        }
        stats.add(riverGap, bridgePixel, imageWidth, imageHeight, markerX, markerY);
        Set<String> aSet = tileAdjacency.get(a);
        if (aSet == null) {
            aSet = new HashSet<String>();
            tileAdjacency.put(a, aSet);
        }
        aSet.add(b);
        Set<String> bSet = tileAdjacency.get(b);
        if (bSet == null) {
            bSet = new HashSet<String>();
            tileAdjacency.put(b, bSet);
        }
        bSet.add(a);
    }

    private static Map<Integer, String> loadTileIds() throws Exception {
        Map<Integer, String> idsByColor = new HashMap<Integer, String>();
        InputStream input = KOMEConquestTileDefaults.class.getClassLoader().getResourceAsStream(TILE_ID_MAP);
        if (input == null) {
            return idsByColor;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0 || equals >= line.length() - 1) {
                    continue;
                }
                String[] rgb = line.substring(0, equals).split(",");
                if (rgb.length != 3) {
                    continue;
                }
                int red = Integer.parseInt(rgb[0].trim());
                int green = Integer.parseInt(rgb[1].trim());
                int blue = Integer.parseInt(rgb[2].trim());
                String tileId = KOMEConquestTile.normalizeId(line.substring(equals + 1));
                if (!isRetiredTile(tileId)) {
                    idsByColor.put(red << 16 | green << 8 | blue, tileId);
                }
            }
        } finally {
            reader.close();
        }
        return idsByColor;
    }

    public static class TileCenter {
        public final int dimensionId;
        public final double x;
        public final double y;
        public final double z;

        private TileCenter(int dimensionId, double x, double y, double z) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class EdgeStats {
        private int total;
        private int river;
        private int bridge;
        private int riverMarkerX;
        private int riverMarkerY;
        private int riverMarkerSamples;
        private int riverMarkerImageWidth;
        private int riverMarkerImageHeight;
        private final List<BridgePixel> bridgePixels = new ArrayList<BridgePixel>();

        private void add(boolean riverGap, BridgePixel bridgePixel, int imageWidth, int imageHeight, int markerX, int markerY) {
            total++;
            if (riverGap) {
                river++;
                recordRiverMarkerPixel(markerX, markerY, imageWidth, imageHeight);
            }
            if (bridgePixel != null) {
                bridge++;
                addBridgePixel(bridgePixel);
            }
        }

        private void recordBridgePixel(BridgePixel pixel) {
            if (pixel != null) {
                bridge++;
                addBridgePixel(pixel);
            }
        }

        private void recordExplicitBridgePixel(BridgePixel pixel) {
            total++;
            river++;
            bridge++;
            addBridgePixel(pixel);
        }

        private void forceOpen() {
            total = 1;
            river = 0;
            bridge = 0;
            riverMarkerX = 0;
            riverMarkerY = 0;
            riverMarkerSamples = 0;
            riverMarkerImageWidth = 0;
            riverMarkerImageHeight = 0;
            bridgePixels.clear();
        }

        private void recordRiverMarkerPixel(int x, int y, int imageWidth, int imageHeight) {
            riverMarkerX += x;
            riverMarkerY += y;
            riverMarkerSamples++;
            riverMarkerImageWidth = imageWidth;
            riverMarkerImageHeight = imageHeight;
        }

        private boolean hasRiverMarker() {
            return riverMarkerSamples > 0 && riverMarkerImageWidth > 0 && riverMarkerImageHeight > 0;
        }

        private int riverMarkerX() {
            return riverMarkerSamples <= 0 ? 0 : Math.round(riverMarkerX / (float) riverMarkerSamples);
        }

        private int riverMarkerY() {
            return riverMarkerSamples <= 0 ? 0 : Math.round(riverMarkerY / (float) riverMarkerSamples);
        }

        private int riverMarkerImageWidth() {
            return riverMarkerImageWidth;
        }

        private int riverMarkerImageHeight() {
            return riverMarkerImageHeight;
        }

        private void addBridgePixel(BridgePixel pixel) {
            for (BridgePixel existing : bridgePixels) {
                int dx = existing.x - pixel.x;
                int dy = existing.y - pixel.y;
                if (dx * dx + dy * dy <= BRIDGE_MARKER_MERGE_PIXELS * BRIDGE_MARKER_MERGE_PIXELS) {
                    return;
                }
            }
            bridgePixels.add(pixel);
        }

        private boolean isRiver() {
            return total > 0 && river * 100 / total >= 60;
        }

        private boolean isBridge() {
            return !bridgePixels.isEmpty();
        }
    }

    private static class BridgeComponent {
        private int count;
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int sumX;
        private int sumY;

        private void add(int x, int y) {
            count++;
            sumX += x;
            sumY += y;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private int centerX() {
            return count <= 0 ? 0 : Math.round(sumX / (float) count);
        }

        private int centerY() {
            return count <= 0 ? 0 : Math.round(sumY / (float) count);
        }
    }

    private static class BridgePixel {
        private final int x;
        private final int y;
        private final int imageWidth;
        private final int imageHeight;

        private BridgePixel(int x, int y, int imageWidth, int imageHeight) {
            this.x = x;
            this.y = y;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
        }
    }

    public static class AutomaticBridgeMarker {
        public final String fromTile;
        public final String toTile;
        public final int dimensionId;
        public final double x;
        public final double y;
        public final double z;
        public final int imageX;
        public final int imageY;

        private AutomaticBridgeMarker(String fromTile, String toTile, int dimensionId, double x, double y, double z, int imageX, int imageY) {
            this.fromTile = KOMEConquestTile.normalizeId(fromTile);
            this.toTile = KOMEConquestTile.normalizeId(toTile);
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.imageX = imageX;
            this.imageY = imageY;
        }

        public String getTilePairLabel() {
            return fromTile + " <-> " + toTile;
        }
    }

    public static class AutomaticRiverBlockerMarker {
        public final String fromTile;
        public final String toTile;
        public final int dimensionId;
        public final double x;
        public final double y;
        public final double z;
        public final int imageX;
        public final int imageY;

        private AutomaticRiverBlockerMarker(String fromTile, String toTile, int dimensionId, double x, double y, double z, int imageX, int imageY) {
            this.fromTile = KOMEConquestTile.normalizeId(fromTile);
            this.toTile = KOMEConquestTile.normalizeId(toTile);
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.imageX = imageX;
            this.imageY = imageY;
        }

        public String getTilePairLabel() {
            return fromTile + " <-> " + toTile;
        }
    }
}
