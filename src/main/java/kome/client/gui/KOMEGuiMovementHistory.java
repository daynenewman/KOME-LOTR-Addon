package kome.client.gui;

import kome.common.data.KOMEMovementHistoryRecord;
import kome.common.data.KOMEAlliance;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketMovementHistoryRequest;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KOMEGuiMovementHistory extends LOTRGuiMenuBase {
    private static String rememberedFilter = "All";
    private static String rememberedCompanyKey = "";
    private static String rememberedMovementId = "";

    private final String baseTitle;
    private final String requestFaction;
    private final boolean allFactions;
    private final List<KOMEMovementHistoryRecord> allRecords = new ArrayList<KOMEMovementHistoryRecord>();
    private final List<KOMEMovementHistoryRecord> visibleRecords = new ArrayList<KOMEMovementHistoryRecord>();
    private final List<CompanySummary> companySummaries = new ArrayList<CompanySummary>();

    private String filter = "All";
    private String selectedCompanyKey = "";
    private String selectedMovementId = "";
    private boolean choosingCompany;
    private int selected = -1;
    private int scroll;
    private int detailScroll;
    private GuiButton buttonBack;
    private GuiButton buttonRefresh;

    public KOMEGuiMovementHistory(String title, String requestFaction, boolean allFactions, List records) {
        this.baseTitle = title == null || title.length() == 0 ? "Troop Movement Records" : title;
        this.requestFaction = requestFaction == null ? "" : requestFaction;
        this.allFactions = allFactions;
        if (records != null) {
            for (Object object : records) {
                if (object instanceof KOMEMovementHistoryRecord) {
                    allRecords.add((KOMEMovementHistoryRecord) object);
                }
            }
        }
        sortRecords(allRecords);
        rebuildCompanies();
        filter = rememberedFilter == null || rememberedFilter.length() == 0 ? "All" : rememberedFilter;
        selectedCompanyKey = findCompany(rememberedCompanyKey) == null ? "" : rememberedCompanyKey;
        selectedMovementId = rememberedMovementId == null ? "" : rememberedMovementId;
        applyFilter(true);
    }

    @Override
    public void initGui() {
        xSize = Math.min(720, width - 30);
        ySize = Math.min(455, height - 34);
        super.initGui();
        buttonList.clear();
        buttonMenuReturn = null;
        buttonBack = KOMEGuiButton.small(0, guiLeft + 14, guiTop + 14, "Back");
        buttonRefresh = KOMEGuiButton.normal(1, guiLeft + xSize - 110, guiTop + 14, "Refresh");
        buttonList.add(buttonBack);
        buttonList.add(buttonRefresh);
        String[] filters = filters();
        int x = guiLeft + 18;
        int y = guiTop + 52;
        for (int i = 0; i < filters.length; i++) {
            GuiButton button = KOMEGuiButton.small(10 + i, x + i * 73, y, filters[i]);
            boolean selectedButton = "Company".equals(filters[i]) ? selectedCompanyKey.length() > 0 || choosingCompany : filters[i].equals(filter) && !choosingCompany;
            button.enabled = !selectedButton;
            buttonList.add(button);
        }
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button == buttonBack) {
            mc.displayGuiScreen(new KOMEGuiServerRecords());
        } else if (button == buttonRefresh) {
            rememberState();
            KOMEPacketHandler.network.sendToServer(new KOMEPacketMovementHistoryRequest(requestFaction, allFactions));
        } else if (button.id >= 10 && button.id < 20) {
            String chosen = filters()[button.id - 10];
            if ("Company".equals(chosen)) {
                choosingCompany = true;
            } else {
                filter = chosen;
                rememberedFilter = filter;
                choosingCompany = false;
                detailScroll = 0;
                scroll = 0;
                applyFilter(false);
            }
            initGui();
        } else {
            super.actionPerformed(button);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (KOMEGuiTheme.isHovered(mouseX, mouseY, detailX(), contentY(), detailW(), contentH())) {
            detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll + (wheel < 0 ? 18 : -18)));
        } else {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel < 0 ? 1 : -1)));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            if (handleHeaderClick(mouseX, mouseY) || handleListClick(mouseX, mouseY) || handleTimelineClick(mouseX, mouseY)) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        KOMEGuiTheme.drawMainPanel(guiLeft, guiTop, xSize, ySize);
        KOMEGuiTheme.drawHeader(fontRendererObj, dynamicTitle(), guiLeft + 124, guiTop + 13, xSize - 248);
        fontRendererObj.drawString(summaryText(), guiLeft + 20, guiTop + 76, KOMEGuiTheme.COLOR_TEXT_MUTED);
        drawList(mouseX, mouseY);
        drawDetail(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private boolean handleHeaderClick(int mouseX, int mouseY) {
        int x = listX();
        int y = contentY() + 25;
        if (selectedCompanyKey.length() > 0 && KOMEGuiTheme.isHovered(mouseX, mouseY, x + 9, y, 118, 16)) {
            selectedCompanyKey = "";
            rememberedCompanyKey = "";
            choosingCompany = false;
            scroll = 0;
            detailScroll = 0;
            applyFilter(false);
            initGui();
            return true;
        }
        return false;
    }

    private boolean handleListClick(int mouseX, int mouseY) {
        int x = listX();
        int y = listRowsY();
        if (choosingCompany) {
            for (int i = 0; i < visibleRows() && scroll + i < companySummaries.size(); i++) {
                int rowY = y + i * companyRowH();
                if (KOMEGuiTheme.isHovered(mouseX, mouseY, x + 4, rowY, listW() - 12, companyRowH() - 6)) {
                    CompanySummary company = companySummaries.get(scroll + i);
                    selectedCompanyKey = company.companyKey;
                    rememberedCompanyKey = selectedCompanyKey;
                    choosingCompany = false;
                    scroll = 0;
                    detailScroll = 0;
                    applyFilter(false);
                    initGui();
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < visibleRows() && scroll + i < visibleRecords.size(); i++) {
            int rowY = y + i * rowH();
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, x + 4, rowY, listW() - 12, rowH() - 6)) {
                selected = scroll + i;
                selectedMovementId = visibleRecords.get(selected).historyId;
                rememberedMovementId = selectedMovementId;
                detailScroll = 0;
                return true;
            }
        }
        return false;
    }

    private boolean handleTimelineClick(int mouseX, int mouseY) {
        if (selectedCompanyKey.length() == 0) {
            return false;
        }
        int x = detailX() + 12;
        int y = contentY() + 252 - detailScroll;
        List<KOMEMovementHistoryRecord> records = companyRecords(selectedCompanyKey);
        for (int i = 0; i < records.size(); i++) {
            int rowY = y + i * 50;
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, x + detailW() - 82, rowY + 16, 48, 16)) {
                selectRecord(records.get(i));
                return true;
            }
        }
        return false;
    }

    private void drawList(int mouseX, int mouseY) {
        int x = listX();
        int y = contentY();
        KOMEGuiTheme.drawSubPanel(x, y, listW(), contentH());
        String title = choosingCompany ? "Companies" : selectedCompanyKey.length() > 0 ? "Company Movements" : filter + " Movements";
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, title, x + 9, y + 8, listW() - 18);
        if (selectedCompanyKey.length() > 0 && !choosingCompany) {
            drawMiniButton(x + 9, y + 25, 118, "Back to All Movements", mouseX, mouseY);
            CompanySummary company = findCompany(selectedCompanyKey);
            if (company != null) {
                fontRendererObj.drawString("Viewing Company: " + trim(company.companyName, listW() - 154), x + 134, y + 29, KOMEGuiTheme.COLOR_TEXT_MUTED);
            }
        }
        if (choosingCompany) {
            drawCompanyList(mouseX, mouseY);
        } else {
            drawMovementList(mouseX, mouseY);
        }
    }

    private void drawMovementList(int mouseX, int mouseY) {
        int x = listX();
        int y = listRowsY();
        if (visibleRecords.isEmpty()) {
            fontRendererObj.drawString(emptyMovementText(), x + 14, y + 14, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        KOMEGuiTheme.enableScissor(mc, x + 1, y, listW() - 2, contentY() + contentH() - y - 4);
        for (int i = 0; i < visibleRows() && scroll + i < visibleRecords.size(); i++) {
            int index = scroll + i;
            KOMEMovementHistoryRecord record = visibleRecords.get(index);
            int rowY = y + i * rowH();
            boolean active = selected == index;
            boolean hovered = KOMEGuiTheme.isHovered(mouseX, mouseY, x + 4, rowY, listW() - 12, rowH() - 6);
            drawMovementRow(record, x + 4, rowY, listW() - 12, rowH() - 6, active, hovered);
        }
        KOMEGuiTheme.disableScissor();
    }

    private void drawCompanyList(int mouseX, int mouseY) {
        int x = listX();
        int y = listRowsY();
        if (companySummaries.isEmpty()) {
            fontRendererObj.drawString("No companies with movement history found.", x + 14, y + 14, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        KOMEGuiTheme.enableScissor(mc, x + 1, y, listW() - 2, contentY() + contentH() - y - 4);
        for (int i = 0; i < visibleRows() && scroll + i < companySummaries.size(); i++) {
            CompanySummary company = companySummaries.get(scroll + i);
            int rowY = y + i * companyRowH();
            boolean hovered = KOMEGuiTheme.isHovered(mouseX, mouseY, x + 4, rowY, listW() - 12, companyRowH() - 6);
            drawCompanyRow(company, x + 4, rowY, listW() - 12, companyRowH() - 6, hovered);
        }
        KOMEGuiTheme.disableScissor();
    }

    private void drawMovementRow(KOMEMovementHistoryRecord record, int x, int y, int w, int h, boolean selectedRow, boolean hovered) {
        KOMEGuiTheme.drawBorderedRect(x, y, w, h, selectedRow ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_GOLD_DARK,
            hovered ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK);
        fontRendererObj.drawString(trim(record.companyName, w - 96), x + 8, y + 6, KOMEGuiTheme.COLOR_BORDER_RED);
        drawStatus(record.status, x + w - 74, y + 5, 66);
        fontRendererObj.drawString(routeSummary(record), x + 8, y + 20, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(trim(record.totalPopulation + " pop | " + clean(record.ownerName) + " | " + displayFaction(record.faction), w - 16), x + 8, y + 34, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Last updated: " + absoluteTime(activityTime(record)), x + 8, y + 48, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawCompanyRow(CompanySummary company, int x, int y, int w, int h, boolean hovered) {
        KOMEGuiTheme.drawBorderedRect(x, y, w, h, KOMEGuiTheme.COLOR_GOLD_DARK, hovered ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK);
        fontRendererObj.drawString(trim(company.companyName, w - 18), x + 8, y + 6, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Owner: " + trim(company.ownerName, w - 70), x + 8, y + 20, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Faction: " + displayFaction(company.faction), x + 8, y + 34, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Current: " + clean(company.currentTile), x + 8, y + 48, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Latest Status: " + statusLabel(company.latestStatus), x + 8, y + 62, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Records: " + company.recordCount, x + w - 72, y + 62, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawDetail(int mouseX, int mouseY) {
        int x = detailX();
        int y = contentY();
        KOMEGuiTheme.drawSubPanel(x, y, detailW(), contentH());
        if (selectedCompanyKey.length() > 0) {
            drawCompanyDetail(x, y, mouseX, mouseY);
        } else {
            drawMovementDetail(x, y);
        }
    }

    private void drawMovementDetail(int x, int y) {
        if (selected < 0 || selected >= visibleRecords.size()) {
            fontRendererObj.drawString("Select a movement record.", x + 14, y + 38, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        KOMEMovementHistoryRecord record = visibleRecords.get(selected);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, trim(record.companyName, detailW() - 18), x + 9, y + 8, detailW() - 18);
        KOMEGuiTheme.enableScissor(mc, x + 1, y + 24, detailW() - 2, contentH() - 28);
        drawMovementDetailCards(record, x, y + 30 - detailScroll);
        KOMEGuiTheme.disableScissor();
    }

    private void drawCompanyDetail(int x, int y, int mouseX, int mouseY) {
        CompanySummary company = findCompany(selectedCompanyKey);
        if (company == null) {
            fontRendererObj.drawString("No records found for this company.", x + 14, y + 38, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, trim(company.companyName, detailW() - 18), x + 9, y + 8, detailW() - 18);
        KOMEGuiTheme.enableScissor(mc, x + 1, y + 24, detailW() - 2, contentH() - 28);
        int cy = y + 30 - detailScroll;
        cy = detailCard("Company Summary", company.companyName + "\nOwner: " + company.ownerName
            + "\nFaction: " + displayFaction(company.faction)
            + "\nCurrent Tile: " + clean(company.currentTile)
            + "\nLatest Destination: " + clean(company.latestDestination)
            + "\nLatest Status: " + statusLabel(company.latestStatus)
            + "\nTotal Recorded Movements: " + company.recordCount, x + 12, cy, detailW() - 24);
        KOMEMovementHistoryRecord latest = selected >= 0 && selected < visibleRecords.size() ? visibleRecords.get(selected) : company.latestRecord;
        cy = detailCard("Current / Latest Movement", movementSummary(latest), x + 12, cy + 8, detailW() - 24);
        cy = drawTimeline(x + 12, cy + 12, detailW() - 24, companyRecords(selectedCompanyKey), mouseX, mouseY);
        KOMEGuiTheme.disableScissor();
    }

    private int drawTimeline(int x, int y, int w, List<KOMEMovementHistoryRecord> records, int mouseX, int mouseY) {
        fontRendererObj.drawString("Movement History Timeline", x, y, KOMEGuiTheme.COLOR_BORDER_RED);
        int cy = y + 14;
        if (records.isEmpty()) {
            fontRendererObj.drawString("No records found for this company.", x, cy + 10, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return cy + 32;
        }
        for (KOMEMovementHistoryRecord record : records) {
            KOMEGuiTheme.drawCard(x, cy, w, 44, false);
            fontRendererObj.drawString(absoluteTime(activityTime(record)), x + 8, cy + 6, KOMEGuiTheme.COLOR_BORDER_RED);
            fontRendererObj.drawString(routeSummary(record), x + 8, cy + 18, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString("Status: " + statusLabel(record.status) + " | Steps: " + record.completedSteps + " / " + stepsTotal(record) + " | Population: " + record.totalPopulation, x + 8, cy + 30, KOMEGuiTheme.COLOR_TEXT_MUTED);
            drawMiniButton(x + w - 58, cy + 16, 48, "View", mouseX, mouseY);
            cy += 50;
        }
        return cy;
    }

    private int drawMovementDetailCards(KOMEMovementHistoryRecord record, int x, int cy) {
        cy = detailCard("Status", statusLabel(record) + "\nOwner: " + record.ownerName + "\nFaction: " + displayFaction(record.faction)
            + "\nLast updated: " + absoluteTime(activityTime(record)), x + 12, cy, detailW() - 24);
        cy = detailCard("Route", "Origin: " + record.originTile + "\nCurrent: " + clean(record.currentTile)
            + "\nNext: " + clean(record.nextTile) + "\nFinal: " + record.finalDestinationTile
            + "\nRoute: " + joinRoute(record.routeTiles), x + 12, cy + 8, detailW() - 24);
        cy = detailCard("Progress", "Steps: " + record.completedSteps + " / " + stepsTotal(record)
            + "\nUnits: " + record.unitCount + "\nPopulation: " + record.totalPopulation
            + "\nMounted: " + record.mountedPopulation + "\nGround: " + record.groundPopulation
            + "\nSpeed: " + record.speedDescription, x + 12, cy + 8, detailW() - 24);
        cy = detailCard("Timing", "Created: " + absoluteTime(record.createdAtMillis)
            + "\nLast step: " + absoluteTime(record.lastStepMillis)
            + "\nNext step: " + absoluteTime(record.nextStepAvailableMillis)
            + "\nCompleted: " + absoluteTime(record.completedAtMillis)
            + "\nStopped: " + absoluteTime(record.stoppedAtMillis)
            + "\nCancelled: " + absoluteTime(record.cancelledAtMillis), x + 12, cy + 8, detailW() - 24);
        if (!record.usedBridgeNames.isEmpty() || !record.usedPassageNames.isEmpty()) {
            cy = detailCard("Special Crossings", "Bridges: " + join(record.usedBridgeNames)
                + "\nPassages: " + join(record.usedPassageNames), x + 12, cy + 8, detailW() - 24);
        }
        if (record.failureReason != null && record.failureReason.length() > 0) {
            cy = detailCard("Failure / Pending Reason", record.failureReason, x + 12, cy + 8, detailW() - 24);
        }
        return cy;
    }

    private String movementSummary(KOMEMovementHistoryRecord record) {
        if (record == null) {
            return "No latest movement.";
        }
        return "Status: " + statusLabel(record.status)
            + "\nOrigin: " + clean(record.originTile)
            + "\nCurrent: " + clean(record.currentTile)
            + "\nNext: " + clean(record.nextTile)
            + "\nFinal: " + clean(record.finalDestinationTile)
            + "\nRoute: " + joinRoute(record.routeTiles)
            + "\nSteps: " + record.completedSteps + " / " + stepsTotal(record)
            + "\nUnits: " + record.unitCount
            + "\nPopulation: " + record.totalPopulation
            + "\nMounted: " + record.mountedPopulation
            + "\nGround: " + record.groundPopulation
            + "\nSpeed: " + record.speedDescription
            + "\nCreated: " + absoluteTime(record.createdAtMillis)
            + "\nLast step: " + absoluteTime(record.lastStepMillis);
    }

    private int detailCard(String title, String value, int x, int y, int w) {
        int h = 28 + KOMEGuiTheme.wrapText(fontRendererObj, value, w - 16).size() * 10;
        KOMEGuiTheme.drawCard(x, y, w, h, false);
        fontRendererObj.drawString(title, x + 8, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        KOMEGuiTheme.drawWrappedText(fontRendererObj, value, x + 8, y + 20, w - 16, KOMEGuiTheme.COLOR_TEXT);
        return y + h;
    }

    private void drawStatus(String status, int x, int y, int w) {
        int color = KOMEMovementHistoryRecord.ACTIVE.equals(status) ? KOMEGuiTheme.COLOR_GOOD
            : KOMEMovementHistoryRecord.ARRIVED.equals(status) ? KOMEGuiTheme.COLOR_GOLD
            : KOMEMovementHistoryRecord.FAILED.equals(status) ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_TEXT_MUTED;
        KOMEGuiTheme.drawBorderedRect(x, y, w, 14, KOMEGuiTheme.COLOR_GOLD_DARK, 0xFFE8D6A8);
        String label = statusLabel(status);
        fontRendererObj.drawString(label, x + (w - fontRendererObj.getStringWidth(label)) / 2, y + 3, color);
    }

    private void drawMiniButton(int x, int y, int width, String label, int mouseX, int mouseY) {
        boolean hovered = KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, 16);
        KOMEGuiTheme.drawBorderedRect(x, y, width, 16, hovered ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_BORDER_RED, hovered ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK);
        String text = KOMEGuiTheme.trimToWidth(fontRendererObj, label, width - 6);
        fontRendererObj.drawString(text, x + width / 2 - fontRendererObj.getStringWidth(text) / 2, y + 4, KOMEGuiTheme.COLOR_TEXT);
    }

    private void applyFilter(boolean preserveSelection) {
        visibleRecords.clear();
        for (KOMEMovementHistoryRecord record : allRecords) {
            if (selectedCompanyKey.length() > 0 && !selectedCompanyKey.equals(record.stableCompanyKey())) {
                continue;
            }
            if ("All".equals(filter) || statusLabel(record.status).equals(filter)) {
                visibleRecords.add(record);
            }
        }
        sortRecords(visibleRecords);
        if (!preserveSelection || !selectMovementById(selectedMovementId)) {
            selected = visibleRecords.isEmpty() ? -1 : 0;
            selectedMovementId = selected >= 0 ? visibleRecords.get(selected).historyId : "";
        }
        rememberedMovementId = selectedMovementId;
        if (scroll > maxScroll()) {
            scroll = maxScroll();
        }
    }

    private boolean selectMovementById(String id) {
        if (id == null || id.length() == 0) {
            return false;
        }
        for (int i = 0; i < visibleRecords.size(); i++) {
            KOMEMovementHistoryRecord record = visibleRecords.get(i);
            if (id.equals(record.historyId) || id.equals(record.movementOrderId)) {
                selected = i;
                selectedMovementId = record.historyId;
                return true;
            }
        }
        return false;
    }

    private void selectRecord(KOMEMovementHistoryRecord record) {
        if (record == null) {
            return;
        }
        selectedMovementId = record.historyId;
        rememberedMovementId = selectedMovementId;
        selectMovementById(selectedMovementId);
        detailScroll = 0;
    }

    private void rebuildCompanies() {
        companySummaries.clear();
        Map<String, CompanySummary> byKey = new HashMap<String, CompanySummary>();
        for (KOMEMovementHistoryRecord record : allRecords) {
            String key = record.stableCompanyKey();
            CompanySummary summary = byKey.get(key);
            if (summary == null) {
                summary = new CompanySummary();
                summary.companyKey = key;
                summary.companyId = record.companyId;
                summary.companyName = cleanStatic(record.companyName);
                summary.ownerUuid = record.ownerUuid == null ? "" : record.ownerUuid.toString();
                summary.ownerName = cleanStatic(record.ownerName);
                summary.faction = record.faction;
                byKey.put(key, summary);
                companySummaries.add(summary);
            }
            summary.recordCount++;
            long activity = activityTime(record);
            if (activity >= summary.latestActivityAt) {
                summary.latestActivityAt = activity;
                summary.latestRecord = record;
                summary.currentTile = cleanStatic(record.currentTile);
                summary.latestDestination = cleanStatic(record.finalDestinationTile);
                summary.latestStatus = record.status;
            }
        }
        Collections.sort(companySummaries, new Comparator<CompanySummary>() {
            @Override
            public int compare(CompanySummary left, CompanySummary right) {
                if (left.latestActivityAt != right.latestActivityAt) {
                    return left.latestActivityAt > right.latestActivityAt ? -1 : 1;
                }
                return right.companyKey.compareTo(left.companyKey);
            }
        });
    }

    private List<KOMEMovementHistoryRecord> companyRecords(String companyKey) {
        List<KOMEMovementHistoryRecord> records = new ArrayList<KOMEMovementHistoryRecord>();
        for (KOMEMovementHistoryRecord record : allRecords) {
            if (companyKey.equals(record.stableCompanyKey())) {
                records.add(record);
            }
        }
        sortRecords(records);
        return records;
    }

    private CompanySummary findCompany(String companyKey) {
        if (companyKey == null || companyKey.length() == 0) {
            return null;
        }
        for (CompanySummary summary : companySummaries) {
            if (companyKey.equals(summary.companyKey)) {
                return summary;
            }
        }
        return null;
    }

    private void sortRecords(List<KOMEMovementHistoryRecord> records) {
        Collections.sort(records, new Comparator<KOMEMovementHistoryRecord>() {
            @Override
            public int compare(KOMEMovementHistoryRecord left, KOMEMovementHistoryRecord right) {
                long leftTime = activityTime(left);
                long rightTime = activityTime(right);
                if (leftTime != rightTime) {
                    return leftTime > rightTime ? -1 : 1;
                }
                String rightId = right == null || right.movementOrderId == null ? "" : right.movementOrderId;
                String leftId = left == null || left.movementOrderId == null ? "" : left.movementOrderId;
                return rightId.compareTo(leftId);
            }
        });
    }

    private void rememberState() {
        rememberedFilter = filter;
        rememberedCompanyKey = selectedCompanyKey;
        rememberedMovementId = selectedMovementId;
    }

    private String dynamicTitle() {
        CompanySummary company = findCompany(selectedCompanyKey);
        if (company != null) {
            return baseTitle + " - " + company.companyName;
        }
        return baseTitle + " - All Companies";
    }

    private String summaryText() {
        int active = 0;
        int arrived = 0;
        int stopped = 0;
        int failed = 0;
        for (KOMEMovementHistoryRecord record : allRecords) {
            if (KOMEMovementHistoryRecord.ACTIVE.equals(record.status)) active++;
            else if (KOMEMovementHistoryRecord.ARRIVED.equals(record.status)) arrived++;
            else if (KOMEMovementHistoryRecord.STOPPED.equals(record.status)) stopped++;
            else if (KOMEMovementHistoryRecord.FAILED.equals(record.status)) failed++;
        }
        return "Active: " + active + " | Arrived: " + arrived + " | Stopped: " + stopped + " | Failed: " + failed + " | Companies: " + companySummaries.size();
    }

    private String emptyMovementText() {
        if (selectedCompanyKey.length() > 0) {
            return "No records found for this company.";
        }
        if (!"All".equals(filter)) {
            return "No " + filter.toLowerCase() + " movements found.";
        }
        return "No movement records found.";
    }

    private String statusLabel(KOMEMovementHistoryRecord record) {
        return statusLabel(record == null ? "" : record.status);
    }

    private String statusLabel(String status) {
        if (KOMEMovementHistoryRecord.ACTIVE.equals(status)) return "Active";
        if (KOMEMovementHistoryRecord.ARRIVED.equals(status)) return "Arrived";
        if (KOMEMovementHistoryRecord.STOPPED.equals(status)) return "Stopped";
        if (KOMEMovementHistoryRecord.CANCELLED.equals(status)) return "Cancelled";
        if (KOMEMovementHistoryRecord.FAILED.equals(status)) return "Failed";
        return "Active";
    }

    private String routeSummary(KOMEMovementHistoryRecord record) {
        return clean(record.originTile) + " -> " + clean(record.finalDestinationTile);
    }

    private int stepsTotal(KOMEMovementHistoryRecord record) {
        return Math.max(record.routeDistance, record.routeTiles == null ? 0 : Math.max(0, record.routeTiles.size() - 1));
    }

    private String joinRoute(List<String> route) {
        return route == null || route.isEmpty() ? "None" : join(route);
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "None";
        }
        String value = "";
        for (String part : values) {
            value += (value.length() == 0 ? "" : " -> ") + part;
        }
        return value;
    }

    private String clean(String value) {
        return cleanStatic(value);
    }

    private static String cleanStatic(String value) {
        return value == null || value.length() == 0 ? "None" : value;
    }

    private String trim(String value, int width) {
        return KOMEGuiTheme.trimToWidth(fontRendererObj, clean(value), width);
    }

    private long activityTime(KOMEMovementHistoryRecord record) {
        return record == null ? 0L : record.getLatestActivityMillis();
    }

    private String absoluteTime(long millis) {
        if (millis <= 0L) {
            return "None";
        }
        return new SimpleDateFormat("MMM d h:mm a").format(new Date(millis));
    }

    private String displayFaction(String faction) {
        return KOMEAlliance.displayFactionName(faction);
    }

    private String[] filters() {
        return new String[] {"All", "Active", "Arrived", "Stopped", "Cancelled", "Failed", "Company"};
    }

    private int contentY() { return guiTop + 90; }
    private int contentH() { return ySize - 108; }
    private int listX() { return guiLeft + 18; }
    private int listW() { return Math.max(280, Math.min(335, xSize / 2 - 12)); }
    private int detailX() { return listX() + listW() + 12; }
    private int detailW() { return guiLeft + xSize - 18 - detailX(); }
    private int rowH() { return 72; }
    private int companyRowH() { return 86; }
    private int listRowsY() { return contentY() + (selectedCompanyKey.length() > 0 && !choosingCompany ? 46 : 24); }
    private int visibleRows() {
        int rowHeight = choosingCompany ? companyRowH() : rowH();
        return Math.max(3, (contentY() + contentH() - listRowsY() - 4) / rowHeight);
    }
    private int maxScroll() {
        int size = choosingCompany ? companySummaries.size() : visibleRecords.size();
        return Math.max(0, size - visibleRows());
    }

    private int maxDetailScroll() {
        return selectedCompanyKey.length() > 0 ? 900 : 420;
    }

    private static class CompanySummary {
        String companyKey = "";
        String companyId = "";
        String companyName = "Unknown Company";
        String ownerUuid = "";
        String ownerName = "Unknown";
        String faction = "";
        String currentTile = "";
        String latestDestination = "";
        String latestStatus = KOMEMovementHistoryRecord.ACTIVE;
        long latestActivityAt;
        int recordCount;
        KOMEMovementHistoryRecord latestRecord;
    }
}
