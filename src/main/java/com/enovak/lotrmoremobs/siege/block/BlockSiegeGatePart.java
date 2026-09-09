package com.enovak.lotrmoremobs.siege.block;

import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateNpcSightHelper;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateOwnershipData;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.repair.GateManagementManager;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import com.enovak.lotrmoremobs.siege.gate.GateLightingBlockAccess;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;

public class BlockSiegeGatePart extends Block {

    private static int fallbackRenderType = -1;

    public BlockSiegeGatePart() {
        super(Material.glass);
        setBlockName("siege_gate_part");
        setBlockTextureName("minecraft:iron_bars");
        setBlockUnbreakable();
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setLightOpacity(0);
    }

    @Override
    public int getLightValue(
            IBlockAccess blockAccess,
            int x,
            int y,
            int z
    ) {
        GateLightingBlockAccess lightingAccess =
                getClosedGateLightingAccess(
                        blockAccess,
                        x,
                        y,
                        z
                );

        if (lightingAccess == null) {
            return 0;
        }

        Block sourceBlock =
                lightingAccess.getBlock(
                        x,
                        y,
                        z
                );

        if (sourceBlock == null
                || sourceBlock == this
                || sourceBlock == net.minecraft.init.Blocks.air) {

            return 0;
        }

        try {
            return Math.max(
                    0,
                    Math.min(
                            15,
                            sourceBlock.getLightValue(
                                    lightingAccess,
                                    x,
                                    y,
                                    z
                            )
                    )
            );

        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    @Override
    public int getLightOpacity(
            IBlockAccess blockAccess,
            int x,
            int y,
            int z
    ) {
        GateLightingBlockAccess lightingAccess =
                getClosedGateLightingAccess(
                        blockAccess,
                        x,
                        y,
                        z
                );

        /*
         * A gate that is opening/open/closing/breached no longer physically
         * occupies its stored GatePart positions for lighting purposes.
         */
        if (lightingAccess == null) {
            return 0;
        }

        Block sourceBlock =
                lightingAccess.getBlock(
                        x,
                        y,
                        z
                );

        if (sourceBlock == null
                || sourceBlock == this
                || sourceBlock == net.minecraft.init.Blocks.air) {

            return 0;
        }

        try {
            return Math.max(
                    0,
                    Math.min(
                            255,
                            sourceBlock.getLightOpacity(
                                    lightingAccess,
                                    x,
                                    y,
                                    z
                            )
                    )
            );

        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private GateLightingBlockAccess getClosedGateLightingAccess(
            IBlockAccess blockAccess,
            int x,
            int y,
            int z
    ) {
        if (!(blockAccess instanceof World)) {
            return null;
        }

        World world =
                (World)blockAccess;

        TileEntitySiegeGate controller =
                GateRegistry.getController(
                        world,
                        x,
                        y,
                        z
                );

        if (controller == null
                || controller.getGateState()
                != GateState.CLOSED) {

            return null;
        }

        GatePartData part =
                controller.getGatePartData(
                        x - controller.xCoord,
                        y - controller.yCoord,
                        z - controller.zCoord
                );

        if (part == null) {
            return null;
        }

        return new GateLightingBlockAccess(
                controller
        );
    }

    @Override
    public void breakBlock(
            World world,
            int x,
            int y,
            int z,
            Block block,
            int metadata
    ) {
        TileEntitySiegeGate controller =
                GateRegistry.getController(world, x, y, z);
        if (controller != null && world.isRemote) {
            controller.onPartChunkAvailabilityChanged();
        }
        GateRegistry.unregisterGatePart(world, x, y, z);
        super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override
    public float getPlayerRelativeBlockHardness(
            EntityPlayer player,
            World world,
            int x,
            int y,
            int z
    ) {
        /*
         * GateParts remain unbreakable in Survival. Creative players get the
         * normal instant-break interaction, but the actual server-side removal
         * below still has to pass the gate structure transaction checks.
         */
        if (player == null || !player.capabilities.isCreativeMode) {
            return 0.0F;
        }

        TileEntitySiegeGate controller =
                GateRegistry.getController(world, x, y, z);
        return controller == null
                || controller.getGateState() == GateState.CLOSED
                ? 1.0F
                : 0.0F;
    }

    @Override
    public boolean removedByPlayer(
            World world,
            EntityPlayer player,
            int x,
            int y,
            int z,
            boolean willHarvest
    ) {
        if (world == null
                || player == null
                || !player.capabilities.isCreativeMode) {
            return false;
        }

        /*
         * Match vanilla Creative prediction on the client. The authoritative
         * structure mutation happens on the server and will resynchronize the
         * controller/part if the server rejects the edit.
         */
        if (world.isRemote) {
            return world.setBlockToAir(x, y, z);
        }

        TileEntitySiegeGate controller =
                GateRegistry.getController(world, x, y, z);

        if (controller != null) {
            boolean removed =
                    controller.removeGatePartForCreativeBreak(
                            player,
                            x,
                            y,
                            z
                    );
            if (!removed) {
                world.markBlockForUpdate(x, y, z);
            }
            return removed;
        }

        /*
         * A durable owner without a loaded controller is not an orphan. Leave
         * it alone so Creative breaking cannot bypass cross-chunk ownership.
         * A truly unowned stale GatePart may be cleaned up normally.
         */
        if (GateRegistry.getDurablePartOwner(world, x, y, z) != null) {
            world.markBlockForUpdate(x, y, z);
            return false;
        }

        return world.setBlockToAir(x, y, z);
    }

    @Override
    @Deprecated
    public boolean removedByPlayer(
            World world,
            EntityPlayer player,
            int x,
            int y,
            int z
    ) {
        return removedByPlayer(
                world,
                player,
                x,
                y,
                z,
                false
        );
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(
            World world,
            int x,
            int y,
            int z
    ) {
        TileEntitySiegeGate controller =
                GateRegistry.getController(world, x, y, z);
        if (controller != null) {
            return controller.getGateState() == GateState.CLOSED
                    ? super.getCollisionBoundingBoxFromPool(
                            world,
                            x,
                            y,
                            z
                    )
                    : null;
        }
        SiegeGateOwnershipData.DurablePartOwner durableOwner =
                GateRegistry.getDurablePartOwner(world, x, y, z);
        if (durableOwner == null) {
            return super.getCollisionBoundingBoxFromPool(world, x, y, z);
        }
        if (durableOwner.getStatus()
                == SiegeGateOwnershipData.ControllerStatus.MUTATING
                || durableOwner.getStatus()
                == SiegeGateOwnershipData.ControllerStatus.TOMBSTONED) {
            return null;
        }
        return durableOwner.getLastGateState() == GateState.CLOSED
                ? super.getCollisionBoundingBoxFromPool(
                        world,
                        x,
                        y,
                        z
                )
                : null;
    }

    @Override
    public MovingObjectPosition collisionRayTrace(
            World world,
            int x,
            int y,
            int z,
            Vec3 start,
            Vec3 end
    ) {
        if (SiegeGateNpcSightHelper.shouldPassThroughGatePart(
                world,
                x,
                y,
                z
        )) {
            return null;
        }
        return super.collisionRayTrace(world, x, y, z, start, end);
    }

    @Override
    public boolean onBlockActivated(
            World world,
            int x,
            int y,
            int z,
            EntityPlayer player,
            int side,
            float hitX,
            float hitY,
            float hitZ
    ) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            TileEntitySiegeGate controller =
                    GateRegistry.getController(world, x, y, z);
            if (controller != null) {
                if (player.isSneaking()) {
                    GateManagementManager.open(
                            (EntityPlayerMP)player,
                            controller
                    );
                } else {
                    controller.tryToggleOpenState(
                            (EntityPlayerMP)player
                    );
                }
            }
        }
        return true;
    }

    @Override
    public int getRenderType() {
        return fallbackRenderType;
    }

    public static void setFallbackRenderType(int renderType) {
        fallbackRenderType = renderType;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public float getExplosionResistance(Entity entity) {
        return Float.MAX_VALUE;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public void onBlockExploded(
            World world,
            int x,
            int y,
            int z,
            Explosion explosion
    ) {
        // GateParts are removable by controller teardown, not explosions.
    }

    @Override
    public int getFlammability(
            IBlockAccess world,
            int x,
            int y,
            int z,
            ForgeDirection face
    ) {
        return 0;
    }

    @Override
    public int getFireSpreadSpeed(
            IBlockAccess world,
            int x,
            int y,
            int z,
            ForgeDirection face
    ) {
        return 0;
    }

    @Override
    public boolean isFlammable(
            IBlockAccess world,
            int x,
            int y,
            int z,
            ForgeDirection face
    ) {
        return false;
    }
}
