package com.lotrcharactercreation.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearanceSelectionRules;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.client.appearance.ClientLocalAppearancePresetCatalog;
import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.common.fac.LOTRFaction;

@SideOnly(Side.CLIENT)
public class GuiCharacterConfirmation extends GuiScreen {

    private static final int CONFIRM_BUTTON_ID = 0;
    private static final int BACK_BUTTON_ID = 1;

    private final GuiScreen parent;
    private final PlayerRace race;
    private final PlayerSex sex;
    private final StartingFaction faction;
    private final String appearancePresetId;
    private final String currentPledgeCode;
    private final LOTRFaction currentPledge;
    private final boolean pledgeReplacementRequired;

    private boolean confirmationPending;

    public GuiCharacterConfirmation(GuiScreen parent, PlayerRace race, PlayerSex sex, StartingFaction faction,
        String appearancePresetId, String currentPledgeCode, boolean automaticStartingAllegiance) {
        this.parent = parent;
        this.race = race;
        this.sex = sex;
        this.faction = faction;
        this.appearancePresetId = appearancePresetId;
        this.currentPledgeCode = currentPledgeCode == null ? "" : currentPledgeCode;
        this.currentPledge = LOTRFaction.forName(this.currentPledgeCode);
        this.pledgeReplacementRequired = automaticStartingAllegiance && currentPledge != null
            && currentPledge != faction.getLotrFaction();
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerY = height / 2;
        if (pledgeReplacementRequired) {
            buttonList.add(new GuiButton(BACK_BUTTON_ID, width / 2 - 128, centerY + 88, 100, 20, "Back"));
            buttonList
                .add(new GuiButton(CONFIRM_BUTTON_ID, width / 2 - 24, centerY + 88, 154, 20, "Break Pledge & Confirm"));
        } else {
            buttonList.add(new GuiButton(BACK_BUTTON_ID, width / 2 - 102, centerY + 54, 100, 20, "Back"));
            buttonList
                .add(new GuiButton(CONFIRM_BUTTON_ID, width / 2 + 2, centerY + 54, 150, 20, "Confirm Character"));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (confirmationPending || !button.enabled) {
            return;
        }

        if (button.id == CONFIRM_BUTTON_ID) {
            confirmationPending = true;
            setButtonsEnabled(false);
            ModNetwork.sendCharacterFinalization(pledgeReplacementRequired, currentPledgeCode);
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
        if (pledgeReplacementRequired) {
            drawPledgeReplacementWarning(centerY);
        } else {
            drawCenteredString(
                fontRendererObj,
                "Starting allegiance and travel are applied only after confirmation.",
                width / 2,
                centerY + 24,
                0xA0A0A0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPledgeReplacementWarning(int centerY) {
        drawCenteredString(fontRendererObj, "PLEDGE REPLACEMENT WARNING", width / 2, centerY + 18, 0xFFB060);
        String warning;
        if (faction == StartingFaction.WANDERER) {
            warning = "You are currently pledged to " + currentPledge.factionName()
                + ". Choosing Wanderer will break your " + currentPledge.factionName()
                + " pledge and leave you unpledged.";
        } else {
            warning = "You are currently pledged to " + currentPledge.factionName() + ". Choosing "
                + faction.getDisplayName() + " will break your " + currentPledge.factionName()
                + " pledge and replace it with " + faction.getDisplayName()
                + ". Normal pledge-departure cleanup will run and conflicting positive alignment may be reduced.";
        }

        List<String> warningLines = fontRendererObj.listFormattedStringToWidth(warning, Math.min(430, width - 36));
        int lineY = centerY + 32;
        for (String warningLine : warningLines) {
            drawCenteredString(fontRendererObj, warningLine, width / 2, lineY, 0xFFD0A0);
            lineY += fontRendererObj.FONT_HEIGHT;
        }
    }

    private String getAppearanceDisplayName() {
        AppearancePreset preset = ClientLocalAppearancePresetCatalog.get().findById(appearancePresetId);
        if (preset == null) {
            return "Unknown";
        }
        if (preset.getSourceType() == AppearanceSourceType.MINECRAFT_ACCOUNT) {
            return "Minecraft Skin";
        }
        if (preset.getDisplayName() != null) {
            return preset.getDisplayName();
        }

        List<AppearancePreset> candidates = AppearanceSelectionRules.getCandidates(
            ClientLocalAppearancePresetCatalog.get(), race, sex, faction);
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
