package kome.client.gui;

import java.util.ArrayList;
import java.util.List;

final class KOMEServerRecordPresentation {
    private KOMEServerRecordPresentation() {
    }

    static PopulationSummary parsePopulationSummary(String value) {
        int offensive = parseMetric(value, "Off");
        int defensive = parseMetric(value, "Def");
        int total = parseMetric(value, "Total");
        if (total < 0) {
            total = Math.max(0, offensive) + Math.max(0, defensive);
        }
        return new PopulationSummary(Math.max(0, offensive), Math.max(0, defensive), Math.max(0, total));
    }

    static List parseAllianceSummaries(String value) {
        List summaries = new ArrayList();
        if (value == null) {
            return summaries;
        }
        String raw = value.trim();
        if (raw.length() == 0 || raw.toLowerCase().startsWith("no alliance")
                || raw.toLowerCase().startsWith("no faction alliance")) {
            return summaries;
        }
        String[] entries = raw.split(", (?=(?:To|From) )");
        for (String entry : entries) {
            String text = entry == null ? "" : entry.trim();
            if (text.startsWith("To ")) {
                text = text.substring(3);
            } else if (text.startsWith("From ")) {
                text = text.substring(5);
            }
            int detailStart = text.indexOf(": ");
            if (detailStart < 0) {
                continue;
            }
            String faction = text.substring(0, detailStart).trim();
            String details = text.substring(detailStart + 2).trim();
            if (faction.length() == 0) {
                continue;
            }
            if (details.startsWith("Stage ")) {
                summaries.add(new AllianceSummary(faction,
                    valueAfter(details, "Stage "), valueAfter(details, "Partner "),
                    valueAfter(details, "Shared ")));
            } else if ("Pending".equalsIgnoreCase(details)) {
                summaries.add(new AllianceSummary(faction, "Pending", "-", "Pending"));
            } else {
                // Legacy record compatibility during rolling upgrades.
                summaries.add(new AllianceSummary(faction,
                    displayAllianceTier(findTrackValue(details, "C")),
                    displayAllianceTier(findTrackValue(details, "M")),
                    displayAllianceTier(findTrackValue(details, "T"))));
            }
        }
        return summaries;
    }

    static List parseTileIds(String names) {
        List tiles = new ArrayList();
        for (Object value : parseTileEntries(names)) {
            tiles.add(((TileEntry) value).label);
        }
        return tiles;
    }

    static List parseTileEntries(String names) {
        List tiles = new ArrayList();
        if (names == null || names.trim().length() == 0) {
            return tiles;
        }
        String[] values = names.split(",");
        for (String value : values) {
            String tile = value == null ? "" : value.trim();
            if (tile.length() > 0) {
                String tileId = tile;
                String waypoint = "";
                int open = tile.lastIndexOf(" (");
                if (open > 0 && tile.endsWith(")")) {
                    String candidate = tile.substring(open + 2, tile.length() - 1).trim();
                    if (isTileId(candidate)) {
                        tileId = candidate.toUpperCase();
                        waypoint = tile.substring(0, open).trim();
                    }
                } else if (isTileId(tile)) {
                    tileId = tile.toUpperCase();
                }
                String label = waypoint.length() == 0 ? tileId : waypoint + " (" + tileId + ")";
                tiles.add(new TileEntry(tileId, waypoint, label));
            }
        }
        return tiles;
    }

    private static boolean isTileId(String value) {
        if (value == null || value.length() < 2 || (value.charAt(0) != 'T' && value.charAt(0) != 't')) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static TileBadgeLayout computeTileBadgeLayout(int tileCount, int badgeWidth, int width,
            int horizontalPadding, int gap, int maxRows) {
        tileCount = Math.max(0, tileCount);
        badgeWidth = Math.max(1, badgeWidth);
        gap = Math.max(0, gap);
        maxRows = Math.max(1, maxRows);
        int usableWidth = Math.max(1, width - Math.max(0, horizontalPadding));
        int perRow = Math.max(1, (usableWidth + gap) / (badgeWidth + gap));
        int capacity = Math.max(1, perRow * maxRows);
        int visible = Math.min(tileCount, capacity);
        int hidden = Math.max(0, tileCount - visible);
        if (hidden > 0 && visible > 0) {
            visible--;
            hidden++;
        }
        int badges = visible + (hidden > 0 ? 1 : 0);
        int rows = badges == 0 ? 0 : (badges + perRow - 1) / perRow;
        return new TileBadgeLayout(tileCount, badgeWidth, perRow, visible, hidden, rows);
    }

    static boolean isUnlockedTier(String tier) {
        if (tier == null || tier.length() < 2 || (tier.charAt(0) != 'T' && tier.charAt(0) != 't')) {
            return false;
        }
        try {
            return Integer.parseInt(tier.substring(1)) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static int parseNonNegativeInt(String value, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(value == null ? "" : value.trim()));
        } catch (NumberFormatException ignored) {
            return Math.max(0, fallback);
        }
    }

    static String countLabel(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private static int parseMetric(String value, String label) {
        if (value == null) {
            return -1;
        }
        int start = value.indexOf(label);
        if (start < 0) {
            return -1;
        }
        start += label.length();
        while (start < value.length() && !Character.isDigit(value.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (start == end) {
            return -1;
        }
        try {
            return Integer.parseInt(value.substring(start, end));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String findTrackValue(String details, String track) {
        String prefix = track + " ";
        int start = details.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        start += prefix.length();
        int end = details.indexOf(',', start);
        return (end < 0 ? details.substring(start) : details.substring(start, end)).trim();
    }

    private static String valueAfter(String details, String label) {
        int start = details.indexOf(label);
        if (start < 0) return "-";
        start += label.length();
        int end = details.indexOf(" |", start);
        return (end < 0 ? details.substring(start) : details.substring(start, end)).trim();
    }

    private static String displayAllianceTier(String tier) {
        if (tier == null) {
            return "Locked";
        }
        String value = tier.trim();
        if ("Pending".equalsIgnoreCase(value)) {
            return "Pending";
        }
        return isUnlockedTier(value) ? value.toUpperCase() : "Locked";
    }

    static final class PopulationSummary {
        final int offensive;
        final int defensive;
        final int total;

        PopulationSummary(int offensive, int defensive, int total) {
            this.offensive = offensive;
            this.defensive = defensive;
            this.total = total;
        }
    }

    static final class AllianceSummary {
        final String faction;
        final String stage;
        final String partnerStage;
        final String sharedRelation;
        /** Legacy aliases kept for old capture/test fixtures only. */
        final String civilian;
        final String military;
        final String trade;

        AllianceSummary(String faction, String stage, String partnerStage, String sharedRelation) {
            this.faction = faction;
            this.stage = stage;
            this.partnerStage = partnerStage;
            this.sharedRelation = sharedRelation;
            this.civilian = stage;
            this.military = partnerStage;
            this.trade = sharedRelation;
        }
    }

    static final class TileBadgeLayout {
        final int totalTiles;
        final int badgeWidth;
        final int perRow;
        final int visibleTiles;
        final int hiddenTiles;
        final int rows;

        TileBadgeLayout(int totalTiles, int badgeWidth, int perRow, int visibleTiles, int hiddenTiles, int rows) {
            this.totalTiles = totalTiles;
            this.badgeWidth = badgeWidth;
            this.perRow = perRow;
            this.visibleTiles = visibleTiles;
            this.hiddenTiles = hiddenTiles;
            this.rows = rows;
        }
    }

    static final class TileEntry {
        final String tileId;
        final String waypointName;
        final String label;

        TileEntry(String tileId, String waypointName, String label) {
            this.tileId = tileId;
            this.waypointName = waypointName;
            this.label = label;
        }
    }
}
