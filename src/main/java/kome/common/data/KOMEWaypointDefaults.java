package kome.common.data;

import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KOMEWaypointDefaults {
    private static final String DEFAULTS_CSV = "assets/kome/config/kome_waypoint_defaults.csv";
    private static final Map<String, Entry> defaultsByWaypointKey = new HashMap<String, Entry>();
    private static boolean loaded;
    private static int loadedEntries;

    public static Entry forLink(KOMETileWaypointLink link) {
        ensureLoaded();
        if (link == null) {
            return null;
        }
        Entry entry = defaultsByWaypointKey.get(normalizeLookupKey(link.lotrWaypointKey));
        if (entry == null) {
            entry = defaultsByWaypointKey.get(normalizeLookupKey(link.waypointName));
        }
        if (entry == null) {
            entry = defaultsByWaypointKey.get(normalizeLookupKey(link.waypointDisplayName));
        }
        return entry;
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        InputStream input = null;
        try {
            input = KOMEWaypointDefaults.class.getClassLoader().getResourceAsStream(DEFAULTS_CSV);
            if (input == null) {
                System.out.println("[KOME] Waypoint defaults file missing: " + DEFAULTS_CSV);
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
                    validateWaypoint(entry);
                    put(entry.waypointIdSuggestion, entry);
                    put(entry.defaultWaypointName, entry);
                    loadedEntries++;
                }
            } finally {
                reader.close();
            }
        } catch (Throwable e) {
            System.out.println("[KOME] Failed to load waypoint defaults: " + e.getClass().getSimpleName()
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
        defaultsByWaypointKey.clear();
        ensureLoaded();
    }

    public static int loadedEntryCount() {
        ensureLoaded();
        return loadedEntries;
    }

    private static Entry readEntry(Map<String, Integer> columns, List<String> values) {
        String waypointName = get(values, columns, "default_waypoint_name");
        String waypointKey = get(values, columns, "waypoint_id_suggestion");
        if (normalizeLookupKey(waypointName).length() == 0 && normalizeLookupKey(waypointKey).length() == 0) {
            warn("Unknown waypoint row with no waypoint name/key.");
            return null;
        }
        int level = parseLevel(get(values, columns, "level"), waypointName.length() > 0 ? waypointName : waypointKey);
        String faction = parseDefaultFaction(get(values, columns, "current_ruling_faction"), waypointName.length() > 0 ? waypointName : waypointKey);
        Entry entry = new Entry();
        entry.defaultWaypointName = waypointName;
        entry.waypointIdSuggestion = waypointKey;
        entry.waypointLevel = level;
        entry.mapRegion = get(values, columns, "map_region");
        entry.defaultRulingFaction = faction;
        return entry;
    }

    private static int parseLevel(String value, String waypointName) {
        String text = value == null ? "" : value.trim();
        if (text.length() == 0) {
            return 0;
        }
        try {
            int level = Integer.parseInt(text.replaceAll("[^0-9]", ""));
            if (level >= 1 && level <= 3) {
                return level;
            }
        } catch (Exception ignored) {
        }
        warn("Invalid waypoint level '" + value + "' for " + waypointName + "; expected 1, 2, or 3.");
        return 0;
    }

    private static String parseDefaultFaction(String value, String waypointName) {
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
        warn("Unknown default ruling faction '" + value + "' for waypoint " + waypointName + ".");
        return "";
    }

    private static void put(String key, Entry entry) {
        String normalized = normalizeLookupKey(key);
        if (normalized.length() > 0) {
            Entry existing = defaultsByWaypointKey.get(normalized);
            if (existing != null && existing != entry) {
                warn("Duplicate waypoint default key/name '" + key + "' for " + entry.label()
                    + "; keeping the later row and replacing " + existing.label() + ".");
            }
            defaultsByWaypointKey.put(normalized, entry);
        }
    }

    private static void validateWaypoint(Entry entry) {
        if (entry == null) {
            return;
        }
        if (resolveWaypoint(entry.waypointIdSuggestion) != null || resolveWaypoint(entry.defaultWaypointName) != null) {
            return;
        }
        warn("Unknown waypoint name/key in defaults file: " + entry.label() + ".");
    }

    private static LOTRWaypoint resolveWaypoint(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        LOTRWaypoint waypoint = LOTRWaypoint.waypointForName(value);
        if (waypoint != null) {
            return waypoint;
        }
        waypoint = LOTRWaypoint.waypointForName(value.toUpperCase(Locale.ROOT));
        if (waypoint != null) {
            return waypoint;
        }
        String normalized = normalizeLookupKey(value);
        for (LOTRWaypoint candidate : LOTRWaypoint.values()) {
            if (candidate == null || candidate.isHidden()) {
                continue;
            }
            if (normalizeLookupKey(candidate.getCodeName()).equals(normalized)) {
                return candidate;
            }
            try {
                if (normalizeLookupKey(candidate.getDisplayName()).equals(normalized)) {
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
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

    private static String normalizeLookupKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = Character.toLowerCase(normalized.charAt(i));
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                key.append(c);
            }
        }
        return key.toString();
    }

    private static void warn(String message) {
        System.out.println("[KOME] " + message);
    }

    public static class Entry {
        public String defaultWaypointName = "";
        public String waypointIdSuggestion = "";
        public int waypointLevel;
        public String mapRegion = "";
        public String defaultRulingFaction = "";

        public String label() {
            if (waypointIdSuggestion != null && waypointIdSuggestion.length() > 0) {
                return waypointIdSuggestion;
            }
            return defaultWaypointName == null || defaultWaypointName.length() == 0 ? "unknown waypoint" : defaultWaypointName;
        }
    }
}
