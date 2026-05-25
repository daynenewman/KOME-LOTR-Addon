package kome.common.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KOMEServerRecordBuilder {
    public static List build(World world) {
        KOMEWorldData data = KOMEWorldData.get(world);
        Set<UUID> playerIDs = new HashSet<>();
        playerIDs.addAll(data.playerNames.keySet());
        playerIDs.addAll(data.progressions.keySet());
        playerIDs.addAll(data.populations.keySet());
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record.owner != null) {
                playerIDs.add(record.owner);
            }
        }

        List<PlayerRecord> records = new ArrayList<>();
        for (UUID playerID : playerIDs) {
            records.add(new PlayerRecord(playerID, getPlayerName(data, playerID)));
        }
        Collections.sort(records, new Comparator<PlayerRecord>() {
            @Override
            public int compare(PlayerRecord a, PlayerRecord b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        List lines = new ArrayList();
        lines.add(join("SUMMARY", String.valueOf(records.size()), String.valueOf(data.territories.size()), String.valueOf(getClaimedTileCount(data))));
        for (PlayerRecord record : records) {
            addPlayerLine(lines, data, world, record);
        }
        if (records.isEmpty()) {
            lines.add(join("EMPTY", "No player records have been saved yet."));
        }
        return lines;
    }

    private static void addPlayerLine(List lines, KOMEWorldData data, World world, PlayerRecord record) {
        KOMEPlayerProgression progression = data.progressions.get(record.id);
        KOMEPlayerPopulation pop = data.populations.get(record.id);
        if (pop == null) {
            pop = new KOMEPlayerPopulation();
        }

        TerritorySummary territories = getTerritories(data, record.name);
        TerritorySummary tiles = getConquestTiles(data, record.name);
        FactionInfo faction = getFactionInfo(data, world, record.id, progression);

        lines.add(join(
            "PLAYER",
            record.id.toString(),
            record.name,
            faction.name,
            getRank(data, record.id, progression, faction.key),
            getProgressionSummary(progression),
            getPopulationSummary(pop),
            String.valueOf(tiles.count),
            String.valueOf(territories.count),
            joinNames(tiles.names),
            joinNames(territories.names)
        ));
    }

    private static String getProgressionSummary(KOMEPlayerProgression progression) {
        if (progression == null) {
            return "0/" + KOMEProgressionAchievement.ALL.size();
        }
        return progression.getCompletedCount(null) + "/" + progression.getTotalCount(null);
    }

    private static String getPopulationSummary(KOMEPlayerPopulation pop) {
        return "Off " + pop.offensiveTotal + ", Def " + pop.defensiveTotal + ", Total " + pop.getCombinedTotal();
    }

    private static String getRank(KOMEWorldData data, UUID playerID, KOMEPlayerProgression progression, String factionKey) {
        if (isGroupComplete(progression, "prince_king")) {
            return data.isFactionKing(factionKey, playerID) ? "King" : "Prince";
        }
        if (isGroupComplete(progression, "lord")) {
            return "Prince";
        }
        if (isGroupComplete(progression, "knight")) {
            return "Lord";
        }
        if (isGroupComplete(progression, "serf")) {
            return "Knight";
        }
        if (isGroupComplete(progression, "wanderer")) {
            return "Serf";
        }
        return "Wanderer";
    }

    private static boolean isGroupComplete(KOMEPlayerProgression progression, String group) {
        return progression != null && progression.getTotalCount(group) > 0 && progression.getCompletedCount(group) >= progression.getTotalCount(group);
    }

    private static FactionInfo getFactionInfo(KOMEWorldData data, World world, UUID playerID, KOMEPlayerProgression progression) {
        EntityPlayer player = world.func_152378_a(playerID);
        if (player != null) {
            LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
            if (pledge != null) {
                return new FactionInfo(pledge.codeName(), pledge.factionName());
            }
        }
        String faction = progression == null ? "" : progression.getPledgedLordFaction();
        return new FactionInfo(faction, faction == null || faction.trim().isEmpty() ? "No faction" : faction);
    }

    private static TerritorySummary getTerritories(KOMEWorldData data, String playerName) {
        TerritorySummary summary = new TerritorySummary();
        for (KOMETerritory territory : data.territories.values()) {
            if (matchesPlayer(territory.ruler, playerName)) {
                summary.count++;
                summary.names.add(territory.displayName == null || territory.displayName.trim().isEmpty() ? territory.waypoint : territory.displayName);
            }
        }
        Collections.sort(summary.names);
        return summary;
    }

    private static TerritorySummary getConquestTiles(KOMEWorldData data, String playerName) {
        TerritorySummary summary = new TerritorySummary();
        for (KOMEConquestTile tile : data.conquestTiles.values()) {
            if (tile.isClaimed() && matchesPlayer(tile.ruler, playerName)) {
                summary.count++;
                summary.names.add(tile.id);
            }
        }
        Collections.sort(summary.names);
        return summary;
    }

    private static boolean matchesPlayer(String value, String playerName) {
        return value != null && playerName != null && value.trim().equalsIgnoreCase(playerName.trim());
    }

    private static String joinNames(List names) {
        if (names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    private static int getClaimedTileCount(KOMEWorldData data) {
        int count = 0;
        for (KOMEConquestTile tile : data.conquestTiles.values()) {
            if (tile.isClaimed()) {
                count++;
            }
        }
        return count;
    }

    private static String getPlayerName(KOMEWorldData data, UUID playerID) {
        String known = data.playerNames.get(playerID);
        if (known != null && known.trim().length() > 0) {
            return known;
        }
        if (MinecraftServer.getServer() != null) {
            net.minecraft.entity.player.EntityPlayerMP player = MinecraftServer.getServer().getConfigurationManager().func_152612_a(playerID.toString());
            if (player != null) {
                return player.getCommandSenderName();
            }
        }
        String id = playerID == null ? "Unknown" : playerID.toString();
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static String join(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append('\t');
            }
            sb.append(clean(values[i]));
        }
        return sb.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }

    private static class PlayerRecord {
        private final UUID id;
        private final String name;

        private PlayerRecord(UUID id, String name) {
            this.id = id;
            this.name = name == null ? "" : name;
        }
    }

    private static class TerritorySummary {
        private int count;
        private final List names = new ArrayList();
    }

    private static class FactionInfo {
        private final String key;
        private final String name;

        private FactionInfo(String key, String name) {
            this.key = key == null ? "" : key;
            this.name = name == null ? "" : name;
        }
    }
}
