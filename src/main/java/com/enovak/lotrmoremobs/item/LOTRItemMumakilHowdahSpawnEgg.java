package com.enovak.lotrmoremobs.item;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.spawning.MumakilWarFormationFactory;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import lotr.common.LOTRCreativeTabs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Facing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * A fixed-purpose spawn egg for one complete autonomous Near Harad Mumak war
 * formation. Formation construction and rollback remain owned by the shared
 * formation factory.
 */
public final class LOTRItemMumakilHowdahSpawnEgg
        extends ItemMonsterPlacer {
    private static final int PRIMARY_COLOR = 0x5D5C51;
    private static final int SECONDARY_COLOR = 0xA57532;

    public LOTRItemMumakilHowdahSpawnEgg() {
        this.setHasSubtypes(false);
        this.setMaxStackSize(64);
        if (MumakilConfig.enableMumakil) {
            this.setCreativeTab(LOTRCreativeTabs.tabSpawn);
        }
        this.setUnlocalizedName("mumakil_howdah_spawn_egg");
        this.setTextureName("spawn_egg");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return StatCollector.translateToLocal(
                this.getUnlocalizedName() + ".name"
        ).trim();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        return renderPass == 0
                ? PRIMARY_COLOR
                : SECONDARY_COLOR;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(
            Item item,
            net.minecraft.creativetab.CreativeTabs creativeTab,
            List itemList
    ) {
        if (MumakilConfig.enableMumakil) {
            itemList.add(new ItemStack(item));
        }
    }

    @Override
    public boolean onItemUse(
            ItemStack stack,
            EntityPlayer player,
            World world,
            int x,
            int y,
            int z,
            int side,
            float hitX,
            float hitY,
            float hitZ
    ) {
        if (!MumakilConfig.enableMumakil) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }

        Block clickedBlock = world.getBlock(x, y, z);
        x += Facing.offsetsXForSide[side];
        y += Facing.offsetsYForSide[side];
        z += Facing.offsetsZForSide[side];

        double verticalOffset = 0.0D;
        if (side == 1 && clickedBlock.getRenderType() == 11) {
            verticalOffset = 0.5D;
        }

        if (this.spawnFormation(
                world,
                player,
                stack,
                (double)x + 0.5D,
                (double)y + verticalOffset,
                (double)z + 0.5D
        ) && !player.capabilities.isCreativeMode) {
            --stack.stackSize;
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(
            ItemStack stack,
            World world,
            EntityPlayer player
    ) {
        if (!MumakilConfig.enableMumakil) {
            return stack;
        }
        if (world.isRemote) {
            return stack;
        }

        MovingObjectPosition hit = this.getMovingObjectPositionFromPlayer(
                world,
                player,
                true
        );
        if (hit == null
                || hit.typeOfHit
                != MovingObjectPosition.MovingObjectType.BLOCK) {
            return stack;
        }

        int x = hit.blockX;
        int y = hit.blockY;
        int z = hit.blockZ;
        if (!world.canMineBlock(player, x, y, z)
                || !player.canPlayerEdit(
                x,
                y,
                z,
                hit.sideHit,
                stack
        )
                || !(world.getBlock(x, y, z)
                instanceof BlockLiquid)) {
            return stack;
        }

        if (this.spawnFormation(
                world,
                player,
                stack,
                (double)x + 0.5D,
                (double)y + 0.5D,
                (double)z + 0.5D
        ) && !player.capabilities.isCreativeMode) {
            --stack.stackSize;
        }
        return stack;
    }

    private boolean spawnFormation(
            World world,
            EntityPlayer player,
            ItemStack eggStack,
            double x,
            double y,
            double z
    ) {
        float yaw = MathHelper.wrapAngleTo180_float(
                world.rand.nextFloat() * 360.0F
        );
        String customName = eggStack.hasDisplayName()
                ? eggStack.getDisplayName()
                : null;
        return MumakilWarFormationFactory
                .createSpawnEggFormation(
                        world,
                        player,
                        x,
                        y,
                        z,
                        yaw,
                        customName
                );
    }
}
