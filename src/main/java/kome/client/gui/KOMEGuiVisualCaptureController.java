package kome.client.gui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.client.KOMEQuotaLedgerOverlay;
import kome.common.network.KOMEPacketConquestCaptureGui;
import kome.common.network.KOMEPacketPledgeDepartureData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Property-gated, inert-in-production visual regression capture for the KOME GUI suite. */
public final class KOMEGuiVisualCaptureController {
    private interface ScreenFactory { GuiScreen create(); }

    private final List names = new ArrayList();
    private final List screens = new ArrayList();
    private int startupTicks;
    private int screenTicks;
    private int index = -1;
    private boolean configured;
    private String captureLabel = "scale";
    private String captureDirectory = "gui-captures";

    public static boolean isCaptureEnabled() {
        return Boolean.getBoolean("kome.guiCapture")
            || System.getProperty("kome.guiCaptureProfile", "").trim().length() > 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isCaptureEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!configured) {
            if (++startupTicks < 30) return;
            configure(mc);
            return;
        }
        if (index < 0) {
            showNext(mc);
            return;
        }
        if (++screenTicks < 8) return;
        capture(mc, String.valueOf(names.get(index)));
        showNext(mc);
    }

    private void configure(Minecraft mc) {
        configured = true;
        String profile = System.getProperty("kome.guiCaptureProfile", "").trim();
        int requestedWidth = mc.displayWidth;
        int requestedHeight = mc.displayHeight;
        int requestedScale = Integer.getInteger("kome.guiCaptureScale", 2);
        if ("small".equals(profile)) {
            requestedWidth = 1280; requestedHeight = 720; requestedScale = 1; captureLabel = "small";
        } else if ("normal".equals(profile)) {
            requestedWidth = 1280; requestedHeight = 720; requestedScale = 2; captureLabel = "normal";
        } else if ("large".equals(profile)) {
            requestedWidth = 1280; requestedHeight = 720; requestedScale = 3; captureLabel = "large";
        } else if ("auto".equals(profile)) {
            requestedWidth = 1600; requestedHeight = 900; requestedScale = 0; captureLabel = "auto";
        } else if ("min-854x480".equals(profile)) {
            requestedWidth = 854; requestedHeight = 480; requestedScale = 0; captureLabel = "min-854x480";
        } else {
            captureLabel = System.getProperty("kome.guiCaptureLabel", "scale");
        }
        if (profile.length() > 0) captureDirectory = "../docs/gui-scale-verification";
        resizeWindow(mc, requestedWidth, requestedHeight);
        mc.gameSettings.guiScale = requestedScale;
        mc.resize(mc.displayWidth, mc.displayHeight);
        prepareAllianceData();
        addScreens();
    }

    private void resizeWindow(Minecraft mc, int width, int height) {
        int safeWidth = Math.max(854, width);
        int safeHeight = Math.max(480, height);
        if (mc.displayWidth == safeWidth && mc.displayHeight == safeHeight) return;
        try {
            Display.setDisplayMode(new DisplayMode(safeWidth, safeHeight));
            mc.displayWidth = safeWidth;
            mc.displayHeight = safeHeight;
        } catch (LWJGLException error) {
            throw new IllegalStateException("Unable to set deterministic GUI capture size.", error);
        }
    }

    private void addScreens() {
        addAlliance("alliance-list", 0, 0, "", false);
        add("alliance-create", new ScreenFactory() { public GuiScreen create() {
            KOMEGuiAllianceUnified gui = new KOMEGuiAllianceUnified();
            gui.setVisualTestState(1, 0, "", false);
            return gui;
        }});
        addAlliance("alliance-overview", 2, 0, "gondor|rohan", false);
        addAlliance("alliance-requirements", 2, 1, "gondor|rohan", false);
        addAlliance("alliance-benefits", 2, 2, "gondor|rohan", false);
        addAlliance("alliance-military", 2, 3, "gondor|rohan", false);
        addAlliance("alliance-break-confirmation", 2, 0, "gondor|rohan", true);
        add("alliance-ledger", new ScreenFactory() { public GuiScreen create() {
            prepareLedgerData();
            return new LedgerVisualPreview();
        }});
        add("war-records", new ScreenFactory() { public GuiScreen create() {
            prepareWarData(); KOMEGuiServerRecords.setVisualTestWarMode(true); return new KOMEGuiServerRecords();
        }});
        add("allied-tile-confirmation", new ScreenFactory() { public GuiScreen create() { return captureGui(); }});
        add("tile-build-create", new ScreenFactory() { public GuiScreen create() {
            return captureGui(0, 2, -1);
        }});
        add("tile-build-detail", new ScreenFactory() { public GuiScreen create() {
            return captureGui(0, 1, 0);
        }});
        add("tile-population", new ScreenFactory() { public GuiScreen create() {
            return captureGui(1, 0, -1);
        }});
        add("pledge-departure-preview", new ScreenFactory() { public GuiScreen create() { return pledgeGui(); }});
    }

    private void addAlliance(String name, final int mode, final int tab, final String pair, final boolean confirmBreak) {
        add(name, new ScreenFactory() { public GuiScreen create() {
            KOMEGuiAllianceUnified gui = new KOMEGuiAllianceUnified();
            gui.setVisualTestState(mode, tab, pair, confirmBreak);
            return gui;
        }});
    }

    private void add(String name, ScreenFactory screen) {
        names.add(name);
        screens.add(screen);
    }

    private void showNext(Minecraft mc) {
        index++;
        screenTicks = 0;
        if (index >= screens.size()) {
            mc.shutdown();
            return;
        }
        mc.displayGuiScreen(((ScreenFactory) screens.get(index)).create());
    }

    private void capture(Minecraft mc, String screenName) {
        File output = new File(System.getProperty("kome.guiCaptureDir", captureDirectory));
        output.mkdirs();
        ScreenShotHelper.saveScreenshot(output, captureLabel + "-" + screenName + ".png",
            mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private static void prepareAllianceData() {
        List lines = new ArrayList();
        lines.add("SUMMARY\t2");
        lines.add("VIEWER\tgondor\tGondor\t1\t1\t1\t0");
        lines.add("CONFIG\tstandard\t1.0\t0\t0\t0\t0");
        lines.add("KING\tgondor\tSteward Ecthelion");
        lines.add("KING\trohan\tKing Eomer");
        lines.add("REQUEST_OPTION_V2\trohan\tRohan\t1\t1\t0\t0\t");
        lines.add("REQUEST_OPTION_V2\thighelves\tHigh Elves of Lindon and Rivendell\t1\t1\t0\t0\t");
        lines.add(join(new String[] {"STAGE_RELATION","gondor|rohan","gondor","rohan","Gondor","Rohan",
            "gondor","rohan","3","2","active","","","Friends","1","1","1","4",
            "Royal Provision Crates","96","48","0","1",
            "Deploy a non-empty Gondor company in Rohan-controlled land during a new defensive war after Stage 3.",
            "1","","","1","Stage 3 claimed by Steward Ecthelion","18420"}));
        lines.add(join(new String[] {"STAGE_RELATION","gondor|highelves","gondor","highelves","Gondor",
            "High Elves of Lindon and Rivendell","gondor","highelves","-1","-1","pending","gondor",
            "highelves","Default","1","1","1","0","","0","0","0","1",
            "Awaiting the receiving king.","0","","","0","Requested by Steward Ecthelion","18422"}));
        KOMEGuiAllianceUnified.update(lines);
    }

    private static void prepareLedgerData() {
        List lines = new ArrayList();
        lines.add("SUMMARY\tGondor\tRohan\tgondor\trohan\tgondor|rohan");
        lines.add("VIEWER\tGondor\t1\t1");
        lines.add("PROGRESS\tStage 4\tCurrent Stage 3\tNext: Military Partnership | Qualifying defensive deployment required\tGoods delivered\t48\t96\tMilitary Partnership");
        lines.add("QUOTA\tStage 4\tRoyal Provision Crates\t48\t96\titems");
        lines.add("CLAIM\tIncoming goods are held for Rohan's king\t0\tNo incoming goods are currently claimable");
        lines.add("SWITCH\tView Rohan Ledger\trohan\tgondor\t1");
        KOMEQuotaLedgerOverlay.update(lines);
    }

    private static void prepareWarData() {
        List lines = new ArrayList();
        lines.add("SUMMARY\t4\t12\t1");
        lines.add(join(new String[] {"WAR","war-north-001","Northern Coalition War","ACTIVE","Free Peoples","Gondor, Rohan, High Elves",
            "Shadow Host","Mordor, Isengard","2026-07-18 08:30","Amon Sul captured by Gondor","3","One Stage 4 support company is awaiting withdrawal",
            "Amon Sul: Mordor -> Gondor; Westfold: Isengard -> Rohan","Gondor joined by declaration; Rohan joined by defense pact",
            "Westfold Riders (Stage 4, server-authorized)","Steward Ecthelion; King Eomer","Westfold Riders: 84 offensive population",
            "One withdrawal completes in 1d 4h","Created by Steward Ecthelion; Rohan joined automatically","None","None","None",
            "Gondor=DECLARATION; Rohan=DEFENSE; Mordor=DECLARATION; Isengard=COALITION","Rohan->Gondor: Westfold Riders"}));
        KOMEGuiServerRecords.update(lines);
    }

    private static GuiScreen captureGui() {
        return captureGui(-1, -1, -1);
    }

    private static GuiScreen captureGui(int tab, int mode, int selectedIndex) {
        KOMEPacketConquestCaptureGui data = new KOMEPacketConquestCaptureGui();
        data.tileId = "amon_sul"; data.ownerFaction = "rohan"; data.viewerFaction = "gondor";
        data.offensivePop = 184; data.defensivePop = 96; data.mountedPop = 122; data.groundPop = 62;
        data.incomingPop = 42; data.outgoingPop = 18; data.incomingEtaMillis = 5400000L;
        data.offensiveTotal = 420; data.offensiveUsed = 214; data.defensiveTotal = 260; data.defensiveUsed = 96;
        data.farmhandTotal = 48; data.farmhandUsed = 22; data.canClaim = true; data.canTransfer = false;
        data.canMoveTroops = true; data.offensiveAllocated = 240; data.defensiveAllocated = 150;
        data.myOffensiveAllocated = 120; data.myOffensiveUsed = 84; data.myDefensiveAllocated = 70; data.myDefensiveUsed = 36;
        data.claimantName = "Steward Ecthelion"; data.allocationSummary = "Gondor 120/70; Rohan 120/80"; data.ownerHasKing = true;
        data.myOffensivePop = 84; data.myDefensivePop = 36; data.myMountedPop = 62; data.myGroundPop = 22;
        data.activeRecruitmentTile = "minas_tirith"; data.lotrWaypointKey = "amonSul"; data.lotrWaypointDisplayName = "Amon Sul";
        data.lotrWaypointRegion = "Eriador"; data.waypointLevel = 2; data.currentRulingFaction = "rohan";
        data.defaultRulingFaction = "rangersnorth"; data.mapRegion = "Eriador"; data.claimConfirmationArmed = true;
        data.claimWarning = "Rohan is allied with Gondor. Confirming converts this capture into a recorded war consequence.";
        data.claimWarDestination = "Destination: Northern Coalition War / Free Peoples side.";
        data.viewerDimension = 0; data.viewerX = 1824.5D; data.viewerY = 72D; data.viewerZ = -935.5D;
        data.selectablePopulationOwners.add("gondor");
        data.selectablePopulationOwners.add("rohan");
        for (int i = 1; i <= 8; i++) {
            KOMEPacketConquestCaptureGui.BuildView build = new KOMEPacketConquestCaptureGui.BuildView();
            build.id = String.format("B%05d", i);
            build.name = i == 2 ? "The Very Long Restoration of the Northern Watch and Beacon Works"
                : i % 2 == 0 ? "Rohan Forward Granary " + i : "Gondor Stoneworks " + i;
            build.populationFaction = i % 3 == 0 ? "rohan" : "gondor";
            build.builder = i % 2 == 0 ? "WestfoldBuilder" : "Steward Ecthelion";
            build.manager = build.populationFaction.equals("gondor") ? "Steward Ecthelion" : "King Eomer";
            build.dimension = 0; build.x = 1810D + i * 4D; build.y = 71D; build.z = -950D + i * 3D;
            build.buildType = i % 2 == 0 ? "DEFENSIVE" : "NORMAL";
            build.approvedHalfHours = 8 + i;
            build.pendingCount = i % 3; build.status = i % 3 == 0 ? "Friendly" : "Owned";
            build.canManage = true;
            build.destroyMode = "delete";
            build.destroyReason = "";
            if (i == 1) {
                build.destroyReason = "";
            }
            data.builds.add(build);
        }
        KOMEPacketConquestCaptureGui.PopulationPoolView gondor = new KOMEPacketConquestCaptureGui.PopulationPoolView();
        gondor.faction = "gondor"; gondor.nativeOffensive = 50; gondor.nativeDefensive = 25;
        gondor.buildOffensive = 180; gondor.buildDefensive = 90; gondor.physicalOffensive = 230;
        gondor.physicalDefensive = 115; gondor.usableOffensive = 115; gondor.usableDefensive = 57;
        gondor.usedOffensive = 84; gondor.usedDefensive = 36; data.populationPools.add(gondor);
        KOMEPacketConquestCaptureGui.PopulationPoolView rohan = new KOMEPacketConquestCaptureGui.PopulationPoolView();
        rohan.faction = "rohan"; rohan.buildOffensive = 120; rohan.buildDefensive = 60;
        rohan.physicalOffensive = 120; rohan.physicalDefensive = 60; rohan.usableOffensive = 120;
        rohan.usableDefensive = 60; rohan.usedOffensive = 42; rohan.usedDefensive = 18;
        data.populationPools.add(rohan);
        KOMEGuiConquestCapture gui = new KOMEGuiConquestCapture(data);
        if (tab >= 0) gui.setVisualTestState(tab, mode, selectedIndex);
        return gui;
    }

    private static GuiScreen pledgeGui() {
        KOMEPacketPledgeDepartureData data = new KOMEPacketPledgeDepartureData();
        data.playerName = "VisualTester"; data.formerFaction = "gondor"; data.units = 137; data.farmhands = 24;
        data.companies = 4; data.movements = 2; data.transferOffers = 1; data.offensivePopulation = 214;
        data.defensivePopulation = 96; data.pendingUnloaded = 3;
        data.fundingSources = "Minas Tirith levy, Westfold allied stewardship, two unloaded provenance records";
        return new KOMEGuiPledgeDeparture(data);
    }

    private static String join(String[] values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append('\t');
            result.append(values[i] == null ? "" : values[i]);
        }
        return result.toString();
    }

    /** The container-free capture twin avoids LOTR's pouch hook, which requires a live player. */
    private static final class LedgerVisualPreview extends GuiScreen {
        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            float scale = Math.min(1.0F, Math.min((width - 8) / 620.0F, (height - 8) / 460.0F));
            int logicalW = Math.round(width / scale);
            int logicalH = Math.round(height / scale);
            int left = (logicalW - 620) / 2;
            int top = (logicalH - 460) / 2;
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glScalef(scale, scale, 1.0F);
            KOMEGuiTheme.drawMainPanel(left, top, 620, 460);
            KOMEGuiTheme.drawHeader(fontRendererObj, "Alliance Goods Ledger", left + 170, top + 12, 280);
            KOMEGuiTheme.drawCard(left + 18, top + 48, 584, 70, false);
            KOMEGuiTheme.drawFactionBadge(fontRendererObj, "gondor", "Gondor", left + 28, top + 54, 190);
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, "->", left + 310, top + 59, KOMEGuiTheme.COLOR_GOLD);
            KOMEGuiTheme.drawFactionBadge(fontRendererObj, "rohan", "Rohan", left + 402, top + 54, 190);
            fontRendererObj.drawString("Your Faction: Gondor", left + 28, top + 76, KOMEGuiTheme.COLOR_TEXT);
            KOMEGuiTheme.drawStatusChip(fontRendererObj, "Deposit Yes", left + 28, top + 95, KOMEGuiTheme.Status.ACTIVE);
            int claimW = KOMEGuiTheme.statusChipWidth(fontRendererObj, "Claim No");
            KOMEGuiTheme.drawStatusChip(fontRendererObj, "Claim No", left + 592 - claimW, top + 95, KOMEGuiTheme.Status.LOCKED);
            int stageX = left + 18;
            int stageW = 584;
            KOMEGuiTheme.drawCard(stageX, top + 128, stageW, 66, false);
            fontRendererObj.drawString("Stage 4 - Military Partnership", stageX + 10, top + 135, KOMEGuiTheme.COLOR_GOLD);
            String stageState = "Current Stage 3";
            int chipW = KOMEGuiTheme.statusChipWidth(fontRendererObj, stageState);
            KOMEGuiTheme.drawStatusChip(fontRendererObj, stageState, KOMEGuiTheme.Status.ACTIVE,
                stageX + stageW - chipW - 10, top + 133, chipW);
            fontRendererObj.drawString("Next requirement: Royal Provision Crates and one qualifying defensive deployment",
                stageX + 10, top + 155, KOMEGuiTheme.COLOR_TEXT_MUTED);
            KOMEGuiTheme.drawProgressBar(fontRendererObj, stageX + 10, top + 172, stageW - 20, 11,
                0.5F, KOMEGuiTheme.COLOR_GOLD, "48 / 96 goods delivered");
            KOMEGuiTheme.drawSubPanel(left + 18, top + 204, 584, 56);
            fontRendererObj.drawString("Current Quotas", left + 28, top + 211, KOMEGuiTheme.COLOR_GOLD);
            fontRendererObj.drawString("Stage 4: 48/96 Royal Provision Crates", left + 28, top + 227, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString("Fixed milestone: qualifying deployment not yet recorded", left + 28, top + 242, KOMEGuiTheme.COLOR_WARN);
            KOMEGuiTheme.drawSubPanel(left + 18, top + 260, 584, 52);
            fontRendererObj.drawString("Deposit Goods", left + 28, top + 268, KOMEGuiTheme.COLOR_GOLD);
            fontRendererObj.drawString("Place required goods into the authoritative ledger slots.", left + 28, top + 282, KOMEGuiTheme.COLOR_TEXT_MUTED);
            drawSlots(left + 420, top + 278, 9, 1);
            fontRendererObj.drawString("Inventory", left + 226, top + 322, KOMEGuiTheme.COLOR_GOLD);
            drawSlots(left + 226, top + 338, 9, 3);
            drawSlots(left + 226, top + 396, 9, 1);
            drawButton(left + 18, top + 432, 92, "Back", false);
            drawButton(left + 118, top + 432, 132, "View Rohan Ledger", false);
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
                "No incoming goods are currently claimable.", 192), left + 266, top + 439, KOMEGuiTheme.COLOR_TEXT_MUTED);
            drawButton(left + 470, top + 432, 132, "Claim Goods", true);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }

        private void drawSlots(int x, int y, int columns, int rows) {
            for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++)
                KOMEGuiTheme.drawIconSlot(x + col * 18, y + row * 18, 18, false);
        }

        private void drawButton(int x, int y, int w, String label, boolean disabled) {
            KOMEGuiTheme.drawBorderedRect(x, y, w, 22, disabled ? 0xFF625B51 : KOMEGuiTheme.COLOR_GOLD_DARK,
                disabled ? 0xFF3B3430 : KOMEGuiTheme.COLOR_PANEL_DARK);
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, label, x + w / 2, y + 7,
                disabled ? KOMEGuiTheme.COLOR_TEXT_DISABLED : KOMEGuiTheme.COLOR_TEXT_LIGHT);
        }
    }
}
