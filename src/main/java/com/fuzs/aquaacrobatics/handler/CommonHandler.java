package com.fuzs.aquaacrobatics.handler;

import net.minecraft.entity.item.EntityBoat;
import net.minecraftforge.event.entity.EntityEvent;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.IRockableBoat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class CommonHandler {

    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (event.entity instanceof EntityBoat) {
            if (ConfigHandler.MiscellaneousConfig.bubbleColumns) ((IRockableBoat) event.entity).aqua$doRegisterData();
        }
    }
}
