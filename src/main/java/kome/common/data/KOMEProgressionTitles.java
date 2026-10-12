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
    private static final java.util.Map<UUID,String> LAST=new java.util.HashMap<UUID,String>();
    public static void forget(UUID id){LAST.remove(id);}

    public static void updatePlayerTitle(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        World world = KOMEReflection.getWorld(player);
        if (world == null || KOMEReflection.isRemote(world)) {
            return;
        }
        UUID playerID = KOMEReflection.getEntityUUID(player);
        Scoreboard scoreboard = world.getScoreboard();
        String playerName = player.getCommandSenderName();
        ScorePlayerTeam current = scoreboard.getPlayersTeam(playerName);
        // Retire only exact legacy title teams. Never claim another mod's team.
        if(current!=null&&isLegacyTitleTeam(current.getRegisteredName()))scoreboard.removePlayerFromTeams(playerName);
        net.minecraft.server.MinecraftServer server=net.minecraft.server.MinecraftServer.getServer();
        if(server==null||kome.common.network.KOMEPacketHandler.network==null)return;
        java.util.Map<UUID,String> roster=new java.util.LinkedHashMap<UUID,String>();
        for(Object value:server.getConfigurationManager().playerEntityList){
            EntityPlayerMP online=(EntityPlayerMP)value;
            LOTRFaction pledge=LOTRLevelData.getData(online).getPledgeFaction();
            roster.put(online.getUniqueID(),KOMEProgressionIdentity.suffix(pledge==null?"":pledge.codeName(),KOMEWorldData.get(online.worldObj).getProgression(online.getUniqueID()).getCanonicalRank()));
            if(roster.size()==1024)break;
        }
        String signature=roster.toString();
        if(!signature.equals(LAST.put(playerID,signature)))kome.common.network.KOMEPacketHandler.network.sendTo(new kome.common.network.KOMEPacketProgressionIdentities(roster),player);
    }
    static boolean isLegacyTitleTeam(String name){return java.util.Arrays.asList("kome_king","kome_prince","kome_lord","kome_knight","kome_serf","kome_wander").contains(name);}

    static String resolveRankName(KOMEWorldData data, UUID playerID, KOMEPlayerProgression progression, String factionKey) {
        if (data != null && KOMERulerService.isRuler(data, factionKey, playerID)) {
            return "King";
        }
        KOMEProgressionRank rank = progression == null ? KOMEProgressionRank.WANDERER : progression.getCanonicalRank();
        return rank == null ? KOMEProgressionRank.WANDERER.displayName : rank.displayName;
    }

    static boolean isKomeOwnedTeam(String teamName) {
        return teamName != null && teamName.startsWith(TEAM_PREFIX);
    }

}
