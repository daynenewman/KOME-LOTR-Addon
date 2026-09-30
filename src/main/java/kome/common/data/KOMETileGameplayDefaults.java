package kome.common.data;

import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Packaged gameplay defaults, independent of geographic pixels. WorldData owns saved overrides. */
public final class KOMETileGameplayDefaults {
    static final String ROOT = "assets/kome/config/";
    static final String VERSION = "# KOME gameplay defaults 1";
    private static volatile KOMETileGameplayDefaults instance;
    private final Map<String, Point> references;
    private final Map<String, Point> arrivals;
    private final Map<String, String> legacyOutside;
    private final Map<String, String> routes;
    private final Map<String, Set<String>> neighbors;
    private final Map<String, List<String>> waypoints;
    private final List<Marker> markers;

    private KOMETileGameplayDefaults(Map<String, Point> references, Map<String, Point> arrivals,
            Map<String, String> legacyOutside, Map<String, String> routes, Map<String, Set<String>> neighbors,
            Map<String, List<String>> waypoints, List<Marker> markers) {
        this.references = Collections.unmodifiableMap(new HashMap<String, Point>(references));
        this.arrivals = Collections.unmodifiableMap(new HashMap<String, Point>(arrivals));
        this.legacyOutside = Collections.unmodifiableMap(new HashMap<String, String>(legacyOutside));
        this.routes = Collections.unmodifiableMap(new TreeMap<String, String>(routes));
        Map<String, Set<String>> ns = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> e : neighbors.entrySet())
            ns.put(e.getKey(), Collections.unmodifiableSet(new TreeSet<String>(e.getValue())));
        this.neighbors = Collections.unmodifiableMap(ns);
        Map<String, List<String>> ws = new TreeMap<String, List<String>>();
        for (Map.Entry<String, List<String>> e : waypoints.entrySet())
            ws.put(e.getKey(), Collections.unmodifiableList(new ArrayList<String>(e.getValue())));
        this.waypoints = Collections.unmodifiableMap(ws);
        this.markers = Collections.unmodifiableList(new ArrayList<Marker>(markers));
    }

    public static KOMETileGameplayDefaults get() {
        KOMETileGameplayDefaults captured = instance;
        if (captured == null) {
            synchronized (KOMETileGameplayDefaults.class) {
                captured = instance;
                if (captured == null) {
                    try {
                        captured = load(KOMETileGameplayDefaults.class.getClassLoader());
                    } catch (IOException e) {
                        throw new IllegalStateException("Invalid KOME gameplay defaults: " + e.getMessage()
                            + ". Restore a matching complete versioned gameplay resource set; geographic inference is disabled.", e);
                    }
                    instance = captured; // No partially loaded defaults can be observed.
                }
            }
        }
        return captured;
    }

    public Point getRouteReference(String tile) { return references.get(KOMEConquestTile.normalizeId(tile)); }
    public Point getArrivalDefault(String tile) { return arrivals.get(KOMEConquestTile.normalizeId(tile)); }
    public Map<String, String> legacyDestinationExceptions() { return legacyOutside; }
    public Set<String> neighbors(String tile) {
        Set<String> result = neighbors.get(KOMEConquestTile.normalizeId(tile));
        return result == null ? Collections.<String>emptySet() : result;
    }
    public List<String> waypointCandidates(String tile) {
        List<String> result = waypoints.get(KOMEConquestTile.normalizeId(tile));
        return result == null ? Collections.<String>emptyList() : result;
    }
    public Set<String> waypointTiles() { return waypoints.keySet(); }
    public Set<String> routeKeys() { return routes.keySet(); }
    public KOMEConquestRouteEdge route(String a, String b) {
        String type = routes.get(KOMEConquestRouteEdge.key(a, b));
        if (type == null) return null;
        // The existing edge DTO is mutable: never expose a shared instance.
        KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge(a, b, type);
        edge.name = KOMEConquestRouteEdge.BRIDGE.equals(type) ? "Automatic bridge crossing"
            : KOMEConquestRouteEdge.RIVER.equals(type) ? "Automatic river crossing" : "";
        return edge;
    }
    List<Marker> markers() { return markers; }

    /** Pure candidate construction; failures never replace the published instance. */
    static KOMETileGameplayDefaults load(ClassLoader loader) throws IOException {
        Properties manifest = new Properties();
        manifest.load(new ByteArrayInputStream(readResource(loader, "kome_tile_gameplay_manifest.properties")));
        if (!"1".equals(manifest.getProperty("version")) || manifest.getProperty("revision", "").trim().isEmpty())
            throw bad("kome_tile_gameplay_manifest.properties", "missing/unsupported version or revision");
        Set<String> known = KOMEConquestTileDefaults.getKnownTileIds();
        Map<String, Point> refs = new HashMap<String, Point>(), arrivals = new HashMap<String, Point>();
        Map<String, String> exceptions = new HashMap<String, String>(), routes = new TreeMap<String, String>();
        Map<String, Set<String>> neighbors = new HashMap<String, Set<String>>();
        Map<String, List<String>> waypoints = new TreeMap<String, List<String>>();
        List<Marker> markers = new ArrayList<Marker>();
        String file = "kome_tile_gameplay_points.csv";
        for (String[] row : rows(loader, manifest, file, "tile,routeX,routeZ,arrivalX,arrivalY,arrivalZ,legacyOutside")) {
            String tile = tile(row[0], known, file);
            if (refs.containsKey(tile)) throw bad(file, "duplicate point " + tile);
            refs.put(tile, new Point(number(row[1], file), 80.0D, number(row[2], file)));
            arrivals.put(tile, new Point(number(row[3], file), number(row[4], file), number(row[5], file)));
            if (!row[6].isEmpty()) {
                if (!row[6].equals("IN_BOUNDS_GAP:") && !(row[6].startsWith("RESOLVED:")
                        && known.contains(row[6].substring(9)) && !tile.equals(row[6].substring(9))))
                    throw bad(file, "invalid legacy destination annotation for " + tile);
                exceptions.put(tile, row[6]);
            }
        }
        if (!refs.keySet().equals(known)) throw bad(file, "point rows must cover every active tile exactly once");
        file = "kome_tile_route_defaults.csv";
        for (String[] row : rows(loader, manifest, file, "from,to,type")) {
            String a = tile(row[0], known, file), b = tile(row[1], known, file), type = row[2];
            if (a.compareTo(b) >= 0) throw bad(file, "endpoints must be distinct and in canonical order: " + a + "/" + b);
            if (!Arrays.asList(KOMEConquestRouteEdge.OPEN, KOMEConquestRouteEdge.RIVER, KOMEConquestRouteEdge.BRIDGE,
                    KOMEConquestRouteEdge.MOUNTAIN, KOMEConquestRouteEdge.MOUNTAIN_PASS, KOMEConquestRouteEdge.BLOCKED).contains(type))
                throw bad(file, "unknown route type " + type);
            String key = KOMEConquestRouteEdge.key(a, b);
            if (routes.put(key, type) != null) throw bad(file, "duplicate route " + key);
            addNeighbor(neighbors, a, b); addNeighbor(neighbors, b, a);
        }
        if (routes.isEmpty()) throw bad(file, "empty route set");
        file = "kome_tile_waypoint_candidates.csv";
        Set<String> waypointKeys = new HashSet<String>();
        for (LOTRWaypoint w : LOTRWaypoint.values()) if (!w.isHidden()) waypointKeys.add(w.getCodeName());
        Set<String> used = new HashSet<String>();
        for (String[] row : rows(loader, manifest, file, "tile,priority,waypoint")) {
            String tile = tile(row[0], known, file);
            if (!waypointKeys.contains(row[2]) || !used.add(row[2])) throw bad(file, "unknown/duplicate waypoint " + row[2]);
            List<String> list = waypoints.get(tile);
            if (list == null) { list = new ArrayList<String>(); waypoints.put(tile, list); }
            if (integer(row[1], file) != list.size()) throw bad(file, "priorities must start at zero and be consecutive for " + tile);
            list.add(row[2]);
        }
        if (waypoints.isEmpty()) throw bad(file, "empty waypoint candidates");
        file = "kome_tile_route_markers.csv";
        Set<String> markerKeys = new HashSet<String>();
        for (String[] row : rows(loader, manifest, file, "from,to,type,x,y,z,mapX,mapY")) {
            String a = tile(row[0], known, file), b = tile(row[1], known, file);
            if (a.compareTo(b) >= 0 || !(row[2].equals(KOMEConquestRouteEdge.BRIDGE) || row[2].equals(KOMEConquestRouteEdge.RIVER))
                    || !row[2].equals(routes.get(KOMEConquestRouteEdge.key(a, b))))
                throw bad(file, "marker must reference a matching canonical route " + a + "/" + b);
            int mx = integer(row[6], file), my = integer(row[7], file);
            if (mx < 0 || my < 0 || mx >= 3200 || my >= 4000) throw bad(file, "marker outside baseline map extent");
            String key = a + "/" + b + "/" + mx + "/" + my;
            if (!markerKeys.add(key)) throw bad(file, "duplicate marker " + key);
            markers.add(new Marker(a, b, row[2], new Point(number(row[3], file), number(row[4], file), number(row[5], file)), mx, my));
        }
        Set<String> markedRoutes = new HashSet<String>();
        for (Marker m : markers) markedRoutes.add(KOMEConquestRouteEdge.key(m.from, m.to));
        for (Map.Entry<String, String> e : routes.entrySet())
            if ((e.getValue().equals(KOMEConquestRouteEdge.BRIDGE) || e.getValue().equals(KOMEConquestRouteEdge.RIVER))
                    && !markedRoutes.contains(e.getKey())) throw bad(file, "missing marker for " + e.getKey());
        return new KOMETileGameplayDefaults(refs, arrivals, exceptions, routes, neighbors, waypoints, markers);
    }

    private static void addNeighbor(Map<String, Set<String>> map, String a, String b) {
        Set<String> set = map.get(a);
        if (set == null) { set = new TreeSet<String>(); map.put(a, set); }
        set.add(b);
    }
    private static String tile(String s, Set<String> known, String file) throws IOException {
        if (!known.contains(s) || !s.equals(KOMEConquestTile.normalizeId(s))) throw bad(file, "unknown/noncanonical tile " + s);
        return s;
    }
    private static double number(String s, String file) throws IOException {
        try {
            double value = Double.parseDouble(s);
            if (!Double.isNaN(value) && !Double.isInfinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return value;
        } catch (NumberFormatException ignored) { }
        throw bad(file, "invalid finite block coordinate " + s);
    }
    private static int integer(String s, String file) throws IOException {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { throw bad(file, "invalid integer " + s); }
    }
    private static IOException bad(String file, String why) { return new IOException(ROOT + file + ": " + why); }
    private static List<String[]> rows(ClassLoader loader, Properties manifest, String file, String header) throws IOException {
        List<String[]> result = new ArrayList<String[]>();
        byte[] bytes = readResource(loader, file);
        String expected = manifest.getProperty(file, "");
        try {
            StringBuilder actual = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
                actual.append(String.format(Locale.ROOT, "%02x", value & 255));
            if (!actual.toString().equals(expected)) throw bad(file, "SHA-256 mismatch against gameplay manifest");
        } catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 is unavailable", e); }
        // Fixed-schema authoring CSV: no quoted fields, embedded commas, or multiline values.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            if (!VERSION.equals(reader.readLine())) throw bad(file, "missing/unsupported gameplay resource version");
            if (!header.equals(reader.readLine())) throw bad(file, "unexpected columns");
            String line; int length = 0, rowNumber = 2, columns = header.split(",").length;
            while ((line = reader.readLine()) != null) {
                rowNumber++; length += line.length();
                if (length > 1048576 || result.size() >= 65536) throw bad(file, "resource exceeds supported size");
                String[] row = line.split(",", -1);
                if (row.length != columns || line.indexOf('"') >= 0) throw bad(file, "invalid row " + rowNumber);
                for (String s : row) if (!s.equals(s.trim())) throw bad(file, "untrimmed value at row " + rowNumber);
                result.add(row);
            }
        }
        return result;
    }
    private static byte[] readResource(ClassLoader loader, String file) throws IOException {
        InputStream stream = loader.getResourceAsStream(ROOT + file);
        if (stream == null) throw bad(file, "missing resource");
        try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) {
                if (bytes.size() + n > 1048576) throw bad(file, "resource exceeds 1 MiB");
                bytes.write(buffer, 0, n);
            }
            return bytes.toByteArray();
        }
    }
    public static final class Point {
        public final double x, y, z;
        private Point(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public int dimensionId() { return LOTRDimension.MIDDLE_EARTH.dimensionID; }
    }
    static final class Marker {
        final String from, to, type;
        final Point point;
        final int mapX, mapY;
        Marker(String from, String to, String type, Point point, int mapX, int mapY) {
            this.from = from; this.to = to; this.type = type; this.point = point; this.mapX = mapX; this.mapY = mapY;
        }
    }
}
