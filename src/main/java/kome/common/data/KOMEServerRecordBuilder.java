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
        lines.add(join("SUMMARY", String.valueOf(records.size()), String.valueOf(getClaimedTileCount(data)), String.valueOf(data.wars.size())));
        for (PlayerRecord record : records) {
            addPlayerLine(lines, data, world, record);
        }
        if (records.isEmpty()) {
            lines.add(join("EMPTY", "No player records have been saved yet."));
        }
        for (KOMEWar war : KOMEWarService.sortedWars(data)) addWarLine(lines, data, war);
        return lines;
    }

    private static void addWarLine(List lines, KOMEWorldData data, KOMEWar war) {
        KOMEWar.TileCaptureEvent latest = war.tileCaptureHistory.isEmpty() ? null
            : war.tileCaptureHistory.get(war.tileCaptureHistory.size() - 1);
        String latestCapture = latest == null ? "No tile captures" : latest.tileId + ": "
            + displayFaction(latest.formerOwner) + " -> " + displayFaction(latest.newOwner) + " by "
            + latest.claimantName + " @ " + latest.timestamp + " (" + latest.claimMethod + ")";
        int activeCompanies = 0;
        int pending = 0;
        List<String> coordinators = new ArrayList<String>();
        List<String> reservations = new ArrayList<String>();
        for (KOMEWar.StewardshipAuthorization authorization : war.stewardshipAuthorizations) {
            if ("ACTIVE".equals(authorization.state)) activeCompanies++;
            if (!"ACTIVE".equals(authorization.state) && !"DEMOBILIZED".equals(authorization.state)) pending++;
            coordinators.add(authorization.controllerName + " (" + displayFaction(authorization.controllerFaction) + ")");
            reservations.add(authorization.companyId + "=" + authorization.reservation + " [" + authorization.state + "]");
        }
        List<String> warnings = new ArrayList<String>();
        for (String faction : war.sideOneFactions) warnings.addAll(KOMEWarService.contradictoryMemberships(data, faction));
        for (String faction : war.sideTwoFactions) warnings.addAll(KOMEWarService.contradictoryMemberships(data, faction));
        List<String> captures = new ArrayList<String>();
        for (KOMEWar.TileCaptureEvent event : war.tileCaptureHistory) {
            captures.add(event.tileId + " " + displayFaction(event.formerOwner) + " -> " + displayFaction(event.newOwner)
                + " by " + event.claimantName + " @ " + event.timestamp);
        }
        List<String> administration = new ArrayList<String>();
        for (KOMEWar.AdministrativeEvent event : war.administrativeHistory) {
            administration.add(event.action + " by " + event.actor + ": " + event.detail + " @ " + event.timestamp);
        }
        List<String> memberships = new ArrayList<String>();
        for (KOMEWar.MembershipRecord membership : war.membershipHistory) {
            memberships.add(displayFaction(membership.faction) + " side " + membership.side + " [" + membership.source + "]"
                + (membership.nativeFaction.length() == 0 ? "" : " for native " + displayFaction(membership.nativeFaction))
                + (membership.active ? " active" : " ended: " + membership.endReason));
        }
        List<String> supportEnrollments = new ArrayList<String>();
        for (KOMEWar.MilitarySupportEnrollment enrollment : war.militarySupportEnrollments) {
            supportEnrollments.add(displayFaction(enrollment.supportingFaction) + " supporting "
                + displayFaction(enrollment.nativeFaction) + " [" + enrollment.state + "] king="
                + (enrollment.authorizedKingName.length() == 0 ? "none" : enrollment.authorizedKingName + "/" + enrollment.authorizedKing)
                + ": " + enrollment.reason);
            if ("CONTRADICTION".equals(enrollment.state)) warnings.add(enrollment.reason);
        }
        lines.add(join("WAR", war.id, war.displayName.length() == 0 ? war.id : war.displayName, war.status,
            war.sideOneName, displayFactions(war.sideOneFactions), war.sideTwoName, displayFactions(war.sideTwoFactions),
            String.valueOf(war.createdAtMillis), latestCapture, String.valueOf(activeCompanies),
            warnings.isEmpty() ? "None" : joinNames(warnings), joinNames(captures), joinNames(coordinators),
            joinNames(reservations), String.valueOf(pending), joinNames(administration), war.endingReason,
            String.valueOf(war.endingAtMillis), String.valueOf(war.endedAtMillis), joinNames(memberships),
            joinNames(supportEnrollments)));
    }

    private static String displayFactions(Set<String> factions) {
        List<String> names = new ArrayList<String>();
        for (String faction : factions) names.add(displayFaction(faction));
        Collections.sort(names);
        return joinNames(names);
    }

    private static void addPlayerLine(List lines, KOMEWorldData data, World world, PlayerRecord record) {
        KOMEPlayerProgression progression = data.progressions.get(record.id);
        KOMEPlayerPopulation pop = data.populations.get(record.id);
        if (pop == null) {
            pop = new KOMEPlayerPopulation();
        }

        FactionInfo faction = getFactionInfo(data, world, record.id, progression);
        TileSummary tiles = getConquestTiles(data, faction.key);

        lines.add(join(
            "PLAYER",
            record.id.toString(),
            record.name,
            faction.name,
            getRank(data, record.id, progression, faction.key),
            getProgressionSummary(progression),
            getPopulationSummary(pop),
            progression == null ? "No pledged lord" : progression.getPledgedLordDisplay(),
            getAllianceSummary(data, faction.key),
            String.valueOf(tiles.count),
            joinNames(tiles.names)
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
                String key = KOMEAlliance.normalizeFactionKey(pledge.codeName());
                return new FactionInfo(key, key.length() == 0 ? "Unpledged" : pledge.factionName());
            }
        }
        String faction = progression == null ? "" : progression.getPledgedLordFaction();
        LOTRFaction resolved = findFaction(faction);
        if (resolved != null) {
            String key = KOMEAlliance.normalizeFactionKey(resolved.codeName());
            return new FactionInfo(key, key.length() == 0 ? "Unpledged" : resolved.factionName());
        }
        String key = KOMEAlliance.normalizeFactionKey(faction);
        return new FactionInfo(key, key.length() == 0 ? "Unpledged" : KOMEAlliance.displayFactionName(faction));
    }

    private static TileSummary getConquestTiles(KOMEWorldData data, String factionKey) {
        TileSummary summary = new TileSummary();
        for (KOMEConquestTile tile : data.conquestTiles.values()) {
            if (tile.isClaimed() && factionMatches(tile.currentRulingFaction(), factionKey)) {
                summary.count++;
                summary.names.add(tile.id);
            }
        }
        Collections.sort(summary.names);
        return summary;
    }

    private static boolean factionMatches(String value, String factionKey) {
        return value != null && factionKey != null && KOMEAlliance.normalizeFactionKey(value).equals(KOMEAlliance.normalizeFactionKey(factionKey));
    }

    private static String getAllianceSummary(KOMEWorldData data, String factionKey) {
        if (factionKey == null || factionKey.trim().isEmpty()) {
            return "No faction alliances";
        }
        List lines = new ArrayList();
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || !alliance.hasAnyAlliance()) {
                continue;
            }
            if (factionMatches(alliance.factionA, factionKey)) {
                lines.add("To " + displayFaction(alliance.factionB) + ": " + tierSummary(alliance));
            } else if (factionMatches(alliance.factionB, factionKey)) {
                lines.add("From " + displayFaction(alliance.factionA) + ": " + tierSummary(alliance));
            }
        }
        Collections.sort(lines);
        return joinNames(lines);
    }

    private static String tierSummary(KOMEAlliance alliance) {
        return "C " + displayTier(alliance.civilTier) + ", M " + displayTier(alliance.militaryTier) + ", T " + displayTier(alliance.tradeTier);
    }

    private static String displayTier(int tier) {
        return tier == KOMEAlliance.PENDING ? "Pending" : tier == KOMEAlliance.NONE ? "None" : "T" + tier;
    }

    private static String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private static LOTRFaction findFaction(String value) {
        LOTRFaction direct = LOTRFaction.forName(value);
        if (direct != null) {
            return direct;
        }
        String normalized = KOMEAlliance.normalizeFactionKey(value);
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null
                && (KOMEAlliance.normalizeFactionKey(faction.codeName()).equals(normalized)
                || KOMEAlliance.normalizeFactionKey(faction.factionName()).equals(normalized))) {
                return faction;
            }
        }
        return null;
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

    private static class TileSummary {
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
