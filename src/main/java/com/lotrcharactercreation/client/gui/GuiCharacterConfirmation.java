package com.lotrcharactercreation.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearanceSelectionRules;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiCharacterConfirmation extends GuiScreen {

    private static final int CONFIRM_BUTTON_ID = 0;
    private static final int BACK_BUTTON_ID = 1;

    private final GuiScreen parent;
    private final PlayerRace race;
    private final PlayerSex sex;
    private final StartingFaction faction;
    private final String appearancePresetId;

    private boolean confirmationPending;

    public GuiCharacterConfirmation(GuiScreen parent, PlayerRace race, PlayerSex sex, StartingFaction faction,
        String appearancePresetId) {
        this.parent = parent;
        this.race = race;
        this.sex = sex;
        this.faction = faction;
        this.appearancePresetId = appearancePresetId;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerY = height / 2;
        buttonList.add(new GuiButton(BACK_BUTTON_ID, width / 2 - 102, centerY + 54, 100, 20, "Back"));
        buttonList.add(new GuiButton(CONFIRM_BUTTON_ID, width / 2 + 2, centerY + 54, 150, 20, "Confirm Character"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (confirmationPending || !button.enabled) {
            return;
        }

        if (button.id == CONFIRM_BUTTON_ID) {
            confirmationPending = true;
            setButtonsEnabled(false);
            ModNetwork.sendCharacterFinalization();
        } else if (button.id == BACK_BUTTON_ID) {
            goBack();
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Object buttonObject : buttonList) {
            ((GuiButton) buttonObject).enabled = enabled;
        }
    }

    private void goBack() {
        confirmationPending = true;
        setButtonsEnabled(false);
        ModNetwork.sendCharacterCreationBack(CharacterCreationStage.CONFIRMATION);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 && !confirmationPending) {
            goBack();
        } else if (keyCode != 1) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerY = height / 2;
        drawCenteredString(fontRendererObj, "CONFIRM YOUR CHARACTER", width / 2, centerY - 78, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Race: " + race.getDisplayName(), width / 2, centerY - 48, 0xD0D0D0);
        if (sex == PlayerSex.MALE || sex == PlayerSex.FEMALE) {
            drawCenteredString(fontRendererObj, "Sex: " + sex.getDisplayName(), width / 2, centerY - 32, 0xD0D0D0);
        }
        drawCenteredString(
            fontRendererObj,
            "Starting Faction: " + faction.getDisplayName(),
            width / 2,
            centerY - 16,
            0xD0D0D0);
        drawCenteredString(fontRendererObj, "Appearance: " + getAppearanceDisplayName(), width / 2, centerY, 0xD0D0D0);
        drawCenteredString(
            fontRendererObj,
            "Starting allegiance and travel are applied only after confirmation.",
            width / 2,
            centerY + 24,
            0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String getAppearanceDisplayName() {
        AppearancePreset preset = AppearancePresetRegistry.findById(appearancePresetId);
        if (preset == null) {
            return "Unknown";
        }
        if (preset.getSourceType() == AppearanceSourceType.MINECRAFT_ACCOUNT) {
            return "Minecraft Skin";
        }
        if (preset.getDisplayName() != null) {
            return preset.getDisplayName();
        }

        List<AppearancePreset> candidates = AppearanceSelectionRules.getCandidates(race, sex, faction);
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index)
                .getId()
                .equals(appearancePresetId)) {
                return "Option " + (index + 1) + " / " + candidates.size();
            }
        }
        return "Selected appearance";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
