package kome.common.item;

import net.minecraft.item.Item;

/** Sealed property has no recipes or trade value; replacement cannot manufacture usable silver. */
public final class KOMEItemStolenProperty extends Item {
    public KOMEItemStolenProperty() {
        setUnlocalizedName("kome.stolenProperty");
        setTextureName("minecraft:paper");
        setMaxStackSize(16);
    }
}
