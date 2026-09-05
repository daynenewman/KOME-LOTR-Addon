package com.lotrcharactercreation.body;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

public final class PlayerRaceSizeService {

    private static final int WAKE_REAPPLY_DELAY_TICKS = 1;
    private static final Map<UUID, Integer> PENDING_SERVER_REAPPLIES = new HashMap<UUID, Integer>();

    private PlayerRaceSizeService() {}

    public static boolean applyStoredRaceSize(EntityPlayerMP player) {
        return applyRaceSize(player, PlayerRaceData.getRace(player));
    }

    public static boolean applyRaceSize(EntityPlayer player, PlayerRace race) {
        if (player == null || !player.isEntityAlive()) {
            return false;
        }

        RaceBodyDefinition raceBody = RaceBodyDefinition.forRace(race);
        RaceBodyDefinition appliedBody = raceBody.isPrototypeSizeEnabled() ? raceBody : RaceBodyDefinition.MAN;
        if (player.width == appliedBody.getWidth() && player.height == appliedBody.getHeight()) {
            return false;
        }

        player.setSize(appliedBody.getWidth(), appliedBody.getHeight());
        return true;
    }

    public static void scheduleServerReapplyAfterWake(EntityPlayerMP player) {
        if (player != null) {
            PENDING_SERVER_REAPPLIES.put(player.getUniqueID(), Integer.valueOf(WAKE_REAPPLY_DELAY_TICKS));
        }
    }

    public static void removeScheduledServerReapply(EntityPlayerMP player) {
        if (player != null) {
            PENDING_SERVER_REAPPLIES.remove(player.getUniqueID());
        }
    }

    public static void processScheduledServerReapplies() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            PENDING_SERVER_REAPPLIES.clear();
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING_SERVER_REAPPLIES.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (entry.getValue()
                .intValue() > 0) {
                entry.setValue(
                    Integer.valueOf(
                        entry.getValue()
                            .intValue() - 1));
                continue;
            }

            EntityPlayerMP player = findOnlinePlayer(server, entry.getKey());
            if (player != null) {
                applyStoredRaceSize(player);
                PlayerRaceEyeService.applyStoredServerEyeHeight(player);
            }
            iterator.remove();
        }
    }

    private static EntityPlayerMP findOnlinePlayer(MinecraftServer server, UUID playerId) {
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) entry;
                if (playerId.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }
}
