package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.client.command.CommandEditRamTargets;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.network.RamTargetSelectPacket;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public class RamTargetClientHandler {

    private static final int CANDIDATE_REFRESH_TICKS = 5;
    private static final double OUTLINE_FACE_OFFSET = 0.025D;
    private static final double HOVER_TOLERANCE = 0.075D;
    private static final float OUTLINE_HALO_WIDTH = 7.0F;
    private static final float OUTLINE_SOFT_WIDTH = 4.0F;
    private static final float OUTLINE_CORE_WIDTH = 2.25F;
    private static boolean clientCommandRegistered;

    private final List<TargetCandidate> candidates =
            new ArrayList<TargetCandidate>();
    private int refreshTicks;
    private TargetCandidate hoveredCandidate;

    public RamTargetClientHandler() {
        if (!clientCommandRegistered) {
            clientCommandRegistered = true;
            ClientCommandHandler.instance.registerCommand(
                    new CommandEditRamTargets()
            );
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!ClientRamTargetState.isActive()
                || event == null
                || event.gui == null) {
            return;
        }

        /*
         * ESC normally opens GuiIngameMenu. While editing a ram queue, consume
         * that GUI open so ESC exits targeting mode without also showing the
         * pause/options screen.
         */
        if (event.gui instanceof GuiIngameMenu) {
            event.setCanceled(true);
            ClientRamTargetState.clear();
            clearTransientState();
            return;
        }

        /*
         * Inventory remains a normal GUI action, but entering it ends the
         * targeting overlay.
         */
        if (event.gui instanceof GuiInventory) {
            ClientRamTargetState.clear();
            clearTransientState();
        }
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (!ClientRamTargetState.isActive()
                || event == null
                || event.button != 1
                || !event.buttonstate) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            hoveredCandidate = findHoveredCandidate(minecraft.thePlayer);
        }

        if (hoveredCandidate == null) {
            return;
        }

        event.setCanceled(true);
        Main.network.sendToServer(new RamTargetSelectPacket(
                ClientRamTargetState.getDimensionId(),
                ClientRamTargetState.getRamEntityId(),
                hoveredCandidate.x,
                hoveredCandidate.y,
                hoveredCandidate.z
        ));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!ClientRamTargetState.isActive()) {
            clearTransientState();
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;

        if (player == null
                || player.isDead
                || minecraft.theWorld == null
                || player.dimension
                != ClientRamTargetState.getDimensionId()) {
            ClientRamTargetState.clear();
            clearTransientState();
            return;
        }

        if (++refreshTicks >= CANDIDATE_REFRESH_TICKS
                || candidates.isEmpty()) {
            refreshTicks = 0;
            rebuildCandidates(player);
        }

        hoveredCandidate = findHoveredCandidate(player);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ClientRamTargetState.isActive() || candidates.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        if (player == null) {
            return;
        }

        double cameraX = player.lastTickPosX
                + (player.posX - player.lastTickPosX) * event.partialTicks;
        double cameraY = player.lastTickPosY
                + (player.posY - player.lastTickPosY) * event.partialTicks;
        double cameraZ = player.lastTickPosZ
                + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_LINE_BIT
        );
        GL11.glPushMatrix();

        try {
            GL11.glTranslated(-cameraX, -cameraY, -cameraZ);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA
            );
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(
                    GL11.GL_LINE_SMOOTH_HINT,
                    GL11.GL_NICEST
            );

            for (TargetCandidate candidate : candidates) {
                int queueIndex = getQueueIndex(candidate);
                boolean hovered = candidate == hoveredCandidate;

                float red;
                float green;
                float blue;
                float alpha;

                if (queueIndex >= 0) {
                    if (hovered) {
                        red = 1.00F;
                        green = 0.20F;
                        blue = 0.16F;
                    } else {
                        red = 0.30F;
                        green = 1.00F;
                        blue = 0.38F;
                    }
                    alpha = 0.98F;

                } else if (hovered) {
                    red = 1.00F;
                    green = 0.66F;
                    blue = 0.16F;
                    alpha = 1.00F;

                } else {
                    /*
                     * Slightly cool white, similar to the spectral/glowing
                     * outline family used by newer Minecraft.
                     */
                    red = 0.92F;
                    green = 0.97F;
                    blue = 1.00F;
                    alpha = 0.92F;
                }

                drawSpectralSilhouette(
                        candidate,
                        player,
                        red,
                        green,
                        blue,
                        alpha
                );
            }

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            RenderHelper.disableStandardItemLighting();

            for (TargetCandidate candidate : candidates) {
                int queueIndex = getQueueIndex(candidate);
                if (queueIndex < 0) {
                    continue;
                }

                drawQueueNumber(
                        minecraft,
                        player,
                        candidate,
                        queueIndex + 1,
                        candidate == hoveredCandidate
                                ? 0xFFFF4444
                                : 0xFF55FF55
                );
            }

        } finally {
            GL11.glDepthMask(true);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void rebuildCandidates(EntityPlayer player) {
        candidates.clear();

        UUID ramUuid = ClientRamTargetState.getRamUuid();
        LOTRFaction ramFaction = ClientRamTargetState.getRamFaction();

        for (TileEntitySiegeGate gate
                : GateRegistry.getLoadedControllers(player.worldObj)) {
            if (!isVisibleCandidate(
                    player,
                    ramUuid,
                    ramFaction,
                    gate
            )) {
                continue;
            }

            AxisAlignedBB bounds = gate.getRenderBoundingBox();
            if (bounds == null) {
                continue;
            }

            TargetCandidate candidate =
                    TargetCandidate.fromGate(
                            gate,
                            bounds
                    );

            if (candidate != null) {
                candidates.add(
                        candidate
                );
            }
        }
    }

    private static boolean isVisibleCandidate(
            EntityPlayer player,
            UUID ramUuid,
            LOTRFaction ramFaction,
            TileEntitySiegeGate gate
    ) {
        if (player == null
                || gate == null
                || !gate.isFinalized()
                || gate.getGateState() == GateState.BREACHED) {
            return false;
        }

        UUID reservation = gate.getReservedRamUuid();
        if (reservation != null
                && (ramUuid == null || !reservation.equals(ramUuid))) {
            return false;
        }

        LOTRFaction gateFaction = gate.getGateFaction();

        if (ramFaction != null
                && gateFaction != null
                && ramFaction == gateFaction) {
            return false;
        }

        if (!player.capabilities.isCreativeMode
                && ramFaction != null
                && gateFaction != null
                && ramFaction.isAlly(gateFaction)) {
            return false;
        }

        return true;
    }

    private TargetCandidate findHoveredCandidate(EntityPlayer player) {
        if (player == null || candidates.isEmpty()) {
            return null;
        }

        Vec3 start = Vec3.createVectorHelper(
                player.posX,
                player.posY + player.getEyeHeight(),
                player.posZ
        );

        Vec3 look =
                player.getLookVec();

        TargetCandidate best =
                null;

        double bestDistance =
                Double.MAX_VALUE;

        for (TargetCandidate candidate : candidates) {
            double distance =
                    candidate.getRayIntersectionDistance(
                            start,
                            look
                    );

            if (distance < 0.0D
                    || distance >= bestDistance) {
                continue;
            }

            bestDistance =
                    distance;

            best =
                    candidate;
        }

        return best;
    }

    private static int getQueueIndex(TargetCandidate candidate) {
        return candidate == null
                ? -1
                : ClientRamTargetState.getQueueIndex(
                ClientRamTargetState.getDimensionId(),
                candidate.x,
                candidate.y,
                candidate.z
        );
    }

    private static void drawSpectralSilhouette(
            TargetCandidate candidate,
            EntityPlayer player,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        /*
         * Three line passes produce a restrained glow: a broad faint halo,
         * a softer middle edge, and a crisp bright core.
         */
        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE
        );

        drawSilhouettePass(
                candidate,
                player,
                red,
                green,
                blue,
                alpha * 0.18F,
                OUTLINE_HALO_WIDTH
        );

        drawSilhouettePass(
                candidate,
                player,
                red,
                green,
                blue,
                alpha * 0.34F,
                OUTLINE_SOFT_WIDTH
        );

        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        drawSilhouettePass(
                candidate,
                player,
                red,
                green,
                blue,
                alpha,
                OUTLINE_CORE_WIDTH
        );
    }

    private static void drawSilhouettePass(
            TargetCandidate candidate,
            EntityPlayer player,
            float red,
            float green,
            float blue,
            float alpha,
            float lineWidth
    ) {
        if (candidate == null
                || player == null
                || candidate.cells.isEmpty()) {
            return;
        }

        double centerDepth =
                (candidate.minimumDepth
                        + candidate.maximumDepth)
                        * 0.5D;

        double faceDepth;

        if (candidate.orientation
                == GateOrientation.WIDTH_X) {

            faceDepth =
                    player.posZ <= centerDepth
                            ? candidate.minimumDepth
                            - OUTLINE_FACE_OFFSET
                            : candidate.maximumDepth
                            + OUTLINE_FACE_OFFSET;

        } else {
            faceDepth =
                    player.posX <= centerDepth
                            ? candidate.minimumDepth
                            - OUTLINE_FACE_OFFSET
                            : candidate.maximumDepth
                            + OUTLINE_FACE_OFFSET;
        }

        GL11.glLineWidth(
                lineWidth
        );

        Tessellator tessellator =
                Tessellator.instance;

        tessellator.startDrawing(
                GL11.GL_LINES
        );

        tessellator.setColorRGBA_F(
                red,
                green,
                blue,
                alpha
        );

        for (SilhouetteCell cell : candidate.cells) {
            int horizontal =
                    cell.horizontal;

            int y =
                    cell.y;

            if (!candidate.containsCell(
                    horizontal,
                    y - 1
            )) {
                addSilhouetteLine(
                        tessellator,
                        candidate.orientation,
                        horizontal,
                        y,
                        horizontal + 1,
                        y,
                        faceDepth
                );
            }

            if (!candidate.containsCell(
                    horizontal,
                    y + 1
            )) {
                addSilhouetteLine(
                        tessellator,
                        candidate.orientation,
                        horizontal,
                        y + 1,
                        horizontal + 1,
                        y + 1,
                        faceDepth
                );
            }

            if (!candidate.containsCell(
                    horizontal - 1,
                    y
            )) {
                addSilhouetteLine(
                        tessellator,
                        candidate.orientation,
                        horizontal,
                        y,
                        horizontal,
                        y + 1,
                        faceDepth
                );
            }

            if (!candidate.containsCell(
                    horizontal + 1,
                    y
            )) {
                addSilhouetteLine(
                        tessellator,
                        candidate.orientation,
                        horizontal + 1,
                        y,
                        horizontal + 1,
                        y + 1,
                        faceDepth
                );
            }
        }

        tessellator.draw();
    }

    private static void addSilhouetteLine(
            Tessellator tessellator,
            GateOrientation orientation,
            double horizontal0,
            double y0,
            double horizontal1,
            double y1,
            double depth
    ) {
        if (orientation == GateOrientation.WIDTH_X) {
            addLine(
                    tessellator,
                    horizontal0,
                    y0,
                    depth,
                    horizontal1,
                    y1,
                    depth
            );

        } else {
            addLine(
                    tessellator,
                    depth,
                    y0,
                    horizontal0,
                    depth,
                    y1,
                    horizontal1
            );
        }
    }

    private static void drawQueueNumber(
            Minecraft minecraft,
            EntityPlayer player,
            TargetCandidate candidate,
            int queueNumber,
            int color
    ) {
        double centerX =
                (candidate.bounds.minX
                        + candidate.bounds.maxX)
                        * 0.5D;

        double centerY =
                (candidate.bounds.minY
                        + candidate.bounds.maxY)
                        * 0.5D;

        double centerZ =
                (candidate.bounds.minZ
                        + candidate.bounds.maxZ)
                        * 0.5D;

        double distance =
                player.getDistance(
                        centerX,
                        centerY,
                        centerZ
                );

        float distanceScale =
                (float)Math.max(
                        1.0D,
                        distance / 16.0D
                );

        float scale =
                0.025F * distanceScale;

        String text =
                Integer.toString(
                        queueNumber
                );

        int width =
                minecraft.fontRenderer
                        .getStringWidth(
                                text
                        );

        GL11.glPushMatrix();

        GL11.glTranslated(
                centerX,
                centerY,
                centerZ
        );

        GL11.glRotatef(
                -RenderManager.instance.playerViewY,
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glRotatef(
                RenderManager.instance.playerViewX,
                1.0F,
                0.0F,
                0.0F
        );

        GL11.glScalef(
                -scale,
                -scale,
                scale
        );

        GL11.glDisable(
                GL11.GL_DEPTH_TEST
        );

        GL11.glDepthMask(
                false
        );

        minecraft.fontRenderer.drawStringWithShadow(
                text,
                -width / 2,
                -4,
                color
        );

        GL11.glDepthMask(
                true
        );

        GL11.glPopMatrix();
    }

    private static void addLine(
            Tessellator tessellator,
            double x0,
            double y0,
            double z0,
            double x1,
            double y1,
            double z1
    ) {
        tessellator.addVertex(x0, y0, z0);
        tessellator.addVertex(x1, y1, z1);
    }

    private void clearTransientState() {
        candidates.clear();
        hoveredCandidate = null;
        refreshTicks = 0;
    }

    private static final class TargetCandidate {

        private final int x;
        private final int y;
        private final int z;
        private final AxisAlignedBB bounds;

        private final GateOrientation orientation;

        private final Set<SilhouetteCell> cells;

        private final double minimumDepth;
        private final double maximumDepth;

        private TargetCandidate(
                int x,
                int y,
                int z,
                AxisAlignedBB bounds,
                GateOrientation orientation,
                Set<SilhouetteCell> cells,
                double minimumDepth,
                double maximumDepth
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.bounds = bounds;
            this.orientation = orientation;
            this.cells = cells;
            this.minimumDepth = minimumDepth;
            this.maximumDepth = maximumDepth;
        }

        private static TargetCandidate fromGate(
                TileEntitySiegeGate gate,
                AxisAlignedBB bounds
        ) {
            if (gate == null
                    || bounds == null
                    || gate.getGateOrientation() == null) {
                return null;
            }

            GateOrientation orientation =
                    gate.getGateOrientation();

            Set<SilhouetteCell> cells =
                    new HashSet<SilhouetteCell>();

            int minimumDepth =
                    Integer.MAX_VALUE;

            int maximumDepth =
                    Integer.MIN_VALUE;

            for (GatePartData part : gate.getGateParts()) {
                if (part == null
                        || !part.hasValidAbsolutePosition(
                        gate.xCoord,
                        gate.yCoord,
                        gate.zCoord
                )) {
                    continue;
                }

                int absoluteX =
                        part.getAbsoluteX(
                                gate.xCoord
                        );

                int absoluteY =
                        part.getAbsoluteY(
                                gate.yCoord
                        );

                int absoluteZ =
                        part.getAbsoluteZ(
                                gate.zCoord
                        );

                int horizontal;

                int depth;

                if (orientation == GateOrientation.WIDTH_X) {
                    horizontal =
                            absoluteX;

                    depth =
                            absoluteZ;

                } else {
                    horizontal =
                            absoluteZ;

                    depth =
                            absoluteX;
                }

                cells.add(
                        new SilhouetteCell(
                                horizontal,
                                absoluteY
                        )
                );

                minimumDepth =
                        Math.min(
                                minimumDepth,
                                depth
                        );

                maximumDepth =
                        Math.max(
                                maximumDepth,
                                depth + 1
                        );
            }

            if (cells.isEmpty()
                    || minimumDepth == Integer.MAX_VALUE
                    || maximumDepth == Integer.MIN_VALUE) {
                return null;
            }

            return new TargetCandidate(
                    gate.xCoord,
                    gate.yCoord,
                    gate.zCoord,
                    bounds,
                    orientation,
                    cells,
                    minimumDepth,
                    maximumDepth
            );
        }

        private boolean containsCell(
                int horizontal,
                int y
        ) {
            return cells.contains(
                    new SilhouetteCell(
                            horizontal,
                            y
                    )
            );
        }

        private boolean containsPoint(
                double horizontal,
                double y
        ) {
            return containsPointExact(
                    horizontal,
                    y
            )
                    || containsPointExact(
                    horizontal + HOVER_TOLERANCE,
                    y
            )
                    || containsPointExact(
                    horizontal - HOVER_TOLERANCE,
                    y
            )
                    || containsPointExact(
                    horizontal,
                    y + HOVER_TOLERANCE
            )
                    || containsPointExact(
                    horizontal,
                    y - HOVER_TOLERANCE
            );
        }

        private boolean containsPointExact(
                double horizontal,
                double y
        ) {
            return containsCell(
                    floor(
                            horizontal
                    ),
                    floor(
                            y
                    )
            );
        }

        private double getRayIntersectionDistance(
                Vec3 start,
                Vec3 look
        ) {
            if (start == null
                    || look == null) {
                return -1.0D;
            }

            double centerDepth =
                    (minimumDepth
                            + maximumDepth)
                            * 0.5D;

            double planeDepth;

            double direction;

            if (orientation == GateOrientation.WIDTH_X) {
                planeDepth =
                        start.zCoord <= centerDepth
                                ? minimumDepth
                                : maximumDepth;

                direction =
                        look.zCoord;

            } else {
                planeDepth =
                        start.xCoord <= centerDepth
                                ? minimumDepth
                                : maximumDepth;

                direction =
                        look.xCoord;
            }

            if (Math.abs(direction) < 0.0000001D) {
                return -1.0D;
            }

            double distance;

            if (orientation == GateOrientation.WIDTH_X) {
                distance =
                        (planeDepth - start.zCoord)
                                / direction;

            } else {
                distance =
                        (planeDepth - start.xCoord)
                                / direction;
            }

            if (distance < 0.0D) {
                return -1.0D;
            }

            double hitY =
                    start.yCoord
                            + look.yCoord
                            * distance;

            double horizontal;

            if (orientation == GateOrientation.WIDTH_X) {
                horizontal =
                        start.xCoord
                                + look.xCoord
                                * distance;

            } else {
                horizontal =
                        start.zCoord
                                + look.zCoord
                                * distance;
            }

            return containsPoint(
                    horizontal,
                    hitY
            )
                    ? distance
                    : -1.0D;
        }

        private static int floor(
                double value
        ) {
            int integer =
                    (int)value;

            return value < integer
                    ? integer - 1
                    : integer;
        }
    }

    private static final class SilhouetteCell {

        private final int horizontal;
        private final int y;

        private SilhouetteCell(
                int horizontal,
                int y
        ) {
            this.horizontal =
                    horizontal;

            this.y =
                    y;
        }

        @Override
        public boolean equals(
                Object other
        ) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof SilhouetteCell)) {
                return false;
            }

            SilhouetteCell cell =
                    (SilhouetteCell)other;

            return horizontal == cell.horizontal
                    && y == cell.y;
        }

        @Override
        public int hashCode() {
            int result =
                    horizontal;

            result =
                    31 * result
                            + y;

            return result;
        }
    }
}
