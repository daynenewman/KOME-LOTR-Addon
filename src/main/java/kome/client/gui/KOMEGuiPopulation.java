package kome.client.gui;

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
        int x = width / 2 - 120;
        int y = height / 2 - 95;
        playerField = new GuiTextField(fontRendererObj, x, y + 20, 240, 20);
        playerField.setText(initialPlayer);
        amountField = new GuiTextField(fontRendererObj, x, y + 55, 80, 20);
        amountField.setText("25");
        buttonList.add(new GuiButton(0, x + 90, y + 55, 70, 20, "Load"));
        buttonList.add(new GuiButton(1, x, y + 135, 75, 20, "Set Off"));
        buttonList.add(new GuiButton(2, x + 82, y + 135, 75, 20, "Add Off"));
        buttonList.add(new GuiButton(3, x + 164, y + 135, 75, 20, "Remove Off"));
        buttonList.add(new GuiButton(4, x, y + 160, 75, 20, "Set Def"));
        buttonList.add(new GuiButton(5, x + 82, y + 160, 75, 20, "Add Def"));
        buttonList.add(new GuiButton(6, x + 164, y + 160, 75, 20, "Remove Def"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        String player = playerField.getText().trim();
        String amount = amountField.getText().trim();
        if (button.id == 0) {
            mc.thePlayer.sendChatMessage("/population gui " + player);
        } else if (button.id == 1) {
            mc.thePlayer.sendChatMessage("/population set " + player + " offensive " + amount);
        } else if (button.id == 2) {
            mc.thePlayer.sendChatMessage("/population add " + player + " offensive " + amount);
        } else if (button.id == 3) {
            mc.thePlayer.sendChatMessage("/population remove " + player + " offensive " + amount);
        } else if (button.id == 4) {
            mc.thePlayer.sendChatMessage("/population set " + player + " defensive " + amount);
        } else if (button.id == 5) {
            mc.thePlayer.sendChatMessage("/population add " + player + " defensive " + amount);
        } else if (button.id == 6) {
            mc.thePlayer.sendChatMessage("/population remove " + player + " defensive " + amount);
        }
        mc.thePlayer.closeScreen();
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
        int x = width / 2 - 120;
        int y = height / 2 - 95;
        drawCenteredString(fontRendererObj, "Population Manager", width / 2, y, 16777215);
        drawString(fontRendererObj, "Player", x, y + 10, 10526880);
        drawString(fontRendererObj, "Amount", x, y + 45, 10526880);
        playerField.drawTextBox();
        amountField.drawTextBox();
        drawString(fontRendererObj, "Offensive total: " + offensiveTotal + "   Defensive total: " + defensiveTotal, x, y + 85, 16777215);
        drawString(fontRendererObj, "Army population: " + armyUsed + "/" + armyTotal + " used   available " + Math.max(0, armyTotal - armyUsed), x, y + 100, 16777215);
        drawString(fontRendererObj, "Farmhands: " + farmhandsUsed + "/" + farmhandsLimit + " used  (total pop / 25)", x, y + 115, 16777215);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
