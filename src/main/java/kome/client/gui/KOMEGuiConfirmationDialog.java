package kome.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/** Modal destructive confirmation used by alliance breaks and allied-tile consequences. */
public final class KOMEGuiConfirmationDialog {
    public static final int NONE = 0;
    public static final int CONFIRM = 1;
    public static final int CANCEL = 2;

    private boolean visible;
    private String title = "Confirm";
    private String message = "";
    private String confirmLabel = "Confirm";
    private String cancelLabel = "Cancel";
    private int x;
    private int y;
    private int width;
    private int height;

    public void show(String title, String message, String confirmLabel) {
        this.title = safe(title);
        this.message = safe(message);
        this.confirmLabel = safe(confirmLabel);
        visible = true;
    }

    public void hide() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void draw(FontRenderer font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;
        Gui.drawRect(0, 0, screenWidth, screenHeight, 0xB8000000);
        width = Math.min(420, Math.max(250, screenWidth - 48));
        int textWidth = width - 36;
        int bannerHeight = KOMEGuiTheme.warningBannerHeight(font, message, width - 36);
        height = Math.max(144, 96 + bannerHeight);
        height = Math.min(height, screenHeight - 32);
        x = (screenWidth - width) / 2;
        y = (screenHeight - height) / 2;
        KOMEGuiTheme.drawMainPanel(x, y, width, height);
        KOMEGuiTheme.drawHeader(font, title, x + 16, y + 14, width - 32);
        KOMEGuiTheme.drawWarningBanner(font, "Consequences", message, x + 18, y + 48, width - 36,
            KOMEGuiTheme.Status.DENIED);
        drawDialogButton(font, confirmX(), buttonY(), buttonWidth(), confirmLabel,
            KOMEGuiTheme.isHovered(mouseX, mouseY, confirmX(), buttonY(), buttonWidth(), 24), true);
        drawDialogButton(font, cancelX(), buttonY(), buttonWidth(), cancelLabel,
            KOMEGuiTheme.isHovered(mouseX, mouseY, cancelX(), buttonY(), buttonWidth(), 24), false);
    }

    public int click(int mouseX, int mouseY, int mouseButton) {
        if (!visible || mouseButton != 0) return NONE;
        if (KOMEGuiTheme.isHovered(mouseX, mouseY, confirmX(), buttonY(), buttonWidth(), 24)) return CONFIRM;
        if (KOMEGuiTheme.isHovered(mouseX, mouseY, cancelX(), buttonY(), buttonWidth(), 24)) return CANCEL;
        return NONE;
    }

    private void drawDialogButton(FontRenderer font, int x, int y, int width, String label, boolean hover, boolean danger) {
        int border = danger ? KOMEGuiTheme.COLOR_BAD : hover ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_GOLD_DARK;
        int fill = danger ? (hover ? 0xFF6E2427 : 0xFF431C1E)
            : hover ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK;
        KOMEGuiTheme.drawBorderedRect(x, y, width, 24, border, fill);
        KOMEGuiTheme.drawCenteredPlainText(font, KOMEGuiTheme.trimToWidth(font, label, width - 8),
            x + width / 2, y + 8, KOMEGuiTheme.COLOR_TEXT_LIGHT);
    }

    private int buttonWidth() { return Math.max(88, (width - 54) / 2); }
    private int confirmX() { return x + 18; }
    private int cancelX() { return x + width - 18 - buttonWidth(); }
    private int buttonY() { return y + height - 38; }
    private static String safe(String value) { return value == null ? "" : value; }
}
