package com.lotrcharactercreation.client.trait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.lotrcharactercreation.trait.UrukHaiTraitService;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public final class ClientUrukRageState {

    private static final int HUD_X = 6;
    private static final int HUD_BOTTOM_OFFSET = 46;
    private static final int HUD_BAR_WIDTH = 88;
    private static final int HUD_BAR_HEIGHT = 5;
    private static final int HUD_ROW_HEIGHT = 18;
    private static final int HUD_BACKGROUND_COLOR = 0xB0000000;
    private static final int HUD_RAGE_COLOR = 0xFFC23B2A;
    private static final ClientUrukRageState INSTANCE = new ClientUrukRageState();

    private boolean active;
    private float rage;

    private ClientUrukRageState() {}

    public static ClientUrukRageState getInstance() {
        return INSTANCE;
    }

    public void update(boolean newActive, float newRage) {
        active = newActive;
        rage = active ? MathHelper.clamp_float(newRage, 0.0F, UrukHaiTraitService.MAX_RAGE) : 0.0F;
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !active
            || minecraft.thePlayer == null
            || minecraft.gameSettings.hideGUI) {
            return;
        }

        ScaledResolution resolution = event.resolution;
        int top = resolution.getScaledHeight() - HUD_BOTTOM_OFFSET - HUD_ROW_HEIGHT;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            minecraft.fontRenderer.drawStringWithShadow(
                "Rage: " + Math.round(rage) + "/" + Math.round(UrukHaiTraitService.MAX_RAGE),
                HUD_X,
                top,
                0xFFFFFF);
            int barTop = top + 10;
            Gui.drawRect(HUD_X, barTop, HUD_X + HUD_BAR_WIDTH, barTop + HUD_BAR_HEIGHT, HUD_BACKGROUND_COLOR);
            int fillWidth = Math
                .round((HUD_BAR_WIDTH - 2) * MathHelper.clamp_float(rage / UrukHaiTraitService.MAX_RAGE, 0.0F, 1.0F));
            if (fillWidth > 0) {
                Gui.drawRect(HUD_X + 1, barTop + 1, HUD_X + 1 + fillWidth, barTop + HUD_BAR_HEIGHT - 1, HUD_RAGE_COLOR);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    @SubscribeEvent
    public void worldUnloaded(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            clear();
        }
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        clear();
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clear();
    }

    private void clear() {
        active = false;
        rage = 0.0F;
    }
}
