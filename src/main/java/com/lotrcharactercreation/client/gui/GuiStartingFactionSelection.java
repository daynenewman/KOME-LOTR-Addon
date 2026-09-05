package com.lotrcharactercreation.client.gui;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiStartingFactionSelection extends GuiScreen {

    private static final int PREVIOUS_BUTTON_ID = 0;
    private static final int NEXT_BUTTON_ID = 1;
    private static final int CHOOSE_BUTTON_ID = 2;
    private static final int BACK_BUTTON_ID = 3;
    private static final int BANNER_SOURCE_WIDTH = 32;
    private static final int BANNER_SOURCE_HEIGHT = 64;
    private static final int BANNER_RENDER_WIDTH = 48;
    private static final int BANNER_RENDER_HEIGHT = 96;
    private static final int BANNER_TEXTURE_SIZE = 128;

    private final GuiScreen parent;
    private final boolean mandatoryFlow;
    private final List<StartingFaction> factions;
    private int factionIndex;
    private boolean selectionPending;
    private GuiButton previousButton;
    private GuiButton nextButton;
    private GuiButton chooseButton;
    private GuiButton backButton;

    public GuiStartingFactionSelection(PlayerRace race) {
        this(null, race, null, false);
    }

    public GuiStartingFactionSelection(GuiScreen parent, PlayerRace race, StartingFaction currentFaction) {
        this(parent, race, currentFaction, false);
    }

    public GuiStartingFactionSelection(GuiScreen parent, PlayerRace race, StartingFaction currentFaction,
        boolean mandatoryFlow) {
        this.parent = parent;
        this.mandatoryFlow = mandatoryFlow;
        factions = StartingFaction.getAllowedForRace(race);
        int currentIndex = factions.indexOf(currentFaction);
        factionIndex = currentIndex < 0 ? 0 : currentIndex;
    }

    @Override
    public void initGui() {
        buttonList.clear();

        previousButton = new GuiButton(PREVIOUS_BUTTON_ID, width / 2 - 145, height / 2 - 42, 32, 20, "<");
        nextButton = new GuiButton(NEXT_BUTTON_ID, width / 2 + 113, height / 2 - 42, 32, 20, ">");
        chooseButton = new GuiButton(CHOOSE_BUTTON_ID, width / 2 - 80, height / 2 + 72, 160, 20, "");
        buttonList.add(previousButton);
        buttonList.add(nextButton);
        buttonList.add(chooseButton);
        if (mandatoryFlow || parent != null) {
            backButton = new GuiButton(BACK_BUTTON_ID, width / 2 - 145, height / 2 + 72, 60, 20, "Back");
            buttonList.add(backButton);
        }
        updateButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (selectionPending || !button.enabled) {
            return;
        }

        if (button.id == PREVIOUS_BUTTON_ID) {
            factionIndex = (factionIndex - 1 + factions.size()) % factions.size();
            updateButtons();
        } else if (button.id == NEXT_BUTTON_ID) {
            factionIndex = (factionIndex + 1) % factions.size();
            updateButtons();
        } else if (button.id == CHOOSE_BUTTON_ID) {
            selectionPending = true;
            updateButtons();
            ModNetwork.sendStartingFactionSelection(getSelectedFaction());
        } else if (button.id == BACK_BUTTON_ID) {
            goBack();
        }
    }

    private void updateButtons() {
        boolean canCycle = !selectionPending && factions.size() > 1;
        previousButton.enabled = canCycle;
        nextButton.enabled = canCycle;
        chooseButton.enabled = !selectionPending;
        chooseButton.displayString = "Choose " + getSelectedFaction().getDisplayName();
        if (backButton != null) {
            backButton.enabled = !selectionPending;
        }
    }

    private StartingFaction getSelectedFaction() {
        return factions.get(factionIndex);
    }

    private void goBack() {
        if (mandatoryFlow) {
            selectionPending = true;
            updateButtons();
            ModNetwork.sendCharacterCreationBack(CharacterCreationStage.FACTION);
        } else {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 && !selectionPending && (mandatoryFlow || parent != null)) {
            goBack();
        } else if (keyCode != 1) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "CHOOSE YOUR FACTION", width / 2, height / 2 - 108, 0xFFFFFF);

        int cardLeft = width / 2 - 104;
        int cardTop = height / 2 - 96;
        drawRect(cardLeft, cardTop, cardLeft + 208, height / 2 + 66, 0xA0000000);

        StartingFaction faction = getSelectedFaction();
        drawFactionVisual(faction, width / 2, height / 2 - 88);
        drawCenteredString(fontRendererObj, faction.getDisplayName(), width / 2, height / 2 + 13, 0xFFFFFF);

        List<String> descriptionLines = fontRendererObj.listFormattedStringToWidth(faction.getDescription(), 188);
        int descriptionY = height / 2 + 29;
        for (String line : descriptionLines) {
            drawCenteredString(fontRendererObj, line, width / 2, descriptionY, 0xBFBFBF);
            descriptionY += 10;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawFactionVisual(StartingFaction faction, int centerX, int y) {
        if (faction.getVisualResource() == null) {
            drawCenteredString(fontRendererObj, "WANDERER", centerX, y + BANNER_RENDER_HEIGHT / 2 - 4, 0xD0D0D0);
            return;
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager()
            .bindTexture(new ResourceLocation(faction.getVisualResource()));
        Gui.func_152125_a(
            centerX - BANNER_RENDER_WIDTH / 2,
            y,
            0.0F,
            0.0F,
            BANNER_SOURCE_WIDTH,
            BANNER_SOURCE_HEIGHT,
            BANNER_RENDER_WIDTH,
            BANNER_RENDER_HEIGHT,
            BANNER_TEXTURE_SIZE,
            BANNER_TEXTURE_SIZE);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
