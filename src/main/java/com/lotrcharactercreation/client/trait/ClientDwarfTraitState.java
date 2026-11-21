package com.lotrcharactercreation.client.trait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.GuiIngameForge;
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

    private static final ResourceLocation RUNES = new ResourceLocation(
        "lotrcharactercreation", "textures/gui/dwarf_cirth.png");
    // Angerthas Moria: E46 N22 D9 U42 R12 A48 N22 S54 E46; F3 E46 A48 S54 T8 I39 N22 G19.
    // English soft C in ENDURANCE takes the /s/ certh, rather than the /k/ certh.
    private static final int[] ENDURANCE = {0, 1, 2, 3, 4, 5, 1, 6, 0};
    private static final int[] FEASTING = {7, 0, 5, 6, 8, 9, 1, 10};
    private static final float[] ENGRAVED = {0.19F, 0.22F, 0.25F};
    private static final float[] WARNING_ENGRAVED = {0.52F, 0.12F, 0.10F};
    private static final float[] BLUE = {0.29F, 0.70F, 1.00F};
    private static final float[] RED = {1.00F, 0.24F, 0.21F};
    private static final float[] AMBER = {1.00F, 0.66F, 0.20F};
    private static final ClientDwarfTraitState INSTANCE = new ClientDwarfTraitState();

    private boolean active;
    private float stamina;
    private int feast;
    private boolean exhausted;
    private boolean foodRendered;

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
            foodRendered = false;
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
    public void beginOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            foodRendered = false;
        }
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.FOOD) {
            foodRendered = true;
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL
            || !DwarfRuneHudLayout.show(active, minecraft.thePlayer != null,
                minecraft.thePlayer != null && minecraft.thePlayer.capabilities.isCreativeMode,
                minecraft.gameSettings.hideGUI, foodRendered)) {
            return;
        }

        ScaledResolution resolution = event.resolution;
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            minecraft.getTextureManager().bindTexture(RUNES);
            drawRow(ENDURANCE, DwarfRuneHudLayout.enduranceX(width),
                DwarfRuneHudLayout.enduranceY(width, height, GuiIngameForge.left_height,
                    GuiIngameForge.right_height),
                DwarfRuneHudLayout.fillWidth(stamina, DwarfTraitService.MAX_STAMINA, ENDURANCE.length),
                DwarfRuneHudLayout.warn(exhausted) ? RED : BLUE,
                DwarfRuneHudLayout.warn(exhausted) ? WARNING_ENGRAVED : ENGRAVED);
            drawRow(FEASTING, DwarfRuneHudLayout.feastingX(width),
                DwarfRuneHudLayout.feastingY(width, height, GuiIngameForge.left_height,
                    GuiIngameForge.right_height),
                DwarfRuneHudLayout.fillWidth(feast, DwarfTraitService.MAX_FEAST, FEASTING.length), AMBER,
                ENGRAVED);
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

    private static void drawRow(int[] glyphs, int x, int y, float fillWidth, float[] litColor,
        float[] engravedColor) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glColor4f(engravedColor[0], engravedColor[1], engravedColor[2], 0.94F);
        tessellator.startDrawingQuads();
        for (int i = 0; i < glyphs.length; i++) {
            blit(tessellator, x + i * DwarfRuneHudLayout.ADVANCE, y, glyphs[i] * 8, 7.0F);
        }
        tessellator.draw();

        GL11.glColor4f(litColor[0], litColor[1], litColor[2], 1.0F);
        tessellator.startDrawingQuads();
        for (int i = 0; i < glyphs.length; i++) {
            float lit = DwarfRuneHudLayout.litRuneWidth(fillWidth, i);
            if (lit > 0.0F) {
                blit(tessellator, x + i * DwarfRuneHudLayout.ADVANCE, y, glyphs[i] * 8, lit);
            }
        }
        tessellator.draw();
    }

    private static void blit(Tessellator tessellator, int x, int y, int u, float width) {
        double left = u / 128.0D;
        double right = (u + width) / 128.0D;
        double top = 2.0D / 16.0D;
        double bottom = (2.0D + DwarfRuneHudLayout.RUNE_HEIGHT) / 16.0D;
        tessellator.addVertexWithUV(x, y + DwarfRuneHudLayout.RUNE_HEIGHT, 0, left, bottom);
        tessellator.addVertexWithUV(x + width, y + DwarfRuneHudLayout.RUNE_HEIGHT, 0, right, bottom);
        tessellator.addVertexWithUV(x + width, y, 0, right, top);
        tessellator.addVertexWithUV(x, y, 0, left, top);
    }

    private void clear() {
        active = false;
        stamina = 0.0F;
        feast = 0;
        exhausted = false;
        foodRendered = false;
    }
}
