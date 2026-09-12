package com.enovak.lotrmoremobs.render.entity;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHirePreviewArcher;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHirePreviewDriver;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.render.MumakilRenderContext;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import lotr.client.render.entity.LOTRRenderNearHaradrim;
import net.geckominecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.IntBuffer;

/**
 * Small wrapper around the stable Mumakil Geo renderer.
 *
 * World rendering stays untouched. The extra scale only applies when Minecraft is rendering the Mumakil
 * as an inventory-style GUI preview.
 */
public class LOTRRenderMumakilGeoInventoryScaled extends LOTRRenderMumakilGeo {
    /*
     * The original large hiring preview used 0.35F. Keep the later 0.18F
     * inventory fit for non-hiring adult previews and the calf's 0.45F fit.
     */
    private static final float ADULT_HIRE_PREVIEW_SCALE = 0.35F;
    private static final float ADULT_INVENTORY_PREVIEW_SCALE = 0.18F;
    private static final float CALF_INVENTORY_PREVIEW_SCALE = 0.45F;
    private static final float SPAWN_CAGE_PREVIEW_SCALE = 0.11F;
    private static final int PREVIEW_CACHE_IDLE_TICKS = 20;
    private static final int PREVIEW_GL_ATTRIB_MASK =
            GL11.GL_ENABLE_BIT
                    | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_DEPTH_BUFFER_BIT
                    | GL11.GL_LIGHTING_BIT
                    | GL11.GL_TEXTURE_BIT
                    | GL11.GL_TRANSFORM_BIT
                    | GL11.GL_VIEWPORT_BIT
                    | GL11.GL_SCISSOR_BIT
                    | GL11.GL_CURRENT_BIT
                    | GL11.GL_POLYGON_BIT;
    private static boolean loggedInventoryPreviewScale;
    private PreviewFormation previewFormation;
    private int ticksSinceHirePreviewRender = PREVIEW_CACHE_IDLE_TICKS + 1;
    private GuiScreen activeInventoryPreviewScreen;
    private final PreviewGlState previewGlState = new PreviewGlState();

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (entity instanceof LOTREntityMumakil) {
            MumakilRenderContext.Type context =
                    this.getRenderContext((LOTREntityMumakil)entity);
            if (context == MumakilRenderContext.Type.SPAWN_CAGE) {
                this.doSpawnCagePreview(
                        entity,
                        x,
                        y,
                        z,
                        entityYaw
                );
                return;
            }
            if (context == MumakilRenderContext.Type.HIRING_PREVIEW
                    || context
                    == MumakilRenderContext.Type.HORSE_INVENTORY) {
                this.doScaledInventoryPreview(
                        entity,
                        x,
                        y,
                        z,
                        entityYaw,
                        partialTicks,
                        context
                );
                return;
            }
        }

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    public void doRender(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (entity instanceof LOTREntityMumakil) {
            MumakilRenderContext.Type context =
                    this.getRenderContext((LOTREntityMumakil)entity);
            if (context == MumakilRenderContext.Type.SPAWN_CAGE) {
                this.doSpawnCagePreview(
                        entity,
                        x,
                        y,
                        z,
                        entityYaw
                );
                return;
            }
            if (context == MumakilRenderContext.Type.HIRING_PREVIEW
                    || context
                    == MumakilRenderContext.Type.HORSE_INVENTORY) {
                this.doScaledInventoryPreview(
                        entity,
                        x,
                        y,
                        z,
                        entityYaw,
                        partialTicks,
                        context
                );
                return;
            }
        }

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    private void doScaledInventoryPreview(
            Entity entity,
            double x,
            double y,
            double z,
            float entityYaw,
            float partialTicks,
            MumakilRenderContext.Type context
    ) {
        LOTREntityMumakil mumakil = (LOTREntityMumakil)entity;
        boolean hirePreview =
                context == MumakilRenderContext.Type.HIRING_PREVIEW;
        float stablePartialTick = hirePreview ? 0.0F : partialTicks;
        if (hirePreview) {
            stabilizePreviewEntity(mumakil);
            this.ticksSinceHirePreviewRender = 0;
        }
        float previewScale = mumakil.isBabyMumakil()
                ? CALF_INVENTORY_PREVIEW_SCALE
                : hirePreview
                ? ADULT_HIRE_PREVIEW_SCALE
                : ADULT_INVENTORY_PREVIEW_SCALE;
        PreviewGlState callerState = this.previewGlState;
        callerState.capture();
        boolean attributesPushed = false;
        boolean matrixPushed = false;
        try {
            GL11.glPushAttrib(PREVIEW_GL_ATTRIB_MASK);
            attributesPushed = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            matrixPushed = true;
            GlStateManager.scale(previewScale, previewScale, previewScale);

            if (!loggedInventoryPreviewScale) {
                loggedInventoryPreviewScale = true;
                System.out.println("[LOTRMoreMobs] Scaling Mumakil inventory preview by " + previewScale
                        + " renderCoords=" + x + "," + y + "," + z);
            }

            super.doRender(entity, x, y, z, entityYaw, stablePartialTick);
            if (hirePreview) {
                this.renderHireFormation(
                        mumakil,
                        x,
                        y,
                        z,
                        entityYaw
                );
            }
        } finally {
            try {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                if (matrixPushed) {
                    GlStateManager.popMatrix();
                }
            } finally {
                try {
                    if (attributesPushed) {
                        GL11.glPopAttrib();
                    }
                } finally {
                    callerState.restoreForGui();
                }
            }
        }
    }

    private void doSpawnCagePreview(
            Entity entity,
            double x,
            double y,
            double z,
            float entityYaw
    ) {
        stabilizePreviewEntity((LOTREntityMumakil)entity);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(
                    SPAWN_CAGE_PREVIEW_SCALE,
                    SPAWN_CAGE_PREVIEW_SCALE,
                    SPAWN_CAGE_PREVIEW_SCALE
            );
            super.doRender(
                    entity,
                    x,
                    y,
                    z,
                    entityYaw,
                    0.0F
            );
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderHireFormation(
            LOTREntityMumakil mumakil,
            double x,
            double y,
            double z,
            float entityYaw
    ) {
        PreviewFormation formation = this.previewFormation;
        if (formation == null
                || formation.archer.worldObj != mumakil.worldObj
                || formation.driver.worldObj != mumakil.worldObj) {
            formation = new PreviewFormation(mumakil);
            this.previewFormation = formation;
        }

        /*
         * All archers have identical static model/equipment state. Reuse one
         * silent, unticked cosmetic entity at the immutable live slot
         * transforms instead of retaining seventeen full NPC objects.
         */
        LOTREntityMumakilHowdahArcher archer = formation.archer;
        int slotCount =
                LOTREntityMumakilHowdahArcher.getHowdahArcherSlotCount();
        for (int slot = 0; slot < slotCount; ++slot) {
            if (!archer.placeOnHowdahForHirePreview(mumakil, slot)) {
                continue;
            }
            stabilizePreviewEntity(archer);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            try {
                formation.archerRenderer.doRender(
                        archer,
                        x + archer.posX - mumakil.posX,
                        y + archer.posY - mumakil.posY,
                        z + archer.posZ - mumakil.posZ,
                        archer.rotationYaw,
                        0.0F
                );
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GlStateManager.popMatrix();
            }
        }

        LOTREntityMumakilHirePreviewDriver driver = formation.driver;
        mumakil.positionRiderAtMumakilAnchor(driver);
        driver.ridingEntity = mumakil;
        driver.setCurrentItemOrArmor(0, null);
        driver.rotationYaw = mumakil.renderYawOffset;
        driver.renderYawOffset = mumakil.renderYawOffset;
        driver.rotationYawHead = mumakil.renderYawOffset;
        stabilizePreviewEntity(driver);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            formation.driverRenderer.doRender(
                    driver,
                    x + driver.posX - mumakil.posX,
                    y + driver.posY - mumakil.posY,
                    z + driver.posZ - mumakil.posZ,
                    driver.rotationYaw,
                    0.0F
            );
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || this.previewFormation == null) {
            return;
        }

        if (++this.ticksSinceHirePreviewRender > PREVIEW_CACHE_IDLE_TICKS) {
            /*
             * Page switches and GUI closure stop invoking this renderer. A
             * short grace interval avoids cache churn on low-FPS clients,
             * then drops the two preview-only entities.
             */
            this.previewFormation = null;
        }
    }

    @SubscribeEvent
    public void onGuiDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        this.activeInventoryPreviewScreen =
                event.gui instanceof GuiScreenHorseInventory
                        ? event.gui
                        : null;
    }

    @SubscribeEvent
    public void onGuiDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        this.activeInventoryPreviewScreen = null;
    }

    private MumakilRenderContext.Type getRenderContext(
            LOTREntityMumakil mumakil
    ) {
        if (MumakilRenderContext.get()
                == MumakilRenderContext.Type.SPAWN_CAGE) {
            return MumakilRenderContext.Type.SPAWN_CAGE;
        }

        if (this.isDetachedHirePreview(mumakil)) {
            return MumakilRenderContext.Type.HIRING_PREVIEW;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null
                && this.activeInventoryPreviewScreen != null
                && minecraft.currentScreen
                == this.activeInventoryPreviewScreen) {
            return MumakilRenderContext.Type.HORSE_INVENTORY;
        }
        return MumakilRenderContext.Type.WORLD;
    }

    private boolean isDetachedHirePreview(LOTREntityMumakil mumakil) {
        return mumakil.isMumakilHirePreview()
                && mumakil.worldObj != null
                && mumakil.worldObj.isRemote
                && !mumakil.addedToChunk;
    }

    private static void stabilizePreviewEntity(EntityLivingBase entity) {
        entity.prevPosX = entity.posX;
        entity.prevPosY = entity.posY;
        entity.prevPosZ = entity.posZ;
        entity.lastTickPosX = entity.posX;
        entity.lastTickPosY = entity.posY;
        entity.lastTickPosZ = entity.posZ;
        entity.prevRotationYaw = entity.rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch;
        entity.prevRenderYawOffset = entity.renderYawOffset;
        entity.prevRotationYawHead = entity.rotationYawHead;
        entity.prevSwingProgress = 0.0F;
        entity.swingProgress = 0.0F;
        entity.prevLimbSwingAmount = 0.0F;
        entity.limbSwingAmount = 0.0F;
        entity.limbSwing = 0.0F;
        entity.prevDistanceWalkedModified = 0.0F;
        entity.distanceWalkedModified = 0.0F;
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
        entity.ticksExisted = 0;
    }

    private static final class PreviewFormation {
        private final LOTREntityMumakilHowdahArcher archer;
        private final LOTREntityMumakilHirePreviewDriver driver;
        private final Render archerRenderer;
        private final Render driverRenderer;

        private PreviewFormation(LOTREntityMumakil mumakil) {
            this.archer =
                    new LOTREntityMumakilHirePreviewArcher(mumakil.worldObj);
            this.archer.onSpawnWithEgg(null);
            this.archer.setRuntimeHowdahPassenger(true);
            this.archerRenderer = new PreviewArcherRenderer();
            this.archerRenderer.setRenderManager(RenderManager.instance);

            this.driver = new LOTREntityMumakilHirePreviewDriver(mumakil.worldObj);
            this.driver.initCreatureForHire(null);
            this.driver.refreshCurrentAttackMode();
            this.driver.setCurrentItemOrArmor(0, null);
            this.driverRenderer = new PreviewDriverRenderer();
            this.driverRenderer.setRenderManager(RenderManager.instance);
        }
    }

    /**
     * Captures the states most likely to corrupt GUI tooltips and NEI. The
     * targeted attribute mask above handles lighting, alpha, blend, depth,
     * cull, rescale-normal, texture bindings, and depth-mask state without
     * the per-figure GL_ALL_ATTRIB_BITS churn used by the earlier preview.
     */
    private static final class PreviewGlState {
        private static final int INTEGER_QUERY_CAPACITY = 16;

        /*
         * LWJGL 2 checks that glGetInteger buffers have at least sixteen
         * remaining integers even for four-value states. Reuse one direct
         * render-thread buffer and never narrow its limit before the query.
         */
        private final IntBuffer integerQueryBuffer =
                BufferUtils.createIntBuffer(INTEGER_QUERY_CAPACITY);
        private int viewportX;
        private int viewportY;
        private int viewportWidth;
        private int viewportHeight;
        private int scissorX;
        private int scissorY;
        private int scissorWidth;
        private int scissorHeight;
        private boolean scissorEnabled;
        private int activeTexture;

        private void capture() {
            this.integerQueryBuffer.clear();
            GL11.glGetInteger(
                    GL11.GL_VIEWPORT,
                    this.integerQueryBuffer
            );
            this.viewportX = this.integerQueryBuffer.get(0);
            this.viewportY = this.integerQueryBuffer.get(1);
            this.viewportWidth = this.integerQueryBuffer.get(2);
            this.viewportHeight = this.integerQueryBuffer.get(3);

            this.integerQueryBuffer.clear();
            GL11.glGetInteger(
                    GL11.GL_SCISSOR_BOX,
                    this.integerQueryBuffer
            );
            this.scissorX = this.integerQueryBuffer.get(0);
            this.scissorY = this.integerQueryBuffer.get(1);
            this.scissorWidth = this.integerQueryBuffer.get(2);
            this.scissorHeight = this.integerQueryBuffer.get(3);
            this.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            this.activeTexture =
                    GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        }

        private void restoreForGui() {
            GL11.glViewport(
                    this.viewportX,
                    this.viewportY,
                    this.viewportWidth,
                    this.viewportHeight
            );
            GL11.glScissor(
                    this.scissorX,
                    this.scissorY,
                    this.scissorWidth,
                    this.scissorHeight
            );
            if (this.scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            /*
             * GL_TEXTURE_BIT restores the bindings and enable state for both
             * Minecraft texture units. Select the caller's exact unit again
             * so neither the lightmap nor the default unit leaks as active.
             */
            OpenGlHelper.setActiveTexture(this.activeTexture);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static final class PreviewArcherRenderer
            extends LOTRRenderNearHaradrim {
        @Override
        protected void passSpecialRender(
                EntityLivingBase entity,
                double x,
                double y,
                double z
        ) {
        }
    }

    private static final class PreviewDriverRenderer
            extends LOTRRenderMumakilDriver {
        @Override
        protected void passSpecialRender(
                EntityLivingBase entity,
                double x,
                double y,
                double z
        ) {
        }
    }
}
