package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public class KOMEGuiTroopMove extends GuiScreen {
    private static final String[] FILTERS = new String[] {"all", "mounted", "ground"};
    private final String originTile;
    private final int offensivePop;
    private final int mountedPop;
    private final int groundPop;
    private GuiTextField destinationField;
    private GuiTextField populationField;
    private GuiTextField distanceField;
    private int filterIndex;

    public KOMEGuiTroopMove(String originTile, int offensivePop, int mountedPop, int groundPop) {
        this.originTile = originTile;
        this.offensivePop = offensivePop;
        this.mountedPop = mountedPop;
        this.groundPop = groundPop;
    }

    @Override
    public void initGui() {
        int x = width / 2 - 150;
        int y = height / 2 - 105;
        buttonList.clear();
        destinationField = new GuiTextField(fontRendererObj, x + 124, y + 58, 112, 18);
        destinationField.setText("");
        populationField = new GuiTextField(fontRendererObj, x + 124, y + 88, 70, 18);
        populationField.setText(String.valueOf(Math.min(25, Math.max(0, offensivePop))));
        distanceField = new GuiTextField(fontRendererObj, x + 124, y + 118, 70, 18);
        distanceField.setText("1");
        buttonList.add(new GuiButton(0, x + 36, y + 168, 95, 20, "Move"));
        buttonList.add(new GuiButton(1, x + 166, y + 168, 95, 20, "Cancel"));
        buttonList.add(new GuiButton(2, x + 124, y + 142, 112, 20, filterLabel()));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            String destination = destinationField.getText().trim();
            String population = populationField.getText().trim();
            String distance = distanceField.getText().trim();
            if (destination.length() > 0 && population.length() > 0 && distance.length() > 0) {
                KOMEMinecraftClient.sendChat("/troops move " + originTile + " " + destination + " " + population + " " + distance + " " + FILTERS[filterIndex]);
            }
            mc.displayGuiScreen(new LOTRGuiMap());
        } else if (button.id == 1) {
            mc.displayGuiScreen(new LOTRGuiMap());
        } else if (button.id == 2) {
            filterIndex = (filterIndex + 1) % FILTERS.length;
            button.displayString = filterLabel();
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (destinationField.textboxKeyTyped(c, key) || populationField.textboxKeyTyped(c, key) || distanceField.textboxKeyTyped(c, key)) {
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        destinationField.mouseClicked(mouseX, mouseY, button);
        populationField.mouseClicked(mouseX, mouseY, button);
        distanceField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = width / 2 - 150;
        int y = height / 2 - 105;
        drawRect(x, y, x + 300, y + 205, 0xE01F160E);
        drawRect(x + 8, y + 8, x + 292, y + 197, 0xFFE8D4A0);
        drawRect(x + 16, y + 36, x + 284, y + 38, 0xFF5A3019);
        drawCenteredString(fontRendererObj, "Move Troops from " + originTile, width / 2, y + 18, 0x2B160D);
        drawString(fontRendererObj, "Available: " + offensivePop + " pop", x + 28, y + 46, 0x2B160D);
        drawString(fontRendererObj, "Mounted " + mountedPop + " / Ground " + groundPop, x + 28, y + 156, 0x6F4A2A);
        drawString(fontRendererObj, "Destination tile", x + 28, y + 63, 0x70401C);
        drawString(fontRendererObj, "Population", x + 28, y + 93, 0x70401C);
        drawString(fontRendererObj, "Distance tiles", x + 28, y + 123, 0x70401C);
        drawString(fontRendererObj, "Move type", x + 28, y + 148, 0x70401C);
        destinationField.drawTextBox();
        populationField.drawTextBox();
        distanceField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String filterLabel() {
        return "Type: " + FILTERS[filterIndex];
    }
}
