package com.enovak.lotrmoremobs.config;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Shared authority boundary for KOME's modern player movement mode.
 *
 * <p>The logical server always reads the server/world configuration. Client
 * worlds read only the value synchronized by the connected server so a remote
 * client's local config cannot change movement, pose, hitbox, or presentation.</p>
 */
public final class PlayerMovementMode {

    private static volatile boolean clientSyncedModern = true;

    private PlayerMovementMode() {
    }

    public static boolean useModernPlayerMovement(EntityPlayer player) {
        if (player != null && player.worldObj != null && player.worldObj.isRemote) {
            return clientSyncedModern;
        }
        return MumakilConfig.modernPlayerAnimations;
    }

    public static boolean useModernPlayerMovementClient() {
        return clientSyncedModern;
    }

    public static void setClientSyncedModern(boolean enabled) {
        clientSyncedModern = enabled;
    }
}
