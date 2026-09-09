package com.enovak.lotrmoremobs.client.config;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

/** Native Forge property editor for one or more KOME config categories. */
@SideOnly(Side.CLIENT)
public final class MumakilConfigCategoryGui extends GuiConfig {

    public MumakilConfigCategoryGui(
            GuiScreen parentScreen,
            String[] categoryNames,
            String categoryTitle
    ) {
        super(
                parentScreen,
                getCategoryElements(categoryNames),
                Main.MODID,
                false,
                false,
                "LOTR KOME Addon - " + categoryTitle
        );
    }

    private static List<IConfigElement> getCategoryElements(
            String[] categoryNames
    ) {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        if (categoryNames == null) {
            return elements;
        }

        for (String categoryName : categoryNames) {
            if (categoryName == null) {
                continue;
            }
            elements.addAll(new ConfigElement(
                    MumakilConfig.getConfiguration().getCategory(categoryName)
            ).getChildElements());
        }
        return elements;
    }
}
