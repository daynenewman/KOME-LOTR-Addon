package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.creation.GateBlockPosition;
import com.enovak.lotrmoremobs.siege.creation.GateCreationManager;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.network.GateCreationSelectPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class GateCreationClientHandler {

    private static final double COLOR_INSET = 0.0D;
    private static final double PANEL_OUTSET = 0.001D;

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (GateEditClientContext.isActive()
                || !ClientGateCreationState.isWorldSelectionActive()
                || event.world == null
                || !event.world.isRemote
                || event.action
                != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        GateBlockPosition controller =
                ClientGateCreationState.getControllerPosition();

        if (controller != null
                && event.x == controller.getX()
                && event.y == controller.getY()
                && event.z == controller.getZ()) {
            return;
        }

        event.setCanceled(true);

        boolean fillEnclosed =
                ClientGateCreationState.getSelectionMode()
                        == com.enovak.lotrmoremobs.siege.creation.GateSelectionMode.BLOCKS
                        && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                        || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));

        Main.network.sendToServer(
                new GateCreationSelectPacket(
                        event.x,
                        event.y,
                        event.z,
                        ClientGateCreationState.getDimensionId(),
                        controller.getX(),
                        controller.getY(),
                        controller.getZ(),
                        fillEnclosed
                )
        );
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !ClientGateCreationState.isActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;

        GateBlockPosition controller =
                ClientGateCreationState.getControllerPosition();

        if (player == null
                || player.isDead
                || !player.isEntityAlive()
                || minecraft.theWorld == null
                || player.dimension
                != ClientGateCreationState.getDimensionId()
                || controller == null
                || !minecraft.theWorld.blockExists(
                controller.getX(),
                controller.getY(),
                controller.getZ()
        )
                || minecraft.theWorld.getBlock(
                controller.getX(),
                controller.getY(),
                controller.getZ()
        ) != SiegeRegistry.gateController
                || player.getDistanceSq(
                controller.getX() + 0.5D,
                controller.getY() + 0.5D,
                controller.getZ() + 0.5D
        ) > GateCreationManager.MAX_CREATION_DISTANCE
                * GateCreationManager.MAX_CREATION_DISTANCE) {

            ClientGateCreationState.clear();
            GateCreationGuiOpenHandler.requestClose();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ClientGateCreationState.isActive()
                || GateEditClientContext.isActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        Entity camera = minecraft.renderViewEntity;

        if (camera == null) {
            return;
        }

        double cameraX =
                camera.lastTickPosX
                        + (camera.posX - camera.lastTickPosX)
                        * event.partialTicks;

        double cameraY =
                camera.lastTickPosY
                        + (camera.posY - camera.lastTickPosY)
                        * event.partialTicks;

        double cameraZ =
                camera.lastTickPosZ
                        + (camera.posZ - camera.lastTickPosZ)
                        * event.partialTicks;

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
         * The overlay is real quad geometry rather than GL_LINES.
         * The tiny polygon offset keeps it stable against block surfaces
         * while still visually hugging the exact block boundary.
         */
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-2.0F, -2.0F);

        renderSelectionOverlay(
                cameraX,
                cameraY,
                cameraZ
        );

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static void renderSelectionOverlay(
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        Tessellator tessellator = Tessellator.instance;
        Map<GateBlockPosition, GateLeaf> selections =
                ClientGateCreationState.getSelections();

        tessellator.startDrawingQuads();

        renderLeaf(
                tessellator,
                selections,
                GateLeaf.LEFT,
                0.20F,
                0.55F,
                1.00F,
                cameraX,
                cameraY,
                cameraZ
        );

        renderLeaf(
                tessellator,
                selections,
                GateLeaf.RIGHT,
                1.00F,
                0.35F,
                0.20F,
                cameraX,
                cameraY,
                cameraZ
        );

        renderLeaf(
                tessellator,
                selections,
                GateLeaf.SPLIT_CENTER,
                0.40F,
                1.00F,
                0.55F,
                cameraX,
                cameraY,
                cameraZ
        );

        /*
         * Hinges retain the normal leaf outline.
         * A neutral white gear communicates the hinge role instead of
         * assigning the hinge another arbitrary color.
         */
        tessellator.setColorRGBA_F(
                1.0F,
                1.0F,
                1.0F,
                0.95F
        );

        addHingeGear(
                tessellator,
                ClientGateCreationState.getLeftHingePosition(),
                cameraX,
                cameraY,
                cameraZ
        );

        addHingeGear(
                tessellator,
                ClientGateCreationState.getRightHingePosition(),
                cameraX,
                cameraY,
                cameraZ
        );

        tessellator.draw();
    }

    private static void renderLeaf(
            Tessellator tessellator,
            Map<GateBlockPosition, GateLeaf> selections,
            GateLeaf leaf,
            float red,
            float green,
            float blue,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        tessellator.setColorRGBA_F(
                red,
                green,
                blue,
                0.82F
        );

        for (Map.Entry<GateBlockPosition, GateLeaf> entry
                : selections.entrySet()) {

            if (entry.getValue() != leaf) {
                continue;
            }

            addLeafOutlinePanels(
                    tessellator,
                    selections,
                    entry.getKey(),
                    leaf,
                    cameraX,
                    cameraY,
                    cameraZ
            );
        }
    }

    private static void addLeafOutlinePanels(
            Tessellator tessellator,
            Map<GateBlockPosition, GateLeaf> selections,
            GateBlockPosition position,
            GateLeaf leaf,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        boolean west =
                hasSameLeafPart(
                        selections,
                        position,
                        leaf,
                        -1,
                        0,
                        0
                );

        boolean east =
                hasSameLeafPart(
                        selections,
                        position,
                        leaf,
                        1,
                        0,
                        0
                );

        boolean down =
                hasSameLeafPart(
                        selections,
                        position,
                        leaf,
                        0,
                        -1,
                        0
                );

        boolean up =
                hasSameLeafPart(
                        selections,
                        position,
                        leaf,
                        0,
                        1,
                        0
                );

        boolean north =
                hasSameLeafPart(
                        selections,
                        position,
                        leaf,
                        0,
                        0,
                        -1
                );

        boolean south =
                hasSameLeafPart(
                        selections,
                        position,
                        leaf,
                        0,
                        0,
                        1
                );

        addBlockPanels(
                tessellator,
                position.getX() - cameraX,
                position.getY() - cameraY,
                position.getZ() - cameraZ,
                COLOR_INSET,
                west,
                east,
                down,
                up,
                north,
                south
        );
    }

    private static boolean hasSameLeafPart(
            Map<GateBlockPosition, GateLeaf> selections,
            GateBlockPosition source,
            GateLeaf leaf,
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        return selections.get(
                source.offset(
                        offsetX,
                        offsetY,
                        offsetZ
                )
        ) == leaf;
    }

    /*
     * Draws only the exposed perimeter of a connected same-leaf region.
     * Shared boundaries between sibling blocks are suppressed.
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
            GateBlockPosition hinge,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        if (hinge == null) {
            return;
        }

        double x =
                hinge.getX()
                        - cameraX;

        double y =
                hinge.getY()
                        - cameraY;

        double z =
                hinge.getZ()
                        - cameraZ;

        double offset = 0.008D;

        /*
         * Draw the gear on all six faces so it remains readable from
         * either side of the gate and from oblique viewing angles.
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