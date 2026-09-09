package com.enovak.lotrmoremobs.client.config;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

/**
 * Clean one-click hub for the addon's focused config categories.
 *
 * The stock Forge nested-category entry requires list-entry selection before
 * activation in some 1.7.10 GUI paths. Normal GuiButtons make category
 * navigation respond to the first click while each category still uses the
 * native Forge property editor underneath.
 */
@SideOnly(Side.CLIENT)
public final class MumakilConfigGui extends GuiScreen {

    private static final int BUTTON_WIDTH = 210;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int DONE_BUTTON_ID = 9000;

    private final GuiScreen parentScreen;
    private final List<CategoryEntry> categories =
            new ArrayList<CategoryEntry>();

    public MumakilConfigGui(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
        categories.add(new CategoryEntry(
                MumakilConfig.CATEGORY_MUMAKIL,
                "config.lotrmoremobs.category.mumakil"
        ));
        categories.add(new CategoryEntry(
                MumakilConfig.CATEGORY_PICKUP_FILTER,
                "config.lotrmoremobs.category.itemPickupFilter"
        ));
        categories.add(new CategoryEntry(
                MumakilConfig.CATEGORY_MORTAL_GANDALF,
                "config.lotrmoremobs.category.mortalGandalf"
        ));
        categories.add(new CategoryEntry(
                new String[] {
                        MumakilConfig.CATEGORY_SIEGE_GATES,
                        MumakilConfig.CATEGORY_BATTLE_RAMS
                },
                "config.lotrmoremobs.category.siege"
        ));
        categories.add(new CategoryEntry(
                MumakilConfig.CATEGORY_PLAYER_ANIMATIONS,
                "config.lotrmoremobs.category.playerAnimations"
        ));
        categories.add(new CategoryEntry(
                MumakilConfig.CATEGORY_SERVER_GAMEPLAY,
                "config.lotrmoremobs.category.serverGameplay"
        ));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();

        int totalCategoryHeight = categories.size() * BUTTON_HEIGHT
                + (categories.size() - 1) * BUTTON_GAP;
        int startY = Math.max(42, (height - totalCategoryHeight - 42) / 2);
        int x = (width - BUTTON_WIDTH) / 2;

        for (int i = 0; i < categories.size(); ++i) {
            CategoryEntry entry = categories.get(i);
            buttonList.add(new GuiButton(
                    i,
                    x,
                    startY + i * (BUTTON_HEIGHT + BUTTON_GAP),
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    StatCollector.translateToLocal(entry.languageKey)
            ));
        }

        buttonList.add(new GuiButton(
                DONE_BUTTON_ID,
                x,
                height - 28,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                StatCollector.translateToLocal("gui.done")
        ));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }

        if (button.id == DONE_BUTTON_ID) {
            mc.displayGuiScreen(parentScreen);
            return;
        }

        if (button.id >= 0 && button.id < categories.size()) {
            CategoryEntry entry = categories.get(button.id);
            mc.displayGuiScreen(new MumakilConfigCategoryGui(
                    this,
                    entry.categoryNames,
                    StatCollector.translateToLocal(entry.languageKey)
            ));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
                fontRendererObj,
                "LOTR KOME Addon",
                width / 2,
                16,
                0xFFFFFF
        );
        drawCenteredString(
                fontRendererObj,
                "Settings marked restart-required apply after restarting Minecraft.",
                width / 2,
                28,
                0xA0A0A0
        );
        super.drawScreen(mouseX, mouseY, partialTicks);
    }


    private static final class CategoryEntry {
        private final String[] categoryNames;
        private final String languageKey;

        private CategoryEntry(String categoryName, String languageKey) {
            this(new String[] { categoryName }, languageKey);
        }

        private CategoryEntry(String[] categoryNames, String languageKey) {
            this.categoryNames = categoryNames;
            this.languageKey = languageKey;
        }
    }
}
