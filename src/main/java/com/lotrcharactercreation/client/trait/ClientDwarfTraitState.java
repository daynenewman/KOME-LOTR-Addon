package com.lotrcharactercreation.client.trait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.lotrcharactercreation.trait.DwarfTraitService;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import lotr.common.LOTRLevelData;

public final class ClientDwarfTraitState {

    private static final int HUD_X = 6;
    private static final int HUD_BOTTOM_OFFSET = 46;
    private static final int HUD_BAR_WIDTH = 88;
    private static final int HUD_BAR_HEIGHT = 5;
    private static final int HUD_ROW_HEIGHT = 18;
    private static final int HUD_BACKGROUND_COLOR = 0xB0000000;
    private static final int HUD_STAMINA_COLOR = 0xFF3CB371;
    private static final int HUD_EXHAUSTED_COLOR = 0xFFB84343;
    private static final int HUD_FEAST_COLOR = 0xFFD39A3A;
    private static final ClientDwarfTraitState INSTANCE = new ClientDwarfTraitState();

    private boolean active;
    private float stamina;
    private int feast;
    private boolean exhausted;

    private ClientDwarfTraitState() {}

    public static ClientDwarfTraitState getInstance() {
        return INSTANCE;
    }

    public void update(boolean newActive, float newStamina, int newFeast, boolean newExhausted) {
        active = newActive;
        if (!active) {
            stamina = 0.0F;
            feast = 0;
            exhausted = false;
            return;
        }

        stamina = MathHelper.clamp_float(newStamina, 0.0F, DwarfTraitService.MAX_STAMINA);
        feast = MathHelper.clamp_int(newFeast, 0, DwarfTraitService.MAX_FEAST);
        exhausted = newExhausted;
        EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && !player.capabilities.isCreativeMode
            && exhausted
            && stamina < DwarfTraitService.STAMINA_RESTART_THRESHOLD) {
            player.setSprinting(false);
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityClientPlayerMP player = minecraft.thePlayer;
        if (player == null || !player.isEntityAlive()
            || player.capabilities.isCreativeMode
            || minecraft.isGamePaused()) {
            return;
        }

        float feastFactor = DwarfTraitService.getFeastFactor(feast);
        if (exhausted && stamina < DwarfTraitService.STAMINA_RESTART_THRESHOLD) {
            player.setSprinting(false);
        }

        if (player.isSprinting() && !exhausted && stamina > 0.0F) {
            stamina = Math.max(0.0F, stamina - DwarfTraitService.BASE_STAMINA_DRAIN_PER_TICK * feastFactor);
            if (stamina <= 0.0F) {
                exhausted = true;
                player.setSprinting(false);
            }
        } else if (stamina < DwarfTraitService.MAX_STAMINA) {
            stamina = Math
                .min(DwarfTraitService.MAX_STAMINA, stamina + DwarfTraitService.getStaminaRecoveryPerTick(feast));
            if (exhausted && stamina >= DwarfTraitService.STAMINA_RESTART_THRESHOLD) {
                exhausted = false;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playerInteracted(PlayerInteractEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!active || event.entityPlayer != minecraft.thePlayer
            || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.useItem == Event.Result.DENY
            || LOTRLevelData.clientside_thisServer_feastMode
            || minecraft.thePlayer == null
            || minecraft.thePlayer.capabilities.isCreativeMode
            || minecraft.thePlayer.ridingEntity != null
            || minecraft.thePlayer.isUsingItem()
            || minecraft.thePlayer.getFoodStats()
                .getFoodLevel() < 20
            || feast >= DwarfTraitService.MAX_FEAST) {
            return;
        }

        ItemStack heldItem = minecraft.thePlayer.getCurrentEquippedItem();
        if (heldItem != null && heldItem.getItem() instanceof ItemFood && !minecraft.thePlayer.canEat(false)) {
            minecraft.thePlayer.setItemInUse(heldItem, heldItem.getMaxItemUseDuration());
        }
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !active
            || minecraft.thePlayer == null
            || minecraft.thePlayer.capabilities.isCreativeMode
            || minecraft.gameSettings.hideGUI) {
            return;
        }

        ScaledResolution resolution = event.resolution;
        int top = resolution.getScaledHeight() - HUD_BOTTOM_OFFSET - HUD_ROW_HEIGHT * 2;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            drawResourceRow(
                minecraft,
                HUD_X,
                top,
                "Stamina " + Math.round(stamina) + "/" + Math.round(DwarfTraitService.MAX_STAMINA),
                stamina / DwarfTraitService.MAX_STAMINA,
                exhausted ? HUD_EXHAUSTED_COLOR : HUD_STAMINA_COLOR);
            drawResourceRow(
                minecraft,
                HUD_X,
                top + HUD_ROW_HEIGHT,
                "Feast " + feast + "/" + DwarfTraitService.MAX_FEAST,
                feast / (float) DwarfTraitService.MAX_FEAST,
                HUD_FEAST_COLOR);
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

    private static void drawResourceRow(Minecraft minecraft, int x, int y, String label, float fraction,
        int fillColor) {
        minecraft.fontRenderer.drawStringWithShadow(label, x, y, 0xFFFFFF);
        int barTop = y + 10;
        Gui.drawRect(x, barTop, x + HUD_BAR_WIDTH, barTop + HUD_BAR_HEIGHT, HUD_BACKGROUND_COLOR);
        int fillWidth = Math.round((HUD_BAR_WIDTH - 2) * MathHelper.clamp_float(fraction, 0.0F, 1.0F));
        if (fillWidth > 0) {
            Gui.drawRect(x + 1, barTop + 1, x + 1 + fillWidth, barTop + HUD_BAR_HEIGHT - 1, fillColor);
        }
    }

    private void clear() {
        active = false;
        stamina = 0.0F;
        feast = 0;
        exhausted = false;
    }
}
