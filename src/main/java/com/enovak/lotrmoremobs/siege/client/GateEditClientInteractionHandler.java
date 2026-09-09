package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.edit.GateEditSelectionMode;
import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.management.FinalizedGateSnapshot;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftActionPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftSnapshotPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Dedicated real-world selection and non-mutating draft overlay.
 *
 * Selected blocks are rendered with inset translucent face panels rather
 * than GL_LINES. This avoids line-rasterization instability under OptiFine
 * and leaves a visible separator between neighboring selected blocks.
 */
public final class GateEditClientInteractionHandler {

    private static final double BACKING_INSET = 0.035D;
    private static final double COLOR_INSET = 0.0D;
    private static final double PANEL_OUTSET = 0.001D;

    /**
     * When EDIT_EXISTING has temporarily closed the GUI for real-world block
     * selection, vanilla ESC would otherwise open the pause menu and leave the
     * transient edit session alive. Treat that ESC exactly like Discard
     * Changes and consume the pause-menu open.
     */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (!(e.gui instanceof GuiIngameMenu)
                || !GateEditClientContext.isActive()
                || GateEditClientContext.getToken() == null) {
            return;
        }

        UUID token =
                GateEditClientContext.getToken();

        GateEditClientContext.clear();

        Main.network.sendToServer(
                new com.enovak.lotrmoremobs.siege.network.GateEditCancelPacket(
                        token
                )
        );

        e.setCanceled(
                true
        );
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.world == null
                || !e.world.isRemote
                || e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                || !GateEditClientContext.isWorldSelectionActive()) {
            return;
        }

        if (e.x == GateEditClientContext.getControllerX()
                && e.y == GateEditClientContext.getControllerY()
                && e.z == GateEditClientContext.getControllerZ()) {
            return;
        }

        GateEditSelectionMode mode =
                GateEditClientContext.getSelectionMode();

        if (mode.getAction() == null) {
            return;
        }

        e.setCanceled(true);

        boolean fillEnclosed =
                mode.getAction().isSelect()
                        && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                        || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));

        Main.network.sendToServer(
                new GateEditDraftActionPacket(
                        GateEditClientContext.getToken(),
                        mode.getAction(),
                        e.x,
                        e.y,
                        e.z,
                        fillEnclosed
                )
        );
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!GateEditClientContext.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;

        if (camera == null) {
            return;
        }

        double cameraX =
                camera.lastTickPosX
                        + (camera.posX - camera.lastTickPosX)
                        * e.partialTicks;

        double cameraY =
                camera.lastTickPosY
                        + (camera.posY - camera.lastTickPosY)
                        * e.partialTicks;

        double cameraZ =
                camera.lastTickPosZ
                        + (camera.posZ - camera.lastTickPosZ)
                        * e.partialTicks;

        FinalizedGateSnapshot snapshot =
                getMatchingFinalizedSnapshot();

        GL11.glPushMatrix();

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_POLYGON_BIT
        );

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        /*
         * Quad geometry still receives a tiny depth bias so it stays stable
         * against the underlying block surface, but there is deliberately no
         * dark backing pass anymore.
         */
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-2.0F, -2.0F);

        renderColorPass(
                snapshot,
                cameraX,
                cameraY,
                cameraZ
        );

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static void renderBackingPass(
            FinalizedGateSnapshot snapshot,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        Tessellator tessellator = Tessellator.instance;

        tessellator.startDrawingQuads();

        tessellator.setColorRGBA_F(
                0.025F,
                0.025F,
                0.025F,
                0.82F
        );

        for (GateEditClientContext.DraftPart part
                : GateEditClientContext.getParts()) {

            addLeafOutlinePanels(
                    tessellator,
                    part,
                    cameraX,
                    cameraY,
                    cameraZ,
                    COLOR_INSET
            );
        }

        if (snapshot != null) {
            for (FinalizedGateSnapshot.PartEntry part
                    : snapshot.getParts()) {

                if (!GateEditClientContext.containsRelative(
                        part.getRelativeX(),
                        part.getRelativeY(),
                        part.getRelativeZ()
                )) {
                    addBlockPanels(
                            tessellator,
                            GateEditClientContext.getControllerX()
                                    + part.getRelativeX()
                                    - cameraX,
                            GateEditClientContext.getControllerY()
                                    + part.getRelativeY()
                                    - cameraY,
                            GateEditClientContext.getControllerZ()
                                    + part.getRelativeZ()
                                    - cameraZ,
                            BACKING_INSET
                    );
                }
            }
        }

        tessellator.draw();
    }

    private static void renderColorPass(
            FinalizedGateSnapshot snapshot,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        Tessellator tessellator = Tessellator.instance;

        tessellator.startDrawingQuads();

        for (GateEditClientContext.DraftPart part
                : GateEditClientContext.getParts()) {

            float alpha;

            if (part.kind
                    == GateEditDraftSnapshotPacket.VisualKind.ADDED) {
                alpha = 0.82F;
            } else if (part.kind
                    == GateEditDraftSnapshotPacket.VisualKind
                    .ORIGINAL_ROLE_CHANGED) {
                alpha = 0.72F;
            } else {
                alpha = 0.52F;
            }

            setRoleColor(
                    tessellator,
                    part.leaf,
                    alpha
            );

            addLeafOutlinePanels(
                    tessellator,
                    part,
                    cameraX,
                    cameraY,
                    cameraZ,
                    COLOR_INSET
            );
        }


        /*
         * Hinges keep their normal leaf outline. A neutral cog marker identifies
         * the hinge function without assigning the hinge a separate role color.
         */
        tessellator.setColorRGBA_F(
                1.0F,
                1.0F,
                1.0F,
                0.95F
        );

        addHingeGear(
                tessellator,
                GateEditClientContext.getLeftHinge(),
                cameraX,
                cameraY,
                cameraZ
        );

        addHingeGear(
                tessellator,
                GateEditClientContext.getRightHinge(),
                cameraX,
                cameraY,
                cameraZ
        );

        tessellator.draw();
    }

    private static FinalizedGateSnapshot getMatchingFinalizedSnapshot() {
        FinalizedGateSnapshot snapshot =
                GateFinalizedInspectionClientContext.getSnapshot();

        if (snapshot == null
                || snapshot.getGateUuid() == null
                || !snapshot.getGateUuid().equals(
                GateEditClientContext.getGateUuid()
        )
                || snapshot.getBaseStructureRevision()
                != GateEditClientContext.getRevision()) {
            return null;
        }

        return snapshot;
    }

    private static void setRoleColor(
            Tessellator tessellator,
            GateLeaf leaf,
            float alpha
    ) {
        if (leaf == GateLeaf.LEFT) {
            tessellator.setColorRGBA_F(
                    0.20F,
                    0.55F,
                    1.0F,
                    alpha
            );
            return;
        }

        if (leaf == GateLeaf.RIGHT) {
            tessellator.setColorRGBA_F(
                    1.0F,
                    0.35F,
                    0.20F,
                    alpha
            );
            return;
        }

        tessellator.setColorRGBA_F(
                0.40F,
                1.0F,
                0.55F,
                alpha
        );
    }

    private static void addHingePanels(
            Tessellator tessellator,
            GateHinge hinge,
            double cameraX,
            double cameraY,
            double cameraZ,
            double inset
    ) {
        if (hinge == null) {
            return;
        }

        addBlockPanels(
                tessellator,
                GateEditClientContext.getControllerX()
                        + hinge.getRelativeX()
                        - cameraX,
                GateEditClientContext.getControllerY()
                        - cameraY,
                GateEditClientContext.getControllerZ()
                        + hinge.getRelativeZ()
                        - cameraZ,
                inset
        );
    }

    /**
     * Adds six inset face panels around one block-sized cell.
     *
     * The panels do not reach the cell edges. Neighboring selected blocks
     * therefore retain a visible gap instead of visually merging into one
     * large outline.
     */
    private static void addLeafOutlinePanels(
            Tessellator tessellator,
            GateEditClientContext.DraftPart part,
            double cameraX,
            double cameraY,
            double cameraZ,
            double inset
    ) {
        boolean west =
                hasSameLeafPart(part, -1, 0, 0);

        boolean east =
                hasSameLeafPart(part, 1, 0, 0);

        boolean down =
                hasSameLeafPart(part, 0, -1, 0);

        boolean up =
                hasSameLeafPart(part, 0, 1, 0);

        boolean north =
                hasSameLeafPart(part, 0, 0, -1);

        boolean south =
                hasSameLeafPart(part, 0, 0, 1);

        addBlockPanels(
                tessellator,
                GateEditClientContext.getControllerX()
                        + part.relativeX
                        - cameraX,
                GateEditClientContext.getControllerY()
                        + part.relativeY
                        - cameraY,
                GateEditClientContext.getControllerZ()
                        + part.relativeZ
                        - cameraZ,
                inset,
                west,
                east,
                down,
                up,
                north,
                south
        );
    }

    private static boolean hasSameLeafPart(
            GateEditClientContext.DraftPart source,
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        int targetX =
                source.relativeX + offsetX;

        int targetY =
                source.relativeY + offsetY;

        int targetZ =
                source.relativeZ + offsetZ;

        for (GateEditClientContext.DraftPart other
                : GateEditClientContext.getParts()) {

            if (other.leaf == source.leaf
                    && other.relativeX == targetX
                    && other.relativeY == targetY
                    && other.relativeZ == targetZ) {
                return true;
            }
        }

        return false;
    }

    /*
     * Normal non-merged outline. Used for things such as removed-part
     * visualization where every individual cell should remain identifiable.
     */
    private static void addBlockPanels(
            Tessellator tessellator,
            double x,
            double y,
            double z,
            double inset
    ) {
        addBlockPanels(
                tessellator,
                x,
                y,
                z,
                inset,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    /*
     * Draws only the perimeter edges which are exposed at the outside of a
     * connected same-leaf region.
     *
     * Adjacent blocks belonging to the same leaf suppress their shared border,
     * making the complete leaf read visually as one continuous structure.
     */
    private static void addBlockPanels(
            Tessellator tessellator,
            double x,
            double y,
            double z,
            double inset,
            boolean westNeighbor,
            boolean eastNeighbor,
            boolean downNeighbor,
            boolean upNeighbor,
            boolean northNeighbor,
            boolean southNeighbor
    ) {
        /*
         * Thin quad strips give us the appearance of an outline without relying
         * on GL_LINES, which is considerably more stable with OptiFine.
         */
        double thickness = 0.035D;

        double minX = x + inset;
        double minY = y + inset;
        double minZ = z + inset;

        double maxX = x + 1.0D - inset;
        double maxY = y + 1.0D - inset;
        double maxZ = z + 1.0D - inset;

        double innerMinX = minX + thickness;
        double innerMinY = minY + thickness;
        double innerMinZ = minZ + thickness;

        double innerMaxX = maxX - thickness;
        double innerMaxY = maxY - thickness;
        double innerMaxZ = maxZ - thickness;

        double west = x - PANEL_OUTSET;
        double east = x + 1.0D + PANEL_OUTSET;

        double bottom = y - PANEL_OUTSET;
        double top = y + 1.0D + PANEL_OUTSET;

        double north = z - PANEL_OUTSET;
        double south = z + 1.0D + PANEL_OUTSET;

        /*
         * WEST FACE
         */
        if (!westNeighbor) {
            if (!downNeighbor) {
                quad(
                        tessellator,
                        west, minY, minZ,
                        west, innerMinY, minZ,
                        west, innerMinY, maxZ,
                        west, minY, maxZ
                );
            }

            if (!upNeighbor) {
                quad(
                        tessellator,
                        west, innerMaxY, minZ,
                        west, maxY, minZ,
                        west, maxY, maxZ,
                        west, innerMaxY, maxZ
                );
            }

            if (!northNeighbor) {
                quad(
                        tessellator,
                        west, innerMinY, minZ,
                        west, innerMaxY, minZ,
                        west, innerMaxY, innerMinZ,
                        west, innerMinY, innerMinZ
                );
            }

            if (!southNeighbor) {
                quad(
                        tessellator,
                        west, innerMinY, innerMaxZ,
                        west, innerMaxY, innerMaxZ,
                        west, innerMaxY, maxZ,
                        west, innerMinY, maxZ
                );
            }
        }

        /*
         * EAST FACE
         */
        if (!eastNeighbor) {
            if (!downNeighbor) {
                quad(
                        tessellator,
                        east, minY, minZ,
                        east, minY, maxZ,
                        east, innerMinY, maxZ,
                        east, innerMinY, minZ
                );
            }

            if (!upNeighbor) {
                quad(
                        tessellator,
                        east, innerMaxY, minZ,
                        east, innerMaxY, maxZ,
                        east, maxY, maxZ,
                        east, maxY, minZ
                );
            }

            if (!northNeighbor) {
                quad(
                        tessellator,
                        east, innerMinY, minZ,
                        east, innerMinY, innerMinZ,
                        east, innerMaxY, innerMinZ,
                        east, innerMaxY, minZ
                );
            }

            if (!southNeighbor) {
                quad(
                        tessellator,
                        east, innerMinY, innerMaxZ,
                        east, innerMinY, maxZ,
                        east, innerMaxY, maxZ,
                        east, innerMaxY, innerMaxZ
                );
            }
        }

        /*
         * BOTTOM FACE
         *
         * A same-leaf block directly below means this plane is internal to the
         * leaf, so nothing should be rendered here.
         */
        if (!downNeighbor) {
            quad(
                    tessellator,
                    minX, bottom, minZ,
                    minX, bottom, maxZ,
                    maxX, bottom, maxZ,
                    maxX, bottom, minZ
            );
        }

        /*
         * TOP FACE
         *
         * A same-leaf block directly above means this plane is internal to the
         * leaf, so nothing should be rendered here.
         */
        if (!upNeighbor) {
            quad(
                    tessellator,
                    minX, top, minZ,
                    maxX, top, minZ,
                    maxX, top, maxZ,
                    minX, top, maxZ
            );
        }

        /*
         * NORTH FACE
         */
        if (!northNeighbor) {
            if (!westNeighbor) {
                quad(
                        tessellator,
                        minX, minY, north,
                        innerMinX, minY, north,
                        innerMinX, maxY, north,
                        minX, maxY, north
                );
            }

            if (!eastNeighbor) {
                quad(
                        tessellator,
                        innerMaxX, minY, north,
                        maxX, minY, north,
                        maxX, maxY, north,
                        innerMaxX, maxY, north
                );
            }

            if (!downNeighbor) {
                quad(
                        tessellator,
                        innerMinX, minY, north,
                        innerMaxX, minY, north,
                        innerMaxX, innerMinY, north,
                        innerMinX, innerMinY, north
                );
            }

            if (!upNeighbor) {
                quad(
                        tessellator,
                        innerMinX, innerMaxY, north,
                        innerMaxX, innerMaxY, north,
                        innerMaxX, maxY, north,
                        innerMinX, maxY, north
                );
            }
        }

        /*
         * SOUTH FACE
         */
        if (!southNeighbor) {
            if (!westNeighbor) {
                quad(
                        tessellator,
                        minX, minY, south,
                        minX, maxY, south,
                        innerMinX, maxY, south,
                        innerMinX, minY, south
                );
            }

            if (!eastNeighbor) {
                quad(
                        tessellator,
                        innerMaxX, minY, south,
                        innerMaxX, maxY, south,
                        maxX, maxY, south,
                        maxX, minY, south
                );
            }

            if (!downNeighbor) {
                quad(
                        tessellator,
                        innerMinX, minY, south,
                        innerMinX, innerMinY, south,
                        innerMaxX, innerMinY, south,
                        innerMaxX, minY, south
                );
            }

            if (!upNeighbor) {
                quad(
                        tessellator,
                        innerMinX, innerMaxY, south,
                        innerMinX, maxY, south,
                        innerMaxX, maxY, south,
                        innerMaxX, innerMaxY, south
                );
            }
        }
    }

    private static void addHingeGear(
            Tessellator tessellator,
            GateHinge hinge,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        if (hinge == null) {
            return;
        }

        double x =
                GateEditClientContext.getControllerX()
                        + hinge.getRelativeX()
                        - cameraX;

        double y =
                GateEditClientContext.getControllerY()
                        - cameraY;

        double z =
                GateEditClientContext.getControllerZ()
                        + hinge.getRelativeZ()
                        - cameraZ;

        double offset = 0.008D;

        /*
         * Put the cog on all six faces. That way the hinge remains obvious
         * regardless of which side of the gate the player is viewing.
         */
        addGearX(
                tessellator,
                x - offset,
                y,
                z
        );

        addGearX(
                tessellator,
                x + 1.0D + offset,
                y,
                z
        );

        addGearY(
                tessellator,
                y - offset,
                x,
                z
        );

        addGearY(
                tessellator,
                y + 1.0D + offset,
                x,
                z
        );

        addGearZ(
                tessellator,
                z - offset,
                x,
                y
        );

        addGearZ(
                tessellator,
                z + 1.0D + offset,
                x,
                y
        );
    }

    private static void addGearX(
            Tessellator tessellator,
            double plane,
            double baseY,
            double baseZ
    ) {
        gearRectX(tessellator, plane, baseY, baseZ, 0.34D, 0.34D, 0.66D, 0.42D);
        gearRectX(tessellator, plane, baseY, baseZ, 0.34D, 0.58D, 0.66D, 0.66D);
        gearRectX(tessellator, plane, baseY, baseZ, 0.34D, 0.42D, 0.42D, 0.58D);
        gearRectX(tessellator, plane, baseY, baseZ, 0.58D, 0.42D, 0.66D, 0.58D);

        gearRectX(tessellator, plane, baseY, baseZ, 0.45D, 0.25D, 0.55D, 0.34D);
        gearRectX(tessellator, plane, baseY, baseZ, 0.45D, 0.66D, 0.55D, 0.75D);
        gearRectX(tessellator, plane, baseY, baseZ, 0.25D, 0.45D, 0.34D, 0.55D);
        gearRectX(tessellator, plane, baseY, baseZ, 0.66D, 0.45D, 0.75D, 0.55D);
    }

    private static void addGearY(
            Tessellator tessellator,
            double plane,
            double baseX,
            double baseZ
    ) {
        gearRectY(tessellator, plane, baseX, baseZ, 0.34D, 0.34D, 0.66D, 0.42D);
        gearRectY(tessellator, plane, baseX, baseZ, 0.34D, 0.58D, 0.66D, 0.66D);
        gearRectY(tessellator, plane, baseX, baseZ, 0.34D, 0.42D, 0.42D, 0.58D);
        gearRectY(tessellator, plane, baseX, baseZ, 0.58D, 0.42D, 0.66D, 0.58D);

        gearRectY(tessellator, plane, baseX, baseZ, 0.45D, 0.25D, 0.55D, 0.34D);
        gearRectY(tessellator, plane, baseX, baseZ, 0.45D, 0.66D, 0.55D, 0.75D);
        gearRectY(tessellator, plane, baseX, baseZ, 0.25D, 0.45D, 0.34D, 0.55D);
        gearRectY(tessellator, plane, baseX, baseZ, 0.66D, 0.45D, 0.75D, 0.55D);
    }

    private static void addGearZ(
            Tessellator tessellator,
            double plane,
            double baseX,
            double baseY
    ) {
        gearRectZ(tessellator, plane, baseX, baseY, 0.34D, 0.34D, 0.66D, 0.42D);
        gearRectZ(tessellator, plane, baseX, baseY, 0.34D, 0.58D, 0.66D, 0.66D);
        gearRectZ(tessellator, plane, baseX, baseY, 0.34D, 0.42D, 0.42D, 0.58D);
        gearRectZ(tessellator, plane, baseX, baseY, 0.58D, 0.42D, 0.66D, 0.58D);

        gearRectZ(tessellator, plane, baseX, baseY, 0.45D, 0.25D, 0.55D, 0.34D);
        gearRectZ(tessellator, plane, baseX, baseY, 0.45D, 0.66D, 0.55D, 0.75D);
        gearRectZ(tessellator, plane, baseX, baseY, 0.25D, 0.45D, 0.34D, 0.55D);
        gearRectZ(tessellator, plane, baseX, baseY, 0.66D, 0.45D, 0.75D, 0.55D);
    }

    private static void gearRectX(
            Tessellator tessellator,
            double x,
            double baseY,
            double baseZ,
            double u1,
            double v1,
            double u2,
            double v2
    ) {
        quad(
                tessellator,
                x, baseY + v1, baseZ + u1,
                x, baseY + v2, baseZ + u1,
                x, baseY + v2, baseZ + u2,
                x, baseY + v1, baseZ + u2
        );
    }

    private static void gearRectY(
            Tessellator tessellator,
            double y,
            double baseX,
            double baseZ,
            double u1,
            double v1,
            double u2,
            double v2
    ) {
        quad(
                tessellator,
                baseX + u1, y, baseZ + v1,
                baseX + u1, y, baseZ + v2,
                baseX + u2, y, baseZ + v2,
                baseX + u2, y, baseZ + v1
        );
    }

    private static void gearRectZ(
            Tessellator tessellator,
            double z,
            double baseX,
            double baseY,
            double u1,
            double v1,
            double u2,
            double v2
    ) {
        quad(
                tessellator,
                baseX + u1, baseY + v1, z,
                baseX + u2, baseY + v1, z,
                baseX + u2, baseY + v2, z,
                baseX + u1, baseY + v2, z
        );
    }

    private static void quad(
            Tessellator tessellator,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double x3,
            double y3,
            double z3,
            double x4,
            double y4,
            double z4
    ) {
        tessellator.addVertex(x1, y1, z1);
        tessellator.addVertex(x2, y2, z2);
        tessellator.addVertex(x3, y3, z3);
        tessellator.addVertex(x4, y4, z4);
    }
}