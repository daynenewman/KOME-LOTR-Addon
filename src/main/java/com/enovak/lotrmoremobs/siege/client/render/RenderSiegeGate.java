package com.enovak.lotrmoremobs.siege.client.render;

import com.enovak.lotrmoremobs.siege.banner.SiegeGateBannerAttachmentData;
import com.enovak.lotrmoremobs.siege.gate.GateAnimation;
import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateHingeSide;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import lotr.client.model.LOTRModelBanner;
import lotr.client.model.LOTRModelBannerWall;
import lotr.client.render.entity.LOTRRenderBanner;
import lotr.client.render.tileentity.LOTRRenderDwarvenGlow;
import lotr.common.block.LOTRBlockGateDwarvenIthildin;
import lotr.common.item.LOTRItemBanner;
import lotr.common.tileentity.LOTRTileEntityDwarvenDoor;
import java.nio.DoubleBuffer;
import org.lwjgl.BufferUtils;

@SideOnly(Side.CLIENT)
public class RenderSiegeGate extends TileEntitySpecialRenderer
        implements IResourceManagerReloadListener {

    private static final int SPLIT_CLIP_PLANE =
            GL11.GL_CLIP_PLANE5;

    private final DoubleBuffer splitClipEquation =
            BufferUtils.createDoubleBuffer(4);

    private final LOTRModelBanner lotrBannerModel =
            new LOTRModelBanner();

    private final LOTRModelBannerWall lotrBannerWallModel =
            new LOTRModelBannerWall();
    private static final int[][] FACE_OFFSETS = {
            {0, -1, 0}, {0, 1, 0},
            {0, 0, -1}, {0, 0, 1},
            {-1, 0, 0}, {1, 0, 0}
    };
    private static final float[] FACE_SHADES = {
            0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F
    };

    private static final int LIGHTING_CACHE_CHECK_INTERVAL_TICKS =
            5;

    private static final int LIGHTING_SIGNATURE_PADDING =
            2;

    private static final float DAMAGE_VISUAL_START_FRACTION =
            0.035F;

    private static final double DAMAGE_OVERLAY_OFFSET =
            0.0025D;

    private static final double MOVING_SEAM_FACE_OFFSET =
            0.0015D;

    /*
     * End-face lighting replacement sits just above the native detached face,
     * but below seams and the damage overlay.
     */
    private static final double MOVING_END_LIGHT_FACE_OFFSET =
            0.00075D;

    /*
     * Detached gate geometry can become exactly coplanar with the stationary
     * frame/world when fully open. Pull the gate a tiny amount toward the
     * camera in depth-buffer space so the moving gate texture wins instead of
     * flickering with the block behind it.
     */
    private static final float DETACHED_DEPTH_BIAS_FACTOR =
            -2.0F;

    private static final float DETACHED_DEPTH_BIAS_UNITS =
            -8.0F;

    private static final double GATE_SEAM_FACE_OFFSET =
            0.0035D;

    private static final double GATE_OUTER_SEAM_WIDTH =
            0.040D;

    private static final double GATE_CENTER_SEAM_WIDTH =
            0.052D;

    private static final double GATE_SEAM_HIGHLIGHT_WIDTH =
            0.012D;

    private static final float GATE_SEAM_DARK =
            0.10F;

    private static final float GATE_SEAM_DARK_ALPHA =
            0.58F;

    private static final float GATE_SEAM_HIGHLIGHT =
            0.72F;

    private static final float GATE_SEAM_HIGHLIGHT_ALPHA =
            0.18F;

    private static final ResourceLocation[] DAMAGE_STAGE_TEXTURES =
            createDamageStageTextures();

    private final Map<TileEntitySiegeGate, GateRenderCache> caches =
            new WeakHashMap<TileEntitySiegeGate, GateRenderCache>();

    /*
     * Prepared once per siege-gate render. Every source block belonging to
     * the same captured LOTR Ithildin multi-block door uses one shared glow
     * brightness, matching the native door's base-TE behavior.
     */
    private TileEntitySiegeGate activeIthildinController;

    private Map<IthildinDoorKey, Float> activeIthildinBrightness =
            new HashMap<IthildinDoorKey, Float>();

    private final RenderBlocks renderBlocks = new RenderBlocks();

    public RenderSiegeGate() {
        IResourceManager resourceManager =
                Minecraft.getMinecraft().getResourceManager();
        if (resourceManager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager)resourceManager)
                    .registerReloadListener(this);
        }
    }

    @Override
    public void renderTileEntityAt(
            TileEntity tileEntity,
            double x,
            double y,
            double z,
            float partialTicks
    ) {
        if (!(tileEntity
                instanceof TileEntitySiegeGate)) {
            return;
        }

        TileEntitySiegeGate controller =
                (TileEntitySiegeGate)tileEntity;

        if (controller.getWorldObj() == null
                || !controller.isFinalized()) {
            return;
        }

        GateRenderCache cache =
                getOrBuildCache(
                        controller
                );

        if (cache == null) {
            return;
        }

        /*
         * Advance explicitly-approved detached visual state once per client
         * world tick. These TEs are not registered in the World.
         */
        cache.leftBlockAccess
                .updateVisualTileEntities();

        cache.rightBlockAccess
                .updateVisualTileEntities();

        activeIthildinController =
                controller;

        activeIthildinBrightness =
                buildIthildinBrightnessMap(
                        controller,
                        cache,
                        partialTicks
                );

        bindTexture(
                TextureMap.locationBlocksTexture
        );

        GL11.glPushMatrix();

        /*
         * RenderGlobal enables fixed-function item lighting before invoking
         * TESRs. Terrain chunks do not use that lighting: RenderBlocks has
         * already baked Minecraft's lightmap, face shade, and (when enabled)
         * ambient-occlusion values into their vertices. Leaving GL_LIGHTING
         * enabled here applies a second, TESR-specific directional shade to
         * the same source-block vertices and makes a closed gate visibly
         * different from the adjacent terrain block.
         *
         * Preserve the caller's state because glow renderers and later TESRs
         * own their own lighting policy.
         */
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_LIGHTING_BIT
        );

        GL11.glDisable(
                GL11.GL_LIGHTING
        );

        /*
         * RenderBlocks writes per-vertex AO/color values just like terrain,
         * but TESRs are not guaranteed to inherit the terrain renderer's
         * shade model. GL_FLAT collapses those four corner values into one
         * value for the whole quad, which is especially visible as the dark
         * full-block band along a gate's bottom row. Match normal terrain:
         * interpolate vertex lighting when Minecraft AO is enabled, and use
         * flat shading when AO is disabled. GL_LIGHTING_BIT above preserves
         * and restores the caller's shade model.
         */
        GL11.glShadeModel(
                Minecraft.getMinecraft()
                        .gameSettings
                        .ambientOcclusion != 0
                        ? GL11.GL_SMOOTH
                        : GL11.GL_FLAT
        );

        try {
            GL11.glTranslated(
                    x,
                    y,
                    z
            );

            float impactRecoil =
                    controller.getRenderImpactRecoil(
                            partialTicks
                    );

            float breachSag =
                    controller.getRenderBreachSag(
                            partialTicks
                    );

            GateOrientation impactOrientation =
                    controller.getGateOrientation();

            if (impactOrientation == GateOrientation.WIDTH_X) {
                GL11.glTranslated(
                        0.0D,
                        breachSag,
                        impactRecoil
                );

            } else if (impactOrientation == GateOrientation.WIDTH_Z) {
                GL11.glTranslated(
                        impactRecoil,
                        breachSag,
                        0.0D
                );

            } else {
                GL11.glTranslated(
                        0.0D,
                        breachSag,
                        0.0D
                );
            }

            GL11.glColor4f(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            if (controller
                    .hasCompleteHingeConfiguration()) {

                float openProgress =
                        controller.getRenderOpenProgress(
                                partialTicks
                        );

                float impactGateFlex =
                        controller.getRenderImpactGateFlex(
                                partialTicks
                        );

                openProgress =
                        Math.max(
                                openProgress,
                                impactGateFlex
                        );

                boolean detached =
                        openProgress > 0.001F;

                float leftAngle =
                        GateAnimation.getLeafAngleDegrees(
                                controller.getGateOrientation(),
                                controller.getOpeningDirection(),
                                controller.getLeftHinge(),
                                openProgress
                        );

                float rightAngle =
                        GateAnimation.getLeafAngleDegrees(
                                controller.getGateOrientation(),
                                controller.getOpeningDirection(),
                                controller.getRightHinge(),
                                openProgress
                        );

                boolean fullyOpen =
                        detached
                                && openProgress >= 0.999F;

                cache.leftBlockAccess.setDetached(
                        detached
                );

                cache.rightBlockAccess.setDetached(
                        detached
                );

                cache.leftBlockAccess.setDetachedAngleDegrees(
                        leftAngle
                );

                cache.rightBlockAccess.setDetachedAngleDegrees(
                        rightAngle
                );

                try {
                    if (!detached) {
                        renderLeaf(
                                cache.leftClosedOuterDisplayList,
                                controller.getLeftHinge(),
                                leftAngle,
                                controller
                        );

                        renderLeaf(
                                cache.rightClosedOuterDisplayList,
                                controller.getRightHinge(),
                                rightAngle,
                                controller
                        );

                    } else if (fullyOpen) {
                        /*
                         * Fully-open leaves can use the cached native geometry.
                         * That display list is baked with the full-open
                         * transformed environmental light/AO field and is
                         * invalidated by the expanded swing-envelope lighting
                         * signature.
                         */
                        renderLeafWithDetachedDepthBias(
                                cache.leftMovingOuterDisplayList,
                                controller.getLeftHinge(),
                                leftAngle,
                                controller
                        );

                        renderLeafWithDetachedDepthBias(
                                cache.rightMovingOuterDisplayList,
                                controller.getRightHinge(),
                                rightAngle,
                                controller
                        );

                    } else {
                        /*
                         * During an actual hinge animation the world-space
                         * position changes every frame. Render the native outer
                         * geometry directly so RenderBlocks can sample the
                         * CURRENT transformed environment instead of freezing
                         * lighting from either endpoint.
                         */
                        renderDynamicMovingLeafOuter(
                                controller,
                                GateLeaf.LEFT,
                                cache.leftBlockAccess,
                                controller.getLeftHinge(),
                                leftAngle
                        );

                        renderDynamicMovingLeafOuter(
                                controller,
                                GateLeaf.RIGHT,
                                cache.rightBlockAccess,
                                controller.getRightHinge(),
                                rightAngle
                        );
                    }

                    if (detached) {
                        /*
                         * SPLIT_CENTER cut faces are newly-exposed geometry.
                         * They cannot keep the closed-footprint lighting baked
                         * into the old cut display lists: once a leaf swings,
                         * those faces need to sample the environment at their
                         * current transformed position just like the rest of
                         * the moving gate.
                         */
                        renderDynamicMovingLeafCutFaces(
                                controller,
                                GateLeaf.LEFT,
                                cache.leftBlockAccess,
                                controller.getLeftHinge(),
                                leftAngle
                        );

                        renderDynamicMovingLeafCutFaces(
                                controller,
                                GateLeaf.RIGHT,
                                cache.rightBlockAccess,
                                controller.getRightHinge(),
                                rightAngle
                        );
                    }

                    renderLeafVisualEffects(
                            controller,
                            GateLeaf.LEFT,
                            cache.leftBlockAccess,
                            controller.getLeftHinge(),
                            leftAngle,
                            partialTicks
                    );

                    renderLeafVisualEffects(
                            controller,
                            GateLeaf.RIGHT,
                            cache.rightBlockAccess,
                            controller.getRightHinge(),
                            rightAngle,
                            partialTicks
                    );

                } finally {
                    cache.leftBlockAccess.setDetached(
                            false
                    );

                    cache.rightBlockAccess.setDetached(
                            false
                    );

                    cache.leftBlockAccess.setDetachedAngleDegrees(
                            0.0F
                    );

                    cache.rightBlockAccess.setDetachedAngleDegrees(
                            0.0F
                    );
                }

            } else {
                GL11.glCallList(
                        cache.leftClosedOuterDisplayList
                );

                GL11.glCallList(
                        cache.rightClosedOuterDisplayList
                );

                renderGateDamageOverlay(
                        controller,
                        GateLeaf.LEFT,
                        cache.leftBlockAccess
                );

                renderGateDamageOverlay(
                        controller,
                        GateLeaf.RIGHT,
                        cache.rightBlockAccess
                );

                renderIthildinVisuals(
                        controller,
                        GateLeaf.LEFT,
                        cache.leftBlockAccess,
                        partialTicks
                );

                renderIthildinVisuals(
                        controller,
                        GateLeaf.RIGHT,
                        cache.rightBlockAccess,
                        partialTicks
                );

                renderChestVisuals(
                        controller,
                        GateLeaf.LEFT,
                        cache.leftBlockAccess,
                        partialTicks
                );

                renderChestVisuals(
                        controller,
                        GateLeaf.RIGHT,
                        cache.rightBlockAccess,
                        partialTicks
                );

                renderBannerVisuals(
                        controller,
                        GateLeaf.LEFT,
                        cache.leftBlockAccess
                );

                renderBannerVisuals(
                        controller,
                        GateLeaf.RIGHT,
                        cache.rightBlockAccess
                );
            }

            GL11.glColor4f(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    public void release(TileEntitySiegeGate controller) {
        GateRenderCache cache = caches.remove(controller);
        if (cache != null) {
            cache.release();
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        releaseAll();
    }

    @Override
    public void func_147496_a(World world) {
        releaseAll();
    }

    private GateRenderCache getOrBuildCache(
            TileEntitySiegeGate controller
    ) {
        GateRenderCache cache =
                caches.get(
                        controller
                );

        int revision =
                controller.getRenderDataRevision();

        World world =
                controller.getWorldObj();

        long worldTick =
                world == null
                        ? 0L
                        : world.getTotalWorldTime();

        Integer currentLightingSignature =
                null;

        boolean currentOpenEnvelope =
                controller.getGateState()
                        != GateState.CLOSED;

        if (cache != null
                && cache.revision == revision) {

            boolean envelopeModeChanged =
                    cache.openEnvelopeSignature
                            != currentOpenEnvelope;

            boolean timeMovedBackward =
                    worldTick
                            < cache.lastLightingCheckTick;

            boolean lightingCheckDue =
                    envelopeModeChanged
                            || timeMovedBackward
                            || worldTick
                            - cache.lastLightingCheckTick
                            >= LIGHTING_CACHE_CHECK_INTERVAL_TICKS;

            if (!lightingCheckDue) {
                return cache;
            }

            currentLightingSignature =
                    Integer.valueOf(
                            calculateLightingSignature(
                                    controller
                            )
                    );

            cache.lastLightingCheckTick =
                    worldTick;

            if (cache.lightingSignature
                    == currentLightingSignature.intValue()) {

                return cache;
            }
        }

        if (cache != null) {
            cache.release();
        }

        if (currentLightingSignature == null) {
            currentLightingSignature =
                    Integer.valueOf(
                            calculateLightingSignature(
                                    controller
                            )
                    );
        }

        GateRenderBlockAccess leftBlockAccess =
                new GateRenderBlockAccess(
                        controller,
                        GateLeaf.LEFT
                );

        GateRenderBlockAccess rightBlockAccess =
                new GateRenderBlockAccess(
                        controller,
                        GateLeaf.RIGHT
                );

        /*
         * Lighting/envelope cache rebuilds do not represent a structural gate
         * change. Preserve approved visual-only TE state so Ithildin glow does
         * not restart from zero when the gate begins moving.
         */
        if (cache != null
                && cache.revision == revision) {

            leftBlockAccess.inheritVisualStateFrom(
                    cache.leftBlockAccess
            );

            rightBlockAccess.inheritVisualStateFrom(
                    cache.rightBlockAccess
            );
        }

        /*
         * Closed geometry sees normal neighboring world/gate blocks so native
         * RenderBlocks retains normal AO and face culling.
         */
        leftBlockAccess.setDetached(
                false
        );

        rightBlockAccess.setDetached(
                false
        );

        int leftClosedOuterDisplayList =
                buildLeafDisplayList(
                        controller,
                        GateLeaf.LEFT,
                        leftBlockAccess,
                        false
                );

        int rightClosedOuterDisplayList =
                buildLeafDisplayList(
                        controller,
                        GateLeaf.RIGHT,
                        rightBlockAccess,
                        false
                );

        /*
         * Moving geometry sees only blocks which move with its own leaf.
         * Stationary wall blocks and the opposite leaf therefore cannot suppress
         * faces which become exposed after rotation.
         */
        int leftMovingOuterDisplayList;
        int rightMovingOuterDisplayList;

        leftBlockAccess.setDetached(
                true
        );

        rightBlockAccess.setDetached(
                true
        );

        leftBlockAccess.setDetachedAngleDegrees(
                GateAnimation.getLeafAngleDegrees(
                        controller.getGateOrientation(),
                        controller.getOpeningDirection(),
                        controller.getLeftHinge(),
                        1.0F
                )
        );

        rightBlockAccess.setDetachedAngleDegrees(
                GateAnimation.getLeafAngleDegrees(
                        controller.getGateOrientation(),
                        controller.getOpeningDirection(),
                        controller.getRightHinge(),
                        1.0F
                )
        );

        try {
            leftMovingOuterDisplayList =
                    buildLeafDisplayList(
                            controller,
                            GateLeaf.LEFT,
                            leftBlockAccess,
                            false
                    );

            rightMovingOuterDisplayList =
                    buildLeafDisplayList(
                            controller,
                            GateLeaf.RIGHT,
                            rightBlockAccess,
                            false
                    );

        } finally {
            leftBlockAccess.setDetached(
                    false
            );

            rightBlockAccess.setDetached(
                    false
            );

            leftBlockAccess.setDetachedAngleDegrees(
                    0.0F
            );

            rightBlockAccess.setDetachedAngleDegrees(
                    0.0F
            );
        }

        int leftCutDisplayList =
                buildLeafDisplayList(
                        controller,
                        GateLeaf.LEFT,
                        leftBlockAccess,
                        true
                );

        int rightCutDisplayList =
                buildLeafDisplayList(
                        controller,
                        GateLeaf.RIGHT,
                        rightBlockAccess,
                        true
                );

        GateRenderCache rebuilt =
                new GateRenderCache(
                        revision,
                        currentLightingSignature.intValue(),
                        currentOpenEnvelope,
                        worldTick,
                        leftBlockAccess,
                        rightBlockAccess,
                        leftClosedOuterDisplayList,
                        rightClosedOuterDisplayList,
                        leftMovingOuterDisplayList,
                        rightMovingOuterDisplayList,
                        leftCutDisplayList,
                        rightCutDisplayList
                );

        caches.put(
                controller,
                rebuilt
        );

        return rebuilt;
    }

    private int calculateLightingSignature(
            TileEntitySiegeGate controller
    ) {
        World world =
                controller.getWorldObj();

        if (world == null) {
            return 0;
        }

        int minX =
                Integer.MAX_VALUE;

        int minY =
                Integer.MAX_VALUE;

        int minZ =
                Integer.MAX_VALUE;

        int maxX =
                Integer.MIN_VALUE;

        int maxY =
                Integer.MIN_VALUE;

        int maxZ =
                Integer.MIN_VALUE;

        boolean foundPart =
                false;

        /*
         * Keep the cheap closed-footprint signature while the gate is idle.
         * As soon as it enters any non-closed state, include the fully-open
         * footprint as well. Changing this mode deliberately changes the
         * signature, forcing a one-time cache rebuild at the start/end of a
         * hinge cycle without continuously scanning the whole swing envelope
         * for every closed gate in the world.
         */
        boolean includeOpenEnvelope =
                controller.getGateState()
                        != GateState.CLOSED;

        for (GatePartData part
                : controller.getGateParts()) {

            if (part == null
                    || !part.hasValidAbsolutePosition(
                    controller.xCoord,
                    controller.yCoord,
                    controller.zCoord
            )) {

                continue;
            }

            int partX =
                    part.getAbsoluteX(
                            controller.xCoord
                    );

            int partY =
                    part.getAbsoluteY(
                            controller.yCoord
                    );

            int partZ =
                    part.getAbsoluteZ(
                            controller.zCoord
                    );

            minX =
                    Math.min(
                            minX,
                            partX
                    );

            minY =
                    Math.min(
                            minY,
                            partY
                    );

            minZ =
                    Math.min(
                            minZ,
                            partZ
                    );

            maxX =
                    Math.max(
                            maxX,
                            partX
                    );

            maxY =
                    Math.max(
                            maxY,
                            partY
                    );

            maxZ =
                    Math.max(
                            maxZ,
                            partZ
                    );

            if (includeOpenEnvelope) {
                /*
                 * The fully-open display lists are cached too, so while the
                 * gate is non-closed the cache signature must include the
                 * space the leaves occupy after the hinge transform.
                 */
                GateLeaf[] lightingLeaves = {
                        GateLeaf.LEFT,
                        GateLeaf.RIGHT
                };

                for (GateLeaf lightingLeaf
                        : lightingLeaves) {

                    if (!part.getLeaf()
                            .contributesTo(
                                    lightingLeaf
                            )) {
                        continue;
                    }

                    int[] openPosition =
                            getLeafWorldPosition(
                                    controller,
                                    part,
                                    lightingLeaf,
                                    1.0F
                            );

                    minX =
                            Math.min(
                                    minX,
                                    openPosition[0]
                            );

                    minY =
                            Math.min(
                                    minY,
                                    openPosition[1]
                            );

                    minZ =
                            Math.min(
                                    minZ,
                                    openPosition[2]
                            );

                    maxX =
                            Math.max(
                                    maxX,
                                    openPosition[0]
                            );

                    maxY =
                            Math.max(
                                    maxY,
                                    openPosition[1]
                            );

                    maxZ =
                            Math.max(
                                    maxZ,
                                    openPosition[2]
                            );
                }
            }

            foundPart =
                    true;
        }

        if (!foundPart) {
            return 0;
        }

        minX -=
                LIGHTING_SIGNATURE_PADDING;

        minY =
                Math.max(
                        0,
                        minY - LIGHTING_SIGNATURE_PADDING
                );

        minZ -=
                LIGHTING_SIGNATURE_PADDING;

        maxX +=
                LIGHTING_SIGNATURE_PADDING;

        maxY =
                Math.min(
                        world.getHeight() - 1,
                        maxY + LIGHTING_SIGNATURE_PADDING
                );

        maxZ +=
                LIGHTING_SIGNATURE_PADDING;

        int signature =
                includeOpenEnvelope
                        ? 1231
                        : 1237;

        for (int y = minY;
             y <= maxY;
             ++y) {

            for (int z = minZ;
                 z <= maxZ;
                 ++z) {

                for (int x = minX;
                     x <= maxX;
                     ++x) {

                    if (!world.blockExists(
                            x,
                            y,
                            z
                    )) {

                        signature =
                                31 * signature;

                        continue;
                    }

                    int packedBrightness =
                            world.getLightBrightnessForSkyBlocks(
                                    x,
                                    y,
                                    z,
                                    0
                            );

                    Block block =
                            world.getBlock(
                                    x,
                                    y,
                                    z
                            );

                    int blockId =
                            block == null
                                    ? 0
                                    : Block.getIdFromBlock(
                                    block
                            );

                    int metadata =
                            world.getBlockMetadata(
                                    x,
                                    y,
                                    z
                            );

                    signature =
                            31 * signature
                                    + packedBrightness;

                    signature =
                            31 * signature
                                    + blockId;

                    signature =
                            31 * signature
                                    + metadata;
                }
            }
        }

        return signature;
    }

    private int[] getLeafWorldPosition(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            float progress
    ) {
        if (controller == null
                || part == null
                || leaf == null) {

            return new int[] {
                    0,
                    0,
                    0
            };
        }

        if (controller.getGateOrientation() == null
                || controller.getOpeningDirection() == null) {

            return new int[] {
                    part.getAbsoluteX(controller.xCoord),
                    part.getAbsoluteY(controller.yCoord),
                    part.getAbsoluteZ(controller.zCoord)
            };
        }

        GateHinge hinge =
                leaf == GateLeaf.LEFT
                        ? controller.getLeftHinge()
                        : controller.getRightHinge();

        if (hinge == null) {
            return new int[] {
                    part.getAbsoluteX(controller.xCoord),
                    part.getAbsoluteY(controller.yCoord),
                    part.getAbsoluteZ(controller.zCoord)
            };
        }

        float angleDegrees =
                GateAnimation.getLeafAngleDegrees(
                        controller.getGateOrientation(),
                        controller.getOpeningDirection(),
                        hinge,
                        progress
                );

        double angleRadians =
                Math.toRadians(
                        angleDegrees
                );

        double pivotX =
                hinge.getPivotRelativeX(
                        controller.getGateOrientation()
                );

        double pivotZ =
                hinge.getPivotRelativeZ(
                        controller.getGateOrientation()
                );

        double centerX =
                part.getRelativeX()
                        + 0.5D;

        double centerZ =
                part.getRelativeZ()
                        + 0.5D;

        double offsetX =
                centerX - pivotX;

        double offsetZ =
                centerZ - pivotZ;

        double cosine =
                Math.cos(
                        angleRadians
                );

        double sine =
                Math.sin(
                        angleRadians
                );

        double rotatedX =
                pivotX
                        + offsetX * cosine
                        + offsetZ * sine;

        double rotatedZ =
                pivotZ
                        - offsetX * sine
                        + offsetZ * cosine;

        return new int[] {
                (int)Math.floor(
                        controller.xCoord
                                + rotatedX
                ),
                part.getAbsoluteY(
                        controller.yCoord
                ),
                (int)Math.floor(
                        controller.zCoord
                                + rotatedZ
                )
        };
    }

    private int buildLeafDisplayList(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            boolean cutFaces
    ) {
        int displayList =
                GLAllocation.generateDisplayLists(
                        1
                );

        RenderBlocks nativeRenderBlocks =
                new RenderBlocks(
                        blockAccess
                );

        GL11.glNewList(
                displayList,
                GL11.GL_COMPILE
        );

        Tessellator tessellator =
                Tessellator.instance;

        tessellator.startDrawingQuads();

        try {
            for (GatePartData part
                    : controller
                    .getRenderableGatePartsForLeaf(
                            leaf
                    )) {

                renderPart(
                        controller,
                        part,
                        leaf,
                        blockAccess,
                        tessellator,
                        nativeRenderBlocks,
                        cutFaces
                );
            }

        } finally {
            resetRenderBounds();

            tessellator.draw();

            GL11.glEndList();
        }

        return displayList;
    }

    private void renderPart(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            Tessellator tessellator,
            RenderBlocks nativeRenderBlocks,
            boolean cutFaces
    ) {
        if (part.getLeaf().isSplitCenter()) {

            /*
             * For a finalized, hinged gate the visible outer geometry of a
             * SPLIT_CENTER source is rendered dynamically under a GPU clipping
             * plane. This preserves arbitrary native source geometry instead of
             * replacing it with a half cube.
             *
             * The secondary display list remains responsible only for the new
             * interior cut face exposed when the two halves swing apart.
             */
            if (cutFaces) {
                renderSplitPart(
                        controller,
                        part,
                        leaf,
                        tessellator,
                        true
                );

            } else if (!controller
                    .hasCompleteHingeConfiguration()) {

                /*
                 * Defensive fallback for an incomplete configuration.
                 */
                renderSplitPart(
                        controller,
                        part,
                        leaf,
                        tessellator,
                        false
                );
            }

            return;
        }

        if (cutFaces) {
            return;
        }

        renderNativePart(
                controller,
                part,
                leaf,
                blockAccess,
                tessellator,
                nativeRenderBlocks
        );
    }

    private void renderNativePart(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            Tessellator tessellator,
            RenderBlocks nativeRenderBlocks
    ) {
        Block sourceBlock =
                part.getSourceBlock();

        if (sourceBlock == null
                || sourceBlock == Blocks.air) {

            renderCuboidPart(
                    controller,
                    part,
                    leaf,
                    tessellator,
                    -1,
                    false
            );

            return;
        }

        /*
         * Vanilla chests are drawn by renderChestVisuals() through their native
         * TileEntitySpecialRenderer. RenderBlocks has no chest model and would
         * otherwise fall through to the generic cuboid/plank placeholder.
         * SPLIT_CENTER keeps the conservative old fallback because one chest
         * TESR cannot be safely clipped between two independently moving leaves.
         */
        if (isVanillaChestSource(sourceBlock)
                && !part.getLeaf().isSplitCenter()) {
            return;
        }

        boolean rendered =
                false;

        boolean freezeClosedConnections =
                shouldFreezeClosedConnectivity(
                        sourceBlock
                );

        /*
         * Closed source geometry is rendered in its real world coordinate
         * space and translated back into controller-local vertex coordinates.
         * This is important for vanilla/LOTR RenderBlocks: ambient occlusion,
         * mixed brightness, biome tinting, connected geometry, and any source
         * block logic now receive the same x/y/z values they would have received
         * from the normal terrain renderer.
         *
         * Moving leaves remain controller-relative because those vertices are
         * subsequently rotated around the gate hinge.
         */
        boolean renderAtWorldCoordinates =
                !blockAccess.isDetached();

        int renderX =
                renderAtWorldCoordinates
                        ? part.getAbsoluteX(controller.xCoord)
                        : part.getRelativeX();

        int renderY =
                renderAtWorldCoordinates
                        ? part.getAbsoluteY(controller.yCoord)
                        : part.getRelativeY();

        int renderZ =
                renderAtWorldCoordinates
                        ? part.getAbsoluteZ(controller.zCoord)
                        : part.getRelativeZ();

        /*
         * Fences, panes/iron bars, and walls derive their visible geometry from
         * neighboring blocks. The moving display list normally hides the world
         * and opposite leaf so newly-exposed cube faces can appear, but doing
         * that also makes connected blocks instantly lose the shape they had
         * while the gate was closed.
         *
         * Freeze only the neighbor topology for those connected render types.
         * Detached lighting is intentionally left active, so the moving gate can
         * still relight while its connected geometry stays visually unchanged.
         */
        if (freezeClosedConnections) {
            blockAccess.setClosedNeighborTopology(
                    true
            );
        }

        if (renderAtWorldCoordinates) {
            blockAccess.setAbsoluteCoordinates(
                    true
            );
            tessellator.setTranslation(
                    -controller.xCoord,
                    -controller.yCoord,
                    -controller.zCoord
            );
        }

        /*
         * Do NOT force RenderBlocks.renderAllFaces for a detached leaf. The
         * moving IBlockAccess now exposes same-leaf neighbors and hides the
         * opposite leaf, so native culling can remove interior faces normally.
         * Forcing all six faces is what lets hidden top/bottom/side quads show
         * up as thin dark lines at block boundaries after rotation.
         *
         * The existing moving-end replacement pass still supplies ordinary
         * opaque-cube leaf ends that need corrected lighting.
         */
        boolean hideControllerForDetachedSpecialGeometry =
                blockAccess.isDetached()
                        && !freezeClosedConnections
                        && usesNativeDetachedSpecialEndFaces(
                        sourceBlock
                );

        /*
         * Two source families still need one extra detached-only culling hint:
         *
         * - Ithildin dwarven doors are physically full blocks but deliberately
         *   report non-opaque, so vanilla Block.shouldSideBeRendered keeps the
         *   faces between adjacent door tiles. Those hidden internal faces show
         *   up edge-on only after the whole Siege Gate leaf rotates.
         *
         * - Stairs are also non-opaque. Their native renderer must keep seeing
         *   stair neighbors (for orientation/corner shape), but same-leaf block
         *   boundaries should be opaque for side-culling while detached.
         *
         * GateRenderBlockAccess supplies geometry-only opaque stand-ins for
         * matching same-leaf face-neighbors during this one native render call.
         * Normal cubes, ordinary LOTR gates, panes/fences/walls, metadata, and
         * TileEntity/glow state are not changed.
         */
        boolean cullDetachedIthildinInternalFaces =
                blockAccess.isDetached()
                        && sourceBlock
                        instanceof LOTRBlockGateDwarvenIthildin;

        boolean cullDetachedStairInternalFaces =
                blockAccess.isDetached()
                        && sourceBlock
                        instanceof BlockStairs;

        if (cullDetachedIthildinInternalFaces
                || cullDetachedStairInternalFaces) {
            blockAccess.beginDetachedInternalFaceCulling(
                    part,
                    sourceBlock,
                    cullDetachedStairInternalFaces
            );
        }

        if (hideControllerForDetachedSpecialGeometry) {
            blockAccess
                    .setHideControllerFromDetachedSpecialGeometry(
                            true
                    );
        }

        try {
            try {
                rendered =
                        nativeRenderBlocks
                                .renderBlockByRenderType(
                                        sourceBlock,
                                        renderX,
                                        renderY,
                                        renderZ
                                );

            } catch (RuntimeException ignored) {
                rendered =
                        false;
            }

        } finally {
            if (cullDetachedIthildinInternalFaces
                    || cullDetachedStairInternalFaces) {
                blockAccess.clearDetachedInternalFaceCulling();
            }

            if (hideControllerForDetachedSpecialGeometry) {
                blockAccess
                        .setHideControllerFromDetachedSpecialGeometry(
                                false
                        );
            }

            if (renderAtWorldCoordinates) {
                tessellator.setTranslation(
                        0.0D,
                        0.0D,
                        0.0D
                );
                blockAccess.setAbsoluteCoordinates(
                        false
                );
            }

            if (freezeClosedConnections) {
                blockAccess.setClosedNeighborTopology(
                        false
                );
            }
        }

        if (!rendered) {
            resetRenderBounds();

            renderCuboidPart(
                    controller,
                    part,
                    leaf,
                    tessellator,
                    -1,
                    false
            );
        }

    }

    private static boolean shouldFreezeClosedConnectivity(
            Block sourceBlock
    ) {
        if (sourceBlock == null) {
            return false;
        }

        if (sourceBlock instanceof BlockFence
                || sourceBlock instanceof BlockPane
                || sourceBlock instanceof BlockWall) {

            return true;
        }

        /*
         * Vanilla 1.7.10 render IDs: fence=11, pane=18, wall=32. Keep these
         * fallbacks for compatible modded connected blocks which reuse the
         * vanilla renderer without directly extending the vanilla class.
         */
        int renderType =
                sourceBlock.getRenderType();

        return renderType == 11
                || renderType == 18
                || renderType == 32;
    }

    private static boolean usesNativeDetachedSpecialEndFaces(
            Block sourceBlock
    ) {
        if (sourceBlock == null
                || sourceBlock == Blocks.air) {
            return false;
        }

        /*
         * These source types already have an authoritative native shape-aware
         * renderer. Replaying a second full-square end skin over them causes
         * one-block seams and can overwrite continuous textures.
         *
         * Keep ordinary opaque cubes on their existing, already-correct
         * replacement-lighting path. Connected/open meshes (fences, panes,
         * walls, etc.) also retain their existing compatibility handling.
         */
        return sourceBlock instanceof BlockStairs
                || sourceBlock.getRenderType() == 0
                && !sourceBlock.isOpaqueCube();
    }

    private static boolean isOpenMeshOrPlanarSplitSource(
            Block sourceBlock
    ) {
        if (sourceBlock == null
                || sourceBlock == Blocks.air) {
            return true;
        }

        if (sourceBlock instanceof BlockFence
                || sourceBlock instanceof BlockPane
                || sourceBlock instanceof BlockWall) {
            return true;
        }

        int renderType =
                sourceBlock.getRenderType();

        /*
         * These vanilla render types are open meshes / flat planes rather than
         * closed single-volume solids. A synthetic cut cap would invent solid
         * geometry. In particular, panes / iron bars stay entirely native.
         */
        return renderType == 1   // crossed squares
                || renderType == 2   // torch
                || renderType == 3   // fire
                || renderType == 5   // redstone wire
                || renderType == 6   // crops
                || renderType == 8   // ladder
                || renderType == 9   // rail
                || renderType == 11  // fence
                || renderType == 12  // lever
                || renderType == 18  // pane / iron bars
                || renderType == 19  // stem
                || renderType == 20  // vine
                || renderType == 21  // vanilla fence gate
                || renderType == 30; // tripwire-style planar renderer
    }

    private static double clampBlockBound(
            double value
    ) {
        if (Double.isNaN(value)
                || Double.isInfinite(value)) {
            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        value
                )
        );
    }

    private double[] getStateAwareSourceBounds(
            Block sourceBlock,
            GatePartData part,
            GateRenderBlockAccess blockAccess
    ) {
        if (sourceBlock == null
                || sourceBlock == Blocks.air
                || part == null
                || blockAccess == null
                || isOpenMeshOrPlanarSplitSource(
                sourceBlock
        )) {
            return null;
        }

        try {
            sourceBlock.setBlockBoundsBasedOnState(
                    blockAccess,
                    part.getRelativeX(),
                    part.getRelativeY(),
                    part.getRelativeZ()
            );

        } catch (RuntimeException ignored) {
            /*
             * Some third-party renderers assume a real World/TileEntity.
             * Their native clipped geometry remains the safe fallback.
             */
            return null;
        }

        double minX =
                clampBlockBound(
                        sourceBlock.getBlockBoundsMinX()
                );

        double minY =
                clampBlockBound(
                        sourceBlock.getBlockBoundsMinY()
                );

        double minZ =
                clampBlockBound(
                        sourceBlock.getBlockBoundsMinZ()
                );

        double maxX =
                clampBlockBound(
                        sourceBlock.getBlockBoundsMaxX()
                );

        double maxY =
                clampBlockBound(
                        sourceBlock.getBlockBoundsMaxY()
                );

        double maxZ =
                clampBlockBound(
                        sourceBlock.getBlockBoundsMaxZ()
                );

        if (maxX - minX <= 0.0001D
                || maxY - minY <= 0.0001D
                || maxZ - minZ <= 0.0001D) {
            return null;
        }

        return new double[] {
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
        };
    }

    private int getMovingExposedReferenceBrightness(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            Block sourceBlock,
            GatePartData part,
            GateRenderBlockAccess blockAccess,
            float angleDegrees
    ) {
        int referenceSide =
                getMovingExposedReferenceSide(
                        controller,
                        leaf,
                        angleDegrees
                );

        int[] offset =
                FACE_OFFSETS[referenceSide];

        int minimumBlockLight =
                sourceBlock.getLightValue(
                        blockAccess,
                        part.getRelativeX(),
                        part.getRelativeY(),
                        part.getRelativeZ()
                );

        /*
         * Do not borrow light from a fixed opposite face: depending on opening
         * direction, that face can itself be the broad side phasing into the
         * frame wall.
         *
         * Choose the broad face whose CURRENT rotated normal points toward the
         * open gateway / gate center, then sample the adjacent light coordinate
         * on that exposed side.
         */
        return blockAccess.getLightBrightnessForSkyBlocks(
                part.getRelativeX() + offset[0],
                part.getRelativeY() + offset[1],
                part.getRelativeZ() + offset[2],
                minimumBlockLight
        );
    }

    private int getMovingExposedReferenceSide(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            float angleDegrees
    ) {
        if (controller == null
                || leaf == null
                || controller.getGateOrientation() == null) {
            return 3;
        }

        GateHinge hinge =
                leaf == GateLeaf.LEFT
                        ? controller.getLeftHinge()
                        : controller.getRightHinge();

        if (hinge == null
                || hinge.getSide() == null) {
            return controller.getGateOrientation()
                    == GateOrientation.WIDTH_X
                    ? 3
                    : 5;
        }

        /*
         * At the first opening frame the current angle can still be exactly
         * zero. Use the eventual full-open angle only to decide WHICH broad
         * side will be exposed. The brightness lookup itself still uses the
         * current transformed GateRenderBlockAccess.
         */
        float directionAngle =
                Math.abs(angleDegrees) > 0.001F
                        ? angleDegrees
                        : GateAnimation.getLeafAngleDegrees(
                                controller.getGateOrientation(),
                                controller.getOpeningDirection(),
                                hinge,
                                1.0F
                        );

        double radians =
                Math.toRadians(
                        directionAngle
                );

        double cos =
                Math.cos(
                        radians
                );

        double sin =
                Math.sin(
                        radians
                );

        GateOrientation orientation =
                controller.getGateOrientation();

        int[] candidates =
                orientation == GateOrientation.WIDTH_X
                        ? new int[] {2, 3}
                        : new int[] {4, 5};

        /*
         * At full open, the broad side facing toward the gate center remains
         * exposed while the other broad side is the one which can phase into
         * the frame wall.
         */
        double centerSign =
                hinge.getSide() == GateHingeSide.MINIMUM
                        ? 1.0D
                        : -1.0D;

        double targetX =
                orientation == GateOrientation.WIDTH_X
                        ? centerSign
                        : 0.0D;

        double targetZ =
                orientation == GateOrientation.WIDTH_Z
                        ? centerSign
                        : 0.0D;

        int bestSide =
                candidates[0];

        double bestDot =
                -Double.MAX_VALUE;

        for (int candidate : candidates) {
            int[] normal =
                    FACE_OFFSETS[candidate];

            double localX =
                    normal[0];

            double localZ =
                    normal[2];

            /*
             * Same Y-axis rotation used by GL11.glRotatef(angle, 0, 1, 0).
             */
            double rotatedX =
                    localX * cos
                            + localZ * sin;

            double rotatedZ =
                    -localX * sin
                            + localZ * cos;

            double dot =
                    rotatedX * targetX
                            + rotatedZ * targetZ;

            if (dot > bestDot) {
                bestDot =
                        dot;

                bestSide =
                        candidate;
            }
        }

        return bestSide;
    }

    private static boolean isGateWidthEndSide(
            TileEntitySiegeGate controller,
            int side
    ) {
        if (controller == null
                || controller.getGateOrientation() == null) {
            return false;
        }

        if (controller.getGateOrientation()
                == GateOrientation.WIDTH_X) {
            return side == 4
                    || side == 5;
        }

        return side == 2
                || side == 3;
    }

    private boolean prepareStateAwareSplitCapBounds(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            Block sourceBlock
    ) {
        double[] bounds =
                getStateAwareSourceBounds(
                        sourceBlock,
                        part,
                        blockAccess
                );

        if (bounds == null) {
            return false;
        }

        boolean minimumHalf =
                isMinimumHalfForLeaf(
                        controller,
                        leaf
                );

        GateOrientation orientation =
                controller.getGateOrientation();

        if (orientation == GateOrientation.WIDTH_X) {
            /*
             * A generated face is needed only when the actual source volume
             * crosses the center plane. If the source bounds merely end at the
             * plane, its native renderer already owns that real outer face.
             */
            if (!(bounds[0] < 0.5D - 0.0001D
                    && bounds[3] > 0.5D + 0.0001D)) {
                return false;
            }

            renderBlocks.setRenderBounds(
                    minimumHalf
                            ? bounds[0]
                            : 0.5D,
                    bounds[1],
                    bounds[2],
                    minimumHalf
                            ? 0.5D
                            : bounds[3],
                    bounds[4],
                    bounds[5]
            );

            return true;
        }

        if (orientation == GateOrientation.WIDTH_Z) {
            if (!(bounds[2] < 0.5D - 0.0001D
                    && bounds[5] > 0.5D + 0.0001D)) {
                return false;
            }

            renderBlocks.setRenderBounds(
                    bounds[0],
                    bounds[1],
                    minimumHalf
                            ? bounds[2]
                            : 0.5D,
                    bounds[3],
                    bounds[4],
                    minimumHalf
                            ? 0.5D
                            : bounds[5]
            );

            return true;
        }

        return false;
    }

    private static boolean shouldRenderGeneratedSplitCutFace(
            Block sourceBlock
    ) {
        if (sourceBlock == null
                || sourceBlock == Blocks.air) {
            return false;
        }

        /*
         * renderAsNormalBlock + render type 0 is the conservative definition
         * of a source whose center cross-section really is a full rectangle.
         * Stairs have a dedicated shape-aware moving center cap below.
         * This intentionally excludes fences (11), panes/iron bars (18),
         * walls (32), and other custom/multipart shapes for which a rectangular
         * cut face would invent geometry.
         */
        return sourceBlock.getRenderType() == 0
                && sourceBlock.renderAsNormalBlock();
    }

    private void renderSplitPart(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            Tessellator tessellator,
            boolean cutFaces
    ) {
        /*
         * A synthetic center cap is only geometrically truthful for a normal
         * full cube. Stairs, panes/iron bars, fences, walls, slabs and custom
         * renderers have source geometry that is not a 1x1 rectangular
         * cross-section; drawing a full renderFace() there invents geometry.
         *
         * Their actual native model is already clipped at the split plane, so
         * let that native geometry define the exposed silhouette rather than
         * painting a fake rectangular cap over it.
         */
        if (cutFaces
                && !shouldRenderGeneratedSplitCutFace(
                part == null ? null : part.getSourceBlock()
        )) {
            return;
        }

        boolean minimumHalf = isMinimumHalfForLeaf(controller, leaf);
        GateOrientation orientation = controller.getGateOrientation();
        int cutSide;
        if (orientation == GateOrientation.WIDTH_X) {
            renderBlocks.setRenderBounds(
                    minimumHalf ? 0.0D : 0.5D,
                    0.0D,
                    0.0D,
                    minimumHalf ? 0.5D : 1.0D,
                    1.0D,
                    1.0D
            );
            cutSide = minimumHalf ? 5 : 4;
        } else {
            renderBlocks.setRenderBounds(
                    0.0D,
                    0.0D,
                    minimumHalf ? 0.0D : 0.5D,
                    1.0D,
                    1.0D,
                    minimumHalf ? 0.5D : 1.0D
            );
            cutSide = minimumHalf ? 3 : 2;
        }
        try {
            renderCuboidPart(
                    controller,
                    part,
                    leaf,
                    tessellator,
                    cutSide,
                    cutFaces
            );
        } finally {
            resetRenderBounds();
        }
    }

    private boolean isMinimumHalfForLeaf(
            TileEntitySiegeGate controller,
            GateLeaf leaf
    ) {
        boolean leftUsesMinimum = controller.getLeftHinge().getSide()
                == GateHingeSide.MINIMUM;
        return leaf == GateLeaf.LEFT
                ? leftUsesMinimum
                : !leftUsesMinimum;
    }

    private void renderCuboidPart(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            Tessellator tessellator,
            int cutSide,
            boolean cutFaces
    ) {
        Block sourceBlock = part.getSourceBlock();
        int absoluteX = part.getAbsoluteX(controller.xCoord);
        int absoluteY = part.getAbsoluteY(controller.yCoord);
        int absoluteZ = part.getAbsoluteZ(controller.zCoord);
        int brightness = sourceBlock.getMixedBrightnessForBlock(
                controller.getWorldObj(),
                absoluteX,
                absoluteY,
                absoluteZ
        );
        int tint = getSourceTint(
                sourceBlock,
                controller,
                absoluteX,
                absoluteY,
                absoluteZ
        );
        float red = (float)(tint >> 16 & 255) / 255.0F;
        float green = (float)(tint >> 8 & 255) / 255.0F;
        float blue = (float)(tint & 255) / 255.0F;

        for (int side = 0; side < FACE_OFFSETS.length; ++side) {
            if (cutFaces) {
                if (side != cutSide) {
                    continue;
                }
            } else if (side == cutSide) {
                continue;
            }
            int[] offset = FACE_OFFSETS[side];
            GatePartData neighbor = controller.getGatePartData(
                    part.getRelativeX() + offset[0],
                    part.getRelativeY() + offset[1],
                    part.getRelativeZ() + offset[2]
            );
            if (!cutFaces && neighbor != null
                    && neighbor.getLeaf().contributesTo(leaf)
                    && controller.isGatePartLoadedAndPresent(neighbor)) {
                continue;
            }

            tessellator.setBrightness(brightness);
            float shade = FACE_SHADES[side];
            tessellator.setColorOpaque_F(
                    red * shade,
                    green * shade,
                    blue * shade
            );
            setFaceNormal(tessellator, side);
            renderFace(
                    sourceBlock,
                    part,
                    side,
                    getSourceIcon(sourceBlock, part, side)
            );
        }
    }

    private void resetRenderBounds() {
        renderBlocks.setRenderBounds(
                0.0D,
                0.0D,
                0.0D,
                1.0D,
                1.0D,
                1.0D
        );
    }

    private int getSourceTint(
            Block sourceBlock,
            TileEntitySiegeGate controller,
            int x,
            int y,
            int z
    ) {
        try {
            return sourceBlock.colorMultiplier(
                    controller.getWorldObj(),
                    x,
                    y,
                    z
            );
        } catch (RuntimeException ignored) {
            return 0xFFFFFF;
        }
    }

    private static IIcon getSourceIcon(
            Block sourceBlock,
            GatePartData part,
            int side
    ) {
        try {
            IIcon icon = sourceBlock.getIcon(
                    side,
                    part.getSourceMetadata()
            );
            if (icon != null) {
                return icon;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the safe source-appearance fallback.
        }
        return Blocks.iron_block.getIcon(side, 0);
    }

    private static IIcon getSourceIcon(
            Block sourceBlock,
            GatePartData part,
            GateRenderBlockAccess blockAccess,
            int side
    ) {
        if (sourceBlock == null
                || part == null) {
            return Blocks.iron_block.getIcon(
                    side,
                    0
            );
        }

        if (blockAccess != null) {
            try {
                IIcon icon =
                        sourceBlock.getIcon(
                                blockAccess,
                                part.getRelativeX(),
                                part.getRelativeY(),
                                part.getRelativeZ(),
                                side
                        );

                if (icon != null) {
                    return icon;
                }

            } catch (RuntimeException ignored) {
                // Fall through to the metadata-only source icon.
            }
        }

        return getSourceIcon(
                sourceBlock,
                part,
                side
        );
    }

    private void renderFace(
            Block sourceBlock,
            GatePartData part,
            int side,
            IIcon icon
    ) {
        double x = part.getRelativeX();
        double y = part.getRelativeY();
        double z = part.getRelativeZ();
        if (side == 0) {
            renderBlocks.renderFaceYNeg(sourceBlock, x, y, z, icon);
        } else if (side == 1) {
            renderBlocks.renderFaceYPos(sourceBlock, x, y, z, icon);
        } else if (side == 2) {
            renderBlocks.renderFaceZNeg(sourceBlock, x, y, z, icon);
        } else if (side == 3) {
            renderBlocks.renderFaceZPos(sourceBlock, x, y, z, icon);
        } else if (side == 4) {
            renderBlocks.renderFaceXNeg(sourceBlock, x, y, z, icon);
        } else {
            renderBlocks.renderFaceXPos(sourceBlock, x, y, z, icon);
        }
    }

    private static void setFaceNormal(
            Tessellator tessellator,
            int side
    ) {
        int[] offset = FACE_OFFSETS[side];
        tessellator.setNormal(offset[0], offset[1], offset[2]);
    }

    private void renderDynamicMovingLeafOuter(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            GateHinge hinge,
            float angleDegrees
    ) {
        if (controller == null
                || leaf == null
                || blockAccess == null
                || hinge == null) {
            return;
        }

        double pivotX =
                hinge.getPivotRelativeX(
                        controller.getGateOrientation()
                );

        double pivotZ =
                hinge.getPivotRelativeZ(
                        controller.getGateOrientation()
                );

        GL11.glPushMatrix();

        GL11.glTranslated(
                pivotX,
                0.0D,
                pivotZ
        );

        GL11.glRotatef(
                angleDegrees,
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glTranslated(
                -pivotX,
                0.0D,
                -pivotZ
        );

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_POLYGON_BIT
        );

        try {
            GL11.glEnable(
                    GL11.GL_POLYGON_OFFSET_FILL
            );

            GL11.glPolygonOffset(
                    DETACHED_DEPTH_BIAS_FACTOR,
                    DETACHED_DEPTH_BIAS_UNITS
            );

            bindTexture(
                    TextureMap.locationBlocksTexture
            );

            resetRenderBounds();

            RenderBlocks nativeRenderBlocks =
                    new RenderBlocks(
                            blockAccess
                    );

            Tessellator tessellator =
                    Tessellator.instance;

            tessellator.startDrawingQuads();

            try {
                for (GatePartData part
                        : controller
                        .getRenderableGatePartsForLeaf(
                                leaf
                        )) {

                    renderPart(
                            controller,
                            part,
                            leaf,
                            blockAccess,
                            tessellator,
                            nativeRenderBlocks,
                            false
                    );
                }

            } finally {
                resetRenderBounds();
                tessellator.draw();
            }

        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private void renderDynamicMovingLeafCutFaces(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            GateHinge hinge,
            float angleDegrees
    ) {
        if (controller == null
                || leaf == null
                || blockAccess == null
                || hinge == null) {
            return;
        }

        double pivotX =
                hinge.getPivotRelativeX(
                        controller.getGateOrientation()
                );

        double pivotZ =
                hinge.getPivotRelativeZ(
                        controller.getGateOrientation()
                );

        GL11.glPushMatrix();

        GL11.glTranslated(
                pivotX,
                0.0D,
                pivotZ
        );

        GL11.glRotatef(
                angleDegrees,
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glTranslated(
                -pivotX,
                0.0D,
                -pivotZ
        );

        try {
            bindTexture(
                    TextureMap.locationBlocksTexture
            );

            Tessellator tessellator =
                    Tessellator.instance;

            tessellator.startDrawingQuads();

            try {
                for (GatePartData part
                        : controller
                        .getRenderableGatePartsForLeaf(
                                leaf
                        )) {

                    if (part == null
                            || !part.getLeaf()
                            .isSplitCenter()) {
                        continue;
                    }

                    renderMovingSplitCutFace(
                            controller,
                            part,
                            leaf,
                            blockAccess,
                            tessellator,
                            angleDegrees
                    );
                }

            } finally {
                resetRenderBounds();
                tessellator.draw();
            }

        } finally {
            GL11.glPopMatrix();
        }
    }

    private void renderMovingSplitCutFace(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            Tessellator tessellator,
            float angleDegrees
    ) {
        Block sourceBlock =
                part.getSourceBlock();

        if (sourceBlock == null
                || sourceBlock == Blocks.air) {
            return;
        }

        if (sourceBlock instanceof BlockStairs) {
            renderMovingStairSplitCutFace(
                    controller,
                    part,
                    leaf,
                    blockAccess,
                    tessellator,
                    angleDegrees,
                    sourceBlock
            );
            return;
        }

        /*
         * General cap policy:
         *
         * - ordinary cubes: 1x1 cap (same result as before)
         * - LOTR gates / Ithildin gates: thin state-aware cap
         * - slabs / doors / trapdoors / other single-bound special blocks:
         *   cap follows the source block's actual state bounds
         * - iron bars / panes / fences / walls / planar renderers:
         *   no synthetic fill; their clipped native mesh remains authoritative
         */
        if (!prepareStateAwareSplitCapBounds(
                controller,
                part,
                leaf,
                blockAccess,
                sourceBlock
        )) {
            return;
        }

        boolean minimumHalf =
                isMinimumHalfForLeaf(
                        controller,
                        leaf
                );

        GateOrientation orientation =
                controller.getGateOrientation();

        int cutSide;

        if (orientation == GateOrientation.WIDTH_X) {
            cutSide =
                    minimumHalf ? 5 : 4;

        } else if (orientation == GateOrientation.WIDTH_Z) {
            cutSide =
                    minimumHalf ? 3 : 2;

        } else {
            resetRenderBounds();
            return;
        }

        try {
            int brightness =
                    getMovingExposedReferenceBrightness(
                            controller,
                            leaf,
                            sourceBlock,
                            part,
                            blockAccess,
                            angleDegrees
                    );

            int absoluteX =
                    part.getAbsoluteX(
                            controller.xCoord
                    );

            int absoluteY =
                    part.getAbsoluteY(
                            controller.yCoord
                    );

            int absoluteZ =
                    part.getAbsoluteZ(
                            controller.zCoord
                    );

            int tint =
                    getSourceTint(
                            sourceBlock,
                            controller,
                            absoluteX,
                            absoluteY,
                            absoluteZ
                    );

            float shade =
                    getMovingFaceShade(
                            cutSide,
                            angleDegrees
                    );

            tessellator.setBrightness(
                    brightness
            );

            tessellator.setColorOpaque_F(
                    (float)(tint >> 16 & 255) / 255.0F * shade,
                    (float)(tint >> 8 & 255) / 255.0F * shade,
                    (float)(tint & 255) / 255.0F * shade
            );

            setFaceNormal(
                    tessellator,
                    cutSide
            );

            renderFace(
                    sourceBlock,
                    part,
                    cutSide,
                    getSourceIcon(
                            sourceBlock,
                            part,
                            blockAccess,
                            cutSide
                    )
            );

        } finally {
            resetRenderBounds();
        }
    }


    private void renderMovingStairSplitCutFace(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            Tessellator tessellator,
            float angleDegrees,
            Block sourceBlock
    ) {
        boolean minimumHalf =
                isMinimumHalfForLeaf(
                        controller,
                        leaf
                );

        GateOrientation orientation =
                controller.getGateOrientation();

        int cutSide;

        if (orientation == GateOrientation.WIDTH_X) {
            cutSide =
                    minimumHalf ? 5 : 4;

        } else if (orientation == GateOrientation.WIDTH_Z) {
            cutSide =
                    minimumHalf ? 3 : 2;

        } else {
            return;
        }

        int brightness =
                getMovingExposedReferenceBrightness(
                        controller,
                        leaf,
                        sourceBlock,
                        part,
                        blockAccess,
                        angleDegrees
                );

        int absoluteX =
                part.getAbsoluteX(
                        controller.xCoord
                );

        int absoluteY =
                part.getAbsoluteY(
                        controller.yCoord
                );

        int absoluteZ =
                part.getAbsoluteZ(
                        controller.zCoord
                );

        int tint =
                getSourceTint(
                        sourceBlock,
                        controller,
                        absoluteX,
                        absoluteY,
                        absoluteZ
                );

        float shade =
                getMovingFaceShade(
                        cutSide,
                        angleDegrees
                );

        tessellator.setBrightness(
                brightness
        );

        tessellator.setColorOpaque_F(
                (float)(tint >> 16 & 255) / 255.0F * shade,
                (float)(tint >> 8 & 255) / 255.0F * shade,
                (float)(tint & 255) / 255.0F * shade
        );

        setFaceNormal(
                tessellator,
                cutSide
        );

        IIcon icon =
                getSourceIcon(
                        sourceBlock,
                        part,
                        blockAccess,
                        cutSide
                );

        int metadata =
                part.getSourceMetadata();

        boolean upsideDown =
                (metadata & 4) != 0;

        int direction =
                metadata & 3;

        double baseMinY =
                upsideDown
                        ? 0.5D
                        : 0.0D;

        double baseMaxY =
                upsideDown
                        ? 1.0D
                        : 0.5D;

        double stepMinY =
                upsideDown
                        ? 0.0D
                        : 0.5D;

        double stepMaxY =
                upsideDown
                        ? 0.5D
                        : 1.0D;

        /*
         * Vanilla 1.7.10 stair metadata:
         * 0 = high half on +X
         * 1 = high half on -X
         * 2 = high half on +Z
         * 3 = high half on -Z
         * 4 = upside-down bit
         *
         * The center split is a literal cut through the source stair. Render
         * the base slab on both leaves, then add only the step half physically
         * present on that side of the cut. If the stair runs perpendicular to
         * the split axis, both leaves receive the same L-shaped end profile.
         */
        if (orientation == GateOrientation.WIDTH_X) {
            double minX =
                    minimumHalf
                            ? 0.0D
                            : 0.5D;

            double maxX =
                    minimumHalf
                            ? 0.5D
                            : 1.0D;

            renderBlocks.setRenderBounds(
                    minX,
                    baseMinY,
                    0.0D,
                    maxX,
                    baseMaxY,
                    1.0D
            );

            renderFace(
                    sourceBlock,
                    part,
                    cutSide,
                    icon
            );

            if (direction == 0
                    || direction == 1) {

                boolean stepOnThisLeaf =
                        direction == 0
                                ? !minimumHalf
                                : minimumHalf;

                if (stepOnThisLeaf) {
                    renderBlocks.setRenderBounds(
                            minX,
                            stepMinY,
                            0.0D,
                            maxX,
                            stepMaxY,
                            1.0D
                    );

                    renderFace(
                            sourceBlock,
                            part,
                            cutSide,
                            icon
                    );
                }

            } else {
                double minZ =
                        direction == 2
                                ? 0.5D
                                : 0.0D;

                double maxZ =
                        direction == 2
                                ? 1.0D
                                : 0.5D;

                renderBlocks.setRenderBounds(
                        minX,
                        stepMinY,
                        minZ,
                        maxX,
                        stepMaxY,
                        maxZ
                );

                renderFace(
                        sourceBlock,
                        part,
                        cutSide,
                        icon
                );
            }

        } else {
            double minZ =
                    minimumHalf
                            ? 0.0D
                            : 0.5D;

            double maxZ =
                    minimumHalf
                            ? 0.5D
                            : 1.0D;

            renderBlocks.setRenderBounds(
                    0.0D,
                    baseMinY,
                    minZ,
                    1.0D,
                    baseMaxY,
                    maxZ
            );

            renderFace(
                    sourceBlock,
                    part,
                    cutSide,
                    icon
            );

            if (direction == 2
                    || direction == 3) {

                boolean stepOnThisLeaf =
                        direction == 2
                                ? !minimumHalf
                                : minimumHalf;

                if (stepOnThisLeaf) {
                    renderBlocks.setRenderBounds(
                            0.0D,
                            stepMinY,
                            minZ,
                            1.0D,
                            stepMaxY,
                            maxZ
                    );

                    renderFace(
                            sourceBlock,
                            part,
                            cutSide,
                            icon
                    );
                }

            } else {
                double minX =
                        direction == 0
                                ? 0.5D
                                : 0.0D;

                double maxX =
                        direction == 0
                                ? 1.0D
                                : 0.5D;

                renderBlocks.setRenderBounds(
                        minX,
                        stepMinY,
                        minZ,
                        maxX,
                        stepMaxY,
                        maxZ
                );

                renderFace(
                        sourceBlock,
                        part,
                        cutSide,
                        icon
                );
            }
        }

        resetRenderBounds();
    }

    private static float getMovingFaceShade(
            int side,
            float angleDegrees
    ) {
        if (side == 0
                || side == 1) {
            return FACE_SHADES[side];
        }

        double radians =
                Math.toRadians(
                        angleDegrees
                );

        double cosine =
                Math.cos(
                        radians
                );

        double sine =
                Math.sin(
                        radians
                );

        int[] normal =
                FACE_OFFSETS[side];

        double worldX =
                normal[0] * cosine
                        + normal[2] * sine;

        double worldZ =
                -normal[0] * sine
                        + normal[2] * cosine;

        /*
         * Vanilla terrain uses 0.6 for X-facing sides and 0.8 for Z-facing
         * sides. A moving face can sit between those axes, so blend by the
         * squared world-normal contribution. This stays bounded between the
         * two vanilla values instead of making a 45-degree face artificially
         * brighter than either terrain direction.
         */
        return (float)(
                0.6D * worldX * worldX
                        + 0.8D * worldZ * worldZ
        );
    }

    private void renderLeafVisualEffects(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            GateHinge hinge,
            float angleDegrees,
            float partialTicks
    ) {
        if (hinge == null) {
            return;
        }

        double pivotX =
                hinge.getPivotRelativeX(
                        controller.getGateOrientation()
                );

        double pivotZ =
                hinge.getPivotRelativeZ(
                        controller.getGateOrientation()
                );

        GL11.glPushMatrix();

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_POLYGON_BIT
        );

        if (blockAccess.isDetached()) {
            GL11.glEnable(
                    GL11.GL_POLYGON_OFFSET_FILL
            );

            GL11.glPolygonOffset(
                    DETACHED_DEPTH_BIAS_FACTOR,
                    DETACHED_DEPTH_BIAS_UNITS
            );
        }

        GL11.glTranslated(
                pivotX,
                0.0D,
                pivotZ
        );

        GL11.glRotatef(
                angleDegrees,
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glTranslated(
                -pivotX,
                0.0D,
                -pivotZ
        );

        try {
            /*
             * SPLIT_CENTER geometry cannot be baked into the normal display list,
             * because each copy must be geometrically clipped after this leaf's
             * hinge transform has been applied.
             */
            renderSplitCenterNativeGeometry(
                    controller,
                    leaf,
                    blockAccess
            );

            boolean moving =
                    Math.abs(angleDegrees) > 0.001F;

            if (moving) {
                renderMovingLeafBoundaryFaces(
                        controller,
                        leaf,
                        blockAccess,
                        angleDegrees
                );
            }

            renderGateSeams(
                    controller,
                    leaf,
                    blockAccess,
                    moving
            );

            renderGateDamageOverlay(
                    controller,
                    leaf,
                    blockAccess
            );

            renderIthildinVisuals(
                    controller,
                    leaf,
                    blockAccess,
                    partialTicks
            );

            renderChestVisuals(
                    controller,
                    leaf,
                    blockAccess,
                    partialTicks
            );

            renderBannerVisuals(
                    controller,
                    leaf,
                    blockAccess
            );

        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private void renderMovingLeafBoundaryFaces(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            float angleDegrees
    ) {
        if (controller == null
                || leaf == null
                || blockAccess == null) {
            return;
        }

        bindTexture(
                TextureMap.locationBlocksTexture
        );

        resetRenderBounds();

        Tessellator tessellator =
                Tessellator.instance;

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_POLYGON_BIT
        );

        GL11.glEnable(
                GL11.GL_POLYGON_OFFSET_FILL
        );

        GL11.glPolygonOffset(
                DETACHED_DEPTH_BIAS_FACTOR,
                DETACHED_DEPTH_BIAS_UNITS
        );

        tessellator.startDrawingQuads();

        try {
            for (GatePartData part
                    : controller.getRenderableGatePartsForLeaf(
                    leaf
            )) {

                if (part == null
                        || part.getLeaf().isSplitCenter()) {
                    continue;
                }

                Block sourceBlock =
                        part.getSourceBlock();

                if (sourceBlock == null
                        || sourceBlock == Blocks.air) {
                    continue;
                }

                /*
                 * Chest geometry is supplied by its TESR. Never synthesize a
                 * moving boundary face from BlockChest's plank placeholder.
                 */
                if (isVanillaChestSource(sourceBlock)) {
                    continue;
                }

                boolean standardOpaqueCube =
                        sourceBlock.getRenderType() == 0
                                && sourceBlock.isOpaqueCube();

                /*
                 * Stairs and render-type-0 non-opaque/state-bounded blocks now
                 * trust their native detached geometry. The moving virtual
                 * neighborhood hides the opposite leaf (and, for these special
                 * renderers, the controller), so their own side culling can
                 * expose the real leaf end without a second offset face skin.
                 *
                 * Avoiding that replay is important: a synthetic end quad or
                 * stair sub-quad can itself become another edge-on interior
                 * surface and recreate the same seam we are trying to remove.
                 */
                if (!standardOpaqueCube
                        && usesNativeDetachedSpecialEndFaces(
                        sourceBlock
                )) {
                    continue;
                }

                for (int side = 0;
                     side < FACE_OFFSETS.length;
                     ++side) {

                    int[] offset =
                            FACE_OFFSETS[side];

                    GatePartData neighbor =
                            controller.getGatePartData(
                                    part.getRelativeX() + offset[0],
                                    part.getRelativeY() + offset[1],
                                    part.getRelativeZ() + offset[2]
                            );

                    boolean sameLeafNeighbor =
                            neighbor != null
                                    && neighbor.getLeaf()
                                    .contributesTo(
                                            leaf
                                    );

                    if (standardOpaqueCube) {
                        /*
                         * Native RenderBlocks owns ordinary cube geometry. This
                         * pass remains only the established lighting replacement
                         * for the two real width-axis leaf ends.
                         */
                        if (!isGateWidthEndSide(
                                controller,
                                side
                        )
                                || sameLeafNeighbor) {
                            continue;
                        }

                    } else {
                        /*
                         * Preserve the existing compatibility replay for
                         * non-standard/open renderers only when the source face
                         * was hidden by the opposite moving leaf. Open meshes
                         * such as iron bars therefore retain their currently-good
                         * connected/native appearance.
                         */
                        if (neighbor == null
                                || sameLeafNeighbor) {
                            continue;
                        }
                    }

                    int brightness =
                            standardOpaqueCube
                                    ? getMovingExposedReferenceBrightness(
                                    controller,
                                    leaf,
                                    sourceBlock,
                                    part,
                                    blockAccess,
                                    angleDegrees
                            )
                                    : blockAccess
                                    .getLightBrightnessForSkyBlocks(
                                            part.getRelativeX() + offset[0],
                                            part.getRelativeY() + offset[1],
                                            part.getRelativeZ() + offset[2],
                                            sourceBlock.getLightValue(
                                                    blockAccess,
                                                    part.getRelativeX(),
                                                    part.getRelativeY(),
                                                    part.getRelativeZ()
                                            )
                                    );

                    int absoluteX =
                            part.getAbsoluteX(
                                    controller.xCoord
                            );

                    int absoluteY =
                            part.getAbsoluteY(
                                    controller.yCoord
                            );

                    int absoluteZ =
                            part.getAbsoluteZ(
                                    controller.zCoord
                            );

                    int tint =
                            getSourceTint(
                                    sourceBlock,
                                    controller,
                                    absoluteX,
                                    absoluteY,
                                    absoluteZ
                            );

                    float shade =
                            getMovingFaceShade(
                                    side,
                                    angleDegrees
                            );

                    tessellator.setBrightness(
                            brightness
                    );

                    tessellator.setColorOpaque_F(
                            (float)(tint >> 16 & 255) / 255.0F * shade,
                            (float)(tint >> 8 & 255) / 255.0F * shade,
                            (float)(tint & 255) / 255.0F * shade
                    );

                    setFaceNormal(
                            tessellator,
                            side
                    );

                    IIcon endIcon =
                            getSourceIcon(
                                    sourceBlock,
                                    part,
                                    blockAccess,
                                    side
                            );

                    renderFaceWithOffset(
                            sourceBlock,
                            part,
                            side,
                            endIcon,
                            standardOpaqueCube
                                    ? MOVING_END_LIGHT_FACE_OFFSET
                                    : MOVING_SEAM_FACE_OFFSET
                    );
                }
            }

        } finally {
            tessellator.draw();
            resetRenderBounds();
            GL11.glPopAttrib();
        }
    }

    private void renderFaceWithOffset(
            Block sourceBlock,
            GatePartData part,
            int side,
            IIcon icon,
            double offset
    ) {
        double x =
                part.getRelativeX();

        double y =
                part.getRelativeY();

        double z =
                part.getRelativeZ();

        if (side == 0) {
            y -= offset;
        } else if (side == 1) {
            y += offset;
        } else if (side == 2) {
            z -= offset;
        } else if (side == 3) {
            z += offset;
        } else if (side == 4) {
            x -= offset;
        } else {
            x += offset;
        }

        if (side == 0) {
            renderBlocks.renderFaceYNeg(sourceBlock, x, y, z, icon);
        } else if (side == 1) {
            renderBlocks.renderFaceYPos(sourceBlock, x, y, z, icon);
        } else if (side == 2) {
            renderBlocks.renderFaceZNeg(sourceBlock, x, y, z, icon);
        } else if (side == 3) {
            renderBlocks.renderFaceZPos(sourceBlock, x, y, z, icon);
        } else if (side == 4) {
            renderBlocks.renderFaceXNeg(sourceBlock, x, y, z, icon);
        } else {
            renderBlocks.renderFaceXPos(sourceBlock, x, y, z, icon);
        }
    }

    private void renderGateSeams(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            boolean moving
    ) {
        if (controller == null
                || leaf == null
                || blockAccess == null
                || !controller.isGateBorderTextureEnabled()) {
            return;
        }

        GateOrientation orientation =
                controller.getGateOrientation();

        if (orientation != GateOrientation.WIDTH_X
                && orientation != GateOrientation.WIDTH_Z) {
            return;
        }

        GatePartData splitPart =
                getFirstSplitCenterPart(
                        controller,
                        leaf
                );

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_POLYGON_BIT
                        | GL11.GL_TEXTURE_BIT
                        | GL11.GL_TRANSFORM_BIT
        );

        try {
            GL11.glEnable(
                    GL11.GL_BLEND
            );

            GL11.glBlendFunc(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA
            );

            GL11.glDepthMask(
                    false
            );

            GL11.glDisable(
                    GL11.GL_TEXTURE_2D
            );

            GL11.glDisable(
                    GL11.GL_CULL_FACE
            );

            GL11.glEnable(
                    GL11.GL_POLYGON_OFFSET_FILL
            );

            GL11.glPolygonOffset(
                    -1.0F,
                    -12.0F
            );

            if (splitPart != null) {
                enableSplitClipPlane(
                        controller,
                        leaf,
                        splitPart
                );
            }

            Tessellator tessellator =
                    Tessellator.instance;

            tessellator.startDrawingQuads();

            try {
                for (GatePartData part
                        : controller.getRenderableGatePartsForLeaf(
                        leaf
                )) {

                    if (part == null
                            || part.getSourceBlock() == null
                            || part.getSourceBlock() == Blocks.air) {
                        continue;
                    }

                    if (orientation == GateOrientation.WIDTH_X) {
                        renderGateSeamsOnFace(
                                controller,
                                part,
                                leaf,
                                blockAccess,
                                tessellator,
                                2,
                                moving
                        );

                        renderGateSeamsOnFace(
                                controller,
                                part,
                                leaf,
                                blockAccess,
                                tessellator,
                                3,
                                moving
                        );

                    } else {
                        renderGateSeamsOnFace(
                                controller,
                                part,
                                leaf,
                                blockAccess,
                                tessellator,
                                4,
                                moving
                        );

                        renderGateSeamsOnFace(
                                controller,
                                part,
                                leaf,
                                blockAccess,
                                tessellator,
                                5,
                                moving
                        );
                    }
                }

            } finally {
                tessellator.draw();
            }

        } finally {
            GL11.glColor4f(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            GL11.glPopAttrib();
        }
    }

    private void renderGateSeamsOnFace(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            Tessellator tessellator,
            int faceSide,
            boolean moving
    ) {
        int[] faceOffset =
                FACE_OFFSETS[faceSide];

        GatePartData faceNeighbor =
                controller.getGatePartData(
                        part.getRelativeX() + faceOffset[0],
                        part.getRelativeY() + faceOffset[1],
                        part.getRelativeZ() + faceOffset[2]
                );

        /*
         * Seams belong only to an actually visible front/back face. This keeps
         * two-block-thick gates from drawing the same seam inside themselves.
         */
        if (faceNeighbor != null) {
            return;
        }

        Block sourceBlock =
                part.getSourceBlock();

        /*
         * The Ithildin door is one continuous multi-block artwork. Do not let
         * the addon's optional masonry seam overlay cut through the moon-runes.
         */
        if (sourceBlock
                instanceof LOTRBlockGateDwarvenIthildin) {
            return;
        }

        int brightness =
                sourceBlock.getMixedBrightnessForBlock(
                        blockAccess,
                        part.getRelativeX(),
                        part.getRelativeY(),
                        part.getRelativeZ()
                );

        tessellator.setBrightness(
                brightness
        );

        GateOrientation orientation =
                controller.getGateOrientation();

        int widthX =
                orientation == GateOrientation.WIDTH_X
                        ? 1
                        : 0;

        int widthZ =
                orientation == GateOrientation.WIDTH_Z
                        ? 1
                        : 0;

        GatePartData widthNegative =
                controller.getGatePartData(
                        part.getRelativeX() - widthX,
                        part.getRelativeY(),
                        part.getRelativeZ() - widthZ
                );

        GatePartData widthPositive =
                controller.getGatePartData(
                        part.getRelativeX() + widthX,
                        part.getRelativeY(),
                        part.getRelativeZ() + widthZ
                );

        GatePartData below =
                controller.getGatePartData(
                        part.getRelativeX(),
                        part.getRelativeY() - 1,
                        part.getRelativeZ()
                );

        GatePartData above =
                controller.getGatePartData(
                        part.getRelativeX(),
                        part.getRelativeY() + 1,
                        part.getRelativeZ()
                );

        double u =
                orientation == GateOrientation.WIDTH_X
                        ? part.getRelativeX()
                        : part.getRelativeZ();

        double v =
                part.getRelativeY();

        double faceCoordinate;

        if (faceSide == 2) {
            faceCoordinate =
                    part.getRelativeZ()
                            - GATE_SEAM_FACE_OFFSET;

        } else if (faceSide == 3) {
            faceCoordinate =
                    part.getRelativeZ()
                            + 1.0D
                            + GATE_SEAM_FACE_OFFSET;

        } else if (faceSide == 4) {
            faceCoordinate =
                    part.getRelativeX()
                            - GATE_SEAM_FACE_OFFSET;

        } else {
            faceCoordinate =
                    part.getRelativeX()
                            + 1.0D
                            + GATE_SEAM_FACE_OFFSET;
        }

        if (widthNegative == null) {
            renderVerticalSeamStrip(
                    tessellator,
                    orientation,
                    faceSide,
                    faceCoordinate,
                    u,
                    v,
                    false,
                    GATE_OUTER_SEAM_WIDTH
            );

        } else if (isOppositeLeafBoundary(
                widthNegative,
                leaf
        ) && shouldRenderCenterSeam(
                leaf,
                moving
        )) {
            renderVerticalSeamStrip(
                    tessellator,
                    orientation,
                    faceSide,
                    faceCoordinate,
                    u,
                    v,
                    false,
                    GATE_CENTER_SEAM_WIDTH
            );
        }

        if (widthPositive == null) {
            renderVerticalSeamStrip(
                    tessellator,
                    orientation,
                    faceSide,
                    faceCoordinate,
                    u + 1.0D,
                    v,
                    true,
                    GATE_OUTER_SEAM_WIDTH
            );

        } else if (isOppositeLeafBoundary(
                widthPositive,
                leaf
        ) && shouldRenderCenterSeam(
                leaf,
                moving
        )) {
            renderVerticalSeamStrip(
                    tessellator,
                    orientation,
                    faceSide,
                    faceCoordinate,
                    u + 1.0D,
                    v,
                    true,
                    GATE_CENTER_SEAM_WIDTH
            );
        }

        if (below == null) {
            renderHorizontalSeamStrip(
                    tessellator,
                    orientation,
                    faceSide,
                    faceCoordinate,
                    u,
                    v,
                    false,
                    GATE_OUTER_SEAM_WIDTH
            );
        }

        if (above == null) {
            renderHorizontalSeamStrip(
                    tessellator,
                    orientation,
                    faceSide,
                    faceCoordinate,
                    u,
                    v + 1.0D,
                    true,
                    GATE_OUTER_SEAM_WIDTH
            );
        }

        /*
         * SPLIT_CENTER represents a true two-leaf split through the middle of
         * one captured source block. Give that internal split the same seam as
         * an ordinary LEFT/RIGHT block boundary.
         */
        if (part.getLeaf().isSplitCenter()
                && shouldRenderCenterSeam(
                leaf,
                moving
        )) {

            renderCenteredVerticalSeamStrip(
                    tessellator,
                    orientation,
                    faceSide,
                    faceCoordinate,
                    u + 0.5D,
                    v,
                    GATE_CENTER_SEAM_WIDTH
            );
        }
    }

    private static boolean isOppositeLeafBoundary(
            GatePartData neighbor,
            GateLeaf leaf
    ) {
        return neighbor != null
                && leaf != null
                && !neighbor.getLeaf().contributesTo(
                leaf
        );
    }

    private static boolean shouldRenderCenterSeam(
            GateLeaf leaf,
            boolean moving
    ) {
        /*
         * Closed LEFT/RIGHT seams occupy the same coordinates, so render one
         * canonical copy. Once the leaves move, each physical leaf needs its
         * own seam edge so the line separates naturally with the doors.
         */
        return moving
                || leaf == GateLeaf.LEFT;
    }

    private void renderVerticalSeamStrip(
            Tessellator tessellator,
            GateOrientation orientation,
            int faceSide,
            double faceCoordinate,
            double edgeU,
            double minV,
            boolean insideNegativeU,
            double seamWidth
    ) {
        double darkMinU =
                insideNegativeU
                        ? edgeU - seamWidth
                        : edgeU;

        double darkMaxU =
                insideNegativeU
                        ? edgeU
                        : edgeU + seamWidth;

        addGateSeamQuad(
                tessellator,
                orientation,
                faceSide,
                faceCoordinate,
                darkMinU,
                minV,
                darkMaxU,
                minV + 1.0D,
                GATE_SEAM_DARK,
                GATE_SEAM_DARK_ALPHA
        );

        double highlightMinU =
                insideNegativeU
                        ? darkMinU - GATE_SEAM_HIGHLIGHT_WIDTH
                        : darkMaxU;

        double highlightMaxU =
                insideNegativeU
                        ? darkMinU
                        : darkMaxU + GATE_SEAM_HIGHLIGHT_WIDTH;

        addGateSeamQuad(
                tessellator,
                orientation,
                faceSide,
                faceCoordinate,
                highlightMinU,
                minV,
                highlightMaxU,
                minV + 1.0D,
                GATE_SEAM_HIGHLIGHT,
                GATE_SEAM_HIGHLIGHT_ALPHA
        );
    }

    private void renderHorizontalSeamStrip(
            Tessellator tessellator,
            GateOrientation orientation,
            int faceSide,
            double faceCoordinate,
            double minU,
            double edgeV,
            boolean insideNegativeV,
            double seamWidth
    ) {
        double darkMinV =
                insideNegativeV
                        ? edgeV - seamWidth
                        : edgeV;

        double darkMaxV =
                insideNegativeV
                        ? edgeV
                        : edgeV + seamWidth;

        addGateSeamQuad(
                tessellator,
                orientation,
                faceSide,
                faceCoordinate,
                minU,
                darkMinV,
                minU + 1.0D,
                darkMaxV,
                GATE_SEAM_DARK,
                GATE_SEAM_DARK_ALPHA
        );

        double highlightMinV =
                insideNegativeV
                        ? darkMinV - GATE_SEAM_HIGHLIGHT_WIDTH
                        : darkMaxV;

        double highlightMaxV =
                insideNegativeV
                        ? darkMinV
                        : darkMaxV + GATE_SEAM_HIGHLIGHT_WIDTH;

        addGateSeamQuad(
                tessellator,
                orientation,
                faceSide,
                faceCoordinate,
                minU,
                highlightMinV,
                minU + 1.0D,
                highlightMaxV,
                GATE_SEAM_HIGHLIGHT,
                GATE_SEAM_HIGHLIGHT_ALPHA
        );
    }

    private void renderCenteredVerticalSeamStrip(
            Tessellator tessellator,
            GateOrientation orientation,
            int faceSide,
            double faceCoordinate,
            double centerU,
            double minV,
            double seamWidth
    ) {
        double halfWidth =
                seamWidth * 0.5D;

        addGateSeamQuad(
                tessellator,
                orientation,
                faceSide,
                faceCoordinate,
                centerU - halfWidth,
                minV,
                centerU + halfWidth,
                minV + 1.0D,
                GATE_SEAM_DARK,
                GATE_SEAM_DARK_ALPHA
        );

        addGateSeamQuad(
                tessellator,
                orientation,
                faceSide,
                faceCoordinate,
                centerU - halfWidth
                        - GATE_SEAM_HIGHLIGHT_WIDTH,
                minV,
                centerU - halfWidth,
                minV + 1.0D,
                GATE_SEAM_HIGHLIGHT,
                GATE_SEAM_HIGHLIGHT_ALPHA
        );

        addGateSeamQuad(
                tessellator,
                orientation,
                faceSide,
                faceCoordinate,
                centerU + halfWidth,
                minV,
                centerU + halfWidth
                        + GATE_SEAM_HIGHLIGHT_WIDTH,
                minV + 1.0D,
                GATE_SEAM_HIGHLIGHT,
                GATE_SEAM_HIGHLIGHT_ALPHA
        );
    }

    private void addGateSeamQuad(
            Tessellator tessellator,
            GateOrientation orientation,
            int faceSide,
            double faceCoordinate,
            double minU,
            double minV,
            double maxU,
            double maxV,
            float shade,
            float alpha
    ) {
        tessellator.setColorRGBA_F(
                shade,
                shade,
                shade,
                alpha
        );

        if (orientation == GateOrientation.WIDTH_X) {
            tessellator.addVertex(
                    minU,
                    minV,
                    faceCoordinate
            );

            tessellator.addVertex(
                    maxU,
                    minV,
                    faceCoordinate
            );

            tessellator.addVertex(
                    maxU,
                    maxV,
                    faceCoordinate
            );

            tessellator.addVertex(
                    minU,
                    maxV,
                    faceCoordinate
            );

        } else {
            tessellator.addVertex(
                    faceCoordinate,
                    minV,
                    minU
            );

            tessellator.addVertex(
                    faceCoordinate,
                    minV,
                    maxU
            );

            tessellator.addVertex(
                    faceCoordinate,
                    maxV,
                    maxU
            );

            tessellator.addVertex(
                    faceCoordinate,
                    maxV,
                    minU
            );
        }
    }

    private void renderGateDamageOverlay(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess
    ) {
        if (controller == null
                || leaf == null
                || blockAccess == null
                || controller.getWorldObj() == null) {
            return;
        }

        int maxHealth =
                controller.getMaxHealth();

        if (maxHealth <= 0) {
            return;
        }

        float damageFraction =
                1.0F
                        - (float)controller.getCurrentHealth()
                        / (float)maxHealth;

        damageFraction =
                Math.max(
                        0.0F,
                        Math.min(
                                damageFraction,
                                1.0F
                        )
                );

        if (damageFraction
                < DAMAGE_VISUAL_START_FRACTION) {
            return;
        }

        /*
         * Damage now belongs to the whole structure rather than a handful of
         * randomly selected blocks. Every exposed gate face receives a very
         * faint crack layer; health loss increases the crack stage and opacity
         * gradually. The deterministic per-block stage variation prevents the
         * gate from looking like one repeated tiled breaking texture.
         */
        float damageAlpha =
                getDamageOverlayAlpha(
                        damageFraction
                );

        GatePartData splitPart =
                getFirstSplitCenterPart(
                        controller,
                        leaf
                );

        /*
         * The damage pass binds vanilla destroy-stage textures directly.
         * Preserve both texture bindings/state and the caller's active texture
         * unit so the crack texture cannot leak into later terrain/TESR draws.
         * The gate seam pass already isolates GL_TEXTURE_BIT for the same
         * reason; damage needs the same boundary because it changes bindings.
         */
        int previousActiveTexture =
                GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_POLYGON_BIT
                        | GL11.GL_TEXTURE_BIT
                        | GL11.GL_TRANSFORM_BIT
                        | GL11.GL_CURRENT_BIT
        );

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        try {
            GL11.glEnable(
                    GL11.GL_BLEND
            );

            GL11.glBlendFunc(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA
            );

            /*
             * The normal block render path leaves alpha testing enabled.
             * That is a poor fit for this deliberately faint blended overlay:
             * at oblique camera angles texture filtering can lower thin crack
             * texels below Minecraft's alpha-test cutoff, making parts of the
             * destroy texture appear to pop in and out as the view moves.
             *
             * Damage is already isolated behind glPushAttrib(GL_ENABLE_BIT),
             * so disable alpha testing only for this pass and let blending
             * preserve the filtered crack coverage. The caller's state is
             * restored exactly by glPopAttrib().
             */
            GL11.glDisable(
                    GL11.GL_ALPHA_TEST
            );

            GL11.glDepthMask(
                    false
            );

            GL11.glEnable(
                    GL11.GL_POLYGON_OFFSET_FILL
            );

            /*
             * Detached gate geometry is already pulled toward the camera by
             * DETACHED_DEPTH_BIAS_* in renderLeafVisualEffects(). A weaker
             * slope factor here lets the base leaf overtake the crack layer at
             * oblique angles, which makes portions of the overlay disappear
             * while an open/opening/closing leaf rotates relative to the view.
             *
             * Keep the verified closed-gate bias unchanged. Only detached
             * leaves get a stronger overlay bias so the crack layer always
             * remains in front of the detached base geometry.
             */
            if (blockAccess.isDetached()) {
                GL11.glPolygonOffset(
                        -3.0F,
                        -16.0F
                );
            } else {
                GL11.glPolygonOffset(
                        -1.0F,
                        -10.0F
                );
            }

            if (splitPart != null) {
                enableSplitClipPlane(
                        controller,
                        leaf,
                        splitPart
                );
            }

            for (int stage = 0;
                 stage < DAMAGE_STAGE_TEXTURES.length;
                 ++stage) {

                bindTexture(
                        DAMAGE_STAGE_TEXTURES[stage]
                );

                Tessellator tessellator =
                        Tessellator.instance;

                tessellator.startDrawingQuads();

                try {
                    for (GatePartData part
                            : controller
                            .getRenderableGatePartsForLeaf(
                                    leaf
                            )) {

                        if (part == null
                                || part.getSourceBlock() == null
                                || part.getSourceBlock() == Blocks.air
                                || part.getSourceBlock().getRenderType() != 0) {
                            continue;
                        }

                        if (getDamageStage(
                                controller,
                                part,
                                damageFraction
                        ) != stage) {
                            continue;
                        }

                        renderDamageFaces(
                                controller,
                                part,
                                blockAccess,
                                tessellator,
                                damageAlpha
                        );
                    }

                } finally {
                    tessellator.draw();
                }

                /*
                 * The center-cut face does not exist while the captured block
                 * is whole, so the normal front/back damage pass has no face to
                 * project onto there. Once detached, render the same destroy
                 * stage on that generated face too. Temporarily disable the
                 * split clip plane because the damage quad is deliberately
                 * nudged just beyond the cut surface to avoid z-fighting.
                 */
                if (splitPart != null
                        && blockAccess.isDetached()) {

                    GL11.glDisable(
                            SPLIT_CLIP_PLANE
                    );

                    try {
                        Tessellator cutTessellator =
                                Tessellator.instance;

                        cutTessellator.startDrawingQuads();

                        try {
                            for (GatePartData part
                                    : controller
                                    .getRenderableGatePartsForLeaf(
                                            leaf
                                    )) {

                                if (part == null
                                        || !part.getLeaf()
                                        .isSplitCenter()
                                        || !shouldRenderGeneratedSplitCutFace(
                                        part.getSourceBlock()
                                )) {
                                    continue;
                                }

                                if (getDamageStage(
                                        controller,
                                        part,
                                        damageFraction
                                ) != stage) {
                                    continue;
                                }

                                renderSplitCenterDamageCutFace(
                                        controller,
                                        part,
                                        leaf,
                                        blockAccess,
                                        cutTessellator,
                                        damageAlpha
                                );
                            }

                        } finally {
                            cutTessellator.draw();
                        }

                    } finally {
                        GL11.glEnable(
                                SPLIT_CLIP_PLANE
                        );
                    }
                }
            }

        } finally {
            /*
             * Restore the exact caller state rather than assuming the caller
             * had the block atlas bound. GL_TEXTURE_BIT restores bindings and
             * texture enables; the active unit itself is restored explicitly.
             */
            GL11.glPopAttrib();
            OpenGlHelper.setActiveTexture(
                    previousActiveTexture
            );
        }
    }

    private void renderSplitCenterDamageCutFace(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            Tessellator tessellator,
            float damageAlpha
    ) {
        Block sourceBlock =
                part.getSourceBlock();

        if (sourceBlock == null
                || sourceBlock == Blocks.air) {
            return;
        }

        boolean minimumHalf =
                isMinimumHalfForLeaf(
                        controller,
                        leaf
                );

        GateOrientation orientation =
                controller.getGateOrientation();

        int cutSide;

        if (orientation == GateOrientation.WIDTH_X) {
            cutSide =
                    minimumHalf ? 5 : 4;

        } else if (orientation == GateOrientation.WIDTH_Z) {
            cutSide =
                    minimumHalf ? 3 : 2;

        } else {
            return;
        }

        int[] offset =
                FACE_OFFSETS[cutSide];

        int brightness =
                blockAccess.getLightBrightnessForSkyBlocks(
                        part.getRelativeX() + offset[0],
                        part.getRelativeY() + offset[1],
                        part.getRelativeZ() + offset[2],
                        sourceBlock.getLightValue(
                                blockAccess,
                                part.getRelativeX(),
                                part.getRelativeY(),
                                part.getRelativeZ()
                        )
                );

        tessellator.setBrightness(
                brightness
        );

        tessellator.setColorRGBA_F(
                0.70F,
                0.70F,
                0.70F,
                damageAlpha
        );

        setFaceNormal(
                tessellator,
                cutSide
        );

        double minX =
                part.getRelativeX();

        double minY =
                part.getRelativeY();

        double minZ =
                part.getRelativeZ();

        double maxX =
                minX + 1.0D;

        double maxY =
                minY + 1.0D;

        double maxZ =
                minZ + 1.0D;

        if (cutSide == 5) {
            double x =
                    minX + 0.5D
                            + DAMAGE_OVERLAY_OFFSET;

            tessellator.addVertexWithUV(x, maxY, minZ, 0.0D, 0.0D);
            tessellator.addVertexWithUV(x, maxY, maxZ, 1.0D, 0.0D);
            tessellator.addVertexWithUV(x, minY, maxZ, 1.0D, 1.0D);
            tessellator.addVertexWithUV(x, minY, minZ, 0.0D, 1.0D);

        } else if (cutSide == 4) {
            double x =
                    minX + 0.5D
                            - DAMAGE_OVERLAY_OFFSET;

            tessellator.addVertexWithUV(x, maxY, maxZ, 0.0D, 0.0D);
            tessellator.addVertexWithUV(x, maxY, minZ, 1.0D, 0.0D);
            tessellator.addVertexWithUV(x, minY, minZ, 1.0D, 1.0D);
            tessellator.addVertexWithUV(x, minY, maxZ, 0.0D, 1.0D);

        } else if (cutSide == 3) {
            double z =
                    minZ + 0.5D
                            + DAMAGE_OVERLAY_OFFSET;

            tessellator.addVertexWithUV(maxX, maxY, z, 0.0D, 0.0D);
            tessellator.addVertexWithUV(minX, maxY, z, 1.0D, 0.0D);
            tessellator.addVertexWithUV(minX, minY, z, 1.0D, 1.0D);
            tessellator.addVertexWithUV(maxX, minY, z, 0.0D, 1.0D);

        } else if (cutSide == 2) {
            double z =
                    minZ + 0.5D
                            - DAMAGE_OVERLAY_OFFSET;

            tessellator.addVertexWithUV(minX, maxY, z, 0.0D, 0.0D);
            tessellator.addVertexWithUV(maxX, maxY, z, 1.0D, 0.0D);
            tessellator.addVertexWithUV(maxX, minY, z, 1.0D, 1.0D);
            tessellator.addVertexWithUV(minX, minY, z, 0.0D, 1.0D);
        }
    }

    private void renderDamageFaces(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateRenderBlockAccess blockAccess,
            Tessellator tessellator,
            float damageAlpha
    ) {
        GateOrientation orientation =
                controller.getGateOrientation();

        if (orientation == GateOrientation.WIDTH_X) {
            renderDamageFaceIfExposed(
                    controller,
                    part,
                    blockAccess,
                    tessellator,
                    damageAlpha,
                    2
            );

            renderDamageFaceIfExposed(
                    controller,
                    part,
                    blockAccess,
                    tessellator,
                    damageAlpha,
                    3
            );

        } else if (orientation == GateOrientation.WIDTH_Z) {
            renderDamageFaceIfExposed(
                    controller,
                    part,
                    blockAccess,
                    tessellator,
                    damageAlpha,
                    4
            );

            renderDamageFaceIfExposed(
                    controller,
                    part,
                    blockAccess,
                    tessellator,
                    damageAlpha,
                    5
            );
        }
    }

    private void renderDamageFaceIfExposed(
            TileEntitySiegeGate controller,
            GatePartData part,
            GateRenderBlockAccess blockAccess,
            Tessellator tessellator,
            float damageAlpha,
            int side
    ) {
        int[] offset =
                FACE_OFFSETS[side];

        GatePartData neighbor =
                controller.getGatePartData(
                        part.getRelativeX()
                                + offset[0],
                        part.getRelativeY()
                                + offset[1],
                        part.getRelativeZ()
                                + offset[2]
                );

        if (neighbor != null) {
            return;
        }

        int absoluteX =
                part.getAbsoluteX(
                        controller.xCoord
                );

        int absoluteY =
                part.getAbsoluteY(
                        controller.yCoord
                );

        int absoluteZ =
                part.getAbsoluteZ(
                        controller.zCoord
                );

        Block sourceBlock =
                part.getSourceBlock();

        int brightness =
                sourceBlock.getMixedBrightnessForBlock(
                        blockAccess,
                        part.getRelativeX(),
                        part.getRelativeY(),
                        part.getRelativeZ()
                );

        tessellator.setBrightness(
                brightness
        );

        tessellator.setColorRGBA_F(
                0.70F,
                0.70F,
                0.70F,
                damageAlpha
        );

        double minX =
                part.getRelativeX();

        double minY =
                part.getRelativeY();

        double minZ =
                part.getRelativeZ();

        double maxX =
                minX + 1.0D;

        double maxY =
                minY + 1.0D;

        double maxZ =
                minZ + 1.0D;

        if (side == 2) {
            double z =
                    minZ
                            - DAMAGE_OVERLAY_OFFSET;

            tessellator.addVertexWithUV(minX, maxY, z, 0.0D, 0.0D);
            tessellator.addVertexWithUV(maxX, maxY, z, 1.0D, 0.0D);
            tessellator.addVertexWithUV(maxX, minY, z, 1.0D, 1.0D);
            tessellator.addVertexWithUV(minX, minY, z, 0.0D, 1.0D);

        } else if (side == 3) {
            double z =
                    maxZ
                            + DAMAGE_OVERLAY_OFFSET;

            tessellator.addVertexWithUV(maxX, maxY, z, 0.0D, 0.0D);
            tessellator.addVertexWithUV(minX, maxY, z, 1.0D, 0.0D);
            tessellator.addVertexWithUV(minX, minY, z, 1.0D, 1.0D);
            tessellator.addVertexWithUV(maxX, minY, z, 0.0D, 1.0D);

        } else if (side == 4) {
            double x =
                    minX
                            - DAMAGE_OVERLAY_OFFSET;

            tessellator.addVertexWithUV(x, maxY, maxZ, 0.0D, 0.0D);
            tessellator.addVertexWithUV(x, maxY, minZ, 1.0D, 0.0D);
            tessellator.addVertexWithUV(x, minY, minZ, 1.0D, 1.0D);
            tessellator.addVertexWithUV(x, minY, maxZ, 0.0D, 1.0D);

        } else if (side == 5) {
            double x =
                    maxX
                            + DAMAGE_OVERLAY_OFFSET;

            tessellator.addVertexWithUV(x, maxY, minZ, 0.0D, 0.0D);
            tessellator.addVertexWithUV(x, maxY, maxZ, 1.0D, 0.0D);
            tessellator.addVertexWithUV(x, minY, maxZ, 1.0D, 1.0D);
            tessellator.addVertexWithUV(x, minY, minZ, 0.0D, 1.0D);
        }
    }

    private static int getDamageStage(
            TileEntitySiegeGate controller,
            GatePartData part,
            float damageFraction
    ) {
        float localVariation =
                getDamageHashUnit(
                        controller,
                        part,
                        0x119DE1F3
                )
                        - 0.5F;

        /*
         * Keep the damage readable across the whole gate without jumping
         * immediately to the nearly-black final destroy texture. The small
         * deterministic variation keeps neighboring blocks from looking tiled.
         */
        int stage =
                (int)Math.floor(
                        damageFraction * 8.5F
                                + localVariation * 1.5F
                                - 0.35F
                );

        return Math.max(
                0,
                Math.min(stage, 9)
        );
    }

    private static float getDamageOverlayAlpha(
            float damageFraction
    ) {
        return lerp(
                0.12F,
                0.55F,
                (damageFraction - DAMAGE_VISUAL_START_FRACTION)
                        / (1.0F - DAMAGE_VISUAL_START_FRACTION)
        );
    }

    private static float lerp(
            float minimum,
            float maximum,
            float progress
    ) {
        progress =
                Math.max(
                        0.0F,
                        Math.min(
                                progress,
                                1.0F
                        )
                );

        return minimum
                + (maximum - minimum)
                * progress;
    }

    private static float getDamageHashUnit(
            TileEntitySiegeGate controller,
            GatePartData part,
            int salt
    ) {
        int hash =
                salt;

        hash =
                31 * hash
                        + controller.xCoord;

        hash =
                31 * hash
                        + controller.yCoord;

        hash =
                31 * hash
                        + controller.zCoord;

        hash =
                31 * hash
                        + part.getRelativeX();

        hash =
                31 * hash
                        + part.getRelativeY();

        hash =
                31 * hash
                        + part.getRelativeZ();

        hash ^=
                hash >>> 16;

        hash *=
                0x7FEB352D;

        hash ^=
                hash >>> 15;

        hash *=
                0x846CA68B;

        hash ^=
                hash >>> 16;

        return (float)(hash & 0x7FFFFFFF)
                / (float)Integer.MAX_VALUE;
    }

    private static GatePartData getFirstSplitCenterPart(
            TileEntitySiegeGate controller,
            GateLeaf leaf
    ) {
        for (GatePartData part
                : controller
                .getRenderableGatePartsForLeaf(
                        leaf
                )) {

            if (part != null
                    && part.getLeaf()
                    .isSplitCenter()) {
                return part;
            }
        }

        return null;
    }

    private static ResourceLocation[] createDamageStageTextures() {
        ResourceLocation[] textures =
                new ResourceLocation[10];

        for (int stage = 0;
             stage < textures.length;
             ++stage) {

            textures[stage] =
                    new ResourceLocation(
                            "textures/blocks/destroy_stage_"
                                    + stage
                                    + ".png"
                    );
        }

        return textures;
    }

    private void renderSplitCenterNativeGeometry(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess
    ) {
        GatePartData firstSplitPart =
                null;

        for (GatePartData part
                : controller
                .getRenderableGatePartsForLeaf(
                        leaf
                )) {

            if (part.getLeaf()
                    .isSplitCenter()) {

                firstSplitPart =
                        part;

                break;
            }
        }

        if (firstSplitPart == null) {
            return;
        }

        RenderBlocks nativeRenderBlocks =
                new RenderBlocks(
                        blockAccess
                );

        Tessellator tessellator =
                Tessellator.instance;

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_POLYGON_BIT
                        | GL11.GL_TRANSFORM_BIT
        );

        try {
            if (blockAccess.isDetached()) {
                GL11.glEnable(
                        GL11.GL_POLYGON_OFFSET_FILL
                );

                GL11.glPolygonOffset(
                        DETACHED_DEPTH_BIAS_FACTOR,
                        DETACHED_DEPTH_BIAS_UNITS
                );
            }

            enableSplitClipPlane(
                    controller,
                    leaf,
                    firstSplitPart
            );

            tessellator.startDrawingQuads();

            try {
                for (GatePartData part
                        : controller
                        .getRenderableGatePartsForLeaf(
                                leaf
                        )) {

                    if (!part.getLeaf()
                            .isSplitCenter()) {
                        continue;
                    }

                    renderNativePart(
                            controller,
                            part,
                            leaf,
                            blockAccess,
                            tessellator,
                            nativeRenderBlocks
                    );
                }

            } finally {
                resetRenderBounds();

                tessellator.draw();
            }

        } finally {
            GL11.glPopAttrib();
        }
    }

    private void enableSplitClipPlane(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GatePartData splitPart
    ) {
        boolean minimumHalf =
                isMinimumHalfForLeaf(
                        controller,
                        leaf
                );

        GateOrientation orientation =
                controller.getGateOrientation();

        splitClipEquation.clear();

        if (orientation
                == GateOrientation.WIDTH_X) {

            double boundary =
                    splitPart.getRelativeX()
                            + 0.5D;

            if (minimumHalf) {
                /*
                 * Keep X <= boundary:
                 *
                 * -X + boundary >= 0
                 */
                splitClipEquation.put(-1.0D);
                splitClipEquation.put(0.0D);
                splitClipEquation.put(0.0D);
                splitClipEquation.put(boundary);

            } else {
                /*
                 * Keep X >= boundary:
                 *
                 * X - boundary >= 0
                 */
                splitClipEquation.put(1.0D);
                splitClipEquation.put(0.0D);
                splitClipEquation.put(0.0D);
                splitClipEquation.put(-boundary);
            }

        } else {
            double boundary =
                    splitPart.getRelativeZ()
                            + 0.5D;

            if (minimumHalf) {
                /*
                 * Keep Z <= boundary.
                 */
                splitClipEquation.put(0.0D);
                splitClipEquation.put(0.0D);
                splitClipEquation.put(-1.0D);
                splitClipEquation.put(boundary);

            } else {
                /*
                 * Keep Z >= boundary.
                 */
                splitClipEquation.put(0.0D);
                splitClipEquation.put(0.0D);
                splitClipEquation.put(1.0D);
                splitClipEquation.put(-boundary);
            }
        }

        splitClipEquation.flip();

        GL11.glClipPlane(
                SPLIT_CLIP_PLANE,
                splitClipEquation
        );

        GL11.glEnable(
                SPLIT_CLIP_PLANE
        );
    }

    private Map<IthildinDoorKey, Float> buildIthildinBrightnessMap(
            TileEntitySiegeGate controller,
            GateRenderCache cache,
            float partialTicks
    ) {
        Map<IthildinDoorKey, IthildinGlowSample> samples =
                new HashMap<IthildinDoorKey, IthildinGlowSample>();

        if (controller == null
                || cache == null) {
            return new HashMap<IthildinDoorKey, Float>();
        }

        for (GatePartData part
                : controller.getGateParts()) {

            if (part == null
                    || !(part.getSourceBlock()
                    instanceof LOTRBlockGateDwarvenIthildin)) {
                continue;
            }

            TileEntity sourceTileEntity =
                    null;

            if (part.getLeaf()
                    .contributesTo(
                            GateLeaf.LEFT
                    )) {

                sourceTileEntity =
                        cache.leftBlockAccess.getTileEntity(
                                part.getRelativeX(),
                                part.getRelativeY(),
                                part.getRelativeZ()
                        );
            }

            if (!(sourceTileEntity
                    instanceof LOTRTileEntityDwarvenDoor)
                    && part.getLeaf()
                    .contributesTo(
                            GateLeaf.RIGHT
                    )) {

                sourceTileEntity =
                        cache.rightBlockAccess.getTileEntity(
                                part.getRelativeX(),
                                part.getRelativeY(),
                                part.getRelativeZ()
                        );
            }

            if (!(sourceTileEntity
                    instanceof LOTRTileEntityDwarvenDoor)) {
                continue;
            }

            LOTRTileEntityDwarvenDoor door =
                    (LOTRTileEntityDwarvenDoor)
                            sourceTileEntity;

            float brightness;

            try {
                brightness =
                        door.getGlowBrightness(
                                partialTicks
                        );

            } catch (RuntimeException ignored) {
                continue;
            }

            IthildinDoorKey key =
                    IthildinDoorKey.from(
                            controller,
                            part
                    );

            boolean basePart =
                    key.matchesAbsolutePart(
                            controller,
                            part
                    );

            IthildinGlowSample existing =
                    samples.get(
                            key
                    );

            /*
             * Native LOTR uses the original multi-block door base as the
             * shared glow source. Prefer that exact captured block. If unusual
             * captured data lacks it, use the brightest matching slice so one
             * shadowed clone cannot stripe the whole artwork.
             */
            if (existing == null
                    || basePart
                    && !existing.basePart
                    || !existing.basePart
                    && brightness > existing.brightness) {

                samples.put(
                        key,
                        new IthildinGlowSample(
                                brightness,
                                basePart
                        )
                );
            }
        }

        Map<IthildinDoorKey, Float> result =
                new HashMap<IthildinDoorKey, Float>();

        for (Map.Entry<IthildinDoorKey, IthildinGlowSample> entry
                : samples.entrySet()) {

            result.put(
                    entry.getKey(),
                    Float.valueOf(
                            entry.getValue().brightness
                    )
            );
        }

        return result;
    }

    private float getSharedIthildinBrightness(
            TileEntitySiegeGate controller,
            GatePartData part,
            LOTRTileEntityDwarvenDoor door,
            float partialTicks
    ) {
        if (controller == activeIthildinController
                && activeIthildinBrightness != null) {

            Float shared =
                    activeIthildinBrightness.get(
                            IthildinDoorKey.from(
                                    controller,
                                    part
                            )
                    );

            if (shared != null) {
                return shared.floatValue();
            }
        }

        return door.getGlowBrightness(
                partialTicks
        );
    }

    private static boolean isVanillaChestSource(
            Block sourceBlock
    ) {
        return sourceBlock == Blocks.chest
                || sourceBlock == Blocks.trapped_chest;
    }

    /**
     * Draws inert LOTR banner attachments with LOTR's own 36.15 models and
     * textures. This runs inside the same leaf hinge matrix as the moving gate
     * geometry, but never constructs or spawns a LOTR banner entity, so banner
     * protection/whitelist gameplay remains completely absent while attached.
     */
    private void renderBannerVisuals(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess
    ) {
        if (controller == null
                || leaf == null
                || blockAccess == null) {
            return;
        }

        java.util.List<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot>
                attachments = controller.getBannerRenderAttachments();
        if (attachments.isEmpty()) {
            return;
        }

        for (SiegeGateBannerAttachmentData.RenderAttachmentSnapshot attachment
                : attachments) {
            if (attachment == null || attachment.getLeaf() != leaf) {
                continue;
            }

            LOTRItemBanner.BannerType bannerType =
                    LOTRItemBanner.BannerType.forID(
                            attachment.getBannerTypeId()
                    );
            if (bannerType == null) {
                continue;
            }

            int packedBrightness =
                    blockAccess.getLightBrightnessForSkyBlocks(
                            attachment.getRelativeSupportX(),
                            attachment.getRelativeSupportY(),
                            attachment.getRelativeSupportZ(),
                            0
                    );

            float previousBrightnessX =
                    OpenGlHelper.lastBrightnessX;
            float previousBrightnessY =
                    OpenGlHelper.lastBrightnessY;

            GL11.glPushMatrix();
            GL11.glPushAttrib(
                    GL11.GL_ENABLE_BIT
                            | GL11.GL_LIGHTING_BIT
                            | GL11.GL_COLOR_BUFFER_BIT
            );

            try {
                /*
                 * RenderGlobal normally enters entity rendering with standard
                 * item lighting active. The surrounding gate TESR disables it
                 * for RenderBlocks parity, so restore it only for this native
                 * entity-model visual and let glPopAttrib return to gate state.
                 */
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                OpenGlHelper.setLightmapTextureCoords(
                        OpenGlHelper.lightmapTexUnit,
                        packedBrightness & 65535,
                        packedBrightness >> 16
                );

                if (attachment.getKind()
                        == SiegeGateBannerAttachmentData.RenderKind.STANDING) {
                    renderStandingBannerVisual(attachment, bannerType);
                } else if (attachment.getKind()
                        == SiegeGateBannerAttachmentData.RenderKind.WALL) {
                    renderWallBannerVisual(attachment, bannerType);
                }
            } catch (RuntimeException ignored) {
                /*
                 * A visual-only native LOTR model must never be able to crash
                 * the Siege Gate TESR or compromise gate persistence.
                 */
            } finally {
                OpenGlHelper.setLightmapTextureCoords(
                        OpenGlHelper.lightmapTexUnit,
                        previousBrightnessX,
                        previousBrightnessY
                );

                GL11.glPopAttrib();
                GL11.glPopMatrix();

                bindTexture(TextureMap.locationBlocksTexture);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private void renderStandingBannerVisual(
            SiegeGateBannerAttachmentData.RenderAttachmentSnapshot attachment,
            LOTRItemBanner.BannerType bannerType
    ) {
        GL11.glTranslated(
                attachment.getRelativeEntityX(),
                attachment.getRelativeEntityY() + 1.5D,
                attachment.getRelativeEntityZ()
        );
        GL11.glScalef(-1.0F, -1.0F, 1.0F);
        GL11.glRotatef(
                180.0F - attachment.getRotationYaw(),
                0.0F,
                1.0F,
                0.0F
        );
        GL11.glTranslatef(0.0F, 0.01F, 0.0F);

        bindTexture(LOTRRenderBanner.getStandTexture(bannerType));
        lotrBannerModel.renderStand(0.0625F);
        lotrBannerModel.renderPost(0.0625F);

        bindTexture(LOTRRenderBanner.getBannerTexture(bannerType));
        lotrBannerModel.renderBanner(0.0625F);
    }

    private void renderWallBannerVisual(
            SiegeGateBannerAttachmentData.RenderAttachmentSnapshot attachment,
            LOTRItemBanner.BannerType bannerType
    ) {
        GL11.glTranslated(
                attachment.getRelativeEntityX(),
                attachment.getRelativeEntityY(),
                attachment.getRelativeEntityZ()
        );
        GL11.glScalef(-1.0F, -1.0F, 1.0F);
        GL11.glRotatef(
                attachment.getRotationYaw(),
                0.0F,
                1.0F,
                0.0F
        );

        bindTexture(LOTRRenderBanner.getStandTexture(bannerType));
        lotrBannerWallModel.renderPost(0.0625F);

        bindTexture(LOTRRenderBanner.getBannerTexture(bannerType));
        lotrBannerWallModel.renderBanner(0.0625F);
    }

    private void renderChestVisuals(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            float partialTicks
    ) {
        if (controller == null
                || leaf == null
                || blockAccess == null) {
            return;
        }

        for (GatePartData part
                : controller.getRenderableGatePartsForLeaf(leaf)) {

            if (part == null
                    || part.getLeaf().isSplitCenter()) {
                continue;
            }

            Block sourceBlock =
                    part.getSourceBlock();

            if (!isVanillaChestSource(sourceBlock)) {
                continue;
            }

            TileEntity sourceTileEntity =
                    blockAccess.getTileEntity(
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ()
                    );

            if (!(sourceTileEntity instanceof TileEntityChest)) {
                continue;
            }

            double renderX = part.getRelativeX();
            double renderY = part.getRelativeY();
            double renderZ = part.getRelativeZ();

            int packedBrightness =
                    blockAccess.getLightBrightnessForSkyBlocks(
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ(),
                            0
                    );

            float previousBrightnessX =
                    OpenGlHelper.lastBrightnessX;

            float previousBrightnessY =
                    OpenGlHelper.lastBrightnessY;

            GL11.glPushMatrix();
            GL11.glPushAttrib(
                    GL11.GL_ENABLE_BIT
                            | GL11.GL_LIGHTING_BIT
                            | GL11.GL_COLOR_BUFFER_BIT
                            | GL11.GL_POLYGON_BIT
            );

            try {
                /*
                 * Detached leaf effects use polygon offset to keep overlay
                 * geometry from z-fighting. A native chest TESR is real model
                 * geometry, not an overlay; inheriting that offset can expose
                 * a dark hairline where the lid and body meet while the leaf
                 * is moving. Render the chest with normal TESR depth state and
                 * restore the enclosing leaf state afterwards.
                 */
                GL11.glDisable(
                        GL11.GL_POLYGON_OFFSET_FILL
                );

                GL11.glEnable(
                        GL11.GL_LIGHTING
                );

                GL11.glColor4f(
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F
                );

                OpenGlHelper.setLightmapTextureCoords(
                        OpenGlHelper.lightmapTexUnit,
                        packedBrightness & 65535,
                        packedBrightness >> 16
                );

                TileEntityRendererDispatcher.instance
                        .renderTileEntityAt(
                                sourceTileEntity,
                                renderX,
                                renderY,
                                renderZ,
                                partialTicks
                        );

            } catch (RuntimeException ignored) {
                /*
                 * Source visuals must never be able to crash the gate TESR.
                 */
            } finally {
                OpenGlHelper.setLightmapTextureCoords(
                        OpenGlHelper.lightmapTexUnit,
                        previousBrightnessX,
                        previousBrightnessY
                );

                GL11.glPopAttrib();
                GL11.glPopMatrix();

                bindTexture(
                        TextureMap.locationBlocksTexture
                );

                GL11.glColor4f(
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F
                );
            }
        }
    }

    private void renderIthildinVisuals(
            TileEntitySiegeGate controller,
            GateLeaf leaf,
            GateRenderBlockAccess blockAccess,
            float partialTicks
    ) {
        if (controller == null
                || blockAccess == null) {
            return;
        }

        RenderBlocks sourceRenderBlocks =
                new RenderBlocks(
                        blockAccess
                );

        for (GatePartData part
                : controller
                .getRenderableGatePartsForLeaf(
                        leaf
                )) {

            Block sourceBlock =
                    part.getSourceBlock();

            if (!(sourceBlock
                    instanceof LOTRBlockGateDwarvenIthildin)) {
                continue;
            }

            TileEntity sourceTileEntity =
                    blockAccess.getTileEntity(
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ()
                    );

            if (!(sourceTileEntity
                    instanceof LOTRTileEntityDwarvenDoor)) {
                continue;
            }

            LOTRTileEntityDwarvenDoor door =
                    (LOTRTileEntityDwarvenDoor)
                            sourceTileEntity;

            float brightness;

            try {
                brightness =
                        getSharedIthildinBrightness(
                                controller,
                                part,
                                door,
                                partialTicks
                        );

            } catch (RuntimeException ignored) {
                continue;
            }

            if (brightness <= 0.001F) {
                continue;
            }

            float previousAlpha =
                    LOTRRenderDwarvenGlow
                            .setupGlow(
                                    brightness
                            );

            boolean clipped =
                    part.getLeaf()
                            .isSplitCenter()
                            && controller
                            .hasCompleteHingeConfiguration();

            if (clipped) {
                GL11.glPushAttrib(
                        GL11.GL_ENABLE_BIT
                                | GL11.GL_TRANSFORM_BIT
                );
            }

            try {
                if (clipped) {
                    enableSplitClipPlane(
                            controller,
                            leaf,
                            part
                    );
                }

                bindTexture(
                        TextureMap.locationBlocksTexture
                );

                /*
                 * Keep every Ithildin atlas slice in ONE shared leaf coordinate
                 * system. The native LOTR TESR normally draws around 0,0,0 and
                 * relies on an independent matrix translation for each block.
                 * Repeating that translation after the entire door has been put
                 * under a rotating gate-leaf transform produces tiny transformed
                 * coordinate differences at block boundaries. Those differences
                 * are the dark grid/slits visible only in the rendered state.
                 *
                 * We preserve LOTR's state bounds, per-side glow icon, 0.01 face
                 * offset, blend setup, and glow timing, but emit the face at the
                 * exact integer controller-relative block coordinate instead.
                 */
                renderIthildinGlowSliceShared(
                        (LOTRBlockGateDwarvenIthildin)sourceBlock,
                        sourceRenderBlocks,
                        blockAccess,
                        part
                );

            } catch (RuntimeException ignored) {
                /* Keep the rest of the gate render-safe if a source visual fails. */

            } finally {
                if (clipped) {
                    GL11.glPopAttrib();
                }

                LOTRRenderDwarvenGlow
                        .endGlow(
                                previousAlpha
                        );
            }
        }
    }


    private void renderIthildinGlowSliceShared(
            LOTRBlockGateDwarvenIthildin sourceBlock,
            RenderBlocks sourceRenderBlocks,
            GateRenderBlockAccess blockAccess,
            GatePartData part
    ) {
        int x = part.getRelativeX();
        int y = part.getRelativeY();
        int z = part.getRelativeZ();

        sourceBlock.setBlockBoundsBasedOnState(
                blockAccess,
                x,
                y,
                z
        );
        sourceRenderBlocks.setRenderBoundsFromBlock(
                sourceBlock
        );

        final double offset = 0.01D;
        Tessellator tessellator = Tessellator.instance;

        for (int side = 0; side < FACE_OFFSETS.length; ++side) {
            IIcon icon =
                    sourceBlock.getGlowIcon(
                            blockAccess,
                            x,
                            y,
                            z,
                            side
                    );

            if (icon == null) {
                continue;
            }

            tessellator.startDrawingQuads();
            try {
                if (side == 0) {
                    sourceRenderBlocks.renderFaceYNeg(
                            sourceBlock, x, y - offset, z, icon
                    );
                } else if (side == 1) {
                    sourceRenderBlocks.renderFaceYPos(
                            sourceBlock, x, y + offset, z, icon
                    );
                } else if (side == 2) {
                    sourceRenderBlocks.renderFaceZNeg(
                            sourceBlock, x, y, z - offset, icon
                    );
                } else if (side == 3) {
                    sourceRenderBlocks.renderFaceZPos(
                            sourceBlock, x, y, z + offset, icon
                    );
                } else if (side == 4) {
                    sourceRenderBlocks.renderFaceXNeg(
                            sourceBlock, x - offset, y, z, icon
                    );
                } else {
                    sourceRenderBlocks.renderFaceXPos(
                            sourceBlock, x + offset, y, z, icon
                    );
                }
            } finally {
                tessellator.draw();
                sourceRenderBlocks.flipTexture = false;
            }
        }

        sourceRenderBlocks.setRenderBounds(
                0.0D, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D
        );
    }

    private static void renderLeafWithDetachedDepthBias(
            int displayList,
            GateHinge hinge,
            float angleDegrees,
            TileEntitySiegeGate controller
    ) {
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_POLYGON_BIT
        );

        try {
            GL11.glEnable(
                    GL11.GL_POLYGON_OFFSET_FILL
            );

            GL11.glPolygonOffset(
                    DETACHED_DEPTH_BIAS_FACTOR,
                    DETACHED_DEPTH_BIAS_UNITS
            );

            renderLeaf(
                    displayList,
                    hinge,
                    angleDegrees,
                    controller
            );

        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void renderLeaf(
            int displayList,
            GateHinge hinge,
            float angleDegrees,
            TileEntitySiegeGate controller
    ) {
        double pivotX = hinge.getPivotRelativeX(
                controller.getGateOrientation()
        );
        double pivotZ = hinge.getPivotRelativeZ(
                controller.getGateOrientation()
        );
        GL11.glPushMatrix();
        GL11.glTranslated(pivotX, 0.0D, pivotZ);
        GL11.glRotatef(angleDegrees, 0.0F, 1.0F, 0.0F);
        GL11.glTranslated(-pivotX, 0.0D, -pivotZ);
        GL11.glCallList(displayList);
        GL11.glPopMatrix();
    }

    private void releaseAll() {
        for (GateRenderCache cache : caches.values()) {
            cache.release();
        }
        caches.clear();
    }

    private static final class IthildinGlowSample {

        private final float brightness;
        private final boolean basePart;

        private IthildinGlowSample(
                float brightness,
                boolean basePart
        ) {
            this.brightness =
                    brightness;

            this.basePart =
                    basePart;
        }
    }

    private static final class IthildinDoorKey {

        private final int x;
        private final int y;
        private final int z;

        private IthildinDoorKey(
                int x,
                int y,
                int z
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static IthildinDoorKey from(
                TileEntitySiegeGate controller,
                GatePartData part
        ) {
            NBTTagCompound snapshot =
                    part == null
                            ? null
                            : part.getSourceTileEntityNbt();

            if (snapshot != null
                    && snapshot.hasKey(
                    "DoorBaseX"
            )
                    && snapshot.hasKey(
                    "DoorBaseY"
            )
                    && snapshot.hasKey(
                    "DoorBaseZ"
            )) {

                return new IthildinDoorKey(
                        snapshot.getInteger(
                                "DoorBaseX"
                        ),
                        snapshot.getInteger(
                                "DoorBaseY"
                        ),
                        snapshot.getInteger(
                                "DoorBaseZ"
                        )
                );
            }

            if (controller != null
                    && part != null) {

                return new IthildinDoorKey(
                        part.getAbsoluteX(
                                controller.xCoord
                        ),
                        part.getAbsoluteY(
                                controller.yCoord
                        ),
                        part.getAbsoluteZ(
                                controller.zCoord
                        )
                );
            }

            return new IthildinDoorKey(
                    0,
                    0,
                    0
            );
        }

        private boolean matchesAbsolutePart(
                TileEntitySiegeGate controller,
                GatePartData part
        ) {
            return controller != null
                    && part != null
                    && x == part.getAbsoluteX(
                    controller.xCoord
            )
                    && y == part.getAbsoluteY(
                    controller.yCoord
            )
                    && z == part.getAbsoluteZ(
                    controller.zCoord
            );
        }

        @Override
        public boolean equals(
                Object object
        ) {
            if (this == object) {
                return true;
            }

            if (!(object
                    instanceof IthildinDoorKey)) {
                return false;
            }

            IthildinDoorKey other =
                    (IthildinDoorKey)object;

            return x == other.x
                    && y == other.y
                    && z == other.z;
        }

        @Override
        public int hashCode() {
            int result =
                    x;

            result =
                    31 * result
                            + y;

            result =
                    31 * result
                            + z;

            return result;
        }
    }

    private static final class GateRenderCache {

        private final int revision;

        private final int lightingSignature;

        private final boolean openEnvelopeSignature;

        private long lastLightingCheckTick;

        private final GateRenderBlockAccess leftBlockAccess;
        private final GateRenderBlockAccess rightBlockAccess;

        private final int leftClosedOuterDisplayList;
        private final int rightClosedOuterDisplayList;

        private final int leftMovingOuterDisplayList;
        private final int rightMovingOuterDisplayList;

        private final int leftCutDisplayList;
        private final int rightCutDisplayList;

        private GateRenderCache(
                int revision,
                int lightingSignature,
                boolean openEnvelopeSignature,
                long lastLightingCheckTick,
                GateRenderBlockAccess leftBlockAccess,
                GateRenderBlockAccess rightBlockAccess,
                int leftClosedOuterDisplayList,
                int rightClosedOuterDisplayList,
                int leftMovingOuterDisplayList,
                int rightMovingOuterDisplayList,
                int leftCutDisplayList,
                int rightCutDisplayList
        ) {
            this.revision =
                    revision;

            this.lightingSignature =
                    lightingSignature;

            this.openEnvelopeSignature =
                    openEnvelopeSignature;

            this.lastLightingCheckTick =
                    lastLightingCheckTick;

            this.leftBlockAccess =
                    leftBlockAccess;

            this.rightBlockAccess =
                    rightBlockAccess;

            this.leftClosedOuterDisplayList =
                    leftClosedOuterDisplayList;

            this.rightClosedOuterDisplayList =
                    rightClosedOuterDisplayList;

            this.leftMovingOuterDisplayList =
                    leftMovingOuterDisplayList;

            this.rightMovingOuterDisplayList =
                    rightMovingOuterDisplayList;

            this.leftCutDisplayList =
                    leftCutDisplayList;

            this.rightCutDisplayList =
                    rightCutDisplayList;
        }

        private void release() {
            GLAllocation.deleteDisplayLists(
                    leftClosedOuterDisplayList
            );

            GLAllocation.deleteDisplayLists(
                    rightClosedOuterDisplayList
            );

            GLAllocation.deleteDisplayLists(
                    leftMovingOuterDisplayList
            );

            GLAllocation.deleteDisplayLists(
                    rightMovingOuterDisplayList
            );

            GLAllocation.deleteDisplayLists(
                    leftCutDisplayList
            );

            GLAllocation.deleteDisplayLists(
                    rightCutDisplayList
            );
        }
    }
}
