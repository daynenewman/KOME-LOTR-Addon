package com.enovak.lotrmoremobs.materials;


import lotr.common.LOTRMod;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.EnumHelper;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.List;
import lotr.common.LOTRMod;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.ToolMaterial;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraftforge.common.util.EnumHelper;
public class AddonMaterial {
    private static float[] protectionBase = new float[]{0.14F, 0.4F, 0.32F, 0.14F};
    private static float maxProtection = 25.0F;
    public static List<AddonMaterial> allLOTRMaterials = new ArrayList();
    public static AddonMaterial LEGENDARY = (new AddonMaterial("LEGENDARY")).setUndamageable().setUses(10000).setDamage(5.0F).setProtection(0.8F).setHarvestLevel(4).setSpeed(9.0F).setEnchantability(8);

    private String materialName;
    private boolean undamageable = false;
    private int uses;
    private float damage;
    private int[] protection;
    private int harvestLevel;
    private float speed;
    private int enchantability;
    private boolean canHarvestManFlesh = false;
    private Item.ToolMaterial toolMaterial;
    private ItemArmor.ArmorMaterial armorMaterial;

    public static void setCraftingItems() {
        LEGENDARY.setCraftingItem( Items.iron_ingot );
    }

    private AddonMaterial(String name) {
        this.materialName = "LOTR_" + name;
    }

    private AddonMaterial setUndamageable() {
        this.undamageable = true;
        return this;
    }

    public boolean isDamageable() {
        return !this.undamageable;
    }

    private AddonMaterial setUses(int i) {
        this.uses = i;
        return this;
    }

    private AddonMaterial setDamage(float f) {
        this.damage = f;
        return this;
    }

    private AddonMaterial setProtection(float f) {
        this.protection = new int[protectionBase.length];

        for(int i = 0; i < this.protection.length; ++i) {
            this.protection[i] = Math.round(protectionBase[i] * f * maxProtection);
        }

        return this;
    }

    private AddonMaterial setHarvestLevel(int i) {
        this.harvestLevel = i;
        return this;
    }

    private AddonMaterial setSpeed(float f) {
        this.speed = f;
        return this;
    }

    private AddonMaterial setEnchantability(int i) {
        this.enchantability = i;
        return this;
    }

    private AddonMaterial setManFlesh() {
        this.canHarvestManFlesh = true;
        return this;
    }

    public boolean canHarvestManFlesh() {
        return this.canHarvestManFlesh;
    }

    public Item.ToolMaterial toToolMaterial() {
        if (this.toolMaterial == null) {
            this.toolMaterial = EnumHelper.addToolMaterial(this.materialName, this.harvestLevel, this.uses, this.speed, this.damage, this.enchantability);
        }

        return this.toolMaterial;
    }

    public ItemArmor.ArmorMaterial toArmorMaterial() {
        if (this.armorMaterial == null) {
            this.armorMaterial = EnumHelper.addArmorMaterial(this.materialName, Math.round((float)this.uses * 0.06F), this.protection, this.enchantability);
        }

        return this.armorMaterial;
    }

    private void setCraftingItem(Item item) {
        this.setCraftingItems(item, item);
    }

    private void setCraftingItems(Item toolItem, Item armorItem) {
        this.toToolMaterial().setRepairItem(new ItemStack(toolItem));
        this.toArmorMaterial().customCraftingMaterial = armorItem;
    }

    public static Item.ToolMaterial getToolMaterialByName(String name) {
        return Item.ToolMaterial.valueOf(name);
    }

    public static ItemArmor.ArmorMaterial getArmorMaterialByName(String name) {
        return ItemArmor.ArmorMaterial.valueOf(name);
    }
}