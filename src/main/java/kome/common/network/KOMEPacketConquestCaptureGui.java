package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

public class KOMEPacketConquestCaptureGui implements IMessage {
    public String tileId;
    public String ownerFaction;
    public String pendingFromFaction;
    public String pendingToFaction;
    public String viewerFaction;
    public int offensivePop;
    public int defensivePop;
    public int mountedPop;
    public int groundPop;
    public int incomingPop;
    public int outgoingPop;
    public long incomingEtaMillis;
    public int offensiveTotal;
    public int offensiveUsed;
    public int defensiveTotal;
    public int defensiveUsed;
    public int farmhandTotal;
    public int farmhandUsed;
    public boolean canClaim;
    public boolean canTransfer;
    public boolean canAcceptTransfer;
    public boolean canCancelTransfer;
    public boolean canMoveTroops;
    public boolean canEditPopulation;
    public int offensiveAllocated;
    public int defensiveAllocated;
    public int myOffensiveAllocated;
    public int myOffensiveUsed;
    public int myDefensiveAllocated;
    public int myDefensiveUsed;
    public String claimantName = "";
    public String allocationSummary = "";
    public boolean ownerHasKing;
    public int myOffensivePop;
    public int myDefensivePop;
    public int myMountedPop;
    public int myGroundPop;
    public String activeRecruitmentTile = "";
    public boolean canSetRecruitmentTile;
    public String lotrWaypointKey = "";
    public String lotrWaypointDisplayName = "";
    public String lotrWaypointRegion = "";
    public int waypointLevel;
    public String currentRulingFaction = "";
    public String defaultRulingFaction = "";
    public String mapRegion = "";
    public boolean claimConfirmationArmed;
    public String claimWarning = "";
    public String claimWarDestination = "";

    public KOMEPacketConquestCaptureGui() {
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, "", 0, 0, 0, 0, 0, 0, 0L);
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, "", offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis);
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, 0, 0, 0, 0, 0, 0, false, false, false, false, offensivePop > 0, false, 0, 0, 0, 0, 0, 0, "", "", false, 0, 0, 0, 0, "", false, "", "", "");
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, false, 0, 0, 0, 0, 0, 0, "", "", false, 0, 0, 0, 0, "", false, "", "", "");
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, 0, 0, 0, 0, 0, 0, "", "", false, 0, 0, 0, 0, "", false, "", "", "");
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, 0, 0, 0, 0, "", false, "", "", "");
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, myOffensivePop, myDefensivePop, myMountedPop, myGroundPop, activeRecruitmentTile, canSetRecruitmentTile, "", "", "");
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile, String lotrWaypointKey, String lotrWaypointDisplayName, String lotrWaypointRegion) {
        this.tileId = tileId;
        this.ownerFaction = ownerFaction;
        this.pendingFromFaction = pendingFromFaction;
        this.pendingToFaction = pendingToFaction;
        this.viewerFaction = viewerFaction;
        this.offensivePop = offensivePop;
        this.defensivePop = defensivePop;
        this.mountedPop = mountedPop;
        this.groundPop = groundPop;
        this.incomingPop = incomingPop;
        this.outgoingPop = outgoingPop;
        this.incomingEtaMillis = incomingEtaMillis;
        this.offensiveTotal = offensiveTotal;
        this.offensiveUsed = offensiveUsed;
        this.defensiveTotal = defensiveTotal;
        this.defensiveUsed = defensiveUsed;
        this.farmhandTotal = farmhandTotal;
        this.farmhandUsed = farmhandUsed;
        this.canClaim = canClaim;
        this.canTransfer = canTransfer;
        this.canAcceptTransfer = canAcceptTransfer;
        this.canCancelTransfer = canCancelTransfer;
        this.canMoveTroops = canMoveTroops;
        this.canEditPopulation = canEditPopulation;
        this.offensiveAllocated = offensiveAllocated;
        this.defensiveAllocated = defensiveAllocated;
        this.myOffensiveAllocated = myOffensiveAllocated;
        this.myOffensiveUsed = myOffensiveUsed;
        this.myDefensiveAllocated = myDefensiveAllocated;
        this.myDefensiveUsed = myDefensiveUsed;
        this.claimantName = claimantName == null ? "" : claimantName;
        this.allocationSummary = allocationSummary == null ? "" : allocationSummary;
        this.ownerHasKing = ownerHasKing;
        this.myOffensivePop = myOffensivePop;
        this.myDefensivePop = myDefensivePop;
        this.myMountedPop = myMountedPop;
        this.myGroundPop = myGroundPop;
        this.activeRecruitmentTile = activeRecruitmentTile == null ? "" : activeRecruitmentTile;
        this.canSetRecruitmentTile = canSetRecruitmentTile;
        this.lotrWaypointKey = lotrWaypointKey == null ? "" : lotrWaypointKey;
        this.lotrWaypointDisplayName = lotrWaypointDisplayName == null ? "" : lotrWaypointDisplayName;
        this.lotrWaypointRegion = lotrWaypointRegion == null ? "" : lotrWaypointRegion;
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile, String lotrWaypointKey, String lotrWaypointDisplayName, String lotrWaypointRegion, int waypointLevel, String currentRulingFaction, String defaultRulingFaction, String mapRegion) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, myOffensivePop, myDefensivePop, myMountedPop, myGroundPop, activeRecruitmentTile, canSetRecruitmentTile, lotrWaypointKey, lotrWaypointDisplayName, lotrWaypointRegion);
        this.waypointLevel = waypointLevel;
        this.currentRulingFaction = currentRulingFaction == null ? "" : currentRulingFaction;
        this.defaultRulingFaction = defaultRulingFaction == null ? "" : defaultRulingFaction;
        this.mapRegion = mapRegion == null ? "" : mapRegion;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
        ownerFaction = ByteBufUtils.readUTF8String(buf);
        pendingFromFaction = ByteBufUtils.readUTF8String(buf);
        pendingToFaction = ByteBufUtils.readUTF8String(buf);
        viewerFaction = ByteBufUtils.readUTF8String(buf);
        offensivePop = buf.readInt();
        defensivePop = buf.readInt();
        mountedPop = buf.readInt();
        groundPop = buf.readInt();
        incomingPop = buf.readInt();
        outgoingPop = buf.readInt();
        incomingEtaMillis = buf.readLong();
        offensiveTotal = buf.readInt();
        offensiveUsed = buf.readInt();
        defensiveTotal = buf.readInt();
        defensiveUsed = buf.readInt();
        farmhandTotal = buf.readInt();
        farmhandUsed = buf.readInt();
        canClaim = buf.readBoolean();
        canTransfer = buf.readBoolean();
        canAcceptTransfer = buf.readBoolean();
        canCancelTransfer = buf.readBoolean();
        canMoveTroops = buf.readBoolean();
        canEditPopulation = buf.readBoolean();
        offensiveAllocated = buf.readInt();
        defensiveAllocated = buf.readInt();
        myOffensiveAllocated = buf.readInt();
        myOffensiveUsed = buf.readInt();
        myDefensiveAllocated = buf.readInt();
        myDefensiveUsed = buf.readInt();
        claimantName = ByteBufUtils.readUTF8String(buf);
        allocationSummary = ByteBufUtils.readUTF8String(buf);
        ownerHasKing = buf.readBoolean();
        myOffensivePop = buf.readInt();
        myDefensivePop = buf.readInt();
        myMountedPop = buf.readInt();
        myGroundPop = buf.readInt();
        activeRecruitmentTile = ByteBufUtils.readUTF8String(buf);
        canSetRecruitmentTile = buf.readBoolean();
        lotrWaypointKey = ByteBufUtils.readUTF8String(buf);
        lotrWaypointDisplayName = ByteBufUtils.readUTF8String(buf);
        lotrWaypointRegion = ByteBufUtils.readUTF8String(buf);
        waypointLevel = buf.readInt();
        currentRulingFaction = ByteBufUtils.readUTF8String(buf);
        defaultRulingFaction = ByteBufUtils.readUTF8String(buf);
        mapRegion = ByteBufUtils.readUTF8String(buf);
        claimConfirmationArmed = buf.readBoolean();
        claimWarning = ByteBufUtils.readUTF8String(buf);
        claimWarDestination = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
        ByteBufUtils.writeUTF8String(buf, ownerFaction);
        ByteBufUtils.writeUTF8String(buf, pendingFromFaction);
        ByteBufUtils.writeUTF8String(buf, pendingToFaction);
        ByteBufUtils.writeUTF8String(buf, viewerFaction);
        buf.writeInt(offensivePop);
        buf.writeInt(defensivePop);
        buf.writeInt(mountedPop);
        buf.writeInt(groundPop);
        buf.writeInt(incomingPop);
        buf.writeInt(outgoingPop);
        buf.writeLong(incomingEtaMillis);
        buf.writeInt(offensiveTotal);
        buf.writeInt(offensiveUsed);
        buf.writeInt(defensiveTotal);
        buf.writeInt(defensiveUsed);
        buf.writeInt(farmhandTotal);
        buf.writeInt(farmhandUsed);
        buf.writeBoolean(canClaim);
        buf.writeBoolean(canTransfer);
        buf.writeBoolean(canAcceptTransfer);
        buf.writeBoolean(canCancelTransfer);
        buf.writeBoolean(canMoveTroops);
        buf.writeBoolean(canEditPopulation);
        buf.writeInt(offensiveAllocated);
        buf.writeInt(defensiveAllocated);
        buf.writeInt(myOffensiveAllocated);
        buf.writeInt(myOffensiveUsed);
        buf.writeInt(myDefensiveAllocated);
        buf.writeInt(myDefensiveUsed);
        ByteBufUtils.writeUTF8String(buf, claimantName);
        ByteBufUtils.writeUTF8String(buf, allocationSummary);
        buf.writeBoolean(ownerHasKing);
        buf.writeInt(myOffensivePop);
        buf.writeInt(myDefensivePop);
        buf.writeInt(myMountedPop);
        buf.writeInt(myGroundPop);
        ByteBufUtils.writeUTF8String(buf, activeRecruitmentTile);
        buf.writeBoolean(canSetRecruitmentTile);
        ByteBufUtils.writeUTF8String(buf, lotrWaypointKey);
        ByteBufUtils.writeUTF8String(buf, lotrWaypointDisplayName);
        ByteBufUtils.writeUTF8String(buf, lotrWaypointRegion);
        buf.writeInt(waypointLevel);
        ByteBufUtils.writeUTF8String(buf, currentRulingFaction);
        ByteBufUtils.writeUTF8String(buf, defaultRulingFaction);
        ByteBufUtils.writeUTF8String(buf, mapRegion);
        buf.writeBoolean(claimConfirmationArmed);
        ByteBufUtils.writeUTF8String(buf, claimWarning == null ? "" : claimWarning);
        ByteBufUtils.writeUTF8String(buf, claimWarDestination == null ? "" : claimWarDestination);
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestCaptureGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestCaptureGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayConquestCaptureGui(message);
            return null;
        }
    }
}
