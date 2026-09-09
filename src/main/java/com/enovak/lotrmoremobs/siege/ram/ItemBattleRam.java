package com.enovak.lotrmoremobs.siege.ram;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import lotr.common.LOTRCreativeTabs;
import lotr.common.fac.LOTRFaction;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Facing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Creative-only faction Battle Ram spawn egg.
 *
 * The item deliberately has no crafting recipe. One registered item stores the
 * selected faction in NBT and exposes one creative-stack variant per supported
 * normal faction.
 */
public class ItemBattleRam extends ItemMonsterPlacer {

    public static final String NBT_RAM_FACTION = "RamFaction";

    private static final int EGG_WOOD_COLOR = 0x6A4A2D;
    private static final int EGG_FALLBACK_FACTION_COLOR = 0xA9A9A9;

    public ItemBattleRam() {
        setHasSubtypes(true);
        setMaxStackSize(64);
        if (MumakilConfig.enableBattleRams) {
            setCreativeTab(LOTRCreativeTabs.tabSpawn);
        }
        setUnlocalizedName("battle_ram_spawn_egg");
        setTextureName("spawn_egg");
    }

    public static ItemStack createForFaction(
            Item item,
            LOTRFaction faction
    ) {
        ItemStack stack = new ItemStack(item);
        if (faction != null) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString(NBT_RAM_FACTION, faction.codeName());
            stack.setTagCompound(nbt);
        }
        return stack;
    }

    public static LOTRFaction getFaction(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        return LOTRFaction.forName(
                stack.getTagCompound().getString(NBT_RAM_FACTION)
        );
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        LOTRFaction faction = getFaction(stack);
        if (faction == null) {
            return "Battle Ram Spawn Egg";
        }
        return faction.factionName() + " Battle Ram Spawn Egg";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(
            ItemStack stack,
            int renderPass
    ) {
        if (renderPass == 0) {
            return EGG_WOOD_COLOR;
        }

        LOTRFaction faction = getFaction(stack);
        return faction == null
                ? EGG_FALLBACK_FACTION_COLOR
                : faction.getFactionColor();
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
        if (world.isRemote) {
            return true;
        }
        if (!MumakilConfig.enableBattleRams) {
            player.addChatMessage(new ChatComponentText(
                    "Battle Rams are disabled in the addon config."
            ));
            return false;
        }

        LOTRFaction faction = getFaction(stack);
        if (!BattleRamCrewTypes.isSupported(faction)) {
            player.addChatMessage(new ChatComponentText(
                    "This Battle Ram has no supported faction crew."
            ));
            return false;
        }

        Block clickedBlock = world.getBlock(x, y, z);
        x += Facing.offsetsXForSide[side];
        y += Facing.offsetsYForSide[side];
        z += Facing.offsetsZForSide[side];

        if (!player.canPlayerEdit(x, y, z, side, stack)) {
            return false;
        }

        double verticalOffset = side == 1
                && clickedBlock.getRenderType() == 11
                ? 0.5D
                : 0.0D;

        float yaw = MathHelper.floor_double(
                player.rotationYaw * 4.0F / 360.0F + 0.5D
        ) & 3;
        yaw *= 90.0F;

        EntityBattleRam ram = new EntityBattleRam(world);
        ram.setLocationAndAngles(
                x + 0.5D,
                y + verticalOffset,
                z + 0.5D,
                yaw,
                0.0F
        );

        if (!world.checkNoEntityCollision(ram.boundingBox)
                || !world.getCollidingBoundingBoxes(
                        ram,
                        ram.boundingBox
                ).isEmpty()) {

            player.addChatMessage(new ChatComponentText(
                    "There is not enough room to place the Battle Ram."
            ));
            return false;
        }

        if (!ram.initializeForCommander(player, faction)) {
            player.addChatMessage(new ChatComponentText(
                    "Battle Ram ownership storage is unavailable."
            ));
            return false;
        }

        if (!world.spawnEntityInWorld(ram)) {
            return false;
        }

        if (!player.capabilities.isCreativeMode) {
            --stack.stackSize;
        }
        return true;
    }

    /**
     * Battle Rams need a large, dry placement footprint, so unlike ordinary
     * spawn eggs they do not use ItemMonsterPlacer's water right-click path.
     */
    @Override
    public ItemStack onItemRightClick(
            ItemStack stack,
            World world,
            EntityPlayer player
    ) {
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(
            ItemStack stack,
            EntityPlayer player,
            List lines,
            boolean advanced
    ) {
        LOTRFaction faction = getFaction(stack);
        lines.add("Faction: " + (faction == null
                ? "UNASSIGNED"
                : faction.factionName()));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(
            Item item,
            CreativeTabs tab,
            List items
    ) {
        if (!MumakilConfig.enableBattleRams) {
            return;
        }
        for (LOTRFaction faction
                : BattleRamCrewTypes.getSupportedTypes().keySet()) {

            if (faction == LOTRFaction.RUFFIAN
                    || faction == LOTRFaction.UTUMNO) {
                continue;
            }
            items.add(createForFaction(item, faction));
        }
    }
}
