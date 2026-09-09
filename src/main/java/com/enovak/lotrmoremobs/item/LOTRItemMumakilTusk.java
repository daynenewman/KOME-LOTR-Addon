package com.enovak.lotrmoremobs.item;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Field;
import lotr.common.LOTRCreativeTabs;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;

public class LOTRItemMumakilTusk extends Item {
    private static final String TUSK_ICON_NAME =
            Main.MODID + ":mumakil_tusk";

    private static final LOTRCreativeTabs LOTR_MATERIALS_CREATIVE_TAB =
            resolveLOTRMaterialsCreativeTab();

    private static LOTRCreativeTabs resolveLOTRMaterialsCreativeTab() {
        try {
            Field field =
                    LOTRCreativeTabs.class.getDeclaredField("tabMaterials");
            Object value = field.get(null);

            if (!(value instanceof LOTRCreativeTabs)) {
                throw new IllegalStateException(
                        "LOTRCreativeTabs.tabMaterials did not resolve "
                                + "to a LOTRCreativeTabs instance"
                );
            }

            return (LOTRCreativeTabs)value;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(
                    "Unable to resolve LOTRCreativeTabs.tabMaterials",
                    e
            );
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Unable to access LOTRCreativeTabs.tabMaterials",
                    e
            );
        }
    }

    public LOTRItemMumakilTusk() {
        this.setMaxStackSize(16);

        if (MumakilConfig.enableMumakil) {
            this.setCreativeTab(LOTR_MATERIALS_CREATIVE_TAB);
        }

        this.setUnlocalizedName("mumakil_tusk");
        this.setTextureName(TUSK_ICON_NAME);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        System.out.println(
                "[LOTRMoreMobs] Registering Mumakil tusk item icon: "
                        + TUSK_ICON_NAME
        );
        this.itemIcon =
                iconRegister.registerIcon(TUSK_ICON_NAME);
    }
}