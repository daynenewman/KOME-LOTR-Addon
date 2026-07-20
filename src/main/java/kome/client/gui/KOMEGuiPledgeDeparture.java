package kome.client.gui;

import kome.common.data.KOMEAlliance;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketPledgeDepartureData;
import kome.common.network.KOMEPacketPledgeDepartureRequest;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

public class KOMEGuiPledgeDeparture extends GuiScreen {
    private final KOMEPacketPledgeDepartureData data;
    private final GuiScreen parent;
    private int x, y, w, h;
    private int scroll;
    private final KOMEGuiScrollPanel contentPanel = new KOMEGuiScrollPanel();

    public KOMEGuiPledgeDeparture(KOMEPacketPledgeDepartureData data) { this(data, null); }

    public KOMEGuiPledgeDeparture(KOMEPacketPledgeDepartureData data, GuiScreen parent) {
        this.data = data;
        this.parent = parent;
    }

    @Override
    public void initGui() {
        w = Math.min(540, width - 24);
        h = Math.min(360, height - 24);
        x = (width - w) / 2;
        y = (height - h) / 2;
        buttonList.clear();
        buttonList.add(new KOMEGuiButton(1, x + 18, y + h - 34, 100, 22, "Back"));
        buttonList.add(new KOMEGuiButton(2, x + w - 118, y + h - 34, 100, 22, "Refresh"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) mc.displayGuiScreen(parent);
        else if (button.id == 2) KOMEPacketHandler.network.sendToServer(new KOMEPacketPledgeDepartureRequest());
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        contentPanel.layout(x + 18, y + 44, w - 36, Math.max(1, h - 92), getContentHeight()).setScroll(scroll);
        contentPanel.setScroll(scroll + (wheel < 0 ? 18 : wheel > 0 ? -18 : 0));
        scroll = contentPanel.getScroll();
    }

    public int getScroll() { return scroll; }

    public GuiScreen getParentScreen() { return parent; }

    public void setScroll(int value) { scroll = Math.max(0, value); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        KOMEGuiTheme.drawMainPanel(x, y, w, h);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Pledge Departure", x + 120, y + 12, w - 240);
        int cardX = x + 18;
        int cardW = w - 36;
        int contentTop = y + 44;
        int contentBottom = y + h - 48;
        contentPanel.layout(cardX, contentTop, cardW, Math.max(1, contentBottom - contentTop), getContentHeight()).setScroll(scroll);
        scroll = contentPanel.getScroll();
        contentPanel.begin(mc);
        int cursor = y + 48 - scroll;
        KOMEGuiTheme.drawWarningBanner(fontRendererObj, "Departure Preview", warningBody(), cardX, cursor, cardW,
            KOMEGuiTheme.Status.WARNING);
        cursor += KOMEGuiTheme.warningBannerHeight(fontRendererObj, warningBody(), cardW) + 8;
        cursor = card("Leaving " + KOMEAlliance.displayFactionName(data.formerFaction), leavingBody(), cardX, cursor, cardW, 54) + 8;
        cursor = card("Units and companies", unitsBody(), cardX, cursor, cardW, 54) + 8;
        cursor = card("Population and movement", populationBody(), cardX, cursor, cardW, 64) + 8;
        cursor = card("Transfers and unresolved records", transfersBody(), cardX, cursor, cardW, 64) + 8;
        contentPanel.end();
        contentPanel.drawScrollbar();
        if (getMaxScroll() > 0) {
            KOMEGuiTheme.drawMutedText(fontRendererObj, "Mouse wheel to scroll", x + w - 122, y + 35);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int getMaxScroll() {
        int viewport = Math.max(1, h - 92);
        return Math.max(0, getContentHeight() - viewport);
    }

    private int getContentHeight() {
        int bodyWidth = Math.max(1, w - 60);
        int content = KOMEGuiTheme.warningBannerHeight(fontRendererObj, warningBody(), Math.max(1, w - 36)) + 8
            + cardHeight(leavingBody(), bodyWidth, 54) + 8
            + cardHeight(unitsBody(), bodyWidth, 54) + 8
            + cardHeight(populationBody(), bodyWidth, 64) + 8
            + cardHeight(transfersBody(), bodyWidth, 64) + 8;
        return content;
    }

    private int card(String title, String body, int cardX, int cardY, int cardW, int cardH) {
        cardH = cardHeight(body, Math.max(1, cardW - 24), cardH);
        KOMEGuiTheme.drawCard(cardX, cardY, cardW, cardH, false);
        fontRendererObj.drawString(title, cardX + 12, cardY + 9, KOMEGuiTheme.COLOR_BORDER_RED);
        KOMEGuiTheme.drawWrappedText(fontRendererObj, body, cardX + 12, cardY + 25, cardW - 24, KOMEGuiTheme.COLOR_TEXT);
        return cardY + cardH;
    }

    private int cardHeight(String body, int bodyWidth, int minimum) {
        return Math.max(minimum, 31 + KOMEGuiTheme.wrapText(fontRendererObj, body, bodyWidth).size() * 10);
    }

    private String leavingBody() {
        return "This is a preview only. Cleanup begins automatically after the actual LOTR pledge transition.";
    }

    private String unitsBody() {
        return data.units + " units (" + data.farmhands + " farmhands) in " + data.companies
            + " companies will be released. Untransferred old-faction units will be removed.";
    }

    private String populationBody() {
        return "Expected returns: " + data.offensivePopulation + " offensive, " + data.defensivePopulation
            + " defensive. Movements cancelled: " + data.movements + ". Sources: " + data.fundingSources;
    }

    private String transfersBody() {
        return "Open transfer offers: " + data.transferOffers
            + ". Completed permanent transfers remain with their recipients. Pending unloaded/quarantined records: "
            + data.pendingUnloaded + ". Unresolved provenance is quarantined instead of guessed.";
    }

    private String warningBody() {
        return "Warning: transfer any company you intend to preserve before leaving the faction.";
    }
}
