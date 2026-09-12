package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class GateHealthHudHandler {

    /*
     * Do not render floating bars from absurd distances.
     */
    private static final double MAX_RENDER_DISTANCE =
            48.0D;

    private static final double MAX_RENDER_DISTANCE_SQ =
            MAX_RENDER_DISTANCE
                    * MAX_RENDER_DISTANCE;

    /*
     * The health bar normally sits this far above the highest GatePart.
     */
    private static final double HEIGHT_ABOVE_GATE =
            0.65D;

    /*
     * For very tall gates, the bar may never rise more than ten blocks
     * above the lowest GatePart.
     */
    private static final double MAX_HEIGHT_ABOVE_GATE_BASE =
            10.0D;

    /*
     * Pull the billboard slightly toward the viewer.
     *
     * This is especially important when the ten-block height cap places
     * the bar vertically in front of a very tall gate.
     */
    private static final double FRONT_OFFSET =
            0.40D;

    /*
     * Pixel dimensions before world-space billboard scaling.
     */
    private static final int BAR_WIDTH =
            120;

    private static final int BAR_HEIGHT =
            7;

    /*
     * Gives us a health bar a little over two world blocks wide.
     */
    private static final float WORLD_SCALE =
            0.018F;

    @SubscribeEvent
    public void onRenderWorldLast(
            RenderWorldLastEvent event
    ) {
        Minecraft minecraft =
                Minecraft.getMinecraft();

        if (minecraft.thePlayer == null
                || minecraft.theWorld == null
                || minecraft.renderViewEntity == null
                || minecraft.gameSettings.hideGUI) {
            return;
        }

        /*
         * The health bar is contextual instead of globally floating over every
         * damaged gate. Trace the player's view out to the same distance that
         * the bar is allowed to render and only show the gate which is actually
         * visible under the crosshair.
         *
         * This solves both problems at once:
         *
         * - a gate hidden behind terrain does not reveal its health;
         * - once the player really can see the gate, the billboard may render
         *   without depth clipping against the gate's own blocks.
         */
        TileEntitySiegeGate gate =
                getLookedAtGate(
                        minecraft,
                        event.partialTicks
                );

        if (!shouldRenderHealthBar(
                gate
        )) {
            return;
        }

        Entity camera =
                minecraft.renderViewEntity;

        double cameraX =
                camera.lastTickPosX
                        + (camera.posX
                        - camera.lastTickPosX)
                        * event.partialTicks;

        double cameraY =
                camera.lastTickPosY
                        + (camera.posY
                        - camera.lastTickPosY)
                        * event.partialTicks;

        double cameraZ =
                camera.lastTickPosZ
                        + (camera.posZ
                        - camera.lastTickPosZ)
                        * event.partialTicks;

        GateHealthAnchor anchor =
                calculateAnchor(
                        gate,
                        cameraX,
                        cameraZ
                );

        if (anchor == null) {
            return;
        }

        double deltaX =
                anchor.x
                        - cameraX;

        double deltaY =
                anchor.y
                        - cameraY;

        double deltaZ =
                anchor.z
                        - cameraZ;

        double distanceSq =
                deltaX * deltaX
                        + deltaY * deltaY
                        + deltaZ * deltaZ;

        if (distanceSq
                > MAX_RENDER_DISTANCE_SQ) {
            return;
        }

        renderGateHealthBar(
                minecraft,
                gate,
                deltaX,
                deltaY,
                deltaZ
        );
    }

    private static TileEntitySiegeGate getLookedAtGate(
            Minecraft minecraft,
            float partialTicks
    ) {
        if (minecraft == null
                || minecraft.thePlayer == null
                || minecraft.theWorld == null) {
            return null;
        }

        Vec3 start =
                Vec3.createVectorHelper(
                        minecraft.thePlayer.posX,
                        minecraft.thePlayer.posY
                                + minecraft.thePlayer.getEyeHeight(),
                        minecraft.thePlayer.posZ
                );

        Vec3 look =
                minecraft.thePlayer.getLook(
                        partialTicks
                );

        if (look == null) {
            return null;
        }

        Vec3 end =
                start.addVector(
                        look.xCoord
                                * MAX_RENDER_DISTANCE,
                        look.yCoord
                                * MAX_RENDER_DISTANCE,
                        look.zCoord
                                * MAX_RENDER_DISTANCE
                );

        MovingObjectPosition hit =
                minecraft.theWorld.rayTraceBlocks(
                        start,
                        end
                );

        if (hit == null
                || hit.typeOfHit
                != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }

        TileEntity tileEntity =
                minecraft.theWorld.getTileEntity(
                        hit.blockX,
                        hit.blockY,
                        hit.blockZ
                );

        if (tileEntity
                instanceof TileEntitySiegeGate) {
            return (TileEntitySiegeGate)tileEntity;
        }

        return GateRegistry.getController(
                minecraft.theWorld,
                hit.blockX,
                hit.blockY,
                hit.blockZ
        );
    }

    private static boolean shouldRenderHealthBar(
            TileEntitySiegeGate gate
    ) {
        if (gate == null
                || !gate.isFinalized()) {
            return false;
        }

        int maxHealth =
                gate.getMaxHealth();

        if (maxHealth <= 0) {
            return false;
        }

        /*
         * Full-health gates have no health bar at all.
         *
         * BREACHED gates naturally remain visible because their health is 0.
         */
        return gate.getCurrentHealth()
                < maxHealth;
    }

    private static GateHealthAnchor calculateAnchor(
            TileEntitySiegeGate gate,
            double cameraX,
            double cameraZ
    ) {
        List<GatePartData> parts =
                gate.getGateParts();

        if (parts == null
                || parts.isEmpty()) {
            return null;
        }

        double minX =
                Double.POSITIVE_INFINITY;

        double maxX =
                Double.NEGATIVE_INFINITY;

        double minY =
                Double.POSITIVE_INFINITY;

        double maxY =
                Double.NEGATIVE_INFINITY;

        double minZ =
                Double.POSITIVE_INFINITY;

        double maxZ =
                Double.NEGATIVE_INFINITY;

        for (GatePartData part
                : parts) {

            if (part == null) {
                continue;
            }

            double partX =
                    part.getAbsoluteX(
                            gate.xCoord
                    );

            double partY =
                    part.getAbsoluteY(
                            gate.yCoord
                    );

            double partZ =
                    part.getAbsoluteZ(
                            gate.zCoord
                    );

            minX =
                    Math.min(
                            minX,
                            partX
                    );

            maxX =
                    Math.max(
                            maxX,
                            partX + 1.0D
                    );

            minY =
                    Math.min(
                            minY,
                            partY
                    );

            maxY =
                    Math.max(
                            maxY,
                            partY + 1.0D
                    );

            minZ =
                    Math.min(
                            minZ,
                            partZ
                    );

            maxZ =
                    Math.max(
                            maxZ,
                            partZ + 1.0D
                    );
        }

        if (minX
                == Double.POSITIVE_INFINITY) {
            return null;
        }

        /*
         * Horizontal center of the actual irregular gate geometry.
         */
        double centerX =
                (minX + maxX)
                        * 0.5D;

        double centerZ =
                (minZ + maxZ)
                        * 0.5D;

        /*
         * Normally:
         *
         *     highest GatePart + 0.65
         *
         * But never:
         *
         *     higher than lowest GatePart + 10
         *
         * Example:
         *
         * 3-block gate:
         *     bar appears just above the top.
         *
         * 40-block fortress gate:
         *     bar stays ten blocks above its base.
         */
        double naturalY =
                maxY
                        + HEIGHT_ABOVE_GATE;

        double maximumY =
                minY
                        + MAX_HEIGHT_ABOVE_GATE_BASE;

        double centerY =
                Math.min(
                        naturalY,
                        maximumY
                );

        /*
         * Push the billboard horizontally toward the camera.
         *
         * This makes the bar sit slightly "in front" of the gate regardless
         * of whether the gate is WIDTH_X, WIDTH_Z, or which side the player
         * approaches from.
         */
        double towardCameraX =
                cameraX
                        - centerX;

        double towardCameraZ =
                cameraZ
                        - centerZ;

        double horizontalDistance =
                Math.sqrt(
                        towardCameraX
                                * towardCameraX
                                + towardCameraZ
                                * towardCameraZ
                );

        if (horizontalDistance
                > 0.0001D) {

            centerX +=
                    towardCameraX
                            / horizontalDistance
                            * FRONT_OFFSET;

            centerZ +=
                    towardCameraZ
                            / horizontalDistance
                            * FRONT_OFFSET;
        }

        return new GateHealthAnchor(
                centerX,
                centerY,
                centerZ
        );
    }

    private static void renderGateHealthBar(
            Minecraft minecraft,
            TileEntitySiegeGate gate,
            double renderX,
            double renderY,
            double renderZ
    ) {
        int maxHealth =
                Math.max(
                        1,
                        gate.getMaxHealth()
                );

        int currentHealth =
                Math.max(
                        0,
                        Math.min(
                                gate.getCurrentHealth(),
                                maxHealth
                        )
                );

        int filledWidth =
                (int)(
                        (long)BAR_WIDTH
                                * currentHealth
                                / maxHealth
                );

        boolean breached =
                gate.getGateState()
                        == GateState.BREACHED;

        String title =
                breached
                        ? gate.getGateName()
                        + " - BREACHED"
                        : gate.getGateName();

        String healthText =
                currentHealth
                        + " / "
                        + maxHealth;

        int barColor =
                gate.getGateFaction() == null
                        ? 0xFFF2F2F2
                        : 0xFF000000
                        | gate.getGateFaction()
                        .getFactionColor();

        FontRenderer fontRenderer =
                minecraft.fontRenderer;

        RenderManager renderManager =
                RenderManager.instance;

        int halfWidth =
                BAR_WIDTH / 2;

        GL11.glPushMatrix();

        GL11.glTranslated(
                renderX,
                renderY,
                renderZ
        );

        /*
         * Billboard toward the active camera.
         */
        GL11.glRotatef(
                -renderManager.playerViewY,
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glRotatef(
                renderManager.playerViewX,
                1.0F,
                0.0F,
                0.0F
        );

        /*
         * Negative X/Y is the standard Minecraft nametag-style transform.
         */
        GL11.glScalef(
                -WORLD_SCALE,
                -WORLD_SCALE,
                WORLD_SCALE
        );

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
        );

        try {
            GL11.glDisable(
                    GL11.GL_LIGHTING
            );

            GL11.glDisable(
                    GL11.GL_CULL_FACE
            );

            GL11.glEnable(
                    GL11.GL_BLEND
            );

            GL11.glBlendFunc(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA
            );

            /*
             * Visibility has already been established by the long-range view
             * ray above. Disable depth testing for the billboard itself so the
             * gate's own geometry cannot slice through the title or health bar.
             * A gate behind another block never reaches this render path.
             */
            GL11.glDisable(
                    GL11.GL_DEPTH_TEST
            );

            GL11.glDepthMask(
                    false
            );

            int titleWidth =
                    fontRenderer.getStringWidth(
                            title
                    );

            fontRenderer.drawStringWithShadow(
                    title,
                    -titleWidth / 2,
                    -14,
                    breached
                            ? 0xFF7777
                            : 0xFFFFFF
            );

            /*
             * Black outer border.
             */
            Gui.drawRect(
                    -halfWidth - 1,
                    -1,
                    halfWidth + 1,
                    BAR_HEIGHT + 1,
                    0xCC000000
            );

            /*
             * Empty/damaged portion.
             */
            Gui.drawRect(
                    -halfWidth,
                    0,
                    halfWidth,
                    BAR_HEIGHT,
                    0xFF32183C
            );

            /*
             * Remaining health.
             */
            if (filledWidth > 0) {
                Gui.drawRect(
                        -halfWidth,
                        0,
                        -halfWidth
                                + filledWidth,
                        BAR_HEIGHT,
                        barColor
                );
            }

            int healthTextWidth =
                    fontRenderer.getStringWidth(
                            healthText
                    );

            fontRenderer.drawStringWithShadow(
                    healthText,
                    -healthTextWidth / 2,
                    BAR_HEIGHT + 4,
                    breached
                            ? 0xFF7777
                            : 0xFFFFFF
            );

        } finally {
            GL11.glDepthMask(
                    true
            );

            GL11.glPopAttrib();

            GL11.glColor4f(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            GL11.glPopMatrix();
        }
    }

    private static final class GateHealthAnchor {

        private final double x;
        private final double y;
        private final double z;

        private GateHealthAnchor(
                double x,
                double y,
                double z
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}