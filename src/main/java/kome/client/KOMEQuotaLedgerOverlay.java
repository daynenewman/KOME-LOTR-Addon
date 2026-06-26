package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import kome.client.gui.KOMEGuiTheme;
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

    public static void reset() {
        lines = new ArrayList();
    }

    @SubscribeEvent
    public void onDrawChest(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChest) || lines.isEmpty() || !isLedgerInventory((GuiChest) event.gui)) {
            return;
        }
        drawLegacyLedger(event);
    }

    private void drawLegacyLedger(GuiScreenEvent.DrawScreenEvent.Post event) {
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        List drawLines = new ArrayList();
        drawLines.add(getLedgerTitle((GuiChest) event.gui));
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

    public static List getLinesSnapshot() {
        return new ArrayList(lines);
    }

    public static List getStructuredLines(String key) {
        List result = new ArrayList();
        for (Object object : lines) {
            String[] parts = String.valueOf(object).split("\t", -1);
            if (parts.length > 0 && key.equals(parts[0])) {
                result.add(parts);
            }
        }
        return result;
    }

    public static String part(String[] parts, int index) {
        return parts != null && index >= 0 && index < parts.length ? parts[index] : "";
    }

    public static String[] getStructuredLine(String key, String type) {
        for (Object object : lines) {
            String[] parts = String.valueOf(object).split("\t", -1);
            if (parts.length >= 2 && key.equals(parts[0]) && type.equals(parts[1])) {
                return parts;
            }
        }
        return null;
    }

    public static String getRelationSummary() {
        for (Object object : lines) {
            String[] parts = String.valueOf(object).split("\t", -1);
            if (parts.length >= 3 && "SUMMARY".equals(parts[0])) {
                return parts[1] + " -> " + parts[2];
            }
        }
        return "Alliance goods";
    }

    public static boolean canDepositGoods() {
        for (Object object : lines) {
            String[] parts = String.valueOf(object).split("\t", -1);
            if (parts.length >= 4 && "VIEWER".equals(parts[0])) {
                return "1".equals(parts[2]);
            }
        }
        return false;
    }

    public static String getViewerName() {
        for (Object object : lines) {
            String[] parts = String.valueOf(object).split("\t", -1);
            if (parts.length >= 2 && "VIEWER".equals(parts[0])) {
                String value = parts[1];
                return "Unknown viewer".equals(value) || "No pledged faction".equals(value) ? "" : value;
            }
        }
        return "";
    }

    public static boolean canClaimGoods() {
        for (Object object : lines) {
            String[] parts = String.valueOf(object).split("\t", -1);
            if (parts.length >= 3 && "CLAIM".equals(parts[0])) {
                return "1".equals(parts[2]);
            }
        }
        return false;
    }

    public static String getClaimText() {
        for (Object object : lines) {
            String[] parts = String.valueOf(object).split("\t", -1);
            if (parts.length >= 4 && "CLAIM".equals(parts[0])) {
                return canClaimGoods() ? parts[1] : parts[3] + ".";
            }
        }
        return "Only the receiving faction king can claim goods.";
    }

    public static String getSenderKey() {
        return getSummaryPart(3);
    }

    public static String getReceiverKey() {
        return getSummaryPart(4);
    }

    private static String getSummaryPart(int index) {
        for (Object object : lines) {
            String[] parts = String.valueOf(object).split("\t", -1);
            if (parts.length > index && "SUMMARY".equals(parts[0])) {
                return parts[index];
            }
        }
        return "";
    }

    private boolean isLedgerInventory(GuiChest chest) {
        String name = getLowerInventoryName(chest);
        return "Lord Offerings".equals(name);
    }

    private String getLedgerTitle(GuiChest chest) {
        return "Alliance Ledger".equals(getLowerInventoryName(chest)) ? "Alliance Ledger" : "Lord Ledger";
    }

    private String getLowerInventoryName(GuiChest chest) {
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
            return inventory == null ? "" : inventory.getInventoryName();
        } catch (Exception e) {
            return "";
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
