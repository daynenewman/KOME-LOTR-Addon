package kome.client.gui;

import kome.client.KOMEMinecraftClient;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public class KOMEGuiPopulation extends GuiScreen {
    private final String initialPlayer;
    private final int offensiveTotal;
    private final int offensiveUsed;
    private final int defensiveTotal;
    private final int defensiveUsed;
    private final int farmhandsUsed;
    private final int farmhandsLimit;
    private final int armyUsed;
    private final int armyTotal;
    private GuiTextField playerField;
    private GuiTextField amountField;

    public KOMEGuiPopulation(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandsUsed, int farmhandsLimit, int armyUsed, int armyTotal) {
        this.initialPlayer = playerName;
        this.offensiveTotal = offensiveTotal;
        this.offensiveUsed = offensiveUsed;
        this.defensiveTotal = defensiveTotal;
        this.defensiveUsed = defensiveUsed;
        this.farmhandsUsed = farmhandsUsed;
        this.farmhandsLimit = farmhandsLimit;
        this.armyUsed = armyUsed;
        this.armyTotal = armyTotal;
    }

    @Override
    public void initGui() {
        int x = width / 2 - 135;
        int y = height / 2 - 115;
        playerField = new GuiTextField(fontRendererObj, x, y + 20, 270, 20);
        playerField.setText(initialPlayer);
        amountField = new GuiTextField(fontRendererObj, x, y + 55, 90, 20);
        amountField.setText("25");
        buttonList.add(new GuiButton(0, x + 100, y + 55, 75, 20, "Load"));
        int buttonWidth = 86;
        int buttonGap = 6;
        buttonList.add(new GuiButton(1, x, y + 155, buttonWidth, 20, "Set Off"));
        buttonList.add(new GuiButton(2, x + buttonWidth + buttonGap, y + 155, buttonWidth, 20, "Add Off"));
        buttonList.add(new GuiButton(3, x + (buttonWidth + buttonGap) * 2, y + 155, buttonWidth, 20, "Remove Off"));
        buttonList.add(new GuiButton(4, x, y + 180, buttonWidth, 20, "Set Def"));
        buttonList.add(new GuiButton(5, x + buttonWidth + buttonGap, y + 180, buttonWidth, 20, "Add Def"));
        buttonList.add(new GuiButton(6, x + (buttonWidth + buttonGap) * 2, y + 180, buttonWidth, 20, "Remove Def"));
        buttonList.add(new GuiButton(7, x, y + 205, 270, 20, "Units"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        String player = playerField.getText().trim();
        String amount = amountField.getText().trim();
        if (button.id == 0) {
            KOMEMinecraftClient.sendChat("/population gui " + player);
        } else if (button.id == 1) {
            KOMEMinecraftClient.sendChat("/population set " + player + " offensive " + amount);
        } else if (button.id == 2) {
            KOMEMinecraftClient.sendChat("/population add " + player + " offensive " + amount);
        } else if (button.id == 3) {
            KOMEMinecraftClient.sendChat("/population remove " + player + " offensive " + amount);
        } else if (button.id == 4) {
            KOMEMinecraftClient.sendChat("/population set " + player + " defensive " + amount);
        } else if (button.id == 5) {
            KOMEMinecraftClient.sendChat("/population add " + player + " defensive " + amount);
        } else if (button.id == 6) {
            KOMEMinecraftClient.sendChat("/population remove " + player + " defensive " + amount);
        } else if (button.id == 7) {
            KOMEMinecraftClient.sendChat("/population units " + player);
        }
        KOMEMinecraftClient.closePlayerScreen();
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (playerField.textboxKeyTyped(c, key) || amountField.textboxKeyTyped(c, key)) {
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        playerField.mouseClicked(mouseX, mouseY, button);
        amountField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = width / 2 - 135;
        int y = height / 2 - 115;
        int offensiveAvailable = Math.max(0, offensiveTotal - offensiveUsed);
        int defensiveAvailable = Math.max(0, defensiveTotal - defensiveUsed);
        int armyAvailable = Math.max(0, armyTotal - armyUsed);
        drawCenteredString(fontRendererObj, "Population Manager", width / 2, y, 16777215);
        drawString(fontRendererObj, "Player", x, y + 10, 10526880);
        drawString(fontRendererObj, "Amount", x, y + 45, 10526880);
        playerField.drawTextBox();
        amountField.drawTextBox();
        drawString(fontRendererObj, "Offensive: " + offensiveUsed + "/" + offensiveTotal + " used, " + offensiveAvailable + " available", x, y + 85, 0xF5D27A);
        drawString(fontRendererObj, "Defensive: " + defensiveUsed + "/" + defensiveTotal + " used, " + defensiveAvailable + " available", x, y + 100, 0x9ED6FF);
        drawString(fontRendererObj, "Total army: " + armyUsed + "/" + armyTotal + " used, " + armyAvailable + " available", x, y + 115, 16777215);
        drawString(fontRendererObj, "Farmhands: " + farmhandsUsed + "/" + farmhandsLimit + " used", x, y + 130, 0xD7FF9E);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
