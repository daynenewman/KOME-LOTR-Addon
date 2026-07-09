package kome.client.gui;

import kome.client.KOMEConquestMapOverlay;
import kome.client.KOMEMinecraftClient;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEClientData;
import kome.common.network.KOMEPacketConquestClaim;
import kome.common.network.KOMEPacketConquestTransfer;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketTilePopulationUpdate;
import kome.common.network.KOMEPacketTileAllocationUpdate;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiConquestCapture extends GuiScreen {
    private static final int PANEL_MARGIN = 22;
    private static final int CARD_GAP = 12;
    private static final int ACTION_AREA_HEIGHT = 78;
    private static final int ACTION_BUTTON_HEIGHT = 24;
    private static final int ACTION_BUTTON_GAP = 12;
    private static final int HEADER_META_Y_OFFSET = 39;
    private static final int CONTENT_Y_OFFSET = 62;
    private static final int CARD_CONTENT_Y_OFFSET = 24;
    private static final int CARD_CONTROL_HEIGHT = 18;
    private static final int CARD_CONTROL_BOTTOM_PADDING = 13;
    private static final int CARD_CONTROL_LABEL_GAP = 13;
    private static final int ID_CLAIM = 0;
    private static final int ID_BACK = 1;
    private static final int ID_PREV_FACTION = 2;
    private static final int ID_NEXT_FACTION = 3;
    private static final int ID_TRANSFER = 4;
    private static final int ID_TRANSFER_MODE = 5;
    private static final int ID_ACCEPT = 6;
    private static final int ID_CANCEL_OFFER = 7;
    private static final int ID_MOVE = 8;
    private static final int ID_CANCEL_TRANSFER_MODE = 9;
    private static final int ID_ADD_OFFENSIVE = 10;
    private static final int ID_REMOVE_OFFENSIVE = 11;
    private static final int ID_ADD_DEFENSIVE = 12;
    private static final int ID_REMOVE_DEFENSIVE = 13;
    private static final int ID_ALLOCATE_OFFENSIVE = 14;
    private static final int ID_UNALLOCATE_OFFENSIVE = 15;
    private static final int ID_ALLOCATE_DEFENSIVE = 16;
    private static final int ID_UNALLOCATE_DEFENSIVE = 17;
    private static final int ID_SET_RECRUITMENT_TILE = 18;
    private static final int ID_VIEW_UNITS = 19;

    private final String tileId;
    private final String ownerFaction;
    private final String pendingFromFaction;
    private final String pendingToFaction;
    private final String viewerFaction;
    private final int offensivePop;
    private final int defensivePop;
    private final int mountedPop;
    private final int groundPop;
    private final int incomingPop;
    private final int outgoingPop;
    private final long incomingEtaMillis;
    private final int offensiveTotal;
    private final int offensiveUsed;
    private final int defensiveTotal;
    private final int defensiveUsed;
    private final int farmhandTotal;
    private final int farmhandUsed;
    private final boolean canClaim;
    private final boolean canTransfer;
    private final boolean canAcceptTransfer;
    private final boolean canCancelTransfer;
    private final boolean canMoveTroops;
    private final boolean canEditPopulation;
    private final int offensiveAllocated;
    private final int defensiveAllocated;
    private final int myOffensiveAllocated;
    private final int myOffensiveUsed;
    private final int myDefensiveAllocated;
    private final int myDefensiveUsed;
    private final String claimantName;
    private final String allocationSummary;
    private final boolean ownerHasKing;
    private final int myOffensivePop;
    private final int myDefensivePop;
    private final int myMountedPop;
    private final int myGroundPop;
    private final String activeRecruitmentTile;
    private final boolean canSetRecruitmentTile;
    private final String lotrWaypointKey;
    private final String lotrWaypointDisplayName;
    private final String lotrWaypointRegion;
    private int waypointLevel;
    private String currentRulingFaction = "";
    private String defaultRulingFaction = "";
    private String mapRegion = "";
    private final List transferFactions = new ArrayList();
    private int transferIndex;
    private boolean transferMode;
    private GuiTextField populationAmountField;
    private GuiTextField allocationPlayerField;
    private GuiTextField allocationAmountField;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, "", 0, 0, 0, 0, 0, 0, 0L);
    }

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, "", offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis);
    }

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, 0, 0, 0, 0, 0, 0, hasFaction(viewerFaction) && !safe(viewerFaction).equals(safe(ownerFaction)), safe(viewerFaction).equals(safe(ownerFaction)), hasFaction(viewerFaction) && safe(viewerFaction).equals(safe(pendingToFaction)), hasFaction(viewerFaction) && safe(viewerFaction).equals(safe(ownerFaction)) && hasPending(pendingFromFaction, pendingToFaction), offensivePop > 0, false, 0, 0, 0, 0, 0, 0, "", "", false);
    }

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, false, 0, 0, 0, 0, 0, 0, "", "", false);
    }

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, 0, 0, 0, 0, 0, 0, "", "", false);
    }

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, 0, 0, 0, 0, "", false);
    }

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, myOffensivePop, myDefensivePop, myMountedPop, myGroundPop, activeRecruitmentTile, canSetRecruitmentTile, "", "", "");
    }

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile, String lotrWaypointKey, String lotrWaypointDisplayName, String lotrWaypointRegion) {
        this.tileId = safe(tileId);
        this.ownerFaction = safe(ownerFaction);
        this.pendingFromFaction = safe(pendingFromFaction);
        this.pendingToFaction = safe(pendingToFaction);
        this.viewerFaction = safe(viewerFaction);
        this.offensivePop = Math.max(0, offensivePop);
        this.defensivePop = Math.max(0, defensivePop);
        this.mountedPop = Math.max(0, mountedPop);
        this.groundPop = Math.max(0, groundPop);
        this.incomingPop = Math.max(0, incomingPop);
        this.outgoingPop = Math.max(0, outgoingPop);
        this.incomingEtaMillis = incomingEtaMillis;
        this.offensiveTotal = Math.max(0, offensiveTotal);
        this.offensiveUsed = Math.max(0, offensiveUsed);
        this.defensiveTotal = Math.max(0, defensiveTotal);
        this.defensiveUsed = Math.max(0, defensiveUsed);
        this.farmhandTotal = Math.max(0, farmhandTotal);
        this.farmhandUsed = Math.max(0, farmhandUsed);
        this.canClaim = canClaim;
        this.canTransfer = canTransfer;
        this.canAcceptTransfer = canAcceptTransfer;
        this.canCancelTransfer = canCancelTransfer;
        this.canMoveTroops = canMoveTroops;
        this.canEditPopulation = canEditPopulation;
        this.offensiveAllocated = Math.max(0, offensiveAllocated);
        this.defensiveAllocated = Math.max(0, defensiveAllocated);
        this.myOffensiveAllocated = Math.max(0, myOffensiveAllocated);
        this.myOffensiveUsed = Math.max(0, myOffensiveUsed);
        this.myDefensiveAllocated = Math.max(0, myDefensiveAllocated);
        this.myDefensiveUsed = Math.max(0, myDefensiveUsed);
        this.claimantName = safe(claimantName);
        this.allocationSummary = safe(allocationSummary);
        this.ownerHasKing = ownerHasKing;
        this.myOffensivePop = Math.max(0, myOffensivePop);
        this.myDefensivePop = Math.max(0, myDefensivePop);
        this.myMountedPop = Math.max(0, myMountedPop);
        this.myGroundPop = Math.max(0, myGroundPop);
        this.activeRecruitmentTile = safe(activeRecruitmentTile);
        this.canSetRecruitmentTile = canSetRecruitmentTile;
        this.lotrWaypointKey = safe(lotrWaypointKey);
        this.lotrWaypointDisplayName = safe(lotrWaypointDisplayName);
        this.lotrWaypointRegion = safe(lotrWaypointRegion);
    }

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile, String lotrWaypointKey, String lotrWaypointDisplayName, String lotrWaypointRegion, int waypointLevel, String currentRulingFaction, String defaultRulingFaction, String mapRegion) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, myOffensivePop, myDefensivePop, myMountedPop, myGroundPop, activeRecruitmentTile, canSetRecruitmentTile, lotrWaypointKey, lotrWaypointDisplayName, lotrWaypointRegion);
        this.waypointLevel = waypointLevel;
        this.currentRulingFaction = safe(currentRulingFaction);
        this.defaultRulingFaction = safe(defaultRulingFaction);
        this.mapRegion = safe(mapRegion);
    }

    @Override
    public void initGui() {
        buildTransferFactions();
        computeLayout();
        buttonList.clear();
        populationAmountField = null;
        allocationPlayerField = null;
        allocationAmountField = null;
        int actionTop = panelY + panelH - ACTION_AREA_HEIGHT;
        int actionY = actionTop + 40;
        if (transferMode) {
            int selectorX = panelX + panelW / 2 - 98;
            buttonList.add(KOMEGuiButton.small(ID_PREV_FACTION, selectorX, actionY - 30, "<"));
            buttonList.add(KOMEGuiButton.small(ID_NEXT_FACTION, selectorX + 144, actionY - 30, ">"));
            GuiButton transfer = KOMEGuiButton.normal(ID_TRANSFER, panelX + panelW / 2 - 100, actionY, "Transfer");
            transfer.enabled = canTransfer && getTransferFaction() != null;
            buttonList.add(transfer);
            buttonList.add(KOMEGuiButton.normal(ID_CANCEL_TRANSFER_MODE, panelX + panelW / 2 + 4, actionY, "Cancel"));
            return;
        }
        if (canEditPopulation) {
            addPopulationControls();
            addAllocationControls();
        }
        if (isOwnedByPledge()) {
            int contentY = panelY + CONTENT_Y_OFFSET;
            int colW = (panelW - PANEL_MARGIN * 2 - CARD_GAP) / 2;
            int rowH = Math.max(112, (actionTop - contentY - CARD_GAP) / 2);
            GuiButton recruitment = new KOMEGuiButton(ID_SET_RECRUITMENT_TILE, panelX + PANEL_MARGIN + 10, contentY + rowH - 25, colW - 20, 18,
                tileId.equals(activeRecruitmentTile) ? "Active Recruitment Tile" : "Use as Recruitment Tile");
            recruitment.enabled = canSetRecruitmentTile && !tileId.equals(activeRecruitmentTile);
            buttonList.add(recruitment);
        }
        int contentY = panelY + CONTENT_Y_OFFSET;
        int colW = (panelW - PANEL_MARGIN * 2 - CARD_GAP) / 2;
        int rowH = Math.max(112, (actionTop - contentY - CARD_GAP) / 2);
        buttonList.add(new KOMEGuiButton(ID_VIEW_UNITS, panelX + PANEL_MARGIN + colW - 92,
            contentY + rowH + CARD_GAP + 5, 82, 18, "View Units"));
        int startX = panelX + PANEL_MARGIN;
        int gap = ACTION_BUTTON_GAP;
        int buttonCount = hasPendingTransfer() ? 5 : 4;
        int buttonW = Math.max(52, (panelW - PANEL_MARGIN * 2 - gap * (buttonCount - 1)) / buttonCount);
        GuiButton claim = new KOMEGuiButton(ID_CLAIM, startX, actionY, buttonW, ACTION_BUTTON_HEIGHT, "Claim");
        claim.enabled = canClaim;
        buttonList.add(claim);
        GuiButton transfer = new KOMEGuiButton(ID_TRANSFER_MODE, startX + (buttonW + gap), actionY, buttonW, ACTION_BUTTON_HEIGHT, "Sell/Trade");
        transfer.enabled = canTransfer && !transferFactions.isEmpty();
        buttonList.add(transfer);
        GuiButton move = new KOMEGuiButton(ID_MOVE, startX + (buttonW + gap) * 2, actionY, buttonW, ACTION_BUTTON_HEIGHT, "Companies");
        move.enabled = canMoveTroops;
        buttonList.add(move);
        if (hasPendingTransfer()) {
            GuiButton pending = new KOMEGuiButton(canAcceptTransfer ? ID_ACCEPT : ID_CANCEL_OFFER, startX + (buttonW + gap) * 3, actionY, buttonW, ACTION_BUTTON_HEIGHT, canAcceptTransfer ? "Accept" : "Cancel Offer");
            pending.enabled = canAcceptTransfer || canCancelTransfer;
            buttonList.add(pending);
            buttonList.add(new KOMEGuiButton(ID_BACK, startX + (buttonW + gap) * 4, actionY, buttonW, ACTION_BUTTON_HEIGHT, "Back"));
        } else {
            buttonList.add(new KOMEGuiButton(ID_BACK, startX + (buttonW + gap) * 3, actionY, buttonW, ACTION_BUTTON_HEIGHT, "Back"));
        }
    }

    private void addAllocationControls() {
        int margin = PANEL_MARGIN;
        int gap = CARD_GAP;
        int contentY = panelY + CONTENT_Y_OFFSET;
        int actionTop = panelY + panelH - ACTION_AREA_HEIGHT;
        int colW = (panelW - margin * 2 - gap) / 2;
        int rowH = Math.max(112, (actionTop - contentY - gap) / 2);
        int x = panelX + margin + colW + gap;
        int y = contentY + rowH + gap;
        int controlsY = controlRowY(y, rowH);
        allocationPlayerField = new GuiTextField(fontRendererObj, x + 10, controlsY + 1, 90, 16);
        allocationPlayerField.setText(mc.thePlayer == null ? "" : mc.thePlayer.getCommandSenderName());
        allocationAmountField = new GuiTextField(fontRendererObj, x + 104, controlsY + 1, 38, 16);
        allocationAmountField.setText("25");
        int buttonX = x + 147;
        int buttonW = Math.max(38, (colW - 157 - 12) / 4);
        int buttonGap = 3;
        GuiButton addOff = new KOMEGuiButton(ID_ALLOCATE_OFFENSIVE, buttonX, controlsY, buttonW, 18, "+Off");
        GuiButton removeOff = new KOMEGuiButton(ID_UNALLOCATE_OFFENSIVE, buttonX + (buttonW + buttonGap), controlsY, buttonW, 18, "-Off");
        GuiButton addDef = new KOMEGuiButton(ID_ALLOCATE_DEFENSIVE, buttonX + (buttonW + buttonGap) * 2, controlsY, buttonW, 18, "+Def");
        GuiButton removeDef = new KOMEGuiButton(ID_UNALLOCATE_DEFENSIVE, buttonX + (buttonW + buttonGap) * 3, controlsY, buttonW, 18, "-Def");
        addOff.enabled = canEditPopulation;
        removeOff.enabled = canEditPopulation;
        addDef.enabled = canEditPopulation;
        removeDef.enabled = canEditPopulation;
        buttonList.add(addOff);
        buttonList.add(removeOff);
        buttonList.add(addDef);
        buttonList.add(removeDef);
    }

    private void addPopulationControls() {
        int margin = PANEL_MARGIN;
        int gap = CARD_GAP;
        int contentY = panelY + CONTENT_Y_OFFSET;
        int actionTop = panelY + panelH - ACTION_AREA_HEIGHT;
        int colW = (panelW - margin * 2 - gap) / 2;
        int rowH = Math.max(112, (actionTop - contentY - gap) / 2);
        int x = panelX + margin + colW + gap;
        int y = contentY;
        int controlGap = 5;
        int controlY = controlRowY(y, rowH);
        int amountW = 48;
        int buttonStartX = x + 12 + amountW + 12;
        int buttonW = Math.max(42, (x + colW - 12 - buttonStartX - controlGap * 3) / 4);
        populationAmountField = new GuiTextField(fontRendererObj, x + 12, controlY + 1, amountW, 16);
        populationAmountField.setText("25");
        populationAmountField.setMaxStringLength(5);
        GuiButton addOff = new KOMEGuiButton(ID_ADD_OFFENSIVE, buttonStartX, controlY, buttonW, 18, "+Off");
        GuiButton removeOff = new KOMEGuiButton(ID_REMOVE_OFFENSIVE, buttonStartX + (buttonW + controlGap), controlY, buttonW, 18, "-Off");
        GuiButton addDef = new KOMEGuiButton(ID_ADD_DEFENSIVE, buttonStartX + (buttonW + controlGap) * 2, controlY, buttonW, 18, "+Def");
        GuiButton removeDef = new KOMEGuiButton(ID_REMOVE_DEFENSIVE, buttonStartX + (buttonW + controlGap) * 3, controlY, buttonW, 18, "-Def");
        addOff.enabled = canEditPopulation;
        removeOff.enabled = canEditPopulation;
        addDef.enabled = canEditPopulation;
        removeDef.enabled = canEditPopulation;
        buttonList.add(addOff);
        buttonList.add(removeOff);
        buttonList.add(addDef);
        buttonList.add(removeDef);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == ID_CLAIM) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestClaim(tileId));
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id == ID_BACK) {
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id == ID_PREV_FACTION) {
            transferIndex = wrap(transferIndex - 1, transferFactions.size());
        } else if (button.id == ID_NEXT_FACTION) {
            transferIndex = wrap(transferIndex + 1, transferFactions.size());
        } else if (button.id == ID_TRANSFER) {
            LOTRFaction target = getTransferFaction();
            if (target != null) {
                KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestTransfer(tileId, target.codeName(), KOMEPacketConquestTransfer.OFFER));
            }
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id == ID_TRANSFER_MODE) {
            transferMode = true;
            initGui();
        } else if (button.id == ID_ACCEPT) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestTransfer(tileId, pendingToFaction, KOMEPacketConquestTransfer.ACCEPT));
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id == ID_CANCEL_OFFER) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestTransfer(tileId, pendingToFaction, KOMEPacketConquestTransfer.CANCEL));
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id == ID_MOVE) {
            KOMEMinecraftClient.sendChat("/troops companies " + tileId);
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == ID_CANCEL_TRANSFER_MODE) {
            transferMode = false;
            initGui();
        } else if (button.id == ID_ADD_OFFENSIVE || button.id == ID_REMOVE_OFFENSIVE || button.id == ID_ADD_DEFENSIVE || button.id == ID_REMOVE_DEFENSIVE) {
            sendPopulationUpdate(button.id);
        } else if (button.id >= ID_ALLOCATE_OFFENSIVE && button.id <= ID_UNALLOCATE_DEFENSIVE) {
            sendAllocationUpdate(button.id);
        } else if (button.id == ID_SET_RECRUITMENT_TILE) {
            KOMEMinecraftClient.sendChat("/troops recruit " + tileId);
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id == ID_VIEW_UNITS) {
            String player = mc.thePlayer == null ? "" : mc.thePlayer.getCommandSenderName();
            KOMEMinecraftClient.sendChat("/population units " + player + " " + tileId);
            KOMEMinecraftClient.closePlayerScreen();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        computeLayout();
        KOMEGuiTheme.drawMainPanel(panelX, panelY, panelW, panelH);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Tile Command", panelX + 8, panelY + 8, panelW - 16);
        drawHeaderMeta();
        drawCards(mouseX, mouseY);
        if (populationAmountField != null) {
            populationAmountField.drawTextBox();
        }
        if (allocationPlayerField != null) {
            allocationPlayerField.drawTextBox();
            allocationAmountField.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawDisabledTooltip(mouseX, mouseY);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (populationAmountField != null && populationAmountField.textboxKeyTyped(c, key)) {
            return;
        }
        if (allocationPlayerField != null && (allocationPlayerField.textboxKeyTyped(c, key) || allocationAmountField.textboxKeyTyped(c, key))) {
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (populationAmountField != null) {
            populationAmountField.mouseClicked(mouseX, mouseY, button);
        }
        if (allocationPlayerField != null) {
            allocationPlayerField.mouseClicked(mouseX, mouseY, button);
            allocationAmountField.mouseClicked(mouseX, mouseY, button);
        }
    }

    private void computeLayout() {
        panelW = Math.min(760, width - 28);
        panelH = Math.min(430, height - 28);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
    }

    private void drawHeaderMeta() {
        int y = panelY + HEADER_META_Y_OFFSET;
        int leftX = panelX + PANEL_MARGIN;
        int ownerX = panelX + panelW / 3;
        int viewerX = panelX + panelW * 2 / 3;
        int ownerW = viewerX - ownerX - 14;
        int viewerW = panelX + panelW - PANEL_MARGIN - viewerX;
        fontRendererObj.drawString("Tile: " + KOMEGuiTheme.trimToWidth(fontRendererObj, tileId, ownerX - leftX - 18), leftX, y, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Ruler: " + KOMEGuiTheme.trimToWidth(fontRendererObj, rulingFactionLabel(currentRulingFaction.length() == 0 ? ownerFaction : currentRulingFaction), ownerW - 42), ownerX, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
        String viewer = "Your Faction: " + (hasViewerFaction() ? factionName(viewerFaction) : "No faction");
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, viewer, viewerW), viewerX, y, hasViewerFaction() ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_BAD);
    }

    private void drawCards(int mouseX, int mouseY) {
        int margin = PANEL_MARGIN;
        int gap = CARD_GAP;
        int contentY = panelY + CONTENT_Y_OFFSET;
        int actionTop = panelY + panelH - ACTION_AREA_HEIGHT;
        int colW = (panelW - margin * 2 - gap) / 2;
        int leftX = panelX + margin;
        int rightX = leftX + colW + gap;
        int rowH = Math.max(112, (actionTop - contentY - gap) / 2);
        drawStatusCard(leftX, contentY, colW, rowH, mouseX, mouseY);
        drawPopulationCard(rightX, contentY, colW, rowH, mouseX, mouseY);
        drawStationedCard(leftX, contentY + rowH + gap, colW, rowH, mouseX, mouseY);
        drawMovementCard(rightX, contentY + rowH + gap, colW, rowH, mouseX, mouseY);
        drawActionCard(panelX + margin, actionTop, panelW - margin * 2, ACTION_AREA_HEIGHT - 14, mouseX, mouseY);
    }

    private void drawStatusCard(int x, int y, int w, int h, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
        title("Tile Status", x, y, w);
        int lineY = y + CARD_CONTENT_Y_OFFSET;
        lineY = line(x, lineY, "ID", tileId, w);
        lineY = line(x, lineY, "Level", waypointLevel >= 1 && waypointLevel <= 3 ? String.valueOf(waypointLevel) : "Unknown", w);
        lineY = line(x, lineY, "Current Ruling Faction", rulingFactionLabel(currentRulingFaction.length() == 0 ? ownerFaction : currentRulingFaction), w);
        lineY = line(x, lineY, "Default Ruling Faction", rulingFactionLabel(defaultRulingFaction), w);
        lineY = line(x, lineY, "Region", mapRegion.length() == 0 ? regionLabel() : mapRegion, w);
        lineY = line(x, lineY, "LOTR Waypoint", lotrWaypointLabel(), w);
        if (hasPendingTransfer()) {
            lineY = line(x, lineY, "Transfer", factionName(pendingFromFaction) + " -> " + factionName(pendingToFaction), w);
        } else {
            lineY = line(x, lineY, "Transfer", "No pending offer", w);
        }
        line(x, lineY, "Recruitment", activeRecruitmentTile.length() == 0 ? "Automatic selection" : activeRecruitmentTile, w);
    }

    private void drawPopulationCard(int x, int y, int w, int h, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
        title("Tile Population", x, y, w);
        if (!hasPopulationData()) {
            KOMEGuiTheme.drawWrappedText(fontRendererObj, "Tile population data unavailable.", x + 8, y + CARD_CONTENT_Y_OFFSET, w - 16, KOMEGuiTheme.COLOR_TEXT_MUTED);
        } else {
            int offAvail = Math.max(0, offensiveTotal - offensiveUsed);
            int defAvail = Math.max(0, defensiveTotal - defensiveUsed);
            line(x, y + CARD_CONTENT_Y_OFFSET, "Offensive", offensiveUsed + "/" + offensiveTotal + " used, " + offAvail + " available", w);
            KOMEGuiTheme.drawProgressBar(fontRendererObj, x + 10, y + CARD_CONTENT_Y_OFFSET + 15, w - 20, 10, ratio(offensiveUsed, offensiveTotal), KOMEGuiTheme.COLOR_GOOD, "");
            line(x, y + CARD_CONTENT_Y_OFFSET + 31, "Defensive", defensiveUsed + "/" + defensiveTotal + " used, " + defAvail + " available", w);
            KOMEGuiTheme.drawProgressBar(fontRendererObj, x + 10, y + CARD_CONTENT_Y_OFFSET + 46, w - 20, 10, ratio(defensiveUsed, defensiveTotal), KOMEGuiTheme.COLOR_WARN, "");
            if (farmhandTotal > 0 || farmhandUsed > 0) {
                line(x, y + CARD_CONTENT_Y_OFFSET + 61, "Farmhands", farmhandUsed + "/" + farmhandTotal, w);
            }
        }
        if (populationAmountField != null) {
            int labelY = controlLabelY(y, h);
            fontRendererObj.drawString("Amount", x + 12, labelY, KOMEGuiTheme.COLOR_TEXT_MUTED);
            if (!canEditPopulation) {
                fontRendererObj.drawString("View only", x + w - 66, labelY, KOMEGuiTheme.COLOR_TEXT_DISABLED);
            }
        }
    }

    private void drawStationedCard(int x, int y, int w, int h, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
        title("Stationed Units / " + stationedCompanyCount() + " Companies", x, y, w, 104);
        int lineY = y + CARD_CONTENT_Y_OFFSET;
        lineY = line(x, lineY, "Faction Offensive", offensivePop + " movable", w);
        lineY = line(x, lineY, "Faction Defensive", defensivePop + " immobile", w);
        lineY = line(x, lineY, "Faction Split", mountedPop + " mounted / " + groundPop + " ground", w);
        lineY = line(x, lineY, "My Offensive", myOffensivePop + " (" + myMountedPop + " mounted, " + myGroundPop + " ground)", w);
        lineY = line(x, lineY, "My Defensive", myDefensivePop + " immobile", w);
        lineY = line(x, lineY, "Moving Out", movementSummary(true), w);
        line(x, lineY, "Incoming", movementSummary(false), w);
    }

    private int stationedCompanyCount() {
        int count = 0;
        for (KOMEArmyCompany company : KOMEClientData.INSTANCE.armyCompanies.values()) {
            if (company != null && !company.isMoving() && tileId.equals(company.currentTile)
                    && KOMEAlliance.normalizeFactionKey(ownerFaction).equals(KOMEAlliance.normalizeFactionKey(company.faction))) {
                count++;
            }
        }
        return count;
    }

    private String movementSummary(boolean outgoing) {
        int total = outgoing ? outgoingPop : incomingPop;
        if (total <= 0) {
            return "None";
        }
        KOMEArmyMovementOrder first = null;
        int orders = 0;
        long earliest = Long.MAX_VALUE;
        for (KOMEArmyMovementOrder order : KOMEClientData.INSTANCE.armyMovements.values()) {
            if (order == null || !order.isMoving()) {
                continue;
            }
            boolean matches = outgoing ? tileId.equals(order.originTile) : tileId.equals(order.destinationTile);
            if (!matches) {
                continue;
            }
            orders++;
            if (first == null || order.arrivalMillis < first.arrivalMillis) {
                first = order;
            }
            earliest = Math.min(earliest, order.getRemainingMillis(System.currentTimeMillis()));
        }
        if (first == null) {
            return total + " pop";
        }
        String otherTile = outgoing ? first.destinationTile : first.originTile;
        String direction = outgoing ? " -> " : " <- ";
        String extra = orders > 1 ? ", +" + (orders - 1) + " order" + (orders == 2 ? "" : "s") : "";
        String company = first.companyName == null || first.companyName.length() == 0 ? "Company" : first.companyName;
        return company + ": " + total + " pop / " + first.units.size() + " units" + direction + otherTile + ", ETA "
            + formatDuration(earliest == Long.MAX_VALUE ? 0L : earliest) + extra;
    }

    private void drawMovementCard(int x, int y, int w, int h, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
        title("Population Allocation", x, y, w);
        int lineY = y + CARD_CONTENT_Y_OFFSET;
        lineY = line(x, lineY, "Offensive", offensiveAllocated + " allocated, " + Math.max(0, offensiveTotal - offensiveAllocated) + " unallocated", w);
        lineY = line(x, lineY, "Defensive", defensiveAllocated + " allocated, " + Math.max(0, defensiveTotal - defensiveAllocated) + " unallocated", w);
        lineY = line(x, lineY, "My Offensive", myOffensiveUsed + "/" + myOffensiveAllocated + " used, " + Math.max(0, myOffensiveAllocated - myOffensiveUsed) + " available", w);
        lineY = line(x, lineY, "My Defensive", myDefensiveUsed + "/" + myDefensiveAllocated + " used, " + Math.max(0, myDefensiveAllocated - myDefensiveUsed) + " available", w);
        if (!ownerHasKing && claimantName.length() > 0) {
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "No king: tile population assigned to " + claimantName, w - 20), x + 10, lineY + 1, KOMEGuiTheme.COLOR_WARN);
            lineY += 12;
        }
        if (allocationSummary.length() > 0) {
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, allocationSummary, w - 20), x + 10, lineY + 1, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        if (allocationPlayerField != null) {
            fontRendererObj.drawString(canEditPopulation ? "Player / amount" : "Allocation management requires king/admin", x + 10, controlLabelY(y, h), canEditPopulation ? KOMEGuiTheme.COLOR_TEXT_MUTED : KOMEGuiTheme.COLOR_TEXT_DISABLED);
        }
        if (canEditPopulation) {
            String arrival = "Arrival Point: stand there and run /troops arrival set " + tileId;
            int arrivalY = Math.max(lineY + 2, controlLabelY(y, h) - 12);
            if (arrivalY + 8 < controlLabelY(y, h)) {
                fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, arrival, w - 20), x + 10, arrivalY, KOMEGuiTheme.COLOR_TEXT_MUTED);
            }
        }
    }

    private static int controlRowY(int y, int h) {
        return y + h - CARD_CONTROL_BOTTOM_PADDING - CARD_CONTROL_HEIGHT;
    }

    private static int controlLabelY(int y, int h) {
        return controlRowY(y, h) - CARD_CONTROL_LABEL_GAP;
    }

    private void drawActionCard(int x, int y, int w, int h, int mouseX, int mouseY) {
        KOMEGuiTheme.drawSubPanel(x, y, w, h);
        if (transferMode) {
            LOTRFaction target = getTransferFaction();
            String text = target == null ? "No valid receiver faction" : "Receiver: " + target.factionName();
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, KOMEGuiTheme.trimToWidth(fontRendererObj, text, w - 180), x + w / 2, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        } else if (hasPendingTransfer()) {
            String pending = "Pending: " + factionName(pendingFromFaction) + " -> " + factionName(pendingToFaction);
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, KOMEGuiTheme.trimToWidth(fontRendererObj, pending, w - 20), x + w / 2, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        }
    }

    private void title(String text, int x, int y, int w) {
        title(text, x, y, w, 0);
    }

    private void title(String text, int x, int y, int w, int rightInset) {
        int usableW = Math.max(24, w - 24 - rightInset);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, text, usableW), x + 12, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        KOMEGuiTheme.drawDivider(x + 12, y + 18, usableW);
    }

    private int line(int x, int y, String label, String value, int w) {
        int valueX = x + Math.min(142, Math.max(110, w / 2));
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, label + ":", valueX - x - 18), x + 12, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, value, x + w - valueX - 12), valueX, y, KOMEGuiTheme.COLOR_TEXT);
        return y + 11;
    }

    private void drawDisabledTooltip(int mouseX, int mouseY) {
        for (Object object : buttonList) {
            if (!(object instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) object;
            if (button.visible && !button.enabled && KOMEGuiTheme.isHovered(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height)) {
                List lines = new ArrayList();
                lines.add(disabledReason(button.id));
                KOMEGuiTheme.drawTooltip(fontRendererObj, lines, mouseX, mouseY, width, height);
                return;
            }
        }
    }

    private String disabledReason(int id) {
        if (id == ID_ADD_OFFENSIVE || id == ID_REMOVE_OFFENSIVE || id == ID_ADD_DEFENSIVE || id == ID_REMOVE_DEFENSIVE) {
            return "Only admins or the owning faction's king can edit tile population.";
        }
        if (id >= ID_ALLOCATE_OFFENSIVE && id <= ID_UNALLOCATE_DEFENSIVE) {
            return "Only admins or the owning faction's king can manage allocations.";
        }
        if (id == ID_SET_RECRUITMENT_TILE) {
            if (tileId.equals(activeRecruitmentTile)) {
                return "This is already your active recruitment tile.";
            }
            return "You need an allocation here or player reserve population to use this tile for recruitment.";
        }
        if (id == ID_CLAIM) {
            return hasViewerFaction() ? "This tile is already controlled by your faction." : "Pledge to a faction before claiming tiles.";
        }
        if (id == ID_TRANSFER_MODE || id == ID_TRANSFER) {
            if (!isOwnedByPledge()) {
                return "Only the owning faction can offer this tile.";
            }
            return "Only the owning faction's king can offer tile transfers.";
        }
        if (id == ID_ACCEPT) {
            return "Only the receiving faction's king can accept this offer.";
        }
        if (id == ID_CANCEL_OFFER) {
            return "Only the owning faction's king can cancel this offer.";
        }
        if (id == ID_MOVE) {
            return "No controllable movement company is stationed in this tile.";
        }
        return "This action is not available.";
    }

    private void sendPopulationUpdate(int buttonId) {
        int amount = parseAmount();
        if (amount <= 0) {
            return;
        }
        boolean defensive = buttonId == ID_ADD_DEFENSIVE || buttonId == ID_REMOVE_DEFENSIVE;
        boolean add = buttonId == ID_ADD_OFFENSIVE || buttonId == ID_ADD_DEFENSIVE;
        KOMEPacketHandler.network.sendToServer(new KOMEPacketTilePopulationUpdate(tileId, defensive ? "defensive" : "offensive", amount, add));
    }

    private int parseAmount() {
        if (populationAmountField == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(populationAmountField.getText().trim()));
        } catch (NumberFormatException e) {
            populationAmountField.setText("1");
            return 1;
        }
    }

    private void sendAllocationUpdate(int buttonId) {
        if (allocationPlayerField == null || allocationAmountField == null) {
            return;
        }
        int amount;
        try {
            amount = Math.max(0, Integer.parseInt(allocationAmountField.getText().trim()));
        } catch (NumberFormatException e) {
            allocationAmountField.setText("1");
            amount = 1;
        }
        String player = allocationPlayerField.getText().trim();
        if (amount <= 0 || player.length() == 0) {
            return;
        }
        boolean defensive = buttonId == ID_ALLOCATE_DEFENSIVE || buttonId == ID_UNALLOCATE_DEFENSIVE;
        boolean add = buttonId == ID_ALLOCATE_OFFENSIVE || buttonId == ID_ALLOCATE_DEFENSIVE;
        KOMEPacketHandler.network.sendToServer(new KOMEPacketTileAllocationUpdate(tileId, player, defensive ? "defensive" : "offensive", amount, add));
    }

    private boolean hasViewerFaction() {
        return viewerFaction.length() > 0;
    }

    private boolean isOwnedByPledge() {
        return hasViewerFaction() && viewerFaction.equals(ownerFaction);
    }

    private boolean hasPendingTransfer() {
        return hasPending(pendingFromFaction, pendingToFaction);
    }

    private boolean hasPopulationData() {
        return offensiveTotal > 0 || offensiveUsed > 0 || defensiveTotal > 0 || defensiveUsed > 0 || farmhandTotal > 0 || farmhandUsed > 0;
    }

    private String lotrWaypointLabel() {
        if (lotrWaypointDisplayName.length() == 0 && lotrWaypointKey.length() == 0) {
            return "Missing";
        }
        String display = lotrWaypointDisplayName.length() == 0 ? lotrWaypointKey : lotrWaypointDisplayName;
        if (canEditPopulation && lotrWaypointKey.length() > 0) {
            String suffix = lotrWaypointRegion.length() == 0 ? lotrWaypointKey : lotrWaypointKey + " / " + lotrWaypointRegion;
            return display + " (" + suffix + ")";
        }
        return display;
    }

    private String regionLabel() {
        if (mapRegion.length() > 0) {
            return mapRegion;
        }
        if (lotrWaypointRegion.length() > 0) {
            return lotrWaypointRegion;
        }
        return "Unknown";
    }

    private static String rulingFactionLabel(String faction) {
        return faction == null || faction.trim().length() == 0 ? "Unclaimed" : factionName(faction);
    }

    private void buildTransferFactions() {
        transferFactions.clear();
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction() && (!hasViewerFaction() || !viewerFaction.equals(faction.codeName()))) {
                transferFactions.add(faction);
            }
        }
        if (transferIndex >= transferFactions.size()) {
            transferIndex = 0;
        }
    }

    private LOTRFaction getTransferFaction() {
        return transferFactions.isEmpty() ? null : (LOTRFaction) transferFactions.get(transferIndex);
    }

    private static int wrap(int value, int size) {
        return size <= 0 ? 0 : (value % size + size) % size;
    }

    private static String factionName(String factionKey) {
        return KOMEAlliance.displayFactionName(factionKey);
    }

    private static String factionNameOr(String factionKey, String fallback) {
        return factionKey == null || factionKey.trim().isEmpty() ? fallback : factionName(factionKey);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String formatName(String key) {
        if (key == null || key.trim().length() == 0) {
            return "None";
        }
        String[] words = key.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static boolean hasFaction(String value) {
        return value != null && value.length() > 0;
    }

    private static boolean hasPending(String from, String to) {
        return from != null && from.length() > 0 && to != null && to.length() > 0;
    }

    private static float ratio(int used, int total) {
        return total <= 0 ? 0.0f : used / (float) total;
    }

    private static String formatDuration(long millis) {
        long minutes = Math.max(0L, (millis + 59999L) / 60000L);
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long mins = minutes % 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }
}
