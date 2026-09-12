package com.enovak.lotrmoremobs.client.config;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-facing facade for gameplay settings selected by the connected server. */
@SideOnly(Side.CLIENT)
public final class ClientServerGameplayState {

    private ClientServerGameplayState() {
    }

    public static boolean useModernPlayerAnimations() {
        return PlayerMovementMode.useModernPlayerMovementClient();
    }

    public static void setModernPlayerAnimations(boolean enabled) {
        PlayerMovementMode.setClientSyncedModern(enabled);
    }
}
