package kome.common.data;

import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.world.World;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict, all-or-nothing fresh-world capital definition source. */
public final class KOMEFactionCapitalDefaults {
    public static final String RESOURCE = "assets/kome/config/kome_faction_capital_defaults.csv";
    private static List<Definition> loaded;
    private KOMEFactionCapitalDefaults() { }

    public static synchronized List<Definition> definitions() {
        if (loaded == null) loaded = Collections.unmodifiableList(loadResource());
        return loaded;
    }

    static List<Definition> validateComplete(List<Definition> candidate) {
        if (candidate == null) throw new IllegalArgumentException("Capital defaults are missing.");
        List<String> supported = KOMEAlliance.allFactionKeys();
        Map<String, Definition> byFaction = new LinkedHashMap<String, Definition>();
        for (Definition definition : candidate) {
            if (definition == null)
                throw new IllegalArgumentException("Capital defaults contain a null row.");
            String faction = KOMEAlliance.normalizeFactionKey(definition.factionId);
            if (!faction.equals(definition.factionId) || !supported.contains(faction))
                throw new IllegalArgumentException("Unsupported or noncanonical capital faction: "
                    + definition.factionId);
            if (byFaction.put(faction, definition) != null)
                throw new IllegalArgumentException("Duplicate capital default for faction: " + faction);
            definition.resolveAndValidate();
        }
        if (byFaction.size() != supported.size())
            throw new IllegalArgumentException("Capital defaults must define exactly " + supported.size()
                + " supported factions; found " + byFaction.size() + ".");
        List<Definition> ordered = new ArrayList<Definition>();
        for (String faction : supported) {
            Definition definition = byFaction.get(faction);
            if (definition == null)
                throw new IllegalArgumentException("Missing capital default for faction: " + faction);
            ordered.add(definition);
        }
        return ordered;
    }

    static Map<String, KOMEFactionCapitalRecord> resolveLive(World middleEarth, long nowMillis) {
        if (middleEarth == null || middleEarth.provider == null
                || middleEarth.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID)
            throw new IllegalArgumentException(
                "The live Middle-earth world is required to initialize faction capitals.");
        Map<String, KOMEFactionCapitalRecord> result =
            new LinkedHashMap<String, KOMEFactionCapitalRecord>();
        for (Definition definition : definitions()) {
            LOTRWaypoint waypoint = definition.resolveAndValidate();
            int x = waypoint.getXCoord(), z = waypoint.getZCoord();
            int liveY = waypoint.getYCoord(middleEarth, x, z);
            KOMEStrategicDeploymentResolver.Validation safe =
                KOMEStrategicDeploymentResolver.resolveAround(middleEarth,
                    definition.expectedTileId, x + 0.5D, liveY, z + 0.5D,
                    KOMEStrategicDeploymentResolver.DEFAULT_SEARCH_RADIUS);
            if (!safe.valid)
                throw new IllegalArgumentException(definition.factionId + " / "
                    + definition.waypointKey + ": " + safe.reason);
            KOMEStrategicDeploymentResolver.Anchor anchor = safe.anchor;
            result.put(definition.factionId, new KOMEFactionCapitalRecord(
                definition.factionId, definition.expectedTileId, anchor.dimensionId,
                anchor.x, anchor.y, anchor.z, nowMillis,
                "SERVER_DEFAULT_WAYPOINT:" + definition.waypointKey, "SERVER"));
        }
        return KOMEFactionCapitalService.validateCompleteSet(result);
    }

    /** Metadata-only fixture for legacy unit tests that do not construct a live World. */
    static Map<String, KOMEFactionCapitalRecord> metadataFixture(long nowMillis) {
        Map<String, KOMEFactionCapitalRecord> result =
            new LinkedHashMap<String, KOMEFactionCapitalRecord>();
        for (Definition definition : definitions()) {
            LOTRWaypoint waypoint = definition.resolveAndValidate();
            result.put(definition.factionId, new KOMEFactionCapitalRecord(
                definition.factionId, definition.expectedTileId,
                LOTRDimension.MIDDLE_EARTH.dimensionID,
                waypoint.getXCoord() + 0.5D, 80.0D, waypoint.getZCoord() + 0.5D,
                nowMillis, "TEST_METADATA_FIXTURE:" + definition.waypointKey, "TEST"));
        }
        return KOMEFactionCapitalService.validateCompleteSet(result);
    }

    private static List<Definition> loadResource() {
        InputStream input = KOMEFactionCapitalDefaults.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Required capital defaults file is missing: " + RESOURCE);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            try {
                String header = reader.readLine();
                if (!"faction_id,waypoint_key,dimension,expected_tile_id".equals(
                        header == null ? "" : header.trim()))
                    throw new IllegalArgumentException("Capital defaults header is invalid.");
                List<Definition> rows = new ArrayList<Definition>();
                String line;
                int lineNumber = 1;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.trim().length() == 0 || line.trim().startsWith("#")) continue;
                    String[] values = line.split(",", -1);
                    if (values.length != 4)
                        throw new IllegalArgumentException("Malformed capital default row " + lineNumber + ".");
                    rows.add(new Definition(values[0].trim(), values[1].trim(),
                        values[2].trim(), values[3].trim()));
                }
                return validateComplete(rows);
            } finally {
                reader.close();
            }
        } catch (RuntimeException problem) {
            throw problem;
        } catch (Exception problem) {
            throw new IllegalStateException("Could not read required capital defaults: "
                + problem.getMessage(), problem);
        } finally {
            try { input.close(); } catch (Exception ignored) { }
        }
    }

    public static final class Definition {
        public final String factionId, waypointKey, dimension, expectedTileId;
        public Definition(String factionId, String waypointKey, String dimension, String expectedTileId) {
            this.factionId = factionId == null ? "" : factionId.trim();
            this.waypointKey = waypointKey == null ? "" : waypointKey.trim();
            this.dimension = dimension == null ? "" : dimension.trim().toLowerCase(Locale.ROOT);
            this.expectedTileId = KOMEConquestTile.normalizeId(expectedTileId);
        }

        LOTRWaypoint resolveAndValidate() {
            if (!"middle_earth".equals(dimension))
                throw new IllegalArgumentException("Capital " + factionId
                    + " has unsupported dimension: " + dimension);
            LOTRWaypoint waypoint;
            try {
                waypoint = LOTRWaypoint.valueOf(waypointKey);
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Unknown LOTR waypoint " + waypointKey
                    + " for " + factionId + ".");
            }
            if (waypoint == null || waypoint.isHidden())
                throw new IllegalArgumentException("Unavailable LOTR waypoint " + waypointKey
                    + " for " + factionId + ".");
            String tile = KOMEBuildService.tileAtWorldCoordinates(
                waypoint.getXCoord(), waypoint.getZCoord());
            if (!expectedTileId.equals(tile))
                throw new IllegalArgumentException("Capital " + factionId + " waypoint "
                    + waypointKey + " resolves to " + tile + ", expected " + expectedTileId + ".");
            return waypoint;
        }
    }
}
