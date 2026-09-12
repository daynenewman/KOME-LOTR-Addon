package com.enovak.lotrmoremobs.siege.access;

import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import com.mojang.authlib.GameProfile;
import lotr.common.LOTRLevelData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.server.management.UserListOpsEntry;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;

public final class GateAccess {

    private GateAccess() {
    }

    public static boolean isAdministrativePlayer(EntityPlayerMP player) {
        if (player == null) {
            return false;
        }
        if (player.capabilities.isCreativeMode) {
            return true;
        }

        /*
         * Do not use canCommandSenderUseCommand here. On an integrated LAN
         * server, opening the world with Allow Cheats sets Minecraft's
         * commandsAllowedForAll flag, which makes every connected player
         * pass that check even when they are not actually an operator.
         *
         * Gate administration is intentionally limited to explicit ops (at
         * permission level 2+) plus Creative mode.
         */
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return false;
        }
        ServerConfigurationManager configurationManager =
                server.getConfigurationManager();
        GameProfile opProfile =
                configurationManager
                        .func_152603_m()
                        .func_152700_a(player.getCommandSenderName());
        if (opProfile == null) {
            return false;
        }
        UserListOpsEntry opEntry =
                (UserListOpsEntry)configurationManager
                        .func_152603_m()
                        .func_152683_b(opProfile);
        return opEntry != null && opEntry.func_152644_a() >= 2;
    }

    public static void deny(
            EntityPlayerMP player,
            TileEntitySiegeGate gate
    ) {
        if (player == null || gate == null || gate.getWorldObj() == null) {
            return;
        }
        String denialMessage;
        if (gate.getGateFaction() != null
                && gate.isFactionAccessEnabled()) {
            float currentAlignment =
                    LOTRLevelData.getData(player)
                            .getAlignment(gate.getGateFaction());
            denialMessage =
                    "You have "
                            + currentAlignment
                            + " alignment with "
                            + gate.getGateFaction().factionName()
                            + "; need at least +"
                            + gate.getRequiredAlignment()
                            + " or explicit Player Access to operate "
                            + gate.getGateName()
                            + ".";
        } else if (gate.getGateFaction() != null) {
            denialMessage =
                    "Faction alignment access is disabled for "
                            + gate.getGateName()
                            + "; you need explicit Player Access.";
        } else {
            denialMessage =
                    "You are not authorized to operate "
                            + gate.getGateName()
                            + ".";
        }
        player.addChatMessage(new ChatComponentText(
                denialMessage
        ));
        if (gate.getWorldObj() instanceof WorldServer) {
            ((WorldServer)gate.getWorldObj()).func_147487_a(
                    "reddust",
                    gate.xCoord + 0.5D,
                    gate.yCoord + 1.0D,
                    gate.zCoord + 0.5D,
                    12,
                    0.45D,
                    0.65D,
                    0.45D,
                    0.0D
            );
        }
    }
}
