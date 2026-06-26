package kome.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.opengl.GL11;

public class KOMEGuiButton extends GuiButton {
    public static final int HEIGHT_SMALL = 20;
    public static final int HEIGHT = 24;
    public static final int HEIGHT_ACTION = 28;
    public static final int WIDTH_SMALL = 52;
    public static final int WIDTH_NORMAL = 96;
    public static final int WIDTH_WIDE = 144;

    private final boolean darkFill;
    private boolean selected;

    public KOMEGuiButton(int id, int x, int y, int width, String text) {
        this(id, x, y, width, HEIGHT, text, false);
    }

    public KOMEGuiButton(int id, int x, int y, int width, int height, String text) {
        this(id, x, y, width, height, text, false);
    }

    public KOMEGuiButton(int id, int x, int y, int width, int height, String text, boolean darkFill) {
        super(id, x, y, width, height, text);
        this.darkFill = darkFill;
    }

    public static KOMEGuiButton small(int id, int x, int y, String text) {
        return new KOMEGuiButton(id, x, y, WIDTH_SMALL, HEIGHT_SMALL, text);
    }

    public static KOMEGuiButton normal(int id, int x, int y, String text) {
        return new KOMEGuiButton(id, x, y, WIDTH_NORMAL, HEIGHT, text);
    }

    public static KOMEGuiButton wide(int id, int x, int y, String text) {
        return new KOMEGuiButton(id, x, y, WIDTH_WIDE, HEIGHT, text);
    }

    public static KOMEGuiButton dark(int id, int x, int y, int width, String text) {
        return new KOMEGuiButton(id, x, y, width, HEIGHT, text, true);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        FontRenderer font = mc.fontRenderer;
        field_146123_n = KOMEGuiTheme.isHovered(mouseX, mouseY, xPosition, yPosition, width, height);

        int border = selected ? KOMEGuiTheme.COLOR_GOLD : enabled ? (field_146123_n ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_BORDER_RED) : 0xFF8B7354;
        int fill;
        if (selected) {
            fill = darkFill ? KOMEGuiTheme.COLOR_BORDER_RED : KOMEGuiTheme.COLOR_PARCHMENT_LIGHT;
        } else if (!enabled) {
            fill = darkFill ? 0xFF6A4C42 : 0xFFC5AD7F;
        } else if (darkFill) {
            fill = field_146123_n ? KOMEGuiTheme.COLOR_BORDER_RED : KOMEGuiTheme.COLOR_PANEL_DARK;
        } else {
            fill = field_146123_n ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK;
        }
        int textColor = selected ? (darkFill ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_BORDER_RED) : enabled ? (field_146123_n ? (darkFill ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_BORDER_RED) : (darkFill ? KOMEGuiTheme.COLOR_TEXT_LIGHT : KOMEGuiTheme.COLOR_TEXT)) : (darkFill ? 0xFFE2D0AA : KOMEGuiTheme.COLOR_TEXT_DISABLED);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        KOMEGuiTheme.drawBorderedRect(xPosition, yPosition, width, height, border, fill);
        if (enabled && field_146123_n) {
            KOMEGuiTheme.drawBorderedRect(xPosition + 2, yPosition + 2, width - 4, height - 4, 0x33E8C46A, fill);
        } else {
            org.lwjgl.opengl.GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
        mouseDragged(mc, mouseX, mouseY);
        String text = KOMEGuiTheme.trimToWidth(font, displayString, width - 8);
        font.drawString(text, xPosition + width / 2 - font.getStringWidth(text) / 2, yPosition + (height - font.FONT_HEIGHT) / 2 + 1, textColor);
        GL11.glPopAttrib();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public KOMEGuiButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }
}
