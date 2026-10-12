package kome.common.data;

import lotr.common.LOTRMod;
import net.minecraft.init.Items;
import net.minecraft.item.*;

/** Conservative acquisition allow-list: ordinary gathering, farming and vanilla recipes.
 * No military equipment, faction furnace alloy, restricted recipe or reward-only input.
 * Native water in an ordinary mug needs no brewing strength or faction workbench.
 */
public final class KOMEProgressionGoodsEligibility {
    private KOMEProgressionGoodsEligibility() {}
    public static boolean profession(ItemStack stack){
        if(stack==null||stack.getItem()==null||KOMEProgressionGoodsRules.consumable(stack.getItem()))return false;
        String key=String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
        if(stack.getItemDamage()!=0)return false;
        return java.util.Arrays.asList("minecraft:coal","minecraft:iron_ingot","minecraft:gold_ingot","minecraft:gold_nugget",
            "minecraft:iron_ore","minecraft:gold_ore","minecraft:leather","minecraft:string","minecraft:flint",
            "minecraft:cobblestone","minecraft:stone","minecraft:stonebrick","minecraft:brick","minecraft:clay_ball",
            "minecraft:sandstone","minecraft:planks","minecraft:log","minecraft:stick","minecraft:sapling",
            "minecraft:wheat","minecraft:wheat_seeds","minecraft:wool","minecraft:hay_block","minecraft:lead",
            "minecraft:red_flower","minecraft:yellow_flower","minecraft:sugar","minecraft:egg","minecraft:water_bucket",
            "minecraft:arrow","minecraft:feather","minecraft:bone","minecraft:glass_bottle","minecraft:red_mushroom",
            "minecraft:brown_mushroom","minecraft:reeds","minecraft:bowl").contains(key)
            ||stack.getItem()==LOTRMod.copper||stack.getItem()==LOTRMod.tin||stack.getItem()==Item.getItemFromBlock(LOTRMod.oreCopper)
            ||stack.getItem()==Item.getItemFromBlock(LOTRMod.oreTin)||stack.getItem()==LOTRMod.mug;
    }
    public static boolean provision(ItemStack stack){
        if(stack==null||stack.getItem()==null)return false;
        Item item=stack.getItem();
        return item instanceof ItemFood||item==LOTRMod.mugWater;
    }
    public static boolean provision(net.minecraft.entity.player.EntityPlayer player,ItemStack stack){
        if(!provision(stack))return false;
        if(player==null||KOMEProgressionPermissions.has(player,KOMEProgressionPermissions.COOKING))return true;
        for(Object output:net.minecraft.item.crafting.FurnaceRecipes.smelting().getSmeltingList().values())if(output instanceof ItemStack&&((ItemStack)output).getItem()==stack.getItem())return false;
        return true;
    }
}
