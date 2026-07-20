package kome.client.gui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.client.KOMEQuotaLedgerOverlay;
import kome.common.network.KOMEPacketConquestCaptureGui;
import kome.common.network.KOMEPacketPledgeDepartureData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ScreenShotHelper;

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

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("kome.guiCapture")) return;
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
        mc.gameSettings.guiScale = Integer.getInteger("kome.guiCaptureScale", 2);
        mc.resize(mc.displayWidth, mc.displayHeight);
        prepareAllianceData();
        addScreens();
    }

    private void addScreens() {
        add("alliance-list", new ScreenFactory() { public GuiScreen create() { return new KOMEGuiAlliance(); }});
        add("alliance-create", new ScreenFactory() { public GuiScreen create() {
            KOMEGuiAlliance gui = new KOMEGuiAlliance(); gui.setVisualTestCreateMode(true); return gui;
        }});
        addDetail("alliance-overview", 0, 0, false);
        addDetail("alliance-requirements", 1, 0, false);
        addDetail("alliance-benefits", 2, 0, false);
        addDetail("alliance-military", 3, 1, false);
        addDetail("alliance-break-confirmation", 0, 1, true);
        add("alliance-ledger", new ScreenFactory() { public GuiScreen create() {
            prepareLedgerData();
            return new LedgerVisualPreview();
        }});
        add("war-records", new ScreenFactory() { public GuiScreen create() {
            prepareWarData(); KOMEGuiServerRecords.setVisualTestWarMode(true); return new KOMEGuiServerRecords();
        }});
        add("allied-tile-confirmation", new ScreenFactory() { public GuiScreen create() { return captureGui(); }});
        add("pledge-departure-preview", new ScreenFactory() { public GuiScreen create() { return pledgeGui(); }});
    }

    private void addDetail(String name, final int view, final int type, final boolean confirmBreak) {
        add(name, new ScreenFactory() { public GuiScreen create() {
            KOMEGuiAlliance.Record record = KOMEGuiAlliance.recordFor("gondor", "rohan");
            KOMEGuiAllianceDetail gui = new KOMEGuiAllianceDetail(record);
            gui.setVisualTestView(view, type, confirmBreak);
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
        File output = new File(System.getProperty("kome.guiCaptureDir", "gui-captures"));
        output.mkdirs();
        String label = System.getProperty("kome.guiCaptureLabel", "scale");
        ScreenShotHelper.saveScreenshot(output, label + "-" + screenName + ".png",
            mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private static void prepareAllianceData() {
        List lines = new ArrayList();
        lines.add("SUMMARY\t2");
        lines.add("VIEWER\tgondor\tGondor\t1\t1\t1\t0");
        lines.add("CONFIG\tstandard\t0\t1\t0");
        lines.add("KING\tgondor");
        lines.add("KING\trohan");
        lines.add("REQUEST_OPTION\trohan\t1\t1\t1\t1");
        lines.add("REQUEST_OPTION\thighelves\t1\t0\t1\t1");
        long grace = System.currentTimeMillis() + 172800000L;
        lines.add(join(new String[] {"ALLIANCE","gondor","rohan","Gondor","Rohan","1","2","-2","Steward Ecthelion","",
            "T3 provisions","48","Trade stores","20","74","638","0","222","1000","300","active","pending","active",
            "gondor","rohan","1","0","2","1","0","2","0","0",String.valueOf(grace),"0","rohan","rohan","rohan",
            "T2: 32/64 White Stone","T3: 48/96 Lembas","T1: quota not rolled","T2: 64/64 White Stone","T3: 96/96 Lembas","T1: quota not rolled"}));
        lines.add(join(new String[] {"ALLIANCE","gondor","highelves","Gondor","High Elves","0","-1","1","Lady Miriel","",
            "","0","","0","12","0","0","52","0","0","active","active","none","gondor","highelves","0","1","-1","0","1","-1","0","0","0","0","highelves","highelves","highelves","T1: quota not rolled","None","T2: 8/40 Mallorn Goods","T1: complete","None","T2: 40/40 Mallorn Goods"}));
        String pair = "gondor|rohan";
        addTrack(lines, pair, "gondor", "rohan", "civil", 1, 2, "White Stone", 64, 32, "Allied trades", 25, 18, 0, 0, "Civil access active");
        addTrack(lines, pair, "rohan", "gondor", "civil", 1, 2, "White Stone", 64, 64, "Allied trades", 25, 25, 0, 0, "Partner complete");
        addTrack(lines, pair, "gondor", "rohan", "military", 2, 3, "Lembas", 96, 48, "Eligible kills", 1000, 638, 300, 214, "War support authorized");
        addTrack(lines, pair, "rohan", "gondor", "military", 2, 3, "Lembas", 96, 96, "Eligible kills", 1000, 1000, 300, 327, "Partner complete");
        lines.add("MILITARY_CONTEXT\t" + pair + "\tgondor\trohan\tACTIVE\tSteward Ecthelion\tking-gondor\tKing Eomer\tking-rohan\tNorthern Coalition War\tMordor, Isengard\t420\t214\t206\tAll war support is server-authorized.");
        lines.add("MILITARY_CONTEXT\t" + pair + "\trohan\tgondor\tACTIVE\tKing Eomer\tking-rohan\tSteward Ecthelion\tking-gondor\tNorthern Coalition War\tMordor, Isengard\t380\t192\t188\tAll war support is server-authorized.");
        lines.add("MILITARY_COMPANY\t" + pair + "\tgondor\trohan\tcompany-westfold\tWestfold Riders\tKing Eomer\tSteward Ecthelion\t1\tNorthern Coalition War\tDEFENSIVE\tSTATIONED\tNONE\tAuthorized allied stewardship\t84\twithdraw, recall, inspect");
        KOMEGuiAlliance.update(lines);
    }

    private static void addTrack(List lines, String pair, String side, String partner, String type, int tier, int target,
            String quota, int required, int delivered, String activity, int activityRequired, int activityProgress,
            int populationRequired, int populationProgress, String reason) {
        boolean complete = delivered >= required && activityProgress >= activityRequired && populationProgress >= populationRequired;
        lines.add(join(new String[] {"TRACK",pair,side,partner,type,"active",String.valueOf(tier),String.valueOf(target),quota,
            String.valueOf(required),String.valueOf(delivered),activity,String.valueOf(activityRequired),String.valueOf(activityProgress),
            String.valueOf(populationRequired),String.valueOf(populationProgress),complete ? "1" : "0","0","0","0","0","0",
            "Authoritative server benefit","1",reason}));
    }

    private static void prepareLedgerData() {
        List lines = new ArrayList();
        lines.add("SUMMARY\tGondor\tRohan\tgondor\trohan");
        lines.add("VIEWER\tGondor\t1\t1");
        lines.add("PROGRESS\tCivil\tActive\tT2 cooperative goods\tGoods delivered\t32\t64");
        lines.add("PROGRESS\tMilitary\tActive\tT3 provisions\tGoods delivered\t48\t96");
        lines.add("PROGRESS\tTrade\tPending\tAwaiting acceptance\tGoods delivered\t0\t40");
        lines.add("QUOTA\tCivil\tWhite Stone\t32\t64\tblocks");
        lines.add("QUOTA\tMilitary\tLembas\t48\t96\titems");
        lines.add("CLAIM\tIncoming goods are held for Rohan's king\t0\tNo incoming goods are currently claimable");
        lines.add("SWITCH\tView Rohan Ledger\trohan\tgondor\t1");
        KOMEQuotaLedgerOverlay.update(lines);
    }

    private static void prepareWarData() {
        List lines = new ArrayList();
        lines.add("SUMMARY\t4\t12\t1");
        lines.add(join(new String[] {"WAR","war-north-001","Northern Coalition War","ACTIVE","Free Peoples","Gondor, Rohan, High Elves",
            "Shadow Host","Mordor, Isengard","2026-07-18 08:30","Amon Sul captured by Gondor","3","Succession grace affects one support company",
            "Amon Sul: Mordor -> Gondor; Westfold: Isengard -> Rohan","Gondor joined by declaration; Rohan joined by defense pact",
            "Westfold Riders (Military T3, server-authorized)","Steward Ecthelion; King Eomer","Westfold Riders: 84 offensive population",
            "One withdrawal completes in 1d 4h","Created by Steward Ecthelion; Rohan joined automatically","None","None","None",
            "Gondor=DECLARATION; Rohan=DEFENSE; Mordor=DECLARATION; Isengard=COALITION","Rohan->Gondor: Westfold Riders"}));
        KOMEGuiServerRecords.update(lines);
    }

    private static GuiScreen captureGui() {
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
        return new KOMEGuiConquestCapture(data);
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
            String[] types = {"Civil", "Military", "Trade"};
            String[] states = {"Active", "Active", "Pending"};
            int[] delivered = {32, 48, 0};
            int[] required = {64, 96, 40};
            int cardW = 185;
            for (int i = 0; i < 3; i++) {
                int x = left + 18 + i * (cardW + 14);
                KOMEGuiTheme.drawCard(x, top + 128, cardW, 66, false);
                fontRendererObj.drawString(types[i], x + 10, top + 135, KOMEGuiTheme.COLOR_GOLD);
                int chipW = KOMEGuiTheme.statusChipWidth(fontRendererObj, states[i]);
                KOMEGuiTheme.drawStatusChip(fontRendererObj, states[i], i == 2 ? KOMEGuiTheme.Status.WARNING : KOMEGuiTheme.Status.ACTIVE,
                    x + cardW - chipW - 10, top + 133, chipW);
                fontRendererObj.drawString(i == 2 ? "Awaiting acceptance" : "Rolled cooperative goods", x + 10, top + 155, KOMEGuiTheme.COLOR_TEXT_MUTED);
                KOMEGuiTheme.drawProgressBar(fontRendererObj, x + 10, top + 172, cardW - 20, 11,
                    delivered[i] / (float) required[i], KOMEGuiTheme.COLOR_GOLD,
                    delivered[i] + " / " + required[i] + " delivered");
            }
            KOMEGuiTheme.drawSubPanel(left + 18, top + 204, 584, 56);
            fontRendererObj.drawString("Current Quotas", left + 28, top + 211, KOMEGuiTheme.COLOR_GOLD);
            fontRendererObj.drawString("Civil: 32/64 blocks of White Stone", left + 28, top + 227, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString("Military: 48/96 items of Lembas", left + 28, top + 239, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString("Trade: awaiting acceptance", left + 28, top + 251, KOMEGuiTheme.COLOR_WARN);
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
