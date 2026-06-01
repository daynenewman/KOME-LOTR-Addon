package kome.client.gui;

import kome.client.KOMEMinecraftClient;

import lotr.client.gui.LOTRGuiMenu;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public class KOMEGuiPopulation extends GuiScreen {
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 280;
    private static final int COLOR_PANEL = 0xFFE8D4A0;
    private static final int COLOR_PANEL_DARK = 0xFFC2AE80;
    private static final int COLOR_BORDER = 0xFF5A3019;
    private static final int COLOR_TEXT = 0xFF21150D;
    private static final int COLOR_MUTED = 0xFF6F4A2A;
    private static final int COLOR_OFFENSIVE = 0xFFE3B843;
    private static final int COLOR_DEFENSIVE = 0xFF6FB7E8;
    private static final int COLOR_FARMHAND = 0xFF8BCF63;
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
        int x = width / 2 - PANEL_WIDTH / 2;
        int y = height / 2 - PANEL_HEIGHT / 2;
        playerField = new GuiTextField(fontRendererObj, x + 28, y + 78, 180, 18);
        playerField.setText(initialPlayer);
        amountField = new GuiTextField(fontRendererObj, x + 28, y + 120, 80, 18);
        amountField.setText("25");
        buttonList.add(new GuiButton(0, x + 18, y + 16, 70, 20, "Menu"));
        buttonList.add(new GuiButton(7, x + PANEL_WIDTH - 90, y + 16, 72, 20, "Units"));
        buttonList.add(new GuiButton(2, x + 28, y + 166, 86, 20, "+ Off"));
        buttonList.add(new GuiButton(3, x + 122, y + 166, 86, 20, "- Off"));
        buttonList.add(new GuiButton(5, x + 28, y + 192, 86, 20, "+ Def"));
        buttonList.add(new GuiButton(6, x + 122, y + 192, 86, 20, "- Def"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        String player = playerField.getText().trim();
        String amount = amountField.getText().trim();
        if (button.id == 0) {
            mc.displayGuiScreen(new LOTRGuiMenu());
            return;
        } else if (button.id == 2) {
            KOMEMinecraftClient.sendChat("/population add " + player + " offensive " + amount);
        } else if (button.id == 3) {
            KOMEMinecraftClient.sendChat("/population remove " + player + " offensive " + amount);
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
        int x = width / 2 - PANEL_WIDTH / 2;
        int y = height / 2 - PANEL_HEIGHT / 2;
        int offensiveAvailable = Math.max(0, offensiveTotal - offensiveUsed);
        int defensiveAvailable = Math.max(0, defensiveTotal - defensiveUsed);
        int armyAvailable = Math.max(0, armyTotal - armyUsed);
        drawPanel(x, y);
        drawCenteredString(fontRendererObj, "KOME Population", width / 2, y + 22, COLOR_TEXT);
        drawString(fontRendererObj, "Manager", x + 98, y + 22, COLOR_MUTED);

        drawString(fontRendererObj, "Player", x + 28, y + 66, COLOR_MUTED);
        drawString(fontRendererObj, "Amount", x + 28, y + 108, COLOR_MUTED);
        playerField.drawTextBox();
        amountField.drawTextBox();

        drawString(fontRendererObj, "Adjust totals", x + 28, y + 150, COLOR_MUTED);
        drawStatCard(x + 240, y + 62, 190, 48, "Offensive", offensiveUsed, offensiveTotal, offensiveAvailable, COLOR_OFFENSIVE);
        drawStatCard(x + 240, y + 120, 190, 48, "Defensive", defensiveUsed, defensiveTotal, defensiveAvailable, COLOR_DEFENSIVE);
        drawStatCard(x + 240, y + 178, 190, 48, "Total army", armyUsed, armyTotal, armyAvailable, 0xFFFFFFFF);
        drawFarmhandCard(x + 28, y + 226, 402);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel(int x, int y) {
        drawRect(x - 6, y - 6, x + PANEL_WIDTH + 6, y + PANEL_HEIGHT + 6, 0xCC000000);
        drawRect(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, COLOR_PANEL);
        drawRect(x + 8, y + 8, x + PANEL_WIDTH - 8, y + 10, COLOR_BORDER);
        drawRect(x + 8, y + 48, x + PANEL_WIDTH - 8, y + 52, COLOR_BORDER);
        drawRect(x + 18, y + 58, x + 222, y + 220, COLOR_PANEL_DARK);
        drawRect(x + 232, y + 58, x + PANEL_WIDTH - 18, y + 236, COLOR_PANEL_DARK);
    }

    private void drawStatCard(int x, int y, int width, int height, String label, int used, int total, int available, int color) {
        drawRect(x, y, x + width, y + height, 0xFF8A5A31);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFE0C58A);
        drawString(fontRendererObj, label, x + 8, y + 7, COLOR_TEXT);
        drawString(fontRendererObj, used + "/" + total + " used", x + 8, y + 20, 0xFFFFFFFF);
        drawString(fontRendererObj, available + " available", x + width - 80, y + 20, COLOR_MUTED);
        drawProgressBar(x + 8, y + height - 12, width - 16, 5, used, total, color);
    }

    private void drawFarmhandCard(int x, int y, int width) {
        drawRect(x, y, x + width, y + 34, 0xFF8A5A31);
        drawRect(x + 1, y + 1, x + width - 1, y + 33, 0xFFE0C58A);
        drawString(fontRendererObj, "Farmhands", x + 8, y + 8, COLOR_TEXT);
        drawString(fontRendererObj, farmhandsUsed + "/" + farmhandsLimit + " used", x + 92, y + 8, COLOR_MUTED);
        drawProgressBar(x + 8, y + 23, width - 16, 5, farmhandsUsed, farmhandsLimit, COLOR_FARMHAND);
    }

    private void drawProgressBar(int x, int y, int width, int height, int used, int total, int color) {
        drawRect(x, y, x + width, y + height, 0xFF3B2A18);
        int filled = total <= 0 ? 0 : Math.min(width, Math.max(0, used * width / total));
        if (filled > 0) {
            drawRect(x, y, x + filled, y + height, color);
        }
    }
}
