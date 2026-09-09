package com.enovak.lotrmoremobs.siege.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Drops the purely client-side inspection receipt when its client World unloads. */
public final class GateFinalizedInspectionClientHandler {

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world != null && event.world.isRemote) {
            GateFinalizedInspectionClientContext.clear();
            GateEditClientContext.clear();
        }
    }
}
