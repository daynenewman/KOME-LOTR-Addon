package kome.common.item;

import net.minecraft.item.Item;

/** Hidden presentation-only item used by KOME's native-style visual marker renderer. */
public final class KOMEItemVisualIcon extends Item {
    public KOMEItemVisualIcon(String unlocalizedName, String textureName) {
        setUnlocalizedName("kome." + unlocalizedName);
        setTextureName("kome:" + textureName);
        setMaxStackSize(1);
    }
}
