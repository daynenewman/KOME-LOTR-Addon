package com.enovak.lotrmoremobs.siege.repair;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class GateManagementEventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            GateManagementManager.processQueuedRequests();
        }
    }
}
