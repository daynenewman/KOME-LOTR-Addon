package com.enovak.lotrmoremobs.item;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import java.lang.reflect.Field;
import lotr.common.LOTRCreativeTabs;
import net.minecraft.item.ItemFood;

/**
 * MUMAKIL_SHANK_SYSTEM_V1_1
 *
 * Held movement effects are managed by MumakilShankHeldEffectHandler.
 */
public class LOTRItemMumakilShank extends ItemFood {
    private static final String RAW_TEXTURE =
            Main.MODID + ":mumak_shank";
    private static final String COOKED_TEXTURE =
            Main.MODID + ":mumak_shank_cooked";
    private static final LOTRCreativeTabs LOTR_FOOD_CREATIVE_TAB =
            resolveLOTRFoodCreativeTab();

    private static LOTRCreativeTabs resolveLOTRFoodCreativeTab() {
        try {
            Field field = LOTRCreativeTabs.class.getDeclaredField("tabFood");
            Object value = field.get(null);
            if (!(value instanceof LOTRCreativeTabs)) {
                throw new IllegalStateException(
                        "LOTRCreativeTabs.tabFood did not resolve to a LOTRCreativeTabs instance"
                );
            }
            return (LOTRCreativeTabs)value;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(
                    "Unable to resolve LOTRCreativeTabs.tabFood",
                    e
            );
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Unable to access LOTRCreativeTabs.tabFood",
                    e
            );
        }
    }

    public LOTRItemMumakilShank(boolean cooked) {
        super(
                cooked ? 8 : 3,
                cooked ? 0.8F : 0.3F,
                true
        );

        this.setUnlocalizedName(
                cooked
                        ? "cooked_mumakil_shank"
                        : "mumakil_shank"
        );
        if (MumakilConfig.enableMumakil) {
            this.setCreativeTab(LOTR_FOOD_CREATIVE_TAB);
        }
        this.setTextureName(
                cooked ? COOKED_TEXTURE : RAW_TEXTURE
        );
    }
}
