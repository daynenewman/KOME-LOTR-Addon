package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.IInventory;
import net.minecraftforge.client.event.GuiScreenEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class KOMEQuotaLedgerOverlay {
    private static List lines = new ArrayList();
    private static Field lowerChestField;

    public static void update(List newLines) {
        lines = newLines == null ? new ArrayList() : new ArrayList(newLines);
    }

    @SubscribeEvent
    public void onDrawChest(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChest) || lines.isEmpty() || !isLordOfferings((GuiChest) event.gui)) {
            return;
        }
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        List drawLines = new ArrayList();
        drawLines.add("Lord Ledger");

        int panelWidth = Math.min(220, Math.max(150, getPanelWidth(font, lines) + 12));
        int textWidth = panelWidth - 10;
        for (Object line : lines) {
            addWrappedLine(font, drawLines, String.valueOf(line), textWidth);
        }
        int panelHeight = drawLines.size() * 10 + 12;
        int chestLeft = event.gui.width / 2 - 88;
        int x = Math.max(4, chestLeft - panelWidth - 8);
        int y = Math.max(8, event.gui.height / 2 - 104);

        Gui.drawRect(x, y, x + panelWidth, y + panelHeight, 0xDD1B1710);
        Gui.drawRect(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, 0xCC3A2B18);
        for (int i = 0; i < drawLines.size(); i++) {
            int color = i == 0 ? 0xFFE8C46A : (String.valueOf(drawLines.get(i)).contains(" complete") ? 0xFF8CFF8C : 0xFFE8E0C8);
            font.drawString(String.valueOf(drawLines.get(i)), x + 5, y + 6 + i * 10, color);
        }
    }

    private boolean isLordOfferings(GuiChest chest) {
        try {
            if (lowerChestField == null) {
                try {
                    lowerChestField = GuiChest.class.getDeclaredField("lowerChestInventory");
                } catch (NoSuchFieldException e) {
                    lowerChestField = GuiChest.class.getDeclaredField("field_147015_w");
                }
                lowerChestField.setAccessible(true);
            }
            IInventory inventory = (IInventory) lowerChestField.get(chest);
            return inventory != null && "Lord Offerings".equals(inventory.getInventoryName());
        } catch (Exception e) {
            return false;
        }
    }

    private static int getPanelWidth(FontRenderer font, List values) {
        int width = 0;
        for (Object line : values) {
            width = Math.max(width, font.getStringWidth(String.valueOf(line)));
        }
        return width + 12;
    }

    private static void addWrappedLine(FontRenderer font, List target, String text, int width) {
        if (font.getStringWidth(text) <= width) {
            target.add(text);
            return;
        }
        List wrapped = font.listFormattedStringToWidth(text, width);
        for (Object line : wrapped) {
            target.add(String.valueOf(line));
        }
    }
}
