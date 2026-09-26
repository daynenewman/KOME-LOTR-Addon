package kome.common.data;

import cpw.mods.fml.common.registry.GameRegistry;
import kome.common.item.KOMEItemVisualIcon;
import net.minecraft.item.Item;

/** Registered but unobtainable presentation items for native ItemStack-based marker rendering. */
public final class KOMEProgressionVisualItems {
    public static final Item RELATIONSHIP =
        new KOMEItemVisualIcon("progressionRelationship", "progression_relationship");
    public static final Item RULER =
        new KOMEItemVisualIcon("progressionRuler", "progression_ruler");

    private KOMEProgressionVisualItems() { }

    public static void register() {
        GameRegistry.registerItem(RELATIONSHIP, "progression_relationship");
        GameRegistry.registerItem(RULER, "progression_ruler");
    }
}
