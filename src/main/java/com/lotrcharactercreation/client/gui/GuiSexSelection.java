package com.lotrcharactercreation.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiSexSelection extends GuiScreen {

    private static final int MALE_BUTTON_ID = 0;
    private static final int FEMALE_BUTTON_ID = 1;
    private static final int BACK_BUTTON_ID = 2;

    private final GuiScreen parent;
    private final PlayerRace race;
    private final PlayerSex currentSex;
    private final boolean mandatoryFlow;

    private PlayerSex pendingSex;
    private boolean navigationPending;

    public GuiSexSelection(PlayerRace race, PlayerSex currentSex) {
        this(null, race, currentSex, false);
    }

    public GuiSexSelection(GuiScreen parent, PlayerRace race, PlayerSex currentSex) {
        this(parent, race, currentSex, false);
    }

    public GuiSexSelection(GuiScreen parent, PlayerRace race, PlayerSex currentSex, boolean mandatoryFlow) {
        this.parent = parent;
        this.race = race;
        this.currentSex = currentSex;
        this.mandatoryFlow = mandatoryFlow;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerY = height / 2;
        buttonList.add(
            new GuiButton(
                MALE_BUTTON_ID,
                width / 2 - 102,
                centerY - 10,
                100,
                20,
                currentSex == PlayerSex.MALE ? "Male (current)" : "Male"));
        buttonList.add(
            new GuiButton(
                FEMALE_BUTTON_ID,
                width / 2 + 2,
                centerY - 10,
                100,
                20,
                currentSex == PlayerSex.FEMALE ? "Female (current)" : "Female"));
        buttonList.add(new GuiButton(BACK_BUTTON_ID, width / 2 - 50, centerY + 24, 100, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled || pendingSex != null || navigationPending) {
            return;
        }

        if (button.id == MALE_BUTTON_ID || button.id == FEMALE_BUTTON_ID) {
            pendingSex = button.id == MALE_BUTTON_ID ? PlayerSex.MALE : PlayerSex.FEMALE;
            setButtonsEnabled(false);
            ModNetwork.sendSexSelection(pendingSex);
        } else if (button.id == BACK_BUTTON_ID) {
            goBack();
        }
    }

    public void handleSelectionResult(boolean accepted, String serializedSexId) {
        PlayerSex acceptedSex = PlayerSex.findBySerializedId(serializedSexId);
        if (accepted && pendingSex != null && acceptedSex == pendingSex) {
            mc.displayGuiScreen(parent);
            return;
        }

        pendingSex = null;
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Object buttonObject : buttonList) {
            ((GuiButton) buttonObject).enabled = enabled;
        }
    }

    private void goBack() {
        if (mandatoryFlow) {
            navigationPending = true;
            setButtonsEnabled(false);
            ModNetwork.sendCharacterCreationBack(CharacterCreationStage.SEX);
        } else {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 && pendingSex == null && !navigationPending) {
            goBack();
        } else if (keyCode != 1) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerY = height / 2;
        drawCenteredString(fontRendererObj, "LOTR CHARACTER CREATION", width / 2, centerY - 64, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Choose Your Sex", width / 2, centerY - 46, 0xE0E0E0);
        drawCenteredString(fontRendererObj, "Race: " + race.getDisplayName(), width / 2, centerY - 30, 0xB0B0B0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
