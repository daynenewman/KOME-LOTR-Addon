package kome.common.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/** Existing tile identity authority and compatibility accessors for explicit gameplay defaults. */
public class KOMEConquestTileDefaults {
    private static final String TILE_ID_MAP = "assets/kome/map/reset_conquest_tile_ids.txt";
    private static final Set<String> RETIRED_TILE_IDS = new HashSet<String>();
    private static Map<Integer, String> canonicalIdsByColor;

    /** Legacy API: routing reference, not a geographic centroid or guaranteed interior position. */
    public static TileCenter getTileCenter(String tileId) {
        KOMETileGameplayDefaults.Point p = KOMETileGameplayDefaults.get().getRouteReference(tileId);
        return p == null ? null : new TileCenter(p.dimensionId(), p.x, p.y, p.z);
    }
    public static String getTileIdAtMapPosition(int dimension, double mapX, double mapZ) {
        return KOMETileWorldResolver.INSTANCE.resolveMapPosition(dimension, mapX, mapZ).tileId;
    }
    public static KOMETileResolution resolveWorldCoordinates(int dimension, int worldX, int worldZ) {
        return KOMETileWorldResolver.INSTANCE.resolve(dimension, worldX, worldZ);
    }
    public static Set<String> getAdjacentTiles(String tileId) {
        return KOMETileGameplayDefaults.get().neighbors(tileId);
    }
    public static KOMEConquestRouteEdge getAutomaticRouteEdge(String a, String b) {
        return KOMETileGameplayDefaults.get().route(a, b);
    }
    public static Set<KOMEConquestRouteEdge> getAutomaticBridgeEdges() {
        Set<KOMEConquestRouteEdge> edges = new HashSet<KOMEConquestRouteEdge>();
        KOMETileGameplayDefaults defaults = KOMETileGameplayDefaults.get();
        for (String key : defaults.routeKeys()) {
            String[] tiles = key.split("\\|");
            KOMEConquestRouteEdge edge = defaults.route(tiles[0], tiles[1]);
            if (KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType)) edges.add(edge);
        }
        return edges;
    }
    public static List<AutomaticBridgeMarker> getAutomaticBridgeMarkers() {
        List<AutomaticBridgeMarker> result = new ArrayList<AutomaticBridgeMarker>();
        for (KOMETileGameplayDefaults.Marker m : KOMETileGameplayDefaults.get().markers())
            if (KOMEConquestRouteEdge.BRIDGE.equals(m.type)) result.add(new AutomaticBridgeMarker(m.from, m.to,
                m.point.dimensionId(), m.point.x, m.point.y, m.point.z, m.mapX, m.mapY));
        return result;
    }
    public static int getAutomaticBridgeMarkerCount() { return getAutomaticBridgeMarkers().size(); }
    public static List<AutomaticRiverBlockerMarker> getAutomaticRiverBlockerMarkers() {
        List<AutomaticRiverBlockerMarker> result = new ArrayList<AutomaticRiverBlockerMarker>();
        for (KOMETileGameplayDefaults.Marker m : KOMETileGameplayDefaults.get().markers())
            if (KOMEConquestRouteEdge.RIVER.equals(m.type)) result.add(new AutomaticRiverBlockerMarker(m.from, m.to,
                m.point.dimensionId(), m.point.x, m.point.y, m.point.z, m.mapX, m.mapY));
        return result;
    }

    public static Set<String> getKnownTileIds() {
        return new HashSet<String>(getTileIdsByColor().values());
    }

    /** Sole packaged tile-ID authority, independent of center/adjacency or biome initialization. */
    public static synchronized Map<Integer, String> getTileIdsByColor() {
        if (canonicalIdsByColor == null) {
            try { canonicalIdsByColor = loadTileIds(); }
            catch (IOException e) { throw new IllegalStateException("Invalid conquest tile identity resource", e); }
        }
        return canonicalIdsByColor;
    }

    public static Set<String> getRetiredTileIds() {
        ensureRetiredTilesLoaded();
        return new HashSet<String>(RETIRED_TILE_IDS);
    }

    public static boolean isRetiredTile(String tileId) {
        ensureRetiredTilesLoaded();
        return RETIRED_TILE_IDS.contains(KOMEConquestTile.normalizeId(tileId));
    }

    private static synchronized void ensureRetiredTilesLoaded() {
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

    private static Map<Integer, String> loadTileIds() throws IOException {
        Map<Integer, String> idsByColor = new HashMap<Integer, String>();
        try (InputStream input = KOMEConquestTileDefaults.class.getClassLoader().getResourceAsStream(TILE_ID_MAP)) {
            for (Map.Entry<Integer, String> entry : KOMETileRasterSnapshot.readMapping(input).entrySet()) {
                if (!isRetiredTile(entry.getValue())) idsByColor.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(idsByColor);
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
