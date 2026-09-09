package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.client.gui.GuiGateCreation;
import com.enovak.lotrmoremobs.siege.client.gui.GuiGateManagement;
import com.enovak.lotrmoremobs.siege.client.gui.GuiGatePlayerAccess;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import net.minecraftforge.client.event.RenderGameOverlayEvent;

public final class GateGuiHudSuppressor {

    @SubscribeEvent
    public void onRenderGameOverlay(
            RenderGameOverlayEvent.Pre event
    ) {
        GuiScreen screen =
                Minecraft
                        .getMinecraft()
                        .currentScreen;

        if (!(screen instanceof GuiGateManagement)
                && !(screen instanceof GuiGatePlayerAccess)
                && !(screen instanceof GuiGateCreation)) {

            return;
        }

        /*
         * Hide only the normal gameplay HUD behind the gate GUI.
         *
         * Deliberately DO NOT cancel:
         *
         * CHAT
         * TEXT
         * DEBUG
         * PLAYER_LIST
         * HELMET
         * PORTAL
         * ALL
         *
         * That keeps the suppression narrowly scoped instead of blanking
         * every overlay Forge knows about.
         */
        switch (event.type) {

            case CROSSHAIRS:
            case HOTBAR:
            case ARMOR:
            case HEALTH:
            case FOOD:
            case AIR:
            case EXPERIENCE:
            case HEALTHMOUNT:
            case JUMPBAR:
            case BOSSHEALTH:

                event.setCanceled(
                        true
                );

                break;

            default:
                break;
        }
    }
}