package com.lotrcharactercreation.client.gui;

import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearanceSelectionRules;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.client.appearance.ClientLocalAppearancePresetCatalog;
import com.lotrcharactercreation.client.render.AppearancePreviewRenderer;
import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiAppearanceSelection extends GuiScreen {

    private static final int PREVIOUS_BUTTON_ID = 0;
    private static final int NEXT_BUTTON_ID = 1;
    private static final int RANDOMIZE_BUTTON_ID = 2;
    private static final int CONFIRM_BUTTON_ID = 3;
    private static final int BACK_BUTTON_ID = 4;

    private final GuiScreen parent;
    private final boolean mandatoryFlow;
    private final PlayerRace race;
    private final PlayerSex sex;
    private final StartingFaction faction;
    private final List<AppearancePreset> candidates;
    private final AppearancePreviewRenderer previewRenderer = new AppearancePreviewRenderer();
    private final Random random = new Random();

    private int selectedIndex;
    private boolean selectionPending;
    private GuiButton previousButton;
    private GuiButton nextButton;
    private GuiButton randomizeButton;
    private GuiButton confirmButton;
    private GuiButton backButton;

    public GuiAppearanceSelection(PlayerRace race, PlayerSex sex, StartingFaction faction, String currentPresetId) {
        this(null, race, sex, faction, currentPresetId, false);
    }

    public GuiAppearanceSelection(GuiScreen parent, PlayerRace race, PlayerSex sex, StartingFaction faction,
        String currentPresetId) {
        this(parent, race, sex, faction, currentPresetId, false);
    }

    public GuiAppearanceSelection(GuiScreen parent, PlayerRace race, PlayerSex sex, StartingFaction faction,
        String currentPresetId, boolean mandatoryFlow) {
        this.parent = parent;
        this.mandatoryFlow = mandatoryFlow;
        this.race = race;
        this.sex = sex;
        this.faction = faction;
        candidates = AppearanceSelectionRules.getCandidates(
            ClientLocalAppearancePresetCatalog.get(), race, sex, faction);
        selectedIndex = findPresetIndex(currentPresetId);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerY = height / 2;
        previousButton = new GuiButton(PREVIOUS_BUTTON_ID, width / 2 - 154, centerY + 72, 70, 20, "Previous");
        randomizeButton = new GuiButton(RANDOMIZE_BUTTON_ID, width / 2 - 75, centerY + 72, 150, 20, "Randomize");
        nextButton = new GuiButton(NEXT_BUTTON_ID, width / 2 + 84, centerY + 72, 70, 20, "Next");
        backButton = new GuiButton(BACK_BUTTON_ID, width / 2 - 154, centerY + 96, 70, 20, "Back");
        confirmButton = new GuiButton(CONFIRM_BUTTON_ID, width / 2 - 75, centerY + 96, 229, 20, "Confirm Appearance");
        buttonList.add(previousButton);
        buttonList.add(randomizeButton);
        buttonList.add(nextButton);
        buttonList.add(backButton);
        buttonList.add(confirmButton);
        updateButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (selectionPending || !button.enabled) {
            return;
        }

        if (button.id == PREVIOUS_BUTTON_ID) {
            selectedIndex = (selectedIndex - 1 + candidates.size()) % candidates.size();
        } else if (button.id == NEXT_BUTTON_ID) {
            selectedIndex = (selectedIndex + 1) % candidates.size();
        } else if (button.id == RANDOMIZE_BUTTON_ID) {
            randomizeSelection();
        } else if (button.id == CONFIRM_BUTTON_ID) {
            AppearancePreset selected = getSelectedPreset();
            if (selected != null) {
                selectionPending = true;
                ModNetwork.sendAppearanceSelection(selected.getId());
            }
        } else if (button.id == BACK_BUTTON_ID) {
            goBack();
        }
        updateButtons();
    }

    public void handleSelectionResult(boolean accepted, String presetId) {
        AppearancePreset selected = getSelectedPreset();
        if (accepted && selected != null
            && selected.getId()
                .equals(presetId)) {
            mc.displayGuiScreen(parent);
            return;
        }

        selectionPending = false;
        updateButtons();
    }

    private void randomizeSelection() {
        if (candidates.size() > 1) {
            selectedIndex = (selectedIndex + 1 + random.nextInt(candidates.size() - 1)) % candidates.size();
        }
    }

    private void updateButtons() {
        boolean hasSelection = !candidates.isEmpty();
        boolean canCycle = hasSelection && !selectionPending && candidates.size() > 1;
        previousButton.enabled = canCycle;
        nextButton.enabled = canCycle;
        randomizeButton.enabled = canCycle;
        confirmButton.enabled = hasSelection && !selectionPending;
        backButton.enabled = !selectionPending;
    }

    private AppearancePreset getSelectedPreset() {
        return candidates.isEmpty() ? null : candidates.get(selectedIndex);
    }

    private void goBack() {
        if (mandatoryFlow) {
            selectionPending = true;
            updateButtons();
            ModNetwork.sendCharacterCreationBack(CharacterCreationStage.APPEARANCE);
        } else {
            mc.displayGuiScreen(parent);
        }
    }

    private int findPresetIndex(String presetId) {
        if (presetId != null) {
            for (int index = 0; index < candidates.size(); index++) {
                if (candidates.get(index)
                    .getId()
                    .equals(presetId)) {
                    return index;
                }
            }
        }
        return 0;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 && !selectionPending) {
            goBack();
        } else if (keyCode != 1) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerY = height / 2;
        drawCenteredString(fontRendererObj, "CHOOSE APPEARANCE", width / 2, centerY - 112, 0xFFFFFF);

        int contextY = centerY - 96;
        drawCenteredString(fontRendererObj, "Race: " + race.getDisplayName(), width / 2, contextY, 0xD0D0D0);
        contextY += 11;
        if (sex == PlayerSex.MALE || sex == PlayerSex.FEMALE) {
            drawCenteredString(fontRendererObj, "Sex: " + sex.getDisplayName(), width / 2, contextY, 0xD0D0D0);
            contextY += 11;
        }
        drawCenteredString(
            fontRendererObj,
            "Starting faction: " + faction.getDisplayName(),
            width / 2,
            contextY,
            0xD0D0D0);
        String groupDisplayName = AppearanceSelectionRules.getGroupDisplayName(race, faction);
        if (groupDisplayName != null) {
            contextY += 11;
            drawCenteredString(fontRendererObj, "Culture: " + groupDisplayName, width / 2, contextY, 0xD0D0D0);
        }

        drawRect(width / 2 - 104, centerY - 50, width / 2 + 104, centerY + 64, 0xA0000000);
        AppearancePreset selected = getSelectedPreset();
        if (selected == null) {
            drawCenteredString(fontRendererObj, "No valid appearances available.", width / 2, centerY, 0xFF8080);
        } else {
            previewRenderer.draw(
                mc.thePlayer,
                selected,
                width / 2,
                centerY + 42,
                46,
                mouseX - width / 2,
                mouseY - centerY,
                partialTicks);
            int indexY = centerY + 52;
            if (selected.getDisplayName() != null) {
                drawCenteredString(fontRendererObj, selected.getDisplayName(), width / 2, indexY, 0xFFFFFF);
                indexY += 11;
            }
            drawCenteredString(
                fontRendererObj,
                selectedIndex + 1 + " / " + candidates.size(),
                width / 2,
                indexY,
                0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
