package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.world.World;

import java.util.UUID;

public class KOMEProgressionTitles {
    private static final String TEAM_PREFIX = "kome_";

    public static void updatePlayerTitle(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        World world = KOMEReflection.getWorld(player);
        if (world == null || KOMEReflection.isRemote(world)) {
            return;
        }
        KOMEWorldData data = KOMEWorldData.get(world);
        UUID playerID = KOMEReflection.getEntityUUID(player);
        KOMEPlayerProgression progression = data.getProgression(playerID);
        Rank rank = getRank(player, data, playerID, progression);
        Scoreboard scoreboard = world.getScoreboard();
        ensureTeam(scoreboard, rank);
        String playerName = player.getCommandSenderName();
        ScorePlayerTeam current = scoreboard.getPlayersTeam(playerName);
        if (current == null || !rank.teamName.equals(current.getRegisteredName())) {
            scoreboard.removePlayerFromTeams(playerName);
            scoreboard.func_151392_a(playerName, rank.teamName);
        }
    }

    private static void ensureTeam(Scoreboard scoreboard, Rank rank) {
        ScorePlayerTeam team = scoreboard.getTeam(rank.teamName);
        if (team == null) {
            team = scoreboard.createTeam(rank.teamName);
        }
        if (!rank.prefix.equals(team.getColorPrefix())) {
            team.setNamePrefix(rank.prefix);
        }
        if (!"".equals(team.getColorSuffix())) {
            team.setNameSuffix("");
        }
    }

    private static Rank getRank(EntityPlayerMP player, KOMEWorldData data, UUID playerID, KOMEPlayerProgression progression) {
        if (isGroupComplete(progression, "prince_king")) {
            FactionRankKey faction = getFactionRankKey(player, progression);
            if (faction.key.length() > 0 && data.claimFactionKing(faction.key, faction.name, playerID, player.getCommandSenderName())) {
                return new Rank(TEAM_PREFIX + "king", "[King] ");
            }
            return new Rank(TEAM_PREFIX + "prince", "[Prince] ");
        }
        if (isGroupComplete(progression, "lord")) {
            return new Rank(TEAM_PREFIX + "prince", "[Prince] ");
        }
        if (isGroupComplete(progression, "knight")) {
            return new Rank(TEAM_PREFIX + "lord", "[Lord] ");
        }
        if (isGroupComplete(progression, "serf")) {
            return new Rank(TEAM_PREFIX + "knight", "[Knight] ");
        }
        if (isGroupComplete(progression, "wanderer")) {
            return new Rank(TEAM_PREFIX + "serf", "[Serf] ");
        }
        return new Rank(TEAM_PREFIX + "wander", "[Wanderer] ");
    }

    private static boolean isGroupComplete(KOMEPlayerProgression progression, String group) {
        return progression != null
            && progression.getTotalCount(group) > 0
            && progression.getCompletedCount(group) >= progression.getTotalCount(group);
    }

    private static FactionRankKey getFactionRankKey(EntityPlayerMP player, KOMEPlayerProgression progression) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        if (pledge != null) {
            return new FactionRankKey(pledge.codeName(), pledge.factionName());
        }
        String faction = progression == null ? "" : progression.getPledgedLordFaction();
        return new FactionRankKey(faction, faction);
    }

    private static class FactionRankKey {
        private final String key;
        private final String name;

        private FactionRankKey(String key, String name) {
            this.key = key == null ? "" : key;
            this.name = name == null ? "" : name;
        }
    }

    private static class Rank {
        private final String teamName;
        private final String prefix;

        private Rank(String teamName, String prefix) {
            this.teamName = teamName;
            this.prefix = prefix;
        }
    }
}
