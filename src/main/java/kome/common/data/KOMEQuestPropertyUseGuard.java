package kome.common.data;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

/** Real quest property remains identifiable equipment, but is not a recipe/trade input. */
public final class KOMEQuestPropertyUseGuard {
    private KOMEQuestPropertyUseGuard(){}
    public static boolean bound(ItemStack stack){return KOMEKnightCommissionService.taggedProperty(stack)||KOMESerfKnightRecoveryService.isRecoveryTagged(stack)||KOMEKnightEspionage.bound(stack);}
    public static boolean boundRecipe(InventoryCrafting grid){
        if(grid==null)return false;for(int i=0;i<grid.getSizeInventory();i++)if(bound(grid.getStackInSlot(i)))return true;return false;
    }
    public static boolean boundRepair(net.minecraft.inventory.ContainerRepair container){
        if(!bound(container.getSlot(0).getStack())&&!bound(container.getSlot(1).getStack()))return false;
        container.getSlot(2).putStack(null);container.maximumCost=0;return true;
    }
    public static boolean boundNativeCrafting(lotr.common.inventory.LOTRContainerCraftingTable table){
        if(!boundRecipe(table.craftMatrix))return false;table.craftResult.setInventorySlotContents(0,null);return true;
    }
    public static boolean boundNativeAnvil(lotr.common.inventory.LOTRContainerAnvil anvil){
        for(int i=0;i<anvil.invInput.getSizeInventory();i++)if(bound(anvil.invInput.getStackInSlot(i))){
            anvil.invOutput.setInventorySlotContents(0,null);anvil.materialCost=anvil.reforgeCost=anvil.engraveOwnerCost=0;anvil.isSmithScrollCombine=false;return true;
        }return false;
    }
}
