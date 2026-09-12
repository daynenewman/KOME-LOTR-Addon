package com.enovak.lotrmoremobs.siege.client.render;

import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import lotr.common.block.LOTRBlockGateDwarvenIthildin;
import lotr.common.tileentity.LOTRTileEntityDwarvenDoor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import net.minecraftforge.common.util.ForgeDirection;

import com.enovak.lotrmoremobs.siege.gate.GateAnimation;
import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import net.minecraft.util.MathHelper;

/**
 * Client-only virtual block view used while rendering a moving siege-gate leaf.
 *
 * The real world contains Siege Gate Part blocks after finalization, but native
 * renderers see the stored source blocks, metadata, optional render-only
 * TileEntity snapshots, and the surrounding real-world neighborhood through
 * this IBlockAccess.
 *
 * Render-only TileEntities are never inserted into the real World and are
 * never part of Minecraft's normal TileEntity update list.
 */
public final class GateRenderBlockAccess
        implements IBlockAccess {

    private final GateLeaf leaf;

    private boolean detached;

    /*
     * Closed native source blocks are rendered using their real world
     * coordinates so vanilla/LOTR RenderBlocks sees exactly the same
     * coordinate space as ordinary terrain. Moving leaves continue to use
     * controller-relative coordinates because their geometry is transformed
     * around the hinge.
     */
    private boolean absoluteCoordinates;

    /*
     * Current Y-axis leaf rotation used by the moving render view.
     *
     * Closed geometry still renders in exact world coordinates. Detached
     * geometry stays in controller-relative coordinates for its hinge
     * transform, while lighting/AO queries are projected into the real world
     * using this angle.
     */
    private float detachedAngleDegrees;

    /*
     * When true, moving connected blocks (fences, panes/bars, walls) can
     * still inspect the exact closed-gate neighborhood that determined
     * their original connected shape. Lighting remains detached/moving;
     * only neighbor topology is frozen.
     */
    private boolean closedNeighborTopology;

    /*
     * Some detached special/state-bounded source renderers still perform their
     * own side-culling against IBlockAccess even though they are moving away
     * from the stationary controller. Ordinary opaque cubes already use the
     * detached same-leaf neighborhood and do not need this special masking.
     *
     * This flag is therefore enabled only while rendering selected special
     * source blocks (stairs and render-type-0 non-opaque blocks such as LOTR
     * gates/Ithildin gates). It makes the controller cell virtual air for
     * geometry/metadata queries without changing normal cube or connected
     * fence/pane/wall behavior.
     */
    private boolean hideControllerFromDetachedSpecialGeometry;

    /*
     * Detached special renderers can leave faces between adjacent non-opaque
     * source blocks in the native mesh. Those faces are harmless while the
     * structure is axis-aligned, but become visible edge-on as dark one-pixel
     * seams after the complete gate leaf rotates.
     *
     * This temporary context is enabled only while RenderSiegeGate renders one
     * Ithildin block or one stair. Neighbor lookups for matching same-leaf
     * source blocks then receive an opaque geometry-only stand-in. Metadata,
     * TileEntities, lighting, the current source block, opposite-leaf masking,
     * and ordinary world lookups are untouched.
     */
    private boolean detachedInternalFaceCullActive;
    private boolean detachedInternalFaceCullStairs;
    private int detachedInternalFaceCullX;
    private int detachedInternalFaceCullY;
    private int detachedInternalFaceCullZ;
    private Block detachedInternalFaceCullSource;

    /*
     * Keep stair identity for vanilla/LOTR stair corner/shape tests while making
     * a same-leaf neighbor opaque to Block.shouldSideBeRendered(). This object
     * is never placed in the world and is never rendered as the current block.
     */
    private static final Block DETACHED_OPAQUE_STAIR_NEIGHBOR =
            new BlockStairs(Blocks.planks, 0) {
                @Override
                public boolean isOpaqueCube() {
                    return true;
                }
            };

    private static final Class<?> LOTR_DWARVEN_DOOR_TE_CLASS =
            LOTRTileEntityDwarvenDoor.class;

    private static final int[][] NEIGHBOR_OFFSETS = new int[][] {
            {1, 0, 0},
            {-1, 0, 0},
            {0, 1, 0},
            {0, -1, 0},
            {0, 0, 1},
            {0, 0, -1}
    };

    private final TileEntitySiegeGate controller;
    private final World world;

    private final Map<PositionKey, GatePartData> parts =
            new HashMap<PositionKey, GatePartData>();

    /*
     * Absolute cells occupied by the finalized gate proxy blocks. While a
     * leaf is detached, these stationary proxy blocks must not become part of
     * the moving leaf's environmental AO field.
     */
    private final Set<PositionKey> gateWorldPositions =
            new HashSet<PositionKey>();

    private final Map<PositionKey, TileEntity> renderTileEntities =
            new HashMap<PositionKey, TileEntity>();

    private long lastVisualUpdateTick =
            Long.MIN_VALUE;

    public GateRenderBlockAccess(
            TileEntitySiegeGate controller,
            GateLeaf leaf
    ) {
        if (controller == null) {
            throw new IllegalArgumentException(
                    "Siege gate controller cannot be null"
            );
        }

        if (leaf == null) {
            throw new IllegalArgumentException(
                    "Siege gate leaf cannot be null"
            );
        }

        this.controller =
                controller;

        this.leaf =
                leaf;

        this.world =
                controller.getWorldObj();

        for (GatePartData part
                : controller.getGateParts()) {

            if (part == null) {
                continue;
            }

            if (part.hasValidAbsolutePosition(
                    controller.xCoord,
                    controller.yCoord,
                    controller.zCoord
            )) {
                gateWorldPositions.add(
                        new PositionKey(
                                part.getAbsoluteX(controller.xCoord),
                                part.getAbsoluteY(controller.yCoord),
                                part.getAbsoluteZ(controller.zCoord)
                        )
                );
            }

            /*
             * Preserve the existing chunk-safe render policy. Include both
             * leaves in the virtual block map so closed-gate source blocks
             * retain their native neighboring blocks for face culling and
             * ambient-occlusion sampling.
             */
            if (!controller
                    .isGatePartLoadedAndPresent(part)) {
                continue;
            }

            PositionKey key =
                    new PositionKey(
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ()
                    );

            parts.put(
                    key,
                    part
            );

            if (part.getLeaf()
                    .contributesTo(leaf)) {
                TileEntity renderTileEntity =
                        createRenderTileEntity(
                                part
                        );

                if (renderTileEntity != null) {
                    renderTileEntities.put(
                            key,
                            renderTileEntity
                    );
                }
            }
        }
    }

    @Override
    public Block getBlock(
            int x,
            int y,
            int z
    ) {
        GatePartData part =
                getPart(
                        x,
                        y,
                        z
                );

        if (part != null) {
            /*
             * Detached moving geometry must see only the blocks that actually
             * move with this leaf. Keeping the opposite leaf in the virtual
             * neighborhood makes native renderers believe two blocks are still
             * touching after the leaves separate; compensating with
             * renderAllFaces then emits hidden interior faces between ordinary
             * same-leaf blocks. Those interior quads become visible as dark
             * one-pixel seams once the complete leaf is rotated.
             *
             * Connected fences/panes/walls deliberately opt into the frozen
             * closed topology and therefore retain the old both-leaves view.
             */
            if (detached
                    && !closedNeighborTopology
                    && !part.getLeaf().contributesTo(leaf)) {
                return Blocks.air;
            }

            Block block =
                    part.getSourceBlock();

            Block cullingProxy =
                    getDetachedInternalFaceCullingProxy(
                            x,
                            y,
                            z,
                            part,
                            block
                    );

            if (cullingProxy != null) {
                return cullingProxy;
            }

            return block == null
                    ? Blocks.iron_block
                    : block;
        }

        if (detached
                && !closedNeighborTopology) {
            return getDetachedEnvironmentalBlock(
                    x,
                    y,
                    z
            );
        }

        return world == null
                ? Blocks.air
                : world.getBlock(
                toWorldX(x),
                toWorldY(y),
                toWorldZ(z)
        );
    }

    @Override
    public TileEntity getTileEntity(
            int x,
            int y,
            int z
    ) {
        TileEntity tileEntity =
                renderTileEntities.get(
                        new PositionKey(
                                toRelativeX(x),
                                toRelativeY(y),
                                toRelativeZ(z)
                        )
                );

        if (tileEntity != null) {
            return tileEntity;
        }

        if (detached
                || world == null) {

            return null;
        }

        return world.getTileEntity(
                toWorldX(x),
                toWorldY(y),
                toWorldZ(z)
        );
    }

    /**
     * Advances explicitly-approved render-only visual state.
     *
     * Generic captured TileEntities are NEVER ticked here because arbitrary
     * mod TileEntities may perform gameplay, inventory, network, redstone,
     * spawning, or world mutation work.
     *
     * The LOTR Ithildin dwarven-door TileEntity is explicitly allowed because
     * its client update is the glow interpolation/proximity state needed by
     * its visual renderer.
     */
    public void updateVisualTileEntities() {
        if (world == null) {
            return;
        }

        long worldTick =
                world.getTotalWorldTime();

        if (worldTick
                == lastVisualUpdateTick) {
            return;
        }

        lastVisualUpdateTick =
                worldTick;

        for (TileEntity tileEntity
                : renderTileEntities.values()) {

            if (tileEntity == null) {
                continue;
            }

            if (!LOTR_DWARVEN_DOOR_TE_CLASS.equals(
                    tileEntity.getClass()
            )) {
                continue;
            }

            try {
                tileEntity.updateEntity();

            } catch (RuntimeException ignored) {
                /*
                 * A visual-only source must never be able to crash the
                 * Siege Gate renderer.
                 */
            }
        }
    }

    @Override
    public int getLightBrightnessForSkyBlocks(
            int x,
            int y,
            int z,
            int minimumBlockLight
    ) {
        if (world == null) {
            return 0;
        }

        /*
         * Detached geometry stays in a virtual controller-relative grid so it
         * can rotate around its hinge. Project every RenderBlocks light sample
         * into the leaf's CURRENT world-space position instead of using a
         * single fully-open or maximum-neighbor brightness.
         *
         * RenderBlocks will make several of these calls for the face and its
         * AO corners. Returning the exact transformed world sample preserves
         * gradients from ground shadow, walls, torches, skylight, etc.
         */
        if (detached) {
            int[] position =
                    getDetachedWorldPosition(
                            toRelativeX(x),
                            toRelativeY(y),
                            toRelativeZ(z)
                    );

            return getDetachedWorldBrightness(
                    position[0],
                    position[1],
                    position[2],
                    minimumBlockLight
            );
        }

        /*
         * Closed geometry must expose the same spatial light field that the
         * surrounding terrain renderer sees. Do not substitute a viewer-side
         * brightness or special-case the structural bottom row here.
         *
         * RenderBlocks already chooses the correct neighboring light samples
         * independently for each face and each AO corner. Replacing a gate
         * cell's brightness with one selected surface value causes those AO
         * samples to become spatially inconsistent, which is what produces the
         * solid dark strip along the lowest gate row and the flatter lighting
         * on the opposite face.
         */
        return world.getLightBrightnessForSkyBlocks(
                toWorldX(x),
                toWorldY(y),
                toWorldZ(z),
                minimumBlockLight
        );
    }



    private int[] getDetachedWorldPosition(
            int x,
            int y,
            int z
    ) {
        GateHinge hinge =
                leaf == GateLeaf.LEFT
                        ? controller.getLeftHinge()
                        : controller.getRightHinge();

        if (hinge == null
                || controller.getGateOrientation() == null) {

            return new int[] {
                    controller.xCoord + x,
                    controller.yCoord + y,
                    controller.zCoord + z
            };
        }

        double angleRadians =
                Math.toRadians(
                        detachedAngleDegrees
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
                x + 0.5D;

        double centerZ =
                z + 0.5D;

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

        /*
         * Match the GL11 Y-axis rotation used by RenderSiegeGate.
         */
        double rotatedX =
                pivotX
                        + offsetX * cosine
                        + offsetZ * sine;

        double rotatedZ =
                pivotZ
                        - offsetX * sine
                        + offsetZ * cosine;

        return new int[] {
                MathHelper.floor_double(
                        controller.xCoord
                                + rotatedX
                ),
                controller.yCoord + y,
                MathHelper.floor_double(
                        controller.zCoord
                                + rotatedZ
                )
        };
    }

    private Block getDetachedEnvironmentalBlock(
            int x,
            int y,
            int z
    ) {
        if (world == null) {
            return Blocks.air;
        }

        int[] position =
                getDetachedWorldPosition(
                        toRelativeX(x),
                        toRelativeY(y),
                        toRelativeZ(z)
                );

        if (!isWorldPositionLoaded(
                position[0],
                position[1],
                position[2]
        )) {
            return Blocks.air;
        }

        /*
         * The real World still contains GatePart proxy blocks at the closed
         * footprint. Those blocks are bookkeeping/collision proxies, not
         * stationary scenery. Do not let a swinging leaf AO-shadow itself
         * against those old cells.
         */
        if (isDetachedRemovedWorldCell(
                position[0],
                position[1],
                position[2]
        )
                || hideControllerFromDetachedSpecialGeometry
                && isControllerWorldCell(
                position[0],
                position[1],
                position[2]
        )) {
            return Blocks.air;
        }

        Block block =
                world.getBlock(
                        position[0],
                        position[1],
                        position[2]
                );

        return block == null
                ? Blocks.air
                : block;
    }

    private int getDetachedEnvironmentalMetadata(
            int x,
            int y,
            int z
    ) {
        if (world == null) {
            return 0;
        }

        int[] position =
                getDetachedWorldPosition(
                        toRelativeX(x),
                        toRelativeY(y),
                        toRelativeZ(z)
                );

        if (!isWorldPositionLoaded(
                position[0],
                position[1],
                position[2]
        )
                || isDetachedRemovedWorldCell(
                position[0],
                position[1],
                position[2]
        )
                || hideControllerFromDetachedSpecialGeometry
                && isControllerWorldCell(
                position[0],
                position[1],
                position[2]
        )) {
            return 0;
        }

        return world.getBlockMetadata(
                position[0],
                position[1],
                position[2]
        );
    }

    private boolean isWorldPositionLoaded(
            int x,
            int y,
            int z
    ) {
        return world != null
                && y >= 0
                && y < world.getHeight()
                && world.blockExists(
                x,
                y,
                z
        );
    }

    private int getDetachedWorldBrightness(
            int x,
            int y,
            int z,
            int minimumBlockLight
    ) {
        if (!isWorldPositionLoaded(
                x,
                y,
                z
        )) {

            return 0;
        }

        /*
         * Detached AO/environment block lookup already removes the finalized
         * GatePart proxy cells from the moving leaf's world. Brightness must
         * use the SAME virtual world.
         *
         * Reading World brightness directly at one of those proxy cells asks
         * Minecraft for the light inside an opaque gate block. That is why a
         * newly exposed inner leaf face can stay almost black after the leaf
         * has visibly swung into open air.
         *
         * The controller cell gets the same surface treatment for brightness
         * samples that quantize into it. Unlike GatePart proxies, the
         * controller remains visible to AO/block-neighbor queries so it can
         * still cast a real local shadow; its dark interior light value is
         * simply not reused as the moving leaf's surface brightness.
         */
        if (isDetachedRemovedWorldCell(
                x,
                y,
                z
        )
                || isControllerWorldCell(
                x,
                y,
                z
        )) {

            return getVirtualAirBrightness(
                    x,
                    y,
                    z,
                    minimumBlockLight
            );
        }

        return world.getLightBrightnessForSkyBlocks(
                x,
                y,
                z,
                minimumBlockLight
        );
    }

    private boolean isDetachedRemovedWorldCell(
            int x,
            int y,
            int z
    ) {
        return gateWorldPositions.contains(
                new PositionKey(
                        x,
                        y,
                        z
                )
        );
    }

    private boolean isControllerWorldCell(
            int x,
            int y,
            int z
    ) {
        return x == controller.xCoord
                && y == controller.yCoord
                && z == controller.zCoord;
    }

    private int getVirtualAirBrightness(
            int x,
            int y,
            int z,
            int minimumBlockLight
    ) {
        /*
         * Reconstruct the light field that would exist at this sample if a
         * stale GatePart proxy were air. Keep sky and block-light channels
         * independent so a nearby torch is not discarded merely because a
         * different neighbor supplies stronger skylight.
         */
        int maxSkyLight = 0;
        int maxBlockLight =
                Math.max(
                        0,
                        Math.min(
                                15,
                                minimumBlockLight
                        )
                );

        boolean foundSample =
                false;

        for (int[] offset : NEIGHBOR_OFFSETS) {
            int sampleX =
                    x + offset[0];

            int sampleY =
                    y + offset[1];

            int sampleZ =
                    z + offset[2];

            if (!isWorldPositionLoaded(
                    sampleX,
                    sampleY,
                    sampleZ
            )) {
                continue;
            }

            /*
             * Do not reconstruct virtual air from another stale gate proxy
             * cell or from the controller's interior. A one- or two-block
             * thick gate still has an exposed front/back neighbor available.
             */
            if (isDetachedRemovedWorldCell(
                    sampleX,
                    sampleY,
                    sampleZ
            )
                    || isControllerWorldCell(
                    sampleX,
                    sampleY,
                    sampleZ
            )) {
                continue;
            }

            int packed =
                    world.getLightBrightnessForSkyBlocks(
                            sampleX,
                            sampleY,
                            sampleZ,
                            minimumBlockLight
                    );

            maxSkyLight =
                    Math.max(
                            maxSkyLight,
                            packed >> 20 & 15
                    );

            maxBlockLight =
                    Math.max(
                            maxBlockLight,
                            packed >> 4 & 15
                    );

            foundSample =
                    true;
        }

        if (!foundSample) {
            return world.getLightBrightnessForSkyBlocks(
                    x,
                    y,
                    z,
                    minimumBlockLight
            );
        }

        return maxSkyLight << 20
                | maxBlockLight << 4;
    }

    @Override
    public int getBlockMetadata(
            int x,
            int y,
            int z
    ) {
        GatePartData part =
                getPart(
                        x,
                        y,
                        z
                );

        if (part != null) {
            if (detached
                    && !closedNeighborTopology
                    && !part.getLeaf().contributesTo(leaf)) {
                return 0;
            }

            return part.getSourceMetadata();
        }

        if (detached
                && !closedNeighborTopology) {
            return getDetachedEnvironmentalMetadata(
                    x,
                    y,
                    z
            );
        }

        if (world == null) {
            return 0;
        }

        return world.getBlockMetadata(
                toWorldX(x),
                toWorldY(y),
                toWorldZ(z)
        );
    }

    @Override
    public boolean isAirBlock(
            int x,
            int y,
            int z
    ) {
        GatePartData part =
                getPart(
                        x,
                        y,
                        z
                );

        if (part != null) {
            return detached
                    && !closedNeighborTopology
                    && !part.getLeaf().contributesTo(leaf);
        }

        if (detached
                && !closedNeighborTopology) {
            return getDetachedEnvironmentalBlock(
                    x,
                    y,
                    z
            ) == Blocks.air;
        }

        return world == null
                || world.isAirBlock(
                toWorldX(x),
                toWorldY(y),
                toWorldZ(z)
        );
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(
            int x,
            int z
    ) {
        if (world == null) {
            return BiomeGenBase.plains;
        }

        if (detached
                && !closedNeighborTopology) {
            int[] position =
                    getDetachedWorldPosition(
                            toRelativeX(x),
                            0,
                            toRelativeZ(z)
                    );

            return world.getBiomeGenForCoords(
                    position[0],
                    position[2]
            );
        }

        return world.getBiomeGenForCoords(
                toWorldX(x),
                toWorldZ(z)
        );
    }

    @Override
    public int getHeight() {
        return world == null
                ? 256
                : world.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return world != null
                && world.extendedLevelsInChunkCache();
    }

    @Override
    public int isBlockProvidingPowerTo(
            int x,
            int y,
            int z,
            int side
    ) {
        /*
         * This is a render-only world. It deliberately does not simulate
         * source redstone behavior.
         */
        return 0;
    }

    @Override
    public boolean isSideSolid(
            int x,
            int y,
            int z,
            ForgeDirection side,
            boolean defaultValue
    ) {
        Block block =
                getBlock(
                        x,
                        y,
                        z
                );

        if (block == null
                || block == Blocks.air) {
            return false;
        }

        try {
            return block.isSideSolid(
                    this,
                    x,
                    y,
                    z,
                    side
            );

        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    public void setDetached(
            boolean detached
    ) {
        this.detached =
                detached;
    }

    public boolean isDetached() {
        return detached;
    }

    public void setAbsoluteCoordinates(
            boolean absoluteCoordinates
    ) {
        this.absoluteCoordinates =
                absoluteCoordinates;
    }

    public void setDetachedAngleDegrees(
            float detachedAngleDegrees
    ) {
        this.detachedAngleDegrees =
                detachedAngleDegrees;
    }

    public void setClosedNeighborTopology(
            boolean closedNeighborTopology
    ) {
        this.closedNeighborTopology =
                closedNeighborTopology;
    }

    public void setHideControllerFromDetachedSpecialGeometry(
            boolean hideControllerFromDetachedSpecialGeometry
    ) {
        this.hideControllerFromDetachedSpecialGeometry =
                hideControllerFromDetachedSpecialGeometry;
    }

    public void beginDetachedInternalFaceCulling(
            GatePartData currentPart,
            Block sourceBlock,
            boolean stairs
    ) {
        if (!detached
                || currentPart == null
                || sourceBlock == null) {
            clearDetachedInternalFaceCulling();
            return;
        }

        detachedInternalFaceCullActive =
                true;
        detachedInternalFaceCullStairs =
                stairs;
        detachedInternalFaceCullX =
                currentPart.getRelativeX();
        detachedInternalFaceCullY =
                currentPart.getRelativeY();
        detachedInternalFaceCullZ =
                currentPart.getRelativeZ();
        detachedInternalFaceCullSource =
                sourceBlock;
    }

    public void clearDetachedInternalFaceCulling() {
        detachedInternalFaceCullActive =
                false;
        detachedInternalFaceCullStairs =
                false;
        detachedInternalFaceCullX =
                0;
        detachedInternalFaceCullY =
                0;
        detachedInternalFaceCullZ =
                0;
        detachedInternalFaceCullSource =
                null;
    }

    private Block getDetachedInternalFaceCullingProxy(
            int x,
            int y,
            int z,
            GatePartData queriedPart,
            Block queriedBlock
    ) {
        if (!detachedInternalFaceCullActive
                || !detached
                || closedNeighborTopology
                || queriedPart == null
                || queriedBlock == null
                || !queriedPart.getLeaf().contributesTo(leaf)) {
            return null;
        }

        int relativeX =
                toRelativeX(x);
        int relativeY =
                toRelativeY(y);
        int relativeZ =
                toRelativeZ(z);

        if (relativeX == detachedInternalFaceCullX
                && relativeY == detachedInternalFaceCullY
                && relativeZ == detachedInternalFaceCullZ) {
            return null;
        }

        int dx =
                Math.abs(relativeX - detachedInternalFaceCullX);
        int dy =
                Math.abs(relativeY - detachedInternalFaceCullY);
        int dz =
                Math.abs(relativeZ - detachedInternalFaceCullZ);

        /* Only direct face-neighbors participate in side culling. */
        if (dx + dy + dz != 1) {
            return null;
        }

        if (detachedInternalFaceCullStairs) {
            if (queriedBlock instanceof BlockStairs) {
                return DETACHED_OPAQUE_STAIR_NEIGHBOR;
            }
            return null;
        }

        /*
         * Ithildin uses one exact block instance for every captured door tile.
         * Replacing only matching same-leaf neighbors with an opaque stand-in
         * suppresses the otherwise-retained internal full-block faces without
         * changing mixed-material boundaries.
         */
        return queriedBlock == detachedInternalFaceCullSource
                ? Blocks.stone
                : null;
    }

    /**
     * Preserve approved render-only visual state across a renderer-cache
     * rebuild which did not change the gate's structural revision.
     *
     * Ithildin glow interpolation is transient TileEntity state and is not
     * written into the captured source NBT. Recreating the render-only TE on
     * CLOSED -> OPENING would therefore restart the moon-rune glow.
     */
    public void inheritVisualStateFrom(
            GateRenderBlockAccess previous
    ) {
        if (previous == null
                || previous.controller != controller
                || previous.leaf != leaf) {
            return;
        }

        for (Map.Entry<PositionKey, TileEntity> entry
                : renderTileEntities.entrySet()) {

            TileEntity current =
                    entry.getValue();

            TileEntity old =
                    previous.renderTileEntities.get(
                            entry.getKey()
                    );

            if (current == null
                    || old == null
                    || !current.getClass().equals(
                    old.getClass()
            )) {
                continue;
            }

            if (!LOTR_DWARVEN_DOOR_TE_CLASS.equals(
                    current.getClass()
            )) {
                continue;
            }

            entry.setValue(
                    old
            );
        }

        /*
         * Do not advance the inherited visual TE twice if a cache rebuild
         * occurs during the same client world tick.
         */
        lastVisualUpdateTick =
                previous.lastVisualUpdateTick;
    }

    private TileEntity createRenderTileEntity(
            GatePartData part
    ) {
        if (world == null
                || part == null) {
            return null;
        }

        Block sourceBlock =
                part.getSourceBlock();

        int absoluteX = part.getAbsoluteX(controller.xCoord);
        int absoluteY = part.getAbsoluteY(controller.yCoord);
        int absoluteZ = part.getAbsoluteZ(controller.zCoord);

        /*
         * Vanilla chests are TESR-only blocks. RenderBlocks intentionally has
         * no real chest model and exposes a plank-like placeholder icon, which
         * is why a captured chest otherwise appears as an oak-plank cube while
         * it belongs to a Siege Gate.
         *
         * Create only a visual chest TE. It never receives inventory NBT and is
         * never inserted into the real World TE list. The overridden visual
         * block/metadata let vanilla's chest TESR retain the captured facing
         * even though the real world cell contains a GatePart.
         */
        if (sourceBlock == Blocks.chest
                || sourceBlock == Blocks.trapped_chest) {
            RenderOnlyChestTileEntity chest =
                    new RenderOnlyChestTileEntity(
                            this,
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ(),
                            sourceBlock,
                            part.getSourceMetadata()
                    );

            chest.setWorldObj(world);
            chest.xCoord = absoluteX;
            chest.yCoord = absoluteY;
            chest.zCoord = absoluteZ;
            return chest;
        }

        if (!(sourceBlock
                instanceof LOTRBlockGateDwarvenIthildin)
                || !part.hasSourceTileEntityNbt()) {
            return null;
        }

        NBTTagCompound snapshot = part.getSourceTileEntityNbt();
        if (snapshot == null) {
            return null;
        }

        snapshot.setInteger("x", absoluteX);
        snapshot.setInteger("y", absoluteY);
        snapshot.setInteger("z", absoluteZ);

        try {
            /*
             * Never instantiate arbitrary captured TileEntities on the client.
             * Ithildin remains the other explicit visual exception required by
             * the working rune/glow renderer.
             */
            LOTRTileEntityDwarvenDoor tileEntity =
                    new LOTRTileEntityDwarvenDoor();
            tileEntity.readFromNBT(snapshot);
            tileEntity.setWorldObj(world);
            tileEntity.xCoord = absoluteX;
            tileEntity.yCoord = absoluteY;
            tileEntity.zCoord = absoluteZ;
            return tileEntity;

        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private TileEntityChest getAdjacentRenderChest(
            int relativeX,
            int relativeY,
            int relativeZ,
            Block expectedBlock
    ) {
        PositionKey key =
                new PositionKey(
                        relativeX,
                        relativeY,
                        relativeZ
                );

        GatePartData neighborPart =
                parts.get(
                        key
                );

        if (neighborPart == null
                || neighborPart.getSourceBlock() != expectedBlock) {
            return null;
        }

        /*
         * A closed double chest may legitimately straddle the two gate leaves,
         * because both leaves still occupy their original shared plane. Once
         * detached, never bind a chest to a neighbor on the other leaf: the
         * halves can rotate apart and must render independently.
         */
        if (detached
                && !neighborPart.getLeaf()
                .contributesTo(leaf)) {
            return null;
        }

        TileEntity tileEntity =
                renderTileEntities.get(
                        key
                );

        if (!(tileEntity instanceof RenderOnlyChestTileEntity)) {
            tileEntity =
                    createRenderTileEntity(
                            neighborPart
                    );

            if (tileEntity instanceof RenderOnlyChestTileEntity) {
                renderTileEntities.put(
                        key,
                        tileEntity
                );
            }
        }

        if (!(tileEntity instanceof RenderOnlyChestTileEntity)) {
            return null;
        }

        RenderOnlyChestTileEntity chest =
                (RenderOnlyChestTileEntity)tileEntity;

        return chest.getBlockType() == expectedBlock
                ? chest
                : null;
    }

    private static final class RenderOnlyChestTileEntity
            extends TileEntityChest {

        private final GateRenderBlockAccess owner;
        private final int relativeX;
        private final int relativeY;
        private final int relativeZ;
        private final Block visualBlock;
        private final int visualMetadata;

        private RenderOnlyChestTileEntity(
                GateRenderBlockAccess owner,
                int relativeX,
                int relativeY,
                int relativeZ,
                Block visualBlock,
                int visualMetadata
        ) {
            super(
                    visualBlock == Blocks.trapped_chest
                            ? 1
                            : 0
            );

            this.owner = owner;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.relativeZ = relativeZ;
            this.visualBlock = visualBlock;
            this.visualMetadata =
                    Math.max(
                            0,
                            Math.min(
                                    visualMetadata,
                                    15
                            )
                    );
        }

        @Override
        public Block getBlockType() {
            return visualBlock;
        }

        @Override
        public int getBlockMetadata() {
            return visualMetadata;
        }

        @Override
        public void checkForAdjacentChests() {
            /*
             * The real world cells are Siege Gate Part blocks, so vanilla's
             * TileEntityChest adjacency lookup cannot see another captured
             * chest. Resolve neighbors from the gate's captured source map
             * instead. Closed leaves may still form a native double chest;
             * detached leaves deliberately ignore opposite-leaf neighbors.
             */
            this.adjacentChestChecked = true;
            this.adjacentChestXNeg =
                    owner.getAdjacentRenderChest(
                            relativeX - 1,
                            relativeY,
                            relativeZ,
                            visualBlock
                    );
            this.adjacentChestXPos =
                    owner.getAdjacentRenderChest(
                            relativeX + 1,
                            relativeY,
                            relativeZ,
                            visualBlock
                    );
            this.adjacentChestZNeg =
                    owner.getAdjacentRenderChest(
                            relativeX,
                            relativeY,
                            relativeZ - 1,
                            visualBlock
                    );
            this.adjacentChestZPos =
                    owner.getAdjacentRenderChest(
                            relativeX,
                            relativeY,
                            relativeZ + 1,
                            visualBlock
                    );
        }
    }

    private int toRelativeX(int x) {
        return absoluteCoordinates
                ? x - controller.xCoord
                : x;
    }

    private int toRelativeY(int y) {
        return absoluteCoordinates
                ? y - controller.yCoord
                : y;
    }

    private int toRelativeZ(int z) {
        return absoluteCoordinates
                ? z - controller.zCoord
                : z;
    }

    private int toWorldX(int x) {
        return absoluteCoordinates
                ? x
                : controller.xCoord + x;
    }

    private int toWorldY(int y) {
        return absoluteCoordinates
                ? y
                : controller.yCoord + y;
    }

    private int toWorldZ(int z) {
        return absoluteCoordinates
                ? z
                : controller.zCoord + z;
    }

    private GatePartData getPart(
            int x,
            int y,
            int z
    ) {
        GatePartData part =
                parts.get(
                        new PositionKey(
                                toRelativeX(x),
                                toRelativeY(y),
                                toRelativeZ(z)
                        )
                );

        if (part != null
                && detached
                && !closedNeighborTopology
                && !part.getLeaf()
                .contributesTo(leaf)) {

            return null;
        }

        return part;
    }

    private static final class PositionKey {

        private final int x;
        private final int y;
        private final int z;

        private PositionKey(
                int x,
                int y,
                int z
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(
                Object other
        ) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof PositionKey)) {
                return false;
            }

            PositionKey position =
                    (PositionKey)other;

            return x == position.x
                    && y == position.y
                    && z == position.z;
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
}
