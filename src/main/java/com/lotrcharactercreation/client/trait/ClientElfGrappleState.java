package com.lotrcharactercreation.client.trait;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.trait.ElfGrappleAnchor;
import com.lotrcharactercreation.trait.ElfGrappleWeaponPolicy;
import com.lotrcharactercreation.trait.RaceTraitService;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import lotr.common.LOTRMod;
import lotr.common.item.LOTRWeaponStats;

public final class ClientElfGrappleState {

    private static final int HUD_X = 6;
    private static final int HUD_BOTTOM_OFFSET = 46;
    private static final int ICON_SIZE = 16;
    private static final int NOT_READY_OVERLAY_COLOR = 0x99000000;
    private static final ClientElfGrappleState INSTANCE = new ClientElfGrappleState();

    private final Map<Integer, ActiveGrapple> activeGrapples = new HashMap<Integer, ActiveGrapple>();
    private boolean localStateKnown;
    private boolean localReady;
    private int daggerAttackCooldownTicks;

    private ClientElfGrappleState() {}

    public static ClientElfGrappleState getInstance() {
        return INSTANCE;
    }

    public void update(int playerEntityId, int targetEntityId, boolean active, boolean ready) {
        if (active && targetEntityId >= 0) {
            activeGrapples.put(playerEntityId, new ActiveGrapple(targetEntityId));
        } else {
            activeGrapples.remove(playerEntityId);
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null && minecraft.thePlayer.getEntityId() == playerEntityId) {
            localStateKnown = true;
            localReady = ready && !active;
            if (!active) {
                daggerAttackCooldownTicks = 0;
            }
        }
    }

    @SubscribeEvent
    public void mousePressed(MouseEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.button != 0 || !event.buttonstate
            || minecraft.thePlayer == null
            || minecraft.currentScreen != null
            || !activeGrapples.containsKey(minecraft.thePlayer.getEntityId())
            || !ElfGrappleWeaponPolicy.isDagger(minecraft.thePlayer.getCurrentEquippedItem())) {
            return;
        }

        event.setCanceled(true);
        if (daggerAttackCooldownTicks > 0) {
            return;
        }
        daggerAttackCooldownTicks = Math
            .max(1, LOTRWeaponStats.getAttackTimePlayer(minecraft.thePlayer.getCurrentEquippedItem()));
        minecraft.thePlayer.swingItem();
        ModNetwork.sendElfGrappleAttack();
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (daggerAttackCooldownTicks > 0) {
            --daggerAttackCooldownTicks;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) {
            return;
        }
        for (Map.Entry<Integer, ActiveGrapple> entry : activeGrapples.entrySet()) {
            Entity playerEntity = minecraft.theWorld.getEntityByID(entry.getKey());
            Entity targetEntity = minecraft.theWorld.getEntityByID(entry.getValue().targetEntityId);
            if (playerEntity instanceof EntityPlayer && targetEntity instanceof EntityLivingBase
                && playerEntity.ridingEntity == targetEntity
                && targetEntity.riddenByEntity == playerEntity) {
                ElfGrappleAnchor.apply((EntityPlayer) playerEntity, (EntityLivingBase) targetEntity);
            }
        }
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !localStateKnown
            || minecraft.thePlayer == null
            || minecraft.gameSettings.hideGUI
            || !RaceTraitService.hasElfJumpTrait(minecraft.thePlayer)) {
            return;
        }

        boolean ready = localReady && !activeGrapples.containsKey(minecraft.thePlayer.getEntityId());
        ScaledResolution resolution = event.resolution;
        int y = resolution.getScaledHeight() - HUD_BOTTOM_OFFSET - ICON_SIZE;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            RenderItem.getInstance()
                .renderItemAndEffectIntoGUI(
                    minecraft.fontRenderer,
                    minecraft.getTextureManager(),
                    new ItemStack(LOTRMod.daggerElven),
                    HUD_X,
                    y);
            RenderHelper.disableStandardItemLighting();
            if (!ready) {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                Gui.drawRect(HUD_X, y, HUD_X + ICON_SIZE, y + ICON_SIZE, NOT_READY_OVERLAY_COLOR);
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glPopMatrix();
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
        activeGrapples.clear();
        localStateKnown = false;
        localReady = false;
        daggerAttackCooldownTicks = 0;
    }

    private static final class ActiveGrapple {

        private final int targetEntityId;

        private ActiveGrapple(int targetEntityId) {
            this.targetEntityId = targetEntityId;
        }
    }
}
