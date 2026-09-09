package com.enovak.lotrmoremobs.client.config;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Collections;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

@SideOnly(Side.CLIENT)
public final class MumakilConfigGuiFactory
        implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraft) {
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return MumakilConfigGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement>
    runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(
            RuntimeOptionCategoryElement element
    ) {
        return null;
    }
}
