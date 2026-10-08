package kome.common.item;

import net.minecraft.item.Item;

/** Sealed property has no recipes or trade value; replacement cannot manufacture usable silver. */
public final class KOMEItemStolenProperty extends Item {
    private net.minecraft.item.ItemStack contents(net.minecraft.item.ItemStack stack){
        if(stack==null||!stack.hasTagCompound())return null;
        net.minecraft.nbt.NBTTagCompound tag=stack.getTagCompound().getCompoundTag("KOMEStolenProperty");
        Item item=(Item)Item.itemRegistry.getObject(tag.getString("Contents"));
        return item==null||item==this?null:new net.minecraft.item.ItemStack(item,1,tag.getInteger("Damage"));
    }
    @Override public String getItemStackDisplayName(net.minecraft.item.ItemStack stack){
        net.minecraft.item.ItemStack item=contents(stack);return item==null?super.getItemStackDisplayName(stack):"Recovered "+item.getDisplayName();
    }
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    @Override public net.minecraft.util.IIcon getIcon(net.minecraft.item.ItemStack stack,int pass){
        net.minecraft.item.ItemStack item=contents(stack);return item==null?super.getIcon(stack,pass):item.getItem().getIcon(item,pass);
    }
    public KOMEItemStolenProperty() {
        setUnlocalizedName("kome.stolenProperty");
        setTextureName("minecraft:paper");
        setMaxStackSize(16);
    }
}
