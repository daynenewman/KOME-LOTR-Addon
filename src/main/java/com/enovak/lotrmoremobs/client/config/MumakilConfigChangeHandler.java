package com.enovak.lotrmoremobs.client.config;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class MumakilConfigChangeHandler {
    @SubscribeEvent
    public void onConfigChanged(
            ConfigChangedEvent.OnConfigChangedEvent event
    ) {
        if (event != null && Main.MODID.equals(event.modID)) {
            MumakilConfig.syncFromConfiguration();
        }
    }
}
