package kome.common.data;

import lotr.common.fac.LOTRFaction;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KOMETileOwnershipDefaults {
    private static final String DEFAULTS_CSV = "assets/kome/config/kome_tile_ownership_defaults.csv";
    private static final Set<String> EXTRA_KOME_FACTIONS = new HashSet<String>();
    private static final Map<String, Entry> defaultsByTileId = new HashMap<String, Entry>();
    private static boolean loaded;
    private static int loadedEntries;
    private static int ownedEntries;
    private static int unclaimedEntries;

    static {
        EXTRA_KOME_FACTIONS.add("harad");
        EXTRA_KOME_FACTIONS.add("rhudel");
    }

    public static Entry forTile(String tileId) {
        ensureLoaded();
        return defaultsByTileId.get(KOMEConquestTile.normalizeId(tileId));
    }

    public static List<Entry> entries() {
        ensureLoaded();
        return new ArrayList<Entry>(defaultsByTileId.values());
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        InputStream input = null;
        try {
            input = KOMETileOwnershipDefaults.class.getClassLoader().getResourceAsStream(DEFAULTS_CSV);
            if (input == null) {
                warn("Tile ownership defaults file missing: " + DEFAULTS_CSV);
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            try {
                String header = reader.readLine();
                if (header == null) {
                    return;
                }
                Map<String, Integer> columns = indexColumns(parseCsvLine(header));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().length() == 0) {
                        continue;
                    }
                    Entry entry = readEntry(columns, parseCsvLine(line));
                    if (entry == null) {
                        continue;
                    }
                    Entry existing = defaultsByTileId.put(entry.tileId, entry);
                    if (existing != null) {
                        warn("Duplicate tile ownership default for " + entry.tileId + "; keeping the later row.");
                    }
                    loadedEntries++;
                    if (entry.defaultRulingFaction.length() > 0) {
                        ownedEntries++;
                    } else {
                        unclaimedEntries++;
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Throwable e) {
            warn("Failed to load tile ownership defaults: " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void reload() {
        loaded = false;
        loadedEntries = 0;
        ownedEntries = 0;
        unclaimedEntries = 0;
        defaultsByTileId.clear();
        ensureLoaded();
    }

    public static int loadedEntryCount() {
        ensureLoaded();
        return loadedEntries;
    }

    public static int ownedEntryCount() {
        ensureLoaded();
        return ownedEntries;
    }

    public static int unclaimedEntryCount() {
        ensureLoaded();
        return unclaimedEntries;
    }

    private static Entry readEntry(Map<String, Integer> columns, List<String> values) {
        String tileId = KOMEConquestTile.normalizeId(get(values, columns, "tile_id"));
        if (tileId.length() == 0) {
            warn("Tile ownership default row has no tile_id.");
            return null;
        }
        if (KOMEConquestTileDefaults.isRetiredTile(tileId)) {
            return null;
        }
        if (!KOMEConquestTileDefaults.getKnownTileIds().contains(tileId)) {
            warn("Unknown tile id in ownership defaults: " + tileId + ".");
            return null;
        }
        Entry entry = new Entry();
        entry.tileId = tileId;
        entry.defaultRulingFaction = parseFaction(get(values, columns, "default_ruling_faction"), tileId);
        entry.waypointLevel = parseLevel(get(values, columns, "waypoint_level"), tileId);
        entry.mapRegion = get(values, columns, "map_region");
        entry.source = get(values, columns, "source");
        entry.reason = get(values, columns, "reason");
        entry.questionable = "yes".equalsIgnoreCase(get(values, columns, "questionable"));
        entry.balanceWeight = parseInt(get(values, columns, "balance_weight"), defaultBalanceWeight(entry));
        return entry;
    }

    private static String parseFaction(String value, String tileId) {
        String text = value == null ? "" : value.trim();
        if (text.length() == 0 || "none".equalsIgnoreCase(text) || "unclaimed".equalsIgnoreCase(text)
                || "neutral".equalsIgnoreCase(text) || "neutral zone".equalsIgnoreCase(text)) {
            return "";
        }
        LOTRFaction direct = LOTRFaction.forName(text);
        if (direct != null && direct.isPlayableAlignmentFaction()) {
            return KOMEAlliance.normalizeFactionKey(direct.codeName());
        }
        String normalized = KOMEAlliance.normalizeFactionKey(text);
        for (Object object : LOTRFaction.getPlayableAlignmentFactionNames()) {
            String factionName = (String) object;
            LOTRFaction faction = LOTRFaction.forName(factionName);
            if (faction != null && faction.isPlayableAlignmentFaction()
                    && (KOMEAlliance.normalizeFactionKey(faction.codeName()).equals(normalized)
                    || KOMEAlliance.normalizeFactionKey(faction.factionName()).equals(normalized))) {
                return KOMEAlliance.normalizeFactionKey(faction.codeName());
            }
        }
        if (EXTRA_KOME_FACTIONS.contains(normalized)) {
            return normalized;
        }
        warn("Unknown default ruling faction '" + value + "' for tile " + tileId + ".");
        return "";
    }

    private static int parseLevel(String value, String tileId) {
        String text = value == null ? "" : value.trim();
        if (text.length() == 0) {
            return 0;
        }
        try {
            int level = Integer.parseInt(text.replaceAll("[^0-9]", ""));
            if (level >= 0 && level <= 3) {
                return level;
            }
        } catch (Exception ignored) {
        }
        warn("Invalid waypoint level '" + value + "' for tile " + tileId + "; expected 0, 1, 2, or 3.");
        return 0;
    }

    private static int defaultBalanceWeight(Entry entry) {
        if (entry == null || entry.defaultRulingFaction.length() == 0) {
            return 0;
        }
        if (entry.waypointLevel == 1) {
            return 2;
        }
        if (entry.waypointLevel == 2) {
            return 4;
        }
        if (entry.waypointLevel == 3) {
            return 6;
        }
        return 1;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String get(List<String> values, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index.intValue() < 0 || index.intValue() >= values.size()) {
            return "";
        }
        return values.get(index.intValue()).trim();
    }

    private static Map<String, Integer> indexColumns(List<String> headers) {
        Map<String, Integer> columns = new HashMap<String, Integer>();
        for (int i = 0; i < headers.size(); i++) {
            columns.put(headers.get(i).trim().toLowerCase(Locale.ROOT), Integer.valueOf(i));
        }
        return columns;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<String>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        values.add(value.toString());
        return values;
    }

    private static void warn(String message) {
        System.out.println("[KOME] " + message);
    }

    public static class Entry {
        public String tileId = "";
        public String defaultRulingFaction = "";
        public int waypointLevel;
        public String mapRegion = "";
        public String source = "";
        public int balanceWeight;
        public String reason = "";
        public boolean questionable;

        public KOMEWaypointDefaults.Entry asWaypointDefaultsEntry() {
            KOMEWaypointDefaults.Entry entry = new KOMEWaypointDefaults.Entry();
            entry.defaultWaypointName = tileId;
            entry.waypointIdSuggestion = tileId;
            entry.waypointLevel = waypointLevel;
            entry.mapRegion = mapRegion;
            entry.defaultRulingFaction = defaultRulingFaction;
            return entry;
        }
    }
}
