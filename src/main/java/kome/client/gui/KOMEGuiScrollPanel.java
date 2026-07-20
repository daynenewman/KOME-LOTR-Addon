package kome.client.gui;

import net.minecraft.client.Minecraft;

/** Reusable scaled scissor viewport with clamped wheel scrolling and a proportional scrollbar. */
public final class KOMEGuiScrollPanel {
    private int x;
    private int y;
    private int width;
    private int height;
    private int contentHeight;
    private int scroll;

    public KOMEGuiScrollPanel layout(int x, int y, int width, int height, int contentHeight) {
        this.x = x;
        this.y = y;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.contentHeight = Math.max(0, contentHeight);
        this.scroll = clamp(scroll);
        return this;
    }

    public void begin(Minecraft minecraft) {
        KOMEGuiTheme.enableScissor(minecraft, x, y, width, height);
    }

    public void end() {
        KOMEGuiTheme.disableScissor();
    }

    public boolean wheel(int mouseX, int mouseY, int wheelDelta, int step) {
        if (wheelDelta == 0 || !contains(mouseX, mouseY)) return false;
        setScroll(scroll + (wheelDelta < 0 ? Math.max(1, step) : -Math.max(1, step)));
        return true;
    }

    public void drawScrollbar() {
        int max = getMaxScroll();
        if (max <= 0) return;
        int trackX = x + width - 6;
        KOMEGuiTheme.drawBorderedRect(trackX, y, 5, height, KOMEGuiTheme.COLOR_BORDER_DARK, 0xAA15120F);
        int handleHeight = Math.max(18, height * height / Math.max(height, contentHeight));
        int handleY = y + (height - handleHeight) * scroll / max;
        KOMEGuiTheme.drawBorderedRect(trackX, handleY, 5, handleHeight,
            KOMEGuiTheme.COLOR_GOLD_DARK, KOMEGuiTheme.COLOR_GOLD);
    }

    public boolean contains(int mouseX, int mouseY) {
        return KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, height);
    }

    public int getScroll() {
        return scroll;
    }

    public void setScroll(int value) {
        scroll = clamp(value);
    }

    public int getMaxScroll() {
        return Math.max(0, contentHeight - height);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(getMaxScroll(), value));
    }
}
