package com.enovak.lotrmoremobs.client;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.network.MumakilOpenGuiPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.lwjgl.input.Keyboard;

public class MumakilInventoryKeyHandler {
    private boolean wasInventoryKeyDown;
    private boolean hadScreenLastTick;
    private int openPacketCooldownTicks;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (this.openPacketCooldownTicks > 0) {
            --this.openPacketCooldownTicks;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.thePlayer == null || mc.gameSettings == null) {
            return;
        }

        boolean screenOpen = mc.currentScreen != null;
        boolean inventoryKeyDown = Keyboard.isKeyDown(mc.gameSettings.keyBindInventory.getKeyCode());

        if (!inventoryKeyDown) {
            this.wasInventoryKeyDown = false;
            this.hadScreenLastTick = screenOpen;
            return;
        }

        if (this.wasInventoryKeyDown) {
            this.hadScreenLastTick = screenOpen;
            return;
        }

        this.wasInventoryKeyDown = true;

        if (!(mc.thePlayer.ridingEntity instanceof LOTREntityMumakil)) {
            this.hadScreenLastTick = screenOpen;
            return;
        }

        GuiScreen screen = mc.currentScreen;

        if (screen != null && this.isHorseOrMumakilInventoryScreen(screen)) {
            this.hadScreenLastTick = true;
            return;
        }

        if (screen != null && this.isVanillaPlayerInventoryScreen(screen)) {
            System.out.println("[LOTRMoreMobs] Tick handler replacing vanilla inventory with Mumakil GUI. screen="
                    + screen.getClass().getName());

            mc.displayGuiScreen(null);
            this.sendOpenMumakilGuiPacket("client tick replaced vanilla inventory");
            this.hadScreenLastTick = false;
            return;
        }

        if (screen != null) {
            this.hadScreenLastTick = true;
            return;
        }

        if (this.hadScreenLastTick) {
            return;
        }

        this.sendOpenMumakilGuiPacket("client tick no screen");
        this.hadScreenLastTick = false;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.thePlayer == null) {
            return;
        }

        if (!(mc.thePlayer.ridingEntity instanceof LOTREntityMumakil)) {
            return;
        }

        if (event.gui == null) {
            return;
        }

        if (this.isHorseOrMumakilInventoryScreen(event.gui)) {
            return;
        }

        if (this.isVanillaPlayerInventoryScreen(event.gui)) {
            System.out.println("[LOTRMoreMobs] GuiOpenEvent intercepted vanilla inventory while riding Mumakil. screen="
                    + event.gui.getClass().getName());

            event.gui = null;
            this.sendOpenMumakilGuiPacket("GuiOpenEvent intercepted vanilla inventory");
        }
    }

    private void sendOpenMumakilGuiPacket(String reason) {
        if (this.openPacketCooldownTicks > 0) {
            return;
        }

        System.out.println("[LOTRMoreMobs] Sending Mumakil open GUI packet. reason=" + reason);
        Main.network.sendToServer(new MumakilOpenGuiPacket());
        this.openPacketCooldownTicks = 5;
    }

    private boolean isHorseOrMumakilInventoryScreen(GuiScreen screen) {
        if (screen == null) {
            return false;
        }

        String name = screen.getClass().getName();
        return name.indexOf("GuiScreenHorseInventory") >= 0
                || name.indexOf("GuiHorse") >= 0
                || name.indexOf("HorseInventory") >= 0;
    }

    private boolean isVanillaPlayerInventoryScreen(GuiScreen screen) {
        if (screen == null) {
            return false;
        }

        String name = screen.getClass().getName();
        return name.indexOf("GuiInventory") >= 0
                && name.indexOf("GuiScreenHorseInventory") < 0
                && name.indexOf("GuiHorse") < 0
                && name.indexOf("HorseInventory") < 0;
    }
}