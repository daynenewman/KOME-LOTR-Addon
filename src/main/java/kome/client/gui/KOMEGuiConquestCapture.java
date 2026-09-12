package kome.client.gui;

import kome.client.KOMEConquestMapOverlay;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEHalfHourService;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEPopulationGraph;
import kome.common.network.KOMEPacketConquestClaim;
import kome.common.network.KOMEPacketConquestCaptureGui;
import kome.common.network.KOMEPacketBuildAction;
import kome.common.data.KOMEBuildType;
import kome.common.network.KOMEPacketConquestTransfer;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketTilePopulationUpdate;
import kome.common.network.KOMEPacketTileAllocationUpdate;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiConquestCapture extends GuiScreen {
    private static final int PANEL_MARGIN = 22;
    private static final int CARD_GAP = 12;
    private static final int ACTION_AREA_HEIGHT = 78;
    private static final int ACTION_BUTTON_HEIGHT = 24;
    private static final int ACTION_BUTTON_GAP = 12;
    private static final int HEADER_META_Y_OFFSET = 39;
    private static final int CONTENT_Y_OFFSET = 88;
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
    private static final int ID_TAB_BUILDS = 20;
    private static final int ID_TAB_POPULATION = 21;
    private static final int ID_TAB_ALLOCATIONS = 22;
    private static final int ID_BUILD_NEW = 23;
    private static final int ID_BUILD_LIST = 24;
    private static final int ID_BUILD_CONTRIBUTE = 25;
    private static final int ID_BUILD_RENAME = 26;
    private static final int ID_BUILD_DELETE = 27;
    private static final int ID_BUILD_SUBMIT_CREATE = 29;
    private static final int ID_BUILD_OWNER_PREV = 30;
    private static final int ID_BUILD_OWNER_NEXT = 31;
    private static final int ID_BUILD_OFF_MINUS = 32;
    private static final int ID_BUILD_OFF_PLUS = 33;
    private static final int ID_BUILD_DEF_MINUS = 34;
    private static final int ID_BUILD_DEF_PLUS = 35;
    private static final int ID_BUILD_SUBMIT_CONTRIBUTION = 36;
    private static final int ID_BUILD_SUBMIT_RENAME = 37;
    private static final int ID_BUILD_VIEW_BASE = 100;
    private static final int ID_BUILD_APPROVE_BASE = 200;
    private static final int ID_BUILD_REJECT_BASE = 300;
    private static final int ID_BUILD_REMOVE_BASE = 400;
    private static final int BUILD_MODE_LIST = 0;
    private static final int BUILD_MODE_DETAIL = 1;
    private static final int BUILD_MODE_CREATE = 2;
    private static final int BUILD_MODE_CONTRIBUTE = 3;
    private static final int BUILD_MODE_RENAME = 4;
    private static final int POPULATION_POOL_ROW_HEIGHT = 104;
    private static final int COLOR_POOL_USED = 0xFF8E2F2F;
    private static final int COLOR_POOL_AVAILABLE = 0xFFD6A04A;
    private static final int COLOR_POOL_INACCESSIBLE = 0xFF57504A;

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
    private boolean claimConfirmationArmed;
    private String claimWarning = "";
    private String claimWarDestination = "";
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
    private float renderScale = 1.0F;
    private int logicalWidth;
    private int logicalHeight;
    private final KOMEGuiConfirmationDialog confirmation = new KOMEGuiConfirmationDialog();
    private boolean confirmationDismissed;
    private final List buildViews = new ArrayList();
    private final List populationPoolViews = new ArrayList();
    private final List selectablePopulationOwners = new ArrayList();
    private int viewerDimension;
    private double viewerWorldX;
    private double viewerWorldY;
    private double viewerWorldZ;
    private int activeTab;
    private int buildMode;
    private int selectedBuildIndex = -1;
    private int buildScroll;
    private int poolScroll;
    private int contributionScroll;
    private int populationOwnerIndex;
    private int editHalfHours;
    private KOMEBuildType editBuildType = KOMEBuildType.NORMAL;
    private GuiTextField buildNameField;
    private GuiTextField buildHoursField;
    private String buildHoursValidation = "";
    private String populationHoverTooltip = "";
    private String pendingDestructiveBuildAction = "";
    private String pendingDestructiveBuildId = "";

    public KOMEGuiConquestCapture(KOMEPacketConquestCaptureGui message) {
        this(message.tileId, message.ownerFaction, message.pendingFromFaction, message.pendingToFaction,
            message.viewerFaction, message.offensivePop, message.defensivePop, message.mountedPop, message.groundPop,
            message.incomingPop, message.outgoingPop, message.incomingEtaMillis, message.offensiveTotal, message.offensiveUsed,
            message.defensiveTotal, message.defensiveUsed, message.farmhandTotal, message.farmhandUsed, message.canClaim,
            message.canTransfer, message.canAcceptTransfer, message.canCancelTransfer, message.canMoveTroops,
            message.canEditPopulation, message.offensiveAllocated, message.defensiveAllocated, message.myOffensiveAllocated,
            message.myOffensiveUsed, message.myDefensiveAllocated, message.myDefensiveUsed, message.claimantName,
            message.allocationSummary, message.ownerHasKing, message.myOffensivePop, message.myDefensivePop,
            message.myMountedPop, message.myGroundPop, message.activeRecruitmentTile, message.canSetRecruitmentTile,
            message.lotrWaypointKey, message.lotrWaypointDisplayName, message.lotrWaypointRegion, message.waypointLevel,
            message.currentRulingFaction, message.defaultRulingFaction, message.mapRegion);
        claimConfirmationArmed = message.claimConfirmationArmed;
        claimWarning = safe(message.claimWarning);
        claimWarDestination = safe(message.claimWarDestination);
        buildViews.addAll(message.builds);
        populationPoolViews.addAll(message.populationPools);
        selectablePopulationOwners.addAll(message.selectablePopulationOwners);
        viewerDimension = message.viewerDimension;
        viewerWorldX = message.viewerX;
        viewerWorldY = message.viewerY;
        viewerWorldZ = message.viewerZ;
        if (message.focusBuildId != null && message.focusBuildId.length() > 0) {
            for (int i = 0; i < buildViews.size(); i++) {
                KOMEPacketConquestCaptureGui.BuildView view =
                    (KOMEPacketConquestCaptureGui.BuildView) buildViews.get(i);
                if (message.focusBuildId.equals(view.id)) {
                    selectedBuildIndex = i;
                    buildMode = BUILD_MODE_DETAIL;
                    break;
                }
            }
        }
    }

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

    void setVisualTestState(int tab, int mode, int selectedIndex) {
        activeTab = clamp(tab, 0, 2);
        buildMode = clamp(mode, BUILD_MODE_LIST, BUILD_MODE_RENAME);
        selectedBuildIndex = selectedIndex;
        claimConfirmationArmed = false;
        confirmationDismissed = true;
    }

    @Override
    public void initGui() {
        buildTransferFactions();
        computeLayout();
        buttonList.clear();
        populationAmountField = null;
        allocationPlayerField = null;
        allocationAmountField = null;
        buildNameField = null;
        buildHoursField = null;
        addTabButtons();
        if (claimConfirmationArmed && !confirmationDismissed && !confirmation.isVisible()) {
            showClaimConfirmation();
        }
        if (activeTab == 0) {
            initBuildControls();
            return;
        }
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
        KOMEGuiButton claim = new KOMEGuiButton(ID_CLAIM, startX, actionY, buttonW, ACTION_BUTTON_HEIGHT,
            claimConfirmationArmed ? "Confirm Hostile Claim" : "Claim");
        if (claimConfirmationArmed) claim.setStyle(KOMEGuiButton.Style.DESTRUCTIVE);
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

    private void addTabButtons() {
        int x = panelX + PANEL_MARGIN;
        int y = panelY + 59;
        int width = Math.max(62, panelW - PANEL_MARGIN * 2);
        KOMEGuiButton builds = KOMEGuiButton.tab(ID_TAB_BUILDS, x, y, width, "Builds", activeTab == 0);
        buttonList.add(builds);
    }

    private void initBuildControls() {
        int contentY = panelY + CONTENT_Y_OFFSET;
        int footerY = panelY + panelH - 32;
        buttonList.add(new KOMEGuiButton(ID_BACK, panelX + PANEL_MARGIN, footerY, 104, 22, "Back to Map"));
        if (buildMode == BUILD_MODE_LIST) {
            GuiButton create = new KOMEGuiButton(ID_BUILD_NEW, panelX + panelW - PANEL_MARGIN - 132, footerY,
                132, 22, "Create Build");
            create.enabled = !selectablePopulationOwners.isEmpty();
            buttonList.add(create);
            int visible = buildVisibleRows();
            buildScroll = clamp(buildScroll, 0, Math.max(0, buildViews.size() - visible));
            for (int row = 0; row < visible && buildScroll + row < buildViews.size(); row++) {
                int y = contentY + 27 + row * 58;
                buttonList.add(new KOMEGuiButton(ID_BUILD_VIEW_BASE + row,
                    panelX + panelW - PANEL_MARGIN - 78, y + 17, 66, 19, "Details"));
            }
            return;
        }
        buttonList.add(new KOMEGuiButton(ID_BUILD_LIST, panelX + 132, footerY, 94, 22, "Build List"));
        KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
        if (buildMode == BUILD_MODE_CREATE || buildMode == BUILD_MODE_RENAME) {
            int editorX = panelX + PANEL_MARGIN;
            int editorW = panelW - PANEL_MARGIN * 2;
            buildNameField = new GuiTextField(fontRendererObj, editorX + 20, contentY + 39,
                Math.max(80, editorW - 40), 18);
            buildNameField.setMaxStringLength(40);
            buildNameField.setText(buildMode == BUILD_MODE_RENAME && selected != null ? selected.name : "");
        }
        if (buildMode == BUILD_MODE_CREATE || buildMode == BUILD_MODE_CONTRIBUTE) {
            int editorX = panelX + PANEL_MARGIN;
            int editorW = panelW - PANEL_MARGIN * 2;
            int controlsY = contentY + (buildMode == BUILD_MODE_CREATE ? 128 : 119);
            addHourControls(editorX, editorW, controlsY);
            if (buildMode == BUILD_MODE_CREATE) {
                int selectorW = populationOwnerSelectorWidth(editorW);
                int selectorX = populationOwnerSelectorX(editorX, editorW, selectorW);
                buttonList.add(KOMEGuiButton.small(ID_BUILD_OWNER_PREV, selectorX, contentY + 72, "<"));
                buttonList.add(KOMEGuiButton.small(ID_BUILD_OWNER_NEXT, selectorX + selectorW - 20, contentY + 72, ">"));
                buttonList.add(new KOMEGuiButton(ID_BUILD_SUBMIT_CREATE, panelX + panelW - PANEL_MARGIN - 136,
                    footerY, 136, 22, "Confirm Build"));
            } else {
                buttonList.add(new KOMEGuiButton(ID_BUILD_SUBMIT_CONTRIBUTION, panelX + panelW - PANEL_MARGIN - 136,
                    footerY, 136, 22, selected != null && selected.canManage ? "Apply Hours" : "Submit Hours"));
            }
            return;
        }
        if (buildMode == BUILD_MODE_RENAME) {
            buttonList.add(new KOMEGuiButton(ID_BUILD_SUBMIT_RENAME, panelX + panelW - PANEL_MARGIN - 128,
                footerY, 128, 22, "Save Name"));
            return;
        }
        if (selected == null) {
            buildMode = BUILD_MODE_LIST;
            initGui();
            return;
        }
        int actionX = panelX + PANEL_MARGIN;
        int actionY = contentY + 121;
        int gap = 7;
        int actionW = Math.max(72, (panelW - PANEL_MARGIN * 2 - gap * 2) / 3);
        buttonList.add(new KOMEGuiButton(ID_BUILD_CONTRIBUTE, actionX, actionY, actionW, 20, "Add Hours"));
        GuiButton rename = new KOMEGuiButton(ID_BUILD_RENAME, actionX + actionW + gap, actionY, actionW, 20, "Rename");
        rename.enabled = selected.canManage;
        buttonList.add(rename);
        String destroyMode = buildDestroyMode(selected);
        GuiButton destroy = new KOMEGuiButton(ID_BUILD_DELETE, actionX + (actionW + gap) * 2, actionY,
            actionW, 20, "Destroy Build");
        destroy.enabled = destroyMode.length() > 0 && safe(selected.destroyReason).length() == 0;
        ((KOMEGuiButton) destroy).setStyle(KOMEGuiButton.Style.DESTRUCTIVE);
        buttonList.add(destroy);
        int visible = contributionVisibleRows();
        contributionScroll = clamp(contributionScroll, 0, Math.max(0, selected.contributions.size() - visible));
        for (int row = 0; row < visible && contributionScroll + row < selected.contributions.size(); row++) {
            KOMEPacketConquestCaptureGui.ContributionView contribution =
                (KOMEPacketConquestCaptureGui.ContributionView) selected.contributions.get(contributionScroll + row);
            int y = contentY + 173 + row * 38;
            if (selected.canManage && "PENDING".equals(contribution.status)) {
                buttonList.add(new KOMEGuiButton(ID_BUILD_APPROVE_BASE + row, panelX + panelW - PANEL_MARGIN - 126,
                    y + 9, 57, 18, "Approve"));
                buttonList.add(new KOMEGuiButton(ID_BUILD_REJECT_BASE + row, panelX + panelW - PANEL_MARGIN - 64,
                    y + 9, 57, 18, "Reject"));
            } else if (selected.canManage && "APPROVED".equals(contribution.status)) {
                GuiButton remove = new KOMEGuiButton(ID_BUILD_REMOVE_BASE + row, panelX + panelW - PANEL_MARGIN - 72,
                    y + 9, 65, 18, "Remove");
                ((KOMEGuiButton) remove).setStyle(KOMEGuiButton.Style.DESTRUCTIVE);
                buttonList.add(remove);
            }
        }
    }

    private void initPopulationPoolControls() {
        int footerY = panelY + panelH - 32;
        buttonList.add(new KOMEGuiButton(ID_BACK, panelX + PANEL_MARGIN, footerY, 104, 22, "Back to Map"));
        buttonList.add(new KOMEGuiButton(ID_VIEW_UNITS, panelX + panelW - PANEL_MARGIN - 112, footerY,
            112, 22, "View Units"));
        poolScroll = clamp(poolScroll, 0, Math.max(0, populationPoolViews.size() - poolVisibleRows()));
    }

    private void addHourControls(int x, int w, int y) {
        int groupW = Math.max(76, w - 40);
        int left = x + 20;
        buttonList.add(KOMEGuiButton.small(ID_BUILD_OFF_MINUS, left, y, "-"));
        buttonList.add(KOMEGuiButton.small(ID_BUILD_OFF_PLUS, left + groupW - 20, y, "+"));
        if (buildMode == BUILD_MODE_CREATE) {
            buttonList.add(new KOMEGuiButton(ID_BUILD_DEF_MINUS, left, y - 24, 92, 18, "Normal"));
            buttonList.add(new KOMEGuiButton(ID_BUILD_DEF_PLUS, left + 98, y - 24, 92, 18, "Defensive"));
        }
        buildHoursField = new GuiTextField(fontRendererObj, left + 25, y + 1,
            Math.max(26, groupW - 50), 16);
        buildHoursField.setMaxStringLength(12);
        syncHourFields();
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
        if (handleBuildAndTabAction(button)) {
            return;
        }
        if (button.id == ID_CLAIM) {
            confirmationDismissed = false;
            showClaimConfirmation();
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
            KOMEPacketHandler.network.sendToServer(new kome.common.network.KOMEPacketTroopGuiAction("list", "", "", tileId));
        } else if (button.id == ID_CANCEL_TRANSFER_MODE) {
            transferMode = false;
            initGui();
        } else if (button.id == ID_ADD_OFFENSIVE || button.id == ID_REMOVE_OFFENSIVE || button.id == ID_ADD_DEFENSIVE || button.id == ID_REMOVE_DEFENSIVE) {
            sendPopulationUpdate(button.id);
        } else if (button.id >= ID_ALLOCATE_OFFENSIVE && button.id <= ID_UNALLOCATE_DEFENSIVE) {
            sendAllocationUpdate(button.id);
        } else if (button.id == ID_SET_RECRUITMENT_TILE) {
            KOMEPacketHandler.network.sendToServer(new kome.common.network.KOMEPacketTroopGuiAction("recruit", "", "", tileId));
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id == ID_VIEW_UNITS) {
            KOMEPacketHandler.network.sendToServer(new kome.common.network.KOMEPacketTroopGuiAction("population_units", "", "", tileId));
        }
    }

    private boolean handleBuildAndTabAction(GuiButton button) {
        if (button.id >= ID_TAB_BUILDS && button.id <= ID_TAB_ALLOCATIONS) {
            activeTab = button.id - ID_TAB_BUILDS;
            buildMode = BUILD_MODE_LIST;
            transferMode = false;
            initGui();
            return true;
        }
        if (activeTab != 0) return false;
        if (button.id >= ID_BUILD_VIEW_BASE && button.id < ID_BUILD_VIEW_BASE + 100) {
            selectedBuildIndex = buildScroll + button.id - ID_BUILD_VIEW_BASE;
            buildMode = BUILD_MODE_DETAIL;
            contributionScroll = 0;
            initGui();
            return true;
        }
        if (button.id == ID_BUILD_NEW) {
            buildMode = BUILD_MODE_CREATE;
            editHalfHours = 0;
            editBuildType = KOMEBuildType.NORMAL;
            populationOwnerIndex = clamp(populationOwnerIndex, 0, Math.max(0, selectablePopulationOwners.size() - 1));
            initGui();
            return true;
        }
        if (button.id == ID_BUILD_LIST) {
            buildMode = BUILD_MODE_LIST;
            initGui();
            return true;
        }
        if (button.id == ID_BUILD_CONTRIBUTE) {
            buildMode = BUILD_MODE_CONTRIBUTE;
            editHalfHours = 0;
            KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
            if (selected != null) editBuildType = KOMEBuildType.forKey(selected.buildType);
            initGui();
            return true;
        }
        if (button.id == ID_BUILD_RENAME) {
            buildMode = BUILD_MODE_RENAME;
            initGui();
            return true;
        }
        if (button.id == ID_BUILD_OFF_MINUS || button.id == ID_BUILD_OFF_PLUS) {
            if (!normalizeHourFields()) return true;
            if (button.id == ID_BUILD_OFF_MINUS) {
                editHalfHours = KOMEHalfHourService.adjustHalfHours(editHalfHours, -1);
            } else {
                editHalfHours = KOMEHalfHourService.adjustHalfHours(editHalfHours, 1);
            }
            syncHourFields();
        }
        else if (button.id == ID_BUILD_DEF_MINUS && buildMode == BUILD_MODE_CREATE) editBuildType = KOMEBuildType.NORMAL;
        else if (button.id == ID_BUILD_DEF_PLUS && buildMode == BUILD_MODE_CREATE) editBuildType = KOMEBuildType.DEFENSIVE;
        else if (button.id == ID_BUILD_OWNER_PREV) populationOwnerIndex = wrap(populationOwnerIndex - 1, selectablePopulationOwners.size());
        else if (button.id == ID_BUILD_OWNER_NEXT) populationOwnerIndex = wrap(populationOwnerIndex + 1, selectablePopulationOwners.size());
        else if (button.id == ID_BUILD_SUBMIT_CREATE) {
            if (!normalizeHourFields()) return true;
            String owner = selectablePopulationOwners.isEmpty() ? "" : (String) selectablePopulationOwners.get(populationOwnerIndex);
            sendBuildAction("create", "", "", buildNameField == null ? "" : buildNameField.getText(), owner);
        } else if (button.id == ID_BUILD_SUBMIT_CONTRIBUTION) {
            if (!normalizeHourFields()) return true;
            KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
            if (selected != null && editHalfHours > 0) {
                sendBuildAction("contribute", selected.id, "", "", "");
            }
        } else if (button.id == ID_BUILD_SUBMIT_RENAME) {
            KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
            if (selected != null) sendBuildAction("rename", selected.id, "",
                buildNameField == null ? "" : buildNameField.getText(), "");
        } else if (button.id == ID_BUILD_DELETE) {
            KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
            if (selected != null) {
                pendingDestructiveBuildAction = buildDestroyMode(selected);
                pendingDestructiveBuildId = selected.id;
                boolean enemyDestruction = "destroy".equals(pendingDestructiveBuildAction);
                confirmation.show(enemyDestruction ? "Destroy Hostile Build" : "Destroy Managed Build",
                    (enemyDestruction
                        ? "As the eligible homeland controller king, you are destroying a hostile faction's Build. "
                        : "As this Build's manager or an administrator, you are permanently deleting the managed Build. ")
                        + "This removes its active hours, generated population, progression credit, pending submissions, and map marker. "
                        + "The server will reject the action if population remains committed or allocated.",
                    "Destroy Build");
            }
        } else if (button.id >= ID_BUILD_APPROVE_BASE && button.id < ID_BUILD_APPROVE_BASE + 100) {
            sendContributionAction("approve", button.id - ID_BUILD_APPROVE_BASE);
        } else if (button.id >= ID_BUILD_REJECT_BASE && button.id < ID_BUILD_REJECT_BASE + 100) {
            sendContributionAction("reject", button.id - ID_BUILD_REJECT_BASE);
        } else if (button.id >= ID_BUILD_REMOVE_BASE && button.id < ID_BUILD_REMOVE_BASE + 100) {
            sendContributionAction("remove", button.id - ID_BUILD_REMOVE_BASE);
        } else {
            return false;
        }
        return true;
    }

    private void sendContributionAction(String action, int visibleRow) {
        KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
        int index = contributionScroll + visibleRow;
        if (selected == null || index < 0 || index >= selected.contributions.size()) return;
        KOMEPacketConquestCaptureGui.ContributionView contribution =
            (KOMEPacketConquestCaptureGui.ContributionView) selected.contributions.get(index);
        sendBuildAction(action, selected.id, contribution.id, "", "");
    }

    private void sendBuildAction(String action, String buildId, String contributionId, String text, String owner) {
        KOMEPacketHandler.network.sendToServer(new KOMEPacketBuildAction(action, tileId, buildId,
            contributionId, text, owner, editBuildType.key, editHalfHours,
            viewerDimension, viewerWorldX, viewerWorldY, viewerWorldZ));
    }

    private String buildDestroyMode(KOMEPacketConquestCaptureGui.BuildView build) {
        if (build == null) return "";
        String mode = safe(build.destroyMode).toLowerCase(java.util.Locale.ROOT);
        if ("delete".equals(mode) || "destroy".equals(mode)) return mode;
        if (build.canManage) return "delete";
        return build.canDestroy ? "destroy" : "";
    }

    private void syncHourFields() {
        if (buildHoursField != null) {
            buildHoursField.setText(KOMEHalfHourService.displayHours(editHalfHours));
        }
        buildHoursValidation = "";
    }

    private boolean updateHoursFromFields(boolean normalize) {
        if (buildHoursField == null) return true;
        try {
            editHalfHours = KOMEHalfHourService.parseHalfHours(buildHoursField.getText());
            buildHoursValidation = "";
            if (normalize) syncHourFields();
            return true;
        } catch (IllegalArgumentException error) {
            buildHoursValidation = error.getMessage();
            return false;
        }
    }

    private boolean normalizeHourFields() {
        return updateHoursFromFields(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // A previous screen may have been replaced while its scroll viewport was
        // active. Start each frame from a known GL clipping state.
        KOMEGuiTheme.disableScissor();
        drawDefaultBackground();
        computeLayout();
        int logicalMouseX = toLogical(mouseX);
        int logicalMouseY = toLogical(mouseY);
        GL11.glPushMatrix();
        GL11.glScalef(renderScale, renderScale, 1.0F);
        KOMEGuiTheme.drawMainPanel(panelX, panelY, panelW, panelH);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Tile Command", panelX + 8, panelY + 8, panelW - 16);
        drawHeaderMeta();
        if (activeTab == 0) {
            drawBuildTab(logicalMouseX, logicalMouseY);
        } else if (activeTab == 1) {
            drawPopulationPoolTab(logicalMouseX, logicalMouseY);
        } else {
            drawCards(logicalMouseX, logicalMouseY);
        }
        if (populationAmountField != null) {
            populationAmountField.drawTextBox();
        }
        if (allocationPlayerField != null) {
            allocationPlayerField.drawTextBox();
            allocationAmountField.drawTextBox();
        }
        if (buildNameField != null) buildNameField.drawTextBox();
        if (buildHoursField != null) {
            buildHoursField.drawTextBox();
        }
        super.drawScreen(logicalMouseX, logicalMouseY, partialTicks);
        if (populationHoverTooltip.length() > 0) {
            List lines = new ArrayList();
            lines.add(populationHoverTooltip);
            KOMEGuiTheme.drawTooltip(fontRendererObj, lines, logicalMouseX, logicalMouseY,
                logicalWidth, logicalHeight);
        }
        drawDisabledTooltip(logicalMouseX, logicalMouseY);
        confirmation.draw(fontRendererObj, logicalWidth, logicalHeight, logicalMouseX, logicalMouseY);
        GL11.glPopMatrix();
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (confirmation.isVisible() && key == 1) {
            confirmation.hide();
            confirmationDismissed = true;
            pendingDestructiveBuildAction = "";
            pendingDestructiveBuildId = "";
            return;
        }
        if (buildHoursField != null && buildHoursField.isFocused()) {
            if (key == 28 || key == 156) {
                normalizeHourFields();
                return;
            }
            if (buildHoursField.textboxKeyTyped(c, key)) {
                updateHoursFromFields(false);
                return;
            }
        }
        if (populationAmountField != null && populationAmountField.textboxKeyTyped(c, key)) {
            return;
        }
        if (allocationPlayerField != null && (allocationPlayerField.textboxKeyTyped(c, key) || allocationAmountField.textboxKeyTyped(c, key))) {
            return;
        }
        if (buildNameField != null && buildNameField.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        mouseX = toLogical(mouseX);
        mouseY = toLogical(mouseY);
        if (confirmation.isVisible()) {
            int result = confirmation.click(mouseX, mouseY, button);
            if (result == KOMEGuiConfirmationDialog.CONFIRM) {
                confirmation.hide();
                if (pendingDestructiveBuildAction.length() > 0) {
                    sendBuildAction(pendingDestructiveBuildAction, pendingDestructiveBuildId, "",
                        pendingDestructiveBuildAction.equals("destroy") ? "Enemy homeland destruction" : "Deleted in Tile Command", "");
                    pendingDestructiveBuildAction = "";
                    pendingDestructiveBuildId = "";
                } else {
                    KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestClaim(tileId));
                    KOMEConquestMapOverlay.openPreservedMap();
                }
            } else if (result == KOMEGuiConfirmationDialog.CANCEL) {
                confirmation.hide();
                confirmationDismissed = true;
                pendingDestructiveBuildAction = "";
                pendingDestructiveBuildId = "";
            }
            return;
        }
        boolean hoursFocused = buildHoursField != null && buildHoursField.isFocused();
        super.mouseClicked(mouseX, mouseY, button);
        if (populationAmountField != null) {
            populationAmountField.mouseClicked(mouseX, mouseY, button);
        }
        if (allocationPlayerField != null) {
            allocationPlayerField.mouseClicked(mouseX, mouseY, button);
            allocationAmountField.mouseClicked(mouseX, mouseY, button);
        }
        if (buildNameField != null) buildNameField.mouseClicked(mouseX, mouseY, button);
        if (buildHoursField != null) {
            buildHoursField.mouseClicked(mouseX, mouseY, button);
            if (hoursFocused && !buildHoursField.isFocused()) {
                normalizeHourFields();
            }
        }
    }

    @Override
    public void updateScreen() {
        if (populationAmountField != null) populationAmountField.updateCursorCounter();
        if (allocationPlayerField != null) allocationPlayerField.updateCursorCounter();
        if (allocationAmountField != null) allocationAmountField.updateCursorCounter();
        if (buildNameField != null) buildNameField.updateCursorCounter();
        if (buildHoursField != null) buildHoursField.updateCursorCounter();
        super.updateScreen();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int delta = wheel > 0 ? -1 : 1;
        if (activeTab == 0 && buildMode == BUILD_MODE_LIST) {
            buildScroll = clamp(buildScroll + delta, 0, Math.max(0, buildViews.size() - buildVisibleRows()));
            initGui();
        } else if (activeTab == 0 && buildMode == BUILD_MODE_DETAIL) {
            KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
            contributionScroll = clamp(contributionScroll + delta, 0,
                Math.max(0, selected == null ? 0 : selected.contributions.size() - contributionVisibleRows()));
            initGui();
        } else if (activeTab == 1) {
            poolScroll = clamp(poolScroll + delta, 0, Math.max(0, populationPoolViews.size() - poolVisibleRows()));
        }
    }

    private void computeLayout() {
        renderScale = Math.min(1.0F, Math.min(
            Math.max(1, width) / 788.0F, Math.max(1, height) / 458.0F));
        logicalWidth = Math.max(1, Math.round(width / renderScale));
        logicalHeight = Math.max(1, Math.round(height / renderScale));
        panelW = Math.min(760, logicalWidth - 28);
        panelH = Math.min(430, logicalHeight - 28);
        panelX = (logicalWidth - panelW) / 2;
        panelY = (logicalHeight - panelH) / 2;
    }

    private int toLogical(int coordinate) {
        return Math.round(coordinate / Math.max(0.01F, renderScale));
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

    private void drawBuildTab(int mouseX, int mouseY) {
        int x = panelX + PANEL_MARGIN;
        int y = panelY + CONTENT_Y_OFFSET;
        int w = panelW - PANEL_MARGIN * 2;
        int bottom = panelY + panelH - 40;
        KOMEGuiTheme.drawSubPanel(x, y, w, Math.max(40, bottom - y));
        if (buildMode == BUILD_MODE_LIST) {
            KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Builds in " + tileDisplayName(), x + 12, y + 9, w - 24);
            if (buildViews.isEmpty()) {
                KOMEGuiTheme.drawWarningBanner(fontRendererObj, "No Builds",
                    "No persistent Builds exist in this tile. Create one at your current world position; the server validates the coordinates, controller relation, and selected population owner.",
                    x + 14, y + 38, w - 28, KOMEGuiTheme.Status.NEUTRAL);
                return;
            }
            int visible = buildVisibleRows();
            int viewportY = y + 25;
            int viewportH = Math.max(24, bottom - viewportY - 6);
            KOMEGuiTheme.enableScissor(mc, x + 6, viewportY, w - 12, viewportH, renderScale);
            for (int row = 0; row < visible && buildScroll + row < buildViews.size(); row++) {
                KOMEPacketConquestCaptureGui.BuildView build =
                    (KOMEPacketConquestCaptureGui.BuildView) buildViews.get(buildScroll + row);
                int cardY = y + 27 + row * 58;
                KOMEGuiTheme.drawCard(x + 8, cardY, w - 16, 52,
                    KOMEGuiTheme.isHovered(mouseX, mouseY, x + 8, cardY, w - 16, 52));
                fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
                    build.name + " (" + build.id + ")", Math.max(80, w - 230)), x + 18, cardY + 7,
                    KOMEGuiTheme.COLOR_BORDER_RED);
                KOMEGuiTheme.drawFactionBadge(fontRendererObj, build.populationFaction, build.status,
                    x + w - 210, cardY + 4, 112);
                fontRendererObj.drawString("Type " + build.buildType + "   Approved Hours "
                    + displayHalfHours(build.approvedHalfHours),
                    x + 18, cardY + 22, KOMEGuiTheme.COLOR_TEXT);
                fontRendererObj.drawString("Manager: " + safeName(build.manager, "Unassigned")
                    + "   Pending: " + build.pendingCount + "   At " + coord(build.x) + ", "
                    + coord(build.y) + ", " + coord(build.z),
                    x + 18, cardY + 36, KOMEGuiTheme.COLOR_TEXT_MUTED);
            }
            KOMEGuiTheme.disableScissor();
            drawSimpleScrollbar(x + w - 7, viewportY, viewportH, buildScroll, buildViews.size(), visible);
            return;
        }
        if (buildMode == BUILD_MODE_CREATE) {
            drawBuildEditor(x, y, w, true);
            return;
        }
        KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
        if (selected == null) {
            fontRendererObj.drawString("The selected Build is no longer available.", x + 16, y + 18, KOMEGuiTheme.COLOR_BAD);
            return;
        }
        if (buildMode == BUILD_MODE_CONTRIBUTE) {
            drawBuildEditor(x, y, w, false);
            return;
        }
        if (buildMode == BUILD_MODE_RENAME) {
            KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Rename " + selected.id, x + 12, y + 10, w - 24);
            fontRendererObj.drawString("Build name", x + 20, y + 28, KOMEGuiTheme.COLOR_TEXT_MUTED);
            KOMEGuiTheme.drawWrappedText(fontRendererObj,
                "The stable Build ID and source tile do not change when the visible name changes.",
                x + 20, y + 69, w - 40, KOMEGuiTheme.COLOR_TEXT);
            return;
        }
        drawBuildDetail(selected, x, y, w, bottom, mouseX, mouseY);
    }

    private void drawBuildEditor(int x, int y, int w, boolean creating) {
        KOMEPacketConquestCaptureGui.BuildView selected = selectedBuild();
        KOMEGuiTheme.drawSectionTitle(fontRendererObj,
            creating ? "Create Persistent Build" : "Contribute to " + (selected == null ? "Build" : selected.name),
            x + 12, y + 10, w - 24);
        int controlsY = y + (creating ? 128 : 119);
        if (creating) {
            fontRendererObj.drawString("Name", x + 20, y + 28, KOMEGuiTheme.COLOR_TEXT_MUTED);
            String owner = selectablePopulationOwners.isEmpty() ? "No eligible owner"
                : (String) selectablePopulationOwners.get(clamp(populationOwnerIndex, 0, selectablePopulationOwners.size() - 1));
            int selectorW = populationOwnerSelectorWidth(w);
            int selectorX = populationOwnerSelectorX(x, w, selectorW);
            fontRendererObj.drawString("Population Owner", x + 20, y + 78, KOMEGuiTheme.COLOR_TEXT_MUTED);
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj,
                KOMEGuiTheme.trimToWidth(fontRendererObj, factionName(owner), selectorW - 48),
                selectorX + selectorW / 2, y + 78,
                KOMEGuiTheme.COLOR_GOLD);
            fontRendererObj.drawString("Coordinates", x + 20, y + 102, KOMEGuiTheme.COLOR_TEXT_MUTED);
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
                "X: " + coord(viewerWorldX) + "   Y: " + coord(viewerWorldY) + "   Z: "
                    + coord(viewerWorldZ) + "   Dimension: " + viewerDimension, w - 146),
                x + 126, y + 102, KOMEGuiTheme.COLOR_TEXT);
        } else {
            KOMEGuiTheme.drawWarningBanner(fontRendererObj, selected != null && selected.canManage
                    ? "Immediate Manager Contribution" : "Manager Approval Required",
                selected != null && selected.canManage
                    ? "Your hours apply immediately because you manage this Build."
                    : "Your submission remains Pending and creates no population or alliance credit until the current manager approves it.",
                x + 16, y + 37, w - 32,
                selected != null && selected.canManage ? KOMEGuiTheme.Status.ACTIVE : KOMEGuiTheme.Status.WARNING);
        }
        fontRendererObj.drawString((creating ? "Build Type: " + editBuildType.key : "Build Type: "
            + (selected == null ? "" : selected.buildType)) + "   Hours", x + 20, controlsY - 13,
            KOMEGuiTheme.COLOR_TEXT_MUTED);
        if (buildHoursValidation.length() > 0) {
            KOMEGuiTheme.drawWrappedText(fontRendererObj, buildHoursValidation, x + 20, controlsY + 22,
                w - 40, KOMEGuiTheme.COLOR_BAD);
        }
        KOMEGuiTheme.drawWarningBanner(fontRendererObj, "Canonical Build Hours",
            "Approved half-hours are stored exactly. " + (creating ? "The builder receives contribution credit; " + (selectablePopulationOwners.isEmpty()
                    ? "no owner is eligible." : factionName((String) selectablePopulationOwners.get(populationOwnerIndex)))
                    + " permanently owns the generated population." : "Contribution credit follows your current faction; population ownership does not change."),
            x + 16, y + (creating ? 164 : 157), w - 32, KOMEGuiTheme.Status.NEUTRAL);
    }

    private void drawBuildDetail(KOMEPacketConquestCaptureGui.BuildView build, int x, int y, int w,
            int bottom, int mouseX, int mouseY) {
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, build.name + " (" + build.id + ")", x + 12, y + 9, w - 24);
        KOMEGuiTheme.drawCard(x + 12, y + 25, w - 24, 43, false);
        fontRendererObj.drawString("Owner: " + factionName(build.populationFaction) + "   Status: " + build.status,
            x + 20, y + 32, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Builder: " + safeName(build.builder, "Unknown") + "   Manager: "
            + safeName(build.manager, "Unassigned"), x + 20, y + 44, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
            "Coordinates: X " + coord(build.x) + " / Y " + coord(build.y) + " / Z " + coord(build.z)
                + " / Dimension " + build.dimension + "   Pending: " + build.pendingCount, w - 48),
            x + 20, y + 56, KOMEGuiTheme.COLOR_TEXT_MUTED);
        KOMEGuiTheme.drawCard(x + 12, y + 74, w - 24, 42, false);
        fontRendererObj.drawString("Type: " + build.buildType, x + 20, y + 82, KOMEGuiTheme.COLOR_GOLD);
        fontRendererObj.drawString("Approved Hours: " + displayHalfHours(build.approvedHalfHours), x + 20, y + 98,
            KOMEGuiTheme.COLOR_TEXT);
        KOMEGuiTheme.drawDivider(x + 12, y + 147, w - 24);
        fontRendererObj.drawString("Contribution Audit", x + 16, y + 156, KOMEGuiTheme.COLOR_BORDER_RED);
        if (build.contributions.isEmpty()) {
            fontRendererObj.drawString("No contributions have been submitted.", x + 18, y + 181, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        int visible = contributionVisibleRows();
        int viewportY = y + 169;
        int viewportH = Math.max(20, bottom - viewportY - 4);
        KOMEGuiTheme.enableScissor(mc, x + 8, viewportY, w - 16, viewportH, renderScale);
        for (int row = 0; row < visible && contributionScroll + row < build.contributions.size(); row++) {
            KOMEPacketConquestCaptureGui.ContributionView contribution =
                (KOMEPacketConquestCaptureGui.ContributionView) build.contributions.get(contributionScroll + row);
            int cardY = y + 173 + row * 38;
            KOMEGuiTheme.drawCard(x + 10, cardY, w - 20, 33,
                KOMEGuiTheme.isHovered(mouseX, mouseY, x + 10, cardY, w - 20, 33));
            int color = "APPROVED".equals(contribution.status) ? KOMEGuiTheme.COLOR_GOOD
                : "PENDING".equals(contribution.status) ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_BAD;
            fontRendererObj.drawString(contribution.player + " / " + factionName(contribution.faction)
                + "   Hours " + displayHalfHours(contribution.halfHours), x + 18, cardY + 7,
                KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString(contribution.status, x + 18, cardY + 20, color);
        }
        KOMEGuiTheme.disableScissor();
        drawSimpleScrollbar(x + w - 7, viewportY, viewportH, contributionScroll,
            build.contributions.size(), visible);
    }


    private void drawPopulationPoolTab(int mouseX, int mouseY) {
        populationHoverTooltip = "";
        int x = panelX + PANEL_MARGIN;
        int y = panelY + CONTENT_Y_OFFSET;
        int w = panelW - PANEL_MARGIN * 2;
        int bottom = panelY + panelH - 40;
        KOMEGuiTheme.drawSubPanel(x, y, w, Math.max(40, bottom - y));
        int physical = 0;
        int usable = 0;
        int used = 0;
        int available = 0;
        for (Object object : populationPoolViews) {
            KOMEPacketConquestCaptureGui.PopulationPoolView pool =
                (KOMEPacketConquestCaptureGui.PopulationPoolView) object;
            KOMEPopulationGraph.Segments offensive = KOMEPopulationGraph.segments(
                pool.physicalOffensive, pool.usableOffensive, pool.usedOffensive);
            KOMEPopulationGraph.Segments defensive = KOMEPopulationGraph.segments(
                pool.physicalDefensive, pool.usableDefensive, pool.usedDefensive);
            physical = KOMEPopulationGraph.saturatingAdd(physical,
                KOMEPopulationGraph.saturatingAdd(offensive.physical, defensive.physical));
            usable = KOMEPopulationGraph.saturatingAdd(usable,
                KOMEPopulationGraph.saturatingAdd(offensive.usable, defensive.usable));
            used = KOMEPopulationGraph.saturatingAdd(used,
                KOMEPopulationGraph.saturatingAdd(offensive.used, defensive.used));
            available = KOMEPopulationGraph.saturatingAdd(available,
                KOMEPopulationGraph.saturatingAdd(offensive.available, defensive.available));
        }
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Faction-owned Population Pools", x + 12, y + 9, w - 24);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
            "Physical " + physical + "   Usable " + usable + "   Used " + used + "   Available " + available,
            w - 32), x + 16, y + 28, KOMEGuiTheme.COLOR_TEXT);
        drawPopulationLegend(x + 16, y + 42, w - 32);
        if (populationPoolViews.isEmpty()) {
            fontRendererObj.drawString("No population pools are recorded in this tile.", x + 18, y + 66,
                KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        int visible = poolVisibleRows();
        int viewportY = y + 57;
        int viewportH = Math.max(24, bottom - viewportY - 5);
        KOMEGuiTheme.enableScissor(mc, x + 7, viewportY, w - 14, viewportH, renderScale);
        for (int row = 0; row < visible && poolScroll + row < populationPoolViews.size(); row++) {
            KOMEPacketConquestCaptureGui.PopulationPoolView pool =
                (KOMEPacketConquestCaptureGui.PopulationPoolView) populationPoolViews.get(poolScroll + row);
            int cardY = y + 60 + row * POPULATION_POOL_ROW_HEIGHT;
            int cardW = w - 18;
            KOMEGuiTheme.drawCard(x + 9, cardY, cardW, 98,
                KOMEGuiTheme.isHovered(mouseX, mouseY, x + 9, cardY, cardW, 98));
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
                factionName(pool.faction), Math.max(70, cardW / 2 - 16)),
                x + 18, cardY + 7, KOMEGuiTheme.COLOR_BORDER_RED);
            boolean controllerOwned = KOMEAlliance.normalizeFactionKey(pool.faction).equals(
                KOMEAlliance.normalizeFactionKey(currentRulingFaction.length() == 0
                    ? ownerFaction : currentRulingFaction));
            int poolPhysical = KOMEPopulationGraph.saturatingAdd(
                pool.physicalOffensive, pool.physicalDefensive);
            int poolUsable = KOMEPopulationGraph.saturatingAdd(
                pool.usableOffensive, pool.usableDefensive);
            String access = KOMEPopulationGraph.accessLabel(controllerOwned, poolPhysical, poolUsable);
            fontRendererObj.drawString(access, x + w - 18 - fontRendererObj.getStringWidth(access), cardY + 7,
                access.startsWith("100") ? KOMEGuiTheme.COLOR_GOOD
                    : access.startsWith("50") ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_BAD);
            String sources = "Base O " + pool.nativeOffensive + " / D " + pool.nativeDefensive
                + "   Build O " + pool.buildOffensive + " / D " + pool.buildDefensive;
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, sources, cardW - 18),
                x + 18, cardY + 21, KOMEGuiTheme.COLOR_TEXT_MUTED);
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, x + 16, cardY + 18, cardW - 14, 12)) {
                populationHoverTooltip =
                    "Base Population is population already assigned to this tile and is not generated by a Build.";
            }
            drawPopulationPoolGraph("Offensive", pool.physicalOffensive, pool.usableOffensive,
                pool.usedOffensive, x + 18, cardY + 35, cardW - 18, mouseX, mouseY);
            drawPopulationPoolGraph("Defensive", pool.physicalDefensive, pool.usableDefensive,
                pool.usedDefensive, x + 18, cardY + 63, cardW - 18, mouseX, mouseY);
        }
        KOMEGuiTheme.disableScissor();
        drawSimpleScrollbar(x + w - 7, viewportY, viewportH, poolScroll, populationPoolViews.size(), visible);
    }

    private void drawPopulationLegend(int x, int y, int width) {
        drawRect(x, y + 2, x + 7, y + 9, COLOR_POOL_USED);
        fontRendererObj.drawString("Used", x + 10, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
        drawRect(x + 48, y + 2, x + 55, y + 9, COLOR_POOL_AVAILABLE);
        fontRendererObj.drawString("Available", x + 58, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
        drawRect(x + 124, y + 2, x + 131, y + 9, COLOR_POOL_INACCESSIBLE);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
            "Inaccessible (foreign pools provide 50% while occupied)", Math.max(20, width - 134)),
            x + 134, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawPopulationPoolGraph(String label, int physical, int usable, int used,
            int x, int y, int width, int mouseX, int mouseY) {
        KOMEPopulationGraph.Segments values = KOMEPopulationGraph.segments(physical, usable, used);
        String numbers = label + ": Physical " + values.physical + " | Usable " + values.usable
            + " | Used " + values.used + " | Available " + values.available;
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, numbers, width),
            x, y, KOMEGuiTheme.COLOR_TEXT);
        int barY = y + 12;
        int barH = 9;
        KOMEGuiTheme.drawBorderedRect(x, barY, width, barH,
            KOMEGuiTheme.COLOR_BORDER_DARK, 0xFF3A2A1B);
        int innerW = Math.max(0, width - 2);
        if (values.physical > 0 && innerW > 0) {
            int usedEnd = (int) ((long) innerW * values.used / values.physical);
            int availableEnd = (int) ((long) innerW
                * (values.used + values.available) / values.physical);
            drawPoolSegment(x + 1, barY + 1, usedEnd, barH - 2, COLOR_POOL_USED);
            drawPoolSegment(x + 1 + usedEnd, barY + 1, availableEnd - usedEnd,
                barH - 2, COLOR_POOL_AVAILABLE);
            drawPoolSegment(x + 1 + availableEnd, barY + 1, innerW - availableEnd,
                barH - 2, COLOR_POOL_INACCESSIBLE);
        }
        if (KOMEGuiTheme.isHovered(mouseX, mouseY, x, barY, width, barH)) {
            populationHoverTooltip = label + ": Used " + values.used + " | Available "
                + values.available + " | Inaccessible " + values.inaccessible
                + " | Physical " + values.physical;
        }
    }

    private void drawPoolSegment(int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        drawRect(x, y, x + width, y + height, color);
        drawRect(x, y, x + width, y + Math.min(2, height), 0x22FFFFFF);
    }

    private void drawSimpleScrollbar(int x, int y, int height, int offset, int total, int visible) {
        if (total <= visible || visible <= 0) return;
        KOMEGuiTheme.drawBorderedRect(x, y, 5, height, KOMEGuiTheme.COLOR_GOLD_DARK, 0x552B2117);
        int handleH = Math.max(16, height * visible / Math.max(1, total));
        int max = Math.max(1, total - visible);
        int handleY = y + (height - handleH) * offset / max;
        KOMEGuiTheme.drawBorderedRect(x, handleY, 5, handleH, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_GOLD);
    }

    private int buildVisibleRows() {
        return Math.max(1, (panelH - CONTENT_Y_OFFSET - 48) / 58);
    }

    private int poolVisibleRows() {
        return Math.max(1, (panelH - 190) / POPULATION_POOL_ROW_HEIGHT);
    }

    private int contributionVisibleRows() {
        return Math.max(1, (panelH - CONTENT_Y_OFFSET - 196) / 38);
    }

    private KOMEPacketConquestCaptureGui.BuildView selectedBuild() {
        return selectedBuildIndex >= 0 && selectedBuildIndex < buildViews.size()
            ? (KOMEPacketConquestCaptureGui.BuildView) buildViews.get(selectedBuildIndex) : null;
    }

    private int populationOwnerSelectorWidth(int editorW) {
        int textW = fontRendererObj.getStringWidth("No eligible owner");
        for (Object owner : selectablePopulationOwners) {
            textW = Math.max(textW, fontRendererObj.getStringWidth(factionName(String.valueOf(owner))));
        }
        return Math.min(Math.max(112, textW + 56), Math.max(112, editorW - 154));
    }

    private int populationOwnerSelectorX(int editorX, int editorW, int selectorW) {
        int result = editorX + Math.max(126, (editorW - selectorW) / 2);
        return Math.min(result, editorX + editorW - selectorW - 18);
    }

    private String tileDisplayName() {
        return lotrWaypointDisplayName.length() > 0 ? lotrWaypointDisplayName + " (" + tileId + ")" : tileId;
    }

    private static String displayHalfHours(int halfHours) {
        int safe = Math.max(0, halfHours);
        return safe % 2 == 0 ? Integer.toString(safe / 2) : safe / 2 + ".5";
    }

    private static String safeName(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static int coord(double value) {
        return (int) Math.floor(value);
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
        } else if (claimWarning.length() > 0) {
            String warning = claimWarning + (claimWarDestination.length() == 0 ? "" : " " + claimWarDestination);
            KOMEGuiTheme.drawWarningBanner(fontRendererObj, claimConfirmationArmed ? "Claim Consequences" : "Claim Notice",
                warning, x + 4, y + 2, w - 8,
                claimConfirmationArmed ? KOMEGuiTheme.Status.DENIED : KOMEGuiTheme.Status.WARNING);
        }
    }

    private void showClaimConfirmation() {
        String warning = claimWarning.length() > 0 ? claimWarning : "The server will evaluate ownership, alliance, and war consequences before changing this tile.";
        if (claimWarDestination.length() > 0) warning += " " + claimWarDestination;
        confirmation.show(claimConfirmationArmed ? "Confirm Allied/Hostile Claim" : "Confirm Tile Claim",
            warning, claimConfirmationArmed ? "Confirm Claim" : "Continue");
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
                KOMEGuiTheme.drawTooltip(fontRendererObj, lines, mouseX, mouseY,
                    logicalWidth, logicalHeight);
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
        if (id == ID_BUILD_DELETE) {
            KOMEPacketConquestCaptureGui.BuildView build = selectedBuild();
            if (build != null && safe(build.destroyReason).length() > 0) {
                return build.destroyReason;
            }
            return "Only the Build manager, an eligible hostile-homeland king, or an administrator may destroy this Build.";
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

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
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
