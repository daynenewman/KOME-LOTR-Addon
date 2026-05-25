package kome.client.gui;

import kome.client.KOMEMinecraftClient;

import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiTerritory extends GuiScreen {
    private final String waypoint;
    private final String waypointName;
    private final String initialFaction;
    private final String initialRuler;
    private final String initialDisplayName;
    private final List<String> factions = new ArrayList<>();
    private int factionIndex;
    private GuiTextField rulerField;
    private GuiTextField displayNameField;

    public KOMEGuiTerritory(String waypoint, String waypointName, String faction, String ruler, String displayName) {
        this.waypoint = waypoint;
        this.waypointName = waypointName;
        this.initialFaction = faction == null ? "" : faction;
        this.initialRuler = ruler == null ? "" : ruler;
        this.initialDisplayName = displayName == null ? "" : displayName;
    }

    @Override
    public void initGui() {
        factions.clear();
        factions.add("none");
        factions.addAll(LOTRFaction.getPlayableAlignmentFactionNames());
        factionIndex = Math.max(0, factions.indexOf(initialFaction.isEmpty() ? "none" : initialFaction));

        int x = width / 2 - 130;
        int y = height / 2 - 80;
        rulerField = new GuiTextField(fontRendererObj, x, y + 70, 260, 20);
        rulerField.setText(initialRuler);
        displayNameField = new GuiTextField(fontRendererObj, x, y + 105, 260, 20);
        displayNameField.setText(initialDisplayName);

        buttonList.add(new GuiButton(0, x, y + 35, 25, 20, "<"));
        buttonList.add(new GuiButton(1, x + 30, y + 35, 200, 20, factionLabel()));
        buttonList.add(new GuiButton(2, x + 235, y + 35, 25, 20, ">"));
        buttonList.add(new GuiButton(3, x, y + 140, 80, 20, "Save"));
        buttonList.add(new GuiButton(4, x + 90, y + 140, 80, 20, "Clear"));
        buttonList.add(new GuiButton(5, x + 180, y + 140, 80, 20, "Load"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            factionIndex = (factionIndex + factions.size() - 1) % factions.size();
            refreshFactionButton();
        } else if (button.id == 1 || button.id == 2) {
            factionIndex = (factionIndex + 1) % factions.size();
            refreshFactionButton();
        } else if (button.id == 3) {
            KOMEMinecraftClient.sendChat("/territory set " + waypoint + " " + factions.get(factionIndex) + " " + noneIfBlank(rulerField.getText()) + suffix(displayNameField.getText()));
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == 4) {
            KOMEMinecraftClient.sendChat("/territory clear " + waypoint);
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == 5) {
            KOMEMinecraftClient.sendChat("/territory gui " + waypoint);
            KOMEMinecraftClient.closePlayerScreen();
        }
    }

    private void refreshFactionButton() {
        ((GuiButton) buttonList.get(1)).displayString = factionLabel();
    }

    private String factionLabel() {
        return "Faction: " + factions.get(factionIndex);
    }

    private static String noneIfBlank(String value) {
        value = value.trim();
        return value.isEmpty() ? "none" : value;
    }

    private static String suffix(String value) {
        value = value.trim();
        return value.isEmpty() ? "" : " " + value;
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (rulerField.textboxKeyTyped(c, key) || displayNameField.textboxKeyTyped(c, key)) {
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        rulerField.mouseClicked(mouseX, mouseY, button);
        displayNameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = width / 2 - 130;
        int y = height / 2 - 80;
        drawCenteredString(fontRendererObj, waypointName, width / 2, y, 16777215);
        drawString(fontRendererObj, "Ruling Player", x, y + 60, 10526880);
        drawString(fontRendererObj, "Display Name", x, y + 95, 10526880);
        rulerField.drawTextBox();
        displayNameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
