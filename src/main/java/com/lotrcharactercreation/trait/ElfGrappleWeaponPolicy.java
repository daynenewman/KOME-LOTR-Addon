package com.lotrcharactercreation.trait;

import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;

import lotr.common.item.LOTRItemDagger;

public final class ElfGrappleWeaponPolicy {

    private ElfGrappleWeaponPolicy() {}

    public static boolean isDagger(ItemStack itemStack) {
        return itemStack != null && itemStack.getItem() instanceof LOTRItemDagger;
    }

    public static boolean isBow(ItemStack itemStack) {
        return itemStack != null && itemStack.getItem() instanceof ItemBow;
    }

    public static boolean canStartGrapple(ItemStack itemStack) {
        return isDagger(itemStack) || isBow(itemStack);
    }
}
