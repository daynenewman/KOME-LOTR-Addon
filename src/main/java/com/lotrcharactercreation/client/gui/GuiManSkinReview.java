package com.lotrcharactercreation.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.client.render.ManSkinReviewPreviewRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiManSkinReview extends GuiScreen {

    private static final int PREVIOUS_TEXTURE_BUTTON_ID = 0;
    private static final int NEXT_TEXTURE_BUTTON_ID = 1;
    private static final int PREVIOUS_POOL_BUTTON_ID = 2;
    private static final int NEXT_POOL_BUTTON_ID = 3;
    private static final int CLOSE_BUTTON_ID = 4;

    private static final ReviewPool[] REVIEW_POOLS = {
        single("Dúnedain of the North", "Generic Dúnedain", PlayerSex.FEMALE, "lotr:mob/ranger/ranger_female/1.png"),
        numbered("Dale", "Dale Soldier / Levyman", PlayerSex.MALE, "lotr:mob/dale/dale_soldier/", 3),
        single("Dunland", "Berserker", PlayerSex.MALE, "lotr:mob/dunland/berserker/0.png"),
        numbered("Gondor", "Gondor Soldier", PlayerSex.MALE, "lotr:mob/gondor/gondorSoldier/", 6),
        numbered("Gondor", "Ithilien Ranger", PlayerSex.MALE, "lotr:mob/gondor/ranger/", 3),
        numbered("Gondor", "Swan Knight / Dol Amroth", PlayerSex.MALE, "lotr:mob/gondor/swanKnight/", 3),
        numbered("Gondor", "Gondor-origin Harad Slave", PlayerSex.MALE, "lotr:mob/nearHarad/slave/gondor_male/", 2),
        numbered("Mordor", "Nurn Slave / Farmhand", PlayerSex.MALE, "lotr:mob/nurn/slave_male/", 4),
        numbered("Mordor", "Nurn Slave / Farmhand", PlayerSex.FEMALE, "lotr:mob/nurn/slave_female/", 3),
        single(
            "Morwaith",
            "Morwaith-origin Harad Slave",
            PlayerSex.MALE,
            "lotr:mob/nearHarad/slave/morwaith_male/0.png"),
        single("Near Harad", "Near Haradrim Warrior", PlayerSex.MALE, "lotr:mob/nearHarad/warrior/0.png"),
        numbered("Near Harad", "Harnedor / Gulf Warrior", PlayerSex.MALE, "lotr:mob/nearHarad/harnedorWarrior/", 5),
        single("Near Harad", "Near Harad Warlord", PlayerSex.MALE, "lotr:mob/nearHarad/warlord.png"),
        numbered(
            "Near Harad",
            "Near-Harad-origin Harad Slave",
            PlayerSex.MALE,
            "lotr:mob/nearHarad/slave/nearHarad_male/",
            2),
        numbered("Rohan", "Rohirrim Warrior", PlayerSex.MALE, "lotr:mob/rohan/warrior/", 6),
        numbered("Rohan", "Shieldmaiden", PlayerSex.FEMALE, "lotr:mob/rohan/shieldmaiden/", 3),
        single(
            "Taurethrim",
            "Taurethrim-origin Harad Slave",
            PlayerSex.MALE,
            "lotr:mob/nearHarad/slave/taurethrim_male/0.png") };

    private final ManSkinReviewPreviewRenderer previewRenderer = new ManSkinReviewPreviewRenderer();

    private int poolIndex;
    private int textureIndex;

    @Override
    public void initGui() {
        buttonList.clear();
        int centerY = height / 2;
        buttonList.add(new GuiButton(PREVIOUS_TEXTURE_BUTTON_ID, width / 2 - 154, centerY + 74, 70, 20, "Previous"));
        buttonList.add(new GuiButton(NEXT_TEXTURE_BUTTON_ID, width / 2 + 84, centerY + 74, 70, 20, "Next"));
        buttonList.add(new GuiButton(PREVIOUS_POOL_BUTTON_ID, width / 2 - 154, centerY + 98, 100, 20, "Previous Pool"));
        buttonList.add(new GuiButton(CLOSE_BUTTON_ID, width / 2 - 35, centerY + 98, 70, 20, "Close"));
        buttonList.add(new GuiButton(NEXT_POOL_BUTTON_ID, width / 2 + 54, centerY + 98, 100, 20, "Next Pool"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        ReviewPool pool = getCurrentPool();
        if (button.id == PREVIOUS_TEXTURE_BUTTON_ID) {
            textureIndex = wrap(textureIndex - 1, pool.texturePaths.length);
        } else if (button.id == NEXT_TEXTURE_BUTTON_ID) {
            textureIndex = wrap(textureIndex + 1, pool.texturePaths.length);
        } else if (button.id == PREVIOUS_POOL_BUTTON_ID) {
            poolIndex = wrap(poolIndex - 1, REVIEW_POOLS.length);
            textureIndex = 0;
        } else if (button.id == NEXT_POOL_BUTTON_ID) {
            poolIndex = wrap(poolIndex + 1, REVIEW_POOLS.length);
            textureIndex = 0;
        } else if (button.id == CLOSE_BUTTON_ID) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(null);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerY = height / 2;
        ReviewPool pool = getCurrentPool();
        String texturePath = pool.texturePaths[textureIndex];

        drawCenteredString(fontRendererObj, "MAN SKIN VISUAL REVIEW", width / 2, centerY - 125, 0xFFFFFF);
        drawCenteredString(
            fontRendererObj,
            "Pool " + (poolIndex + 1) + " / " + REVIEW_POOLS.length,
            width / 2,
            centerY - 113,
            0xA0A0A0);
        drawCenteredString(fontRendererObj, pool.faction, width / 2, centerY - 99, 0xFFFFFF);
        drawCenteredString(fontRendererObj, pool.source, width / 2, centerY - 88, 0xD0D0D0);
        drawCenteredString(fontRendererObj, pool.sex.getDisplayName(), width / 2, centerY - 77, 0xD0D0D0);

        drawRect(width / 2 - 104, centerY - 64, width / 2 + 104, centerY + 48, 0xA0000000);
        previewRenderer.draw(
            mc.thePlayer,
            pool.sex,
            new ResourceLocation(texturePath),
            width / 2,
            centerY + 35,
            45,
            mouseX - width / 2,
            mouseY - centerY,
            partialTicks);

        drawCenteredString(
            fontRendererObj,
            textureIndex + 1 + " / " + pool.texturePaths.length,
            width / 2,
            centerY + 52,
            0xFFFFFF);
        drawCenteredString(fontRendererObj, texturePath, width / 2, centerY + 63, 0xB0B0B0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private ReviewPool getCurrentPool() {
        return REVIEW_POOLS[poolIndex];
    }

    private static int wrap(int value, int size) {
        return (value % size + size) % size;
    }

    private static ReviewPool numbered(String faction, String source, PlayerSex sex, String directory, int count) {
        String[] texturePaths = new String[count];
        for (int index = 0; index < count; index++) {
            texturePaths[index] = directory + index + ".png";
        }
        return new ReviewPool(faction, source, sex, texturePaths);
    }

    private static ReviewPool single(String faction, String source, PlayerSex sex, String texturePath) {
        return new ReviewPool(faction, source, sex, new String[] { texturePath });
    }

    private static final class ReviewPool {

        private final String faction;
        private final String source;
        private final PlayerSex sex;
        private final String[] texturePaths;

        private ReviewPool(String faction, String source, PlayerSex sex, String[] texturePaths) {
            this.faction = faction;
            this.source = source;
            this.sex = sex;
            this.texturePaths = texturePaths;
        }
    }
}
