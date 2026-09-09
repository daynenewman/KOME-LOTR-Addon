package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.client.gui.GuiBattleRamControl;
import com.enovak.lotrmoremobs.siege.network.RamControlOpenPacket;
import net.minecraft.client.Minecraft;

public final class RamControlClientContext {

    private static int dimensionId;
    private static int entityId;
    private static boolean active;

    private RamControlClientContext() {
    }

    public static void open(RamControlOpenPacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null
                || minecraft.theWorld.provider.dimensionId
                != packet.getDimensionId()) {
            return;
        }
        dimensionId = packet.getDimensionId();
        entityId = packet.getEntityId();
        active = true;
        minecraft.displayGuiScreen(new GuiBattleRamControl());
    }

    public static void clear() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static int getDimensionId() {
        return dimensionId;
    }

    public static int getEntityId() {
        return entityId;
    }
}
