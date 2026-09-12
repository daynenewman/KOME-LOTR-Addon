package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.network.ServerGameplaySyncPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;

/** Keeps server-owned player movement mode synchronized to connected clients. */
public final class ServerGameplaySyncEventHandler {

    private boolean initialized;
    private boolean lastModernPlayerAnimations;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            Main.network.sendTo(currentPacket(), (EntityPlayerMP)event.player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        boolean current = MumakilConfig.modernPlayerAnimations;
        if (!initialized) {
            initialized = true;
            lastModernPlayerAnimations = current;
            return;
        }

        if (current != lastModernPlayerAnimations) {
            lastModernPlayerAnimations = current;
            Main.network.sendToAll(currentPacket());
        }
    }

    private static ServerGameplaySyncPacket currentPacket() {
        return new ServerGameplaySyncPacket(
                MumakilConfig.modernPlayerAnimations
        );
    }
}
