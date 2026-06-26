package kome.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.List;

public final class KOMEGuiTheme {
    public static final int COLOR_SHADOW = 0xAA000000;
    public static final int COLOR_PARCHMENT = 0xFFE7D1A4;
    public static final int COLOR_PARCHMENT_LIGHT = 0xFFF2DFB4;
    public static final int COLOR_PARCHMENT_DARK = 0xFFD5B982;
    public static final int COLOR_PANEL_DARK = 0xFF1E1410;
    public static final int COLOR_PANEL_DARK_SOFT = 0xDD2A1B14;
    public static final int COLOR_BORDER_DARK = 0xFF2B1412;
    public static final int COLOR_BORDER_RED = 0xFF5A171A;
    public static final int COLOR_BORDER_RED_LIGHT = 0xFF8A2D31;
    public static final int COLOR_GOLD = 0xFFF1D27A;
    public static final int COLOR_GOLD_DARK = 0xFF9F7530;
    public static final int COLOR_TEXT = 0xFF2B1A0E;
    public static final int COLOR_TEXT_LIGHT = 0xFFEEDDB1;
    public static final int COLOR_TEXT_MUTED = 0xFF6B5436;
    public static final int COLOR_TEXT_DISABLED = 0xFF7A6A55;
    public static final int COLOR_GOOD = 0xFF3E6B2E;
    public static final int COLOR_WARN = 0xFF8A5A00;
    public static final int COLOR_BAD = 0xFF7B3434;

    public static final ResourceLocation PARCHMENT_TEXTURE = new ResourceLocation("kome", "textures/gui/parchment.png");

    private KOMEGuiTheme() {
    }

    public static void drawMainPanel(int x, int y, int width, int height) {
        drawRect(x - 4, y - 4, x + width + 4, y + height + 4, COLOR_SHADOW);
        drawBorderedRect(x, y, width, height, COLOR_BORDER_DARK, COLOR_PARCHMENT_DARK);
        drawBorderedRect(x + 3, y + 3, width - 6, height - 6, COLOR_BORDER_RED, COLOR_PARCHMENT);
        drawRect(x + 7, y + 7, x + width - 7, y + 9, 0x66FFFFFF);
        drawRect(x + 7, y + height - 9, x + width - 7, y + height - 7, 0x335A171A);
    }

    public static void drawSubPanel(int x, int y, int width, int height) {
        drawBorderedRect(x, y, width, height, 0xAA5A171A, COLOR_PARCHMENT_LIGHT);
        drawRect(x + 2, y + 2, x + width - 2, y + 4, 0x44FFFFFF);
    }

    public static void drawCard(int x, int y, int width, int height, boolean hovered) {
        int border = hovered ? COLOR_GOLD : COLOR_GOLD_DARK;
        int fill = hovered ? COLOR_PARCHMENT_LIGHT : COLOR_PARCHMENT_DARK;
        drawBorderedRect(x, y, width, height, border, fill);
        drawRect(x + 2, y + 2, x + width - 2, y + height - 2, hovered ? 0x22FFFFFF : 0x11FFFFFF);
    }

    public static void drawHeader(FontRenderer font, String title, int x, int y, int width) {
        drawRect(x, y, x + width, y + 22, COLOR_BORDER_DARK);
        drawRect(x + 1, y + 1, x + width - 1, y + 21, COLOR_BORDER_RED);
        drawRect(x + 3, y + 3, x + width - 3, y + 5, 0x44E8C46A);
        drawCenteredString(font, title, x + width / 2, y + 7, COLOR_GOLD);
    }

    public static void drawSectionTitle(FontRenderer font, String title, int x, int y, int width) {
        font.drawString(title, x, y, COLOR_BORDER_RED);
        drawDivider(x, y + 11, width);
    }

    public static void drawPlainText(FontRenderer font, String text, int x, int y) {
        font.drawString(text, x, y, COLOR_TEXT);
    }

    public static void drawMutedText(FontRenderer font, String text, int x, int y) {
        font.drawString(text, x, y, COLOR_TEXT_MUTED);
    }

    public static void drawTitleText(FontRenderer font, String text, int x, int y) {
        font.drawString(text, x, y, COLOR_BORDER_RED);
    }

    public static void drawValueText(FontRenderer font, String text, int x, int y) {
        font.drawString(text, x, y, COLOR_TEXT);
    }

    public static void drawCenteredPlainText(FontRenderer font, String text, int x, int y, int color) {
        drawCenteredString(font, text, x, y, color);
    }

    public static void drawDivider(int x, int y, int width) {
        drawRect(x, y, x + width, y + 1, 0x665A171A);
        drawRect(x, y + 1, x + width, y + 2, 0x44E8C46A);
    }

    public static void drawTooltip(FontRenderer font, List lines, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int tooltipWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = String.valueOf(lines.get(i));
            tooltipWidth = Math.max(tooltipWidth, font.getStringWidth(line));
        }
        int x = mouseX + 12;
        int y = mouseY - 12;
        int height = 8 + (lines.size() - 1) * 10;
        if (x + tooltipWidth + 8 > screenWidth) {
            x = mouseX - tooltipWidth - 12;
        }
        if (y + height + 8 > screenHeight) {
            y = screenHeight - height - 8;
        }
        drawBorderedRect(x - 4, y - 4, tooltipWidth + 8, height + 8, COLOR_BORDER_DARK, 0xF0F2DFB4);
        for (int i = 0; i < lines.size(); i++) {
            font.drawString(String.valueOf(lines.get(i)), x, y + i * 10, COLOR_TEXT);
        }
    }

    public static int drawWrappedText(FontRenderer font, String text, int x, int y, int width, int color) {
        if (text == null || text.length() == 0) {
            return y;
        }
        List lines = wrapText(font, text, width);
        for (int i = 0; i < lines.size(); i++) {
            font.drawString((String) lines.get(i), x, y + i * 10, color);
        }
        return y + lines.size() * 10;
    }

    public static void drawProgressBar(FontRenderer font, int x, int y, int width, int height, float progress, int fillColor, String label) {
        progress = clamp(progress, 0.0f, 1.0f);
        drawBorderedRect(x, y, width, height, COLOR_BORDER_DARK, 0xFF3A2A1B);
        int filled = Math.max(0, Math.min(width - 2, (int) ((width - 2) * progress)));
        if (filled > 0) {
            drawRect(x + 1, y + 1, x + 1 + filled, y + height - 1, fillColor);
            drawRect(x + 1, y + 1, x + 1 + filled, y + 3, 0x33FFFFFF);
        }
        if (label != null && label.length() > 0) {
            drawCenteredString(font, label, x + width / 2, y + (height - 8) / 2, COLOR_TEXT_LIGHT);
        }
    }

    public static void drawFactionBadge(FontRenderer font, String label, int x, int y, int width, int color) {
        int fill = 0xFF000000 | (color & 0x00FFFFFF);
        drawBorderedRect(x, y, width, 18, COLOR_BORDER_DARK, fill);
        drawRect(x + 2, y + 2, x + width - 2, y + 4, 0x33FFFFFF);
        drawCenteredString(font, trimToWidth(font, label, width - 8), x + width / 2, y + 5, COLOR_TEXT_LIGHT);
    }

    public static void drawIconSlot(int x, int y, int size, boolean hovered) {
        drawBorderedRect(x, y, size, size, hovered ? COLOR_GOLD : COLOR_BORDER_DARK, 0xFF3A281A);
        drawRect(x + 2, y + 2, x + size - 2, y + size - 2, hovered ? 0x22E8C46A : 0x22000000);
    }

    public static void enableScissor(Minecraft mc, int x, int y, int width, int height) {
        ScaledResolution scaledResolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = scaledResolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, (scaledResolution.getScaledHeight() - y - height) * scale, width * scale, height * scale);
    }

    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static String trimToWidth(FontRenderer font, String text, int width) {
        text = text == null ? "" : text;
        if (font.getStringWidth(text) <= width) {
            return text;
        }
        String suffix = "...";
        while (text.length() > 0 && font.getStringWidth(text + suffix) > width) {
            text = text.substring(0, text.length() - 1);
        }
        return text + suffix;
    }

    public static List wrapText(FontRenderer font, String text, int width) {
        text = text == null ? "" : text;
        return font.listFormattedStringToWidth(text, width);
    }

    static void drawBorderedRect(int x, int y, int width, int height, int border, int fill) {
        drawRect(x, y, x + width, y + height, border);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawCenteredString(FontRenderer font, String text, int x, int y, int color) {
        font.drawString(text, x - font.getStringWidth(text) / 2, y, color);
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        Gui.drawRect(left, top, right, bottom, color);
        GL11.glPopAttrib();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
