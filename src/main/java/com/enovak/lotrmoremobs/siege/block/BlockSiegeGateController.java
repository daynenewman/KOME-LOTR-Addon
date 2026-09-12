package com.enovak.lotrmoremobs.siege.block;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.creation.GateCreationManager;
import com.enovak.lotrmoremobs.siege.edit.GateEditSessionManager;
import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateOwnershipData;
import com.enovak.lotrmoremobs.siege.repair.GateManagementManager;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import lotr.common.LOTRCreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class BlockSiegeGateController extends BlockContainer {

    public BlockSiegeGateController() {
        super(Material.iron);
        setBlockName("siege_gate_controller");
        setBlockTextureName("lotrmoremobs:gate_controller_block");
        if (MumakilConfig.enableSiegeGates) {
            setCreativeTab(LOTRCreativeTabs.tabUtil);
        }
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntitySiegeGate();
    }

    @Override
    public IIcon getIcon(
            IBlockAccess world,
            int x,
            int y,
            int z,
            int side
    ) {
        TileEntity tileEntity =
                world.getTileEntity(
                        x,
                        y,
                        z
                );

        if (tileEntity instanceof TileEntitySiegeGate) {
            TileEntitySiegeGate gate =
                    (TileEntitySiegeGate)tileEntity;

            Block appearance =
                    gate.getControllerAppearanceBlock();

            if (appearance != null
                    && appearance != this) {

                try {
                    IIcon icon =
                            appearance.getIcon(
                                    side,
                                    gate.getControllerAppearanceMetadata()
                            );

                    if (icon != null) {
                        return icon;
                    }

                } catch (RuntimeException ignored) {
                }
            }
        }

        return getDefaultControllerIcon(
                side
        );
    }

    @Override
    public IIcon getIcon(
            int side,
            int metadata
    ) {
        return getDefaultControllerIcon(
                side
        );
    }

    @Override
    public int colorMultiplier(
            IBlockAccess world,
            int x,
            int y,
            int z
    ) {
        TileEntity tileEntity =
                world.getTileEntity(
                        x,
                        y,
                        z
                );

        if (tileEntity instanceof TileEntitySiegeGate) {
            TileEntitySiegeGate gate =
                    (TileEntitySiegeGate)tileEntity;

            Block appearance =
                    gate.getControllerAppearanceBlock();

            if (appearance != null
                    && appearance != this) {

                try {
                    return appearance.colorMultiplier(
                            world,
                            x,
                            y,
                            z
                    );

                } catch (RuntimeException ignored) {
                    return appearance.getRenderColor(
                            gate.getControllerAppearanceMetadata()
                    );
                }
            }
        }

        return 0xFFFFFF;
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
        /*
         * Sneaking deliberately does NOT activate the controller.
         *
         * Returning false allows normal held-item interaction to continue,
         * including placing blocks against any face of the controller.
         */
        if (player.isSneaking()) {
            return false;
        }

        if (!MumakilConfig.enableSiegeGates) {
            return false;
        }

        /*
         * Normal right-click is now the single controller-GUI interaction.
         *
         * Consume the click on both sides so a held block/item does not also
         * try to use itself against the controller.
         */
        if (world.isRemote) {
            return true;
        }

        TileEntity tileEntity =
                world.getTileEntity(
                        x,
                        y,
                        z
                );

        if (!(tileEntity instanceof TileEntitySiegeGate)
                || !(player instanceof EntityPlayerMP)) {

            return true;
        }

        TileEntitySiegeGate gate =
                (TileEntitySiegeGate)tileEntity;

        EntityPlayerMP serverPlayer =
                (EntityPlayerMP)player;

        if (gate.isFinalized()) {
            /*
             * Finalized gate:
             * normal right-click opens Gate Management.
             */
            GateManagementManager.open(
                    serverPlayer,
                    gate
            );

        } else {
            /*
             * New/unfinalized controller:
             * normal right-click opens Creation controls.
             */
            GateCreationManager.openControls(
                    serverPlayer,
                    gate
            );
        }

        return true;
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
        if (world.isRemote) {
            return world.setBlockToAir(x, y, z);
        }
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof TileEntitySiegeGate)) {
            return GateRegistry.hasDurableController(world, x, y, z)
                    ? false
                    : world.setBlockToAir(x, y, z);
        }

        TileEntitySiegeGate gate = (TileEntitySiegeGate)tileEntity;
        boolean protectedGate = gate.isFinalized()
                || gate.isGateStructureQuarantined()
                || GateRegistry.hasDurableController(world, x, y, z);
        if (!protectedGate) {
            return world.setBlockToAir(x, y, z);
        }
        if (!(player instanceof EntityPlayerMP)
                || !gate.canDismantle((EntityPlayerMP)player)
                || !gate.prepareControllerRemovalTransaction()) {
            return false;
        }

        boolean removed = world.setBlockToAir(x, y, z);
        if (removed) {
            gate.dismantleGateParts();
        } else {
            gate.abortPreparedControllerRemovalTransaction();
        }
        return removed;
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
        return removedByPlayer(world, player, x, y, z, false);
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
        if (!world.isRemote) {
            /*
             * Removing the controller invalidates any transient EDIT_EXISTING
             * draft immediately. Release the server lease and tell the editing
             * client to clear its edit-mode mirror before teardown continues.
             */
            GateEditSessionManager.cancelForController(world, x, y, z);
            GateCreationManager.cancelForController(world, x, y, z);
        }
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!world.isRemote
                && tileEntity instanceof TileEntitySiegeGate) {
            ((TileEntitySiegeGate)tileEntity).dismantleGateParts();
        } else {
            if (!world.isRemote
                    && GateRegistry.hasDurableController(
                            world,
                            x,
                            y,
                            z
                    )) {
                SiegeGateOwnershipData ownership =
                        SiegeGateOwnershipData.get(world, false);
                if (ownership != null) {
                    ownership.markControllerQuarantined(
                            world.provider.dimensionId,
                            null,
                            x,
                            y,
                            z
                    );
                }
            }
            GateRegistry.unregisterController(world, x, y, z);
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override
    public int getRenderType() {
        return 0;
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
        // Only normal player/controller teardown should dismantle a gate.
    }

    private IIcon getDefaultControllerIcon(
            int side
    ) {
        /*
         * blockIcon comes from:
         *
         * lotrmoremobs:textures/blocks/gate_controller_block.png
         *
         * Iron block remains only an early-registration safety fallback.
         */
        return blockIcon != null
                ? blockIcon
                : Blocks.iron_block.getIcon(
                side,
                0
        );
    }
}
